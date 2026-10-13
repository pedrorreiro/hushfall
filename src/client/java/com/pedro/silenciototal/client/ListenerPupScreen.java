package com.pedro.silenciototal.client;

import com.pedro.silenciototal.entity.ListenerPup;
import com.pedro.silenciototal.pup.ListenerPupMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Painel do filhote. Usa o fundo do inventário do cavalo (que já tem a moldura do retrato), sem a
 * moldura dos baús e esticado no meio: em cima o filhote à esquerda e, à direita, o espaço do
 * minério, as buscas (uma marquinha por busca) e o botão Procurar; embaixo, uma linha para o aviso
 * quando ele não pode procurar (Ouvinte acordado, ou já procurando).
 */
public class ListenerPupScreen extends AbstractContainerScreen<ListenerPupMenu> {
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/horse.png");
	private static final Identifier SLOT = Identifier.withDefaultNamespace("container/slot");
	private static final int WIDTH = 176;
	/** Altura da parte de cima do fundo do cavalo (título, retrato e área dos baús). */
	private static final int TOP_HEIGHT = 72;
	private static final int HORSE_HEIGHT = 166;
	private static final int EXTRA = ListenerPupMenu.MESSAGE_HEIGHT;
	private static final int BACKGROUND_COLOR = 0xFFC6C6C6;

	private static final int PANEL_X = 79;
	private static final int PANEL_WIDTH = 90;
	/** Marquinhas das buscas: até 10 numa fileira; com mais que isso (config) vira número. */
	private static final int PIPS_X = PANEL_X + 23;
	private static final int PIPS_Y = 29;
	private static final int PIP_WIDTH = 5;
	private static final int PIP_HEIGHT = 6;
	private static final int MAX_PIPS = 10;
	/** Linha do aviso: largura toda, entre o retrato e o "Inventário". */
	private static final int MESSAGE_X = 8;
	private static final int MESSAGE_WIDTH = 160;
	private static final int MESSAGE_Y = TOP_HEIGHT + 2;

	private Button searchButton;

	public ListenerPupScreen(ListenerPupMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HORSE_HEIGHT + EXTRA);
	}

	@Override
	protected void init() {
		super.init();
		searchButton = addRenderableWidget(Button.builder(Component.translatable("gui.silenciototal.pup.search"), button -> {
			if (minecraft.gameMode != null) {
				minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ListenerPupMenu.SEARCH_BUTTON);
			}
		}).bounds(leftPos + PANEL_X, topPos + 50, PANEL_WIDTH, 20).build());
		updateButton();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		updateButton();
	}

	private void updateButton() {
		if (searchButton != null) {
			searchButton.active = menu.canSearch();
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		// Fundo do cavalo em duas partes, com a faixa da mensagem no meio (uma linha lisa repetida).
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0.0f, 0.0f, WIDTH, TOP_HEIGHT, 256, 256);
		for (int i = 0; i < EXTRA; i++) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos + TOP_HEIGHT + i, 0.0f, TOP_HEIGHT, WIDTH, 1, 256, 256);
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos + TOP_HEIGHT + EXTRA, 0.0f, TOP_HEIGHT,
				WIDTH, HORSE_HEIGHT - TOP_HEIGHT, 256, 256);
		// O fundo do cavalo tem a moldura dos espaços do baú aqui; o painel não usa, então apaga.
		graphics.fill(leftPos + PANEL_X, topPos + 17, leftPos + PANEL_X + PANEL_WIDTH + 1, topPos + TOP_HEIGHT, BACKGROUND_COLOR);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, leftPos + PANEL_X, topPos + 17, 18, 18);
		ListenerPup pup = menu.pup();
		if (pup != null) {
			InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, leftPos + 26, topPos + 18, leftPos + 78, topPos + 70,
					32, 0.15f, mouseX, mouseY, pup);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		super.extractLabels(graphics, xm, ym);
		int left = menu.searchesLeft();
		int max = menu.maxSearches();
		graphics.text(font, Component.translatable("gui.silenciototal.pup.searches_label"), PIPS_X, 18, 0xFF404040, false);
		if (max <= MAX_PIPS) {
			for (int i = 0; i < max; i++) {
				int x = PIPS_X + i * (PIP_WIDTH + 1);
				graphics.fill(x, PIPS_Y, x + PIP_WIDTH, PIPS_Y + PIP_HEIGHT, 0xFF373737);
				graphics.fill(x + 1, PIPS_Y + 1, x + PIP_WIDTH - 1, PIPS_Y + PIP_HEIGHT - 1, i < left ? 0xFFB0505C : 0xFF8B8B8B);
			}
		} else {
			graphics.text(font, Component.translatable("gui.silenciototal.pup.searches", left, max), PIPS_X, PIPS_Y - 1, 0xFF404040, false);
		}
		Component message = switch (menu.status()) {
			case ListenerPupMenu.REFUSES -> Component.translatable("gui.silenciototal.pup.refuses");
			case ListenerPupMenu.BUSY -> Component.translatable("gui.silenciototal.pup.busy");
			default -> null;
		};
		if (message != null) {
			int color = menu.status() == ListenerPupMenu.REFUSES ? 0xFF8B2020 : 0xFF555555;
			graphics.text(font, message, MESSAGE_X + (MESSAGE_WIDTH - font.width(message)) / 2, MESSAGE_Y, color, false);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		// Passar o mouse nas marquinhas mostra o número.
		int x = mouseX - leftPos;
		int y = mouseY - topPos;
		if (x >= PIPS_X && x < PANEL_X + PANEL_WIDTH && y >= 17 && y < PIPS_Y + PIP_HEIGHT) {
			graphics.setTooltipForNextFrame(font, Component.translatable("gui.silenciototal.pup.searches_tooltip",
					menu.searchesLeft(), menu.maxSearches()), mouseX, mouseY);
		}
	}
}
