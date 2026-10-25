package com.diamondaltar.plugin;

import com.diamondaltar.ModComponents;
import com.diamondaltar.ability.Abilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Commandes du plugin (pas de touches personnalisées sans mod) :
 * clic gauche = épée / lance, clic droit = autres outils, F = changer de capacité,
 * accroupi + F = changer la capacité d'armure, accroupi + clic droit = lancer la capacité d'armure.
 */
public final class AbilityListener implements Listener {
	/** Dernier tick où le joueur a fait un clic droit (pour ignorer le mouvement de bras qui va avec). */
	private final Map<UUID, Integer> lastUse = new HashMap<>();

	private static ServerPlayer nms(Player p) {
		return ((CraftPlayer) p).getHandle();
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onInteract(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) {
			return;
		}
		Action action = event.getAction();
		if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
			return;
		}
		Player p = event.getPlayer();
		ServerPlayer sp = nms(p);
		lastUse.put(p.getUniqueId(), sp.level().getServer().getTickCount());
		if (p.isSneaking() && Abilities.wearsInfusedChest(sp)) {
			boolean emptyOrInfused = sp.getMainHandItem().isEmpty() || ModComponents.isInfused(sp.getMainHandItem());
			if (action == Action.RIGHT_CLICK_AIR || emptyOrInfused) {
				Abilities.useArmor(sp);
				event.setCancelled(true);
				return;
			}
		}
		if (Abilities.usesRightClick(sp.getMainHandItem())) {
			Abilities.rightClick(sp, InteractionHand.MAIN_HAND);
		}
	}

	/** Le mouvement du bras = clic gauche (dans le vide, sur un bloc ou sur une créature). */
	@EventHandler
	public void onSwing(PlayerAnimationEvent event) {
		if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
			return;
		}
		Player p = event.getPlayer();
		ServerPlayer sp = nms(p);
		Integer used = lastUse.get(p.getUniqueId());
		if (used != null && used == sp.level().getServer().getTickCount()) {
			return;
		}
		if (Abilities.isLeftClick(sp.getMainHandItem()) && ModComponents.isInfused(sp.getMainHandItem())) {
			Abilities.leftClick(sp);
		}
	}

	/** F = capacité suivante ; accroupi + F = capacité d'armure suivante. */
	@EventHandler
	public void onSwap(PlayerSwapHandItemsEvent event) {
		ServerPlayer sp = nms(event.getPlayer());
		if (event.getPlayer().isSneaking() && Abilities.wearsInfusedChest(sp)) {
			event.setCancelled(true);
			Abilities.switchArmor(sp);
		} else if (ModComponents.isInfused(sp.getMainHandItem()) && !Abilities.of(sp.getMainHandItem().getItem()).isEmpty()) {
			event.setCancelled(true);
			Abilities.switchTool(sp);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onDamage(EntityDamageEvent event) {
		if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player p
				&& ModComponents.isInfused(nms(p).getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET))) {
			event.setCancelled(true);
		}
	}
}
