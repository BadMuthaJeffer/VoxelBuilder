package com.voxelbuilder.client.render;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Tiny helper: draws a simple 1x1x1 wireframe box at a block position.
 * Uses 1.21.1-friendly LevelRenderer.renderLineBox signature.
 */
public final class GhostVoxelRenderer {

    private GhostVoxelRenderer() {}

    public static void renderWireBox(PoseStack ps,
                                     MultiBufferSource buffers,
                                     BlockPos pos,
                                     float r, float g, float b, float a) {

        VertexConsumer vc = buffers.getBuffer(RenderType.lines());

        // 1-block AABB. Small inflate to avoid z-fighting with block edges.
        AABB box = new AABB(pos).inflate(0.002D);

        // Signature available in 1.21.1:
        // LevelRenderer.renderLineBox(PoseStack, VertexConsumer, AABB, float,float,float,float)
        LevelRenderer.renderLineBox(ps, vc, box, r, g, b, a);
    }
}
