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
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
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

	/** Version de l'infusion : les vieux objets infusés sont mis à jour automatiquement. */
	public static final DataComponentType<Integer> INFUSE_VERSION = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			DiamondAltar.id("infuse_version"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
	public static final int CURRENT_VERSION = 2;

	public static final Set<Item> INFUSABLE = Set.of(
			Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
			Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL,
			Items.DIAMOND_HOE, Items.MACE, Items.DIAMOND_SPEAR);

	private ModComponents() {
	}

	public static void register() {
	}

	/** L'équipement astral a les mêmes capacités que sa version en diamant. */
	public static Item abilityItem(Item item) {
		if (ASTRAL_TO_DIAMOND == null) {
			ASTRAL_TO_DIAMOND = java.util.Map.of(
					com.diamondaltar.astral.AstralContent.ASTRAL_SWORD, Items.DIAMOND_SWORD,
					com.diamondaltar.astral.AstralContent.ASTRAL_PICKAXE, Items.DIAMOND_PICKAXE,
					com.diamondaltar.astral.AstralContent.ASTRAL_AXE, Items.DIAMOND_AXE,
					com.diamondaltar.astral.AstralContent.ASTRAL_SHOVEL, Items.DIAMOND_SHOVEL,
					com.diamondaltar.astral.AstralContent.ASTRAL_HOE, Items.DIAMOND_HOE,
					com.diamondaltar.astral.AstralContent.ASTRAL_HELMET, Items.DIAMOND_HELMET,
					com.diamondaltar.astral.AstralContent.ASTRAL_CHESTPLATE, Items.DIAMOND_CHESTPLATE,
					com.diamondaltar.astral.AstralContent.ASTRAL_LEGGINGS, Items.DIAMOND_LEGGINGS,
					com.diamondaltar.astral.AstralContent.ASTRAL_BOOTS, Items.DIAMOND_BOOTS);
		}
		return ASTRAL_TO_DIAMOND.getOrDefault(item, item);
	}

	private static java.util.Map<Item, Item> ASTRAL_TO_DIAMOND;

	public static boolean isAstral(ItemStack stack) {
		return abilityItem(stack.getItem()) != stack.getItem();
	}

	public static boolean isInfused(ItemStack stack) {
		return !stack.isEmpty() && Boolean.TRUE.equals(stack.get(INFUSED));
	}

	public static int getSelected(ItemStack stack) {
		Integer i = stack.get(SELECTED_ABILITY);
		return i == null ? 0 : i;
	}

	public static void setSelected(ItemStack stack, int index) {
		stack.set(SELECTED_ABILITY, index);
	}

	public static boolean canInfuse(ItemStack stack) {
		return (INFUSABLE.contains(stack.getItem()) || isAstral(stack)) && !isInfused(stack);
	}

	/** Met à jour un objet infusé avec une ancienne version du mod (textures, armure renforcée…). */
	public static void upgradeIfOld(ItemStack stack) {
		if (isInfused(stack) && !Integer.valueOf(CURRENT_VERSION).equals(stack.get(INFUSE_VERSION))) {
			infuse(stack);
		} else if (isInfused(stack) && isAstral(stack)
				&& !java.util.Objects.equals(stack.get(DataComponents.ITEM_MODEL), stack.getItem().components().get(DataComponents.ITEM_MODEL))) {
			// Objet infusé puis amélioré à la forge : il prend l'apparence et les stats astrales.
			infuse(stack);
		}
	}

	/** Armure infusée : bien plus solide que le diamant (et même que la netherite). */
	private static ItemAttributeModifiers armorStats(EquipmentSlot slot, boolean astral) {
		double armor = switch (slot) {
			case HEAD -> astral ? 8 : 6;
			case CHEST -> astral ? 15 : 12;
			case LEGS -> astral ? 12 : 9;
			case FEET -> astral ? 8 : 6;
			default -> 0;
		};
		double toughness = astral ? 8 : 5;
		double health = astral ? 10 : 6;
		EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(slot);
		String name = slot.getSerializedName();
		return ItemAttributeModifiers.builder()
				.add(Attributes.ARMOR, new AttributeModifier(DiamondAltar.id("armor_" + name), armor, AttributeModifier.Operation.ADD_VALUE), group)
				.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(DiamondAltar.id("toughness_" + name), toughness, AttributeModifier.Operation.ADD_VALUE), group)
				.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(DiamondAltar.id("knockback_" + name), 0.2, AttributeModifier.Operation.ADD_VALUE), group)
				.add(Attributes.MAX_HEALTH, new AttributeModifier(DiamondAltar.id("health_" + name), health, AttributeModifier.Operation.ADD_VALUE), group)
				.build();
	}

	public static void infuse(ItemStack stack) {
		stack.set(INFUSE_VERSION, CURRENT_VERSION);
		Component baseName = stack.getItem().getName();
		stack.set(INFUSED, true);
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		stack.set(DataComponents.RARITY, Rarity.EPIC);
		// Texture animée spéciale : assets/diamondaltar/items/infused_<objet>.json
		boolean astral = isAstral(stack);
		if (astral) {
			// L'équipement astral garde sa propre texture et sa propre armure portée.
			stack.set(DataComponents.ITEM_MODEL, stack.getItem().components().get(DataComponents.ITEM_MODEL));
			stack.set(DataComponents.EQUIPPABLE, stack.getItem().components().get(DataComponents.EQUIPPABLE));
		}
		// (la lance garde son modèle 3D d'origine, avec la brillance)
		if (!stack.is(Items.DIAMOND_SPEAR) && !astral) {
			String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			stack.set(DataComponents.ITEM_MODEL, DiamondAltar.id("infused_" + path));
		}
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable != null && equippable.assetId().isPresent()) {
			if (!astral) {
				stack.set(DataComponents.EQUIPPABLE, Equippable.builder(equippable.slot())
						.setEquipSound(equippable.equipSound())
						.setAsset(INFUSED_ARMOR)
						.build());
			}
			stack.set(DataComponents.ATTRIBUTE_MODIFIERS, armorStats(equippable.slot(), astral));
			stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
		}
		stack.set(DataComponents.ITEM_NAME, Component.translatable("diamondaltar.infused_name", baseName)
				.withStyle(ChatFormatting.AQUA));
	}
}
