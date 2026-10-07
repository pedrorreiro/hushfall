package com.pedro.silenciototal.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pedro.silenciototal.SilencioTotal;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** config/silenciototal-client.json: só o que é do jogador (onde e como o medidor aparece). */
public final class ClientConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ClientConfig instance = new ClientConfig();

	public static final float MIN_SCALE = 0.5f;
	public static final float MAX_SCALE = 2.0f;
	public static final int MAX_OFFSET = 200;

	public enum Corner {
		TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

		public boolean right() {
			return this == TOP_RIGHT || this == BOTTOM_RIGHT;
		}

		public boolean bottom() {
			return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
		}
	}

	/** Canto da tela em que o medidor fica. */
	public Corner corner = Corner.TOP_LEFT;
	/** Distância da borda lateral, em pixels da interface. */
	public int offsetX = 6;
	/** Distância da borda de cima (ou de baixo), em pixels da interface. */
	public int offsetY = 6;
	/** Tamanho do medidor (1 = normal). */
	public float scale = 1.0f;
	/** Mostra a faixa e o valor ao lado do anel ("Audível · 52"). */
	public boolean showText = true;

	public static ClientConfig get() {
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(SilencioTotal.MOD_ID + "-client.json");
	}

	public static void load() {
		Path path = path();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				ClientConfig loaded = GSON.fromJson(reader, ClientConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} catch (Exception e) {
				SilencioTotal.LOGGER.error("Não foi possível ler {}, usando valores padrão", path, e);
			}
		}
		instance.sanitize();
		save();
	}

	public static void save() {
		instance.sanitize();
		try (Writer writer = Files.newBufferedWriter(path())) {
			GSON.toJson(instance, writer);
		} catch (IOException e) {
			SilencioTotal.LOGGER.error("Não foi possível salvar {}", path(), e);
		}
	}

	private void sanitize() {
		if (corner == null) {
			corner = Corner.TOP_LEFT;
		}
		offsetX = Math.max(0, Math.min(MAX_OFFSET, offsetX));
		offsetY = Math.max(0, Math.min(MAX_OFFSET, offsetY));
		scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
	}

	public ClientConfig copy() {
		ClientConfig copy = new ClientConfig();
		copy.corner = corner;
		copy.offsetX = offsetX;
		copy.offsetY = offsetY;
		copy.scale = scale;
		copy.showText = showText;
		return copy;
	}

	/** Troca a configuração atual (a tela de opções edita uma cópia e só aplica ao clicar em Concluído). */
	public static void set(ClientConfig config) {
		instance = config;
		save();
	}
}
