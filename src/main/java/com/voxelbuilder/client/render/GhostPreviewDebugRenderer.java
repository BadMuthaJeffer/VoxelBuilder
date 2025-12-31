package com.voxelbuilder.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voxelbuilder.client.build.BuildPlan;

import net.minecraft.client.Camera;
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
 * Ghost preview renderer (debug line boxes).
 *
 * LOCKED INTENT:
 * - Preview data (BuildPlan) must NOT be cleared by cancel/reset placement.
 * - Preview should only render once the anchor is locked (right-clicked).
 * - Rotation is render-only (PoseStack) and should NOT rebuild the plan.
 */
public final class GhostPreviewDebugRenderer {

    private static BuildPlan previewPlan = null;

    private static boolean placementArmed = false;
    private static boolean anchorLocked = false;
    private static BlockPos anchorPos = null;

    private static double previewScale = 1.0;

    // Render-only rotation (degrees)
    private static float rotX = 0f;
    private static float rotY = 0f;
    private static float rotZ = 0f;

    // Confirm/lock indicator (used for color)
    private static boolean previewLocked = false;

    // Cached bounds/pivot in min-shifted space (prevents empty gap + enables centered pivot rotation)
    private static boolean boundsValid = false;
    private static int minX = 0, minY = 0, minZ = 0;
    private static int maxX = 0, maxY = 0, maxZ = 0;
    private static double pivotX = 0.0, pivotY = 0.0, pivotZ = 0.0;

    private GhostPreviewDebugRenderer() {}

    // =========================
    // Preview data
    // =========================

    public static void setPreview(BuildPlan plan) {
        previewPlan = plan;
        recacheBounds();
    }

    public static BuildPlan getPreview() {
        return previewPlan;
    }

    public static void setPreviewScale(double scale) {
        if (scale <= 0.0001) scale = 0.0001;
        previewScale = scale;
    }

    public static double getPreviewScale() {
        return previewScale;
    }

    // =========================
    // Confirm/lock (color indicator)
    // =========================

    public static void setPreviewLocked(boolean locked) {
        previewLocked = locked;
    }

    public static boolean isPreviewLocked() {
        return previewLocked;
    }

    // =========================
    // Rotation (render-only)
    // =========================

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

    // =========================
    // Placement state
    // =========================

    public static void armPlacement() {
        placementArmed = true;
        // Do not change previewPlan here.
        // Do not force anchorLocked false here (player may be re-placing).
        anchorLocked = false;
        anchorPos = null;

        // When placing again, treat as "not confirmed"
        previewLocked = false;
    }

    public static boolean isPlacementArmed() {
        return placementArmed;
    }

    public static boolean isAnchorLocked() {
        return anchorLocked && anchorPos != null;
    }

    public static BlockPos getAnchorPos() {
        return anchorPos;
    }

    /**
     * Called by PreviewPlacementHandler when the player right-clicks a block.
     * This locks the anchor and ends placement mode.
     */
    public static void setAnchor(BlockPos pos) {
        if (pos == null) return;
        anchorPos = pos;
        anchorLocked = true;
        placementArmed = false;
    }

    /**
     * Cancels placement/anchor WITHOUT clearing preview data.
     * This is the critical regression fix.
     */
    public static void clearPlacement() {
        placementArmed = false;
        anchorLocked = false;
        anchorPos = null;

        // DO NOT clear previewPlan here.
        // previewPlan is owned by the controller UI and must persist.

        previewLocked = false;
    }

    // =========================
    // Rendering
    // =========================

    /**
     * Render the preview as wireframe boxes.
     * This method is called from VoxelBuilderClientRenderEvents during a world render stage.
     */
    public static void render(PoseStack poseStack) {
        if (poseStack == null) return;

        BuildPlan plan = previewPlan;
        if (plan == null) return;

        // Only render when we have a locked anchor.
        // (Avoid the "moves with player" regression.)
        if (!isAnchorLocked()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.getPosition();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        List<BuildPlan.BlockPos3> blocks = plan.getBlocks();
        if (blocks == null || blocks.isEmpty()) return;

        if (!boundsValid) recacheBounds();

        poseStack.pushPose();

        // Anchor to world position relative to camera
        poseStack.translate(
                anchorPos.getX() - camPos.x,
                anchorPos.getY() - camPos.y,
                anchorPos.getZ() - camPos.z
        );

        // Rotate around CENTER PIVOT (in min-shifted local space)
        poseStack.translate(pivotX * previewScale, pivotY * previewScale, pivotZ * previewScale);

        if (rotX != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(rotX));
        if (rotY != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(rotY));
        if (rotZ != 0f) poseStack.mulPose(Axis.ZP.rotationDegrees(rotZ));

        poseStack.translate(-pivotX * previewScale, -pivotY * previewScale, -pivotZ * previewScale);

        // Color: cyan when unlocked, red when locked/confirmed
        final float r = previewLocked ? 1.0f : 0.0f;
        final float g = previewLocked ? 0.0f : 1.0f;
        final float b = previewLocked ? 0.0f : 1.0f;
        final float a = 1.0f;

        for (BuildPlan.BlockPos3 p : blocks) {
            // Remove empty gap by shifting by min corner before drawing
            double lx = (p.x - minX) * previewScale;
            double ly = (p.y - minY) * previewScale;
            double lz = (p.z - minZ) * previewScale;

            AABB box = new AABB(
                    lx, ly, lz,
                    lx + previewScale, ly + previewScale, lz + previewScale
            );

            LevelRenderer.renderLineBox(
                    poseStack,
                    bufferSource.getBuffer(RenderType.lines()),
                    box,
                    r, g, b, a
            );
        }

        poseStack.popPose();
        bufferSource.endBatch();
    }

    // =========================
    // Bounds cache
    // =========================

    private static void recacheBounds() {
        boundsValid = false;
        minX = minY = minZ = 0;
        maxX = maxY = maxZ = 0;
        pivotX = pivotY = pivotZ = 0.0;

        BuildPlan plan = previewPlan;
        if (plan == null) return;

        List<BuildPlan.BlockPos3> blocks = plan.getBlocks();
        if (blocks == null || blocks.isEmpty()) return;

        int miX = Integer.MAX_VALUE, miY = Integer.MAX_VALUE, miZ = Integer.MAX_VALUE;
        int maX = Integer.MIN_VALUE, maY = Integer.MIN_VALUE, maZ = Integer.MIN_VALUE;

        for (BuildPlan.BlockPos3 p : blocks) {
            if (p.x < miX) miX = p.x;
            if (p.y < miY) miY = p.y;
            if (p.z < miZ) miZ = p.z;

            if (p.x > maX) maX = p.x;
            if (p.y > maY) maY = p.y;
            if (p.z > maZ) maZ = p.z;
        }

        minX = miX; minY = miY; minZ = miZ;
        maxX = maX; maxY = maY; maxZ = maZ;

        int sizeX = (maxX - minX) + 1;
        int sizeY = (maxY - minY) + 1;
        int sizeZ = (maxZ - minZ) + 1;

        pivotX = sizeX / 2.0;
        pivotY = sizeY / 2.0;
        pivotZ = sizeZ / 2.0;

        boundsValid = true;
    }
}
