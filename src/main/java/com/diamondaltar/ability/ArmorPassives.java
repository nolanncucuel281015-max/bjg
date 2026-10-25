package com.diamondaltar.ability;

import com.diamondaltar.ModComponents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/**
 * Capacités passives de l'armure infusée :
 * casque = vision nocturne + respiration + force des conduits, plastron = résistance II + feu,
 * jambières = vitesse II + célérité II, bottes = saut II + grâce du dauphin + aucune chute.
 * Armure complète = régénération II + force II + saturation + aura.
 */
public final class ArmorPassives {
	private ArmorPassives() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 0) {
				return;
			}
			for (ServerLevel level : server.getAllLevels()) {
				for (ServerPlayer player : level.players()) {
					tickPlayer(level, player);
				}
			}
		});

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof Player player
						&& source.is(DamageTypeTags.IS_FALL)
						&& ModComponents.isInfused(player.getItemBySlot(EquipmentSlot.FEET))));
	}

	private static void tickPlayer(ServerLevel level, ServerPlayer player) {
		// Les objets infusés avec une ancienne version du mod reçoivent les nouveautés.
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ModComponents.upgradeIfOld(player.getItemBySlot(slot));
		}
		boolean head = ModComponents.isInfused(player.getItemBySlot(EquipmentSlot.HEAD));
		boolean chest = ModComponents.isInfused(player.getItemBySlot(EquipmentSlot.CHEST));
		boolean legs = ModComponents.isInfused(player.getItemBySlot(EquipmentSlot.LEGS));
		boolean feet = ModComponents.isInfused(player.getItemBySlot(EquipmentSlot.FEET));

		if (head) {
			give(player, MobEffects.NIGHT_VISION, 0, 300);
			give(player, MobEffects.WATER_BREATHING, 0, 60);
			give(player, MobEffects.CONDUIT_POWER, 0, 60);
		}
		if (chest) {
			give(player, MobEffects.RESISTANCE, 1, 60);
			give(player, MobEffects.FIRE_RESISTANCE, 0, 60);
		}
		if (legs) {
			give(player, MobEffects.SPEED, 1, 60);
			give(player, MobEffects.HASTE, 1, 60);
		}
		if (feet) {
			give(player, MobEffects.JUMP_BOOST, 1, 60);
			give(player, MobEffects.DOLPHINS_GRACE, 0, 60);
		}
		if (head && chest && legs && feet) {
			give(player, MobEffects.REGENERATION, 1, 60);
			give(player, MobEffects.STRENGTH, 1, 60);
			give(player, MobEffects.SATURATION, 0, 2);
			for (int i = 0; i < 12; i++) {
				double a = Math.PI * 2 * i / 12;
				level.sendParticles(ParticleTypes.END_ROD,
						player.getX() + Math.cos(a) * 0.8, player.getY() + 0.1, player.getZ() + Math.sin(a) * 0.8,
						1, 0, 0.05, 0, 0.01);
			}
		}
	}

	private static void give(ServerPlayer player, Holder<MobEffect> effect, int amplifier, int duration) {
		player.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false, true));
	}
}
