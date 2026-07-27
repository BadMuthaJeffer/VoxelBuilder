package com.voxelbuilder.client;

import com.voxelbuilder.client.input.PreviewPlacementHandler;
import com.voxelbuilder.client.render.VoxelBuilderClientRenderEvents;

import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only initialization.
 */
public final class VoxelBuilderClientInit {

    private VoxelBuilderClientInit() {}

    public static void initClient() {

        // Client tick for placement
        NeoForge.EVENT_BUS.addListener(
                PreviewPlacementHandler::onClientTick
        );

        // Render hook (NO-OP in Step 7A)
        NeoForge.EVENT_BUS.addListener(
                VoxelBuilderClientRenderEvents::onRenderLevelStage
        );
    }
}
