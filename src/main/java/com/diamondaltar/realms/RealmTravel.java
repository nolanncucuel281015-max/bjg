package com.diamondaltar.realms;

import com.diamondaltar.Compat;
import com.diamondaltar.ModComponents;
import com.diamondaltar.astral.AstralContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Moyens de transport des Royaumes :
 * <ul>
 * <li>Ailes de l'Aurore : des élytres qui se propulsent toutes seules dans la direction du regard
 * (accroupi pour planer sans propulsion) ;</li>
 * <li>Perle des Royaumes : accroupi + clic droit pour choisir le monde, clic droit pour s'y téléporter.</li>
 * </ul>
 */
public final class RealmTravel {
	private static final int PEARL_COOLDOWN = 400;
	private static final Map<UUID, Long> PEARL_USED = new HashMap<>();
	/** Le serveur en cours (mis à jour à chaque tick). */
	private static MinecraftServer server;

	private RealmTravel() {
	}

	// ---------------------------------------------------------------- destinations de la perle

	/** 0 = Overworld, 1 = Dimension Astrale, 2..8 = les 7 Royaumes. */
	public static int destinations() {
		return 2 + Realm.values().length;
	}

	public static ResourceKey<Level> destination(int index) {
		if (index == 0) {
			return Level.OVERWORLD;
		}
		if (index == 1) {
			return AstralContent.DIMENSION;
		}
		return Realm.values()[index - 2].dimension;
	}

	public static Component destinationName(int index) {
		if (index == 0) {
			return Component.translatable("diamondaltar.pearl.overworld").withStyle(ChatFormatting.GREEN);
		}
		if (index == 1) {
			return Component.translatable("diamondaltar.pearl.astral").withStyle(ChatFormatting.LIGHT_PURPLE);
		}
		Realm r = Realm.values()[index - 2];
		return Component.translatable(r.nameKey()).withStyle(r.chat);
	}

	public static int selected(ItemStack pearl) {
		return Math.floorMod(ModComponents.getSelected(pearl), destinations());
	}

	public static void usePearl(ServerLevel level, ServerPlayer player, ItemStack pearl) {
		if (player.isShiftKeyDown()) {
			int next = (selected(pearl) + 1) % destinations();
			ModComponents.setSelected(pearl, next);
			Compat.overlay(player, Component.translatable("diamondaltar.pearl.selected", destinationName(next)));
			level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 0.8f + next * 0.08f);
			return;
		}
		Long until = PEARL_USED.get(player.getUUID());
		if (until != null && level.getGameTime() < until && !player.getAbilities().instabuild) {
			Compat.overlay(player, Component.translatable("diamondaltar.pearl.cooldown",
					String.format("%.0f", (until - level.getGameTime()) / 20.0)).withStyle(ChatFormatting.GRAY));
			return;
		}
		int index = selected(pearl);
		ServerLevel target = server == null ? null : server.getLevel(destination(index));
		if (target == null) {
			return;
		}
		if (target == level) {
			Compat.overlay(player, Component.translatable("diamondaltar.pearl.already", destinationName(index)).withStyle(ChatFormatting.GRAY));
			return;
		}
		PEARL_USED.put(player.getUUID(), level.getGameTime() + PEARL_COOLDOWN);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY(1.0), player.getZ(), 120, 0.5, 1, 0.5, 0.4);
		level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.5f, 0.6f);
		BlockPos dest = safeSpot(target, player.getBlockX(), player.getBlockZ(), index);
		Vec3 pos = Vec3.atBottomCenterOf(dest);
		player.teleportTo(target, pos.x, pos.y, pos.z, Set.<Relative>of(), player.getYRot(), player.getXRot(), true);
		player.fallDistance = 0;
		target.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + 1, pos.z, 120, 0.5, 1, 0.5, 0.4);
		target.playSound(null, dest, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.4f, 1.6f);
		Compat.overlay(player, destinationName(index).copy().withStyle(ChatFormatting.BOLD));
	}

	/** Un endroit sûr où arriver : le sol à ces coordonnées, sinon une petite plateforme au-dessus du vide. */
	public static BlockPos safeSpot(ServerLevel level, int x, int z, int index) {
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (y > level.getMinY() + 2 && y < level.getMaxY() - 4) {
			return new BlockPos(x, y, z);
		}
		y = Math.min(level.getMaxY() - 10, Math.max(level.getMinY() + 10, 100));
		BlockState floor = index >= 2 ? RealmContent.STONE[index - 2].defaultBlockState() : AstralContent.ASTRAL_STONE.defaultBlockState();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				level.setBlockAndUpdate(new BlockPos(x + dx, y - 1, z + dz), floor);
				for (int dy = 0; dy < 3; dy++) {
					level.setBlockAndUpdate(new BlockPos(x + dx, y + dy, z + dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				}
			}
		}
		return new BlockPos(x, y, z);
	}

	// ---------------------------------------------------------------- ailes

	public static boolean gliding(ServerPlayer player) {
		return player.getPose() == Pose.FALL_FLYING;
	}

	/** Chaque tick : les Ailes de l'Aurore propulsent le joueur qui plane (sauf accroupi). */
	public static void tick(MinecraftServer current) {
		server = current;
		for (ServerPlayer player : current.getPlayerList().getPlayers()) {
			if (!player.getItemBySlot(EquipmentSlot.CHEST).is(RealmContent.DAWN_WINGS) || !gliding(player)) {
				continue;
			}
			ServerLevel level = (ServerLevel) player.level();
			if (!player.isShiftKeyDown()) {
				Vec3 look = player.getLookAngle();
				Vec3 v = player.getDeltaMovement();
				// Comme une fusée douce et sans fin : la vitesse tend vers 1,6 bloc/tick dans la direction du regard.
				Vec3 wanted = look.scale(1.6);
				player.setDeltaMovement(v.add(wanted.subtract(v).scale(0.08)));
				Compat.markHurt(player);
			}
			if (level.getGameTime() % 2 == 0) {
				level.sendParticles(new DustParticleOptions(Realm.SOLAR.color, 1.4f), player.getX(), player.getY() + 0.6, player.getZ(),
						2, 0.3, 0.1, 0.3, 0);
				level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.6, player.getZ(), 1, 0.2, 0.1, 0.2, 0.01);
			}
		}
	}
}
