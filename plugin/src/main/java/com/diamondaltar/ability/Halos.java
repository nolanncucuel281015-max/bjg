package com.diamondaltar.ability;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;

/** Version plugin : pas de particule personnalisée, on utilise de grosses poussières lumineuses. */
final class Halos {
	static final ParticleOptions WHITE = new DustParticleOptions(0xFFFFFF, 4.0f);
	static final ParticleOptions ICE = new DustParticleOptions(0xA8EEFF, 3.5f);
	static final ParticleOptions NEBULA = new DustParticleOptions(0xB070FF, 3.5f);
	static final ParticleOptions PINK = new DustParticleOptions(0xFF70D8, 3.5f);
	static final ParticleOptions GOLD = new DustParticleOptions(0xFFD860, 3.5f);
	static final ParticleOptions FIRE = new DustParticleOptions(0xFF8A30, 3.5f);

	private Halos() {
	}
}
