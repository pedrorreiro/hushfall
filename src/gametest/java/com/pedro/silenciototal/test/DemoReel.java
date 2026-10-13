package com.pedro.silenciototal.test;

import com.mojang.blaze3d.systems.RenderSystem;
import com.pedro.silenciototal.ModConfig;
import com.pedro.silenciototal.entity.Listener;
import com.pedro.silenciototal.noise.NoiseTracker;
import com.pedro.silenciototal.registry.ModEntities;
import com.pedro.silenciototal.registry.ModItems;
import com.pedro.silenciototal.registry.ModSounds;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.function.IntUnaryOperator;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Vídeo de demonstração: roda uma cena roteirizada e grava 3 quadros por tick (60 fps, com a
 * interpolação do próprio jogo entre os ticks) e um log dos sons tocados, para montar o vídeo com
 * tools/montar_video.py.
 *
 * Nas tomadas de cinema a câmera é uma entidade solta e um manequim faz o papel do jogador (o
 * jogador local não aparece para uma câmera que não é ele); o jogador de verdade continua lá,
 * invisível, para o Ouvinte ouvir e caçar.
 *
 * Só roda com -Dhushfall.demo=&lt;pasta&gt; (./gradlew runClientGameTest -PdemoReel=&lt;pasta&gt;),
 * e aí os testes normais ficam de fora.
 */
public class DemoReel implements FabricClientGameTest {
	static final String OUT = System.getProperty("hushfall.demo");
	private static final int SUBFRAMES = 3;

	private Path dir;
	private TestServerContext serverRef;
	private int frame;
	private PrintWriter log;
	private final AtomicInteger pending = new AtomicInteger();
	/** Câmera solta para as tomadas de cinema; null = primeira pessoa. */
	private Entity camera;
	/** O "jogador" que aparece nas tomadas de cinema. */
	private Mannequin actor;
	private BlockPos base;
	private final java.util.Random shake = new java.util.Random(5);

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (OUT == null) {
			return;
		}
		dir = Path.of(OUT);
		try {
			Files.createDirectories(dir.resolve("frames"));
			log = new PrintWriter(Files.newBufferedWriter(dir.resolve("events.tsv")));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		ctx.getInput().resizeWindow(1920, 1080);
		ctx.runOnClient(mc -> {
			mc.options.languageCode = "pt_br";
			mc.getLanguageManager().setSelected("pt_br");
			mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
			mc.options.guiScale().set(3);
			mc.options.renderDistance().set(10);
			mc.options.gamma().set(1.0);
			mc.options.fov().set(75);
			mc.options.bobView().set(true);
			// Mudo no Mac: os sons são registrados pelo listener e mixados depois.
			mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
			mc.getSoundManager().addListener((sound, event, range) -> {
				Vec3 cam = mc.gameRenderer.mainCamera().position();
				log.printf(Locale.ROOT, "sound\t%d\t%s\t%s\t%.3f\t%.3f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%s\t%s%n",
						frame, sound.getIdentifier(), sound.getSound().getLocation(), sound.getVolume(), sound.getPitch(),
						sound.getX(), sound.getY(), sound.getZ(), cam.x, cam.y, cam.z,
						sound.isRelative(), sound.getAttenuation());
			});
		});

		try (TestSingleplayerContext sp = ctx.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(s -> s.setSeed("hushfall-demo"))
				.create()) {
			TestServerContext server = sp.getServer();
			serverRef = server;
			ModConfig.get().nightChance = 1f;
			server.runCommand("gamemode survival @a");
			server.runCommand("difficulty normal");
			server.runCommand("weather clear 100000");
			server.runCommand("gamerule spawn_monsters false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule advance_time false");
			server.runCommand("time set 24000");

			setup(ctx, server);
			sunset(ctx, server);
			emerge(ctx, server);
			walkPast(ctx, server);
			meterRises(ctx, server);
			roar(ctx, server);
			bell(ctx, server);
			while (pending.get() > 0) {
				ctx.waitTick();
			}
		}
		log.close();
	}

	// ------------------------------------------------------------------ cenário

	private void setup(ClientGameTestContext ctx, TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			ServerLevel level = player.level();
			var plains = level.findClosestBiome3d(b -> b.is(net.minecraft.world.level.biome.Biomes.PLAINS),
					player.blockPosition(), 3000, 32, 64);
			BlockPos found = plains != null ? plains.getFirst() : player.blockPosition();
			base = flattestDrySpot(level, found);
			player.teleportTo(level, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, Set.of(), 0, 0, true);
		});
		ctx.waitTicks(80);
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			ServerLevel level = player.level();
			base = ground(level, base.getX(), base.getZ());
			clearing(level, base, 27);
			BlockPos fire = ground(level, base.getX() + 3, base.getZ() + 5);
			level.setBlockAndUpdate(fire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
			for (int[] o : new int[][] {{-3, 3}, {5, -2}, {9, -2}, {12, 4}}) {
				BlockPos post = ground(level, base.getX() + o[0], base.getZ() + o[1]);
				level.setBlockAndUpdate(post, Blocks.OAK_FENCE.defaultBlockState());
				level.setBlockAndUpdate(post.above(), Blocks.LANTERN.defaultBlockState());
			}
			player.getInventory().clearContent();
			player.getInventory().setItem(0, new ItemStack(ModItems.SILENT_DAGGER));
			player.getInventory().setItem(1, new ItemStack(ModItems.DEAFENING_BELL));
			player.getInventory().setItem(2, new ItemStack(Blocks.WOOL.white(), 32));
			player.getInventory().setItem(3, new ItemStack(Blocks.TORCH, 16));
			player.getInventory().setSelectedSlot(0);
			player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
			player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
			player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
			player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.FELT_BOOTS));
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100000, 0, false, false));
		});
		ctx.waitTicks(60);
		ctx.runOnClient(mc -> {
			actor = Mannequin.create(EntityTypes.MANNEQUIN, mc.level);
			actor.setId(-4242);
			try {
				var hide = Mannequin.class.getDeclaredMethod("setHideDescription", boolean.class);
				hide.setAccessible(true);
				hide.invoke(actor, true);
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				actor.setItemSlot(slot, mc.player.getItemBySlot(slot).copy());
			}
			actor.setPos(mc.player.getX(), mc.player.getY() - 500, mc.player.getZ());
			mc.level.addEntity(actor);
		});
	}

	// ------------------------------------------------------------------ tomadas

	/** Pôr do sol: o jogador olhando o horizonte, a fogueira na frente, a câmera subindo devagar. */
	private void sunset(ClientGameTestContext ctx, TestServerContext server) {
		Vec3 p = Vec3.atBottomCenterOf(base).add(1, 0, 3);
		server.runOnServer(s -> player(s).teleportTo(player(s).level(), p.x, p.y, p.z, Set.of(), 95, 0, true));
		ctx.waitTicks(3);
		ctx.runOnClient(mc -> setRot(mc.player, 95, -4));
		useCamera(ctx, true);
		mark("A");
		int n = 100;
		rec(ctx, n, i -> {
			float t = i / (float) (n - 1);
			server.runCommand("time set " + (24000 + 11500 + Math.round(t * 1050)));
			float e = (float) (0.5 - 0.5 * Math.cos(t * Math.PI));
			Vec3 pos = p.add(Mth.lerp(e, 5.5, 3.4), Mth.lerp(e, 0.9, 1.9), Mth.lerp(e, 2.6, 1.0));
			Vec3 target = p.add(Mth.lerp(e, -1.0, -3.5), Mth.lerp(e, 1.4, 1.6), Mth.lerp(e, 0.2, -0.6));
			placeCamera(ctx, pos, target, 0);
		});
	}

	/** Ele sai da terra, visto bem de baixo. */
	private void emerge(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("time set " + (24000 + 12900));
		Vec3 spot = Vec3.atBottomCenterOf(base).add(18, 0, 6);
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			BlockPos floor = ground(level, (int) Math.floor(spot.x), (int) Math.floor(spot.z));
			for (BlockPos pos : BlockPos.betweenClosed(floor.offset(-8, 0, -8), floor.offset(3, 0, 3))) {
				if (level.getBlockState(pos).is(Blocks.SHORT_GRASS)) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
				}
			}
			Listener listener = ModEntities.LISTENER.create(level, EntitySpawnReason.COMMAND);
			listener.snapTo(floor.getX() + 0.5, floor.getY() - 2.8, floor.getZ() + 0.5, 131, 0);
			listener.setYHeadRot(131);
			listener.setNoAi(true);
			listener.setNoGravity(true);
			level.addFreshEntity(listener);
		});
		ctx.waitTicks(4);
		mark("B");
		int n = 96;
		rec(ctx, n, i -> {
			float t = i / (float) (n - 1);
			float rise = Mth.clamp((i - 6) / 42f, 0, 1);
			server.runOnServer(s -> {
				Listener listener = listener(s);
				ServerLevel level = (ServerLevel) listener.level();
				BlockPos floor = ground(level, listener.getBlockX(), listener.getBlockZ());
				if (i == 5) {
					level.playSound(null, listener.getX(), floor.getY(), listener.getZ(), ModSounds.LISTENER_EMERGE, SoundSource.HOSTILE, 4f, 0.75f);
				}
				if (i >= 6 && i < 48) {
					float eased = 1 - (1 - rise) * (1 - rise);
					listener.teleportTo(listener.getX(), floor.getY() - 2.8 + 2.8 * eased, listener.getZ());
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(floor.below())),
							listener.getX(), floor.getY() + 0.1, listener.getZ(), 14, 0.5, 0.1, 0.5, 0.15);
					if (i % 5 == 0) {
						level.sendParticles(ParticleTypes.LARGE_SMOKE, listener.getX(), floor.getY() + 0.3, listener.getZ(), 3, 0.4, 0.2, 0.4, 0.01);
					}
				}
				if (i == 50) {
					listener.setNoGravity(false);
					listener.playSound(ModSounds.LISTENER_CLICK, 2f, 1f);
				}
				if (i == 60 || i == 84) {
					listener.playSound(ModSounds.LISTENER_SNIFF, 2f, 1f);
				}
				if (i == 72) {
					listener.playSound(ModSounds.LISTENER_CLICK, 2f, 0.9f);
				}
			});
			Vec3 pos = spot.add(Mth.lerp(t, -3.9, -2.9), Mth.lerp(t, 0.35, 0.3), Mth.lerp(t, -3.3, -2.5));
			placeCamera(ctx, pos, spot.add(0, 0.5 + 1.9 * rise, 0), 0);
		});
		server.runOnServer(s -> listener(s).discard());
	}

	/** Agachado em silêncio: ele vem farejando e passa do lado. Câmera baixa atrás do jogador, depois primeira pessoa. */
	private void walkPast(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("time set " + (24000 + 14500));
		Vec3 at = Vec3.atBottomCenterOf(base);
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			player.teleportTo(player.level(), at.x, at.y, at.z, Set.of(), -90, 8, true);
			NoiseTracker.set(player, 0);
		});
		ctx.runOnClient(mc -> mc.options.keyShift.setDown(true));
		ctx.waitTicks(10);
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			BlockPos floor = ground(level, base.getX() + 16, base.getZ() + 2);
			Listener listener = ModEntities.LISTENER.create(level, EntitySpawnReason.COMMAND);
			listener.snapTo(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5, 90, 0);
			level.addFreshEntity(listener);
		});
		ctx.waitTicks(2);
		server.runOnServer(s -> listener(s).hearSound(Vec3.atBottomCenterOf(base).add(-14, 0, -1), 30));
		ctx.runOnClient(mc -> setRot(mc.player, -90, 8));
		useCamera(ctx, true);
		mark("C1");
		rec(ctx, 80, i -> {
			server.runOnServer(s -> NoiseTracker.set(player(s), 0));
			Vec3 l = server.computeOnServer(s -> listener(s).position());
			float t = i / 79f;
			Vec3 pos = at.add(-2.4 - t * 0.4, 0.75, -1.7 - t * 0.3);
			Vec3 head = at.add(0.3, 1.2, 0);
			Vec3 target = head.lerp(l.add(0, 2.0, 0), 0.55 + 0.25 * t);
			placeCamera(ctx, pos, target, 0);
		});
		useCamera(ctx, false);
		mark("C2");
		rec(ctx, 60, i -> {
			server.runOnServer(s -> NoiseTracker.set(player(s), 0));
			Vec3 target = server.computeOnServer(s -> listener(s).position().add(0, 2.1, 0));
			ctx.runOnClient(mc -> turnTowards(mc, target, 0.15f));
		});
	}

	/** Levanta e anda: o medidor sobe, ele desconfia, vira e vem. */
	private void meterRises(ClientGameTestContext ctx, TestServerContext server) {
		ctx.runOnClient(mc -> mc.options.keyShift.setDown(false));
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			Listener listener = listener(s);
			Vec3 at = player.position().add(-13, 0, 11);
			BlockPos floor = ground(player.level(), (int) Math.floor(at.x), (int) Math.floor(at.z));
			listener.teleportTo(at.x, floor.getY(), at.z);
			// Parado só escutando: com o medidor no médio ele viria atrás, e a cena é de tensão, não de ataque.
			listener.setNoAi(true);
			listener.getNavigation().stop();
			listener.setYRot(90);
			listener.setYHeadRot(90);
			NoiseTracker.set(player, 0);
		});
		ctx.runOnClient(mc -> setRot(mc.player, 80, 8));
		ctx.waitTicks(4);
		mark("D");
		float[] noise = {0};
		rec(ctx, 135, i -> {
			ctx.runOnClient(mc -> mc.options.keyUp.setDown(i > 6 && i < 100));
			if (i > 6 && i < 128) {
				noise[0] = Math.min(68, noise[0] + 0.58f);
			}
			float value = noise[0];
			server.runOnServer(s -> {
				NoiseTracker.set(player(s), value);
				Listener listener = listener(s);
				Vec3 d = player(s).position().subtract(listener.position());
				float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90;
				float head = Mth.approachDegrees(listener.getYHeadRot(), yaw, i > 100 ? 14 : 3);
				listener.setYHeadRot(head);
				listener.setYRot(head);
				listener.setYBodyRot(head);
			});
			Vec3 target = server.computeOnServer(s -> listener(s).position().add(0, 2.2, 0));
			if (i > 75) {
				ctx.runOnClient(mc -> turnTowards(mc, target, 0.08f));
			}
		});
		ctx.runOnClient(mc -> mc.options.keyUp.setDown(false));
	}

	/** Barulho demais: o rugido visto de baixo, com a câmera tremendo, e a caçada em primeira pessoa. */
	private void roar(ClientGameTestContext ctx, TestServerContext server) {
		Vec3 p = Vec3.atBottomCenterOf(base).add(1, 0, 1);
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			player.teleportTo(player.level(), p.x, p.y, p.z, Set.of(), -90, 0, true);
			player.setHealth(player.getMaxHealth());
			NoiseTracker.set(player, 40);
			Listener listener = listener(s);
			listener.setNoAi(false);
			listener.teleportTo(p.x + 10, p.y, p.z);
			listener.setYRot(90);
			listener.setYHeadRot(90);
			listener.setYBodyRot(90);
		});
		ctx.runOnClient(mc -> setRot(mc.player, -90, -6));
		ctx.waitTicks(5);
		useCamera(ctx, true);
		mark("E1");
		rec(ctx, 36, i -> i >= 22 ? SUBFRAMES * 2 : SUBFRAMES, i -> {
			if (i == 3) {
				server.runOnServer(s -> NoiseTracker.set(player(s), 88));
			}
			Vec3 l = server.computeOnServer(s -> listener(s).position());
			float t = i / 35f;
			float amp = i < 4 ? 0 : 0.06f * (1 - t * 0.5f);
			Vec3 pos = l.add(Mth.lerp(t, -2.3, -1.6), 0.5, Mth.lerp(t, 2.0, 1.4));
			placeCamera(ctx, pos, l.add(0, 2.6, 0), amp);
		});
		useCamera(ctx, false);
		mark("E2");
		rec(ctx, 60, i -> {
			server.runOnServer(s -> NoiseTracker.set(player(s), Math.max(75, NoiseTracker.get(player(s)))));
			Vec3 target = server.computeOnServer(s -> listener(s).position().add(0, 2.2, 0));
			ctx.runOnClient(mc -> turnTowards(mc, target, 0.3f));
		});
	}

	/** O Sino Ensurdecedor: toca em primeira pessoa, e de longe a gente vê ele cambaleando enquanto você sai de fininho. */
	private void bell(ClientGameTestContext ctx, TestServerContext server) {
		server.runOnServer(s -> player(s).getInventory().setSelectedSlot(1));
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(1));
		ctx.waitTicks(2);
		mark("F1");
		rec(ctx, 26, i -> {
			if (i == 6) {
				server.runOnServer(s -> {
					ServerPlayer player = player(s);
					ModItems.DEAFENING_BELL.use(player.level(), player, InteractionHand.MAIN_HAND);
				});
				ctx.runOnClient(mc -> mc.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false));
			}
			Vec3 target = server.computeOnServer(s -> listener(s).position().add(0, 2.0, 0));
			ctx.runOnClient(mc -> turnTowards(mc, target, 0.25f));
		});
		useCamera(ctx, true);
		Vec3 me = server.computeOnServer(s -> player(s).position());
		Vec3 him = server.computeOnServer(s -> listener(s).position());
		Vec3 mid = me.lerp(him, 0.5);
		Vec3 side = new Vec3(-(him.z - me.z), 0, him.x - me.x).normalize();
		mark("F2");
		int n = 90;
		rec(ctx, n, i -> i >= 36 ? SUBFRAMES * 2 : SUBFRAMES, i -> {
			server.runOnServer(s -> NoiseTracker.set(player(s), Math.max(20, 70 - i)));
			ctx.runOnClient(mc -> mc.options.keyDown.setDown(i > 10 && i < 85));
			Vec3 target = server.computeOnServer(s -> listener(s).position().add(0, 2.0, 0));
			ctx.runOnClient(mc -> turnTowards(mc, target, 0.2f));
			float t = i / (float) (n - 1);
			Vec3 pos = mid.add(side.scale(Mth.lerp(t, 4.8, 4.0))).add(0, Mth.lerp(t, 1.1, 1.4), 0);
			placeCamera(ctx, pos, mid.add(0, 1.4, 0), 0);
		});
		ctx.runOnClient(mc -> mc.options.keyDown.setDown(false));
	}

	// ------------------------------------------------------------------ captura

	/**
	 * Roda {@code ticks} ticks; depois de cada um renderiza {@link #SUBFRAMES} quadros com o
	 * tempo parcial entre o tick anterior e o atual (é o que o jogo faz a 60 fps) e salva cada um.
	 */
	private void rec(ClientGameTestContext ctx, int ticks, IntConsumer beforeTick) {
		rec(ctx, ticks, i -> SUBFRAMES, beforeTick);
	}

	/** Igual, mas com quantos quadros por tick em cada um: o dobro de quadros vira câmera lenta de verdade (0,5x a 60 fps). */
	private void rec(ClientGameTestContext ctx, int ticks, IntUnaryOperator subframesAt, IntConsumer beforeTick) {
		for (int i = 0; i < ticks; i++) {
			beforeTick.accept(i);
			int subframes = subframesAt.applyAsInt(i);
			// A armadura segura bem, mas não deixa morrer no meio da cena.
			serverRef.runOnServer(s -> {
				ServerPlayer player = player(s);
				if (player.getHealth() < 8) {
					player.setHealth(8);
				}
			});
			ctx.runOnClient(mc -> {
				mc.gui.toastManager().clear();
				mirrorActor(mc);
				for (int sub = 1; sub <= subframes; sub++) {
					float partial = sub / (float) subframes;
					DeltaTracker delta = new DeltaTracker() {
						@Override
						public float getGameTimeDeltaTicks() {
							return 1f / subframes;
						}

						@Override
						public float getGameTimeDeltaPartialTick(boolean ignoreFrozenGame) {
							return partial;
						}

						@Override
						public float getRealtimeDeltaTicks() {
							return 1f / subframes;
						}
					};
					mc.gameRenderer.update(delta);
					mc.gameRenderer.extract(delta, true);
					mc.gameRenderer.render();
					RenderSystem.getDevice().createCommandEncoder().submit();
					Path file = dir.resolve("frames").resolve(String.format(Locale.ROOT, "%05d.png", frame));
					frame++;
					pending.incrementAndGet();
					Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
						try (image) {
							image.writeToFile(file);
						} catch (IOException e) {
							throw new RuntimeException(e);
						} finally {
							pending.decrementAndGet();
						}
					});
				}
			});
			ctx.waitTick();
		}
		server(ctx);
		log.flush();
	}

	private void server(ClientGameTestContext ctx) {
		// Não deixa acumular gravação pendente demais entre as tomadas.
		while (pending.get() > 30) {
			ctx.waitTick();
		}
	}

	private void mark(String shot) {
		if (serverRef != null) {
			serverRef.runCommand("kill @e[type=!minecraft:player,type=!silenciototal:listener,type=!minecraft:item]");
		}
		log.println("shot\t" + shot + "\t" + frame);
	}

	/** Nas tomadas de cinema o manequim copia o jogador; em primeira pessoa ele some lá para baixo. */
	private void mirrorActor(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (camera == null) {
			actor.setPos(player.getX(), player.getY() - 500, player.getZ());
			actor.setOldPosAndRot();
			return;
		}
		boolean teleport = actor.getY() < player.getY() - 100;
		double moved = Math.sqrt(Mth.square(player.getX() - actor.getX()) + Mth.square(player.getZ() - actor.getZ()));
		actor.setPos(player.getX(), player.getY(), player.getZ());
		actor.setYRot(player.getYRot());
		actor.setXRot(player.getXRot());
		actor.setYHeadRot(player.getYHeadRot());
		actor.setYBodyRot(player.getVisualRotationYInDegrees());
		actor.setPose(player.isCrouching() ? Pose.CROUCHING : Pose.STANDING);
		actor.setShiftKeyDown(player.isShiftKeyDown());
		actor.setItemSlot(EquipmentSlot.MAINHAND, player.getMainHandItem().copy());
		if (teleport) {
			actor.setOldPosAndRot();
			actor.yBodyRotO = actor.yBodyRot;
			actor.yHeadRotO = actor.yHeadRot;
		} else {
			actor.walkAnimation.update((float) Math.min(moved * 4, 1), 0.4f, 1f);
		}
	}

	private void useCamera(ClientGameTestContext ctx, boolean detached) {
		ctx.runOnClient(mc -> {
			if (detached) {
				camera = EntityTypes.ARMOR_STAND.create(mc.level, EntitySpawnReason.COMMAND);
				camera.setInvisible(true);
				camera.setPos(mc.player.position());
				mc.setCameraEntity(camera);
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			} else {
				camera = null;
				mc.setCameraEntity(mc.player);
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			}
			mirrorActor(mc);
		});
	}

	private boolean cameraPlaced;

	/** Põe a câmera solta em {@code pos} olhando para {@code target}, com tremida opcional. */
	private void placeCamera(ClientGameTestContext ctx, Vec3 pos, Vec3 target, float shakeAmount) {
		Vec3 jitter = new Vec3(shake.nextGaussian(), shake.nextGaussian(), shake.nextGaussian()).scale(shakeAmount);
		ctx.runOnClient(mc -> {
			Vec3 at = pos.add(jitter);
			double eyeHeight = camera.getEyeHeight();
			boolean first = camera.tickCount == 0 && !cameraPlaced;
			camera.xo = first ? at.x : camera.getX();
			camera.yo = first ? at.y - eyeHeight : camera.getY();
			camera.zo = first ? at.z : camera.getZ();
			camera.setPos(at.x, at.y - eyeHeight, at.z);
			Vec3 d = target.subtract(at);
			float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90;
			float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
			float oldYaw = camera.getYRot();
			float oldPitch = camera.getXRot();
			yaw = oldYaw + Mth.wrapDegrees(yaw - oldYaw);
			camera.setYRot(yaw);
			camera.setXRot(pitch);
			camera.yRotO = first ? yaw : oldYaw;
			camera.xRotO = first ? pitch : oldPitch;
			camera.setYHeadRot(yaw);
			if (camera instanceof net.minecraft.world.entity.LivingEntity living) {
				living.yHeadRotO = camera.yRotO;
			}
			camera.tickCount++;
		});
	}

	private static void turnTowards(Minecraft mc, Vec3 target, float speed) {
		Vec3 d = target.subtract(mc.player.getEyePosition());
		float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90;
		float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
		mc.player.setYRot(mc.player.getYRot() + Mth.wrapDegrees(yaw - mc.player.getYRot()) * speed);
		mc.player.setXRot(Mth.lerp(speed, mc.player.getXRot(), pitch));
	}

	/** Gira na hora, sem interpolar (antes de começar uma tomada). */
	private static void setRot(Entity entity, float yaw, float pitch) {
		entity.setYRot(yaw);
		entity.setXRot(pitch);
		entity.yRotO = yaw;
		entity.xRotO = pitch;
		entity.setYHeadRot(yaw);
		if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
			living.yHeadRotO = yaw;
			living.yBodyRot = yaw;
			living.yBodyRotO = yaw;
		}
	}

	// ------------------------------------------------------------------ ajudantes

	/** Clareira plana e redonda, com a mata em volta de fundo e lanternas iluminando a borda. */
	private static void clearing(ServerLevel level, BlockPos center, int radius) {
		net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(7);
		for (int dx = -radius - 3; dx <= radius + 3; dx++) {
			for (int dz = -radius - 3; dz <= radius + 3; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > radius + random.nextInt(4)) {
					continue;
				}
				int x = center.getX() + dx;
				int z = center.getZ() + dz;
				for (int y = center.getY() - 4; y < center.getY() + 32; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					net.minecraft.world.level.block.state.BlockState state;
					if (y < center.getY() - 1) {
						state = Blocks.DIRT.defaultBlockState();
					} else if (y == center.getY() - 1) {
						state = Blocks.GRASS_BLOCK.defaultBlockState();
					} else if (y == center.getY() && random.nextFloat() < 0.0f) {
						state = Blocks.SHORT_GRASS.defaultBlockState();
					} else {
						state = Blocks.AIR.defaultBlockState();
					}
					level.setBlock(pos, state, 2);
				}
			}
		}
		for (int i = 0; i < 10; i++) {
			double a = i * Math.PI * 2 / 10;
			BlockPos post = new BlockPos(center.getX() + (int) Math.round(Math.cos(a) * (radius - 4)), center.getY(),
					center.getZ() + (int) Math.round(Math.sin(a) * (radius - 4)));
			level.setBlock(post, Blocks.OAK_FENCE.defaultBlockState(), 2);
			level.setBlock(post.above(), Blocks.LANTERN.defaultBlockState(), 2);
		}
	}

	/** Procura em volta um lugar seco e plano (sem água nem penhasco num raio de ~24 blocos). */
	private static BlockPos flattestDrySpot(ServerLevel level, BlockPos around) {
		BlockPos best = around;
		double bestScore = Double.MAX_VALUE;
		for (int cx = -4; cx <= 4; cx++) {
			for (int cz = -4; cz <= 4; cz++) {
				int x0 = around.getX() + cx * 40;
				int z0 = around.getZ() + cz * 40;
				int min = Integer.MAX_VALUE;
				int max = Integer.MIN_VALUE;
				int water = 0;
				for (int dx = -24; dx <= 24; dx += 6) {
					for (int dz = -24; dz <= 24; dz += 6) {
						BlockPos g = ground(level, x0 + dx, z0 + dz);
						min = Math.min(min, g.getY());
						max = Math.max(max, g.getY());
						if (!level.getFluidState(g.below()).isEmpty() || !level.getFluidState(g).isEmpty()) {
							water++;
						}
					}
				}
				double score = water * 20 + (max - min);
				if (score < bestScore) {
					bestScore = score;
					best = new BlockPos(x0, 0, z0);
				}
			}
		}
		return ground(level, best.getX(), best.getZ());
	}

	private static BlockPos ground(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	private static Listener listener(MinecraftServer server) {
		List<Listener> all = server.overworld().getEntitiesOfClass(Listener.class, player(server).getBoundingBox().inflate(256));
		if (all.isEmpty()) {
			throw new AssertionError("nenhum Ouvinte no mundo");
		}
		return all.getFirst();
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}
}
