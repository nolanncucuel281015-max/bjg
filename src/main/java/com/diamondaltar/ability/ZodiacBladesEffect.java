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

import java.util.Comparator;

/**
 * Épée — « Lames du Zodiaque » : 12 épées apparaissent une à une en cercle derrière toi comme
 * les signes du zodiaque, puis partent l'une après l'autre transpercer les ennemis.
 */
public class ZodiacBladesEffect extends TimedEffect {
	private static final int COUNT = 12;
	private static final int APPEAR = 12;
	private static final int HOLD = 8;

	private final ServerPlayer player;
	private final Display.ItemDisplay[] blades = new Display.ItemDisplay[COUNT];
	private final Vec3[] pos = new Vec3[COUNT];
	private final Vec3[] vel = new Vec3[COUNT];
	private final boolean[] done = new boolean[COUNT];

	public ZodiacBladesEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 1.8f);
	}

	private Vec3 slot(int i) {
		Vec3 f = FX.forward(player);
		Vec3 r = FX.right(f);
		double a = Math.PI * 2 * i / COUNT + age * 0.03;
		return player.position().add(0, 2.4, 0).subtract(f.scale(1.2))
				.add(r.scale(Math.cos(a) * 2.6)).add(0, Math.sin(a) * 2.6, 0);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved()) {
			return true;
		}
		int fireStart = APPEAR + HOLD;
		for (int i = 0; i < COUNT; i++) {
			if (done[i]) {
				continue;
			}
			int fireAt = fireStart + i * 2;
			if (age < fireAt) {
				if (age == i) {
					blades[i] = track(Visuals.item(level, slot(i), player.getMainHandItem()));
					Visuals.transform(blades[i], new org.joml.Vector3f(), Visuals.pointing(player.getLookAngle(), 0f), 0.01f, 0);
					level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.6f + i * 0.1f);
				}
				if (blades[i] != null) {
					Vec3 at = slot(i);
					blades[i].setPos(at);
					Visuals.transform(blades[i], new org.joml.Vector3f(), Visuals.pointing(player.getLookAngle(), 0f), 1.3f, 2);
					Cosmic.star(level, at);
					if (i > 0 && blades[i - 1] != null && age % 3 == 0) {
						Cosmic.line(level, Cosmic.STARLIGHT, slot(i - 1), at, 0.5);
					}
				}
				continue;
			}
			if (age == fireAt) {
				pos[i] = slot(i);
				LivingEntity prey = FX.livingAround(level, player, player.position(), 24).stream()
						.min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
				Vec3 aim = prey != null ? prey.position().add(0, prey.getBbHeight() * 0.5, 0) : FX.target(player, 30);
				vel[i] = aim.subtract(pos[i]).normalize().scale(2.2);
				level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.8f);
			}
			Vec3 next = pos[i].add(vel[i]);
			Cosmic.line(level, Cosmic.MOON_TO_NEBULA, pos[i], next, 0.4);
			Glow.beam(level, pos[i], next, 0.12f, Glow.Tint.ICE, 1);
			pos[i] = next;
			blades[i].setPos(next);
			Visuals.transform(blades[i], new org.joml.Vector3f(), Visuals.pointing(vel[i], 0f), 1.3f, 1);
			boolean hitSomething = false;
			for (LivingEntity t : FX.livingAround(level, player, next, 1.5)) {
				FX.hit(level, player, t, 16.0f);
				FX.push(t, vel[i].x * 0.3, 0.2, vel[i].z * 0.3);
				hitSomething = true;
			}
			if (hitSomething || FX.solid(level, BlockPos.containing(next)) || age > fireAt + 14) {
				level.sendParticles(ParticleTypes.END_ROD, next.x, next.y, next.z, 10, 0.2, 0.2, 0.2, 0.15);
				blades[i].discard();
				done[i] = true;
			}
		}
		for (boolean d : done) {
			if (!d) {
				return false;
			}
		}
		return true;
	}
}
