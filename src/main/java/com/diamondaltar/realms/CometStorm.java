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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Épée des Sept Royaumes — « Pluie de Comètes », la cinquième attaque :
 * <ol>
 * <li><b>Faille</b> : un anneau géant aux couleurs des sept royaumes s'ouvre dans le ciel au-dessus du bloc visé,
 * sept orbes tournent autour ;</li>
 * <li><b>Pluie</b> : des dizaines de comètes (sphères avec une traînée laser) tombent de la faille et
 * <b>traquent chaque créature</b> dans la portée choisie ; chacune creuse un petit cratère ;</li>
 * <li><b>Comète Mère</b> : une comète gigantesque s'écrase sur le bloc visé — énorme cratère, onde de choc ronde
 * jusqu'au bout de la portée et <b>trois fois les dégâts choisis</b>.</li>
 * </ol>
 * Les joueurs ne sont jamais touchés ; la bedrock et les coffres ne sont jamais détruits.
 */
public final class CometStorm extends TimedEffect {
	private static final int RIFT = 30;
	private static final int RAIN = 80;
	private static final int MOTHER = 30;
	private static final int BOOM = 45;
	/** Durée de chute d'une comète. */
	private static final int FALL = 14;
	private static final double SKY = 70;
	private static final float RIFT_SIZE = 26f;
	private static final float MOTHER_SIZE = 30f;
	private static final int RING = 28;
	private static final int WAVE = 24;

	private final ServerPlayer player;
	private final int rangeLevel;
	private final double radius;
	private final float damage;
	private final Vec3 target;
	private final Vec3 rift;
	private final Set<UUID> hit = new HashSet<>();
	/** Les créatures déjà frappées par la Comète Mère (les comètes, elles, peuvent frapper plusieurs fois). */
	private final Set<UUID> boomHit = new HashSet<>();
	private final Display.BlockDisplay[] riftRing = new Display.BlockDisplay[RING];
	private final Display.ItemDisplay[] orbs = new Display.ItemDisplay[7];
	private final Display.BlockDisplay[] wave = new Display.BlockDisplay[WAVE];
	private final List<Comet> comets = new ArrayList<>();
	private Display.ItemDisplay mother;
	private Display.BlockDisplay motherTrail;
	private double carved;
	private int launched;

	/** Une comète en vol : une sphère colorée et sa traînée laser, de {@code from} vers {@code to}. */
	private static final class Comet {
		final Vec3 from;
		final Vec3 to;
		final int color;
		final Display.ItemDisplay ball;
		final Display.BlockDisplay trail;
		int t;

		Comet(Vec3 from, Vec3 to, int color, Display.ItemDisplay ball, Display.BlockDisplay trail) {
			this.from = from;
			this.to = to;
			this.color = color;
			this.ball = ball;
			this.trail = trail;
		}
	}

	public CometStorm(ServerLevel level, ServerPlayer player, int rangeLevel, int damageLevel) {
		super(level);
		this.player = player;
		this.rangeLevel = SkyFx.clamp(rangeLevel);
		this.radius = SkyFx.range(this.rangeLevel);
		this.damage = SkyFx.damage(damageLevel) * 3;
		Vec3 ahead = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(30));
		this.target = SkyFx.aim(level, player, ahead, radius);
		this.rift = target.add(0, SKY, 0);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, RIFT + RAIN + MOTHER + BOOM + FALL, 4, false, false));
		level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 4f, 0.6f);
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 4f, 0.4f);
	}

	/** Recharge : deux fois celle de l'Anéantissement Céleste. */
	public static int cooldown(int rangeLevel) {
		return CelestialAnnihilation.cooldown(rangeLevel) * 2;
	}

	public Vec3 impactPoint() {
		return target;
	}

	public int hitCount() {
		return hit.size();
	}

	@Override
	protected boolean tick() {
		flyComets();
		if (age < RIFT) {
			openRift(age);
		} else if (age < RIFT + RAIN) {
			rain(age - RIFT);
		} else if (age < RIFT + RAIN + MOTHER) {
			motherFalls(age - RIFT - RAIN);
		} else if (age < RIFT + RAIN + MOTHER + BOOM) {
			boom(age - RIFT - RAIN - MOTHER);
		} else {
			return comets.isEmpty();
		}
		return false;
	}

	// ================================================================ 1. la faille s'ouvre dans le ciel

	private void openRift(int t) {
		if (riftRing[0] == null) {
			for (int i = 0; i < RING; i++) {
				riftRing[i] = track(SkyFx.laser(level, rift, i % 7));
			}
			for (int i = 0; i < 7; i++) {
				orbs[i] = track(SkyFx.sphere(level, rift, RealmContent.ORB[i], 0.1f));
			}
			SkyFx.flash(level, rift, radius + 150, 0x221144, 0.5f, 30);
		}
		float p = (float) (1 - Math.pow(1 - (t + 1) / (double) RIFT, 3));
		spinRift(t, p);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, rift.x, rift.y, rift.z, 20, RIFT_SIZE * p * 0.5, 2, RIFT_SIZE * p * 0.5, 0.3);
		if (t % 8 == 0) {
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 4f, 0.4f + p);
			SkyFx.shake(level, rift, radius + 100, 0.3f + p * 0.4f, 10);
		}
	}

	/** L'anneau de la faille tourne, les sept orbes des royaumes gravitent autour. */
	private void spinRift(int t, float p) {
		SkyFx.ring(riftRing, rift, RIFT_SIZE * p, t * 0.08, 1.5f + p * 1.5f, 2);
		for (int i = 0; i < 7; i++) {
			double a = -t * 0.12 + i * Math.PI * 2 / 7;
			orbs[i].setPos(rift.add(Math.cos(a) * (RIFT_SIZE * p + 6), Math.sin(t * 0.2 + i) * 3, Math.sin(a) * (RIFT_SIZE * p + 6)));
			SkyFx.resize(orbs[i], 5f * p, 2);
		}
	}

	// ================================================================ 2. la pluie de comètes

	private void rain(int t) {
		spinRift(RIFT + t, 1f);
		if (t % 2 != 0) {
			return;
		}
		// Chaque comète traque une créature de la zone (la plus proche n'est pas forcément la première),
		// sinon elle tombe au hasard autour du bloc visé.
		List<LivingEntity> prey = t % 6 == 0 ? SkyFx.creatures(level, target, radius) : List.of();
		int volley = 1 + (int) Math.min(3, prey.size());
		for (int k = 0; k < volley; k++) {
			Vec3 to;
			if (!prey.isEmpty() && k < prey.size()) {
				to = prey.get((launched + k) % prey.size()).position();
			} else {
				double a = level.getRandom().nextDouble() * Math.PI * 2;
				double d = Math.sqrt(level.getRandom().nextDouble()) * Math.min(radius, 120);
				to = SkyFx.groundBelow(level, target.add(Math.cos(a) * d, 0, Math.sin(a) * d));
			}
			launch(to);
		}
		if (t % 10 == 0) {
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 3f, 0.4f);
		}
	}

	private void launch(Vec3 to) {
		int color = launched % 7;
		launched++;
		// Les comètes partent de la faille ; pour une proie lointaine, elles partent du ciel au-dessus d'elle.
		Vec3 from = to.distanceToSqr(target) < 200 * 200
				? rift.add((level.getRandom().nextDouble() - 0.5) * RIFT_SIZE, 0, (level.getRandom().nextDouble() - 0.5) * RIFT_SIZE)
				: to.add(20, SKY, 20);
		Display.ItemDisplay ball = track(SkyFx.sphere(level, from, RealmContent.ORB[color], 3.5f));
		Display.BlockDisplay trail = track(SkyFx.laser(level, from, color));
		comets.add(new Comet(from, to, color, ball, trail));
	}

	/** Les comètes en vol avancent ; à l'arrivée : explosion, petit cratère, dégâts autour. */
	private void flyComets() {
		for (int i = comets.size() - 1; i >= 0; i--) {
			Comet c = comets.get(i);
			c.t++;
			double p = Math.min(1, c.t / (double) FALL);
			Vec3 pos = c.from.add(c.to.subtract(c.from).scale(p * p));
			c.ball.setPos(pos);
			Vec3 tail = c.from.add(c.to.subtract(c.from).scale(Math.max(0, p * p - 0.25)));
			c.trail.setPos(tail);
			SkyFx.beam(c.trail, tail, pos, 1.2f, 1);
			if (c.t % 2 == 0) {
				level.sendParticles(SkyFx.glow(c.color), pos.x, pos.y, pos.z, 3, 0.6, 0.6, 0.6, 0.02);
			}
			if (c.t < FALL) {
				continue;
			}
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.to.x, c.to.y + 1, c.to.z, 1, 0, 0, 0, 0);
			level.sendParticles(SkyFx.glow(c.color), c.to.x, c.to.y + 1, c.to.z, 20, 2, 1, 2, 0.15);
			if (comets.size() < 6 || level.getRandom().nextInt(3) == 0) {
				level.playSound(null, BlockPos.containing(c.to), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 3f, 0.6f + level.getRandom().nextFloat() * 0.4f);
			}
			SkyFx.carve(level, c.to, 2.5, 0);
			// Chaque comète frappe à nouveau : une créature peut être touchée par plusieurs comètes.
			Set<UUID> once = new HashSet<>();
			SkyFx.strike(level, player, c.to, 5, damage / 3, once);
			hit.addAll(once);
			c.ball.discard();
			c.trail.discard();
			comets.remove(i);
		}
	}

	// ================================================================ 3. la Comète Mère

	private void motherFalls(int t) {
		float p = (t + 1) / (float) MOTHER;
		spinRift(RIFT + RAIN + t, 1f - p * 0.7f);
		if (mother == null) {
			mother = track(SkyFx.sphere(level, rift, RealmContent.ORB[LaserBlock.WHITE], 1f));
			motherTrail = track(SkyFx.laser(level, rift, 0));
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 5f, 0.3f);
		}
		Vec3 pos = rift.add(target.subtract(rift).scale(p * p));
		mother.setPos(pos);
		SkyFx.resize(mother, MOTHER_SIZE * Math.min(1f, p * 2.5f), 1);
		motherTrail.setPos(rift);
		SkyFx.beam(motherTrail, rift, pos, 6f * Math.min(1f, p * 2f), 1);
		level.sendParticles(ModParticles.GLOW_FIRE, pos.x, pos.y, pos.z, 12, 6, 6, 6, 0.1);
		if (t % 5 == 0) {
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, SoundSource.PLAYERS, 4f, 0.3f + p * 0.3f);
		}
	}

	// ================================================================ 4. l'impact

	private void boom(int t) {
		if (t == 0) {
			for (int i = 0; i < RING; i++) {
				riftRing[i].discard();
			}
			for (int i = 0; i < 7; i++) {
				orbs[i].discard();
			}
			motherTrail.discard();
			for (int i = 0; i < WAVE; i++) {
				wave[i] = track(SkyFx.laser(level, target, i % 2 == 0 ? LaserBlock.WHITE : i % 7));
			}
			mother.setPos(target);
			BlockPos at = BlockPos.containing(target);
			level.playSound(null, at, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 8f, 0.3f);
			level.playSound(null, at, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 8f, 0.4f);
			level.playSound(null, at, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 8f, 0.4f);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, target.x, target.y + 2, target.z, 16, 8, 3, 8, 0);
			SkyFx.flash(level, target, radius + 200, 0xFFE8C0, 1.0f, 30);
			SkyFx.shake(level, target, radius + 200, 2.0f + rangeLevel * 0.2f, 40);
		}
		float fade = t > BOOM - 12 ? (BOOM - t) / 12f : 1f;
		SkyFx.resize(mother, (MOTHER_SIZE + t * 2.5f) * fade, 1);
		if (t < 14) {
			double r = (SkyFx.crater(rangeLevel) + 8) * (t + 1) / 14.0;
			SkyFx.carve(level, target, r, carved);
			carved = r;
		}
		if (t % 2 == 1 && t != BOOM - 1) {
			return; // onde et dégâts un tick sur deux : moins de lag
		}
		double reach = Math.min(radius, (t + 2) * radius / 26.0);
		SkyFx.ring(wave, target.add(0, 0.8, 0), reach, t * 0.05, (1.6f + rangeLevel * 0.3f) * fade, 2);
		int n = Math.max(8, Math.min(40, (int) (reach * 0.35)));
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n + t;
			level.sendParticles(i % 2 == 0 ? ParticleTypes.EXPLOSION : SkyFx.glow(i % 7),
					target.x + Math.cos(a) * reach, target.y + 1, target.z + Math.sin(a) * reach, 1, 0.5, 0.5, 0.5, 0);
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, target.x, target.y + 4 + t * 0.6, target.z, 14, 4, 3, 4, 0.05);
		if (t % 6 == 0) {
			level.playSound(null, BlockPos.containing(target), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 6f, 0.3f + t * 0.01f);
		}
		SkyFx.strike(level, player, target, reach, damage, boomHit);
		hit.addAll(boomHit);
	}
}
