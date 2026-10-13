package com.pedro.silenciototal.client.render;

import com.pedro.silenciototal.SilencioTotal;
import com.pedro.silenciototal.entity.ListenerPup;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

public class ListenerPupRenderer extends MobRenderer<ListenerPup, ListenerPupRenderState, ListenerPupModel> {
	private static final Identifier TEXTURE = SilencioTotal.id("textures/entity/listener_pup/listener_pup.png");

	public ListenerPupRenderer(EntityRendererProvider.Context context) {
		super(context, new ListenerPupModel(context.bakeLayer(ListenerPupModel.LAYER)), 0.3f);
	}

	@Override
	public ListenerPupRenderState createRenderState() {
		return new ListenerPupRenderState();
	}

	@Override
	public void extractRenderState(ListenerPup entity, ListenerPupRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.sitting = entity.isInSittingPose();
		state.action = entity.getAction();
		state.actionTime = entity.actionTicks() + partialTicks;
	}

	@Override
	public Identifier getTextureLocation(ListenerPupRenderState state) {
		return TEXTURE;
	}
}
