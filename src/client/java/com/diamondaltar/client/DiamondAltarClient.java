package com.diamondaltar.client;

import com.diamondaltar.ModComponents;
import com.diamondaltar.ability.Abilities;
import com.diamondaltar.block.ModBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.equipment.Equippable;

public class DiamondAltarClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BlockEntityRenderers.register(ModBlocks.INFUSION_ALTAR_ENTITY, InfusionAltarRenderer::new);

		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			if (!ModComponents.isInfused(stack)) {
				return;
			}
			String key;
			Abilities.Ability ability = Abilities.ABILITIES.get(stack.getItem());
			Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
			if (ability != null) {
				key = ability.key();
			} else if (equippable != null) {
				key = "armor_" + equippable.slot().getSerializedName();
			} else {
				return;
			}
			lines.add(Component.translatable("diamondaltar.ability." + key + ".name").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			lines.add(Component.translatable("diamondaltar.ability." + key + ".desc").withStyle(ChatFormatting.GRAY));
			if (ability != null) {
				lines.add(Component.translatable("diamondaltar.ability.hint", ability.cooldown() / 20).withStyle(ChatFormatting.DARK_AQUA));
			} else {
				lines.add(Component.translatable("diamondaltar.ability.armor_set").withStyle(ChatFormatting.DARK_AQUA));
			}
		});
	}
}
