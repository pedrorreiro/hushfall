package com.pedro.silenciototal.client.render;

import com.pedro.silenciototal.entity.ListenerPup;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class ListenerPupRenderState extends LivingEntityRenderState {
	public boolean sitting;
	/** {@link ListenerPup#IDLE}, {@link ListenerPup#LISTEN}, {@link ListenerPup#TAP}, {@link ListenerPup#NO} ou {@link ListenerPup#MARK}. */
	public int action = ListenerPup.IDLE;
	/** Ticks (com fração) desde que a ação começou. */
	public float actionTime;
}
