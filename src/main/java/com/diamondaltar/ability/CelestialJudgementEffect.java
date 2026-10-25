package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.world.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Épée — « Jugement Céleste » : un cercle sacré apparaît là où tu vises, puis un pilier
 * de lumière géant tombe du ciel et pulvérise tout dans un rayon de 5 blocs.
 */
public class CelestialJudgementEffect extends TimedEffect {
	private static final int CHARGE = 25;
	private static final double RADIUS = 5.0;
	private static final float DAMAGE = 40.0f;
	private static final DustParticleOptions GOLD = new DustParticleOptions(0xFFD84A, 1.8f);

	private final ServerPlayer player;
	private final Vec3 center;
	private final Display.ItemDisplay giant;
	private static final float GIANT = 12.0f;
	private static final double SKY = 40.0;

	public CelestialJudgementEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.center = FX.ground(level, FX.target(player, 40));
		this.giant = track(Visuals.item(level, center, player.getMainHandItem()));
		Visuals.transform(giant, new Vector3f(0, (float) SKY, 0), DOWN, GIANT, 0);
		level.playSound(null, BlockPos.containing(center), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0f, 0.5f);
	}

	private boolean sink() {
		if (age == CHARGE + 15) {
			Visuals.transform(giant, new Vector3f(0, -GIANT * 0.6f, 0), DOWN, GIANT * 0.6f, 7);
		}
		level.sendParticles(ParticleTypes.END_ROD, center.x, center.y + 0.5, center.z, 3, 0.6, 0.2, 0.6, 0.02);
		return false;
	}

	/** Pointe vers le bas. */
	private static final Quaternionf DOWN = Visuals.pointing(new Vec3(0, -1, 0), 0f);

	@Override
	protected boolean tick() {
		if (age < CHARGE) {
			double t = age / (double) CHARGE;
			// L'épée tombe de plus en plus vite (accélération), pointe vers le bas.
			double fall = SKY * (1 - t * t) + GIANT * 0.45;
			Visuals.transform(giant, new Vector3f(0, (float) fall, 0), new Quaternionf(DOWN).rotateY((float) (t * 2)), GIANT, 1);
			Vec3 c = center.add(0, 0.1, 0);
			FX.ring(level, GOLD, c, RADIUS, 40);
			FX.ring(level, ParticleTypes.END_ROD, c, RADIUS * (1 - t), 24);
			// Croix sacrée au sol qui tourne.
			double rot = age * 0.15;
			for (double r = 0; r < RADIUS; r += 0.5) {
				for (int k = 0; k < 4; k++) {
					double a = rot + k * Math.PI / 2;
					level.sendParticles(GOLD, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
				}
			}
			// Fin rayon qui descend du ciel.
			for (double y = 0; y < 25; y += 1.5) {
				level.sendParticles(ParticleTypes.END_ROD, center.x, center.y + 25 - y * t, center.z, 1, 0, 0, 0, 0);
			}
			if (age % 5 == 0) {
				level.playSound(null, BlockPos.containing(center), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 2.0f, 0.8f + (float) t);
			}
			return false;
		}
		if (age == CHARGE) {
			Visuals.transform(giant, new Vector3f(0, GIANT * 0.3f, 0), DOWN, GIANT, 1);
			for (int i = 0; i < 3; i++) {
				LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
				bolt.setPos(center.x + (i - 1) * 1.5, center.y, center.z + (i % 2) * 1.5);
				bolt.setVisualOnly(true);
				level.addFreshEntity(bolt);
			}
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 1, center.z, 1, 0, 0, 0, 0);
			for (LivingEntity target : FX.livingAround(level, player, center, RADIUS + 1)) {
				FX.hit(level, player, target, DAMAGE);
				FX.push(target, 0, 1.2, 0);
			}
			level.playSound(null, BlockPos.containing(center), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0f, 0.8f);
			FX.shake(level, center, 32, 1.6f, 14);
			level.playSound(null, BlockPos.containing(center), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.5f, 1.5f);
		}
		if (age > CHARGE + 6) {
			return age >= CHARGE + 22 || sink();
		}
		// Pilier de lumière.
		for (double y = 0; y < 30; y += 0.6) {
			double a = y * 0.8 + age;
			level.sendParticles(ParticleTypes.END_ROD, center.x + Math.cos(a) * 1.2, center.y + y, center.z + Math.sin(a) * 1.2, 1, 0, 0, 0, 0);
			level.sendParticles(GOLD, center.x, center.y + y, center.z, 1, 0.3, 0, 0.3, 0);
		}
		FX.ring(level, ParticleTypes.FIREWORK, center.add(0, 0.2, 0), RADIUS * (age - CHARGE + 1) / 6.0, 36);
		return false;
	}
}
