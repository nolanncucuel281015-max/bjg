package com.diamondaltar.realms;

import com.diamondaltar.ModComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Butin des boss des Royaumes : plus le boss est fort, plus les récompenses sont fortes.
 * <ul>
 * <li>toujours : l'Éclat du royaume, des lingots et du minerai brut du royaume, des diamants ;</li>
 * <li>équipement en diamant : rare chez les premiers boss, sûr chez les derniers (le Paladin Solaire donne tout) ;
 * certaines pièces sont même déjà infusées ;</li>
 * <li>lingots de netherite à partir des Tempêtes, lingots d'Aetherite à partir des Abysses ;</li>
 * <li>équipement en Aetherite : rare (de 2 % aux Tempêtes à 20 % chez le Paladin Solaire) ;</li>
 * <li>moyens de transport : Perle des Royaumes (Roi des Ombres), Ailes de l'Aurore (Paladin Solaire).</li>
 * </ul>
 */
public final class RealmRewards {
	private static final Item[] DIAMOND_GEAR = {Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_HELMET,
			Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
	/** Chance d'avoir une pièce d'équipement en Aetherite, par royaume. */
	private static final float[] AETHERITE_GEAR_CHANCE = {0f, 0f, 0.02f, 0.04f, 0.07f, 0.12f, 0.20f};

	private RealmRewards() {
	}

	/** Toutes les récompenses du boss, selon son royaume. */
	public static List<ItemStack> roll(Realm realm, RandomSource random) {
		int t = realm.tier();
		int i = realm.ordinal();
		List<ItemStack> loot = new ArrayList<>();
		loot.add(new ItemStack(RealmContent.SHARD[i]));
		loot.add(new ItemStack(RealmContent.INGOT[i], 3 + t + random.nextInt(3)));
		loot.add(new ItemStack(RealmContent.RAW[i], 2 + random.nextInt(4)));
		loot.add(new ItemStack(Items.DIAMOND, 2 + t * 2 + random.nextInt(3)));
		if (i + 1 < Realm.values().length && random.nextFloat() < 0.5f) {
			loot.add(new ItemStack(RealmContent.INGOT[i + 1]));
		}
		// Équipement en diamant : 1 pièce au hasard (chance) pour les premiers, puis de plus en plus de pièces sûres.
		int pieces = switch (t) {
			case 0, 1, 2 -> random.nextFloat() < 0.25f + 0.15f * t ? 1 : 0;
			case 3 -> 1;
			case 4 -> 2;
			case 5 -> 4;
			default -> DIAMOND_GEAR.length;
		};
		List<Item> gear = new ArrayList<>(List.of(DIAMOND_GEAR));
		Collections.shuffle(gear, new java.util.Random(random.nextLong()));
		for (int k = 0; k < pieces; k++) {
			ItemStack piece = new ItemStack(gear.get(k));
			if (random.nextFloat() < 0.05f * t && ModComponents.canInfuse(piece)) {
				ModComponents.infuse(piece); // une pièce déjà infusée !
			}
			loot.add(piece);
		}
		if (t >= 2 && random.nextFloat() < 0.3f + 0.1f * t) {
			loot.add(new ItemStack(Items.NETHERITE_INGOT, 1 + random.nextInt(Math.max(1, t - 1))));
		}
		int aetherite = switch (t) {
			case 4 -> random.nextBoolean() ? 1 : 0;
			case 5 -> 1 + random.nextInt(2);
			case 6 -> 2 + random.nextInt(3);
			default -> 0;
		};
		if (aetherite > 0) {
			loot.add(new ItemStack(RealmContent.AETHERITE_INGOT, aetherite));
		}
		if (random.nextFloat() < AETHERITE_GEAR_CHANCE[t]) {
			loot.add(new ItemStack(randomAetheriteGear(random)));
		}
		if (realm == Realm.SHADOW) {
			loot.add(new ItemStack(RealmContent.REALM_PEARL));
		}
		if (realm == Realm.SOLAR) {
			loot.add(new ItemStack(RealmContent.DAWN_WINGS));
		}
		return loot;
	}

	private static Item randomAetheriteGear(RandomSource random) {
		int k = random.nextInt(6);
		return switch (k) {
			case 4 -> RealmContent.AETHERITE_SWORD;
			case 5 -> RealmContent.AETHERITE_PICKAXE;
			default -> RealmContent.AETHERITE_ARMOR[k];
		};
	}

	public static void drop(ServerLevel level, LivingEntity boss, Realm realm) {
		for (ItemStack stack : roll(realm, level.getRandom())) {
			boss.spawnAtLocation(level, stack);
		}
	}
}
