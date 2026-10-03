package com.pedro.silenciototal.noise;

import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Sala silenciosa: um espaço fechado em que todas as paredes, o piso e o teto são de lã.
 * Portas, alçapões e portões fechados também vedam (senão não daria para entrar).
 *
 * <p>Faz um flood fill a partir do jogador pelos blocos "vazados" (ar, tochas, camas, baús,
 * tapetes...). Se o preenchimento encostar em qualquer bloco cheio que não seja lã, ou crescer
 * demais, o som vaza e não é uma sala silenciosa.
 */
public final class SilentRoom {
	/** Volume interno máximo, em blocos. Dá para uma sala de ~9x9x4. */
	public static final int MAX_VOLUME = 400;
	private static final int MAX_REACH = 12;

	private SilentRoom() {
	}

	public static boolean isInside(ServerLevel level, BlockPos start) {
		if (seals(level.getBlockState(start))) {
			start = start.above();
		}
		LongOpenHashSet visited = new LongOpenHashSet();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		visited.add(start.asLong());
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			if (visited.size() > MAX_VOLUME || pos.distManhattan(start) > MAX_REACH * 3
					|| Math.abs(pos.getX() - start.getX()) > MAX_REACH
					|| Math.abs(pos.getY() - start.getY()) > MAX_REACH
					|| Math.abs(pos.getZ() - start.getZ()) > MAX_REACH) {
				return false;
			}
			if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
				return false;
			}
			for (Direction direction : Direction.values()) {
				BlockPos next = pos.relative(direction);
				if (visited.contains(next.asLong())) {
					continue;
				}
				BlockState state = level.getBlockState(next);
				if (seals(state)) {
					continue;
				}
				if (state.isCollisionShapeFullBlock(level, next)) {
					// Bloco cheio que não é lã: pedra, madeira, vidro... o som atravessa.
					return false;
				}
				visited.add(next.asLong());
				queue.add(next);
			}
		}
		return true;
	}

	/** Blocos que vedam o som: lã e passagens fechadas. */
	public static boolean seals(BlockState state) {
		if (state.is(BlockTags.WOOL)) {
			return true;
		}
		if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock) {
			return state.hasProperty(BlockStateProperties.OPEN) && !state.getValue(BlockStateProperties.OPEN);
		}
		return false;
	}
}
