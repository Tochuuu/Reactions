package me.tochuuu.reactions.neoforge;

import me.tochuuu.reactions.client.ReactionsClient;
import me.tochuuu.reactions.client.ReactionsConfigScreen;
import me.tochuuu.reactions.network.ReactionsNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.PacketDistributor;

final class ReactionsNeoForgeClient {
    private static boolean initialized;

    private ReactionsNeoForgeClient() {
    }

    static void init(IEventBus modEventBus) {
        if (initialized) {
            return;
        }
        initialized = true;
        ReactionsClient.init();
        modEventBus.addListener(ReactionsNeoForgeClient::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(ReactionsNeoForgeClient::onClientJoin);
        NeoForge.EVENT_BUS.addListener(ReactionsNeoForgeClient::onClientQuit);
        NeoForge.EVENT_BUS.addListener(ReactionsNeoForgeClient::onClientTick);
        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class, () -> (container, parent) -> new ReactionsConfigScreen(parent));
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ReactionsClient.openConfigKey());
        event.register(ReactionsClient.manualCloseEyesKey());
        event.register(ReactionsClient.manualSquintEyesKey());
        event.register(ReactionsClient.manualLookEyesKey());
        event.register(ReactionsClient.manualRaiseEyebrowKey());
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        ReactionsClient.onClientTick(Minecraft.getInstance());
        ReactionsNetworking.onClientTick();
    }

    private static void onClientJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        ReactionsNetworking.onClientJoin();
    }

    private static void onClientQuit(ClientPlayerNetworkEvent.LoggingOut event) {
        ReactionsNetworking.onClientQuit();
    }

    static boolean canSendToServer(CustomPacketPayload.Type<?> type) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection instanceof ICommonPacketListener listener && ReactionsNeoForgeNetworking.canUseChannel(listener, type);
    }

    static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}
