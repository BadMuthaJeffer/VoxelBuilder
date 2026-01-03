package com.voxelbuilder.network.payload;

import com.voxelbuilder.VoxelBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record CancelBuildC2S(UUID planId) implements CustomPacketPayload {

    public static final Type<CancelBuildC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VoxelBuilder.MODID, "cancel_build"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CancelBuildC2S> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> msg.write(buf),
                    CancelBuildC2S::new
            );

    public CancelBuildC2S(RegistryFriendlyByteBuf buf) {
        this(buf.readUUID());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(planId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
