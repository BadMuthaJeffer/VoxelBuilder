package com.voxelbuilder.client.build;

import java.util.List;

public class BuildPlan {

    public static class BlockPos3 {
        public final int x, y, z;
        public BlockPos3(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final List<BlockPos3> blocks;
    private final int sizeX, sizeY, sizeZ;

    public BuildPlan(List<BlockPos3> blocks, int sizeX, int sizeY, int sizeZ) {
        this.blocks = blocks;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
    }

    public List<BlockPos3> getBlocks() {
        return blocks;
    }

    public int getBlockCount() {
        return blocks.size();
    }

    public int getSizeX() { return sizeX; }
    public int getSizeY() { return sizeY; }
    public int getSizeZ() { return sizeZ; }
}
