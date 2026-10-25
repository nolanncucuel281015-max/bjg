package com.diamondaltar.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/** Halo lumineux flou : grand, en pleine lumière, qui grossit un peu puis s'estompe. */
public class GlowOrbParticle extends SingleQuadParticle {
	private final float startSize;

	protected GlowOrbParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
			TextureAtlasSprite sprite, float r, float g, float b, RandomSource random) {
		super(level, x, y, z, 0, 0, 0, sprite);
		this.xd = vx;
		this.yd = vy;
		this.zd = vz;
		this.hasPhysics = false;
		this.gravity = 0;
		this.friction = 0.85f;
		this.lifetime = 7 + random.nextInt(6);
		this.startSize = 0.55f + random.nextFloat() * 0.6f;
		this.quadSize = startSize;
		setColor(r, g, b);
		setAlpha(0.9f);
	}

	@Override
	public void tick() {
		super.tick();
		float t = age / (float) lifetime;
		quadSize = startSize * (1.0f + t * 0.6f);
		setAlpha(0.9f * (1.0f - t) * (1.0f - t));
	}

	@Override
	protected SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}

	@Override
	public int getLightColor(float partialTick) {
		return 0xF000F0;
	}

	public static final class Provider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;
		private final float r;
		private final float g;
		private final float b;

		public Provider(SpriteSet sprites, int rgb) {
			this.sprites = sprites;
			this.r = ((rgb >> 16) & 0xFF) / 255f;
			this.g = ((rgb >> 8) & 0xFF) / 255f;
			this.b = (rgb & 0xFF) / 255f;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
				double vx, double vy, double vz, RandomSource random) {
			return new GlowOrbParticle(level, x, y, z, vx, vy, vz, sprites.get(random), r, g, b, random);
		}
	}
}
