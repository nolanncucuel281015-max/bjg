package com.diamondaltar.realms;

import com.diamondaltar.DiamondAltar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Envoyé par le client pour régler les grandes attaques de l'Épée des Sept Royaumes :
 * touche Y = portée suivante ({@code damage} faux), touche U = dégâts suivants ({@code damage} vrai).
 */
public record SwordPowerPayload(boolean damage) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SwordPowerPayload> ID = new CustomPacketPayload.Type<>(DiamondAltar.id("sword_power"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SwordPowerPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, SwordPowerPayload::damage, SwordPowerPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
