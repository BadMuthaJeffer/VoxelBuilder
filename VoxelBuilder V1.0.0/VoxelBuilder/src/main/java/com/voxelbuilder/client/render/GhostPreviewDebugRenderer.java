package com.voxelbuilder.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import com.voxelbuilder.client.build.BuildPlan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.mojang.math.Axis;

import java.util.List;

/**
 * Ghost preview renderer (wireframe cubes).
 *
 * Render-only rotation and render-only anchoring offset.
 * Does NOT mutate build plan or world.
 *
 * Pivot mode: CENTER of model bounds (in min-shifted space).
 */
public final class GhostPreviewDebugRenderer {

    private static BuildPlan previewPlan = null;

    private static boolean placementArmed = false;
    private static BlockPos anchorPos = null;

    // Render-only rotation (degrees)
    private static float rotX = 0f;
    private static float rotY = 0f;
    private static float rotZ = 0f;

    // Cached bounds / min-corner so preview starts at the anchor block (no empty gap)
    private static int minPX = 0;
    private static int minPY = 0;
    private static int minPZ = 0;

    private static int maxPX = 0;
    private static int maxPY = 0;
    private static int maxPZ = 0;

    // Cached pivot in "min-shifted" local space
    private static double pivotX = 0.0;
    private static double pivotY = 0.0;
    private static double pivotZ = 0.0;

    private static boolean hasBounds = false;

    private GhostPreviewDebugRenderer() {}

    /* =========================
     * Preview data
     * ========================= */

    public static void setPreview(BuildPlan plan) {
        previewPlan = plan;
        cacheBoundsAndPivotFromPlan();
    }

    public static BuildPlan getPreview() {
        return previewPlan;
    }

    private static void cacheBoundsAndPivotFromPlan() {
        hasBounds = false;
        minPX = minPY = minPZ = 0;
        maxPX = maxPY = maxPZ = 0;
        pivotX = pivotY = pivotZ = 0.0;

        if (previewPlan == null) return;
        List<BuildPlan.BlockPos3> blocks = previewPlan.getBlocks();
        if (blocks == null || blocks.isEmpty()) return;

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;

        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BuildPlan.BlockPos3 p : blocks) {
            // BlockPos3 uses public fields (p.x / p.y / p.z)
            if (p.x < minX) minX = p.x;
            if (p.y < minY) minY = p.y;
            if (p.z < minZ) minZ = p.z;

            if (p.x > maxX) maxX = p.x;
            if (p.y > maxY) maxY = p.y;
            if (p.z > maxZ) maxZ = p.z;
        }

        minPX = minX; minPY = minY; minPZ = minZ;
        maxPX = maxX; maxPY = maxY; maxPZ = maxZ;

        // Size in blocks
        int sizeX = (maxPX - minPX) + 1;
        int sizeY = (maxPY - minPY) + 1;
        int sizeZ = (maxPZ - minPZ) + 1;

        // Pivot at center of bounds in min-shifted space.
        // Using size/2 keeps pivot centered between blocks for even sizes.
        pivotX = sizeX / 2.0;
        pivotY = sizeY / 2.0;
        pivotZ = sizeZ / 2.0;

        hasBounds = true;
    }

    /* =========================
     * Placement state
     * ========================= */

    public static void armPlacement() {
        placementArmed = true;
    }

    public static void disarmPlacement() {
        placementArmed = false;
    }

    public static boolean isPlacementArmed() {
        return placementArmed;
    }

    public static void setAnchor(BlockPos pos) {
        anchorPos = pos;
    }

    public static BlockPos getAnchor() {
        return anchorPos;
    }

    public static void clearAnchor() {
        anchorPos = null;
    }

    /* =========================
     * Rotation setters (HOTKEYS)
     * ========================= */

    public static void setPreviewRotationX(float deg) { rotX = deg; }
    public static void setPreviewRotationY(float deg) { rotY = deg; }
    public static void setPreviewRotationZ(float deg) { rotZ = deg; }

    public static float getPreviewRotationX() { return rotX; }
    public static float getPreviewRotationY() { return rotY; }
    public static float getPreviewRotationZ() { return rotZ; }

    public static void resetPreviewRotation() {
        rotX = 0f;
        rotY = 0f;
        rotZ = 0f;
    }

    /* =========================
     * Render entrypoints
     * ========================= */

    public static void render(PoseStack poseStack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        render(poseStack, bufferSource, cam.x, cam.y, cam.z);
        // DO NOT endBatch() here.
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ) {
        if (!placementArmed) return;
        if (previewPlan == null) return;

        List<BuildPlan.BlockPos3> blocks = previewPlan.getBlocks();
        if (blocks == null || blocks.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;

        BlockPos basePos = anchorPos;

        // If no anchor yet, show floating in front of player
        if (basePos == null) {
            Vec3 eye = mc.player.getEyePosition(1.0f);
            Vec3 look = mc.player.getLookAngle();
            basePos = BlockPos.containing(
                    eye.x + look.x * 4,
                    eye.y + look.y * 4,
                    eye.z + look.z * 4
            );
        }

        // Ensure bounds/pivot cache exists
        if (!hasBounds) cacheBoundsAndPivotFromPlan();

        poseStack.pushPose();

        // Move preview into world space
        poseStack.translate(
                basePos.getX() - camX,
                basePos.getY() - camY,
                basePos.getZ() - camZ
        );

        // === CENTER PIVOT ROTATION ===
        // Work in min-shifted local space:
        //  1) translate to pivot
        //  2) rotate
        //  3) translate back
        poseStack.translate(pivotX, pivotY, pivotZ);

        if (rotX != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(rotX));
        if (rotY != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(rotY));
        if (rotZ != 0f) poseStack.mulPose(Axis.ZP.rotationDegrees(rotZ));

        poseStack.translate(-pivotX, -pivotY, -pivotZ);

        // Draw each voxel as a wireframe cube, snapped to min corner at the anchor
        for (BuildPlan.BlockPos3 p : blocks) {
            double x = (p.x - minPX);
            double y = (p.y - minPY);
            double z = (p.z - minPZ);

            AABB box = new AABB(x, y, z, x + 1.0, y + 1.0, z + 1.0);

            LevelRenderer.renderLineBox(
                    poseStack,
                    bufferSource.getBuffer(RenderType.lines()),
                    box,
                    0.0f, 1.0f, 1.0f, 1.0f
            );
        }

        poseStack.popPose();
    }
}
