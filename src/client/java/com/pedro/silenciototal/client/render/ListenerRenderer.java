package com.pedro.silenciototal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pedro.silenciototal.SilencioTotal;
import com.pedro.silenciototal.entity.Listener;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class ListenerRenderer extends MobRenderer<Listener, ListenerRenderState, ListenerModel> {
	private static final Identifier TEXTURE = SilencioTotal.id("textures/entity/listener/listener.png");

	/** Escala normal: o modelo tem ~30 px, então 1,6 dá 3 blocos de altura (sem contar as orelhas). */
	public static final float FULL_SCALE = 1.6f;
	/** Espremido sob teto baixo: cabe num túnel de 2 blocos. */
	public static final float SQUEEZED_SCALE = 1.0f;

	public ListenerRenderer(EntityRendererProvider.Context context) {
		super(context, new ListenerModel(context.bakeLayer(ListenerModel.LAYER)), 0.85f);
	}

	@Override
	public ListenerRenderState createRenderState() {
		return new ListenerRenderState();
	}

	@Override
	public void extractRenderState(Listener entity, ListenerRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.mode = entity.getState();
		state.suspicious = entity.isSuspicious();
		// Sob um teto baixo ele se encolhe (a caixa de colisão já cabe em 2 blocos; só o desenho muda).
		BlockPos head = BlockPos.containing(entity.getX(), entity.getY() + 2.1, entity.getZ());
		boolean lowCeiling = !entity.level().getBlockState(head).getCollisionShape(entity.level(), head).isEmpty()
				|| !entity.level().getBlockState(head.above()).getCollisionShape(entity.level(), head.above()).isEmpty();
		float target = lowCeiling ? SQUEEZED_SCALE : FULL_SCALE;
		entity.clientScale = entity.clientScale < 0 ? target : Mth.lerp(0.08f, entity.clientScale, target);
		state.squeeze = entity.clientScale;
	}

	@Override
	protected void scale(ListenerRenderState state, PoseStack poseStack) {
		// Encolhe mais na altura que na largura: parece que ele se curva, não que diminuiu.
		float height = state.squeeze;
		float width = Mth.lerp(0.5f, height, FULL_SCALE);
		poseStack.scale(width, height, width);
	}

	@Override
	public Identifier getTextureLocation(ListenerRenderState state) {
		return TEXTURE;
	}
}
