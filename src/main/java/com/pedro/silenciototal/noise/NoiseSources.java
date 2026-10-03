package com.pedro.silenciototal.noise;

import com.pedro.silenciototal.ModConfig;
import com.pedro.silenciototal.registry.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.fabricmc.fabric.api.util.EventResult;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Ações do jogador que fazem barulho, mais as regras de sono. */
public final class NoiseSources {
	public static final float JUMP = 6;
	public static final float BREAK_STONE = 15;
	public static final float BREAK_WOOD = 10;
	public static final float BREAK_SOFT = 5;
	public static final float OPEN_CONTAINER = 8;
	public static final float COMBAT = 15;

	private static final Player.BedSleepingProblem NEEDS_SILENT_ROOM = new Player.BedSleepingProblem(
			Component.translatable("message.silenciototal.needs_silent_room").withStyle(ChatFormatting.DARK_AQUA));

	private NoiseSources() {
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer serverPlayer) {
				NoiseTracker.add(serverPlayer, breakNoise(state));
			}
		});

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer serverPlayer && !player.isSecondaryUseActive()
					&& isNoisyOpenable(level.getBlockState(hit.getBlockPos()).getBlock())) {
				NoiseTracker.add(serverPlayer, OPEN_CONTAINER);
			}
			return InteractionResult.PASS;
		});

		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			// Adaga Silenciosa: golpe abafado, não faz barulho.
			if (player instanceof ServerPlayer serverPlayer && !player.getMainHandItem().is(ModItems.SILENT_DAGGER)) {
				NoiseTracker.add(serverPlayer, COMBAT);
			}
			return InteractionResult.PASS;
		});

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && source.getEntity() != null && (damageTaken > 0 || blocked)) {
				NoiseTracker.add(player, COMBAT);
			}
		});

		// Dentro de uma sala silenciosa dá para dormir mesmo com o Ouvinte rondando lá fora.
		EntitySleepEvents.ALLOW_NEARBY_MONSTERS.register((player, sleepingPos, vanillaResult) -> {
			if (player instanceof ServerPlayer serverPlayer && NightCycle.isNight(serverPlayer.level())
					&& NoiseTracker.refreshSilentRoom(serverPlayer)) {
				return EventResult.ALLOW;
			}
			return EventResult.PASS;
		});
		EntitySleepEvents.ALLOW_SLEEPING.register((player, sleepingPos) -> {
			if (!ModConfig.get().sleepOnlyInSilentRoom || !(player instanceof ServerPlayer serverPlayer)) {
				return null;
			}
			if (!NightCycle.isHuntingNight(serverPlayer.level()) || NoiseTracker.refreshSilentRoom(serverPlayer)) {
				return null;
			}
			return NEEDS_SILENT_ROOM;
		});
	}

	/** Enquanto você bate num bloco, por segundo: pedra +5, madeira +4, resto +2 (lã quase nada). */
	public static float hitNoisePerSecond(BlockState state) {
		if (state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS)) {
			return 0.5f;
		}
		if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
			return 5;
		}
		if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
			return 4;
		}
		return 2;
	}

	/** Pedra (e vidro, que estilhaça) +15, madeira +10, terra/lã/resto +5. */
	public static float breakNoise(BlockState state) {
		if (state.is(ConventionalBlockTags.GLASS_BLOCKS) || state.is(ConventionalBlockTags.GLASS_PANES)) {
			return BREAK_STONE;
		}
		if (state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS)) {
			return BREAK_SOFT;
		}
		if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
			return BREAK_STONE;
		}
		if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
			return BREAK_WOOD;
		}
		return BREAK_SOFT;
	}

	private static boolean isNoisyOpenable(Block block) {
		return block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof EnderChestBlock
				|| block instanceof ShulkerBoxBlock || block instanceof DoorBlock || block instanceof TrapDoorBlock
				|| block instanceof FenceGateBlock;
	}

	/** Chamado pelo mixin em {@code ServerPlayer#jumpFromGround}. */
	public static void onJump(ServerPlayer player) {
		NoiseTracker.add(player, JUMP);
	}

	/** Explosões: barulho na barra de quem estiver perto e um estrondo que o Ouvinte vai investigar. */
	public static void onExplosion(ServerLevel level, Vec3 pos) {
		if (!WorldSounds.emit(level, pos, WorldSounds.Kind.EXPLOSION)) {
			return;
		}
		double radius = WorldSounds.EXPLOSION_PLAYER_RADIUS;
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(pos) <= radius * radius) {
				NoiseTracker.add(player, WorldSounds.EXPLOSION_PLAYER_NOISE);
			}
		}
	}
}
