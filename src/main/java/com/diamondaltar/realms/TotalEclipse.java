package com.diamondaltar.realms;

import com.diamondaltar.ModParticles;
import com.diamondaltar.effect.TimedEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Épée des Sept Royaumes — « Éclipse Totale », la plus forte de toutes :
 * <ol>
 * <li><b>Alignement</b> : un soleil géant apparaît haut dans le ciel au-dessus du bloc visé, une lune noire glisse
 * devant lui ; le ciel s'assombrit et la couronne (anneau de lumière) s'allume autour ;</li>
 * <li><b>Couronne</b> : éclipse totale — la couronne tourne de plus en plus vite puis tire un rayon colossal
 * (blanc à cœur rouge) sur le bloc visé ;</li>
 * <li><b>Rupture</b> : douze lasers balaient le sol en tournant depuis l'impact, des piliers de lumière jaillissent
 * partout dans la zone, onde de choc jusqu'au bout de la portée et <b>six fois les dégâts choisis</b>.</li>
 * </ol>
 * Les joueurs ne sont jamais touchés ; la bedrock et les coffres ne sont jamais détruits.
 */
public final class TotalEclipse extends TimedEffect {
	private static final int ALIGN = 35;
	private static final int CORONA = 30;
	private static final int RUPTURE = 55;
	private static final double HIGH = 70;
	private static final float SUN_SIZE = 40f;
	private static final int RING = 24;
	private static final int SWEEP = 12;
	private static final int PILLARS = 10;
	private static final int WAVE = 24;

	private final ServerPlayer player;
	private final int rangeLevel;
	private final double radius;
	private final float damage;
	private final int damageLevel;
	private final Vec3 target;
	private final Vec3 eclipse;
	private final Set<UUID> hit = new HashSet<>();
	private final Set<UUID> ruptureHit = new HashSet<>();
	private final Display.BlockDisplay[] corona = new Display.BlockDisplay[RING];
	private final Display.BlockDisplay[] sweep = new Display.BlockDisplay[SWEEP];
	private final Display.BlockDisplay[] pillars = new Display.BlockDisplay[PILLARS];
	private final Display.BlockDisplay[] wave = new Display.BlockDisplay[WAVE];
	private final Display.BlockDisplay[] ray = new Display.BlockDisplay[2];
	private Display.ItemDisplay sun;
	private Display.ItemDisplay moon;
	private double carved;

	public TotalEclipse(ServerLevel level, ServerPlayer player, int rangeLevel, int damageLevel) {
		super(level);
		this.player = player;
		this.rangeLevel = SkyFx.clamp(rangeLevel);
		this.radius = SkyFx.range(this.rangeLevel);
		this.damage = SkyFx.damage(damageLevel) * 6;
		this.damageLevel = damageLevel;
		Vec3 ahead = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(30));
		this.target = SkyFx.aim(level, player, ahead, radius);
		this.eclipse = target.add(0, HIGH, 0);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ALIGN + CORONA + RUPTURE, 4, false, false));
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 4f, 0.3f);
	}

	/** Recharge : trois fois et demie celle de l'Anéantissement Céleste. */
	public static int cooldown(int rangeLevel) {
		return CelestialAnnihilation.cooldown(rangeLevel) * 7 / 2;
	}

	public Vec3 impactPoint() {
		return target;
	}

	public int hitCount() {
		return hit.size();
	}

	@Override
	protected boolean tick() {
		if (age < ALIGN) {
			align(age);
		} else if (age < ALIGN + CORONA) {
			corona(age - ALIGN);
		} else if (age < ALIGN + CORONA + RUPTURE) {
			rupture(age - ALIGN - CORONA);
		} else {
			return true;
		}
		return false;
	}

	// ================================================================ 1. alignement

	private void align(int t) {
		if (sun == null) {
			sun = track(SkyFx.sphere(level, eclipse, RealmContent.SUN, 0.1f));
			moon = track(SkyFx.sphere(level, eclipse.add(60, 0, 0), RealmContent.BLACK_HOLE, SUN_SIZE * 0.9f));
			for (int i = 0; i < RING; i++) {
				corona[i] = track(SkyFx.laser(level, eclipse, i % 3 == 0 ? 6 : LaserBlock.WHITE));
			}
		}
		float p = (t + 1) / (float) ALIGN;
		SkyFx.resize(sun, SUN_SIZE * (float) Math.min(1, p * 2.5), 1);
		// La lune glisse devant le soleil (un peu plus près du joueur pour le cacher).
		double ease = 1 - Math.pow(1 - p, 2);
		moon.setPos(eclipse.add(60 * (1 - ease), -0.5, 0));
		// La couronne s'allume quand la lune couvre le soleil.
		SkyFx.ring(corona, eclipse, SUN_SIZE * 0.55 * Math.max(0.05, ease), t * 0.05, 0.4f + (float) ease * 2f, 1);
		if (t == ALIGN / 2) {
			SkyFx.flash(level, target, radius + 200, 0x000000, 0.7f, ALIGN + CORONA);
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 5f, 0.3f);
		}
		if (t % 8 == 0) {
			SkyFx.shake(level, target, radius + 100, 0.3f + p * 0.5f, 10);
		}
	}

	// ================================================================ 2. la couronne tire

	private void corona(int t) {
		float p = (t + 1) / (float) CORONA;
		double spin = 1.75 + t * (0.1 + p * 0.5);
		SkyFx.ring(corona, eclipse, SUN_SIZE * 0.55 + Math.sin(t * 0.8) * 1.5, spin, 2.4f + p * 2f, 1);
		level.sendParticles(ModParticles.GLOW_GOLD, eclipse.x, eclipse.y, eclipse.z, 20, SUN_SIZE * 0.3, SUN_SIZE * 0.3, 2, 0.2);
		if (t == CORONA / 2) {
			// Le rayon colossal : blanc, avec un cœur rouge.
			ray[0] = track(SkyFx.laser(level, eclipse, LaserBlock.WHITE));
			ray[1] = track(SkyFx.laser(level, eclipse, 0));
			SkyFx.flash(level, target, radius + 200, 0xFF2020, 0.8f, 12);
			level.playSound(null, BlockPos.containing(target), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 8f, 0.4f);
		}
		if (ray[0] != null) {
			float q = (t - CORONA / 2 + 1) / (float) (CORONA - CORONA / 2);
			Vec3 tip = eclipse.add(target.subtract(eclipse).scale(Math.min(1, q * 2)));
			SkyFx.beam(ray[0], eclipse, tip, 6f + q * 10f, 1);
			SkyFx.beam(ray[1], eclipse, tip, 3f + q * 5f, 1);
			if (q >= 0.5f) {
				SkyFx.strike(level, player, target, 10, damage, hit);
				level.sendParticles(ParticleTypes.EXPLOSION, target.x, target.y + 1, target.z, 4, 4, 1, 4, 0);
			}
		}
		if (t % 5 == 0) {
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 5f, 0.5f + p * 0.5f);
			SkyFx.shake(level, target, radius + 100, 0.8f + p, 8);
		}
	}

	// ================================================================ 3. rupture

	private void rupture(int t) {
		if (t == 0) {
			GiantCrater.start(level, target, damageLevel);
			for (int i = 0; i < SWEEP; i++) {
				sweep[i] = track(SkyFx.laser(level, target, i % 2 == 0 ? 0 : LaserBlock.WHITE));
			}
			for (int i = 0; i < PILLARS; i++) {
				pillars[i] = track(SkyFx.laser(level, target, i % 7));
			}
			for (int i = 0; i < WAVE; i++) {
				wave[i] = track(SkyFx.laser(level, target, i % 2 == 0 ? 0 : 6));
			}
			BlockPos at = BlockPos.containing(target);
			level.playSound(null, at, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 10f, 0.3f);
			level.playSound(null, at, SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 6f, 0.4f);
			level.playSound(null, at, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 10f, 0.3f);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, target.x, target.y + 2, target.z, 30, 12, 4, 12, 0);
			SkyFx.flash(level, target, radius + 300, 0xFFFFFF, 1.0f, 35);
			SkyFx.shake(level, target, radius + 300, 3f + rangeLevel * 0.2f, 60);
		}
		float fade = t > RUPTURE - 12 ? (RUPTURE - t) / 12f : 1f;
		// Le rayon et l'éclipse s'éteignent doucement.
		SkyFx.resize(sun, SUN_SIZE * fade, 1);
		SkyFx.resize(moon, SUN_SIZE * 0.9f * fade, 1);
		SkyFx.ring(corona, eclipse, SUN_SIZE * 0.55, t * 0.6, 4f * fade, 1);
		SkyFx.beam(ray[0], eclipse, target, 16f * fade, 1);
		SkyFx.beam(ray[1], eclipse, target, 8f * fade, 1);
		if (t < 14) {
			double r = (SkyFx.crater(rangeLevel) + 8) * (t + 1) / 14.0;
			SkyFx.carve(level, target, r, carved);
			carved = r;
		}
		if (t % 2 == 1 && t != RUPTURE - 1) {
			return; // balayage, piliers et dégâts un tick sur deux : moins de lag
		}
		double reach = Math.min(radius, (t + 2) * radius / 22.0);
		Vec3 base = target.add(0, 0.8, 0);
		// Douze lasers qui balaient le sol en tournant.
		for (int i = 0; i < SWEEP; i++) {
			double a = t * 0.09 + Math.PI * 2 * i / SWEEP;
			Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
			sweep[i].setPos(base);
			SkyFx.beam(sweep[i], base, base.add(dir.scale(Math.min(reach, SkyFx.VISUAL_MAX))), (1.2f + rangeLevel * 0.2f) * fade, 2);
			if (SkyFx.trench(rangeLevel) > 0 && fade > 0.5f) {
				SkyFx.plough(level, target, dir, SkyFx.crater(rangeLevel), reach, SkyFx.trench(rangeLevel));
			}
		}
		// Des piliers de lumière jaillissent un peu partout.
		for (int i = 0; i < PILLARS; i++) {
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			double d = level.getRandom().nextDouble() * Math.min(reach, SkyFx.VISUAL_MAX);
			Vec3 foot = SkyFx.groundBelow(level, target.add(Math.cos(a) * d, 0, Math.sin(a) * d));
			pillars[i].setPos(foot);
			SkyFx.beam(pillars[i], foot, foot.add(0, 40 + level.getRandom().nextInt(40), 0), 2.5f * fade, 1);
			level.sendParticles(ParticleTypes.EXPLOSION, foot.x, foot.y + 1, foot.z, 1, 0.5, 0.5, 0.5, 0);
		}
		SkyFx.ring(wave, base, reach, t * 0.05, (2f + rangeLevel * 0.3f) * fade, 2);
		if (t % 6 == 0) {
			level.playSound(null, BlockPos.containing(target), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 8f, 0.3f + t * 0.01f);
		}
		SkyFx.strike(level, player, target, reach, damage, ruptureHit);
		hit.addAll(ruptureHit);
	}
}
