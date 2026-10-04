package com.pedro.silenciototal.noise;

import com.pedro.silenciototal.ModConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** O mod só roda à noite e só no Overworld. */
public final class NightCycle {
	/** Pôr do sol (o céu já está escuro o bastante para os monstros). */
	public static final long DUSK = 12600;
	/** Nascer do sol. */
	public static final long DAWN = 23400;

	private NightCycle() {
	}

	public static long timeOfDay(ServerLevel level) {
		return Math.floorMod(level.getOverworldClockTime(), 24000L);
	}

	public static long day(ServerLevel level) {
		return Math.floorDiv(level.getOverworldClockTime(), 24000L);
	}

	public static boolean isNight(ServerLevel level) {
		if (level.dimension() != Level.OVERWORLD) {
			return false;
		}
		long time = timeOfDay(level);
		return time >= DUSK && time < DAWN;
	}

	/** Noite em que o Ouvinte aparece: já passou a noite de graça e o sorteio desta noite caiu. */
	public static boolean isHuntingNight(ServerLevel level) {
		return isNight(level) && isHuntingDay(level, day(level));
	}

	/**
	 * Sorteio da noite: cada noite tem {@code nightChance} de chance (50% por padrão) de ter o
	 * Ouvinte. O resultado depende só da semente do mundo e do dia, então sair e entrar no mundo
	 * não sorteia de novo.
	 */
	public static boolean isHuntingDay(ServerLevel level, long day) {
		if (day < ModConfig.get().graceNights) {
			return false;
		}
		float chance = ModConfig.get().nightChance;
		if (chance >= 1f) {
			return true;
		}
		long hash = level.getSeed() * 0x9E3779B97F4A7C15L + day * 0xC2B2AE3D27D4EB4FL;
		hash ^= hash >>> 33;
		hash *= 0xFF51AFD7ED558CCDL;
		hash ^= hash >>> 33;
		float roll = (hash >>> 40) / (float) (1L << 24);
		return roll < chance;
	}
}
