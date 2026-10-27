package com.diamondaltar.astral;

import com.diamondaltar.Compat;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Mobs de la Dimension Astrale : les mobs qui y apparaissent deviennent des versions astrales
 * (nom, taille, vie, faible gravité lunaire, équipement) et lâchent du butin astral.
 */
public final class AstralMobs {
	public static final String TAG = "diamondaltar_astral_mob";

	private AstralMobs() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && level.dimension() == AstralContent.DIMENSION
					&& !Compat.tags(mob).contains(TAG) && !Guardian.isGuardian(mob)) {
				transform(mob);
			}
		});
	}

	public static boolean isAstral(LivingEntity e) {
		return Compat.tags(e).contains(TAG);
	}

	/** Transforme un mob en version astrale (ne fait rien pour les types non prévus). */
	public static void transform(Mob mob) {
		EntityType<?> type = mob.getType();
		String key;
		ChatFormatting color;
		boolean hostile = true;
		if (type == Compat.ENDERMAN) {
			key = "void_walker"; color = ChatFormatting.DARK_PURPLE;
			set(mob, Attributes.SCALE, 1.25); set(mob, Attributes.MAX_HEALTH, 60); set(mob, Attributes.ATTACK_DAMAGE, 10);
		} else if (type == Compat.PHANTOM) {
			key = "star_ray"; color = ChatFormatting.AQUA;
			set(mob, Attributes.SCALE, 1.6); set(mob, Attributes.MAX_HEALTH, 34);
		} else if (type == Compat.SKELETON) {
			key = "lunar_archer"; color = ChatFormatting.WHITE;
			set(mob, Attributes.MAX_HEALTH, 30); set(mob, Attributes.MOVEMENT_SPEED, 0.3);
			mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.AMETHYST_CLUSTER));
			mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			mob.setDropChance(EquipmentSlot.HEAD, 0);
		} else if (type == Compat.MAGMA_CUBE) {
			key = "living_meteor"; color = ChatFormatting.GOLD;
			set(mob, Attributes.MAX_HEALTH, 24);
		} else if (type == Compat.RABBIT) {
			key = "moon_rabbit"; color = ChatFormatting.WHITE; hostile = false;
			mob.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, MobEffectInstance.INFINITE_DURATION, 3, false, false));
		} else if (type == Compat.GOAT) {
			key = "lunar_goat"; color = ChatFormatting.LIGHT_PURPLE; hostile = false;
			set(mob, Attributes.SCALE, 1.15);
			mob.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, MobEffectInstance.INFINITE_DURATION, 2, false, false));
		} else {
			return;
		}
		mob.addTag(TAG);
		if (hostile) {
			mob.addTag(TAG + "_hostile");
		}
		mob.setHealth(mob.getMaxHealth());
		mob.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		if (!mob.hasCustomName()) {
			mob.setCustomName(Component.translatable("entity.diamondaltar." + key).withStyle(color));
			mob.setCustomNameVisible(false);
		}
	}

	/** Butin astral à la mort d'un mob hostile tué par un joueur. */
	public static void onDeath(ServerLevel level, LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
		if (!Compat.tags(entity).contains(TAG + "_hostile") || !(source.getEntity() instanceof Player)) {
			return;
		}
		var random = entity.getRandom();
		int lunarite = random.nextInt(3);
		if (lunarite > 0) {
			entity.spawnAtLocation(level, new ItemStack(AstralContent.LUNARITE, lunarite));
		}
		if (random.nextFloat() < 0.04f) {
			entity.spawnAtLocation(level, new ItemStack(AstralContent.RAW_ASTRALITE));
		}
	}

	private static void set(LivingEntity e, Holder<Attribute> attr, double value) {
		var inst = e.getAttribute(attr);
		if (inst != null) {
			inst.setBaseValue(value);
		}
	}
}
