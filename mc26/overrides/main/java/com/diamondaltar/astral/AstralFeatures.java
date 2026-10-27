package com.diamondaltar.astral;

import com.diamondaltar.DiamondAltar;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.chunk.ChunkGenerator;

/** Décoration de la Dimension Astrale : arbres astraux et rochers (version 26.3 : les features sont des codecs). */
public final class AstralFeatures {
	public static final MapCodec<AstralTree> TREE_CODEC = MapCodec.unit(AstralTree::new);
	public static final MapCodec<AstralRock> ROCK_CODEC = MapCodec.unit(AstralRock::new);

	private AstralFeatures() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, DiamondAltar.id("astral_tree"), TREE_CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, DiamondAltar.id("astral_rock"), ROCK_CODEC);
	}

	private static int debugCount;

	static void debug(String msg) {
		if (debugCount++ < 30) {
			DiamondAltar.LOGGER.info("[arbre astral] " + msg);
		}
	}

	static boolean isGround(BlockState state) {
		return state.is(AstralContent.LUNAR_REGOLITH) || state.is(AstralContent.ASTRAL_STONE)
				|| state.is(net.minecraft.world.level.block.Blocks.AMETHYST_BLOCK);
	}

	static boolean replaceable(WorldGenLevel level, BlockPos pos) {
		BlockState st = level.getBlockState(pos);
		return st.isAir() || st.is(AstralContent.ASTRAL_LEAVES);
	}

	/** Arbre astral : tronc blanc argenté, feuillage cristallin violet en boule, quelques cristaux lumineux. */
	static final class AstralTree implements Feature {
		@Override
		public MapCodec<AstralTree> codec() {
			return TREE_CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos base) {
			debug("appel en " + base);
			if (!isGround(level.getBlockState(base.below()))) {
				debug("sol " + level.getBlockState(base.below()) + " en " + base);
				return false;
			}
			boolean tall = random.nextInt(4) == 0;
			int height = tall ? 9 + random.nextInt(4) : 5 + random.nextInt(3);
			if (base.getY() + height + 4 >= level.getMaxY()) {
				debug("trop haut " + base + " maxY=" + level.getMaxY());
				return false;
			}
			for (int y = 0; y <= height + 2; y++) {
				if (!replaceable(level, base.above(y))) {
					debug("bloque par " + level.getBlockState(base.above(y)) + " en " + base.above(y));
					return false;
				}
			}
			BlockState log = AstralContent.ASTRAL_LOG.defaultBlockState();
			BlockState leaves = AstralContent.ASTRAL_LEAVES.defaultBlockState();
			level.setBlock(base.below(), AstralContent.LUNAR_REGOLITH.defaultBlockState(), 2);
			for (int y = 0; y < height; y++) {
				level.setBlock(base.above(y), log, 2);
			}
			BlockPos top = base.above(height);
			// Branches pour les grands arbres
			if (tall) {
				for (Direction dir : Direction.Plane.HORIZONTAL) {
					if (random.nextBoolean()) {
						BlockPos b = base.above(height - 3 - random.nextInt(2));
						for (int i = 1; i <= 2; i++) {
							BlockPos bp = b.relative(dir, i).above(i - 1);
							if (replaceable(level, bp)) {
								level.setBlock(bp, log.setValue(RotatedPillarBlock.AXIS, dir.getAxis()), 2);
							}
						}
						blob(level, random, b.relative(dir, 2).above(2), 2, leaves);
					}
				}
			}
			int radius = tall ? 3 : 2;
			blob(level, random, top, radius, leaves);
			level.setBlock(top.below(), log, 2);
			// Cristaux lumineux qui pendent sous le feuillage
			for (int i = 0; i < 3 + random.nextInt(3); i++) {
				BlockPos p = top.offset(random.nextInt(radius * 2 + 1) - radius, -radius - 1, random.nextInt(radius * 2 + 1) - radius);
				if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).is(AstralContent.ASTRAL_LEAVES)) {
					level.setBlock(p, Blocks.AMETHYST_CLUSTER.defaultBlockState()
							.setValue(net.minecraft.world.level.block.AmethystClusterBlock.FACING, Direction.DOWN), 2);
				}
			}
			return true;
		}

		private static void blob(WorldGenLevel level, RandomSource random, BlockPos center, int r, BlockState leaves) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dy = -r; dy <= r; dy++) {
					for (int dz = -r; dz <= r; dz++) {
						double d = Math.sqrt(dx * dx + dy * dy * 1.4 + dz * dz);
						if (d <= r + 0.4 - random.nextFloat() * 0.6) {
							BlockPos p = center.offset(dx, dy, dz);
							if (level.getBlockState(p).isAir()) {
								level.setBlock(p, leaves, 2);
							}
						}
					}
				}
			}
		}
	}

	/** Rocher de pierre astrale avec des éclats de régolithe, de calcite et parfois un minerai de Lunarite. */
	static final class AstralRock implements Feature {
		@Override
		public MapCodec<AstralRock> codec() {
			return ROCK_CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!isGround(level.getBlockState(origin.below()))) {
				return false;
			}
			int pieces = 1 + random.nextInt(3);
			for (int n = 0; n < pieces; n++) {
				BlockPos c = origin.offset(random.nextInt(3) - 1, -1 + random.nextInt(2), random.nextInt(3) - 1);
				float rx = 1.2f + random.nextFloat() * 1.6f;
				float ry = 1f + random.nextFloat() * 1.4f;
				float rz = 1.2f + random.nextFloat() * 1.6f;
				int mx = (int) Math.ceil(rx), my = (int) Math.ceil(ry), mz = (int) Math.ceil(rz);
				for (int dx = -mx; dx <= mx; dx++) {
					for (int dy = -my; dy <= my; dy++) {
						for (int dz = -mz; dz <= mz; dz++) {
							double d = (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) + (dz * dz) / (rz * rz);
							if (d > 1.0) {
								continue;
							}
							BlockPos p = c.offset(dx, dy, dz);
							BlockState cur = level.getBlockState(p);
							if (!cur.isAir() && !isGround(cur)) {
								continue;
							}
							float r = random.nextFloat();
							BlockState st = r < 0.08f ? AstralContent.LUNARITE_ORE.defaultBlockState()
									: r < 0.22f ? Blocks.CALCITE.defaultBlockState()
									: r < 0.32f ? AstralContent.LUNAR_REGOLITH.defaultBlockState()
									: AstralContent.ASTRAL_STONE.defaultBlockState();
							level.setBlock(p, st, 2);
						}
					}
				}
			}
			// Petit cristal d'améthyste sur le dessus
			if (random.nextInt(3) == 0) {
				BlockPos top = origin.above(2);
				while (!level.getBlockState(top).isAir() && top.getY() < level.getMaxY() - 1) {
					top = top.above();
				}
				if (!level.getBlockState(top.below()).isAir()) {
					level.setBlock(top, Blocks.AMETHYST_CLUSTER.defaultBlockState(), 2);
				}
			}
			return true;
		}
	}
}
