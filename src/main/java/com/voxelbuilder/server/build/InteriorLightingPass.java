package com.voxelbuilder.server.build;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Server-side interior lighting pass.
 *
 * Option 3: 3D lattice with surface-first placement and floating fallback.
 */
public final class InteriorLightingPass {

    private InteriorLightingPass() {}

    public static final class Settings {
        public final boolean enabled;
        public final int spacingXZ;
        public final int spacingY;
        public final int searchRadius;
        public final int blocksPerTick;
        public final ResourceLocation blockId;

        public Settings(boolean enabled,
                        int spacingXZ,
                        int spacingY,
                        int searchRadius,
                        int blocksPerTick,
                        ResourceLocation blockId) {
            this.enabled = enabled;
            this.spacingXZ = Math.max(1, spacingXZ);
            this.spacingY = Math.max(1, spacingY);
            this.searchRadius = Math.max(0, searchRadius);
            this.blocksPerTick = Math.max(1, blocksPerTick);
            this.blockId = Objects.requireNonNull(blockId, "blockId");
        }
    }

    /**
     * Tick cursor/state. Keep one per build job.
     */
    public static final class State {
        private boolean started = false;
        private boolean finished = false;

        private BoundingBox inner;
        private int x;
        private int y;
        private int z;

        public boolean isFinished() {
            return finished;
        }

        private void start(BoundingBox buildBounds) {
            // Shrink by 1 on all sides to bias toward interior.
            int minX = buildBounds.minX() + 1;
            int minY = buildBounds.minY() + 1;
            int minZ = buildBounds.minZ() + 1;
            int maxX = buildBounds.maxX() - 1;
            int maxY = buildBounds.maxY() - 1;
            int maxZ = buildBounds.maxZ() - 1;

            if (minX > maxX || minY > maxY || minZ > maxZ) {
                this.finished = true;
                this.started = true;
                this.inner = buildBounds;
                return;
            }

            this.inner = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
            this.x = inner.minX();
            this.y = inner.minY();
            this.z = inner.minZ();
            this.started = true;
            this.finished = false;
        }
    }

    /**
     * Runs up to settings.blocksPerTick placements (capped by caller global budget).
     *
     * @param canTouchPos  MUST enforce world bounds + chunk-loaded policy + any other server safety rules.
     * @param surfaceBlock If non-null, only this block type is eligible for surface replacement.
     */
    public static int tick(ServerLevel level,
                           BoundingBox buildBounds,
                           Settings settings,
                           State state,
                           Predicate<BlockPos> canTouchPos,
                           Block surfaceBlock) {
        if (!settings.enabled) return 0;
        if (state == null || state.isFinished()) return 0;
        if (level == null || buildBounds == null) return 0;

        if (!state.started) {
            state.start(buildBounds);
            if (state.isFinished()) return 0;
        }

        BlockState lightState = resolveLightState(settings.blockId);
        if (lightState == null) {
            state.finished = true;
            return 0;
        }

        int placed = 0;

        while (placed < settings.blocksPerTick && !state.finished) {
            BlockPos lattice = new BlockPos(state.x, state.y, state.z);

            // We only want to light enclosed interior air, not empty space inside the AABB but outside the model.
            // If the lattice point is not enclosed, we will still try to find a nearby enclosed pocket within
            // searchRadius (handled below). Surface placement is additionally guarded by enclosed-air checks.
            boolean latticeEnclosed = isEnclosedAir(level, state.inner, lattice, canTouchPos);

            boolean didPlace = false;

            // 1) Replace a nearby interior-facing surface block (wall/ceiling/floor)
            BlockPos surface = findInteriorFacingSurface(level, state.inner, lattice, settings.searchRadius, canTouchPos, surfaceBlock);
            if (surface != null) {
                didPlace = trySetBlock(level, surface, lightState, canTouchPos, true);
            }

            // 2) Fallback: place glowstone floating at lattice point (prefer air/replaceable)
            if (!didPlace) {
                if (latticeEnclosed) {
                    didPlace = trySetBlock(level, lattice, lightState, canTouchPos, false);
                }

                // Extra fallback: if lattice point is blocked, find a nearby enclosed air spot.
                if (!didPlace && settings.searchRadius > 0) {
                    BlockPos nearbyAir = findNearbyEnclosedAir(level, state.inner, lattice, settings.searchRadius, canTouchPos);
                    if (nearbyAir != null) {
                        didPlace = trySetBlock(level, nearbyAir, lightState, canTouchPos, false);
                    }
                }
            }

            if (didPlace) placed++;

            advanceCursor(state, settings);
            if (state.y > state.inner.maxY()) {
                state.finished = true;
            }
        }

        return placed;
    }

    private static void advanceCursor(State state, Settings s) {
        state.x += s.spacingXZ;
        if (state.x > state.inner.maxX()) {
            state.x = state.inner.minX();
            state.z += s.spacingXZ;

            if (state.z > state.inner.maxZ()) {
                state.z = state.inner.minZ();
                state.y += s.spacingY;
            }
        }
    }

    private static BlockState resolveLightState(ResourceLocation id) {
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block == null) return null;
        BlockState st = block.defaultBlockState();
        if (st.isAir()) return null;
        return st;
    }

    private static BlockPos findInteriorFacingSurface(ServerLevel level,
                                                     BoundingBox inner,
                                                     BlockPos center,
                                                     int radius,
                                                     Predicate<BlockPos> canTouchPos,
                                                     Block surfaceBlock) {
        if (radius <= 0) return null;

        // Priority: closer first by manhattan distance.
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != r) continue;

                        BlockPos pos = center.offset(dx, dy, dz);
                        if (!inner.isInside(pos)) continue;
                        if (!canTouchPos.test(pos)) continue;

                        BlockState st = level.getBlockState(pos);
                        if (st.isAir()) continue;
                        if (!st.getFluidState().isEmpty()) continue;

                        if (surfaceBlock != null && st.getBlock() != surfaceBlock) continue;

                        // Must border enclosed interior air.
                        if (bordersEnclosedInteriorAir(level, inner, pos, canTouchPos)) {
                            return pos;
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * True if this solid block borders *enclosed* interior air.
     *
     * This prevents placing glowstone on exterior surfaces where the neighboring air is actually outside the model,
     * even if it happens to be within the model's AABB bounds.
     */
    private static boolean bordersEnclosedInteriorAir(ServerLevel level,
                                                     BoundingBox inner,
                                                     BlockPos pos,
                                                     Predicate<BlockPos> canTouchPos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (!inner.isInside(n)) continue;
            if (!canTouchPos.test(n)) continue;
            if (level.getBlockState(n).isAir() && isEnclosedAir(level, inner, n, canTouchPos)) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos findNearbyEnclosedAir(ServerLevel level,
                                                  BoundingBox inner,
                                                  BlockPos center,
                                                  int radius,
                                                  Predicate<BlockPos> canTouchPos) {
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != r) continue;
                        BlockPos pos = center.offset(dx, dy, dz);
                        if (!inner.isInside(pos)) continue;
                        if (!canTouchPos.test(pos)) continue;

                        BlockState st = level.getBlockState(pos);
                        if (!st.getFluidState().isEmpty()) continue;

                        if ((st.isAir() || st.canBeReplaced()) && isEnclosedAir(level, inner, pos, canTouchPos)) {
                            return pos;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Cheap "inside vs outside" test for air pockets within the AABB.
     *
     * A lattice point is considered interior if, in all 6 axis directions, you hit a solid block
     * before reaching the inner bounds edge.
     *
     * This intentionally biases toward *not* placing lights in ambiguous areas, rather than
     * spamming glowstone outside the model.
     */
    private static boolean isEnclosedAir(ServerLevel level,
                                         BoundingBox inner,
                                         BlockPos pos,
                                         Predicate<BlockPos> canTouchPos) {
        // Must be within inner bounds.
        if (!inner.isInside(pos)) return false;
        if (!canTouchPos.test(pos)) return false;

        BlockState st = level.getBlockState(pos);
        if (!st.getFluidState().isEmpty()) return false;
        if (!(st.isAir() || st.canBeReplaced())) return false;

        // For each direction, we need to encounter a solid block before we leave bounds.
        for (Direction d : Direction.values()) {
            if (!rayHitsSolidBeforeEdge(level, inner, pos, d, canTouchPos)) {
                return false;
            }
        }
        return true;
    }

    private static boolean rayHitsSolidBeforeEdge(ServerLevel level,
                                                  BoundingBox inner,
                                                  BlockPos start,
                                                  Direction dir,
                                                  Predicate<BlockPos> canTouchPos) {
        BlockPos.MutableBlockPos cur = start.mutable();
        while (true) {
            cur.move(dir);
            if (!inner.isInside(cur)) {
                return false; // open to outside (relative to the model bounds)
            }
            if (!canTouchPos.test(cur)) {
                return false; // treat unloaded / invalid as open (do not place)
            }
            BlockState s = level.getBlockState(cur);
            if (!s.getFluidState().isEmpty()) {
                // Fluids are not a wall for our purposes (also: don't light here)
                continue;
            }
            if (!s.isAir()) {
                return true;
            }
        }
    }

    private static boolean trySetBlock(ServerLevel level,
                                      BlockPos pos,
                                      BlockState lightState,
                                      Predicate<BlockPos> canTouchPos,
                                      boolean allowReplaceSolid) {
        if (!canTouchPos.test(pos)) return false;

        BlockState existing = level.getBlockState(pos);
        if (existing == lightState) return false;
        if (!existing.getFluidState().isEmpty()) return false;

        if (!allowReplaceSolid) {
            // Floating fallback: prefer only air/replaceable.
            if (!(existing.isAir() || existing.canBeReplaced())) {
                return false;
            }
        }

        // 3 = notify neighbors + send to clients (equivalent to UPDATE_ALL usage in your build loop).
        return level.setBlock(pos, lightState, 3);
    }
}
