package com.pedro.silenciototal.pup;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * Um filhote que fugiu em vez de morrer e volta para o dono.
 *
 * @param owner        o dono
 * @param searchesLeft as buscas que ainda tinha
 * @param day          o dia (do Overworld) a partir do qual ele volta, de manhã
 * @param name         o nome dado com etiqueta, se tinha
 */
public record PupReturn(UUID owner, int searchesLeft, long day, Optional<Component> name) {
	public static final Codec<PupReturn> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("owner").forGetter(PupReturn::owner),
			Codec.INT.fieldOf("searches_left").forGetter(PupReturn::searchesLeft),
			Codec.LONG.fieldOf("day").forGetter(PupReturn::day),
			ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(PupReturn::name)
	).apply(instance, PupReturn::new));
}
