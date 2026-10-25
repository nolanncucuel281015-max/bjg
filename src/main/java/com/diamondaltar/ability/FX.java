package com.diamondaltar.ability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** Petits utilitaires partagés par les capacités. */
final class FX {
	private FX() {
	}

	/** Direction horizontale du regard du joueur (normalisée). */
	static Vec3d forward(ServerPlayerEntity player) {
		float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
		return new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
	}

	/** Direction horizontale à droite du joueur. */
	static Vec3d right(Vec3d forward) {
		return new Vec3d(-forward.z, 0, forward.x);
	}

	static List<LivingEntity> livingAround(ServerWorld world, ServerPlayerEntity player, Vec3d center, double radius) {
		Box box = new Box(center, center).expand(radius);
		return world.getEntitiesByClass(LivingEntity.class, box,
				e -> e != player && e.isAlive() && !e.isSpectator() && e.squaredDistanceTo(center) <= radius * radius);
	}

	static void hit(ServerPlayerEntity player, LivingEntity target, float damage) {
		target.damage(player.getDamageSources().playerAttack(player), damage);
	}

	static void push(Entity target, double x, double y, double z) {
		target.addVelocity(x, y, z);
		target.velocityModified = true;
	}
}
