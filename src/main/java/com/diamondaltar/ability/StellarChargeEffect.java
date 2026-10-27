package com.diamondaltar.ability;

import com.diamondaltar.Compat;
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
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Lance — « Charge Stellaire » : tu charges à la vitesse de la lumière, une lance géante
 * pointée devant toi, et tu embroches tout sur 20 blocs. Onde de choc à l'arrivée.
 */
public class StellarChargeEffect extends TimedEffect {
	private static final int DURATION = 10;

	private final ServerPlayer player;
	private final Vec3 dir;
	private final Display.ItemDisplay lance;
	private final Set<UUID> hit = new HashSet<>();

	public StellarChargeEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		Vec3 look = player.getLookAngle();
		this.dir = new Vec3(look.x, Math.max(-0.3, Math.min(0.3, look.y)), look.z).normalize();
		this.lance = track(Visuals.item(level, player.position().add(0, 1, 0), player.getMainHandItem()));
		Visuals.transform(lance, new org.joml.Vector3f(), Visuals.pointing(dir, 0f), 4.0f, 0);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20, 4, false, false));
		level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.0f, 2.0f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved()) {
			return true;
		}
		player.setDeltaMovement(dir.scale(2.1));
		Compat.markHurt(player);
		player.fallDistance = 0;
		Vec3 p = player.position().add(0, 1.0, 0);
		lance.setPos(p.add(dir.scale(1.5)));
		Visuals.transform(lance, new org.joml.Vector3f(), Visuals.pointing(dir, age * 0.6f), 4.0f, 1);
		// Traînée lumineuse en forme de cône derrière le joueur.
		for (int k = 0; k < 10; k++) {
			Vec3 b = p.subtract(dir.scale(k * 0.5));
			double s = k * 0.08;
			level.sendParticles(k < 5 ? Cosmic.MOON : Cosmic.NEBULA, b.x, b.y, b.z, 2, s, s, s, 0);
		}
		Cosmic.starfield(level, p, 1.5, 4);
		if (age % 2 == 0) {
			Glow.beam(level, p.subtract(dir.scale(4)), p.add(dir.scale(2)), 0.3f, Glow.Tint.ICE, 1);
		}
		for (LivingEntity target : FX.livingAround(level, player, p.add(dir.scale(1.5)), 2.5)) {
			if (hit.add(target.getUUID())) {
				FX.hit(level, player, target, 28.0f);
				FX.push(target, dir.x * 2.0, 0.8, dir.z * 2.0);
				level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5), target.getZ(), 20, 0.3, 0.4, 0.3, 0.4);
				level.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.5f, 0.7f);
			}
		}
		if (age >= DURATION) {
			player.setDeltaMovement(player.getDeltaMovement().scale(0.2));
			Compat.markHurt(player);
			FX.ring(level, Cosmic.MOON, p, 3, 40);
			FX.ring(level, Cosmic.NEBULA, p, 4.5, 50);
			Glow.rays(level, p, 10, 8, Glow.Tint.ICE);
			for (LivingEntity target : FX.livingAround(level, player, p, 4.5)) {
				FX.hit(level, player, target, 10.0f);
			}
			level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 2.0f, 0.6f);
			return true;
		}
		return false;
	}
}
