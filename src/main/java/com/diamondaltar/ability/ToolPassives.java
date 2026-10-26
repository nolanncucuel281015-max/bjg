package com.diamondaltar.ability;

import com.diamondaltar.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bonus permanents des outils infusés : la pioche, la hache et la pelle minent beaucoup plus vite,
 * et la hache abat l'arbre entier quand on casse une bûche (sauf accroupi).
 */
public final class ToolPassives {
	/** Bonus d'efficacité de minage (l'enchantement Efficacité V donne 26). */
	public static final double MINING_BONUS = 40;
	private static final int MAX_LOGS = 256;
	private static final int MAX_LEAVES = 600;
	private static final int LEAF_REACH = 4;

	private ToolPassives() {
	}

	public static boolean isFastTool(ItemStack stack) {
		return stack.is(Items.DIAMOND_PICKAXE) || stack.is(Items.DIAMOND_AXE) || stack.is(Items.DIAMOND_SHOVEL);
	}

	/** Ajoute le bonus de vitesse de minage aux attributs de l'outil (sans perdre ses dégâts d'origine). */
	public static void boostMining(ItemStack stack) {
		if (!isFastTool(stack)) {
			return;
		}
		ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, mods.withModifierAdded(Attributes.MINING_EFFICIENCY,
				new AttributeModifier(Identifier.fromNamespaceAndPath("diamondaltar", "infused_mining"), MINING_BONUS,
						AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND));
	}

	/**
	 * Appelé quand un joueur casse un bloc. Avec la hache infusée en main (et pas accroupi),
	 * si c'est une bûche : toutes les bûches de l'arbre et ses feuilles tombent.
	 */
	public static void onBlockBroken(ServerLevel level, ServerPlayer player, BlockPos origin, BlockState broken) {
		ItemStack tool = player.getMainHandItem();
		if (!broken.is(BlockTags.LOGS) || !tool.is(Items.DIAMOND_AXE) || !ModComponents.isInfused(tool)
				|| player.isShiftKeyDown() || player.isSpectator()) {
			return;
		}
		// Bûches reliées (aussi en diagonale), à partir des voisins du bloc cassé.
		List<BlockPos> logs = new ArrayList<>();
		Set<BlockPos> logSeen = new HashSet<>();
		logSeen.add(origin);
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		addLogs(origin, queue, logSeen, level);
		while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
			BlockPos p = queue.poll();
			logs.add(p);
			addLogs(p, queue, logSeen, level);
		}
		if (logs.isEmpty()) {
			return;
		}
		// Feuilles accrochées à l'arbre (jusqu'à 4 blocs des bûches).
		List<BlockPos> leaves = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> lq = new ArrayDeque<>();
		ArrayDeque<Integer> depth = new ArrayDeque<>();
		List<BlockPos> roots = new ArrayList<>(logs);
		roots.add(origin);
		for (BlockPos log : roots) {
			for (BlockPos n : around(log)) {
				if (seen.add(n) && level.getBlockState(n).is(BlockTags.LEAVES)) {
					lq.add(n);
					depth.add(1);
				}
			}
		}
		while (!lq.isEmpty() && leaves.size() < MAX_LEAVES) {
			BlockPos p = lq.poll();
			int d = depth.poll();
			leaves.add(p);
			if (d >= LEAF_REACH) {
				continue;
			}
			for (BlockPos n : around(p)) {
				if (seen.add(n) && level.getBlockState(n).is(BlockTags.LEAVES)) {
					lq.add(n);
					depth.add(d + 1);
				}
			}
		}
		logs.sort(Comparator.comparingInt(BlockPos::getY));
		for (BlockPos p : logs) {
			if (level.mayInteract(player, p)) {
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 1, 0, 0, 0, 0);
				level.destroyBlock(p, true, player);
			}
		}
		for (BlockPos p : leaves) {
			if (level.mayInteract(player, p)) {
				level.destroyBlock(p, true, player);
			}
		}
		level.playSound(null, origin, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.PLAYERS, 1.0f, 0.8f);
	}

	private static void addLogs(BlockPos p, ArrayDeque<BlockPos> queue, Set<BlockPos> seen, ServerLevel level) {
		for (BlockPos n : around(p)) {
			if (!seen.contains(n) && level.getBlockState(n).is(BlockTags.LOGS)) {
				seen.add(n);
				queue.add(n);
			}
		}
	}

	private static List<BlockPos> around(BlockPos p) {
		List<BlockPos> out = new ArrayList<>(26);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx != 0 || dy != 0 || dz != 0) {
						out.add(p.offset(dx, dy, dz));
					}
				}
			}
		}
		return out;
	}
}
