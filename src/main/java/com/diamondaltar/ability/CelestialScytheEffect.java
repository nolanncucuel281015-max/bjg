package com.diamondaltar.ability;

import com.diamondaltar.Compat;
import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Houe — « Faux Céleste » : ta houe devient une faux d'étoiles géante qui tourne en spirale
 * autour de toi en s'éloignant jusqu'à 12 blocs, fauchant tout sur son passage.
 */
public class CelestialScytheEffect extends TimedEffect {
	private static final int DURATION = 40;
	private static final double MAX_RADIUS = 12.0;

	private final ServerPlayer player;
	private final Display.ItemDisplay scythe;
	private final double start;
	private final Map<UUID, Integer> lastHit = new HashMap<>();

	public CelestialScytheEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		Vec3 f = FX.forward(player);
		this.start = Math.atan2(f.z, f.x);
		this.scythe = track(Visuals.item(level, player.position(), player.getMainHandItem()));
		Visuals.transform(scythe, new org.joml.Vector3f(), new org.joml.Quaternionf(), 0.01f, 0);
		level.playSound(null, player.blockPosition(), SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS, 2.0f, 0.4f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved()) {
			return true;
		}
		double t = age / (double) DURATION;
		double radius = 1.5 + (MAX_RADIUS - 1.5) * Math.sin(t * Math.PI);
		double a = start + age * 0.45;
		Vec3 c = player.position().add(0, 1.0, 0);
		Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
		Vec3 at = c.add(out.scale(radius));
		scythe.setPos(at);
		Vec3 tangent = new Vec3(-Math.sin(a), 0, Math.cos(a));
		Visuals.transform(scythe, new org.joml.Vector3f(), Visuals.pointing(out, (float) (Math.PI / 2)).rotateZ(age * 0.9f), 3.5f, 1);
		// Lame d'étoiles : un arc lumineux qui suit la faux.
		for (int k = 0; k < 10; k++) {
			double back = k * 0.06;
			Vec3 p = c.add(new Vec3(Math.cos(a - back), 0, Math.sin(a - back)).scale(radius));
			Cosmic.at(level, k < 3 ? Cosmic.MOON : Cosmic.MOON_TO_NEBULA, p);
		}
		Cosmic.star(level, at.add(tangent.scale(-0.5)));
		Glow.ribbon(level, at, tangent, out, 3.0f, 0.6f, Compat.PURPLE_STAINED_GLASS, 0xC080FF, 1, 1, 6);
		Cosmic.nebula(level, at, 0.3, 2);
		for (LivingEntity target : FX.livingAround(level, player, at, 2.2)) {
			Integer last = lastHit.get(target.getUUID());
			if (last == null || age - last > 8) {
				lastHit.put(target.getUUID(), age);
				FX.hit(level, player, target, 18.0f);
				player.heal(1.5f);
				FX.push(target, tangent.x * 0.8, 0.4, tangent.z * 0.8);
			}
		}
		if (age % 6 == 0) {
			level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.5f);
		}
		return age >= DURATION;
	}
}
