package com.diamondaltar.ability;

import com.diamondaltar.ModComponents;
import com.diamondaltar.effect.EffectManager;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypedActionResult;

import java.util.Map;

/** Capacités actives des outils infusés, déclenchées par clic droit. */
public final class Abilities {
	public record Ability(String key, int cooldown, int durabilityCost, Factory factory) {
	}

	@FunctionalInterface
	public interface Factory {
		void start(ServerWorld world, ServerPlayerEntity player);
	}

	public static final Map<Item, Ability> ABILITIES = Map.of(
			Items.DIAMOND_SWORD, new Ability("sword", 60, 5,
					(w, p) -> EffectManager.start(new SwordSlashEffect(w, p))),
			Items.DIAMOND_PICKAXE, new Ability("pickaxe", 80, 10,
					(w, p) -> EffectManager.start(new PickaxeDrillEffect(w, p))),
			Items.DIAMOND_AXE, new Ability("axe", 120, 8,
					(w, p) -> EffectManager.start(new AxeCycloneEffect(w, p))),
			Items.DIAMOND_SHOVEL, new Ability("shovel", 100, 6,
					(w, p) -> EffectManager.start(new ShovelQuakeEffect(w, p))),
			Items.DIAMOND_HOE, new Ability("hoe", 100, 6,
					(w, p) -> EffectManager.start(new HoeReapEffect(w, p))),
			Items.MACE, new Ability("mace", 160, 8,
					(w, p) -> EffectManager.start(new MaceMeteorEffect(w, p))));

	private Abilities() {
	}

	public static void register() {
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getStackInHand(hand);
			Ability ability = ABILITIES.get(stack.getItem());
			if (ability == null || !ModComponents.isInfused(stack) || player.isSpectator()) {
				return TypedActionResult.pass(ItemStack.EMPTY);
			}
			if (player.getItemCooldownManager().isCoolingDown(stack.getItem())) {
				return TypedActionResult.fail(stack);
			}
			if (world instanceof ServerWorld server && player instanceof ServerPlayerEntity serverPlayer) {
				ability.factory().start(server, serverPlayer);
				player.getItemCooldownManager().set(stack.getItem(), ability.cooldown());
				stack.damage(ability.durabilityCost(), player, LivingEntity.getSlotForHand(hand));
				player.swingHand(hand, true);
			}
			return TypedActionResult.success(stack, world.isClient);
		});
	}
}
