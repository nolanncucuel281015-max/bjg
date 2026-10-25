package com.diamondaltar.block;

import com.diamondaltar.ModComponents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class InfusionAltarBlockEntity extends BlockEntity {
	/** Durée de l'animation d'infusion, en ticks (5 secondes). */
	public static final int DURATION = 100;

	private static final DustParticleEffect DIAMOND_DUST = new DustParticleEffect(new Vector3f(0.35f, 0.95f, 1.0f), 1.2f);
	private static final DustParticleEffect WHITE_DUST = new DustParticleEffect(new Vector3f(1.0f, 1.0f, 1.0f), 0.8f);

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

	public void startInfusion(ItemStack item, PlayerEntity player) {
		stack = item;
		progress = 0;
		if (world != null) {
			world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.0f, 1.2f);
			world.playSound(null, pos, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 1.0f, 0.8f);
		}
		sync();
	}

	public ItemStack takeItem() {
		ItemStack taken = stack;
		stack = ItemStack.EMPTY;
		progress = -1;
		if (world != null && !world.isClient && world.getBlockState(pos).isOf(ModBlocks.INFUSION_ALTAR)) {
			world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.6f, 1.0f);
			sync();
		}
		return taken;
	}

	/** Position (dans le monde) où flotte l'objet, en fonction de l'avancement de l'animation. */
	public static double itemHeight(int progress, float tickDelta) {
		if (progress < 0) {
			return 1.15;
		}
		float t = MathHelper.clamp((progress + tickDelta) / DURATION, 0f, 1f);
		return 1.15 + 0.6 * MathHelper.sin(t * MathHelper.HALF_PI);
	}

	public static void clientTick(World world, BlockPos pos, BlockState state, InfusionAltarBlockEntity altar) {
		if (altar.progress >= 0 && altar.progress < DURATION) {
			altar.progress++;
		}
	}

	public static void serverTick(World world, BlockPos pos, BlockState state, InfusionAltarBlockEntity altar) {
		if (altar.progress < 0 || !(world instanceof ServerWorld server)) {
			return;
		}
		int p = ++altar.progress;
		Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY() + itemHeight(p, 0) + 0.25, pos.getZ() + 0.5);
		float t = p / (float) DURATION;

		// 1) Anneau de lumière qui tourne autour de l'objet, de plus en plus vite et de plus en plus serré.
		double radius = 1.1 - 0.6 * t;
		double spin = p * (0.15 + 0.35 * t);
		for (int i = 0; i < 3; i++) {
			double a = spin + i * (Math.PI * 2 / 3);
			server.spawnParticles(ParticleTypes.END_ROD,
					center.x + Math.cos(a) * radius, center.y + Math.sin(a * 2) * 0.15, center.z + Math.sin(a) * radius,
					1, 0, 0, 0, 0);
		}

		// 2) Arcs électriques entre le sommet des 4 piliers et l'objet.
		if (p % 4 == 0) {
			double[][] pillars = {{0.18, 0.18}, {0.82, 0.18}, {0.18, 0.82}, {0.82, 0.82}};
			for (double[] c : pillars) {
				Vec3d top = new Vec3d(pos.getX() + c[0], pos.getY() + 1.05, pos.getZ() + c[1]);
				spawnArc(server, top, center, world.random.nextLong());
			}
			world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.7f, 0.5f + t * 1.5f);
		}

		// 3) Spirale de poussière de diamant qui monte des bords vers l'objet.
		if (p > DURATION / 3) {
			double a = p * 0.5;
			for (int k = 0; k < 2; k++) {
				double ang = a + k * Math.PI;
				server.spawnParticles(DIAMOND_DUST,
						center.x + Math.cos(ang) * 0.9, pos.getY() + 0.4 + (p % 20) / 20.0, center.z + Math.sin(ang) * 0.9,
						1, 0, 0, 0, 0);
			}
			server.spawnParticles(ParticleTypes.ENCHANT, center.x, center.y, center.z, 4, 0.6, 0.4, 0.6, 0.6);
		}

		if (p % 20 == 0) {
			world.playSound(null, pos, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS, 1.0f, 0.8f + t);
		}

		// 4) Fin : éclair, explosion de lumière et l'objet est infusé.
		if (p >= DURATION) {
			LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
			if (bolt != null) {
				bolt.refreshPositionAfterTeleport(center.x, pos.getY() + 1.0, center.z);
				bolt.setCosmetic(true);
				world.spawnEntity(bolt);
			}
			server.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, center.x, center.y, center.z, 80, 0.2, 0.2, 0.2, 0.6);
			server.spawnParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 40, 0.1, 0.1, 0.1, 0.25);
			server.spawnParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0, 0, 0, 0);
			world.playSound(null, pos, SoundEvents.ITEM_TOTEM_USE, SoundCategory.BLOCKS, 1.0f, 1.3f);
			world.playSound(null, pos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 1.0f, 0.7f);

			ModComponents.infuse(altar.stack);
			altar.progress = -1;
			altar.sync();
		}
	}

	/** Dessine un éclair en zigzag entre deux points avec des particules. */
	private static void spawnArc(ServerWorld world, Vec3d from, Vec3d to, long seed) {
		java.util.Random rnd = new java.util.Random(seed);
		int steps = 8;
		Vec3d prev = from;
		for (int i = 1; i <= steps; i++) {
			double f = i / (double) steps;
			Vec3d next = from.lerp(to, f);
			if (i < steps) {
				next = next.add((rnd.nextDouble() - 0.5) * 0.25, (rnd.nextDouble() - 0.5) * 0.25, (rnd.nextDouble() - 0.5) * 0.25);
			}
			Vec3d mid = prev.lerp(next, 0.5);
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, next.x, next.y, next.z, 1, 0, 0, 0, 0);
			world.spawnParticles(WHITE_DUST, mid.x, mid.y, mid.z, 1, 0, 0, 0, 0);
			prev = next;
		}
	}

	private void sync() {
		markDirty();
		if (world != null && !world.isClient) {
			BlockState state = getCachedState();
			world.updateListeners(pos, state, state, Block.NOTIFY_ALL);
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
		if (!stack.isEmpty()) {
			nbt.put("Item", stack.encode(registryLookup));
		}
		nbt.putInt("Progress", progress);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
		stack = nbt.contains("Item") ? ItemStack.fromNbtOrEmpty(registryLookup, nbt.getCompound("Item")) : ItemStack.EMPTY;
		progress = nbt.contains("Progress") ? nbt.getInt("Progress") : -1;
	}

	@Nullable
	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
		return createNbt(registryLookup);
	}
}
