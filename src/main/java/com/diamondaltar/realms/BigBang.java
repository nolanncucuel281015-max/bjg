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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Épée des Sept Royaumes — « Big Bang », la septième et la plus forte des attaques :
 * <ol>
 * <li><b>Silence</b> : le ciel devient noir, toutes les créatures de la zone sont figées sur place, la matière
 * est aspirée vers un point minuscule au-dessus du bloc visé ;</li>
 * <li><b>Singularité</b> : le point de lumière pulse comme un cœur, trois anneaux (blanc, nébuleuse, soleil)
 * tournent autour de plus en plus vite, la terre tremble de plus en plus fort… puis tout se tait ;</li>
 * <li><b>Big Bang</b> : flash blanc total, deux sphères (lumière et soleil) gonflent jusqu'à des dizaines de
 * blocs, quatre ondes de choc rondes partent jusqu'au bout de la portée et
 * <b>cinq fois les dégâts choisis</b> frappent tout (au sol comme en l'air).</li>
 * </ol>
 * Les joueurs ne sont jamais touchés ; la bedrock et les coffres ne sont jamais détruits.
 */
public final class BigBang extends TimedEffect {
	private static final int SILENCE = 30;
	private static final int SINGULARITY = 40;
	private static final int BANG = 60;
	private static final double LIFT = 12;
	private static final int RING = 20;
	private static final int WAVE = 24;

	private final ServerPlayer player;
	private final int rangeLevel;
	private final double radius;
	private final float damage;
	private final int damageLevel;
	private final Vec3 target;
	private final Vec3 point;
	private final Set<UUID> hit = new HashSet<>();
	private final Set<UUID> airHit = new HashSet<>();
	private final Display.BlockDisplay[][] rings = new Display.BlockDisplay[3][RING];
	private final Display.BlockDisplay[][] waves = new Display.BlockDisplay[4][WAVE];
	private Display.ItemDisplay seed;
	private Display.ItemDisplay light;
	private Display.ItemDisplay sun;
	private double carved;

	public BigBang(ServerLevel level, ServerPlayer player, int rangeLevel, int damageLevel) {
		super(level);
		this.player = player;
		this.rangeLevel = SkyFx.clamp(rangeLevel);
		this.radius = SkyFx.range(this.rangeLevel);
		this.damage = SkyFx.damage(damageLevel) * 5;
		this.damageLevel = damageLevel;
		Vec3 ahead = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(30));
		this.target = SkyFx.aim(level, player, ahead, radius);
		this.point = target.add(0, LIFT, 0);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, SILENCE + SINGULARITY + BANG, 4, false, false));
		level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 3f, 0.4f);
	}

	/** Recharge : trois fois celle de l'Anéantissement Céleste. */
	public static int cooldown(int rangeLevel) {
		return CelestialAnnihilation.cooldown(rangeLevel) * 3;
	}

	public Vec3 impactPoint() {
		return target;
	}

	public int hitCount() {
		return hit.size();
	}

	@Override
	protected boolean tick() {
		if (age < SILENCE) {
			silence(age);
		} else if (age < SILENCE + SINGULARITY) {
			singularity(age - SILENCE);
		} else if (age < SILENCE + SINGULARITY + BANG) {
			bang(age - SILENCE - SINGULARITY);
		} else {
			return true;
		}
		return false;
	}

	// ================================================================ 1. silence : tout s'arrête

	private void silence(int t) {
		if (seed == null) {
			seed = track(SkyFx.sphere(level, point, RealmContent.ORB[LaserBlock.WHITE], 0.3f));
			SkyFx.flash(level, target, radius + 200, 0x000000, 0.85f, SILENCE + SINGULARITY + 10);
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 4f, 0.3f);
		}
		if (t % 4 == 0) {
			// Toutes les créatures de la zone sont figées.
			for (LivingEntity e : SkyFx.creatures(level, target, radius)) {
				e.setDeltaMovement(Vec3.ZERO);
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 10, false, false));
				com.diamondaltar.Compat.markHurt(e);
			}
		}
		// La matière est aspirée vers le point.
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, point.x, point.y, point.z, 40, 10, 10, 10, 1.2);
		SkyFx.resize(seed, 0.3f + t * 0.05f, 1);
	}

	// ================================================================ 2. la singularité

	private void singularity(int t) {
		float p = (t + 1) / (float) SINGULARITY;
		if (rings[0][0] == null) {
			for (int r = 0; r < 3; r++) {
				int color = r == 0 ? LaserBlock.WHITE : (r == 1 ? 5 : 6);
				for (int i = 0; i < RING; i++) {
					rings[r][i] = track(SkyFx.laser(level, point, color));
				}
			}
		}
		// Le point pulse comme un cœur, de plus en plus vite ; il disparaît presque à la toute fin.
		float beat = (float) Math.abs(Math.sin(t * (0.3 + p * 0.9)));
		float size = t > SINGULARITY - 6 ? 0.4f : 2f + beat * (2f + p * 4f);
		SkyFx.resize(seed, size, 1);
		for (int r = 0; r < 3; r++) {
			double ringRadius = (6 + r * 4) * (t > SINGULARITY - 6 ? 0.2 : 1);
			double spin = (r % 2 == 0 ? 1 : -1) * t * (0.1 + p * 0.4) * (r + 1);
			SkyFx.ring(rings[r], point.add(0, (r - 1) * 2.5, 0), ringRadius, spin, 0.6f + p, 1);
		}
		level.sendParticles(ModParticles.GLOW_WHITE, point.x, point.y, point.z, 10, 1, 1, 1, 0.05);
		int beatEvery = Math.max(3, 10 - (int) (p * 8));
		if (t % beatEvery == 0 && t < SINGULARITY - 6) {
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 5f, 0.5f + p * 0.5f);
			SkyFx.shake(level, target, radius + 150, 0.4f + p * 1.6f, 6);
		}
		if (t == SINGULARITY - 6) {
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 6f, 0.5f);
		}
	}

	// ================================================================ 3. Big Bang

	private void bang(int t) {
		if (t == 0) {
			GiantCrater.start(level, target, damageLevel);
			for (Display.BlockDisplay[] ring : rings) {
				for (Display.BlockDisplay d : ring) {
					d.discard();
				}
			}
			seed.discard();
			light = track(SkyFx.sphere(level, point, RealmContent.ORB[LaserBlock.WHITE], 4f));
			sun = track(SkyFx.sphere(level, point, RealmContent.SUN, 2f));
			for (int w = 0; w < 4; w++) {
				for (int i = 0; i < WAVE; i++) {
					waves[w][i] = track(SkyFx.laser(level, target, w == 0 ? LaserBlock.WHITE : (w == 1 ? 6 : (w == 2 ? 0 : 5))));
				}
			}
			BlockPos at = BlockPos.containing(point);
			level.playSound(null, at, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 10f, 0.3f);
			level.playSound(null, at, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 10f, 0.3f);
			level.playSound(null, at, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 10f, 0.3f);
			level.playSound(null, at, SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 6f, 0.4f);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, point.x, point.y, point.z, 40, 14, 10, 14, 0);
			level.sendParticles(ModParticles.GLOW_WHITE, point.x, point.y, point.z, 200, 6, 6, 6, 1.5);
			SkyFx.flash(level, point, radius + 300, 0xFFFFFF, 1.0f, 45);
			SkyFx.shake(level, point, radius + 300, 3.5f + rangeLevel * 0.2f, 70);
		}
		float grow = (float) (1 - Math.pow(1 - Math.min(1, (t + 1) / 20.0), 3));
		float fade = t > BANG - 15 ? (BANG - t) / 15f : 1f;
		float maxSize = (float) Math.min(140, 30 + radius * 0.5);
		SkyFx.resize(light, maxSize * grow * fade, 1);
		SkyFx.resize(sun, maxSize * 0.75f * (float) Math.min(1, (t + 1) / 28.0) * fade, 1);
		if (t < 14) {
			double r = (SkyFx.crater(rangeLevel) + 8) * (t + 1) / 14.0;
			SkyFx.carve(level, target, r, carved);
			carved = r;
		}
		if (t % 2 == 1 && t != BANG - 1) {
			return; // ondes et dégâts un tick sur deux : moins de lag
		}
		// Quatre ondes de choc, chacune plus rapide que la précédente.
		for (int w = 0; w < 4; w++) {
			double reach = Math.max(0.5, Math.min(radius, (t + 2) * radius / (30.0 - w * 5)));
			SkyFx.ring(waves[w], target.add(0, 0.8 + w * 1.2, 0), reach, t * 0.04 * (w + 1), (2f + rangeLevel * 0.3f) * fade, 2);
		}
		double reach = Math.min(radius, (t + 2) * radius / 15.0);
		int n = Math.max(8, Math.min(48, (int) (reach * 0.35)));
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n + t;
			level.sendParticles(i % 2 == 0 ? ParticleTypes.EXPLOSION : ModParticles.GLOW_WHITE,
					target.x + Math.cos(a) * reach, target.y + 1, target.z + Math.sin(a) * reach, 1, 0.5, 0.5, 0.5, 0);
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, target.x, target.y + 6 + t * 0.8, target.z, 20, 6, 4, 6, 0.06);
		if (t % 6 == 0) {
			level.playSound(null, BlockPos.containing(target), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 8f, 0.3f + t * 0.01f);
		}
		SkyFx.strike(level, player, target, reach, damage, hit);
		// Les créatures en l'air sont frappées par la boule de lumière.
		SkyFx.strike(level, player, point, Math.min(radius, maxSize * 0.5 * grow + 8), damage, airHit);
		hit.addAll(airHit);
	}
}
