package com.voxelbuilder.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.voxelbuilder.VoxelBuilderServerConfig;
import com.voxelbuilder.server.build.ServerBuildManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

public final class VoxelBuilderCommands {

    private VoxelBuilderCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(
                Commands.literal("voxelbuilder")
                        .requires(src -> src.hasPermission(2)) // OP-only
                        .then(Commands.literal("config")
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();

                                    src.sendSuccess(() -> Component.literal("VoxelBuilder SERVER config:"), false);

                                    src.sendSuccess(() -> Component.literal("  globalBlocksPerTick = " + VoxelBuilderServerConfig.globalBlocksPerTick), false);
                                    src.sendSuccess(() -> Component.literal("  perJobBlocksPerTick = " + VoxelBuilderServerConfig.perJobBlocksPerTick), false);

                                    src.sendSuccess(() -> Component.literal("  maxActiveJobsGlobal = " + VoxelBuilderServerConfig.maxActiveJobsGlobal), false);
                                    src.sendSuccess(() -> Component.literal("  maxActiveJobsPerPlayer = " + VoxelBuilderServerConfig.maxActiveJobsPerPlayer), false);

                                    src.sendSuccess(() -> Component.literal("  maxDistanceFromAnchor = " + VoxelBuilderServerConfig.maxDistanceFromAnchor), false);
                                    src.sendSuccess(() -> Component.literal("  cancelWhenOutOfRange = " + VoxelBuilderServerConfig.cancelWhenOutOfRange), false);
                                    src.sendSuccess(() -> Component.literal("  cancelOnDimensionChange = " + VoxelBuilderServerConfig.cancelOnDimensionChange), false);
                                    src.sendSuccess(() -> Component.literal("  skipUnloadedChunks = " + VoxelBuilderServerConfig.skipUnloadedChunks), false);

                                    src.sendSuccess(() -> Component.literal("  maxExtentXZ = " + VoxelBuilderServerConfig.maxExtentXZ), false);
                                    src.sendSuccess(() -> Component.literal("  maxExtentY = " + VoxelBuilderServerConfig.maxExtentY), false);

                                    src.sendSuccess(() -> Component.literal("  lightingEnabled = " + VoxelBuilderServerConfig.lightingEnabled), false);
                                    src.sendSuccess(() -> Component.literal("  lightingSpacingXZ = " + VoxelBuilderServerConfig.lightingSpacingXZ), false);
                                    src.sendSuccess(() -> Component.literal("  lightingSpacingY = " + VoxelBuilderServerConfig.lightingSpacingY), false);
                                    src.sendSuccess(() -> Component.literal("  lightingSearchRadius = " + VoxelBuilderServerConfig.lightingSearchRadius), false);
                                    src.sendSuccess(() -> Component.literal("  lightingBlocksPerTick = " + VoxelBuilderServerConfig.lightingBlocksPerTick), false);
                                    src.sendSuccess(() -> Component.literal("  lightingBlockId = " + VoxelBuilderServerConfig.lightingBlockId), false);

                                    return 1;
                                }))
                        .then(Commands.literal("status")
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();
                                    MinecraftServer server = src.getServer();

                                    int active = ServerBuildManager.getActiveCount();
                                    src.sendSuccess(() -> Component.literal("VoxelBuilder MP status: active builds = " + active), false);

                                    List<ServerBuildManager.JobSnapshot> jobs = ServerBuildManager.getSnapshots(server);
                                    if (jobs.isEmpty()) {
                                        src.sendSuccess(() -> Component.literal("  (no active builds)"), false);
                                        return 1;
                                    }

                                    for (ServerBuildManager.JobSnapshot j : jobs) {
                                        String planShort = j.planId().toString();
                                        if (planShort.length() > 8) planShort = planShort.substring(0, 8);

                                        String dim = (j.dimensionId() == null) ? "unknown" : j.dimensionId().toString();

                                        String line =
                                                "  " + j.playerName() +
                                                " plan=" + planShort +
                                                " phase=" + j.phase() +
                                                " " + j.placed() + "/" + j.total() +
                                                " skipped=" + j.skipped() +
                                                (j.cancelled() ? " (CANCELLED)" : "") +
                                                " anchor=" + j.anchor().getX() + "," + j.anchor().getY() + "," + j.anchor().getZ() +
                                                " dim=" + dim;

                                        Component msg = Component.literal(line);
                                        src.sendSuccess(() -> msg, false);
                                    }

                                    return 1;
                                }))
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> {
                                            CommandSourceStack src = ctx.getSource();
                                            MinecraftServer server = src.getServer();
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

                                            int n = ServerBuildManager.cancelAllForPlayer(server, target.getUUID());

                                            if (n <= 0) {
                                                src.sendSuccess(() -> Component.literal("VoxelBuilder: no active builds for " + target.getGameProfile().getName()), false);
                                            } else {
                                                src.sendSuccess(() -> Component.literal("VoxelBuilder: cancelled " + n + " build(s) for " + target.getGameProfile().getName()), false);
                                            }
                                            return 1;
                                        })))
        );
    }
}
