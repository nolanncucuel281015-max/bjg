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
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** Houe — « Lianes Étrangleuses » : des lianes sortent du sol et immobilisent tous les ennemis à 10 blocs pendant 4 s. */
public class VinesEffect extends TimedEffect {
	private static final int DURATION = 80;
	private static final DustParticleOptions VINE = new DustParticleOptions(0x2FA02F, 1.5f);
	private static final DustParticleOptions THORN = new DustParticleOptions(0x7CFF3C, 0.9f);

	private final ServerPlayer player;
	private final List<LivingEntity> targets;

	public VinesEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.targets = FX.livingAround(level, player, player.position(), 10);
		level.playSound(null, player.blockPosition(), SoundEvents.CAVE_VINES_PLACE, SoundSource.PLAYERS, 2.0f, 0.5f);
		level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 1.0f, 0.8f);
	}

	@Override
	protected boolean tick() {
		for (LivingEntity target : targets) {
			if (!target.isAlive()) {
				continue;
			}
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 9));
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 10, 1));
			target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
			target.hurtMarked = true;
			// Lianes en spirale qui s'enroulent autour de la cible.
			double h = Math.min(target.getBbHeight(), age / 10.0 * target.getBbHeight());
			for (int k = 0; k < 3; k++) {
				for (double y = 0; y < h; y += 0.25) {
					double a = y * 3 + k * (Math.PI * 2 / 3) + age * 0.1;
					double r = target.getBbWidth() * 0.7;
					level.sendParticles(VINE, target.getX() + Math.cos(a) * r, target.getY() + y, target.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
				}
			}
			if (age % 4 == 0) {
				level.sendParticles(THORN, target.getX(), target.getY(0.5), target.getZ(), 3, 0.3, 0.5, 0.3, 0);
			}
			if (age % 10 == 0) {
				FX.hit(level, player, target, 5.0f);
				level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, target.getX(), target.getY(1.0), target.getZ(), 4, 0.3, 0.3, 0.3, 0);
			}
		}
		return age >= DURATION;
	}
}
