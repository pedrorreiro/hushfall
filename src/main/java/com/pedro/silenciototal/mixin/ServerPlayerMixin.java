package com.pedro.silenciototal.mixin;

import com.pedro.silenciototal.noise.NoiseSources;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** O servidor chama {@code jumpFromGround} quando o cliente avisa que pulou: +4 de ruído. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Inject(method = "jumpFromGround", at = @At("TAIL"))
	private void silenciototal$jumpNoise(CallbackInfo ci) {
		NoiseSources.onJump((ServerPlayer) (Object) this);
	}
}
