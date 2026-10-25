package com.diamondaltar;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/** Particules de lueur (halo flou en pleine lumière) pour imiter le « bloom » des bandes de lumière. */
public final class ModParticles {
	public static final SimpleParticleType GLOW_WHITE = register("glow_white");
	public static final SimpleParticleType GLOW_ICE = register("glow_ice");
	public static final SimpleParticleType GLOW_NEBULA = register("glow_nebula");
	public static final SimpleParticleType GLOW_PINK = register("glow_pink");
	public static final SimpleParticleType GLOW_GOLD = register("glow_gold");
	public static final SimpleParticleType GLOW_FIRE = register("glow_fire");

	private ModParticles() {
	}

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, DiamondAltar.id(name), FabricParticleTypes.simple(true));
	}

	public static void register() {
	}
}
