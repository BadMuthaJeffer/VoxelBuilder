package com.voxelbuilder.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.voxelbuilder.client.build.VoxelBuilderSession;
import com.voxelbuilder.client.render.GhostPreviewDebugRenderer;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import org.lwjgl.glfw.GLFW;

/**
 * Hotkeys for preview rotation while the ghost is visible (screen closed).
 *
 * X / Y / Z : rotate +90 around axis
 * R         : reset rotations to 0
 * ENTER     : confirm (locks rotation so it cannot be changed accidentally)
 * BACKSPACE : cancel (clears confirmed state + unlocks rotation + resets rotations)
 */
public final class VoxelBuilderPreviewHotkeys {

    private static final String MODID = "voxelbuilder";
    private static final String CATEGORY = "key.categories.voxelbuilder";

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

    // Stored degrees (render-only)
    private static int rx = 0;
    private static int ry = 0;
    private static int rz = 0;

    // When true: rotation hotkeys are ignored (preview is "locked" after confirm)
    private static boolean rotationLocked = false;

    private VoxelBuilderPreviewHotkeys() {}

    /* =========================
     * Register keybinds (MOD BUS)
     * ========================= */

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class ModBusHandlers {

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(ROT_X);
            event.register(ROT_Y);
            event.register(ROT_Z);
            event.register(RESET);
            event.register(CONFIRM);
            event.register(CANCEL);
        }
    }

    /* =========================
     * Handle presses (GAME BUS)
     * ========================= */

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.GAME, value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class GameBusHandlers {

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;

            // Only when no GUI is open
            if (mc.screen != null) return;

            // Confirm (locks rotation)
            while (CONFIRM.consumeClick()) {
                // You already have a working session start elsewhere.
                // This file's job is: lock rotation after confirm so the ghost can't be changed accidentally.
                rotationLocked = true;

                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("Confirmed. Rotation locked (Backspace to cancel)."),
                            true
                    );
                }
            }

            // Cancel (unlocks + resets)
            while (CANCEL.consumeClick()) {
                rotationLocked = false;

                rx = 0; ry = 0; rz = 0;
                GhostPreviewDebugRenderer.resetPreviewRotation();

                // Clear builder session if you’re using it
                try {
                    VoxelBuilderSession.clear();
                } catch (Throwable ignored) {}

                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("Cancelled. Rotation unlocked."),
                            true
                    );
                }
            }

            // If confirmed/locked: ignore rotation keys completely
            if (rotationLocked) {
                // Still consume clicks so keys don't “queue up”
                while (ROT_X.consumeClick()) {}
                while (ROT_Y.consumeClick()) {}
                while (ROT_Z.consumeClick()) {}
                while (RESET.consumeClick()) {}
                return;
            }

            // Rotation keys (unlocked)
            while (ROT_X.consumeClick()) {
                rx = (rx + 90) % 360;
                GhostPreviewDebugRenderer.setPreviewRotationX(rx);
            }

            while (ROT_Y.consumeClick()) {
                ry = (ry + 90) % 360;
                GhostPreviewDebugRenderer.setPreviewRotationY(ry);
            }

            while (ROT_Z.consumeClick()) {
                rz = (rz + 90) % 360;
                GhostPreviewDebugRenderer.setPreviewRotationZ(rz);
            }

            while (RESET.consumeClick()) {
                rx = 0; ry = 0; rz = 0;
                GhostPreviewDebugRenderer.resetPreviewRotation();
            }
        }
    }
}
