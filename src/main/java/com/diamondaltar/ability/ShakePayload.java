package com.diamondaltar.ability;

import com.diamondaltar.DiamondAltar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Envoyé aux joueurs proches d'une grosse explosion : fait trembler leur écran. */
public record ShakePayload(float strength, int ticks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ShakePayload> ID = new CustomPacketPayload.Type<>(DiamondAltar.id("shake"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ShakePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, ShakePayload::strength,
			ByteBufCodecs.VAR_INT, ShakePayload::ticks,
			ShakePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
