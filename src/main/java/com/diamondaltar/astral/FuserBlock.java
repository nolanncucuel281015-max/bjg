package com.diamondaltar.astral;

import com.mojang.serialization.MapCodec; // @1.21.11
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Le Fusionneur : réservoir de lave à gauche, réservoir d'eau à droite (vus de face). On les
 * remplit avec des seaux, ou les tuyaux aspirent la lave / l'eau posées juste à côté. Quand les
 * deux sont pleins, il les mélange en Essence Galactique violette.
 */
public class FuserBlock extends BaseEntityBlock {
	public static final MapCodec<FuserBlock> CODEC = simpleCodec(FuserBlock::new); // @1.21.11
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

	public FuserBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	// #if 1.21.11
	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}
	// #endif

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FuserBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, AstralContent.FUSER_ENTITY, FuserBlockEntity::serverTick);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof FuserBlockEntity fuser)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return stack.is(Items.LAVA_BUCKET) || stack.is(Items.WATER_BUCKET) || stack.is(Items.BUCKET)
					? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (stack.is(Items.LAVA_BUCKET) && fuser.fillLava()) {
			swap(player, hand, stack, new ItemStack(Items.BUCKET));
			return InteractionResult.SUCCESS;
		}
		if (stack.is(Items.WATER_BUCKET) && fuser.fillWater()) {
			swap(player, hand, stack, new ItemStack(Items.BUCKET));
			return InteractionResult.SUCCESS;
		}
		if (stack.is(Items.BUCKET) && fuser.takeEssence()) {
			swap(player, hand, stack, new ItemStack(AstralContent.GALAXY_ESSENCE_BUCKET));
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FuserBlockEntity fuser) {
			fuser.describe(player);
		}
		return InteractionResult.SUCCESS;
	}

	private static void swap(Player player, InteractionHand hand, ItemStack stack, ItemStack result) {
		if (player.getAbilities().instabuild) {
			if (!player.getInventory().contains(result)) {
				player.getInventory().add(result);
			}
			return;
		}
		stack.shrink(1);
		if (stack.isEmpty()) {
			player.setItemInHand(hand, result);
		} else if (!player.getInventory().add(result)) {
			Compat.give(player, result);
		}
	}
}
