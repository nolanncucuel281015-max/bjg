package com.diamondaltar.astral;

import com.diamondaltar.DiamondAltar;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.util.valueproviders.UniformInt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Tout le contenu de la Dimension Astrale : Fusionneur, Essence Galactique, portail, blocs et
 * minerais de la dimension, Astralite (minerai ultime), gabarit, Cœur du Gardien, équipement astral.
 */
public final class AstralContent {
	public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, DiamondAltar.id("astralis"));

	private static final List<Item> TAB_ITEMS = new ArrayList<>();

	// ---------- Blocs ----------
	public static final Block FUSER = block("fuser", FuserBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL).strength(4f, 6f).requiresCorrectToolForDrops().sound(SoundType.COPPER).noOcclusion()
			.lightLevel(s -> 6));
	public static final Block ASTRAL_PORTAL = blockNoItem("astral_portal", AstralPortalBlock::new, BlockBehaviour.Properties.of()
			.noCollision().strength(-1f, 3600000f).sound(SoundType.GLASS).lightLevel(s -> 12).noLootTable().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK));
	public static final Block ASTRAL_FRAME = block("astral_frame", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.DIAMOND).strength(50f, 1200f).requiresCorrectToolForDrops().sound(SoundType.METAL).lightLevel(s -> 4));
	public static final Block ASTRAL_STONE = block("astral_stone", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLUE).strength(1.8f, 6f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE));
	public static final Block LUNAR_REGOLITH = block("lunar_regolith", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.QUARTZ).strength(0.8f).sound(SoundType.SAND));
	public static final Block ASTRAL_BRICKS = block("astral_bricks", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_PURPLE).strength(3f, 9f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_BRICKS));
	public static final Block CHISELED_ASTRAL_BRICKS = block("chiseled_astral_bricks", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_PURPLE).strength(3f, 9f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_BRICKS).lightLevel(s -> 7));
	public static final Block ASTRAL_LAMP = block("astral_lamp", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(1f).sound(SoundType.GLASS).lightLevel(s -> 15));
	public static final Block LUNARITE_ORE = block("lunarite_ore", p -> new DropExperienceBlock(UniformInt.of(2, 5), p), BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLUE).strength(3.5f, 6f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE).lightLevel(s -> 5));
	public static final Block ASTRALITE_ORE = block("astralite_ore", p -> new DropExperienceBlock(UniformInt.of(6, 12), p), BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_MAGENTA).strength(40f, 1200f).requiresCorrectToolForDrops().sound(SoundType.ANCIENT_DEBRIS).lightLevel(s -> 9));
	public static final Block GUARDIAN_ALTAR = block("guardian_altar", GuardianAltarBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLACK).strength(-1f, 3600000f).sound(SoundType.LODESTONE).lightLevel(s -> 10).noLootTable());

	public static final Block ASTRAL_LOG = block("astral_log", net.minecraft.world.level.block.RotatedPillarBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.QUARTZ).strength(2f).sound(SoundType.CHERRY_WOOD).lightLevel(s -> 2));
	public static final Block ASTRAL_PLANKS = block("astral_planks", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.QUARTZ).strength(2f, 3f).sound(SoundType.CHERRY_WOOD));
	public static final Block ASTRAL_LEAVES = block("astral_leaves", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_PURPLE).strength(0.2f).sound(SoundType.AZALEA_LEAVES).noOcclusion().lightLevel(s -> 7)
			.isSuffocating((st, l, pos) -> false).isViewBlocking((st, l, pos) -> false));

	public static final BlockEntityType<FuserBlockEntity> FUSER_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			DiamondAltar.id("fuser"), FabricBlockEntityTypeBuilder.create(FuserBlockEntity::new, FUSER).build());
	public static final BlockEntityType<GuardianAltarBlockEntity> GUARDIAN_ALTAR_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			DiamondAltar.id("guardian_altar"), FabricBlockEntityTypeBuilder.create(GuardianAltarBlockEntity::new, GUARDIAN_ALTAR).build());

	// ---------- Objets ----------
	public static final Item GALAXY_ESSENCE_BUCKET = item("galaxy_essence_bucket", GalaxyEssenceItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
	public static final Item LUNARITE = item("lunarite", Item::new, new Item.Properties());
	public static final Item RAW_ASTRALITE = item("raw_astralite", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON).fireResistant());
	public static final Item ASTRALITE_FRAGMENT = item("astralite_fragment", Item::new, new Item.Properties().rarity(Rarity.RARE).fireResistant());
	public static final Item ASTRALITE_INGOT = item("astralite_ingot", Item::new, new Item.Properties().rarity(Rarity.EPIC).fireResistant());
	public static final Item ASTRAL_UPGRADE_TEMPLATE = item("astral_upgrade_smithing_template", Item::new, new Item.Properties().rarity(Rarity.EPIC));
	public static final Item GUARDIAN_HEART = item("guardian_heart", Item::new, new Item.Properties().rarity(Rarity.EPIC).fireResistant());
	/** Cœur du Gardien amélioré à la forge (gabarit + lingot d'Astralite) : sert à fabriquer l'Autel d'Infusion. */
	public static final Item CELESTIAL_HEART = item("celestial_heart", Item::new, new Item.Properties().rarity(Rarity.EPIC).fireResistant()
			.component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

	/** Manuel du mod : s'ouvre en jeu (clic droit) et explique tout, avec les objets et les crafts. */
	public static final Item MANUAL = item("diamond_manual", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

	/** Onglet créatif « Diamond Altar » : l'autel, tout l'équipement déjà infusé, et tout le contenu astral. */
	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, DiamondAltar.id("main"),
			FabricItemGroup.builder()
					.title(Component.translatable("itemGroup.diamondaltar.main"))
					.icon(() -> new ItemStack(com.diamondaltar.block.ModBlocks.INFUSION_ALTAR_ITEM))
					.displayItems((params, out) -> {
						out.accept(MANUAL);
						out.accept(com.diamondaltar.block.ModBlocks.INFUSION_ALTAR_ITEM);
						for (Item item : List.of(Items.DIAMOND_SWORD, Items.DIAMOND_SPEAR, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE,
								Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE, Items.MACE, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE,
								Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS)) {
							ItemStack infused = new ItemStack(item);
							com.diamondaltar.ModComponents.infuse(infused);
							out.accept(infused);
						}
						TAB_ITEMS.forEach(out::accept);
					})
					.build());

	private AstralContent() {
	}

	private static ResourceKey<Block> blockKey(String name) {
		return ResourceKey.create(Registries.BLOCK, DiamondAltar.id(name));
	}

	private static ResourceKey<Item> itemKey(String name) {
		return ResourceKey.create(Registries.ITEM, DiamondAltar.id(name));
	}

	private static Block blockNoItem(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
		return Registry.register(BuiltInRegistries.BLOCK, blockKey(name), factory.apply(props.setId(blockKey(name))));
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
		Block block = blockNoItem(name, factory, props);
		Item item = Registry.register(BuiltInRegistries.ITEM, itemKey(name),
				new BlockItem(block, new Item.Properties().setId(itemKey(name)).useBlockDescriptionPrefix()));
		TAB_ITEMS.add(item);
		return block;
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
		Item item = Registry.register(BuiltInRegistries.ITEM, itemKey(name), factory.apply(props.setId(itemKey(name))));
		TAB_ITEMS.add(item);
		return item;
	}

	public static void register() {
		SurfaceStructure.register();
		AstralFeatures.register();
		AstralEvents.register();
	}
}
