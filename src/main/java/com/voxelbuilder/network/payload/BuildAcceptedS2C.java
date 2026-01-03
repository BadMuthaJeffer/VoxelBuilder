package com.voxelbuilder.network.payload;

import com.voxelbuilder.VoxelBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record BuildAcceptedS2C(UUID planId, int total) implements CustomPacketPayload {

    public static final Type<BuildAcceptedS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VoxelBuilder.MODID, "build_accepted"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuildAcceptedS2C> STREAM_CODEC =
            StreamCodec.ofMember(BuildAcceptedS2C::write, BuildAcceptedS2C::new);

    public BuildAcceptedS2C(RegistryFriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(planId);
        buf.writeVarInt(total);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
