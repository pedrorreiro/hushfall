package com.pedro.silenciototal.client;

import com.pedro.silenciototal.network.NoisePayload;
import com.pedro.silenciototal.noise.NoiseLevel;
import com.pedro.silenciototal.registry.ModItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Medidor de ruído redondo no canto superior esquerdo. Só aparece à noite.
 * Um anel que enche no sentido horário a partir do topo (marcas em 30 e 70), com a orelha no
 * meio e, ao lado, a faixa e o valor.
 */
public final class NoiseHud {
	private static final int MARGIN = 6;
	private static final float OUTER = 13.5f;
	private static final float INNER = 9.5f;
	private static final int SIZE = (int) Math.ceil(OUTER * 2);

	private static final int TRACK = 0xB0202228;
	private static final int BACKDROP = 0x90000000;
	private static final int MARK = 0xFF0A0A0A;
	private static final ItemStack EAR = new ItemStack(ModItems.LISTENER_EAR);

	private static float noise;
	private static float shown;
	private static byte flags;

	private NoiseHud() {
	}

	public static void receive(NoisePayload payload) {
		noise = payload.noise();
		flags = payload.flags();
	}

	public static void reset() {
		noise = 0;
		shown = 0;
		flags = 0;
	}

	public static float noise() {
		return noise;
	}

	public static boolean visible() {
		return (flags & NoisePayload.NIGHT) != 0;
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (!visible() || mc.player == null) {
			return;
		}
		shown = Mth.lerp(0.25f, shown, noise);
		Font font = mc.font;
		NoiseLevel level = NoiseLevel.of(noise);
		int color = switch (level) {
			case LOW -> 0xFF4FBF6A;
			case MEDIUM -> 0xFFE0B13A;
			case HIGH -> 0xFFD8443A;
		};
		if (level == NoiseLevel.HIGH && (mc.player.tickCount / 4) % 2 == 0) {
			color = 0xFFFF7A6E;
		}

		int x0 = MARGIN;
		int y0 = MARGIN;
		drawRing(graphics, x0, y0, Mth.clamp(shown / NoiseLevel.MAX, 0f, 1f), color);

		// No centro, a orelha: "isto é o quanto ele consegue te ouvir".
		graphics.fakeItem(EAR, x0 + SIZE / 2 - 8, y0 + SIZE / 2 - 8);

		// Ao lado: a faixa e o valor ("Audível · 52") e, embaixo, o que está mexendo no ruído.
		int textX = x0 + SIZE + 5;
		Component label = Component.translatable("hud.silenciototal.level." + level.name().toLowerCase())
				.append(Component.literal(" · " + Math.round(noise)));
		Component status = status();
		int textY = status == null ? y0 + SIZE / 2 - 4 : y0 + SIZE / 2 - 9;
		graphics.text(font, label, textX, textY, color, true);
		if (status != null) {
			graphics.text(font, status, textX, textY + 10, statusColor(), true);
		}
	}

	private static Component status() {
		if ((flags & NoisePayload.GRACE) != 0) {
			return Component.translatable("hud.silenciototal.grace");
		}
		if ((flags & NoisePayload.RAIN) != 0) {
			return Component.translatable("hud.silenciototal.rain");
		}
		return null;
	}

	private static int statusColor() {
		return (flags & NoisePayload.GRACE) != 0 ? 0xFF9A9A9A : 0xFF8FA8D8;
	}

	/**
	 * Desenha o anel pixel a pixel (a GUI só sabe desenhar retângulos), juntando pixels vizinhos
	 * da mesma cor numa linha em um único retângulo.
	 */
	private static void drawRing(GuiGraphicsExtractor graphics, int x0, int y0, float fraction, int color) {
		float c = OUTER;
		for (int py = 0; py < SIZE; py++) {
			int runStart = -1;
			int runColor = 0;
			for (int px = 0; px <= SIZE; px++) {
				int pixel = px < SIZE ? pixelColor(px + 0.5f - c, py + 0.5f - c, fraction, color) : 0;
				if (pixel != runColor) {
					if (runColor != 0) {
						graphics.fill(x0 + runStart, y0 + py, x0 + px, y0 + py + 1, runColor);
					}
					runStart = px;
					runColor = pixel;
				}
			}
		}
	}

	private static int pixelColor(float dx, float dy, float fraction, int color) {
		float distance = Mth.sqrt(dx * dx + dy * dy);
		if (distance > OUTER) {
			return 0;
		}
		if (distance < INNER) {
			// Miolo escuro para o número ficar legível.
			return distance < INNER - 0.5f ? BACKDROP : TRACK;
		}
		// Ângulo no sentido horário a partir do topo, de 0 a 1.
		float angle = (float) (Math.atan2(dx, -dy) / (Math.PI * 2));
		if (angle < 0) {
			angle += 1;
		}
		if (Math.abs(angle - 0.30f) < 0.012f || Math.abs(angle - 0.70f) < 0.012f) {
			return MARK;
		}
		return angle <= fraction && fraction > 0 ? color : TRACK;
	}
}
