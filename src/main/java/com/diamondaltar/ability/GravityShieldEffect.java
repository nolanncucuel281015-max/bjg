package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Armure — « Bouclier Gravitationnel » : pendant 8 s, une sphère de gravité t'entoure et renvoie
 * toutes les flèches, boules de feu et projectiles vers ceux qui les ont tirés.
 */
public class GravityShieldEffect extends TimedEffect {
	private static final int DURATION = 160;
	private static final double RADIUS = 4.0;

	private final ServerPlayer player;
	private final Set<UUID> reflected = new HashSet<>();

	public GravityShieldEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 0.6f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved() || !player.isAlive()) {
			return true;
		}
		Vec3 c = player.position().add(0, 1.0, 0);
		if (age % 3 == 0) {
			Cosmic.sphere(level, age % 6 == 0 ? Cosmic.STARLIGHT : Cosmic.NEBULA, c, RADIUS * (0.95 + Math.sin(age * 0.3) * 0.05), 60);
		}
		for (Projectile p : level.getEntitiesOfClass(Projectile.class, AABB.ofSize(c, RADIUS * 2, RADIUS * 2, RADIUS * 2))) {
			if (p.getOwner() == player || !reflected.add(p.getUUID())) {
				continue;
			}
			p.setDeltaMovement(p.getDeltaMovement().scale(-1.3));
			p.setOwner(player);
			p.hurtMarked = true;
			level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY(), p.getZ(), 10, 0.1, 0.1, 0.1, 0.15);
			level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.5f, 1.5f);
		}
		return age >= DURATION;
	}
}
