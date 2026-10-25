package com.diamondaltar.ability;

import com.diamondaltar.effect.TimedEffect;
import com.diamondaltar.effect.Visuals;
import net.minecraft.world.entity.Display;
import org.joml.Vector3f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Épée — « Lames Fantômes » : 5 lames spectrales partent en éventail et transpercent tout sur 24 blocs. */
public class PhantomBladesEffect extends TimedEffect {
	private static final int BLADES = 5;
	private static final double SPEED = 1.6;
	private static final int DURATION = 15;
	private static final float DAMAGE = 20.0f;
	private static final DustParticleOptions VIOLET = new DustParticleOptions(0xC060FF, 1.4f);
	private static final DustParticleOptions CYAN = new DustParticleOptions(0x70F0FF, 1.0f);

	private final ServerPlayer player;
	private final Vec3[] pos = new Vec3[BLADES];
	private final Vec3[] dir = new Vec3[BLADES];
	private final boolean[] alive = new boolean[BLADES];
	private final Set<UUID> hit = new HashSet<>();
	private final Display.ItemDisplay[] swords = new Display.ItemDisplay[BLADES];

	public PhantomBladesEffect(ServerLevel level, ServerPlayer player) {
		super(level);
		this.player = player;
		Vec3 look = player.getLookAngle();
		Vec3 right = FX.right(FX.forward(player));
		for (int i = 0; i < BLADES; i++) {
			double spread = (i - (BLADES - 1) / 2.0) * 0.18;
			dir[i] = look.add(right.scale(spread)).normalize();
			pos[i] = player.getEyePosition().subtract(0, 0.3, 0);
			alive[i] = true;
			swords[i] = track(Visuals.item(level, pos[i], player.getMainHandItem()));
			Visuals.transform(swords[i], new Vector3f(), Visuals.pointing(dir[i], 0f), 1.4f, 0);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.5f, 1.6f);
		level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 1.6f);
	}

	@Override
	protected boolean tick() {
		boolean any = false;
		for (int i = 0; i < BLADES; i++) {
			if (!alive[i]) {
				continue;
			}
			any = true;
			// Tête chercheuse : la lame se tourne un peu vers l'ennemi le plus proche.
			Vec3 here = pos[i];
			LivingEntity prey = FX.livingAround(level, player, here, 6).stream()
					.filter(e -> !hit.contains(e.getUUID()))
					.min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(here)))
					.orElse(null);
			if (prey != null) {
				Vec3 want = prey.position().add(0, prey.getBbHeight() * 0.5, 0).subtract(here).normalize();
				dir[i] = dir[i].scale(0.7).add(want.scale(0.3)).normalize();
			}
			Vec3 from = pos[i];
			Vec3 to = from.add(dir[i].scale(SPEED));
			for (int s = 0; s < 4; s++) {
				Vec3 p = from.lerp(to, s / 4.0);
				level.sendParticles(VIOLET, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
				level.sendParticles(CYAN, p.x, p.y + 0.1, p.z, 1, 0, 0, 0, 0);
			}
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, to.x, to.y, to.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 1, 0.05, 0.05, 0.05, 0.01);
			for (LivingEntity target : FX.livingAround(level, player, to, 1.4)) {
				if (hit.add(target.getUUID())) {
					FX.hit(level, player, target, DAMAGE);
					FX.push(target, dir[i].x * 0.6, 0.2, dir[i].z * 0.6);
					level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY(0.5), target.getZ(), 12, 0.3, 0.4, 0.3, 0.2);
				}
			}
			if (FX.solid(level, BlockPos.containing(to))) {
				alive[i] = false;
				level.sendParticles(ParticleTypes.CRIT, to.x, to.y, to.z, 10, 0.2, 0.2, 0.2, 0.3);
			}
			pos[i] = to;
			swords[i].setPos(to);
			Visuals.transform(swords[i], new Vector3f(), Visuals.pointing(dir[i], age * 0.6f), 1.4f, 1);
			if (!alive[i]) {
				swords[i].discard();
			}
		}
		return !any || age >= DURATION;
	}
}
