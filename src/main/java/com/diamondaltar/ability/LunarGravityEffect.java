package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * Pelle — « Gravité Lunaire » : la gravité de la lune s'empare de la zone : les ennemis et des
 * morceaux du sol flottent en apesanteur pendant 4 s, puis tout retombe violemment.
 */
public class LunarGravityEffect extends TimedEffect {
	private static final int FLOAT = 80;
	private static final double RADIUS = 9.0;

	private final ServerPlayer player;
	private final Vec3 center;
	private final List<Display.BlockDisplay> rocks = new ArrayList<>();
	private final List<Vec3> rockPos = new ArrayList<>();
	private final List<LivingEntity> targets;

	public LunarGravityEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.center = player.position();
		this.targets = FX.livingAround(level, player, center, RADIUS);
		RandomSource r = level.getRandom();
		for (int i = 0; i < 14; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double d = 1.5 + r.nextDouble() * (RADIUS - 2);
			Vec3 p = FX.ground(level, center.add(Math.cos(a) * d, 2, Math.sin(a) * d));
			BlockState state = level.getBlockState(BlockPos.containing(p.x, p.y - 0.5, p.z));
			if (state.isAir()) {
				continue;
			}
			Display.BlockDisplay b = track(Visuals.block(level, p, state));
			Visuals.transform(b, new org.joml.Vector3f(-0.3f), new Quaternionf(), 0.6f, 0);
			rocks.add(b);
			rockPos.add(p);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0f, 0.5f);
	}

	@Override
	protected boolean tick() {
		boolean slam = age >= FLOAT;
		for (int i = 0; i < rocks.size(); i++) {
			Vec3 base = rockPos.get(i);
			double h = slam ? 0 : Math.min(4.0, age * 0.08) + Math.sin(age * 0.1 + i) * 0.3;
			Quaternionf q = new Quaternionf().rotateXYZ(age * 0.03f + i, age * 0.05f, i * 0.7f);
			rocks.get(i).setPos(base.add(0, h, 0));
			Visuals.transform(rocks.get(i), Visuals.centered(q, 0.6f), q, slam ? 0.01f : 0.6f, slam ? 4 : 1);
		}
		if (age % 2 == 0) {
			Cosmic.starfield(level, center.add(0, 2.5, 0), RADIUS, 12);
			FX.ring(level, Cosmic.STARLIGHT, center.add(0, 0.2, 0), RADIUS, 60);
		}
		if (!slam) {
			for (LivingEntity t : targets) {
				if (t.isAlive()) {
					t.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 10, 0));
					t.fallDistance = 0;
					Cosmic.at(level, Cosmic.NEBULA, t.position());
				}
			}
			if (age % 20 == 0) {
				level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 0.5f);
			}
			return false;
		}
		for (LivingEntity t : targets) {
			if (t.isAlive()) {
				t.removeEffect(MobEffects.LEVITATION);
				t.setDeltaMovement(0, -3.5, 0);
				t.hurtMarked = true;
				FX.hit(level, player, t, 24.0f);
				level.sendParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY(), t.getZ(), 1, 0, 0, 0, 0);
			}
		}
		level.playSound(null, player.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 2.0f, 0.5f);
		return age >= FLOAT + 5;
	}
}
