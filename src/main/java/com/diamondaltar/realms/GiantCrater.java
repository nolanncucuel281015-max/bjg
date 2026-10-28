package com.diamondaltar.realms;

import com.diamondaltar.effect.TimedEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

/**
 * Le cratère géant des grandes puissances (5000 dégâts et plus) : une demi-sphère aplatie de rayon r
 * (jusqu'à 250 blocs) qui se creuse du centre vers les bords. Pour ne pas figer le jeu, on ne travaille
 * qu'environ {@link #MAX_NANOS} par tick : un énorme cratère se creuse en quelques secondes, le serveur reste fluide.
 */
public final class GiantCrater extends TimedEffect {
	/** Temps de travail maximal par tick (20 ms : le reste du tick reste au jeu). */
	private static final long MAX_NANOS = 20_000_000L;
	private static final int QUIET = 2 | 16;

	private final BlockPos center;
	private final int radius;
	/** Les colonnes (dx, dz) du cratère, rangées de la plus proche du centre à la plus lointaine. */
	private final long[] columns;
	private int next;
	private long removed;

	public GiantCrater(ServerLevel level, Vec3 center, int radius) {
		super(level);
		this.center = BlockPos.containing(center);
		this.radius = radius;
		int n = 0;
		long[] cols = new long[(2 * radius + 1) * (2 * radius + 1)];
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				long d2 = (long) dx * dx + (long) dz * dz;
				if (d2 <= (long) radius * radius) {
					// distance² en poids fort, puis dx et dz (décalés pour rester positifs) : un simple tri suffit.
					cols[n++] = (d2 << 32) | ((long) (dx + 32768) << 16) | (dz + 32768);
				}
			}
		}
		this.columns = Arrays.copyOf(cols, n);
		Arrays.sort(this.columns);
		level.playSound(null, this.center, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 8f, 0.3f);
	}

	public int radius() {
		return radius;
	}

	/** Le cratère est entièrement creusé. */
	public boolean done() {
		return next >= columns.length;
	}

	/** Nombre de blocs détruits jusqu'ici. */
	public long removed() {
		return removed;
	}

	@Override
	protected boolean tick() {
		long start = System.nanoTime();
		int work = 0;
		int lastDx = 0, lastDz = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		while (next < columns.length) {
			long c = columns[next++];
			int dx = (int) ((c >> 16) & 0xFFFF) - 32768;
			int dz = (int) (c & 0xFFFF) - 32768;
			lastDx = dx;
			lastDz = dz;
			double h = (double) radius * radius - (dx * dx + dz * dz);
			int down = (int) Math.sqrt(h / 1.6);
			int up = (int) (Math.sqrt(h) * 0.5) + 3;
			int x = center.getX() + dx, z = center.getZ() + dz;
			if (!level.isLoaded(p.set(x, center.getY(), z))) {
				continue;
			}
			int bottom = Math.max(level.getMinY() + 1, center.getY() - down);
			int top = Math.min(level.getMaxY(), center.getY() + up);
			for (int y = bottom; y <= top; y++) {
				p.set(x, y, z);
				if (!level.getBlockState(p).isAir() && SkyFx.breakable(level, p)) {
					level.setBlock(p, Blocks.AIR.defaultBlockState(), QUIET);
					removed++;
				}
			}
			work += top - bottom + 1;
			if (work > 1024) {
				work = 0;
				if (System.nanoTime() - start > MAX_NANOS) {
					break;
				}
			}
		}
		// Explosions et fumée sur le bord qui avance.
		double front = Math.sqrt(lastDx * lastDx + lastDz * lastDz);
		for (int i = 0; i < 6; i++) {
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			level.sendParticles(i % 2 == 0 ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.LARGE_SMOKE,
					center.getX() + Math.cos(a) * front, center.getY() + 1, center.getZ() + Math.sin(a) * front, 1, 1, 1, 1, 0);
		}
		if (age % 10 == 0) {
			level.playSound(null, center, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 6f, 0.4f + level.getRandom().nextFloat() * 0.3f);
		}
		return next >= columns.length;
	}

	/** Lance le cratère géant correspondant à la puissance choisie (rien en dessous de 5000 dégâts). */
	public static void start(ServerLevel level, Vec3 center, int damageLevel) {
		int r = SkyFx.blast(damageLevel);
		if (r > 0) {
			com.diamondaltar.effect.EffectManager.start(new GiantCrater(level, center, r));
		}
	}
}
