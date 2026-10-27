package com.diamondaltar.client;

import com.diamondaltar.block.InfusionAltarBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Fait flotter et tourner l'objet posé sur l'autel, de plus en plus vite pendant l'infusion. */
public class InfusionAltarRenderer implements BlockEntityRenderer<InfusionAltarBlockEntity, InfusionAltarRenderState> {
	private final ItemModelResolver itemModelResolver;

	public InfusionAltarRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModelResolver = context.itemModelResolver();
	}

	@Override
	public InfusionAltarRenderState createRenderState() {
		return new InfusionAltarRenderState();
	}

	@Override
	public void extractRenderState(InfusionAltarBlockEntity altar, InfusionAltarRenderState state, float partialTick,
			Vec3 cameraPos, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
		BlockEntityRenderer.super.extractRenderState(altar, state, partialTick, cameraPos, crumblingOverlay);
		ItemStack stack = altar.getStack();
		state.hasItem = !stack.isEmpty() && altar.getLevel() != null;
		state.progress = altar.getProgress();
		state.partialTick = partialTick;
		if (state.hasItem) {
			state.time = altar.getLevel().getGameTime() + partialTick;
			itemModelResolver.updateForTopItem(state.item, stack, ItemDisplayContext.GROUND, altar.getLevel(), null,
					(int) altar.getBlockPos().asLong());
		} else {
			state.item.clear();
		}
	}

	@Override
	public void submit(InfusionAltarRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState cameraState) {
		if (!state.hasItem) {
			return;
		}
		float time = state.time;
		int progress = state.progress;
		float t = progress < 0 ? 0f : Mth.clamp((progress + state.partialTick) / InfusionAltarBlockEntity.DURATION, 0f, 1f);

		// Rotation : lente au repos, puis accélération progressive pendant l'infusion.
		float baseSpin = time * 2.0f;
		float extraSpin = progress < 0 ? 0f : 1080f * t * t;
		float bob = (float) Math.sin(time * 0.1f) * 0.05f;
		float shake = progress < 0 ? 0f : t * 0.03f * (float) Math.sin(time * 3.1f);
		float scale = 1.6f + 0.5f * t;

		poseStack.pushPose();
		poseStack.translate(0.5 + shake, InfusionAltarBlockEntity.itemHeight(progress, state.partialTick) + bob, 0.5 - shake);
		// #if 1.21.11
		poseStack.mulPose(Axis.YP.rotationDegrees(baseSpin + extraSpin));
		// #else
		//? poseStack.rotate(Axis.YP.rotationDegrees(baseSpin + extraSpin));
		// #endif
		poseStack.scale(scale, scale, scale);
		state.item.submit(poseStack, collector, 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
