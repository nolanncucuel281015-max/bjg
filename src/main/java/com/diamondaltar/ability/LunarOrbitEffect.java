package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Hache — « Orbite Lunaire » : deux lunes miniatures tournent autour de toi pendant 8 s
 * et écrasent tous les ennemis qu'elles percutent.
 */
public class LunarOrbitEffect extends TimedEffect {
	private static final int DURATION = 160;
	private static final double ORBIT = 3.2;

	private final ServerPlayer player;
	private final Display.BlockDisplay[] moons = new Display.BlockDisplay[2];

	public LunarOrbitEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		for (int i = 0; i < moons.length; i++) {
			moons[i] = track(Visuals.block(level, player.position(), Blocks.END_STONE.defaultBlockState()));
			Visuals.transform(moons[i], new org.joml.Vector3f(), new Quaternionf(), 0.01f, 0);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.5f, 0.6f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved() || !player.isAlive()) {
			return true;
		}
		Vec3 c = player.position().add(0, 1.0, 0);
		boolean ending = age >= DURATION - 6;
		for (int i = 0; i < moons.length; i++) {
			double a = age * 0.22 + i * Math.PI;
			double tilt = i == 0 ? 0.35 : -0.35;
			Vec3 at = c.add(Math.cos(a) * ORBIT, Math.sin(a) * ORBIT * tilt, Math.sin(a) * ORBIT);
			float s = ending ? 0.01f : 1.1f;
			Quaternionf q = new Quaternionf().rotateXYZ(age * 0.1f, age * 0.07f, i);
			moons[i].setPos(at);
			Visuals.transform(moons[i], Visuals.centered(q, s), q, s, ending ? 6 : 1);
			// Traînée d'étoiles derrière chaque lune.
			Cosmic.at(level, Cosmic.MOON_TO_NEBULA, at);
			Cosmic.star(level, at.add((level.getRandom().nextDouble() - 0.5), 0, (level.getRandom().nextDouble() - 0.5)));
			if (age % 4 == 0) {
				for (LivingEntity target : FX.livingAround(level, player, at, 1.6)) {
					FX.hit(level, player, target, 12.0f);
					Vec3 out = target.position().subtract(c).normalize().scale(1.2);
					FX.push(target, out.x, 0.5, out.z);
					level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY(0.5), target.getZ(), 1, 0, 0, 0, 0);
				}
			}
		}
		if (age % 20 == 0) {
			level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 0.5f);
		}
		return age >= DURATION;
	}
}
