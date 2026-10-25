package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.world.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Épée — « Pas de l'Ombre » : téléportation éclair de 10 blocs qui tranche tout sur le passage. */
public class ShadowStepEffect extends TimedEffect {
	private static final double DISTANCE = 10.0;
	private static final float DAMAGE = 24.0f;

	private final ServerPlayer player;
	private final Vec3 start;
	private final Vec3 end;
	private final Display.ItemDisplay[] cross = new Display.ItemDisplay[2];
	private final Vec3 facing;

	public ShadowStepEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.start = player.position();
		Vec3 f = FX.forward(player);
		Vec3 dest = start;
		for (double d = 0.5; d <= DISTANCE; d += 0.5) {
			Vec3 p = start.add(f.scale(d));
			BlockPos feet = BlockPos.containing(p.x, p.y + 0.1, p.z);
			if (FX.solid(level, feet) || FX.solid(level, feet.above())) {
				break;
			}
			dest = p;
		}
		this.end = dest;
		this.facing = f;
		Vec3 mid = start.lerp(end, 0.5).add(0, 1.0, 0);
		for (int k = 0; k < 2; k++) {
			cross[k] = track(Visuals.item(level, mid, player.getMainHandItem()));
			Visuals.transform(cross[k], new Vector3f(), crossRotation(k, 0), 0.5f, 0);
		}

		for (LivingEntity target : FX.livingAround(level, player, start.lerp(end, 0.5), start.distanceTo(end) / 2 + 2)) {
			if (distanceToSegment(target.position().add(0, 0.9, 0), start.add(0, 0.9, 0), end.add(0, 0.9, 0)) < 1.8) {
				FX.hit(level, player, target, DAMAGE);
				FX.push(target, 0, 0.5, 0);
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.5), target.getZ(), 2, 0.2, 0.2, 0.2, 0);
			}
		}
		Glow.beam(level, start.add(0, 1, 0), end.add(0, 1, 0), 0.35f, Glow.Tint.NEBULA, 4);
		player.teleportTo(end.x, end.y, end.z);
		player.fallDistance = 0;
		level.playSound(null, BlockPos.containing(start), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.6f);
		level.playSound(null, BlockPos.containing(end), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.5f, 0.6f);
	}

	/** Les deux épées forment un X vertical perpendiculaire au déplacement, qui grandit puis s'efface. */
	private Quaternionf crossRotation(int k, float t) {
		float tilt = (float) Math.toRadians(k == 0 ? 45 + t * 40 : -45 - t * 40);
		return new Quaternionf().rotateY((float) Math.atan2(facing.x, facing.z)).rotateZ(tilt - (float) (Math.PI / 4));
	}

	private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double len = ab.lengthSqr();
		double t = len < 1.0E-6 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / len));
		return p.distanceTo(a.add(ab.scale(t)));
	}

	@Override
	protected boolean tick() {
		// Traînée d'ombre qui reste quelques ticks entre le départ et l'arrivée.
		double len = start.distanceTo(end);
		for (double d = 0; d <= len; d += 0.4) {
			Vec3 p = start.lerp(end, len == 0 ? 0 : d / len);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y + 1.0, p.z, 1, 0.15, 0.4, 0.15, 0.01);
			if (age < 2) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y + 1.0, p.z, 1, 0.1, 0.3, 0.1, 0.01);
			}
		}
		float t = Math.min(1f, age / 5f);
		float scale = age < 6 ? 1.0f + t * (float) Math.max(3.0, start.distanceTo(end)) : 0.01f;
		for (int k = 0; k < 2; k++) {
			Visuals.transform(cross[k], new Vector3f(), crossRotation(k, t), scale, age < 6 ? 2 : 3);
		}
		return age >= 10;
	}
}
