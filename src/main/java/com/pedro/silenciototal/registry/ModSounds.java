package com.pedro.silenciototal.registry;

import com.pedro.silenciototal.SilencioTotal;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Sons próprios (com legendas próprias) que reaproveitam áudios do jogo base. Ver assets/silenciototal/sounds.json. */
public final class ModSounds {
	/** Clique das orelhas: ele está escutando. O aviso mais comum. */
	public static final SoundEvent LISTENER_CLICK = register("entity.listener.click");
	/** Rosnado enquanto caça. */
	public static final SoundEvent LISTENER_GROWL = register("entity.listener.growl");
	/** Rugido de aviso, um instante antes de começar a caçada. */
	public static final SoundEvent LISTENER_ALERT = register("entity.listener.alert");
	/** Fareja o lugar de onde veio o som. */
	public static final SoundEvent LISTENER_SNIFF = register("entity.listener.sniff");
	/** Rosnado distante quando um Ouvinte surge na noite. */
	public static final SoundEvent LISTENER_EMERGE = register("entity.listener.emerge");
	public static final SoundEvent LISTENER_BURROW = register("entity.listener.burrow");
	public static final SoundEvent LISTENER_STEP = register("entity.listener.step");
	public static final SoundEvent LISTENER_ATTACK = register("entity.listener.attack");
	public static final SoundEvent LISTENER_HURT = register("entity.listener.hurt");
	public static final SoundEvent LISTENER_DEATH = register("entity.listener.death");
	/** Orelha do Ouvinte: batimento que acelera conforme ele se aproxima. */
	public static final SoundEvent EAR_HEARTBEAT = register("item.listener_ear.heartbeat");

	// Filhote do Ouvinte: os sons do pai, mais agudos.
	public static final SoundEvent PUP_AMBIENT = register("entity.listener_pup.ambient");
	public static final SoundEvent PUP_HURT = register("entity.listener_pup.hurt");
	public static final SoundEvent PUP_DEATH = register("entity.listener_pup.death");
	public static final SoundEvent PUP_STEP = register("entity.listener_pup.step");
	/** As duas patinhas batendo no chão. */
	public static final SoundEvent PUP_TAP = register("entity.listener_pup.tap");
	/** O eco que volta do minério. O tom diz a profundidade (agudo = raso). */
	public static final SoundEvent PUP_ECHO = register("entity.listener_pup.echo");
	/** Ganido de "não achei nada". */
	public static final SoundEvent PUP_WHINE = register("entity.listener_pup.whine");
	public static final SoundEvent PUP_BURROW = register("entity.listener_pup.burrow");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = SilencioTotal.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
	}
}
