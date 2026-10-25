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
import net.minecraft.world.phys.Vec3;

/**
 * Lance — « Lance Comète » : tu projettes une lance spectrale qui file comme une comète
 * sur 40 blocs avec une queue d'étoiles, puis explose en pluie cosmique à l'impact.
 */
public class CometLanceEffect extends TimedEffect {
	private static final double SPEED = 2.2;
	private static final int MAX = 20;

	private final ServerPlayer player;
	private final Vec3 dir;
	private Vec3 pos;
	private final Display.ItemDisplay lance;

	public CometLanceEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.dir = player.getLookAngle();
		this.pos = player.getEyePosition().add(dir);
		this.lance = track(Visuals.item(level, pos, player.getMainHandItem()));
		Visuals.transform(lance, new org.joml.Vector3f(), Visuals.pointing(dir, 0f), 2.5f, 0);
		level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 2.0f, 0.6f);
	}

	@Override
	protected boolean tick() {
		Vec3 next = pos.add(dir.scale(SPEED));
		// Queue de comète : bleue près de la lance, violette plus loin.
		for (int k = 0; k < 12; k++) {
			Vec3 p = pos.subtract(dir.scale(k * 0.35));
			double spread = k * 0.03;
			level.sendParticles(k < 4 ? Cosmic.MOON : (k < 8 ? Cosmic.STARLIGHT : Cosmic.NEBULA), p.x, p.y, p.z, 1, spread, spread, spread, 0);
		}
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 3, 0.1, 0.1, 0.1, 0.02);
		Cosmic.star(level, pos.add((level.getRandom().nextDouble() - 0.5) * 0.8, (level.getRandom().nextDouble() - 0.5) * 0.8, (level.getRandom().nextDouble() - 0.5) * 0.8));
		Glow.beam(level, pos.subtract(dir.scale(3)), next, 0.25f, Glow.Tint.ICE, 1);
		lance.setPos(next);
		Visuals.transform(lance, new org.joml.Vector3f(), Visuals.pointing(dir, age * 0.5f), 2.5f, 1);

		boolean blocked = FX.solid(level, BlockPos.containing(next));
		boolean touched = !FX.livingAround(level, player, next, 1.5).isEmpty();
		pos = next;
		if (blocked || touched || age >= MAX) {
			impact(pos);
			return true;
		}
		return false;
	}

	private void impact(Vec3 p) {
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.FIREWORK, p.x, p.y, p.z, 60, 0.3, 0.3, 0.3, 0.4);
		Cosmic.sphere(level, Cosmic.MOON_TO_NEBULA, p, 3.5, 140);
		Cosmic.nebula(level, p, 1.5, 30);
		Glow.rays(level, p, 14, 9, Glow.Tint.ICE);
		for (LivingEntity target : FX.livingAround(level, player, p, 4.0)) {
			FX.hit(level, player, target, 34.0f);
			Vec3 out = target.position().subtract(p).normalize().scale(1.5);
			FX.push(target, out.x, 0.7, out.z);
		}
		level.playSound(null, BlockPos.containing(p), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, SoundSource.PLAYERS, 3.0f, 0.5f);
		level.playSound(null, BlockPos.containing(p), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 1.5f, 0.8f);
		FX.shake(level, p, 20, 1.0f, 8);
	}
}
