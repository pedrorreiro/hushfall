package com.pedro.silenciototal.item;

import com.pedro.silenciototal.entity.Listener;
import com.pedro.silenciototal.noise.NightCycle;
import com.pedro.silenciototal.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Sino Ensurdecedor (feito com a Orelha do Ouvinte): tocado, atordoa o Ouvinte por 10 segundos
 * se ele estiver a até 16 blocos. Só funciona uma vez por noite. O toque não conta no seu ruído:
 * ele é feito para machucar os ouvidos dele, não para chamá-lo. Quando o atordoamento passa,
 * ele vai investigar de onde veio o sino, então aproveite para sair dali.
 */
public class DeafeningBellItem extends Item {
	public static final int STUN_TICKS = 200;
	public static final double RANGE = 16;

	public DeafeningBellItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (!NightCycle.isNight(serverLevel)) {
			serverPlayer.sendOverlayMessage(Component.translatable("message.silenciototal.bell_day").withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}
		long night = NightCycle.day(serverLevel);
		if (player.getAttachedOrElse(ModAttachments.BELL_NIGHT, -1L) == night) {
			serverPlayer.sendOverlayMessage(Component.translatable("message.silenciototal.bell_used").withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}
		player.setAttached(ModAttachments.BELL_NIGHT, night);
		// O ícone fica "carregando" até o amanhecer, mostrando que já foi usado nesta noite.
		player.getCooldowns().addCooldown(stack, (int) Math.max(20, NightCycle.DAWN - NightCycle.timeOfDay(serverLevel)));

		serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 3.0f, 0.6f);
		serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2.0f, 0.8f);
		Vec3 source = player.position();
		int stunned = 0;
		for (Listener listener : serverLevel.getEntitiesOfClass(Listener.class, player.getBoundingBox().inflate(RANGE))) {
			if (listener.distanceTo(player) <= RANGE) {
				listener.stun(STUN_TICKS, source);
				stunned++;
			}
		}
		serverPlayer.sendOverlayMessage(Component.translatable(stunned > 0 ? "message.silenciototal.bell_stunned" : "message.silenciototal.bell_missed")
				.withStyle(stunned > 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY));
		return InteractionResult.SUCCESS;
	}
}
