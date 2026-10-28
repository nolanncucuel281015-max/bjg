package com.diamondaltar.realms;

import com.diamondaltar.Compat;
import com.diamondaltar.ModParticles;
import com.diamondaltar.ability.Net;
import com.diamondaltar.effect.Visuals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Set;
import java.util.UUID;

/**
 * Outils des grandes attaques de l'Épée des Sept Royaumes : visée, réglages (portée, dégâts),
 * laser rond (cylindre de la matière {@link LaserBlock}), sphères (planètes, noyaux d'énergie) toujours
 * tournées vers la caméra, destruction du terrain et dégâts.
 */
public final class SkyFx {
	/** Portées réglables avec la touche Y (en blocs). */
	public static final int[] RANGES = {10, 25, 50, 75, 100, 150, 200, 300, 500, 750, 1000};
	/** Dégâts réglables avec la touche U. */
	public static final int[] DAMAGES = {10, 25, 50, 100, 200, 500, 1000};
	private static final int[] CRATER = {2, 4, 6, 8, 10, 13, 16, 18, 20, 22, 24};
	private static final float[] WIDTH = {1.5f, 2.5f, 3.5f, 4.5f, 5.5f, 7f, 9f, 10f, 11f, 12f, 13f};
	/** Blocs détruits sans prévenir les voisins (bien moins de calculs, donc moins de lag). */
	private static final int QUIET = 2 | 16;
	/** On ne vise jamais plus loin que ça (au-delà, les tronçons ne sont pas chargés de toute façon). */
	private static final int MAX_AIM = 320;

	private SkyFx() {
	}

	// ---------------------------------------------------------------- réglages

	public static int range(int level) {
		return RANGES[clamp(level) - 1];
	}

	public static float damage(int level) {
		return DAMAGES[clampDamage(level) - 1];
	}

	public static int clampDamage(int level) {
		return Math.max(1, Math.min(DAMAGES.length, level));
	}

	public static int crater(int level) {
		return CRATER[clamp(level) - 1];
	}

	public static float width(int level) {
		return WIDTH[clamp(level) - 1];
	}

	/** Le sol est labouré par les lasers à partir de la portée 75 (1 bloc), puis 150 (2 blocs). */
	public static int trench(int level) {
		return clamp(level) >= 6 ? 2 : clamp(level) >= 4 ? 1 : 0;
	}

	public static int clamp(int level) {
		return Math.max(1, Math.min(RANGES.length, level));
	}

	// ---------------------------------------------------------------- visée

	/**
	 * Le bloc visé, sinon le sol sous {@code fallback}. Le rayon s'arrête au premier tronçon non chargé :
	 * on ne force jamais le chargement (ni la génération) de terrain lointain, qui ferait laguer.
	 */
	public static Vec3 aim(ServerLevel level, ServerPlayer player, Vec3 fallback) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		double dist = 0;
		while (dist < MAX_AIM && level.isLoaded(BlockPos.containing(eye.add(look.scale(dist + 8))))) {
			dist += 8;
		}
		if (dist <= 0) {
			return groundBelow(level, fallback);
		}
		Vec3 end = eye.add(look.scale(dist));
		HitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.BLOCK) {
			return hit.getLocation();
		}
		return groundBelow(level, fallback);
	}

	public static Vec3 groundBelow(ServerLevel level, Vec3 pos) {
		int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(pos.x), (int) Math.floor(pos.z));
		return new Vec3(pos.x, gy > level.getMinY() && gy <= pos.y + 1 ? gy : pos.y, pos.z);
	}

	// ---------------------------------------------------------------- formes

	/** Un morceau de laser (cylindre), de la couleur d'un royaume (0..6) ou blanc (7). */
	public static Display.BlockDisplay laser(ServerLevel level, Vec3 pos, int color) {
		Display.BlockDisplay d = Visuals.block(level, pos, LaserBlock.of(color));
		d.setGlowingTag(true);
		d.setGlowColorOverride(color == LaserBlock.WHITE ? 0xFFFFFF : Realm.values()[color].color);
		return d;
	}

	/** Cylindre de diamètre w, de la position de l'entité jusqu'à {@code to} (n'importe quelle direction). */
	public static void beam(Display d, Vec3 from, Vec3 to, float w) {
		beam(d, from, to, w, 1);
	}

	/** Pareil, avec une animation fluide sur {@code ticks} ticks (moins de mises à jour = moins de lag). */
	public static void beam(Display d, Vec3 from, Vec3 to, float w, int ticks) {
		Vec3 dir = to.subtract(from);
		float len = (float) dir.length();
		if (len < 0.01f) {
			len = 0.01f;
			dir = new Vec3(0, 1, 0);
		}
		Vec3 n = dir.normalize();
		Quaternionf q = new Quaternionf().rotationTo(0, 1, 0, (float) n.x, (float) n.y, (float) n.z);
		float ww = Math.max(0.001f, w);
		Vector3f t = new Vector3f(-ww / 2, 0, -ww / 2).rotate(q);
		Visuals.transform(d, t, q, new Vector3f(ww, len, ww), ticks);
	}

	/** Une sphère (sprite rond toujours face à la caméra) : planète, noyau d'énergie… */
	public static Display.ItemDisplay sphere(ServerLevel level, Vec3 pos, Item item, float size) {
		Display.ItemDisplay d = Visuals.item(level, pos, new ItemStack(item));
		d.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		resize(d, size, 1);
		return d;
	}

	public static void resize(Display d, float size, int ticks) {
		Visuals.transform(d, new Vector3f(), new Quaternionf(), Math.max(0.001f, size), ticks);
	}

	/** Anneau rond de morceaux de laser (rayon r, épaisseur w) à plat autour du centre. */
	public static void ring(Display.BlockDisplay[] segments, Vec3 center, double r, double spin, float w) {
		ring(segments, center, r, spin, w, 1);
	}

	public static void ring(Display.BlockDisplay[] segments, Vec3 center, double r, double spin, float w, int ticks) {
		int n = segments.length;
		for (int i = 0; i < n; i++) {
			double a0 = spin + Math.PI * 2 * i / n;
			double a1 = spin + Math.PI * 2 * (i + 1) / n;
			Vec3 p0 = center.add(Math.cos(a0) * r, 0, Math.sin(a0) * r);
			Vec3 p1 = center.add(Math.cos(a1) * r, 0, Math.sin(a1) * r);
			Vec3 ext = p1.subtract(p0).normalize().scale(w * 0.5);
			segments[i].setPos(p0.subtract(ext));
			beam(segments[i], p0.subtract(ext), p1.add(ext), w, ticks);
		}
	}

	public static ParticleOptions glow(int color) {
		return switch (color) {
			case 0 -> ModParticles.GLOW_FIRE;
			case 1, 4 -> ModParticles.GLOW_ICE;
			case 2, 6 -> ModParticles.GLOW_GOLD;
			case 5 -> ModParticles.GLOW_NEBULA;
			case 3 -> ModParticles.GLOW_PINK;
			default -> ModParticles.GLOW_WHITE;
		};
	}

	public static void particleRing(ServerLevel level, ParticleOptions p, Vec3 c, double r, int n, double spin) {
		n = Math.min(n, 64);
		for (int i = 0; i < n; i++) {
			double a = spin + Math.PI * 2 * i / n;
			level.sendParticles(p, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0.2, 0.2, 0.2, 0);
		}
	}

	public static void shake(ServerLevel level, Vec3 at, double range, float strength, int ticks) {
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(at) < range * range) {
				Net.shake(p, strength, ticks);
			}
		}
	}

	public static void flash(ServerLevel level, Vec3 at, double range, int color, float strength, int ticks) {
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(at) < range * range) {
				Net.flash(p, color, strength, ticks);
			}
		}
	}

	// ---------------------------------------------------------------- destruction et dégâts

	/** Un bloc que l'attaque peut détruire : pas l'air, pas la bedrock ni les portails, pas un coffre ou autre bloc à contenu. */
	public static boolean breakable(ServerLevel level, BlockPos p) {
		BlockState st = level.getBlockState(p);
		return !st.isAir() && st.getDestroySpeed(level, p) >= 0 && level.getBlockEntity(p) == null;
	}

	/** Creuse un cratère (demi-sphère aplatie) de rayon r, en sautant ce qui l'a déjà été (rayon {@code done}). */
	public static void carve(ServerLevel level, Vec3 center, double r, double done) {
		BlockPos c = BlockPos.containing(center);
		int ir = (int) Math.ceil(r);
		for (int dx = -ir; dx <= ir; dx++) {
			for (int dy = -ir; dy <= 3; dy++) {
				for (int dz = -ir; dz <= ir; dz++) {
					double d = Math.sqrt(dx * dx + dy * dy * 1.6 + dz * dz);
					if (d > r || d <= done - 0.5) {
						continue;
					}
					BlockPos p = c.offset(dx, dy, dz);
					if (level.isLoaded(p) && breakable(level, p)) {
						level.setBlock(p, Blocks.AIR.defaultBlockState(), QUIET);
						if (level.getRandom().nextInt(30) == 0) {
							level.sendParticles(ParticleTypes.LAVA, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 1, 0, 0, 0, 0);
						}
					}
				}
			}
		}
	}

	/**
	 * Pulvérise les blocs de surface le long d'une ligne (les lasers qui balaient le sol). Pour ne pas laguer,
	 * on traite au plus ~120 colonnes par laser et par tick (un pas plus grand quand la ligne est très longue).
	 */
	public static void plough(ServerLevel level, Vec3 center, Vec3 dir, double from, double to, int depth) {
		double step = Math.max(1.0, (to - from) / 120.0);
		for (double k = from; k < to; k += step) {
			int x = (int) Math.floor(center.x + dir.x * k);
			int z = (int) Math.floor(center.z + dir.z * k);
			if (!level.isLoaded(new BlockPos(x, (int) center.y, z))) {
				continue;
			}
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
			if (Math.abs(top - center.y) > 20) {
				continue;
			}
			for (int d = -1; d < depth; d++) {
				BlockPos p = new BlockPos(x, top - d, z);
				if (breakable(level, p)) {
					level.setBlock(p, Blocks.AIR.defaultBlockState(), QUIET);
				}
			}
		}
	}

	/**
	 * Frappe toutes les créatures (pas les joueurs) à moins de {@code reach} blocs du centre, une seule fois chacune.
	 * @return le nombre de nouvelles créatures touchées
	 */
	public static int strike(ServerLevel level, ServerPlayer attacker, Vec3 center, double reach, float damage, Set<UUID> hit) {
		double h = Math.min(reach, 64) + 10;
		java.util.List<LivingEntity> victims = new java.util.ArrayList<>();
		if (reach <= 64) {
			victims.addAll(level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(center, reach * 2 + 2, h * 2, reach * 2 + 2), e -> true));
		} else {
			// Très grande portée : on parcourt les créatures chargées plutôt que toute la zone (bien plus rapide).
			for (var entity : level.getAllEntities()) {
				if (entity instanceof LivingEntity living) {
					victims.add(living);
				}
			}
		}
		int n = 0;
		for (LivingEntity e : victims) {
			if (!e.isAlive() || e instanceof Player || hit.contains(e.getUUID()) || Math.abs(e.getY() - center.y) > h
					|| e.distanceToSqr(center.x, e.getY(), center.z) > reach * reach) {
				continue;
			}
			hit.add(e.getUUID());
			n++;
			e.hurtServer(level, level.damageSources().playerAttack(attacker), damage);
			e.igniteForSeconds(8);
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 3));
			Vec3 out = e.position().subtract(center).multiply(1, 0, 1);
			out = out.lengthSqr() > 0.01 ? out.normalize().scale(2.0) : Vec3.ZERO;
			e.push(out.x, 1.0, out.z);
			Compat.markHurt(e);
			level.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY(0.5), e.getZ(), 2, 0.4, 0.4, 0.4, 0);
		}
		return n;
	}
}
