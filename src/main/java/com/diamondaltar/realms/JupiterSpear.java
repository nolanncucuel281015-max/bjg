package com.diamondaltar.realms;

import com.diamondaltar.ModParticles;
import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Épée des Sept Royaumes — « Lance de Jupiter » :
 * <ol>
 * <li>Jupiter (une sphère géante aux bandes qui tournent) apparaît dans le ciel, au-dessus de la cible ;
 * la Terre (une sphère bleue et verte) apparaît au-dessus du bloc visé ;</li>
 * <li>une lance d'énergie surpuissante naît près de Jupiter et tourne autour d'elle de plus en plus vite,
 * en laissant une traînée de lumière ;</li>
 * <li>la lance est projetée sur la Terre ;</li>
 * <li>énorme explosion : flash, cratère, onde de choc ronde et dégâts jusqu'à la portée choisie (touches Y et U).</li>
 * </ol>
 */
public final class JupiterSpear extends TimedEffect {
	private static final int APPEAR = 25;
	private static final int ORBIT = 70;
	private static final int THROW = 18;
	private static final int BOOM = 45;
	private static final float JUPITER_SIZE = 34f;
	private static final float EARTH_SIZE = 9f;
	private static final int RING = 28;

	private final ServerPlayer player;
	private final int rangeLevel;
	private final double radius;
	private final float damage;
	private final Vec3 target;
	private final Vec3 earth;
	private final Vec3 jupiter;
	private final Set<UUID> hit = new HashSet<>();
	private Display.ItemDisplay jupiterDisplay;
	private Display.ItemDisplay earthDisplay;
	private Display.ItemDisplay spear;
	private Display.BlockDisplay trail;
	private final Display.BlockDisplay[] ring = new Display.BlockDisplay[RING];
	private Vec3 spearPos;
	private Vec3 launchFrom;
	private double angle;
	private double carved;

	public JupiterSpear(ServerLevel level, ServerPlayer player, int rangeLevel, int damageLevel) {
		super(level);
		this.player = player;
		this.rangeLevel = SkyFx.clamp(rangeLevel);
		this.radius = SkyFx.range(this.rangeLevel);
		this.damage = SkyFx.damage(damageLevel);
		Vec3 ahead = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(25));
		this.target = SkyFx.aim(level, player, ahead);
		this.earth = target.add(0, EARTH_SIZE * 0.5 + 3, 0);
		// Jupiter : haut dans le ciel, entre le joueur et la cible, pour qu'on la voie bien.
		Vec3 mid = player.position().add(target).scale(0.5);
		this.jupiter = new Vec3(mid.x, Math.max(player.getY(), target.y) + 70, mid.z);
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3f, 0.4f);
		level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 3f, 0.5f);
	}

	public static int cooldown(int rangeLevel) {
		return CelestialAnnihilation.cooldown(rangeLevel);
	}

	public Vec3 impactPoint() {
		return target;
	}

	public int hitCount() {
		return hit.size();
	}

	@Override
	protected boolean tick() {
		if (age < APPEAR) {
			appear(age);
		} else if (age < APPEAR + ORBIT) {
			orbit(age - APPEAR);
		} else if (age < APPEAR + ORBIT + THROW) {
			fly(age - APPEAR - ORBIT);
		} else if (age < APPEAR + ORBIT + THROW + BOOM) {
			boom(age - APPEAR - ORBIT - THROW);
		} else {
			return true;
		}
		return false;
	}

	// ================================================================ 1. les planètes apparaissent

	private void appear(int t) {
		if (jupiterDisplay == null) {
			jupiterDisplay = track(SkyFx.sphere(level, jupiter, RealmContent.JUPITER, 0.1f));
			earthDisplay = track(SkyFx.sphere(level, earth, RealmContent.EARTH, 0.1f));
			SkyFx.flash(level, jupiter, 250, 0x1A0A00, 0.45f, 40);
		}
		float p = (float) (1 - Math.pow(1 - (t + 1) / (double) APPEAR, 3));
		SkyFx.resize(jupiterDisplay, JUPITER_SIZE * p, 1);
		SkyFx.resize(earthDisplay, EARTH_SIZE * p, 1);
		SkyFx.particleRing(level, ModParticles.GLOW_GOLD, jupiter, JUPITER_SIZE * 0.6 * p, 24, t * 0.1);
		level.sendParticles(ModParticles.GLOW_ICE, earth.x, earth.y, earth.z, 4, EARTH_SIZE * 0.4, EARTH_SIZE * 0.4, EARTH_SIZE * 0.4, 0.02);
		if (t % 6 == 0) {
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 3f, 0.4f + t * 0.03f);
		}
	}

	// ================================================================ 2. la lance tourne autour de Jupiter

	private Vec3 orbitPos(double a) {
		double r = JUPITER_SIZE * 0.85;
		// orbite inclinée : elle passe devant et derrière Jupiter
		return jupiter.add(Math.cos(a) * r, Math.sin(a) * r * 0.35, Math.sin(a) * r);
	}

	private void orbit(int t) {
		float p = t / (float) ORBIT;
		angle += 0.06 + p * p * 0.45;
		Vec3 pos = orbitPos(angle);
		Vec3 next = orbitPos(angle + 0.05);
		if (spear == null) {
			spear = track(Visuals.item(level, pos, new ItemStack(RealmContent.JUPITER_SPEAR)));
			spear.setGlowingTag(true);
			spear.setGlowColorOverride(0xFFD860);
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 4f, 0.5f);
		}
		spearPos = pos;
		spear.setPos(pos);
		Visuals.transform(spear, new Vector3f(), Visuals.pointing(next.subtract(pos), 0), 7f + p * 3f, 1);
		// traînée de lumière
		for (int k = 0; k < 6; k++) {
			Vec3 back = orbitPos(angle - k * 0.08);
			level.sendParticles(k % 2 == 0 ? ModParticles.GLOW_GOLD : ModParticles.GLOW_WHITE, back.x, back.y, back.z, 2, 0.4, 0.4, 0.4, 0);
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 6, 1, 1, 1, 0.3);
		if (t % 10 == 0) {
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2f, 0.6f + p);
			SkyFx.shake(level, player.position(), 120, 0.1f + p * 0.4f, 10);
		}
	}

	// ================================================================ 3. la lance file vers la Terre

	private void fly(int t) {
		if (launchFrom == null) {
			launchFrom = spearPos;
			trail = track(SkyFx.laser(level, launchFrom, LaserBlock.WHITE));
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS, 4f, 0.5f);
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3f, 1.2f);
		}
		double p = Math.pow((t + 1) / (double) THROW, 2.2);
		Vec3 pos = launchFrom.add(earth.subtract(launchFrom).scale(p));
		Vec3 dir = earth.subtract(launchFrom);
		spear.setPos(pos);
		Visuals.transform(spear, new Vector3f(), Visuals.pointing(dir, 0), 12f, 1);
		// la traînée : un laser rond qui suit la lance
		Vec3 tail = pos.subtract(dir.normalize().scale(Math.min(40, dir.length() * p)));
		trail.setPos(tail);
		SkyFx.beam(trail, tail, pos, 1.6f);
		for (int k = 0; k < 10; k++) {
			Vec3 q = tail.add(pos.subtract(tail).scale(level.getRandom().nextDouble()));
			level.sendParticles(ModParticles.GLOW_GOLD, q.x, q.y, q.z, 1, 0.5, 0.5, 0.5, 0);
		}
	}

	// ================================================================ 4. l'explosion

	private void boom(int t) {
		if (t == 0) {
			if (spear != null) {
				spear.discard();
				spear = null;
			}
			if (trail != null) {
				trail.discard();
				trail = null;
			}
			for (int i = 0; i < RING; i++) {
				ring[i] = track(SkyFx.laser(level, target, i % 2 == 0 ? LaserBlock.WHITE : 6));
			}
			BlockPos at = BlockPos.containing(target);
			level.playSound(null, at, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 6f, 0.3f);
			level.playSound(null, at, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 6f, 0.4f);
			level.playSound(null, at, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 6f, 0.4f);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, target.x, target.y + 2, target.z, 8 + rangeLevel * 3, 3 + rangeLevel, 2, 3 + rangeLevel, 0);
			level.sendParticles(ParticleTypes.SONIC_BOOM, target.x, target.y + 3, target.z, 10, 4, 2, 4, 0);
			for (int i = 0; i < 6; i++) {
				LightningBolt bolt = new LightningBolt(com.diamondaltar.Compat.LIGHTNING_BOLT, level);
				double a = Math.PI * 2 * i / 6;
				bolt.setPos(target.x + Math.cos(a) * 5, target.y, target.z + Math.sin(a) * 5);
				bolt.setVisualOnly(true);
				level.addFreshEntity(bolt);
			}
			SkyFx.flash(level, target, radius + 120, 0xFFF2C0, 0.9f, 25);
			SkyFx.shake(level, target, radius + 120, 1.0f + rangeLevel * 0.3f, 30 + rangeLevel * 4);
		}
		float fade = t > BOOM - 12 ? (BOOM - t) / 12f : 1f;
		// La Terre éclate dans une boule de lumière, Jupiter s'éloigne.
		if (earthDisplay != null) {
			if (t < 8) {
				SkyFx.resize(earthDisplay, EARTH_SIZE * (1 + t * 0.5f), 1);
			} else {
				earthDisplay.discard();
				earthDisplay = null;
			}
		}
		SkyFx.resize(jupiterDisplay, JUPITER_SIZE * fade, 1);
		// Cratère, onde de choc ronde et dégâts.
		if (t < 14) {
			double r = (SkyFx.crater(rangeLevel) + 2) * (t + 1) / 14.0;
			SkyFx.carve(level, target, r, carved);
			carved = r;
		}
		double reach = Math.min(radius, (t + 2) * radius / 28.0);
		if (t % 2 == 1 && t != BOOM - 1) {
			return; // l'onde et les dégâts : un tick sur deux (moins de lag), l'anneau glisse en douceur entre les deux
		}
		SkyFx.ring(ring, target.add(0, 0.8, 0), reach, t * 0.04, (1.2f + rangeLevel * 0.35f) * fade, 2);
		int n = Math.max(8, Math.min(36, (int) (reach * 0.35)));
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n + t;
			level.sendParticles(i % 3 == 0 ? ParticleTypes.EXPLOSION : ModParticles.GLOW_FIRE,
					target.x + Math.cos(a) * reach, target.y + 1, target.z + Math.sin(a) * reach, 1, 0.4, 0.4, 0.4, 0);
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, target.x, target.y + 4 + t * 0.4, target.z, 12, 3, 2, 3, 0.05);
		level.sendParticles(ModParticles.GLOW_FIRE, target.x, target.y + 2 + t * 0.6, target.z, 8, 2, 2, 2, 0.05);
		if (t % 6 == 0) {
			level.playSound(null, BlockPos.containing(target), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 5f, 0.4f + t * 0.01f);
		}
		SkyFx.strike(level, player, target, reach, damage, hit);
	}
}
