package me.tochuuu.reactions.fabric;

import me.tochuuu.reactions.network.ReactionsNetworking;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

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

        ServerPlayNetworking.registerGlobalReceiver(ReactionsNetworking.EYE_CONFIG_C2S, (server, player, handler, buf, responseSender) -> {
            ReactionsNetworking.EyeConfigC2SPayload payload = ReactionsNetworking.EyeConfigC2SPayload.read(buf);
            server.execute(() -> ReactionsNetworking.handleServerboundConfig(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(ReactionsNetworking.EYE_FOCUS_C2S, (server, player, handler, buf, responseSender) -> {
            ReactionsNetworking.EyeFocusC2SPayload payload = ReactionsNetworking.EyeFocusC2SPayload.read(buf);
            server.execute(() -> ReactionsNetworking.handleServerboundEyeFocus(payload, player));
        });
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
        return ServerPlayNetworking.canSend(player, ReactionsNetworking.EYE_CONFIG_S2C)
            || ReactionsNetworking.hasServerConfig(player.getUUID());
    }

    @Override
    public boolean canSendEyeFocusToServer() {
        return false;
    }

    @Override
    public boolean canSendEyeFocusToPlayer(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, ReactionsNetworking.EYE_FOCUS_S2C);
    }

    @Override
    public void sendToServer(ReactionsNetworking.EyeConfigC2SPayload payload) {
        throw new UnsupportedOperationException("Cannot send serverbound packets from a dedicated server");
    }

    @Override
    public void sendToPlayer(ServerPlayer player, ReactionsNetworking.EyeConfigS2CPayload payload) {
        if (ServerPlayNetworking.canSend(player, ReactionsNetworking.EYE_CONFIG_S2C)) {
            ServerPlayNetworking.send(player, ReactionsNetworking.EYE_CONFIG_S2C, buffer(payload::write));
            return;
        }

        player.connection.send(new ClientboundCustomPayloadPacket(ReactionsNetworking.EYE_CONFIG_S2C, buffer(payload::write)));
    }

    @Override
    public void sendEyeFocusToServer(ReactionsNetworking.EyeFocusC2SPayload payload) {
        throw new UnsupportedOperationException("Cannot send serverbound packets from a dedicated server");
    }

    @Override
    public void sendEyeFocusToPlayer(ServerPlayer player, ReactionsNetworking.EyeFocusS2CPayload payload) {
        ServerPlayNetworking.send(player, ReactionsNetworking.EYE_FOCUS_S2C, buffer(payload::write));
    }

    protected static FriendlyByteBuf buffer(Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        writer.accept(buf);
        return buf;
    }
}
