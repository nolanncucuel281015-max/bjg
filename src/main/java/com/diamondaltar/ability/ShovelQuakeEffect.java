package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.world.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Pelle — « Onde Sismique » : le joueur frappe le sol, une onde de choc circulaire
 * s'étend jusqu'à 8 blocs en soulevant la terre et en projetant les ennemis en l'air.
 */
public class ShovelQuakeEffect extends TimedEffect {
	private static final int DURATION = 12;
	private static final double RANGE = 8.0;

	private final ServerPlayer player;
	private final Vec3 center;
	private final Set<UUID> alreadyHit = new HashSet<>();
	private final java.util.List<Display.BlockDisplay> wave = new java.util.ArrayList<>();
	private final java.util.List<Integer> born = new java.util.ArrayList<>();
	private static final Quaternionf NONE = new Quaternionf();

	public ShovelQuakeEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.center = player.position();
		level.playSound(null, player.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.5f, 0.7f);
	}

	@Override
	protected boolean tick() {
		// Chaque bloc de la vague monte, puis redescend et disparaît.
		for (int i = 0; i < wave.size(); i++) {
			int life = age - born.get(i);
			if (life == 1) {
				Visuals.transform(wave.get(i), new Vector3f(-0.5f, -0.2f, -0.5f), NONE, 1f, 2);
			} else if (life == 4) {
				Visuals.transform(wave.get(i), new Vector3f(-0.5f, -1.05f, -0.5f), NONE, 1f, 3);
			} else if (life == 8) {
				wave.get(i).discard();
			}
		}
		if (age >= DURATION) {
			return age >= DURATION + 9;
		}
		double radius = 1.0 + (RANGE - 1.0) * (age / (double) (DURATION - 1));
		int points = (int) (radius * 7);
		for (int i = 0; i < points; i++) {
			double a = Math.PI * 2 * i / points;
			double x = center.x + Math.cos(a) * radius;
			double z = center.z + Math.sin(a) * radius;
			BlockState state = level.getBlockState(BlockPos.containing(x, center.y - 0.5, z));
			if (!state.isAir() && i % 2 == 0) {
				Display.BlockDisplay b = track(Visuals.block(level, new Vec3(Math.floor(x) + 0.5, Math.floor(center.y - 0.5) + 1, Math.floor(z) + 0.5), state));
				Visuals.transform(b, new Vector3f(-0.5f, -1.05f, -0.5f), NONE, 1f, 0);
				wave.add(b);
				born.add(age);
			}
			if (!state.isAir()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
						x, center.y + 0.1, z, 4, 0.2, 0.4, 0.2, 0.15);
			}
			if (i % 3 == 0) {
				level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, center.y + 0.2, z, 0, 0, 0.05, 0, 1);
			}
		}
		for (LivingEntity target : FX.livingAround(level, player, center, radius + 0.5)) {
			if (alreadyHit.add(target.getUUID())) {
				FX.hit(level, player, target, 14.0f);
				Vec3 out = target.position().subtract(center).multiply(1, 0, 1).normalize().scale(0.5);
				FX.push(target, out.x, 1.0, out.z);
			}
		}
		if (age % 3 == 0) {
			level.playSound(null, BlockPos.containing(center), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.5f, 0.5f);
		}
		return false;
	}
}
