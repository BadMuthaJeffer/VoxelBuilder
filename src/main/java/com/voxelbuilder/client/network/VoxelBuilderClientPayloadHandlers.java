package com.voxelbuilder.client.network;

import com.voxelbuilder.VoxelBuilder;
import com.voxelbuilder.client.screen.VoxelBuilderControllerScreen;
import com.voxelbuilder.network.payload.BuildAcceptedS2C;
import com.voxelbuilder.network.payload.BuildCompleteS2C;
import com.voxelbuilder.network.payload.BuildProgressS2C;
import com.voxelbuilder.network.payload.BuildRejectedS2C;
import net.minecraft.client.Minecraft;

public final class VoxelBuilderClientPayloadHandlers {

    public static void openControllerScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.setScreen(new VoxelBuilderControllerScreen());
    }

    public static void onBuildAccepted(BuildAcceptedS2C msg) {
        VoxelBuilder.LOGGER.info("Build accepted: {} total={}", msg.planId(), msg.total());
    }

    public static void onBuildRejected(BuildRejectedS2C msg) {
        VoxelBuilder.LOGGER.warn("Build rejected: {} reason={}", msg.planId(), msg.reason());
    }

    public static void onBuildProgress(BuildProgressS2C msg) {
        VoxelBuilder.LOGGER.info("Build progress: {} {}/{} (skipped={})", msg.planId(), msg.placed(), msg.total(), msg.skipped());
    }

    public static void onBuildComplete(BuildCompleteS2C msg) {
        String status = msg.cancelled() ? "cancelled" : "complete";
        VoxelBuilder.LOGGER.info("Build {}: {} placed={} skipped={} total={}",
                status, msg.planId(), msg.placed(), msg.skipped(), msg.total());
    }
}
