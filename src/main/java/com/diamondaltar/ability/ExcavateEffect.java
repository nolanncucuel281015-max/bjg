package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/** Pelle — « Excavation » : creuse une zone de 5x5 sur 3 de profondeur dans la terre, le sable ou le gravier devant toi. */
public class ExcavateEffect extends TimedEffect {
	private static final int DEPTH = 3;

	private final ServerPlayer player;
	private final BlockPos center;

	public ExcavateEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		Direction facing = player.getDirection();
		this.center = player.blockPosition().relative(facing, 3).below();
		level.playSound(null, center, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.5f, 0.8f);
	}

	@Override
	protected boolean tick() {
		if (age % 3 != 0) {
			return false;
		}
		int layer = age / 3;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				BlockPos pos = center.offset(dx, -layer, dz);
				BlockState state = level.getBlockState(pos);
				if (state.is(BlockTags.MINEABLE_WITH_SHOVEL) && FX.canMine(level, player, pos, state)) {
					level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 1, 0.2, 0.1, 0.2, 0.02);
					level.destroyBlock(pos, true, player);
				}
			}
		}
		level.playSound(null, center, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1.2f, 0.7f);
		return layer >= DEPTH - 1;
	}
}
