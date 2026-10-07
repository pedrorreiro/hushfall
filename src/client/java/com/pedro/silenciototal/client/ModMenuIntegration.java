package com.pedro.silenciototal.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Botão de configurações na lista do Mod Menu (opcional: sem o Mod Menu esta classe nunca é carregada). */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return HudConfigScreen::new;
	}
}
