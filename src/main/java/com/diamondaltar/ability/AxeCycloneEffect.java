package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Hache — « Cyclone d'Acier » : le joueur tourne sur lui-même pendant 2 secondes,
 * entouré de lames tourbillonnantes qui aspirent et frappent les ennemis proches.
 */
public class AxeCycloneEffect extends TimedEffect {
	private static final int DURATION = 40;
	private static final double RADIUS = 4.5;
	private static final DustParticleEffect STEEL = new DustParticleEffect(new Vector3f(0.75f, 0.9f, 1.0f), 1.6f);

	private final ServerPlayerEntity player;

	public AxeCycloneEffect(ServerWorld world, ServerPlayerEntity player) {
		super(world);
		this.player = player;
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.PLAYERS, 1.0f, 1.4f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved() || !player.isAlive()) {
			return true;
		}
		Vec3d c = player.getPos();
		double spin = age * 0.7;
		// 3 lames qui tournent autour du joueur sur des hauteurs différentes.
		for (int blade = 0; blade < 3; blade++) {
			double a = spin + blade * (Math.PI * 2 / 3);
			for (double r = 1.0; r <= RADIUS; r += 0.7) {
				double y = c.y + 0.4 + blade * 0.55;
				double ang = a - r * 0.15;
				world.spawnParticles(STEEL, c.x + Math.cos(ang) * r, y, c.z + Math.sin(ang) * r, 1, 0, 0, 0, 0);
			}
			world.spawnParticles(ParticleTypes.SWEEP_ATTACK,
					c.x + Math.cos(a) * RADIUS * 0.7, c.y + 1.0, c.z + Math.sin(a) * RADIUS * 0.7, 1, 0, 0, 0, 0);
		}
		world.spawnParticles(ParticleTypes.CLOUD, c.x, c.y + 0.1, c.z, 3, 1.5, 0.05, 1.5, 0.05);

		if (age % 5 == 0) {
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS,
					0.9f, 0.8f + age / 80f);
			for (LivingEntity target : FX.livingAround(world, player, c, RADIUS)) {
				FX.hit(player, target, 5.0f);
				Vec3d pull = c.subtract(target.getPos()).normalize().multiply(0.25);
				FX.push(target, pull.x, 0.1, pull.z);
			}
		}
		if (age == DURATION - 1) {
			// Coup final : les ennemis sont projetés vers l'extérieur.
			for (LivingEntity target : FX.livingAround(world, player, c, RADIUS + 1)) {
				FX.hit(player, target, 8.0f);
				Vec3d out = target.getPos().subtract(c).normalize().multiply(1.4);
				FX.push(target, out.x, 0.6, out.z);
			}
			world.spawnParticles(ParticleTypes.EXPLOSION, c.x, c.y + 1, c.z, 3, 1.0, 0.3, 1.0, 0);
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 1.5f, 0.6f);
			return true;
		}
		return false;
	}
}
