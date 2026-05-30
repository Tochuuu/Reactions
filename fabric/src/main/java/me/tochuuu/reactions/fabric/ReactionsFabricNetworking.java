package me.tochuuu.reactions.fabric;

import me.tochuuu.reactions.network.ReactionsNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;

public class ReactionsFabricNetworking implements ReactionsNetworking.Platform {
    protected static final ReactionsFabricNetworking INSTANCE = new ReactionsFabricNetworking();
    private static boolean initialized;

    protected ReactionsFabricNetworking() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ReactionsNetworking.setPlatform(INSTANCE);

        PayloadTypeRegistry.serverboundPlay().register(ReactionsNetworking.EyeConfigC2SPayload.TYPE, ReactionsNetworking.EyeConfigC2SPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ReactionsNetworking.EyeConfigS2CPayload.TYPE, ReactionsNetworking.EyeConfigS2CPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ReactionsNetworking.EyeConfigC2SPayload.TYPE, (payload, context) -> ReactionsNetworking.handleServerboundConfig(payload, context.player()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ReactionsNetworking.onServerPlayerJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ReactionsNetworking.onServerPlayerQuit(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(ReactionsNetworking::onServerTick);
    }

    @Override
    public boolean canSendToServer() {
        return false;
    }

    @Override
    public boolean canSendToPlayer(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, ReactionsNetworking.EyeConfigS2CPayload.TYPE)
            || ReactionsNetworking.hasServerConfig(player.getUUID());
    }

    @Override
    public void sendToServer(ReactionsNetworking.EyeConfigC2SPayload payload) {
        throw new UnsupportedOperationException("Cannot send serverbound packets from a dedicated server");
    }

    @Override
    public void sendToPlayer(ServerPlayer player, ReactionsNetworking.EyeConfigS2CPayload payload) {
        if (ServerPlayNetworking.canSend(player, ReactionsNetworking.EyeConfigS2CPayload.TYPE)) {
            ServerPlayNetworking.send(player, payload);
            return;
        }

        player.connection.send(new ClientboundCustomPayloadPacket(payload));
    }
}
