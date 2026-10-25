package com.diamondaltar.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public class InfusionAltarRenderState extends BlockEntityRenderState {
	public final ItemStackRenderState item = new ItemStackRenderState();
	public boolean hasItem;
	public float time;
	public int progress;
	public float partialTick;
}
