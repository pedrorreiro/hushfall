package com.pedro.silenciototal.registry;

import com.pedro.silenciototal.SilencioTotal;
import com.pedro.silenciototal.entity.Listener;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final ResourceKey<EntityType<?>> LISTENER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, SilencioTotal.id("listener"));

	/** O Ouvinte. Não entra no spawn natural de biomas: quem decide onde e quando ele surge é o {@code ListenerSpawner}. */
	public static final EntityType<Listener> LISTENER = Registry.register(BuiltInRegistries.ENTITY_TYPE, LISTENER_KEY,
			EntityType.Builder.of(Listener::new, MobCategory.MONSTER)
					// O desenho tem 3 blocos de altura, mas a caixa de colisão cabe em túneis de 2:
					// ele se espreme para passar onde você passa (ver ListenerRenderer).
					.sized(0.9f, 1.95f)
					.eyeHeight(1.75f)
					.clientTrackingRange(10)
					.build(LISTENER_KEY));

	private ModEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(LISTENER, Listener.createAttributes());
	}
}
