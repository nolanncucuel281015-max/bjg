package com.diamondaltar.client;

import com.diamondaltar.ModComponents;
import com.diamondaltar.ability.Abilities;
import com.diamondaltar.astral.AstralContent;
import com.diamondaltar.block.ModBlocks;
import com.diamondaltar.realms.Realm;
import com.diamondaltar.realms.RealmContent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;

/**
 * Le manuel en jeu : un livre de pages avec du texte, les vrais objets du jeu et les crafts
 * dessinés dans des grilles. Le sommaire permet d'aller directement à un chapitre.
 */
public class ManualScreen extends Screen {
	private static final int W = 320;
	private static final int H = 224;
	private static final int PAD = 14;
	private static final int TEXT = 0xFFE8E6F4;
	private static final int MUTED = 0xFFA7A2C6;
	private static final int GOLD = 0xFFF3C867;
	private static final int VIOLET = 0xFFB48CFF;
	private static final int CYAN = 0xFF8EE6FF;

	/** Un élément de page : il connaît sa hauteur et sait se dessiner. */
	private interface Element {
		int height(Font font, int width);

		void draw(GuiGraphics g, Font font, int x, int y, int width);
	}

	private record Chapter(String title, List<Element> elements) {
	}

	private record Page(int chapter, List<Element> elements) {
	}

	private final List<Chapter> chapters = new ArrayList<>();
	private final List<Page> pages = new ArrayList<>();
	private final List<Integer> chapterStart = new ArrayList<>();
	private int page;

	public ManualScreen() {
		super(Component.literal("Manuel Diamond Altar"));
		buildContent();
	}

	// ---------------------------------------------------------------- contenu

	private static ItemStack st(ItemLike item) {
		return new ItemStack(item);
	}

	private static ItemStack st(ItemLike item, int count) {
		return new ItemStack(item, count);
	}

	private static ItemStack infused(Item item) {
		ItemStack s = new ItemStack(item);
		ModComponents.infuse(s);
		return s;
	}

	private void chapter(String title, Element... elements) {
		chapters.add(new Chapter(title, List.of(elements)));
	}

	private void buildContent() {
		ItemStack altar = st(ModBlocks.INFUSION_ALTAR_ITEM);
		chapter("Bienvenue",
				big(altar, 4),
				text("Bienvenue dans Diamond Altar ! Infuse ton équipement en diamant sur l'Autel d'Infusion pour lui donner des capacités lunaires et galactiques, puis pars dans la Dimension Astrale vaincre son Gardien."),
				text("Utilise les flèches en bas pour tourner les pages, ou le bouton Sommaire pour aller à un chapitre.", MUTED));

		chapter("L'Autel d'Infusion",
				sub("Le fabriquer"),
				craft(new ItemStack[]{null, st(AstralContent.CELESTIAL_HEART), null, null, st(Items.NETHER_STAR), null,
						st(Items.NETHERITE_INGOT), st(Items.NETHERITE_INGOT), st(Items.NETHERITE_INGOT)}, altar),
				text("Le Cœur Céleste vient de la Dimension Astrale : il faut battre le Gardien (voir Progression).", MUTED),
				sub("L'utiliser"),
				text("1. Pose l'autel par terre."),
				text("2. Clic droit dessus avec un objet en diamant (armure, épée, lance, pioche, hache, pelle, houe) ou une masse."),
				text("3. L'infusion dure 5 secondes : l'objet tourne, des arcs électriques partent des piliers puis un éclair le frappe."),
				text("4. Clic droit pour récupérer ton objet Infusé."),
				sub("Objets infusables"),
				row(infused(Items.DIAMOND_SWORD), infused(Items.DIAMOND_SPEAR), infused(Items.DIAMOND_PICKAXE), infused(Items.DIAMOND_AXE),
						infused(Items.DIAMOND_SHOVEL), infused(Items.DIAMOND_HOE), infused(Items.MACE), infused(Items.DIAMOND_HELMET),
						infused(Items.DIAMOND_CHESTPLATE), infused(Items.DIAMOND_LEGGINGS), infused(Items.DIAMOND_BOOTS)));

		chapter("Les touches",
				key("Clic gauche", "Capacité de l'épée ou de la lance."),
				key("Clic droit", "Capacité de la pioche, hache, pelle, houe ou masse."),
				key("K", "Changer la capacité de l'objet en main."),
				key("Shift + clic droit", "Capacité d'armure (plastron infusé porté)."),
				key("J", "Changer la capacité d'armure."),
				text("Toutes les capacités se rechargent en 0,5 s. La Tranche de la Volonté n'a pas de recharge.", MUTED),
				text("Les touches K et J se changent dans Options → Commandes.", MUTED));

		abilities("Capacités : épée", Items.DIAMOND_SWORD, "Clic gauche");
		abilities("Capacités : lance", Items.DIAMOND_SPEAR, "Clic gauche");
		abilities("Capacités : pioche", Items.DIAMOND_PICKAXE, "Clic droit");
		abilities("Capacités : hache", Items.DIAMOND_AXE, "Clic droit");
		abilities("Capacités : pelle", Items.DIAMOND_SHOVEL, "Clic droit");
		abilities("Capacités : houe", Items.DIAMOND_HOE, "Clic droit");
		abilities("Capacités : masse", Items.MACE, "Clic droit");
		List<Element> armor = new ArrayList<>();
		armor.add(text("Avec le plastron infusé porté : Shift + clic droit pour lancer, J pour changer.", MUTED));
		for (Abilities.Ability a : Abilities.ARMOR) {
			armor.add(ability(infused(Items.DIAMOND_CHESTPLATE), a.key()));
		}
		chapters.add(new Chapter("Capacités : armure", armor));

		chapter("Bonus des outils",
				icon(infused(Items.DIAMOND_PICKAXE), "Minage ultra rapide", "Tous les objets infusés sont incassables. La pioche, la hache et la pelle minent beaucoup plus vite (bien plus qu'Efficacité V)."),
				icon(infused(Items.DIAMOND_AXE), "Abattage d'arbre", "Avec la hache infusée, casse une bûche : tout l'arbre tombe, bûches et feuilles. Accroupi pour couper normalement."));

		chapter("L'armure infusée",
				text("Incassable et plus solide que la netherite. Par pièce : armure 6 / 12 / 9 / 6, robustesse +5, +6 PV et résistance au recul."),
				passive(Items.DIAMOND_HELMET, "head"),
				passive(Items.DIAMOND_CHESTPLATE, "chest"),
				passive(Items.DIAMOND_LEGGINGS, "legs"),
				passive(Items.DIAMOND_BOOTS, "feet"),
				text("Armure complète : Régénération II, Force II, Saturation et une aura de lumière.", GOLD));

		chapter("Le Fusionneur",
				craft(new ItemStack[]{null, null, null, null, st(Items.DIAMOND), null,
						st(Items.IRON_BLOCK), st(Items.IRON_BLOCK), st(Items.IRON_BLOCK)}, st(AstralContent.FUSER)),
				text("1. Mets de la lave côté gauche (dessin de lave) et de l'eau côté droit : clic droit avec les seaux, ou pose-les juste à côté, les tuyaux les aspirent."),
				text("2. Quand les deux sont pleins, ça bout, ça tourne et ça se mélange en Essence Galactique."),
				text("3. Clic droit avec un seau vide pour la récupérer."),
				icon(st(AstralContent.GALAXY_ESSENCE_BUCKET), "Seau d'Essence Galactique", "Sert à allumer le Portail Astral."));

		chapter("Le Portail Astral",
				row(st(Items.DIAMOND_BLOCK), st(AstralContent.GALAXY_ESSENCE_BUCKET)),
				text("1. Construis un cadre de blocs de diamant comme un portail du Nether : 4 × 5 minimum."),
				text("2. Clic droit dans le cadre avec le Seau d'Essence Galactique, comme un briquet."),
				text("3. Reste dans le portail quelques secondes. Un portail de retour est construit de l'autre côté."));

		chapter("La Dimension Astrale",
				text("Des îles flottantes sous un ciel violet étoilé, avec deux biomes : les Plaines Lunaires (beaucoup d'arbres) et les Champs de Cristal (beaucoup de rochers)."),
				row(st(AstralContent.LUNAR_REGOLITH), st(AstralContent.ASTRAL_STONE), st(AstralContent.ASTRAL_LOG), st(AstralContent.ASTRAL_LEAVES),
						st(AstralContent.ASTRAL_PLANKS), st(AstralContent.LUNARITE_ORE), st(AstralContent.ASTRALITE_ORE), st(AstralContent.ASTRAL_BRICKS),
						st(AstralContent.CHISELED_ASTRAL_BRICKS), st(AstralContent.ASTRAL_LAMP)),
				icon(st(AstralContent.LUNARITE_ORE), "Minerai de Lunarite", "Pioche en fer. Donne de la Lunarite."),
				icon(st(AstralContent.ASTRALITE_ORE), "Minerai d'Astralite", "Le minerai ultime, rare. Pioche en diamant."),
				icon(st(AstralContent.ASTRAL_LOG), "Arbres astraux", "Tronc argenté, feuilles de cristal violet. Les feuilles se récupèrent à la cisaille."),
				icon(st(AstralContent.ASTRAL_BRICKS), "Structures", "Bastions Astraux : pyramides de blackstone à gradins, avec un lac de lave et 3 coffres au sommet. Châteaux Astraux, plus rares, avec l'Autel du Gardien."));

		chapter("Les mobs astraux",
				text("Ils tombent lentement (gravité lunaire). Les hostiles lâchent de la Lunarite et parfois de l'Astralite Brute.", MUTED),
				icon(st(Items.ENDERMAN_SPAWN_EGG), "Marcheur du Vide", "Enderman géant, 60 PV, frappe fort."),
				icon(st(Items.SKELETON_SPAWN_EGG), "Archer Lunaire", "Squelette couronné d'améthyste, 30 PV."),
				icon(st(Items.PHANTOM_SPAWN_EGG), "Raie Stellaire", "Phantom énorme, 34 PV."),
				icon(st(Items.MAGMA_CUBE_SPAWN_EGG), "Météore Vivant", "Cube de magma, 24 PV."),
				icon(st(Items.RABBIT_SPAWN_EGG), "Lapin Lunaire", "Paisible, saute très haut."),
				icon(st(Items.GOAT_SPAWN_EGG), "Chèvre Lunaire", "Paisible, saute haut."));

		chapter("Le Gardien Astral",
				icon(st(Items.WITHER_SKELETON_SKULL), "Gardien Astral", "Squelette wither géant à la hache. 200 PV. Il apparaît près de l'Autel du Gardien, dans un Château Astral."),
				text("Lent au début (une attaque spéciale toutes les 9 s), plus rapide quand il perd ses PV (4,5 s). Pluie d'étoiles, onde de choc, téléportation."),
				sub("Le saut écrasant"),
				text("Il rugit et lève sa hache : éloigne-toi ! Il saute vers toi puis retombe en smash explosif (jusqu'à 6 blocs)."),
				text("Ensuite il est étourdi 4 s : il ne bouge plus et brille. Frappe-le : chaque coup fait DEUX FOIS plus de dégâts !", GOLD),
				sub("Butin"),
				row(st(AstralContent.GUARDIAN_HEART), st(AstralContent.ASTRALITE_INGOT), st(AstralContent.ASTRAL_UPGRADE_TEMPLATE)),
				text("Cœur du Gardien et Lingot d'Astralite à chaque fois, Gabarit d'Amélioration Astrale une fois sur deux.", MUTED));

		chapter("Progression",
				text("1. Fusionneur → Essence Galactique → portail en diamant."),
				text("2. Mine de l'Astralite et fais-la cuire :"),
				smith(st(AstralContent.RAW_ASTRALITE), null, null, st(AstralContent.ASTRALITE_FRAGMENT), "Four"),
				text("3. 6 fragments + 2 lingots de netherite, n'importe où sur la table :"),
				craft(new ItemStack[]{st(AstralContent.ASTRALITE_FRAGMENT), st(AstralContent.ASTRALITE_FRAGMENT), st(AstralContent.ASTRALITE_FRAGMENT),
						st(AstralContent.ASTRALITE_FRAGMENT), st(AstralContent.ASTRALITE_FRAGMENT), st(AstralContent.ASTRALITE_FRAGMENT),
						st(Items.NETHERITE_INGOT), st(Items.NETHERITE_INGOT), null}, st(AstralContent.ASTRALITE_INGOT)),
				text("4. Bats le Gardien, puis à la table de forge :"),
				smith(st(AstralContent.ASTRAL_UPGRADE_TEMPLATE), st(AstralContent.GUARDIAN_HEART), st(AstralContent.ASTRALITE_INGOT),
						st(AstralContent.CELESTIAL_HEART), "Forge"),
				text("5. Avec le Cœur Céleste, fabrique l'Autel d'Infusion !", GOLD));

		buildRealms();

		chapter("Autres crafts",
				craft(new ItemStack[]{st(AstralContent.ASTRAL_STONE), st(AstralContent.ASTRAL_STONE), null,
						st(AstralContent.ASTRAL_STONE), st(AstralContent.ASTRAL_STONE), null, null, null, null}, st(AstralContent.ASTRAL_BRICKS, 4)),
				craft(new ItemStack[]{null, st(AstralContent.LUNARITE), null, st(AstralContent.LUNARITE), st(Items.GLOWSTONE),
						st(AstralContent.LUNARITE), null, st(AstralContent.LUNARITE), null}, st(AstralContent.ASTRAL_LAMP)),
				craft(new ItemStack[]{null, null, null, null, st(AstralContent.ASTRAL_LOG), null, null, null, null}, st(AstralContent.ASTRAL_PLANKS, 4)),
				craft(new ItemStack[]{null, null, null, st(Items.BOOK), st(Items.DIAMOND), null, null, null, null}, st(AstralContent.MANUAL)),
				text("Le manuel : un livre et un diamant, sans forme.", MUTED));

		paginate();
	}

	/** Les 7 Royaumes : dimensions, portails, boss, armures légendaires et Épée des Sept Royaumes. */
	private void buildRealms() {
		String[] realms = {"Royaume de Braise", "Royaume de Givre", "Royaume des Tempêtes", "Royaume Sylvestre",
				"Royaume des Abysses", "Royaume des Ombres", "Royaume du Soleil"};
		String[] looks = {"Îles de basalte et de cendres, flèches de magma.", "Îles enneigées hérissées de pics de glace.",
				"Ciel d'orage, pylônes foudroyés et gravier noirci.", "Îles de mousse couvertes de grands arbres lumineux.",
				"Îles de sable bleu sous un ciel noir, coraux de prismarine.", "Ténèbres presque totales, éclats d'obsidienne pleureuse.",
				"Sable doré et obélisques de lumière sous un soleil éternel."};
		String[] bosses = {"Seigneur des Braises", "Reine du Givre", "Titan de la Tempête", "Béhémoth Sylvestre",
				"Léviathan des Abysses", "Roi des Ombres", "Paladin Solaire"};
		String[] bossDesc = {"Blaze géant, 300 PV. Anneaux de flammes qui embrasent tout.",
				"Vagabonde géante à l'arc, 350 PV. Blizzard qui gèle et ralentit.",
				"Breeze colossal, 400 PV. La foudre tombe sur chaque cercle d'avertissement.",
				"Ravageur, 450 PV. Racines qui immobilisent ; il se soigne.",
				"Noyé géant au trident, 500 PV. Raz-de-marée qui emporte tout.",
				"Squelette wither, 550 PV. Ténèbres, téléportation et armée d'Ombres.",
				"Vindicateur à la hache, 600 PV. Colonnes de lumière ; il se soigne."};
		String[] sets = {"Résistance au feu, Force, immunité au feu.", "Vitesse II, Résistance, immunité au gel.",
				"Vitesse III, Saut III, Célérité II, immunité à la foudre et aux chutes.", "Régénération II, saturation, immunité au poison.",
				"Respiration aquatique, Grâce du dauphin, Force des conduits, Force II.",
				"Vision nocturne, Force II, immunité aux ténèbres, invisible accroupi.",
				"Résistance II, Régénération, Force II, Absorption IV toutes les 30 s."};
		String[] thematic = {"poudre de blaze", "glace bleue", "paratonnerre", "bloc de mousse", "cristaux de prismarine",
				"éclat d'écho", "poudre de pierre lumineuse"};

		List<Element> intro = new ArrayList<>();
		intro.add(text("Sept nouvelles dimensions, à visiter dans l'ordre. Chacune a sa pierre, son sol, son minerai, son boss et son armure légendaire."));
		intro.add(sub("Le portail"));
		intro.add(craft(new ItemStack[]{st(Items.OBSIDIAN), st(Items.OBSIDIAN), st(Items.OBSIDIAN), st(Items.OBSIDIAN),
				st(AstralContent.ASTRALITE_INGOT), st(Items.OBSIDIAN), st(Items.OBSIDIAN), st(Items.OBSIDIAN), st(Items.OBSIDIAN)},
				st(RealmContent.REALM_FRAME, 8)));
		intro.add(text("Construis un cadre de Cadres des Royaumes comme un portail du Nether (4 × 5), puis clic droit dedans avec la Clé du royaume. La clé n'est pas consommée."));
		intro.add(sub("Les clés"));
		intro.add(craft(new ItemStack[]{null, st(Items.BLAZE_POWDER), null, st(AstralContent.ASTRALITE_INGOT), st(Items.ENDER_EYE),
				st(AstralContent.ASTRALITE_INGOT), null, st(Items.BLAZE_POWDER), null}, st(RealmContent.KEY[0])));
		intro.add(text("La 1re clé demande des lingots d'Astralite. Chaque clé suivante demande les lingots du royaume précédent :", MUTED));
		for (Realm r : Realm.values()) {
			int i = r.ordinal();
			intro.add(icon(st(RealmContent.KEY[i]), realms[i], (i == 0 ? "Astralite" : "Lingots du royaume précédent")
					+ " + " + thematic[i] + " + œil de l'Ender."));
		}
		chapters.add(new Chapter("Les 7 Royaumes", intro));

		List<Element> lands = new ArrayList<>();
		lands.add(text("Les minerais se cuisent en lingots (four ou haut fourneau). Les 3 premiers se minent à la pioche en fer, les suivants en diamant.", MUTED));
		for (Realm r : Realm.values()) {
			int i = r.ordinal();
			lands.add(sub(realms[i]));
			lands.add(text(looks[i]));
			lands.add(row(st(RealmContent.STONE[i]), st(RealmContent.SOIL[i]), st(RealmContent.ORE[i]), st(RealmContent.RAW[i]), st(RealmContent.INGOT[i])));
		}
		lands.add(row(st(RealmContent.SYLVAN_LOG), st(RealmContent.SYLVAN_LEAVES)));
		chapters.add(new Chapter("Visiter les Royaumes", lands));

		List<Element> boss = new ArrayList<>();
		boss.add(text("Fabrique le Sceau du royaume avec 4 lingots et un diamant, puis clic droit avec dans son royaume : après un rituel de 3 s, le boss apparaît."));
		boss.add(craft(new ItemStack[]{null, st(RealmContent.INGOT[0]), null, st(RealmContent.INGOT[0]), st(Items.DIAMOND),
				st(RealmContent.INGOT[0]), null, st(RealmContent.INGOT[0]), null}, st(RealmContent.SIGIL[0])));
		boss.add(text("Chaque boss alterne trois attaques : un barrage qui tombe du ciel (cercle au sol : bouge !), une onde de choc, et son attaque signature. Sous 40 % de PV, il enrage.", MUTED));
		for (Realm r : Realm.values()) {
			int i = r.ordinal();
			boss.add(icon(st(RealmContent.SIGIL[i]), bosses[i], bossDesc[i]));
		}
		boss.add(sub("Butin"));
		boss.add(text("L'Éclat du royaume (pour l'épée ultime), 3 à 5 lingots, du minerai brut, et parfois un lingot du royaume suivant."));
		boss.add(row(st(RealmContent.SHARD[0]), st(RealmContent.SHARD[1]), st(RealmContent.SHARD[2]), st(RealmContent.SHARD[3]),
				st(RealmContent.SHARD[4]), st(RealmContent.SHARD[5]), st(RealmContent.SHARD[6])));
		chapters.add(new Chapter("Les boss des Royaumes", boss));

		List<Element> gear = new ArrayList<>();
		gear.add(text("Chaque minerai donne une armure légendaire et incassable (même forme de craft que l'armure en fer), de plus en plus solide d'un royaume à l'autre. L'armure complète donne un bonus :"));
		for (Realm r : Realm.values()) {
			int i = r.ordinal();
			gear.add(row(st(RealmContent.ARMOR[i][0]), st(RealmContent.ARMOR[i][1]), st(RealmContent.ARMOR[i][2]), st(RealmContent.ARMOR[i][3])));
			gear.add(text(sets[i], MUTED));
		}
		gear.add(sub("L'Épée des Sept Royaumes"));
		gear.add(craft(new ItemStack[]{st(RealmContent.SHARD[0]), st(RealmContent.SHARD[1]), st(RealmContent.SHARD[2]),
				st(RealmContent.SHARD[3]), st(Items.NETHERITE_SWORD), st(RealmContent.SHARD[4]),
				st(RealmContent.SHARD[5]), st(Items.NETHER_STAR), st(RealmContent.SHARD[6])}, st(RealmContent.SEVEN_REALMS_SWORD)));
		gear.add(text("Les 7 éclats des boss + une épée en netherite + une étoile du Nether. 30 dégâts, incassable, portée +1,5."));
		gear.add(text("Chaque coup : feu, gel, poison, flétrissement, lumière, et parfois la foudre.", GOLD));
		gear.add(text("Clic droit : Jugement des Sept Royaumes. Sept colonnes, une par royaume, jaillissent autour de toi puis explosent (recharge 8 s).", GOLD));
		chapters.add(new Chapter("Armures et épée ultime", gear));
	}

	private void abilities(String title, Item item, String use) {
		List<Element> list = new ArrayList<>();
		list.add(text(use + " pour lancer · K pour changer", MUTED));
		ItemStack icon = infused(item);
		for (Abilities.Ability a : Abilities.ABILITIES.getOrDefault(item, List.of())) {
			list.add(ability(icon, a.key()));
		}
		chapters.add(new Chapter(title, list));
	}

	// ---------------------------------------------------------------- éléments

	private static Element text(String s) {
		return text(s, TEXT);
	}

	private static Element text(String s, int color) {
		Component c = Component.literal(s);
		return new Element() {
			public int height(Font font, int width) {
				return font.split(c, width).size() * 10 + 3;
			}

			public void draw(GuiGraphics g, Font font, int x, int y, int width) {
				for (FormattedCharSequence line : font.split(c, width)) {
					g.drawString(font, line, x, y, color, false);
					y += 10;
				}
			}
		};
	}

	private static Element sub(String s) {
		return new Element() {
			public int height(Font font, int width) {
				return 14;
			}

			public void draw(GuiGraphics g, Font font, int x, int y, int width) {
				g.drawString(font, Component.literal(s), x, y + 3, VIOLET, false);
			}
		};
	}

	private static Element key(String key, String what) {
		return icon(null, key, what);
	}

	/** Une icône d'objet, un nom et une description sur plusieurs lignes. */
	private static Element icon(ItemStack stack, String name, String desc) {
		return iconC(stack, Component.literal(name).withStyle(ChatFormatting.BOLD), Component.literal(desc));
	}

	private static Element iconC(ItemStack stack, Component name, Component desc) {
		return new Element() {
			public int height(Font font, int width) {
				return Math.max(20, 11 + font.split(desc, width - 22).size() * 10) + 3;
			}

			public void draw(GuiGraphics g, Font font, int x, int y, int width) {
				if (stack != null) {
					g.renderItem(stack, x, y + 1);
				} else {
					g.fill(x, y + 1, x + 16, y + 17, 0xFF2B2550);
					g.fill(x + 3, y + 12, x + 13, y + 14, CYAN);
				}
				g.drawString(font, name, x + 22, y, stack == null ? CYAN : GOLD, false);
				int ly = y + 11;
				for (FormattedCharSequence line : font.split(desc, width - 22)) {
					g.drawString(font, line, x + 22, ly, MUTED, false);
					ly += 10;
				}
			}
		};
	}

	private static Element ability(ItemStack icon, String key) {
		return iconC(icon, Component.translatable("diamondaltar.ability." + key + ".name"),
				Component.translatable("diamondaltar.ability." + key + ".desc"));
	}

	private static Element passive(Item item, String part) {
		return iconC(infused(item), Component.translatable("diamondaltar.ability.armor_" + part + ".name"),
				Component.translatable("diamondaltar.ability.armor_" + part + ".desc"));
	}

	/** Une rangée d'objets (passe à la ligne si besoin). */
	private static Element row(ItemStack... stacks) {
		return new Element() {
			public int height(Font font, int width) {
				int perRow = Math.max(1, width / 20);
				return ((stacks.length + perRow - 1) / perRow) * 20 + 2;
			}

			public void draw(GuiGraphics g, Font font, int x, int y, int width) {
				int perRow = Math.max(1, width / 20);
				for (int i = 0; i < stacks.length; i++) {
					int sx = x + (i % perRow) * 20;
					int sy = y + (i / perRow) * 20;
					slot(g, sx, sy);
					g.renderItem(stacks[i], sx + 1, sy + 1);
				}
			}
		};
	}

	/** Un gros objet centré (rendu agrandi). */
	private static Element big(ItemStack stack, int scale) {
		return new Element() {
			public int height(Font font, int width) {
				return 16 * scale + 8;
			}

			public void draw(GuiGraphics g, Font font, int x, int y, int width) {
				g.pose().pushMatrix();
				g.pose().translate(x + (width - 16 * scale) / 2f, y + 2);
				g.pose().scale(scale, scale);
				g.renderItem(stack, 0, 0);
				g.pose().popMatrix();
			}
		};
	}

	private static void slot(GuiGraphics g, int x, int y) {
		g.fill(x, y, x + 18, y + 18, 0xFF373737);
		g.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
		g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
	}

	/** Une table de craft 3 × 3 avec le résultat. */
	private static Element craft(ItemStack[] grid, ItemStack result) {
		return new Element() {
			public int height(Font font, int width) {
				return 3 * 18 + 8;
			}

			public void draw(GuiGraphics g, Font font, int x, int y, int width) {
				g.fill(x - 3, y - 1, x + 3 * 18 + 3 + 56, y + 3 * 18 + 3, 0xFFC6C6C6);
				for (int i = 0; i < 9; i++) {
					int sx = x + (i % 3) * 18;
					int sy = y + 2 + (i / 3) * 18 - 1;
					slot(g, sx, sy);
					if (grid[i] != null) {
						g.renderItem(grid[i], sx + 1, sy + 1);
					}
				}
				g.drawString(font, "→", x + 3 * 18 + 10, y + 23, 0xFF555555, false);
				int rx = x + 3 * 18 + 28, ry = y + 18;
				slot(g, rx, ry);
				g.renderItem(result, rx + 1, ry + 1);
				g.renderItemDecorations(font, result, rx + 1, ry + 1);
			}
		};
	}

	/** Table de forge (gabarit + base + ajout) ou four (une seule entrée). */
	private static Element smith(ItemStack a, ItemStack b, ItemStack c, ItemStack result, String label) {
		return new Element() {
			public int height(Font font, int width) {
				return 28;
			}

			public void draw(GuiGraphics g, Font font, int x, int y, int width) {
				int n = b == null ? 1 : 3;
				int w = n * 20 + 40 + 18;
				g.fill(x - 3, y, x + w + 3, y + 24, 0xFFC6C6C6);
				ItemStack[] in = {a, b, c};
				for (int i = 0; i < n; i++) {
					slot(g, x + i * 20, y + 3);
					g.renderItem(in[i], x + i * 20 + 1, y + 4);
				}
				g.drawString(font, "→", x + n * 20 + 12, y + 8, 0xFF555555, false);
				slot(g, x + n * 20 + 36, y + 3);
				g.renderItem(result, x + n * 20 + 37, y + 4);
				g.drawString(font, label, x + w + 10, y + 8, MUTED, false);
			}
		};
	}

	// ---------------------------------------------------------------- pages

	private int contentHeight() {
		return H - 26 - 30;
	}

	private void paginate() {
		pages.clear();
		chapterStart.clear();
		pages.add(new Page(-1, List.of())); // sommaire
		int width = W - PAD * 2;
		for (int c = 0; c < chapters.size(); c++) {
			chapterStart.add(pages.size());
			List<Element> cur = new ArrayList<>();
			int used = 0;
			for (Element e : chapters.get(c).elements()) {
				int h = e.height(mc().font, width);
				if (used + h > contentHeight() && !cur.isEmpty()) {
					pages.add(new Page(c, cur));
					cur = new ArrayList<>();
					used = 0;
				}
				cur.add(e);
				used += h;
			}
			pages.add(new Page(c, cur));
		}
	}

	private static net.minecraft.client.Minecraft mc() {
		return net.minecraft.client.Minecraft.getInstance();
	}

	private int left() {
		return (width - W) / 2;
	}

	private int top() {
		return (height - H) / 2;
	}

	@Override
	protected void init() {
		int l = left(), t = top();
		addRenderableWidget(Button.builder(Component.literal("<"), b -> go(page - 1)).bounds(l + 10, t + H - 24, 20, 18).build());
		addRenderableWidget(Button.builder(Component.literal(">"), b -> go(page + 1)).bounds(l + W - 30, t + H - 24, 20, 18).build());
		addRenderableWidget(Button.builder(Component.literal("Sommaire"), b -> go(0)).bounds(l + W / 2 - 35, t + H - 24, 70, 18).build());
		if (page == 0) {
			int cols = chapters.size() > 20 ? 3 : 2;
			int colW = (W - PAD * 2 - 6 * (cols - 1)) / cols;
			int perCol = (chapters.size() + cols - 1) / cols;
			for (int c = 0; c < chapters.size(); c++) {
				int target = chapterStart.get(c);
				int col = c / perCol, r = c % perCol;
				addRenderableWidget(Button.builder(Component.literal(chapters.get(c).title()), b -> go(target))
						.bounds(l + PAD + col * (colW + 6), t + 26 + r * 16, colW, 15).build());
			}
		}
	}

	private void go(int p) {
		page = Math.max(0, Math.min(pages.size() - 1, p));
		rebuildWidgets();
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
		int l = left(), t = top();
		// Couverture du livre : fond nuit étoilée, liseré violet.
		g.fill(l - 2, t - 2, l + W + 2, t + H + 2, 0xFF6A4FC8);
		g.fill(l, t, l + W, t + H, 0xF2141029);
		g.fill(l + 4, t + 20, l + W - 4, t + 21, 0xFF342C5E);
		g.fill(l + 4, t + H - 29, l + W - 4, t + H - 28, 0xFF342C5E);
		long seed = 7;
		for (int i = 0; i < 40; i++) {
			seed = seed * 6364136223846793005L + 1442695040888963407L;
			int sx = l + 4 + (int) ((seed >>> 33) % (W - 8));
			int sy = t + 22 + (int) ((seed >>> 13) % (H - 52));
			g.fill(sx, sy, sx + 1, sy + 1, 0x44FFFFFF);
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int l = left(), t = top();
		Page p = pages.get(page);
		String title = p.chapter() < 0 ? "Sommaire" : chapters.get(p.chapter()).title();
		g.drawString(font, Component.literal(title).withStyle(ChatFormatting.BOLD), l + PAD, t + 7, 0xFFF2F4FF, true);
		String num = (page + 1) + " / " + pages.size();
		g.drawString(font, num, l + W - PAD - font.width(num), t + 7, MUTED, false);
		int y = t + 26;
		for (Element e : p.elements()) {
			e.draw(g, font, l + PAD, y, W - PAD * 2);
			y += e.height(font, W - PAD * 2);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
