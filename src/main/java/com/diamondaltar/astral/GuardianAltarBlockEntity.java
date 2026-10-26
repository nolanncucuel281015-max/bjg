package com.diamondaltar.astral;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class GuardianAltarBlockEntity extends BlockEntity {
	private boolean summoned;

	public GuardianAltarBlockEntity(BlockPos pos, BlockState state) {
		super(AstralContent.GUARDIAN_ALTAR_ENTITY, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, GuardianAltarBlockEntity altar) {
		if (!(level instanceof ServerLevel server) || altar.summoned) {
			return;
		}
		if (level.getGameTime() % 10 == 0) {
			server.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 6, 0.3, 0.4, 0.3, 0.05);
			Player p = level.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 12, false);
			if (p != null && !p.isSpectator() && !p.isCreative()) {
				altar.summoned = true;
				altar.setChanged();
				Guardian.summon(server, pos.above());
			}
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putBoolean("Summoned", summoned);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		summoned = input.getBooleanOr("Summoned", false);
	}
}
