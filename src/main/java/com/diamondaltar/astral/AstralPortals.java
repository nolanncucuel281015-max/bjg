package com.diamondaltar.astral;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Portail Astral : cadre de blocs de diamant (comme un portail du Nether), allumé avec l'Essence Galactique. */
public final class AstralPortals {
	private static final int MAX = 21;
	/** Combien de ticks le joueur doit rester dans le portail avant d'être téléporté. */
	private static final int WAIT = 50;
	private static final Map<UUID, Integer> INSIDE = new HashMap<>();
	private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

	private AstralPortals() {
	}

	public static boolean isFrame(BlockState state) {
		return state.is(Blocks.DIAMOND_BLOCK) || state.is(AstralContent.ASTRAL_FRAME);
	}

	/** Remplit l'intérieur d'un cadre valide (dans le plan X ou Z) avec le voile du portail. */
	public static boolean tryIgnite(ServerLevel level, BlockPos start) {
		if (!level.getBlockState(start).isAir()) {
			return false;
		}
		for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
			Set<BlockPos> inside = flood(level, start, axis);
			if (inside != null && inside.size() >= 6) {
				BlockState portal = AstralContent.ASTRAL_PORTAL.defaultBlockState().setValue(AstralPortalBlock.AXIS, axis);
				for (BlockPos p : inside) {
					level.setBlock(p, portal, 18);
					level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.2);
				}
				return true;
			}
		}
		return false;
	}

	/** Les cases d'air entourées par le cadre, ou null si le cadre n'est pas fermé. */
	private static Set<BlockPos> flood(Level level, BlockPos start, Direction.Axis axis) {
		Direction a = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
		Direction[] dirs = {Direction.UP, Direction.DOWN, a, a.getOpposite()};
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		seen.add(start);
		int minH = Integer.MAX_VALUE, maxH = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		while (!queue.isEmpty()) {
			BlockPos p = queue.poll();
			int h = axis == Direction.Axis.X ? p.getX() : p.getZ();
			minH = Math.min(minH, h);
			maxH = Math.max(maxH, h);
			minY = Math.min(minY, p.getY());
			maxY = Math.max(maxY, p.getY());
			if (maxH - minH >= MAX || maxY - minY >= MAX) {
				return null;
			}
			for (Direction d : dirs) {
				BlockPos n = p.relative(d);
				if (seen.contains(n)) {
					continue;
				}
				BlockState s = level.getBlockState(n);
				if (s.isAir()) {
					seen.add(n);
					queue.add(n);
				} else if (!isFrame(s)) {
					return null;
				}
			}
		}
		return (maxH - minH + 1) >= 2 && (maxY - minY + 1) >= 3 ? seen : null;
	}

	// ---------- Téléportation ----------

	/** À chaque tick : les joueurs qui restent dans le voile sont envoyés dans l'autre monde. */
	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = (ServerLevel) player.level();
			BlockPos feet = player.blockPosition();
			boolean in = level.getBlockState(feet).is(AstralContent.ASTRAL_PORTAL) || level.getBlockState(feet.above()).is(AstralContent.ASTRAL_PORTAL);
			UUID id = player.getUUID();
			if (!in) {
				INSIDE.remove(id);
				continue;
			}
			Long cd = COOLDOWN.get(id);
			if (cd != null && level.getGameTime() < cd) {
				continue;
			}
			int t = INSIDE.merge(id, 1, Integer::sum);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY(1.0), player.getZ(), 8, 0.4, 0.8, 0.4, 0.1);
			if (t == 1) {
				level.playSound(null, feet, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 0.5f, 1.4f);
			}
			if (t >= WAIT) {
				INSIDE.remove(id);
				ServerLevel target = level.dimension() == AstralContent.DIMENSION
						? server.overworld()
						: server.getLevel(AstralContent.DIMENSION);
				if (target != null) {
					travel(player, target);
					COOLDOWN.put(id, target.getGameTime() + 100);
				}
			}
		}
	}

	public static void travel(ServerPlayer player, ServerLevel target) {
		int x = player.getBlockX();
		int z = player.getBlockZ();
		BlockPos portal = findPortal(target, x, z);
		if (portal == null) {
			portal = buildPortal(target, x, z);
		}
		Vec3 dest = Vec3.atBottomCenterOf(portal);
		player.teleportTo(target, dest.x, dest.y, dest.z, Set.<Relative>of(), player.getYRot(), player.getXRot(), true);
		player.fallDistance = 0;
		target.playSound(null, portal, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.4f, 1.6f);
		target.sendParticles(ParticleTypes.REVERSE_PORTAL, dest.x, dest.y + 1, dest.z, 80, 0.6, 1.0, 0.6, 0.3);
	}

	/** Cherche un voile de portail existant pas trop loin (rayon 24). */
	private static BlockPos findPortal(ServerLevel level, int x, int z) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int dx = -24; dx <= 24; dx++) {
			for (int dz = -24; dz <= 24; dz++) {
				int cx = x + dx;
				int cz = z + dz;
				if (!level.hasChunk(cx >> 4, cz >> 4)) {
					continue;
				}
				for (int y = level.getMinY(); y < level.getMaxY(); y++) {
					BlockPos p = new BlockPos(cx, y, cz);
					if (level.getBlockState(p).is(AstralContent.ASTRAL_PORTAL) && !level.getBlockState(p.below()).is(AstralContent.ASTRAL_PORTAL)) {
						double d = dx * dx + dz * dz;
						if (d < bestD) {
							bestD = d;
							best = p;
						}
					}
				}
			}
		}
		return best;
	}

	/** Construit un portail de retour (cadre astral 4×5) sur le sol, ou sur une plateforme si c'est le vide. */
	public static BlockPos buildPortal(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (y <= level.getMinY() + 2 || y > level.getMaxY() - 8) {
			y = 100;
			for (int dx = -2; dx <= 3; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					level.setBlockAndUpdate(new BlockPos(x + dx, y - 1, z + dz), AstralContent.ASTRAL_STONE.defaultBlockState());
				}
			}
		}
		BlockState frame = AstralContent.ASTRAL_FRAME.defaultBlockState();
		for (int dx = -1; dx <= 2; dx++) {
			for (int dy = -1; dy <= 3; dy++) {
				BlockPos p = new BlockPos(x + dx, y + dy, z);
				boolean edge = dx == -1 || dx == 2 || dy == -1 || dy == 3;
				level.setBlockAndUpdate(p, edge ? frame : Blocks.AIR.defaultBlockState());
				// on dégage l'espace devant et derrière
				level.setBlockAndUpdate(p.north(), dy >= 0 ? Blocks.AIR.defaultBlockState() : level.getBlockState(p.north()));
				level.setBlockAndUpdate(p.south(), dy >= 0 ? Blocks.AIR.defaultBlockState() : level.getBlockState(p.south()));
			}
		}
		BlockState portal = AstralContent.ASTRAL_PORTAL.defaultBlockState().setValue(AstralPortalBlock.AXIS, Direction.Axis.X);
		for (int dx = 0; dx <= 1; dx++) {
			for (int dy = 0; dy <= 2; dy++) {
				level.setBlock(new BlockPos(x + dx, y + dy, z), portal, 18);
			}
		}
		return new BlockPos(x, y, z);
	}
}
