package com.voxelbuilder.client.build;

import com.voxelbuilder.client.mp.MpBuildClientState;
import com.voxelbuilder.client.render.GhostPreviewDebugRenderer;
import com.voxelbuilder.network.payload.SubmitBuildPlanC2S;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

/**
 * Build runner:
 * - Singleplayer: places blocks via integrated server thread.
 * - Multiplayer: submits frozen plan to server (server-authoritative).
 *
 * CRITICAL RULE:
 * - Build ONLY from VoxelBuilderSession.getConfirmedBlocks() (frozen snapshot).
 */
@EventBusSubscriber(modid = "voxelbuilder", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class VoxelBuilderClientBuildRunner {

    private static final int BLOCKS_PER_TICK = 50;

    private static boolean running = false;
    private static int index = 0;

    private VoxelBuilderClientBuildRunner() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        if (!VoxelBuilderSession.isConfirmed()) {
            running = false;
            index = 0;
            return;
        }

        List<BuildPlan.BlockPos3> blocks = VoxelBuilderSession.getConfirmedBlocks();
        BlockPos anchor = VoxelBuilderSession.getAnchor();

        if (blocks == null || blocks.isEmpty() || anchor == null) {
            mc.player.displayClientMessage(Component.literal("Builder: confirmed session has no frozen plan. Aborting."), true);
            stopAndClear();
            return;
        }

        // ALWAYS use server-authoritative build system (both SP and MP)
        // This ensures lighting and all server features work consistently
        
        BlockState state = VoxelBuilderSession.getSelectedBlock();
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());

        UUID planId = UUID.randomUUID();

        int total = blocks.size();
        int[] dx = new int[total];
        int[] dy = new int[total];
        int[] dz = new int[total];

        for (int i = 0; i < total; i++) {
            BuildPlan.BlockPos3 p = blocks.get(i);
            dx[i] = p.x;
            dy[i] = p.y;
            dz[i] = p.z;
        }

        // Track on client side
        MpBuildClientState.start(planId, total, blockId, anchor);
        
        // Submit to server (works for both integrated and dedicated servers)
        PacketDistributor.sendToServer(new SubmitBuildPlanC2S(planId, anchor, blockId, dx, dy, dz));

        mc.player.displayClientMessage(Component.literal("Builder: submitted plan to server (" + total + " blocks)."), true);
        stopAndClear();
    }

    private static void stopAndClear() {
        running = false;
        index = 0;

        VoxelBuilderSession.clear();

        try {
            GhostPreviewDebugRenderer.clearPlacement();
        } catch (Throwable ignored) {}

        try {
            GhostPreviewDebugRenderer.setPreviewLocked(false);
        } catch (Throwable ignored) {}
    }
}
