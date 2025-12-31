package com.voxelbuilder.client.render;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * FINAL:
 * Render ghost preview at the correct stage.
 */
public final class VoxelBuilderClientRenderEvents {

    private VoxelBuilderClientRenderEvents() {}

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (event.getPoseStack() == null) return;

        GhostPreviewDebugRenderer.render(event.getPoseStack());
    }
}
