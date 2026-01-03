package com.voxelbuilder.network.payload;

import com.voxelbuilder.VoxelBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record BuildRejectedS2C(UUID planId, String reason) implements CustomPacketPayload {

    public static final Type<BuildRejectedS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VoxelBuilder.MODID, "build_rejected"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuildRejectedS2C> STREAM_CODEC =
            StreamCodec.ofMember(BuildRejectedS2C::write, BuildRejectedS2C::new);

    public BuildRejectedS2C(RegistryFriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readUtf(32767));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(planId);
        buf.writeUtf(reason == null ? "Rejected." : reason, 32767);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
