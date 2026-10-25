package com.diamondaltar.ability;

import com.diamondaltar.DiamondAltar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Envoyé par le client quand le joueur fait clic gauche avec une épée infusée. */
public record SlashPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SlashPayload> ID = new CustomPacketPayload.Type<>(DiamondAltar.id("slash"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SlashPayload> CODEC = StreamCodec.unit(new SlashPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
