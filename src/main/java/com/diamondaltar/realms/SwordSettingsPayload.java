package com.diamondaltar.realms;

import com.diamondaltar.DiamondAltar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Envoyé par l'interface de réglages (touche Y) : attaque choisie, niveau de portée et niveau de dégâts de l'épée. */
public record SwordSettingsPayload(int mode, int range, int damage) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SwordSettingsPayload> ID = new CustomPacketPayload.Type<>(DiamondAltar.id("sword_settings"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SwordSettingsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SwordSettingsPayload::mode,
			ByteBufCodecs.VAR_INT, SwordSettingsPayload::range,
			ByteBufCodecs.VAR_INT, SwordSettingsPayload::damage,
			SwordSettingsPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
