package com.pedro.silenciototal.client.render;

import com.pedro.silenciototal.entity.Listener;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class ListenerRenderState extends LivingEntityRenderState {
	/** {@link Listener#PATROL}, {@link Listener#INVESTIGATE}, {@link Listener#ALERT} ou {@link Listener#HUNT}. */
	public int mode = Listener.PATROL;
	/** Desconfiado: orelhas em pé viradas para você. */
	public boolean suspicious;
	/** Escala de desenho (menor quando ele se espreme sob um teto baixo). */
	public float squeeze = ListenerRenderer.FULL_SCALE;
}
