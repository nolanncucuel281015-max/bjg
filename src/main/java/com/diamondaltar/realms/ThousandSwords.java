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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Épée des Sept Royaumes — « Mille Épées » :
 * <ol>
 * <li><b>Invocation</b> : un cercle d'épées géantes (l'Épée des Sept Royaumes elle-même) apparaît dans le ciel
 * au-dessus du bloc visé et tourne, pointe vers le bas ;</li>
 * <li><b>Pluie d'épées</b> : les épées plongent une à une sur chaque créature de la portée, explosent au sol ;</li>
 * <li><b>L'Épée Colossale</b> : une épée de cinquante blocs descend du ciel et se plante dans le bloc visé —
 * flash doré, onde de choc et <b>cinq fois les dégâts choisis</b>.</li>
 * </ol>
 * Les joueurs ne sont jamais touchés ; la bedrock et les coffres ne sont jamais détruits.
 */
public final class ThousandSwords extends TimedEffect {
	private static final int SUMMON = 30;
	private static final int RAIN = 60;
	private static final int DESCEND = 20;
	private static final int IMPACT = 40;
	private static final int CIRCLE = 12;
	private static final int FALL = 10;
	private static final double SKY = 45;
	private static final float SWORD_SIZE = 7f;
	private static final float COLOSSUS = 50f;
	private static final int WAVE = 24;
	/** Tourne l'épée (dessinée en diagonale, pointe en haut à droite) pour que sa pointe vise le bas. */
	private static final Quaternionf POINT_DOWN = new Quaternionf().rotationZ((float) (-Math.PI * 3 / 4));

	private final ServerPlayer player;
	private final int rangeLevel;
	private final double radius;
	private final float damage;
	private final int damageLevel;
	private final Vec3 target;
	private final Vec3 sky;
	private final Set<UUID> hit = new HashSet<>();
	private final Set<UUID> finalHit = new HashSet<>();
	private final Display.ItemDisplay[] circle = new Display.ItemDisplay[CIRCLE];
	private final List<Falling> falling = new ArrayList<>();
	private final Display.BlockDisplay[] wave = new Display.BlockDisplay[WAVE];
	private Display.ItemDisplay colossus;
	private double carved;
	private int dropped;

	/** Une épée qui plonge de {@code from} vers {@code to}. */
	private static final class Falling {
		final Vec3 from;
		final Vec3 to;
		final Display.ItemDisplay sword;
		int t;

		Falling(Vec3 from, Vec3 to, Display.ItemDisplay sword) {
			this.from = from;
			this.to = to;
			this.sword = sword;
		}
	}

	public ThousandSwords(ServerLevel level, ServerPlayer player, int rangeLevel, int damageLevel) {
		super(level);
		this.player = player;
		this.rangeLevel = SkyFx.clamp(rangeLevel);
		this.radius = SkyFx.range(this.rangeLevel);
		this.damage = SkyFx.damage(damageLevel) * 5;
		this.damageLevel = damageLevel;
		Vec3 ahead = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(30));
		this.target = SkyFx.aim(level, player, ahead, radius);
		this.sky = target.add(0, SKY, 0);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, SUMMON + RAIN + DESCEND + IMPACT + FALL, 4, false, false));
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 4f, 0.8f);
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

	private Display.ItemDisplay sword(Vec3 pos, float size) {
		Display.ItemDisplay d = track(Visuals.item(level, pos, new ItemStack(RealmContent.SEVEN_REALMS_SWORD)));
		d.setGlowingTag(true);
		d.setGlowColorOverride(0xFFD966);
		d.setViewRange(8f);
		point(d, size, 1);
		return d;
	}

	private static void point(Display d, float size, int ticks) {
		Visuals.transform(d, new Vector3f(), new Quaternionf(POINT_DOWN), new Vector3f(size, size, size * 0.3f), ticks);
	}

	@Override
	protected boolean tick() {
		dropSwords();
		if (age < SUMMON) {
			summon(age);
		} else if (age < SUMMON + RAIN) {
			rain(age - SUMMON);
		} else if (age < SUMMON + RAIN + DESCEND) {
			descend(age - SUMMON - RAIN);
		} else if (age < SUMMON + RAIN + DESCEND + IMPACT) {
			impact(age - SUMMON - RAIN - DESCEND);
		} else {
			return falling.isEmpty();
		}
		return false;
	}

	private void spinCircle(int t, float size) {
		for (int i = 0; i < CIRCLE; i++) {
			if (circle[i] == null) {
				continue;
			}
			double a = t * 0.06 + Math.PI * 2 * i / CIRCLE;
			circle[i].setPos(sky.add(Math.cos(a) * 18, Math.sin(t * 0.15 + i) * 1.5, Math.sin(a) * 18));
			point(circle[i], size, 2);
		}
	}

	// ================================================================ 1. invocation

	private void summon(int t) {
		if (t % 2 == 0 && t / 2 < CIRCLE) {
			int i = t / 2;
			circle[i] = sword(sky, 0.1f);
			level.sendParticles(ModParticles.GLOW_GOLD, sky.x, sky.y, sky.z, 20, 10, 2, 10, 0.1);
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 3f, 0.5f + i * 0.1f);
		}
		spinCircle(t, SWORD_SIZE * Math.min(1f, (t + 1) / (float) SUMMON * 1.5f));
	}

	// ================================================================ 2. pluie d'épées

	private void rain(int t) {
		spinCircle(SUMMON + t, SWORD_SIZE);
		if (t % 3 != 0) {
			return;
		}
		List<LivingEntity> prey = SkyFx.creatures(level, target, radius);
		int volley = 1 + Math.min(3, prey.size());
		for (int k = 0; k < volley; k++) {
			Vec3 to;
			if (k < prey.size()) {
				to = prey.get((dropped + k) % prey.size()).position();
			} else {
				double a = level.getRandom().nextDouble() * Math.PI * 2;
				double d = Math.sqrt(level.getRandom().nextDouble()) * Math.min(radius, 100);
				to = SkyFx.groundBelow(level, target.add(Math.cos(a) * d, 0, Math.sin(a) * d));
			}
			Vec3 from = to.distanceToSqr(target) < 200 * 200 ? circle[dropped % CIRCLE].position() : to.add(0, SKY, 0);
			falling.add(new Falling(from, to, sword(from, SWORD_SIZE * 0.7f)));
			dropped++;
		}
		if (t % 9 == 0) {
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 3f, 0.6f);
		}
	}

	/** Les épées en vol plongent ; à l'arrivée : explosion, petit trou, dégâts autour. */
	private void dropSwords() {
		for (int i = falling.size() - 1; i >= 0; i--) {
			Falling f = falling.get(i);
			f.t++;
			double p = Math.min(1, f.t / (double) FALL);
			Vec3 pos = f.from.add(f.to.subtract(f.from).scale(p * p)).add(0, SWORD_SIZE * 0.35, 0);
			f.sword.setPos(pos);
			level.sendParticles(ModParticles.GLOW_GOLD, pos.x, pos.y, pos.z, 2, 0.4, 0.4, 0.4, 0.02);
			if (f.t < FALL) {
				continue;
			}
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, f.to.x, f.to.y + 1, f.to.z, 1, 0, 0, 0, 0);
			level.sendParticles(ModParticles.GLOW_GOLD, f.to.x, f.to.y + 1, f.to.z, 20, 2, 1, 2, 0.15);
			if (falling.size() < 6 || level.getRandom().nextInt(3) == 0) {
				level.playSound(null, BlockPos.containing(f.to), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 3f, 0.6f);
			}
			SkyFx.carve(level, f.to, 2.5, 0);
			Set<UUID> once = new HashSet<>();
			SkyFx.strike(level, player, f.to, 5, damage / 5, once);
			hit.addAll(once);
			f.sword.discard();
			falling.remove(i);
		}
	}

	// ================================================================ 3. l'Épée Colossale

	private void descend(int t) {
		float p = (t + 1) / (float) DESCEND;
		if (colossus == null) {
			for (Display.ItemDisplay d : circle) {
				if (d != null) {
					d.discard();
				}
			}
			colossus = sword(target.add(0, 140, 0), COLOSSUS);
			SkyFx.flash(level, target, radius + 150, 0xFFD966, 0.5f, 15);
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 5f, 0.5f);
		}
		// Elle descend de plus en plus vite ; sa pointe arrive pile sur le bloc visé.
		double y = 140 * (1 - p * p) + COLOSSUS * 0.35;
		colossus.setPos(target.add(0, y, 0));
		level.sendParticles(ModParticles.GLOW_GOLD, target.x, target.y + y, target.z, 20, 4, 15, 4, 0.1);
		if (t % 4 == 0) {
			level.playSound(null, BlockPos.containing(player.position()), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, SoundSource.PLAYERS, 4f, 0.3f + p * 0.4f);
		}
	}

	private void impact(int t) {
		if (t == 0) {
			GiantCrater.start(level, target, damageLevel);
			for (int i = 0; i < WAVE; i++) {
				wave[i] = track(SkyFx.laser(level, target, i % 2 == 0 ? 6 : LaserBlock.WHITE));
			}
			BlockPos at = BlockPos.containing(target);
			level.playSound(null, at, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 10f, 0.4f);
			level.playSound(null, at, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 8f, 0.4f);
			level.playSound(null, at, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 8f, 0.3f);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, target.x, target.y + 2, target.z, 24, 10, 3, 10, 0);
			level.sendParticles(ModParticles.GLOW_GOLD, target.x, target.y + 4, target.z, 150, 10, 8, 10, 0.8);
			SkyFx.flash(level, target, radius + 200, 0xFFD966, 1.0f, 30);
			SkyFx.shake(level, target, radius + 200, 2.5f + rangeLevel * 0.2f, 45);
		}
		float fade = t > IMPACT - 12 ? (IMPACT - t) / 12f : 1f;
		point(colossus, COLOSSUS * fade, 1);
		if (t < 12) {
			double r = (SkyFx.crater(rangeLevel) + 6) * (t + 1) / 12.0;
			SkyFx.carve(level, target, r, carved);
			carved = r;
		}
		if (t % 2 == 1 && t != IMPACT - 1) {
			return;
		}
		double reach = Math.min(radius, (t + 2) * radius / 22.0);
		SkyFx.ring(wave, target.add(0, 0.8, 0), reach, t * 0.05, (1.8f + rangeLevel * 0.3f) * fade, 2);
		int n = Math.max(8, Math.min(40, (int) (reach * 0.35)));
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n + t;
			level.sendParticles(i % 2 == 0 ? ParticleTypes.EXPLOSION : ModParticles.GLOW_GOLD,
					target.x + Math.cos(a) * reach, target.y + 1, target.z + Math.sin(a) * reach, 1, 0.5, 0.5, 0.5, 0);
		}
		SkyFx.strike(level, player, target, reach, damage, finalHit);
		hit.addAll(finalHit);
	}
}
