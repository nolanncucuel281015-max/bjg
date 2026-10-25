package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Hache — « Bûcheron Titanesque » : abat l'arbre entier que tu vises, bûche par bûche. */
public class TimberEffect extends TimedEffect {
	private static final int MAX_LOGS = 256;
	private static final int PER_TICK = 6;

	private final ServerPlayer player;
	private final List<BlockPos> logs = new ArrayList<>();
	private int index;

	public TimberEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		HitResult hit = player.pick(8, 0f, false);
		if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK
				&& level.getBlockState(bhr.getBlockPos()).is(BlockTags.LOGS)) {
			BlockPos origin = bhr.getBlockPos();
			Set<BlockPos> seen = new HashSet<>();
			ArrayDeque<BlockPos> queue = new ArrayDeque<>();
			queue.add(origin);
			seen.add(origin);
			while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
				BlockPos p = queue.poll();
				logs.add(p);
				for (int dx = -1; dx <= 1; dx++) {
					for (int dy = -1; dy <= 1; dy++) {
						for (int dz = -1; dz <= 1; dz++) {
							BlockPos n = p.offset(dx, dy, dz);
							if (seen.add(n) && level.getBlockState(n).is(BlockTags.LOGS)) {
								queue.add(n);
							}
						}
					}
				}
			}
			logs.sort(Comparator.comparingInt(BlockPos::getY));
			level.playSound(null, origin, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.PLAYERS, 1.0f, 0.7f);
		}
	}

	@Override
	protected boolean tick() {
		for (int i = 0; i < PER_TICK && index < logs.size(); i++, index++) {
			BlockPos pos = logs.get(index);
			if (player.mayBuild() && level.mayInteract(player, pos) && level.getBlockState(pos).is(BlockTags.LOGS)) {
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0);
				level.destroyBlock(pos, true, player);
			}
		}
		return index >= logs.size();
	}
}
