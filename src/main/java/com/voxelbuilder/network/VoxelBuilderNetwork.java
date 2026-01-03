package com.voxelbuilder.network;

import com.voxelbuilder.VoxelBuilder;
import com.voxelbuilder.network.payload.*;
import com.voxelbuilder.server.build.ServerBuildManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Payload registration + common handlers.
 *
 * IMPORTANT:
 * - This class is loaded on dedicated servers.
 * - Do NOT directly reference com.voxelbuilder.client.* types; use reflection.
 */
@EventBusSubscriber(modid = VoxelBuilder.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class VoxelBuilderNetwork {

    private static final String NETWORK_VERSION = "1";

    private VoxelBuilderNetwork() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(NETWORK_VERSION);

        // -------- Serverbound --------
        registrar.playToServer(SubmitBuildPlanC2S.TYPE, SubmitBuildPlanC2S.STREAM_CODEC, VoxelBuilderNetwork::handleSubmitBuildPlan);
        registrar.playToServer(CancelBuildC2S.TYPE, CancelBuildC2S.STREAM_CODEC, VoxelBuilderNetwork::handleCancelBuild);

        // -------- Clientbound --------
        registrar.playToClient(OpenControllerScreenS2C.TYPE, OpenControllerScreenS2C.STREAM_CODEC, VoxelBuilderNetwork::handleOpenControllerScreen);

        registrar.playToClient(BuildAcceptedS2C.TYPE, BuildAcceptedS2C.STREAM_CODEC, VoxelBuilderNetwork::handleBuildAccepted);
        registrar.playToClient(BuildRejectedS2C.TYPE, BuildRejectedS2C.STREAM_CODEC, VoxelBuilderNetwork::handleBuildRejected);
        registrar.playToClient(BuildProgressS2C.TYPE, BuildProgressS2C.STREAM_CODEC, VoxelBuilderNetwork::handleBuildProgress);
        registrar.playToClient(BuildCompleteS2C.TYPE, BuildCompleteS2C.STREAM_CODEC, VoxelBuilderNetwork::handleBuildComplete);
    }

    // --------------------
    // Serverbound handlers
    // --------------------

    private static void handleSubmitBuildPlan(SubmitBuildPlanC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            String rejectReason = ServerBuildManager.trySubmit(player, payload);
            if (rejectReason == null) {
                PacketDistributor.sendToPlayer(player, new BuildAcceptedS2C(payload.planId(), payload.dx().length));
            } else {
                PacketDistributor.sendToPlayer(player, new BuildRejectedS2C(payload.planId(), rejectReason));
            }
        });
    }

    private static void handleCancelBuild(CancelBuildC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            boolean cancelled = ServerBuildManager.cancel(player, payload.planId());
            if (!cancelled) {
                PacketDistributor.sendToPlayer(player, new BuildRejectedS2C(payload.planId(), "No active build to cancel."));
            }
        });
    }

    // --------------------
    // Clientbound handlers
    // --------------------

    private static void handleOpenControllerScreen(OpenControllerScreenS2C payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Prefer the client-side helper if it exists (keeps UI logic out of common code).
            if (!invokeClientHandler("openControllerScreen", new Class<?>[]{}, new Object[]{})) {
                // Minimal fallback: tell the user something happened.
                if (context.player() != null) {
                    context.player().displayClientMessage(Component.literal("VoxelBuilder: opening controller..."), true);
                }
            }
        });
    }

    private static void handleBuildAccepted(BuildAcceptedS2C payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Update client MP state if present.
            invokeMpBuildClientState("onAccepted",
                    new Class<?>[]{UUID.class, int.class},
                    new Object[]{payload.planId(), payload.total()});
        });
    }

    private static void handleBuildRejected(BuildRejectedS2C payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            invokeMpBuildClientState("onRejected",
                    new Class<?>[]{UUID.class, String.class},
                    new Object[]{payload.planId(), payload.reason()});

            if (context.player() != null) {
                context.player().displayClientMessage(Component.literal("VoxelBuilder: rejected: " + payload.reason()), false);
            }
        });
    }

    private static void handleBuildProgress(BuildProgressS2C payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            invokeMpBuildClientState("onProgress",
                    new Class<?>[]{UUID.class, int.class, int.class, int.class},
                    new Object[]{payload.planId(), payload.placed(), payload.skipped(), payload.total()});
        });
    }

    private static void handleBuildComplete(BuildCompleteS2C payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            invokeMpBuildClientState("onComplete",
                    new Class<?>[]{UUID.class, boolean.class, int.class, int.class, int.class},
                    new Object[]{payload.planId(), payload.cancelled(), payload.placed(), payload.skipped(), payload.total()});

            if (context.player() != null) {
                String msg = payload.cancelled()
                        ? "VoxelBuilder: build cancelled (" + payload.placed() + "/" + payload.total() + ")"
                        : "VoxelBuilder: build complete (" + payload.placed() + "/" + payload.total() + ")";
                context.player().displayClientMessage(Component.literal(msg), false);
            }
        });
    }

    // --------------------
    // Reflection helpers (dedicated-server safe)
    // --------------------

    private static boolean invokeClientHandler(String method, Class<?>[] paramTypes, Object[] args) {
        try {
            Class<?> cls = Class.forName("com.voxelbuilder.client.network.VoxelBuilderClientPayloadHandlers");
            cls.getMethod(method, paramTypes).invoke(null, args);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean invokeMpBuildClientState(String method, Class<?>[] paramTypes, Object[] args) {
        try {
            Class<?> cls = Class.forName("com.voxelbuilder.client.mp.MpBuildClientState");
            cls.getMethod(method, paramTypes).invoke(null, args);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
