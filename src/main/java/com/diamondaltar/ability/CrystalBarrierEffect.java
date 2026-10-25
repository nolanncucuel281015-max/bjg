package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Armure — « Bouclier de Cristal » : 8 cristaux tournent autour de toi pendant 6 s,
 * te donnent une grosse absorption et repoussent violemment les ennemis qui approchent.
 */
public class CrystalBarrierEffect extends TimedEffect {
	private static final int DURATION = 120;
	private static final int COUNT = 8;

	private final ServerPlayer player;
	private final Display.BlockDisplay[] crystals = new Display.BlockDisplay[COUNT];

	public CrystalBarrierEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		for (int i = 0; i < COUNT; i++) {
			crystals[i] = track(Visuals.block(level, player.position(), Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState()));
			Visuals.transform(crystals[i], new Vector3f(), new Quaternionf(), 0.01f, 0);
		}
		player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, DURATION, 4, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, DURATION, 2, false, false, true));
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0f, 0.8f);
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.6f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved() || !player.isAlive()) {
			return true;
		}
		Vec3 c = player.position().add(0, 1.0, 0);
		boolean ending = age >= DURATION - 5;
		for (int i = 0; i < COUNT; i++) {
			double a = age * 0.12 + i * Math.PI * 2 / COUNT;
			float s = ending ? 0.01f : 0.55f;
			Quaternionf q = new Quaternionf().rotateY((float) -a).rotateZ((float) (Math.PI / 4)).rotateX((float) (Math.PI / 4));
			Vector3f off = new Vector3f((float) Math.cos(a) * 1.6f, (float) Math.sin(a * 2 + age * 0.1) * 0.5f, (float) Math.sin(a) * 1.6f);
			crystals[i].setPos(c);
			Visuals.transform(crystals[i], off.add(Visuals.centered(q, s)), q, s, ending ? 5 : 1);
		}
		if (age % 3 == 0) {
			level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 3, 0.9, 0.9, 0.9, 0.01);
		}
		for (LivingEntity target : FX.livingAround(level, player, c, 2.4)) {
			Vec3 out = target.position().subtract(player.position()).multiply(1, 0, 1).normalize().scale(1.4);
			FX.push(target, out.x, 0.5, out.z);
			if (age % 10 == 0) {
				FX.hit(level, player, target, 8.0f);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.5), target.getZ(), 10, 0.3, 0.4, 0.3, 0.2);
			}
		}
		return age >= DURATION;
	}
}
