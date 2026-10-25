package com.diamondaltar.plugin;

import com.diamondaltar.ModComponents;
import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

/** L'animation d'infusion (5 s) : l'objet monte en tournant de plus en plus vite, arcs électriques, éclair final. */
final class AltarInfusionEffect extends TimedEffect {
	private static final int DURATION = 100;
	private static final DustParticleOptions DIAMOND_DUST = new DustParticleOptions(0xDDEBFF, 1.2f);
	private static final DustParticleOptions WHITE_DUST = new DustParticleOptions(0xFFFFFF, 0.8f);

	private final AltarManager.Altar altar;
	private final Runnable onDone;
	private final BlockPos pos;
	private float spin;

	AltarInfusionEffect(ServerLevel level, AltarManager.Altar altar, Runnable onDone) {
		super(level);
		this.altar = altar;
		this.onDone = onDone;
		this.pos = new BlockPos(altar.x, altar.y, altar.z);
		level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.2f);
		level.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.0f, 0.8f);
	}

	@Override
	protected boolean tick() {
		int p = age + 1;
		float t = p / (float) DURATION;
		double rise = 0.6 * Math.sin(t * Math.PI / 2);
		Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 1.25 + rise + 0.25, pos.getZ() + 0.5);

		spin += 0.1f + 0.6f * t * t;
		if (altar.floating != null) {
			float shake = t * 0.03f * (float) Math.sin(p * 3.1);
			Visuals.transform(altar.floating, new Vector3f(shake, (float) rise, -shake), new Quaternionf().rotateY(spin), 0.9f + 0.3f * t, 1);
		}

		double radius = 1.1 - 0.6 * t;
		double ring = p * (0.15 + 0.35 * t);
		for (int i = 0; i < 3; i++) {
			double a = ring + i * (Math.PI * 2 / 3);
			level.sendParticles(ParticleTypes.END_ROD, center.x + Math.cos(a) * radius, center.y + Math.sin(a * 2) * 0.15, center.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
		}
		if (p % 4 == 0) {
			double[][] pillars = {{0.18, 0.18}, {0.82, 0.18}, {0.18, 0.82}, {0.82, 0.82}};
			for (double[] c : pillars) {
				arc(new Vec3(pos.getX() + c[0], pos.getY() + 1.05, pos.getZ() + c[1]), center, level.getRandom().nextLong());
			}
			level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7f, 0.5f + t * 1.5f);
		}
		if (p > DURATION / 3) {
			double a = p * 0.5;
			for (int k = 0; k < 2; k++) {
				double ang = a + k * Math.PI;
				level.sendParticles(DIAMOND_DUST, center.x + Math.cos(ang) * 0.9, pos.getY() + 0.4 + (p % 20) / 20.0, center.z + Math.sin(ang) * 0.9, 1, 0, 0, 0, 0);
			}
			level.sendParticles(ParticleTypes.ENCHANT, center.x, center.y, center.z, 4, 0.6, 0.4, 0.6, 0.6);
		}
		if (p % 20 == 0) {
			level.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 1.0f, 0.8f + t);
		}
		if (p >= DURATION) {
			LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
			bolt.setPos(center.x, pos.getY() + 1.0, center.z);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, center.x, center.y, center.z, 80, 0.2, 0.2, 0.2, 0.6);
			level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 40, 0.1, 0.1, 0.1, 0.25);
			level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0, 0, 0, 0);
			level.playSound(null, pos, SoundEvents.TOTEM_USE, SoundSource.BLOCKS, 1.0f, 1.3f);
			level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0f, 0.7f);
			ModComponents.infuse(altar.item);
			onDone.run();
			return true;
		}
		return false;
	}

	private void arc(Vec3 from, Vec3 to, long seed) {
		Random rnd = new Random(seed);
		Vec3 prev = from;
		for (int i = 1; i <= 8; i++) {
			Vec3 next = from.lerp(to, i / 8.0);
			if (i < 8) {
				next = next.add((rnd.nextDouble() - 0.5) * 0.25, (rnd.nextDouble() - 0.5) * 0.25, (rnd.nextDouble() - 0.5) * 0.25);
			}
			Vec3 mid = prev.lerp(next, 0.5);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, next.x, next.y, next.z, 1, 0, 0, 0, 0);
			level.sendParticles(WHITE_DUST, mid.x, mid.y, mid.z, 1, 0, 0, 0, 0);
			prev = next;
		}
	}
}
