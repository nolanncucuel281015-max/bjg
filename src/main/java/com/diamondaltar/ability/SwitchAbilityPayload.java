package com.diamondaltar.ability;

import com.diamondaltar.DiamondAltar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Envoyé par le client quand le joueur appuie sur K pour changer de capacité. */
public record SwitchAbilityPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SwitchAbilityPayload> ID = new CustomPacketPayload.Type<>(DiamondAltar.id("switch_ability"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SwitchAbilityPayload> CODEC = StreamCodec.unit(new SwitchAbilityPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
