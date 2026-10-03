package com.pedro.silenciototal.registry;

import com.pedro.silenciototal.SilencioTotal;
import com.pedro.silenciototal.spawn.ListenerState;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public final class ModAttachments {
	/** O Ouvinte único do mundo (guardado nos attachments globais do servidor). */
	public static final AttachmentType<ListenerState> LISTENER_STATE = AttachmentRegistry.create(SilencioTotal.id("listener_state"),
			builder -> builder.initializer(() -> ListenerState.EMPTY).persistent(ListenerState.CODEC));

	private ModAttachments() {
	}

	public static void init() {
	}
}
