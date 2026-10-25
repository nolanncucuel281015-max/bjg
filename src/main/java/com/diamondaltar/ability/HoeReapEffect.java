package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BoneMealItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
 * Houe — « Moisson des Âmes » : une faux spectrale fait le tour du joueur (6 blocs),
 * vole la vie des ennemis touchés et fait pousser instantanément les cultures autour.
 */
public class HoeReapEffect extends TimedEffect {
	private static final int DURATION = 14;
	private static final double RADIUS = 6.0;

	private final ServerPlayerEntity player;
	private final Set<UUID> alreadyHit = new HashSet<>();
	private final double startAngle;

	public HoeReapEffect(ServerWorld world, ServerPlayerEntity player) {
		super(world);
		this.player = player;
		Vec3d f = FX.forward(player);
		this.startAngle = Math.atan2(f.z, f.x);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PHANTOM_SWOOP, SoundCategory.PLAYERS, 1.5f, 0.6f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VEX_CHARGE, SoundCategory.PLAYERS, 1.0f, 0.5f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved()) {
			return true;
		}
		Vec3d c = player.getPos();
		// La lame de la faux : un rayon de flammes d'âmes qui balaie un tour complet.
		double angle = startAngle + Math.PI * 2 * age / (DURATION - 1);
		for (double r = 0.8; r <= RADIUS; r += 0.35) {
			double curve = angle - (r / RADIUS) * 0.6;
			double x = c.x + Math.cos(curve) * r;
			double z = c.z + Math.sin(curve) * r;
			world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, c.y + 0.9, z, 1, 0.02, 0.02, 0.02, 0);
			if (r > RADIUS - 1.5) {
				world.spawnParticles(ParticleTypes.SOUL, x, c.y + 1.0, z, 1, 0.05, 0.05, 0.05, 0.01);
			}
		}

		for (LivingEntity target : FX.livingAround(world, player, c, RADIUS)) {
			Vec3d d = target.getPos().subtract(c);
			double targetAngle = Math.atan2(d.z, d.x);
			double diff = Math.abs(normalize(targetAngle - angle));
			if (diff < 0.55 && alreadyHit.add(target.getUuid())) {
				FX.hit(player, target, 9.0f);
				player.heal(2.0f);
				world.spawnParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getBodyY(0.6), target.getZ(),
						6, 0.2, 0.3, 0.2, 0.05);
			}
		}

		if (age == DURATION - 1) {
			BlockPos origin = player.getBlockPos();
			ItemStack boneMeal = new ItemStack(Items.BONE_MEAL, 64);
			for (BlockPos pos : BlockPos.iterate(origin.add(-6, -2, -6), origin.add(6, 2, 6))) {
				BlockState state = world.getBlockState(pos);
				if (state.getBlock() instanceof CropBlock crop && !crop.isMature(state)) {
					BlockPos p = pos.toImmutable();
					world.setBlockState(p, crop.withAge(crop.getMaxAge()));
					world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
							5, 0.3, 0.3, 0.3, 0);
				} else if (world.random.nextInt(6) == 0 && state.isOf(net.minecraft.block.Blocks.GRASS_BLOCK)
						&& world.getBlockState(pos.up()).isAir()) {
					BoneMealItem.useOnFertilizable(boneMeal, world, pos.toImmutable());
				}
			}
			world.playSound(null, origin, SoundEvents.ITEM_BONE_MEAL_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);
			return true;
		}
		return false;
	}

	private static double normalize(double a) {
		while (a > Math.PI) a -= Math.PI * 2;
		while (a < -Math.PI) a += Math.PI * 2;
		return a;
	}
}
