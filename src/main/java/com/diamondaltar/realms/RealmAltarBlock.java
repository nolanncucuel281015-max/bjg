package com.diamondaltar.realms;

import com.mojang.serialization.MapCodec; // @1.21.11
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

/** Autel d'un Sanctuaire des Royaumes : le boss du royaume s'éveille quand un joueur approche. */
public class RealmAltarBlock extends BaseEntityBlock {
	public static final MapCodec<RealmAltarBlock> CODEC = simpleCodec(RealmAltarBlock::new); // @1.21.11
	public static final IntegerProperty REALM = IntegerProperty.create("realm", 0, Realm.values().length - 1);

	public RealmAltarBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(REALM, 0));
	}

	// #if 1.21.11
	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}
	// #endif

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(REALM);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RealmAltarBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, RealmContent.REALM_ALTAR_ENTITY, RealmAltarBlockEntity::serverTick);
	}
}
