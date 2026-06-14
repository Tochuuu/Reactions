package me.tochuuu.reactions.mixin;

import me.tochuuu.reactions.client.PlayerActionAnimationState;
import me.tochuuu.reactions.client.PlayerEyeRenderLayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void reactions$addEyeLayer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        RenderLayerParent<PlayerRenderState, PlayerModel> parent = (RenderLayerParent<PlayerRenderState, PlayerModel>) (Object) this;
        LivingEntityRendererAccessor<PlayerRenderState, PlayerModel> accessor = (LivingEntityRendererAccessor<PlayerRenderState, PlayerModel>) (Object) this;
        accessor.reactions$addLayer(new PlayerEyeRenderLayer(parent));
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V", at = @At("TAIL"))
    private void reactions$captureActionState(AbstractClientPlayer player, PlayerRenderState state, float partialTick, CallbackInfo ci) {
        PlayerActionAnimationState.capture(player, state);
    }
}
