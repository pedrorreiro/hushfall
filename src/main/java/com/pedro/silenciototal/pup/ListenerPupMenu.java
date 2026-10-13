package com.pedro.silenciototal.pup;

import com.pedro.silenciototal.ModConfig;
import com.pedro.silenciototal.entity.ListenerPup;
import com.pedro.silenciototal.registry.ModMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Painel do filhote: um espaço para o minério, quantas buscas restam e o botão Procurar.
 * É um menu do próprio jogo, então devolver o item ao fechar e sincronizar os números já vêm prontos.
 */
public class ListenerPupMenu extends AbstractContainerMenu {
	/** Botão Procurar (o id que vai em {@code clickMenuButton}). */
	public static final int SEARCH_BUTTON = 0;

	/** Pode procurar. */
	public static final int READY = 0;
	/** Noite de caça com o Ouvinte vivo: ele se recusa a fazer barulho. */
	public static final int REFUSES = 1;
	/** Já está numa busca. */
	public static final int BUSY = 2;

	/** Quanto o painel é mais alto que o do cavalo: a linha do aviso, entre o filhote e o inventário. */
	public static final int MESSAGE_HEIGHT = 14;

	private static final int SEARCHES_LEFT = 0;
	private static final int MAX_SEARCHES = 1;
	private static final int STATUS = 2;

	private final Container container = new SimpleContainer(1);
	private final ContainerData data;
	/** No servidor é o filhote; no cliente é o mesmo filhote achado pelo id (só para o retrato), ou null. */
	private final @Nullable ListenerPup pup;

	/** Lado do cliente: o id do filhote vem do servidor ao abrir. */
	public ListenerPupMenu(int containerId, Inventory inventory, int pupId) {
		this(containerId, inventory, inventory.player.level().getEntity(pupId) instanceof ListenerPup found ? found : null,
				new SimpleContainerData(3));
	}

	/** Lado do servidor. */
	public ListenerPupMenu(int containerId, Inventory inventory, ListenerPup pup) {
		this(containerId, inventory, pup, new ContainerData() {
			@Override
			public int get(int index) {
				return switch (index) {
					case SEARCHES_LEFT -> pup.searchesLeft();
					case MAX_SEARCHES -> ModConfig.get().pupSearches;
					default -> status(pup);
				};
			}

			@Override
			public void set(int index, int value) {
			}

			@Override
			public int getCount() {
				return 3;
			}
		});
	}

	private ListenerPupMenu(int containerId, Inventory inventory, @Nullable ListenerPup pup, ContainerData data) {
		super(ModMenus.LISTENER_PUP, containerId);
		this.pup = pup;
		this.data = data;
		addSlot(new Slot(container, 0, 80, 18) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return PupOres.accepts(stack);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		addStandardInventorySlots(inventory, 8, 84 + MESSAGE_HEIGHT);
		addDataSlots(data);
	}

	private static int status(ListenerPup pup) {
		// O aviso do Ouvinte vem primeiro: guiando em noite de caça ele está parado, escondido.
		if (pup.isListenerAwake()) {
			return REFUSES;
		}
		return pup.isSearching() ? BUSY : READY;
	}

	public @Nullable ListenerPup pup() {
		return pup;
	}

	public int searchesLeft() {
		return data.get(SEARCHES_LEFT);
	}

	public int maxSearches() {
		return data.get(MAX_SEARCHES);
	}

	public int status() {
		return data.get(STATUS);
	}

	public boolean hasItem() {
		return !container.getItem(0).isEmpty();
	}

	public boolean canSearch() {
		return status() == READY && hasItem();
	}

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (buttonId != SEARCH_BUTTON || pup == null || !(player instanceof ServerPlayer serverPlayer)) {
			return false;
		}
		if (!canSearch()) {
			return false;
		}
		// A busca tira 1 item do espaço e fecha o painel.
		return pup.startSearch(serverPlayer, container.getItem(0));
	}

	@Override
	public boolean stillValid(Player player) {
		return pup != null && pup.isAlive() && pup.isOwnedBy(player) && pup.distanceToSqr(player) < 8 * 8;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		clearContainer(player, container);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = slots.get(slotIndex);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack moved = stack.copy();
		if (slotIndex == 0) {
			if (!moveItemStackTo(stack, 1, 37, true)) {
				return ItemStack.EMPTY;
			}
		} else if (PupOres.accepts(stack) && !slots.get(0).hasItem()) {
			if (!moveItemStackTo(stack, 0, 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (slotIndex < 28) {
			if (!moveItemStackTo(stack, 28, 37, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!moveItemStackTo(stack, 1, 28, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == moved.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(player, stack);
		return moved;
	}
}
