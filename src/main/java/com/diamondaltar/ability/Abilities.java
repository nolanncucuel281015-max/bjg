package com.diamondaltar.ability;

import com.diamondaltar.Compat;
import com.diamondaltar.ModComponents;
import com.diamondaltar.effect.EffectManager;
import com.diamondaltar.effect.TimedEffect;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
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
					a("sword_edge", 10, 1, CelestialEdgeEffect::new),
					a("sword_blades", 10, 2, PhantomBladesEffect::new),
					a("sword_shadow", 10, 2, ShadowStepEffect::new),
					a("sword_judgement", 10, 4, CelestialJudgementEffect::new),
					a("sword_void", 10, 5, VoidRiftEffect::new),
					a("sword_crescent", 10, 2, LunarCrescentEffect::new),
					a("sword_starfall", 10, 4, StarfallEffect::new),
					a("sword_eclipse", 10, 5, EclipseEffect::new),
					a("sword_zodiac", 10, 4, ZodiacBladesEffect::new)),
			Items.DIAMOND_SPEAR, List.of(
					a("spear_comet", 10, 3, CometLanceEffect::new),
					a("spear_charge", 10, 3, StellarChargeEffect::new),
					a("spear_constellation", 10, 5, ConstellationEffect::new),
					a("spear_whirl", 10, 4, GalacticWhirlEffect::new),
					a("spear_polar", 10, 4, PolarLanceEffect::new)),
			Items.DIAMOND_PICKAXE, List.of(
					a("pickaxe_drill", 10, 8, PickaxeDrillEffect::new),
					a("pickaxe_sphere", 10, 6, CrystalSphereEffect::new),
					a("pickaxe_ores", 10, 2, OreSightEffect::new),
					a("pickaxe_eruption", 10, 4, CrystalEruptionEffect::new),
					a("pickaxe_beam", 10, 6, StellarBeamEffect::new),
					a("pickaxe_geode", 10, 5, CelestialGeodeEffect::new),
					a("pickaxe_meteorite", 10, 8, MeteoriteEffect::new)),
			Items.DIAMOND_AXE, List.of(
					a("axe_cyclone", 10, 6, AxeCycloneEffect::new),
					a("axe_boomerang", 10, 3, BoomerangAxeEffect::new),
					a("axe_timber", 10, 6, TimberEffect::new),
					a("axe_fissure", 10, 4, FissureEffect::new),
					a("axe_orbit", 10, 5, LunarOrbitEffect::new),
					a("axe_titan", 10, 6, TitanAxeEffect::new)),
			Items.DIAMOND_SHOVEL, List.of(
					a("shovel_quake", 10, 5, ShovelQuakeEffect::new),
					a("shovel_quicksand", 10, 4, QuicksandEffect::new),
					a("shovel_excavate", 10, 8, ExcavateEffect::new),
					a("shovel_gravity", 10, 5, LunarGravityEffect::new),
					a("shovel_crater", 10, 5, LunarCraterEffect::new),
					a("shovel_asteroids", 10, 5, AsteroidWallEffect::new)),
			Items.DIAMOND_HOE, List.of(
					a("hoe_reap", 10, 5, HoeReapEffect::new),
					a("hoe_vines", 10, 4, VinesEffect::new),
					a("hoe_bloom", 10, 4, BloomEffect::new),
					a("hoe_scythe", 10, 5, CelestialScytheEffect::new),
					a("hoe_garden", 10, 4, StarGardenEffect::new),
					a("hoe_petals", 10, 4, NebulaPetalsEffect::new)),
			Items.MACE, List.of(
					a("mace_meteor", 10, 6, MaceMeteorEffect::new),
					a("mace_gravity", 10, 5, GravityWellEffect::new),
					a("mace_thunder", 10, 6, ThunderEffect::new),
					a("mace_supernova", 10, 8, SupernovaEffect::new),
					a("mace_pulsar", 10, 6, PulsarEffect::new)));

	/** Capacités de l'armure infusée (il faut porter le plastron infusé). J pour changer, Shift + clic droit pour lancer. */
	public static final List<Ability> ARMOR = List.of(
			a("armor_barrier", 10, 0, CrystalBarrierEffect::new),
			a("armor_dash", 10, 0, DiamondDashEffect::new),
			a("armor_nova", 10, 0, PrismNovaEffect::new),
			a("armor_wings", 10, 0, DiamondWingsEffect::new),
			a("armor_avatar", 10, 0, AvatarEffect::new),
			a("armor_astral", 10, 0, AstralJumpEffect::new),
			a("armor_gravity", 10, 0, GravityShieldEffect::new));

	public static int armorIndex(ItemStack chest) {
		return Math.floorMod(ModComponents.getSelected(chest), ARMOR.size());
	}

	/** Recharges par joueur et par capacité : fin de recharge en ticks de jeu. */
	private static final Map<UUID, Map<String, Long>> COOLDOWNS = new HashMap<>();

	private Abilities() {
	}

	public static List<Ability> of(Item item) {
		return ABILITIES.getOrDefault(ModComponents.abilityItem(item), List.of());
	}

	public static int selectedIndex(ItemStack stack) {
		List<Ability> list = of(stack.getItem());
		if (list.isEmpty()) {
			return 0;
		}
		return Math.floorMod(ModComponents.getSelected(stack), list.size());
	}

	public static Ability selected(ItemStack stack) {
		List<Ability> list = of(stack.getItem());
		return list.isEmpty() ? null : list.get(selectedIndex(stack));
	}

	public static boolean isLeftClick(ItemStack stack) {
		Item item = ModComponents.abilityItem(stack.getItem());
		return item == Items.DIAMOND_SWORD || item == Items.DIAMOND_SPEAR;
	}

	/** Touche « changer de capacité » (K) : capacité suivante de l'objet en main. */
	public static void switchTool(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();
		List<Ability> list = of(stack.getItem());
		if (list.isEmpty() || !ModComponents.isInfused(stack)) {
			return;
		}
		int next = (selectedIndex(stack) + 1) % list.size();
		ModComponents.setSelected(stack, next);
		announce(player, list.get(next), next, list.size());
	}

	/** Touche « changer la capacité d'armure » (J). */
	public static void switchArmor(ServerPlayer player) {
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (!requireChest(player, chest)) {
			return;
		}
		int next = (armorIndex(chest) + 1) % ARMOR.size();
		ModComponents.setSelected(chest, next);
		announce(player, ARMOR.get(next), next, ARMOR.size());
	}

	/** Shift + clic droit : lance la capacité d'armure choisie. */
	public static void useArmor(ServerPlayer player) {
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (requireChest(player, chest)) {
			activate(player, chest, ARMOR.get(armorIndex(chest)), EquipmentSlot.CHEST);
		}
	}

	public static boolean wearsInfusedChest(ServerPlayer player) {
		return ModComponents.isInfused(player.getItemBySlot(EquipmentSlot.CHEST));
	}

	private static boolean requireChest(ServerPlayer player, ItemStack chest) {
		if (!ModComponents.isInfused(chest)) {
			Compat.overlay(player, Component.translatable("diamondaltar.ability.need_chestplate").withStyle(ChatFormatting.RED));
			return false;
		}
		return true;
	}

	/** Clic gauche : capacité de l'épée / de la lance infusée en main. */
	public static void leftClick(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();
		if (isLeftClick(stack)) {
			tryActivate(player, stack, InteractionHand.MAIN_HAND);
		}
	}

	/** Est-ce que cet objet lance une capacité au clic droit ? */
	public static boolean usesRightClick(ItemStack stack) {
		return !of(stack.getItem()).isEmpty() && !isLeftClick(stack) && ModComponents.isInfused(stack);
	}

	/** Clic droit avec un outil infusé (pas l'épée / la lance). */
	public static void rightClick(ServerPlayer player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (usesRightClick(stack) && !player.isSpectator()) {
			tryActivate(player, stack, hand);
		}
	}

	private static void announce(ServerPlayer player, Ability ability, int index, int count) {
		Compat.overlay(player, Component.translatable("diamondaltar.ability.switched",
				Component.translatable("diamondaltar.ability." + ability.key() + ".name").withStyle(ChatFormatting.GOLD),
				index + 1, count).withStyle(ChatFormatting.AQUA));
		player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.2f + index * 0.15f);
	}

	private static void tryActivate(ServerPlayer player, ItemStack stack, InteractionHand hand) {
		Ability ability = selected(stack);
		if (ability != null && ModComponents.isInfused(stack)) {
			activate(player, stack, ability, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
			Compat.swing(player, hand);
		}
	}

	private static void activate(ServerPlayer player, ItemStack stack, Ability ability, EquipmentSlot slot) {
		if (player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		Map<String, Long> mine = COOLDOWNS.computeIfAbsent(player.getUUID(), u -> new HashMap<>());
		long readyAt = mine.getOrDefault(ability.key(), 0L);
		if (now < readyAt) {
			Compat.overlay(player, Component.translatable("diamondaltar.ability.cooldown",
					String.format("%.1f", (readyAt - now) / 20.0)).withStyle(ChatFormatting.RED));
			return;
		}
		mine.put(ability.key(), now + ability.cooldown());
		// Aura de lancement : anneau de lune + étoiles qui montent autour du joueur.
		Vec3 feet = player.position().add(0, 0.1, 0);
		FX.ring(level, Cosmic.MOON_SMALL, feet, 1.2, 20);
		FX.ring(level, Cosmic.NEBULA, feet, 0.8, 14);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(1.0), player.getZ(), 6, 0.4, 0.6, 0.4, 0.05);
		EffectManager.start(ability.factory().apply(level, player));
		if (ability.durabilityCost() > 0) {
			stack.hurtAndBreak(ability.durabilityCost(), player, slot);
		}
	}
}
