package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Hache — « Cyclone d'Acier » : le joueur tourne sur lui-même pendant 2 secondes,
 * entouré de lames tourbillonnantes qui aspirent et frappent les ennemis proches.
 */
public class AxeCycloneEffect extends TimedEffect {
	private static final int DURATION = 40;
	private static final double RADIUS = 4.5;
	private static final DustParticleOptions STEEL = new DustParticleOptions(0xBFE6FF, 1.6f);

	private final ServerPlayer player;

	public AxeCycloneEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 1.4f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved() || !player.isAlive()) {
			return true;
		}
		Vec3 c = player.position();
		double spin = age * 0.7;
		// 3 lames qui tournent autour du joueur sur des hauteurs différentes.
		for (int blade = 0; blade < 3; blade++) {
			double a = spin + blade * (Math.PI * 2 / 3);
			for (double r = 1.0; r <= RADIUS; r += 0.7) {
				double y = c.y + 0.4 + blade * 0.55;
				double ang = a - r * 0.15;
				level.sendParticles(STEEL, c.x + Math.cos(ang) * r, y, c.z + Math.sin(ang) * r, 1, 0, 0, 0, 0);
			}
			level.sendParticles(ParticleTypes.SWEEP_ATTACK,
					c.x + Math.cos(a) * RADIUS * 0.7, c.y + 1.0, c.z + Math.sin(a) * RADIUS * 0.7, 1, 0, 0, 0, 0);
		}
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.1, c.z, 3, 1.5, 0.05, 1.5, 0.05);

		if (age % 5 == 0) {
			level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
					0.9f, 0.8f + age / 80f);
			for (LivingEntity target : FX.livingAround(level, player, c, RADIUS)) {
				FX.hit(level, player, target, 9.0f);
				Vec3 pull = c.subtract(target.position()).normalize().scale(0.25);
				FX.push(target, pull.x, 0.1, pull.z);
			}
		}
		if (age == DURATION - 1) {
			// Coup final : les ennemis sont projetés vers l'extérieur.
			for (LivingEntity target : FX.livingAround(level, player, c, RADIUS + 1)) {
				FX.hit(level, player, target, 18.0f);
				Vec3 out = target.position().subtract(c).normalize().scale(1.4);
				FX.push(target, out.x, 0.6, out.z);
			}
			level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 1, c.z, 3, 1.0, 0.3, 1.0, 0);
			level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.5f, 0.6f);
			return true;
		}
		return false;
	}
}
