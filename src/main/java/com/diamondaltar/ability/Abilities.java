package com.diamondaltar.ability;

import com.diamondaltar.ModComponents;
import com.diamondaltar.effect.EffectManager;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;

/** Capacités actives des outils infusés : clic gauche pour l'épée, clic droit pour les autres. */
public final class Abilities {
	public record Ability(String key, int cooldown, int durabilityCost, boolean leftClick, Factory factory) {
		Ability(String key, int cooldown, int durabilityCost, Factory factory) {
			this(key, cooldown, durabilityCost, false, factory);
		}
	}

	@FunctionalInterface
	public interface Factory {
		void start(ServerLevel level, ServerPlayer player);
	}

	public static final Map<Item, Ability> ABILITIES = Map.of(
			Items.DIAMOND_SWORD, new Ability("sword", 0, 1, true,
					(l, p) -> EffectManager.start(new SwordSlashEffect(l, p))),
			Items.DIAMOND_PICKAXE, new Ability("pickaxe", 80, 10,
					(l, p) -> EffectManager.start(new PickaxeDrillEffect(l, p))),
			Items.DIAMOND_AXE, new Ability("axe", 120, 8,
					(l, p) -> EffectManager.start(new AxeCycloneEffect(l, p))),
			Items.DIAMOND_SHOVEL, new Ability("shovel", 100, 6,
					(l, p) -> EffectManager.start(new ShovelQuakeEffect(l, p))),
			Items.DIAMOND_HOE, new Ability("hoe", 100, 6,
					(l, p) -> EffectManager.start(new HoeReapEffect(l, p))),
			Items.MACE, new Ability("mace", 160, 8,
					(l, p) -> EffectManager.start(new MaceMeteorEffect(l, p))));

	private Abilities() {
	}

	public static void register() {
		// Clic gauche avec l'épée infusée : Tranche de la Volonté, sans recharge.
		PayloadTypeRegistry.playC2S().register(SlashPayload.ID, SlashPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SlashPayload.ID, (payload, context) -> {
			ServerPlayer player = context.player();
			ItemStack stack = player.getMainHandItem();
			Ability ability = ABILITIES.get(stack.getItem());
			if (ability == null || !ability.leftClick() || !ModComponents.isInfused(stack) || player.isSpectator()
					|| !(player.level() instanceof ServerLevel server)) {
				return;
			}
			ability.factory().start(server, player);
			stack.hurtAndBreak(ability.durabilityCost(), player, EquipmentSlot.MAINHAND);
		});

		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			Ability ability = ABILITIES.get(stack.getItem());
			if (ability == null || ability.leftClick() || !ModComponents.isInfused(stack) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			if (player.getCooldowns().isOnCooldown(stack)) {
				return InteractionResult.FAIL;
			}
			if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
				ability.factory().start(server, serverPlayer);
				player.getCooldowns().addCooldown(stack, ability.cooldown());
				stack.hurtAndBreak(ability.durabilityCost(), player,
						hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
				player.swing(hand, true);
			}
			return InteractionResult.SUCCESS;
		});
	}
}
