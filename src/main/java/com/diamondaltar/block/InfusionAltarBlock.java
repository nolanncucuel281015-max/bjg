package com.diamondaltar.block;

import com.diamondaltar.ModComponents;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class InfusionAltarBlock extends BlockWithEntity {
	public static final MapCodec<InfusionAltarBlock> CODEC = createCodec(InfusionAltarBlock::new);

	private static final VoxelShape SHAPE = VoxelShapes.union(
			createCuboidShape(0, 0, 0, 16, 6, 16),
			createCuboidShape(0.5, 6, 0.5, 5.5, 16, 5.5),
			createCuboidShape(10.5, 6, 0.5, 15.5, 16, 5.5),
			createCuboidShape(0.5, 6, 10.5, 5.5, 16, 15.5),
			createCuboidShape(10.5, 6, 10.5, 15.5, 16, 15.5));

	public InfusionAltarBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new InfusionAltarBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return validateTicker(type, ModBlocks.INFUSION_ALTAR_ENTITY,
				world.isClient ? InfusionAltarBlockEntity::clientTick : InfusionAltarBlockEntity::serverTick);
	}

	@Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
			PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (!(world.getBlockEntity(pos) instanceof InfusionAltarBlockEntity altar)) {
			return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (altar.isEmpty() && ModComponents.canInfuse(stack)) {
			if (!world.isClient) {
				altar.startInfusion(stack.splitUnlessCreative(1, player), player);
			}
			return ItemActionResult.success(world.isClient);
		}
		return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.getBlockEntity(pos) instanceof InfusionAltarBlockEntity altar && !altar.isEmpty()) {
			if (altar.isInfusing()) {
				return ActionResult.CONSUME;
			}
			if (!world.isClient) {
				ItemStack taken = altar.takeItem();
				if (!player.giveItemStack(taken)) {
					player.dropItem(taken, false);
				}
			}
			return ActionResult.success(world.isClient);
		}
		return ActionResult.PASS;
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof InfusionAltarBlockEntity altar) {
			ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, altar.takeItem());
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}
}
