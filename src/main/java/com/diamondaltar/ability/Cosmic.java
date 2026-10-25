package com.diamondaltar.ability;

import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Palette et formes du thème galactique / lunaire : croissants de lune, étoiles, nébuleuses. */
final class Cosmic {
	static final DustParticleOptions MOON = new DustParticleOptions(0xF2F6FF, 2.2f);
	static final DustParticleOptions MOON_SMALL = new DustParticleOptions(0xDDE8FF, 1.2f);
	static final DustParticleOptions STARLIGHT = new DustParticleOptions(0xA8C8FF, 1.6f);
	static final DustParticleOptions NEBULA = new DustParticleOptions(0x8C4BFF, 1.8f);
	static final DustParticleOptions NEBULA_PINK = new DustParticleOptions(0xFF5FD2, 1.6f);
	static final DustParticleOptions VOID = new DustParticleOptions(0x1A0F40, 2.4f);
	static final DustColorTransitionOptions MOON_TO_NEBULA = new DustColorTransitionOptions(0xE8F0FF, 0x7A3CFF, 1.8f);
	static final DustColorTransitionOptions NEBULA_TO_PINK = new DustColorTransitionOptions(0x6A3CFF, 0xFF5FD2, 1.6f);
	static final DustColorTransitionOptions STAR_TO_GOLD = new DustColorTransitionOptions(0xFFFFFF, 0xFFC94A, 1.4f);

	private Cosmic() {
	}

	static void at(ServerLevel level, ParticleOptions p, Vec3 v) {
		level.sendParticles(p, v.x, v.y, v.z, 1, 0, 0, 0, 0);
	}

	/** Une étoile qui scintille (point lumineux + petites étincelles). */
	static void star(ServerLevel level, Vec3 v) {
		level.sendParticles(ParticleTypes.END_ROD, v.x, v.y, v.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, v.x, v.y, v.z, 1, 0.05, 0.05, 0.05, 0);
	}

	/** Un nuage de nébuleuse violet/rose. */
	static void nebula(ServerLevel level, Vec3 v, double spread, int count) {
		level.sendParticles(NEBULA_TO_PINK, v.x, v.y, v.z, count, spread, spread, spread, 0);
		level.sendParticles(MOON_TO_NEBULA, v.x, v.y, v.z, Math.max(1, count / 2), spread, spread, spread, 0);
	}

	/** Étoiles au hasard dans une boule de rayon {@code radius}. */
	static void starfield(ServerLevel level, Vec3 c, double radius, int count) {
		RandomSource r = level.getRandom();
		for (int i = 0; i < count; i++) {
			Vec3 d = new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5).normalize().scale(r.nextDouble() * radius);
			if (r.nextInt(3) == 0) {
				star(level, c.add(d));
			} else {
				at(level, MOON_SMALL, c.add(d));
			}
		}
	}

	/**
	 * Dessine un croissant de lune dans le plan (u, v) : bord extérieur sur un cercle de rayon {@code radius},
	 * épais au milieu (dans la direction {@code v}) et pointu aux extrémités.
	 */
	static void crescent(ServerLevel level, Vec3 center, Vec3 u, Vec3 v, double radius, double halfAngle, double thickness) {
		double arc = 2 * halfAngle * radius;
		int steps = (int) Math.max(12, arc / 0.35);
		for (int i = 0; i <= steps; i++) {
			double a = -halfAngle + 2 * halfAngle * i / steps;
			double w = thickness * Math.cos(a / halfAngle * Math.PI / 2);
			Vec3 dir = v.scale(Math.cos(a)).add(u.scale(Math.sin(a)));
			// Bord extérieur brillant.
			at(level, MOON, center.add(dir.scale(radius)));
			if (i % 3 == 0) {
				level.sendParticles(ParticleTypes.END_ROD, center.x + dir.x * radius, center.y + dir.y * radius, center.z + dir.z * radius, 1, 0, 0, 0, 0.01);
			}
			// Intérieur : dégradé lune → nébuleuse.
			for (double d = 0.3; d < w; d += 0.35) {
				at(level, d < w * 0.5 ? STARLIGHT : MOON_TO_NEBULA, center.add(dir.scale(radius - d)));
			}
			if (i % 4 == 0) {
				at(level, NEBULA, center.add(dir.scale(radius - w)));
			}
		}
	}

	/** Une ligne d'étoiles entre deux points (pour les constellations et les rayons). */
	static void line(ServerLevel level, ParticleOptions p, Vec3 a, Vec3 b, double step) {
		double len = a.distanceTo(b);
		for (double d = 0; d <= len; d += step) {
			at(level, p, a.lerp(b, len == 0 ? 0 : d / len));
		}
	}

	/** Coquille sphérique (répartition de Fibonacci). */
	static void sphere(ServerLevel level, ParticleOptions p, Vec3 c, double radius, int points) {
		double golden = Math.PI * (3 - Math.sqrt(5));
		for (int i = 0; i < points; i++) {
			double y = 1 - (i / (double) (points - 1)) * 2;
			double r = Math.sqrt(1 - y * y);
			double t = golden * i;
			at(level, p, c.add(Math.cos(t) * r * radius, y * radius, Math.sin(t) * r * radius));
		}
	}
}
