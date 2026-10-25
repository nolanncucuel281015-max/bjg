package com.diamondaltar.ability;

import com.diamondaltar.DiamondAltar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Flash d'écran : couleur RGB, intensité (0..1), durée en ticks. */
public record FlashPayload(int color, float strength, int ticks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<FlashPayload> ID = new CustomPacketPayload.Type<>(DiamondAltar.id("flash"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FlashPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, FlashPayload::color,
			ByteBufCodecs.FLOAT, FlashPayload::strength,
			ByteBufCodecs.VAR_INT, FlashPayload::ticks,
			FlashPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
