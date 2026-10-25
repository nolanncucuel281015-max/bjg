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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Pelle — « Mur d'Astéroïdes » : 9 astéroïdes se dressent devant toi en mur (ils bloquent les
 * projectiles), puis ils sont catapultés en avant et pulvérisent les ennemis.
 */
public class AsteroidWallEffect extends TimedEffect {
	private static final int HOLD = 40;
	private static final int FLY = 18;

	private final ServerPlayer player;
	private final Display.BlockDisplay[] rocks = new Display.BlockDisplay[9];
	private final Vec3[] pos = new Vec3[9];
	private Vec3 dir;

	public AsteroidWallEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.dir = FX.forward(player);
		Vec3 right = FX.right(dir);
		Vec3 center = player.position().add(dir.scale(3));
		for (int i = 0; i < 9; i++) {
			int x = i % 3 - 1;
			int y = i / 3;
			pos[i] = center.add(right.scale(x * 1.3)).add(0, 0.6 + y * 1.3, 0);
			rocks[i] = track(Visuals.block(level, pos[i].add(0, -2, 0), (i % 2 == 0 ? Blocks.END_STONE : Blocks.BLACKSTONE).defaultBlockState()));
			Visuals.transform(rocks[i], new org.joml.Vector3f(-0.6f), new Quaternionf(), 1.2f, 0);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 2.0f, 0.4f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved()) {
			return true;
		}
		if (age < HOLD) {
			for (int i = 0; i < 9; i++) {
				double rise = Math.min(1.0, age / 6.0);
				Vec3 at = pos[i].add(0, -2 + 2 * rise + Math.sin(age * 0.2 + i) * 0.08, 0);
				Quaternionf q = new Quaternionf().rotateXYZ(age * 0.04f + i, age * 0.03f, i);
				rocks[i].setPos(at);
				Visuals.transform(rocks[i], Visuals.centered(q, 1.2f), q, 1.2f, 1);
				if (age % 4 == 0) {
					Cosmic.at(level, Cosmic.NEBULA, at);
				}
				// Le mur arrête les projectiles.
				for (Projectile p : level.getEntitiesOfClass(Projectile.class, AABB.ofSize(at, 2, 2, 2))) {
					level.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY(), p.getZ(), 6, 0.1, 0.1, 0.1, 0.2);
					p.discard();
				}
			}
			if (age == HOLD - 1) {
				dir = player.getLookAngle();
				level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 2.0f, 0.5f);
			}
			return false;
		}
		for (int i = 0; i < 9; i++) {
			if (rocks[i].isRemoved()) {
				continue;
			}
			pos[i] = pos[i].add(dir.scale(1.6));
			Quaternionf q = new Quaternionf().rotateXYZ(age * 0.3f + i, age * 0.2f, i);
			rocks[i].setPos(pos[i]);
			Visuals.transform(rocks[i], Visuals.centered(q, 1.2f), q, 1.2f, 1);
			Cosmic.line(level, Cosmic.MOON_TO_NEBULA, pos[i], pos[i].subtract(dir.scale(1.6)), 0.5);
			level.sendParticles(ParticleTypes.FLAME, pos[i].x, pos[i].y, pos[i].z, 2, 0.2, 0.2, 0.2, 0.01);
			for (LivingEntity t : FX.livingAround(level, player, pos[i], 1.8)) {
				FX.hit(level, player, t, 18.0f);
				FX.push(t, dir.x * 1.2, 0.5, dir.z * 1.2);
				level.sendParticles(ParticleTypes.EXPLOSION, pos[i].x, pos[i].y, pos[i].z, 1, 0, 0, 0, 0);
				rocks[i].discard();
				break;
			}
		}
		return age >= HOLD + FLY;
	}
}
