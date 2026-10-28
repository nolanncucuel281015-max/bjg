package com.diamondaltar.realms;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Matière du laser de l'Anéantissement Céleste : jamais posée dans le monde, elle sert seulement à dessiner
 * les rayons (texture d'énergie animée, translucide, en pleine lumière). Couleur 0..6 = les 7 royaumes, 7 = blanc.
 */
public class LaserBlock extends Block {
	public static final IntegerProperty COLOR = IntegerProperty.create("color", 0, 7);
	public static final int WHITE = 7;

	public LaserBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(COLOR, WHITE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(COLOR);
	}

	public static BlockState of(int color) {
		return RealmContent.LASER.defaultBlockState().setValue(COLOR, color);
	}
}
