package com.diamondaltar.astral;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;

/** Seau d'Essence Galactique : versé dans un cadre de diamant, il ouvre le Portail Astral (comme un briquet). */
public class GalaxyEssenceItem extends Item {
	public GalaxyEssenceItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		if (!(ctx.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos inside = ctx.getClickedPos().relative(ctx.getClickedFace());
		if (!AstralPortals.tryIgnite(level, inside)) {
			return InteractionResult.FAIL;
		}
		level.playSound(null, inside, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 0.6f);
		level.playSound(null, inside, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.7f, 1.4f);
		Player player = ctx.getPlayer();
		if (player != null && !player.getAbilities().instabuild) {
			ItemStack stack = ctx.getItemInHand();
			stack.shrink(1);
			if (stack.isEmpty()) {
				player.setItemInHand(ctx.getHand(), new ItemStack(Items.BUCKET));
			} else {
				player.getInventory().add(new ItemStack(Items.BUCKET));
			}
		}
		return InteractionResult.SUCCESS;
	}
}
