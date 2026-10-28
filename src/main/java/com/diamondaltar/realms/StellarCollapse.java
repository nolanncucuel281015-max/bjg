package com.diamondaltar.realms;

import com.diamondaltar.Compat;
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
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Épée des Sept Royaumes — « Effondrement Stellaire », l'attaque la plus forte :
 * <ol>
 * <li><b>Naissance</b> : une étoile géante (sphère de feu animée) apparaît dans le ciel au-dessus du bloc visé ;</li>
 * <li><b>Effondrement</b> : l'étoile se contracte en tremblant et devient un trou noir entouré d'un disque de lumière ;</li>
 * <li><b>Aspiration</b> : le trou noir attire et soulève toutes les créatures dans la portée choisie,
 * pendant que le disque tourne de plus en plus vite et que la foudre tombe autour ;</li>
 * <li><b>Supernova</b> : tout explose — flash aveuglant, trois ondes de choc rondes, énorme cratère, et
 * <b>deux fois les dégâts choisis</b> à chaque créature de la zone.</li>
 * </ol>
 * Les joueurs ne sont jamais touchés ni aspirés ; la bedrock et les coffres ne sont jamais détruits.
 */
public final class StellarCollapse extends TimedEffect {
	private static final int BIRTH = 30;
	private static final int COLLAPSE = 35;
	private static final int PULL = 60;
	private static final int NOVA = 50;
	private static final float STAR_SIZE = 38f;
	private static final float HOLE_SIZE = 16f;
	private static final int DISK = 24;
	private static final int WAVE = 24;

	private final ServerPlayer player;
	private final int rangeLevel;
	private final double radius;
	private final float damage;
	private final Vec3 target;
	private final Vec3 core;
	private final Set<UUID> hit = new HashSet<>();
	private Display.ItemDisplay star;
	private Display.ItemDisplay hole;
	private final Display.BlockDisplay[] disk = new Display.BlockDisplay[DISK];
	private final Display.BlockDisplay[][] waves = new Display.BlockDisplay[3][WAVE];
	private double carved;

	public StellarCollapse(ServerLevel level, ServerPlayer player, int rangeLevel, int damageLevel) {
		super(level);
		this.player = player;
		this.rangeLevel = SkyFx.clamp(rangeLevel);
		this.radius = SkyFx.range(this.rangeLevel);
		this.damage = SkyFx.damage(damageLevel) * 2;
		Vec3 ahead = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(30));
		this.target = SkyFx.aim(level, player, ahead);
		this.core = target.add(0, 28, 0);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, BIRTH + COLLAPSE + PULL + NOVA, 4, false, false));
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 4f, 0.3f);
		level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 4f, 0.3f);
	}

	/** Recharge : une fois et demie celle des autres grandes attaques. */
	public static int cooldown(int rangeLevel) {
		return CelestialAnnihilation.cooldown(rangeLevel) * 3 / 2;
	}

	public Vec3 impactPoint() {
		return target;
	}

	public int hitCount() {
		return hit.size();
	}

	/** Pour l'auto-test : depuis combien de ticks l'attaque tourne, et jusqu'où l'onde de la supernova est allée. */
	public String debug() {
		return "age=" + age + " onde max=" + String.format("%.1f", maxReach);
	}

	private double maxReach;

	@Override
	protected boolean tick() {
		if (age < BIRTH) {
			birth(age);
		} else if (age < BIRTH + COLLAPSE) {
			collapse(age - BIRTH);
		} else if (age < BIRTH + COLLAPSE + PULL) {
			pull(age - BIRTH - COLLAPSE);
		} else if (age < BIRTH + COLLAPSE + PULL + NOVA) {
			nova(age - BIRTH - COLLAPSE - PULL);
		} else {
			return true;
		}
		return false;
	}

	// ================================================================ 1. naissance de l'étoile

	private void birth(int t) {
		if (star == null) {
			star = track(SkyFx.sphere(level, core, RealmContent.SUN, 0.1f));
			level.playSound(null, BlockPos.containing(core), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 4f, 0.3f);
		}
		float p = (float) (1 - Math.pow(1 - (t + 1) / (double) BIRTH, 3));
		SkyFx.resize(star, STAR_SIZE * p, 1);
		SkyFx.particleRing(level, ModParticles.GLOW_FIRE, core, STAR_SIZE * 0.55 * p, 32, t * 0.2);
		level.sendParticles(ModParticles.GLOW_GOLD, core.x, core.y, core.z, 8, STAR_SIZE * 0.3, STAR_SIZE * 0.3, STAR_SIZE * 0.3, 0.1);
		if (t % 6 == 0) {
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, SoundSource.PLAYERS, 3f, 0.3f);
		}
	}

	// ================================================================ 2. effondrement en trou noir

	private void collapse(int t) {
		float p = (t + 1) / (float) COLLAPSE;
		// L'étoile tremble et rétrécit, le trou noir grandit en son centre.
		float wobble = (float) Math.sin(t * 2.3) * 2f * (1 - p);
		SkyFx.resize(star, Math.max(0.05f, STAR_SIZE * (1 - p * p) + wobble), 1);
		if (hole == null) {
			hole = track(SkyFx.sphere(level, core, RealmContent.BLACK_HOLE, 0.1f));
			for (int i = 0; i < DISK; i++) {
				disk[i] = track(SkyFx.laser(level, core, i % 3 == 0 ? LaserBlock.WHITE : (i % 3 == 1 ? 0 : 5)));
			}
			SkyFx.flash(level, core, radius + 150, 0x000000, 0.55f, 40);
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 4f, 0.5f);
		}
		SkyFx.resize(hole, HOLE_SIZE * p, 1);
		SkyFx.ring(disk, core, HOLE_SIZE * 0.9 * p + 1, t * 0.2, 0.8f + p * 1.2f);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, core.x, core.y, core.z, 30, 8, 8, 8, 0.6);
		if (t == COLLAPSE - 1) {
			star.discard();
			star = null;
		}
		if (t % 7 == 0) {
			SkyFx.shake(level, core, radius + 100, 0.3f + p * 0.6f, 12);
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 4f, 0.5f);
		}
	}

	// ================================================================ 3. le trou noir aspire tout

	private void pull(int t) {
		float p = t / (float) PULL;
		SkyFx.resize(hole, HOLE_SIZE * (1 + 0.08f * (float) Math.sin(t * 0.7)), 1);
		if (t % 2 == 0) {
			SkyFx.ring(disk, core, HOLE_SIZE * 0.9 + 1, t * (0.2 + p * 0.5), 2f, 2);
			// Aspiration : les créatures sont soulevées et tirées vers le trou noir.
			for (LivingEntity e : SkyFx.creatures(level, target, radius)) {
				Vec3 to = core.subtract(e.position());
				double d = to.length();
				// Plus la créature est proche, plus elle ralentit : elle reste piégée autour du trou noir au lieu de le dépasser.
				Vec3 v = d < 3 ? to.scale(0.3) : to.normalize().scale(Math.min(Math.min(2.2, d * 0.3), 0.35 + d * 0.04 + p));
				e.setDeltaMovement(v);
				e.fallDistance = 0;
				Compat.markHurt(e);
			}
		}
		// La matière tombe en spirale dans le trou noir.
		for (int k = 0; k < 6; k++) {
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			double d = 6 + level.getRandom().nextDouble() * 30;
			Vec3 from = core.add(Math.cos(a) * d, (level.getRandom().nextDouble() - 0.5) * 6, Math.sin(a) * d);
			Vec3 dir = core.subtract(from).normalize();
			level.sendParticles(k % 2 == 0 ? ModParticles.GLOW_NEBULA : ModParticles.GLOW_FIRE, from.x, from.y, from.z, 0, dir.x, dir.y, dir.z, 1.6);
		}
		// La foudre tombe autour.
		if (t % 6 == 0) {
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			double d = level.getRandom().nextDouble() * Math.min(radius, 60);
			LightningBolt bolt = new LightningBolt(Compat.LIGHTNING_BOLT, level);
			bolt.setPos(target.x + Math.cos(a) * d, target.y, target.z + Math.sin(a) * d);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
			SkyFx.shake(level, core, radius + 100, 0.4f + p * 0.6f, 8);
		}
		if (t % 10 == 0) {
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 4f, 0.3f + p * 0.5f);
		}
	}

	// ================================================================ 4. supernova

	private void nova(int t) {
		if (t == 0) {
			if (hole != null) {
				hole.discard();
				hole = null;
			}
			for (int i = 0; i < DISK; i++) {
				if (disk[i] != null) {
					disk[i].discard();
					disk[i] = null;
				}
			}
			for (int w = 0; w < 3; w++) {
				for (int i = 0; i < WAVE; i++) {
					waves[w][i] = track(SkyFx.laser(level, target, w == 0 ? LaserBlock.WHITE : (w == 1 ? 6 : 5)));
				}
			}
			star = track(SkyFx.sphere(level, core, RealmContent.ORB[LaserBlock.WHITE], 1f));
			BlockPos at = BlockPos.containing(core);
			level.playSound(null, at, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 8f, 0.3f);
			level.playSound(null, at, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 8f, 0.3f);
			level.playSound(null, at, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 8f, 0.3f);
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 5f, 0.4f);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, core.x, core.y, core.z, 20, 10, 10, 10, 0);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, target.x, target.y + 2, target.z, 12, 6, 2, 6, 0);
			SkyFx.flash(level, core, radius + 200, 0xFFFFFF, 1.0f, 30);
			SkyFx.shake(level, core, radius + 200, 2.0f + rangeLevel * 0.2f, 45);
		}
		float fade = t > NOVA - 12 ? (NOVA - t) / 12f : 1f;
		// La boule de lumière de la supernova gonfle puis s'éteint.
		SkyFx.resize(star, (12 + t * 3f) * fade, 1);
		// Le cratère, plus grand que pour les autres attaques.
		if (t < 14) {
			double r = (SkyFx.crater(rangeLevel) + 6) * (t + 1) / 14.0;
			SkyFx.carve(level, target, r, carved);
			carved = r;
		}
		if (t % 2 == 1 && t != NOVA - 1) {
			return; // ondes et dégâts un tick sur deux : moins de lag
		}
		// Trois ondes de choc rondes, décalées.
		for (int w = 0; w < 3; w++) {
			double reach = Math.max(0.5, Math.min(radius, (t + 2 - w * 6) * radius / 26.0));
			SkyFx.ring(waves[w], target.add(0, 0.8 + w * 1.5, 0), reach, t * 0.05 * (w + 1), (1.5f + rangeLevel * 0.3f) * fade, 2);
		}
		double reach = Math.min(radius, (t + 2) * radius / 26.0);
		int n = Math.max(8, Math.min(40, (int) (reach * 0.35)));
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n + t;
			level.sendParticles(i % 2 == 0 ? ParticleTypes.EXPLOSION : ModParticles.GLOW_WHITE,
					target.x + Math.cos(a) * reach, target.y + 1, target.z + Math.sin(a) * reach, 1, 0.5, 0.5, 0.5, 0);
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, target.x, target.y + 4 + t * 0.6, target.z, 14, 4, 3, 4, 0.05);
		if (t % 6 == 0) {
			level.playSound(null, BlockPos.containing(target), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 6f, 0.3f + t * 0.01f);
		}
		maxReach = Math.max(maxReach, reach);
		SkyFx.strike(level, player, target, reach, damage, hit);
		// Les créatures aspirées autour du trou noir (en l'air) sont aussi pulvérisées.
		SkyFx.strike(level, player, core, Math.min(radius, 16 + t * 2.0), damage, hit);
	}
}
