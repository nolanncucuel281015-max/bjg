package com.diamondaltar.realms;

import com.diamondaltar.Compat;
import com.diamondaltar.effect.EffectManager;
import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
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
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les 7 boss des Royaumes, invoqués avec le Sceau de leur royaume. Chacun a une barre de boss, une aura,
 * et trois attaques spéciales qui tournent : un barrage qui tombe du ciel, une onde de choc et une attaque
 * signature propre à son royaume. Ils sont de plus en plus forts d'un royaume à l'autre et accélèrent
 * quand ils perdent leurs PV. À leur mort : voir {@link RealmRewards} (plus le boss est fort, plus le butin l'est).
 */
public final class RealmBoss {
	public static final String TAG = "diamondaltar_realm_boss";
	private static final String REALM_TAG = "diamondaltar_boss_";
	public static final String MINION_TAG = "diamondaltar_realm_minion";
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
	private static final Map<UUID, ResourceKeyHolder> BAR_LEVELS = new HashMap<>();
	private static final Map<UUID, Integer> TIMERS = new HashMap<>();
	private static final Map<UUID, Integer> ATTACKS = new HashMap<>();

	private record ResourceKeyHolder(net.minecraft.resources.ResourceKey<Level> key) {
	}

	private RealmBoss() {
	}

	// ---------------------------------------------------------------- description des boss

	private static EntityType<? extends Mob> type(Realm r) {
		return switch (r) {
			case EMBER -> Compat.BLAZE;
			case FROST -> Compat.STRAY;
			case STORM -> Compat.BREEZE;
			case SYLVAN -> Compat.RAVAGER;
			case ABYSS -> Compat.DROWNED;
			case SHADOW -> Compat.WITHER_SKELETON;
			case SOLAR -> Compat.VINDICATOR;
		};
	}

	private static double scale(Realm r) {
		return switch (r) {
			case EMBER -> 2.6;
			case FROST, ABYSS -> 2.4;
			case STORM -> 3.0;
			case SYLVAN -> 1.7;
			case SHADOW -> 2.2;
			case SOLAR -> 2.5;
		};
	}

	public static float maxHealth(Realm r) {
		return 300 + 50 * r.tier();
	}

	/** Multiplicateur des dégâts des attaques spéciales (1,0 pour Braise, 2,2 pour Soleil). */
	private static float power(Realm r) {
		return 1f + 0.2f * r.tier();
	}

	private static ItemStack barrageItem(Realm r) {
		Item item = switch (r) {
			case EMBER -> Items.FIRE_CHARGE;
			case FROST -> Items.BLUE_ICE;
			case STORM -> Items.TRIDENT;
			case SYLVAN -> Items.MOSS_BLOCK;
			case ABYSS -> Items.HEART_OF_THE_SEA;
			case SHADOW -> Items.WITHER_SKELETON_SKULL;
			case SOLAR -> Items.GLOWSTONE;
		};
		return new ItemStack(item);
	}

	static ParticleOptions ambient(Realm r) {
		return switch (r) {
			case EMBER -> ParticleTypes.FLAME;
			case FROST -> ParticleTypes.SNOWFLAKE;
			case STORM -> ParticleTypes.ELECTRIC_SPARK;
			case SYLVAN -> ParticleTypes.HAPPY_VILLAGER;
			case ABYSS -> ParticleTypes.SPLASH;
			case SHADOW -> ParticleTypes.SOUL;
			case SOLAR -> ParticleTypes.END_ROD;
		};
	}

	// ---------------------------------------------------------------- invocation

	/** Fait apparaître le boss du royaume à cette position. */
	public static LivingEntity summon(ServerLevel level, Realm realm, Vec3 pos) {
		Mob boss = type(realm).create(level, EntitySpawnReason.TRIGGERED);
		if (boss == null) {
			return null;
		}
		boss.setPos(pos);
		boss.addTag(TAG);
		boss.addTag(REALM_TAG + realm.id);
		boss.setPersistenceRequired();
		boss.setCustomName(Component.translatable("entity.diamondaltar." + realm.id + "_boss").withStyle(realm.chat, ChatFormatting.BOLD));
		boss.setCustomNameVisible(true);
		int t = realm.tier();
		set(boss, Attributes.MAX_HEALTH, maxHealth(realm));
		set(boss, Attributes.SCALE, scale(realm));
		set(boss, Attributes.ATTACK_DAMAGE, 8 + 2 * t);
		set(boss, Attributes.ARMOR, 8 + t);
		set(boss, Attributes.ARMOR_TOUGHNESS, 2 + t);
		set(boss, Attributes.KNOCKBACK_RESISTANCE, 1.0);
		set(boss, Attributes.FOLLOW_RANGE, 48);
		if (realm != Realm.EMBER && realm != Realm.STORM) {
			set(boss, Attributes.MOVEMENT_SPEED, realm == Realm.SYLVAN ? 0.32 : 0.28);
		}
		boss.setHealth(boss.getMaxHealth());
		ItemStack weapon = switch (realm) {
			case FROST -> new ItemStack(Items.BOW);
			case ABYSS -> new ItemStack(Items.TRIDENT);
			case SHADOW -> new ItemStack(Items.DIAMOND_SWORD);
			case SOLAR -> new ItemStack(Items.NETHERITE_AXE);
			default -> ItemStack.EMPTY;
		};
		if (!weapon.isEmpty()) {
			weapon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			boss.setItemSlot(EquipmentSlot.MAINHAND, weapon);
			boss.setDropChance(EquipmentSlot.MAINHAND, 0);
		}
		if (realm == Realm.FROST || realm == Realm.ABYSS || realm == Realm.SHADOW) {
			// Un casque : les morts-vivants ne brûlent pas au soleil des royaumes.
			boss.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
			boss.setDropChance(EquipmentSlot.HEAD, 0);
		}
		boss.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		level.addFreshEntity(boss);
		DustParticleOptions dust = new DustParticleOptions(realm.color, 2.5f);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 1, pos.z, 1, 0, 0, 0, 0);
		level.sendParticles(dust, pos.x, pos.y + 2, pos.z, 200, 1.5, 2.5, 1.5, 0);
		level.sendParticles(ambient(realm), pos.x, pos.y + 2, pos.z, 120, 1.5, 2.5, 1.5, 0.1);
		level.playSound(null, BlockPos.containing(pos), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0f, 0.6f + 0.1f * t);
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(pos) < 64 * 64) {
				Compat.overlay(p, Component.translatable("diamondaltar.realm_boss.arrives",
						Component.translatable("entity.diamondaltar." + realm.id + "_boss")).withStyle(realm.chat, ChatFormatting.BOLD));
			}
		}
		return boss;
	}

	/** Position d'invocation : sur le sol, quelques blocs devant le joueur. */
	public static Vec3 groundAhead(ServerLevel level, ServerPlayer player, double distance) {
		Vec3 look = player.getLookAngle().multiply(1, 0, 1);
		look = look.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : look.normalize();
		Vec3 p = player.position().add(look.scale(distance));
		int x = (int) Math.floor(p.x);
		int z = (int) Math.floor(p.z);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (y <= level.getMinY() + 1 || Math.abs(y - player.getY()) > 12) {
			return player.position().add(look.scale(3));
		}
		return new Vec3(x + 0.5, y, z + 0.5);
	}

	private static void set(LivingEntity e, Holder<Attribute> attr, double value) {
		var inst = e.getAttribute(attr);
		if (inst != null) {
			inst.setBaseValue(value);
		}
	}

	public static boolean isBoss(LivingEntity e) {
		return Compat.tags(e).contains(TAG);
	}

	public static Realm realmOf(LivingEntity e) {
		for (Realm r : Realm.values()) {
			if (Compat.tags(e).contains(REALM_TAG + r.id)) {
				return r;
			}
		}
		return null;
	}

	/** Un boss de ce royaume est-il déjà là, pas trop loin ? */
	public static boolean bossNear(ServerLevel level, Realm realm, Vec3 pos) {
		return !level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(pos, 160, 160, 160),
				e -> e.isAlive() && realm == realmOf(e)).isEmpty();
	}

	// ---------------------------------------------------------------- comportement

	/** Chaque tick, dans la dimension d'un royaume : barre de boss, aura et attaques. */
	public static void tick(ServerLevel level) {
		List<LivingEntity> bosses = new ArrayList<>();
		for (var entity : level.getAllEntities()) {
			if (entity instanceof LivingEntity living && living.isAlive() && isBoss(living)) {
				bosses.add(living);
			}
		}
		for (LivingEntity boss : bosses) {
			Realm realm = realmOf(boss);
			if (realm != null) {
				tickBoss(level, boss, realm);
			}
		}
		Iterator<Map.Entry<UUID, ServerBossEvent>> it = BARS.entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			ResourceKeyHolder where = BAR_LEVELS.get(e.getKey());
			if (where != null && !where.key().equals(level.dimension())) {
				continue;
			}
			var ent = level.getEntity(e.getKey());
			if (ent == null || !ent.isAlive()) {
				e.getValue().removeAllPlayers();
				BAR_LEVELS.remove(e.getKey());
				it.remove();
			}
		}
	}

	private static void tickBoss(ServerLevel level, LivingEntity boss, Realm realm) {
		ServerBossEvent bar = BARS.computeIfAbsent(boss.getUUID(), u -> {
			ServerBossEvent b = Compat.bossEvent(boss.getDisplayName(), realm.bar, BossEvent.BossBarOverlay.NOTCHED_10);
			b.setDarkenScreen(realm == Realm.SHADOW);
			b.setCreateWorldFog(true);
			BAR_LEVELS.put(u, new ResourceKeyHolder(level.dimension()));
			return b;
		});
		bar.setProgress(boss.getHealth() / boss.getMaxHealth());
		for (ServerPlayer p : level.players()) {
			boolean near = p.distanceToSqr(boss) < 56 * 56;
			if (near && !bar.getPlayers().contains(p)) {
				bar.addPlayer(p);
			} else if (!near && bar.getPlayers().contains(p)) {
				bar.removePlayer(p);
			}
		}
		// Aura : deux anneaux aux couleurs du royaume.
		if (level.getGameTime() % 3 == 0) {
			double a = level.getGameTime() * 0.2;
			double r = boss.getBbWidth() * 0.8 + 0.6;
			DustParticleOptions main = new DustParticleOptions(realm.color, 2.0f);
			DustParticleOptions accent = new DustParticleOptions(realm.accent, 1.6f);
			for (int k = 0; k < 3; k++) {
				double ang = a + k * Math.PI * 2 / 3;
				level.sendParticles(k == 0 ? accent : main, boss.getX() + Math.cos(ang) * r, boss.getY() + boss.getBbHeight() * 0.5
						+ Math.sin(ang * 2) * 0.5, boss.getZ() + Math.sin(ang) * r, 1, 0, 0, 0, 0);
			}
			level.sendParticles(ambient(realm), boss.getX(), boss.getY() + boss.getBbHeight() * 0.5, boss.getZ(), 1,
					boss.getBbWidth() * 0.5, boss.getBbHeight() * 0.4, boss.getBbWidth() * 0.5, 0.01);
		}
		ServerPlayer target = nearest(level, boss, 40);
		if (boss instanceof Mob mob && target != null && level.getGameTime() % 20 == 0
				&& (mob.getTarget() == null || !mob.getTarget().isAlive())) {
			mob.setTarget(target);
		}
		boolean enraged = boss.getHealth() < boss.getMaxHealth() * 0.4;
		if (enraged && level.getGameTime() % 20 == 0) {
			boss.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 0, false, false));
			boss.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 30, 1, false, false));
		}
		// Une attaque spéciale toutes les 7 s au début, toutes les 3,5 s à la fin.
		int period = 70 + (int) (70 * (boss.getHealth() / boss.getMaxHealth()));
		int t = TIMERS.merge(boss.getUUID(), 1, Integer::sum);
		if (t < period || target == null) {
			return;
		}
		TIMERS.put(boss.getUUID(), 0);
		int n = ATTACKS.merge(boss.getUUID(), 1, Integer::sum);
		switch (n % 3) {
			case 1 -> EffectManager.start(new Barrage(level, boss, realm, target.position()));
			case 2 -> EffectManager.start(new Nova(level, boss, realm));
			default -> signature(level, boss, realm, target);
		}
	}

	static void signature(ServerLevel level, LivingEntity boss, Realm realm, ServerPlayer target) {
		switch (realm) {
			case EMBER -> EffectManager.start(new Inferno(level, boss));
			case FROST -> EffectManager.start(new Blizzard(level, boss));
			case STORM -> EffectManager.start(new ThunderStorm(level, boss));
			case SYLVAN -> EffectManager.start(new Roots(level, boss));
			case ABYSS -> EffectManager.start(new TidalWave(level, boss, target));
			case SHADOW -> shadowStep(level, boss, target);
			case SOLAR -> EffectManager.start(new SolarJudgement(level, boss));
		}
	}

	static ServerPlayer nearest(ServerLevel level, LivingEntity boss, double range) {
		ServerPlayer best = null;
		double bd = range * range;
		for (ServerPlayer p : level.players()) {
			double d = p.distanceToSqr(boss);
			if (d < bd && !p.isSpectator() && !p.isCreative()) {
				bd = d;
				best = p;
			}
		}
		return best;
	}

	static List<LivingEntity> victims(ServerLevel level, LivingEntity boss, Vec3 center, double radius) {
		return level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(center, radius * 2, radius * 2, radius * 2),
				e -> e != boss && e.isAlive() && !isBoss(e) && !Compat.tags(e).contains(MINION_TAG)
						&& !(e instanceof ServerPlayer p && (p.isCreative() || p.isSpectator()))
						&& e.distanceToSqr(center) < radius * radius);
	}

	/** Dégâts + effet du royaume (feu, gel, poison, ténèbres…). */
	static void afflict(ServerLevel level, LivingEntity boss, Realm realm, LivingEntity victim, float damage) {
		victim.hurtServer(level, level.damageSources().mobAttack(boss), damage * power(realm));
		switch (realm) {
			case EMBER -> victim.igniteForSeconds(4);
			case FROST -> {
				victim.setTicksFrozen(victim.getTicksRequiredToFreeze() + 100);
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
			}
			case STORM -> {
				victim.push(0, 0.6, 0);
				Compat.markHurt(victim);
			}
			case SYLVAN -> victim.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
			case ABYSS -> victim.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 120, 1));
			case SHADOW -> {
				victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
				victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1));
			}
			case SOLAR -> {
				victim.igniteForSeconds(2);
				victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
				victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
			}
		}
	}

	private static void ring(ServerLevel level, ParticleOptions p, Vec3 c, double r, double y) {
		int n = Math.max(12, (int) (r * 8));
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			level.sendParticles(p, c.x + Math.cos(a) * r, c.y + y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
		}
	}

	private static void heal(LivingEntity boss, float fraction) {
		boss.setHealth(Math.min(boss.getMaxHealth(), boss.getHealth() + boss.getMaxHealth() * fraction));
	}

	/** Roi des Ombres : ténèbres sur tous les joueurs, il apparaît derrière sa cible et appelle ses ombres. */
	private static void shadowStep(ServerLevel level, LivingEntity boss, ServerPlayer target) {
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(boss) < 40 * 40) {
				p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0));
				p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0));
			}
		}
		Vec3 from = boss.position();
		Vec3 behind = target.position().subtract(target.getLookAngle().multiply(1, 0, 1).normalize().scale(2.5));
		level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y + 1.5, from.z, 60, 0.8, 1.5, 0.8, 0.05);
		boss.teleportTo(behind.x, target.getY(), behind.z);
		level.sendParticles(ParticleTypes.SOUL, behind.x, target.getY() + 1.5, behind.z, 60, 0.8, 1.5, 0.8, 0.05);
		level.playSound(null, boss.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 2.0f, 0.4f);
		afflict(level, boss, Realm.SHADOW, target, 4f);
		int minions = level.getEntitiesOfClass(LivingEntity.class, boss.getBoundingBox().inflate(32),
				e -> e.isAlive() && Compat.tags(e).contains(MINION_TAG)).size();
		for (int i = 0; i < 3 && minions + i < 6; i++) {
			Mob shade = Compat.WITHER_SKELETON.create(level, EntitySpawnReason.TRIGGERED);
			if (shade == null) {
				break;
			}
			double a = Math.PI * 2 * i / 3;
			shade.setPos(boss.getX() + Math.cos(a) * 3, boss.getY(), boss.getZ() + Math.sin(a) * 3);
			shade.addTag(MINION_TAG);
			shade.setCustomName(Component.translatable("entity.diamondaltar.shade").withStyle(ChatFormatting.DARK_PURPLE));
			shade.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));
			shade.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
			shade.setDropChance(EquipmentSlot.MAINHAND, 0);
			shade.setDropChance(EquipmentSlot.HEAD, 0);
			shade.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false));
			shade.setTarget(target);
			level.addFreshEntity(shade);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, shade.getX(), shade.getY() + 1, shade.getZ(), 20, 0.3, 0.8, 0.3, 0.02);
		}
	}

	// ---------------------------------------------------------------- mort

	public static void onDeath(ServerLevel level, LivingEntity boss) {
		Realm realm = realmOf(boss);
		if (realm == null) {
			return;
		}
		RealmRewards.drop(level, boss, realm);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, boss.getX(), boss.getY() + 1, boss.getZ(), 3, 1, 1, 1, 0);
		level.sendParticles(new DustParticleOptions(realm.color, 3f), boss.getX(), boss.getY() + 2, boss.getZ(), 250, 1.5, 2, 1.5, 0);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, boss.getX(), boss.getY() + 2, boss.getZ(), 150, 1, 1.5, 1, 0.6);
		level.playSound(null, boss.blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 2.0f, 1.0f + 0.1f * realm.tier());
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(boss) < 64 * 64) {
				Compat.overlay(p, Component.translatable("diamondaltar.realm_boss.defeated",
						Component.translatable("entity.diamondaltar." + realm.id + "_boss")).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			}
		}
		ServerBossEvent bar = BARS.remove(boss.getUUID());
		if (bar != null) {
			bar.removeAllPlayers();
		}
		BAR_LEVELS.remove(boss.getUUID());
		TIMERS.remove(boss.getUUID());
		ATTACKS.remove(boss.getUUID());
		// Les ombres disparaissent avec leur roi.
		for (LivingEntity shade : level.getEntitiesOfClass(LivingEntity.class, boss.getBoundingBox().inflate(48),
				e -> Compat.tags(e).contains(MINION_TAG))) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, shade.getX(), shade.getY() + 1, shade.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
			shade.discard();
		}
	}

	// ================================================================ attaques

	/** Barrage : des projectiles du royaume tombent du ciel autour du joueur (cercle d'avertissement au sol). */
	static final class Barrage extends TimedEffect {
		private final LivingEntity boss;
		private final Realm realm;
		private final Vec3 center;
		private final Display.ItemDisplay[] shots = new Display.ItemDisplay[7];
		private final Vec3[] pos = new Vec3[7];
		private final Vec3[] land = new Vec3[7];

		Barrage(ServerLevel level, LivingEntity boss, Realm realm, Vec3 center) {
			super(level);
			this.boss = boss;
			this.realm = realm;
			this.center = center;
			for (int i = 0; i < shots.length; i++) {
				double a = Math.PI * 2 * i / (shots.length - 1);
				double r = i == 0 ? 0 : 2.5;
				land[i] = center.add(Math.cos(a) * r, 0, Math.sin(a) * r);
				pos[i] = land[i].add(Math.cos(a) * 4, 20 + i * 1.5, Math.sin(a) * 4);
				shots[i] = track(Visuals.item(level, pos[i], barrageItem(realm)));
				Visuals.transform(shots[i], new org.joml.Vector3f(), new Quaternionf(), 1.8f, 0);
			}
			level.playSound(null, BlockPos.containing(center), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0f, 0.5f + 0.15f * realm.tier());
		}

		@Override
		protected boolean tick() {
			if (age % 2 == 0) {
				ring(level, new DustParticleOptions(realm.color, 1.5f), center, 4, 0.1);
			}
			boolean any = false;
			for (int i = 0; i < shots.length; i++) {
				if (shots[i] == null) {
					continue;
				}
				any = true;
				Vec3 next = pos[i].add(land[i].subtract(pos[i]).normalize().scale(1.4));
				pos[i] = next;
				shots[i].setPos(next);
				Visuals.transform(shots[i], new org.joml.Vector3f(), new Quaternionf().rotateY(age * 0.5f), 1.8f, 1);
				level.sendParticles(ambient(realm), next.x, next.y, next.z, 2, 0.15, 0.15, 0.15, 0.01);
				if (next.y <= land[i].y + 0.4) {
					level.sendParticles(ParticleTypes.EXPLOSION, next.x, next.y + 0.5, next.z, 1, 0, 0, 0, 0);
					level.sendParticles(new DustParticleOptions(realm.color, 2f), next.x, next.y + 0.5, next.z, 20, 1, 0.4, 1, 0);
					if (realm == Realm.STORM) {
						LightningBolt bolt = new LightningBolt(Compat.LIGHTNING_BOLT, level);
						bolt.setPos(next.x, next.y, next.z);
						bolt.setVisualOnly(true);
						level.addFreshEntity(bolt);
					}
					for (LivingEntity e : victims(level, boss, next, 2.6)) {
						afflict(level, boss, realm, e, 3f);
					}
					level.playSound(null, BlockPos.containing(next), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.HOSTILE, 1.5f, 0.6f);
					shots[i].discard();
					shots[i] = null;
				}
			}
			return !any || age > 60;
		}
	}

	/** Onde de choc aux couleurs du royaume, autour du boss. */
	static final class Nova extends TimedEffect {
		private final LivingEntity boss;
		private final Realm realm;
		private final Vec3 center;

		Nova(ServerLevel level, LivingEntity boss, Realm realm) {
			super(level);
			this.boss = boss;
			this.realm = realm;
			this.center = boss.position();
			level.playSound(null, boss.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 2.0f, 0.5f);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 0.5, center.z, 1, 0, 0, 0, 0);
		}

		@Override
		protected boolean tick() {
			double r = 1.5 + age * 1.1;
			ring(level, new DustParticleOptions(realm.color, 2f), center, r, 0.3);
			ring(level, ambient(realm), center, r - 0.4, 0.6);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(center, r * 2 + 2, 6, r * 2 + 2),
					e -> e != boss && !isBoss(e) && !Compat.tags(e).contains(MINION_TAG)
							&& Math.abs(Math.sqrt(e.distanceToSqr(center.x, e.getY(), center.z)) - r) < 0.9
							&& Math.abs(e.getY() - center.y) < 3)) {
				afflict(level, boss, realm, e, 3f);
				Vec3 out = e.position().subtract(center).multiply(1, 0, 1);
				out = out.lengthSqr() > 0.01 ? out.normalize().scale(1.3) : Vec3.ZERO;
				e.push(out.x, 0.5, out.z);
				Compat.markHurt(e);
			}
			return age >= 14;
		}
	}

	/** Seigneur des Braises : trois anneaux de flammes qui s'élargissent et embrasent tout. */
	static final class Inferno extends TimedEffect {
		private final LivingEntity boss;
		private final Vec3 center;

		Inferno(ServerLevel level, LivingEntity boss) {
			super(level);
			this.boss = boss;
			this.center = boss.position();
			level.playSound(null, boss.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.5f, 0.5f);
		}

		@Override
		protected boolean tick() {
			for (int wave = 0; wave < 3; wave++) {
				int a = age - wave * 10;
				if (a < 0 || a > 14) {
					continue;
				}
				double r = 1 + a * 0.9;
				ring(level, ParticleTypes.FLAME, center, r, 0.2);
				ring(level, ParticleTypes.LAVA, center, r, 0.5);
				for (LivingEntity e : victims(level, boss, center, r + 0.8)) {
					if (Math.abs(Math.sqrt(e.distanceToSqr(center.x, e.getY(), center.z)) - r) < 0.9) {
						afflict(level, boss, Realm.EMBER, e, 2.5f);
					}
				}
				if (a == 0) {
					level.playSound(null, BlockPos.containing(center), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2f, 0.7f);
				}
			}
			return age >= 34;
		}
	}

	/** Reine du Givre : un blizzard de 5 s qui gèle, ralentit et blesse tout autour d'elle. */
	static final class Blizzard extends TimedEffect {
		private final LivingEntity boss;

		Blizzard(ServerLevel level, LivingEntity boss) {
			super(level);
			this.boss = boss;
			level.playSound(null, boss.blockPosition(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.HOSTILE, 3f, 0.5f);
			level.playSound(null, boss.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.HOSTILE, 1.5f, 0.6f);
		}

		@Override
		protected boolean tick() {
			if (!boss.isAlive()) {
				return true;
			}
			Vec3 c = boss.position();
			level.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y + 3, c.z, 40, 9, 3, 9, 0.15);
			level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 1, c.z, 10, 8, 1, 8, 0.05);
			if (age % 10 == 0) {
				for (LivingEntity e : victims(level, boss, c, 12)) {
					afflict(level, boss, Realm.FROST, e, 1.5f);
					Vec3 in = c.subtract(e.position()).multiply(1, 0, 1);
					if (in.lengthSqr() > 0.01) {
						in = in.normalize().scale(0.25);
						e.push(in.x, 0, in.z);
						Compat.markHurt(e);
					}
				}
			}
			return age >= 100;
		}
	}

	/** Titan de la Tempête : la foudre tombe sur chaque joueur, après un cercle d'avertissement. */
	static final class ThunderStorm extends TimedEffect {
		private final LivingEntity boss;
		private final List<Vec3> marks = new ArrayList<>();

		ThunderStorm(ServerLevel level, LivingEntity boss) {
			super(level);
			this.boss = boss;
			level.playSound(null, boss.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2f, 0.6f);
		}

		@Override
		protected boolean tick() {
			if (age % 15 == 0 && age < 75) {
				marks.clear();
				for (ServerPlayer p : level.players()) {
					if (p.distanceToSqr(boss) < 36 * 36 && !p.isCreative() && !p.isSpectator()) {
						marks.add(p.position());
						marks.add(p.position().add(level.getRandom().nextGaussian() * 3, 0, level.getRandom().nextGaussian() * 3));
					}
				}
			}
			int phase = age % 15;
			for (Vec3 m : marks) {
				if (phase < 12) {
					ring(level, ParticleTypes.ELECTRIC_SPARK, m, 2.2, 0.1);
				} else if (phase == 12) {
					LightningBolt bolt = new LightningBolt(Compat.LIGHTNING_BOLT, level);
					bolt.setPos(m.x, m.y, m.z);
					bolt.setVisualOnly(true);
					level.addFreshEntity(bolt);
					for (LivingEntity e : victims(level, boss, m, 2.6)) {
						afflict(level, boss, Realm.STORM, e, 4f);
					}
				}
			}
			return age >= 80;
		}
	}

	/** Béhémoth Sylvestre : des racines jaillissent sous les joueurs et les immobilisent ; il se soigne. */
	static final class Roots extends TimedEffect {
		private final LivingEntity boss;
		private final List<Vec3> marks = new ArrayList<>();

		Roots(ServerLevel level, LivingEntity boss) {
			super(level);
			this.boss = boss;
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(boss) < 32 * 32 && !p.isCreative() && !p.isSpectator()) {
					marks.add(p.position());
				}
			}
			level.playSound(null, boss.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.5f, 0.7f);
		}

		@Override
		protected boolean tick() {
			for (Vec3 m : marks) {
				if (age < 16) {
					ring(level, ParticleTypes.HAPPY_VILLAGER, m, 1.8, 0.1);
				} else if (age == 16) {
					level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, m.x, m.y + 1, m.z, 60, 1, 1, 1, 0);
					level.sendParticles(ParticleTypes.CRIT, m.x, m.y + 0.5, m.z, 30, 1, 0.5, 1, 0.3);
					level.playSound(null, BlockPos.containing(m), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 2f, 0.5f);
					for (LivingEntity e : victims(level, boss, m, 2.5)) {
						afflict(level, boss, Realm.SYLVAN, e, 3f);
						e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 6));
						e.setDeltaMovement(0, 0, 0);
						Compat.markHurt(e);
					}
				}
			}
			if (age == 16) {
				heal(boss, 0.04f);
				level.sendParticles(ParticleTypes.HEART, boss.getX(), boss.getY() + boss.getBbHeight(), boss.getZ(), 8, 0.6, 0.3, 0.6, 0);
			}
			return age >= 20;
		}
	}

	/** Léviathan des Abysses : un raz-de-marée part vers le joueur et emporte tout sur son passage. */
	static final class TidalWave extends TimedEffect {
		private final LivingEntity boss;
		private final Vec3 origin;
		private final Vec3 dir;
		private final java.util.Set<UUID> hit = new java.util.HashSet<>();

		TidalWave(ServerLevel level, LivingEntity boss, ServerPlayer target) {
			super(level);
			this.boss = boss;
			this.origin = boss.position();
			Vec3 d = target.position().subtract(origin).multiply(1, 0, 1);
			this.dir = d.lengthSqr() > 0.01 ? d.normalize() : new Vec3(1, 0, 0);
			level.playSound(null, boss.blockPosition(), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.HOSTILE, 3f, 0.5f);
			level.playSound(null, boss.blockPosition(), SoundEvents.TRIDENT_HIT, SoundSource.HOSTILE, 2f, 0.5f);
		}

		@Override
		protected boolean tick() {
			Vec3 side = new Vec3(-dir.z, 0, dir.x);
			Vec3 front = origin.add(dir.scale(1 + age * 1.2));
			DustParticleOptions blue = new DustParticleOptions(Realm.ABYSS.color, 2.2f);
			DustParticleOptions foam = new DustParticleOptions(Realm.ABYSS.accent, 1.6f);
			for (int s = -4; s <= 4; s++) {
				for (int h = 0; h < 4; h++) {
					Vec3 p = front.add(side.scale(s)).add(0, h * 0.7, 0);
					level.sendParticles(h == 3 ? foam : blue, p.x, p.y, p.z, 1, 0.2, 0.1, 0.2, 0);
				}
				Vec3 p = front.add(side.scale(s));
				level.sendParticles(ParticleTypes.SPLASH, p.x, p.y + 2.5, p.z, 3, 0.3, 0.2, 0.3, 0.1);
			}
			for (LivingEntity e : victims(level, boss, front, 5)) {
				Vec3 rel = e.position().subtract(front);
				if (Math.abs(rel.dot(dir)) < 1.5 && Math.abs(rel.dot(side)) < 4.5 && hit.add(e.getUUID())) {
					afflict(level, boss, Realm.ABYSS, e, 4f);
					e.push(dir.x * 2.2, 0.7, dir.z * 2.2);
					Compat.markHurt(e);
				}
			}
			return age >= 20;
		}
	}

	/** Paladin Solaire : des colonnes de lumière s'abattent sur les joueurs ; il se soigne au soleil. */
	static final class SolarJudgement extends TimedEffect {
		private final LivingEntity boss;
		private final List<Vec3> marks = new ArrayList<>();

		SolarJudgement(ServerLevel level, LivingEntity boss) {
			super(level);
			this.boss = boss;
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(boss) < 36 * 36 && !p.isCreative() && !p.isSpectator()) {
					marks.add(p.position());
					for (int i = 0; i < 2; i++) {
						marks.add(p.position().add(level.getRandom().nextGaussian() * 4, 0, level.getRandom().nextGaussian() * 4));
					}
				}
			}
			level.playSound(null, boss.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.5f, 0.6f);
		}

		@Override
		protected boolean tick() {
			for (Vec3 m : marks) {
				if (age < 30) {
					ring(level, new DustParticleOptions(Realm.SOLAR.color, 1.5f), m, 2.5 - age * 0.05, 0.1);
					if (age % 5 == 0) {
						level.sendParticles(ParticleTypes.END_ROD, m.x, m.y + 12, m.z, 3, 0.2, 2, 0.2, 0);
					}
				} else if (age == 30) {
					for (int y = 0; y < 24; y++) {
						level.sendParticles(ParticleTypes.END_ROD, m.x, m.y + y, m.z, 4, 0.5, 0.3, 0.5, 0.02);
						level.sendParticles(ParticleTypes.FIREWORK, m.x, m.y + y, m.z, 2, 0.6, 0.3, 0.6, 0.02);
					}
					level.sendParticles(ParticleTypes.EXPLOSION, m.x, m.y + 1, m.z, 1, 0, 0, 0, 0);
					for (LivingEntity e : victims(level, boss, m, 2.8)) {
						afflict(level, boss, Realm.SOLAR, e, 4.5f);
					}
				}
			}
			if (age == 30) {
				level.playSound(null, boss.blockPosition(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.HOSTILE, 3f, 0.5f);
				heal(boss, 0.03f);
			}
			return age >= 32;
		}
	}
}
