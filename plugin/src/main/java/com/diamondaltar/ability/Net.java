package com.diamondaltar.ability;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Version plugin : le flash d'écran est un « titre » qui affiche une grande image blanche
 * (police du pack de ressources), teintée de la couleur voulue, qui s'estompe.
 * Le tremblement d'écran n'est pas possible sans mod côté client.
 */
final class Net {
	private static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("diamondaltar", "flash"));

	private Net() {
	}

	static void shake(ServerPlayer player, float strength, int ticks) {
	}

	static void flash(ServerPlayer player, int color, float strength, int ticks) {
		if (strength < 0.08f) {
			return;
		}
		String glyph = strength > 0.4f ? "" : "";
		Component c = Component.literal(glyph).withStyle(s -> s.withFont(FONT).withColor(color));
		player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 1, Math.max(2, ticks)));
		player.connection.send(new ClientboundSetTitleTextPacket(c));
	}
}
