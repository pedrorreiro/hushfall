package com.pedro.silenciototal.registry;

import com.pedro.silenciototal.SilencioTotal;
import com.pedro.silenciototal.spawn.ListenerState;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public final class ModAttachments {
	/** O Ouvinte único do mundo (guardado nos attachments globais do servidor). */
	public static final AttachmentType<ListenerState> LISTENER_STATE = AttachmentRegistry.create(SilencioTotal.id("listener_state"),
			builder -> builder.initializer(() -> ListenerState.EMPTY).persistent(ListenerState.CODEC));

	/** Última noite em que o jogador tocou o Sino Ensurdecedor (uma vez por noite). */
	public static final AttachmentType<Long> BELL_NIGHT = AttachmentRegistry.create(SilencioTotal.id("bell_night"),
			builder -> builder.initializer(() -> -1L).persistent(Codec.LONG).copyOnDeath());

	private ModAttachments() {
	}

	public static void init() {
	}
}
