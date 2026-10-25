package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Armure — « Saut Astral » : un vortex galactique t'aspire et te recrache jusqu'à 40 blocs
 * plus loin, là où tu regardes.
 */
public class AstralJumpEffect extends TimedEffect {
	private final ServerPlayer player;
	private final Vec3 from;
	private Vec3 to;

	public AstralJumpEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.from = player.position();
		HitResult hit = player.pick(40, 0f, false);
		Vec3 dest = hit.getLocation();
		if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos above = bhr.getBlockPos().relative(bhr.getDirection());
			dest = Vec3.atBottomCenterOf(above);
		}
		this.to = dest;
		level.playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.4f, 2.0f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved()) {
			return true;
		}
		vortex(from, age);
		if (age == 6) {
			player.teleportTo(to.x, to.y, to.z);
			player.fallDistance = 0;
			level.playSound(null, BlockPos.containing(to), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.5f, 0.5f);
			Cosmic.line(level, Cosmic.MOON_TO_NEBULA, from.add(0, 1, 0), to.add(0, 1, 0), 0.6);
		}
		if (age >= 6) {
			vortex(to, 12 - age);
		}
		return age >= 12;
	}

	/** Un disque de nébuleuse qui tourne, de plus en plus serré. */
	private void vortex(Vec3 c, int phase) {
		for (int arm = 0; arm < 4; arm++) {
			for (double r = 0.3; r < 2.5; r += 0.25) {
				double a = arm * Math.PI / 2 + r * 1.5 - age * 0.6;
				Cosmic.at(level, arm % 2 == 0 ? Cosmic.NEBULA : Cosmic.NEBULA_PINK, c.add(Math.cos(a) * r, 1.0 + (2.5 - r) * 0.3, Math.sin(a) * r));
			}
		}
		Cosmic.starfield(level, c.add(0, 1, 0), 1.5, 8);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 1, c.z, 20, 0.3, 0.6, 0.3, 0.3);
	}
}
