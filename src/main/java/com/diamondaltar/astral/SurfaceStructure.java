package com.diamondaltar.astral;

import com.diamondaltar.DiamondAltar;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * Structure posée sur la surface d'une île : elle n'apparaît que si toute sa zone a du sol
 * (jamais dans le vide entre les îles), puis délègue la construction à une structure jigsaw.
 */
public class SurfaceStructure extends Structure {
	public static final MapCodec<SurfaceStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			settingsCodec(i),
			Structure.DIRECT_CODEC.fieldOf("delegate").forGetter(s -> s.delegate),
			com.mojang.serialization.Codec.INT.optionalFieldOf("footprint", 12).forGetter(s -> s.footprint)
	).apply(i, SurfaceStructure::new));

	public static final StructureType<SurfaceStructure> TYPE = () -> CODEC;

	private final Structure delegate;
	private final int footprint;

	public SurfaceStructure(StructureSettings settings, Structure delegate, int footprint) {
		super(settings);
		this.delegate = delegate;
		this.footprint = footprint;
	}

	public static void register() {
		Registry.register(BuiltInRegistries.STRUCTURE_TYPE, DiamondAltar.id("surface"), TYPE);
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		int cx = context.chunkPos().getMiddleBlockX();
		int cz = context.chunkPos().getMiddleBlockZ();
		int minY = context.heightAccessor().getMinY();
		int f = footprint;
		int[][] samples = {{0, 0}, {f, f}, {-f, f}, {f, -f}, {-f, -f}, {f, 0}, {-f, 0}, {0, f}, {0, -f}};
		int lowest = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;
		for (int[] s : samples) {
			int y = context.chunkGenerator().getFirstOccupiedHeight(cx + s[0], cz + s[1],
					Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
			if (y <= minY + 4) {
				return Optional.empty(); // du vide sous la structure
			}
			lowest = Math.min(lowest, y);
			highest = Math.max(highest, y);
		}
		if (highest - lowest > 14) {
			return Optional.empty(); // terrain trop accidenté
		}
		return delegate.findValidGenerationPoint(context);
	}

	@Override
	public StructureType<?> type() {
		return TYPE;
	}
}
