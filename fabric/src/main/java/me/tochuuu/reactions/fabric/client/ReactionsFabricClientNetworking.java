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
        KeyBindingHelper.registerKeyBinding(ReactionsClient.manualCloseEyesKey());
        KeyBindingHelper.registerKeyBinding(ReactionsClient.manualSquintEyesKey());
        KeyBindingHelper.registerKeyBinding(ReactionsClient.manualLookEyesKey());
        ClientPlayNetworking.registerGlobalReceiver(ReactionsNetworking.EYE_CONFIG_S2C, (client, handler, buf, responseSender) -> {
            ReactionsNetworking.EyeConfigS2CPayload payload = ReactionsNetworking.EyeConfigS2CPayload.read(buf);
            client.execute(() -> ReactionsNetworking.handleClientboundConfig(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(ReactionsNetworking.EYE_FOCUS_S2C, (client, handler, buf, responseSender) -> {
            ReactionsNetworking.EyeFocusS2CPayload payload = ReactionsNetworking.EyeFocusS2CPayload.read(buf);
            client.execute(() -> ReactionsNetworking.handleClientboundEyeFocus(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(ReactionsNetworking.MANUAL_EYE_S2C, (client, handler, buf, responseSender) -> {
            ReactionsNetworking.ManualEyeS2CPayload payload = ReactionsNetworking.ManualEyeS2CPayload.read(buf);
            client.execute(() -> ReactionsNetworking.handleClientboundManualEye(payload));
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ReactionsNetworking.onClientJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ReactionsNetworking.onClientQuit());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ReactionsClient.onClientTick(client);
            ReactionsNetworking.onClientTick();
        });
    }

    @Override
    public boolean canSendToServer() {
        return ClientPlayNetworking.canSend(ReactionsNetworking.EYE_CONFIG_C2S);
    }

    @Override
    public boolean canSendEyeFocusToServer() {
        return ClientPlayNetworking.canSend(ReactionsNetworking.EYE_FOCUS_C2S);
    }

    @Override
    public boolean canSendManualEyeToServer() {
        return ClientPlayNetworking.canSend(ReactionsNetworking.MANUAL_EYE_C2S);
    }

    @Override
    public void sendToServer(ReactionsNetworking.EyeConfigC2SPayload payload) {
        ClientPlayNetworking.send(ReactionsNetworking.EYE_CONFIG_C2S, buffer(payload::write));
    }

    @Override
    public void sendEyeFocusToServer(ReactionsNetworking.EyeFocusC2SPayload payload) {
        ClientPlayNetworking.send(ReactionsNetworking.EYE_FOCUS_C2S, buffer(payload::write));
    }

    @Override
    public void sendManualEyeToServer(ReactionsNetworking.ManualEyeC2SPayload payload) {
        ClientPlayNetworking.send(ReactionsNetworking.MANUAL_EYE_C2S, buffer(payload::write));
    }
}
