package com.voxelbuilder.client.input;

import com.voxelbuilder.client.render.GhostPreviewDebugRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Handles build anchor placement.
 *
 * Option A architecture:
 * - This class ONLY places the anchor
 * - It does NOT open screens
 * - Confirm / Cancel lives in VoxelBuilderControllerScreen
 */
public final class PreviewPlacementHandler {

    private static boolean wasUseDown = false;

    private PreviewPlacementHandler() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        if (!GhostPreviewDebugRenderer.isPlacementArmed()) {
            wasUseDown = false;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        boolean isUseDown = mc.options.keyUse.isDown();

        // Trigger only on NEW right-click
        if (isUseDown && !wasUseDown) {

            LocalPlayer player = mc.player;
            Level level = mc.level;

            Vec3 start = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            Vec3 end = start.add(look.scale(6.0));

            BlockHitResult hit = level.clip(new ClipContext(
                    start,
                    end,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    player
            ));

            if (hit.getType() == HitResult.Type.BLOCK) {
                // Anchor is model (0,0,0) in world space
                BlockPos anchorPos = hit.getBlockPos().above();
                GhostPreviewDebugRenderer.setAnchor(anchorPos);
            }
        }

        wasUseDown = isUseDown;
    }
}
