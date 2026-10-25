package com.diamondaltar.ability;

import com.diamondaltar.ModComponents;
import com.diamondaltar.effect.EffectManager;
import com.diamondaltar.effect.TimedEffect;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Capacités actives des objets infusés. Chaque objet en a plusieurs : la touche K change
 * la capacité choisie. L'épée se lance au clic gauche, les autres objets au clic droit.
 */
public final class Abilities {
	public record Ability(String key, int cooldown, int durabilityCost, BiFunction<ServerLevel, ServerPlayer, TimedEffect> factory) {
	}

	private static Ability a(String key, int cooldown, int durability, BiFunction<ServerLevel, ServerPlayer, TimedEffect> factory) {
		return new Ability(key, cooldown, durability, factory);
	}

	public static final Map<Item, List<Ability>> ABILITIES = Map.of(
			Items.DIAMOND_SWORD, List.of(
					a("sword_slash", 0, 1, SwordSlashEffect::new),
					a("sword_blades", 10, 2, PhantomBladesEffect::new),
					a("sword_shadow", 10, 2, ShadowStepEffect::new),
					a("sword_judgement", 10, 4, CelestialJudgementEffect::new),
					a("sword_void", 10, 5, VoidRiftEffect::new)),
			Items.DIAMOND_PICKAXE, List.of(
					a("pickaxe_drill", 10, 8, PickaxeDrillEffect::new),
					a("pickaxe_sphere", 10, 6, CrystalSphereEffect::new),
					a("pickaxe_ores", 10, 2, OreSightEffect::new),
					a("pickaxe_eruption", 10, 4, CrystalEruptionEffect::new)),
			Items.DIAMOND_AXE, List.of(
					a("axe_cyclone", 10, 6, AxeCycloneEffect::new),
					a("axe_boomerang", 10, 3, BoomerangAxeEffect::new),
					a("axe_timber", 10, 6, TimberEffect::new),
					a("axe_fissure", 10, 4, FissureEffect::new)),
			Items.DIAMOND_SHOVEL, List.of(
					a("shovel_quake", 10, 5, ShovelQuakeEffect::new),
					a("shovel_quicksand", 10, 4, QuicksandEffect::new),
					a("shovel_excavate", 10, 8, ExcavateEffect::new)),
			Items.DIAMOND_HOE, List.of(
					a("hoe_reap", 10, 5, HoeReapEffect::new),
					a("hoe_vines", 10, 4, VinesEffect::new),
					a("hoe_bloom", 10, 4, BloomEffect::new)),
			Items.MACE, List.of(
					a("mace_meteor", 10, 6, MaceMeteorEffect::new),
					a("mace_gravity", 10, 5, GravityWellEffect::new),
					a("mace_thunder", 10, 6, ThunderEffect::new)));

	/** Recharges par joueur et par capacité : fin de recharge en ticks de jeu. */
	private static final Map<UUID, Map<String, Long>> COOLDOWNS = new HashMap<>();

	private Abilities() {
	}

	public static List<Ability> of(Item item) {
		return ABILITIES.getOrDefault(item, List.of());
	}

	public static int selectedIndex(ItemStack stack) {
		List<Ability> list = of(stack.getItem());
		if (list.isEmpty()) {
			return 0;
		}
		Integer i = stack.get(ModComponents.SELECTED_ABILITY);
		return Math.floorMod(i == null ? 0 : i, list.size());
	}

	public static Ability selected(ItemStack stack) {
		List<Ability> list = of(stack.getItem());
		return list.isEmpty() ? null : list.get(selectedIndex(stack));
	}

	public static boolean isLeftClick(ItemStack stack) {
		return stack.is(Items.DIAMOND_SWORD);
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(SlashPayload.ID, SlashPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SwitchAbilityPayload.ID, SwitchAbilityPayload.CODEC);

		// Clic gauche avec l'épée infusée.
		ServerPlayNetworking.registerGlobalReceiver(SlashPayload.ID, (payload, context) -> {
			ServerPlayer player = context.player();
			ItemStack stack = player.getMainHandItem();
			if (isLeftClick(stack)) {
				tryActivate(player, stack, InteractionHand.MAIN_HAND);
			}
		});

		// Touche K : capacité suivante.
		ServerPlayNetworking.registerGlobalReceiver(SwitchAbilityPayload.ID, (payload, context) -> {
			ServerPlayer player = context.player();
			ItemStack stack = player.getMainHandItem();
			List<Ability> list = of(stack.getItem());
			if (list.isEmpty() || !ModComponents.isInfused(stack)) {
				return;
			}
			int next = (selectedIndex(stack) + 1) % list.size();
			stack.set(ModComponents.SELECTED_ABILITY, next);
			player.displayClientMessage(Component.translatable("diamondaltar.ability.switched",
					Component.translatable("diamondaltar.ability." + list.get(next).key() + ".name").withStyle(ChatFormatting.GOLD),
					next + 1, list.size()).withStyle(ChatFormatting.AQUA), true);
			player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.2f + next * 0.15f);
		});

		// Clic droit avec les autres objets infusés.
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (of(stack.getItem()).isEmpty() || isLeftClick(stack) || !ModComponents.isInfused(stack) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer serverPlayer) {
				tryActivate(serverPlayer, stack, hand);
			}
			return InteractionResult.SUCCESS;
		});
	}

	private static void tryActivate(ServerPlayer player, ItemStack stack, InteractionHand hand) {
		Ability ability = selected(stack);
		if (ability == null || !ModComponents.isInfused(stack) || player.isSpectator()
				|| !(player.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		Map<String, Long> mine = COOLDOWNS.computeIfAbsent(player.getUUID(), u -> new HashMap<>());
		long readyAt = mine.getOrDefault(ability.key(), 0L);
		if (now < readyAt) {
			player.displayClientMessage(Component.translatable("diamondaltar.ability.cooldown",
					String.format("%.1f", (readyAt - now) / 20.0)).withStyle(ChatFormatting.RED), true);
			return;
		}
		mine.put(ability.key(), now + ability.cooldown());
		EffectManager.start(ability.factory().apply(level, player));
		stack.hurtAndBreak(ability.durabilityCost(), player,
				hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
		player.swing(hand, true);
	}
}
