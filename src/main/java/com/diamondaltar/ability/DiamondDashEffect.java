package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Armure — « Ruée de Diamant » : tu fonces en avant comme une comète et tu percutes tout sur ton passage. */
public class DiamondDashEffect extends TimedEffect {
	private static final int DURATION = 8;
	private static final DustParticleOptions CYAN = new DustParticleOptions(0x6FF0FF, 2.0f);

	private final ServerPlayer player;
	private final Vec3 dir;
	private final Set<UUID> hit = new HashSet<>();

	public DiamondDashEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		Vec3 look = player.getLookAngle();
		this.dir = new Vec3(look.x, Math.max(-0.2, Math.min(0.4, look.y)), look.z).normalize();
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20, 4, false, false));
		level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.5f, 1.4f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved()) {
			return true;
		}
		player.setDeltaMovement(dir.scale(1.9));
		player.hurtMarked = true;
		player.fallDistance = 0;
		Vec3 p = player.position().add(0, 1, 0);
		level.sendParticles(CYAN, p.x, p.y, p.z, 10, 0.4, 0.6, 0.4, 0);
		level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 4, 0.3, 0.5, 0.3, 0.05);
		level.sendParticles(ParticleTypes.CLOUD, p.x, p.y - 0.5, p.z, 2, 0.2, 0.1, 0.2, 0.02);
		for (LivingEntity target : FX.livingAround(level, player, p, 2.2)) {
			if (hit.add(target.getUUID())) {
				FX.hit(level, player, target, 22.0f);
				FX.push(target, dir.x * 1.8, 0.7, dir.z * 1.8);
				level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY(0.5), target.getZ(), 1, 0, 0, 0, 0);
				level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.5f, 0.7f);
			}
		}
		if (age >= DURATION) {
			player.setDeltaMovement(player.getDeltaMovement().scale(0.3));
			player.hurtMarked = true;
			return true;
		}
		return false;
	}
}
