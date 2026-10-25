package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Épée — « Tranche de la Volonté » : un coup balayé comme le coup normal de l'épée,
 * mais en croissant géant qui s'étend jusqu'à 10 blocs devant le joueur.
 */
public class SwordSlashEffect extends TimedEffect {
	private static final int DURATION = 8;
	private static final double RANGE = 10.0;
	private static final double HALF_ANGLE = Math.toRadians(65);
	private static final float DAMAGE = 16.0f;
	private static final DustParticleEffect CYAN = new DustParticleEffect(new Vector3f(0.4f, 0.95f, 1.0f), 2.0f);
	private static final DustParticleEffect WHITE = new DustParticleEffect(new Vector3f(1.0f, 1.0f, 1.0f), 1.5f);

	private final ServerPlayerEntity player;
	private final Vec3d origin;
	private final Vec3d forward;
	private final Vec3d right;
	private final Set<UUID> alreadyHit = new HashSet<>();

	public SwordSlashEffect(ServerWorld world, ServerPlayerEntity player) {
		super(world);
		this.player = player;
		this.origin = player.getEyePos().subtract(0, 0.4, 0);
		this.forward = FX.forward(player);
		this.right = FX.right(forward);

		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 2.0f, 0.5f);
		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.6f, 1.8f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved()) {
			return true;
		}
		double distance = 1.5 + (RANGE - 1.5) * (age / (double) (DURATION - 1));

		// Le croissant : arc horizontal légèrement incliné (comme un vrai coup d'épée en diagonale).
		double arcLength = 2 * HALF_ANGLE * distance;
		int points = (int) Math.max(8, arcLength / 0.55);
		for (int i = 0; i <= points; i++) {
			double a = -HALF_ANGLE + 2 * HALF_ANGLE * i / points;
			Vec3d dir = forward.multiply(Math.cos(a)).add(right.multiply(Math.sin(a)));
			double tilt = Math.sin(a) * distance * 0.18;
			Vec3d p = origin.add(dir.multiply(distance)).add(0, tilt, 0);
			world.spawnParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
			if (i % 2 == 0) {
				world.spawnParticles(CYAN, p.x, p.y + 0.15, p.z, 1, 0.05, 0.05, 0.05, 0);
				world.spawnParticles(WHITE, p.x, p.y - 0.15, p.z, 1, 0.05, 0.05, 0.05, 0);
			}
			if (i % 4 == 0) {
				world.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.2);
			}
		}

		// Dégâts : tout ce qui est dans le cône, jusqu'à la distance atteinte par le croissant.
		for (LivingEntity target : FX.livingAround(world, player, origin, distance + 1.0)) {
			Vec3d to = target.getPos().subtract(origin.x, target.getY(), origin.z);
			Vec3d flat = new Vec3d(to.x, 0, to.z);
			if (flat.lengthSquared() < 1.0E-4 || alreadyHit.contains(target.getUuid())) {
				continue;
			}
			if (flat.normalize().dotProduct(forward) >= Math.cos(HALF_ANGLE)) {
				alreadyHit.add(target.getUuid());
				FX.hit(player, target, DAMAGE);
				FX.push(target, forward.x * 1.2, 0.35, forward.z * 1.2);
				world.spawnParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getBodyY(0.5), target.getZ(),
						15, 0.3, 0.4, 0.3, 0.3);
			}
		}

		if (age == 3) {
			world.playSound(null, origin.x, origin.y, origin.z,
					SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.5f, 0.6f);
		}
		return age >= DURATION - 1;
	}
}
