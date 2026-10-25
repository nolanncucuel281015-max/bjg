package com.diamondaltar.plugin;

import com.diamondaltar.ability.ArmorPassives;
import com.diamondaltar.effect.EffectManager;
import net.minecraft.server.MinecraftServer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin Paper « Diamond Altar » : les mêmes capacités que le mod Fabric, sans mod côté client.
 * Il faut le pack de ressources fourni pour les textures, les modèles et les traductions.
 */
public final class DiamondAltarPlugin extends JavaPlugin {
	private AltarManager altars;

	@Override
	public void onEnable() {
		altars = new AltarManager(this);
		altars.load();
		getServer().getPluginManager().registerEvents(new AbilityListener(), this);
		getServer().getPluginManager().registerEvents(altars, this);
		altars.registerRecipe();
		getServer().getScheduler().runTaskTimer(this, () -> {
			EffectManager.tickAll();
			ArmorPassives.tick(MinecraftServer.getServer());
			altars.tick();
		}, 1L, 1L);
		var cmd = getCommand("diamondaltar");
		if (cmd != null) {
			cmd.setExecutor((sender, command, label, args) -> {
				if (sender instanceof Player p) {
					p.getInventory().addItem(AltarManager.altarItem());
					p.sendMessage("§bTu as reçu un §fAutel d'Infusion§b !");
				}
				return true;
			});
		}
		getLogger().info("Diamond Altar activé ! N'oublie pas le pack de ressources (DiamondAltar-ResourcePack.zip).");
	}

	@Override
	public void onDisable() {
		EffectManager.stopAll();
		if (altars != null) {
			altars.save();
		}
	}
}
