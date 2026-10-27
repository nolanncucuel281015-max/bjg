package com.diamondaltar.realms;

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

/**
 * Décor de chaque royaume (feature « diamondaltar:realm_decor_<royaume> ») : flèches de basalte et de magma
 * (Braise), pics de glace (Givre), pylônes foudroyés (Tempêtes), grands arbres (Sylvestre), coraux de prismarine
 * (Abysses), éclats d'obsidienne pleureuse (Ombres) et obélisques dorés (Soleil).
 */
public final class RealmFeatures {
	private RealmFeatures() {
	}

	public static void register() {
		for (Realm r : Realm.values()) {
			// #if 1.21.11
			Registry.register(BuiltInRegistries.FEATURE, DiamondAltar.id("realm_decor_" + r.id), new Decor(r, false));
			Registry.register(BuiltInRegistries.FEATURE, DiamondAltar.id("realm_shrine_" + r.id), new Decor(r, true));
			// #else
			//? Decor d = new Decor(r, false);
			//? Registry.register(BuiltInRegistries.FEATURE_TYPE, DiamondAltar.id("realm_decor_" + r.id), d.codec);
			//? Decor shrine = new Decor(r, true);
			//? Registry.register(BuiltInRegistries.FEATURE_TYPE, DiamondAltar.id("realm_shrine_" + r.id), shrine.codec);
			// #endif
		}
	}

	// #if 1.21.11
	static final class Decor extends net.minecraft.world.level.levelgen.feature.Feature<net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration> {
		private final Realm realm;
		private final boolean shrine;

		Decor(Realm realm, boolean shrine) {
			super(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC);
			this.realm = realm;
			this.shrine = shrine;
		}

		@Override
		public boolean place(net.minecraft.world.level.levelgen.feature.FeaturePlaceContext<net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration> ctx) {
			return shrine ? RealmFeatures.shrine(realm, ctx.level(), ctx.random(), ctx.origin())
					: RealmFeatures.place(realm, ctx.level(), ctx.random(), ctx.origin());
		}
	}
	// #else
	//? static final class Decor implements net.minecraft.world.level.levelgen.feature.Feature {
	//? 	private final Realm realm;
	//? 	private final boolean shrine;
	//? 	final com.mojang.serialization.MapCodec<Decor> codec;
	//?
	//? 	Decor(Realm realm, boolean shrine) {
	//? 		this.realm = realm;
	//? 		this.shrine = shrine;
	//? 		this.codec = com.mojang.serialization.MapCodec.unit(this);
	//? 	}
	//?
	//? 	@Override
	//? 	public com.mojang.serialization.MapCodec<Decor> codec() {
	//? 		return codec;
	//? 	}
	//?
	//? 	@Override
	//? 	public boolean place(WorldGenLevel level, net.minecraft.world.level.chunk.ChunkGenerator generator, RandomSource random, BlockPos origin) {
	//? 		return shrine ? RealmFeatures.shrine(realm, level, random, origin) : RealmFeatures.place(realm, level, random, origin);
	//? 	}
	//? }
	// #endif

	private static boolean isGround(Realm realm, BlockState state) {
		int i = realm.ordinal();
		return state.is(RealmContent.SOIL[i]) || state.is(RealmContent.STONE[i]) || !state.isAir() && state.isSolid();
	}

	private static boolean free(WorldGenLevel level, BlockPos pos) {
		return level.getBlockState(pos).isAir();
	}

	private static void put(WorldGenLevel level, BlockPos pos, BlockState state) {
		if (free(level, pos) || level.getBlockState(pos).is(RealmContent.SYLVAN_LEAVES)) {
			level.setBlock(pos, state, 2);
		}
	}

	static boolean place(Realm realm, WorldGenLevel level, RandomSource random, BlockPos base) {
		if (!isGround(realm, level.getBlockState(base.below())) || !free(level, base)) {
			return false;
		}
		if (base.getY() + 16 >= level.getMaxY()) {
			return false;
		}
		BlockState stone = RealmContent.STONE[realm.ordinal()].defaultBlockState();
		switch (realm) {
			case EMBER -> spire(level, random, base, Blocks.BASALT.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState(), 4 + random.nextInt(6));
			case FROST -> spike(level, random, base, Blocks.PACKED_ICE.defaultBlockState(), Blocks.BLUE_ICE.defaultBlockState(), 5 + random.nextInt(8));
			case STORM -> spire(level, random, base, stone, Blocks.SEA_LANTERN.defaultBlockState(), 5 + random.nextInt(5));
			case SYLVAN -> tree(level, random, base);
			case ABYSS -> coral(level, random, base);
			case SHADOW -> spike(level, random, base, Blocks.OBSIDIAN.defaultBlockState(), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 4 + random.nextInt(7));
			case SOLAR -> spire(level, random, base, Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.GLOWSTONE.defaultBlockState(), 3 + random.nextInt(5));
		}
		return true;
	}

	/**
	 * Sanctuaire du royaume : une dalle de 9 × 9 en pierre du royaume, 4 piliers en Cadres de Portail du royaume
	 * surmontés de minerai, des marches, et au centre l'Autel qui réveille le boss quand un joueur approche.
	 */
	static boolean shrine(Realm realm, WorldGenLevel level, RandomSource random, BlockPos base) {
		if (!isGround(realm, level.getBlockState(base.below())) || base.getY() + 12 >= level.getMaxY() || base.getY() < level.getMinY() + 8) {
			return false;
		}
		int i = realm.ordinal();
		BlockState stone = RealmContent.STONE[i].defaultBlockState();
		BlockState frame = RealmContent.PORTAL_FRAME[i].defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				BlockPos floor = base.offset(dx, -1, dz);
				level.setBlock(floor, stone, 2);
				// fondations, pour ne pas flotter au bord d'une île
				for (int d = 1; d <= 3 && level.getBlockState(floor.below(d)).isAir(); d++) {
					level.setBlock(floor.below(d), stone, 2);
				}
				for (int dy = 0; dy < 7; dy++) {
					level.setBlock(base.offset(dx, dy, dz), air, 2);
				}
				boolean edge = Math.abs(dx) == 4 || Math.abs(dz) == 4;
				if (edge && (dx + dz) % 2 == 0) {
					level.setBlock(base.offset(dx, 0, dz), frame, 2);
				}
			}
		}
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				BlockPos pillar = base.offset(sx * 3, 0, sz * 3);
				for (int dy = 0; dy < 4; dy++) {
					level.setBlock(pillar.above(dy), frame, 2);
				}
				level.setBlock(pillar.above(4), RealmContent.ORE[i].defaultBlockState(), 2);
			}
		}
		// estrade et autel
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				level.setBlock(base.offset(dx, 0, dz), frame, 2);
			}
		}
		level.setBlock(base.above(), RealmContent.REALM_ALTAR.defaultBlockState().setValue(RealmAltarBlock.REALM, i), 2);
		return true;
	}

	/** Colonne avec un bloc lumineux au sommet et parfois des blocs autour du pied. */
	private static void spire(WorldGenLevel level, RandomSource random, BlockPos base, BlockState body, BlockState top, int height) {
		for (int y = 0; y < height; y++) {
			put(level, base.above(y), body);
		}
		put(level, base.above(height), top);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (random.nextInt(3) == 0) {
				int h = 1 + random.nextInt(Math.max(1, height / 2));
				for (int y = 0; y < h; y++) {
					put(level, base.relative(d).above(y), body);
				}
			}
		}
	}

	/** Pic pointu (base 3×3 qui s'affine), avec une veine de bloc secondaire. */
	private static void spike(WorldGenLevel level, RandomSource random, BlockPos base, BlockState body, BlockState vein, int height) {
		for (int y = 0; y < height; y++) {
			int r = y < height / 3 ? 1 : 0;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (r == 1 && Math.abs(dx) + Math.abs(dz) == 2 && random.nextBoolean()) {
						continue;
					}
					put(level, base.offset(dx, y, dz), random.nextInt(5) == 0 ? vein : body);
				}
			}
		}
	}

	/** Grand arbre sylvestre : tronc épais en bas, large couronne de feuilles lumineuses. */
	private static void tree(WorldGenLevel level, RandomSource random, BlockPos base) {
		int height = 6 + random.nextInt(5);
		for (int y = 0; y <= height + 2; y++) {
			if (!free(level, base.above(y))) {
				return;
			}
		}
		BlockState log = RealmContent.SYLVAN_LOG.defaultBlockState();
		BlockState leaves = RealmContent.SYLVAN_LEAVES.defaultBlockState();
		for (int y = 0; y < height; y++) {
			put(level, base.above(y), log);
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (random.nextBoolean()) {
				put(level, base.relative(d), log.setValue(RotatedPillarBlock.AXIS, d.getAxis()));
			}
		}
		BlockPos top = base.above(height);
		int radius = 3;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					double d = dx * dx + dy * dy * 2.2 + dz * dz;
					if (d <= radius * radius + 1 && random.nextInt(9) != 0) {
						put(level, top.offset(dx, dy, dz), leaves);
					}
				}
			}
		}
		put(level, top, log);
	}

	/** Corail de prismarine : branches qui montent, lanternes marines au bout. */
	private static void coral(WorldGenLevel level, RandomSource random, BlockPos base) {
		BlockState body = Blocks.PRISMARINE.defaultBlockState();
		BlockState dark = Blocks.DARK_PRISMARINE.defaultBlockState();
		int branches = 2 + random.nextInt(3);
		for (int b = 0; b < branches; b++) {
			BlockPos p = base;
			int len = 3 + random.nextInt(5);
			for (int i = 0; i < len; i++) {
				put(level, p, random.nextInt(3) == 0 ? dark : body);
				p = p.above();
				if (random.nextInt(3) == 0) {
					p = p.relative(Direction.Plane.HORIZONTAL.getRandomDirection(random));
				}
			}
			put(level, p, Blocks.SEA_LANTERN.defaultBlockState());
		}
	}
}
