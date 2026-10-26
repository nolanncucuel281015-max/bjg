package com.diamondaltar.astral;

import com.diamondaltar.effect.EffectManager;
import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Le Gardien Astral : un squelette wither géant et céleste (700 PV) avec une barre de boss.
 * Il lance des pluies d'étoiles, des ondes de choc lunaires, se téléporte et invoque des esprits.
 * À sa mort : Cœur du Gardien + Lingot d'Astralite (+ parfois un Gabarit d'Amélioration Astrale).
 */
public final class Guardian {
	public static final String TAG = "diamondaltar_guardian";
	private static final DustParticleOptions MOON = new DustParticleOptions(0xEEF4FF, 2.0f);
	private static final DustParticleOptions NEBULA = new DustParticleOptions(0x9B4BFF, 2.0f);
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
	private static final Map<UUID, Integer> TIMERS = new HashMap<>();

	private Guardian() {
	}

	public static void summon(ServerLevel level, BlockPos pos) {
		Mob boss = EntityType.WITHER_SKELETON.create(level, EntitySpawnReason.TRIGGERED);
		if (boss == null) {
			return;
		}
		boss.setPos(Vec3.atBottomCenterOf(pos));
		boss.addTag(TAG);
		boss.setPersistenceRequired();
		boss.setCustomName(Component.translatable("entity.diamondaltar.guardian").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
		boss.setCustomNameVisible(true);
		set(boss, Attributes.MAX_HEALTH, 400);
		set(boss, Attributes.SCALE, 2.0);
		set(boss, Attributes.ATTACK_DAMAGE, 10);
		set(boss, Attributes.ARMOR, 10);
		set(boss, Attributes.ARMOR_TOUGHNESS, 4);
		set(boss, Attributes.KNOCKBACK_RESISTANCE, 1.0);
		set(boss, Attributes.MOVEMENT_SPEED, 0.28);
		set(boss, Attributes.FOLLOW_RANGE, 48);
		boss.setHealth(boss.getMaxHealth());
		ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
		sword.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		boss.setItemSlot(EquipmentSlot.MAINHAND, sword);
		boss.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
		boss.setDropChance(EquipmentSlot.MAINHAND, 0);
		boss.setDropChance(EquipmentSlot.HEAD, 0);
		level.addFreshEntity(boss);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, boss.getX(), boss.getY() + 1, boss.getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, boss.getX(), boss.getY() + 1, boss.getZ(), 200, 1, 2, 1, 0.5);
		level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0f, 0.8f);
	}

	private static void set(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double value) {
		var inst = e.getAttribute(attr);
		if (inst != null) {
			inst.setBaseValue(value);
		}
	}

	public static boolean isGuardian(LivingEntity e) {
		return e.getTags().contains(TAG);
	}

	/** Chaque tick : barre de boss, aura et attaques spéciales de chaque Gardien vivant. */
	public static void tick(ServerLevel level) {
		for (var entity : level.getAllEntities()) {
			if (entity instanceof LivingEntity living && living.isAlive() && isGuardian(living)) {
				tickBoss(level, living);
			}
		}
		// Retire les barres des boss disparus.
		Iterator<Map.Entry<UUID, ServerBossEvent>> it = BARS.entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			var ent = level.getEntity(e.getKey());
			if (ent == null || !ent.isAlive()) {
				if (ent != null || level.dimension() == AstralContent.DIMENSION) {
					e.getValue().removeAllPlayers();
					it.remove();
				}
			}
		}
	}

	private static void tickBoss(ServerLevel level, LivingEntity boss) {
		ServerBossEvent bar = BARS.computeIfAbsent(boss.getUUID(), u -> {
			ServerBossEvent b = new ServerBossEvent(boss.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
			b.setDarkenScreen(true);
			b.setCreateWorldFog(true);
			return b;
		});
		bar.setProgress(boss.getHealth() / boss.getMaxHealth());
		for (ServerPlayer p : level.players()) {
			boolean near = p.distanceToSqr(boss) < 48 * 48;
			if (near && !bar.getPlayers().contains(p)) {
				bar.addPlayer(p);
			} else if (!near && bar.getPlayers().contains(p)) {
				bar.removePlayer(p);
			}
		}
		// Aura : anneau de lune et poussière de nébuleuse.
		if (level.getGameTime() % 3 == 0) {
			double a = level.getGameTime() * 0.2;
			for (int k = 0; k < 3; k++) {
				double ang = a + k * Math.PI * 2 / 3;
				level.sendParticles(k == 0 ? MOON : NEBULA, boss.getX() + Math.cos(ang) * 1.4, boss.getY() + 1.5 + Math.sin(ang * 2) * 0.5, boss.getZ() + Math.sin(ang) * 1.4, 1, 0, 0, 0, 0);
			}
		}
		boolean enraged = boss.getHealth() < boss.getMaxHealth() * 0.4;
		if (enraged && level.getGameTime() % 20 == 0) {
			boss.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 1, false, false));
		}
		int t = TIMERS.merge(boss.getUUID(), 1, Integer::sum);
		int period = enraged ? 110 : 160;
		if (t % period != 0) {
			return;
		}
		ServerPlayer target = nearest(level, boss);
		if (target == null) {
			return;
		}
		switch ((t / period) % 4) {
			case 0 -> EffectManager.start(new GuardianStarfall(level, boss, target.position()));
			case 1 -> EffectManager.start(new GuardianShockwave(level, boss));
			case 2 -> blink(level, boss, target);
			default -> summonVexes(level, boss, target);
		}
	}

	private static ServerPlayer nearest(ServerLevel level, LivingEntity boss) {
		ServerPlayer best = null;
		double bd = 32 * 32;
		for (ServerPlayer p : level.players()) {
			double d = p.distanceToSqr(boss);
			if (d < bd && !p.isSpectator() && !p.isCreative()) {
				bd = d;
				best = p;
			}
		}
		return best;
	}

	private static void blink(ServerLevel level, LivingEntity boss, ServerPlayer target) {
		Vec3 from = boss.position();
		Vec3 behind = target.position().subtract(target.getLookAngle().multiply(1, 0, 1).normalize().scale(2.5));
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1.5, from.z, 80, 0.6, 1.5, 0.6, 0.3);
		boss.teleportTo(behind.x, target.getY(), behind.z);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, behind.x, target.getY() + 1.5, behind.z, 80, 0.6, 1.5, 0.6, 0.3);
		level.playSound(null, boss.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 2.0f, 0.5f);
		// Croissant lunaire : coup immédiat.
		for (int i = -6; i <= 6; i++) {
			double a = Math.toRadians(i * 12);
			Vec3 dir = target.position().subtract(boss.position()).multiply(1, 0, 1).normalize();
			Vec3 side = new Vec3(-dir.z, 0, dir.x);
			Vec3 p = boss.position().add(0, 1.6, 0).add(dir.scale(Math.cos(a) * 3)).add(side.scale(Math.sin(a) * 3));
			level.sendParticles(MOON, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
		target.hurtServer(level, level.damageSources().mobAttack(boss), 8f);
	}

	private static void summonVexes(ServerLevel level, LivingEntity boss, ServerPlayer target) {
		for (int i = 0; i < 2; i++) {
			Vex vex = EntityType.VEX.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (vex != null) {
				vex.setPos(boss.getX() + (i - 1) * 1.5, boss.getY() + 2.5, boss.getZ());
				vex.setLimitedLife(20 * 15);
				vex.setTarget(target);
				level.addFreshEntity(vex);
				level.sendParticles(NEBULA, vex.getX(), vex.getY(), vex.getZ(), 20, 0.3, 0.3, 0.3, 0);
			}
		}
		level.playSound(null, boss.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 2.0f, 0.7f);
	}

	/** À la mort : butin. */
	public static void onDeath(ServerLevel level, LivingEntity boss) {
		boss.spawnAtLocation(level, new ItemStack(AstralContent.GUARDIAN_HEART));
		boss.spawnAtLocation(level, new ItemStack(AstralContent.ASTRALITE_INGOT));
		if (level.getRandom().nextFloat() < 0.5f) {
			boss.spawnAtLocation(level, new ItemStack(AstralContent.ASTRAL_UPGRADE_TEMPLATE));
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, boss.getX(), boss.getY() + 1, boss.getZ(), 3, 1, 1, 1, 0);
		level.sendParticles(ParticleTypes.END_ROD, boss.getX(), boss.getY() + 1.5, boss.getZ(), 200, 0.5, 1, 0.5, 0.4);
		level.playSound(null, boss.blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 2.0f, 1.2f);
		ServerBossEvent bar = BARS.remove(boss.getUUID());
		if (bar != null) {
			bar.removeAllPlayers();
		}
		TIMERS.remove(boss.getUUID());
	}

	/** Pluie d'étoiles du Gardien sur la position du joueur. */
	static final class GuardianStarfall extends TimedEffect {
		private final LivingEntity boss;
		private final Vec3 center;
		private final Display.ItemDisplay[] stars = new Display.ItemDisplay[6];
		private final Vec3[] pos = new Vec3[6];

		GuardianStarfall(ServerLevel level, LivingEntity boss, Vec3 center) {
			super(level);
			this.boss = boss;
			this.center = center;
			for (int i = 0; i < stars.length; i++) {
				double a = Math.PI * 2 * i / stars.length;
				pos[i] = center.add(Math.cos(a) * 3, 18 + i, Math.sin(a) * 3);
				stars[i] = track(Visuals.item(level, pos[i], new ItemStack(Items.NETHER_STAR)));
				Visuals.transform(stars[i], new org.joml.Vector3f(), new Quaternionf(), 1.6f, 0);
			}
			level.playSound(null, BlockPos.containing(center), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0f, 0.5f);
		}

		@Override
		protected boolean tick() {
			// Cercle d'avertissement au sol.
			for (int k = 0; k < 24; k++) {
				double a = Math.PI * 2 * k / 24;
				level.sendParticles(NEBULA, center.x + Math.cos(a) * 3.5, center.y + 0.1, center.z + Math.sin(a) * 3.5, 1, 0, 0, 0, 0);
			}
			boolean any = false;
			for (int i = 0; i < stars.length; i++) {
				if (stars[i] == null) {
					continue;
				}
				any = true;
				Vec3 land = center.add(Math.cos(Math.PI * 2 * i / stars.length) * 1.5, 0, Math.sin(Math.PI * 2 * i / stars.length) * 1.5);
				Vec3 next = pos[i].add(land.subtract(pos[i]).normalize().scale(1.6));
				pos[i] = next;
				stars[i].setPos(next);
				Visuals.transform(stars[i], new org.joml.Vector3f(), new Quaternionf().rotateY(age * 0.6f), 1.6f, 1);
				level.sendParticles(ParticleTypes.END_ROD, next.x, next.y, next.z, 2, 0.1, 0.1, 0.1, 0.02);
				if (next.y <= center.y + 0.4) {
					level.sendParticles(ParticleTypes.EXPLOSION, next.x, next.y + 0.5, next.z, 1, 0, 0, 0, 0);
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(next, 6, 6, 6), e -> e != boss && !isGuardian(e))) {
						e.hurtServer(level, level.damageSources().mobAttack(boss), 5f);
					}
					level.playSound(null, BlockPos.containing(next), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.HOSTILE, 1.5f, 0.6f);
					stars[i].discard();
					stars[i] = null;
				}
			}
			return !any || age > 60;
		}
	}

	/** Onde de choc lunaire autour du Gardien. */
	static final class GuardianShockwave extends TimedEffect {
		private final LivingEntity boss;
		private final Vec3 center;

		GuardianShockwave(ServerLevel level, LivingEntity boss) {
			super(level);
			this.boss = boss;
			this.center = boss.position();
			level.playSound(null, boss.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 2.0f, 0.5f);
		}

		@Override
		protected boolean tick() {
			double r = 1 + age * 1.0;
			int n = (int) (r * 8);
			for (int i = 0; i < n; i++) {
				double a = Math.PI * 2 * i / n;
				level.sendParticles(i % 2 == 0 ? MOON : NEBULA, center.x + Math.cos(a) * r, center.y + 0.3, center.z + Math.sin(a) * r, 1, 0, 0.05, 0, 0);
			}
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(center, r * 2 + 1, 4, r * 2 + 1),
					e -> e != boss && !isGuardian(e) && Math.abs(Math.sqrt(e.distanceToSqr(center.x, e.getY(), center.z)) - r) < 0.8)) {
				e.hurtServer(level, level.damageSources().mobAttack(boss), 5f);
				Vec3 out = e.position().subtract(center).multiply(1, 0, 1).normalize().scale(1.4);
				e.push(out.x, 0.6, out.z);
				e.hurtMarked = true;
			}
			return age >= 12;
		}
	}
}
