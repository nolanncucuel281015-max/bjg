package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Épée — « Tranchant Céleste » : un trait de lumière blanche éblouissant traverse l'écran en
 * diagonale ; les ennemis touchés sont illuminés, zébrés de rayures de lumière et une colonne
 * de lumière jaillit sur eux.
 */
public class CelestialEdgeEffect extends TimedEffect {
	private static final float LENGTH = 18f;
	private static final double DISTANCE = 5.5;
	private static final float DAMAGE = 34f;
	private static final DustParticleOptions WHITE = new DustParticleOptions(0xFFFFFF, 3.5f);
	private static final DustParticleOptions ICE = new DustParticleOptions(0xBFF6FF, 2.5f);

	private final ServerPlayer player;
	private final Vec3 look;
	private final Vec3 dir;
	private final Vec3 center;
	private final Display.BlockDisplay core;
	private final Display.BlockDisplay glow;
	private final Display.BlockDisplay halo;
	private final Set<UUID> hit = new HashSet<>();

	/** Les rayures de lumière qui restent sur les ennemis touchés. */
	private record Streak(Display.BlockDisplay display, Vec3 dir, int born, float length) {
	}

	private final List<Streak> streaks = new ArrayList<>();

	public CelestialEdgeEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.look = player.getLookAngle();
		Vec3 right = FX.right(FX.forward(player));
		Vec3 up = right.cross(look).normalize().scale(-1);
		// Diagonale : du haut-gauche vers le bas-droite, comme un coup d'épée.
		double tilt = Math.toRadians(-22);
		this.dir = right.scale(Math.cos(tilt)).add(up.scale(Math.sin(tilt))).normalize();
		this.center = player.getEyePosition().add(look.scale(DISTANCE)).subtract(0, 0.4, 0);

		this.halo = track(Visuals.ribbon(level, center, Blocks.WHITE_STAINED_GLASS.defaultBlockState(), -1));
		this.glow = track(Visuals.ribbon(level, center, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), 0xBFF6FF));
		this.core = track(Visuals.ribbon(level, center, Blocks.WHITE_CONCRETE.defaultBlockState(), 0xFFFFFF));
		for (Display.BlockDisplay d : new Display.BlockDisplay[]{halo, glow, core}) {
			Visuals.ribbonShape(d, dir, look, 0.5f, 0.05f, 0);
		}

		level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.0f, 1.7f);
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0f, 2.0f);
		level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.5f, 1.4f);
	}

	@Override
	protected boolean tick() {
		// Ruban principal : il s'étire d'un coup (2 ticks) puis s'amincit et disparaît.
		if (age == 0) {
			Visuals.ribbonShape(halo, dir, look, LENGTH * 1.05f, 1.3f, 2);
			Visuals.ribbonShape(glow, dir, look, LENGTH, 0.55f, 2);
			Visuals.ribbonShape(core, dir, look, LENGTH * 0.95f, 0.18f, 2);
		} else if (age == 4) {
			Visuals.ribbonShape(halo, dir, look, LENGTH * 1.3f, 0.01f, 8);
			Visuals.ribbonShape(glow, dir, look, LENGTH * 1.2f, 0.01f, 8);
			Visuals.ribbonShape(core, dir, look, LENGTH * 1.1f, 0.01f, 8);
		} else if (age == 13) {
			halo.discard();
			glow.discard();
			core.discard();
		}

		if (age <= 3) {
			// Le trait : particules très denses le long de la diagonale, plus épaisses au centre.
			double half = LENGTH / 2.0;
			for (double d = -half; d <= half; d += 0.25) {
				double w = 1 - Math.abs(d) / half;
				Vec3 p = center.add(dir.scale(d));
				level.sendParticles(WHITE, p.x, p.y, p.z, 1, 0.02, 0.05 * w, 0.02, 0);
				if (w > 0.3) {
					level.sendParticles(ICE, p.x, p.y, p.z, 1, 0.05, 0.15 * w, 0.05, 0);
				}
				if (((int) (d * 4)) % 3 == 0) {
					level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.12 * w);
				}
			}
			level.sendParticles(ParticleTypes.FIREWORK, center.x, center.y, center.z, 20, LENGTH * 0.25, 0.4, LENGTH * 0.25, 0.05);
			Glow.haloLine(level, center.subtract(dir.scale(LENGTH / 2.0)), center.add(dir.scale(LENGTH / 2.0)), 0.35, Glow.Tint.ICE);

			for (LivingEntity t : FX.livingAround(level, player, center, LENGTH / 2.0 + 1)) {
				Vec3 mid = t.position().add(0, t.getBbHeight() * 0.5, 0);
				if (distanceToLine(mid) < 2.2 + t.getBbWidth() && hit.add(t.getUUID())) {
					strike(t, mid);
				}
			}
		}

		// Les rayures de lumière sur les ennemis touchés s'étirent puis s'effacent.
		for (Streak s : streaks) {
			int life = age - s.born();
			if (life == 1) {
				Visuals.ribbonShape(s.display(), s.dir(), look, s.length(), 0.12f, 2);
			} else if (life == 6) {
				Visuals.ribbonShape(s.display(), s.dir(), look, s.length() * 1.4f, 0.005f, 7);
			}
		}
		return age >= 22;
	}

	private void strike(LivingEntity t, Vec3 mid) {
		FX.hit(level, player, t, DAMAGE);
		FX.push(t, look.x * 0.6, 0.25, look.z * 0.6);
		// Illuminé : contour blanc.
		t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20, 0, false, false));
		// Éclair de lumière sur la cible.
		level.sendParticles(ParticleTypes.END_ROD, mid.x, mid.y, mid.z, 25, 0.3, 0.5, 0.3, 0.25);
		level.sendParticles(WHITE, mid.x, mid.y, mid.z, 20, 0.4, 0.6, 0.4, 0);
		Glow.halo(level, mid, 18, 0.5, Glow.Tint.WHITE);
		// Colonne de lumière qui jaillit.
		for (double y = 0; y < 5; y += 0.3) {
			level.sendParticles(y < 2 ? WHITE : ICE, t.getX(), t.getY() + y, t.getZ(), 1, 0.08, 0, 0.08, 0);
		}
		// 2 rayures de lumière en travers de la cible.
		for (int k = 0; k < 2; k++) {
			Vec3 sdir = dir.add(0, (k == 0 ? 0.15 : -0.1), 0).normalize();
			Vec3 at = mid.add(0, (k - 0.5) * 0.5, 0);
			Display.BlockDisplay d = track(Visuals.ribbon(level, at, Blocks.WHITE_CONCRETE.defaultBlockState(), 0xFFFFFF));
			Visuals.ribbonShape(d, sdir, look, 0.2f, 0.02f, 0);
			streaks.add(new Streak(d, sdir, age, 3.5f + k));
		}
		level.playSound(null, t.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.5f, 1.8f);
		FX.shake(level, mid, 10, 0.35f, 4);
		FX.flash(level, player.position(), 16, 0xFFFFFF, 0.32f, 6);
	}

	private double distanceToLine(Vec3 p) {
		Vec3 rel = p.subtract(center);
		double along = rel.dot(dir);
		if (Math.abs(along) > LENGTH / 2.0 + 0.5) {
			return Double.MAX_VALUE;
		}
		// Distance perpendiculaire, en ignorant la profondeur le long du regard (le trait "coupe" l'écran).
		Vec3 perp = rel.subtract(dir.scale(along));
		Vec3 perpNoDepth = perp.subtract(look.scale(perp.dot(look)));
		double depth = Math.abs(perp.dot(look));
		return depth > 6 ? Double.MAX_VALUE : perpNoDepth.length();
	}
}
