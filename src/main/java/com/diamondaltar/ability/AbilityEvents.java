package com.diamondaltar.ability;

import com.diamondaltar.Compat;
import com.diamondaltar.ModComponents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Branche les capacités sur les touches et clics du mod Fabric (paquets envoyés par le client). */
public final class AbilityEvents {
	private AbilityEvents() {
	}

	public static void register() {
		Compat.toServer().register(SlashPayload.ID, SlashPayload.CODEC);
		Compat.toServer().register(SwitchAbilityPayload.ID, SwitchAbilityPayload.CODEC);
		Compat.toServer().register(ArmorActionPayload.ID, ArmorActionPayload.CODEC);
		Compat.toClient().register(ShakePayload.ID, ShakePayload.CODEC);
		Compat.toClient().register(FlashPayload.ID, FlashPayload.CODEC);

		// Touche J : changer la capacité d'armure. Shift + clic droit (envoyé par le client) : la lancer.
		ServerPlayNetworking.registerGlobalReceiver(ArmorActionPayload.ID, (payload, context) -> {
			if (payload.activate()) {
				Abilities.useArmor(context.player());
			} else {
				Abilities.switchArmor(context.player());
			}
		});
		// Clic gauche avec l'épée / la lance infusée.
		ServerPlayNetworking.registerGlobalReceiver(SlashPayload.ID, (payload, context) -> Abilities.leftClick(context.player()));
		// Touche K : capacité suivante.
		ServerPlayNetworking.registerGlobalReceiver(SwitchAbilityPayload.ID, (payload, context) -> Abilities.switchTool(context.player()));

		// Clic droit avec les autres objets infusés.
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof net.minecraft.server.level.ServerLevel sl && player instanceof ServerPlayer sp) {
				ToolPassives.onBlockBroken(sl, sp, pos, state);
			}
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!Abilities.usesRightClick(stack) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			// Shift + clic droit est réservé à l'armure (envoyé par le client) : l'outil ne fait rien.
			if (player.isShiftKeyDown() && ModComponents.isInfused(player.getItemBySlot(EquipmentSlot.CHEST))) {
				return InteractionResult.SUCCESS;
			}
			if (player instanceof ServerPlayer serverPlayer) {
				Abilities.rightClick(serverPlayer, hand);
			}
			return InteractionResult.SUCCESS;
		});
	}
}
