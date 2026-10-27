package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Pioche — « Vision des Minerais » : les minerais dans un rayon de 14 blocs scintillent de leur couleur pendant 10 s. */
public class OreSightEffect extends TimedEffect {
	private static final int RADIUS = 14;
	private static final int DURATION = 200;
	private static final int MAX = 400;

	private record Ore(BlockPos pos, DustParticleOptions color) {
	}

	private final ServerPlayer player;
	private final List<Ore> ores = new ArrayList<>();

	public OreSightEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		BlockPos c = player.blockPosition();
		for (BlockPos m : BlockPos.betweenClosed(c.offset(-RADIUS, -RADIUS, -RADIUS), c.offset(RADIUS, RADIUS, RADIUS))) {
			if (ores.size() >= MAX) {
				break;
			}
			DustParticleOptions color = colorOf(level.getBlockState(m));
			if (color != null) {
				ores.add(new Ore(m.immutable(), color));
			}
		}
		level.playSound(null, c, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.8f);
	}

	private static boolean isAny(BlockState s, net.minecraft.world.level.block.Block... blocks) {
		for (var b : blocks) {
			if (s.is(b)) {
				return true;
			}
		}
		return false;
	}

	private static DustParticleOptions colorOf(BlockState s) {
		if (isAny(s, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE)) return new DustParticleOptions(0x4FF6FF, 1.6f);
		if (isAny(s, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE)) return new DustParticleOptions(0x30FF60, 1.6f);
		if (s.is(Blocks.ANCIENT_DEBRIS)) return new DustParticleOptions(0xB050FF, 1.8f);
		if (s.is(BlockTags.GOLD_ORES)) return new DustParticleOptions(0xFFD030, 1.3f);
		if (isAny(s, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE)) return new DustParticleOptions(0xFF2020, 1.2f);
		if (isAny(s, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE)) return new DustParticleOptions(0x2050FF, 1.2f);
		if (s.is(BlockTags.IRON_ORES)) return new DustParticleOptions(0xE8C8A8, 1.1f);
		if (s.is(BlockTags.COPPER_ORES)) return new DustParticleOptions(0xFF8040, 1.0f);
		if (isAny(s, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE)) return new DustParticleOptions(0x303030, 1.0f);
		return null;
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved()) {
			return true;
		}
		if (age % 5 == 0) {
			for (Ore ore : ores) {
				if (level.getBlockState(ore.pos()).isAir()) {
					continue;
				}
				level.sendParticles(ore.color(), true, true, ore.pos().getX() + 0.5, ore.pos().getY() + 0.5, ore.pos().getZ() + 0.5,
						3, 0.35, 0.35, 0.35, 0);
			}
		}
		return age >= DURATION;
	}
}
