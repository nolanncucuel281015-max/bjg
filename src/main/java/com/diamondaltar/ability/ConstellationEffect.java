package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Lance — « Constellation » : 7 lances d'étoiles apparaissent dans le ciel, reliées entre elles
 * comme une constellation, puis s'abattent sur les ennemis (ou autour de toi s'il n'y en a pas).
 */
public class ConstellationEffect extends TimedEffect {
	private static final int COUNT = 7;
	private static final int HOLD = 25;
	private static final double HEIGHT = 12;

	private final ServerPlayer player;
	private final List<Vec3> targets = new ArrayList<>();
	private final List<Display.ItemDisplay> lances = new ArrayList<>();
	private final Vec3 down = new Vec3(0, -1, 0);

	public ConstellationEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		List<LivingEntity> enemies = FX.livingAround(level, player, player.position(), 20);
		RandomSource r = level.getRandom();
		for (int i = 0; i < COUNT; i++) {
			Vec3 t;
			if (i < enemies.size()) {
				t = enemies.get(i).position();
			} else {
				double a = Math.PI * 2 * i / COUNT;
				t = FX.ground(level, player.position().add(Math.cos(a) * 6 + r.nextDouble(), 2, Math.sin(a) * 6 + r.nextDouble()));
			}
			targets.add(t);
			Display.ItemDisplay d = track(Visuals.item(level, t.add(0, HEIGHT, 0), player.getMainHandItem()));
			Visuals.transform(d, new org.joml.Vector3f(), Visuals.pointing(down, 0f), 0.01f, 0);
			lances.add(d);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0f, 1.8f);
	}

	@Override
	protected boolean tick() {
		if (age < HOLD) {
			float s = Math.min(3.0f, age * 0.3f);
			for (int i = 0; i < COUNT; i++) {
				Vec3 top = targets.get(i).add(0, HEIGHT, 0);
				Visuals.transform(lances.get(i), new org.joml.Vector3f(), Visuals.pointing(down, age * 0.2f), s, 1);
				Cosmic.star(level, top);
				Cosmic.at(level, Cosmic.MOON, top.add(0, 1.5, 0));
				// Lignes de la constellation.
				Vec3 next = targets.get((i + 1) % COUNT).add(0, HEIGHT, 0);
				if (age % 2 == 0) {
					Cosmic.line(level, Cosmic.STARLIGHT, top, next, 0.5);
				}
				if (age == 4) {
					Glow.beam(level, top, next, 0.08f, Glow.Tint.ICE, HOLD - 2);
				}
			}
			if (age % 5 == 0) {
				level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, 0.6f + age * 0.04f);
			}
			return false;
		}
		// Chute des lances.
		int fallTicks = 5;
		int f = age - HOLD;
		for (int i = 0; i < COUNT; i++) {
			Vec3 t = targets.get(i);
			double y = HEIGHT * (1 - (f + 1) / (double) fallTicks);
			Vec3 at = t.add(0, Math.max(0, y) + 1.2, 0);
			lances.get(i).setPos(at);
			Cosmic.line(level, Cosmic.MOON_TO_NEBULA, at, at.add(0, 3, 0), 0.4);
			if (f == fallTicks - 1) {
				level.sendParticles(ParticleTypes.EXPLOSION, t.x, t.y + 0.5, t.z, 1, 0, 0, 0, 0);
				level.sendParticles(ParticleTypes.FIREWORK, t.x, t.y + 0.5, t.z, 30, 0.3, 0.3, 0.3, 0.3);
				FX.ring(level, Cosmic.MOON, t.add(0, 0.2, 0), 2.0, 24);
				Glow.pillar(level, t, 8, 0.4f, Glow.Tint.ICE);
				for (LivingEntity target : FX.livingAround(level, player, t, 2.8)) {
					FX.hit(level, player, target, 28.0f);
					FX.push(target, 0, 0.6, 0);
				}
			}
		}
		if (f == fallTicks - 1) {
			level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 2.0f, 1.2f);
		}
		return f >= fallTicks + 8;
	}
}
