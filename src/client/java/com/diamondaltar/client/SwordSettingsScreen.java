package com.diamondaltar.client;

import com.diamondaltar.realms.CelestialAnnihilation;
import com.diamondaltar.realms.RealmContent;
import com.diamondaltar.realms.RealmEvents;
import com.diamondaltar.realms.SkyFx;
import com.diamondaltar.realms.SwordSettingsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Réglages de l'Épée des Sept Royaumes (touche Y) : on choisit l'attaque, la portée (10 à 100 000 blocs)
 * et la puissance (dégâts, 10 à 1 000 000) en cliquant directement sur la valeur voulue.
 */
public class SwordSettingsScreen extends Screen {
	private static final int W = 460;
	private static final int H = 252;
	private static final int PAD = 12;
	private static final int TEXT = 0xFFE8E6F4;
	private static final int MUTED = 0xFFA7A2C6;
	private static final int GOLD = 0xFFF3C867;

	private int mode;
	private int range;
	private int damage;

	public SwordSettingsScreen(ItemStack sword) {
		super(Component.translatable("diamondaltar.sword_settings.title"));
		this.mode = RealmEvents.swordMode(sword);
		this.range = RealmEvents.swordRange(sword);
		this.damage = RealmEvents.swordDamage(sword);
	}

	private int left() {
		return (width - W) / 2;
	}

	private int top() {
		return (height - H) / 2;
	}

	private static Component choice(Component text, boolean on) {
		return on ? Component.literal("▶").append(text).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
				: text.copy().withStyle(ChatFormatting.WHITE);
	}

	@Override
	protected void init() {
		int l = left(), t = top();
		// Attaque
		int n = RealmEvents.SWORD_MODES.length;
		int aw = (W - PAD * 2 - 4 * (n - 1)) / n;
		for (int i = 0; i < n; i++) {
			int index = i;
			Component name = Component.translatable("diamondaltar.seven_realms." + RealmEvents.SWORD_MODES[i] + ".name");
			addRenderableWidget(Button.builder(choice(name, i == mode), b -> set(index, range, damage))
					.bounds(l + PAD + i * (aw + 4), t + 38, aw, 18).build());
		}
		// Portée, sur deux lignes
		int nr = SkyFx.RANGES.length;
		int perRow = (nr + 1) / 2;
		int rw = (W - PAD * 2 - 2 * (perRow - 1)) / perRow;
		for (int i = 0; i < nr; i++) {
			int level = i + 1;
			int col = i % perRow, row = i / perRow;
			addRenderableWidget(Button.builder(choice(Component.literal(SkyFx.shortNumber(SkyFx.RANGES[i])), level == range),
					b -> set(mode, level, damage)).bounds(l + PAD + col * (rw + 2), t + 86 + row * 20, rw, 18).build());
		}
		// Puissance (dégâts)
		int nd = SkyFx.DAMAGES.length;
		int dw = (W - PAD * 2 - 2 * (nd - 1)) / nd;
		for (int i = 0; i < nd; i++) {
			int level = i + 1;
			addRenderableWidget(Button.builder(choice(Component.literal(SkyFx.shortNumber(SkyFx.DAMAGES[i])), level == damage),
					b -> set(mode, range, level)).bounds(l + PAD + i * (dw + 2), t + 154, dw, 18).build());
		}
		addRenderableWidget(Button.builder(Component.translatable("diamondaltar.sword_settings.done"), b -> onClose())
				.bounds(l + W / 2 - 40, t + H - 26, 80, 18).build());
	}

	/** Change un réglage : l'épée en main est mise à jour par le serveur, et l'interface se redessine. */
	private void set(int newMode, int newRange, int newDamage) {
		mode = newMode;
		range = newRange;
		damage = newDamage;
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.player != null && mc.player.getMainHandItem().is(RealmContent.SEVEN_REALMS_SWORD)) {
			ClientPlayNetworking.send(new SwordSettingsPayload(mode, range, damage));
		}
		rebuildWidgets();
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
		int l = left(), t = top();
		g.fill(l - 2, t - 2, l + W + 2, t + H + 2, 0xFFE0B040);
		g.fill(l, t, l + W, t + H, 0xF2100C20);
		g.fill(l + 4, t + 20, l + W - 4, t + 21, 0xFF4A3A18);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int l = left(), t = top();
		g.drawString(font, Component.translatable("diamondaltar.sword_settings.title").withStyle(ChatFormatting.BOLD), l + PAD, t + 7, GOLD, true);
		g.drawString(font, Component.translatable("diamondaltar.sword_settings.attack"), l + PAD, t + 26, TEXT, false);
		// La description de l'attaque, sur une ou deux lignes.
		int ly = t + 58;
		for (net.minecraft.util.FormattedCharSequence line : font.split(
				Component.translatable("diamondaltar.seven_realms." + RealmEvents.SWORD_MODES[mode] + ".desc"), W - PAD * 2)) {
			if (ly > t + 66) {
				break;
			}
			g.drawString(font, line, l + PAD, ly, MUTED, false);
			ly += 9;
		}
		g.drawString(font, Component.translatable("diamondaltar.sword_settings.range", SkyFx.range(range),
				CelestialAnnihilation.cooldown(range) / 20), l + PAD, t + 76, TEXT, false);
		g.drawString(font, Component.translatable("diamondaltar.sword_settings.damage", (int) SkyFx.damage(damage)), l + PAD, t + 142, TEXT, false);
		int hy = t + 178;
		for (net.minecraft.util.FormattedCharSequence line : font.split(Component.translatable("diamondaltar.sword_settings.hint"), W - PAD * 2)) {
			g.drawString(font, line, l + PAD, hy, MUTED, false);
			hy += 9;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
