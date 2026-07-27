package com.voxelbuilder.client.build;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Confirmed build session (client-side handoff).
 *
 * IMPORTANT:
 * - The build MUST place exactly the "confirmed ghost" plan, not a live/rebuilt plan.
 * - Rotation is a render-only transform in GhostPreviewDebugRenderer, so we bake rotation
 *   into the confirmed block list at confirm time.
 *
 * This class stores an immutable snapshot of the confirmed plan:
 * - confirmedBlocks: already-rotated block positions in plan-local coordinates
 * - anchor: world anchor position
 * - selectedBlock: single material (for now)
 */
public final class VoxelBuilderSession {

    private static boolean confirmed = false;

    // Legacy (kept for compatibility; some code still calls confirm(BuildPlan,...))
    private static BuildPlan plan = null;

    private static BlockPos anchor = null;
    private static int rotX = 0, rotY = 0, rotZ = 0;

    // Frozen, authoritative confirmed plan (already includes render rotation)
    private static List<BuildPlan.BlockPos3> confirmedBlocks = null;

    // Single material selection (default stone)
    private static BlockState selectedBlock = Blocks.STONE.defaultBlockState();

    private VoxelBuilderSession() {}

    /**
     * CONFIRM using a frozen snapshot of blocks (authoritative).
     */
    public static void confirmFrozen(List<BuildPlan.BlockPos3> frozenBlocks, BlockPos anchorPos, int rx, int ry, int rz) {
        if (frozenBlocks == null || frozenBlocks.isEmpty() || anchorPos == null) return;

        confirmedBlocks = Collections.unmodifiableList(new ArrayList<>(frozenBlocks));
        anchor = anchorPos.immutable();
        rotX = rx; rotY = ry; rotZ = rz;

        // Keep legacy plan null to discourage rebuild-at-build-time.
        plan = null;

        confirmed = true;
    }

    /**
     * Legacy confirm: stores the plan reference. This is NOT authoritative for building anymore.
     * Prefer confirmFrozen().
     */
    public static void confirm(BuildPlan buildPlan, BlockPos anchorPos, int rx, int ry, int rz) {
        plan = buildPlan;
        anchor = (anchorPos == null ? null : anchorPos.immutable());
        rotX = rx; rotY = ry; rotZ = rz;
        confirmed = (plan != null && anchor != null);
    }

    public static void clear() {
        confirmed = false;
        plan = null;
        anchor = null;
        confirmedBlocks = null;
        rotX = rotY = rotZ = 0;
        // Keep selectedBlock: user choice should persist across sessions.
    }

    public static boolean isConfirmed() {
        return confirmed;
    }

    /** Legacy: may be null in frozen mode. */
    public static BuildPlan getPlan() {
        return plan;
    }

    public static BlockPos getAnchor() {
        return anchor;
    }

    public static int getRotX() { return rotX; }
    public static int getRotY() { return rotY; }
    public static int getRotZ() { return rotZ; }

    /**
     * Authoritative confirmed blocks snapshot (already rotated).
     * Returns null if not confirmed/frozen.
     */
    public static List<BuildPlan.BlockPos3> getConfirmedBlocks() {
        return confirmedBlocks;
    }

    // ===== Block selection =====

    public static void setSelectedBlock(BlockState state) {
        if (state != null) selectedBlock = state;
    }

    public static BlockState getSelectedBlock() {
        return selectedBlock;
    }
}
