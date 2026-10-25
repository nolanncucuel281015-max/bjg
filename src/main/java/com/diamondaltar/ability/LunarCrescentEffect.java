package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Épée — « Croissant Lunaire » : tu lances un immense croissant de lune vertical qui fend l'air
 * sur 32 blocs en grandissant, transperce tout et laisse une traînée de poussière d'étoiles.
 */
public class LunarCrescentEffect extends TimedEffect {
	private static final int DURATION = 20;
	private static final double SPEED = 1.6;
	private static final float DAMAGE = 32.0f;

	private final ServerPlayer player;
	private final Vec3 dir;
	private final Vec3 side;
	private final Vec3 up;
	private Vec3 pos;
	private final Set<UUID> hit = new HashSet<>();

	public LunarCrescentEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.dir = player.getLookAngle();
		Vec3 flatRight = FX.right(FX.forward(player));
		this.side = flatRight;
		this.up = flatRight.cross(dir).normalize().scale(-1);
		this.pos = player.getEyePosition().subtract(0, 0.2, 0).subtract(dir.scale(1.0));
		level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.0f, 0.4f);
		level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.5f, 0.6f);
	}

	@Override
	protected boolean tick() {
		pos = pos.add(dir.scale(SPEED));
		double radius = 2.0 + age * 0.12;
		// Croissant dressé verticalement, bombé vers l'avant (plan side/up, tourné vers dir).
		Vec3 center = pos.subtract(dir.scale(radius * 0.6));
		for (int layer = 0; layer < 2; layer++) {
			Vec3 c = center.add(dir.scale(layer * 0.25));
			drawVertical(c, radius);
		}
		if (age % 2 == 0) {
			Glow.crescent(level, center, up, dir, side, radius, Math.toRadians(70), Glow.Tint.NEBULA);
		}
		Cosmic.starfield(level, pos.subtract(dir.scale(2)), 1.8, 6);
		for (LivingEntity target : FX.livingAround(level, player, pos, radius)) {
			if (hit.add(target.getUUID())) {
				FX.hit(level, player, target, DAMAGE);
				FX.push(target, dir.x * 1.2, 0.4, dir.z * 1.2);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2));
				Cosmic.nebula(level, target.position().add(0, 1, 0), 0.4, 12);
				level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY(0.5), target.getZ(), 14, 0.2, 0.2, 0.2, 0.2);
			}
		}
		if (age % 4 == 0) {
			level.playSound(null, BlockPos.containing(pos), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, 0.6f + age * 0.05f);
		}
		return age >= DURATION;
	}

	/** Le croissant : on le dessine dans le plan (side, up) en le faisant bomber vers l'avant. */
	private void drawVertical(Vec3 c, double radius) {
		double half = Math.toRadians(70);
		int steps = (int) (2 * half * radius / 0.3);
		for (int i = 0; i <= steps; i++) {
			double a = -half + 2 * half * i / steps;
			double w = radius * 0.32 * Math.cos(a / half * Math.PI / 2);
			Vec3 outer = dir.scale(Math.cos(a) * radius).add(up.scale(Math.sin(a) * radius));
			Vec3 tangentOut = dir.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
			Cosmic.at(level, Cosmic.MOON, c.add(outer));
			for (double d = 0.3; d < w; d += 0.3) {
				Cosmic.at(level, d < w * 0.5 ? Cosmic.STARLIGHT : Cosmic.MOON_TO_NEBULA, c.add(outer).subtract(tangentOut.scale(d)));
			}
			if (i % 4 == 0) {
				Cosmic.star(level, c.add(outer));
			}
		}
	}
}
