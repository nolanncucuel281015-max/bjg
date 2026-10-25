package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.core.particles.DustColorTransitionOptions;
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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Masse — « Supernova » : une étoile naît au-dessus de ta masse, grossit en tremblant,
 * puis explose en supernova : une coquille de lumière de 14 blocs qui pulvérise tout.
 */
public class SupernovaEffect extends TimedEffect {
	private static final int CHARGE = 30;
	private static final double RADIUS = 14.0;
	private static final DustColorTransitionOptions FIRE_TO_VIOLET = new DustColorTransitionOptions(0xFFB040, 0x8C3CFF, 2.4f);

	private final ServerPlayer player;
	private final Display.BlockDisplay star;
	private final Set<UUID> hit = new HashSet<>();
	private Vec3 core;

	public SupernovaEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.core = player.position().add(0, 3.5, 0);
		this.star = track(Visuals.block(level, core, Blocks.SHROOMLIGHT.defaultBlockState()));
		Visuals.transform(star, new org.joml.Vector3f(), new Quaternionf(), 0.01f, 0);
		level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 2.0f, 0.5f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved()) {
			return true;
		}
		if (age < CHARGE) {
			core = player.position().add(0, 3.5, 0);
			double t = age / (double) CHARGE;
			float s = (float) (0.3 + t * 2.2);
			float shake = (float) (t * 0.15 * Math.sin(age * 2.7));
			Quaternionf q = new Quaternionf().rotateXYZ(age * 0.3f, age * 0.4f, age * 0.2f);
			star.setPos(core.add(shake, 0, -shake));
			Visuals.transform(star, Visuals.centered(q, s), q, s, 1);
			// La matière est aspirée vers l'étoile.
			level.sendParticles(ParticleTypes.END_ROD, core.x, core.y, core.z, 8, 3, 3, 3, -0.2);
			Cosmic.nebula(level, core, 2.5, 4);
			if (age % 5 == 0) {
				level.playSound(null, player.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 2.0f, 0.5f + (float) t);
			}
			return false;
		}
		if (age == CHARGE) {
			star.discard();
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, core.x, core.y, core.z, 3, 1, 1, 1, 0);
			level.sendParticles(ParticleTypes.END_ROD, core.x, core.y, core.z, 200, 0.5, 0.5, 0.5, 1.0);
			level.playSound(null, player.blockPosition(), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 3.0f, 0.5f);
			level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0f, 0.5f);
			FX.shake(level, core, 40, 2.4f, 22);
			FX.flash(level, core, 48, 0xFFF0D0, 0.75f, 18);
			Glow.rays(level, core, 20, 18, Glow.Tint.FIRE);
			Glow.rays(level, core.add(0, 0.5, 0), 14, 14, Glow.Tint.NEBULA);
			Glow.pillar(level, core.add(0, -6, 0), 30, 1.0f, Glow.Tint.GOLD);
		}
		double r = (age - CHARGE + 1) * RADIUS / 7.0;
		Cosmic.sphere(level, FIRE_TO_VIOLET, core, r, (int) (r * r * 3) + 40);
		Cosmic.sphere(level, Cosmic.MOON, core, r * 0.9, (int) (r * r) + 20);
		for (LivingEntity target : FX.livingAround(level, player, core, r)) {
			if (hit.add(target.getUUID())) {
				FX.hit(level, player, target, 42.0f);
				target.igniteForSeconds(5);
				Vec3 out = target.position().subtract(core).normalize().scale(2.2);
				FX.push(target, out.x, 1.0, out.z);
			}
		}
		return r >= RADIUS;
	}
}
