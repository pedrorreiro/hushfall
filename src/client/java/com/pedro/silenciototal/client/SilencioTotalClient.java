package com.pedro.silenciototal.client;

import com.pedro.silenciototal.SilencioTotal;
import com.pedro.silenciototal.client.render.ListenerModel;
import com.pedro.silenciototal.client.render.ListenerRenderer;
import com.pedro.silenciototal.network.NoisePayload;
import com.pedro.silenciototal.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

public class SilencioTotalClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientConfig.load();
		ModelLayerRegistry.registerModelLayer(ListenerModel.LAYER, ListenerModel::createBodyLayer);
		EntityRendererRegistry.register(ModEntities.LISTENER, ListenerRenderer::new);

		ClientPlayNetworking.registerGlobalReceiver(NoisePayload.TYPE, (payload, context) -> NoiseHud.receive(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> NoiseHud.reset());
		ClientTickEvents.END_CLIENT_TICK.register(PanicFeedback::tick);
		// Por baixo do resto da HUD, para não cobrir hotbar e medidor.
		HudElementRegistry.addFirst(SilencioTotal.id("panic_vignette"), PanicFeedback::extract);
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, SilencioTotal.id("noise_bar"), NoiseHud::extract);
	}
}
