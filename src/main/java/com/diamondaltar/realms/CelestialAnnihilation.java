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
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Épée des Sept Royaumes — « Anéantissement Céleste » :
 * <ol>
 * <li><b>Envol</b> : une colonne de lumière soulève le joueur dans une spirale aux 7 couleurs ;</li>
 * <li><b>Charge</b> : une sphère d'énergie grossit dans sa main, 7 petites sphères (une par royaume) tournent autour et
 * s'y fondent, la lumière converge vers elle, et un cercle-cible suit le bloc que le joueur vise ;</li>
 * <li><b>Tir</b> : un laser rond gigantesque part de la main du joueur jusqu'au bloc visé, une colonne de lumière
 * monte au ciel au point d'impact, un cratère se creuse, une onde de choc ronde et 7 lasers ronds tournants détruisent
 * tout jusqu'à la portée choisie (touche Y, jusqu'à 200 blocs), avec les dégâts choisis (touche U) ;</li>
 * <li><b>Fin</b> : tout s'éteint, le joueur redescend en douceur.</li>
 * </ol>
 * Les joueurs ne sont jamais touchés ; la bedrock et les coffres (et autres blocs à contenu) ne sont jamais détruits.
 */
public final class CelestialAnnihilation extends TimedEffect {
	private static final int RISE = 20;
	private static final int CHARGE = 40;
	private static final int FIRE = 44;
	private static final int RING = 40;
	private static final int TARGET = 24;

	private final ServerPlayer player;
	private final int rangeLevel;
	private final double radius;
	private final float damage;
	private final float width;
	private final double height;
	private final Vec3 start;
	private Vec3 impact;
	private final Set<UUID> hit = new HashSet<>();
	private Display.BlockDisplay lift;
	private Display.ItemDisplay orb;
	private final Display.ItemDisplay[] shards = new Display.ItemDisplay[7];
	private final Display.BlockDisplay[] target = new Display.BlockDisplay[TARGET];
	private final Display.BlockDisplay[] beam = new Display.BlockDisplay[3];
	private Display.BlockDisplay sky;
	private final Display.BlockDisplay[] ring = new Display.BlockDisplay[RING];
	private final Display.BlockDisplay[] lasers = new Display.BlockDisplay[7];
	private double carved;

	public CelestialAnnihilation(ServerLevel level, ServerPlayer player, int rangeLevel, int damageLevel) {
		super(level);
		this.player = player;
		this.rangeLevel = SkyFx.clamp(rangeLevel);
		this.radius = SkyFx.range(this.rangeLevel);
		this.damage = SkyFx.damage(damageLevel);
		this.width = SkyFx.width(this.rangeLevel);
		this.height = 6 + this.rangeLevel * 2;
		this.start = player.position();
		this.impact = SkyFx.aim(level, player, start);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, RISE + CHARGE + FIRE + 20, 4, false, false));
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3f, 0.5f);
		level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.5f, 1.5f - this.rangeLevel * 0.1f);
	}

	/** Recharge selon la portée : de 12 s (10 blocs) à 36 s (200 blocs). */
	public static int cooldown(int rangeLevel) {
		return 160 + SkyFx.clamp(rangeLevel) * 80;
	}

	/** Pour l'auto-test : le point d'impact du rayon. */
	public Vec3 impactPoint() {
		return impact;
	}

	/** Pour l'auto-test : combien de créatures ont été touchées. */
	public int hitCount() {
		return hit.size();
	}

	private double hoverY() {
		return start.y + height;
	}

	/** Garde le joueur en l'air à cette hauteur (il peut toujours tourner la tête pour viser). */
	private void hold(double y) {
		player.teleportTo(level, start.x, y, start.z, Set.of(Relative.X_ROT, Relative.Y_ROT), 0, 0, false);
		player.setDeltaMovement(Vec3.ZERO);
		player.fallDistance = 0;
	}

	private Vec3 hand() {
		return player.position().add(0, 1.3, 0).add(player.getLookAngle().scale(1.4));
	}

	private <T extends Display> T keep(T d) {
		return track(d);
	}

	@Override
	protected boolean tick() {
		if (!player.isAlive() || player.level() != level) {
			return true;
		}
		if (age < RISE) {
			rise(age);
		} else if (age < RISE + CHARGE) {
			charge(age - RISE);
		} else if (age < RISE + CHARGE + FIRE) {
			fire(age - RISE - CHARGE);
		} else {
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, false, false));
			player.fallDistance = 0;
			return true;
		}
		return false;
	}

	// ================================================================ 1. l'envol

	private void rise(int t) {
		double eased = 1 - Math.pow(1 - (t + 1) / (double) RISE, 3);
		hold(start.y + height * eased);
		if (lift == null) {
			lift = keep(SkyFx.laser(level, start, LaserBlock.WHITE));
		}
		float w = 1.4f * (1 - t / (float) RISE) + 0.2f;
		SkyFx.beam(lift, start, player.position(), w);
		Vec3 p = player.position();
		for (int i = 0; i < 7; i++) {
			double a = t * 0.7 + i * Math.PI * 2 / 7;
			double r = 2.2 - t * 0.05;
			level.sendParticles(SkyFx.glow(i), p.x + Math.cos(a) * r, p.y + 0.3, p.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
		}
		if (t % 4 == 0) {
			level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2f, 0.5f + t * 0.07f);
		}
	}

	// ================================================================ 2. la charge

	private void charge(int t) {
		hold(hoverY());
		float progress = t / (float) CHARGE;
		if (lift != null) {
			lift.discard();
			lift = null;
		}
		impact = SkyFx.aim(level, player, start);
		Vec3 hand = hand();
		if (orb == null) {
			orb = keep(SkyFx.sphere(level, hand, RealmContent.ORB[LaserBlock.WHITE], 0.4f));
			for (int i = 0; i < 7; i++) {
				shards[i] = keep(SkyFx.sphere(level, hand, RealmContent.ORB[i], 0.6f));
			}
			for (int i = 0; i < TARGET; i++) {
				target[i] = keep(SkyFx.laser(level, impact, i % 7));
			}
			level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 3f, 0.5f);
		}
		// La sphère d'énergie grossit dans la main.
		orb.setPos(hand);
		SkyFx.resize(orb, 0.4f + progress * (1.2f + rangeLevel * 0.35f), 1);
		// Les 7 sphères des royaumes tournent autour puis s'y fondent.
		for (int i = 0; i < 7; i++) {
			double a = t * (0.25 + progress * 0.5) + i * Math.PI * 2 / 7;
			double r = 4.0 * (1 - progress) + 0.5;
			Vec3 pos = hand.add(Math.cos(a) * r, Math.sin(a * 1.7) * r * 0.4, Math.sin(a) * r);
			shards[i].setPos(pos);
			SkyFx.resize(shards[i], 0.7f * (1 - progress * 0.7f), 1);
			level.sendParticles(SkyFx.glow(i), pos.x, pos.y, pos.z, 1, 0.05, 0.05, 0.05, 0);
		}
		// La cible ronde suit le bloc visé et se resserre.
		double tr = Math.max(width, radius * (1 - progress) * 0.5 + width);
		SkyFx.ring(target, impact.add(0, 0.2, 0), tr, t * 0.05, 0.35f + rangeLevel * 0.05f);
		// La lumière converge vers la sphère.
		for (int k = 0; k < 10 + rangeLevel * 2; k++) {
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			double d = 6 + level.getRandom().nextDouble() * 20;
			Vec3 from = hand.add(Math.cos(a) * d, (level.getRandom().nextDouble() - 0.5) * 10, Math.sin(a) * d);
			Vec3 dir = hand.subtract(from).normalize();
			level.sendParticles(SkyFx.glow(k % 8), from.x, from.y, from.z, 0, dir.x, dir.y, dir.z, 1.4);
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y, hand.z, 4 + rangeLevel, 0.6, 0.6, 0.6, 0.3);
		if (t % 8 == 0) {
			level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 2.5f, 0.5f + progress);
			SkyFx.shake(level, player.position(), 80, 0.15f + progress * 0.5f, 10);
		}
		if (t == CHARGE - 6) {
			level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 3f, 1.6f);
		}
	}

	// ================================================================ 3. le tir

	private void fire(int t) {
		hold(hoverY());
		float fade = t > FIRE - 10 ? (FIRE - t) / 10f : 1f;
		Vec3 hand = hand();
		if (t == 0) {
			ignite(hand);
		}
		// Le laser rond : de la main jusqu'au bloc visé (halo coloré qui change de royaume, couche blanche, cœur).
		float pulse = (float) (1 + Math.sin(t * 1.3) * 0.12);
		int col = (t / 3) % 7;
		beam[0].setBlockState(LaserBlock.of(col));
		beam[0].setGlowColorOverride(Realm.values()[col].color);
		for (int i = 0; i < 3; i++) {
			beam[i].setPos(hand);
		}
		SkyFx.beam(beam[0], hand, impact, width * pulse * fade);
		SkyFx.beam(beam[1], hand, impact, width * 0.62f * fade);
		SkyFx.beam(beam[2], hand, impact, width * 0.3f * pulse * fade);
		// Une colonne de lumière monte au ciel au point d'impact.
		SkyFx.beam(sky, impact, impact.add(0, 120, 0), width * 0.8f * pulse * fade);
		// Le cratère se creuse.
		if (t < 12) {
			double r = SkyFx.crater(rangeLevel) * (t + 1) / 12.0;
			SkyFx.carve(level, impact, r, carved);
			carved = r;
		}
		// L'onde de choc ronde et les 7 lasers ronds tournants.
		double reach = Math.min(radius, (t + 1) * radius / 26.0);
		SkyFx.ring(ring, impact.add(0, 0.6, 0), reach, t * 0.03, (1.0f + rangeLevel * 0.3f) * fade);
		int trench = SkyFx.trench(rangeLevel);
		for (int i = 0; i < 7; i++) {
			double a = t * 0.11 + i * Math.PI * 2 / 7;
			Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
			Vec3 from = impact.add(0, 0.8, 0);
			lasers[i].setPos(from);
			SkyFx.beam(lasers[i], from, from.add(dir.scale(reach)), (0.8f + rangeLevel * 0.25f) * fade);
			for (int k = 0; k < 4; k++) {
				Vec3 p = from.add(dir.scale(level.getRandom().nextDouble() * reach));
				level.sendParticles(SkyFx.glow(i), p.x, p.y, p.z, 1, 0.3, 0.3, 0.3, 0.02);
			}
			if (trench > 0 && fade > 0.5f) {
				SkyFx.plough(level, impact, dir, SkyFx.crater(rangeLevel), reach, trench);
			}
		}
		// Explosions le long de l'onde.
		int n = Math.max(6, (int) (reach * 0.3));
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n + t;
			level.sendParticles(i % 3 == 0 ? ParticleTypes.EXPLOSION : ModParticles.GLOW_WHITE,
					impact.x + Math.cos(a) * reach, impact.y + 1, impact.z + Math.sin(a) * reach, 1, 0.3, 0.3, 0.3, 0);
		}
		Vec3 along = impact.subtract(hand);
		for (int k = 0; k < 12; k++) {
			Vec3 p = hand.add(along.scale(level.getRandom().nextDouble()));
			level.sendParticles(ModParticles.GLOW_WHITE, p.x, p.y, p.z, 1, width * 0.3, width * 0.3, width * 0.3, 0.05);
		}
		if (t % 5 == 0) {
			level.playSound(null, BlockPos.containing(impact), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 4f, 0.4f + t * 0.02f);
			level.playSound(null, player.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 4f, 0.5f);
		}
		SkyFx.strike(level, player, impact, reach, damage, hit);
	}

	/** Le moment du tir : la sphère part, le laser jaillit, flash et tremblement pour tout le monde autour. */
	private void ignite(Vec3 hand) {
		impact = SkyFx.aim(level, player, start);
		if (orb != null) {
			orb.discard();
			orb = null;
		}
		for (int i = 0; i < 7; i++) {
			if (shards[i] != null) {
				shards[i].discard();
				shards[i] = null;
			}
		}
		for (int i = 0; i < TARGET; i++) {
			if (target[i] != null) {
				target[i].discard();
				target[i] = null;
			}
		}
		beam[0] = keep(SkyFx.laser(level, hand, 0));
		beam[1] = keep(SkyFx.laser(level, hand, LaserBlock.WHITE));
		beam[2] = keep(SkyFx.laser(level, hand, LaserBlock.WHITE));
		sky = keep(SkyFx.laser(level, impact, LaserBlock.WHITE));
		for (int i = 0; i < RING; i++) {
			ring[i] = keep(SkyFx.laser(level, impact, i % 2 == 0 ? LaserBlock.WHITE : 1));
		}
		for (int i = 0; i < 7; i++) {
			lasers[i] = keep(SkyFx.laser(level, impact, i));
		}
		BlockPos at = BlockPos.containing(impact);
		level.playSound(null, at, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 5f, 0.4f);
		level.playSound(null, at, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 5f, 0.5f);
		level.playSound(null, at, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 5f, 0.4f);
		level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3f, 0.6f);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, impact.x, impact.y + 1, impact.z, 4 + rangeLevel * 2, 2 + rangeLevel, 1, 2 + rangeLevel, 0);
		level.sendParticles(ParticleTypes.SONIC_BOOM, impact.x, impact.y + 2, impact.z, 6, 3, 1, 3, 0);
		SkyFx.flash(level, impact, radius + 80, 0xFFFFFF, 0.6f + rangeLevel * 0.05f, 10 + rangeLevel * 3);
		SkyFx.shake(level, impact, radius + 80, 0.6f + rangeLevel * 0.25f, 20 + rangeLevel * 4);
	}
}
