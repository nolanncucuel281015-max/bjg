package com.diamondaltar.realms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** L'autel garde en mémoire si son boss a déjà été réveillé (une seule fois par sanctuaire). */
public class RealmAltarBlockEntity extends BlockEntity {
	private boolean summoned;

	public RealmAltarBlockEntity(BlockPos pos, BlockState state) {
		super(RealmContent.REALM_ALTAR_ENTITY, pos, state);
	}

	public boolean summoned() {
		return summoned;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, RealmAltarBlockEntity altar) {
		if (!(level instanceof ServerLevel server) || altar.summoned || level.getGameTime() % 10 != 0) {
			return;
		}
		Realm realm = Realm.values()[state.getValue(RealmAltarBlock.REALM)];
		server.sendParticles(new DustParticleOptions(realm.color, 1.6f), pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5, 6, 0.3, 0.5, 0.3, 0);
		server.sendParticles(RealmBoss.ambient(realm), pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 2, 0.3, 0.4, 0.3, 0.01);
		Player p = level.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 14, false);
		if (p != null && !p.isSpectator() && !p.isCreative()) {
			altar.summoned = true;
			altar.setChanged();
			RealmBoss.summon(server, realm, Vec3.atBottomCenterOf(pos.above()));
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
