package com.pedro.silenciototal.noise;

import com.pedro.silenciototal.ModConfig;
import com.pedro.silenciototal.entity.Listener;
import com.pedro.silenciototal.mixin.ServerPlayerGameModeAccessor;
import com.pedro.silenciototal.network.NoisePayload;
import com.pedro.silenciototal.registry.ModItems;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A barra de ruído de cada jogador (0 a 100). Só existe à noite.
 *
 * <p>Passos somam continuamente (andar +5/s, correr +10/s, agachado +0), bater num bloco também
 * (pedra +5/s, madeira +4/s, resto +2/s) e ações somam de uma vez
 * (pular, quebrar bloco, abrir baú, combate, explosão). Parado ou agachado o ruído cai 4/s.
 * Só andar nunca passa de {@link #WALK_CAP}: quem anda muito mantém o Ouvinte sempre investigando
 * por perto; correr (ou pular, quebrar blocos...) é o que dispara a caçada.
 */
public final class NoiseTracker {
	public static final float WALK_PER_SECOND = 5f;
	public static final float SPRINT_PER_SECOND = 10f;
	public static final float DECAY_PER_SECOND = 4f;
	public static final float WALK_CAP = 70f;

	public static final float SOFT_FLOOR = 0.5f;
	public static final float LOUD_FLOOR = 1.5f;
	public static final float WATER = 0.5f;
	public static final float FELT_BOOTS = 0.4f;
	public static final float RAIN = 0.7f;
	public static final float THUNDER = 0.5f;

	private static final int HEARING_INTERVAL = 10;
	private static final Map<UUID, PlayerNoise> NOISE = new HashMap<>();

	private NoiseTracker() {
	}

	private static final class PlayerNoise {
		float noise;
		Vec3 lastPos;
		boolean active;
		float sentNoise = -1;
		byte sentFlags = -1;
		long warnedNight = -1;
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickPlayer(player);
		}
	}

	private static void tickPlayer(ServerPlayer player) {
		PlayerNoise data = NOISE.computeIfAbsent(player.getUUID(), u -> new PlayerNoise());
		ServerLevel level = player.level();
		if (NightCycle.isNight(level) && player.isAlive()) {
			announceNight(player, data);
		}
		// O medidor e o ruído só existem nas noites em que o Ouvinte vem.
		boolean active = NightCycle.isHuntingNight(level) && !player.isCreative() && !player.isSpectator() && player.isAlive();
		Vec3 pos = player.position();
		Vec3 last = data.lastPos == null ? pos : data.lastPos;
		data.lastPos = pos;

		if (!active) {
			data.active = false;
			data.noise = 0;
			sync(player, data);
			return;
		}
		data.active = true;

		float footsteps = footstepsPerTick(player, pos.subtract(last));
		float hitting = hittingPerTick(player);
		if (hitting > 0) {
			// Batendo num bloco (árvore, pedra...): cada golpe faz barulho e o ruído não baixa.
			data.noise += hitting * environment(player);
		} else if (footsteps > 0) {
			float cap = player.isSprinting() ? NoiseLevel.MAX : WALK_CAP;
			if (data.noise < cap) {
				data.noise = Math.min(cap, data.noise + footsteps * environment(player));
			} else {
				// Acima do teto dos passos (ex.: correu e depois voltou a andar), o ruído vai baixando até o teto.
				data.noise = Math.max(cap, data.noise - DECAY_PER_SECOND / 20f);
			}
		} else {
			data.noise -= DECAY_PER_SECOND / 20f;
		}
		data.noise = clamp(data.noise);

		if (player.tickCount % HEARING_INTERVAL == 0) {
			broadcast(player, data.noise);
		}
		sync(player, data);
	}

	/** Ruído de passos neste tick, já com piso e botas (sem chuva). */
	private static float footstepsPerTick(ServerPlayer player, Vec3 delta) {
		double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		if (horizontal < 0.02 || player.isPassenger() && !(player.getVehicle() instanceof net.minecraft.world.entity.LivingEntity)) {
			return 0;
		}
		if (player.isFallFlying() || player.getAbilities().flying) {
			return 0;
		}
		boolean inWater = player.isInWater();
		if (!player.onGround() && !inWater && !player.isPassenger()) {
			return 0;
		}
		if (player.isShiftKeyDown() || player.isCrouching()) {
			return 0;
		}
		boolean running = player.isSprinting() || player.isPassenger();
		float perTick = (running ? SPRINT_PER_SECOND : WALK_PER_SECOND) / 20f;
		if (inWater) {
			perTick *= WATER;
		} else {
			perTick *= floorMultiplier(player);
		}
		if (player.getItemBySlot(EquipmentSlot.FEET).is(ModItems.FELT_BOOTS)) {
			perTick *= FELT_BOOTS;
		}
		return perTick;
	}

	/** Ruído de golpes num bloco que está sendo quebrado (por tick). */
	private static float hittingPerTick(ServerPlayer player) {
		ServerPlayerGameModeAccessor mode = (ServerPlayerGameModeAccessor) player.gameMode;
		if (!mode.silenciototal$isDestroyingBlock()) {
			return 0;
		}
		BlockState state = player.level().getBlockState(mode.silenciototal$destroyPos());
		if (state.isAir()) {
			return 0;
		}
		return NoiseSources.hitNoisePerSecond(state) / 20f;
	}

	/** Lã, tapete e neve abafam; cascalho e vidro amplificam. */
	public static float floorMultiplier(ServerPlayer player) {
		BlockState feet = player.level().getBlockState(player.blockPosition());
		BlockState below = player.getBlockStateOn();
		if (muffles(feet) || muffles(below)) {
			return SOFT_FLOOR;
		}
		if (amplifies(below)) {
			return LOUD_FLOOR;
		}
		return 1f;
	}

	private static boolean muffles(BlockState state) {
		return state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS)
				|| state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW);
	}

	private static boolean amplifies(BlockState state) {
		return state.is(ConventionalBlockTags.GRAVELS) || state.is(Blocks.GRAVEL) || state.is(Blocks.SUSPICIOUS_GRAVEL)
				|| state.is(ConventionalBlockTags.GLASS_BLOCKS) || state.is(ConventionalBlockTags.GLASS_PANES);
	}

	/** Chuva disfarça tudo (-30%), trovoada ainda mais (-50%). Vale para passos e ações. */
	public static float environment(ServerPlayer player) {
		ServerLevel level = player.level();
		float multiplier = ModConfig.get().noiseMultiplier;
		if (level.isThundering()) {
			multiplier *= THUNDER;
		} else if (level.isRaining()) {
			multiplier *= RAIN;
		}
		return multiplier;
	}

	/** Soma ruído de uma ação (pular, quebrar bloco, abrir porta, combate, explosão...). */
	public static void add(ServerPlayer player, float amount) {
		PlayerNoise data = NOISE.get(player.getUUID());
		if (data == null || !data.active || amount <= 0) {
			return;
		}
		float before = data.noise;
		data.noise = clamp(data.noise + amount * environment(player));
		// Um pico de barulho é ouvido na hora, sem esperar o próximo ciclo de audição.
		if (NoiseLevel.of(data.noise) != NoiseLevel.of(before) && NoiseLevel.of(data.noise) != NoiseLevel.LOW) {
			broadcast(player, data.noise);
		}
	}

	public static float get(ServerPlayer player) {
		PlayerNoise data = NOISE.get(player.getUUID());
		return data == null ? 0 : data.noise;
	}

	/** Só para testes e comandos: força o valor da barra. */
	public static void set(ServerPlayer player, float noise) {
		PlayerNoise data = NOISE.computeIfAbsent(player.getUUID(), u -> new PlayerNoise());
		data.noise = clamp(noise);
		if (data.active) {
			broadcast(player, data.noise);
		}
	}

	/** O Ouvinte escuta conforme a faixa: alto = caça, médio = investiga a região, baixo = só colado nele. */
	private static void broadcast(ServerPlayer player, float noise) {
		double radius = NoiseLevel.hearingRadius(noise);
		if (radius <= 0) {
			return;
		}
		ServerLevel level = player.level();
		for (Listener listener : level.getEntitiesOfClass(Listener.class, player.getBoundingBox().inflate(radius))) {
			if (listener.distanceToSqr(player) <= radius * radius) {
				listener.hearPlayer(player, noise);
			}
		}
	}

	private static void announceNight(ServerPlayer player, PlayerNoise data) {
		ServerLevel level = player.level();
		long night = NightCycle.day(level);
		if (data.warnedNight == night) {
			return;
		}
		data.warnedNight = night;
		Component message;
		if (NightCycle.isHuntingNight(level)) {
			message = Component.translatable("message.silenciototal.night_falls").withStyle(ChatFormatting.DARK_AQUA);
		} else if (night < ModConfig.get().graceNights) {
			message = Component.translatable("message.silenciototal.grace_night").withStyle(ChatFormatting.GRAY);
		} else {
			message = Component.translatable("message.silenciototal.calm_night").withStyle(ChatFormatting.GRAY);
		}
		player.sendOverlayMessage(message);
	}

	private static void sync(ServerPlayer player, PlayerNoise data) {
		ServerLevel level = player.level();
		byte flags = 0;
		if (data.active) {
			flags |= NoisePayload.NIGHT;
		}
		if (data.active && level.isRaining()) {
			flags |= NoisePayload.RAIN;
		}
		boolean changed = flags != data.sentFlags || Math.abs(data.noise - data.sentNoise) >= 0.5f
				|| data.noise == 0 && data.sentNoise != 0;
		if (!changed || !ServerPlayNetworking.canSend(player, NoisePayload.TYPE)) {
			return;
		}
		data.sentFlags = flags;
		data.sentNoise = data.noise;
		ServerPlayNetworking.send(player, new NoisePayload(data.noise, flags));
	}

	private static float clamp(float noise) {
		return Math.max(0, Math.min(NoiseLevel.MAX, noise));
	}

	public static void forget(UUID player) {
		NOISE.remove(player);
	}

	public static void clear() {
		NOISE.clear();
	}
}
