package com.diamondaltar.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/** Un effet animé qui se déroule sur plusieurs ticks côté serveur. */
public abstract class TimedEffect {
	protected final ServerLevel level;
	protected int age;
	private final List<Entity> visuals = new ArrayList<>();

	protected TimedEffect(ServerLevel level) {
		this.level = level;
	}

	/** Appelé à chaque tick. Renvoie {@code true} quand l'effet est terminé. */
	protected abstract boolean tick();

	/** Enregistre une entité visuelle (display) qui sera supprimée à la fin de l'effet. */
	protected <T extends Entity> T track(T entity) {
		visuals.add(entity);
		return entity;
	}

	final boolean step() {
		boolean done = tick();
		age++;
		return done;
	}

	final void cleanup() {
		for (Entity e : visuals) {
			e.discard();
		}
		visuals.clear();
	}
}
