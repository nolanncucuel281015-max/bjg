package com.diamondaltar;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

import java.util.Set;

/**
 * Tout ce qui diffère entre Minecraft 1.21.11 et 26.3, au même endroit.
 * Le code entre « #if 1.21.11 » et « #else » sert à la 1.21.11 ; les lignes « //? » de la partie
 * « #else » sont activées par le préprocesseur de la build 26.3 (voir mc26/build.gradle).
 */
public final class Compat {
	private Compat() {
	}

	// ---------------------------------------------------------------- types d'entités
	// #if 1.21.11
	public static final EntityType<? extends Mob> WITHER_SKELETON = EntityType.WITHER_SKELETON;
	public static final EntityType<? extends Mob> SKELETON = EntityType.SKELETON;
	public static final EntityType<?> ENDERMAN = EntityType.ENDERMAN;
	public static final EntityType<?> PHANTOM = EntityType.PHANTOM;
	public static final EntityType<?> MAGMA_CUBE = EntityType.MAGMA_CUBE;
	public static final EntityType<?> RABBIT = EntityType.RABBIT;
	public static final EntityType<?> GOAT = EntityType.GOAT;
	public static final EntityType<LightningBolt> LIGHTNING_BOLT = EntityType.LIGHTNING_BOLT;
	public static final EntityType<Display.ItemDisplay> ITEM_DISPLAY = EntityType.ITEM_DISPLAY;
	public static final EntityType<Display.BlockDisplay> BLOCK_DISPLAY = EntityType.BLOCK_DISPLAY;
	public static final EntityType<? extends Mob> BLAZE = EntityType.BLAZE;
	public static final EntityType<? extends Mob> STRAY = EntityType.STRAY;
	public static final EntityType<? extends Mob> BREEZE = EntityType.BREEZE;
	public static final EntityType<? extends Mob> RAVAGER = EntityType.RAVAGER;
	public static final EntityType<? extends Mob> DROWNED = EntityType.DROWNED;
	public static final EntityType<? extends Mob> VINDICATOR = EntityType.VINDICATOR;
	// #else
	//? public static final EntityType<? extends Mob> WITHER_SKELETON = net.minecraft.world.entity.EntityTypes.WITHER_SKELETON;
	//? public static final EntityType<? extends Mob> SKELETON = net.minecraft.world.entity.EntityTypes.SKELETON;
	//? public static final EntityType<?> ENDERMAN = net.minecraft.world.entity.EntityTypes.ENDERMAN;
	//? public static final EntityType<?> PHANTOM = net.minecraft.world.entity.EntityTypes.PHANTOM;
	//? public static final EntityType<?> MAGMA_CUBE = net.minecraft.world.entity.EntityTypes.MAGMA_CUBE;
	//? public static final EntityType<?> RABBIT = net.minecraft.world.entity.EntityTypes.RABBIT;
	//? public static final EntityType<?> GOAT = net.minecraft.world.entity.EntityTypes.GOAT;
	//? public static final EntityType<LightningBolt> LIGHTNING_BOLT = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT;
	//? public static final EntityType<Display.ItemDisplay> ITEM_DISPLAY = net.minecraft.world.entity.EntityTypes.ITEM_DISPLAY;
	//? public static final EntityType<Display.BlockDisplay> BLOCK_DISPLAY = net.minecraft.world.entity.EntityTypes.BLOCK_DISPLAY;
	//? public static final EntityType<? extends Mob> BLAZE = net.minecraft.world.entity.EntityTypes.BLAZE;
	//? public static final EntityType<? extends Mob> STRAY = net.minecraft.world.entity.EntityTypes.STRAY;
	//? public static final EntityType<? extends Mob> BREEZE = net.minecraft.world.entity.EntityTypes.BREEZE;
	//? public static final EntityType<? extends Mob> RAVAGER = net.minecraft.world.entity.EntityTypes.RAVAGER;
	//? public static final EntityType<? extends Mob> DROWNED = net.minecraft.world.entity.EntityTypes.DROWNED;
	//? public static final EntityType<? extends Mob> VINDICATOR = net.minecraft.world.entity.EntityTypes.VINDICATOR;
	// #endif

	// ---------------------------------------------------------------- blocs colorés
	// #if 1.21.11
	public static final Block WHITE_STAINED_GLASS = Blocks.WHITE_STAINED_GLASS;
	public static final Block LIGHT_BLUE_STAINED_GLASS = Blocks.LIGHT_BLUE_STAINED_GLASS;
	public static final Block PURPLE_STAINED_GLASS = Blocks.PURPLE_STAINED_GLASS;
	public static final Block MAGENTA_STAINED_GLASS = Blocks.MAGENTA_STAINED_GLASS;
	public static final Block YELLOW_STAINED_GLASS = Blocks.YELLOW_STAINED_GLASS;
	public static final Block ORANGE_STAINED_GLASS = Blocks.ORANGE_STAINED_GLASS;
	public static final Block BLACK_STAINED_GLASS = Blocks.BLACK_STAINED_GLASS;
	public static final Block WHITE_CONCRETE = Blocks.WHITE_CONCRETE;
	public static final Block BLACK_CONCRETE = Blocks.BLACK_CONCRETE;
	// #else
	//? public static final Block WHITE_STAINED_GLASS = Blocks.STAINED_GLASS.white();
	//? public static final Block LIGHT_BLUE_STAINED_GLASS = Blocks.STAINED_GLASS.lightBlue();
	//? public static final Block PURPLE_STAINED_GLASS = Blocks.STAINED_GLASS.purple();
	//? public static final Block MAGENTA_STAINED_GLASS = Blocks.STAINED_GLASS.magenta();
	//? public static final Block YELLOW_STAINED_GLASS = Blocks.STAINED_GLASS.yellow();
	//? public static final Block ORANGE_STAINED_GLASS = Blocks.STAINED_GLASS.orange();
	//? public static final Block BLACK_STAINED_GLASS = Blocks.STAINED_GLASS.black();
	//? public static final Block WHITE_CONCRETE = Blocks.CONCRETE.white();
	//? public static final Block BLACK_CONCRETE = Blocks.CONCRETE.black();
	// #endif

	/** Bloc impossible à pousser par un piston (le portail). */
	// #if 1.21.11
	public static final PushReaction PUSH_BLOCKED = PushReaction.BLOCK;
	// #else
	//? public static final PushReaction PUSH_BLOCKED = PushReaction.IMMOVEABLE;
	// #endif

	/** Bloc dont on voit à travers et dans lequel on n'étouffe pas (feuilles). */
	public static BlockBehaviour.Properties seeThrough(BlockBehaviour.Properties p) {
		// #if 1.21.11
		return p.isSuffocating((st, l, pos) -> false).isViewBlocking((st, l, pos) -> false);
		// #else
		//? return p.isSuffocating((st, l, pos) -> false).isViewBlocking((st, l, pos, box) -> false);
		// #endif
	}

	// ---------------------------------------------------------------- entités et joueurs
	/** Le jeu envoie tout de suite au client le nouvel élan de l'entité. */
	public static void markHurt(Entity e) {
		// #if 1.21.11
		e.hurtMarked = true;
		// #else
		//? e.syncVelocity = true;
		// #endif
	}

	/** Message au-dessus de la barre d'objets. */
	public static void overlay(Player p, Component message) {
		// #if 1.21.11
		p.displayClientMessage(message, true);
		// #else
		//? p.sendOverlayMessage(message);
		// #endif
	}

	public static void swing(LivingEntity e, InteractionHand hand) {
		// #if 1.21.11
		e.swing(hand, true);
		// #else
		//? e.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		// #endif
	}

	public static Set<String> tags(Entity e) {
		// #if 1.21.11
		return e.getTags();
		// #else
		//? return e.entityTags();
		// #endif
	}

	public static void setInvulnerableTime(Entity e, int ticks) {
		// #if 1.21.11
		e.invulnerableTime = ticks;
		// #else
		//? e.setInvulnerableTime(ticks);
		// #endif
	}

	public static Component itemName(ItemStack stack) {
		// #if 1.21.11
		return stack.getItem().getName();
		// #else
		//? return stack.getItem().getName(new ItemStack(stack.getItem()));
		// #endif
	}

	public static ServerBossEvent bossEvent(Component name, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
		// #if 1.21.11
		return new ServerBossEvent(name, color, overlay);
		// #else
		//? return new ServerBossEvent(java.util.UUID.randomUUID(), name, color, overlay);
		// #endif
	}

	/** Rend un objet au joueur (dans l'inventaire, sinon par terre à ses pieds). */
	public static void give(Player p, ItemStack stack) {
		if (!p.getInventory().add(stack) && !stack.isEmpty() && p.level() instanceof net.minecraft.server.level.ServerLevel level) {
			p.spawnAtLocation(level, stack);
		}
	}

	/** Propriétés d'une pioche (niveau netherite : mine tous les blocs). */
	public static net.minecraft.world.item.Item.Properties pickaxe(net.minecraft.world.item.Item.Properties p) {
		return p.pickaxe(net.minecraft.world.item.ToolMaterial.NETHERITE, 5f, -2.8f);
	}

	// ---------------------------------------------------------------- recettes
	public static <T extends RecipeInput> ItemStack assemble(Recipe<T> recipe, T input, HolderLookup.Provider registries) {
		// #if 1.21.11
		return recipe.assemble(input, registries);
		// #else
		//? return recipe.assemble(input);
		// #endif
	}

	// ---------------------------------------------------------------- onglets créatifs
	public static CreativeModeTab.Builder tabBuilder() {
		// #if 1.21.11
		return net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup.builder();
		// #else
		//? return net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab.builder();
		// #endif
	}

	public static void addToTab(ResourceKey<CreativeModeTab> tab, ItemLike item) {
		// #if 1.21.11
		net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(tab).register(entries -> entries.accept(item));
		// #else
		//? net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(tab).register(out -> out.accept(item));
		// #endif
	}

	// ---------------------------------------------------------------- réseau
	public static net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry<RegistryFriendlyByteBuf> toServer() {
		// #if 1.21.11
		return net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S();
		// #else
		//? return net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay();
		// #endif
	}

	public static net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry<RegistryFriendlyByteBuf> toClient() {
		// #if 1.21.11
		return net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C();
		// #else
		//? return net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay();
		// #endif
	}
}
