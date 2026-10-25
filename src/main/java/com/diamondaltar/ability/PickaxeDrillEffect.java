package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import net.minecraft.block.BlockState;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Pioche — « Foreuse de Diamant » : creuse un tunnel 3x3 de 8 blocs de profondeur
 * dans la direction du regard, couche par couche, avec une onde de cristal.
 */
public class PickaxeDrillEffect extends TimedEffect {
	private static final int DEPTH = 8;

	private final ServerPlayerEntity player;
	private final Direction direction;
	private final Direction sideA;
	private final Direction sideB;
	private final BlockPos start;

	public PickaxeDrillEffect(ServerWorld world, ServerPlayerEntity player) {
		super(world);
		this.player = player;
		this.direction = Direction.getEntityFacingOrder(player)[0];
		if (direction.getAxis() == Direction.Axis.Y) {
			sideA = Direction.NORTH;
			sideB = Direction.EAST;
		} else {
			sideA = Direction.UP;
			sideB = direction.rotateYClockwise();
		}
		BlockPos base = direction.getAxis() == Direction.Axis.Y ? player.getBlockPos() : player.getBlockPos().up();
		this.start = base.offset(direction);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.0f, 1.6f);
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
		BlockPos center = start.offset(direction, layer);
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				BlockPos pos = center.offset(sideA, a).offset(sideB, b);
				BlockState state = world.getBlockState(pos);
				if (state.isAir()) {
					continue;
				}
				world.spawnParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
						3, 0.3, 0.3, 0.3, 0.02);
				if (canBreak(pos, state)) {
					world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
							pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.1);
					world.breakBlock(pos, true, player);
				}
			}
		}
		world.playSound(null, center, SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.PLAYERS, 1.0f, 0.6f + layer * 0.1f);
		return layer >= DEPTH - 1;
	}

	private boolean canBreak(BlockPos pos, BlockState state) {
		float hardness = state.getHardness(world, pos);
		return hardness >= 0 && hardness < 50
				&& (state.isIn(BlockTags.PICKAXE_MINEABLE) || state.isIn(BlockTags.SHOVEL_MINEABLE))
				&& player.canModifyBlocks() && world.canPlayerModifyAt(player, pos);
	}
}
