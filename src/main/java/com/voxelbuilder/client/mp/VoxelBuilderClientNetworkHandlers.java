package com.voxelbuilder.client.mp;

/**
 * Intentionally NOT an EventBus subscriber.
 *
 * NeoForge will crash at mod load time if a class is marked @EventBusSubscriber
 * but contains no @SubscribeEvent listener methods.
 *
 * Clientbound payload handlers are registered directly in
 * com.voxelbuilder.network.VoxelBuilderNetwork via registrar.playToClient(...),
 * so this class is not needed for event registration.
 */
public final class VoxelBuilderClientNetworkHandlers {
    private VoxelBuilderClientNetworkHandlers() {}
}
