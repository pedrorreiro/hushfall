package com.pedro.silenciototal.noise;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * O ruído do jogador salvo junto com ele, para que sair e voltar ao mundo não zere a barra nem
 * encerre uma caçada.
 *
 * @param noise  a barra no último instante antes de sair
 * @param night  a noite a que isso se refere (de outra noite não vale mais)
 * @param hunted o Ouvinte estava caçando (ou rugindo para) este jogador
 */
public record NoiseMemory(float noise, long night, boolean hunted) {
	public static final Codec<NoiseMemory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.FLOAT.fieldOf("noise").forGetter(NoiseMemory::noise),
			Codec.LONG.fieldOf("night").forGetter(NoiseMemory::night),
			Codec.BOOL.fieldOf("hunted").forGetter(NoiseMemory::hunted)
	).apply(instance, NoiseMemory::new));
}
