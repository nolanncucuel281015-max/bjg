package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * Masse — « Chute Météore » : le joueur s'envole très haut puis retombe comme une
 * météorite, provoquant une énorme onde de choc à l'impact (sans dégâts de chute).
 */
public class MaceMeteorEffect extends TimedEffect {
	private static final int MAX_DURATION = 200;
	private static final double IMPACT_RADIUS = 7.0;

	private final ServerPlayerEntity player;
	private boolean falling;

	public MaceMeteorEffect(ServerWorld world, ServerPlayerEntity player) {
		super(world);
		this.player = player;
		Vec3d f = FX.forward(player);
		player.setVelocity(f.x * 0.6, 2.2, f.z * 0.6);
		player.velocityModified = true;
		world.spawnParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY(), player.getZ(), 3, 0.5, 0.1, 0.5, 0);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 1.5f, 0.8f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved() || !player.isAlive()) {
			return true;
		}
		// Pas de dégâts de chute pendant toute la capacité.
		player.fallDistance = 0;

		if (!falling && age > 8 && player.getVelocity().y <= 0.05) {
			// Au sommet : on plonge vers le sol.
			falling = true;
			Vec3d f = FX.forward(player);
			player.setVelocity(f.x * 0.4, -2.5, f.z * 0.4);
			player.velocityModified = true;
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.5f, 0.5f);
		}

		Vec3d p = player.getPos();
		if (falling) {
			world.spawnParticles(ParticleTypes.FLAME, p.x, p.y + 1, p.z, 8, 0.3, 0.6, 0.3, 0.02);
			world.spawnParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y + 1.5, p.z, 3, 0.2, 0.4, 0.2, 0.01);
			world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y + 1, p.z, 2, 0.3, 0.3, 0.3, 0.05);
		} else {
			world.spawnParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 3, 0.2, 0.1, 0.2, 0.02);
		}

		if (falling && (player.isOnGround() || player.isTouchingWater())) {
			impact(p);
			return true;
		}
		return age >= MAX_DURATION;
	}

	private void impact(Vec3d c) {
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
		for (int ring = 1; ring <= 3; ring++) {
			double r = ring * IMPACT_RADIUS / 3;
			int points = (int) (r * 10);
			for (int i = 0; i < points; i++) {
				double a = Math.PI * 2 * i / points;
				world.spawnParticles(ParticleTypes.FLAME, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r,
						1, 0, 0.05, 0, 0.02);
				world.spawnParticles(ParticleTypes.LAVA, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r,
						0, 0, 0, 0, 0);
			}
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 2.0f, 0.5f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.PLAYERS, 1.5f, 0.7f);

		for (LivingEntity target : FX.livingAround(world, player, c, IMPACT_RADIUS)) {
			double dist = Math.sqrt(target.squaredDistanceTo(c));
			float damage = (float) (22.0 * (1.0 - dist / (IMPACT_RADIUS + 1)));
			FX.hit(player, target, Math.max(4.0f, damage));
			Vec3d out = target.getPos().subtract(c).multiply(1, 0, 1).normalize().multiply(1.6);
			FX.push(target, out.x, 0.9, out.z);
			target.setOnFireFor(3);
		}
	}
}
