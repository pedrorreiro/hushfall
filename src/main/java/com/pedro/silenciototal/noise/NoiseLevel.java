package com.pedro.silenciototal.noise;

/** Faixas da barra de ruído e o raio em que o Ouvinte escuta cada uma. */
public enum NoiseLevel {
	/** 0 a 30: ele ouve de longe (até ~46 blocos) mas só sabe a região, com muito erro. */
	LOW,
	/** 31 a 70: o Ouvinte vai investigar a região. */
	MEDIUM,
	/** 71+: o Ouvinte corre direto até você. */
	HIGH;

	public static final float MAX = 100f;
	public static final float MEDIUM_FROM = 31f;
	public static final float HIGH_FROM = 71f;

	public static NoiseLevel of(float noise) {
		if (noise >= HIGH_FROM) {
			return HIGH;
		}
		return noise >= MEDIUM_FROM ? MEDIUM : LOW;
	}

	/** Até que distância (em blocos) o Ouvinte escuta um jogador com esse ruído. */
	public static double hearingRadius(float noise) {
		if (noise <= 0.5f) {
			return 0;
		}
		return switch (of(noise)) {
			case LOW -> 16 + noise;
			case MEDIUM -> 48 + (noise - MEDIUM_FROM) * 1.2;
			case HIGH -> 128;
		};
	}
}
