package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.world.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Pioche — « Éruption de Cristaux » : une ligne de pics de cristal jaillit du sol sur 16 blocs et projette les ennemis. */
public class CrystalEruptionEffect extends TimedEffect {
	private static final int LENGTH = 16;
	private static final float DAMAGE = 22.0f;
	private static final DustParticleOptions CRYSTAL = new DustParticleOptions(0x9AF4FF, 1.5f);
	private static final DustParticleOptions AMETHYST = new DustParticleOptions(0xB070FF, 1.3f);

	private final ServerPlayer player;
	private final Vec3 start;
	private final Vec3 forward;
	private final Set<UUID> hit = new HashSet<>();
	private final java.util.List<Display.BlockDisplay> spikes = new java.util.ArrayList<>();
	private final java.util.List<Quaternionf> tilts = new java.util.ArrayList<>();

	public CrystalEruptionEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.start = player.position();
		this.forward = FX.forward(player);
	}

	/** Translation : bloc centré horizontalement, base posée au sol (+ décalage vertical). */
	private static Vector3f base(Quaternionf q, float s, float dy) {
		return new Vector3f(-0.5f * s, dy, -0.5f * s).rotate(q);
	}

	@Override
	protected boolean tick() {
		// Les pics restent un instant puis rentrent dans le sol.
		for (int i = 0; i < spikes.size(); i++) {
			int life = age - i;
			if (life == 2) {
				Visuals.transform(spikes.get(i), base(tilts.get(i), 2.6f, 0f), tilts.get(i), 2.6f, 2);
			} else if (life == 22) {
				Visuals.transform(spikes.get(i), base(tilts.get(i), 2.6f, -2.8f), tilts.get(i), 2.6f, 8);
			}
		}
		if (age >= LENGTH) {
			return age >= LENGTH + 32;
		}
		Vec3 p = FX.ground(level, start.add(forward.scale(1.5 + age)).add(0, 2, 0));
		// Pic de cristal : une pointe qui monte puis s'affine.
		for (double y = 0; y < 3.0; y += 0.2) {
			double w = 0.45 * (1 - y / 3.0);
			level.sendParticles(CRYSTAL, p.x, p.y + y, p.z, 2, w, 0.05, w, 0);
			if (y > 1.5) {
				level.sendParticles(AMETHYST, p.x, p.y + y, p.z, 1, w * 0.5, 0.05, w * 0.5, 0);
			}
		}
		level.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 3.1, p.z, 2, 0.05, 0.1, 0.05, 0.05);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()),
				p.x, p.y + 0.2, p.z, 12, 0.4, 0.2, 0.4, 0.2);
		for (LivingEntity target : FX.livingAround(level, player, p.add(0, 1, 0), 1.8)) {
			if (hit.add(target.getUUID())) {
				FX.hit(level, player, target, DAMAGE);
				FX.push(target, forward.x * 0.3, 1.3, forward.z * 0.3);
			}
		}
		level.playSound(null, BlockPos.containing(p), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.5f, 0.5f + age * 0.05f);
		Quaternionf tilt = new Quaternionf().rotateY(level.getRandom().nextFloat() * 6.28f)
				.rotateX((level.getRandom().nextFloat() - 0.5f) * 0.6f);
		Display.BlockDisplay spike = track(Visuals.block(level, p, (age % 3 == 0 ? Blocks.LARGE_AMETHYST_BUD : Blocks.AMETHYST_CLUSTER).defaultBlockState()));
		Visuals.transform(spike, base(tilt, 2.6f, -2.8f), tilt, 2.6f, 0);
		spikes.add(spike);
		tilts.add(tilt);
		return false;
	}
}
