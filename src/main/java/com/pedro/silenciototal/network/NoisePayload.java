package com.pedro.silenciototal.network;

import com.pedro.silenciototal.SilencioTotal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor -> cliente: valor da barra de ruído e o que está afetando ela. */
public record NoisePayload(float noise, byte flags) implements CustomPacketPayload {
	public static final byte NIGHT = 1;
	public static final byte SILENT_ROOM = 2;
	public static final byte RAIN = 4;
	public static final byte GRACE = 8;

	public static final Type<NoisePayload> TYPE = new Type<>(SilencioTotal.id("noise"));
	public static final StreamCodec<RegistryFriendlyByteBuf, NoisePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, NoisePayload::noise,
			ByteBufCodecs.BYTE, NoisePayload::flags,
			NoisePayload::new);

	public boolean has(byte flag) {
		return (flags & flag) != 0;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
