package com.pedro.silenciototal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** config/silenciototal.json */
public final class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ModConfig instance = new ModConfig();

	/** Noites iniciais sem Ouvinte. A primeira noite de um mundo novo é de graça para dar tempo de achar lã. */
	public int graceNights = 1;
	/** Chance (0 a 1) de cada noite ter o Ouvinte. 0.5 = metade das noites; 1 = toda noite. */
	public float nightChance = 0.5f;
	/** Distância mínima entre o ponto de spawn e qualquer jogador (regra de ouro: nunca spawnar em cima de ninguém). */
	public int minSpawnDistance = 32;
	/** Distância máxima do spawn até o jogador escolhido. */
	public int maxSpawnDistance = 45;
	/** Multiplicador geral do ruído do jogador (0.5 = metade do barulho). */
	public float noiseMultiplier = 1.0f;
	/** Versão do arquivo. Usada para atualizar valores padrão antigos. */
	public int configVersion = CURRENT_VERSION;

	private static final int CURRENT_VERSION = 2;

	public static ModConfig get() {
		return instance;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(SilencioTotal.MOD_ID + ".json");
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				ModConfig loaded = GSON.fromJson(json, ModConfig.class);
				if (loaded != null) {
					if (!json.has("configVersion")) {
						// Versão 1 surgia a 40–60 blocos; agora o padrão é 32–45 (ele chega mais rápido).
						if (loaded.minSpawnDistance == 40 && loaded.maxSpawnDistance == 60) {
							loaded.minSpawnDistance = 32;
							loaded.maxSpawnDistance = 45;
						}
					}
					loaded.configVersion = CURRENT_VERSION;
					instance = loaded;
				}
			} catch (Exception e) {
				SilencioTotal.LOGGER.error("Não foi possível ler {}, usando valores padrão", path, e);
			}
		}
		instance.graceNights = Math.max(0, instance.graceNights);
		// Nunca menos de 30 blocos: o jogador sempre tem chance de ouvir a criatura antes de vê-la.
		instance.minSpawnDistance = Math.max(30, instance.minSpawnDistance);
		instance.maxSpawnDistance = Math.max(instance.minSpawnDistance + 4, instance.maxSpawnDistance);
		instance.noiseMultiplier = Math.max(0f, instance.noiseMultiplier);
		instance.nightChance = Math.max(0f, Math.min(1f, instance.nightChance));
		try (Writer writer = Files.newBufferedWriter(path)) {
			GSON.toJson(instance, writer);
		} catch (IOException e) {
			SilencioTotal.LOGGER.error("Não foi possível salvar {}", path, e);
		}
	}
}
