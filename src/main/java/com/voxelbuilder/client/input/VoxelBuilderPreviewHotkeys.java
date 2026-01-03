package com.voxelbuilder.client.input;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import org.lwjgl.glfw.GLFW;

/**
 * Preview hotkeys (no GUI required)
 *
 * Workflow:
 * 1) Place Build + right click sets anchor (ghost becomes visible and stays put)
 * 2) Rotate/Nudge while NOT confirmed
 * 3) Enter confirms placement (locks edits + starts build)
 * 4) Backspace unlocks edits (does NOT clear preview plan)
 *
 * Arrow movement:
 * - Arrows = relative to player facing (forward/back/strafe)
 * - SHIFT + arrows = world axes (X/Z)
 * - PageUp/PageDown = Y up/down
 *
 * IMPORTANT:
 * - Uses reflection for renderer/session APIs to avoid compile breakage when method names drift.
 */
public final class VoxelBuilderPreviewHotkeys {

    public static final String MODID = "voxelbuilder";
    private static final String CATEGORY = "key.categories.voxelbuilder";

    // Renderer class name (avoid hard binding to method signatures)
    private static final String RENDERER_CLASS = "com.voxelbuilder.client.render.GhostPreviewDebugRenderer";
    private static final String SESSION_CLASS  = "com.voxelbuilder.client.build.VoxelBuilderSession";

    // Rotation
    private static final KeyMapping ROT_X = new KeyMapping("key.voxelbuilder.rotate_x",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);

    private static final KeyMapping ROT_Y = new KeyMapping("key.voxelbuilder.rotate_y",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY);

    private static final KeyMapping ROT_Z = new KeyMapping("key.voxelbuilder.rotate_z",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);

    private static final KeyMapping RESET = new KeyMapping("key.voxelbuilder.rotate_reset",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    // Confirm / Cancel
    private static final KeyMapping CONFIRM = new KeyMapping("key.voxelbuilder.confirm_build",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_ENTER, CATEGORY);

    private static final KeyMapping CANCEL = new KeyMapping("key.voxelbuilder.cancel_build",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSPACE, CATEGORY);

    // Nudge (arrows + vertical)
    private static final KeyMapping NUDGE_UP = new KeyMapping("key.voxelbuilder.nudge_up",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UP, CATEGORY);

    private static final KeyMapping NUDGE_DOWN = new KeyMapping("key.voxelbuilder.nudge_down",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_DOWN, CATEGORY);

    private static final KeyMapping NUDGE_LEFT = new KeyMapping("key.voxelbuilder.nudge_left",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT, CATEGORY);

    private static final KeyMapping NUDGE_RIGHT = new KeyMapping("key.voxelbuilder.nudge_right",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, CATEGORY);

    private static final KeyMapping NUDGE_UP_Y = new KeyMapping("key.voxelbuilder.nudge_up_y",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_UP, CATEGORY);

    private static final KeyMapping NUDGE_DOWN_Y = new KeyMapping("key.voxelbuilder.nudge_down_y",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_DOWN, CATEGORY);

    // Stored degrees (90-step)
    private static int rx = 0;
    private static int ry = 0;
    private static int rz = 0;

    // Local confirmed lock (we also mirror renderer preview lock if present)
    private static boolean confirmedLocked = false;

    private VoxelBuilderPreviewHotkeys() {}

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBusHandlers {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(ROT_X);
            event.register(ROT_Y);
            event.register(ROT_Z);
            event.register(RESET);

            event.register(CONFIRM);
            event.register(CANCEL);

            event.register(NUDGE_UP);
            event.register(NUDGE_DOWN);
            event.register(NUDGE_LEFT);
            event.register(NUDGE_RIGHT);
            event.register(NUDGE_UP_Y);
            event.register(NUDGE_DOWN_Y);
        }
    }

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static final class GameBusHandlers {

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;

            // Only when no GUI is open
            if (mc.screen != null) return;

            // Keep local lock in sync if renderer exposes it
            Boolean rendererLocked = getRendererPreviewLocked();
            if (rendererLocked != null) confirmedLocked = rendererLocked.booleanValue();

            // ===== Confirm (Enter): lock edits + start build =====
            while (CONFIRM.consumeClick()) {
                confirmedLocked = true;
                setRendererPreviewLocked(true); // optional (tint / lock state)

                // start build session (best effort)
                startBuildSessionBestEffort();

                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            Component.literal("CONFIRMED: build started. Backspace to unlock."),
                            true
                    );
                }
            }

            // ===== Cancel (Backspace): unlock edits (do NOT clear preview plan) =====
            while (CANCEL.consumeClick()) {
                confirmedLocked = false;
                setRendererPreviewLocked(false);

                // Do not clear preview plan. Only clear "confirmed" session, if present.
                clearBuildSessionBestEffort();

                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            Component.literal("UNLOCKED: preview editable again."),
                            true
                    );
                }
            }

            // If confirmed/locked, do not allow rotate/nudge.
            if (confirmedLocked) {
                // consume queued inputs so they don't apply after unlock
                while (ROT_X.consumeClick()) {}
                while (ROT_Y.consumeClick()) {}
                while (ROT_Z.consumeClick()) {}
                while (RESET.consumeClick()) {}
                while (NUDGE_UP.consumeClick()) {}
                while (NUDGE_DOWN.consumeClick()) {}
                while (NUDGE_LEFT.consumeClick()) {}
                while (NUDGE_RIGHT.consumeClick()) {}
                while (NUDGE_UP_Y.consumeClick()) {}
                while (NUDGE_DOWN_Y.consumeClick()) {}
                return;
            }

            // We only nudge/rotate once an anchor exists (ghost is placed)
            BlockPos anchor = getRendererAnchorPos();
            if (anchor == null) return;

            // ===== Rotation =====
            while (ROT_X.consumeClick()) {
                rx = (rx + 90) % 360;
                setRendererRotation("setPreviewRotationX", rx);
            }

            while (ROT_Y.consumeClick()) {
                ry = (ry + 90) % 360;
                setRendererRotation("setPreviewRotationY", ry);
            }

            while (ROT_Z.consumeClick()) {
                rz = (rz + 90) % 360;
                setRendererRotation("setPreviewRotationZ", rz);
            }

            while (RESET.consumeClick()) {
                rx = 0; ry = 0; rz = 0;
                invokeRendererNoArgs("resetPreviewRotation");
            }

            // ===== Movement (anchor nudge) =====
            long window = mc.getWindow().getWindow();
            boolean shift =
                    InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) ||
                    InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);

            // PageUp/PageDown = Y nudge
            while (NUDGE_UP_Y.consumeClick()) {
                anchor = anchor.offset(0, 1, 0);
                setRendererAnchor(anchor);
            }
            while (NUDGE_DOWN_Y.consumeClick()) {
                anchor = anchor.offset(0, -1, 0);
                setRendererAnchor(anchor);
            }

            // Arrows: world axes (SHIFT) or relative to facing
            while (NUDGE_UP.consumeClick()) {
                int[] d = shift ? new int[]{0, -1} : forwardDelta(mc); // world "up" = -Z
                anchor = anchor.offset(d[0], 0, d[1]);
                setRendererAnchor(anchor);
            }
            while (NUDGE_DOWN.consumeClick()) {
                int[] d = shift ? new int[]{0, -1} : forwardDelta(mc);
                anchor = anchor.offset(-d[0], 0, -d[1]);
                setRendererAnchor(anchor);
            }
            while (NUDGE_LEFT.consumeClick()) {
                int[] d = shift ? new int[]{-1, 0} : leftDelta(mc);
                anchor = anchor.offset(d[0], 0, d[1]);
                setRendererAnchor(anchor);
            }
            while (NUDGE_RIGHT.consumeClick()) {
                int[] d = shift ? new int[]{-1, 0} : leftDelta(mc);
                anchor = anchor.offset(-d[0], 0, -d[1]);
                setRendererAnchor(anchor);
            }
        }

        // ===== Facing helpers =====

        private static int[] forwardDelta(Minecraft mc) {
            if (mc.player == null) return new int[]{0, 1};
            float yaw = mc.player.getYRot();
            int dir = yawToCardinal(yaw);

            // 0=S,1=W,2=N,3=E
            if (dir == 0) return new int[]{0, 1};
            if (dir == 1) return new int[]{-1, 0};
            if (dir == 2) return new int[]{0, -1};
            return new int[]{1, 0};
        }

        private static int[] leftDelta(Minecraft mc) {
            if (mc.player == null) return new int[]{-1, 0};
            float yaw = mc.player.getYRot();
            int dir = yawToCardinal(yaw);

            // left = rotate forward 90° CCW
            if (dir == 0) return new int[]{1, 0};   // S -> left +X
            if (dir == 1) return new int[]{0, 1};   // W -> left +Z
            if (dir == 2) return new int[]{-1, 0};  // N -> left -X
            return new int[]{0, -1};                // E -> left -Z
        }

        private static int yawToCardinal(float yaw) {
            float y = yaw % 360f;
            if (y < 0) y += 360f;
            return (Math.round(y / 90f) & 3); // 0=south,1=west,2=north,3=east
        }
    }

    /* =========================
     * Renderer reflection helpers
     * ========================= */

    private static Class<?> rendererClass() throws ClassNotFoundException {
        return Class.forName(RENDERER_CLASS);
    }

    private static void invokeRendererNoArgs(String method) {
        try {
            Class<?> c = rendererClass();
            java.lang.reflect.Method m = c.getDeclaredMethod(method);
            m.setAccessible(true);
            m.invoke(null);
        } catch (Throwable ignored) {}
    }

    private static void setRendererRotation(String method, int deg) {
        // Try int signature first, then float
        try {
            Class<?> c = rendererClass();
            try {
                java.lang.reflect.Method m = c.getDeclaredMethod(method, int.class);
                m.setAccessible(true);
                m.invoke(null, deg);
                return;
            } catch (NoSuchMethodException ignored) {}

            try {
                java.lang.reflect.Method m = c.getDeclaredMethod(method, float.class);
                m.setAccessible(true);
                m.invoke(null, (float) deg);
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable ignored) {}
    }

    private static BlockPos getRendererAnchorPos() {
        try {
            Class<?> c = rendererClass();
            java.lang.reflect.Method m = c.getDeclaredMethod("getAnchorPos");
            m.setAccessible(true);
            Object o = m.invoke(null);
            if (o instanceof BlockPos) return (BlockPos) o;
        } catch (Throwable ignored) {}
        return null;
    }

    private static void setRendererAnchor(BlockPos pos) {
        if (pos == null) return;
        try {
            Class<?> c = rendererClass();
            java.lang.reflect.Method m = c.getDeclaredMethod("setAnchor", BlockPos.class);
            m.setAccessible(true);
            m.invoke(null, pos);
        } catch (Throwable ignored) {}
    }

    private static void setRendererPreviewLocked(boolean locked) {
        try {
            Class<?> c = rendererClass();
            java.lang.reflect.Method m = c.getDeclaredMethod("setPreviewLocked", boolean.class);
            m.setAccessible(true);
            m.invoke(null, locked);
        } catch (Throwable ignored) {
            // renderer may not have previewLocked; that's fine.
        }
    }

    private static Boolean getRendererPreviewLocked() {
        try {
            Class<?> c = rendererClass();
            java.lang.reflect.Method m = c.getDeclaredMethod("isPreviewLocked");
            m.setAccessible(true);
            Object o = m.invoke(null);
            if (o instanceof Boolean) return (Boolean) o;
        } catch (Throwable ignored) {}
        return null;
    }

    /* =========================
     * Build session reflection helpers
     * ========================= */

    private static Class<?> sessionClass() throws ClassNotFoundException {
        return Class.forName(SESSION_CLASS);
    }

        private static void startBuildSessionBestEffort() {
        // Authoritative confirm: freeze the exact ghost preview the player sees.
        try {
            com.voxelbuilder.client.build.BuildPlan plan = com.voxelbuilder.client.render.GhostPreviewDebugRenderer.getPreview();
            net.minecraft.core.BlockPos anchor = com.voxelbuilder.client.render.GhostPreviewDebugRenderer.getAnchorPos();
            if (plan == null || anchor == null) return;

            java.util.List<com.voxelbuilder.client.build.BuildPlan.BlockPos3> frozen =
                    com.voxelbuilder.client.render.GhostPreviewDebugRenderer.snapshotConfirmedBlocks();
            if (frozen == null || frozen.isEmpty()) return;

            int rx = Math.round(com.voxelbuilder.client.render.GhostPreviewDebugRenderer.getPreviewRotationX());
            int ry = Math.round(com.voxelbuilder.client.render.GhostPreviewDebugRenderer.getPreviewRotationY());
            int rz = Math.round(com.voxelbuilder.client.render.GhostPreviewDebugRenderer.getPreviewRotationZ());

            // Freeze into the session (build must use confirmed blocks, not live rotation).
            com.voxelbuilder.client.build.VoxelBuilderSession.confirmFrozen(frozen, anchor, rx, ry, rz);

            // Lock preview edits while confirmed/running
            com.voxelbuilder.client.render.GhostPreviewDebugRenderer.setPreviewLocked(true);
            return;
        } catch (Throwable ignored) {
            // Fall through to legacy reflection-based confirm if needed.
        }

        // Legacy behavior: populate whatever your runner reads (commonly: isConfirmed + plan/anchor/rot)
        try {
            Class<?> r = rendererClass();
            Class<?> s = sessionClass();

            Object plan = null;
            Object anchor = null;

            // Renderer getters
            try {
                java.lang.reflect.Method m = r.getDeclaredMethod("getPreview");
                m.setAccessible(true);
                plan = m.invoke(null);
            } catch (Throwable ignored) {}

            try {
                java.lang.reflect.Method m = r.getDeclaredMethod("getAnchorPos");
                m.setAccessible(true);
                anchor = m.invoke(null);
            } catch (Throwable ignored) {}

            if (plan == null || anchor == null) return;

            // Use our local rx/ry/rz as a fallback
            int rx = VoxelBuilderPreviewHotkeys.rx;
            int ry = VoxelBuilderPreviewHotkeys.ry;
            int rz = VoxelBuilderPreviewHotkeys.rz;

            // Try confirm(plan, anchor, rx, ry, rz)
            if (tryInvokeStatic(s, "confirm",
                    new Class<?>[]{plan.getClass(), anchor.getClass(), int.class, int.class, int.class},
                    new Object[]{plan, anchor, rx, ry, rz})) return;

            // Try confirm(anchor, plan, rx, ry, rz) (older ordering)
            if (tryInvokeStatic(s, "confirm",
                    new Class<?>[]{anchor.getClass(), plan.getClass(), int.class, int.class, int.class},
                    new Object[]{anchor, plan, rx, ry, rz})) return;

            // Try start(plan, anchor, rx, ry, rz)
            if (tryInvokeStatic(s, "start",
                    new Class<?>[]{plan.getClass(), anchor.getClass(), int.class, int.class, int.class},
                    new Object[]{plan, anchor, rx, ry, rz})) return;

            // Fallback setters + setConfirmed(true)
            tryInvokeStaticVoid(s, "setPlan", new Class<?>[]{plan.getClass()}, new Object[]{plan});
            tryInvokeStaticVoid(s, "setAnchor", new Class<?>[]{anchor.getClass()}, new Object[]{anchor});
            tryInvokeStaticVoid(s, "setRotX", new Class<?>[]{int.class}, new Object[]{rx});
            tryInvokeStaticVoid(s, "setRotY", new Class<?>[]{int.class}, new Object[]{ry});
            tryInvokeStaticVoid(s, "setRotZ", new Class<?>[]{int.class}, new Object[]{rz});
            tryInvokeStaticVoid(s, "setConfirmed", new Class<?>[]{boolean.class}, new Object[]{true});

            // Some versions use confirm() no-args
            tryInvokeStaticVoid(s, "confirm", new Class<?>[0], new Object[0]);

        } catch (Throwable ignored) {
            // never crash client from hotkeys
        }
    }


private static void clearBuildSessionBestEffort() {
        try {
            Class<?> s = sessionClass();
            // common: clear()
            tryInvokeStaticVoid(s, "clear", new Class<?>[0], new Object[0]);
            // common: setConfirmed(false)
            tryInvokeStaticVoid(s, "setConfirmed", new Class<?>[]{boolean.class}, new Object[]{false});
        } catch (Throwable ignored) {}
    }

    private static boolean tryInvokeStatic(Class<?> cls, String name, Class<?>[] params, Object[] args) {
        try {
            java.lang.reflect.Method m = cls.getDeclaredMethod(name, params);
            m.setAccessible(true);
            m.invoke(null, args);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void tryInvokeStaticVoid(Class<?> cls, String name, Class<?>[] params, Object[] args) {
        try {
            java.lang.reflect.Method m = cls.getDeclaredMethod(name, params);
            m.setAccessible(true);
            m.invoke(null, args);
        } catch (Throwable ignored) {}
    }
}
