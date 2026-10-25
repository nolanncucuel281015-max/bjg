package com.diamondaltar.ability;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Effets d'écran envoyés au client du mod (tremblement, flash). */
final class Net {
	private Net() {
	}

	static void shake(ServerPlayer player, float strength, int ticks) {
		ServerPlayNetworking.send(player, new ShakePayload(strength, ticks));
	}

	static void flash(ServerPlayer player, int color, float strength, int ticks) {
		ServerPlayNetworking.send(player, new FlashPayload(color, strength, ticks));
	}
}
