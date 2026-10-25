package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.world.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Épée — « Faille du Néant » : ouvre un trou noir là où tu vises. Il aspire tous les ennemis
 * pendant 3 secondes en les broyant, puis implose violemment.
 */
public class VoidRiftEffect extends TimedEffect {
	private static final int DURATION = 60;
	private static final double RADIUS = 10.0;
	private static final DustParticleOptions BLACK = new DustParticleOptions(0x100018, 2.5f);
	private static final DustParticleOptions PURPLE = new DustParticleOptions(0x8A2BE2, 1.4f);

	private final ServerPlayer player;
	private final Vec3 center;
	private final Display.BlockDisplay core;
	private final Display.BlockDisplay[] shards = new Display.BlockDisplay[6];

	public VoidRiftEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.center = FX.target(player, 20).add(0, 1.0, 0);
		this.core = track(Visuals.block(level, center, Blocks.BLACK_CONCRETE.defaultBlockState()));
		Visuals.transform(core, new Vector3f(), new Quaternionf(), 0.01f, 0);
		for (int i = 0; i < shards.length; i++) {
			shards[i] = track(Visuals.block(level, center, (i % 2 == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.AMETHYST_BLOCK).defaultBlockState()));
			Visuals.transform(shards[i], new Vector3f(), new Quaternionf(), 0.01f, 0);
		}
		level.playSound(null, BlockPos.containing(center), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 2.0f, 0.5f);
		level.playSound(null, BlockPos.containing(center), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8f, 1.5f);
	}

	@Override
	protected boolean tick() {
		// Le noyau grossit en tournant dans tous les sens, les éclats tournent autour.
		boolean imploding = age >= DURATION - 4;
		float coreScale = imploding ? 0.05f : Math.min(1.6f, 0.2f + age * 0.08f);
		Quaternionf spin = new Quaternionf().rotateXYZ(age * 0.21f, age * 0.33f, age * 0.17f);
		Visuals.transform(core, Visuals.centered(spin, coreScale), spin, coreScale, imploding ? 4 : 1);
		for (int i = 0; i < shards.length; i++) {
			double a = age * 0.35 + i * Math.PI * 2 / shards.length;
			float orbit = imploding ? 0f : 2.6f;
			float s = imploding ? 0.05f : 0.45f;
			Quaternionf q = new Quaternionf().rotateXYZ(age * 0.4f + i, age * 0.5f, i);
			Vector3f off = new Vector3f((float) Math.cos(a) * orbit, (float) Math.sin(a * 2 + i) * 0.6f, (float) Math.sin(a) * orbit);
			Visuals.transform(shards[i], off.add(Visuals.centered(q, s)), q, s, imploding ? 4 : 1);
		}

		// Cœur noir + disque d'accrétion qui tourne.
		level.sendParticles(BLACK, center.x, center.y, center.z, 8, 0.25, 0.25, 0.25, 0);
		for (int i = 0; i < 3; i++) {
			double a = age * 0.4 + i * (Math.PI * 2 / 3);
			for (double r = 0.8; r < 3.5; r += 0.6) {
				double ang = a + r * 0.9;
				level.sendParticles(PURPLE, center.x + Math.cos(ang) * r, center.y + Math.sin(ang * 0.5) * 0.15, center.z + Math.sin(ang) * r, 1, 0, 0, 0, 0);
			}
		}
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 10, 3.5, 2.0, 3.5, 0.0);
		level.sendParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 15, 0.5, 0.5, 0.5, 1.5);

		for (LivingEntity target : FX.livingAround(level, player, center, RADIUS)) {
			FX.pull(target, center, 0.45);
			if (age % 5 == 0) {
				FX.hit(level, player, target, 5.0f);
			}
		}
		if (age % 20 == 0) {
			level.playSound(null, BlockPos.containing(center), SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.5f);
		}
		if (age >= DURATION) {
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 2, 0.5, 0.5, 0.5, 0);
			level.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 200, 0.2, 0.2, 0.2, 1.5);
			for (LivingEntity target : FX.livingAround(level, player, center, RADIUS)) {
				FX.hit(level, player, target, 30.0f);
				Vec3 out = target.position().subtract(center).normalize().scale(2.0);
				FX.push(target, out.x, 0.8, out.z);
			}
			level.playSound(null, BlockPos.containing(center), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0f, 0.6f);
			FX.shake(level, center, 26, 1.4f, 12);
			Glow.rays(level, center, 16, 12, Glow.Tint.VOID);
			level.playSound(null, BlockPos.containing(center), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 2.0f, 0.5f);
			return true;
		}
		return false;
	}
}
