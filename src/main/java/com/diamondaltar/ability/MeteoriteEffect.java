package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Pioche — « Météorite Minière » : une météorite tombe du ciel là où tu vises, creuse un cratère
 * de 7 blocs (les blocs sont récupérés) et pulvérise les ennemis.
 */
public class MeteoriteEffect extends TimedEffect {
	private static final int FALL = 20;
	private static final double RADIUS = 3.2;

	private final ServerPlayer player;
	private final Vec3 target;
	private final Vec3 start;
	private final Display.BlockDisplay rock;

	public MeteoriteEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.target = FX.target(player, 40);
		this.start = target.add(-12, 35, -6);
		this.rock = track(Visuals.block(level, start, Blocks.MAGMA_BLOCK.defaultBlockState()));
		Visuals.transform(rock, new org.joml.Vector3f(-1.5f), new Quaternionf(), 3f, 0);
		level.playSound(null, BlockPos.containing(target), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 3.0f, 0.3f);
	}

	@Override
	protected boolean tick() {
		if (age < FALL) {
			double t = (age + 1) / (double) FALL;
			Vec3 p = start.lerp(target, t * t);
			Quaternionf q = new Quaternionf().rotateXYZ(age * 0.3f, age * 0.2f, age * 0.25f);
			rock.setPos(p);
			Visuals.transform(rock, Visuals.centered(q, 3f), q, 3f, 1);
			level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 20, 1.0, 1.0, 1.0, 0.05);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 8, 0.8, 0.8, 0.8, 0.02);
			Cosmic.line(level, Cosmic.MOON_TO_NEBULA, p, p.add(start.subtract(target).normalize().scale(6)), 0.5);
			return false;
		}
		rock.discard();
		BlockPos c = BlockPos.containing(target);
		int r = (int) Math.ceil(RADIUS);
		for (BlockPos m : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
			if (m.distSqr(c) <= RADIUS * RADIUS) {
				BlockPos pos = m.immutable();
				BlockState state = level.getBlockState(pos);
				if (FX.canMine(level, player, pos, state)) {
					level.destroyBlock(pos, true, player);
				}
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, target.x, target.y, target.z, 2, 1, 0.5, 1, 0);
		level.sendParticles(ParticleTypes.LAVA, target.x, target.y, target.z, 40, 2, 1, 2, 0);
		Cosmic.sphere(level, Cosmic.MOON_TO_NEBULA, target, 5, 150);
		for (LivingEntity t : FX.livingAround(level, player, target, 6)) {
			FX.hit(level, player, t, 32.0f);
			t.igniteForSeconds(4);
			Vec3 out = t.position().subtract(target).normalize().scale(1.8);
			FX.push(t, out.x, 1.0, out.z);
		}
		level.playSound(null, c, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 3.0f, 0.4f);
		level.playSound(null, c, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 3.0f, 0.4f);
		FX.shake(level, target, 36, 2.0f, 16);
		FX.flash(level, target, 36, 0xFFC080, 0.45f, 10);
		return true;
	}
}
