package me.tochuuu.reactions.mixin;

import me.tochuuu.reactions.client.AdvancementMouthReaction;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdvancementToast.class)
public abstract class AdvancementToastMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void reactions$triggerMouthReaction(AdvancementHolder advancement, CallbackInfo ci) {
        AdvancementMouthReaction.trigger();
    }
}
