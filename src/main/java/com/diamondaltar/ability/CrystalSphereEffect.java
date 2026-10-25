package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Pioche — « Sphère de Cristal » : fait éclater une sphère de 5 blocs de diamètre là où tu vises. */
public class CrystalSphereEffect extends TimedEffect {
	private static final double RADIUS = 2.6;

	private final ServerPlayer player;
	private final BlockPos center;

	public CrystalSphereEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		Vec3 t = FX.target(player, 8);
		Vec3 inward = t.add(player.getLookAngle().scale(0.2));
		this.center = BlockPos.containing(inward);
		level.playSound(null, center, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 0.8f);
	}

	@Override
	protected boolean tick() {
		// La sphère grandit couche par couche depuis le centre.
		double inner = age * 0.6;
		double outer = inner + 0.6;
		int r = (int) Math.ceil(RADIUS);
		for (BlockPos m : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
			double d = Math.sqrt(m.distSqr(center));
			if (d < inner || d >= outer || d > RADIUS) {
				continue;
			}
			BlockPos pos = m.immutable();
			BlockState state = level.getBlockState(pos);
			if (FX.canMine(level, player, pos, state)) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.1);
				level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0.05);
				level.destroyBlock(pos, true, player);
			}
		}
		level.playSound(null, center, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f + age * 0.2f);
		return outer > RADIUS;
	}
}
