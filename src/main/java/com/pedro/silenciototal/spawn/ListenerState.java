package com.pedro.silenciototal.spawn;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * Estado do Ouvinte único do mundo, salvo junto com o servidor.
 *
 * @param current   o Ouvinte desta noite (se algum já surgiu)
 * @param night     a noite a que {@code health} se refere
 * @param health    vida dele nesta noite: sumir de perto e voltar não cura a criatura
 * @param killedNight última noite em que ele foi morto (só volta na noite seguinte)
 */
public record ListenerState(Optional<UUID> current, long night, float health, long killedNight) {
	public static final ListenerState EMPTY = new ListenerState(Optional.empty(), -1, -1, -1);

	public static final Codec<ListenerState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.optionalFieldOf("current").forGetter(ListenerState::current),
			Codec.LONG.fieldOf("night").forGetter(ListenerState::night),
			Codec.FLOAT.fieldOf("health").forGetter(ListenerState::health),
			Codec.LONG.fieldOf("killed_night").forGetter(ListenerState::killedNight)
	).apply(instance, ListenerState::new));

	public ListenerState withCurrent(UUID id, long night, float health) {
		return new ListenerState(Optional.of(id), night, health, killedNight);
	}

	public ListenerState withHealth(float health) {
		return new ListenerState(current, night, health, killedNight);
	}

	public ListenerState killed(long night) {
		return new ListenerState(Optional.empty(), night, -1, night);
	}

	public boolean isCurrent(UUID id) {
		return current.isPresent() && current.get().equals(id);
	}
}
