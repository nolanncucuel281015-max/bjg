package com.diamondaltar.astral;

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
		});
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
								}
							}
						}
					}
				}
				report.append(solid > 0 ? "OK" : "FAIL").append(" terrain genere : ").append(solid).append(" blocs solides echantillonnes, ").append(ores).append(" minerais\n");
				ok &= solid > 0;
				BlockState surface = astral.getBlockState(new BlockPos(0, astral.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0) - 1, 0));
				report.append("INFO bloc de surface en 0,0 : ").append(surface).append('\n');
				BlockPos found = astral.findNearestMapStructure(ASTRAL_STRUCTURES, BlockPos.ZERO, 50, false);
				report.append(found != null ? "OK" : "WARN").append(" structure astrale la plus proche : ").append(found).append('\n');
				BlockPos portal = AstralPortals.buildPortal(astral, 2000, 2000);
				boolean portalOk = astral.getBlockState(portal).is(AstralContent.ASTRAL_PORTAL);
				report.append(portalOk ? "OK" : "FAIL").append(" portail de retour construit en ").append(portal).append('\n');
				ok &= portalOk;
			}
			ServerLevel overworld = server.overworld();
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
