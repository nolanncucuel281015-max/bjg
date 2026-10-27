package com.diamondaltar.ability;

import com.diamondaltar.Compat;
import com.diamondaltar.effect.TimedEffect;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Masse — « Puits de Gravité » : aspire et soulève tous les ennemis à 12 blocs au-dessus de toi,
 * puis les écrase au sol d'un coup.
 */
public class GravityWellEffect extends TimedEffect {
	private static final int LIFT = 30;
	private static final DustParticleOptions INDIGO = new DustParticleOptions(0x4B3BFF, 1.6f);

	private final ServerPlayer player;
	private final List<LivingEntity> targets;

	public GravityWellEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.targets = FX.livingAround(level, player, player.position(), 12);
		level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.5f, 1.2f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved()) {
			return true;
		}
		Vec3 point = player.position().add(0, 4, 0);
		// Spirale violette qui monte autour du joueur.
		for (int k = 0; k < 4; k++) {
			double a = age * 0.5 + k * Math.PI / 2;
			double r = 3.0 - age * 0.06;
			level.sendParticles(INDIGO, player.getX() + Math.cos(a) * r, player.getY() + age * 0.13, player.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
		}
		if (age < LIFT) {
			for (LivingEntity t : targets) {
				if (t.isAlive()) {
					FX.pull(t, point, 0.35);
					t.fallDistance = 0;
					level.sendParticles(ParticleTypes.REVERSE_PORTAL, t.getX(), t.getY(0.5), t.getZ(), 2, 0.3, 0.4, 0.3, 0);
				}
			}
			return false;
		}
		// L'écrasement.
		for (LivingEntity t : targets) {
			if (t.isAlive()) {
				t.setDeltaMovement(0, -3.0, 0);
				Compat.markHurt(t);
				FX.hit(level, player, t, 26.0f);
				level.sendParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY(0.5), t.getZ(), 1, 0, 0, 0, 0);
			}
		}
		FX.ring(level, ParticleTypes.SONIC_BOOM, player.position().add(0, 1, 0), 3, 8);
		level.playSound(null, player.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 2.0f, 0.4f);
		FX.shake(level, player.position(), 24, 1.3f, 10);
		return true;
	}
}
