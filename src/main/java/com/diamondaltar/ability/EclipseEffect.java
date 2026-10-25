package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
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

import java.util.List;

/**
 * Épée — « Éclipse » : une lune noire entourée d'une couronne de lumière apparaît au-dessus de toi,
 * plonge les ennemis dans les ténèbres, puis chaque ennemi est frappé par un rayon de lune.
 */
public class EclipseEffect extends TimedEffect {
	private static final int CHARGE = 40;
	private static final double RADIUS = 18.0;

	private final ServerPlayer player;
	private final Display.BlockDisplay moon;
	private final Display.BlockDisplay halo;
	private final List<LivingEntity> targets;

	public EclipseEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		Vec3 sky = player.position().add(0, 7, 0);
		this.moon = track(Visuals.block(level, sky, Blocks.BLACK_CONCRETE.defaultBlockState()));
		this.halo = track(Visuals.block(level, sky, Blocks.WHITE_STAINED_GLASS.defaultBlockState()));
		Visuals.transform(moon, new org.joml.Vector3f(), new Quaternionf(), 0.01f, 0);
		Visuals.transform(halo, new org.joml.Vector3f(), new Quaternionf(), 0.01f, 0);
		this.targets = FX.livingAround(level, player, player.position(), RADIUS);
		level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 1.0f, 1.4f);
		level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8f, 0.6f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved()) {
			return true;
		}
		Vec3 sky = player.position().add(0, 7, 0);
		float t = Math.min(1f, age / 15f);
		Quaternionf spin = new Quaternionf().rotateY(age * 0.05f).rotateX(0.6f).rotateZ(0.6f);
		moon.setPos(sky);
		halo.setPos(sky);
		Visuals.transform(moon, Visuals.centered(spin, 2.6f * t), spin, 2.6f * t, 2);
		Visuals.transform(halo, Visuals.centered(spin, 3.1f * t), spin, 3.1f * t, 2);
		// Couronne solaire qui ondule autour de la lune noire.
		for (int i = 0; i < 48; i++) {
			double a = Math.PI * 2 * i / 48 + age * 0.03;
			double r = 2.4 + Math.sin(a * 6 + age * 0.4) * 0.35;
			Cosmic.at(level, i % 2 == 0 ? Cosmic.STAR_TO_GOLD : Cosmic.MOON, sky.add(Math.cos(a) * r, Math.sin(a) * r, 0));
			Cosmic.at(level, Cosmic.MOON_SMALL, sky.add(0, Math.sin(a) * r, Math.cos(a) * r));
		}
		for (LivingEntity target : targets) {
			if (target.isAlive()) {
				target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 3));
			}
		}
		if (age < CHARGE) {
			if (age % 10 == 0) {
				level.playSound(null, player.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 2.0f, 0.5f);
			}
			return false;
		}
		// Rayons de lune sur chaque ennemi.
		for (LivingEntity target : targets) {
			if (!target.isAlive()) {
				continue;
			}
			Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
			Cosmic.line(level, Cosmic.MOON, sky, to, 0.3);
			Glow.beam(level, sky, to, 0.35f, Glow.Tint.ICE, 4);
			Cosmic.line(level, ParticleTypes.END_ROD, sky, to, 1.0);
			level.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 14, 0.2, 0.2, 0.2, 0.2);
			FX.hit(level, player, target, 34.0f);
			FX.push(target, 0, 0.8, 0);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.5f, 1.6f);
		FX.shake(level, player.position(), 26, 1.1f, 10);
		FX.flash(level, player.position(), 30, 0xFFFFFF, 0.55f, 12);
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0f, 0.5f);
		Cosmic.sphere(level, Cosmic.MOON_TO_NEBULA, sky, 4, 120);
		return true;
	}
}
