package com.pedro.silenciototal.client;

import com.pedro.silenciototal.entity.Listener;
import com.pedro.silenciototal.registry.ModSounds;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * Pânico: com o Ouvinte por perto você ouve o próprio coração e as bordas da tela escurecem,
 * pulsando no ritmo. Quanto mais perto ele está e mais perto sua barra está de 10 (o limite em
 * que ele te descobre), mais rápido e mais escuro.
 */
public final class PanicFeedback {
	/** A partir dessa distância o coração começa a bater. */
	public static final double RANGE = 14;
	private static final double CLOSEST = 2.5;
	private static final int SLOWEST = 28;
	private static final int FASTEST = 6;

	private static float danger;
	private static float shownDanger;
	private static float pulse;
	private static int cooldown;

	private PanicFeedback() {
	}

	/** 0 a 1: quão perto de ser descoberto você está agora. */
	public static float danger() {
		return danger;
	}

	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.isPaused()) {
			return;
		}
		danger = computeDanger(mc);
		shownDanger = Mth.lerp(0.15f, shownDanger, danger);
		pulse = Math.max(0, pulse - 0.12f);
		if (danger <= 0.01f) {
			cooldown = 0;
			return;
		}
		if (--cooldown > 0) {
			return;
		}
		cooldown = Math.round(Mth.lerp(danger, SLOWEST, FASTEST));
		pulse = 1;
		mc.level.playLocalSound(player.getX(), player.getY(), player.getZ(), ModSounds.EAR_HEARTBEAT, SoundSource.PLAYERS,
				Mth.lerp(danger, 0.35f, 1.0f), Mth.lerp(danger, 0.95f, 1.2f), false);
	}

	private static float computeDanger(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (!NoiseHud.visible() || player.isCreative() || player.isSpectator() || !player.isAlive()) {
			return 0;
		}
		double nearest = -1;
		boolean hunting = false;
		for (Listener listener : mc.level.getEntitiesOfClass(Listener.class, player.getBoundingBox().inflate(RANGE), Listener::isAlive)) {
			double distance = listener.distanceTo(player);
			if (distance <= RANGE && (nearest < 0 || distance < nearest)) {
				nearest = distance;
				hunting = listener.getState() == Listener.HUNT || listener.getState() == Listener.ALERT;
			}
		}
		if (nearest < 0) {
			return 0;
		}
		if (hunting) {
			return 1;
		}
		float proximity = 1f - (float) Mth.clamp((nearest - CLOSEST) / (RANGE - CLOSEST), 0, 1);
		// Barra em 0: só o susto de ele estar perto. Chegando em 10 (o limite): pânico total.
		float edge = Mth.clamp(NoiseHud.noise() / Listener.QUIET_NOISE, 0f, 1f);
		return proximity * (0.35f + 0.65f * edge);
	}

	/** Bordas escuras (meio avermelhadas) pulsando com o coração. */
	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (shownDanger <= 0.02f) {
			return;
		}
		float strength = shownDanger * (0.75f + 0.25f * pulse);
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int band = (int) (Math.min(width, height) * (0.18f + 0.14f * shownDanger));
		int steps = 16;
		for (int i = 0; i < steps; i++) {
			float t = 1f - (float) i / steps;
			int alpha = (int) (200 * strength * t * t);
			if (alpha <= 0) {
				continue;
			}
			int color = alpha << 24 | 0x0A0000;
			int a = band * i / steps;
			int b = band * (i + 1) / steps;
			graphics.fill(0, a, width, b, color);                        // topo
			graphics.fill(0, height - b, width, height - a, color);      // base
			graphics.fill(a, b, b, height - b, color);                   // esquerda
			graphics.fill(width - b, b, width - a, height - b, color);   // direita
		}
	}
}
