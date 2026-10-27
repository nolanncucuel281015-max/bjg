package com.diamondaltar.ability;

import com.diamondaltar.Compat;
import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * Pioche — « Géode Céleste » : les ennemis proches sont emprisonnés dans des géodes de cristal
 * qui se referment sur eux, puis tu les fais éclater d'un coup.
 */
public class CelestialGeodeEffect extends TimedEffect {
	private static final int CLOSE = 25;

	private final ServerPlayer player;
	private final List<LivingEntity> targets;
	private final List<Display.BlockDisplay[]> shells = new ArrayList<>();

	public CelestialGeodeEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.targets = FX.livingAround(level, player, player.position(), 12).stream().limit(8).toList();
		for (LivingEntity t : targets) {
			Display.BlockDisplay[] shell = new Display.BlockDisplay[6];
			for (int k = 0; k < shell.length; k++) {
				shell[k] = track(Visuals.block(level, t.position(), (k % 2 == 0 ? Blocks.AMETHYST_BLOCK : Blocks.CALCITE).defaultBlockState()));
				Visuals.transform(shell[k], new org.joml.Vector3f(), new Quaternionf(), 0.01f, 0);
			}
			shells.add(shell);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0f, 0.6f);
	}

	@Override
	protected boolean tick() {
		for (int i = 0; i < targets.size(); i++) {
			LivingEntity t = targets.get(i);
			Display.BlockDisplay[] shell = shells.get(i);
			Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
			double closing = Math.max(0, 1 - age / (double) CLOSE);
			float size = Math.max(1.0f, t.getBbHeight() * 0.8f);
			for (int k = 0; k < shell.length; k++) {
				// 6 plaques (une par face) qui viennent se refermer autour de la cible.
				Vec3 n = switch (k) {
					case 0 -> new Vec3(1, 0, 0);
					case 1 -> new Vec3(-1, 0, 0);
					case 2 -> new Vec3(0, 1, 0);
					case 3 -> new Vec3(0, -1, 0);
					case 4 -> new Vec3(0, 0, 1);
					default -> new Vec3(0, 0, -1);
				};
				Vec3 at = c.add(n.scale(size * 0.5 + closing * 3));
				shell[k].setPos(at);
				Quaternionf q = new Quaternionf().rotateXYZ(age * 0.05f, k, age * 0.03f);
				Visuals.transform(shell[k], Visuals.centered(q, size * 0.7f), q, size * 0.7f, 1);
			}
			if (age < CLOSE) {
				t.setDeltaMovement(0, 0, 0);
				Compat.markHurt(t);
				Cosmic.at(level, Cosmic.NEBULA, c);
			}
		}
		if (age % 6 == 0 && age < CLOSE) {
			level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 1.2f, 0.8f + age * 0.03f);
		}
		if (age >= CLOSE + 8) {
			for (LivingEntity t : targets) {
				Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()), c.x, c.y, c.z, 40, 0.6, 0.6, 0.6, 0.3);
				level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 20, 0.3, 0.3, 0.3, 0.3);
				if (t.isAlive()) {
					FX.hit(level, player, t, 30.0f);
				}
			}
			level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 2.0f, 0.5f);
			level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.0f, 0.6f);
			return true;
		}
		return false;
	}
}
