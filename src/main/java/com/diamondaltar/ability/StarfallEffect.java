package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Épée — « Pluie d'Étoiles » : le ciel s'ouvre au-dessus de la zone visée et 24 étoiles filantes
 * s'écrasent une à une en explosant en poussière cosmique.
 */
public class StarfallEffect extends TimedEffect {
	private static final int STARS = 24;
	private static final double AREA = 7.0;

	private final class Star {
		Vec3 pos;
		final Vec3 vel;
		final Display.ItemDisplay display;

		Star(Vec3 pos, Vec3 vel) {
			this.pos = pos;
			this.vel = vel;
			this.display = track(Visuals.item(level, pos, new ItemStack(Items.NETHER_STAR)));
			Visuals.transform(display, new Vector3f(), new Quaternionf(), 1.4f, 0);
		}
	}

	private final ServerPlayer player;
	private final Vec3 center;
	private final List<Star> falling = new ArrayList<>();
	private int launched;

	public StarfallEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.center = FX.ground(level, FX.target(player, 40).add(0, 1, 0));
		level.playSound(null, BlockPos.containing(center), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0f, 0.5f);
	}

	@Override
	protected boolean tick() {
		RandomSource r = level.getRandom();
		// Portail céleste qui tourne au-dessus de la zone.
		Vec3 sky = center.add(0, 22, 0);
		for (int k = 0; k < 3; k++) {
			double a = age * 0.2 + k * Math.PI * 2 / 3;
			for (double rad = 1; rad < AREA; rad += 0.8) {
				double ang = a + rad * 0.4;
				Cosmic.at(level, k == 0 ? Cosmic.NEBULA : Cosmic.NEBULA_PINK, sky.add(Math.cos(ang) * rad, 0, Math.sin(ang) * rad));
			}
		}
		FX.ring(level, Cosmic.STARLIGHT, center.add(0, 0.15, 0), AREA, 50);

		if (launched < STARS && age % 2 == 0) {
			Vec3 land = center.add((r.nextDouble() - 0.5) * 2 * AREA, 0, (r.nextDouble() - 0.5) * 2 * AREA);
			Vec3 start = land.add(-6 + r.nextDouble() * 4, 22, -3 + r.nextDouble() * 6);
			falling.add(new Star(start, land.subtract(start).scale(1 / 10.0)));
			launched++;
		}
		Iterator<Star> it = falling.iterator();
		while (it.hasNext()) {
			Star s = it.next();
			Vec3 next = s.pos.add(s.vel);
			Cosmic.line(level, Cosmic.MOON_TO_NEBULA, s.pos, next, 0.4);
			level.sendParticles(ParticleTypes.END_ROD, next.x, next.y, next.z, 2, 0.1, 0.1, 0.1, 0.02);
			s.pos = next;
			s.display.setPos(next);
			Visuals.transform(s.display, new Vector3f(), new Quaternionf().rotateY(age * 0.8f).rotateZ(age * 0.5f), 1.4f, 1);
			if (next.y <= center.y + 0.3) {
				impact(next);
				s.display.discard();
				it.remove();
			}
		}
		return launched >= STARS && falling.isEmpty();
	}

	private void impact(Vec3 p) {
		level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y + 0.5, p.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.FIREWORK, p.x, p.y + 0.5, p.z, 25, 0.2, 0.2, 0.2, 0.25);
		Cosmic.nebula(level, p.add(0, 0.5, 0), 0.6, 10);
		FX.ring(level, Cosmic.MOON_SMALL, p.add(0, 0.2, 0), 1.8, 18);
		for (LivingEntity target : FX.livingAround(level, player, p, 2.8)) {
			FX.hit(level, player, target, 16.0f);
			FX.push(target, 0, 0.5, 0);
		}
		level.playSound(null, BlockPos.containing(p), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.5f, 0.6f);
	}
}
