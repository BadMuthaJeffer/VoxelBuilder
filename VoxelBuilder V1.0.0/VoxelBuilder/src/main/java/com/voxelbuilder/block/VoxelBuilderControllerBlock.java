package com.voxelbuilder.block;

import com.voxelbuilder.client.screen.VoxelBuilderControllerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class VoxelBuilderControllerBlock extends Block {

    public VoxelBuilderControllerBlock(Properties properties) {
        super(properties);
    }

    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        return handleClick(level);
    }

    public InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        return handleClick(level);
    }

    private InteractionResult handleClick(Level level) {
        if (level.isClientSide()) {
            Minecraft.getInstance().setScreen(new VoxelBuilderControllerScreen());
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
