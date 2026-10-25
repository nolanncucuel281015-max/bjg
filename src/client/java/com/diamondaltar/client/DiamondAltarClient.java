package com.diamondaltar.client;

import com.diamondaltar.ModComponents;
import com.diamondaltar.ability.Abilities;
import com.diamondaltar.ability.SlashPayload;
import com.diamondaltar.block.ModBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.equipment.Equippable;

public class DiamondAltarClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BlockEntityRenderers.register(ModBlocks.INFUSION_ALTAR_ENTITY, InfusionAltarRenderer::new);

		// Clic gauche avec l'épée infusée : on demande au serveur de lancer la Tranche de la Volonté.
		ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
			Abilities.Ability ability = Abilities.ABILITIES.get(player.getMainHandItem().getItem());
			if (clickCount > 0 && ability != null && ability.leftClick()
					&& ModComponents.isInfused(player.getMainHandItem()) && !player.isSpectator()) {
				ClientPlayNetworking.send(new SlashPayload());
			}
			return false;
		});

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
			if (ability != null && ability.leftClick()) {
				lines.add(Component.translatable("diamondaltar.ability.hint_left").withStyle(ChatFormatting.DARK_AQUA));
			} else if (ability != null) {
				lines.add(Component.translatable("diamondaltar.ability.hint", ability.cooldown() / 20).withStyle(ChatFormatting.DARK_AQUA));
			} else {
				lines.add(Component.translatable("diamondaltar.ability.armor_set").withStyle(ChatFormatting.DARK_AQUA));
			}
		});
	}
}
