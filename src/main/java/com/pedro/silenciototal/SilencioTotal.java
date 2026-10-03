package com.pedro.silenciototal;

import com.pedro.silenciototal.network.ModNetworking;
import com.pedro.silenciototal.noise.NoiseSources;
import com.pedro.silenciototal.noise.NoiseTracker;
import com.pedro.silenciototal.noise.WorldSounds;
import com.pedro.silenciototal.registry.ModAttachments;
import com.pedro.silenciototal.registry.ModEntities;
import com.pedro.silenciototal.registry.ModItems;
import com.pedro.silenciototal.registry.ModSounds;
import com.pedro.silenciototal.spawn.ListenerSpawner;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SilencioTotal implements ModInitializer {
	public static final String MOD_ID = "silenciototal";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModConfig.load();
		ModSounds.init();
		ModAttachments.init();
		ModEntities.init();
		ModItems.init();
		ModNetworking.init();
		NoiseSources.register();

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			NoiseTracker.tick(server);
			ListenerSpawner.tick(server);
			WorldSounds.tick(server);
		});
		ServerPlayerEvents.LEAVE.register(player -> NoiseTracker.forget(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			NoiseTracker.clear();
			WorldSounds.clear();
		});

		LOGGER.info("Shhh. O Ouvinte está escutando.");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
