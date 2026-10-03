package com.pedro.silenciototal.client;

import com.pedro.silenciototal.entity.Listener;
import com.pedro.silenciototal.registry.ModItems;
import com.pedro.silenciototal.registry.ModSounds;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * Orelha do Ouvinte: segurando ela (em qualquer mão), você ouve um batimento cardíaco que acelera
 * e fica mais alto quanto mais perto o Ouvinte está. Sem direção, só distância.
 */
public final class EarHeartbeat {
	/** Até onde a orelha "ouve" o Ouvinte. */
	public static final double RANGE = 96;
	private static final double NEAR = 6;
	private static final int FASTEST = 7;
	private static final int SLOWEST = 45;

	private static int cooldown;

	private EarHeartbeat() {
	}

	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.isPaused()) {
			return;
		}
		if (!player.getMainHandItem().is(ModItems.LISTENER_EAR) && !player.getOffhandItem().is(ModItems.LISTENER_EAR)) {
			cooldown = 0;
			return;
		}
		if (PanicFeedback.danger() > 0.01f) {
			// Ele já está perto: o coração de pânico assume, para não tocar dois batimentos.
			return;
		}
		double distance = nearestDistance(mc);
		if (distance < 0 || --cooldown > 0) {
			return;
		}
		// 0 = colado em você, 1 = no limite do alcance.
		float far = (float) Mth.clamp((distance - NEAR) / (RANGE - NEAR), 0, 1);
		cooldown = Math.round(Mth.lerp(far, FASTEST, SLOWEST));
		float volume = Mth.lerp(far, 1.0f, 0.25f);
		float pitch = Mth.lerp(far, 1.15f, 0.9f);
		mc.level.playLocalSound(player.getX(), player.getY(), player.getZ(), ModSounds.EAR_HEARTBEAT, SoundSource.PLAYERS, volume, pitch, false);
	}

	/** Distância até o Ouvinte mais próximo, ou -1 se nenhum estiver ao alcance. */
	public static double nearestDistance(Minecraft mc) {
		LocalPlayer player = mc.player;
		List<Listener> nearby = mc.level.getEntitiesOfClass(Listener.class, player.getBoundingBox().inflate(RANGE), Listener::isAlive);
		double best = -1;
		for (Listener listener : nearby) {
			double distance = listener.distanceTo(player);
			if (distance <= RANGE && (best < 0 || distance < best)) {
				best = distance;
			}
		}
		return best;
	}
}
