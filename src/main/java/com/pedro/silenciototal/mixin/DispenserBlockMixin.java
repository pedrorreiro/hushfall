package com.pedro.silenciototal.mixin;

import com.pedro.silenciototal.noise.WorldSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cada disparo de um dispensador é uma distração num ponto fixo. */
@Mixin(DispenserBlock.class)
public abstract class DispenserBlockMixin {
	@Inject(method = "dispenseFrom", at = @At("TAIL"))
	private void silenciototal$dispenseNoise(ServerLevel level, BlockState state, BlockPos pos, CallbackInfo ci) {
		WorldSounds.emit(level, Vec3.atCenterOf(pos), WorldSounds.Kind.DISPENSER);
	}
}
