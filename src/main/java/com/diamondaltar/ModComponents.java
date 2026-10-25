package com.diamondaltar;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

import java.util.Set;

public final class ModComponents {
	/** Marque un objet comme "infusé" par l'autel : il gagne sa capacité spéciale. */
	public static final DataComponentType<Boolean> INFUSED = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			DiamondAltar.id("infused"),
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());

	/** Index de la capacité choisie avec la touche K. */
	public static final DataComponentType<Integer> SELECTED_ABILITY = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			DiamondAltar.id("selected_ability"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	/** Apparence de l'armure infusée quand on la porte : assets/diamondaltar/equipment/infused_diamond.json */
	public static final ResourceKey<EquipmentAsset> INFUSED_ARMOR = ResourceKey.create(EquipmentAssets.ROOT_ID, DiamondAltar.id("infused_diamond"));

	public static final Set<Item> INFUSABLE = Set.of(
			Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
			Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL,
			Items.DIAMOND_HOE, Items.MACE);

	private ModComponents() {
	}

	public static void register() {
	}

	public static boolean isInfused(ItemStack stack) {
		return !stack.isEmpty() && Boolean.TRUE.equals(stack.get(INFUSED));
	}

	public static boolean canInfuse(ItemStack stack) {
		return INFUSABLE.contains(stack.getItem()) && !isInfused(stack);
	}

	public static void infuse(ItemStack stack) {
		Component baseName = stack.getItem().getName();
		stack.set(INFUSED, true);
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		stack.set(DataComponents.RARITY, Rarity.EPIC);
		// Texture animée spéciale : assets/diamondaltar/items/infused_<objet>.json
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		stack.set(DataComponents.ITEM_MODEL, DiamondAltar.id("infused_" + path));
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable != null && equippable.assetId().isPresent()) {
			stack.set(DataComponents.EQUIPPABLE, Equippable.builder(equippable.slot())
					.setEquipSound(equippable.equipSound())
					.setAsset(INFUSED_ARMOR)
					.build());
		}
		stack.set(DataComponents.ITEM_NAME, Component.translatable("diamondaltar.infused_name", baseName)
				.withStyle(ChatFormatting.AQUA));
	}
}
