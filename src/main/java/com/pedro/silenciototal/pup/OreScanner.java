package com.pedro.silenciototal.pup;

import com.pedro.silenciototal.SilencioTotal;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jspecify.annotations.Nullable;

/**
 * Varredura de minérios do filhote. Um único scanner atende todos os filhotes do servidor, numa
 * fila, com um orçamento fixo de tempo por tick. Regras (PLANO-FILHOTE, fase 5):
 * <ul>
 *   <li>Nunca carrega nem gera chunk: só olha os que já estão na memória ({@code getChunkNow}).</li>
 *   <li>Descarta seções inteiras pela paleta ({@code maybeHas}) sem ler bloco nenhum; seções só de
 *   ar também.</li>
 *   <li>Vai da seção mais perto para a mais longe e para assim que nenhuma seção restante pode ter um
 *   minério mais perto que o melhor já achado.</li>
 *   <li>Não guarda chunk de um tick para o outro, só coordenadas de seção.</li>
 * </ul>
 */
public final class OreScanner {
	/** Raio da busca, na horizontal (em qualquer altura). */
	public static final int RADIUS = 64;
	/** Tempo por tick, somando todas as buscas. Fica abaixo de 0,5 ms mesmo passando um pouco na última seção lida. */
	private static final long BUDGET_NANOS = 300_000;
	/** Seções conferidas só pela paleta por passo, antes de devolver a vez (o orçamento é conferido entre passos). */
	private static final int PALETTE_CHECKS_PER_STEP = 64;

	private static final Deque<Scan> QUEUE = new ArrayDeque<>();
	private static final boolean DEV = FabricLoader.getInstance().isDevelopmentEnvironment();
	private static long maxTickNanos;
	private static long tickCount;

	private OreScanner() {
	}

	/** Começa uma varredura ao redor de {@code origin}. O resultado sai em alguns ticks em {@link Scan#done()}. */
	public static Scan start(ServerLevel level, BlockPos origin, Predicate<BlockState> target) {
		Scan scan = new Scan(level, origin.immutable(), target);
		QUEUE.add(scan);
		return scan;
	}

	public static void tick() {
		if (QUEUE.isEmpty()) {
			return;
		}
		tickCount++;
		long start = System.nanoTime();
		long deadline = start + BUDGET_NANOS;
		// Uma seção de cada busca por vez, em rodízio, até acabar o tempo.
		while (!QUEUE.isEmpty() && System.nanoTime() < deadline) {
			Scan scan = QUEUE.poll();
			if (scan.cancelled) {
				continue;
			}
			if (scan.lastTick != tickCount) {
				scan.lastTick = tickCount;
				scan.ticks++;
			}
			long before = System.nanoTime();
			boolean finished = scan.step();
			scan.nanos += System.nanoTime() - before;
			if (!finished) {
				QUEUE.add(scan);
			}
		}
		maxTickNanos = Math.max(maxTickNanos, System.nanoTime() - start);
	}

	/** O tick mais caro da varredura desde o último {@link #resetStats()} (para os testes e o log). */
	public static long maxTickNanos() {
		return maxTickNanos;
	}

	public static void resetStats() {
		maxTickNanos = 0;
	}

	public static void clear() {
		QUEUE.clear();
	}

	/** Buscas na fila (para os testes). */
	public static int pending() {
		return QUEUE.size();
	}

	public static final class Scan {
		private final ServerLevel level;
		private final BlockPos origin;
		private final Predicate<BlockState> target;
		/** Seções em volta, ordenadas pela menor distância possível até a origem. */
		private long[] sections;
		private double[] minDistances;
		private int next;
		private @Nullable BlockPos found;
		private double bestDistance = Double.MAX_VALUE;
		private boolean done;
		private boolean cancelled;
		/** Seções lidas bloco a bloco e tempo de servidor gasto (para o log de desenvolvimento). */
		private int read;
		private long nanos;
		private int ticks;
		private long lastTick = -1;

		private Scan(ServerLevel level, BlockPos origin, Predicate<BlockState> target) {
			this.level = level;
			this.origin = origin;
			this.target = target;
		}

		public boolean done() {
			return done;
		}

		/** O minério mais perto (em distância 3D), ou null se não havia nenhum. Só vale depois de {@link #done()}. */
		public @Nullable BlockPos found() {
			return found;
		}

		/** Seções lidas bloco a bloco, de quantas no raio. */
		public int sectionsRead() {
			return read;
		}

		public int sectionsTotal() {
			return sections == null ? 0 : sections.length;
		}

		/** Tempo de servidor gasto nesta busca, e em quantos ticks. */
		public long nanos() {
			return nanos;
		}

		public int ticks() {
			return ticks;
		}

		/** O filhote sumiu ou desistiu: a fila descarta esta busca. */
		public void cancel() {
			cancelled = true;
		}

		/** Lê uma seção, ou confere até {@link #PALETTE_CHECKS_PER_STEP} pela paleta, ou monta a lista. Devolve true quando terminou. */
		private boolean step() {
			if (sections == null) {
				prepare();
				return finishIfEmpty();
			}
			int checked = 0;
			while (next < sections.length) {
				if (++checked > PALETTE_CHECKS_PER_STEP) {
					// Muitas seções descartadas pela paleta seguidas: devolve a vez para o orçamento valer.
					return false;
				}
				int i = next++;
				if (minDistances[i] > bestDistance) {
					// Tudo que sobrou está mais longe que o melhor achado.
					next = sections.length;
					break;
				}
				long packed = sections[i];
				LevelChunk chunk = level.getChunkSource().getChunkNow(SectionPos.x(packed), SectionPos.z(packed));
				if (chunk == null) {
					continue;
				}
				int sectionY = SectionPos.y(packed);
				LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
				if (section.hasOnlyAir() || !section.maybeHas(target)) {
					continue;
				}
				readSection(section, SectionPos.x(packed), sectionY, SectionPos.z(packed));
				return false;
			}
			done = true;
			if (DEV) {
				SilencioTotal.LOGGER.info("[filhote] varredura em {}: {} de {} seções lidas por inteiro, {} ms de servidor em {} ticks, achou {}",
						origin.toShortString(), read, sections.length, String.format("%.2f", nanos / 1e6),
						ticks, found == null ? "nada" : found.toShortString());
			}
			return true;
		}

		private boolean finishIfEmpty() {
			if (sections.length == 0) {
				done = true;
				return true;
			}
			return false;
		}

		private void readSection(LevelChunkSection section, int sx, int sy, int sz) {
			read++;
			int baseX = SectionPos.sectionToBlockCoord(sx);
			int baseY = SectionPos.sectionToBlockCoord(sy);
			int baseZ = SectionPos.sectionToBlockCoord(sz);
			// Blocos iguais costumam vir em sequência (pedra, pedra, pedra...): só testa quando muda.
			BlockState last = null;
			boolean lastMatches = false;
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						BlockState state = section.getBlockState(x, y, z);
						if (state != last) {
							last = state;
							lastMatches = target.test(state);
						}
						if (!lastMatches) {
							continue;
						}
						int bx = baseX + x;
						int by = baseY + y;
						int bz = baseZ + z;
						double dx = bx + 0.5 - (origin.getX() + 0.5);
						double dz = bz + 0.5 - (origin.getZ() + 0.5);
						if (dx * dx + dz * dz > (double) RADIUS * RADIUS) {
							continue;
						}
						double dy = by + 0.5 - (origin.getY() + 0.5);
						double distance = dx * dx + dy * dy + dz * dz;
						if (distance < bestDistance) {
							bestDistance = distance;
							found = new BlockPos(bx, by, bz);
						}
					}
				}
			}
		}

		/**
		 * Todas as seções no raio (horizontal), todas as alturas, da mais perto para a mais longe.
		 * Sem objetos: cada seção vira um long (distância em cima, posição relativa embaixo) e o
		 * array é ordenado direto.
		 */
		private void prepare() {
			int minSection = level.getMinSectionY();
			int height = level.getMaxSectionY() - minSection + 1;
			int cx = SectionPos.blockToSectionCoord(origin.getX());
			int cz = SectionPos.blockToSectionCoord(origin.getZ());
			int r = SectionPos.blockToSectionCoord(RADIUS) + 1;
			int side = r * 2 + 1;
			double radiusSq = (double) RADIUS * RADIUS;
			long[] keys = new long[side * side * height];
			int count = 0;
			for (int x = -r; x <= r; x++) {
				double hx = axisGap(origin.getX(), cx + x);
				for (int z = -r; z <= r; z++) {
					double hz = axisGap(origin.getZ(), cz + z);
					double horizontal = hx * hx + hz * hz;
					if (horizontal > radiusSq) {
						continue;
					}
					for (int y = 0; y < height; y++) {
						double hy = axisGap(origin.getY(), minSection + y);
						long distance = (long) Math.ceil(horizontal + hy * hy);
						int local = ((x + r) * side + (z + r)) * height + y;
						keys[count++] = distance << 24 | local;
					}
				}
			}
			Arrays.sort(keys, 0, count);
			sections = new long[count];
			minDistances = new double[count];
			for (int i = 0; i < count; i++) {
				int local = (int) (keys[i] & 0xFFFFFF);
				int y = local % height;
				int xz = local / height;
				sections[i] = SectionPos.asLong(cx + xz / side - r, minSection + y, cz + xz % side - r);
				// Arredondado para cima: só serve para descartar, e descartar a mais nunca acontece.
				minDistances[i] = (keys[i] >>> 24) - 1;
			}
		}

		/** Menor distância, num eixo, do centro do bloco de origem até a seção (0 se está dentro dela). */
		private static double axisGap(int block, int section) {
			double center = block + 0.5;
			int min = SectionPos.sectionToBlockCoord(section);
			int max = min + 16;
			return center < min ? min - center : center > max ? center - max : 0;
		}
	}
}
