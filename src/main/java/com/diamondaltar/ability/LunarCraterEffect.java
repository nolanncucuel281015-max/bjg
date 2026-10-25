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

import java.util.ArrayList;
import java.util.List;

/**
 * Pelle — « Cratère Lunaire » : tu bondis puis t'écrases au sol, qui se transforme en cratère
 * de lune : un anneau de pierre de l'End se soulève et les ennemis sont projetés au loin.
 */
public class LunarCraterEffect extends TimedEffect {
	private static final double RADIUS = 7.0;

	private final ServerPlayer player;
	private final List<Display.BlockDisplay> rim = new ArrayList<>();
	private int landed = -1;

	public LunarCraterEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		player.setDeltaMovement(player.getDeltaMovement().x, 1.2, player.getDeltaMovement().z);
		player.hurtMarked = true;
		level.playSound(null, player.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.5f, 0.6f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved()) {
			return true;
		}
		player.fallDistance = 0;
		if (landed < 0) {
			if (age == 8) {
				player.setDeltaMovement(0, -2.5, 0);
				player.hurtMarked = true;
			}
			Cosmic.at(level, Cosmic.MOON, player.position());
			if (age > 8 && player.onGround() || age > 60) {
				landed = age;
				slam(player.position());
			}
			return false;
		}
		int life = age - landed;
		if (life == 30) {
			Quaternionf q = new Quaternionf();
			for (Display.BlockDisplay b : rim) {
				Visuals.transform(b, new org.joml.Vector3f(-0.5f, -1.3f, -0.5f), q, 1f, 10);
			}
		}
		return life >= 42;
	}

	private void slam(Vec3 c) {
		for (int ring = 0; ring < 2; ring++) {
			double r = RADIUS * (0.6 + ring * 0.4);
			int n = (int) (r * 2.2);
			for (int i = 0; i < n; i++) {
				double a = Math.PI * 2 * i / n;
				Vec3 at = FX.ground(level, c.add(Math.cos(a) * r, 2, Math.sin(a) * r));
				Quaternionf tilt = new Quaternionf().rotateY((float) -a).rotateZ(0.55f);
				Display.BlockDisplay b = track(Visuals.block(level, at, (i % 3 == 0 ? Blocks.END_STONE_BRICKS : Blocks.END_STONE).defaultBlockState()));
				Visuals.transform(b, new org.joml.Vector3f(-0.5f, -1.2f, -0.5f), tilt, 1f, 0);
				Visuals.transform(b, new org.joml.Vector3f(-0.5f, -0.6f + ring * 0.3f, -0.5f).rotate(tilt), tilt, 1f, 0);
				rim.add(b);
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
		Cosmic.sphere(level, Cosmic.MOON_TO_NEBULA, c, RADIUS * 0.6, 120);
		FX.ring(level, Cosmic.MOON, c.add(0, 0.3, 0), RADIUS, 70);
		for (LivingEntity target : FX.livingAround(level, player, c, RADIUS)) {
			FX.hit(level, player, target, 26.0f);
			Vec3 out = target.position().subtract(c).multiply(1, 0, 1).normalize().scale(2.0);
			FX.push(target, out.x, 1.0, out.z);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 2.0f, 0.4f);
		level.playSound(null, player.blockPosition(), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 1.5f, 0.6f);
		FX.shake(level, c, 26, 1.6f, 12);
		FX.flash(level, c, 26, 0xE0F0FF, 0.35f, 8);
		Glow.rays(level, c.add(0, 0.4, 0), 14, (float) RADIUS + 2, Glow.Tint.ICE);
	}
}
