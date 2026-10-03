package com.pedro.silenciototal.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class ModNetworking {
	private ModNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(NoisePayload.TYPE, NoisePayload.CODEC);
	}
}
