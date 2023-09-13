package net.fabricmc.swiftedit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.fabricmc.swiftedit.SwiftEdit;

import net.minecraft.entity.player.PlayerInventory;

// Credit to TechPro424 for hotbar scroll disabling code
// https://github.com/TechPro424/Disable-Hotbar-Scrolling
@Mixin(PlayerInventory.class)
public abstract class ScrollDisableMixin {
	@Inject(at = @At("HEAD"), method = "Lnet/minecraft/entity/player/PlayerInventory;scrollInHotbar(D)V", cancellable = true)
	private void disableScrolling(double scrollAmount, CallbackInfo callbackInfo) {
		if(SwiftEdit.regionSetMode == 3){
			callbackInfo.cancel();
		}
	}
}