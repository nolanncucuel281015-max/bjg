package com.diamondaltar.realms;

import com.diamondaltar.Compat;
import com.diamondaltar.ModParticles;
import com.diamondaltar.ability.Net;
import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Épée des Sept Royaumes — « Anéantissement Céleste », puissance 1 à 5 (touche Y) :
 * <ol>
 * <li><b>Envol</b> : une colonne de lumière soulève le joueur dans une spirale aux 7 couleurs ;</li>
 * <li><b>Charge</b> : un noyau d'énergie grossit dans sa main, 7 éclats (un par royaume) tournent autour et s'y
 * fondent, la lumière de toute la zone converge vers lui, une cible se resserre au sol, l'écran tremble ;</li>
 * <li><b>Tir</b> : un rayon gigantesque (du ciel jusqu'au sol, à travers le joueur) frappe le sol et creuse un
 * cratère ; une onde de choc en anneau et 7 lasers tournants balaient tout jusqu'au rayon de la puissance
 * choisie, et à puissance 4–5 les lasers labourent le sol sur leur passage ;</li>
 * <li><b>Fin</b> : tout s'éteint, le joueur redescend en douceur.</li>
 * </ol>
 * Les rayons sont faits avec la matière {@link LaserBlock} (texture d'énergie animée du mod), jamais posée dans le monde.
 * Les joueurs ne sont jamais touchés ; les coffres et autres blocs à contenu ne sont jamais détruits.
 */
public final class CelestialAnnihilation extends TimedEffect {
	private static final double[] RADIUS = {12, 22, 32, 42, 50};
	private static final float[] DAMAGE = {25, 45, 70, 100, 150};
	private static final int[] CRATER = {0, 3, 5, 8, 12};
	private static final int[] TRENCH = {0, 0, 0, 1, 2};
	private static final float[] BEAM = {2, 3, 4, 5.5f, 7};
	private static final double[] HEIGHT = {8, 10, 12, 14, 18};
	private static final int RISE = 20;
	private static final int CHARGE = 40;
	private static final int FIRE = 40;
	private static final int RING_SEGMENTS = 32;
	private static final int TARGET_SEGMENTS = 16;

	private final ServerPlayer player;
	private final int power;
	private final double radius;
	private final Vec3 start;
	private final Vec3 ground;
	private final Set<UUID> hit = new HashSet<>();
	private Display.BlockDisplay lift;
	private Display.BlockDisplay orb;
	private final Display.BlockDisplay[] shards = new Display.BlockDisplay[7];
	private final Display.BlockDisplay[] target = new Display.BlockDisplay[TARGET_SEGMENTS];
	private final Display.BlockDisplay[] beam = new Display.BlockDisplay[3];
	private final Display.BlockDisplay[] ring = new Display.BlockDisplay[RING_SEGMENTS];
	private final Display.BlockDisplay[] lasers = new Display.BlockDisplay[7];
	private double carved;

	public CelestialAnnihilation(ServerLevel level, ServerPlayer player, int power) {
		super(level);
		this.player = player;
		this.power = Math.max(1, Math.min(5, power));
		this.radius = RADIUS[this.power - 1];
		this.start = player.position();
		int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.getBlockX(), player.getBlockZ());
		this.ground = new Vec3(player.getX(), gy > level.getMinY() && gy <= player.getY() + 1 ? gy : player.getY(), player.getZ());
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, RISE + CHARGE + FIRE + 20, 4, false, false));
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3f, 0.5f);
		level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.5f, 1.6f - power * 0.15f);
	}

	/** Recharge selon la puissance : de 15 s (puissance 1) à 35 s (puissance 5). */
	public static int cooldown(int power) {
		return 200 + power * 100;
	}

	/** Pour l'auto-test : combien de créatures ont été touchées. */
	public int hitCount() {
		return hit.size();
	}

	private double hoverY() {
		return start.y + HEIGHT[power - 1];
	}

	/** Garde le joueur en l'air à cette hauteur (sans toucher à son regard). */
	private void hold(double y) {
		player.teleportTo(level, start.x, y, start.z, Set.of(Relative.X_ROT, Relative.Y_ROT), 0, 0, false);
		player.setDeltaMovement(Vec3.ZERO);
		player.fallDistance = 0;
	}

	@Override
	protected boolean tick() {
		if (!player.isAlive() || player.level() != level) {
			return true;
		}
		if (age < RISE) {
			rise(age);
		} else if (age < RISE + CHARGE) {
			charge(age - RISE);
		} else if (age < RISE + CHARGE + FIRE) {
			fire(age - RISE - CHARGE);
		} else {
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, false, false));
			player.fallDistance = 0;
			return true;
		}
		return false;
	}

	// ================================================================ outils d'affichage

	private Display.BlockDisplay laser(Vec3 pos, int color) {
		Display.BlockDisplay d = track(Visuals.block(level, pos, LaserBlock.of(color)));
		d.setGlowingTag(true);
		d.setGlowColorOverride(color == LaserBlock.WHITE ? 0xFFFFFF : Realm.values()[color].color);
		return d;
	}

	/** Colonne verticale de largeur w et de hauteur h, posée sur la position de l'entité. */
	private static void column(Display d, float w, float h) {
		Visuals.transform(d, new Vector3f(-w / 2, 0, -w / 2), new Quaternionf(), new Vector3f(Math.max(0.001f, w), Math.max(0.001f, h), Math.max(0.001f, w)), 1);
	}

	/** Barre horizontale qui part de la position de l'entité dans la direction dir (longueur, épaisseur). */
	private static void bar(Display d, Vec3 dir, float length, float w) {
		Quaternionf q = new Quaternionf().rotateY((float) Math.atan2(-dir.z, dir.x));
		Vector3f t = new Vector3f(0, -w / 2, -w / 2).rotate(q);
		Visuals.transform(d, t, q, new Vector3f(Math.max(0.001f, length), Math.max(0.001f, w), Math.max(0.001f, w)), 1);
	}

	/** Cube centré qui tourne sur lui-même. */
	private static void spin(Display d, float size, float angle) {
		Quaternionf q = new Quaternionf().rotateY(angle).rotateX(angle * 0.7f).rotateZ(angle * 0.4f);
		Visuals.transform(d, Visuals.centered(q, size), q, size, 1);
	}

	private static ParticleOptions glow(int color) {
		return switch (color) {
			case 0 -> ModParticles.GLOW_FIRE;
			case 1, 4 -> ModParticles.GLOW_ICE;
			case 2, 6 -> ModParticles.GLOW_GOLD;
			case 5 -> ModParticles.GLOW_NEBULA;
			case 3 -> ModParticles.GLOW_PINK;
			default -> ModParticles.GLOW_WHITE;
		};
	}

	private void shake(float strength, int ticks, double range) {
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(ground) < range * range) {
				Net.shake(p, strength, ticks);
			}
		}
	}

	// ================================================================ 1. l'envol

	private void rise(int t) {
		double eased = 1 - Math.pow(1 - (t + 1) / (double) RISE, 3);
		hold(start.y + (hoverY() - start.y) * eased);
		if (lift == null) {
			lift = laser(ground, LaserBlock.WHITE);
		}
		float w = 1.2f * (1 - t / (float) RISE) + 0.2f;
		column(lift, w, (float) (player.getY() - ground.y + 1));
		Vec3 p = player.position();
		for (int i = 0; i < 7; i++) {
			double a = t * 0.7 + i * Math.PI * 2 / 7;
			double r = 2.2 - t * 0.05;
			level.sendParticles(glow(i), p.x + Math.cos(a) * r, p.y + 0.3, p.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
		}
		level.sendParticles(ModParticles.GLOW_WHITE, ground.x, ground.y + 0.2, ground.z, 4, 0.8, 0.1, 0.8, 0.05);
		if (t % 4 == 0) {
			level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2f, 0.5f + t * 0.07f);
		}
	}

	// ================================================================ 2. la charge

	private void charge(int t) {
		hold(hoverY());
		float progress = t / (float) CHARGE;
		if (lift != null) {
			lift.discard();
			lift = null;
		}
		Vec3 hand = player.position().add(0, 1.3, 0).add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(1.2));
		if (orb == null) {
			orb = laser(hand, LaserBlock.WHITE);
			for (int i = 0; i < 7; i++) {
				shards[i] = laser(hand, i);
			}
			for (int i = 0; i < TARGET_SEGMENTS; i++) {
				target[i] = laser(ground, i % 7);
			}
			level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 3f, 0.5f);
		}
		// Le noyau grossit et tourne de plus en plus vite.
		orb.setPos(hand);
		spin(orb, 0.3f + progress * (0.8f + power * 0.35f), t * (0.2f + progress * 0.6f));
		// Les 7 éclats tournent autour du noyau puis s'y fondent.
		for (int i = 0; i < 7; i++) {
			double a = t * (0.25 + progress * 0.5) + i * Math.PI * 2 / 7;
			double r = 4.0 * (1 - progress) + 0.6;
			Vec3 pos = hand.add(Math.cos(a) * r, Math.sin(a * 1.7) * r * 0.4, Math.sin(a) * r);
			shards[i].setPos(pos);
			spin(shards[i], 0.45f * (1 - progress * 0.6f), t * 0.5f + i);
			level.sendParticles(glow(i), pos.x, pos.y, pos.z, 1, 0.05, 0.05, 0.05, 0);
		}
		// La cible au sol se resserre.
		double tr = radius * (1 - progress) + BEAM[power - 1];
		placeRing(target, tr, t * 0.05, 0.35f, ground.add(0, 0.15, 0));
		// Toute la lumière de la zone converge vers le noyau.
		for (int k = 0; k < 10 + power * 3; k++) {
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			double d = radius * (0.3 + level.getRandom().nextDouble() * 0.7);
			Vec3 from = new Vec3(ground.x + Math.cos(a) * d, ground.y + 0.5 + level.getRandom().nextDouble() * 6, ground.z + Math.sin(a) * d);
			Vec3 dir = hand.subtract(from).normalize();
			level.sendParticles(glow(k % 8), from.x, from.y, from.z, 0, dir.x, dir.y, dir.z, 1.4);
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y, hand.z, 4 + power, 0.6, 0.6, 0.6, 0.3);
		if (t % 8 == 0) {
			level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 2.5f, 0.5f + progress);
			shake(0.15f + progress * 0.5f, 10, radius + 30);
		}
		if (t == CHARGE - 6) {
			level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 3f, 1.6f);
		}
	}

	/** Place des segments en anneau (rayon r, rotation a) à plat autour du centre. */
	private void placeRing(Display.BlockDisplay[] segments, double r, double a, float w, Vec3 center) {
		int n = segments.length;
		float len = (float) (Math.PI * 2 * r / n * 1.08);
		for (int i = 0; i < n; i++) {
			double ang = a + Math.PI * 2 * i / n;
			Vec3 p = center.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
			Vec3 tangent = new Vec3(-Math.sin(ang), 0, Math.cos(ang));
			segments[i].setPos(p.subtract(tangent.scale(len / 2)));
			bar(segments[i], tangent, len, w);
		}
	}

	// ================================================================ 3. le tir

	private void fire(int t) {
		hold(hoverY());
		float fade = t > FIRE - 10 ? (FIRE - t) / 10f : 1f;
		float width = BEAM[power - 1];
		if (t == 0) {
			impact();
		}
		// Le rayon : trois couches (halo coloré qui change de royaume, couche blanche, cœur éblouissant).
		float h = (float) (hoverY() - ground.y + 70);
		float pulse = (float) (1 + Math.sin(t * 1.3) * 0.12);
		beam[0].setBlockState(LaserBlock.of((t / 3) % 7));
		beam[0].setGlowColorOverride(Realm.values()[(t / 3) % 7].color);
		column(beam[0], width * pulse * fade, h);
		column(beam[1], width * 0.62f * fade, h);
		column(beam[2], width * 0.3f * pulse * fade, h);
		// Le cratère se creuse sous l'impact.
		int crater = CRATER[power - 1];
		if (crater > 0 && t < 12) {
			carveCrater(crater * (t + 1) / 12.0);
		}
		// L'onde de choc en anneau et les 7 lasers tournants.
		double reach = Math.min(radius, (t + 1) * radius / 24.0);
		placeRing(ring, reach, t * 0.03, (1.0f + power * 0.25f) * fade, ground.add(0, 0.6, 0));
		for (int i = 0; i < 7; i++) {
			double a = t * 0.11 + i * Math.PI * 2 / 7;
			Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
			lasers[i].setPos(ground.add(0, 0.8, 0));
			bar(lasers[i], dir, (float) reach, (0.9f + power * 0.25f) * fade);
			for (int k = 0; k < 4; k++) {
				Vec3 p = ground.add(0, 1, 0).add(dir.scale(level.getRandom().nextDouble() * reach));
				level.sendParticles(glow(i), p.x, p.y, p.z, 1, 0.3, 0.3, 0.3, 0.02);
			}
			if (TRENCH[power - 1] > 0 && fade > 0.5f) {
				plough(dir, reach, TRENCH[power - 1]);
			}
		}
		// Explosions le long de l'onde.
		if (reach < radius || t % 3 == 0) {
			int n = Math.max(6, (int) (reach * 0.4));
			for (int i = 0; i < n; i++) {
				double a = Math.PI * 2 * i / n + t;
				level.sendParticles(i % 3 == 0 ? ParticleTypes.EXPLOSION : ModParticles.GLOW_WHITE,
						ground.x + Math.cos(a) * reach, ground.y + 1, ground.z + Math.sin(a) * reach, 1, 0.3, 0.3, 0.3, 0);
			}
		}
		for (int y = 0; y < h; y += 3) {
			level.sendParticles(ModParticles.GLOW_WHITE, ground.x, ground.y + y, ground.z, 1, width * 0.4, 1, width * 0.4, 0.05);
		}
		if (t % 5 == 0) {
			level.playSound(null, BlockPos.containing(ground), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 4f, 0.4f + t * 0.02f);
			level.playSound(null, BlockPos.containing(ground), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 4f, 0.5f);
		}
		// Tout ce qui est dans le cercle (et n'est pas un joueur) est anéanti.
		float damage = DAMAGE[power - 1];
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(ground, reach * 2 + 2, 120, reach * 2 + 2),
				e -> e.isAlive() && !(e instanceof Player) && e.distanceToSqr(ground.x, e.getY(), ground.z) <= reach * reach
						&& !hit.contains(e.getUUID()))) {
			hit.add(e.getUUID());
			e.hurtServer(level, level.damageSources().playerAttack(player), damage);
			e.igniteForSeconds(8);
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 3));
			Vec3 out = e.position().subtract(ground).multiply(1, 0, 1);
			out = out.lengthSqr() > 0.01 ? out.normalize().scale(1.2 + power * 0.4) : Vec3.ZERO;
			e.push(out.x, 0.8 + power * 0.15, out.z);
			Compat.markHurt(e);
			level.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY(0.5), e.getZ(), 2, 0.4, 0.4, 0.4, 0);
		}
	}

	/** Le moment de l'impact : le noyau part, le rayon tombe, flash et tremblement pour tout le monde autour. */
	private void impact() {
		if (orb != null) {
			orb.discard();
			orb = null;
		}
		for (int i = 0; i < 7; i++) {
			if (shards[i] != null) {
				shards[i].discard();
				shards[i] = null;
			}
		}
		for (int i = 0; i < TARGET_SEGMENTS; i++) {
			if (target[i] != null) {
				target[i].discard();
				target[i] = null;
			}
		}
		beam[0] = laser(ground, 0);
		beam[1] = laser(ground, LaserBlock.WHITE);
		beam[2] = laser(ground, LaserBlock.WHITE);
		for (int i = 0; i < RING_SEGMENTS; i++) {
			ring[i] = laser(ground, i % 2 == 0 ? LaserBlock.WHITE : 1);
		}
		for (int i = 0; i < 7; i++) {
			lasers[i] = laser(ground, i);
		}
		BlockPos at = BlockPos.containing(ground);
		level.playSound(null, at, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 5f, 0.4f);
		level.playSound(null, at, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 5f, 0.5f);
		level.playSound(null, at, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 5f, 0.4f);
		level.playSound(null, at, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 3f, 0.5f);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, ground.x, ground.y + 1, ground.z, 4 + power * 2, 2 + power, 1, 2 + power, 0);
		level.sendParticles(ParticleTypes.SONIC_BOOM, ground.x, ground.y + 2, ground.z, 6, 3, 1, 3, 0);
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(ground) < (radius + 50) * (radius + 50)) {
				Net.flash(p, 0xFFFFFF, 0.6f + power * 0.08f, 10 + power * 3);
				Net.shake(p, 0.6f + power * 0.3f, 20 + power * 4);
			}
		}
	}

	// ================================================================ destruction

	/** Un bloc que le laser peut détruire : pas l'air, pas la bedrock ni les portails, pas un coffre ou autre bloc à contenu. */
	private boolean breakable(BlockPos p) {
		BlockState st = level.getBlockState(p);
		return !st.isAir() && st.getDestroySpeed(level, p) >= 0 && level.getBlockEntity(p) == null;
	}

	/** Creuse le cratère (une demi-sphère sous l'impact) jusqu'au rayon r. */
	private void carveCrater(double r) {
		BlockPos c = BlockPos.containing(ground);
		int ir = (int) Math.ceil(r);
		for (int dx = -ir; dx <= ir; dx++) {
			for (int dy = -ir; dy <= 2; dy++) {
				for (int dz = -ir; dz <= ir; dz++) {
					double d = Math.sqrt(dx * dx + dy * dy * 1.6 + dz * dz);
					if (d > r || d <= carved - 0.5) {
						continue;
					}
					BlockPos p = c.offset(dx, dy, dz);
					if (level.isLoaded(p) && breakable(p)) {
						level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
						if (level.getRandom().nextInt(25) == 0) {
							level.sendParticles(ParticleTypes.LAVA, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 1, 0, 0, 0, 0);
						}
					}
				}
			}
		}
		carved = r;
	}

	/** Les lasers labourent le sol : les blocs de surface sur leur trajet sont pulvérisés. */
	private void plough(Vec3 dir, double reach, int depth) {
		for (double k = CRATER[power - 1]; k < reach; k += 1.0) {
			int x = (int) Math.floor(ground.x + dir.x * k);
			int z = (int) Math.floor(ground.z + dir.z * k);
			BlockPos column = new BlockPos(x, (int) ground.y, z);
			if (!level.isLoaded(column)) {
				continue;
			}
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
			if (Math.abs(top - ground.y) > 14) {
				continue;
			}
			for (int d = 0; d < depth; d++) {
				BlockPos p = new BlockPos(x, top - d, z);
				if (breakable(p)) {
					level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
				}
			}
			// Les feuilles et l'herbe au-dessus partent aussi.
			BlockPos above = new BlockPos(x, top + 1, z);
			if (breakable(above)) {
				level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}
}
