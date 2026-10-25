package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Lance — « Tourbillon Galactique » : ta lance tourne autour de toi en dessinant une galaxie
 * à 4 bras ; les ennemis sont aspirés dans la spirale et déchiquetés pendant 3 s.
 */
public class GalacticWhirlEffect extends TimedEffect {
	private static final int DURATION = 60;
	private static final double RADIUS = 8.0;

	private final ServerPlayer player;
	private final Display.ItemDisplay[] spears = new Display.ItemDisplay[2];

	public GalacticWhirlEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		for (int i = 0; i < spears.length; i++) {
			spears[i] = track(Visuals.item(level, player.position(), player.getMainHandItem()));
			Visuals.transform(spears[i], new org.joml.Vector3f(), new org.joml.Quaternionf(), 0.01f, 0);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.0f, 1.6f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved()) {
			return true;
		}
		Vec3 c = player.position().add(0, 1.0, 0);
		double spin = age * 0.5;
		for (int i = 0; i < spears.length; i++) {
			double a = spin + i * Math.PI;
			Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
			spears[i].setPos(c);
			Visuals.transform(spears[i], out.scale(2.2).toVector3f(), Visuals.pointing(out, (float) spin), 3.0f, 1);
		}
		// Galaxie à 4 bras qui tourne.
		for (int arm = 0; arm < 4; arm++) {
			for (double r = 0.6; r < RADIUS; r += 0.5) {
				double a = -spin * 0.6 + arm * Math.PI / 2 + r * 0.45;
				Cosmic.at(level, r < 3 ? Cosmic.MOON_SMALL : (arm % 2 == 0 ? Cosmic.NEBULA : Cosmic.NEBULA_PINK),
						c.add(Math.cos(a) * r, -0.8 + Math.sin(r + age * 0.2) * 0.1, Math.sin(a) * r));
			}
		}
		Cosmic.starfield(level, c, RADIUS * 0.7, 6);
		for (LivingEntity target : FX.livingAround(level, player, c, RADIUS)) {
			// Aspiration en spirale.
			Vec3 to = c.subtract(target.position()).multiply(1, 0, 1);
			Vec3 tangent = new Vec3(-to.z, 0, to.x).normalize();
			Vec3 v = to.normalize().scale(0.25).add(tangent.scale(0.35));
			target.setDeltaMovement(v.x, target.getDeltaMovement().y, v.z);
			target.hurtMarked = true;
			if (age % 6 == 0 && target.distanceToSqr(c) < 12) {
				FX.hit(level, player, target, 9.0f);
			}
		}
		if (age % 8 == 0) {
			level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.4f);
		}
		return age >= DURATION;
	}
}
