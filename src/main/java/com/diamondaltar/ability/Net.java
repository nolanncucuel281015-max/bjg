package com.diamondaltar.ability;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Effets d'écran envoyés au client du mod (tremblement, flash). */
public final class Net {
	private Net() {
	}

	public static void shake(ServerPlayer player, float strength, int ticks) {
		ServerPlayNetworking.send(player, new ShakePayload(strength, ticks));
	}

	public static void flash(ServerPlayer player, int color, float strength, int ticks) {
		ServerPlayNetworking.send(player, new FlashPayload(color, strength, ticks));
	}
}
