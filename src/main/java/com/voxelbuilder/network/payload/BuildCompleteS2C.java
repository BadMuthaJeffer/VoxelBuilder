package com.voxelbuilder.network.payload;

import com.voxelbuilder.VoxelBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record BuildCompleteS2C(UUID planId, int placed, int skipped, int total, boolean cancelled) implements CustomPacketPayload {

    public static final Type<BuildCompleteS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VoxelBuilder.MODID, "build_complete"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuildCompleteS2C> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> msg.write(buf),
                    BuildCompleteS2C::new
            );

    public BuildCompleteS2C(RegistryFriendlyByteBuf buf) {
        this(
                buf.readUUID(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean()
        );
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(planId);
        buf.writeVarInt(placed);
        buf.writeVarInt(skipped);
        buf.writeVarInt(total);
        buf.writeBoolean(cancelled);
    }

    // Handy compatibility helper (if you log msg.success() anywhere)
    public boolean success() {
        return !cancelled;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
