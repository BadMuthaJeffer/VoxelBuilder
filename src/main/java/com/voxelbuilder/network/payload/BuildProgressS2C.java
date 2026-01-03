package com.voxelbuilder.network.payload;

import com.voxelbuilder.VoxelBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record BuildProgressS2C(UUID planId, int placed, int skipped, int total) implements CustomPacketPayload {

    public static final Type<BuildProgressS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VoxelBuilder.MODID, "build_progress"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuildProgressS2C> STREAM_CODEC =
            StreamCodec.ofMember(BuildProgressS2C::write, BuildProgressS2C::new);

    public BuildProgressS2C(RegistryFriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(planId);
        buf.writeVarInt(placed);
        buf.writeVarInt(skipped);
        buf.writeVarInt(total);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
