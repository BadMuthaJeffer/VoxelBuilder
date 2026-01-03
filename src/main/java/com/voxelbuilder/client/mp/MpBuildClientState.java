package com.voxelbuilder.client.mp;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client-side state tracker for an in-flight multiplayer build job.
 *
 * This class is client-only. Common/server code must reference it only via reflection.
 */
public final class MpBuildClientState {

    private static UUID activePlanId = null;

    private static int total = 0;
    private static int placed = 0;
    private static int skipped = 0;

    private static ResourceLocation blockId = null;
    private static BlockPos anchor = null;

    private static String statusLine = "MP: Idle";

    private MpBuildClientState() {}

    // --- UI / hotkey access ---
    public static UUID getActivePlanId() {
        return activePlanId;
    }

    /** One-line status string suitable for rendering in UI. */
    public static String getStatusLine() {
        return statusLine == null ? "" : statusLine;
    }

    public static void clear(UUID planId) {
        if (activePlanId != null && activePlanId.equals(planId)) {
            activePlanId = null;
            total = 0;
            placed = 0;
            skipped = 0;
            blockId = null;
            anchor = null;
            statusLine = "MP: Idle";
        }
    }

    // --- Called when client submits a plan ---
    public static void start(UUID planId, int totalBlocks, ResourceLocation selectedBlockId, BlockPos anchorPos) {
        activePlanId = planId;
        total = Math.max(0, totalBlocks);
        placed = 0;
        skipped = 0;
        blockId = selectedBlockId;
        anchor = anchorPos;

        statusLine = "MP: Submitted — 0/" + total;
        sendAction("submitted (" + total + " blocks)");
    }

    // --- Called from S2C handlers (via reflection) ---
    public static void onAccepted(UUID planId, int totalBlocks) {
        if (activePlanId == null || !activePlanId.equals(planId)) {
            activePlanId = planId;
        }
        total = Math.max(0, totalBlocks);

        statusLine = "MP: Accepted — 0/" + total;
        sendAction("accepted");
    }

    public static void onRejected(UUID planId, String reason) {
        if (activePlanId != null && activePlanId.equals(planId)) {
            statusLine = "MP: Rejected — " + (reason == null ? "" : reason);
            sendChat("VoxelBuilder MP: rejected — " + reason);
            clear(planId);
        }
    }

    public static void onProgress(UUID planId, int placedBlocks, int skippedBlocks, int totalBlocks) {
        if (activePlanId == null || !activePlanId.equals(planId)) return;

        placed = placedBlocks;
        skipped = skippedBlocks;
        total = totalBlocks;

        statusLine = "MP: Building — " + placed + "/" + total + " (skipped " + skipped + ")";
    }

    /** Compatibility wrapper used by existing network handler code. */
    public static void updateProgress(UUID planId, int placedBlocks, int skippedBlocks, int totalBlocks) {
        onProgress(planId, placedBlocks, skippedBlocks, totalBlocks);
    }

    public static void onComplete(UUID planId, boolean cancelled, int placedBlocks, int skippedBlocks, int totalBlocks) {
        if (activePlanId == null || !activePlanId.equals(planId)) return;

        placed = placedBlocks;
        skipped = skippedBlocks;
        total = totalBlocks;

        statusLine = "MP: " + (cancelled ? "Cancelled" : "Complete") +
                " — " + placed + "/" + total + " (skipped " + skipped + ")";

        sendChat("VoxelBuilder MP: " + (cancelled ? "cancelled" : "complete") +
                " (" + placed + " placed, " + skipped + " skipped, " + total + " total).");

        // clear active build id after completion so Backspace won't keep cancelling a finished job
        clear(planId);
    }

    // --- Small helpers ---
    private static void sendChat(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.displayClientMessage(Component.literal(msg), false);
    }

    private static void sendAction(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.displayClientMessage(Component.literal("VoxelBuilder MP: " + msg), true);
    }
}
