package com.voxelbuilder.network.payload;

import com.voxelbuilder.VoxelBuilder;
import com.voxelbuilder.VoxelBuilderServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client -> Server: Submit a build plan for server-authoritative execution.
 *
 * NOTE: This payload performs a defensive length cap during decode to prevent memory abuse.
 * The cap is configurable via server.maxPacketBlocks in VoxelBuilderServerConfig.
 */
public record SubmitBuildPlanC2S(
        UUID planId,
        BlockPos anchor,
        ResourceLocation blockId,
        int[] dx,
        int[] dy,
        int[] dz
) implements CustomPacketPayload {

    public static final Type<SubmitBuildPlanC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VoxelBuilder.MODID, "submit_build_plan"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SubmitBuildPlanC2S> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> msg.write(buf),
                    SubmitBuildPlanC2S::new
            );

    public SubmitBuildPlanC2S(RegistryFriendlyByteBuf buf) {
        this(
                buf.readUUID(),
                buf.readBlockPos(),
                ResourceLocation.STREAM_CODEC.decode(buf),
                readIntArrayCapped(buf),
                readIntArrayCapped(buf),
                readIntArrayCapped(buf)
        );
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(planId);
        buf.writeBlockPos(anchor);
        ResourceLocation.STREAM_CODEC.encode(buf, blockId);

        writeIntArray(buf, dx);
        writeIntArray(buf, dy);
        writeIntArray(buf, dz);
    }

    private static void writeIntArray(RegistryFriendlyByteBuf buf, int[] arr) {
        int n = (arr == null) ? 0 : arr.length;
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeVarInt(arr[i]);
        }
    }

    private static int[] readIntArrayCapped(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0) n = 0;

        // Server config is synced to clients; still keep a hard floor fallback.
        int cap = VoxelBuilderServerConfig.maxPacketBlocks;
        if (cap <= 0) cap = 1_000_000;

        if (n > cap) {
            // Drain just enough to keep decoder in a sane state would be expensive; instead reject by clamping.
            // The server will fail validation and reject/cancel the job safely.
            n = cap;
        }

        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = buf.readVarInt();
        }
        return out;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
