package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pioche — « Foreuse de Diamant » : creuse un tunnel 3x3 de 8 blocs de profondeur
 * dans la direction du regard, couche par couche, avec une onde de cristal.
 */
public class PickaxeDrillEffect extends TimedEffect {
	private static final int DEPTH = 8;

	private final ServerPlayer player;
	private final Direction direction;
	private final Direction sideA;
	private final Direction sideB;
	private final BlockPos start;

	public PickaxeDrillEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.direction = Direction.orderedByNearest(player)[0];
		if (direction.getAxis() == Direction.Axis.Y) {
			sideA = Direction.NORTH;
			sideB = Direction.EAST;
		} else {
			sideA = Direction.UP;
			sideB = direction.getClockWise();
		}
		BlockPos base = direction.getAxis() == Direction.Axis.Y ? player.blockPosition() : player.blockPosition().above();
		this.start = base.relative(direction);
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.6f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved()) {
			return true;
		}
		if (age % 2 != 0) {
			return false;
		}
		int layer = age / 2;
		BlockPos center = start.relative(direction, layer);
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				BlockPos pos = center.relative(sideA, a).relative(sideB, b);
				BlockState state = level.getBlockState(pos);
				if (state.isAir()) {
					continue;
				}
				level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
						3, 0.3, 0.3, 0.3, 0.02);
				if (canBreak(pos, state)) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
							pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.1);
					level.destroyBlock(pos, true, player);
				}
			}
		}
		level.playSound(null, center, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f + layer * 0.1f);
		return layer >= DEPTH - 1;
	}

	private boolean canBreak(BlockPos pos, BlockState state) {
		float hardness = state.getDestroySpeed(level, pos);
		return hardness >= 0 && hardness < 50
				&& (state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL))
				&& player.mayBuild() && level.mayInteract(player, pos);
	}
}
