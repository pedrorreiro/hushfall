package com.pedro.silenciototal.test;

import com.pedro.silenciototal.SilencioTotal;
import com.pedro.silenciototal.pup.OreScanner;
import com.pedro.silenciototal.pup.PupOres;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Desempenho da varredura do filhote num mundo normal (terreno, cavernas e minérios de verdade),
 * o critério de aceite da fase 5 do PLANO-FILHOTE: média abaixo de 0,5 ms por tick e nenhum tick
 * acima de 2 ms (sem carregar chunk: isso o teste principal confere, no mundo plano). Mede carvão (o pior caso: está em quase toda seção), ferro,
 * diamante, esmeralda (quase sempre "não existe", percorre tudo), quartzo (nunca existe no
 * Overworld) e várias buscas ao mesmo tempo. Os números vão para o log.
 */
public class PupScanBenchmark implements FabricClientGameTest {
	private static final long MAX_TICK = 2_000_000;
	private static final long MAX_AVERAGE = 500_000;
	private static final int RUNS = 5;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (DemoReel.OUT != null) {
			return;
		}
		ctx.runOnClient(mc -> mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0));
		try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(s -> s.setSeed("silencio-filhote"))
				.create()) {
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule spawn_monsters false");
			// Espera os chunks em volta carregarem (a varredura só olha os carregados) e o servidor sossegar.
			ctx.waitTicks(300);
			List<List<Item>> cases = List.of(List.of(Items.COAL), List.of(Items.RAW_IRON), List.of(Items.DIAMOND),
					List.of(Items.EMERALD), List.of(Items.QUARTZ),
					// Quatro filhotes buscando ao mesmo tempo, em lugares diferentes.
					List.of(Items.DIAMOND, Items.COAL, Items.EMERALD, Items.RAW_GOLD));
			// Aquecimento: a primeira vez de tudo na JVM é mais lenta (código ainda não otimizado).
			for (List<Item> items : cases) {
				measure(ctx, server, items);
			}
			// Cada caso 5 vezes; vale a mediana, para uma pausa do coletor de lixo não decidir o teste.
			for (List<Item> items : cases) {
				String name = items.size() == 1 ? items.getFirst().toString() : items.size() + " ao mesmo tempo";
				List<long[]> runs = new ArrayList<>();
				for (int i = 0; i < RUNS; i++) {
					runs.add(measure(ctx, server, items));
				}
				long maxTick = median(runs, 0);
				long average = median(runs, 1);
				long worst = runs.stream().mapToLong(run -> run[0]).max().orElse(0);
				SilencioTotal.LOGGER.info("[desempenho] {}: {} de {} seções lidas; média {} ms por tick, tick mais caro {} ms (mediana de {}; pior {} ms)",
						name, runs.getLast()[2], runs.getLast()[3], ms(average), ms(maxTick), RUNS, ms(worst));
				check(maxTick < MAX_TICK, name + ": nenhum tick deveria passar de 2 ms; foi " + ms(maxTick) + " ms");
				check(average < MAX_AVERAGE, name + ": a média deveria ficar abaixo de 0,5 ms por tick; foi " + ms(average) + " ms");
			}
		}
	}

	/** Uma varredura (ou várias juntas): {tick mais caro, média por tick, seções lidas, seções no raio}. */
	private static long[] measure(ClientGameTestContext ctx, TestServerContext server, List<Item> items) {
		List<OreScanner.Scan> scans = new ArrayList<>();
		server.runOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
			ServerLevel level = player.level();
			OreScanner.resetStats();
			for (int i = 0; i < items.size(); i++) {
				BlockPos origin = player.blockPosition().offset(i * 12, 0, -i * 12);
				scans.add(OreScanner.start(level, origin, PupOres.target(new ItemStack(items.get(i)))));
			}
		});
		int waited = 0;
		while (server.computeOnServer(s -> OreScanner.pending()) > 0 && waited < 400) {
			ctx.waitTick();
			waited++;
		}
		return server.computeOnServer(s -> {
			long nanos = 0;
			int ticks = 0;
			int read = 0;
			int total = 0;
			for (OreScanner.Scan scan : scans) {
				check(scan.done(), "a varredura deveria terminar");
				nanos += scan.nanos();
				ticks = Math.max(ticks, scan.ticks());
				read += scan.sectionsRead();
				total += scan.sectionsTotal();
			}
			return new long[]{OreScanner.maxTickNanos(), nanos / Math.max(1, ticks), read, total};
		});
	}

	private static long median(List<long[]> runs, int index) {
		long[] values = runs.stream().mapToLong(run -> run[index]).sorted().toArray();
		return values[values.length / 2];
	}

	private static String ms(long nanos) {
		return String.format("%.3f", nanos / 1e6);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
