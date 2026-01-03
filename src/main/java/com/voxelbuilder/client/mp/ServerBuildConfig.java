package com.voxelbuilder.server.build;

public final class ServerBuildConfig {

    // Hard caps for Alpha MP. Later: move to server config (synced/unsynced as needed).
    public static final int MAX_VOXELS = 250_000;
    public static final int MAX_SPAN = 512; // max (max-min) across X/Y/Z offsets
    public static final int BLOCKS_PER_TICK = 200; // safe default; tune later
    public static final int PROGRESS_EVERY_TICKS = 20; // once per second

    public static final int MAX_ANCHOR_DISTANCE = 128; // blocks
    public static final int MAX_ANCHOR_DISTANCE_SQ = MAX_ANCHOR_DISTANCE * MAX_ANCHOR_DISTANCE;

    private ServerBuildConfig() {}
}
