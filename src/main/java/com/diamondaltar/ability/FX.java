package com.diamondaltar.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
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

	/** Le point visé par le joueur (bloc ou point dans le vide à {@code range} blocs). */
	static Vec3 target(ServerPlayer player, double range) {
		HitResult hit = player.pick(range, 0f, false);
		return hit.getType() == HitResult.Type.MISS
				? player.getEyePosition().add(player.getLookAngle().scale(range))
				: hit.getLocation();
	}

	/** Descend depuis {@code pos} jusqu'au premier sol solide (max 8 blocs). */
	static Vec3 ground(ServerLevel level, Vec3 pos) {
		BlockPos p = BlockPos.containing(pos);
		for (int i = 0; i < 8; i++) {
			if (solid(level, p.below())) {
				return new Vec3(pos.x, p.getY(), pos.z);
			}
			p = p.below();
		}
		return pos;
	}

	static boolean solid(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !state.getCollisionShape(level, pos).isEmpty();
	}

	/** Bloc cassable par les capacités de minage (pioche/pelle, pas la bedrock, zone autorisée). */
	static boolean canMine(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		float hardness = state.getDestroySpeed(level, pos);
		return !state.isAir() && hardness >= 0 && hardness < 50
				&& (state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL))
				&& player.mayBuild() && level.mayInteract(player, pos);
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

	static void pull(Entity target, Vec3 to, double strength) {
		Vec3 d = to.subtract(target.position());
		if (d.lengthSqr() > 0.25) {
			Vec3 v = d.normalize().scale(strength);
			target.setDeltaMovement(target.getDeltaMovement().scale(0.5).add(v));
			target.hurtMarked = true;
		}
	}

	/** Anneau horizontal de particules. */
	static void ring(ServerLevel level, net.minecraft.core.particles.ParticleOptions particle, Vec3 c, double radius, int points) {
		for (int i = 0; i < points; i++) {
			double a = Math.PI * 2 * i / points;
			level.sendParticles(particle, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
		}
	}
}
