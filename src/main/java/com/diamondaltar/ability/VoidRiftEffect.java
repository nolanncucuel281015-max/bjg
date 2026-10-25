package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Épée — « Faille du Néant » : ouvre un trou noir là où tu vises. Il aspire tous les ennemis
 * pendant 3 secondes en les broyant, puis implose violemment.
 */
public class VoidRiftEffect extends TimedEffect {
	private static final int DURATION = 60;
	private static final double RADIUS = 10.0;
	private static final DustParticleOptions BLACK = new DustParticleOptions(0x100018, 2.5f);
	private static final DustParticleOptions PURPLE = new DustParticleOptions(0x8A2BE2, 1.4f);

	private final ServerPlayer player;
	private final Vec3 center;

	public VoidRiftEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.center = FX.target(player, 20).add(0, 1.0, 0);
		level.playSound(null, BlockPos.containing(center), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 2.0f, 0.5f);
		level.playSound(null, BlockPos.containing(center), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8f, 1.5f);
	}

	@Override
	protected boolean tick() {
		// Cœur noir + disque d'accrétion qui tourne.
		level.sendParticles(BLACK, center.x, center.y, center.z, 8, 0.25, 0.25, 0.25, 0);
		for (int i = 0; i < 3; i++) {
			double a = age * 0.4 + i * (Math.PI * 2 / 3);
			for (double r = 0.8; r < 3.5; r += 0.6) {
				double ang = a + r * 0.9;
				level.sendParticles(PURPLE, center.x + Math.cos(ang) * r, center.y + Math.sin(ang * 0.5) * 0.15, center.z + Math.sin(ang) * r, 1, 0, 0, 0, 0);
			}
		}
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 10, 3.5, 2.0, 3.5, 0.0);
		level.sendParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 15, 0.5, 0.5, 0.5, 1.5);

		for (LivingEntity target : FX.livingAround(level, player, center, RADIUS)) {
			FX.pull(target, center, 0.45);
			if (age % 5 == 0) {
				FX.hit(level, player, target, 5.0f);
			}
		}
		if (age % 20 == 0) {
			level.playSound(null, BlockPos.containing(center), SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.5f);
		}
		if (age >= DURATION) {
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 2, 0.5, 0.5, 0.5, 0);
			level.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 200, 0.2, 0.2, 0.2, 1.5);
			for (LivingEntity target : FX.livingAround(level, player, center, RADIUS)) {
				FX.hit(level, player, target, 30.0f);
				Vec3 out = target.position().subtract(center).normalize().scale(2.0);
				FX.push(target, out.x, 0.8, out.z);
			}
			level.playSound(null, BlockPos.containing(center), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0f, 0.6f);
			level.playSound(null, BlockPos.containing(center), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 2.0f, 0.5f);
			return true;
		}
		return false;
	}
}
