package com.diamondaltar.realms;

import com.diamondaltar.DiamondAltar;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.Level;

/**
 * Les 7 Royaumes, dans l'ordre de la progression : chaque royaume est une dimension avec sa pierre,
 * son sol, son minerai, son boss, son armure légendaire et un éclat pour l'Épée des Sept Royaumes.
 */
public enum Realm {
	EMBER("ember", "pyronite", 0xFF6A1A, 0xFFB347, ChatFormatting.GOLD, BossEvent.BossBarColor.RED),
	FROST("frost", "cryonite", 0x8FE3FF, 0xE8FBFF, ChatFormatting.AQUA, BossEvent.BossBarColor.BLUE),
	STORM("storm", "fulgurite", 0xFFE14A, 0xB8C4FF, ChatFormatting.YELLOW, BossEvent.BossBarColor.YELLOW),
	SYLVAN("sylvan", "verdite", 0x4FD65A, 0xC8FF7A, ChatFormatting.GREEN, BossEvent.BossBarColor.GREEN),
	ABYSS("abyss", "abyssite", 0x1E6BFF, 0x3DF5E0, ChatFormatting.BLUE, BossEvent.BossBarColor.BLUE),
	SHADOW("shadow", "umbrite", 0x7A2BC8, 0x2A0A3A, ChatFormatting.DARK_PURPLE, BossEvent.BossBarColor.PURPLE),
	SOLAR("solar", "solarite", 0xFFC93C, 0xFFFFFF, ChatFormatting.YELLOW, BossEvent.BossBarColor.WHITE);

	/** Nom court (identifiants : ember_stone, ember_key…). */
	public final String id;
	/** Nom du minerai (pyronite_ore, pyronite_ingot, pyronite_helmet…). */
	public final String ore;
	/** Couleur principale (particules, portail) et couleur secondaire. */
	public final int color;
	public final int accent;
	public final ChatFormatting chat;
	public final BossEvent.BossBarColor bar;
	public final ResourceKey<Level> dimension;

	Realm(String id, String ore, int color, int accent, ChatFormatting chat, BossEvent.BossBarColor bar) {
		this.id = id;
		this.ore = ore;
		this.color = color;
		this.accent = accent;
		this.chat = chat;
		this.bar = bar;
		this.dimension = ResourceKey.create(Registries.DIMENSION, DiamondAltar.id(id));
	}

	/** Rang dans la progression (0 = Braise, 6 = Soleil) : les boss et les armures sont de plus en plus forts. */
	public int tier() {
		return ordinal();
	}

	public static Realm of(ResourceKey<Level> dimension) {
		for (Realm r : values()) {
			if (r.dimension == dimension || r.dimension.equals(dimension)) {
				return r;
			}
		}
		return null;
	}

	public String nameKey() {
		return "realm.diamondaltar." + id;
	}
}
