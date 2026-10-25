package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Masse — « Pulsar » : tu deviens une étoile à neutrons : deux jets de lumière jaillissent vers
 * le ciel et le sol, et 5 pulsations d'énergie repoussent et frappent tout autour de toi.
 */
public class PulsarEffect extends TimedEffect {
	private static final int PULSES = 5;
	private static final int PERIOD = 12;
	private static final double RADIUS = 10.0;
	private static final DustParticleOptions BLUE = new DustParticleOptions(0x5AC8FF, 2.0f);

	private final ServerPlayer player;
	private Set<UUID> hit = new HashSet<>();

	public PulsarEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved()) {
			return true;
		}
		Vec3 c = player.position().add(0, 1.0, 0);
		// Jets du pulsar (haut et bas) qui tournent un peu.
		double tilt = Math.sin(age * 0.2) * 0.25;
		for (double y = 0; y < 14; y += 0.5) {
			Cosmic.at(level, y < 4 ? Cosmic.MOON : BLUE, c.add(tilt * y, y, 0));
			if (y < 3) {
				Cosmic.at(level, BLUE, c.add(-tilt * y, -y * 0.3, 0));
			}
		}
		int phase = age % PERIOD;
		if (phase == 0) {
			hit = new HashSet<>();
			level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0f, 1.8f);
			level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0f, 1.0f);
			FX.shake(level, c, 16, 0.5f, 4);
			Glow.pillar(level, c, 16, 0.5f, Glow.Tint.ICE);
			Glow.rays(level, c.add(0, -0.6, 0), 12, (float) RADIUS, Glow.Tint.ICE);
		}
		if (phase < 6) {
			double r = (phase + 1) * RADIUS / 6;
			FX.ring(level, BLUE, c.add(0, -0.6, 0), r, (int) (r * 9));
			FX.ring(level, Cosmic.MOON_SMALL, c.add(0, -0.3, 0), r - 0.3, (int) (r * 6));
			for (LivingEntity t : FX.livingAround(level, player, c, r)) {
				if (hit.add(t.getUUID())) {
					FX.hit(level, player, t, 12.0f);
					Vec3 out = t.position().subtract(c).multiply(1, 0, 1).normalize().scale(1.3);
					FX.push(t, out.x, 0.4, out.z);
				}
			}
		}
		return age >= PULSES * PERIOD;
	}
}
