package com.voxelbuilder.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Simple world anchor marker for voxel builds.
 *
 * v1: no special behaviour, no overrides.
 */
public class BuildAnchorBlock extends Block {

    public BuildAnchorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
