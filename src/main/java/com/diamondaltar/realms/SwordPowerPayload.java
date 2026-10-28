package com.diamondaltar.realms;

import com.diamondaltar.DiamondAltar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Envoyé par le client quand le joueur appuie sur Y : puissance suivante de l'Épée des Sept Royaumes. */
public record SwordPowerPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SwordPowerPayload> ID = new CustomPacketPayload.Type<>(DiamondAltar.id("sword_power"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SwordPowerPayload> CODEC = StreamCodec.unit(new SwordPowerPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
