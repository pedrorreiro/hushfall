package com.pedro.silenciototal.pup;

import com.pedro.silenciototal.entity.ListenerPup;
import com.pedro.silenciototal.noise.NightCycle;
import com.pedro.silenciototal.registry.ModAttachments;
import com.pedro.silenciototal.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * Filhotes que fugiram (vida zerada) esperando para voltar. Ficam no servidor, não no dono, para
 * funcionar mesmo se o dono estava longe ou desconectado quando ele fugiu. Voltam de dia, a partir
 * da manhã seguinte, quando o dono está online e no Overworld.
 */
public final class PupReturns {
	private PupReturns() {
	}

	public static List<PupReturn> all(MinecraftServer server) {
		return server.globalAttachments().getAttachedOrElse(ModAttachments.PUP_RETURNS, List.of());
	}

	public static void add(MinecraftServer server, PupReturn pup) {
		List<PupReturn> list = new ArrayList<>(all(server));
		list.add(pup);
		server.globalAttachments().setAttached(ModAttachments.PUP_RETURNS, List.copyOf(list));
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		List<PupReturn> list = all(server);
		if (list.isEmpty()) {
			return;
		}
		ServerLevel overworld = server.overworld();
		if (NightCycle.isNight(overworld)) {
			return;
		}
		long day = NightCycle.day(overworld);
		List<PupReturn> waiting = new ArrayList<>();
		for (PupReturn pup : list) {
			ServerPlayer owner = server.getPlayerList().getPlayer(pup.owner());
			if (pup.day() > day || owner == null || owner.level() != overworld || !owner.isAlive() || owner.isSpectator()
					|| !bringBack(overworld, owner, pup)) {
				waiting.add(pup);
			}
		}
		if (waiting.size() != list.size()) {
			server.globalAttachments().setAttached(ModAttachments.PUP_RETURNS, List.copyOf(waiting));
		}
	}

	/** Ressurge da terra perto do dono. */
	private static boolean bringBack(ServerLevel level, ServerPlayer owner, PupReturn info) {
		ListenerPup pup = ModEntities.LISTENER_PUP.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (pup == null) {
			return false;
		}
		pup.setTame(true, true);
		pup.setOwner(owner);
		pup.setSearchesLeft(info.searchesLeft());
		info.name().ifPresent(pup::setCustomName);
		pup.snapTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot() + 180, 0);
		// Num lugar livre em volta do dono, como o lobo que se teleporta (se não achar, fica nos pés dele).
		pup.tryToTeleportToOwner();
		level.addFreshEntity(pup);
		pup.burrowEffect(level);
		owner.sendSystemMessage(Component.translatable("message.silenciototal.pup_back").withStyle(ChatFormatting.GRAY));
		return true;
	}
}
