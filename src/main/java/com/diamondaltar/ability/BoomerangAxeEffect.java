package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.world.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Hache — « Hache Boomerang » : une hache spectrale tournoyante part à 16 blocs puis revient dans ta main. */
public class BoomerangAxeEffect extends TimedEffect {
	private static final int OUT_TICKS = 13;
	private static final double SPEED = 1.25;
	private static final float DAMAGE = 20.0f;
	private static final DustParticleOptions STEEL = new DustParticleOptions(0xD0F4FF, 1.3f);

	private final ServerPlayer player;
	private final Vec3 dir;
	private Vec3 pos;
	private boolean returning;
	private final Set<UUID> hit = new HashSet<>();
	private final Display.ItemDisplay axe;

	public BoomerangAxeEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.dir = player.getLookAngle();
		this.pos = player.getEyePosition().subtract(0, 0.2, 0);
		this.axe = track(Visuals.item(level, pos, player.getMainHandItem()));
		Visuals.transform(axe, new Vector3f(), Visuals.flatSpin(0), 2.2f, 0);
		level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2f, 1.5f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved()) {
			return true;
		}
		if (!returning && (age >= OUT_TICKS || FX.solid(level, BlockPos.containing(pos)))) {
			returning = true;
			hit.clear();
		}
		Vec3 home = player.getEyePosition().subtract(0, 0.2, 0);
		if (returning) {
			Vec3 to = home.subtract(pos);
			if (to.length() < 1.5) {
				level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 0.6f);
				return true;
			}
			pos = pos.add(to.normalize().scale(Math.min(SPEED * 1.3, to.length())));
		} else {
			pos = pos.add(dir.scale(SPEED));
		}

		axe.setPos(pos);
		Visuals.transform(axe, new Vector3f(), Visuals.flatSpin(-age * 1.1f), 2.2f, 1);

		// Lame qui tourne : deux arcs opposés en rotation rapide.
		double spin = age * 1.3;
		for (int k = 0; k < 2; k++) {
			for (double r = 0.2; r <= 1.0; r += 0.2) {
				double a = spin + k * Math.PI + r * 0.6;
				level.sendParticles(STEEL, pos.x + Math.cos(a) * r, pos.y, pos.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
			}
		}
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 2, 0.2, 0.2, 0.2, 0.1);
		if (age % 3 == 0) {
			level.playSound(null, BlockPos.containing(pos), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.6f, 1.8f);
		}

		for (LivingEntity target : FX.livingAround(level, player, pos, 1.6)) {
			if (hit.add(target.getUUID())) {
				FX.hit(level, player, target, DAMAGE);
				level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY(0.5), target.getZ(), 10, 0.3, 0.4, 0.3, 0.2);
			}
		}
		return age > 80;
	}
}
