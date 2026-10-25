package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Pioche — « Rayon Stellaire » : un laser d'énergie stellaire sort de ta pioche pendant 2 s,
 * perce la roche sur 24 blocs devant toi et brûle les ennemis touchés.
 */
public class StellarBeamEffect extends TimedEffect {
	private static final int DURATION = 40;
	private static final double LENGTH = 24.0;

	private final ServerPlayer player;
	private double reach;

	public StellarBeamEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 2.0f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved()) {
			return true;
		}
		Vec3 dir = player.getLookAngle();
		Vec3 from = player.getEyePosition().add(dir.scale(0.8)).subtract(0, 0.25, 0);
		reach = Math.min(LENGTH, reach + 1.5);
		// Le rayon avance et creuse jusqu'à sa tête.
		for (double d = 0; d <= reach; d += 0.5) {
			Vec3 p = from.add(dir.scale(d));
			BlockPos pos = BlockPos.containing(p);
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && FX.canMine(level, player, pos, state)) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), p.x, p.y, p.z, 6, 0.2, 0.2, 0.2, 0.1);
				level.destroyBlock(pos, true, player);
			}
		}
		// Visuel : cœur blanc + spirale violette autour.
		Vec3 a = FX.right(FX.forward(player));
		Vec3 b = a.cross(dir).normalize();
		for (double d = 0; d <= reach; d += 0.4) {
			Vec3 p = from.add(dir.scale(d));
			Cosmic.at(level, Cosmic.MOON_SMALL, p);
			double ang = d * 1.4 - age * 0.6;
			Cosmic.at(level, Cosmic.NEBULA, p.add(a.scale(Math.cos(ang) * 0.35)).add(b.scale(Math.sin(ang) * 0.35)));
		}
		Vec3 tip = from.add(dir.scale(reach));
		if (age % 3 == 0) {
			Glow.beam(level, from, from.add(dir.scale(reach)), 0.22f, Glow.Tint.NEBULA, 2);
		}
		level.sendParticles(ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 4, 0.2, 0.2, 0.2, 0.08);
		Cosmic.nebula(level, tip, 0.4, 4);

		if (age % 4 == 0) {
			for (double d = 0; d <= reach; d += 1.0) {
				for (LivingEntity target : FX.livingAround(level, player, from.add(dir.scale(d)), 1.2)) {
					FX.hit(level, player, target, 7.0f);
					target.igniteForSeconds(2);
				}
			}
			level.playSound(null, player.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0f, 2.0f);
		}
		return age >= DURATION;
	}
}
