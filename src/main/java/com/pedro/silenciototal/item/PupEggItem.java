package com.pedro.silenciototal.item;

import com.pedro.silenciototal.entity.ListenerPup;
import com.pedro.silenciototal.registry.ModEntities;
import com.pedro.silenciototal.registry.ModSounds;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jspecify.annotations.Nullable;

/**
 * Ovo do Filhote. Diferente do ovo gerador, o filhote nasce já domesticado por quem usou o ovo
 * (por isso não é um {@code SpawnEggItem}).
 */
public class PupEggItem extends Item {
	public PupEggItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos clicked = context.getClickedPos();
		Direction face = context.getClickedFace();
		BlockPos pos = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty() ? clicked : clicked.relative(face);
		ItemStack stack = context.getItemInHand();
		boolean movedUp = !Objects.equals(clicked, pos) && face == Direction.UP;
		ListenerPup pup = hatch(level, player, stack, pos, movedUp);
		if (pup == null) {
			return InteractionResult.FAIL;
		}
		stack.consume(1, player);
		return InteractionResult.SUCCESS;
	}

	/** Choca o filhote em {@code pos}, já com {@code owner} como dono. */
	public static @Nullable ListenerPup hatch(ServerLevel level, Player owner, @Nullable ItemStack stack, BlockPos pos, boolean movedUp) {
		ListenerPup pup = ModEntities.LISTENER_PUP.spawn(level, stack, owner, pos, EntitySpawnReason.SPAWN_ITEM_USE, true, movedUp);
		if (pup == null) {
			return null;
		}
		pup.tame(owner);
		level.gameEvent(owner, GameEvent.ENTITY_PLACE, pos);
		level.playSound(null, pos, ModSounds.PUP_BURROW, SoundSource.NEUTRAL, 0.8f, 1.2f);
		return pup;
	}
}
