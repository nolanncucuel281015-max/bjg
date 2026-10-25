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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Armure — « Nova Prismatique » : un diamant géant se forme au-dessus de toi puis explose
 * en une onde de lumière arc-en-ciel de 10 blocs qui aveugle et pulvérise les ennemis.
 */
public class PrismNovaEffect extends TimedEffect {
	private static final int CHARGE = 15;
	private static final double RADIUS = 10.0;
	private static final int[] RAINBOW = {0xFF4040, 0xFFA040, 0xFFF040, 0x40FF60, 0x40E0FF, 0x6060FF, 0xD060FF};

	private final ServerPlayer player;
	private final Display.BlockDisplay gem;
	private final Set<UUID> hit = new HashSet<>();

	public PrismNovaEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		this.gem = track(Visuals.block(level, player.position().add(0, 3.5, 0), Blocks.DIAMOND_BLOCK.defaultBlockState()));
		Visuals.transform(gem, new org.joml.Vector3f(), new Quaternionf(), 0.01f, 0);
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0f, 1.5f);
	}

	@Override
	protected boolean tick() {
		if (player.isRemoved()) {
			return true;
		}
		Vec3 c = player.position();
		if (age < CHARGE) {
			// Le diamant grossit en tournant sur sa pointe, la lumière converge vers lui.
			float s = 0.2f + age * 0.1f;
			Quaternionf q = new Quaternionf().rotateY(age * 0.4f).rotateX((float) (Math.PI / 4)).rotateZ((float) (Math.PI / 4));
			gem.setPos(c.add(0, 3.5, 0));
			Visuals.transform(gem, Visuals.centered(q, s), q, s, 1);
			level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 3.5, c.z, 6, 2.5, 2.5, 2.5, -0.15);
			if (age % 4 == 0) {
				level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, 0.5f + age * 0.1f);
			}
			return false;
		}
		if (age == CHARGE) {
			gem.discard();
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 3.5, c.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y + 3.5, c.z, 120, 0.3, 0.3, 0.3, 1.2);
			level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.5f, 1.4f);
			level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.0f, 1.5f);
			FX.shake(level, c, 24, 1.2f, 10);
		}
		// Onde arc-en-ciel qui s'étend.
		double r = (age - CHARGE + 1) * RADIUS / 8.0;
		for (int band = 0; band < RAINBOW.length; band++) {
			FX.ring(level, new DustParticleOptions(RAINBOW[band], 1.8f), c.add(0, 0.3 + band * 0.25, 0), r - band * 0.15, (int) (r * 8) + 8);
		}
		for (LivingEntity target : FX.livingAround(level, player, c, r + 0.5)) {
			if (hit.add(target.getUUID())) {
				FX.hit(level, player, target, 28.0f);
				target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
				target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
				Vec3 out = target.position().subtract(c).normalize().scale(1.5);
				FX.push(target, out.x, 0.6, out.z);
			}
		}
		return r >= RADIUS;
	}
}
