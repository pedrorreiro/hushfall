package com.pedro.silenciototal.noise;

import com.pedro.silenciototal.entity.Listener;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Sons no mundo que não vêm de um jogador: distrações (sino, note block, pistão, dispensador,
 * projéteis caindo) e estrondos (explosões, raios). O Ouvinte vai investigar a origem.
 *
 * <p>Cada ponto tem cooldown: o mesmo bloco tocando sem parar só conta de tempos em tempos,
 * então um relógio de redstone não prende a criatura para sempre.
 */
public final class WorldSounds {
	public enum Kind {
		NOTE_BLOCK(24, 100),
		BELL(48, 200),
		PISTON(16, 100),
		DISPENSER(20, 100),
		PROJECTILE(12, 40),
		EXPLOSION(64, 20),
		LIGHTNING(64, 20);

		/** Até onde o som é ouvido, em blocos. Também é a "força" do som. */
		public final float loudness;
		/** Ticks até o mesmo bloco poder chamar a atenção de novo. */
		public final int cooldown;

		Kind(float loudness, int cooldown) {
			this.loudness = loudness;
			this.cooldown = cooldown;
		}
	}

	/** Raio em que uma explosão soma ruído na barra dos jogadores. */
	public static final double EXPLOSION_PLAYER_RADIUS = 10;
	public static final float EXPLOSION_PLAYER_NOISE = 40;

	private static final Map<ResourceKey<Level>, Long2LongOpenHashMap> LAST_SOUND = new HashMap<>();

	private WorldSounds() {
	}

	public static boolean emit(ServerLevel level, Vec3 pos, Kind kind) {
		if (!NightCycle.isNight(level)) {
			return false;
		}
		long now = level.getGameTime();
		Long2LongOpenHashMap last = LAST_SOUND.computeIfAbsent(level.dimension(), k -> new Long2LongOpenHashMap());
		long key = BlockPos.containing(pos).asLong();
		if (last.containsKey(key) && now - last.get(key) < kind.cooldown) {
			return false;
		}
		last.put(key, now);
		double radius = kind.loudness;
		for (Listener listener : level.getEntitiesOfClass(Listener.class, new AABB(pos, pos).inflate(radius))) {
			if (listener.distanceToSqr(pos) <= radius * radius) {
				listener.hearSound(pos, kind.loudness);
			}
		}
		return true;
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 600 != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Long2LongOpenHashMap last = LAST_SOUND.get(level.dimension());
			if (last != null) {
				long now = level.getGameTime();
				last.long2LongEntrySet().removeIf(entry -> now - entry.getLongValue() > 600);
			}
		}
	}

	public static void clear() {
		LAST_SOUND.clear();
	}
}
