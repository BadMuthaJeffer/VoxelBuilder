package com.voxelbuilder.server.build;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public final class ServerBuildJob {

    public final UUID planId;
    public final UUID playerId;
    public final ServerLevel level;

    public final BlockPos anchor;
    public final ResourceLocation blockId;
    public final BlockState state;

    public final int[] dx;
    public final int[] dy;
    public final int[] dz;

    public int index = 0;
    public int placed = 0;
    public int skipped = 0;
    public int ticks = 0;

    public ServerBuildJob(
            UUID planId,
            UUID playerId,
            ServerLevel level,
            BlockPos anchor,
            ResourceLocation blockId,
            BlockState state,
            int[] dx,
            int[] dy,
            int[] dz
    ) {
        this.planId = planId;
        this.playerId = playerId;
        this.level = level;
        this.anchor = anchor;
        this.blockId = blockId;
        this.state = state;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public int total() {
        return dx == null ? 0 : dx.length;
    }

    public boolean isDone() {
        return index >= total();
    }
}
