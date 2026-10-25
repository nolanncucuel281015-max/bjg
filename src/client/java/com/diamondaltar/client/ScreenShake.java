package com.diamondaltar.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.RandomSource;

/** Tremblement de caméra : on décale légèrement le regard chaque tick, puis on annule le décalage. */
final class ScreenShake {
	private static final RandomSource RANDOM = RandomSource.create();
	private static float strength;
	private static int remaining;
	private static int total;
	private static float lastYaw;
	private static float lastPitch;

	private ScreenShake() {
	}

	static void start(float s, int ticks) {
		if (s * ticks >= strength * remaining) {
			strength = Math.min(3.0f, s);
			remaining = ticks;
			total = Math.max(1, ticks);
		}
	}

	static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			remaining = 0;
			return;
		}
		// On enlève le décalage du tick précédent.
		player.setYRot(player.getYRot() - lastYaw);
		player.setXRot(player.getXRot() - lastPitch);
		lastYaw = 0;
		lastPitch = 0;
		if (remaining <= 0 || client.isPaused()) {
			return;
		}
		float fade = remaining / (float) total;
		float s = strength * fade;
		lastYaw = (RANDOM.nextFloat() - 0.5f) * 2f * s;
		lastPitch = (RANDOM.nextFloat() - 0.5f) * 2f * s;
		player.setYRot(player.getYRot() + lastYaw);
		player.setXRot(player.getXRot() + lastPitch);
		remaining--;
	}
}
