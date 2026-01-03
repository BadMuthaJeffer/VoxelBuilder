package com.voxelbuilder.client.debug;

import com.voxelbuilder.client.model.ModelFileManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.List;

@EventBusSubscriber(modid = "voxelbuilder", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModelDebugLogger {

    private ModelDebugLogger() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            List<String> models = ModelFileManager.getModelFiles();

            System.out.println("=== VoxelBuilder Model Scan ===");

            if (models.isEmpty()) {
                System.out.println("No models found.");
            } else {
                for (String name : models) {
                    System.out.println("Found model: " + name);
                }
            }

            System.out.println("================================");
        });
    }
}
