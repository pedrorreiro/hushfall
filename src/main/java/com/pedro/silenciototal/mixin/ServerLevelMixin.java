package com.pedro.silenciototal.mixin;

import com.pedro.silenciototal.noise.NoiseSources;
import com.pedro.silenciototal.noise.WorldSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Escuta os game events do mundo (os mesmos "vibrações" que o Warden sente) e transforma os
 * barulhentos em sons que o Ouvinte vai investigar.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
	@Inject(method = "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V", at = @At("HEAD"))
	private void silenciototal$worldSounds(Holder<GameEvent> gameEvent, Vec3 position, GameEvent.Context context, CallbackInfo ci) {
		ServerLevel level = (ServerLevel) (Object) this;
		if (gameEvent.is(GameEvent.NOTE_BLOCK_PLAY)) {
			WorldSounds.emit(level, position, WorldSounds.Kind.NOTE_BLOCK);
		} else if (gameEvent.is(GameEvent.EXPLODE)) {
			NoiseSources.onExplosion(level, position);
		} else if (gameEvent.is(GameEvent.LIGHTNING_STRIKE)) {
			WorldSounds.emit(level, position, WorldSounds.Kind.LIGHTNING);
		} else if (gameEvent.is(GameEvent.PROJECTILE_LAND)) {
			WorldSounds.emit(level, position, WorldSounds.Kind.PROJECTILE);
		} else if (gameEvent.is(GameEvent.BLOCK_CHANGE)) {
			if (blockAt(level, position, context) instanceof BellBlock) {
				WorldSounds.emit(level, position, WorldSounds.Kind.BELL);
			}
		} else if (gameEvent.is(GameEvent.BLOCK_ACTIVATE) || gameEvent.is(GameEvent.BLOCK_DEACTIVATE)) {
			Block block = blockAt(level, position, context);
			if (block instanceof PistonBaseBlock || block instanceof MovingPistonBlock) {
				WorldSounds.emit(level, position, WorldSounds.Kind.PISTON);
			}
		}
	}

	private static Block blockAt(ServerLevel level, Vec3 position, GameEvent.Context context) {
		BlockState state = context.affectedState();
		if (state != null) {
			return state.getBlock();
		}
		return level.getBlockState(BlockPos.containing(position)).getBlock();
	}
}
