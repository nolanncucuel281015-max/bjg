package com.diamondaltar.realms;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Clé d'un royaume : clic droit dans un cadre du portail de ce royaume pour l'ouvrir (elle n'est pas consommée). */
public class RealmKeyItem extends Item {
	private final Realm realm;

	public RealmKeyItem(Properties properties, Realm realm) {
		super(properties);
		this.realm = realm;
	}

	public Realm realm() {
		return realm;
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		if (!(ctx.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos inside = ctx.getClickedPos().relative(ctx.getClickedFace());
		if (!RealmPortals.tryIgnite(level, inside, realm)) {
			// Un cadre d'un autre royaume ? On dit quelle clé il faut.
			Realm other = RealmPortals.frameRealm(level.getBlockState(ctx.getClickedPos()));
			if (other != null && other != realm && ctx.getPlayer() != null) {
				com.diamondaltar.Compat.overlay(ctx.getPlayer(), net.minecraft.network.chat.Component.translatable("diamondaltar.key.wrong_frame",
						net.minecraft.network.chat.Component.translatable("item.diamondaltar." + other.id + "_key"))
						.withStyle(net.minecraft.ChatFormatting.RED));
			}
			return InteractionResult.FAIL;
		}
		level.playSound(null, inside, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.7f, 0.8f + realm.ordinal() * 0.1f);
		level.playSound(null, inside, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5f, 0.6f);
		return InteractionResult.SUCCESS;
	}
}
