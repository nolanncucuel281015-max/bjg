package com.diamondaltar.client;

import com.diamondaltar.block.InfusionAltarBlockEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Fait flotter et tourner l'objet posé sur l'autel, de plus en plus vite pendant l'infusion. */
public class InfusionAltarRenderer implements BlockEntityRenderer<InfusionAltarBlockEntity> {
	private final ItemRenderer itemRenderer;

	public InfusionAltarRenderer(BlockEntityRendererFactory.Context context) {
		this.itemRenderer = context.getItemRenderer();
	}

	@Override
	public void render(InfusionAltarBlockEntity altar, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		ItemStack stack = altar.getStack();
		if (stack.isEmpty() || altar.getWorld() == null) {
			return;
		}
		float time = altar.getWorld().getTime() + tickDelta;
		int progress = altar.getProgress();
		float t = progress < 0 ? 0f : MathHelper.clamp((progress + tickDelta) / InfusionAltarBlockEntity.DURATION, 0f, 1f);

		// Rotation : lente au repos, puis accélération progressive (rotation = intégrale de la vitesse).
		float baseSpin = time * 2.0f;
		float extraSpin = progress < 0 ? 0f : 540f * t * t * 2f;
		float bob = MathHelper.sin(time * 0.1f) * 0.05f;
		float shake = progress < 0 ? 0f : t * 0.03f * MathHelper.sin(time * 3.1f);
		float scale = 1.6f + 0.5f * t;

		matrices.push();
		matrices.translate(0.5 + shake, InfusionAltarBlockEntity.itemHeight(progress, tickDelta) + bob, 0.5 - shake);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(baseSpin + extraSpin));
		matrices.scale(scale, scale, scale);
		itemRenderer.renderItem(stack, ModelTransformationMode.GROUND, LightmapTextureManager.MAX_LIGHT_COORDINATE,
				OverlayTexture.DEFAULT_UV, matrices, vertexConsumers, altar.getWorld(), (int) altar.getPos().asLong());
		matrices.pop();
	}
}
