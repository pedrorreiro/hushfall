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

	/** Noite em que o Ouvinte pode aparecer (já passou a noite de graça). */
	public static boolean isHuntingNight(ServerLevel level) {
		return isNight(level) && day(level) >= ModConfig.get().graceNights;
	}
}
