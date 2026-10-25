package com.diamondaltar.effect;

import net.minecraft.server.world.ServerWorld;

/** Un effet animé qui se déroule sur plusieurs ticks côté serveur. */
public abstract class TimedEffect {
	protected final ServerWorld world;
	protected int age;

	protected TimedEffect(ServerWorld world) {
		this.world = world;
	}

	/** Appelé à chaque tick. Renvoie {@code true} quand l'effet est terminé. */
	protected abstract boolean tick();

	final boolean step() {
		boolean done = tick();
		age++;
		return done;
	}
}
