package com.pedro.silenciototal.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Para saber se o jogador está batendo num bloco agora (e em qual), a cada tick. */
@Mixin(ServerPlayerGameMode.class)
public interface ServerPlayerGameModeAccessor {
	@Accessor("isDestroyingBlock")
	boolean silenciototal$isDestroyingBlock();

	@Accessor("destroyPos")
	BlockPos silenciototal$destroyPos();
}
