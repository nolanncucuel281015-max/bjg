package com.diamondaltar.ability;

import com.diamondaltar.Compat;
import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.world.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Masse — « Chute Météore » : le joueur s'envole très haut puis retombe comme une
 * météorite, provoquant une énorme onde de choc à l'impact (sans dégâts de chute).
 */
public class MaceMeteorEffect extends TimedEffect {
	private static final int MAX_DURATION = 200;
	private static final double IMPACT_RADIUS = 7.0;

	private final ServerPlayer player;
	private boolean falling;
	private Display.BlockDisplay shell;
	private final java.util.List<Display.BlockDisplay> crater = new java.util.ArrayList<>();
	private int impactAge = -1;

	public MaceMeteorEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		Vec3 f = FX.forward(player);
		player.setDeltaMovement(f.x * 0.6, 2.2, f.z * 0.6);
		Compat.markHurt(player);
		level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY(), player.getZ(), 3, 0.5, 0.1, 0.5, 0);
		level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.5f, 0.8f);
	}

	@Override
	protected boolean tick() {
		if (impactAge >= 0) {
			// Le cratère de roche en fusion refroidit puis s'enfonce.
			int life = age - impactAge;
			if (life == 30) {
				Quaternionf q = new Quaternionf();
				for (Display.BlockDisplay b : crater) {
					Visuals.transform(b, new Vector3f(-0.5f, -1.2f, -0.5f), q, 1f, 10);
				}
			}
			return life >= 42;
		}
		if (player.isRemoved() || !player.isAlive()) {
			return true;
		}
		// Pas de dégâts de chute pendant toute la capacité.
		player.fallDistance = 0;

		if (!falling && age > 8 && player.getDeltaMovement().y <= 0.05) {
			// Au sommet : on plonge vers le sol.
			falling = true;
			Vec3 f = FX.forward(player);
			player.setDeltaMovement(f.x * 0.4, -2.5, f.z * 0.4);
			Compat.markHurt(player);
			shell = track(Visuals.block(level, player.position(), Blocks.MAGMA_BLOCK.defaultBlockState()));
			level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5f, 0.5f);
		}

		Vec3 p = player.position();
		if (shell != null) {
			Quaternionf spin = new Quaternionf().rotateXYZ(age * 0.5f, age * 0.3f, age * 0.4f);
			shell.setPos(p.add(0, 0.9, 0));
			Visuals.transform(shell, Visuals.centered(spin, 2.2f), spin, 2.2f, 1);
		}
		if (falling) {
			level.sendParticles(ParticleTypes.FLAME, p.x, p.y + 1, p.z, 8, 0.3, 0.6, 0.3, 0.02);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y + 1.5, p.z, 3, 0.2, 0.4, 0.2, 0.01);
			level.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 1, p.z, 2, 0.3, 0.3, 0.3, 0.05);
		} else {
			level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 3, 0.2, 0.1, 0.2, 0.02);
		}

		if (falling && (player.onGround() || player.isInWater())) {
			impact(p);
			if (shell != null) {
				shell.discard();
			}
			impactAge = age;
			return false;
		}
		return age >= MAX_DURATION;
	}

	private void impact(Vec3 c) {
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
		for (int ring = 1; ring <= 3; ring++) {
			double r = ring * IMPACT_RADIUS / 3;
			int points = (int) (r * 10);
			for (int i = 0; i < points; i++) {
				double a = Math.PI * 2 * i / points;
				level.sendParticles(ParticleTypes.FLAME, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r,
						1, 0, 0.05, 0, 0.02);
				level.sendParticles(ParticleTypes.LAVA, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r,
						0, 0, 0, 0, 0);
			}
		}
		// Anneau de magma et de basalte qui jaillit du sol autour de l'impact.
		for (int i = 0; i < 16; i++) {
			double a = Math.PI * 2 * i / 16;
			Vec3 at = new Vec3(c.x + Math.cos(a) * 3.2, Math.floor(c.y), c.z + Math.sin(a) * 3.2);
			Quaternionf tilt = new Quaternionf().rotateY((float) -a).rotateZ(0.5f);
			Display.BlockDisplay b = track(Visuals.block(level, at, (i % 2 == 0 ? Blocks.MAGMA_BLOCK : Blocks.BASALT).defaultBlockState()));
			Visuals.transform(b, new Vector3f(-0.5f, -1.0f, -0.5f).rotate(tilt), tilt, 1f, 0);
			crater.add(b);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 2.0f, 0.5f);
		level.playSound(null, player.blockPosition(), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 1.5f, 0.7f);
		FX.shake(level, c, 32, 2.0f, 16);
		FX.flash(level, c, 32, 0xFFC080, 0.45f, 10);
		Glow.rays(level, c.add(0, 0.5, 0), 14, 10, Glow.Tint.FIRE);
		Glow.pillar(level, c, 20, 0.7f, Glow.Tint.FIRE);

		for (LivingEntity target : FX.livingAround(level, player, c, IMPACT_RADIUS)) {
			double dist = Math.sqrt(target.distanceToSqr(c));
			float damage = (float) (45.0 * (1.0 - dist / (IMPACT_RADIUS + 1)));
			FX.hit(level, player, target, Math.max(10.0f, damage));
			Vec3 out = target.position().subtract(c).multiply(1, 0, 1).normalize().scale(1.6);
			FX.push(target, out.x, 0.9, out.z);
			target.igniteForSeconds(3);
		}
	}
}
