package com.diamondaltar.ability;

import com.diamondaltar.DiamondAltar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Touches de l'armure : J = changer de capacité ({@code activate = false}), H = lancer ({@code activate = true}). */
public record ArmorActionPayload(boolean activate) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ArmorActionPayload> ID = new CustomPacketPayload.Type<>(DiamondAltar.id("armor_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ArmorActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, ArmorActionPayload::activate, ArmorActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
