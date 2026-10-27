package com.diamondaltar.client;

import com.diamondaltar.DiamondAltar;
import com.diamondaltar.ModComponents;
import com.diamondaltar.ModParticles;
import com.diamondaltar.astral.AstralContent;
import com.diamondaltar.ability.FlashPayload;
import com.diamondaltar.ability.Abilities;
import com.diamondaltar.ability.ArmorActionPayload;
import com.diamondaltar.ability.ShakePayload;
import com.diamondaltar.ability.SlashPayload;
import com.diamondaltar.ability.SwitchAbilityPayload;
import com.diamondaltar.block.ModBlocks;
import com.diamondaltar.realms.Realm;
import com.diamondaltar.realms.RealmContent;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap; // @1.21.11
import net.minecraft.client.renderer.chunk.ChunkSectionLayer; // @1.21.11
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
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

	/** Pour détecter le moment où le clic droit est enfoncé (Shift + clic droit = capacité d'armure). */
	private static boolean useWasDown;

	@Override
	public void onInitializeClient() {
		BlockEntityRenderers.register(ModBlocks.INFUSION_ALTAR_ENTITY, InfusionAltarRenderer::new);
		// #if 1.21.11
		BlockRenderLayerMap.putBlock(AstralContent.ASTRAL_PORTAL, ChunkSectionLayer.TRANSLUCENT);
		BlockRenderLayerMap.putBlock(AstralContent.FUSER, ChunkSectionLayer.CUTOUT);
		BlockRenderLayerMap.putBlock(AstralContent.ASTRAL_LEAVES, ChunkSectionLayer.CUTOUT);
		BlockRenderLayerMap.putBlock(RealmContent.REALM_PORTAL, ChunkSectionLayer.TRANSLUCENT);
		BlockRenderLayerMap.putBlock(RealmContent.SYLVAN_LEAVES, ChunkSectionLayer.CUTOUT);
		// #endif

		// Manuel : clic droit pour l'ouvrir.
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) -> {
			if (player.getItemInHand(hand).is(AstralContent.MANUAL)) {
				if (level.isClientSide()) {
					net.minecraft.client.Minecraft.getInstance().setScreen(new ManualScreen());
				}
				return net.minecraft.world.InteractionResult.SUCCESS;
			}
			return net.minecraft.world.InteractionResult.PASS;
		});

		// Halos lumineux (une couleur par type de particule).
		ParticleFactoryRegistry particles = ParticleFactoryRegistry.getInstance();
		particles.register(ModParticles.GLOW_WHITE, s -> new GlowOrbParticle.Provider(s, 0xFFFFFF));
		particles.register(ModParticles.GLOW_ICE, s -> new GlowOrbParticle.Provider(s, 0xA8EEFF));
		particles.register(ModParticles.GLOW_NEBULA, s -> new GlowOrbParticle.Provider(s, 0xB070FF));
		particles.register(ModParticles.GLOW_PINK, s -> new GlowOrbParticle.Provider(s, 0xFF70D8));
		particles.register(ModParticles.GLOW_GOLD, s -> new GlowOrbParticle.Provider(s, 0xFFD860));
		particles.register(ModParticles.GLOW_FIRE, s -> new GlowOrbParticle.Provider(s, 0xFF8A30));

		// Flash d'écran (surexposition) lors des grosses attaques.
		ClientPlayNetworking.registerGlobalReceiver(FlashPayload.ID, (payload, context) ->
				ScreenFlash.start(payload.color(), payload.strength(), payload.ticks()));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, DiamondAltar.id("flash"), ScreenFlash::render);

		ClientPlayNetworking.registerGlobalReceiver(ShakePayload.ID, (payload, context) ->
				ScreenShake.start(payload.strength(), payload.ticks()));

		// Touche K : changer de capacité.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ScreenShake.tick(client);
			ScreenFlash.tick();
			while (SWITCH_KEY.consumeClick()) {
				if (client.player != null && ModComponents.isInfused(client.player.getMainHandItem())
						&& !Abilities.of(client.player.getMainHandItem().getItem()).isEmpty()) {
					ClientPlayNetworking.send(new SwitchAbilityPayload());
				}
			}
			// Touche J : changer la capacité de l'armure. Shift + clic droit : la lancer.
			while (ARMOR_SWITCH_KEY.consumeClick()) {
				if (client.player != null) {
					ClientPlayNetworking.send(new ArmorActionPayload(false));
				}
			}
			boolean useDown = client.options.keyUse.isDown();
			// #if 1.21.11
			if (useDown && !useWasDown && client.player != null && client.screen == null
			// #else
			//? if (useDown && !useWasDown && client.player != null
			// #endif
					&& client.player.isShiftKeyDown()
					&& ModComponents.isInfused(client.player.getItemBySlot(EquipmentSlot.CHEST))) {
				ClientPlayNetworking.send(new ArmorActionPayload(true));
			}
			useWasDown = useDown;
		});

		// Clic gauche avec l'épée infusée : on demande au serveur de lancer la capacité choisie.
		ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
			ItemStack stack = player.getMainHandItem();
			if (clickCount > 0 && Abilities.isLeftClick(stack) && ModComponents.isInfused(stack) && !player.isSpectator()) {
				ClientPlayNetworking.send(new SlashPayload());
			}
			return false;
		});

		// Infobulles des 7 Royaumes : bonus d'armure complète, épée, clés et sceaux.
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			Realm armor = RealmContent.armorRealm(stack.getItem());
			if (armor != null) {
				lines.add(Component.translatable("diamondaltar.realm_armor.set").withStyle(armor.chat, ChatFormatting.BOLD));
				lines.add(Component.translatable("diamondaltar.realm_armor.set." + armor.id).withStyle(ChatFormatting.GRAY));
			}
			if (stack.is(RealmContent.SEVEN_REALMS_SWORD)) {
				lines.add(Component.translatable("diamondaltar.seven_realms.hit").withStyle(ChatFormatting.GRAY));
				lines.add(Component.translatable("diamondaltar.seven_realms.use").withStyle(ChatFormatting.GOLD));
			}
			for (Realm r : Realm.values()) {
				if (stack.is(RealmContent.KEY[r.ordinal()]) || stack.is(RealmContent.SIGIL[r.ordinal()])) {
					lines.add(Component.translatable(r.nameKey()).withStyle(r.chat));
					lines.add(Component.translatable(stack.is(RealmContent.KEY[r.ordinal()]) ? "diamondaltar.key.desc" : "diamondaltar.sigil.desc")
							.withStyle(ChatFormatting.GRAY));
				}
			}
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
					lines.add(Component.translatable("diamondaltar.ability.armor_keys", ARMOR_SWITCH_KEY.getTranslatedKeyMessage())
							.withStyle(ChatFormatting.DARK_AQUA));
				}
			}
		});
	}
}
