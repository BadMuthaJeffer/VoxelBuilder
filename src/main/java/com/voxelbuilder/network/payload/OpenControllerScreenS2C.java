package com.voxelbuilder.network.payload;

import com.voxelbuilder.VoxelBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenControllerScreenS2C() implements CustomPacketPayload {

    public static final Type<OpenControllerScreenS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VoxelBuilder.MODID, "open_controller_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenControllerScreenS2C> STREAM_CODEC =
            StreamCodec.of(OpenControllerScreenS2C::encode, OpenControllerScreenS2C::decode);

    public static void encode(RegistryFriendlyByteBuf buf, OpenControllerScreenS2C msg) {
        // no fields
    }

    public static OpenControllerScreenS2C decode(RegistryFriendlyByteBuf buf) {
        return new OpenControllerScreenS2C();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
