package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Armure — « Avatar de Diamant » : pendant 15 s tu deviens un titan de diamant : Force III,
 * Vitesse III, Résistance III, Régénération II, une couronne de 4 épées tourne au-dessus de ta tête.
 */
public class AvatarEffect extends TimedEffect {
	private static final int DURATION = 300;
	private static final DustParticleOptions AURA = new DustParticleOptions(0x7FF4FF, 1.4f);
	private static final DustParticleOptions GOLD = new DustParticleOptions(0xFFD84A, 1.0f);

	private final ServerPlayer player;
	private final Display.ItemDisplay[] crown = new Display.ItemDisplay[4];

	public AvatarEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		sword.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		for (int i = 0; i < crown.length; i++) {
			crown[i] = track(Visuals.item(level, player.position(), sword));
			Visuals.transform(crown[i], new Vector3f(), Visuals.pointing(new Vec3(0, -1, 0), 0f), 0.01f, 0);
		}
		player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, DURATION, 2, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, DURATION, 2, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, DURATION, 2, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, DURATION, 1, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.HASTE, DURATION, 2, false, false, true));
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY(1.0), player.getZ(), 100, 0.5, 1.0, 0.5, 0.6);
		level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.8f, 1.6f);
		level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved() || !player.isAlive()) {
			return true;
		}
		Vec3 head = player.position().add(0, player.getBbHeight() + 0.9, 0);
		boolean ending = age >= DURATION - 6;
		for (int i = 0; i < crown.length; i++) {
			double a = age * 0.08 + i * Math.PI / 2;
			Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
			crown[i].setPos(head);
			Visuals.transform(crown[i], out.scale(0.9).toVector3f(), Visuals.pointing(new Vec3(0, -1, 0), (float) a), ending ? 0.01f : 0.9f, ending ? 6 : 1);
		}
		if (age % 2 == 0) {
			for (int k = 0; k < 2; k++) {
				double a = age * 0.3 + k * Math.PI;
				level.sendParticles(AURA, player.getX() + Math.cos(a) * 0.8, player.getY() + (age % 20) / 10.0, player.getZ() + Math.sin(a) * 0.8, 1, 0, 0, 0, 0);
			}
			level.sendParticles(GOLD, player.getX(), player.getY(0.5), player.getZ(), 1, 0.4, 0.6, 0.4, 0);
		}
		return age >= DURATION;
	}
}
