package com.diamondaltar.realms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Le voile d'un Portail des Royaumes : sa couleur et sa destination dépendent du royaume (0 à 6). */
public class RealmPortalBlock extends Block {
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	public static final IntegerProperty REALM = IntegerProperty.create("realm", 0, Realm.values().length - 1);
	private static final VoxelShape X_SHAPE = box(0, 0, 6, 16, 16, 10);
	private static final VoxelShape Z_SHAPE = box(6, 0, 0, 10, 16, 16);

	public RealmPortalBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(REALM, 0));
	}

	public static Realm realm(BlockState state) {
		return Realm.values()[state.getValue(REALM)];
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS, REALM);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(AXIS) == Direction.Axis.X ? X_SHAPE : Z_SHAPE;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
			BlockPos neighborPos, BlockState neighbor, RandomSource random) {
		Direction.Axis axis = state.getValue(AXIS);
		boolean inPlane = dir.getAxis() == Direction.Axis.Y || dir.getAxis() == axis;
		if (inPlane && !neighbor.is(this) && !neighbor.is(RealmContent.REALM_FRAME)) {
			return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
		}
		return state;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		Realm realm = realm(state);
		DustParticleOptions main = new DustParticleOptions(realm.color, 1.2f);
		DustParticleOptions accent = new DustParticleOptions(realm.accent, 0.9f);
		for (int i = 0; i < 2; i++) {
			double x = pos.getX() + random.nextDouble();
			double y = pos.getY() + random.nextDouble();
			double z = pos.getZ() + random.nextDouble();
			level.addParticle(i == 0 ? main : accent, x, y, z, 0, 0, 0);
		}
		if (random.nextInt(10) == 0) {
			level.addParticle(ParticleTypes.END_ROD, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0, 0.01, 0);
		}
	}
}
