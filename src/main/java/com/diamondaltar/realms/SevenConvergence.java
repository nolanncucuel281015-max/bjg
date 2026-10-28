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
 * Épée des Sept Royaumes — « Convergence des Sept Royaumes », sixième attaque :
 * <ol>
 * <li><b>Portails</b> : sept portails ronds, un par royaume (couleur du royaume), s'ouvrent en cercle dans le ciel
 * autour du bloc visé ;</li>
 * <li><b>Visée</b> : chaque portail tire un fin laser de sa couleur vers le bloc visé ;</li>
 * <li><b>Convergence</b> : les sept lasers deviennent énormes et tournent autour du point d'impact, où une sphère
 * de lumière grossit et pulvérise tout ce qui approche ;</li>
 * <li><b>Rupture</b> : la sphère éclate — onde de choc aux sept couleurs jusqu'au bout de la portée et
 * <b>quatre fois les dégâts choisis</b>.</li>
 * </ol>
 * Les joueurs ne sont jamais touchés ; la bedrock et les coffres ne sont jamais détruits.
 */
public final class SevenConvergence extends TimedEffect {
	private static final int OPEN = 30;
	private static final int AIM = 25;
	private static final int CONVERGE = 50;
	private static final int BURST = 45;
	private static final double CIRCLE = 40;
	private static final double HEIGHT = 45;
	private static final float PORTAL_SIZE = 9f;
	private static final int PORTAL_RING = 10;
	private static final int WAVE = 28;

	private final ServerPlayer player;
	private final int rangeLevel;
	private final double radius;
	private final float damage;
	private final int damageLevel;
	private final Vec3 target;
	private final Set<UUID> hit = new HashSet<>();
	private final Set<UUID> burstHit = new HashSet<>();
	private final Display.ItemDisplay[] portals = new Display.ItemDisplay[7];
	private final Display.BlockDisplay[][] portalRings = new Display.BlockDisplay[7][PORTAL_RING];
	private final Display.BlockDisplay[] beams = new Display.BlockDisplay[7];
	private final Display.BlockDisplay[] wave = new Display.BlockDisplay[WAVE];
	private Display.ItemDisplay core;
	private Display.BlockDisplay sky;
	private double carved;

	public SevenConvergence(ServerLevel level, ServerPlayer player, int rangeLevel, int damageLevel) {
		super(level);
		this.player = player;
		this.rangeLevel = SkyFx.clamp(rangeLevel);
		this.radius = SkyFx.range(this.rangeLevel);
		this.damage = SkyFx.damage(damageLevel) * 4;
		this.damageLevel = damageLevel;
		Vec3 ahead = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(30));
		this.target = SkyFx.aim(level, player, ahead, radius);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, OPEN + AIM + CONVERGE + BURST, 4, false, false));
		level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 4f, 0.5f);
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 4f, 0.5f);
	}

	/** Recharge : deux fois et demie celle de l'Anéantissement Céleste. */
	public static int cooldown(int rangeLevel) {
		return CelestialAnnihilation.cooldown(rangeLevel) * 5 / 2;
	}

	public Vec3 impactPoint() {
		return target;
	}

	public int hitCount() {
		return hit.size();
	}

	@Override
	protected boolean tick() {
		if (age < OPEN) {
			open(age);
		} else if (age < OPEN + AIM) {
			aim(age - OPEN);
		} else if (age < OPEN + AIM + CONVERGE) {
			converge(age - OPEN - AIM);
		} else if (age < OPEN + AIM + CONVERGE + BURST) {
			burst(age - OPEN - AIM - CONVERGE);
		} else {
			return true;
		}
		return false;
	}

	/** Position du portail i, qui tourne lentement (de plus en plus vite pendant la convergence). */
	private Vec3 portal(int i, double spin) {
		double a = spin + Math.PI * 2 * i / 7;
		return target.add(Math.cos(a) * CIRCLE, HEIGHT, Math.sin(a) * CIRCLE);
	}

	private double spin;

	private void movePortals(float size, double speed) {
		spin += speed;
		for (int i = 0; i < 7; i++) {
			Vec3 p = portal(i, spin);
			portals[i].setPos(p);
			SkyFx.resize(portals[i], size, 2);
			SkyFx.ring(portalRings[i], p, size * 0.65, spin * 3 + i, 1.2f, 2);
		}
	}

	// ================================================================ 1. sept portails

	private void open(int t) {
		if (portals[0] == null) {
			for (int i = 0; i < 7; i++) {
				portals[i] = track(SkyFx.sphere(level, portal(i, 0), RealmContent.ORB[i], 0.1f));
				for (int k = 0; k < PORTAL_RING; k++) {
					portalRings[i][k] = track(SkyFx.laser(level, portal(i, 0), i));
				}
			}
		}
		float p = (float) (1 - Math.pow(1 - (t + 1) / (double) OPEN, 3));
		movePortals(PORTAL_SIZE * p, 0.01);
		if (t % 4 == 0) {
			int i = (t / 4) % 7;
			Vec3 at = portal(i, spin);
			level.sendParticles(SkyFx.glow(i), at.x, at.y, at.z, 30, 3, 3, 3, 0.2);
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 4f, 0.5f + i * 0.15f);
		}
	}

	// ================================================================ 2. les portails visent

	private void aim(int t) {
		if (beams[0] == null) {
			for (int i = 0; i < 7; i++) {
				beams[i] = track(SkyFx.laser(level, portal(i, spin), i));
			}
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 4f, 0.6f);
		}
		float p = (t + 1) / (float) AIM;
		movePortals(PORTAL_SIZE, 0.01 + p * 0.02);
		for (int i = 0; i < 7; i++) {
			Vec3 from = portal(i, spin);
			// Le laser part du portail et s'allonge vers le bloc visé.
			Vec3 to = from.add(target.subtract(from).scale(Math.min(1, p * 1.6)));
			beams[i].setPos(from);
			SkyFx.beam(beams[i], from, to, 0.4f + p * 0.6f, 1);
		}
		SkyFx.particleRing(level, ModParticles.GLOW_WHITE, target.add(0, 0.5, 0), 8 * (1 - p) + 1, 24, t * 0.3);
		if (t % 6 == 0) {
			SkyFx.shake(level, target, radius + 100, 0.3f + p * 0.4f, 8);
		}
	}

	// ================================================================ 3. convergence

	private void converge(int t) {
		float p = (t + 1) / (float) CONVERGE;
		if (core == null) {
			core = track(SkyFx.sphere(level, target.add(0, 2, 0), RealmContent.ORB[LaserBlock.WHITE], 1f));
			sky = track(SkyFx.laser(level, target, LaserBlock.WHITE));
			SkyFx.flash(level, target, radius + 150, 0xFFFFFF, 0.6f, 12);
			level.playSound(null, BlockPos.containing(target), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 6f, 0.5f);
		}
		movePortals(PORTAL_SIZE * (1 + p * 0.4f), 0.04 + p * 0.12);
		float width = (1f + rangeLevel * 0.15f) * (1 + p * 2f);
		for (int i = 0; i < 7; i++) {
			Vec3 from = portal(i, spin);
			beams[i].setPos(from);
			SkyFx.beam(beams[i], from, target, width, 1);
		}
		float pulse = 1 + 0.12f * (float) Math.sin(t * 1.3);
		SkyFx.resize(core, (6 + p * 22) * pulse, 1);
		sky.setPos(target);
		SkyFx.beam(sky, target, target.add(0, 160, 0), width * 1.5f, 1);
		// Le cratère se creuse sous la sphère.
		if (t < 14) {
			double r = (SkyFx.crater(rangeLevel) + 4) * (t + 1) / 14.0;
			SkyFx.carve(level, target, r, carved);
			carved = r;
		}
		level.sendParticles(ModParticles.GLOW_WHITE, target.x, target.y + 2, target.z, 16, 3 + p * 6, 3 + p * 6, 3 + p * 6, 0.3);
		if (t % 2 == 0) {
			// Tout ce qui s'approche de la sphère est pulvérisé.
			SkyFx.strike(level, player, target, 8 + p * 20, damage, hit);
		}
		if (t % 8 == 0) {
			level.playSound(null, BlockPos.containing(target), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 6f, 0.5f + p);
			SkyFx.shake(level, target, radius + 100, 0.6f + p, 10);
		}
	}

	// ================================================================ 4. rupture

	private void burst(int t) {
		if (t == 0) {
			GiantCrater.start(level, target, damageLevel);
			for (int i = 0; i < 7; i++) {
				beams[i].discard();
				portals[i].discard();
				for (Display.BlockDisplay d : portalRings[i]) {
					d.discard();
				}
			}
			sky.discard();
			for (int i = 0; i < WAVE; i++) {
				wave[i] = track(SkyFx.laser(level, target, i % 7));
			}
			BlockPos at = BlockPos.containing(target);
			level.playSound(null, at, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 8f, 0.4f);
			level.playSound(null, at, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 8f, 0.3f);
			level.playSound(null, at, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 6f, 0.4f);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, target.x, target.y + 2, target.z, 24, 10, 4, 10, 0);
			for (int i = 0; i < 7; i++) {
				level.sendParticles(SkyFx.glow(i), target.x, target.y + 3, target.z, 40, 8, 6, 8, 0.6);
			}
			SkyFx.flash(level, target, radius + 200, 0xFFFFFF, 1.0f, 30);
			SkyFx.shake(level, target, radius + 200, 2.5f + rangeLevel * 0.2f, 45);
		}
		float fade = t > BURST - 12 ? (BURST - t) / 12f : 1f;
		SkyFx.resize(core, (28 + t * 3f) * fade, 1);
		if (t % 2 == 1 && t != BURST - 1) {
			return; // onde et dégâts un tick sur deux : moins de lag
		}
		double reach = Math.min(radius, (t + 2) * radius / 24.0);
		SkyFx.ring(wave, target.add(0, 0.8, 0), reach, t * 0.06, (1.8f + rangeLevel * 0.3f) * fade, 2);
		int n = Math.max(8, Math.min(42, (int) (reach * 0.35)));
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n + t;
			level.sendParticles(i % 2 == 0 ? ParticleTypes.EXPLOSION : SkyFx.glow(i % 7),
					target.x + Math.cos(a) * reach, target.y + 1, target.z + Math.sin(a) * reach, 1, 0.5, 0.5, 0.5, 0);
		}
		if (t % 6 == 0) {
			level.playSound(null, BlockPos.containing(target), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 6f, 0.3f + t * 0.01f);
		}
		SkyFx.strike(level, player, target, reach, damage, burstHit);
		hit.addAll(burstHit);
	}
}
