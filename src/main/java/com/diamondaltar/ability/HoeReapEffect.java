package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.world.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

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

	private final ServerPlayer player;
	private final Set<UUID> alreadyHit = new HashSet<>();
	private final double startAngle;
	private final Display.ItemDisplay scythe;

	public HoeReapEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		Vec3 f = FX.forward(player);
		this.startAngle = Math.atan2(f.z, f.x);
		this.scythe = track(Visuals.item(level, player.position().add(0, 1, 0), player.getMainHandItem()));
		Visuals.transform(scythe, new Vector3f(), new Quaternionf(), 0.5f, 0);
		level.playSound(null, player.blockPosition(), SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS, 1.5f, 0.6f);
		level.playSound(null, player.blockPosition(), SoundEvents.VEX_CHARGE, SoundSource.PLAYERS, 1.0f, 0.5f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved()) {
			return true;
		}
		Vec3 c = player.position();
		double sweep = startAngle + Math.PI * 2 * age / (DURATION - 1);
		Vec3 out = new Vec3(Math.cos(sweep), 0, Math.sin(sweep));
		scythe.setPos(c.add(0, 1.0, 0));
		Visuals.transform(scythe, out.scale(RADIUS * 0.45).toVector3f(), Visuals.pointing(out, (float) (Math.PI / 2)), (float) (RADIUS * 0.8), 1);

		if (age % 2 == 0) {
			Vec3 tg = new Vec3(-Math.sin(sweep), 0, Math.cos(sweep));
			Glow.ribbon(level, c.add(out.scale(RADIUS * 0.75)).add(0, 1, 0), tg, out, 3.5f, 0.6f, net.minecraft.world.level.block.Blocks.PURPLE_STAINED_GLASS, 0x80FFD0, 1, 1, 6);
		}
		// La lame de la faux : un rayon de flammes d'âmes qui balaie un tour complet.
		double angle = startAngle + Math.PI * 2 * age / (DURATION - 1);
		for (double r = 0.8; r <= RADIUS; r += 0.35) {
			double curve = angle - (r / RADIUS) * 0.6;
			double x = c.x + Math.cos(curve) * r;
			double z = c.z + Math.sin(curve) * r;
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, c.y + 0.9, z, 1, 0.02, 0.02, 0.02, 0);
			if (r > RADIUS - 1.5) {
				level.sendParticles(ParticleTypes.SOUL, x, c.y + 1.0, z, 1, 0.05, 0.05, 0.05, 0.01);
			}
		}

		for (LivingEntity target : FX.livingAround(level, player, c, RADIUS)) {
			Vec3 d = target.position().subtract(c);
			double diff = Math.abs(wrap(Math.atan2(d.z, d.x) - angle));
			if (diff < 0.55 && alreadyHit.add(target.getUUID())) {
				FX.hit(level, player, target, 16.0f);
				player.heal(2.0f);
				level.sendParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getY(0.6), target.getZ(),
						6, 0.2, 0.3, 0.2, 0.05);
			}
		}

		if (age == DURATION - 1) {
			BlockPos origin = player.blockPosition();
			ItemStack boneMeal = new ItemStack(Items.BONE_MEAL, 64);
			for (BlockPos mutable : BlockPos.betweenClosed(origin.offset(-6, -2, -6), origin.offset(6, 2, 6))) {
				BlockPos pos = mutable.immutable();
				BlockState state = level.getBlockState(pos);
				if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) {
					level.setBlockAndUpdate(pos, crop.getStateForAge(crop.getMaxAge()));
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
							5, 0.3, 0.3, 0.3, 0);
				} else if (level.getRandom().nextInt(6) == 0 && state.is(Blocks.GRASS_BLOCK)
						&& level.getBlockState(pos.above()).isAir()) {
					BoneMealItem.growCrop(boneMeal, level, pos);
				}
			}
			level.playSound(null, origin, SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
			return true;
		}
		return false;
	}

	private static double wrap(double a) {
		while (a > Math.PI) a -= Math.PI * 2;
		while (a < -Math.PI) a += Math.PI * 2;
		return a;
	}
}
