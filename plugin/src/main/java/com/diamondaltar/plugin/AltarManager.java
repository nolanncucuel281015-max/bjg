package com.diamondaltar.plugin;

import com.diamondaltar.ModComponents;
import com.diamondaltar.effect.EffectManager;
import com.diamondaltar.effect.Visuals;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ShapedRecipe;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * L'Autel d'Infusion version plugin : un bloc invisible (barrière) avec le modèle 3D de l'autel
 * affiché par-dessus (pack de ressources). Clic droit avec un objet en diamant = infusion animée.
 * Accroupi + clic gauche = ramasser l'autel.
 */
public final class AltarManager implements Listener {
	static final String TAG = "diamondaltar_altar";
	private static final String ALTAR_KEY = "diamondaltar_altar_item";

	static final class Altar {
		final World world;
		final int x;
		final int y;
		final int z;
		ItemStack item = ItemStack.EMPTY;
		boolean infusing;
		Display.ItemDisplay floating;

		Altar(World world, int x, int y, int z) {
			this.world = world;
			this.x = x;
			this.y = y;
			this.z = z;
		}

		Vec3 center() {
			return new Vec3(x + 0.5, y + 0.5, z + 0.5);
		}

		ServerLevel level() {
			return ((CraftWorld) world).getHandle();
		}
	}

	private final DiamondAltarPlugin plugin;
	private final Map<String, Altar> altars = new HashMap<>();
	private final File file;

	AltarManager(DiamondAltarPlugin plugin) {
		this.plugin = plugin;
		this.file = new File(plugin.getDataFolder(), "altars.yml");
	}

	private static String key(World w, int x, int y, int z) {
		return w.getName() + ";" + x + ";" + y + ";" + z;
	}

	/** L'objet « Autel d'Infusion » : une pierre de magnétite avec le modèle de l'autel. */
	static org.bukkit.inventory.ItemStack altarItem() {
		ItemStack stack = new ItemStack(Items.LODESTONE);
		stack.set(DataComponents.ITEM_MODEL, ModComponents.id("infusion_altar"));
		stack.set(DataComponents.ITEM_NAME, Component.translatableWithFallback("block.diamondaltar.infusion_altar", "Autel d'Infusion"));
		stack.set(DataComponents.RARITY, Rarity.RARE);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putBoolean(ALTAR_KEY, true));
		return CraftItemStack.asBukkitCopy(stack);
	}

	private static boolean isAltarItem(org.bukkit.inventory.ItemStack item) {
		if (item == null || item.getType() != Material.LODESTONE) {
			return false;
		}
		ItemStack nms = CraftItemStack.asNMSCopy(item);
		return nms.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBooleanOr(ALTAR_KEY, false);
	}

	void registerRecipe() {
		ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, "infusion_altar"), altarItem());
		recipe.shape(" S ", "NNN");
		recipe.setIngredient('S', Material.NETHER_STAR);
		recipe.setIngredient('N', Material.NETHERITE_BLOCK);
		Bukkit.addRecipe(recipe);
	}

	// ---------- Pose / retrait ----------

	@EventHandler(ignoreCancelled = true)
	public void onPlace(BlockPlaceEvent event) {
		if (!isAltarItem(event.getItemInHand())) {
			return;
		}
		Block b = event.getBlockPlaced();
		Bukkit.getScheduler().runTask(plugin, () -> create(b.getWorld(), b.getX(), b.getY(), b.getZ()));
	}

	private void create(World w, int x, int y, int z) {
		w.getBlockAt(x, y, z).setType(Material.BARRIER);
		Location loc = new Location(w, x + 0.5, y + 0.5, z + 0.5);
		w.spawn(loc, ItemDisplay.class, d -> {
			d.setItemStack(altarItem());
			d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
			d.setPersistent(true);
			d.addScoreboardTag(TAG);
		});
		altars.put(key(w, x, y, z), new Altar(w, x, y, z));
		w.playSound(loc, org.bukkit.Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.4f);
		save();
	}

	private void remove(Altar a, boolean dropAltar) {
		Location loc = new Location(a.world, a.x + 0.5, a.y + 0.5, a.z + 0.5);
		a.world.getBlockAt(a.x, a.y, a.z).setType(Material.AIR);
		for (Entity e : a.world.getNearbyEntities(loc, 0.6, 0.6, 0.6)) {
			if (e.getScoreboardTags().contains(TAG)) {
				e.remove();
			}
		}
		if (a.floating != null) {
			a.floating.discard();
		}
		if (!a.item.isEmpty()) {
			a.world.dropItemNaturally(loc, CraftItemStack.asBukkitCopy(a.item));
		}
		if (dropAltar) {
			a.world.dropItemNaturally(loc, altarItem());
		}
		altars.remove(key(a.world, a.x, a.y, a.z));
		save();
	}

	private Altar at(Block b) {
		return b == null ? null : altars.get(key(b.getWorld(), b.getX(), b.getY(), b.getZ()));
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onInteract(PlayerInteractEvent event) {
		Altar a = at(event.getClickedBlock());
		if (a == null || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
			return;
		}
		Player p = event.getPlayer();
		if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
			if (p.isSneaking() && !a.infusing) {
				event.setCancelled(true);
				remove(a, p.getGameMode() != org.bukkit.GameMode.CREATIVE);
			}
			return;
		}
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
			return;
		}
		event.setCancelled(true);
		if (a.infusing) {
			return;
		}
		var sp = ((CraftPlayer) p).getHandle();
		if (!a.item.isEmpty()) {
			ItemStack taken = a.item;
			a.item = ItemStack.EMPTY;
			if (a.floating != null) {
				a.floating.discard();
				a.floating = null;
			}
			if (!sp.getInventory().add(taken)) {
				sp.drop(taken, false);
			}
			a.level().playSound(null, sp.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 1.0f);
			save();
			return;
		}
		ItemStack hand = sp.getMainHandItem();
		if (ModComponents.canInfuse(hand)) {
			a.item = hand.consumeAndReturn(1, sp);
			a.infusing = true;
			spawnFloating(a);
			EffectManager.start(new AltarInfusionEffect(a.level(), a, () -> {
				a.infusing = false;
				refreshFloating(a);
				save();
			}));
		} else {
			p.sendActionBar(net.kyori.adventure.text.Component.text("Pose un objet en diamant (ou une masse / une lance) sur l'autel !"));
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onBreak(BlockBreakEvent event) {
		Altar a = at(event.getBlock());
		if (a != null) {
			event.setCancelled(true);
			remove(a, event.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE);
		}
	}

	// ---------- L'objet qui flotte au-dessus de l'autel ----------

	private void spawnFloating(Altar a) {
		if (a.floating != null) {
			a.floating.discard();
		}
		a.floating = Visuals.item(a.level(), a.center().add(0, 0.75, 0), a.item);
		Visuals.transform(a.floating, new Vector3f(), new Quaternionf(), 0.9f, 0);
	}

	private void refreshFloating(Altar a) {
		if (a.floating != null) {
			((ItemDisplay) a.floating.getBukkitEntity()).setItemStack(CraftItemStack.asBukkitCopy(a.item));
		}
	}

	/** Toutes les 2 ticks : l'objet posé tourne doucement ; on le recrée si son chunk vient de charger. */
	void tick() {
		long time = Bukkit.getWorlds().isEmpty() ? 0 : Bukkit.getWorlds().get(0).getGameTime();
		if (time % 2 != 0) {
			return;
		}
		for (Altar a : altars.values()) {
			if (a.item.isEmpty() || a.infusing) {
				continue;
			}
			if (a.floating == null || a.floating.isRemoved()) {
				if (a.world.isChunkLoaded(a.x >> 4, a.z >> 4)) {
					spawnFloating(a);
				} else {
					continue;
				}
			}
			float bob = (float) Math.sin(time * 0.1) * 0.05f;
			Visuals.transform(a.floating, new Vector3f(0, bob, 0), new Quaternionf().rotateY(time * 0.035f), 0.9f, 2);
		}
	}

	// ---------- Sauvegarde ----------

	void save() {
		YamlConfiguration yml = new YamlConfiguration();
		int i = 0;
		for (Altar a : altars.values()) {
			String p = "altars." + i++;
			yml.set(p + ".world", a.world.getName());
			yml.set(p + ".x", a.x);
			yml.set(p + ".y", a.y);
			yml.set(p + ".z", a.z);
			if (!a.item.isEmpty()) {
				yml.set(p + ".item", Base64.getEncoder().encodeToString(CraftItemStack.asBukkitCopy(a.item).serializeAsBytes()));
			}
		}
		try {
			plugin.getDataFolder().mkdirs();
			yml.save(file);
		} catch (IOException e) {
			plugin.getLogger().warning("Impossible de sauvegarder les autels : " + e.getMessage());
		}
	}

	void load() {
		if (!file.exists()) {
			return;
		}
		YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
		var section = yml.getConfigurationSection("altars");
		if (section == null) {
			return;
		}
		for (String k : section.getKeys(false)) {
			String p = "altars." + k;
			World w = Bukkit.getWorld(yml.getString(p + ".world", "world"));
			if (w == null) {
				continue;
			}
			Altar a = new Altar(w, yml.getInt(p + ".x"), yml.getInt(p + ".y"), yml.getInt(p + ".z"));
			String item = yml.getString(p + ".item");
			if (item != null) {
				a.item = CraftItemStack.asNMSCopy(org.bukkit.inventory.ItemStack.deserializeBytes(Base64.getDecoder().decode(item)));
			}
			altars.put(key(w, a.x, a.y, a.z), a);
		}
	}

}
