package me.tochuuu.reactions.mixin;

import me.tochuuu.reactions.client.AdvancementMouthReaction;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleSystemChat", at = @At("TAIL"))
    private void reactions$triggerAdvancementMouthFromChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (!packet.overlay()) {
            AdvancementMouthReaction.triggerFromChat(packet.content());
        }
    }
}
