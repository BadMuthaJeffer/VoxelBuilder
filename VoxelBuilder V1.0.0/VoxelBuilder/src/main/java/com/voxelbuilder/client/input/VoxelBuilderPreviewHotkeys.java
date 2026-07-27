package com.voxelbuilder.client.input;

import com.voxelbuilder.client.render.GhostPreviewDebugRenderer;

import com.mojang.blaze3d.platform.InputConstants;

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
 * Flow supported:
 * - User presses Place Build (arms placement, closes GUI)
 * - User right-clicks block to anchor
 * - User presses X / Y / Z to rotate preview
 * - User presses R to reset
 */
public final class VoxelBuilderPreviewHotkeys {

    private static final String MODID = "voxelbuilder";

    // Key category is a translation key string in 1.21.x (no KeyMapping.Category type)
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

    // Stored degrees (render-only)
    private static int rx = 0;
    private static int ry = 0;
    private static int rz = 0;

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

            // Only rotate when placement mode is armed and no UI is open
            if (!GhostPreviewDebugRenderer.isPlacementArmed()) return;
            if (mc.screen != null) return;

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
