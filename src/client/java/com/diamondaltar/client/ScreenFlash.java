package com.diamondaltar.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/** Flash plein écran (surexposition lumineuse) qui s'estompe en quelques ticks. */
final class ScreenFlash {
	private static int color;
	private static float strength;
	private static int remaining;
	private static int total;

	private ScreenFlash() {
	}

	static void start(int rgb, float s, int ticks) {
		if (s >= strength * (remaining / (float) Math.max(1, total))) {
			color = rgb & 0xFFFFFF;
			strength = Math.min(0.85f, s);
			remaining = ticks;
			total = Math.max(1, ticks);
		}
	}

	static void tick() {
		if (remaining > 0) {
			remaining--;
		}
	}

	static void render(GuiGraphics graphics, DeltaTracker delta) {
		if (remaining <= 0) {
			return;
		}
		float t = (remaining - delta.getGameTimeDeltaPartialTick(false)) / total;
		int a = (int) (255 * strength * Math.max(0, t) * Math.max(0, t));
		if (a <= 0) {
			return;
		}
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (a << 24) | color);
	}
}
