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
 * Épée des Sept Royaumes — « Tornade Céleste » :
 * <ol>
 * <li><b>Naissance</b> : une tornade faite de sept anneaux laser (un par royaume) se forme sur le bloc visé et
 * grandit jusqu'au ciel, la foudre tombe tout autour ;</li>
 * <li><b>Tourbillon</b> : toutes les créatures de la portée sont aspirées, soulevées et tournent dans la tornade,
 * frappées à chaque tour ;</li>
 * <li><b>Éjection</b> : la tornade explose vers l'extérieur — les créatures sont projetées, onde de choc et
 * <b>quatre fois les dégâts choisis</b>.</li>
 * </ol>
 * Les joueurs ne sont jamais touchés ni aspirés ; la bedrock et les coffres ne sont jamais détruits.
 */
public final class CelestialTornado extends TimedEffect {
	private static final int FORM = 30;
	private static final int SPIN = 80;
	private static final int BURST = 40;
	private static final int LAYERS = 7;
	private static final int RING = 14;
	private static final double LAYER_STEP = 9;
	private static final int WAVE = 24;

	private final ServerPlayer player;
	private final int rangeLevel;
	private final double radius;
	private final float damage;
	private final int damageLevel;
	private final Vec3 target;
	private final Set<UUID> hit = new HashSet<>();
	private final Display.BlockDisplay[][] layers = new Display.BlockDisplay[LAYERS][RING];
	private final Display.BlockDisplay[] wave = new Display.BlockDisplay[WAVE];
	private Display.BlockDisplay eye;
	private double spin;

	public CelestialTornado(ServerLevel level, ServerPlayer player, int rangeLevel, int damageLevel) {
		super(level);
		this.player = player;
		this.rangeLevel = SkyFx.clamp(rangeLevel);
		this.radius = SkyFx.range(this.rangeLevel);
		this.damage = SkyFx.damage(damageLevel) * 4;
		this.damageLevel = damageLevel;
		Vec3 ahead = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(30));
		this.target = SkyFx.aim(level, player, ahead, radius);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, FORM + SPIN + BURST, 4, false, false));
		level.playSound(null, player.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 4f, 0.4f);
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
		if (age < FORM) {
			form(age);
		} else if (age < FORM + SPIN) {
			whirl(age - FORM);
		} else if (age < FORM + SPIN + BURST) {
			burst(age - FORM - SPIN);
		} else {
			return true;
		}
		return false;
	}

	/** La tornade : des anneaux de plus en plus larges vers le haut, qui tournent et ondulent. */
	private void shapeTornado(float grow, float width, double speed) {
		spin += speed;
		for (int l = 0; l < LAYERS; l++) {
			double y = l * LAYER_STEP * grow;
			double r = (3 + l * 3.2) * grow;
			Vec3 c = target.add(Math.sin(spin * 0.7 + l) * 1.5, y + 1, Math.cos(spin * 0.5 + l) * 1.5);
			SkyFx.ring(layers[l], c, Math.max(0.5, r), spin * (1 + l * 0.15), width, 2);
		}
	}

	// ================================================================ 1. la tornade se forme

	private void form(int t) {
		if (layers[0][0] == null) {
			for (int l = 0; l < LAYERS; l++) {
				for (int i = 0; i < RING; i++) {
					layers[l][i] = track(SkyFx.laser(level, target, l));
				}
			}
			eye = track(SkyFx.laser(level, target, LaserBlock.WHITE));
		}
		float p = (float) (1 - Math.pow(1 - (t + 1) / (double) FORM, 2));
		if (t % 2 == 0) {
			shapeTornado(p, 0.8f + p, 0.25);
		}
		SkyFx.beam(eye, target, target.add(0, LAYERS * LAYER_STEP * p, 0), 1.5f * p, 1);
		level.sendParticles(ParticleTypes.CLOUD, target.x, target.y + 2, target.z, 20, 4 * p, 3, 4 * p, 0.2);
		if (t % 5 == 0) {
			lightning(30);
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 4f, 0.5f + p * 0.5f);
		}
	}

	private void lightning(double spread) {
		double a = level.getRandom().nextDouble() * Math.PI * 2;
		double d = level.getRandom().nextDouble() * Math.min(radius, spread);
		LightningBolt bolt = new LightningBolt(Compat.LIGHTNING_BOLT, level);
		bolt.setPos(target.x + Math.cos(a) * d, target.y, target.z + Math.sin(a) * d);
		bolt.setVisualOnly(true);
		level.addFreshEntity(bolt);
	}

	// ================================================================ 2. le tourbillon

	private void whirl(int t) {
		float p = (t + 1) / (float) SPIN;
		if (t % 2 == 0) {
			shapeTornado(1f, 1.8f + p * 1.5f, 0.35 + p * 0.3);
			// Les créatures sont aspirées vers la tornade, soulevées et tournent autour.
			for (LivingEntity e : SkyFx.creatures(level, target, radius)) {
				Vec3 to = target.subtract(e.position()).multiply(1, 0, 1);
				double d = to.length();
				Vec3 tangent = new Vec3(-to.z, 0, to.x).normalize();
				Vec3 in = d > 0.01 ? to.normalize() : Vec3.ZERO;
				double pull = d > 8 ? Math.min(2.0, 0.4 + d * 0.03) : 0.1;
				double up = e.getY() < target.y + 30 ? 0.45 : 0.0;
				e.setDeltaMovement(in.scale(pull).add(tangent.scale(0.9)).add(0, up, 0));
				e.fallDistance = 0;
				Compat.markHurt(e);
			}
		}
		// Dans la tornade, on est frappé à chaque tour (toutes les 20 ticks).
		if (t % 20 == 10) {
			Set<UUID> once = new HashSet<>();
			SkyFx.strike(level, player, target.add(0, 10, 0), 40, damage / 4, once);
			SkyFx.strike(level, player, target, 12, damage / 4, once);
			hit.addAll(once);
		}
		SkyFx.beam(eye, target, target.add(0, LAYERS * LAYER_STEP, 0), 1.5f + p * 2, 1);
		level.sendParticles(ParticleTypes.CLOUD, target.x, target.y + 10, target.z, 30, 10, 20, 10, 0.3);
		level.sendParticles(ModParticles.GLOW_ICE, target.x, target.y + 15, target.z, 10, 12, 20, 12, 0.2);
		if (t % 8 == 0) {
			lightning(60);
			SkyFx.shake(level, target, radius + 100, 0.5f + p, 10);
			level.playSound(null, BlockPos.containing(target), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 5f, 0.6f + p * 0.4f);
		}
	}

	// ================================================================ 3. éjection

	private void burst(int t) {
		if (t == 0) {
			GiantCrater.start(level, target, damageLevel);
			for (Display.BlockDisplay[] layer : layers) {
				for (Display.BlockDisplay d : layer) {
					d.discard();
				}
			}
			eye.discard();
			for (int i = 0; i < WAVE; i++) {
				wave[i] = track(SkyFx.laser(level, target, i % 7));
			}
			// Toutes les créatures sont projetées vers l'extérieur.
			for (LivingEntity e : SkyFx.creatures(level, target, radius)) {
				Vec3 out = e.position().subtract(target).multiply(1, 0, 1);
				out = out.lengthSqr() > 0.01 ? out.normalize() : new Vec3(1, 0, 0);
				e.setDeltaMovement(out.scale(3).add(0, 1.2, 0));
				Compat.markHurt(e);
			}
			BlockPos at = BlockPos.containing(target);
			level.playSound(null, at, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 8f, 0.4f);
			level.playSound(null, at, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 8f, 0.3f);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, target.x, target.y + 10, target.z, 30, 12, 20, 12, 0);
			SkyFx.flash(level, target, radius + 200, 0xCCE8FF, 0.9f, 25);
			SkyFx.shake(level, target, radius + 200, 2.5f + rangeLevel * 0.2f, 40);
		}
		float fade = t > BURST - 12 ? (BURST - t) / 12f : 1f;
		if (t % 2 == 1 && t != BURST - 1) {
			return;
		}
		double reach = Math.min(radius, (t + 2) * radius / 22.0);
		SkyFx.ring(wave, target.add(0, 1, 0), reach, t * 0.08, (1.8f + rangeLevel * 0.3f) * fade, 2);
		int n = Math.max(8, Math.min(40, (int) (reach * 0.35)));
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n + t;
			level.sendParticles(i % 2 == 0 ? ParticleTypes.EXPLOSION : ParticleTypes.CLOUD,
					target.x + Math.cos(a) * reach, target.y + 1, target.z + Math.sin(a) * reach, 1, 0.5, 0.5, 0.5, 0);
		}
		SkyFx.strike(level, player, target, reach, damage, burstHit);
		// Les créatures encore en l'air dans la tornade.
		SkyFx.strike(level, player, target.add(0, 25, 0), Math.min(radius, 40), damage, burstHit);
		hit.addAll(burstHit);
	}

	private final Set<UUID> burstHit = new HashSet<>();
}
