package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * Lance — « Lance Polaire » : tu plantes ta lance et le froid de l'étoile polaire jaillit :
 * des pics de glace géants sortent du sol en étoile à 6 branches et gèlent les ennemis.
 */
public class PolarLanceEffect extends TimedEffect {
	private static final int BRANCHES = 6;
	private static final int LENGTH = 10;
	private static final DustParticleOptions ICE = new DustParticleOptions(0xCFF4FF, 1.5f);

	private final ServerPlayer player;
	private final Vec3 center;
	private final List<Display.BlockDisplay> spikes = new ArrayList<>();
	private final List<Quaternionf> tilts = new ArrayList<>();
	private final List<Integer> born = new ArrayList<>();

	public PolarLanceEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.center = player.position();
		level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.0f, 0.5f);
		level.playSound(null, player.blockPosition(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 2.0f, 0.5f);
	}

	private static org.joml.Vector3f base(Quaternionf q, float s, float dy) {
		return new org.joml.Vector3f(-0.5f * s, dy, -0.5f * s).rotate(q);
	}

	@Override
	protected boolean tick() {
		for (int i = 0; i < spikes.size(); i++) {
			int life = age - born.get(i);
			if (life == 1) {
				Visuals.transform(spikes.get(i), base(tilts.get(i), 1.2f, 0f), tilts.get(i), 1.2f, 2);
			} else if (life == 30) {
				Visuals.transform(spikes.get(i), base(tilts.get(i), 1.2f, -2.5f), tilts.get(i), 1.2f, 8);
			}
		}
		if (age >= LENGTH) {
			return age >= LENGTH + 40;
		}
		double d = 1.2 + age * 1.0;
		for (int b = 0; b < BRANCHES; b++) {
			double a = Math.PI * 2 * b / BRANCHES;
			Vec3 p = FX.ground(level, center.add(Math.cos(a) * d, 2, Math.sin(a) * d));
			Quaternionf tilt = new Quaternionf().rotateY((float) -a).rotateZ(-0.35f).rotateY(0.785f);
			Display.BlockDisplay spike = track(Visuals.block(level, p, (b % 2 == 0 ? Blocks.PACKED_ICE : Blocks.BLUE_ICE).defaultBlockState()));
			Visuals.transform(spike, base(tilt, 1.2f, -2.5f), tilt, 1.2f, 0);
			spikes.add(spike);
			tilts.add(tilt);
			born.add(age);
			level.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y + 0.5, p.z, 8, 0.4, 0.6, 0.4, 0.05);
			level.sendParticles(ICE, p.x, p.y + 1, p.z, 4, 0.3, 0.5, 0.3, 0);
			for (LivingEntity target : FX.livingAround(level, player, p.add(0, 1, 0), 1.8)) {
				FX.hit(level, player, target, 18.0f);
				target.setTicksFrozen(target.getTicksRequiredToFreeze() + 120);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 4));
				FX.push(target, 0, 0.6, 0);
			}
		}
		Cosmic.star(level, center.add(0, 1.5, 0));
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.2f, 1.5f);
		return false;
	}
}
