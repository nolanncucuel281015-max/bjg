package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Hache — « Hache du Titan Stellaire » : une hache colossale de 10 blocs se dresse derrière toi
 * puis s'abat devant toi, fend le sol sur 14 blocs et écrase tout.
 */
public class TitanAxeEffect extends TimedEffect {
	private static final int RAISE = 12;
	private static final int CHOP = 5;
	private static final float SIZE = 10f;

	private final ServerPlayer player;
	private final Display.ItemDisplay axe;
	private final Vec3 forward;
	private final Vec3 base;

	public TitanAxeEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.forward = FX.forward(player);
		this.base = player.position();
		this.axe = track(Visuals.item(level, base, player.getMainHandItem()));
		Visuals.transform(axe, new org.joml.Vector3f(), Visuals.pointing(new Vec3(0, 1, 0), 0f), 0.01f, 0);
		level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.5f, 0.5f);
	}

	/** Direction du manche : de vers le haut-arrière (t=0) vers l'avant-bas (t=1). */
	private Vec3 swing(double t) {
		double ang = Math.toRadians(-110 + t * 200);
		return forward.scale(Math.sin(ang)).add(0, Math.cos(ang), 0).normalize();
	}

	@Override
	protected boolean tick() {
		if (age < RAISE) {
			double t = age / (double) RAISE;
			Vec3 d = swing(0);
			float s = (float) (SIZE * t);
			Visuals.transform(axe, d.scale(s * 0.5).add(0, 1, 0).toVector3f(), Visuals.pointing(d, 0f), s, 1);
			Cosmic.starfield(level, base.add(0, 1, 0).add(d.scale(s * 0.6)), 2.0, 8);
			return false;
		}
		int f = age - RAISE;
		if (f <= CHOP) {
			double t = f / (double) CHOP;
			Vec3 d = swing(t * t);
			Visuals.transform(axe, d.scale(SIZE * 0.5).add(0, 1, 0).toVector3f(), Visuals.pointing(d, 0f), SIZE, 1);
			Cosmic.line(level, Cosmic.MOON, base.add(0, 1, 0), base.add(0, 1, 0).add(d.scale(SIZE)), 0.5);
			if (f == CHOP) {
				impact();
			}
			return false;
		}
		if (f == CHOP + 15) {
			Vec3 d = swing(1);
			Visuals.transform(axe, d.scale(SIZE * 0.6).add(0, -2, 0).toVector3f(), Visuals.pointing(d, 0f), 0.01f, 8);
		}
		return f >= CHOP + 24;
	}

	private void impact() {
		for (int i = 2; i <= 15; i++) {
			Vec3 p = FX.ground(level, base.add(forward.scale(i)).add(0, 2, 0));
			BlockState ground = level.getBlockState(BlockPos.containing(p.x, p.y - 0.5, p.z));
			if (!ground.isAir()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), p.x, p.y + 0.2, p.z, 20, 0.6, 0.4, 0.6, 0.3);
			}
			Cosmic.at(level, Cosmic.NEBULA, p.add(0, 0.3, 0));
			level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y + 0.5, p.z, 1, 0.3, 0, 0.3, 0);
			for (LivingEntity t : FX.livingAround(level, player, p.add(0, 0.5, 0), 2.5)) {
				FX.hit(level, player, t, 36.0f);
				FX.push(t, forward.x * 0.6, 1.2, forward.z * 0.6);
			}
		}
		level.playSound(null, player.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 2.5f, 0.3f);
		level.playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.PLAYERS, 1.5f, 0.5f);
		FX.shake(level, base, 30, 1.8f, 14);
	}
}
