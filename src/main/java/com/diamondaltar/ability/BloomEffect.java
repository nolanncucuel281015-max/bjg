package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Houe — « Floraison » : un jardin magique de 8 blocs soigne les joueurs et fait pousser les cultures pendant 8 s. */
public class BloomEffect extends TimedEffect {
	private static final int DURATION = 160;
	private static final double RADIUS = 8.0;
	private static final DustParticleOptions PINK = new DustParticleOptions(0xFF8AD8, 1.2f);
	private static final DustParticleOptions GREEN = new DustParticleOptions(0x6CFF7A, 1.0f);

	private final ServerPlayer player;

	public BloomEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 1.5f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved()) {
			return true;
		}
		Vec3 c = player.position();
		FX.ring(level, PINK, c.add(0, 0.2, 0), RADIUS, 48);
		for (int i = 0; i < 6; i++) {
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			double r = level.getRandom().nextDouble() * RADIUS;
			level.sendParticles(i % 2 == 0 ? PINK : GREEN, c.x + Math.cos(a) * r, c.y + 0.3, c.z + Math.sin(a) * r, 1, 0, 0.3, 0, 0.02);
		}
		level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, c.x, c.y + 2, c.z, 4, RADIUS / 2, 1, RADIUS / 2, 0);

		if (age % 20 == 0) {
			for (Player p : level.getEntitiesOfClass(Player.class, AABB.ofSize(c, RADIUS * 2, RADIUS * 2, RADIUS * 2))) {
				p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 1));
				p.addEffect(new MobEffectInstance(MobEffects.SATURATION, 2, 0));
				level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY(1.2), p.getZ(), 2, 0.3, 0.2, 0.3, 0);
			}
			BlockPos o = player.blockPosition();
			for (BlockPos m : BlockPos.betweenClosed(o.offset(-8, -2, -8), o.offset(8, 2, 8))) {
				BlockState s = level.getBlockState(m);
				if (s.getBlock() instanceof CropBlock crop && !crop.isMaxAge(s) && level.getRandom().nextInt(3) == 0) {
					BlockPos pos = m.immutable();
					crop.growCrops(level, pos, s);
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0);
				}
			}
		}
		return age >= DURATION;
	}
}
