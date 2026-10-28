package com.diamondaltar.realms;

import com.diamondaltar.Compat;
import com.diamondaltar.effect.EffectManager;
import com.diamondaltar.effect.TimedEffect;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Événements des 7 Royaumes : portails, boss, sceaux d'invocation, bonus des armures légendaires
 * et pouvoirs de l'Épée des Sept Royaumes.
 */
public final class RealmEvents {
	private static final Map<UUID, Long> SWORD_COOLDOWN = new HashMap<>();
	private static final int SWORD_COOLDOWN_TICKS = 160;

	private RealmEvents() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			RealmPortals.tick(server);
			RealmTravel.tick(server);
			for (Realm r : Realm.values()) {
				ServerLevel level = server.getLevel(r.dimension);
				if (level != null) {
					RealmBoss.tick(level);
				}
			}
			if (server.getTickCount() % 20 == 0) {
				armorBonuses(server);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (RealmBoss.isBoss(entity) && entity.level() instanceof ServerLevel level) {
				RealmBoss.onDeath(level, entity);
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !armorCancels(entity, source));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) ->
				swordHit(entity, source, damageTaken));
		// Touche Y : puissance de l'Anéantissement Céleste.
		Compat.toServer().register(SwordPowerPayload.ID, SwordPowerPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(SwordPowerPayload.ID, (payload, context) -> {
			ItemStack held = context.player().getMainHandItem();
			if (held.is(RealmContent.SEVEN_REALMS_SWORD)) {
				switchSwordSetting(context.player(), held, payload.damage());
			}
		});
		// Interface de réglages (touche Y) : attaque, portée et dégâts d'un coup.
		Compat.toServer().register(SwordSettingsPayload.ID, SwordSettingsPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(SwordSettingsPayload.ID, (payload, context) -> {
			ItemStack held = context.player().getMainHandItem();
			if (held.is(RealmContent.SEVEN_REALMS_SWORD)) {
				com.diamondaltar.ModComponents.setSelected(held, Math.floorMod(payload.mode(), SWORD_MODES.length));
				held.set(com.diamondaltar.ModComponents.SWORD_POWER, SkyFx.clamp(payload.range()));
				held.set(com.diamondaltar.ModComponents.SWORD_DAMAGE, SkyFx.clampDamage(payload.damage()));
			}
		});
		// Toutes les recettes du mod sont débloquées dans le livre de recettes dès la connexion.
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				handler.getPlayer().awardRecipesByKey(modRecipes(server)));
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (player.isSpectator()) {
				return InteractionResult.PASS;
			}
			for (Realm r : Realm.values()) {
				if (stack.is(RealmContent.SIGIL[r.ordinal()])) {
					if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
						useSigil(sl, sp, stack, r);
					}
					return InteractionResult.SUCCESS;
				}
			}
			if (stack.is(RealmContent.REALM_PEARL)) {
				if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
					RealmTravel.usePearl(sl, sp, stack);
				}
				return InteractionResult.SUCCESS;
			}
			if (stack.is(RealmContent.SEVEN_REALMS_SWORD)) {
				if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
					useSword(sl, sp, stack);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	// ---------------------------------------------------------------- sceaux

	private static void useSigil(ServerLevel level, ServerPlayer player, ItemStack stack, Realm realm) {
		if (level.dimension() != realm.dimension && !level.dimension().equals(realm.dimension)) {
			Compat.overlay(player, Component.translatable("diamondaltar.sigil.wrong_realm", Component.translatable(realm.nameKey()))
					.withStyle(ChatFormatting.RED));
			return;
		}
		Vec3 pos = RealmBoss.groundAhead(level, player, 8);
		if (RealmBoss.bossNear(level, realm, pos)) {
			Compat.overlay(player, Component.translatable("diamondaltar.sigil.already").withStyle(ChatFormatting.RED));
			return;
		}
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		EffectManager.start(new Ritual(level, realm, pos));
	}

	/** Rituel d'invocation : une colonne de particules monte pendant 3 s, puis le boss apparaît. */
	static final class Ritual extends TimedEffect {
		private final Realm realm;
		private final Vec3 pos;

		Ritual(ServerLevel level, Realm realm, Vec3 pos) {
			super(level);
			this.realm = realm;
			this.pos = pos;
			level.playSound(null, BlockPos.containing(pos), SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 1.5f, 0.6f);
		}

		@Override
		protected boolean tick() {
			DustParticleOptions dust = new DustParticleOptions(realm.color, 2f);
			double r = 3.5 - age * 0.05;
			for (int k = 0; k < 4; k++) {
				double a = age * 0.25 + k * Math.PI / 2;
				level.sendParticles(dust, pos.x + Math.cos(a) * r, pos.y + age * 0.08, pos.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
			}
			level.sendParticles(RealmBoss.ambient(realm), pos.x, pos.y + 0.5, pos.z, 3, 0.4, 0.3, 0.4, 0.02);
			if (age % 20 == 0) {
				level.playSound(null, BlockPos.containing(pos), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 2f, 0.5f + age * 0.01f);
			}
			if (age >= 60) {
				RealmBoss.summon(level, realm, pos);
				return true;
			}
			return false;
		}
	}

	// ---------------------------------------------------------------- armures

	/** Le royaume de l'armure complète portée, ou null. */
	public static Realm fullSet(LivingEntity e) {
		Realm set = null;
		for (EquipmentSlot slot : RealmContent.ARMOR_SLOTS) {
			Realm r = RealmContent.armorRealm(e.getItemBySlot(slot).getItem());
			if (r == null || (set != null && r != set)) {
				return null;
			}
			set = r;
		}
		return set;
	}

	private static void armorBonuses(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer p : level.players()) {
				if (RealmContent.fullAetherite(p)) {
					// Armure en Aetherite : le meilleur de tous les royaumes.
					effect(p, MobEffects.STRENGTH, 2);
					effect(p, MobEffects.RESISTANCE, 1);
					effect(p, MobEffects.SPEED, 1);
					effect(p, MobEffects.REGENERATION, 1);
					effect(p, MobEffects.FIRE_RESISTANCE, 0);
					effect(p, MobEffects.WATER_BREATHING, 0);
					p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false));
					if (level.getGameTime() % 40 == 0) {
						level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 4, 0.4, 0.6, 0.4, 0.01);
					}
					continue;
				}
				Realm set = fullSet(p);
				if (set == null) {
					continue;
				}
				switch (set) {
					case EMBER -> {
						effect(p, MobEffects.FIRE_RESISTANCE, 0);
						effect(p, MobEffects.STRENGTH, 0);
					}
					case FROST -> {
						effect(p, MobEffects.SPEED, 1);
						effect(p, MobEffects.RESISTANCE, 0);
						p.setTicksFrozen(0);
					}
					case STORM -> {
						effect(p, MobEffects.SPEED, 2);
						effect(p, MobEffects.JUMP_BOOST, 2);
						effect(p, MobEffects.HASTE, 1);
					}
					case SYLVAN -> {
						effect(p, MobEffects.REGENERATION, 1);
						effect(p, MobEffects.SATURATION, 0);
						p.removeEffect(MobEffects.POISON);
					}
					case ABYSS -> {
						effect(p, MobEffects.WATER_BREATHING, 0);
						effect(p, MobEffects.DOLPHINS_GRACE, 1);
						effect(p, MobEffects.CONDUIT_POWER, 0);
						effect(p, MobEffects.STRENGTH, 1);
					}
					case SHADOW -> {
						p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false));
						p.removeEffect(MobEffects.DARKNESS);
						p.removeEffect(MobEffects.BLINDNESS);
						p.removeEffect(MobEffects.WITHER);
						effect(p, MobEffects.STRENGTH, 1);
						if (p.isShiftKeyDown()) {
							effect(p, MobEffects.INVISIBILITY, 0);
						}
					}
					case SOLAR -> {
						effect(p, MobEffects.RESISTANCE, 1);
						effect(p, MobEffects.REGENERATION, 0);
						effect(p, MobEffects.STRENGTH, 1);
						if (server.getTickCount() % 600 == 0) {
							p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 620, 3, true, false));
						}
					}
				}
				if (level.getGameTime() % 40 == 0) {
					level.sendParticles(new DustParticleOptions(set.color, 1.2f), p.getX(), p.getY() + 1, p.getZ(), 6, 0.4, 0.6, 0.4, 0);
				}
			}
		}
	}

	private static void effect(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier) {
		p.addEffect(new MobEffectInstance(effect, 60, amplifier, true, false));
	}

	/** Immunités des armures complètes : feu (Braise), gel (Givre), foudre et chute (Tempêtes), noyade (Abysses). */
	private static boolean armorCancels(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof Player)) {
			return false;
		}
		if (RealmContent.fullAetherite(entity)) {
			return source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_FREEZING)
					|| source.is(DamageTypeTags.IS_LIGHTNING) || source.is(DamageTypeTags.IS_DROWNING);
		}
		Realm set = fullSet(entity);
		if (set == null) {
			return false;
		}
		return switch (set) {
			case EMBER -> source.is(DamageTypeTags.IS_FIRE);
			case FROST -> source.is(DamageTypeTags.IS_FREEZING);
			case STORM -> source.is(DamageTypeTags.IS_LIGHTNING) || source.is(DamageTypeTags.IS_FALL);
			case ABYSS -> source.is(DamageTypeTags.IS_DROWNING);
			default -> false;
		};
	}

	// ---------------------------------------------------------------- Épée des Sept Royaumes

	/** Chaque coup d'épée porte la marque des 7 royaumes : feu, gel, poison, flétrissement, lumière… et parfois la foudre. */
	private static void swordHit(LivingEntity target, DamageSource source, float damageTaken) {
		if (!(target.level() instanceof ServerLevel level) || !(source.getEntity() instanceof Player player)
				|| source.getDirectEntity() != player || !player.getMainHandItem().is(RealmContent.SEVEN_REALMS_SWORD)
				|| damageTaken <= 0 || !target.isAlive()) {
			return;
		}
		target.igniteForSeconds(5);
		target.setTicksFrozen(target.getTicksRequiredToFreeze() + 80);
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
		target.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1));
		target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1));
		target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
		for (Realm r : Realm.values()) {
			double a = Math.PI * 2 * r.ordinal() / 7;
			level.sendParticles(new DustParticleOptions(r.color, 1.5f), target.getX() + Math.cos(a) * 0.8, target.getY(0.6),
					target.getZ() + Math.sin(a) * 0.8, 3, 0.05, 0.2, 0.05, 0);
		}
		if (level.getRandom().nextFloat() < 0.2f) {
			LightningBolt bolt = new LightningBolt(Compat.LIGHTNING_BOLT, level);
			bolt.setPos(target.getX(), target.getY(), target.getZ());
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
			float health = target.getHealth() - 8f;
			if (health <= 0) {
				target.kill(level);
			} else {
				target.setHealth(health);
			}
		}
	}

	/**
	 * Les attaques de l'épée (touche K pour changer) : 0 = Jugement des Sept Royaumes, 1 = Anéantissement Céleste,
	 * 2 = Lance de Jupiter, 3 = Effondrement Stellaire. Les trois dernières se règlent avec Y (portée et puissance).
	 */
	public static final String[] SWORD_MODES = {"judgement", "annihilation", "jupiter", "collapse"};
	/** Niveaux par défaut : portée 75 blocs, 100 dégâts. */
	public static final int DEFAULT_LEVEL = 4;

	public static int swordMode(ItemStack stack) {
		return Math.floorMod(com.diamondaltar.ModComponents.getSelected(stack), SWORD_MODES.length);
	}

	/** Touche K avec l'épée en main : attaque suivante. */
	public static void switchSwordMode(ServerPlayer player, ItemStack stack) {
		int next = (swordMode(stack) + 1) % SWORD_MODES.length;
		com.diamondaltar.ModComponents.setSelected(stack, next);
		Compat.overlay(player, Component.translatable("diamondaltar.seven_realms.selected",
				Component.translatable("diamondaltar.seven_realms." + SWORD_MODES[next] + ".name").withStyle(ChatFormatting.GOLD),
				next + 1, SWORD_MODES.length).withStyle(ChatFormatting.AQUA));
		player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.2f + next * 0.3f);
	}

	/** Niveau de portée (1 à 11 : 10 à 1000 blocs). */
	public static int swordRange(ItemStack stack) {
		Integer p = stack.get(com.diamondaltar.ModComponents.SWORD_POWER);
		return SkyFx.clamp(p == null ? DEFAULT_LEVEL : p);
	}

	/** Niveau de dégâts (1 à 7 : 10 à 1000). */
	public static int swordDamage(ItemStack stack) {
		Integer p = stack.get(com.diamondaltar.ModComponents.SWORD_DAMAGE);
		return SkyFx.clampDamage(p == null ? DEFAULT_LEVEL : p);
	}

	/** Touche Y : portée suivante ; touche U : dégâts suivants (on revient au début après le maximum). */
	public static void switchSwordSetting(ServerPlayer player, ItemStack stack, boolean damage) {
		int max = damage ? SkyFx.DAMAGES.length : SkyFx.RANGES.length;
		int next = (damage ? swordDamage(stack) : swordRange(stack)) % max + 1;
		stack.set(damage ? com.diamondaltar.ModComponents.SWORD_DAMAGE : com.diamondaltar.ModComponents.SWORD_POWER, next);
		Component bar = Component.literal("■".repeat(next) + "□".repeat(max - next)).withStyle(next == max ? ChatFormatting.RED : ChatFormatting.GOLD);
		Compat.overlay(player, Component.translatable(damage ? "diamondaltar.seven_realms.damage" : "diamondaltar.seven_realms.range",
				bar, damage ? SkyFx.damage(next) : SkyFx.range(next)).withStyle(ChatFormatting.AQUA));
		player.level().playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.8f, 0.5f + next * 0.15f);
	}

	private static void useSword(ServerLevel level, ServerPlayer player, ItemStack stack) {
		int mode = swordMode(stack);
		UUID key = new UUID(player.getUUID().getMostSignificantBits(), player.getUUID().getLeastSignificantBits() ^ mode);
		Long until = SWORD_COOLDOWN.get(key);
		if (until != null && level.getGameTime() < until && !player.getAbilities().instabuild) {
			Compat.overlay(player, Component.translatable("diamondaltar.seven_realms.cooldown",
					Component.translatable("diamondaltar.seven_realms." + SWORD_MODES[mode] + ".name"),
					String.format("%.1f", (until - level.getGameTime()) / 20.0)).withStyle(ChatFormatting.GRAY));
			return;
		}
		int range = swordRange(stack);
		int damage = swordDamage(stack);
		switch (mode) {
			case 1 -> {
				SWORD_COOLDOWN.put(key, level.getGameTime() + CelestialAnnihilation.cooldown(range));
				EffectManager.start(new CelestialAnnihilation(level, player, range, damage));
			}
			case 2 -> {
				SWORD_COOLDOWN.put(key, level.getGameTime() + JupiterSpear.cooldown(range));
				EffectManager.start(new JupiterSpear(level, player, range, damage));
			}
			case 3 -> {
				SWORD_COOLDOWN.put(key, level.getGameTime() + StellarCollapse.cooldown(range));
				EffectManager.start(new StellarCollapse(level, player, range, damage));
			}
			default -> {
				SWORD_COOLDOWN.put(key, level.getGameTime() + SWORD_COOLDOWN_TICKS);
				EffectManager.start(new SevenRealmsJudgement(level, player));
			}
		}
	}

	/**
	 * « Jugement des Sept Royaumes » : sept colonnes, une par royaume, apparaissent autour du joueur
	 * puis explosent ensemble ; chacune frappe les ennemis proches avec le pouvoir de son royaume.
	 */
	static final class SevenRealmsJudgement extends TimedEffect {
		private static final int CHARGE = 24;
		private final ServerPlayer player;
		private final Vec3[] pillars = new Vec3[7];

		SevenRealmsJudgement(ServerLevel level, ServerPlayer player) {
			super(level);
			this.player = player;
			Vec3 c = player.position();
			for (int i = 0; i < 7; i++) {
				double a = Math.PI * 2 * i / 7 + Math.toRadians(player.getYRot());
				pillars[i] = c.add(Math.cos(a) * 5, 0, Math.sin(a) * 5);
			}
			level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2f, 1.4f);
		}

		@Override
		protected boolean tick() {
			if (!player.isAlive()) {
				return true;
			}
			for (int i = 0; i < 7; i++) {
				if (age < i * 3) {
					continue;
				}
				Realm r = Realm.values()[i];
				Vec3 p = pillars[i];
				DustParticleOptions dust = new DustParticleOptions(r.color, 2.2f);
				if (age < CHARGE) {
					int h = Math.min(12, (age - i * 3) * 2);
					for (int y = 0; y < h; y += 2) {
						level.sendParticles(dust, p.x, p.y + y, p.z, 1, 0.15, 0.3, 0.15, 0);
					}
					if (age == i * 3) {
						level.playSound(null, BlockPos.containing(p), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2f, 0.6f + i * 0.15f);
					}
				} else if (age == CHARGE) {
					for (int y = 0; y < 14; y++) {
						level.sendParticles(dust, p.x, p.y + y, p.z, 4, 0.4, 0.3, 0.4, 0);
						level.sendParticles(RealmBoss.ambient(r), p.x, p.y + y, p.z, 2, 0.4, 0.3, 0.4, 0.05);
					}
					level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y + 1, p.z, 2, 0.5, 0.5, 0.5, 0);
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(p, 9, 8, 9),
							e -> e.isAlive() && !(e instanceof Player) && e.distanceToSqr(p) < 4.5 * 4.5)) {
						e.hurtServer(level, level.damageSources().playerAttack(player), 14f);
						strikeEffect(level, r, e);
					}
				}
			}
			if (age == CHARGE) {
				level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 3f, 0.6f);
				level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.5f, 1.2f);
			}
			return age >= CHARGE + 2;
		}

		private static void strikeEffect(ServerLevel level, Realm r, LivingEntity e) {
			switch (r) {
				case EMBER -> e.igniteForSeconds(6);
				case FROST -> e.setTicksFrozen(e.getTicksRequiredToFreeze() + 140);
				case STORM -> {
					LightningBolt bolt = new LightningBolt(Compat.LIGHTNING_BOLT, level);
					bolt.setPos(e.getX(), e.getY(), e.getZ());
					bolt.setVisualOnly(true);
					level.addFreshEntity(bolt);
				}
				case SYLVAN -> e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 4));
				case ABYSS -> {
					e.push(0, 1.0, 0);
					Compat.markHurt(e);
				}
				case SHADOW -> e.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 2));
				case SOLAR -> e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
			}
		}
	}

	// ---------------------------------------------------------------- auto-test

	/** Auto-test des 7 Royaumes (lancé par l'auto-test de la Dimension Astrale). */
	public static boolean selfTest(MinecraftServer server, StringBuilder report) {
		boolean ok = true;
		for (Realm r : Realm.values()) {
			int i = r.ordinal();
			ServerLevel level = server.getLevel(r.dimension);
			if (level == null) {
				report.append("FAIL royaume ").append(r.id).append(" : dimension introuvable\n");
				ok = false;
				continue;
			}
			int solid = 0;
			int ores = 0;
			int decor = 0;
			for (int cx = -3; cx <= 3; cx++) {
				for (int cz = -3; cz <= 3; cz++) {
					level.getChunk(cx, cz);
					for (int x = 0; x < 16; x += 2) {
						for (int z = 0; z < 16; z += 2) {
							int wx = cx * 16 + x;
							int wz = cz * 16 + z;
							int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, wx, wz);
							for (int y = level.getMinY(); y < top; y++) {
								var s = level.getBlockState(new BlockPos(wx, y, wz));
								if (s.is(RealmContent.STONE[i]) || s.is(RealmContent.SOIL[i])) {
									solid++;
								} else if (s.is(RealmContent.ORE[i])) {
									ores++;
								} else if (!s.isAir()) {
									decor++;
								}
							}
						}
					}
				}
			}
			boolean terrain = solid > 0;
			report.append(terrain ? "OK" : "FAIL").append(" royaume ").append(r.id).append(" : ").append(solid)
					.append(" blocs du royaume, ").append(ores).append(" minerais, ").append(decor).append(" blocs de decor\n");
			ok &= terrain;
			// Les sanctuaires : on cherche les autels à la surface, sur 9 × 9 tronçons (environ 10 attendus).
			int shrines = 0;
			int ticking = 0;
			for (int x = -64; x < 80; x++) {
				for (int z = -64; z < 80; z++) {
					BlockPos top = new BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
					if (level.getBlockState(top).is(RealmContent.REALM_ALTAR)) {
						shrines++;
						if (level.getBlockEntity(top) instanceof RealmAltarBlockEntity) {
							ticking++;
						}
					}
				}
			}
			report.append(shrines > 0 && ticking == shrines ? "OK" : "WARN").append(" sanctuaires du boss ").append(r.id).append(" : ")
					.append(shrines).append(" autel(s) dans 81 troncons, ").append(ticking).append(" actif(s)\n");
			if (ores == 0) {
				report.append("WARN aucun minerai de ").append(r.ore).append(" echantillonne\n");
			}
			BlockPos portal = RealmPortals.buildPortal(level, 3000, 3000, r);
			var st = level.getBlockState(portal);
			boolean portalOk = st.is(RealmContent.REALM_PORTAL) && st.getValue(RealmPortalBlock.REALM) == i;
			report.append(portalOk ? "OK" : "FAIL").append(" portail du royaume ").append(r.id).append('\n');
			ok &= portalOk;
			// Arène de pierre sous le boss (sinon, au-dessus du vide, il tombe avec son butin).
			// (Près de l'origine : ces chunks ont été chargés par l'analyse du terrain, les entités y existent vraiment.)
			int groundY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 24, 24);
			BlockPos floor = new BlockPos(24, groundY <= level.getMinY() + 2 || groundY > level.getMaxY() - 12 ? 100 : groundY, 24);
			for (int dx = -6; dx <= 6; dx++) {
				for (int dz = -6; dz <= 6; dz++) {
					level.setBlockAndUpdate(floor.offset(dx, 0, dz), RealmContent.STONE[i].defaultBlockState());
					for (int dy = 1; dy <= 8; dy++) {
						level.setBlockAndUpdate(floor.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
					}
				}
			}
			Vec3 arena = Vec3.atBottomCenterOf(floor.above());
			LivingEntity boss = RealmBoss.summon(level, r, arena);
			boolean bossOk = boss != null && RealmBoss.isBoss(boss) && RealmBoss.realmOf(boss) == r
					&& boss.getMaxHealth() >= RealmBoss.maxHealth(r) - 0.5f;
			report.append(bossOk ? "OK" : "FAIL").append(" boss ").append(r.id).append(" : ")
					.append(boss == null ? "absent" : boss.getName().getString() + " PV=" + boss.getMaxHealth()).append('\n');
			ok &= bossOk;
			if (boss != null) {
				for (int k = 0; k < 160; k++) {
					RealmBoss.tick(level);
					EffectManager.tickAll();
				}
				ServerPlayer target = RealmBoss.nearest(level, boss, 64);
				RealmBoss.signature(level, boss, r, target != null ? target : fakeTarget(level));
				for (int k = 0; k < 120; k++) {
					EffectManager.tickAll();
				}
				RealmBoss.onDeath(level, boss);
				long shards = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, boss.getBoundingBox().inflate(8),
						e -> e.getItem().is(RealmContent.SHARD[i])).size();
				report.append(shards > 0 ? "OK" : "FAIL").append(" butin du boss ").append(r.id).append(" : eclat (boss en ")
						.append(boss.blockPosition()).append(")\n");
				ok &= shards > 0;
				boss.discard();
			}
		}
		// Récompenses : plus le boss est fort, plus le butin l'est.
		var rnd = net.minecraft.util.RandomSource.create(42);
		var solar = RealmRewards.roll(Realm.SOLAR, rnd);
		var ember = RealmRewards.roll(Realm.EMBER, rnd);
		java.util.function.BiFunction<java.util.List<ItemStack>, net.minecraft.world.item.Item, Integer> count = (list, item) ->
				list.stream().filter(st -> st.is(item)).mapToInt(ItemStack::getCount).sum();
		int diamondGear = 0;
		for (var item : java.util.List.of(net.minecraft.world.item.Items.DIAMOND_SWORD, net.minecraft.world.item.Items.DIAMOND_PICKAXE,
				net.minecraft.world.item.Items.DIAMOND_AXE, net.minecraft.world.item.Items.DIAMOND_HELMET, net.minecraft.world.item.Items.DIAMOND_CHESTPLATE,
				net.minecraft.world.item.Items.DIAMOND_LEGGINGS, net.minecraft.world.item.Items.DIAMOND_BOOTS)) {
			diamondGear += count.apply(solar, item);
		}
		boolean rewards = diamondGear == 7 && count.apply(solar, RealmContent.AETHERITE_INGOT) >= 2
				&& count.apply(solar, RealmContent.DAWN_WINGS) == 1 && count.apply(ember, RealmContent.AETHERITE_INGOT) == 0;
		report.append(rewards ? "OK" : "FAIL").append(" butin du Paladin Solaire : ").append(diamondGear).append(" pieces en diamant, ")
				.append(count.apply(solar, RealmContent.AETHERITE_INGOT)).append(" lingots d'Aetherite, ailes ; Braise : pas d'Aetherite\n");
		ok &= rewards;
		boolean glider = new ItemStack(RealmContent.DAWN_WINGS).has(net.minecraft.core.component.DataComponents.GLIDER);
		boolean pick = new ItemStack(RealmContent.AETHERITE_PICKAXE).isCorrectToolForDrops(net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState());
		report.append(glider && pick ? "OK" : "FAIL").append(" ailes de l'Aurore (planeur) : ").append(glider)
				.append(", pioche en Aetherite mine l'obsidienne : ").append(pick).append('\n');
		ok &= glider && pick;
		ServerLevel solarLevel = server.getLevel(Realm.SOLAR.dimension);
		if (solarLevel != null) {
			BlockPos spot = RealmTravel.safeSpot(solarLevel, 5000, 5000, 2 + Realm.SOLAR.ordinal());
			boolean safe = !solarLevel.getBlockState(spot.below()).isAir() && solarLevel.getBlockState(spot).isAir();
			report.append(safe ? "OK" : "FAIL").append(" Perle des Royaumes : arrivee sure en ").append(spot).append('\n');
			ok &= safe;
		}
		ok &= testAnnihilation(server, report);
		ok &= testAllCrafts(server, report);
		var learner = net.fabricmc.fabric.api.entity.FakePlayer.get(server.overworld());
		var recipeKeys = modRecipes(server);
		learner.awardRecipesByKey(recipeKeys);
		boolean known = recipeKeys.size() > 60 && learner.getRecipeBook().contains(net.minecraft.resources.ResourceKey.create(
				net.minecraft.core.registries.Registries.RECIPE, com.diamondaltar.DiamondAltar.id("ember_portal_frame")));
		report.append(known ? "OK" : "FAIL").append(" livre de recettes : ").append(recipeKeys.size()).append(" recettes debloquees\n");
		ok &= known;
		// Recette de l'épée : les 7 éclats + une épée en netherite + une étoile du Nether.
		var recipes = server.getRecipeManager();
		java.util.List<ItemStack> grid = new java.util.ArrayList<>();
		for (int k = 0; k < 3; k++) {
			grid.add(new ItemStack(RealmContent.SHARD[k]));
		}
		grid.add(new ItemStack(RealmContent.SHARD[3]));
		grid.add(new ItemStack(net.minecraft.world.item.Items.NETHERITE_SWORD));
		grid.add(new ItemStack(RealmContent.SHARD[4]));
		grid.add(new ItemStack(RealmContent.SHARD[5]));
		grid.add(new ItemStack(net.minecraft.world.item.Items.NETHER_STAR));
		grid.add(new ItemStack(RealmContent.SHARD[6]));
		var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, grid);
		var recipe = recipes.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, server.overworld());
		boolean sword = recipe.isPresent() && Compat.assemble(recipe.get().value(), input, server.registryAccess()).is(RealmContent.SEVEN_REALMS_SWORD);
		report.append(sword ? "OK" : "FAIL").append(" craft de l'Epee des Sept Royaumes\n");
		ok &= sword;
		// Cadre de portail de Braise : 3 blocs de fer en bas, un diamant au milieu, magma autour, bâton de blaze en haut.
		ItemStack magma = new ItemStack(net.minecraft.world.item.Items.MAGMA_BLOCK);
		var frameInput = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, java.util.List.of(
				magma, new ItemStack(net.minecraft.world.item.Items.BLAZE_ROD), magma,
				magma, new ItemStack(net.minecraft.world.item.Items.DIAMOND), magma,
				new ItemStack(net.minecraft.world.item.Items.IRON_BLOCK), new ItemStack(net.minecraft.world.item.Items.IRON_BLOCK),
				new ItemStack(net.minecraft.world.item.Items.IRON_BLOCK)));
		var frameRecipe = recipes.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, frameInput, server.overworld());
		boolean frameCraft = frameRecipe.isPresent()
				&& Compat.assemble(frameRecipe.get().value(), frameInput, server.registryAccess()).is(RealmContent.PORTAL_FRAME[0].asItem());
		report.append(frameCraft ? "OK" : "FAIL").append(" craft du Cadre de Portail de Braise\n");
		ok &= frameCraft;
		// Allumage d'un cadre de Braise : la Clé de Givre ne marche pas, la Clé de Braise oui.
		ServerLevel overworld = server.overworld();
		BlockPos base = new BlockPos(40, 150, 40);
		for (int dx = -1; dx <= 2; dx++) {
			for (int dy = -1; dy <= 3; dy++) {
				boolean edge = dx == -1 || dx == 2 || dy == -1 || dy == 3;
				overworld.setBlockAndUpdate(base.offset(dx, dy, 0), edge ? RealmContent.PORTAL_FRAME[0].defaultBlockState()
						: net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
			}
		}
		boolean wrong = RealmPortals.tryIgnite(overworld, base, Realm.FROST);
		boolean lit = !wrong && RealmPortals.tryIgnite(overworld, base, Realm.EMBER) && overworld.getBlockState(base).is(RealmContent.REALM_PORTAL);
		report.append(lit ? "OK" : "FAIL").append(" cadre de Braise : refuse la Cle de Givre, s'allume avec la Cle de Braise\n");
		ok &= lit;
		// Le portail de retour est construit avec le cadre du royaume.
		ServerLevel frost = server.getLevel(Realm.FROST.dimension);
		if (frost != null) {
			BlockPos back = RealmPortals.buildPortal(frost, 3100, 3100, Realm.FROST);
			boolean frame = frost.getBlockState(back.below()).is(RealmContent.PORTAL_FRAME[Realm.FROST.ordinal()]);
			report.append(frame ? "OK" : "FAIL").append(" portail de retour en Cadres de Portail de Givre\n");
			ok &= frame;
		}
		return ok;
	}

	/** Anéantissement Céleste : des squelettes à 10, 30 et 45 blocs doivent tous être touchés. */
	private static boolean testAnnihilation(MinecraftServer server, StringBuilder report) {
		ServerLevel level = server.getLevel(Realm.SOLAR.dimension);
		if (level == null) {
			return false;
		}
		// Deux dalles en plein ciel (au-dessus des îles), bien dégagées : une pour le laser, une pour la lance.
		int y = Math.min(215, level.getMaxY() - 40);
		for (int row : new int[]{8, 40, -24}) {
			for (int x = 0; x <= 60; x++) {
				for (int dz = -2; dz <= 2; dz++) {
					level.setBlockAndUpdate(new BlockPos(x, y - 1, row + dz), RealmContent.STONE[Realm.SOLAR.ordinal()].defaultBlockState());
					for (int dy = 0; dy < 30; dy++) {
						level.setBlockAndUpdate(new BlockPos(x, y + dy, row + dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		var caster = net.fabricmc.fabric.api.entity.FakePlayer.get(level);
		caster.setPos(8.5, y, 8.5);
		java.util.List<LivingEntity> targets = new java.util.ArrayList<>();
		for (int d : new int[]{10, 30, 45}) {
			var mob = Compat.SKELETON.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
			if (mob != null) {
				mob.setPos(8.5 + d, y, 8.5);
				level.addFreshEntity(mob);
				targets.add(mob);
			}
		}
		// Le joueur regarde ses pieds : le laser doit tirer sur le bloc visé (la dalle juste en dessous).
		caster.setXRot(90);
		CelestialAnnihilation effect = new CelestialAnnihilation(level, caster, DEFAULT_LEVEL, DEFAULT_LEVEL);
		EffectManager.start(effect);
		for (int i = 0; i < 120; i++) {
			EffectManager.tickAll();
		}
		long dead = targets.stream().filter(e -> !e.isAlive() || e.getHealth() < e.getMaxHealth()).count();
		BlockPos impact = BlockPos.containing(effect.impactPoint());
		boolean aimed = Math.abs(impact.getX() - 8) <= 1 && Math.abs(impact.getZ() - 8) <= 1 && Math.abs(impact.getY() - y) <= 1;
		int carvedBlocks = 0;
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				if (level.getBlockState(impact.offset(dx, -2, dz)).isAir()) {
					carvedBlocks++;
				}
			}
		}
		boolean crater = carvedBlocks >= 40;
		report.append(aimed && crater ? "OK" : "FAIL").append(" Aneantissement Celeste : tir sur le bloc vise ").append(impact)
				.append(" (dalle en y=").append(y - 1).append("), cratere ").append(carvedBlocks).append("/49\n");
		boolean ok = aimed && crater && effect.hitCount() >= targets.size() && dead == targets.size() && !targets.isEmpty();
		report.append(ok ? "OK" : "FAIL").append(" Aneantissement Celeste (portee 75, 100 degats) : ").append(effect.hitCount()).append(" touche(s), ")
				.append(dead).append("/").append(targets.size()).append(" squelettes a 10, 30 et 45 blocs blesses ou morts\n");
		// Lance de Jupiter : de nouveaux squelettes, la lance tombe sur le bloc visé.
		targets.forEach(e -> e.discard());
		targets.clear();
		for (int d : new int[]{12, 40}) {
			var mob = Compat.SKELETON.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
			if (mob != null) {
				mob.setPos(8.5 + d, y, 40.5);
				level.addFreshEntity(mob);
				targets.add(mob);
			}
		}
		caster.setPos(8.5, y, 40.5);
		caster.setXRot(90);
		JupiterSpear spear = new JupiterSpear(level, caster, DEFAULT_LEVEL, DEFAULT_LEVEL);
		EffectManager.start(spear);
		for (int i = 0; i < 170; i++) {
			EffectManager.tickAll();
		}
		long spearHit = targets.stream().filter(e -> !e.isAlive() || e.getHealth() < e.getMaxHealth()).count();
		boolean spearOk = spear.hitCount() >= targets.size() && spearHit == targets.size() && !targets.isEmpty();
		report.append(spearOk ? "OK" : "FAIL").append(" Lance de Jupiter : impact en ").append(BlockPos.containing(spear.impactPoint()))
				.append(", ").append(spearHit).append("/").append(targets.size()).append(" squelettes touches\n");
		ok &= spearOk;
		// Effondrement Stellaire : le trou noir aspire puis la supernova pulvérise.
		targets.forEach(e -> e.discard());
		targets.clear();
		for (int d : new int[]{15, 45}) {
			var mob = Compat.SKELETON.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
			if (mob != null) {
				mob.setPos(8.5 + d, y, -23.5);
				level.addFreshEntity(mob);
				targets.add(mob);
			}
		}
		caster.setPos(8.5, y, -23.5);
		caster.setXRot(90);
		StellarCollapse collapse = new StellarCollapse(level, caster, DEFAULT_LEVEL, DEFAULT_LEVEL);
		EffectManager.start(collapse);
		for (int i = 0; i < 190; i++) {
			EffectManager.tickAll();
		}
		long collapseHit = targets.stream().filter(e -> !e.isAlive() || e.getHealth() < e.getMaxHealth()).count();
		boolean collapseOk = collapse.hitCount() >= targets.size() && collapseHit == targets.size() && !targets.isEmpty();
		report.append(collapseOk ? "OK" : "FAIL").append(" Effondrement Stellaire : impact en ").append(BlockPos.containing(collapse.impactPoint()))
				.append(", ").append(collapseHit).append("/").append(targets.size()).append(" squelettes aspires et pulverises, ")
				.append(collapse.hitCount()).append(" touche(s), ").append(collapse.debug());
		for (LivingEntity e : targets) {
			report.append(" [").append(e.blockPosition()).append(" vivant=").append(e.isAlive()).append(" retire=").append(e.isRemoved())
					.append(" PV=").append(e.getHealth()).append("]");
		}
		report.append('\n');
		ok &= collapseOk;
		targets.forEach(e -> e.discard());
		return ok;
	}

	/** Les clés de toutes les recettes du mod (fichiers data/diamondaltar/recipe/*.json). */
	public static java.util.List<net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>>> modRecipes(MinecraftServer server) {
		java.util.List<net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>>> keys = new java.util.ArrayList<>();
		for (var id : server.getResourceManager().listResources("recipe",
				i -> i.getNamespace().equals(com.diamondaltar.DiamondAltar.MOD_ID) && i.getPath().endsWith(".json")).keySet()) {
			String path = id.getPath().substring("recipe/".length(), id.getPath().length() - ".json".length());
			keys.add(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, com.diamondaltar.DiamondAltar.id(path)));
		}
		return keys;
	}

	/**
	 * Essaie TOUTES les recettes de table de craft du mod : pour chaque fichier de recette, on pose les ingrédients
	 * sur une table de craft comme un joueur le ferait et on vérifie que le bon objet sort.
	 */
	private static boolean testAllCrafts(MinecraftServer server, StringBuilder report) {
		int tested = 0;
		java.util.List<String> broken = new java.util.ArrayList<>();
		var files = server.getResourceManager().listResources("recipe",
				id -> id.getNamespace().equals(com.diamondaltar.DiamondAltar.MOD_ID) && id.getPath().endsWith(".json"));
		for (var entry : files.entrySet()) {
			String name = entry.getKey().getPath();
			try (var reader = entry.getValue().openAsReader()) {
				var json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
				String type = json.get("type").getAsString();
				java.util.List<ItemStack> grid = new java.util.ArrayList<>();
				int w;
				int h;
				if (type.equals("minecraft:crafting_shaped")) {
					var pattern = json.getAsJsonArray("pattern");
					var key = json.getAsJsonObject("key");
					h = pattern.size();
					w = pattern.get(0).getAsString().length();
					for (var row : pattern) {
						String line = row.getAsString();
						for (int c = 0; c < w; c++) {
							char ch = c < line.length() ? line.charAt(c) : ' ';
							grid.add(ch == ' ' ? ItemStack.EMPTY : stackOf(key.get(String.valueOf(ch)).getAsString()));
						}
					}
				} else if (type.equals("minecraft:crafting_shapeless")) {
					for (var ing : json.getAsJsonArray("ingredients")) {
						grid.add(stackOf(ing.getAsString()));
					}
					w = 3;
					h = 3;
					while (grid.size() < 9) {
						grid.add(ItemStack.EMPTY);
					}
				} else {
					continue;
				}
				tested++;
				String expected = json.getAsJsonObject("result").get("id").getAsString();
				var input = net.minecraft.world.item.crafting.CraftingInput.of(w, h, grid);
				var found = server.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, server.overworld());
				ItemStack out = found.isPresent() ? Compat.assemble(found.get().value(), input, server.registryAccess()) : ItemStack.EMPTY;
				String got = out.isEmpty() ? "rien" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(out.getItem()).toString();
				if (!got.equals(expected)) {
					broken.add(name + " -> " + got);
				}
			} catch (Exception e) {
				broken.add(name + " : " + e);
			}
		}
		boolean ok = broken.isEmpty() && tested > 50;
		report.append(ok ? "OK" : "FAIL").append(" toutes les recettes de craft : ").append(tested).append(" testees, ")
				.append(broken.size()).append(" cassees ").append(broken).append('\n');
		return ok;
	}

	private static ItemStack stackOf(String id) {
		return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(id)));
	}

	/** Un joueur factice comme cible des attaques pendant l'auto-test (aucun vrai joueur sur le serveur). */
	private static ServerPlayer fakeTarget(ServerLevel level) {
		return net.fabricmc.fabric.api.entity.FakePlayer.get(level);
	}
}
