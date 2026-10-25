package com.diamondaltar;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
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
		stack.set(DataComponents.ITEM_NAME, Component.translatable("diamondaltar.infused_name", baseName)
				.withStyle(ChatFormatting.AQUA));
	}
}
