package com.pedro.silenciototal.registry;

import com.pedro.silenciototal.SilencioTotal;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

public final class ModItems {
	public static final ResourceKey<EquipmentAsset> FELT_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, SilencioTotal.id("felt"));

	/** Feltro: lã prensada. Protege pouco, mas abafa os passos. */
	public static final ArmorMaterial FELT = new ArmorMaterial(5,
			Map.of(ArmorType.BOOTS, 1, ArmorType.LEGGINGS, 2, ArmorType.CHESTPLATE, 3, ArmorType.HELMET, 1, ArmorType.BODY, 3),
			15, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0f, 0.0f, ItemTags.WOOL, FELT_ASSET);

	/** Botas de feltro: -60% no ruído dos passos. */
	public static final Item FELT_BOOTS = register("felt_boots", Item::new,
			new Item.Properties().humanoidArmor(FELT, ArmorType.BOOTS));

	/**
	 * Adaga Silenciosa: lâmina de ferro com cabo enrolado em lã. Golpear com ela não faz barulho
	 * (levar golpe ainda faz). Um pouco mais fraca que a espada de ferro, mas mais rápida.
	 */
	public static final Item SILENT_DAGGER = register("silent_dagger", Item::new,
			new Item.Properties().sword(ToolMaterial.IRON, 2.0f, -2.0f));

	/** Troféu de quem mata o Ouvinte. Segurando, você ouve um batimento que acelera quando ele se aproxima. */
	public static final Item LISTENER_EAR = register("listener_ear", Item::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());

	public static final Item LISTENER_SPAWN_EGG = register("listener_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.LISTENER));

	private ModItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SilencioTotal.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.accept(FELT_BOOTS);
			output.accept(SILENT_DAGGER);
			output.accept(LISTENER_EAR);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.accept(LISTENER_SPAWN_EGG));
	}
}
