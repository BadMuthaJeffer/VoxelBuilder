package com.voxelbuilder.server.build;

import com.voxelbuilder.VoxelBuilderServerConfig;
import com.voxelbuilder.network.payload.BuildCompleteS2C;
import com.voxelbuilder.network.payload.BuildProgressS2C;
import com.voxelbuilder.network.payload.SubmitBuildPlanC2S;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class ServerBuildManager {

    private ServerBuildManager() {}

    // PlanId -> ActiveBuild
    private static final Map<UUID, ActiveBuild> ACTIVE_BY_PLAN = new ConcurrentHashMap<>();

    // Progress packet cadence (in ticks)
    private static final int PROGRESS_EVERY_TICKS = 10;

    /**
     * Snapshot for admin commands (status).
     */
    public record JobSnapshot(
            UUID planId,
            UUID playerId,
            String playerName,
            ResourceLocation dimensionId,
            BlockPos anchor,
            String phase,
            int placed,
            int skipped,
            int total,
            int index,
            boolean cancelled
    ) {}

    /**
     * Attempts to submit a job.
     *
     * @return null if accepted, otherwise a short rejection reason.
     */
    public static String trySubmit(ServerPlayer player, SubmitBuildPlanC2S payload) {
        if (player == null || payload == null) return "Invalid request.";
        UUID planId = payload.planId();
        if (planId == null) return "Missing plan id.";
        if (payload.anchor() == null) return "Missing anchor.";
        if (payload.blockId() == null) return "Missing block.";

        // Already active?
        if (ACTIVE_BY_PLAN.containsKey(planId)) {
            return "Plan already active.";
        }

        // Global active job cap
        int maxGlobal = Math.max(1, VoxelBuilderServerConfig.maxActiveJobsGlobal);
        if (ACTIVE_BY_PLAN.size() >= maxGlobal) {
            return "Server is busy (too many active builds).";
        }

        // Per-player active job cap
        int maxPerPlayer = Math.max(1, VoxelBuilderServerConfig.maxActiveJobsPerPlayer);
        int existingForPlayer = countActiveForPlayer(player.getUUID());
        if (existingForPlayer >= maxPerPlayer) {
            return "You already have an active build.";
        }

        // Arrays
        int[] dx = payload.dx();
        int[] dy = payload.dy();
        int[] dz = payload.dz();
        if (dx == null || dy == null || dz == null) return "Plan arrays missing.";
        int total = dx.length;
        if (total <= 0) return "Plan is empty.";
        if (dy.length != total || dz.length != total) return "Plan arrays length mismatch.";

        // Extent caps
        int maxXZ = Math.max(1, VoxelBuilderServerConfig.maxExtentXZ);
        int maxY = Math.max(1, VoxelBuilderServerConfig.maxExtentY);
        for (int i = 0; i < total; i++) {
            if (Math.abs(dx[i]) > maxXZ || Math.abs(dz[i]) > maxXZ || Math.abs(dy[i]) > maxY) {
                return "Plan too large (extent cap).";
            }
        }

        // Resolve block to place
        ResourceLocation blockId = payload.blockId();
        Block block = BuiltInRegistries.BLOCK.get(blockId);
        if (block == null) return "Unknown block.";
        BlockState state = block.defaultBlockState();
        if (state.isAir()) return "Block cannot be air.";

        // Anchor distance gate
        int maxDist = Math.max(0, VoxelBuilderServerConfig.maxDistanceFromAnchor);
        if (maxDist > 0) {
            double distSq = player.position().distanceToSqr(Vec3.atCenterOf(payload.anchor()));
            if (distSq > (double) maxDist * (double) maxDist) {
                return "Too far from anchor.";
            }
        }

        // Create build
        ServerLevel level = player.serverLevel();
        ResourceLocation dimensionId = level.dimension().location();

        ActiveBuild build = new ActiveBuild(
                planId,
                player.getUUID(),
                player,
                level,
                dimensionId,
                payload.anchor(),
                state,
                dx,
                dy,
                dz,
                total
        );

        ACTIVE_BY_PLAN.put(planId, build);

        // Optional immediate first progress packet (keeps UI responsive)
        PacketDistributor.sendToPlayer(player, new BuildProgressS2C(planId, 0, 0, total));

        return null;
    }

    /**
     * Cancel a build by plan id (owner only).
     *
     * @return true if cancellation applied, false if no such build or not owned by player.
     */
    public static boolean cancel(ServerPlayer requester, UUID planId) {
        if (requester == null || planId == null) return false;
        ActiveBuild build = ACTIVE_BY_PLAN.get(planId);
        if (build == null) return false;
        if (!requester.getUUID().equals(build.playerId)) return false;

        build.cancelled = true;
        return true;
    }

    /**
     * OP/admin cancel: cancel all builds belonging to target player.
     *
     * @return number of builds cancelled/removed.
     */
    public static int cancelAllForPlayer(MinecraftServer server, UUID targetPlayerId) {
        if (server == null || targetPlayerId == null) return 0;

        List<UUID> toCancel = new ArrayList<>();
        for (Map.Entry<UUID, ActiveBuild> e : ACTIVE_BY_PLAN.entrySet()) {
            if (targetPlayerId.equals(e.getValue().playerId)) {
                toCancel.add(e.getKey());
            }
        }

        int cancelled = 0;
        for (UUID planId : toCancel) {
            ActiveBuild build = ACTIVE_BY_PLAN.get(planId);
            if (build == null) continue;

            build.cancelled = true;

            // If owner is online, finalize immediately so client state clears now.
            ServerPlayer owner = server.getPlayerList().getPlayer(build.playerId);
            if (owner != null && owner.connection != null) {
                PacketDistributor.sendToPlayer(owner, new BuildProgressS2C(build.planId, build.placed, build.skipped, build.total));
                PacketDistributor.sendToPlayer(owner, new BuildCompleteS2C(build.planId, build.placed, build.skipped, build.total, true));
                ACTIVE_BY_PLAN.remove(planId);
            }

            cancelled++;
        }

        return cancelled;
    }

    public static int getActiveCount() {
        return ACTIVE_BY_PLAN.size();
    }

    public static List<JobSnapshot> getSnapshots(MinecraftServer server) {
        List<JobSnapshot> out = new ArrayList<>();
        if (server == null) return out;

        for (ActiveBuild b : ACTIVE_BY_PLAN.values()) {
            ServerPlayer p = server.getPlayerList().getPlayer(b.playerId);
            String name = (p != null) ? p.getGameProfile().getName() : b.playerId.toString();

            out.add(new JobSnapshot(
                    b.planId,
                    b.playerId,
                    name,
                    b.dimensionId,
                    b.anchor,
                    b.phase.name(),
                    b.placed,
                    b.skipped,
                    b.total,
                    b.index,
                    b.cancelled
            ));
        }
        return out;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null) return;

        int globalBudget = Math.max(1, VoxelBuilderServerConfig.globalBlocksPerTick);
        int perJobBudget = Math.max(1, VoxelBuilderServerConfig.perJobBlocksPerTick);

        boolean lightingEnabled = VoxelBuilderServerConfig.lightingEnabled;
        int lightingSpacingXZ = Math.max(1, VoxelBuilderServerConfig.lightingSpacingXZ);
        int lightingSpacingY = Math.max(1, VoxelBuilderServerConfig.lightingSpacingY);
        int lightingSearchRadius = Math.max(0, VoxelBuilderServerConfig.lightingSearchRadius);
        int lightingBlocksPerTick = Math.max(1, VoxelBuilderServerConfig.lightingBlocksPerTick);
        ResourceLocation lightingBlockId = (VoxelBuilderServerConfig.lightingBlockId != null)
                ? VoxelBuilderServerConfig.lightingBlockId
                : ResourceLocation.tryParse("minecraft:glowstone");

        boolean cancelOutOfRange = VoxelBuilderServerConfig.cancelWhenOutOfRange;
        boolean cancelOnDimChange = VoxelBuilderServerConfig.cancelOnDimensionChange;
        boolean skipUnloaded = VoxelBuilderServerConfig.skipUnloadedChunks;

        int maxDist = Math.max(0, VoxelBuilderServerConfig.maxDistanceFromAnchor);
        double maxDistSq = (maxDist <= 0) ? -1.0 : ((double) maxDist * (double) maxDist);

        Iterator<Map.Entry<UUID, ActiveBuild>> it = ACTIVE_BY_PLAN.entrySet().iterator();
        while (it.hasNext()) {
            if (globalBudget <= 0) break;

            ActiveBuild build = it.next().getValue();

            // Player disconnected? Drop silently.
            if (build.player == null || build.player.connection == null) {
                it.remove();
                continue;
            }

            ServerPlayer player = build.player;

            // Keep level ref current
            ServerLevel level = player.serverLevel();
            build.level = level;

            // Common safety predicate for lighting (reuses the same policy flags as build placement).
            final boolean skipUnloadedFinal = skipUnloaded;
            Predicate<BlockPos> canTouchPos = pos -> {
                if (!level.isInWorldBounds(pos)) return false;
                if (skipUnloadedFinal && !level.hasChunkAt(pos)) return false;
                return true;
            };

            // Dimension lock
            if (cancelOnDimChange) {
                ResourceLocation nowDim = level.dimension().location();
                if (!nowDim.equals(build.dimensionId)) {
                    build.cancelled = true;
                }
            }

            // Range lock
            if (!build.cancelled && cancelOutOfRange && maxDistSq > 0) {
                double distSq = player.position().distanceToSqr(Vec3.atCenterOf(build.anchor));
                if (distSq > maxDistSq) {
                    build.cancelled = true;
                }
            }

            int budget = Math.min(perJobBudget, globalBudget);
            int placedThisTick = 0;

            if (build.phase == BuildPhase.BUILDING) {
                while (!build.cancelled && budget > 0 && build.index < build.total) {
                    int i = build.index++;

                    BlockPos pos = build.anchor.offset(build.dx[i], build.dy[i], build.dz[i]);

                    // World bounds check
                    if (!level.isInWorldBounds(pos)) {
                        build.skipped++;
                        budget--;
                        continue;
                    }

                    // Chunk loaded?
                    if (skipUnloaded && !level.hasChunkAt(pos)) {
                        build.skipped++;
                        budget--;
                        continue;
                    }

                    BlockState existing = level.getBlockState(pos);
                    if (!existing.canBeReplaced()) {
                        build.skipped++;
                        budget--;
                        continue;
                    }

                    boolean ok = level.setBlock(pos, build.state, Block.UPDATE_ALL);
                    if (ok) build.placed++;
                    else build.skipped++;

                    placedThisTick++;
                    budget--;
                }

                // Transition to lighting phase once the build placement completes.
                if (!build.cancelled && build.index >= build.total && lightingEnabled) {
                    build.phase = BuildPhase.LIGHTING;
                }
            }

            if (!build.cancelled && build.phase == BuildPhase.LIGHTING && lightingEnabled) {
                // Lighting gets its own per-job budget, still capped by remaining global budget.
                int remainingGlobal = Math.max(0, globalBudget - placedThisTick);
                int lightingBudget = Math.min(lightingBlocksPerTick, remainingGlobal);
                if (lightingBudget > 0) {
                    InteriorLightingPass.Settings settings = new InteriorLightingPass.Settings(
                            true,
                            lightingSpacingXZ,
                            lightingSpacingY,
                            lightingSearchRadius,
                            lightingBudget,
                            lightingBlockId
                    );

                    int litThisTick = InteriorLightingPass.tick(
                            level,
                            build.bounds,
                            settings,
                            build.lightingState,
                            canTouchPos,
                            build.state.getBlock()
                    );

                    placedThisTick += litThisTick;
                }
            }

            globalBudget -= placedThisTick;

            build.ticksSinceLastProgress++;

            boolean finished = (build.phase == BuildPhase.BUILDING && build.index >= build.total && !lightingEnabled)
                    || (build.phase == BuildPhase.LIGHTING && (!lightingEnabled || build.lightingState.isFinished()));

            if (build.cancelled || finished) {
                // Final progress + completion
                PacketDistributor.sendToPlayer(player, new BuildProgressS2C(build.planId, build.placed, build.skipped, build.total));
                PacketDistributor.sendToPlayer(player, new BuildCompleteS2C(build.planId, build.placed, build.skipped, build.total, build.cancelled));
                it.remove();
                continue;
            }

            if (build.ticksSinceLastProgress >= PROGRESS_EVERY_TICKS) {
                build.ticksSinceLastProgress = 0;
                PacketDistributor.sendToPlayer(player, new BuildProgressS2C(build.planId, build.placed, build.skipped, build.total));
            }
        }
    }

    private static int countActiveForPlayer(UUID playerId) {
        int c = 0;
        for (ActiveBuild b : ACTIVE_BY_PLAN.values()) {
            if (playerId.equals(b.playerId)) c++;
        }
        return c;
    }

    private static final class ActiveBuild {
        final UUID planId;
        final UUID playerId;

        // Player ref is safe for live jobs; we drop when disconnected
        final ServerPlayer player;

        // We keep this updated each tick in case of internal changes
        ServerLevel level;

        final ResourceLocation dimensionId;
        final BlockPos anchor;
        final BlockState state;

        final BoundingBox bounds;

        BuildPhase phase = BuildPhase.BUILDING;
        final InteriorLightingPass.State lightingState = new InteriorLightingPass.State();

        final int[] dx;
        final int[] dy;
        final int[] dz;
        final int total;

        int index = 0;
        int placed = 0;
        int skipped = 0;
        int ticksSinceLastProgress = 0;
        volatile boolean cancelled = false;

        ActiveBuild(UUID planId,
                    UUID playerId,
                    ServerPlayer player,
                    ServerLevel level,
                    ResourceLocation dimensionId,
                    BlockPos anchor,
                    BlockState state,
                    int[] dx,
                    int[] dy,
                    int[] dz,
                    int total) {
            this.planId = planId;
            this.playerId = playerId;
            this.player = player;
            this.level = level;
            this.dimensionId = dimensionId;
            this.anchor = anchor;
            this.state = state;
            this.bounds = computeBounds(anchor, dx, dy, dz);
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.total = total;
        }
    }

    private enum BuildPhase {
        BUILDING,
        LIGHTING
    }

    private static BoundingBox computeBounds(BlockPos anchor, int[] dx, int[] dy, int[] dz) {
        int minX = anchor.getX();
        int minY = anchor.getY();
        int minZ = anchor.getZ();
        int maxX = anchor.getX();
        int maxY = anchor.getY();
        int maxZ = anchor.getZ();

        if (dx != null && dy != null && dz != null) {
            int total = dx.length;
            for (int i = 0; i < total; i++) {
                int x = anchor.getX() + dx[i];
                int y = anchor.getY() + dy[i];
                int z = anchor.getZ() + dz[i];

                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (z < minZ) minZ = z;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
                if (z > maxZ) maxZ = z;
            }
        }

        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
