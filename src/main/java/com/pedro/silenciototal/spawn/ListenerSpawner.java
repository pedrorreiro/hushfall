package com.pedro.silenciototal.spawn;

import com.pedro.silenciototal.ModConfig;
import com.pedro.silenciototal.entity.Listener;
import com.pedro.silenciototal.noise.NightCycle;
import com.pedro.silenciototal.registry.ModAttachments;
import com.pedro.silenciototal.registry.ModEntities;
import com.pedro.silenciototal.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Existe um único Ouvinte no mundo. Ele surge logo ao anoitecer, longe dos jogadores (40 a 60
 * blocos, nunca a menos de 30 de ninguém), e solta um rosnado distante: o primeiro aviso da noite.
 *
 * <ul>
 *   <li>Ao amanhecer ele se enterra e volta na noite seguinte com a vida cheia.</li>
 *   <li>Se for morto, só volta na noite seguinte.</li>
 *   <li>Se ficar longe de todo mundo (chunk descarregado), ressurge perto de um jogador com a mesma vida.</li>
 * </ul>
 */
public final class ListenerSpawner {
	private static final int INTERVAL = 20;
	private static final int ATTEMPTS = 12;

	private ListenerSpawner() {
	}

	public static ListenerState state(MinecraftServer server) {
		return server.globalAttachments().getAttachedOrElse(ModAttachments.LISTENER_STATE, ListenerState.EMPTY);
	}

	public static void setState(MinecraftServer server, ListenerState state) {
		server.globalAttachments().setAttached(ModAttachments.LISTENER_STATE, state);
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % INTERVAL != 0) {
			return;
		}
		ServerLevel level = server.getLevel(Level.OVERWORLD);
		if (level == null || !canSpawn(level)) {
			return;
		}
		ListenerState state = state(server);
		long night = NightCycle.day(level);
		if (state.killedNight() == night) {
			return;
		}
		// Já tem um Ouvinte ativo? Então está tudo certo (duplicatas se removem sozinhas no tick delas).
		// Se ele está num chunk carregado mas congelado (fora da distância de simulação), não serve:
		// ele some de lá e ressurge perto de alguém com a mesma vida.
		for (Listener listener : level.getEntities(ModEntities.LISTENER, l -> l.isAlive())) {
			if (state.isCurrent(listener.getUUID())) {
				if (level.isPositionEntityTicking(listener.blockPosition())) {
					return;
				}
				state = state.withHealth(listener.getHealth());
				setState(server, state);
				listener.discard();
			}
		}
		List<ServerPlayer> candidates = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isCreative() && !player.isSpectator() && player.isAlive()) {
				candidates.add(player);
			}
		}
		if (candidates.isEmpty()) {
			return;
		}
		ServerPlayer target = candidates.get(level.getRandom().nextInt(candidates.size()));
		float health = state.night() == night ? state.health() : -1;
		spawnNear(level, target, health);
	}

	public static boolean canSpawn(ServerLevel level) {
		return NightCycle.isHuntingNight(level)
				&& level.getDifficulty() != Difficulty.PEACEFUL
				&& level.getGameRules().get(GameRules.SPAWN_MONSTERS);
	}

	/** Faz o Ouvinte surgir longe do jogador e o registra como o único do mundo. Público para os testes. */
	public static @Nullable Listener spawnNear(ServerLevel level, ServerPlayer player, float health) {
		RandomSource random = level.getRandom();
		ModConfig config = ModConfig.get();
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			float angle = random.nextFloat() * Mth.TWO_PI;
			double distance = config.minSpawnDistance + random.nextDouble() * (config.maxSpawnDistance - config.minSpawnDistance);
			int x = Mth.floor(player.getX() + Mth.cos(angle) * distance);
			int z = Mth.floor(player.getZ() + Mth.sin(angle) * distance);
			BlockPos pos = findFloor(level, player, x, z);
			if (pos == null || !farFromEveryone(level, pos, config.minSpawnDistance)) {
				continue;
			}
			Listener listener = ModEntities.LISTENER.spawn(level, pos, EntitySpawnReason.EVENT);
			if (listener != null) {
				if (health > 0) {
					listener.setHealth(Math.min(health, listener.getMaxHealth()));
				}
				MinecraftServer server = level.getServer();
				setState(server, state(server).withCurrent(listener.getUUID(), NightCycle.day(level), listener.getHealth()));
				level.playSound(null, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, ModSounds.LISTENER_EMERGE,
						SoundSource.HOSTILE, 4.0f, 0.8f + random.nextFloat() * 0.2f);
				return listener;
			}
		}
		return null;
	}

	/** Chamado pelo Ouvinte quando morre: só volta na noite seguinte. */
	public static void onKilled(ServerLevel level) {
		MinecraftServer server = level.getServer();
		setState(server, state(server).killed(NightCycle.day(level)));
	}

	/** Diferença máxima de altura entre o Ouvinte e o jogador ao surgir. */
	private static final int MAX_LAYER_GAP = 10;

	/**
	 * Procura chão na mesma "camada" do jogador: na superfície se ele está na superfície, na
	 * caverna se ele está numa caverna. Nunca a mais de {@link #MAX_LAYER_GAP} blocos de altura dele,
	 * para não surgir preso numa caverna embaixo de quem está num morro (ou vice-versa).
	 */
	private static @Nullable BlockPos findFloor(ServerLevel level, ServerPlayer player, int x, int z) {
		int playerY = Mth.floor(player.getY());
		BlockPos column = new BlockPos(x, playerY, z);
		if (!level.isLoaded(column) || !level.isPositionEntityTicking(column)) {
			return null;
		}
		if (!isUnderground(level, player)) {
			BlockPos surface = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			return Math.abs(surface.getY() - playerY) <= MAX_LAYER_GAP && isValidSpot(level, surface) ? surface : null;
		}
		for (int dy = 0; dy <= MAX_LAYER_GAP; dy++) {
			for (int sign : new int[]{1, -1}) {
				BlockPos pos = new BlockPos(x, playerY + dy * sign, z);
				if (isValidSpot(level, pos)) {
					return pos;
				}
			}
		}
		return null;
	}

	/** Jogador bem abaixo da superfície (caverna, mina). */
	public static boolean isUnderground(ServerLevel level, ServerPlayer player) {
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.getBlockX(), player.getBlockZ());
		return player.getY() < surface - 6;
	}

	private static boolean isValidSpot(ServerLevel level, BlockPos pos) {
		if (level.isOutsideBuildHeight(pos) || level.isOutsideBuildHeight(pos.above(2))) {
			return false;
		}
		BlockPos below = pos.below();
		if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
			return false;
		}
		if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) {
			return false;
		}
		AABB box = ModEntities.LISTENER.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		return level.noCollision(box);
	}

	private static boolean farFromEveryone(ServerLevel level, BlockPos pos, double minDistance) {
		Vec3 center = Vec3.atBottomCenterOf(pos);
		for (ServerPlayer other : level.players()) {
			if (!other.isSpectator() && other.position().distanceToSqr(center) < minDistance * minDistance) {
				return false;
			}
		}
		return true;
	}
}
