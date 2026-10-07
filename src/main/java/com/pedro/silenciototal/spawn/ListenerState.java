package com.pedro.silenciototal.spawn;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.phys.Vec3;

/**
 * Estado do Ouvinte único do mundo, salvo junto com o servidor.
 *
 * @param current   o Ouvinte desta noite (se algum já surgiu)
 * @param night     a noite a que {@code health} se refere
 * @param health    vida dele nesta noite: sumir de perto e voltar não cura a criatura
 * @param killedNight última noite em que ele foi morto (só volta na noite seguinte)
 * @param scars     todo dano que ele já levou (não regenera nem de uma noite para outra); zera quando morre
 * @param habituations lugares onde ele já procurou à toa. Ficam aqui, e não na entidade, porque ele
 *                  vira uma entidade nova toda vez que se enterra e ressurge.
 */
public record ListenerState(Optional<UUID> current, long night, float health, long killedNight, float scars,
		List<Habituation> habituations) {
	public static final ListenerState EMPTY = new ListenerState(Optional.empty(), -1, -1, -1, 0, List.of());

	/**
	 * Um lugar onde ele foi atrás de uma distração e não achou nada.
	 *
	 * @param expires tempo de jogo (game time) em que ele esquece o lugar
	 */
	public record Habituation(Vec3 pos, int visits, long expires) {
		public static final Codec<Habituation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Vec3.CODEC.fieldOf("pos").forGetter(Habituation::pos),
				Codec.INT.fieldOf("visits").forGetter(Habituation::visits),
				Codec.LONG.fieldOf("expires").forGetter(Habituation::expires)
		).apply(instance, Habituation::new));
	}

	public static final Codec<ListenerState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.optionalFieldOf("current").forGetter(ListenerState::current),
			Codec.LONG.fieldOf("night").forGetter(ListenerState::night),
			Codec.FLOAT.fieldOf("health").forGetter(ListenerState::health),
			Codec.LONG.fieldOf("killed_night").forGetter(ListenerState::killedNight),
			Codec.FLOAT.optionalFieldOf("scars", 0f).forGetter(ListenerState::scars),
			Habituation.CODEC.listOf().optionalFieldOf("habituations", List.of()).forGetter(ListenerState::habituations)
	).apply(instance, ListenerState::new));

	public ListenerState withCurrent(UUID id, long night, float health) {
		return new ListenerState(Optional.of(id), night, health, killedNight, scars, habituations);
	}

	public ListenerState withHealth(float health) {
		return new ListenerState(current, night, health, killedNight, scars, habituations);
	}

	public ListenerState killed(long night) {
		return new ListenerState(Optional.empty(), night, -1, night, 0, habituations);
	}

	public ListenerState withScars(float scars) {
		return new ListenerState(current, night, health, killedNight, scars, habituations);
	}

	public ListenerState withHabituations(List<Habituation> habituations) {
		return new ListenerState(current, night, health, killedNight, scars, List.copyOf(habituations));
	}

	public boolean isCurrent(UUID id) {
		return current.isPresent() && current.get().equals(id);
	}

	/** Já foi {@code minVisits} vezes à toa perto de {@code pos} e ainda não esqueceu. */
	public boolean isHabituatedTo(Vec3 pos, long now, int minVisits, double radius) {
		for (Habituation h : habituations) {
			if (h.expires() >= now && h.visits() >= minVisits && h.pos().closerThan(pos, radius)) {
				return true;
			}
		}
		return false;
	}

	/** Mais uma visita à toa perto de {@code pos}; os lugares já esquecidos saem da lista. */
	public ListenerState withFruitlessVisit(Vec3 pos, long now, long duration, double radius) {
		List<Habituation> list = new ArrayList<>();
		boolean found = false;
		for (Habituation h : habituations) {
			if (h.expires() < now) {
				continue;
			}
			if (!found && h.pos().closerThan(pos, radius)) {
				list.add(new Habituation(h.pos(), h.visits() + 1, now + duration));
				found = true;
			} else {
				list.add(h);
			}
		}
		if (!found) {
			list.add(new Habituation(pos, 1, now + duration));
		}
		return withHabituations(list);
	}
}
