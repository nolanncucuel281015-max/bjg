package com.diamondaltar.ability;

import com.diamondaltar.ModComponents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Capacités passives de l'armure infusée :
 * casque = vision nocturne + respiration aquatique, plastron = résistance,
 * jambières = vitesse, bottes = saut + aucune chute. Armure complète = régénération + aura.
 */
public final class ArmorPassives {
	private ArmorPassives() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 20 != 0) {
				return;
			}
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				tickPlayer(player);
			}
		});

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof PlayerEntity player
						&& source.isIn(DamageTypeTags.IS_FALL)
						&& ModComponents.isInfused(player.getEquippedStack(EquipmentSlot.FEET))));
	}

	private static void tickPlayer(ServerPlayerEntity player) {
		boolean head = ModComponents.isInfused(player.getEquippedStack(EquipmentSlot.HEAD));
		boolean chest = ModComponents.isInfused(player.getEquippedStack(EquipmentSlot.CHEST));
		boolean legs = ModComponents.isInfused(player.getEquippedStack(EquipmentSlot.LEGS));
		boolean feet = ModComponents.isInfused(player.getEquippedStack(EquipmentSlot.FEET));

		if (head) {
			give(player, StatusEffects.NIGHT_VISION, 0, 300);
			give(player, StatusEffects.WATER_BREATHING, 0, 60);
		}
		if (chest) {
			give(player, StatusEffects.RESISTANCE, 0, 60);
		}
		if (legs) {
			give(player, StatusEffects.SPEED, 1, 60);
		}
		if (feet) {
			give(player, StatusEffects.JUMP_BOOST, 1, 60);
		}
		if (head && chest && legs && feet) {
			give(player, StatusEffects.REGENERATION, 0, 60);
			give(player, StatusEffects.STRENGTH, 0, 60);
			ServerWorld world = player.getServerWorld();
			for (int i = 0; i < 12; i++) {
				double a = Math.PI * 2 * i / 12;
				world.spawnParticles(ParticleTypes.END_ROD,
						player.getX() + Math.cos(a) * 0.8, player.getY() + 0.1, player.getZ() + Math.sin(a) * 0.8,
						1, 0, 0.05, 0, 0.01);
			}
		}
	}

	private static void give(ServerPlayerEntity player, RegistryEntry<StatusEffect> effect, int amplifier, int duration) {
		player.addStatusEffect(new StatusEffectInstance(effect, duration, amplifier, true, false, true));
	}
}
