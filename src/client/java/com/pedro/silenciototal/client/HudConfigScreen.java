package com.pedro.silenciototal.client;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Opções do medidor de ruído: canto, distância da borda, tamanho e texto. Aberta pelo Mod Menu.
 * O medidor aparece de verdade na tela enquanto você mexe, com o ruído subindo e descendo para
 * mostrar as três cores. Só salva ao clicar em Concluído.
 */
public class HudConfigScreen extends Screen {
	private static final int ROW = 24;
	private static final int WIDGET_WIDTH = 200;

	private final @Nullable Screen parent;
	private ClientConfig editing;
	private int ticks;

	public HudConfigScreen(@Nullable Screen parent) {
		super(Component.translatable("config.silenciototal.title"));
		this.parent = parent;
		this.editing = ClientConfig.get().copy();
	}

	@Override
	protected void init() {
		int x = width / 2 - WIDGET_WIDTH / 2;
		int y = Math.max(40, height / 2 - ROW * 3);

		addRenderableWidget(CycleButton.<ClientConfig.Corner>builder(
						corner -> Component.translatable("config.silenciototal.corner." + corner.name().toLowerCase()), editing.corner)
				.withValues(ClientConfig.Corner.values())
				.create(x, y, WIDGET_WIDTH, 20, Component.translatable("config.silenciototal.corner"),
						(button, corner) -> editing.corner = corner));
		y += ROW;
		addRenderableWidget(new Slider(x, y, Component.translatable("config.silenciototal.scale"),
				ClientConfig.MIN_SCALE, ClientConfig.MAX_SCALE, 0.05, editing.scale,
				value -> Component.literal(Math.round(value * 100) + "%"), value -> editing.scale = (float) value));
		y += ROW;
		addRenderableWidget(new Slider(x, y, Component.translatable("config.silenciototal.offset_x"),
				0, ClientConfig.MAX_OFFSET, 1, editing.offsetX,
				value -> Component.literal(String.valueOf((int) value)), value -> editing.offsetX = (int) value));
		y += ROW;
		addRenderableWidget(new Slider(x, y, Component.translatable("config.silenciototal.offset_y"),
				0, ClientConfig.MAX_OFFSET, 1, editing.offsetY,
				value -> Component.literal(String.valueOf((int) value)), value -> editing.offsetY = (int) value));
		y += ROW;
		addRenderableWidget(CycleButton.onOffBuilder(editing.showText)
				.create(x, y, WIDGET_WIDTH, 20, Component.translatable("config.silenciototal.show_text"),
						(button, show) -> editing.showText = show));
		y += ROW;
		addRenderableWidget(Button.builder(Component.translatable("config.silenciototal.reset"), button -> {
			editing = new ClientConfig();
			rebuildWidgets();
		}).bounds(x, y, WIDGET_WIDTH, 20).build());

		int bottom = height - 28;
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
				.bounds(width / 2 - 154, bottom, 150, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
			ClientConfig.set(editing);
			onClose();
		}).bounds(width / 2 + 4, bottom, 150, 20).build());
	}

	@Override
	public void tick() {
		ticks++;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// Prévia de verdade, no lugar onde vai ficar: o ruído sobe e desce passando pelas três faixas.
		// Desenhada antes dos botões, para não cobrir nenhum.
		float time = ticks + partialTick;
		float value = 50 + 45 * (float) Math.sin(time * 0.04f);
		NoiseHud.draw(graphics, editing, value, value, null, ticks);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 15, 0xFFFFFFFF);
		graphics.centeredText(font, Component.translatable("config.silenciototal.preview"), width / 2, 28, 0xFFA0A0A0);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	/** Slider com limites, passo e texto próprios ("Tamanho: 100%"). */
	private static class Slider extends AbstractSliderButton {
		private final Component label;
		private final double min;
		private final double max;
		private final double step;
		private final DoubleFunction<Component> format;
		private final DoubleConsumer onChange;

		Slider(int x, int y, Component label, double min, double max, double step, double current,
				DoubleFunction<Component> format, DoubleConsumer onChange) {
			super(x, y, WIDGET_WIDTH, 20, Component.empty(), (current - min) / (max - min));
			this.label = label;
			this.min = min;
			this.max = max;
			this.step = step;
			this.format = format;
			this.onChange = onChange;
			updateMessage();
		}

		private double current() {
			double raw = min + value * (max - min);
			return Mth.clamp(Math.round(raw / step) * step, min, max);
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.empty().append(label).append(": ").append(format.apply(current())));
		}

		@Override
		protected void applyValue() {
			onChange.accept(current());
		}
	}
}
