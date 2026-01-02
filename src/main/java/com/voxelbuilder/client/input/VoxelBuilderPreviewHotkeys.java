package com.voxelbuilder.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.voxelbuilder.client.render.GhostPreviewDebugRenderer;

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
 *  - Place Build (right click) sets anchor (ghost becomes visible and stays fixed)
 *  - Rotate / Nudge while NOT confirmed
 *  - Enter confirms (locks preview -> red)
 *  - Backspace cancels confirm (unlocks -> cyan)
 *
 * Arrow movement:
 * - Arrows = relative to player facing (forward/back/strafe)
 * - SHIFT + arrows = world axes (X/Z)
 * - PageUp/PageDown = Y up/down
 */
public final class VoxelBuilderPreviewHotkeys {

    public static final String MODID = "voxelbuilder";
    private static final String CATEGORY = "key.categories.voxelbuilder";

    // Rotation
    private static final KeyMapping ROT_X = new KeyMapping(
            "key.voxelbuilder.rotate_x",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            CATEGORY
    );

    private static final KeyMapping ROT_Y = new KeyMapping(
            "key.voxelbuilder.rotate_y",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            CATEGORY
    );

    private static final KeyMapping ROT_Z = new KeyMapping(
            "key.voxelbuilder.rotate_z",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            CATEGORY
    );

    private static final KeyMapping RESET = new KeyMapping(
            "key.voxelbuilder.rotate_reset",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY
    );

    // Confirm / Cancel
    private static final KeyMapping CONFIRM = new KeyMapping(
            "key.voxelbuilder.confirm_build",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_ENTER,
            CATEGORY
    );

    private static final KeyMapping CANCEL = new KeyMapping(
            "key.voxelbuilder.cancel_build",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_BACKSPACE,
            CATEGORY
    );

    // Nudge (arrows + vertical)
    private static final KeyMapping NUDGE_UP = new KeyMapping(
            "key.voxelbuilder.nudge_up",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UP,
            CATEGORY
    );

    private static final KeyMapping NUDGE_DOWN = new KeyMapping(
            "key.voxelbuilder.nudge_down",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_DOWN,
            CATEGORY
    );

    private static final KeyMapping NUDGE_LEFT = new KeyMapping(
            "key.voxelbuilder.nudge_left",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT,
            CATEGORY
    );

    private static final KeyMapping NUDGE_RIGHT = new KeyMapping(
            "key.voxelbuilder.nudge_right",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT,
            CATEGORY
    );

    private static final KeyMapping NUDGE_UP_Y = new KeyMapping(
            "key.voxelbuilder.nudge_up_y",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_UP,
            CATEGORY
    );

    private static final KeyMapping NUDGE_DOWN_Y = new KeyMapping(
            "key.voxelbuilder.nudge_down_y",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_DOWN,
            CATEGORY
    );

    // Stored degrees (multiples of 90) for UI/hotkeys
    private static float rx = 0f;
    private static float ry = 0f;
    private static float rz = 0f;

    // Local lock mirrors renderer previewLocked (red)
    private static boolean rotationLocked = false;

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

            // Confirm (locks preview -> red)
            while (CONFIRM.consumeClick()) {
                rotationLocked = true;
                GhostPreviewDebugRenderer.setPreviewLocked(true);

                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            Component.literal("CONFIRMED: preview locked (red). Backspace to unlock."),
                            true
                    );
                }
            }

            // Cancel confirm (unlocks preview -> cyan)
            while (CANCEL.consumeClick()) {
                rotationLocked = false;
                GhostPreviewDebugRenderer.setPreviewLocked(false);

                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            Component.literal("UNLOCKED: preview editable again."),
                            true
                    );
                }
            }

            // If preview is confirmed/locked, do not allow rotate/nudge.
            if (rotationLocked || GhostPreviewDebugRenderer.isPreviewLocked()) return;

            // ===== Rotation =====
            while (ROT_X.consumeClick()) {
                rx = (rx + 90f) % 360f;
                GhostPreviewDebugRenderer.setPreviewRotationX(rx);
            }

            while (ROT_Y.consumeClick()) {
                ry = (ry + 90f) % 360f;
                GhostPreviewDebugRenderer.setPreviewRotationY(ry);
            }

            while (ROT_Z.consumeClick()) {
                rz = (rz + 90f) % 360f;
                GhostPreviewDebugRenderer.setPreviewRotationZ(rz);
            }

            while (RESET.consumeClick()) {
                rx = 0f; ry = 0f; rz = 0f;
                GhostPreviewDebugRenderer.resetPreviewRotation();
            }

            // ===== Movement (anchor nudge) =====
            BlockPos anchor = GhostPreviewDebugRenderer.getAnchorPos();
            if (anchor == null) return;

            long window = mc.getWindow().getWindow();
            boolean shift =
                    InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) ||
                    InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);

            // PageUp/PageDown = Y nudge
            while (NUDGE_UP_Y.consumeClick()) {
                anchor = anchor.offset(0, 1, 0);
                GhostPreviewDebugRenderer.setAnchor(anchor);
            }

            while (NUDGE_DOWN_Y.consumeClick()) {
                anchor = anchor.offset(0, -1, 0);
                GhostPreviewDebugRenderer.setAnchor(anchor);
            }

            // Arrows: world axes (SHIFT) or relative to facing
            while (NUDGE_UP.consumeClick()) {
                int[] d = shift ? new int[]{0, -1} : forwardDelta(mc); // world "up" = -Z
                anchor = anchor.offset(d[0], 0, d[1]);
                GhostPreviewDebugRenderer.setAnchor(anchor);
            }

            while (NUDGE_DOWN.consumeClick()) {
                int[] d = shift ? new int[]{0, -1} : forwardDelta(mc);
                anchor = anchor.offset(-d[0], 0, -d[1]);
                GhostPreviewDebugRenderer.setAnchor(anchor);
            }

            while (NUDGE_LEFT.consumeClick()) {
                int[] d = shift ? new int[]{-1, 0} : leftDelta(mc);
                anchor = anchor.offset(d[0], 0, d[1]);
                GhostPreviewDebugRenderer.setAnchor(anchor);
            }

            while (NUDGE_RIGHT.consumeClick()) {
                int[] d = shift ? new int[]{-1, 0} : leftDelta(mc);
                anchor = anchor.offset(-d[0], 0, -d[1]);
                GhostPreviewDebugRenderer.setAnchor(anchor);
            }
        }

        /**
         * Returns (dx, dz) for "forward" based on player yaw, snapped to 4 dirs.
         * Minecraft yaw:
         *  0 = south (+Z)
         *  90 = west (-X)
         * -90 = east (+X)
         * 180/-180 = north (-Z)
         */
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
            // 0=S -> left is +X
            if (dir == 0) return new int[]{1, 0};
            // 1=W -> left is +Z
            if (dir == 1) return new int[]{0, 1};
            // 2=N -> left is -X
            if (dir == 2) return new int[]{-1, 0};
            // 3=E -> left is -Z
            return new int[]{0, -1};
        }

        private static int yawToCardinal(float yaw) {
            float y = yaw % 360f;
            if (y < 0) y += 360f;

            // snap to nearest 90
            int snapped = Math.round(y / 90f) & 3;

            // 0=south,1=west,2=north,3=east
            return snapped;
        }
    }
}
