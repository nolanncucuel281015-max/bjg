package com.diamondaltar.realms;

import com.diamondaltar.Compat;
import com.diamondaltar.DiamondAltar;
import com.diamondaltar.astral.AstralContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Tout le contenu des 7 Royaumes : pour chaque royaume une pierre, un sol, un minerai (brut + lingot),
 * une clé (ouvre le portail), un sceau (invoque le boss), l'éclat du boss et une armure légendaire.
 * Plus le Cadre des Royaumes, le portail, le bois sylvestre et l'Épée des Sept Royaumes.
 */
public final class RealmContent {
	private static final int N = Realm.values().length;
	public static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final String[] ARMOR_NAMES = {"helmet", "chestplate", "leggings", "boots"};

	public static final Block[] STONE = new Block[N];
	public static final Block[] SOIL = new Block[N];
	public static final Block[] ORE = new Block[N];
	public static final Item[] RAW = new Item[N];
	public static final Item[] INGOT = new Item[N];
	public static final Item[] KEY = new Item[N];
	public static final Item[] SIGIL = new Item[N];
	public static final Item[] SHARD = new Item[N];
	/** ARMOR[royaume][0..3] = casque, plastron, jambières, bottes. */
	public static final Item[][] ARMOR = new Item[N][4];

	private static final MapColor[] MAP_COLORS = {MapColor.COLOR_ORANGE, MapColor.ICE, MapColor.COLOR_YELLOW, MapColor.COLOR_GREEN,
			MapColor.COLOR_BLUE, MapColor.COLOR_PURPLE, MapColor.GOLD};
	private static final SoundType[] STONE_SOUNDS = {SoundType.BASALT, SoundType.STONE, SoundType.DEEPSLATE, SoundType.STONE,
			SoundType.DEEPSLATE, SoundType.DEEPSLATE, SoundType.STONE};
	private static final SoundType[] SOIL_SOUNDS = {SoundType.SAND, SoundType.SNOW, SoundType.GRAVEL, SoundType.MOSS,
			SoundType.SAND, SoundType.SAND, SoundType.SAND};

	static {
		for (Realm r : Realm.values()) {
			int i = r.ordinal();
			int t = r.tier();
			MapColor map = MAP_COLORS[i];
			STONE[i] = AstralContent.block(r.id + "_stone", Block::new, BlockBehaviour.Properties.of()
					.mapColor(map).strength(2.0f, 8f).requiresCorrectToolForDrops().sound(STONE_SOUNDS[i])
					.lightLevel(s -> r == Realm.SOLAR || r == Realm.EMBER ? 3 : 0));
			SOIL[i] = AstralContent.block(r.id + "_soil", Block::new, BlockBehaviour.Properties.of()
					.mapColor(map).strength(0.8f).sound(SOIL_SOUNDS[i]));
			ORE[i] = AstralContent.block(r.ore + "_ore", p -> new DropExperienceBlock(UniformInt.of(3 + t, 7 + t * 2), p),
					BlockBehaviour.Properties.of().mapColor(map).strength(6f + t * 2f, 1200f).requiresCorrectToolForDrops()
							.sound(SoundType.ANCIENT_DEBRIS).lightLevel(s -> 8));
		}
	}

	public static final Block REALM_FRAME = AstralContent.block("realm_frame", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLACK).strength(50f, 1200f).requiresCorrectToolForDrops().sound(SoundType.LODESTONE).lightLevel(s -> 6));
	public static final Block REALM_PORTAL = AstralContent.blockNoItem("realm_portal", RealmPortalBlock::new, BlockBehaviour.Properties.of()
			.noCollision().strength(-1f, 3600000f).sound(SoundType.GLASS).lightLevel(s -> 13).noLootTable().pushReaction(Compat.PUSH_BLOCKED));
	public static final Block SYLVAN_LOG = AstralContent.block("sylvan_log", RotatedPillarBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BROWN).strength(2f).sound(SoundType.WOOD));
	public static final Block SYLVAN_LEAVES = AstralContent.block("sylvan_leaves", Block::new, Compat.seeThrough(BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_LIGHT_GREEN).strength(0.2f).sound(SoundType.AZALEA_LEAVES).noOcclusion().lightLevel(s -> 4)));

	static {
		for (Realm r : Realm.values()) {
			int i = r.ordinal();
			RAW[i] = AstralContent.item("raw_" + r.ore, Item::new, new Item.Properties().rarity(Rarity.UNCOMMON).fireResistant());
			INGOT[i] = AstralContent.item(r.ore + "_ingot", Item::new, new Item.Properties().rarity(Rarity.RARE).fireResistant());
			KEY[i] = AstralContent.item(r.id + "_key", p -> new RealmKeyItem(p, r), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
					.fireResistant());
			SIGIL[i] = AstralContent.item(r.id + "_sigil", Item::new, new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant()
					.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
			SHARD[i] = AstralContent.item(r.id + "_shard", Item::new, new Item.Properties().rarity(Rarity.EPIC).fireResistant()
					.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
			ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, DiamondAltar.id(r.ore));
			for (int k = 0; k < 4; k++) {
				EquipmentSlot slot = ARMOR_SLOTS[k];
				ARMOR[i][k] = AstralContent.item(r.ore + "_" + ARMOR_NAMES[k], Item::new, new Item.Properties().stacksTo(1)
						.rarity(Rarity.EPIC).fireResistant()
						.component(DataComponents.EQUIPPABLE, Equippable.builder(slot).setAsset(asset).build())
						.component(DataComponents.ATTRIBUTE_MODIFIERS, armorStats(r, slot))
						.component(DataComponents.UNBREAKABLE, Unit.INSTANCE));
			}
		}
	}

	/** L'Épée des Sept Royaumes : 30 dégâts, incassable, forgée avec les éclats des 7 boss. */
	public static final Item SEVEN_REALMS_SWORD = AstralContent.item("seven_realms_sword", Item::new, new Item.Properties().stacksTo(1)
			.rarity(Rarity.EPIC).fireResistant()
			.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
			.component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
			.component(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
					.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(DiamondAltar.id("seven_realms_damage"), 29,
							AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
					.add(Attributes.ATTACK_SPEED, new AttributeModifier(DiamondAltar.id("seven_realms_speed"), -2.2,
							AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
					.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(DiamondAltar.id("seven_realms_reach"), 1.5,
							AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
					.build()));

	private RealmContent() {
	}

	/** Armure légendaire : de plus en plus solide d'un royaume à l'autre (Braise < … < Soleil). */
	private static ItemAttributeModifiers armorStats(Realm realm, EquipmentSlot slot) {
		int t = realm.tier();
		double armor = switch (slot) {
			case HEAD, FEET -> 4 + t * 0.5;
			case CHEST -> 9 + t;
			case LEGS -> 7 + t * 0.75;
			default -> 0;
		};
		EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(slot);
		String name = realm.ore + "_" + slot.getSerializedName();
		return ItemAttributeModifiers.builder()
				.add(Attributes.ARMOR, new AttributeModifier(DiamondAltar.id("armor_" + name), armor, AttributeModifier.Operation.ADD_VALUE), group)
				.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(DiamondAltar.id("toughness_" + name), 3 + t * 0.5,
						AttributeModifier.Operation.ADD_VALUE), group)
				.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(DiamondAltar.id("knockback_" + name), 0.1 + t * 0.02,
						AttributeModifier.Operation.ADD_VALUE), group)
				.add(Attributes.MAX_HEALTH, new AttributeModifier(DiamondAltar.id("health_" + name), 2 + t,
						AttributeModifier.Operation.ADD_VALUE), group)
				.build();
	}

	/** Le royaume de l'armure légendaire, ou null si ce n'en est pas une. */
	public static Realm armorRealm(Item item) {
		for (Realm r : Realm.values()) {
			for (Item a : ARMOR[r.ordinal()]) {
				if (a == item) {
					return r;
				}
			}
		}
		return null;
	}

	public static void register() {
		RealmFeatures.register();
		RealmEvents.register();
	}
}
