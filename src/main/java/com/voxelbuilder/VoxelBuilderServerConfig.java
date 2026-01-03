package com.voxelbuilder;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config for VoxelBuilder (NeoForge 1.21.1).
 *
 * IMPORTANT:
 * - Register this spec in your mod constructor:
 *     container.registerConfig(ModConfig.Type.SERVER, VoxelBuilderServerConfig.SPEC);
 *
 * SERVER configs are synced to clients by NeoForge. citeturn1view0
 */
@EventBusSubscriber(modid = VoxelBuilder.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class VoxelBuilderServerConfig {

    private VoxelBuilderServerConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ---- Build execution ----
    public static final ModConfigSpec.IntValue GLOBAL_BLOCKS_PER_TICK = BUILDER
            .comment("Global placement budget per server tick across ALL active builds.")
            .defineInRange("server.globalBlocksPerTick", 300, 1, 100000);

    public static final ModConfigSpec.IntValue PER_JOB_BLOCKS_PER_TICK = BUILDER
            .comment("Max placements per tick per build job (still capped by globalBlocksPerTick).")
            .defineInRange("server.perJobBlocksPerTick", 80, 1, 100000);

    public static final ModConfigSpec.IntValue MAX_ACTIVE_JOBS_GLOBAL = BUILDER
            .comment("Maximum number of active build jobs across the whole server.")
            .defineInRange("server.maxActiveJobsGlobal", 16, 1, 512);

    public static final ModConfigSpec.IntValue MAX_ACTIVE_JOBS_PER_PLAYER = BUILDER
            .comment("Maximum active build jobs per player (prevents double-submit spam).")
            .defineInRange("server.maxActiveJobsPerPlayer", 1, 1, 16);

    // ---- Safety / anti-grief ----
    public static final ModConfigSpec.IntValue MAX_DISTANCE_FROM_ANCHOR = BUILDER
            .comment("If the owning player is farther than this from the anchor, the job is cancelled.")
            .defineInRange("server.maxDistanceFromAnchor", 96, 8, 2048);

    public static final ModConfigSpec.BooleanValue CANCEL_WHEN_OUT_OF_RANGE = BUILDER
            .comment("Cancel jobs when the owning player moves out of range (maxDistanceFromAnchor).")
            .define("server.cancelWhenOutOfRange", true);

    public static final ModConfigSpec.BooleanValue CANCEL_ON_DIMENSION_CHANGE = BUILDER
            .comment("Cancel jobs if the player changes dimension while a job is running.")
            .define("server.cancelOnDimensionChange", true);

    public static final ModConfigSpec.BooleanValue SKIP_UNLOADED_CHUNKS = BUILDER
            .comment("If true, blocks in unloaded chunks are skipped. If false, the server may load chunks depending on other settings/mods.")
            .define("server.skipUnloadedChunks", true);

    public static final ModConfigSpec.IntValue MAX_EXTENT_XZ = BUILDER
            .comment("Max build extent in X and Z (|dx| and |dz|).")
            .defineInRange("server.maxExtentXZ", 512, 1, 30000);

    public static final ModConfigSpec.IntValue MAX_EXTENT_Y = BUILDER
            .comment("Max build extent in Y (|dy|).")
            .defineInRange("server.maxExtentY", 256, 1, 4096);

    // ---- Network safety ----
    public static final ModConfigSpec.IntValue MAX_PACKET_BLOCKS = BUILDER
            .comment("Maximum number of blocks allowed in a submitted build plan packet (prevents memory abuse).")
            .defineInRange("server.maxPacketBlocks", 2_000_000, 1, 50_000_000);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ---- Baked values (fast access) ----
    public static volatile int globalBlocksPerTick = 300;
    public static volatile int perJobBlocksPerTick = 80;
    public static volatile int maxActiveJobsGlobal = 16;
    public static volatile int maxActiveJobsPerPlayer = 1;

    public static volatile int maxDistanceFromAnchor = 96;
    public static volatile boolean cancelWhenOutOfRange = true;
    public static volatile boolean cancelOnDimensionChange = true;
    public static volatile boolean skipUnloadedChunks = true;

    public static volatile int maxExtentXZ = 512;
    public static volatile int maxExtentY = 256;

    public static volatile int maxPacketBlocks = 2_000_000;

    private static void bake() {
        globalBlocksPerTick = GLOBAL_BLOCKS_PER_TICK.get();
        perJobBlocksPerTick = PER_JOB_BLOCKS_PER_TICK.get();
        maxActiveJobsGlobal = MAX_ACTIVE_JOBS_GLOBAL.get();
        maxActiveJobsPerPlayer = MAX_ACTIVE_JOBS_PER_PLAYER.get();

        maxDistanceFromAnchor = MAX_DISTANCE_FROM_ANCHOR.get();
        cancelWhenOutOfRange = CANCEL_WHEN_OUT_OF_RANGE.get();
        cancelOnDimensionChange = CANCEL_ON_DIMENSION_CHANGE.get();
        skipUnloadedChunks = SKIP_UNLOADED_CHUNKS.get();

        maxExtentXZ = MAX_EXTENT_XZ.get();
        maxExtentY = MAX_EXTENT_Y.get();

        maxPacketBlocks = MAX_PACKET_BLOCKS.get();
    }

    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent.Loading event) {
        if (isOurServerConfig(event.getConfig())) {
            bake();
        }
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (isOurServerConfig(event.getConfig())) {
            bake();
        }
    }

    private static boolean isOurServerConfig(ModConfig config) {
        return config != null && config.getType() == ModConfig.Type.SERVER && config.getSpec() == SPEC;
    }
}
