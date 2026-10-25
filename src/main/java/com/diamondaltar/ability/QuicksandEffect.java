package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Pelle — « Sables Mouvants » : un tourbillon de sable de 6 blocs piège et engloutit les ennemis pendant 5 s. */
public class QuicksandEffect extends TimedEffect {
	private static final int DURATION = 100;
	private static final double RADIUS = 6.0;

	private final ServerPlayer player;
	private final Vec3 center;

	public QuicksandEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.center = FX.ground(level, FX.target(player, 14).add(0, 1, 0));
		level.playSound(null, BlockPos.containing(center), SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 2.0f, 0.5f);
	}

	@Override
	protected boolean tick() {
		BlockParticleOption sand = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
		BlockParticleOption dust = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());
		for (int arm = 0; arm < 4; arm++) {
			double base = age * 0.25 + arm * Math.PI / 2;
			for (double r = 0.5; r < RADIUS; r += 0.6) {
				double a = base + r * 0.5;
				level.sendParticles(sand, center.x + Math.cos(a) * r, center.y + 0.1, center.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
			}
		}
		level.sendParticles(dust, center.x, center.y + 2, center.z, 6, RADIUS * 0.6, 0.5, RADIUS * 0.6, 0);

		for (LivingEntity target : FX.livingAround(level, player, center, RADIUS)) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 4));
			target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 20, 2));
			FX.pull(target, center.add(0, -1, 0), 0.12);
			if (age % 10 == 0) {
				FX.hit(level, player, target, 4.0f);
			}
		}
		if (age % 15 == 0) {
			level.playSound(null, BlockPos.containing(center), SoundEvents.SAND_STEP, SoundSource.PLAYERS, 2.0f, 0.5f);
		}
		return age >= DURATION;
	}
}
