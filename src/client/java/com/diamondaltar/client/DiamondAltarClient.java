package com.diamondaltar.client;

import com.diamondaltar.DiamondAltar;
import com.diamondaltar.ModComponents;
import com.diamondaltar.ability.Abilities;
import com.diamondaltar.ability.ArmorActionPayload;
import com.diamondaltar.ability.SlashPayload;
import com.diamondaltar.ability.SwitchAbilityPayload;
import com.diamondaltar.block.ModBlocks;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.List;

public class DiamondAltarClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(DiamondAltar.id("abilities"));

	private static final KeyMapping SWITCH_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.diamondaltar.switch_ability", InputConstants.Type.KEYSYM, InputConstants.KEY_K, CATEGORY));

	private static final KeyMapping ARMOR_SWITCH_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.diamondaltar.switch_armor", InputConstants.Type.KEYSYM, InputConstants.KEY_J, CATEGORY));

	private static final KeyMapping ARMOR_USE_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.diamondaltar.use_armor", InputConstants.Type.KEYSYM, InputConstants.KEY_H, CATEGORY));

	@Override
	public void onInitializeClient() {
		BlockEntityRenderers.register(ModBlocks.INFUSION_ALTAR_ENTITY, InfusionAltarRenderer::new);

		// Touche K : changer de capacité.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (SWITCH_KEY.consumeClick()) {
				if (client.player != null && ModComponents.isInfused(client.player.getMainHandItem())
						&& !Abilities.of(client.player.getMainHandItem().getItem()).isEmpty()) {
					ClientPlayNetworking.send(new SwitchAbilityPayload());
				}
			}
			// Touche J : changer la capacité de l'armure. Touche H : la lancer.
			while (ARMOR_SWITCH_KEY.consumeClick()) {
				if (client.player != null) {
					ClientPlayNetworking.send(new ArmorActionPayload(false));
				}
			}
			while (ARMOR_USE_KEY.consumeClick()) {
				if (client.player != null) {
					ClientPlayNetworking.send(new ArmorActionPayload(true));
				}
			}
		});

		// Clic gauche avec l'épée infusée : on demande au serveur de lancer la capacité choisie.
		ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
			ItemStack stack = player.getMainHandItem();
			if (clickCount > 0 && Abilities.isLeftClick(stack) && ModComponents.isInfused(stack) && !player.isSpectator()) {
				ClientPlayNetworking.send(new SlashPayload());
			}
			return false;
		});

		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			if (!ModComponents.isInfused(stack)) {
				return;
			}
			List<Abilities.Ability> abilities = Abilities.of(stack.getItem());
			if (!abilities.isEmpty()) {
				int selected = Abilities.selectedIndex(stack);
				for (int i = 0; i < abilities.size(); i++) {
					Abilities.Ability ability = abilities.get(i);
					boolean on = i == selected;
					lines.add(Component.literal(on ? "▶ " : "  ")
							.append(Component.translatable("diamondaltar.ability." + ability.key() + ".name"))
							.withStyle(on ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
					if (on) {
						lines.add(Component.literal("    ")
								.append(Component.translatable("diamondaltar.ability." + ability.key() + ".desc"))
								.withStyle(ChatFormatting.GRAY));
					}
				}
				Abilities.Ability current = abilities.get(selected);
				String hint = Abilities.isLeftClick(stack) ? "diamondaltar.ability.hint_left" : "diamondaltar.ability.hint";
				String cd = current.cooldown() == 0 ? "0" : String.format("%.1f", current.cooldown() / 20.0);
				lines.add(Component.translatable(hint, cd).withStyle(ChatFormatting.DARK_AQUA));
				lines.add(Component.translatable("diamondaltar.ability.switch_hint", SWITCH_KEY.getTranslatedKeyMessage())
						.withStyle(ChatFormatting.DARK_AQUA));
				return;
			}
			Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
			if (equippable != null) {
				String key = "armor_" + equippable.slot().getSerializedName();
				lines.add(Component.translatable("diamondaltar.ability." + key + ".name").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
				lines.add(Component.translatable("diamondaltar.ability." + key + ".desc").withStyle(ChatFormatting.GRAY));
				lines.add(Component.translatable("diamondaltar.ability.armor_set").withStyle(ChatFormatting.DARK_AQUA));
				if (equippable.slot() == EquipmentSlot.CHEST) {
					int selected = Abilities.armorIndex(stack);
					for (int i = 0; i < Abilities.ARMOR.size(); i++) {
						boolean on = i == selected;
						String k = Abilities.ARMOR.get(i).key();
						lines.add(Component.literal(on ? "▶ " : "  ")
								.append(Component.translatable("diamondaltar.ability." + k + ".name"))
								.withStyle(on ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
						if (on) {
							lines.add(Component.literal("    ").append(Component.translatable("diamondaltar.ability." + k + ".desc"))
									.withStyle(ChatFormatting.GRAY));
						}
					}
					lines.add(Component.translatable("diamondaltar.ability.armor_keys",
							ARMOR_USE_KEY.getTranslatedKeyMessage(), ARMOR_SWITCH_KEY.getTranslatedKeyMessage())
							.withStyle(ChatFormatting.DARK_AQUA));
				}
			}
		});
	}
}
