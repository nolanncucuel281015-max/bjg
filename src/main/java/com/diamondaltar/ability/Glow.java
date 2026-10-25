package com.diamondaltar.ability;

import com.diamondaltar.effect.EffectManager;
import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bandes de lumière éblouissantes (comme un trait de lumière d'anime) faites de block_display
 * très fins et très longs avec un contour brillant : traits, faisceaux, rayons, éclairs d'impact.
 */
final class Glow {
	/** Couleurs : verre teinté pour le halo + couleur du contour lumineux. */
	enum Tint {
		WHITE(Blocks.WHITE_STAINED_GLASS, 0xFFFFFF, Halos.WHITE),
		ICE(Blocks.LIGHT_BLUE_STAINED_GLASS, 0xBFF6FF, Halos.ICE),
		NEBULA(Blocks.PURPLE_STAINED_GLASS, 0xC080FF, Halos.NEBULA),
		PINK(Blocks.MAGENTA_STAINED_GLASS, 0xFF80E0, Halos.PINK),
		GOLD(Blocks.YELLOW_STAINED_GLASS, 0xFFE070, Halos.GOLD),
		FIRE(Blocks.ORANGE_STAINED_GLASS, 0xFFA040, Halos.FIRE),
		VOID(Blocks.BLACK_STAINED_GLASS, 0x9050FF, Halos.NEBULA);

		final Block glass;
		final int color;
		final ParticleOptions halo;

		Tint(Block glass, int color, ParticleOptions halo) {
			this.glass = glass;
			this.color = color;
			this.halo = halo;
		}
	}

	private static final DustParticleOptions SPARK = new DustParticleOptions(0xFFFFFF, 2.2f);
	/** Limite de bandes en même temps, pour ne pas faire ramer le jeu. */
	private static final int MAX_LIVE = 260;
	private static int live;
	private static final Map<UUID, Long> LAST_FLASH = new HashMap<>();

	private Glow() {
	}

	/**
	 * Un ruban lumineux qui s'étire en {@code grow} ticks, reste {@code hold} ticks, puis s'amincit
	 * et s'étire encore en {@code fade} ticks avant de disparaître.
	 */
	static final class Ribbon extends TimedEffect {
		private final Display.BlockDisplay d;
		private final Vec3 dir;
		private final Vec3 facing;
		private final float length;
		private final float width;
		private final int grow;
		private final int hold;
		private final int fade;

		Ribbon(ServerLevel level, Vec3 center, Vec3 dir, Vec3 facing, float length, float width, Block block, int glow, int grow, int hold, int fade) {
			super(level);
			this.dir = dir;
			this.facing = facing;
			this.length = length;
			this.width = width;
			this.grow = Math.max(1, grow);
			this.hold = hold;
			this.fade = Math.max(1, fade);
			this.d = track(Visuals.ribbon(level, center, block.defaultBlockState(), glow));
			Visuals.ribbonShape(d, dir, facing, length * 0.05f, width * 0.2f, 0);
			live++;
		}

		@Override
		protected boolean tick() {
			if (age == 0) {
				Visuals.ribbonShape(d, dir, facing, length, width, grow);
			} else if (age == grow + hold) {
				Visuals.ribbonShape(d, dir, facing, length * 1.25f, 0.003f, fade);
			}
			if (age >= grow + hold + fade) {
				live--;
				return true;
			}
			return false;
		}
	}

	/** Halo flou le long d'une ligne (le « bloom » autour des bandes). */
	static void haloLine(ServerLevel level, Vec3 a, Vec3 b, double step, Tint tint) {
		double len = a.distanceTo(b);
		for (double d = 0; d <= len; d += step) {
			Vec3 p = a.lerp(b, len == 0 ? 0 : d / len);
			level.sendParticles(d % (step * 3) < step ? Halos.WHITE : tint.halo, true, true, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
		}
	}

	static void halo(ServerLevel level, Vec3 p, int count, double spread, Tint tint) {
		level.sendParticles(tint.halo, true, true, p.x, p.y, p.z, count, spread, spread, spread, 0.01);
		level.sendParticles(Halos.WHITE, true, true, p.x, p.y, p.z, Math.max(1, count / 2), spread * 0.5, spread * 0.5, spread * 0.5, 0.005);
	}

	private static boolean full() {
		return live >= MAX_LIVE;
	}

	static void ribbon(ServerLevel level, Vec3 center, Vec3 dir, Vec3 facing, float length, float width, Block block, int glow, int grow, int hold, int fade) {
		if (!full() && dir.lengthSqr() > 1.0E-6) {
			EffectManager.start(new Ribbon(level, center, dir, facing, length, width, block, glow, grow, hold, fade));
		}
	}

	/** Le trait complet : cœur blanc brillant + bande colorée + large halo translucide. */
	static void slash(ServerLevel level, Vec3 center, Vec3 dir, Vec3 facing, float length, float width, Tint tint) {
		ribbon(level, center, dir, facing, length * 1.05f, width * 3.2f, Blocks.WHITE_STAINED_GLASS, -1, 2, 2, 8);
		ribbon(level, center, dir, facing, length, width * 1.6f, tint.glass, tint.color, 2, 2, 8);
		ribbon(level, center, dir, facing, length * 0.95f, width * 0.45f, Blocks.WHITE_CONCRETE, 0xFFFFFF, 2, 2, 8);
		Vec3 nd = dir.normalize().scale(length / 2.0);
		haloLine(level, center.subtract(nd), center.add(nd), 0.45, tint);
		double half = length / 2.0;
		for (double s = -half; s <= half; s += 0.5) {
			Vec3 p = center.add(dir.normalize().scale(s));
			level.sendParticles(SPARK, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
			if (((int) (s * 2)) % 3 == 0) {
				level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.08);
			}
		}
	}

	/** Un faisceau visible sous tous les angles : deux rubans croisés de {@code from} à {@code to}. */
	static void beam(ServerLevel level, Vec3 from, Vec3 to, float width, Tint tint, int hold) {
		Vec3 dir = to.subtract(from);
		float len = (float) dir.length();
		if (len < 0.1) {
			return;
		}
		Vec3 n = dir.normalize();
		Vec3 a = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 f1 = n.cross(a).normalize();
		Vec3 f2 = n.cross(f1).normalize();
		Vec3 mid = from.lerp(to, 0.5);
		haloLine(level, from, to, Math.max(0.5, len / 40.0), tint);
		for (Vec3 f : new Vec3[]{f1, f2}) {
			ribbon(level, mid, n, f, len, width * 2.2f, tint.glass, tint.color, 1, hold, 6);
			ribbon(level, mid, n, f, len, width * 0.6f, Blocks.WHITE_CONCRETE, 0xFFFFFF, 1, hold, 6);
		}
	}

	/** Une colonne de lumière qui jaillit du sol. */
	static void pillar(ServerLevel level, Vec3 base, float height, float width, Tint tint) {
		beam(level, base, base.add(0, height, 0), width, tint, 3);
	}

	/** Des rayons de lumière qui partent dans toutes les directions horizontales. */
	static void rays(ServerLevel level, Vec3 center, int count, float length, Tint tint) {
		for (int i = 0; i < count; i++) {
			double a = Math.PI * 2 * i / count;
			Vec3 dir = new Vec3(Math.cos(a), 0.15 * Math.sin(i * 1.7), Math.sin(a));
			Vec3 mid = center.add(dir.scale(length / 2.0));
			ribbon(level, mid, dir, new Vec3(0, 1, 0), length, 0.5f, tint.glass, tint.color, 2, 1, 7);
			haloLine(level, center, center.add(dir.scale(length)), 0.8, tint);
			ribbon(level, mid, dir, new Vec3(0, 1, 0), length * 0.9f, 0.15f, Blocks.WHITE_CONCRETE, 0xFFFFFF, 2, 1, 7);
		}
	}

	/** Croissant lumineux fait de segments de rubans le long d'un arc (dans le plan u, v). {@code facing} null = tourné vers l'extérieur. */
	static void crescent(ServerLevel level, Vec3 center, Vec3 u, Vec3 v, Vec3 facing, double radius, double halfAngle, Tint tint) {
		int segs = 9;
		for (int i = 0; i < segs; i++) {
			double a0 = -halfAngle + 2 * halfAngle * i / segs;
			double a1 = -halfAngle + 2 * halfAngle * (i + 1) / segs;
			Vec3 p0 = center.add(v.scale(Math.cos(a0) * radius)).add(u.scale(Math.sin(a0) * radius));
			Vec3 p1 = center.add(v.scale(Math.cos(a1) * radius)).add(u.scale(Math.sin(a1) * radius));
			double am = (a0 + a1) / 2;
			float w = (float) (0.25 + 0.9 * Math.cos(am / halfAngle * Math.PI / 2)) * (float) Math.max(1, radius / 6);
			Vec3 mid = p0.lerp(p1, 0.5);
			Vec3 seg = p1.subtract(p0);
			Vec3 f = facing != null ? facing : mid.subtract(center).normalize();
			float len = (float) seg.length() * 1.15f;
			ribbon(level, mid, seg, f, len, w * 1.8f, tint.glass, tint.color, 2, 2, 8);
			ribbon(level, mid, seg, f, len, w * 0.5f, Blocks.WHITE_CONCRETE, 0xFFFFFF, 2, 2, 8);
			haloLine(level, p0, p1, 0.5, tint);
		}
	}

	/** Éclair d'impact sur une cible (rayures croisées + colonne + contour blanc), au plus 1 fois / 5 ticks. */
	static void hitFlash(ServerLevel level, ServerPlayer player, LivingEntity target) {
		long now = level.getGameTime();
		Long last = LAST_FLASH.get(target.getUUID());
		if (last != null && now - last < 5) {
			return;
		}
		LAST_FLASH.put(target.getUUID(), now);
		if (LAST_FLASH.size() > 512) {
			LAST_FLASH.entrySet().removeIf(e -> now - e.getValue() > 40);
		}
		target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 12, 0, false, false));
		Vec3 look = player.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
		if (right.lengthSqr() < 1.0E-4) {
			right = new Vec3(1, 0, 0);
		}
		Vec3 mid = target.position().add(0, target.getBbHeight() * 0.55, 0);
		float size = Math.max(2.5f, target.getBbWidth() * 3f);
		ribbon(level, mid.add(0, 0.2, 0), right.add(0, 0.25, 0), look, size, 0.16f, Blocks.WHITE_CONCRETE, 0xFFFFFF, 1, 2, 6);
		ribbon(level, mid.add(0, -0.25, 0), right.add(0, -0.2, 0), look, size * 1.2f, 0.4f, Blocks.LIGHT_BLUE_STAINED_GLASS, 0xBFF6FF, 1, 2, 6);
		pillar(level, target.position(), target.getBbHeight() + 3.5f, 0.14f, Tint.ICE);
		level.sendParticles(ParticleTypes.END_ROD, mid.x, mid.y, mid.z, 12, 0.25, 0.35, 0.25, 0.2);
		level.sendParticles(SPARK, mid.x, mid.y, mid.z, 10, 0.35, 0.45, 0.35, 0);
		halo(level, mid, 10, 0.4, Tint.ICE);
	}
}
