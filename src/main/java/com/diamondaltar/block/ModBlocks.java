package com.diamondaltar.block;

import com.diamondaltar.DiamondAltar;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Rarity;

public final class ModBlocks {
	public static final InfusionAltarBlock INFUSION_ALTAR = Registry.register(Registries.BLOCK,
			DiamondAltar.id("infusion_altar"),
			new InfusionAltarBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.WHITE)
					.strength(5.0f, 1200.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)
					.luminance(state -> 7)
					.nonOpaque()));

	public static final Item INFUSION_ALTAR_ITEM = Registry.register(Registries.ITEM,
			DiamondAltar.id("infusion_altar"),
			new BlockItem(INFUSION_ALTAR, new Item.Settings().rarity(Rarity.RARE)));

	public static final BlockEntityType<InfusionAltarBlockEntity> INFUSION_ALTAR_ENTITY = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			DiamondAltar.id("infusion_altar"),
			BlockEntityType.Builder.create(InfusionAltarBlockEntity::new, INFUSION_ALTAR).build(null));

	private ModBlocks() {
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(INFUSION_ALTAR_ITEM));
	}
}
