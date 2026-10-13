package com.pedro.silenciototal.pup;

import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * O que o filhote procura: você entrega o que o minério dropa e ele procura o minério.
 * As variantes de deepslate (e o ouro do Nether) vêm junto pelas tags do jogo.
 */
public final class PupOres {
	private static final Map<Item, Predicate<BlockState>> ORES = Map.of(
			Items.COAL, tag(BlockItemTags.COAL_ORES.block()),
			Items.RAW_IRON, tag(BlockItemTags.IRON_ORES.block()),
			Items.RAW_COPPER, tag(BlockItemTags.COPPER_ORES.block()),
			Items.RAW_GOLD, tag(BlockItemTags.GOLD_ORES.block()),
			Items.REDSTONE, tag(BlockItemTags.REDSTONE_ORES.block()),
			Items.LAPIS_LAZULI, tag(BlockItemTags.LAPIS_ORES.block()),
			Items.DIAMOND, tag(BlockItemTags.DIAMOND_ORES.block()),
			Items.EMERALD, tag(BlockItemTags.EMERALD_ORES.block()),
			Items.QUARTZ, block(Blocks.NETHER_QUARTZ_ORE),
			Items.NETHERITE_SCRAP, block(Blocks.ANCIENT_DEBRIS));

	private PupOres() {
	}

	public static boolean accepts(ItemStack stack) {
		return ORES.containsKey(stack.getItem());
	}

	/** Os blocos que o filhote procura quando recebe {@code stack}, ou null se ele não aceita o item. */
	public static @Nullable Predicate<BlockState> target(ItemStack stack) {
		return ORES.get(stack.getItem());
	}

	private static Predicate<BlockState> tag(TagKey<Block> tag) {
		return state -> state.is(tag);
	}

	private static Predicate<BlockState> block(Block block) {
		return state -> state.is(block);
	}
}
