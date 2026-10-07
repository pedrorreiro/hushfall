package com.pedro.silenciototal.test;

import com.pedro.silenciototal.client.HudConfigScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.Screen;

/**
 * Abre a lista do Mod Menu na tela de título, procura o Hushfall, abre as opções pelo Mod Menu e
 * tira prints das duas telas. Confere que o Mod Menu acha a tela de opções do medidor.
 */
public class ModMenuScreenshots implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			mc.options.languageCode = "pt_br";
			mc.getLanguageManager().setSelected("pt_br");
			mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
		});
		ctx.clickScreenButton("modmenu.title");
		ctx.waitTicks(10);
		ctx.getInput().typeChars("Hushfall");
		ctx.waitTicks(10);
		ctx.takeScreenshot("modmenu_list");
		// O que o botão de opções do Mod Menu faz: pede a tela ao entrypoint "modmenu" do mod.
		// (Chamado por reflexão: o Mod Menu só existe no runtime dos testes, não para compilar.)
		ctx.runOnClient(mc -> {
			try {
				Class<?> modMenu = Class.forName("com.terraformersmc.modmenu.ModMenu");
				Screen config = (Screen) modMenu.getMethod("getConfigScreen", String.class, Screen.class)
						.invoke(null, "silenciototal", mc.gui.screen());
				mc.gui.setScreen(config);
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("não consegui pedir a tela de opções ao Mod Menu", e);
			}
		});
		ctx.waitTicks(20);
		ctx.takeScreenshot("modmenu_config");
		boolean opened = ctx.computeOnClient(mc -> mc.gui.screen() instanceof HudConfigScreen);
		// Fechar (o mesmo que o ESC) volta para a lista do Mod Menu; de lá, para a tela de título.
		ctx.runOnClient(mc -> mc.gui.screen().onClose());
		ctx.waitTicks(5);
		boolean backToList = ctx.computeOnClient(mc -> mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().equals("ModsScreen"));
		ctx.runOnClient(mc -> mc.gui.screen().onClose());
		ctx.waitTicks(5);
		if (!backToList) {
			throw new AssertionError("fechar as opções deveria voltar para a lista do Mod Menu");
		}
		if (!opened) {
			throw new AssertionError("o botão de opções do Mod Menu deveria abrir a tela do medidor");
		}
	}
}
