package com.diamondaltar.mixin;

import com.diamondaltar.ModComponents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Les objets infusés ne perdent jamais de durabilité, quoi qu'il arrive. */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@Inject(method = "setDamageValue", at = @At("HEAD"), cancellable = true, require = 0)
	private void diamondaltar$noWear(int damage, CallbackInfo ci) {
		ItemStack self = (ItemStack) (Object) this;
		if (damage > 0 && ModComponents.isInfused(self)) {
			ci.cancel();
		}
	}
}
