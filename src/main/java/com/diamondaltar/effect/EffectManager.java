package com.diamondaltar.effect;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class EffectManager {
	private static final List<TimedEffect> ACTIVE = new ArrayList<>();
	private static final List<TimedEffect> PENDING = new ArrayList<>();

	private EffectManager() {
	}

	public static void start(TimedEffect effect) {
		PENDING.add(effect);
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ACTIVE.addAll(PENDING);
			PENDING.clear();
			Iterator<TimedEffect> it = ACTIVE.iterator();
			while (it.hasNext()) {
				if (it.next().step()) {
					it.remove();
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ACTIVE.clear();
			PENDING.clear();
		});
	}
}
