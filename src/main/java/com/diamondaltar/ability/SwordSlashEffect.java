package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

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
	private static final DustParticleOptions CYAN = new DustParticleOptions(0x66F2FF, 2.0f);
	private static final DustParticleOptions WHITE = new DustParticleOptions(0xFFFFFF, 1.5f);

	private final ServerPlayer player;
	private final Vec3 origin;
	private final Vec3 forward;
	private final Vec3 right;
	private final Set<UUID> alreadyHit = new HashSet<>();

	public SwordSlashEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.origin = player.getEyePosition().subtract(0, 0.4, 0);
		this.forward = FX.forward(player);
		this.right = FX.right(forward);

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.0f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.6f, 1.8f);
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
			Vec3 dir = forward.scale(Math.cos(a)).add(right.scale(Math.sin(a)));
			double tilt = Math.sin(a) * distance * 0.18;
			Vec3 p = origin.add(dir.scale(distance)).add(0, tilt, 0);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
			if (i % 2 == 0) {
				level.sendParticles(CYAN, p.x, p.y + 0.15, p.z, 1, 0.05, 0.05, 0.05, 0);
				level.sendParticles(WHITE, p.x, p.y - 0.15, p.z, 1, 0.05, 0.05, 0.05, 0);
			}
			if (i % 4 == 0) {
				level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.2);
			}
		}

		// Dégâts : tout ce qui est dans le cône, jusqu'à la distance atteinte par le croissant.
		for (LivingEntity target : FX.livingAround(level, player, origin, distance + 1.0)) {
			Vec3 flat = new Vec3(target.getX() - origin.x, 0, target.getZ() - origin.z);
			if (flat.lengthSqr() < 1.0E-4 || alreadyHit.contains(target.getUUID())) {
				continue;
			}
			if (flat.normalize().dot(forward) >= Math.cos(HALF_ANGLE)) {
				alreadyHit.add(target.getUUID());
				FX.hit(level, player, target, DAMAGE);
				FX.push(target, forward.x * 1.2, 0.35, forward.z * 1.2);
				level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY(0.5), target.getZ(),
						15, 0.3, 0.4, 0.3, 0.3);
			}
		}

		if (age == 3) {
			level.playSound(null, origin.x, origin.y, origin.z,
					SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.5f, 0.6f);
		}
		return age >= DURATION - 1;
	}
}
