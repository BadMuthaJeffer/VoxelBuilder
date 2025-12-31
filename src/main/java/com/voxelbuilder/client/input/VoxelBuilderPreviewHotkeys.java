package com.voxelbuilder.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.voxelbuilder.client.render.GhostPreviewDebugRenderer;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import org.lwjgl.glfw.GLFW;

/**
 * Preview hotkeys (client-only)
 *
 * IMPORTANT:
 * - Confirm/Cancel are intentionally DEFAULT UNBOUND so they MUST appear in Controls.
 * - Confirm sets renderer locked state (ghost turns red).
 * - Cancel clears renderer locked state (ghost turns cyan) and resets rotations.
 */
@EventBusSubscriber(modid = "voxelbuilder", value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class VoxelBuilderPreviewHotkeys {

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

    // DEFAULT UNBOUND so it MUST show in Controls and you can choose Enter yourself.
    private static final KeyMapping CONFIRM = new KeyMapping(
            "key.voxelbuilder.confirm_build",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY
    );

    // DEFAULT UNBOUND so it MUST show in Controls and you can choose Backspace yourself.
    private static final KeyMapping CANCEL = new KeyMapping(
            "key.voxelbuilder.cancel_build",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY
    );

    private static int rx = 0;
    private static int ry = 0;
    private static int rz = 0;

    private static boolean rotationLocked = false;

    private VoxelBuilderPreviewHotkeys() {}

    /* =========================
     * Key registration
     * ========================= */

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ROT_X);
        event.register(ROT_Y);
        event.register(ROT_Z);
        event.register(RESET);
        event.register(CONFIRM);
        event.register(CANCEL);
    }

    /* =========================
     * Tick handling
     * ========================= */

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        // Only when no GUI is open
        if (mc.screen != null) return;

        // Confirm: lock + turn ghost RED
        while (CONFIRM.consumeClick()) {
            rotationLocked = true;
            GhostPreviewDebugRenderer.setPreviewLocked(true);

            if (mc.player != null) {
                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("CONFIRMED: preview locked (red)."),
                        true
                );
            }
        }

        // Cancel: unlock + reset + turn ghost CYAN
        while (CANCEL.consumeClick()) {
            rotationLocked = false;

            rx = 0; ry = 0; rz = 0;
            GhostPreviewDebugRenderer.resetPreviewRotation();
            GhostPreviewDebugRenderer.setPreviewLocked(false);

            if (mc.player != null) {
                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("CANCELLED: preview unlocked."),
                        true
                );
            }
        }

        // If locked: eat rotation keys so nothing changes
        if (rotationLocked) {
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
