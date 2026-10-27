package com.diamondaltar.ability;

import com.diamondaltar.Compat;
import com.diamondaltar.effect.TimedEffect;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

import java.util.Comparator;
import java.util.List;

/** Masse — « Colère du Tonnerre » : la foudre frappe jusqu'à 10 monstres autour de toi, l'un après l'autre. */
public class ThunderEffect extends TimedEffect {
	private static final int MAX_TARGETS = 10;
	private static final float DAMAGE = 30.0f;

	private final ServerPlayer player;
	private final List<LivingEntity> targets;

	public ThunderEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.targets = FX.livingAround(level, player, player.position(), 18).stream()
				.filter(e -> e instanceof Enemy)
				.sorted(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
				.limit(MAX_TARGETS)
				.toList();
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY(1.0), player.getZ(), 40, 0.5, 1, 0.5, 0.5);
		level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8f, 1.4f);
	}

	@Override
	protected boolean tick() {
		int i = age / 3;
		if (age % 3 == 0 && i < targets.size()) {
			LivingEntity t = targets.get(i);
			if (t.isAlive()) {
				LightningBolt bolt = new LightningBolt(Compat.LIGHTNING_BOLT, level);
				bolt.setPos(t.getX(), t.getY(), t.getZ());
				bolt.setVisualOnly(true);
				level.addFreshEntity(bolt);
				Glow.pillar(level, t.position(), 24, 0.55f, Glow.Tint.ICE);
				FX.hit(level, player, t, DAMAGE);
				t.igniteForSeconds(2);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, t.getX(), t.getY(0.5), t.getZ(), 30, 0.4, 0.8, 0.4, 0.4);
			}
		}
		return i >= targets.size();
	}
}
