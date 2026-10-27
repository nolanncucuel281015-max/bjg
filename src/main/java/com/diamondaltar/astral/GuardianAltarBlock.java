package com.diamondaltar.astral;

import com.mojang.serialization.MapCodec; // @1.21.11
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Autel du Gardien : au cœur du Château Astral, il invoque le boss quand un joueur approche. */
public class GuardianAltarBlock extends BaseEntityBlock {
	public static final MapCodec<GuardianAltarBlock> CODEC = simpleCodec(GuardianAltarBlock::new); // @1.21.11

	public GuardianAltarBlock(Properties properties) {
		super(properties);
	}

	// #if 1.21.11
	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}
	// #endif

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new GuardianAltarBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, AstralContent.GUARDIAN_ALTAR_ENTITY, GuardianAltarBlockEntity::serverTick);
	}
}
