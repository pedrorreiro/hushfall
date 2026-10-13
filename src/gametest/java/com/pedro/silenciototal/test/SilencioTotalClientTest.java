package com.pedro.silenciototal.test;

import com.pedro.silenciototal.ModConfig;
import com.pedro.silenciototal.SilencioTotal;
import com.pedro.silenciototal.client.ClientConfig;
import com.pedro.silenciototal.client.HudConfigScreen;
import com.pedro.silenciototal.client.ListenerPupScreen;
import com.pedro.silenciototal.client.NoiseHud;
import com.pedro.silenciototal.entity.Listener;
import com.pedro.silenciototal.entity.ListenerPup;
import com.pedro.silenciototal.item.PupEggItem;
import com.pedro.silenciototal.noise.NightCycle;
import com.pedro.silenciototal.noise.NoiseSources;
import com.pedro.silenciototal.noise.NoiseTracker;
import com.pedro.silenciototal.noise.WorldSounds;
import com.pedro.silenciototal.pup.ListenerPupMenu;
import com.pedro.silenciototal.pup.OreScanner;
import com.pedro.silenciototal.pup.PupReturn;
import com.pedro.silenciototal.pup.PupReturns;
import com.pedro.silenciototal.registry.ModEntities;
import com.pedro.silenciototal.registry.ModItems;
import com.pedro.silenciototal.spawn.ListenerSpawner;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
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
		if (DemoReel.OUT != null) {
			return;
		}
		ctx.runOnClient(mc -> {
			mc.options.languageCode = "pt_br";
			mc.getLanguageManager().setSelected("pt_br");
			mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
			// Mudo: o teste abre o jogo de verdade e não deve fazer barulho no computador.
			mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
		});
		try (TestSingleplayerContext singleplayer = ctx.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("difficulty normal");
			server.runCommand("weather clear");
			// O spawn automático fica desligado: o teste cria cada Ouvinte na hora certa.
			server.runCommand("gamerule spawn_monsters false");

			// ---------------------------------------------------------------- noite de graça e sorteio das noites
			server.runCommand("time set 14000");
			ctx.waitTicks(30);
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				check(NightCycle.isNight(level), "14000 deveria ser noite");
				check(!NightCycle.isHuntingNight(level), "a primeira noite deveria ser de graça");
				// 50% por noite: perto de metade, e a mesma noite sempre dá o mesmo resultado.
				ModConfig.get().nightChance = 0.5f;
				int hunting = 0;
				for (long day = 1; day <= 400; day++) {
					boolean first = NightCycle.isHuntingDay(level, day);
					check(first == NightCycle.isHuntingDay(level, day), "o sorteio da noite deveria ser sempre o mesmo");
					if (first) {
						hunting++;
					}
				}
				check(hunting > 150 && hunting < 250, "com 50% deveriam ser ~200 de 400 noites, deu " + hunting);
				ModConfig.get().nightChance = 0f;
			});
			check(ctx.computeOnClient(mc -> !NoiseHud.visible()), "na noite de graça o medidor não deveria aparecer");
			server.runCommand("time set " + HUNTING_NIGHT);
			ctx.waitTicks(30);
			server.runOnServer(s -> check(!NightCycle.isHuntingNight(s.overworld()), "com chance 0 nenhuma noite deveria ter o Ouvinte"));
			check(ctx.computeOnClient(mc -> !NoiseHud.visible()), "em noite calma o medidor não deveria aparecer");
			// O resto do teste precisa do Ouvinte: toda noite.
			ModConfig.get().nightChance = 1f;
			server.runCommand("time set 14000");
			ctx.waitTicks(5);

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
			// Fogo (isqueiro no chão debaixo dele) também é ataque: caça quem está perto, mesmo em silêncio.
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				NoiseTracker.set(player, 0);
				Listener listener = summon(player, 6, false);
				check(listener.getState() != Listener.HUNT, "em silêncio ele não deveria estar caçando");
				listener.hurtServer(player.level(), player.damageSources().inFire(), 1.0f);
				check(listener.getState() == Listener.HUNT && listener.getTarget() == player, "queimar deveria fazer o Ouvinte caçar quem está perto");
				listener.discard();
				// Longe do fogo, ninguém é culpado.
				Listener far = summon(player, 24, false);
				far.hurtServer(player.level(), player.damageSources().inFire(), 1.0f);
				check(far.getState() != Listener.HUNT, "queimando longe de todos ele não deveria caçar ninguém");
				far.discard();
			});

			// ---------------------------------------------------------------- dormir: em silêncio ele não impede
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				NoiseTracker.set(player, 0);
				Listener listener = summon(player, 6, false);
				check(!listener.isPreventingPlayerRest(player.level(), player), "em silêncio, com ele patrulhando, deveria dar para dormir");
				NoiseTracker.set(player, 15);
				check(listener.isPreventingPlayerRest(player.level(), player), "fazendo barulho perto dele não deveria dar para dormir");
				NoiseTracker.set(player, 0);
				listener.hurtServer(player.level(), player.damageSources().playerAttack(player), 1.0f);
				check(listener.isPreventingPlayerRest(player.level(), player), "com ele caçando não deveria dar para dormir");
				listener.discard();
			});

			// ---------------------------------------------------------------- sem regeneração: o dano fica
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				ServerLevel level = player.level();
				NoiseTracker.set(player, 0);
				Listener listener = summon(player, 20, false);
				ListenerSpawner.setState(s, ListenerSpawner.state(s).withCurrent(listener.getUUID(), NightCycle.day(level), listener.getHealth()));
				listener.hurtServer(level, level.damageSources().generic(), 20.0f);
				check(near(ListenerSpawner.state(s).scars(), 20), "20 de dano deveria ficar salvo, ficou " + ListenerSpawner.state(s).scars());
			});
			ctx.waitTicks(20 * 5);
			server.runOnServer(s -> {
				Listener listener = listener(s);
				float expected = (float) Listener.MAX_HEALTH - 20;
				check(near(listener.getHealth(), expected), "ele não deveria regenerar: esperado " + expected + ", está com " + listener.getHealth());
				listener.discard();
				ListenerSpawner.setState(s, ListenerSpawner.state(s).withScars(0));
			});

			// ---------------------------------------------------------------- ouve de longe: ressurge perto de quem fez barulho
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				ServerLevel level = player.level();
				Listener far = summon(player, 20, false);
				ListenerSpawner.setState(s, ListenerSpawner.state(s).withCurrent(far.getUUID(), NightCycle.day(level), far.getHealth()));
				far.hearPlayerFromAfar(player, 50);
				check(far.isRemoved(), "ouvindo de longe, ele deveria se enterrar onde estava");
				Listener moved = listener(s);
				check(moved != far && moved.distanceTo(player) >= 30 && moved.distanceTo(player) <= 50,
						"deveria ressurgir a 30-50 blocos de quem fez barulho, está a " + moved.distanceTo(player));
				check(moved.getState() == Listener.INVESTIGATE, "ao ressurgir ele deveria ir investigar o barulho");
				moved.discard();
				NoiseTracker.set(player, 0);
			});

			// ---------------------------------------------------------------- ele lembra dos alarmes falsos mesmo depois de ressurgir
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				ServerLevel level = player.level();
				Vec3 decoy = player.position().add(10, 0, 0);
				long now = level.getGameTime();
				ListenerSpawner.setState(s, ListenerSpawner.state(s)
						.withFruitlessVisit(decoy, now, 3600, 4)
						.withFruitlessVisit(decoy, now, 3600, 4));
				Listener fresh = summon(player, 20, false);
				fresh.hearSound(decoy, WorldSounds.Kind.NOTE_BLOCK.loudness);
				check(fresh.getState() == Listener.PATROL, "um Ouvinte novo deveria lembrar que ali era alarme falso, estado " + fresh.getState());
				fresh.hearSound(player.position().add(-10, 0, 0), WorldSounds.Kind.NOTE_BLOCK.loudness);
				check(fresh.getState() == Listener.INVESTIGATE, "em outro lugar ele deveria continuar investigando");
				fresh.discard();
				ListenerSpawner.setState(s, ListenerSpawner.state(s).withHabituations(List.of()));
			});

			// ---------------------------------------------------------------- salvar e carregar no meio da caçada
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				ServerLevel level = player.level();
				// Ele vai caçar de verdade: o jogador não pode morrer no meio do teste.
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 300, 4));
				Listener hunter = summon(player, 6, false);
				hunter.hearPlayer(player, 90);
				TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
				hunter.saveWithoutId(output);
				hunter.discard();
				Listener loaded = ModEntities.LISTENER.create(level, EntitySpawnReason.LOAD);
				loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()));
				check(loaded.getState() == Listener.ALERT, "carregado do save ele deveria continuar em alerta, estado " + loaded.getState());
				check(loaded.isAfter(player), "carregado do save ele deveria lembrar quem estava caçando");
				level.addFreshEntity(loaded);
			});
			ctx.waitTicks(Listener.ALERT_TICKS + 10);
			server.runOnServer(s -> {
				Listener loaded = listener(s);
				check(loaded.getState() == Listener.HUNT && loaded.getTarget() == player(s),
						"depois de carregar, o alerta deveria virar caçada ao mesmo jogador, estado " + loaded.getState());
			});

			// ---------------------------------------------------------------- sair do mundo no meio da caçada não salva ninguém
			ctx.waitTicks(20);
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				NoiseTracker.set(player, 40);
				// Sair do servidor apaga a barra da memória; o que vale é o que ficou salvo no jogador.
				NoiseTracker.forget(player.getUUID());
				listener(s).discard();
			});
			ctx.waitTicks(2);
			server.runOnServer(s -> check(NoiseTracker.get(player(s)) >= 71,
					"quem saiu sendo caçado deveria voltar com barulho alto, voltou com " + NoiseTracker.get(player(s))));
			server.runOnServer(s -> NoiseTracker.set(player(s), 40));
			ctx.waitTicks(20);
			server.runOnServer(s -> NoiseTracker.forget(player(s).getUUID()));
			ctx.waitTicks(2);
			server.runOnServer(s -> {
				float noise = NoiseTracker.get(player(s));
				check(noise > 30 && noise < 71, "sem caçada, sair e voltar deveria manter a barra como estava, voltou com " + noise);
				NoiseTracker.set(player(s), 0);
			});

			// ---------------------------------------------------------------- barco e carrinho de mina fazem barulho
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				check(NoiseTracker.vehiclePerSecond(EntityTypes.OAK_BOAT.create(level, EntitySpawnReason.COMMAND)) == NoiseTracker.BOAT_PER_SECOND,
						"barco deveria fazer barulho");
				check(NoiseTracker.vehiclePerSecond(EntityTypes.MINECART.create(level, EntitySpawnReason.COMMAND)) == NoiseTracker.MINECART_PER_SECOND,
						"carrinho de mina deveria fazer barulho");
			});

			// ---------------------------------------------------------------- medidor em outro canto e a tela de opções
			ctx.runOnClient(mc -> {
				ClientConfig config = ClientConfig.get().copy();
				config.corner = ClientConfig.Corner.BOTTOM_RIGHT;
				config.scale = 1.5f;
				config.offsetY = 40;
				ClientConfig.set(config);
			});
			server.runOnServer(s -> NoiseTracker.set(player(s), 50));
			ctx.waitTicks(20);
			ctx.takeScreenshot("silenciototal_hud_bottom_right");
			ctx.runOnClient(mc -> mc.gui.setScreen(new HudConfigScreen(null)));
			ctx.waitTicks(20);
			ctx.takeScreenshot("silenciototal_config_screen");
			ctx.runOnClient(mc -> {
				mc.gui.setScreen(null);
				ClientConfig.set(new ClientConfig());
			});
			server.runOnServer(s -> NoiseTracker.set(player(s), 0));

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

			// ---------------------------------------------------------------- barulho dentro de casa: porta de madeira cai, de ferro aguenta
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 6000, 4, false, false));
				buildHouse(player.level(), player.blockPosition(), Blocks.OAK_DOOR.defaultBlockState());
				spawnOutsideDoor(player);
			});
			boolean woodBroken = waitForDoor(ctx, server, 500, true);
			check(woodBroken, "fazendo barulho dentro de casa, ele deveria arrombar a porta de madeira");
			server.runOnServer(s -> {
				listener(s).discard();
				ServerPlayer player = player(s);
				clearHouse(player.level(), houseDoor.offset(-2, 0, 0));
				buildHouse(player.level(), player.blockPosition(), Blocks.IRON_DOOR.defaultBlockState());
				spawnOutsideDoor(player);
			});
			boolean ironBroken = waitForDoor(ctx, server, 300, false);
			check(!ironBroken, "porta de ferro deveria aguentar");
			server.runOnServer(s -> {
				listener(s).discard();
				clearHouse(player(s).level(), houseDoor.offset(-2, 0, 0));
				NoiseTracker.set(player(s), 0);
			});

			// ---------------------------------------------------------------- Sino Ensurdecedor: atordoa 10 s, uma vez por noite
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				ServerLevel level = player.level();
				Listener listener = summon(player, 6, false);
				player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.DEAFENING_BELL));
				var first = ModItems.DEAFENING_BELL.use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
				check(first.consumesAction() && listener.isStunned(), "o sino deveria atordoar o Ouvinte");
				player.getCooldowns().removeCooldown(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.DEAFENING_BELL));
				var second = ModItems.DEAFENING_BELL.use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
				check(!second.consumesAction(), "o sino só deveria funcionar uma vez por noite");
			});
			ctx.waitTicks(40);
			ctx.takeScreenshot("silenciototal_stunned");
			ctx.waitTicks(180);
			server.runOnServer(s -> {
				Listener listener = listener(s);
				check(!listener.isStunned(), "depois de 10 s o atordoamento deveria passar");
				check(listener.getState() == Listener.INVESTIGATE, "depois de atordoado ele deveria ir investigar o sino, estado " + listener.getState());
				listener.discard();
				player(s).setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
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
				NoiseTracker.set(player, 30);
				listener.hurtServer(s.overworld(), s.overworld().damageSources().genericKill(), 10000);
				check(!listener.isAlive(), "o Ouvinte deveria ter morrido");
			});
			// Mensagem de vitória com o medidor sumindo aos poucos.
			ctx.waitTicks(8);
			ctx.takeScreenshot("silenciototal_listener_slain");
			ctx.waitTicks(32);
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				int alive = s.overworld().getEntitiesOfClass(Listener.class, player.getBoundingBox().inflate(256)).size();
				check(alive == 0, "morto, ele não deveria voltar na mesma noite");
				boolean ear = !s.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(256),
						item -> item.getItem().is(ModItems.LISTENER_EAR)).isEmpty();
				check(ear, "o Ouvinte deveria dropar a Orelha");
				// Morto, o medidor some pelo resto da noite: nada de ruído.
				check(!NightCycle.isListenerAwake(s.overworld()), "morto, ele não deveria contar como ativo nesta noite");
				check(NoiseTracker.get(player) == 0, "depois que ele morre o ruído deveria zerar, está em " + NoiseTracker.get(player));
				NoiseTracker.add(player, NoiseSources.OPEN_CONTAINER);
				check(NoiseTracker.get(player) == 0, "depois que ele morre o barulho não deveria mais contar");
			});
			check(ctx.computeOnClient(mc -> !NoiseHud.visible()), "depois que ele morre o medidor deveria sumir");
			server.runCommand("gamerule spawn_monsters false");

			// ---------------------------------------------------------------- contador de mortes e Orelha de Ouro
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				ServerLevel level = player.level();
				check(ListenerSpawner.state(s).kills() == 1, "a morte acima deveria contar 1, conta " + ListenerSpawner.state(s).kills());
				// Do ovo gerador (não marcado como natural): não conta e não dropa Orelha de Ouro.
				ListenerSpawner.setState(s, ListenerSpawner.state(s).withKills(4));
				killCurrent(level, summon(player, 6, true));
				check(ListenerSpawner.state(s).kills() == 4, "o Ouvinte do ovo gerador não deveria contar");
				check(countDrops(player, ModItems.GOLDEN_EAR) == 0, "o Ouvinte do ovo gerador não deveria dropar a Orelha de Ouro");
				// 4ª morte: só a Orelha normal. 5ª: também a de Ouro.
				ListenerSpawner.setState(s, ListenerSpawner.state(s).withKills(3));
				Listener fourth = summon(player, 6, true);
				fourth.markNatural();
				killCurrent(level, fourth);
				check(ListenerSpawner.state(s).kills() == 4, "a 4ª morte deveria contar, conta " + ListenerSpawner.state(s).kills());
				check(countDrops(player, ModItems.GOLDEN_EAR) == 0, "a 4ª morte não deveria dropar a Orelha de Ouro");
				Listener fifth = summon(player, 6, true);
				fifth.markNatural();
				killCurrent(level, fifth);
				check(ListenerSpawner.state(s).kills() == 5, "a 5ª morte deveria contar, conta " + ListenerSpawner.state(s).kills());
				check(countDrops(player, ModItems.GOLDEN_EAR) == 1, "a 5ª morte deveria dropar uma Orelha de Ouro");
				// Ovo do Filhote: uma Orelha em cada canto de cima, o ovo no meio e a Orelha de Ouro embaixo.
				CraftingInput input = CraftingInput.of(3, 3, List.of(
						new ItemStack(ModItems.LISTENER_EAR), ItemStack.EMPTY, new ItemStack(ModItems.LISTENER_EAR),
						ItemStack.EMPTY, new ItemStack(Items.EGG), ItemStack.EMPTY,
						ItemStack.EMPTY, new ItemStack(ModItems.GOLDEN_EAR), ItemStack.EMPTY));
				ItemStack egg = s.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
						.map(recipe -> recipe.value().assemble(input)).orElse(ItemStack.EMPTY);
				check(egg.is(ModItems.PUP_EGG), "2 Orelhas + ovo + Orelha de Ouro deveria dar o Ovo do Filhote, deu " + egg);
				s.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(256)).forEach(item -> item.discard());
			});

			// ---------------------------------------------------------------- Filhote: choca do ovo, tem dono, senta e segue
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				ServerLevel level = player.level();
				Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
				BlockPos ground = BlockPos.containing(player.position().add(look.scale(3))).below();
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PUP_EGG));
				UseOnContext use = new UseOnContext(player, InteractionHand.MAIN_HAND,
						new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false));
				check(player.getMainHandItem().useOn(use).consumesAction(), "usar o ovo num bloco deveria funcionar");
				check(player.getMainHandItem().isEmpty(), "o ovo deveria ser gasto");
				List<ListenerPup> pups = level.getEntitiesOfClass(ListenerPup.class, player.getBoundingBox().inflate(16));
				check(pups.size() == 1, "o ovo deveria chocar um filhote, existem " + pups.size());
				ListenerPup pup = pups.getFirst();
				check(pup.isTame() && pup.isOwnedBy(player), "o filhote deveria nascer com dono");
				check(pup.searchesLeft() == ModConfig.get().pupSearches, "o filhote deveria começar com " + ModConfig.get().pupSearches + " buscas");
				// Clique direito: senta; de novo: levanta.
				pup.mobInteract(player, InteractionHand.MAIN_HAND);
				check(pup.isOrderedToSit(), "clique direito deveria sentar o filhote");
				pup.mobInteract(player, InteractionHand.MAIN_HAND);
				check(!pup.isOrderedToSit(), "outro clique direito deveria levantar o filhote");
				// Longe demais, ele aparece do lado do dono (como o lobo).
				pup.teleportTo(player.getX() + 20, player.getY(), player.getZ());
			});
			ctx.waitTicks(60);
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				ListenerPup pup = s.overworld().getEntitiesOfClass(ListenerPup.class, player.getBoundingBox().inflate(64)).getFirst();
				check(pup.distanceTo(player) < 8, "o filhote deveria seguir o dono; está a " + pup.distanceTo(player));
				// Retratos: parado na frente do jogador, olhando para ele.
				pup.setNoAi(true);
				Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
				Vec3 spot = player.position().add(look.scale(2.2));
				pup.snapTo(spot.x, player.getY(), spot.z, player.getYRot() + 180, 0);
				pup.setYHeadRot(player.getYRot() + 180);
				pup.setYBodyRot(player.getYRot() + 160);
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 2000, 0, false, false));
			});
			// Câmera olhando para baixo, para o filhote ficar acima da HUD.
			ctx.runOnClient(mc -> mc.player.setXRot(40));
			ctx.waitTicks(20);
			ctx.takeScreenshot("silenciototal_pup");
			server.runOnServer(s -> pup(s).setAction(ListenerPup.LISTEN));
			ctx.waitTicks(10);
			ctx.takeScreenshot("silenciototal_pup_listen");
			server.runOnServer(s -> pup(s).setAction(ListenerPup.TAP));
			ctx.waitTicks(5);
			ctx.takeScreenshot("silenciototal_pup_tap");
			server.runOnServer(s -> pup(s).setAction(ListenerPup.NO));
			ctx.waitTicks(4);
			ctx.takeScreenshot("silenciototal_pup_no");
			server.runOnServer(s -> {
				ListenerPup pup = pup(s);
				pup.setAction(ListenerPup.IDLE);
				pup.setOrderedToSit(true);
				pup.setInSittingPose(true);
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("silenciototal_pup_sit");
			ctx.runOnClient(mc -> mc.player.setXRot(0));

			// ---------------------------------------------------------------- painel do filhote
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				ListenerPup pup = pup(s);
				player.getInventory().clearContent();
				player.getInventory().add(new ItemStack(Items.DIAMOND, 3));
				player.getInventory().add(new ItemStack(Items.RAW_IRON, 5));
				player.getInventory().add(new ItemStack(Items.DIRT, 12));
				player.getInventory().add(new ItemStack(ModItems.GOLDEN_EAR));
				player.getInventory().add(new ItemStack(ModItems.PUP_EGG));
				// Clique direito sem agachar senta/levanta; agachado abre o painel.
				player.setShiftKeyDown(true);
				pup.mobInteract(player, InteractionHand.MAIN_HAND);
				player.setShiftKeyDown(false);
				check(player.containerMenu instanceof ListenerPupMenu, "agachado + clique direito deveria abrir o painel");
				ListenerPupMenu menu = (ListenerPupMenu) player.containerMenu;
				check(menu.searchesLeft() == pup.searchesLeft() && menu.maxSearches() == ModConfig.get().pupSearches, "o painel deveria mostrar as buscas");
				check(menu.status() == ListenerPupMenu.READY, "com o Ouvinte morto o filhote deveria poder procurar");
				check(!menu.getSlot(0).mayPlace(new ItemStack(Items.DIRT)), "terra não é minério: o espaço deveria recusar");
				check(menu.getSlot(0).mayPlace(new ItemStack(Items.DIAMOND)), "o espaço deveria aceitar diamante");
				check(!menu.canSearch(), "sem item o botão Procurar deveria ficar desativado");
			});
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> mc.gui.toastManager().clear());
			ctx.takeScreenshot("silenciototal_pup_panel_empty");
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				ListenerPupMenu menu = (ListenerPupMenu) player.containerMenu;
				// Clique de shift num diamante do inventário: vai para o espaço (só 1).
				int diamond = -1;
				for (int i = 1; i < menu.slots.size(); i++) {
					if (menu.getSlot(i).getItem().is(Items.DIAMOND)) {
						diamond = i;
					}
				}
				menu.quickMoveStack(player, diamond);
				check(menu.getSlot(0).getItem().is(Items.DIAMOND) && menu.getSlot(0).getItem().getCount() == 1, "o diamante deveria ir para o espaço, um só");
				check(menu.canSearch(), "com diamante o botão Procurar deveria ficar ativo");
				// Algumas buscas já gastas, para o print mostrar marquinhas cheias e vazias.
				pup(s).setSearchesLeft(7);
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("silenciototal_pup_panel_diamond");
			// Noite de caça com o Ouvinte vivo: ele se recusa.
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				ListenerSpawner.setState(s, ListenerSpawner.state(s).killed(NightCycle.day(level) - 1, false));
				check(NightCycle.isListenerAwake(level), "o teste precisa de uma noite de caça com o Ouvinte vivo");
			});
			ctx.waitTicks(10);
			server.runOnServer(s -> {
				ListenerPupMenu menu = (ListenerPupMenu) player(s).containerMenu;
				check(menu.status() == ListenerPupMenu.REFUSES, "em noite de caça o filhote deveria se recusar");
				check(!menu.canSearch(), "em noite de caça o botão Procurar deveria ficar desativado");
				check(!pup(s).startSearch(player(s), new ItemStack(Items.DIAMOND)), "em noite de caça a busca não deveria começar");
			});
			check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof ListenerPupScreen screen && screen.getMenu().status() == ListenerPupMenu.REFUSES),
					"a tela deveria saber que ele se recusa");
			ctx.takeScreenshot("silenciototal_pup_panel_refuses");
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				ListenerSpawner.setState(s, ListenerSpawner.state(s).killed(NightCycle.day(s.overworld()), false));
				pup(s).setSearchesLeft(ModConfig.get().pupSearches);
				// Fechar devolve o diamante.
				player.closeContainer();
				check(player.getInventory().countItem(Items.DIAMOND) == 3, "fechar o painel deveria devolver o diamante");
			});

			// ---------------------------------------------------------------- busca: não acha nada (quartzo no Overworld)
			int[] chunks = new int[1];
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				ListenerPup pup = pup(s);
				pup.setNoAi(false);
				pup.snapTo(player.getX() + 1, player.getY(), player.getZ(), 0, 0);
				chunks[0] = s.overworld().getChunkSource().getLoadedChunksCount();
				OreScanner.resetStats();
				ItemStack quartz = new ItemStack(Items.QUARTZ);
				check(pup.startSearch(player, quartz), "Procurar com quartzo deveria começar a busca");
				check(quartz.isEmpty(), "o quartzo deveria sair do espaço na hora");
				check(pup.isSearching() && !pup.isOrderedToSit(), "o filhote deveria levantar e começar a ouvir");
				check(player.getInventory().countItem(Items.QUARTZ) == 0, "o quartzo não deveria voltar antes do fim da busca");
			});
			ctx.waitTicks(80);
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				ListenerPup pup = pup(s);
				check(pup.searchPhase() == ListenerPup.NOTHING || pup.searchPhase() == ListenerPup.NONE,
						"sem quartzo no Overworld ele deveria desistir; etapa " + pup.searchPhase());
				check(player.getInventory().countItem(Items.QUARTZ) == 1, "não achou: o quartzo deveria voltar para o dono");
				check(pup.searchesLeft() == ModConfig.get().pupSearches, "não achou: não deveria gastar busca");
				int after = s.overworld().getChunkSource().getLoadedChunksCount();
				check(after <= chunks[0], "a varredura não deveria carregar chunks (" + chunks[0] + " -> " + after + ")");
				check(OreScanner.pending() == 0, "a fila da varredura deveria ficar vazia");
				// O tempo por tick é medido no PupScanBenchmark (mundo normal, com aquecimento): aqui é a
				// primeira busca da JVM, ainda fria, e o número varia demais para virar regra.
				SilencioTotal.LOGGER.info("[teste] varredura sem minério (fria): tick mais caro {} ms", OreScanner.maxTickNanos() / 1e6);
			});

			// (o desempenho com minérios de verdade fica no PupScanBenchmark, em mundo normal)
			ctx.waitTicks(40);

			// ---------------------------------------------------------------- busca: guia até o minério e marca
			// Uma ponte de pedra lisa para o leste, 17 blocos acima do chão (o mundo de teste é plano e
			// raso), para o caminho ser previsível, e um detrito ancestral (que não existe no Overworld)
			// 30 blocos à frente e 20 abaixo da ponte.
			BlockPos[] ore = new BlockPos[1];
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				ServerLevel level = player.level();
				BlockPos ground = player.blockPosition();
				BlockPos start = ground.above(17);
				for (int x = -3; x <= 36; x++) {
					for (int z = -2; z <= 2; z++) {
						level.setBlockAndUpdate(start.offset(x, -1, z), Blocks.STONE.defaultBlockState());
						for (int y = 0; y <= 3; y++) {
							level.setBlockAndUpdate(start.offset(x, y, z), Blocks.AIR.defaultBlockState());
						}
					}
				}
				ore[0] = start.offset(30, -20, 0);
				check(level.getBlockState(ore[0].below()).isSolid(), "o detrito deveria ficar dentro do chão, em " + ore[0]);
				level.setBlockAndUpdate(ore[0], Blocks.ANCIENT_DEBRIS.defaultBlockState());
				player.teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, Set.of(), -90, 20, true);
				ListenerPup pup = pup(s);
				pup.snapTo(start.getX() + 2.5, start.getY(), start.getZ() + 0.5, -90, 0);
				player.getInventory().add(new ItemStack(Items.NETHERITE_SCRAP));
				ListenerPupMenu menu = openPanel(player, pup);
				int scrap = -1;
				for (int i = 1; i < menu.slots.size(); i++) {
					if (menu.getSlot(i).getItem().is(Items.NETHERITE_SCRAP)) {
						scrap = i;
					}
				}
				menu.quickMoveStack(player, scrap);
				// A última busca dele: depois de marcar, ao ser levantado, ele vai embora.
				pup.setSearchesLeft(1);
				check(menu.clickMenuButton(player, ListenerPupMenu.SEARCH_BUTTON), "o botão Procurar deveria começar a busca");
				check(!(player.containerMenu instanceof ListenerPupMenu), "Procurar deveria fechar o painel");
				check(player.getInventory().countItem(Items.NETHERITE_SCRAP) == 0, "o fragmento deveria ser gasto na hora");
			});
			// Sem a interface nos prints da busca (avisos de receita, mão, barra), só o filhote.
			ctx.runOnClient(mc -> {
				mc.gui.toastManager().clear();
				mc.gui.hud.toggle();
			});
			ctx.waitTicks(20);
			ctx.takeScreenshot("silenciototal_pup_search_tap");
			ctx.waitTicks(30);
			ctx.takeScreenshot("silenciototal_pup_search_listen");
			ctx.waitTicks(40);
			server.runOnServer(s -> {
				ListenerPup pup = pup(s);
				check(pup.searchPhase() == ListenerPup.GUIDING, "deveria achar o detrito e sair guiando; etapa " + pup.searchPhase());
				check(ore[0].equals(pup.searchTarget()), "o alvo deveria ser o detrito em " + ore[0] + ", é " + pup.searchTarget());
				check(pup.searchesLeft() == 0, "achou: deveria gastar uma busca");
				// Clique direito no meio da busca não senta.
				pup.mobInteract(player(s), InteractionHand.MAIN_HAND);
				check(!pup.isOrderedToSit(), "guiando, o clique direito não deveria sentar o filhote");
			});
			// O dono fica parado: ele anda um pouco e espera.
			ctx.waitTicks(120);
			server.runOnServer(s -> {
				ListenerPup pup = pup(s);
				double distance = pup.distanceTo(player(s));
				check(pup.searchPhase() == ListenerPup.GUIDING && distance > 6 && distance < 12.5,
						"com o dono parado ele deveria esperar a uns 10 blocos; está a " + distance + ", etapa " + pup.searchPhase());
			});
			ctx.takeScreenshot("silenciototal_pup_search_waiting");
			// O Ouvinte acorda no meio do caminho: ele para e se esconde, mesmo com o dono do lado.
			double[] hidingAt = new double[1];
			server.runOnServer(s -> {
				ListenerSpawner.setState(s, ListenerSpawner.state(s).killed(NightCycle.day(s.overworld()) - 1, false));
				ListenerPup pup = pup(s);
				ServerPlayer player = player(s);
				player.teleportTo(player.level(), pup.getX() - 3, pup.getY(), pup.getZ(), Set.of(), -90, 25, true);
			});
			ctx.waitTicks(5);
			server.runOnServer(s -> {
				ListenerPup pup = pup(s);
				check(pup.isHiding() && pup.searchPhase() == ListenerPup.GUIDING, "com o Ouvinte acordado ele deveria se esconder e guardar o alvo");
				ListenerPupMenu menu = openPanel(player(s), pup);
				check(menu.status() == ListenerPupMenu.REFUSES, "escondido, o painel deveria avisar do Ouvinte");
				player(s).closeContainer();
				hidingAt[0] = pup.getX();
			});
			ctx.waitTicks(60);
			server.runOnServer(s -> {
				ListenerPup pup = pup(s);
				check(pup.isHiding() && Math.abs(pup.getX() - hidingAt[0]) < 0.5, "escondido, ele não deveria andar; andou "
						+ Math.abs(pup.getX() - hidingAt[0]));
				// O Ouvinte morre: ele continua.
				ListenerSpawner.setState(s, ListenerSpawner.state(s).killed(NightCycle.day(s.overworld()), false));
			});
			ctx.waitTicks(5);
			server.runOnServer(s -> check(!pup(s).isHiding() && pup(s).searchPhase() == ListenerPup.GUIDING,
					"com o Ouvinte morto ele deveria voltar a guiar"));
			// Agora o dono vai atrás, sempre uns 3 blocos atrás dele.
			boolean marked = false;
			for (int i = 0; i < 300 && !marked; i++) {
				boolean follow = i % 5 == 0;
				marked = server.computeOnServer(s -> {
					ListenerPup pup = pup(s);
					if (pup.searchPhase() != ListenerPup.GUIDING) {
						return true;
					}
					if (follow) {
						ServerPlayer player = player(s);
						player.teleportTo(player.level(), pup.getX() - 3, pup.getY(), pup.getZ(), Set.of(), -90, 25, true);
					}
					return false;
				});
				ctx.waitTick();
			}
			check(marked, "o filhote deveria chegar perto do detrito");
			// Logo depois dos pulinhos: o eco e as partículas.
			ctx.waitTicks(34);
			ctx.takeScreenshot("silenciototal_pup_search_mark");
			ctx.runOnClient(mc -> mc.gui.hud.toggle());
			ctx.waitTicks(20);
			server.runOnServer(s -> {
				ListenerPup pup = pup(s);
				double dx = pup.getX() - (ore[0].getX() + 0.5);
				double dz = pup.getZ() - (ore[0].getZ() + 0.5);
				double horizontal = Math.sqrt(dx * dx + dz * dz);
				check(pup.searchPhase() == ListenerPup.NONE && pup.isOrderedToSit(), "depois de marcar ele deveria ficar sentado; etapa "
						+ pup.searchPhase());
				check(horizontal <= ListenerPup.ARRIVE_DISTANCE + 0.5 && horizontal >= 2.5,
						"ele deveria marcar a 3 a 5 blocos (na horizontal) do minério; está a " + horizontal);
				check(near(ListenerPup.echoPitch(0), 1.6f) && near(ListenerPup.echoPitch(100), 0.5f) && ListenerPup.echoPitch(20) < ListenerPup.echoPitch(5),
						"o eco deveria ficar mais grave com a profundidade");
				// Era a última busca: levantado, ele se enterra e vai embora.
				check(pup.searchesLeft() == 0, "a última busca deveria zerar as buscas");
				pup.mobInteract(player(s), InteractionHand.MAIN_HAND);
				check(!pup.isOrderedToSit(), "depois de marcar, o clique direito deveria levantar o filhote");
			});
			ctx.waitTicks(5);
			server.runOnServer(s -> check(s.overworld().getEntitiesOfClass(ListenerPup.class, player(s).getBoundingBox().inflate(64)).isEmpty(),
					"sem buscas, levantado, o filhote deveria se enterrar e sumir"));

			// ---------------------------------------------------------------- receita do Ovo do Filhote na mesa
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				ServerLevel level = player.level();
				BlockPos table = player.blockPosition().offset(2, 0, 2);
				level.setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState());
				player.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new CraftingMenu(id, inventory, ContainerLevelAccess.create(level, table)),
						Component.translatable("container.crafting")));
				CraftingMenu menu = (CraftingMenu) player.containerMenu;
				menu.getSlot(1).set(new ItemStack(ModItems.LISTENER_EAR));
				menu.getSlot(3).set(new ItemStack(ModItems.LISTENER_EAR));
				menu.getSlot(5).set(new ItemStack(Items.EGG));
				menu.getSlot(8).set(new ItemStack(ModItems.GOLDEN_EAR));
			});
			ctx.waitTicks(10);
			server.runOnServer(s -> check(((CraftingMenu) player(s).containerMenu).getSlot(0).getItem().is(ModItems.PUP_EGG),
					"a mesa deveria mostrar o Ovo do Filhote como resultado"));
			ctx.takeScreenshot("silenciototal_pup_egg_recipe");
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				player.closeContainer();
				player.getInventory().clearContent();
				player.level().setBlockAndUpdate(player.blockPosition().offset(2, 0, 2), Blocks.AIR.defaultBlockState());
			});

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

			// ---------------------------------------------------------------- Filhote: vida zerada, foge e volta de manhã
			long[] fledDay = new long[1];
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				ServerLevel level = player.level();
				ListenerPup pup = PupEggItem.hatch(level, player, null, player.blockPosition().offset(3, 0, 0), false);
				check(pup != null, "o ovo deveria chocar outro filhote");
				pup.setSearchesLeft(6);
				pup.setCustomName(Component.literal("Bolinha"));
				pup.hurtServer(level, level.damageSources().generic(), 100);
				check(pup.isAlive() && pup.searchPhase() == ListenerPup.FLEEING, "com dano letal ele deveria fugir em vez de morrer");
				pup.hurtServer(level, level.damageSources().generic(), 100);
				check(pup.isAlive(), "fugindo, nada mais deveria machucá-lo");
				fledDay[0] = NightCycle.day(s.overworld());
				List<PupReturn> returns = PupReturns.all(s);
				check(returns.size() == 1 && returns.getFirst().searchesLeft() == 6 && returns.getFirst().day() == fledDay[0] + 1
						&& returns.getFirst().owner().equals(player.getUUID()), "a volta dele deveria ficar marcada: " + returns);
			});
			ctx.waitTicks(50);
			server.runOnServer(s -> check(s.overworld().getEntitiesOfClass(ListenerPup.class, player(s).getBoundingBox().inflate(64)).isEmpty(),
					"depois de correr ele deveria se enterrar"));
			// No mesmo dia ele não volta.
			ctx.waitTicks(40);
			server.runOnServer(s -> check(PupReturns.all(s).size() == 1, "ele só deveria voltar na manhã seguinte"));
			server.runCommand("time set " + (24000 * (fledDay[0] + 1) + 100));
			ctx.waitTicks(40);
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				List<ListenerPup> back = s.overworld().getEntitiesOfClass(ListenerPup.class, player.getBoundingBox().inflate(16));
				check(back.size() == 1, "de manhã ele deveria voltar perto do dono; filhotes perto: " + back.size());
				ListenerPup pup = back.getFirst();
				check(pup.isOwnedBy(player) && pup.searchesLeft() == 6 && pup.getHealth() == pup.getMaxHealth(),
						"ele deveria voltar do mesmo dono, com as mesmas buscas e vida cheia");
				check(pup.getCustomName() != null && pup.getCustomName().getString().equals("Bolinha"), "ele deveria voltar com o nome");
				check(PupReturns.all(s).isEmpty(), "a volta deveria sair da lista");
				pup.discard();
			});
		}
	}

	/** Torna {@code listener} "o" Ouvinte do mundo e mata ele na hora (o drop sai em seguida). */
	private static void killCurrent(ServerLevel level, Listener listener) {
		MinecraftServer server = level.getServer();
		ListenerSpawner.setState(server, ListenerSpawner.state(server).withCurrent(listener.getUUID(), NightCycle.day(level), listener.getHealth()));
		listener.hurtServer(level, level.damageSources().genericKill(), 10000);
		check(!listener.isAlive(), "o Ouvinte deveria ter morrido");
	}

	private static int countDrops(ServerPlayer player, net.minecraft.world.item.Item item) {
		return player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(256),
				entity -> entity.getItem().is(item)).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
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

	/**
	 * Onde está a porta da última casinha. Fica fixo: o jogador pode levar um empurrão lá dentro e
	 * mudar de bloco, e aí "a porta ao lado dele" seria a parede.
	 */
	private static BlockPos houseDoor = BlockPos.ZERO;

	/** Casinha de pedra com interior 3x3x3 em volta do jogador e uma porta no lado +x. */
	private static void buildHouse(ServerLevel level, BlockPos feet, BlockState door) {
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 3, 2))) {
			boolean shell = Math.abs(pos.getX() - feet.getX()) == 2 || Math.abs(pos.getZ() - feet.getZ()) == 2
					|| pos.getY() == feet.getY() - 1 || pos.getY() == feet.getY() + 3;
			level.setBlockAndUpdate(pos, shell ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
		}
		BlockPos doorPos = feet.offset(2, 0, 0);
		houseDoor = doorPos;
		BlockState lower = door.setValue(DoorBlock.FACING, net.minecraft.core.Direction.WEST)
				.setValue(DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER);
		level.setBlock(doorPos, lower, 3);
		level.setBlock(doorPos.above(), lower.setValue(DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
	}

	private static void clearHouse(ServerLevel level, BlockPos feet) {
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 3, 2))) {
			level.setBlockAndUpdate(pos, pos.getY() < feet.getY() ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
		}
	}

	private static void spawnOutsideDoor(ServerPlayer player) {
		ServerLevel level = player.level();
		Listener listener = ModEntities.LISTENER.create(level, EntitySpawnReason.COMMAND);
		listener.snapTo(player.getBlockX() + 9.5, player.getY(), player.getBlockZ() + 0.5, 90, 0);
		level.addFreshEntity(listener);
		NoiseTracker.set(player, 90);
	}

	/** Mantém o jogador barulhento e espera a porta da casinha sumir (ou o tempo acabar). */
	private static boolean waitForDoor(ClientGameTestContext ctx, TestServerContext server, int ticks, boolean screenshot) {
		for (int waited = 0; waited < ticks; waited += 20) {
			ctx.waitTicks(20);
			boolean gone = server.computeOnServer(s -> {
				ServerPlayer player = player(s);
				NoiseTracker.set(player, 90);
				return !(player.level().getBlockState(houseDoor).getBlock() instanceof DoorBlock);
			});
			if (screenshot && waited == 160) {
				ctx.takeScreenshot("silenciototal_door_bash");
			}
			if (gone) {
				return true;
			}
		}
		return false;
	}

	private static Listener listener(MinecraftServer server) {
		List<Listener> all = server.overworld().getEntitiesOfClass(Listener.class, player(server).getBoundingBox().inflate(256));
		check(!all.isEmpty(), "nenhum Ouvinte no mundo");
		return all.getFirst();
	}

	private static ListenerPup pup(MinecraftServer server) {
		List<ListenerPup> all = server.overworld().getEntitiesOfClass(ListenerPup.class, player(server).getBoundingBox().inflate(64));
		check(!all.isEmpty(), "nenhum filhote no mundo");
		return all.getFirst();
	}

	private static ListenerPupMenu openPanel(ServerPlayer player, ListenerPup pup) {
		player.setShiftKeyDown(true);
		pup.mobInteract(player, InteractionHand.MAIN_HAND);
		player.setShiftKeyDown(false);
		check(player.containerMenu instanceof ListenerPupMenu, "agachado + clique direito deveria abrir o painel");
		return (ListenerPupMenu) player.containerMenu;
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
