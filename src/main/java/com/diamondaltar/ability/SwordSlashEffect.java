package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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
	private static final double TILT = Math.toRadians(18);
	private static final float DAMAGE = 30.0f;
	private static final DustParticleOptions CYAN = new DustParticleOptions(0x66F2FF, 2.0f);
	private static final DustParticleOptions WHITE = new DustParticleOptions(0xFFFFFF, 1.5f);

	private final ServerPlayer player;
	private final Vec3 origin;
	private final Vec3 forward;
	private final Vec3 right;
	private final Set<UUID> alreadyHit = new HashSet<>();
	private final Display.ItemDisplay blade;

	public SwordSlashEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.origin = player.getEyePosition().subtract(0, 0.4, 0);
		this.forward = FX.forward(player);
		this.right = FX.right(forward);

		// L'épée spectrale géante qui balaie l'arc (avec la texture de l'épée infusée du joueur).
		this.blade = track(Visuals.item(level, origin, player.getMainHandItem()));
		Visuals.transform(blade, bladeOffset(-HALF_ANGLE, 1.5), bladeRotation(-HALF_ANGLE), 1.5f, 0);
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, 20, 1, false, false));

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.0f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.6f, 1.8f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 1.6f);
	}

	private Vec3 dirAt(double a) {
		return forward.scale(Math.cos(a)).add(right.scale(Math.sin(a)));
	}

	private Vector3f bladeOffset(double a, double distance) {
		Vec3 d = dirAt(a).scale(distance * 0.5);
		return new Vector3f((float) d.x, (float) (Math.sin(a) * distance * 0.09), (float) d.z);
	}

	private Quaternionf bladeRotation(double a) {
		return Visuals.pointing(dirAt(a), 0f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved()) {
			return true;
		}
		double distance = Math.min(RANGE, 1.5 + (RANGE - 1.5) * (age / (double) (DURATION - 1)));

		double sweep = -HALF_ANGLE + 2 * HALF_ANGLE * Math.min(1.0, (age + 1) / (double) (DURATION - 2));
		Visuals.transform(blade, bladeOffset(sweep, distance), bladeRotation(sweep), (float) (distance * 0.85), 1);

		// Le croissant de lune : bord argenté, intérieur qui vire au violet nébuleuse, légèrement incliné.
		Vec3 up = new Vec3(0, 1, 0);
		Vec3 tiltedRight = right.scale(Math.cos(TILT)).add(up.scale(Math.sin(TILT)));
		Cosmic.crescent(level, origin, tiltedRight, forward, distance, HALF_ANGLE, Math.max(0.6, distance * 0.28));
		if (age % 2 == 0) {
			Cosmic.starfield(level, origin.add(forward.scale(distance * 0.8)), distance * 0.5, 10);
		}
		if (age == DURATION - 1) {
			// Les éclats de lune retombent en poussière d'étoiles.
			Cosmic.nebula(level, origin.add(forward.scale(distance)), 2.0, 30);
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
		return age >= DURATION + 1;
	}
}
