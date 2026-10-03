package com.pedro.silenciototal.test;

import com.pedro.silenciototal.client.NoiseHud;
import com.pedro.silenciototal.entity.Listener;
import com.pedro.silenciototal.noise.NightCycle;
import com.pedro.silenciototal.noise.NoiseSources;
import com.pedro.silenciototal.noise.NoiseTracker;
import com.pedro.silenciototal.noise.SilentRoom;
import com.pedro.silenciototal.noise.WorldSounds;
import com.pedro.silenciototal.registry.ModEntities;
import com.pedro.silenciototal.registry.ModItems;
import com.pedro.silenciototal.spawn.ListenerSpawner;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * Abre o cliente, cria um mundo, faz anoitecer e exercita a barra de ruído, o Ouvinte,
 * distrações, ataque surpresa, sala silenciosa e o amanhecer. Rode com ./gradlew runClientGameTest
 */
public class SilencioTotalClientTest implements FabricClientGameTest {
	/** Dia 1 (a noite 0 é de graça), 14000 = começo da noite. */
	private static final int HUNTING_NIGHT = 24000 + 14000;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			mc.options.languageCode = "pt_br";
			mc.getLanguageManager().setSelected("pt_br");
			mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
		});
		try (TestSingleplayerContext singleplayer = ctx.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("difficulty normal");
			server.runCommand("weather clear");
			// O spawn automático fica desligado: o teste cria cada Ouvinte na hora certa.
			server.runCommand("gamerule spawn_monsters false");

			// ---------------------------------------------------------------- noite de graça
			server.runCommand("time set 14000");
			ctx.waitTicks(30);
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				check(NightCycle.isNight(level), "14000 deveria ser noite");
				check(!NightCycle.isHuntingNight(level), "a primeira noite deveria ser de graça");
			});
			check(ctx.computeOnClient(mc -> NoiseHud.visible()), "a barra de ruído não apareceu à noite");

			// ---------------------------------------------------------------- noite de caça + ruído
			server.runCommand("time set " + HUNTING_NIGHT);
			ctx.waitTicks(30);
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				check(NightCycle.isHuntingNight(player.level()), "dia 1 deveria ser noite de caça");
				check(!ListenerSpawner.canSpawn(player.level()), "spawn_monsters=false deveria desligar o spawn");
				NoiseTracker.set(player, 0);
				NoiseTracker.add(player, NoiseSources.OPEN_CONTAINER);
				check(near(NoiseTracker.get(player), 8), "abrir baú deveria somar 8, deu " + NoiseTracker.get(player));
				player.jumpFromGround();
				check(near(NoiseTracker.get(player), 14), "pular deveria somar 6 (mixin), deu " + NoiseTracker.get(player));
				check(NoiseSources.breakNoise(Blocks.STONE.defaultBlockState()) == 15, "pedra deveria valer 15");
				check(NoiseSources.breakNoise(Blocks.OAK_LOG.defaultBlockState()) == 10, "madeira deveria valer 10");
				check(NoiseSources.hitNoisePerSecond(Blocks.OAK_LOG.defaultBlockState()) == 4, "bater em madeira deveria valer 4/s");
				check(NoiseSources.hitNoisePerSecond(Blocks.STONE.defaultBlockState()) == 5, "bater em pedra deveria valer 5/s");
				check(NoiseSources.breakNoise(Blocks.DIRT.defaultBlockState()) == 5, "terra deveria valer 5");
				check(NoiseSources.breakNoise(Blocks.WOOL.white().defaultBlockState()) == 5, "lã deveria valer 5");
				NoiseTracker.set(player, 20);
			});
			ctx.waitTicks(40);
			server.runOnServer(s -> {
				float noise = NoiseTracker.get(player(s));
				check(noise < 13.5f && noise > 10f, "parado, o ruído deveria cair ~4/s; está em " + noise);
			});
			check(ctx.computeOnClient(mc -> NoiseHud.noise() < 14f), "o cliente não recebeu o ruído atualizado");

			// Chuva disfarça 30%.
			server.runCommand("weather rain");
			// A chuva começa aos poucos: isRaining() só fica true depois de alguns ticks.
			ctx.waitTicks(60);
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				NoiseTracker.set(player, 0);
				NoiseTracker.add(player, 10);
				check(near(NoiseTracker.get(player), 7), "com chuva +10 deveria virar +7, deu " + NoiseTracker.get(player));
				NoiseTracker.set(player, 0);
			});
			server.runCommand("weather clear");

			// Piso de lã e botas de feltro.
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				ServerLevel level = player.level();
				BlockPos below = player.blockPosition().below();
				BlockState old = level.getBlockState(below);
				level.setBlockAndUpdate(below, Blocks.WOOL.white().defaultBlockState());
				check(NoiseTracker.floorMultiplier(player) == 0.5f, "lã deveria abafar 50%");
				level.setBlockAndUpdate(below, Blocks.GRAVEL.defaultBlockState());
				check(NoiseTracker.floorMultiplier(player) == 1.5f, "cascalho deveria amplificar 50%");
				level.setBlockAndUpdate(below, old);
				player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.FELT_BOOTS));
			});
			ctx.waitTicks(5);

			// ---------------------------------------------------------------- spawn longe do jogador
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				Listener spawned = ListenerSpawner.spawnNear(player.level(), player, -1);
				check(spawned != null, "o Ouvinte não conseguiu surgir");
				double distance = Math.sqrt(spawned.distanceToSqr(player));
				check(distance >= 30, "o Ouvinte surgiu perto demais: " + distance);
				spawned.discard();
			});

			// ---------------------------------------------------------------- audição: médio investiga, alto caça
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				summon(player, 10, false);
				NoiseTracker.set(player, 50);
			});
			ctx.waitTicks(15);
			server.runOnServer(s -> {
				Listener listener = listener(s);
				check(listener.getState() == Listener.INVESTIGATE, "ruído médio deveria fazer o Ouvinte investigar, estado " + listener.getState());
				NoiseTracker.set(player(s), 90);
			});
			ctx.waitTicks(5);
			server.runOnServer(s -> check(listener(s).getState() == Listener.ALERT, "ruído alto deveria dar o aviso (alerta) antes da caça"));
			ctx.takeScreenshot("silenciototal_alert");
			ctx.waitTicks(Listener.ALERT_TICKS + 5);
			server.runOnServer(s -> {
				Listener listener = listener(s);
				check(listener.getState() == Listener.HUNT && listener.getTarget() == player(s), "depois do aviso o Ouvinte deveria caçar");
				// Silêncio e distância: perde o rastro.
				ServerPlayer player = player(s);
				NoiseTracker.set(player, 0);
				player.teleportTo(player.getX() + 50, player.getY(), player.getZ());
			});
			ctx.waitTicks(Listener.LOSE_TRACK_TICKS + 30);
			server.runOnServer(s -> {
				check(listener(s).getState() != Listener.HUNT, "o Ouvinte deveria perder o rastro em silêncio e longe");
				listener(s).discard();
			});

			// ---------------------------------------------------------------- distração: note block
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				ServerLevel level = player.level();
				Listener listener = summon(player, 20, false);
				BlockPos note = listener.blockPosition().offset(12, 0, 0);
				level.setBlockAndUpdate(note, Blocks.NOTE_BLOCK.defaultBlockState());
				level.gameEvent(null, GameEvent.NOTE_BLOCK_PLAY, note);
				check(listener.getState() == Listener.INVESTIGATE, "o note block deveria atrair o Ouvinte");
				Vec3 target = listener.getSoundTarget();
				check(target != null && target.closerThan(Vec3.atCenterOf(note), 1.5), "o Ouvinte deveria ir até o note block");
				check(!WorldSounds.emit(level, Vec3.atCenterOf(note), WorldSounds.Kind.NOTE_BLOCK), "a distração deveria ter cooldown");
			});
			ctx.waitTicks(20);
			server.runOnServer(s -> listener(s).discard());

			// ---------------------------------------------------------------- ataque surpresa
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				Listener listener = summon(player, 4, false);
				listener.hurtServer(player.level(), player.damageSources().playerAttack(player), 4.0f);
				check(listener.getHealth() == (float) Listener.MAX_HEALTH - 4 * Listener.SURPRISE_MULTIPLIER,
						"ataque surpresa deveria causar dano x1,5, vida " + listener.getHealth());
				check(listener.getState() == Listener.HUNT, "apanhar deveria fazer o Ouvinte caçar quem bateu");
				listener.discard();
			});

			// ---------------------------------------------------------------- faro: em silêncio ele só fareja
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				NoiseTracker.set(player, 0);
				check(!Listener.canSense(player), "em silêncio ele não deveria te descobrir");
				NoiseTracker.set(player, 15);
				check(Listener.canSense(player), "com a barra em 15 ele deveria te descobrir");
				NoiseTracker.set(player, 0);
				summon(player, 3, false);
			});
			ctx.waitTicks(40);
			server.runOnServer(s -> {
				int state = listener(s).getState();
				check(state != Listener.ALERT && state != Listener.HUNT, "em silêncio a 3 blocos ele só deveria farejar, estado " + state);
				NoiseTracker.set(player(s), 8.5f);
			});
			ctx.waitTicks(10);
			server.runOnServer(s -> {
				Listener listener = listener(s);
				check(listener.isSuspicious(), "com a barra em ~8 perto dele, ele deveria ficar desconfiado");
				check(listener.getState() != Listener.ALERT && listener.getState() != Listener.HUNT, "desconfiado ainda não deveria atacar");
				listener.setYRot(listener.getYRot());
			});
			check(ctx.computeOnClient(mc -> com.pedro.silenciototal.client.PanicFeedback.danger() > 0.5f),
					"perto do limite com ele colado, o pânico (coração/bordas) deveria estar forte");
			ctx.takeScreenshot("silenciototal_suspicious");
			server.runOnServer(s -> NoiseTracker.set(player(s), 20));
			ctx.waitTicks(10);
			server.runOnServer(s -> {
				check(listener(s).getState() == Listener.ALERT, "com barulho a poucos blocos ele deveria te descobrir, estado " + listener(s).getState());
				listener(s).discard();
			});

			// ---------------------------------------------------------------- só existe um
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				summon(player, 20, false);
				summon(player, 25, false);
			});
			ctx.waitTicks(30);
			server.runOnServer(s -> {
				int count = s.overworld().getEntitiesOfClass(Listener.class, player(s).getBoundingBox().inflate(256)).size();
				check(count == 1, "deveria existir um único Ouvinte, existem " + count);
				listener(s).discard();
			});

			// ---------------------------------------------------------------- mira no peito/cabeça e adaga silenciosa
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				ServerLevel level = player.level();
				Listener listener = summon(player, 3, true);
				check(listener.getVisualBox(listener.getBoundingBox()).getYsize() >= 2.9, "a área de acerto deveria ter a altura do desenho");
				Vec3 eye = player.getEyePosition();
				Vec3 head = new Vec3(listener.getX(), listener.getY() + 2.6, listener.getZ());
				Vec3 to = eye.add(head.subtract(eye).scale(1.5));
				var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, to,
						new net.minecraft.world.phys.AABB(eye, to).inflate(1), e -> e == listener, 100);
				check(hit != null && hit.getEntity() == listener, "mirar na cabeça (2,6 blocos de altura) deveria acertar o Ouvinte");
				listener.discard();

				var zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
				zombie.snapTo(player.getX() + 2, player.getY(), player.getZ());
				level.addFreshEntity(zombie);
				NoiseTracker.set(player, 0);
				player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.SILENT_DAGGER));
				net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.invoker().interact(player, level, net.minecraft.world.InteractionHand.MAIN_HAND, zombie, null);
				check(NoiseTracker.get(player) == 0, "golpe com a Adaga Silenciosa não deveria fazer barulho");
				player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.invoker().interact(player, level, net.minecraft.world.InteractionHand.MAIN_HAND, zombie, null);
				check(near(NoiseTracker.get(player), NoiseSources.COMBAT), "golpe com a mão deveria fazer barulho de combate");
				zombie.discard();
				NoiseTracker.set(player, 0);
			});

			// ---------------------------------------------------------------- sala silenciosa
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				ServerLevel level = player.level();
				BlockPos feet = player.blockPosition();
				buildWoolRoom(level, feet);
				check(SilentRoom.isInside(level, feet), "sala de lã deveria ser silenciosa");

				BlockPos wall = feet.offset(2, 0, 0);
				level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
				check(!SilentRoom.isInside(level, feet), "parede de pedra deveria vazar o som");

				BlockState door = Blocks.OAK_DOOR.defaultBlockState();
				level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(wall.above(), Blocks.AIR.defaultBlockState());
				level.setBlock(wall, door.setValue(DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER), 3);
				level.setBlock(wall.above(), door.setValue(DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
				check(SilentRoom.isInside(level, feet), "porta fechada deveria vedar");
				level.setBlock(wall, level.getBlockState(wall).setValue(BlockStateProperties.OPEN, true), 3);
				level.setBlock(wall.above(), level.getBlockState(wall.above()).setValue(BlockStateProperties.OPEN, true), 3);
				check(!SilentRoom.isInside(level, feet), "porta aberta deveria vazar");
				level.setBlock(wall, level.getBlockState(wall).setValue(BlockStateProperties.OPEN, false), 3);
				level.setBlock(wall.above(), level.getBlockState(wall.above()).setValue(BlockStateProperties.OPEN, false), 3);
			});
			ctx.waitTicks(30);
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				check(NoiseTracker.isInSilentRoom(player), "o rastreador não percebeu a sala silenciosa");
				NoiseTracker.add(player, 40);
				check(NoiseTracker.get(player) == 0, "dentro da sala silenciosa não deveria somar ruído");
			});
			ctx.waitTicks(5);
			ctx.takeScreenshot("silenciototal_silent_room");
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				BlockPos feet = player.blockPosition();
				for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 3, 2))) {
					player.level().setBlockAndUpdate(pos, pos.getY() < feet.getY() ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
				}
			});

			// ---------------------------------------------------------------- retrato do Ouvinte
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 2000, 0, false, false));
				summon(player, 5, true);
				NoiseTracker.set(player, 55);
			});
			ctx.waitTicks(30);
			ctx.takeScreenshot("silenciototal_listener");

			// ---------------------------------------------------------------- se espreme sob teto baixo
			server.runOnServer(s -> {
				Listener listener = listener(s);
				check(listener.getBbHeight() < 2.0f, "a caixa de colisão deveria caber num túnel de 2 blocos");
				BlockPos top = listener.blockPosition().above(2);
				for (BlockPos pos : BlockPos.betweenClosed(top.offset(-1, 0, -1), top.offset(1, 0, 1))) {
					s.overworld().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
				}
			});
			ctx.waitTicks(40);
			ctx.takeScreenshot("silenciototal_squeezed");
			server.runOnServer(s -> {
				BlockPos top = listener(s).blockPosition().above(2);
				for (BlockPos pos : BlockPos.betweenClosed(top.offset(-1, 0, -1), top.offset(1, 0, 1))) {
					s.overworld().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
				}
				listener(s).discard();
			});

			// ---------------------------------------------------------------- patrulha vem rondar a sua área
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				// Imune a dano só para o teste não morrer se ele chegar perto.
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 2000, 4, false, false));
				NoiseTracker.set(player, 0);
				summon(player, 34, false);
			});
			double[] closest = {Double.MAX_VALUE};
			for (int i = 0; i < 12; i++) {
				ctx.waitTicks(50);
				server.runOnServer(s -> closest[0] = Math.min(closest[0], listener(s).distanceTo(player(s))));
			}
			check(closest[0] < 22, "em silêncio, a patrulha deveria chegar perto; mais perto que chegou: " + closest[0]);
			server.runOnServer(s -> listener(s).discard());

			// ---------------------------------------------------------------- surge sozinho ao anoitecer; morto, só volta amanhã
			server.runCommand("gamerule spawn_monsters true");
			ctx.waitTicks(30);
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				List<Listener> all = s.overworld().getEntitiesOfClass(Listener.class, player.getBoundingBox().inflate(256));
				check(all.size() == 1, "o Ouvinte deveria surgir sozinho à noite, existem " + all.size());
				Listener listener = all.getFirst();
				check(listener.distanceTo(player) >= 30, "surgiu perto demais: " + listener.distanceTo(player));
				// Longe de todo mundo, ele se enterra e ressurge perto (com a mesma vida).
				listener.setHealth(100);
				listener.teleportTo(player.getX() + 62, player.getY(), player.getZ());
			});
			ctx.waitTicks(Listener.REPOSITION_TICKS + 60);
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				List<Listener> all = s.overworld().getEntitiesOfClass(Listener.class, player.getBoundingBox().inflate(256));
				check(all.size() == 1, "depois de se perder deveria existir um Ouvinte, existem " + all.size());
				Listener listener = all.getFirst();
				check(listener.distanceTo(player) <= 50, "perdido, ele deveria ressurgir perto; está a " + listener.distanceTo(player));
				check(listener.getHealth() >= 100 && listener.getHealth() < Listener.MAX_HEALTH,
						"ao ressurgir ele deveria manter a vida, tem " + listener.getHealth());
				listener.hurtServer(s.overworld(), s.overworld().damageSources().genericKill(), 10000);
				check(!listener.isAlive(), "o Ouvinte deveria ter morrido");
			});
			ctx.waitTicks(40);
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				int alive = s.overworld().getEntitiesOfClass(Listener.class, player.getBoundingBox().inflate(256)).size();
				check(alive == 0, "morto, ele não deveria voltar na mesma noite");
				boolean ear = !s.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(256),
						item -> item.getItem().is(ModItems.LISTENER_EAR)).isEmpty();
				check(ear, "o Ouvinte deveria dropar a Orelha");
			});
			server.runCommand("gamerule spawn_monsters false");

			// ---------------------------------------------------------------- amanhecer
			server.runOnServer(s -> summon(player(s), 10, true));
			ctx.waitTicks(5);
			server.runCommand("time set " + (24000 * 2 + 100));
			ctx.waitTicks(20);
			server.runOnServer(s -> {
				List<Listener> left = s.overworld().getEntitiesOfClass(Listener.class, player(s).getBoundingBox().inflate(128));
				check(left.isEmpty(), "o Ouvinte deveria se enterrar ao amanhecer");
			});
			check(ctx.computeOnClient(mc -> !NoiseHud.visible()), "a barra de ruído deveria sumir de dia");
		}
	}

	private static Listener summon(ServerPlayer player, double distance, boolean frozen) {
		Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
		if (look.lengthSqr() < 0.01) {
			look = new Vec3(0, 0, 1);
		}
		Vec3 pos = player.position().add(look.scale(distance));
		ServerLevel level = player.level();
		Listener listener = ModEntities.LISTENER.create(level, EntitySpawnReason.COMMAND);
		listener.snapTo(pos.x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(pos.x), (int) Math.floor(pos.z)), pos.z,
				player.getYRot() + 180, 0);
		if (frozen) {
			listener.setNoAi(true);
		}
		level.addFreshEntity(listener);
		return listener;
	}

	/** Cubo de lã com interior 3x3x3 em volta dos pés do jogador. */
	private static void buildWoolRoom(ServerLevel level, BlockPos feet) {
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 3, 2))) {
			boolean shell = Math.abs(pos.getX() - feet.getX()) == 2 || Math.abs(pos.getZ() - feet.getZ()) == 2
					|| pos.getY() == feet.getY() - 1 || pos.getY() == feet.getY() + 3;
			level.setBlockAndUpdate(pos, shell ? Blocks.WOOL.white().defaultBlockState() : Blocks.AIR.defaultBlockState());
		}
	}

	private static Listener listener(MinecraftServer server) {
		List<Listener> all = server.overworld().getEntitiesOfClass(Listener.class, player(server).getBoundingBox().inflate(256));
		check(!all.isEmpty(), "nenhum Ouvinte no mundo");
		return all.getFirst();
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static boolean near(float value, float expected) {
		return Math.abs(value - expected) < 0.01f;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
