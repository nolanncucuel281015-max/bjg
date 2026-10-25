package com.diamondaltar;

import com.diamondaltar.ability.Abilities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Version plugin : les infos « infusé » et « capacité choisie » sont stockées dans les données
 * personnalisées de l'objet (custom_data), et l'apparence vient du pack de ressources.
 */
public final class ModComponents {
	private static final String INFUSED = "diamondaltar_infused";
	private static final String SELECTED = "diamondaltar_selected";
	private static final String VERSION = "diamondaltar_version";
	public static final int CURRENT_VERSION = 2;

	public static final Set<Item> INFUSABLE = Set.of(
			Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
			Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL,
			Items.DIAMOND_HOE, Items.MACE, Items.DIAMOND_SPEAR);

	private ModComponents() {
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("diamondaltar", path);
	}

	private static CompoundTag tag(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
	}

	public static boolean isInfused(ItemStack stack) {
		return !stack.isEmpty() && tag(stack).getBooleanOr(INFUSED, false);
	}

	public static int getSelected(ItemStack stack) {
		return tag(stack).getIntOr(SELECTED, 0);
	}

	public static void setSelected(ItemStack stack, int index) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putInt(SELECTED, index));
		refreshLore(stack);
	}

	public static boolean canInfuse(ItemStack stack) {
		return INFUSABLE.contains(stack.getItem()) && !isInfused(stack);
	}

	public static void upgradeIfOld(ItemStack stack) {
		if (isInfused(stack) && tag(stack).getIntOr(VERSION, 0) != CURRENT_VERSION) {
			infuse(stack);
		}
	}

	private static ItemAttributeModifiers armorStats(EquipmentSlot slot) {
		double armor = switch (slot) {
			case HEAD -> 6;
			case CHEST -> 12;
			case LEGS -> 9;
			case FEET -> 6;
			default -> 0;
		};
		EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(slot);
		String name = slot.getSerializedName();
		return ItemAttributeModifiers.builder()
				.add(Attributes.ARMOR, new AttributeModifier(id("armor_" + name), armor, AttributeModifier.Operation.ADD_VALUE), group)
				.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id("toughness_" + name), 5, AttributeModifier.Operation.ADD_VALUE), group)
				.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(id("knockback_" + name), 0.2, AttributeModifier.Operation.ADD_VALUE), group)
				.add(Attributes.MAX_HEALTH, new AttributeModifier(id("health_" + name), 6, AttributeModifier.Operation.ADD_VALUE), group)
				.build();
	}

	public static void infuse(ItemStack stack) {
		Component baseName = stack.getItem().getName();
		CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> {
			t.putBoolean(INFUSED, true);
			t.putInt(VERSION, CURRENT_VERSION);
		});
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		stack.set(DataComponents.RARITY, Rarity.EPIC);
		if (!stack.is(Items.DIAMOND_SPEAR)) {
			String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			stack.set(DataComponents.ITEM_MODEL, id("infused_" + path));
		}
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable != null && equippable.assetId().isPresent()) {
			stack.set(DataComponents.EQUIPPABLE, Equippable.builder(equippable.slot())
					.setEquipSound(equippable.equipSound())
					.setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, id("infused_diamond")))
					.build());
			stack.set(DataComponents.ATTRIBUTE_MODIFIERS, armorStats(equippable.slot()));
			stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
		}
		stack.set(DataComponents.ITEM_NAME, Component.translatableWithFallback("diamondaltar.infused_name", "%s Infusé(e)", baseName)
				.withStyle(ChatFormatting.AQUA));
		refreshLore(stack);
	}

	/** Les infos des capacités sont écrites dans la description de l'objet (pas de mod côté client). */
	public static void refreshLore(ItemStack stack) {
		if (!isInfused(stack)) {
			return;
		}
		List<Component> lines = new ArrayList<>();
		List<Abilities.Ability> list = Abilities.of(stack.getItem());
		boolean chest = stack.is(Items.DIAMOND_CHESTPLATE);
		if (chest) {
			list = Abilities.ARMOR;
		}
		int selected = chest ? Abilities.armorIndex(stack) : Abilities.selectedIndex(stack);
		for (int i = 0; i < list.size(); i++) {
			String k = list.get(i).key();
			boolean on = i == selected;
			lines.add(Component.literal(on ? "▶ " : "  ")
					.append(Component.translatableWithFallback("diamondaltar.ability." + k + ".name", Lang.get("diamondaltar.ability." + k + ".name")))
					.withStyle(s -> s.withItalic(false).withColor(on ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY)));
		}
		if (!list.isEmpty()) {
			String hint = chest ? "Accroupi + clic droit : lancer · Accroupi + F : changer"
					: (Abilities.isLeftClick(stack) ? "Clic gauche : lancer · F : changer" : "Clic droit : lancer · F : changer");
			lines.add(Component.literal(hint).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.DARK_AQUA)));
		}
		stack.set(DataComponents.LORE, new ItemLore(lines));
	}
}
