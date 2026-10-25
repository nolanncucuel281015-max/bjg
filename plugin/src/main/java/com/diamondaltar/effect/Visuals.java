package com.diamondaltar.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Color;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Version plugin des objets 3D animés : même interface que dans le mod, mais pilotée par l'API
 * Paper des display entities. Ils ne sont jamais sauvegardés dans le monde.
 */
public final class Visuals {
	private Visuals() {
	}

	private static org.bukkit.entity.Display api(Display d) {
		return (org.bukkit.entity.Display) d.getBukkitEntity();
	}

	public static Display.ItemDisplay item(ServerLevel level, Vec3 pos, ItemStack stack) {
		Display.ItemDisplay d = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
		d.setPos(pos);
		level.addFreshEntity(d);
		ItemDisplay b = (ItemDisplay) d.getBukkitEntity();
		b.setItemStack(CraftItemStack.asBukkitCopy(stack.copyWithCount(1)));
		b.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
		init(b);
		return d;
	}

	public static Display.BlockDisplay block(ServerLevel level, Vec3 pos, BlockState state) {
		Display.BlockDisplay d = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
		d.setPos(pos);
		level.addFreshEntity(d);
		BlockDisplay b = (BlockDisplay) d.getBukkitEntity();
		b.setBlock(CraftBlockData.fromData(state));
		init(b);
		return d;
	}

	private static void init(org.bukkit.entity.Display b) {
		b.setPersistent(false);
		b.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
		b.setViewRange(4.0f);
		b.setTeleportDuration(2);
	}

	public static void transform(Display d, Vector3f translation, Quaternionf rotation, Vector3f scale, int ticks) {
		org.bukkit.entity.Display b = api(d);
		b.setInterpolationDelay(0);
		b.setInterpolationDuration(ticks);
		b.setTransformation(new Transformation(new Vector3f(translation), new Quaternionf(rotation), new Vector3f(scale), new Quaternionf()));
	}

	public static void transform(Display d, Vector3f translation, Quaternionf rotation, float scale, int ticks) {
		transform(d, translation, rotation, new Vector3f(scale), ticks);
	}

	public static Display.BlockDisplay ribbon(ServerLevel level, Vec3 center, BlockState state, int glowColor) {
		Display.BlockDisplay d = block(level, center, state);
		if (glowColor >= 0) {
			org.bukkit.entity.Display b = api(d);
			b.setGlowing(true);
			b.setGlowColorOverride(Color.fromRGB(glowColor & 0xFFFFFF));
		}
		return d;
	}

	public static void ribbonShape(Display d, Vec3 dir, Vec3 facing, float length, float width, int ticks) {
		Vector3f x = dir.normalize().toVector3f();
		Vector3f z = facing.normalize().toVector3f();
		Vector3f y = new Vector3f(z).cross(x).normalize();
		z = new Vector3f(x).cross(y).normalize();
		Quaternionf q = new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
		float depth = 0.04f;
		Vector3f t = new Vector3f(-length / 2, -width / 2, -depth / 2).rotate(q);
		transform(d, t, q, new Vector3f(Math.max(0.001f, length), Math.max(0.001f, width), depth), ticks);
	}

	public static Quaternionf flatSpin(float spin) {
		return new Quaternionf().rotateY(spin).rotateX((float) (Math.PI / 2));
	}

	public static Vector3f centered(Quaternionf rotation, float scale) {
		return new Vector3f(-0.5f * scale).rotate(rotation);
	}

	public static Quaternionf pointing(Vec3 dir, float roll) {
		double yaw = Math.atan2(dir.x, dir.z);
		double pitch = Math.atan2(-dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z));
		return new Quaternionf()
				.rotateY((float) yaw)
				.rotateX((float) (Math.PI / 2 + pitch))
				.rotateY(roll)
				.rotateZ((float) (Math.PI / 4));
	}
}
