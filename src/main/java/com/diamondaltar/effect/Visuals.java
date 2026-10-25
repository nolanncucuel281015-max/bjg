package com.diamondaltar.effect;

import com.mojang.math.Transformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Objets 3D animés (item_display / block_display) pour les capacités : épées géantes,
 * haches qui tournent, cristaux qui sortent du sol… Ils ne sont jamais sauvegardés.
 */
public final class Visuals {
	private Visuals() {
	}

	public static Display.ItemDisplay item(ServerLevel level, Vec3 pos, ItemStack stack) {
		Display.ItemDisplay d = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level) {
			@Override
			public boolean shouldBeSaved() {
				return false;
			}
		};
		d.setPos(pos);
		d.setItemStack(stack.copyWithCount(1));
		d.setItemTransform(ItemDisplayContext.NONE);
		init(d);
		level.addFreshEntity(d);
		return d;
	}

	public static Display.BlockDisplay block(ServerLevel level, Vec3 pos, BlockState state) {
		Display.BlockDisplay d = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level) {
			@Override
			public boolean shouldBeSaved() {
				return false;
			}
		};
		d.setPos(pos);
		d.setBlockState(state);
		init(d);
		level.addFreshEntity(d);
		return d;
	}

	private static void init(Display d) {
		d.setBrightnessOverride(new Brightness(15, 15));
		d.setViewRange(4.0f);
		d.setPosRotInterpolationDuration(2);
	}

	/** Change la transformation, interpolée en douceur sur {@code ticks} ticks côté client. */
	public static void transform(Display d, Vector3f translation, Quaternionf rotation, Vector3f scale, int ticks) {
		d.setTransformationInterpolationDelay(0);
		d.setTransformationInterpolationDuration(ticks);
		d.setTransformation(new Transformation(translation, rotation, scale, null));
	}

	public static void transform(Display d, Vector3f translation, Quaternionf rotation, float scale, int ticks) {
		transform(d, translation, rotation, new Vector3f(scale), ticks);
	}

	/** Rotation d'un objet posé à plat qui tourne sur lui-même (axe vertical). */
	public static Quaternionf flatSpin(float spin) {
		return new Quaternionf().rotateY(spin).rotateX((float) (Math.PI / 2));
	}

	/** Translation qui centre un bloc (0..1) de taille {@code scale} tourné par {@code rotation} sur la position de l'entité. */
	public static Vector3f centered(Quaternionf rotation, float scale) {
		return new Vector3f(-0.5f * scale).rotate(rotation);
	}

	/** Rotation qui oriente une épée/outil (texture en diagonale) pour que sa pointe suive {@code dir}. */
	public static Quaternionf pointing(Vec3 dir, float roll) {
		double yaw = Math.atan2(dir.x, dir.z);
		double pitch = Math.atan2(-dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z));
		// La texture d'un outil pointe vers le haut-droite (+X+Y) : on la ramène sur +Y, puis +Z.
		return new Quaternionf()
				.rotateY((float) yaw)
				.rotateX((float) (Math.PI / 2 + pitch))
				.rotateY(roll)
				.rotateZ((float) (Math.PI / 4));
	}
}
