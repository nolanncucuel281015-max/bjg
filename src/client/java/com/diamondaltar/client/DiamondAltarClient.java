package com.diamondaltar.client;

import com.diamondaltar.ModComponents;
import com.diamondaltar.ability.Abilities;
import com.diamondaltar.block.ModBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class DiamondAltarClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BlockEntityRendererFactories.register(ModBlocks.INFUSION_ALTAR_ENTITY, InfusionAltarRenderer::new);
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.INFUSION_ALTAR, RenderLayer.getCutout());

		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			if (!ModComponents.isInfused(stack)) {
				return;
			}
			String key;
			Abilities.Ability ability = Abilities.ABILITIES.get(stack.getItem());
			if (ability != null) {
				key = ability.key();
			} else if (stack.getItem() instanceof ArmorItem armor) {
				EquipmentSlot slot = armor.getSlotType();
				key = "armor_" + slot.getName();
			} else {
				return;
			}
			lines.add(Text.translatable("diamondaltar.ability." + key + ".name").formatted(Formatting.GOLD, Formatting.BOLD));
			lines.add(Text.translatable("diamondaltar.ability." + key + ".desc").formatted(Formatting.GRAY));
			if (ability != null) {
				lines.add(Text.translatable("diamondaltar.ability.hint", ability.cooldown() / 20).formatted(Formatting.DARK_AQUA));
			} else {
				lines.add(Text.translatable("diamondaltar.ability.armor_set").formatted(Formatting.DARK_AQUA));
			}
		});
	}
}
