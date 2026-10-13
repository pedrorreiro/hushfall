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
import org.jspecify.annotations.Nullable;

/**
 * Medidor de ruído redondo num canto da tela (superior esquerdo por padrão, ajustável em
 * {@link ClientConfig}). Só aparece à noite.
 * Um anel que enche no sentido horário a partir do topo (marcas em 30 e 70), com a orelha no
 * meio e, ao lado, a faixa e o valor.
 */
public final class NoiseHud {
	private static final int TEXT_GAP = 5;
	private static final float OUTER = 13.5f;
	private static final float INNER = 9.5f;
	private static final int SIZE = (int) Math.ceil(OUTER * 2);

	private static final int TRACK = 0xB0202228;
	private static final int BACKDROP = 0x90000000;
	private static final int MARK = 0xFF0A0A0A;
	/**
	 * A orelha do meio. Criada na primeira vez que dá: na tela de título (opções abertas pelo Mod
	 * Menu) os itens ainda não estão prontos e criar um ItemStack derruba o jogo.
	 */
	private static @Nullable ItemStack ear;

	/** Segundos que o medidor leva para sumir quando a noite acaba ou o Ouvinte morre. */
	private static final float FADE_SECONDS = 1.5f;

	private static float noise;
	private static float shown;
	private static byte flags;
	/** Opacidade do medidor: 1 enquanto a noite dura, depois cai até 0. */
	private static float fade;
	private static long lastFrame;

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
		fade = 0;
		lastFrame = 0;
	}

	public static float noise() {
		return noise;
	}

	public static boolean visible() {
		return (flags & NoisePayload.NIGHT) != 0;
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		long now = System.nanoTime();
		float seconds = lastFrame == 0 ? 0 : (now - lastFrame) / 1_000_000_000f;
		lastFrame = now;
		fade = visible() ? 1 : Math.max(0, fade - seconds / FADE_SECONDS);
		if (fade <= 0 || mc.player == null) {
			return;
		}
		// Sumindo, o anel esvazia junto: o silêncio volta.
		shown = Mth.lerp(0.25f, shown, noise);
		draw(graphics, ClientConfig.get(), noise, shown, status(), mc.player.tickCount, fade);
	}

	/**
	 * Desenha o medidor no canto e com o tamanho da configuração. Também usado pela tela de opções,
	 * para mostrar como fica antes de salvar.
	 *
	 * @param value  o ruído escrito ao lado do anel
	 * @param shown  o ruído que o anel mostra (suavizado)
	 * @param status a linha de baixo ("A chuva abafa"), ou {@code null}
	 */
	public static void draw(GuiGraphicsExtractor graphics, ClientConfig config, float value, float shown, @Nullable Component status, int tickCount) {
		draw(graphics, config, value, shown, status, tickCount, 1);
	}

	/** @param alpha opacidade de 0 a 1 (o medidor sumindo) */
	public static void draw(GuiGraphicsExtractor graphics, ClientConfig config, float value, float shown, @Nullable Component status, int tickCount,
			float alpha) {
		Font font = Minecraft.getInstance().font;
		NoiseLevel level = NoiseLevel.of(value);
		int color = switch (level) {
			case LOW -> 0xFF4FBF6A;
			case MEDIUM -> 0xFFE0B13A;
			case HIGH -> 0xFFD8443A;
		};
		if (level == NoiseLevel.HIGH && (tickCount / 4) % 2 == 0) {
			color = 0xFFFF7A6E;
		}
		Component label = Component.translatable("hud.silenciototal.level." + level.name().toLowerCase())
				.append(Component.literal(" · " + Math.round(value)));
		int textWidth = config.showText ? Math.max(font.width(label), status == null ? 0 : font.width(status)) : 0;
		int width = SIZE + (config.showText ? TEXT_GAP + textWidth : 0);

		// Posição no canto escolhido, em pixels já escalados; o desenho em si é feito sem escala.
		float scale = config.scale;
		float x = config.corner.right() ? graphics.guiWidth() - config.offsetX - width * scale : config.offsetX;
		float y = config.corner.bottom() ? graphics.guiHeight() - config.offsetY - SIZE * scale : config.offsetY;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);

		// Anel à esquerda e texto à direita; nos cantos da direita, o texto vai para o lado de dentro.
		int ringX = config.corner.right() ? width - SIZE : 0;
		drawRing(graphics, ringX, 0, Mth.clamp(shown / NoiseLevel.MAX, 0f, 1f), color, alpha);

		// No centro, a orelha: "isto é o quanto ele consegue te ouvir".
		// O item não tem transparência: a orelha sai na primeira metade do sumiço.
		ItemStack ear = ear();
		if (ear != null && alpha > 0.5f) {
			graphics.fakeItem(ear, ringX + SIZE / 2 - 8, SIZE / 2 - 8);
		}

		// Ao lado: a faixa e o valor ("Audível · 52") e, embaixo, o que está mexendo no ruído.
		// Texto quase transparente é desenhado opaco pela fonte do jogo: some de vez antes disso.
		if (config.showText && alpha > 0.05f) {
			int textY = status == null ? SIZE / 2 - 4 : SIZE / 2 - 9;
			int labelX = config.corner.right() ? textWidth - font.width(label) : SIZE + TEXT_GAP;
			graphics.text(font, label, labelX, textY, faded(color, alpha), true);
			if (status != null) {
				int statusX = config.corner.right() ? textWidth - font.width(status) : SIZE + TEXT_GAP;
				graphics.text(font, status, statusX, textY + 10, faded(statusColor(), alpha), true);
			}
		}
		graphics.pose().popMatrix();
	}

	private static @Nullable ItemStack ear() {
		if (ear == null && ModItems.LISTENER_EAR.builtInRegistryHolder().areComponentsBound()) {
			ear = new ItemStack(ModItems.LISTENER_EAR);
		}
		return ear;
	}

	private static @Nullable Component status() {
		if ((flags & NoisePayload.RAIN) != 0) {
			return Component.translatable("hud.silenciototal.rain");
		}
		return null;
	}

	private static int statusColor() {
		return 0xFF8FA8D8;
	}

	/**
	 * Desenha o anel pixel a pixel (a GUI só sabe desenhar retângulos), juntando pixels vizinhos
	 * da mesma cor numa linha em um único retângulo.
	 */
	private static void drawRing(GuiGraphicsExtractor graphics, int x0, int y0, float fraction, int color, float alpha) {
		float c = OUTER;
		for (int py = 0; py < SIZE; py++) {
			int runStart = -1;
			int runColor = 0;
			for (int px = 0; px <= SIZE; px++) {
				int pixel = px < SIZE ? faded(pixelColor(px + 0.5f - c, py + 0.5f - c, fraction, color), alpha) : 0;
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

	/** A cor com o alfa multiplicado; 0 (nada) se ficar invisível. */
	private static int faded(int argb, float alpha) {
		if (alpha >= 1 || argb == 0) {
			return argb;
		}
		int a = Math.round((argb >>> 24) * alpha);
		return a <= 3 ? 0 : a << 24 | argb & 0xFFFFFF;
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
