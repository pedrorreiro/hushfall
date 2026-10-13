package com.pedro.silenciototal.registry;

import com.pedro.silenciototal.SilencioTotal;
import com.pedro.silenciototal.pup.ListenerPupMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

public final class ModMenus {
	/** Painel do filhote. Os dados de abertura são o id do filhote, para a tela mostrar o retrato dele. */
	public static final ExtendedMenuType<ListenerPupMenu, Integer> LISTENER_PUP = Registry.register(BuiltInRegistries.MENU,
			SilencioTotal.id("listener_pup"), new ExtendedMenuType<>(ListenerPupMenu::new, ByteBufCodecs.VAR_INT));

	private ModMenus() {
	}

	public static void init() {
	}
}
