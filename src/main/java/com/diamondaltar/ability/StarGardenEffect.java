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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * Houe — « Jardin d'Étoiles » : 10 fleurs d'étoile poussent autour de toi, grandissent en
 * brillant puis éclatent l'une après l'autre en explosions de lumière.
 */
public class StarGardenEffect extends TimedEffect {
	private static final int COUNT = 10;
	private static final int GROW = 30;

	private final ServerPlayer player;
	private final List<Vec3> spots = new ArrayList<>();
	private final List<Display.ItemDisplay> flowers = new ArrayList<>();

	public StarGardenEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		RandomSource r = level.getRandom();
		for (int i = 0; i < COUNT; i++) {
			double a = Math.PI * 2 * i / COUNT + r.nextDouble() * 0.3;
			double d = 3 + r.nextDouble() * 5;
			Vec3 p = FX.ground(level, player.position().add(Math.cos(a) * d, 2, Math.sin(a) * d));
			spots.add(p);
			Display.ItemDisplay f = track(Visuals.item(level, p.add(0, 0.6, 0), new ItemStack(i % 2 == 0 ? Items.TORCHFLOWER : Items.PITCHER_PLANT)));
			Visuals.transform(f, new org.joml.Vector3f(), new Quaternionf(), 0.01f, 0);
			flowers.add(f);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 1.8f);
	}

	@Override
	protected boolean tick() {
		for (int i = 0; i < COUNT; i++) {
			Vec3 p = spots.get(i);
			int pop = GROW + i * 3;
			if (age < pop) {
				float s = Math.min(2.2f, age * 0.08f);
				Visuals.transform(flowers.get(i), new org.joml.Vector3f(0, s * 0.4f, 0), new Quaternionf().rotateY(age * 0.15f), s, 1);
				if (age % 3 == 0) {
					Cosmic.star(level, p.add(0, 0.6 + s, 0));
					Cosmic.at(level, Cosmic.NEBULA_PINK, p.add(0, 0.3, 0));
				}
			} else if (age == pop) {
				flowers.get(i).discard();
				Vec3 c = p.add(0, 1.2, 0);
				level.sendParticles(ParticleTypes.FIREWORK, c.x, c.y, c.z, 40, 0.2, 0.2, 0.2, 0.35);
				Cosmic.sphere(level, Cosmic.NEBULA_TO_PINK, c, 2.2, 60);
				for (LivingEntity target : FX.livingAround(level, player, c, 3.5)) {
					FX.hit(level, player, target, 20.0f);
					FX.push(target, 0, 0.7, 0);
				}
				player.heal(1.0f);
				level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 1.5f, 1.0f);
			}
		}
		return age > GROW + COUNT * 3;
	}
}
