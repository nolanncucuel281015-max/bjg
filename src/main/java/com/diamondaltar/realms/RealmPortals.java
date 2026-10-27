package com.diamondaltar.realms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
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

/**
 * Portails des Royaumes : un cadre de Cadres des Royaumes (comme un portail du Nether), allumé avec la clé
 * d'un royaume. Le voile prend la couleur du royaume et y emmène ; dans le royaume, il ramène à l'Overworld.
 */
public final class RealmPortals {
	private static final int MAX = 21;
	private static final int WAIT = 50;
	private static final Map<UUID, Integer> INSIDE = new HashMap<>();
	private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

	private RealmPortals() {
	}

	/** Remplit l'intérieur d'un cadre valide avec le voile du royaume. */
	public static boolean tryIgnite(ServerLevel level, BlockPos start, Realm realm) {
		if (!level.getBlockState(start).isAir()) {
			return false;
		}
		for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
			Set<BlockPos> inside = flood(level, start, axis);
			if (inside != null && inside.size() >= 6) {
				BlockState portal = RealmContent.REALM_PORTAL.defaultBlockState()
						.setValue(RealmPortalBlock.AXIS, axis).setValue(RealmPortalBlock.REALM, realm.ordinal());
				DustParticleOptions dust = new DustParticleOptions(realm.color, 1.5f);
				for (BlockPos p : inside) {
					level.setBlock(p, portal, 18);
					level.sendParticles(dust, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0);
				}
				return true;
			}
		}
		return false;
	}

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
				} else if (!s.is(RealmContent.REALM_FRAME)) {
					return null;
				}
			}
		}
		return (maxH - minH + 1) >= 2 && (maxY - minY + 1) >= 3 ? seen : null;
	}

	/** À chaque tick : les joueurs qui restent dans un voile sont envoyés dans le royaume (ou ramenés). */
	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = (ServerLevel) player.level();
			BlockPos feet = player.blockPosition();
			BlockState here = level.getBlockState(feet);
			if (!here.is(RealmContent.REALM_PORTAL)) {
				here = level.getBlockState(feet.above());
			}
			UUID id = player.getUUID();
			if (!here.is(RealmContent.REALM_PORTAL)) {
				INSIDE.remove(id);
				continue;
			}
			Long cd = COOLDOWN.get(id);
			if (cd != null && level.getGameTime() < cd) {
				continue;
			}
			Realm realm = RealmPortalBlock.realm(here);
			int t = INSIDE.merge(id, 1, Integer::sum);
			level.sendParticles(new DustParticleOptions(realm.color, 1.4f), player.getX(), player.getY(1.0), player.getZ(), 8, 0.4, 0.8, 0.4, 0);
			if (t == 1) {
				level.playSound(null, feet, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 0.5f, 1.2f);
			}
			if (t >= WAIT) {
				INSIDE.remove(id);
				ServerLevel target = level.dimension() == realm.dimension ? server.overworld() : server.getLevel(realm.dimension);
				if (target != null) {
					travel(player, target, realm);
					COOLDOWN.put(id, target.getGameTime() + 100);
				}
			}
		}
	}

	public static void travel(ServerPlayer player, ServerLevel target, Realm realm) {
		int x = player.getBlockX();
		int z = player.getBlockZ();
		BlockPos portal = findPortal(target, x, z, realm);
		if (portal == null) {
			portal = buildPortal(target, x, z, realm);
		}
		Vec3 dest = Vec3.atBottomCenterOf(portal);
		player.teleportTo(target, dest.x, dest.y, dest.z, Set.<Relative>of(), player.getYRot(), player.getXRot(), true);
		player.fallDistance = 0;
		target.playSound(null, portal, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.4f, 1.3f);
		target.sendParticles(new DustParticleOptions(realm.color, 2f), dest.x, dest.y + 1, dest.z, 80, 0.6, 1.0, 0.6, 0);
		if (target.dimension() == realm.dimension) {
			com.diamondaltar.Compat.overlay(player, net.minecraft.network.chat.Component.translatable("diamondaltar.realm.enter",
					net.minecraft.network.chat.Component.translatable(realm.nameKey())).withStyle(realm.chat, net.minecraft.ChatFormatting.BOLD));
		}
	}

	private static BlockPos findPortal(ServerLevel level, int x, int z, Realm realm) {
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
					BlockState s = level.getBlockState(p);
					if (s.is(RealmContent.REALM_PORTAL) && s.getValue(RealmPortalBlock.REALM) == realm.ordinal()
							&& !level.getBlockState(p.below()).is(RealmContent.REALM_PORTAL)) {
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

	/** Construit un portail (cadre 4×5) sur le sol, ou sur une plateforme de pierre du royaume au-dessus du vide. */
	public static BlockPos buildPortal(ServerLevel level, int x, int z, Realm realm) {
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (y <= level.getMinY() + 2 || y > level.getMaxY() - 8) {
			y = 100;
			for (int dx = -2; dx <= 3; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					level.setBlockAndUpdate(new BlockPos(x + dx, y - 1, z + dz), RealmContent.STONE[realm.ordinal()].defaultBlockState());
				}
			}
		}
		BlockState frame = RealmContent.REALM_FRAME.defaultBlockState();
		for (int dx = -1; dx <= 2; dx++) {
			for (int dy = -1; dy <= 3; dy++) {
				BlockPos p = new BlockPos(x + dx, y + dy, z);
				boolean edge = dx == -1 || dx == 2 || dy == -1 || dy == 3;
				level.setBlockAndUpdate(p, edge ? frame : Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(p.north(), dy >= 0 ? Blocks.AIR.defaultBlockState() : level.getBlockState(p.north()));
				level.setBlockAndUpdate(p.south(), dy >= 0 ? Blocks.AIR.defaultBlockState() : level.getBlockState(p.south()));
			}
		}
		BlockState portal = RealmContent.REALM_PORTAL.defaultBlockState()
				.setValue(RealmPortalBlock.AXIS, Direction.Axis.X).setValue(RealmPortalBlock.REALM, realm.ordinal());
		for (int dx = 0; dx <= 1; dx++) {
			for (int dy = 0; dy <= 2; dy++) {
				level.setBlock(new BlockPos(x + dx, y + dy, z), portal, 18);
			}
		}
		return new BlockPos(x, y, z);
	}
}
