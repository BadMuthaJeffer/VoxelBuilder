package com.voxelbuilder.block;

import com.voxelbuilder.network.payload.OpenControllerScreenS2C;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Opens the VoxelBuilder controller screen.
 *
 * Dedicated-server safe:
 * - No net.minecraft.client.* references.
 * - Client screen opening is done via reflection.
 *
 * Robust:
 * - Opens on client immediately (fallback) AND also asks server to open (packet),
 *   so MP works even if one side is temporarily miswired during development.
 */
public class VoxelBuilderControllerBlock extends Block {

    public VoxelBuilderControllerBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    // Empty-hand interaction
    @SuppressWarnings("deprecation")
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            openClientScreenFallback();
            return InteractionResult.SUCCESS;
        }

        if (player instanceof ServerPlayer sp) {
            // Tiny debug breadcrumb so you KNOW the server received the click.
            sp.displayClientMessage(Component.literal("VoxelBuilder: opening controller..."), true);

            PacketDistributor.sendToPlayer(sp, new OpenControllerScreenS2C());
        }

        return InteractionResult.CONSUME;
    }

    // Item-in-hand interaction (so it opens even if you're holding something)
    @SuppressWarnings("deprecation")
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            openClientScreenFallback();
            return ItemInteractionResult.sidedSuccess(true);
        }

        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.literal("VoxelBuilder: opening controller..."), true);
            PacketDistributor.sendToPlayer(sp, new OpenControllerScreenS2C());
        }

        return ItemInteractionResult.sidedSuccess(false);
    }

    private static void openClientScreenFallback() {
        // Reflection keeps this class safe on dedicated servers.
        try {
            Class<?> cls = Class.forName("com.voxelbuilder.client.network.VoxelBuilderClientPayloadHandlers");
            java.lang.reflect.Method m = cls.getMethod("openControllerScreen");
            m.invoke(null);
        } catch (Throwable ignored) {
        }
    }
}
