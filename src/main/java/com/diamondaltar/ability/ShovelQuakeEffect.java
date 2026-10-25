package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

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

	private final ServerPlayerEntity player;
	private final Vec3d center;
	private final Set<UUID> alreadyHit = new HashSet<>();

	public ShovelQuakeEffect(ServerWorld world, ServerPlayerEntity player) {
		super(world);
		this.player = player;
		this.center = player.getPos();
		world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 1.5f, 0.7f);
	}

	@Override
	protected boolean tick() {
		double radius = 1.0 + (RANGE - 1.0) * (age / (double) (DURATION - 1));
		int points = (int) (radius * 7);
		for (int i = 0; i < points; i++) {
			double a = Math.PI * 2 * i / points;
			double x = center.x + Math.cos(a) * radius;
			double z = center.z + Math.sin(a) * radius;
			BlockPos ground = BlockPos.ofFloored(x, center.y - 0.5, z);
			BlockState state = world.getBlockState(ground);
			if (!state.isAir()) {
				world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
						x, center.y + 0.1, z, 4, 0.2, 0.4, 0.2, 0.15);
			}
			if (i % 3 == 0) {
				world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, center.y + 0.2, z, 0, 0, 0.05, 0, 1);
			}
		}
		for (LivingEntity target : FX.livingAround(world, player, center, radius + 0.5)) {
			if (alreadyHit.add(target.getUuid())) {
				FX.hit(player, target, 7.0f);
				Vec3d out = target.getPos().subtract(center).multiply(1, 0, 1).normalize().multiply(0.5);
				FX.push(target, out.x, 1.0, out.z);
			}
		}
		if (age % 3 == 0) {
			world.playSound(null, BlockPos.ofFloored(center), SoundEvents.BLOCK_ROOTED_DIRT_BREAK, SoundCategory.PLAYERS, 1.5f, 0.5f);
		}
		return age >= DURATION - 1;
	}
}
