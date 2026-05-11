package me.tochuuu.reactions.fabric.client;

import me.tochuuu.reactions.client.ReactionsClient;
import me.tochuuu.reactions.fabric.ReactionsFabricNetworking;
import me.tochuuu.reactions.network.ReactionsNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ReactionsFabricClientNetworking extends ReactionsFabricNetworking {
    private static final ReactionsFabricClientNetworking INSTANCE = new ReactionsFabricClientNetworking();
    private static boolean initialized;

    private ReactionsFabricClientNetworking() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ReactionsNetworking.setPlatform(INSTANCE);

        KeyBindingHelper.registerKeyBinding(ReactionsClient.openConfigKey());
        ClientPlayNetworking.registerGlobalReceiver(ReactionsNetworking.EyeConfigS2CPayload.TYPE, (payload, context) -> ReactionsNetworking.handleClientboundConfig(payload));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ReactionsNetworking.onClientJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ReactionsNetworking.onClientQuit());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ReactionsClient.onClientTick(client);
            ReactionsNetworking.onClientTick();
        });
    }

    @Override
    public boolean canSendToServer() {
        return ClientPlayNetworking.canSend(ReactionsNetworking.EyeConfigC2SPayload.TYPE);
    }

    @Override
    public void sendToServer(ReactionsNetworking.EyeConfigC2SPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
