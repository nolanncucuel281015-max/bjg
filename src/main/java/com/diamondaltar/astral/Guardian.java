package com.diamondaltar.astral;

import com.diamondaltar.Compat;
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
 * Le Gardien Astral : un squelette wither géant et céleste (200 PV) armé d'une hache, avec une barre de boss.
 * Il attaque lentement au début puis de plus en plus vite. Attaque spéciale : il saute et écrase le sol
 * (smash explosif), puis reste étourdi quelques secondes en prenant deux fois plus de dégâts.
 * Il lance aussi des pluies d'étoiles, des ondes de choc lunaires et se téléporte.
 * À sa mort : Cœur du Gardien + Lingot d'Astralite (+ parfois un Gabarit d'Amélioration Astrale).
 */
public final class Guardian {
	public static final String TAG = "diamondaltar_guardian";
	private static final DustParticleOptions MOON = new DustParticleOptions(0xEEF4FF, 2.0f);
	private static final DustParticleOptions NEBULA = new DustParticleOptions(0x9B4BFF, 2.0f);
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
	private static final Map<UUID, Integer> TIMERS = new HashMap<>();
	private static final Map<UUID, Integer> ATTACKS = new HashMap<>();
	/** Fin de l'étourdissement (temps de jeu) : pendant ce temps le Gardien prend des dégâts x2. */
	private static final Map<UUID, Long> STUNNED = new HashMap<>();
	private static final int STUN_TICKS = 80;

	private Guardian() {
	}

	public static void summon(ServerLevel level, BlockPos pos) {
		Mob boss = Compat.WITHER_SKELETON.create(level, EntitySpawnReason.TRIGGERED);
		if (boss == null) {
			return;
		}
		boss.setPos(Vec3.atBottomCenterOf(pos));
		boss.addTag(TAG);
		boss.setPersistenceRequired();
		boss.setCustomName(Component.translatable("entity.diamondaltar.guardian").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
		boss.setCustomNameVisible(true);
		set(boss, Attributes.MAX_HEALTH, 200);
		set(boss, Attributes.SCALE, 2.0);
		set(boss, Attributes.ATTACK_DAMAGE, 4);
		set(boss, Attributes.ARMOR, 6);
		set(boss, Attributes.ARMOR_TOUGHNESS, 0);
		set(boss, Attributes.KNOCKBACK_RESISTANCE, 1.0);
		set(boss, Attributes.MOVEMENT_SPEED, 0.25);
		set(boss, Attributes.FOLLOW_RANGE, 48);
		boss.setHealth(boss.getMaxHealth());
		ItemStack sword = new ItemStack(Items.NETHERITE_AXE);
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
		return Compat.tags(e).contains(TAG);
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
			ServerBossEvent b = Compat.bossEvent(boss.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
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
		if (isStunned(level, boss)) {
			// Étourdi : il ne bouge plus et n'attaque plus, des étoiles tournent au-dessus de sa tête.
			if (boss instanceof Mob mob) {
				mob.getNavigation().stop();
				mob.setTarget(null);
			}
			double a = level.getGameTime() * 0.35;
			double top = boss.getBbHeight() + 0.3;
			for (int k = 0; k < 3; k++) {
				double ang = a + k * Math.PI * 2 / 3;
				level.sendParticles(ParticleTypes.WAX_OFF, boss.getX() + Math.cos(ang) * 0.9, boss.getY() + top, boss.getZ() + Math.sin(ang) * 0.9, 1, 0, 0, 0, 0);
			}
			return;
		}
		STUNNED.remove(boss.getUUID());
		boolean enraged = boss.getHealth() < boss.getMaxHealth() * 0.4;
		if (enraged && level.getGameTime() % 20 == 0) {
			boss.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 0, false, false));
		}
		// Lent au début (une attaque toutes les 9 s), de plus en plus rapide quand il perd des PV (4,5 s).
		int period = 90 + (int) (90 * (boss.getHealth() / boss.getMaxHealth()));
		int t = TIMERS.merge(boss.getUUID(), 1, Integer::sum);
		if (t < period) {
			return;
		}
		ServerPlayer target = nearest(level, boss);
		if (target == null) {
			return;
		}
		TIMERS.put(boss.getUUID(), 0);
		int n = ATTACKS.merge(boss.getUUID(), 1, Integer::sum);
		if (n % 2 == 1) {
			EffectManager.start(new GuardianLeap(level, boss, target));
			return;
		}
		switch ((n / 2) % 3) {
			case 0 -> EffectManager.start(new GuardianStarfall(level, boss, target.position()));
			case 1 -> EffectManager.start(new GuardianShockwave(level, boss));
			default -> blink(level, boss, target);
		}
	}

	public static boolean isStunned(ServerLevel level, LivingEntity boss) {
		Long until = STUNNED.get(boss.getUUID());
		return until != null && level.getGameTime() < until;
	}

	/** Étourdit le Gardien : il ne bouge plus et prend deux fois plus de dégâts. */
	public static void stun(ServerLevel level, LivingEntity boss) {
		STUNNED.put(boss.getUUID(), level.getGameTime() + STUN_TICKS);
		boss.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STUN_TICKS, 10, false, false));
		boss.addEffect(new MobEffectInstance(MobEffects.GLOWING, STUN_TICKS, 0, false, false));
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(boss) < 40 * 40) {
				Compat.overlay(p, Component.translatable("diamondaltar.guardian.stunned").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			}
		}
		level.playSound(null, boss.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.2f, 0.6f);
	}

	/** Pendant l'étourdissement, chaque coup reçu inflige ses dégâts une deuxième fois. */
	public static void afterDamage(LivingEntity boss, float damageTaken) {
		if (!(boss.level() instanceof ServerLevel level) || !isGuardian(boss) || !boss.isAlive()
				|| damageTaken <= 0 || !isStunned(level, boss)) {
			return;
		}
		level.sendParticles(ParticleTypes.CRIT, boss.getX(), boss.getY() + boss.getBbHeight() * 0.6, boss.getZ(), 25, 0.6, 0.8, 0.6, 0.4);
		float health = boss.getHealth() - damageTaken;
		if (health <= 0) {
			boss.kill(level);
		} else {
			boss.setHealth(health);
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
		target.hurtServer(level, level.damageSources().mobAttack(boss), 4f);
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
		ATTACKS.remove(boss.getUUID());
		STUNNED.remove(boss.getUUID());
	}

	/**
	 * Saut écrasant : le Gardien lève sa hache, bondit vers le joueur, retombe en écrasant le sol
	 * (explosion sans casser de blocs) puis reste étourdi.
	 */
	static final class GuardianLeap extends TimedEffect {
		private static final int WINDUP = 14;
		private final LivingEntity boss;
		private final ServerPlayer target;
		private boolean launched;
		private boolean landed;

		GuardianLeap(ServerLevel level, LivingEntity boss, ServerPlayer target) {
			super(level);
			this.boss = boss;
			this.target = target;
			level.playSound(null, boss.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0f, 0.7f);
		}

		@Override
		protected boolean tick() {
			if (!boss.isAlive()) {
				return true;
			}
			if (age < WINDUP) {
				// Préparation : il s'accroupit et lève les bras, des éclats montent autour de lui.
				if (boss instanceof Mob mob) {
					mob.getNavigation().stop();
					mob.getLookControl().setLookAt(target);
				}
				boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
				if (age % 4 == 0) {
					Compat.swing(boss, net.minecraft.world.InteractionHand.MAIN_HAND);
				}
				for (int k = 0; k < 12; k++) {
					double a = Math.PI * 2 * k / 12;
					level.sendParticles(k % 2 == 0 ? MOON : NEBULA, boss.getX() + Math.cos(a) * 2.2, boss.getY() + 0.2 + age * 0.12, boss.getZ() + Math.sin(a) * 2.2, 1, 0, 0, 0, 0);
				}
				return false;
			}
			if (!launched) {
				launched = true;
				Vec3 to = target.position().subtract(boss.position());
				Vec3 flat = new Vec3(to.x, 0, to.z);
				double dist = Math.min(flat.length(), 14);
				Vec3 dir = flat.lengthSqr() > 0.01 ? flat.normalize() : Vec3.ZERO;
				boss.setDeltaMovement(dir.x * dist * 0.11, 1.15, dir.z * dist * 0.11);
				Compat.markHurt(boss);
				Compat.swing(boss, net.minecraft.world.InteractionHand.MAIN_HAND);
				level.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.5f, 1.4f);
				level.sendParticles(ParticleTypes.CLOUD, boss.getX(), boss.getY(), boss.getZ(), 30, 1, 0.1, 1, 0.1);
				return false;
			}
			level.sendParticles(ParticleTypes.END_ROD, boss.getX(), boss.getY() + 1, boss.getZ(), 3, 0.4, 0.6, 0.4, 0.01);
			boolean down = (age > WINDUP + 6 && boss.onGround()) || age > WINDUP + 60;
			if (!down) {
				if (age > WINDUP + 10 && boss.getDeltaMovement().y < 0) {
					boss.setDeltaMovement(boss.getDeltaMovement().add(0, -0.25, 0)); // retombe lourdement
					Compat.markHurt(boss);
				}
				return false;
			}
			if (!landed) {
				landed = true;
				smash();
				stun(level, boss);
			}
			return true;
		}

		private void smash() {
			Vec3 c = boss.position();
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 12, 2.5, 0.3, 2.5, 0);
			for (int ring = 1; ring <= 3; ring++) {
				double r = ring * 2.0;
				int n = (int) (r * 10);
				for (int i = 0; i < n; i++) {
					double a = Math.PI * 2 * i / n;
					level.sendParticles(ring % 2 == 0 ? NEBULA : MOON, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, 1, 0, 0.1, 0, 0);
				}
			}
			level.playSound(null, boss.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 2.5f, 0.5f);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(c, 12, 6, 12),
					e -> e != boss && !isGuardian(e) && e.distanceToSqr(c) < 6 * 6)) {
				double d = Math.sqrt(e.distanceToSqr(c));
				e.hurtServer(level, level.damageSources().mobAttack(boss), (float) Math.max(2.0, 6.0 - d));
				Vec3 out = e.position().subtract(c).multiply(1, 0, 1);
				out = out.lengthSqr() > 0.01 ? out.normalize().scale(1.2) : new Vec3(0, 0, 0);
				e.push(out.x, 0.7, out.z);
				Compat.markHurt(e);
			}
		}
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
						e.hurtServer(level, level.damageSources().mobAttack(boss), 3f);
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
				e.hurtServer(level, level.damageSources().mobAttack(boss), 3f);
				Vec3 out = e.position().subtract(center).multiply(1, 0, 1).normalize().scale(1.4);
				e.push(out.x, 0.6, out.z);
				Compat.markHurt(e);
			}
			return age >= 12;
		}
	}
}
