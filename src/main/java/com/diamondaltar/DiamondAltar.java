package com.diamondaltar;

import com.diamondaltar.ability.AbilityEvents;
import com.diamondaltar.ability.ArmorPassives;
import com.diamondaltar.block.ModBlocks;
import com.diamondaltar.effect.EffectManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
		ModParticles.register();
		ModBlocks.register();
		com.diamondaltar.astral.AstralContent.register();
		AbilityEvents.register();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			EffectManager.tickAll();
			ArmorPassives.tick(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> EffectManager.stopAll());
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !ArmorPassives.cancelsDamage(entity, source));
		LOGGER.info("Les 7 Dimensions chargé !");
	}
}
