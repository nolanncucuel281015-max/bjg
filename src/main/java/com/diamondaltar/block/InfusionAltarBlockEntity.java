package com.diamondaltar.block;

import com.diamondaltar.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

public class InfusionAltarBlockEntity extends BlockEntity {
	/** Durée de l'animation d'infusion, en ticks (5 secondes). */
	public static final int DURATION = 100;

	private static final DustParticleOptions DIAMOND_DUST = new DustParticleOptions(0x59F2FF, 1.2f);
	private static final DustParticleOptions WHITE_DUST = new DustParticleOptions(0xFFFFFF, 0.8f);

	private ItemStack stack = ItemStack.EMPTY;
	/** -1 = au repos, sinon nombre de ticks écoulés depuis le début de l'infusion. */
	private int progress = -1;

	public InfusionAltarBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.INFUSION_ALTAR_ENTITY, pos, state);
	}

	public ItemStack getStack() {
		return stack;
	}

	public int getProgress() {
		return progress;
	}

	public boolean isEmpty() {
		return stack.isEmpty();
	}

	public boolean isInfusing() {
		return progress >= 0;
	}

	public void startInfusion(ItemStack item) {
		stack = item;
		progress = 0;
		if (level != null) {
			level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.2f);
			level.playSound(null, worldPosition, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.0f, 0.8f);
		}
		sync();
	}

	public ItemStack takeItem() {
		ItemStack taken = stack;
		stack = ItemStack.EMPTY;
		progress = -1;
		if (level != null && !level.isClientSide()) {
			level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 1.0f);
			sync();
		}
		return taken;
	}

	/** Hauteur (au-dessus du bloc) où flotte l'objet, selon l'avancement de l'animation. */
	public static double itemHeight(int progress, float partialTick) {
		if (progress < 0) {
			return 1.15;
		}
		float t = Mth.clamp((progress + partialTick) / DURATION, 0f, 1f);
		return 1.15 + 0.6 * Math.sin(t * Math.PI / 2);
	}

	public static void clientTick(Level level, BlockPos pos, BlockState state, InfusionAltarBlockEntity altar) {
		if (altar.progress >= 0 && altar.progress < DURATION) {
			altar.progress++;
		}
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, InfusionAltarBlockEntity altar) {
		if (altar.progress < 0 || !(level instanceof ServerLevel server)) {
			return;
		}
		int p = ++altar.progress;
		Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + itemHeight(p, 0) + 0.25, pos.getZ() + 0.5);
		float t = p / (float) DURATION;

		// 1) Anneau de lumière qui tourne autour de l'objet, de plus en plus vite et de plus en plus serré.
		double radius = 1.1 - 0.6 * t;
		double spin = p * (0.15 + 0.35 * t);
		for (int i = 0; i < 3; i++) {
			double a = spin + i * (Math.PI * 2 / 3);
			server.sendParticles(ParticleTypes.END_ROD,
					center.x + Math.cos(a) * radius, center.y + Math.sin(a * 2) * 0.15, center.z + Math.sin(a) * radius,
					1, 0, 0, 0, 0);
		}

		// 2) Arcs électriques entre le sommet des 4 piliers et l'objet.
		if (p % 4 == 0) {
			double[][] pillars = {{0.18, 0.18}, {0.82, 0.18}, {0.18, 0.82}, {0.82, 0.82}};
			for (double[] c : pillars) {
				Vec3 top = new Vec3(pos.getX() + c[0], pos.getY() + 1.05, pos.getZ() + c[1]);
				spawnArc(server, top, center, level.getRandom().nextLong());
			}
			level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7f, 0.5f + t * 1.5f);
		}

		// 3) Spirale de poussière de diamant qui monte des bords vers l'objet.
		if (p > DURATION / 3) {
			double a = p * 0.5;
			for (int k = 0; k < 2; k++) {
				double ang = a + k * Math.PI;
				server.sendParticles(DIAMOND_DUST,
						center.x + Math.cos(ang) * 0.9, pos.getY() + 0.4 + (p % 20) / 20.0, center.z + Math.sin(ang) * 0.9,
						1, 0, 0, 0, 0);
			}
			server.sendParticles(ParticleTypes.ENCHANT, center.x, center.y, center.z, 4, 0.6, 0.4, 0.6, 0.6);
		}

		if (p % 20 == 0) {
			level.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 1.0f, 0.8f + t);
		}

		// 4) Fin : éclair, explosion de lumière et l'objet est infusé.
		if (p >= DURATION) {
			LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
			bolt.setPos(center.x, pos.getY() + 1.0, center.z);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);

			server.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, center.x, center.y, center.z, 80, 0.2, 0.2, 0.2, 0.6);
			server.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 40, 0.1, 0.1, 0.1, 0.25);
			server.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0, 0, 0, 0);
			level.playSound(null, pos, SoundEvents.TOTEM_USE, SoundSource.BLOCKS, 1.0f, 1.3f);
			level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0f, 0.7f);

			ModComponents.infuse(altar.stack);
			altar.progress = -1;
			altar.sync();
		}
	}

	/** Dessine un éclair en zigzag entre deux points avec des particules. */
	private static void spawnArc(ServerLevel level, Vec3 from, Vec3 to, long seed) {
		Random rnd = new Random(seed);
		int steps = 8;
		Vec3 prev = from;
		for (int i = 1; i <= steps; i++) {
			Vec3 next = from.lerp(to, i / (double) steps);
			if (i < steps) {
				next = next.add((rnd.nextDouble() - 0.5) * 0.25, (rnd.nextDouble() - 0.5) * 0.25, (rnd.nextDouble() - 0.5) * 0.25);
			}
			Vec3 mid = prev.lerp(next, 0.5);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, next.x, next.y, next.z, 1, 0, 0, 0, 0);
			level.sendParticles(WHITE_DUST, mid.x, mid.y, mid.z, 1, 0, 0, 0, 0);
			prev = next;
		}
	}

	private void sync() {
		setChanged();
		if (level != null && !level.isClientSide()) {
			BlockState state = getBlockState();
			level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
		}
	}

	/** Quand l'autel est cassé, l'objet posé dessus tombe par terre. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null && !stack.isEmpty()) {
			Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack);
			stack = ItemStack.EMPTY;
		}
		super.preRemoveSideEffects(pos, state);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!stack.isEmpty()) {
			output.store("Item", ItemStack.CODEC, stack);
		}
		output.putInt("Progress", progress);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		stack = input.read("Item", ItemStack.CODEC).orElse(ItemStack.EMPTY);
		progress = input.getIntOr("Progress", -1);
	}

	@Nullable
	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}
}
