package com.diamondaltar;

import com.diamondaltar.ability.Abilities;
import com.diamondaltar.ability.ArmorPassives;
import com.diamondaltar.block.ModBlocks;
import com.diamondaltar.effect.EffectManager;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiamondAltar implements ModInitializer {
	public static final String MOD_ID = "diamondaltar";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModComponents.register();
		ModBlocks.register();
		EffectManager.register();
		Abilities.register();
		ArmorPassives.register();
		LOGGER.info("Diamond Altar chargé !");
	}
}
