package com.pedro.silenciototal.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.pedro.silenciototal.entity.Listener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * O Ouvinte tem 3 blocos de altura no desenho, mas a caixa de colisão cabe em túneis de 2.
 * Na hora de mirar (golpe, flecha), usa a altura do desenho para dar para acertar peito e cabeça.
 */
@Mixin(ProjectileUtil.class)
public abstract class ProjectileUtilMixin {
	@WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
	private static AABB silenciototal$tallListenerHitbox(Entity entity, Operation<AABB> original) {
		AABB box = original.call(entity);
		return entity instanceof Listener listener ? listener.getVisualBox(box) : box;
	}
}
