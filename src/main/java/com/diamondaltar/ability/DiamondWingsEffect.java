package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * Armure — « Ailes de Diamant » : deux grandes ailes de lumière te propulsent dans les airs,
 * puis tu planes doucement pendant 10 s.
 */
public class DiamondWingsEffect extends TimedEffect {
	private static final int DURATION = 200;
	private static final DustParticleOptions FEATHER = new DustParticleOptions(0xBFF8FF, 1.3f);
	private static final DustParticleOptions EDGE = new DustParticleOptions(0x40D8FF, 1.6f);

	private final ServerPlayer player;

	public DiamondWingsEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		Vec3 look = player.getLookAngle();
		player.setDeltaMovement(look.x * 1.2, 1.6, look.z * 1.2);
		player.hurtMarked = true;
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, DURATION, 0, false, false, true));
		level.playSound(null, player.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 2.0f, 0.6f);
		level.playSound(null, player.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.6f, 1.5f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved() || !player.isAlive()) {
			return true;
		}
		player.fallDistance = 0;
		if (age > 10 && player.onGround()) {
			return true;
		}
		// Deux ailes derrière le joueur qui battent lentement.
		double yaw = Math.toRadians(player.getYRot());
		Vec3 back = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw)).scale(0.4);
		Vec3 side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
		double flap = Math.sin(age * 0.35) * 0.5;
		Vec3 root = player.position().add(0, 1.35, 0).add(back);
		if (age % 2 == 0) {
			for (int w = -1; w <= 1; w += 2) {
				for (double t = 0.2; t <= 2.4; t += 0.2) {
					double lift = flap * t * 0.5 + t * 0.35;
					for (double f = 0; f < 1.0 - t / 3.0; f += 0.25) {
						Vec3 p = root.add(side.scale(w * t)).add(0, lift - f, 0).add(back.scale(t * 0.3));
						level.sendParticles(f == 0 ? EDGE : FEATHER, p.x, p.y, p.z, 1, 0, 0, 0, 0);
					}
				}
			}
			level.sendParticles(ParticleTypes.END_ROD, root.x, root.y - 0.5, root.z, 1, 0.3, 0.1, 0.3, 0.01);
		}
		if (age % 16 == 0) {
			level.playSound(null, player.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.8f, 1.2f);
		}
		return age >= DURATION;
	}
}
