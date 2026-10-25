package com.diamondaltar.block;

import com.diamondaltar.DiamondAltar;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	private static final ResourceKey<Block> ALTAR_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, DiamondAltar.id("infusion_altar"));
	private static final ResourceKey<Item> ALTAR_ITEM_KEY = ResourceKey.create(Registries.ITEM, DiamondAltar.id("infusion_altar"));

	public static final InfusionAltarBlock INFUSION_ALTAR = Registry.register(BuiltInRegistries.BLOCK, ALTAR_BLOCK_KEY,
			new InfusionAltarBlock(BlockBehaviour.Properties.of()
					.setId(ALTAR_BLOCK_KEY)
					.mapColor(MapColor.SNOW)
					.strength(5.0f, 1200.0f)
					.requiresCorrectToolForDrops()
					.sound(SoundType.METAL)
					.lightLevel(state -> 7)
					.noOcclusion()));

	public static final Item INFUSION_ALTAR_ITEM = Registry.register(BuiltInRegistries.ITEM, ALTAR_ITEM_KEY,
			new BlockItem(INFUSION_ALTAR, new Item.Properties().setId(ALTAR_ITEM_KEY).useBlockDescriptionPrefix().rarity(Rarity.RARE)));

	public static final BlockEntityType<InfusionAltarBlockEntity> INFUSION_ALTAR_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			DiamondAltar.id("infusion_altar"),
			FabricBlockEntityTypeBuilder.create(InfusionAltarBlockEntity::new, INFUSION_ALTAR).build());

	private ModBlocks() {
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> entries.accept(INFUSION_ALTAR_ITEM));
	}
}
