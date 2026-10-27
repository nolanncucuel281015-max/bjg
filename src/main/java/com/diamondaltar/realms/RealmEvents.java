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
					useSword(sl, sp);
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

	private static void useSword(ServerLevel level, ServerPlayer player) {
		Long until = SWORD_COOLDOWN.get(player.getUUID());
		if (until != null && level.getGameTime() < until) {
			Compat.overlay(player, Component.translatable("diamondaltar.seven_realms.cooldown",
					String.format("%.1f", (until - level.getGameTime()) / 20.0)).withStyle(ChatFormatting.GRAY));
			return;
		}
		SWORD_COOLDOWN.put(player.getUUID(), level.getGameTime() + SWORD_COOLDOWN_TICKS);
		EffectManager.start(new SevenRealmsJudgement(level, player));
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

	/** Un joueur factice comme cible des attaques pendant l'auto-test (aucun vrai joueur sur le serveur). */
	private static ServerPlayer fakeTarget(ServerLevel level) {
		return net.fabricmc.fabric.api.entity.FakePlayer.get(level);
	}
}
