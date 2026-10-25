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

/** Hache — « Fissure » : un coup de hache titanesque fend le sol sur 14 blocs en ligne droite. */
public class FissureEffect extends TimedEffect {
	private static final int LENGTH = 14;
	private static final float DAMAGE = 24.0f;

	private final ServerPlayer player;
	private final Vec3 start;
	private final Vec3 forward;
	private final Set<UUID> hit = new HashSet<>();
	private final java.util.List<Display.BlockDisplay> rocks = new java.util.ArrayList<>();
	private final java.util.List<Quaternionf> tilts = new java.util.ArrayList<>();
	private final java.util.List<Integer> born = new java.util.ArrayList<>();

	public FissureEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.start = player.position();
		this.forward = FX.forward(player);
		level.playSound(null, player.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.5f, 0.6f);
	}

	private static Vector3f base(Quaternionf q, float s, float dy) {
		return new Vector3f(-0.5f * s, dy, -0.5f * s).rotate(q);
	}

	@Override
	protected boolean tick() {
		for (int i = 0; i < rocks.size(); i++) {
			int life = age - born.get(i);
			if (life == 1) {
				Visuals.transform(rocks.get(i), base(tilts.get(i), 1.1f, -0.3f), tilts.get(i), 1.1f, 2);
			} else if (life == 25) {
				Visuals.transform(rocks.get(i), base(tilts.get(i), 1.1f, -1.4f), tilts.get(i), 1.1f, 8);
			}
		}
		if (age >= LENGTH) {
			return age >= LENGTH + 35;
		}
		Vec3 right = FX.right(forward);
		Vec3 p = FX.ground(level, start.add(forward.scale(1 + age)).add(0, 2, 0));
		BlockState ground = level.getBlockState(BlockPos.containing(p.x, p.y - 0.5, p.z));
		for (int side = -1; side <= 1; side += 2) {
			Vec3 s = p.add(right.scale(side * 0.4));
			if (!ground.isAir()) {
				// Rocher incliné vers l'extérieur de la fissure.
				Quaternionf tilt = new Quaternionf().rotateY((float) Math.atan2(forward.x, forward.z))
						.rotateZ(side * 0.45f + (level.getRandom().nextFloat() - 0.5f) * 0.3f);
				Display.BlockDisplay rock = track(Visuals.block(level, s, ground));
				Visuals.transform(rock, base(tilt, 1.1f, -1.4f), tilt, 1.1f, 0);
				rocks.add(rock);
				tilts.add(tilt);
				born.add(age);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), s.x, s.y + 0.1, s.z, 10, 0.15, 0.5, 0.15, 0.3);
			}
		}
		level.sendParticles(ParticleTypes.LAVA, p.x, p.y + 0.1, p.z, 2, 0.1, 0, 0.1, 0);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, p.y + 0.1, p.z, 2, 0.1, 0, 0.1, 0.02);
		level.sendParticles(ParticleTypes.CRIT, p.x, p.y + 0.5, p.z, 6, 0.2, 0.5, 0.2, 0.3);
		for (LivingEntity target : FX.livingAround(level, player, p.add(0, 0.5, 0), 2.0)) {
			if (hit.add(target.getUUID())) {
				FX.hit(level, player, target, DAMAGE);
				FX.push(target, right.x * 0.4, 1.0, right.z * 0.4);
			}
		}
		if (age % 2 == 0) {
			level.playSound(null, BlockPos.containing(p), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.5f, 0.5f);
		}
		return false;
	}
}
