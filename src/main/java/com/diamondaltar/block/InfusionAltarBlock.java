package com.diamondaltar.block;

import com.diamondaltar.Compat;

import com.diamondaltar.ModComponents;
import com.mojang.serialization.MapCodec; // @1.21.11
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class InfusionAltarBlock extends BaseEntityBlock {
	public static final MapCodec<InfusionAltarBlock> CODEC = simpleCodec(InfusionAltarBlock::new); // @1.21.11

	private static final VoxelShape SHAPE = Shapes.or(
			box(0, 0, 0, 16, 6, 16),
			box(0.5, 6, 0.5, 5.5, 16, 5.5),
			box(10.5, 6, 0.5, 15.5, 16, 5.5),
			box(0.5, 6, 10.5, 5.5, 16, 15.5),
			box(10.5, 6, 10.5, 15.5, 16, 15.5));

	public InfusionAltarBlock(Properties properties) {
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

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new InfusionAltarBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return createTickerHelper(type, ModBlocks.INFUSION_ALTAR_ENTITY,
				level.isClientSide() ? InfusionAltarBlockEntity::clientTick : InfusionAltarBlockEntity::serverTick);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (level.getBlockEntity(pos) instanceof InfusionAltarBlockEntity altar
				&& altar.isEmpty() && ModComponents.canInfuse(stack)) {
			if (!level.isClientSide()) {
				altar.startInfusion(stack.consumeAndReturn(1, player));
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.getBlockEntity(pos) instanceof InfusionAltarBlockEntity altar && !altar.isEmpty()) {
			if (altar.isInfusing()) {
				return InteractionResult.CONSUME;
			}
			if (!level.isClientSide()) {
				ItemStack taken = altar.takeItem();
				if (!player.addItem(taken)) {
					Compat.give(player, taken);
				}
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}
}
