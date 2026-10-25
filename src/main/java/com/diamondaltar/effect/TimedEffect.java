package com.diamondaltar.effect;

import net.minecraft.server.level.ServerLevel;

/** Un effet animé qui se déroule sur plusieurs ticks côté serveur. */
public abstract class TimedEffect {
	protected final ServerLevel level;
	protected int age;

	protected TimedEffect(ServerLevel level) {
		this.level = level;
	}

	/** Appelé à chaque tick. Renvoie {@code true} quand l'effet est terminé. */
	protected abstract boolean tick();

	final boolean step() {
		boolean done = tick();
		age++;
		return done;
	}
}
