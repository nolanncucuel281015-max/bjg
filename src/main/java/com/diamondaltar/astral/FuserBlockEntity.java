package com.diamondaltar.astral;

import com.diamondaltar.effect.Visuals;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public class FuserBlockEntity extends BlockEntity {
	public static final int MIX_TIME = 120;
	public static final int MAX_ESSENCE = 4;
	private static final DustParticleOptions LAVA_DUST = new DustParticleOptions(0xFF7A1A, 1.6f);
	private static final DustParticleOptions WATER_DUST = new DustParticleOptions(0x2F7BFF, 1.6f);
	private static final DustParticleOptions GALAXY = new DustParticleOptions(0x9B4BFF, 2.2f);
	private static final DustColorTransitionOptions FIRE_TO_GALAXY = new DustColorTransitionOptions(0xFF8A30, 0x9B4BFF, 1.8f);
	private static final DustColorTransitionOptions WATER_TO_GALAXY = new DustColorTransitionOptions(0x3A8BFF, 0xD060FF, 1.8f);

	private boolean lava;
	private boolean water;
	private int essence;
	private int mixing = -1;

	private Display.BlockDisplay lavaOrb;
	private Display.BlockDisplay waterOrb;
	private Display.BlockDisplay core;

	public FuserBlockEntity(BlockPos pos, BlockState state) {
		super(AstralContent.FUSER_ENTITY, pos, state);
	}

	private Direction facing() {
		return getBlockState().getValue(FuserBlock.FACING);
	}

	/** Côté gauche (vu de face) : réservoir de lave. */
	private Direction left() {
		return facing().getClockWise();
	}

	private Direction right() {
		return facing().getCounterClockWise();
	}

	public boolean fillLava() {
		if (lava || mixing >= 0) {
			return false;
		}
		lava = true;
		changed(SoundEvents.BUCKET_EMPTY_LAVA);
		return true;
	}

	public boolean fillWater() {
		if (water || mixing >= 0) {
			return false;
		}
		water = true;
		changed(SoundEvents.BUCKET_EMPTY);
		return true;
	}

	public boolean takeEssence() {
		if (essence <= 0) {
			return false;
		}
		essence--;
		changed(SoundEvents.BUCKET_FILL);
		return true;
	}

	public void describe(Player player) {
		player.displayClientMessage(Component.translatable("diamondaltar.fuser.status",
				Component.translatable(lava ? "diamondaltar.fuser.full" : "diamondaltar.fuser.empty").withStyle(lava ? ChatFormatting.GOLD : ChatFormatting.GRAY),
				Component.translatable(water ? "diamondaltar.fuser.full" : "diamondaltar.fuser.empty").withStyle(water ? ChatFormatting.AQUA : ChatFormatting.GRAY),
				Component.literal(essence + "/" + MAX_ESSENCE).withStyle(ChatFormatting.LIGHT_PURPLE)), true);
	}

	private void changed(net.minecraft.sounds.SoundEvent sound) {
		setChanged();
		if (level != null) {
			level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 1.0f, 1.0f);
		}
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, FuserBlockEntity fuser) {
		if (!(level instanceof ServerLevel server)) {
			return;
		}
		Vec3 c = Vec3.atCenterOf(pos);
		// Les tuyaux aspirent la lave à gauche et l'eau à droite.
		if (level.getGameTime() % 10 == 0 && fuser.mixing < 0) {
			if (!fuser.lava && isSource(level, pos.relative(fuser.left()), true)) {
				fuser.pump(server, pos.relative(fuser.left()), true);
			}
			if (!fuser.water && isSource(level, pos.relative(fuser.right()), false)) {
				fuser.pump(server, pos.relative(fuser.right()), false);
			}
		}
		// Petites bulles qui montrent ce qu'il y a dans les réservoirs.
		if (level.getGameTime() % 8 == 0) {
			if (fuser.lava) {
				Vec3 l = c.add(Vec3.atLowerCornerOf(fuser.left().getUnitVec3i()).scale(0.52));
				server.sendParticles(ParticleTypes.LAVA, l.x, l.y + 0.3, l.z, 1, 0.1, 0.1, 0.1, 0);
			}
			if (fuser.water) {
				Vec3 r = c.add(Vec3.atLowerCornerOf(fuser.right().getUnitVec3i()).scale(0.52));
				server.sendParticles(ParticleTypes.BUBBLE_POP, r.x, r.y + 0.3, r.z, 2, 0.1, 0.1, 0.1, 0);
			}
			if (fuser.essence > 0) {
				server.sendParticles(GALAXY, c.x, c.y + 0.55, c.z, 2, 0.2, 0.05, 0.2, 0);
			}
		}
		if (fuser.mixing < 0 && fuser.lava && fuser.water && fuser.essence < MAX_ESSENCE) {
			fuser.startMix(server, c);
		}
		if (fuser.mixing >= 0) {
			fuser.mixTick(server, c);
		}
	}

	private static boolean isSource(Level level, BlockPos p, boolean lava) {
		FluidState f = level.getFluidState(p);
		return f.isSource() && f.getType().isSame(lava ? Fluids.LAVA : Fluids.WATER);
	}

	private void pump(ServerLevel server, BlockPos from, boolean isLava) {
		server.setBlockAndUpdate(from, Blocks.AIR.defaultBlockState());
		Vec3 a = Vec3.atCenterOf(from);
		Vec3 b = Vec3.atCenterOf(worldPosition);
		for (int i = 0; i <= 10; i++) {
			Vec3 p = a.lerp(b, i / 10.0);
			server.sendParticles(isLava ? LAVA_DUST : WATER_DUST, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0);
		}
		if (isLava) {
			lava = true;
			changed(SoundEvents.BUCKET_FILL_LAVA);
		} else {
			water = true;
			changed(SoundEvents.BUCKET_FILL);
		}
	}

	private void startMix(ServerLevel server, Vec3 c) {
		mixing = 0;
		lavaOrb = Visuals.block(server, c.add(0, 1, 0), Blocks.MAGMA_BLOCK.defaultBlockState());
		waterOrb = Visuals.block(server, c.add(0, 1, 0), Blocks.BLUE_ICE.defaultBlockState());
		core = Visuals.block(server, c.add(0, 1.2, 0), Blocks.AMETHYST_BLOCK.defaultBlockState());
		Visuals.transform(core, new org.joml.Vector3f(), new Quaternionf(), 0.01f, 0);
		server.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.5f, 0.6f);
		setChanged();
	}

	private void mixTick(ServerLevel server, Vec3 c) {
		int p = ++mixing;
		float t = p / (float) MIX_TIME;
		Vec3 top = c.add(0, 1.3, 0);

		// 1) La lave et l'eau sortent des réservoirs et tournent l'une autour de l'autre en se rapprochant.
		double r = 1.4 * (1 - t) + 0.05;
		double a = p * (0.12 + 0.5 * t);
		float orbScale = Math.max(0.01f, 0.45f * (1 - t * 1.1f));
		Vec3 lp = top.add(Math.cos(a) * r, Math.sin(a * 2) * 0.2, Math.sin(a) * r);
		Vec3 wp = top.add(Math.cos(a + Math.PI) * r, Math.sin(a * 2 + Math.PI) * 0.2, Math.sin(a + Math.PI) * r);
		Quaternionf q1 = new Quaternionf().rotateXYZ(p * 0.2f, p * 0.3f, 0);
		Quaternionf q2 = new Quaternionf().rotateXYZ(0, p * 0.25f, p * 0.2f);
		if (lavaOrb != null) {
			lavaOrb.setPos(lp);
			Visuals.transform(lavaOrb, Visuals.centered(q1, orbScale), q1, orbScale, 1);
		}
		if (waterOrb != null) {
			waterOrb.setPos(wp);
			Visuals.transform(waterOrb, Visuals.centered(q2, orbScale), q2, orbScale, 1);
		}
		server.sendParticles(ParticleTypes.FLAME, lp.x, lp.y, lp.z, 2, 0.05, 0.05, 0.05, 0.01);
		server.sendParticles(FIRE_TO_GALAXY, lp.x, lp.y, lp.z, 2, 0.08, 0.08, 0.08, 0);
		server.sendParticles(ParticleTypes.SPLASH, wp.x, wp.y, wp.z, 3, 0.05, 0.05, 0.05, 0);
		server.sendParticles(WATER_TO_GALAXY, wp.x, wp.y, wp.z, 2, 0.08, 0.08, 0.08, 0);

		// 2) Ça bout, ça fume, ça crépite.
		if (p % 6 == 0) {
			server.sendParticles(ParticleTypes.LARGE_SMOKE, top.x, top.y, top.z, 3, 0.4, 0.2, 0.4, 0.02);
			server.sendParticles(ParticleTypes.ELECTRIC_SPARK, top.x, top.y, top.z, 6, 0.5, 0.3, 0.5, 0.2);
			server.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 0.6f + t);
		}
		// 3) Le mélange violet galactique naît au centre et grossit.
		if (t > 0.5f && core != null) {
			float s = (t - 0.5f) * 1.4f;
			Quaternionf q = new Quaternionf().rotateXYZ(p * 0.15f, p * 0.2f, p * 0.1f);
			Visuals.transform(core, Visuals.centered(q, s), q, s, 1);
			for (int k = 0; k < 3; k++) {
				double ang = p * 0.3 + k * Math.PI * 2 / 3;
				server.sendParticles(GALAXY, top.x + Math.cos(ang) * (s + 0.3), top.y + Math.sin(ang * 2) * 0.2, top.z + Math.sin(ang) * (s + 0.3), 1, 0, 0, 0, 0);
			}
			server.sendParticles(ParticleTypes.END_ROD, top.x, top.y, top.z, 1, s, s, s, 0.01);
			server.sendParticles(ParticleTypes.PORTAL, top.x, top.y, top.z, 6, 0.3, 0.3, 0.3, 0.5);
		}
		if (p % 20 == 0) {
			server.playSound(null, worldPosition, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 1.0f, 0.5f + t);
		}
		if (p >= MIX_TIME) {
			server.sendParticles(ParticleTypes.EXPLOSION, top.x, top.y, top.z, 1, 0, 0, 0, 0);
			server.sendParticles(GALAXY, top.x, top.y, top.z, 60, 0.6, 0.6, 0.6, 0);
			server.sendParticles(ParticleTypes.END_ROD, top.x, top.y, top.z, 40, 0.1, 0.1, 0.1, 0.25);
			server.sendParticles(ParticleTypes.REVERSE_PORTAL, top.x, top.y, top.z, 80, 0.2, 0.2, 0.2, 0.6);
			server.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.5f, 0.8f);
			server.playSound(null, worldPosition, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 0.4f, 1.8f);
			lava = false;
			water = false;
			essence++;
			mixing = -1;
			clearVisuals();
			setChanged();
		}
	}

	private void clearVisuals() {
		for (Display d : new Display[]{lavaOrb, waterOrb, core}) {
			if (d != null) {
				d.discard();
			}
		}
		lavaOrb = null;
		waterOrb = null;
		core = null;
	}

	@Override
	public void setRemoved() {
		clearVisuals();
		super.setRemoved();
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putBoolean("Lava", lava);
		output.putBoolean("Water", water);
		output.putInt("Essence", essence);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		lava = input.getBooleanOr("Lava", false);
		water = input.getBooleanOr("Water", false);
		essence = input.getIntOr("Essence", 0);
	}
}
