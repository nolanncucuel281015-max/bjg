package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Houe — « Tempête de Pétales » : une tornade de pétales de nébuleuse t'entoure pendant 5 s,
 * empoisonne et blesse les ennemis et te soigne à chaque coup.
 */
public class NebulaPetalsEffect extends TimedEffect {
	private static final int DURATION = 100;
	private static final double RADIUS = 6.0;

	private final ServerPlayer player;
	private final Display.ItemDisplay[] petals = new Display.ItemDisplay[8];

	public NebulaPetalsEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		for (int i = 0; i < petals.length; i++) {
			petals[i] = track(Visuals.item(level, player.position(), new ItemStack(Items.PINK_PETALS)));
			Visuals.transform(petals[i], new org.joml.Vector3f(), new Quaternionf(), 0.01f, 0);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.CHERRY_LEAVES_BREAK, SoundSource.PLAYERS, 2.0f, 0.6f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved()) {
			return true;
		}
		Vec3 c = player.position();
		boolean ending = age >= DURATION - 6;
		for (int i = 0; i < petals.length; i++) {
			double a = age * 0.25 + i * Math.PI * 2 / petals.length;
			double h = 0.5 + (i % 4) * 0.7 + Math.sin(age * 0.1 + i) * 0.3;
			double r = 2.0 + (i % 3) * 1.3;
			petals[i].setPos(c.add(Math.cos(a) * r, h, Math.sin(a) * r));
			Visuals.transform(petals[i], new org.joml.Vector3f(), new Quaternionf().rotateY((float) -a).rotateX(age * 0.2f), ending ? 0.01f : 1.3f, ending ? 6 : 1);
		}
		for (int k = 0; k < 14; k++) {
			double a = age * 0.3 + k * 0.45;
			double r = 1 + (k % 7) * 0.75;
			double h = (k * 0.37 + age * 0.05) % 3.5;
			Cosmic.at(level, k % 2 == 0 ? Cosmic.NEBULA_PINK : Cosmic.NEBULA_TO_PINK, c.add(Math.cos(a) * r, h, Math.sin(a) * r));
		}
		if (age % 5 == 0) {
			for (LivingEntity t : FX.livingAround(level, player, c, RADIUS)) {
				FX.hit(level, player, t, 6.0f);
				t.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1));
				player.heal(0.5f);
			}
		}
		if (age % 20 == 0) {
			level.playSound(null, player.blockPosition(), SoundEvents.CHERRY_LEAVES_STEP, SoundSource.PLAYERS, 1.5f, 0.6f);
		}
		return age >= DURATION;
	}
}
