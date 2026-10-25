package com.diamondaltar;

import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;

import java.util.Set;

public final class ModComponents {
	/** Marque un objet comme "infusé" par l'autel : il gagne sa capacité spéciale. */
	public static final ComponentType<Boolean> INFUSED = Registry.register(
			Registries.DATA_COMPONENT_TYPE,
			DiamondAltar.id("infused"),
			ComponentType.<Boolean>builder().codec(Codec.BOOL).packetCodec(PacketCodecs.BOOL).build());

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
		stack.set(INFUSED, true);
		stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
		stack.set(DataComponentTypes.RARITY, Rarity.EPIC);
		stack.set(DataComponentTypes.ITEM_NAME, Text.translatable("diamondaltar.infused_name",
				Text.translatable(stack.getItem().getTranslationKey())).formatted(Formatting.AQUA));
	}
}
