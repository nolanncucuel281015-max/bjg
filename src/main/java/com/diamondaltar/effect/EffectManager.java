package com.diamondaltar.effect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class EffectManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("diamondaltar");
	private static final List<TimedEffect> ACTIVE = new ArrayList<>();
	private static final List<TimedEffect> PENDING = new ArrayList<>();

	private EffectManager() {
	}

	public static void start(TimedEffect effect) {
		PENDING.add(effect);
	}

	/** À appeler à chaque tick serveur. */
	public static void tickAll() {
		ACTIVE.addAll(PENDING);
		PENDING.clear();
		Iterator<TimedEffect> it = ACTIVE.iterator();
		while (it.hasNext()) {
			TimedEffect effect = it.next();
			boolean done;
			try {
				done = effect.step();
			} catch (RuntimeException e) {
				LOGGER.error("Erreur dans une capacité", e);
				done = true;
			}
			if (done) {
				effect.cleanup();
				it.remove();
			}
		}
	}

	/** À appeler quand le serveur s'arrête : supprime tous les objets visuels. */
	public static void stopAll() {
		ACTIVE.forEach(TimedEffect::cleanup);
		ACTIVE.clear();
		PENDING.clear();
	}
}
