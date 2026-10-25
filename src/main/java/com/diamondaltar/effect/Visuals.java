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
import org.joml.Matrix3f;
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

	/**
	 * Une bande de lumière (ruban) : un bloc très fin et très long, orienté le long de {@code dir}
	 * et tourné face à {@code facing} (en général le regard du joueur). Brille d'un contour coloré.
	 */
	public static Display.BlockDisplay ribbon(ServerLevel level, Vec3 center, BlockState state, int glowColor) {
		Display.BlockDisplay d = block(level, center, state);
		if (glowColor >= 0) {
			d.setGlowingTag(true);
			d.setGlowColorOverride(glowColor);
		}
		return d;
	}

	/** Oriente / redimensionne un ruban : longueur {@code length} le long de dir, épaisseur {@code width}. */
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
