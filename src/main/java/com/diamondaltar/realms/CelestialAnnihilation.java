package com.diamondaltar.realms;

import com.diamondaltar.Compat;
import com.diamondaltar.ability.Net;
import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Épée des Sept Royaumes — « Anéantissement Céleste » :
 * <ol>
 * <li>le joueur s'élève de 15 blocs dans une spirale aux 7 couleurs ;</li>
 * <li>il charge une sphère d'énergie pendant que la lumière des 50 blocs autour converge vers lui ;</li>
 * <li>un rayon géant s'abat sur le sol, puis 7 lasers (un par royaume) balaient tout sur 50 blocs
 * pendant qu'une onde de destruction s'étend : chaque créature touchée prend 80 dégâts ;</li>
 * <li>le joueur redescend en douceur.</li>
 * </ol>
 * Les joueurs ne sont jamais touchés, et aucun bloc n'est cassé.
 */
public final class CelestialAnnihilation extends TimedEffect {
	public static final double RADIUS = 50;
	private static final float DAMAGE = 80f;
	private static final int RISE = 20;
	private static final int CHARGE = 45;
	private static final int FIRE = 30;
	private static final double HEIGHT = 15;

	private final ServerPlayer player;
	private final Vec3 start;
	private final Vec3 ground;
	private final Set<UUID> hit = new HashSet<>();
	private Display.ItemDisplay orb;
	private Display.BlockDisplay beam;
	private Display.BlockDisplay core;
	private final Display.BlockDisplay[] lasers = new Display.BlockDisplay[7];

	public CelestialAnnihilation(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.start = player.position();
		int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ());
		this.ground = new Vec3(player.getX(), Math.min(player.getY(), gy > level.getMinY() ? gy : player.getY()), player.getZ());
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, RISE + CHARGE + FIRE + 20, 4, false, false));
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3f, 0.5f);
		level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.5f, 1.4f);
	}

	private Vec3 top() {
		return start.add(0, HEIGHT, 0);
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
			rise();
		} else if (age < RISE + CHARGE) {
			charge(age - RISE);
		} else if (age < RISE + CHARGE + FIRE) {
			fire(age - RISE - CHARGE);
		} else {
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 140, 0, false, false));
			player.fallDistance = 0;
			return true;
		}
		return false;
	}

	// ---------------------------------------------------------------- 1. l'ascension

	private void rise() {
		double t = (age + 1) / (double) RISE;
		double eased = 1 - Math.pow(1 - t, 3);
		hold(start.y + HEIGHT * eased);
		Vec3 p = player.position();
		for (Realm r : Realm.values()) {
			double a = age * 0.6 + r.ordinal() * Math.PI * 2 / 7;
			double rad = 1.6;
			level.sendParticles(new DustParticleOptions(r.color, 1.8f), p.x + Math.cos(a) * rad, p.y + 0.2, p.z + Math.sin(a) * rad,
					2, 0.05, 0.4, 0.05, 0);
		}
		level.sendParticles(ParticleTypes.END_ROD, p.x, p.y - 0.5, p.z, 6, 0.3, 0.6, 0.3, 0.02);
		if (age % 5 == 0) {
			level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2f, 0.5f + age * 0.06f);
		}
	}

	// ---------------------------------------------------------------- 2. la charge

	private void charge(int t) {
		hold(top().y);
		Vec3 hand = player.position().add(0, 1.2, 0).add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(0.8));
		if (orb == null) {
			orb = track(Visuals.item(level, hand, new ItemStack(Items.NETHER_STAR)));
			orb.setGlowingTag(true);
			orb.setGlowColorOverride(0xFFFFFF);
			level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 3f, 0.6f);
		}
		orb.setPos(hand);
		float size = 0.5f + t * 0.06f;
		Visuals.transform(orb, new Vector3f(), new Quaternionf().rotateY(t * 0.5f).rotateX(t * 0.3f), size, 1);
		// La lumière des 50 blocs autour converge vers l'orbe.
		for (int k = 0; k < 14; k++) {
			Realm r = Realm.values()[k % 7];
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			double d = RADIUS * (1 - t / (double) CHARGE) + 2;
			Vec3 from = new Vec3(ground.x + Math.cos(a) * d, ground.y + 0.5 + level.getRandom().nextDouble() * 4, ground.z + Math.sin(a) * d);
			Vec3 dir = hand.subtract(from).normalize();
			level.sendParticles(new DustParticleOptions(r.color, 2.2f), from.x, from.y, from.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, 0, dir.x, dir.y, dir.z, 1.6);
		}
		// Anneaux d'avertissement au sol, de plus en plus serrés.
		if (t % 3 == 0) {
			double r = RADIUS * (1 - t / (double) CHARGE);
			ring(new DustParticleOptions(Realm.values()[(t / 3) % 7].color, 2.5f), ground.add(0, 0.3, 0), Math.max(1, r), 120);
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y, hand.z, 6, size * 0.3, size * 0.3, size * 0.3, 0.2);
		if (t % 8 == 0) {
			level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 2f, 0.5f + t * 0.03f);
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(player) < 80 * 80) {
					Net.shake(p, 0.2f + t * 0.01f, 8);
				}
			}
		}
	}

	// ---------------------------------------------------------------- 3. le rayon et les lasers

	private void fire(int t) {
		hold(top().y);
		double beamHeight = top().y - ground.y + 1;
		if (t == 0) {
			if (orb != null) {
				orb.discard();
				orb = null;
			}
			beam = track(Visuals.block(level, top(), Compat.WHITE_STAINED_GLASS.defaultBlockState()));
			beam.setGlowingTag(true);
			beam.setGlowColorOverride(0xFFFFFF);
			core = track(Visuals.block(level, top(), Compat.LIGHT_BLUE_STAINED_GLASS.defaultBlockState()));
			core.setGlowingTag(true);
			core.setGlowColorOverride(0x9BE7FF);
			for (int i = 0; i < 7; i++) {
				lasers[i] = track(Visuals.ribbon(level, ground, Compat.WHITE_STAINED_GLASS.defaultBlockState(), Realm.values()[i].color));
			}
			level.playSound(null, BlockPos.containing(ground), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 4f, 0.5f);
			level.playSound(null, BlockPos.containing(ground), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 4f, 0.6f);
			level.playSound(null, BlockPos.containing(ground), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 4f, 0.5f);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, ground.x, ground.y + 1, ground.z, 6, 3, 1, 3, 0);
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(player) < 96 * 96) {
					Net.flash(p, 0xFFFFFF, 0.85f, 14);
					Net.shake(p, 1.4f, 20);
				}
			}
		}
		// Le rayon central pulse.
		float width = (float) (3.2 + Math.sin(t * 0.9) * 0.6) * (t > FIRE - 6 ? (FIRE - t) / 6f : 1f);
		Visuals.transform(beam, new Vector3f(-width / 2, (float) -beamHeight, -width / 2), new Quaternionf(),
				new Vector3f(width, (float) beamHeight, width), 1);
		float cw = width * 0.45f;
		Visuals.transform(core, new Vector3f(-cw / 2, (float) -beamHeight, -cw / 2), new Quaternionf(),
				new Vector3f(cw, (float) beamHeight, cw), 1);
		for (int y = 0; y < beamHeight; y += 2) {
			level.sendParticles(ParticleTypes.END_ROD, ground.x, ground.y + y, ground.z, 3, width * 0.3, 0.5, width * 0.3, 0.05);
		}
		// L'onde de destruction s'étend jusqu'à 50 blocs.
		double reach = Math.min(RADIUS, (t + 1) * RADIUS / (FIRE - 6));
		// 7 lasers qui tournent autour du point d'impact.
		for (int i = 0; i < 7; i++) {
			double a = t * 0.35 + i * Math.PI * 2 / 7;
			Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
			Vec3 mid = ground.add(0, 0.6, 0).add(dir.scale(reach / 2));
			lasers[i].setPos(mid);
			Visuals.ribbonShape(lasers[i], dir, new Vec3(0, 1, 0), (float) reach, 1.4f, 1);
			for (int k = 0; k < 6; k++) {
				Vec3 p = ground.add(0, 0.8, 0).add(dir.scale(level.getRandom().nextDouble() * reach));
				level.sendParticles(new DustParticleOptions(Realm.values()[i].color, 2.5f), p.x, p.y, p.z, 1, 0.2, 0.2, 0.2, 0);
			}
		}
		ring(ParticleTypes.EXPLOSION, ground.add(0, 1, 0), reach, Math.max(8, (int) (reach * 0.6)));
		ring(ParticleTypes.FLAME, ground.add(0, 0.5, 0), reach, Math.max(12, (int) (reach * 2)));
		if (t % 4 == 0) {
			level.playSound(null, BlockPos.containing(ground), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 4f, 0.5f + t * 0.02f);
		}
		// Tout ce qui est dans le cercle (et n'est pas un joueur) est anéanti.
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(ground, reach * 2 + 2, 80, reach * 2 + 2),
				e -> e.isAlive() && !(e instanceof Player) && e.distanceToSqr(ground.x, e.getY(), ground.z) <= reach * reach
						&& !hit.contains(e.getUUID()))) {
			hit.add(e.getUUID());
			e.hurtServer(level, level.damageSources().playerAttack(player), DAMAGE);
			e.igniteForSeconds(8);
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 3));
			Vec3 out = e.position().subtract(ground).multiply(1, 0, 1);
			out = out.lengthSqr() > 0.01 ? out.normalize().scale(2.0) : Vec3.ZERO;
			e.push(out.x, 1.2, out.z);
			Compat.markHurt(e);
			level.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY(0.5), e.getZ(), 2, 0.4, 0.4, 0.4, 0);
		}
	}

	private void ring(net.minecraft.core.particles.ParticleOptions particle, Vec3 c, double r, int n) {
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			level.sendParticles(particle, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
		}
	}

	/** Pour l'auto-test : combien de créatures ont été touchées. */
	public int hitCount() {
		return hit.size();
	}
}
