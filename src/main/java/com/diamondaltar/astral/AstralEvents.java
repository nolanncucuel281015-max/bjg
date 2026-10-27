package com.diamondaltar.astral;

import com.diamondaltar.Compat;
import com.diamondaltar.DiamondAltar;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Événements de la Dimension Astrale : portail, Gardien, et un auto-test pour la compilation. */
public final class AstralEvents {
	public static final TagKey<Structure> ASTRAL_STRUCTURES = TagKey.create(Registries.STRUCTURE, DiamondAltar.id("astral_structures"));

	private AstralEvents() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			AstralPortals.tick(server);
			ServerLevel astral = server.getLevel(AstralContent.DIMENSION);
			if (astral != null) {
				Guardian.tick(astral);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (Guardian.isGuardian(entity) && entity.level() instanceof ServerLevel level) {
				Guardian.onDeath(level, entity);
			}
			if (entity.level() instanceof ServerLevel level) {
				AstralMobs.onDeath(level, entity, source);
			}
		});
		AstralMobs.register();
		// Chaque joueur reçoit le manuel et le Guide des Portails la première fois qu'il rejoint le monde.
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			var player = handler.getPlayer();
			if (player.addTag("diamondaltar_got_manual")) {
				player.getInventory().add(new net.minecraft.world.item.ItemStack(AstralContent.MANUAL));
			}
			if (player.addTag("diamondaltar_got_portal_guide")) {
				player.getInventory().add(new net.minecraft.world.item.ItemStack(com.diamondaltar.realms.RealmContent.PORTAL_GUIDE));
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) ->
				Guardian.afterDamage(entity, damageTaken));
		if (Boolean.getBoolean("diamondaltar.selftest")) {
			ServerLifecycleEvents.SERVER_STARTED.register(AstralEvents::selfTest);
		}
	}

	/**
	 * Auto-test lancé par la compilation GitHub (serveur sans joueur) : charge la dimension,
	 * génère du terrain, cherche les structures et construit un portail, puis arrête le serveur.
	 */
	private static void selfTest(MinecraftServer server) {
		StringBuilder report = new StringBuilder("\n===== DIAMONDALTAR SELFTEST =====\n");
		boolean ok = true;
		try {
			ServerLevel astral = server.getLevel(AstralContent.DIMENSION);
			if (astral == null) {
				report.append("FAIL dimension astralis introuvable\n");
				ok = false;
			} else {
				report.append("OK dimension chargee, minY=").append(astral.getMinY()).append(" maxY=").append(astral.getMaxY()).append('\n');
				int solid = 0;
				int ores = 0;
				int trees = 0;
				int rocks = 0;
				for (int cx = -4; cx <= 4; cx++) {
					for (int cz = -4; cz <= 4; cz++) {
						astral.getChunk(cx, cz);
						for (int x = 0; x < 16; x += 2) {
							for (int z = 0; z < 16; z += 2) {
								int wx = cx * 16 + x;
								int wz = cz * 16 + z;
								int top = astral.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
								for (int y = astral.getMinY(); y < top; y += 1) {
									BlockState s = astral.getBlockState(new BlockPos(wx, y, wz));
									if (!s.isAir()) {
										solid++;
									}
									if (s.is(AstralContent.LUNARITE_ORE) || s.is(AstralContent.ASTRALITE_ORE)) {
										ores++;
									}
									if (s.is(AstralContent.ASTRAL_LOG) || s.is(AstralContent.ASTRAL_LEAVES)) {
										trees++;
									}
									if (s.is(net.minecraft.world.level.block.Blocks.CALCITE)) {
										rocks++;
									}
								}
							}
						}
					}
				}
				report.append(solid > 0 ? "OK" : "FAIL").append(" terrain genere : ").append(solid).append(" blocs solides echantillonnes, ").append(ores).append(" minerais\n");
				ok &= solid > 0;
				report.append(trees > 0 ? "OK" : "WARN").append(" arbres astraux : ").append(trees).append(" blocs de bois/feuilles, rochers : ")
						.append(rocks).append(" blocs de calcite\n");
				BlockState surface = astral.getBlockState(new BlockPos(0, astral.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0) - 1, 0));
				report.append("INFO bloc de surface en 0,0 : ").append(surface).append('\n');
				BlockPos found = astral.findNearestMapStructure(ASTRAL_STRUCTURES, BlockPos.ZERO, 50, false);
				report.append(found != null ? "OK" : "WARN").append(" structure astrale la plus proche : ").append(found).append('\n');
				BlockPos portal = AstralPortals.buildPortal(astral, 2000, 2000);
				boolean portalOk = astral.getBlockState(portal).is(AstralContent.ASTRAL_PORTAL);
				report.append(portalOk ? "OK" : "FAIL").append(" portail de retour construit en ").append(portal).append('\n');
				ok &= portalOk;
			}
			if (astral != null) {
				// Le château est-il vraiment construit ? (on cherche l'Autel du Gardien autour)
				var structures = astral.registryAccess().lookupOrThrow(Registries.STRUCTURE);
				var castleKey = net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, DiamondAltar.id("astral_castle"));
				var castle = structures.get(castleKey).orElse(null);
				if (castle != null) {
					var hit = astral.getChunkSource().getGenerator().findNearestMapStructure(astral,
							net.minecraft.core.HolderSet.direct(castle), BlockPos.ZERO, 100, false);
					if (hit != null) {
						BlockPos c = hit.getFirst();
						int altars = 0;
						int altarY = 0;
						int bricks = 0;
						for (int dx = -48; dx <= 48; dx++) {
							for (int dz = -48; dz <= 48; dz++) {
								for (int y = astral.getMinY(); y < astral.getMaxY(); y++) {
									BlockState st = astral.getBlockState(new BlockPos(c.getX() + dx, y, c.getZ() + dz));
									if (st.is(AstralContent.GUARDIAN_ALTAR)) {
										altars++;
										altarY = y;
									} else if (st.is(AstralContent.ASTRAL_BRICKS)) {
										bricks++;
									}
								}
							}
						}
						int ground = astral.getHeight(Heightmap.Types.WORLD_SURFACE, c.getX(), c.getZ());
						boolean onSurface = altars > 0 && Math.abs(altarY - ground) <= 30;
						report.append(onSurface ? "OK" : "FAIL").append(" chateau a la surface : sol y=").append(ground)
								.append(", autel y=").append(altarY).append('\n');
						ok &= onSurface;
						report.append(altars > 0 ? "OK" : "FAIL").append(" chateau en ").append(c).append(" : ").append(altars)
								.append(" autel(s) du Gardien, ").append(bricks).append(" briques\n");
						ok &= altars > 0;
					} else {
						report.append("WARN aucun chateau dans un rayon de 100 chunks\n");
					}
				}
				// Les mobs de la dimension deviennent astraux.
				var archer = Compat.SKELETON.create(astral, net.minecraft.world.entity.EntitySpawnReason.NATURAL);
				if (archer != null) {
					archer.setPos(2000.5, 190, 2003.5);
					astral.addFreshEntity(archer);
					boolean astralMob = AstralMobs.isAstral(archer);
					report.append(astralMob ? "OK" : "FAIL").append(" mob astral : ").append(archer.getName().getString())
							.append(" PV=").append(archer.getMaxHealth()).append('\n');
					ok &= astralMob;
					archer.discard();
				}
				// Outils infusés : minage plus rapide, et la hache abat tout l'arbre.
				var axe = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_AXE);
				com.diamondaltar.ModComponents.infuse(axe);
				var mods = axe.get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
				boolean fast = mods != null && mods.modifiers().stream().anyMatch(m -> m.attribute().is(net.minecraft.world.entity.ai.attributes.Attributes.MINING_EFFICIENCY))
						&& mods.modifiers().stream().anyMatch(m -> m.attribute().is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
				report.append(fast ? "OK" : "FAIL").append(" outils infuses : vitesse de minage +").append(com.diamondaltar.ability.ToolPassives.MINING_BONUS).append('\n');
				ok &= fast;
				boolean unbreakable = axe.has(net.minecraft.core.component.DataComponents.UNBREAKABLE);
				report.append(unbreakable ? "OK" : "FAIL").append(" outils infuses incassables\n");
				ok &= unbreakable;
				var worn = axe.copy();
				worn.set(net.minecraft.core.component.DataComponents.DAMAGE, 0);
				worn.hurtAndBreak(50, astral, null, item -> { });
				com.diamondaltar.ability.ArmorPassives.keepPristine(worn);
				boolean noWear = worn.getDamageValue() == 0;
				var old = axe.copy();
				old.remove(net.minecraft.core.component.DataComponents.UNBREAKABLE);
				old.set(com.diamondaltar.ModComponents.INFUSE_VERSION, 1);
				old.set(net.minecraft.core.component.DataComponents.DAMAGE, 30);
				com.diamondaltar.ability.ArmorPassives.keepPristine(old);
				noWear &= old.getDamageValue() == 0 && old.has(net.minecraft.core.component.DataComponents.UNBREAKABLE);
				report.append(noWear ? "OK" : "FAIL").append(" objets infuses : aucune usure, anciens objets reparés et incassables\n");
				ok &= noWear;
				var faker = net.fabricmc.fabric.api.entity.FakePlayer.get(astral);
				faker.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, axe);
				BlockPos treeBase = new BlockPos(2010, 200, 2010);
				for (int y = 0; y < 6; y++) {
					astral.setBlock(treeBase.above(y), AstralContent.ASTRAL_LOG.defaultBlockState(), 3);
				}
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						if (dx != 0 || dz != 0) {
							astral.setBlock(treeBase.offset(dx, 5, dz), AstralContent.ASTRAL_LEAVES.defaultBlockState(), 3);
						}
					}
				}
				BlockState cut = astral.getBlockState(treeBase);
				astral.removeBlock(treeBase, false);
				com.diamondaltar.ability.ToolPassives.onBlockBroken(astral, faker, treeBase, cut);
				int left = 0;
				for (int dx = -2; dx <= 2; dx++) {
					for (int dy = 0; dy < 7; dy++) {
						for (int dz = -2; dz <= 2; dz++) {
							BlockState rest = astral.getBlockState(treeBase.offset(dx, dy, dz));
							if (rest.is(AstralContent.ASTRAL_LOG) || rest.is(AstralContent.ASTRAL_LEAVES)) {
								left++;
							}
						}
					}
				}
				report.append(left == 0 ? "OK" : "FAIL").append(" hache infusee : arbre entier abattu (blocs restants : ").append(left).append(")\n");
				ok &= left == 0;
				// L'onglet créatif se construit sans erreur (un doublon fait planter l'inventaire).
				try {
					AstralContent.TAB.buildContents(new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(
							server.getWorldData().enabledFeatures(), true, server.registryAccess()));
					report.append("OK onglet creatif Diamond Altar : ").append(AstralContent.TAB.getDisplayItems().size()).append(" objets\n");
				} catch (RuntimeException e) {
					report.append("FAIL onglet creatif : ").append(e.getMessage()).append('\n');
					ok = false;
				}
				// Le Gardien apparaît et donne son butin.
				BlockPos arena = new BlockPos(2000, 190, 2000);
				Guardian.summon(astral, arena);
				var bosses = astral.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
						new net.minecraft.world.phys.AABB(arena).inflate(4), Guardian::isGuardian);
				report.append(!bosses.isEmpty() ? "OK" : "FAIL").append(" Gardien invoque, PV=")
						.append(bosses.isEmpty() ? 0 : bosses.get(0).getMaxHealth()).append('\n');
				ok &= !bosses.isEmpty();
				for (int i = 0; i < 120; i++) {
					Guardian.tick(astral);
					com.diamondaltar.effect.EffectManager.tickAll();
				}
				if (!bosses.isEmpty()) {
					// Étourdi après son smash : un coup de 10 lui retire 20 PV.
					// (Un seul coup : en 26.3 un deuxième coup dans le même tick est ignoré.)
					var b = bosses.get(0);
					Guardian.stun(astral, b);
					Compat.setInvulnerableTime(b, 0);
					float h0 = b.getHealth();
					b.hurtServer(astral, astral.damageSources().generic(), 10f);
					float stunned = h0 - b.getHealth();
					boolean doubled = stunned >= 18f;
					report.append(doubled ? "OK" : "FAIL").append(" Gardien etourdi : coup de 10 -> ")
							.append(stunned).append(" degats\n");
					ok &= doubled;
				}
				if (!bosses.isEmpty()) {
					Guardian.onDeath(astral, bosses.get(0));
					long hearts = astral.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
							new net.minecraft.world.phys.AABB(arena).inflate(8), e -> e.getItem().is(AstralContent.GUARDIAN_HEART)).size();
					report.append(hearts > 0 ? "OK" : "FAIL").append(" butin du Gardien : ").append(hearts).append(" coeur(s)\n");
					ok &= hearts > 0;
					bosses.get(0).discard();
				}
			}
			// Forge : gabarit + Cœur du Gardien + lingot -> Cœur Céleste ; et l'autel se fabrique avec.
			var recipes = server.getRecipeManager();
			var input = new net.minecraft.world.item.crafting.SmithingRecipeInput(
					new net.minecraft.world.item.ItemStack(AstralContent.ASTRAL_UPGRADE_TEMPLATE),
					new net.minecraft.world.item.ItemStack(AstralContent.GUARDIAN_HEART),
					new net.minecraft.world.item.ItemStack(AstralContent.ASTRALITE_INGOT));
			var smith = recipes.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMITHING, input, server.overworld());
			boolean heart = smith.isPresent() && Compat.assemble(smith.get().value(), input, server.registryAccess()).is(AstralContent.CELESTIAL_HEART);
			report.append(heart ? "OK" : "FAIL").append(" forge : coeur du Gardien -> coeur celeste\n");
			ok &= heart;
			net.minecraft.world.item.ItemStack E = net.minecraft.world.item.ItemStack.EMPTY;
			var altarInput = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, java.util.List.of(
					E, new net.minecraft.world.item.ItemStack(AstralContent.CELESTIAL_HEART), E,
					E, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHER_STAR), E,
					new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT),
					new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT),
					new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT)));
			var altarRecipe = recipes.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, altarInput, server.overworld());
			boolean altar = altarRecipe.isPresent() && Compat.assemble(altarRecipe.get().value(), altarInput, server.registryAccess()).is(com.diamondaltar.block.ModBlocks.INFUSION_ALTAR_ITEM);
			report.append(altar ? "OK" : "FAIL").append(" craft de l'autel avec le coeur celeste en haut au milieu\n");
			ok &= altar;
			var ingotInput = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, java.util.List.of(
					new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT),
					new net.minecraft.world.item.ItemStack(AstralContent.ASTRALITE_FRAGMENT), new net.minecraft.world.item.ItemStack(AstralContent.ASTRALITE_FRAGMENT),
					new net.minecraft.world.item.ItemStack(AstralContent.ASTRALITE_FRAGMENT), new net.minecraft.world.item.ItemStack(AstralContent.ASTRALITE_FRAGMENT),
					new net.minecraft.world.item.ItemStack(AstralContent.ASTRALITE_FRAGMENT), new net.minecraft.world.item.ItemStack(AstralContent.ASTRALITE_FRAGMENT),
					new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT), net.minecraft.world.item.ItemStack.EMPTY));
			boolean ingot = recipes.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, ingotInput, server.overworld()).isPresent();
			report.append(ingot ? "OK" : "FAIL").append(" craft du lingot d'Astralite (6 fragments + 2 netherite)\n");
			ok &= ingot;
			ServerLevel overworld = server.overworld();
			// Le Fusionneur mélange lave + eau en Essence Galactique.
			BlockPos fuserPos = new BlockPos(10, 130, 10);
			overworld.setBlockAndUpdate(fuserPos, AstralContent.FUSER.defaultBlockState());
			if (overworld.getBlockEntity(fuserPos) instanceof FuserBlockEntity fuser) {
				fuser.fillLava();
				fuser.fillWater();
				for (int i = 0; i < FuserBlockEntity.MIX_TIME + 5; i++) {
					FuserBlockEntity.serverTick(overworld, fuserPos, overworld.getBlockState(fuserPos), fuser);
				}
				boolean essence = fuser.takeEssence();
				report.append(essence ? "OK" : "FAIL").append(" Fusionneur : lave + eau -> essence galactique\n");
				ok &= essence;
			} else {
				report.append("FAIL Fusionneur sans block entity\n");
				ok = false;
			}
			BlockPos base = new BlockPos(0, 120, 0);
			BlockState diamond = net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK.defaultBlockState();
			for (int dx = -1; dx <= 2; dx++) {
				for (int dy = -1; dy <= 3; dy++) {
					BlockPos p = base.offset(dx, dy, 0);
					boolean edge = dx == -1 || dx == 2 || dy == -1 || dy == 3;
					overworld.setBlockAndUpdate(p, edge ? diamond : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				}
			}
			boolean lit = AstralPortals.tryIgnite(overworld, base);
			report.append(lit ? "OK" : "FAIL").append(" allumage d'un cadre de diamant 4x5\n");
			ok &= lit;
			// Les 7 Royaumes : dimensions, portails, boss, butin et épée ultime.
			ok &= com.diamondaltar.realms.RealmEvents.selfTest(server, report);
		} catch (Throwable t) {
			report.append("FAIL exception : ").append(t).append('\n');
			for (StackTraceElement el : t.getStackTrace()) {
				report.append("    at ").append(el).append('\n');
			}
			ok = false;
		}
		report.append(ok ? "SELFTEST RESULT: PASS" : "SELFTEST RESULT: FAIL").append("\n=================================");
		DiamondAltar.LOGGER.info(report.toString());
		server.halt(false);
	}
}
