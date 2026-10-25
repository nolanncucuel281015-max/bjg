package com.diamondaltar.ability;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Petits utilitaires partagés par les capacités. */
final class FX {
	private FX() {
	}

	/** Direction horizontale du regard du joueur (normalisée). */
	static Vec3 forward(ServerPlayer player) {
		double yaw = Math.toRadians(player.getYRot());
		return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
	}

	/** Direction horizontale à droite du joueur. */
	static Vec3 right(Vec3 forward) {
		return new Vec3(-forward.z, 0, forward.x);
	}

	static List<LivingEntity> livingAround(ServerLevel level, ServerPlayer player, Vec3 center, double radius) {
		AABB box = AABB.ofSize(center, radius * 2, radius * 2, radius * 2);
		return level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e != player && e.isAlive() && !e.isSpectator() && e.distanceToSqr(center) <= radius * radius);
	}

	static void hit(ServerLevel level, ServerPlayer player, LivingEntity target, float damage) {
		target.hurtServer(level, player.damageSources().playerAttack(player), damage);
	}

	static void push(Entity target, double x, double y, double z) {
		target.push(x, y, z);
		target.hurtMarked = true;
	}
}
