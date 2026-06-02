package me.tochuuu.reactions.neoforge;

import me.tochuuu.reactions.client.ReactionsClient;
import me.tochuuu.reactions.network.ReactionsNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class ReactionsNeoForgeNetworking implements ReactionsNetworking.Platform {
    private static final ReactionsNeoForgeNetworking INSTANCE = new ReactionsNeoForgeNetworking();
    private static boolean initialized;
    private static boolean clientInitialized;

    private ReactionsNeoForgeNetworking() {
    }

    public static void init(IEventBus modEventBus) {
        if (initialized) {
            return;
        }
        initialized = true;
        ReactionsNetworking.setPlatform(INSTANCE);

        modEventBus.addListener(ReactionsNeoForgeNetworking::registerPayloads);
        NeoForge.EVENT_BUS.addListener(ReactionsNeoForgeNetworking::onPlayerJoin);
        NeoForge.EVENT_BUS.addListener(ReactionsNeoForgeNetworking::onPlayerQuit);
        NeoForge.EVENT_BUS.addListener(ReactionsNeoForgeNetworking::onServerTick);
    }

    public static void initClient(IEventBus modEventBus) {
        if (clientInitialized) {
            return;
        }
        clientInitialized = true;

        modEventBus.addListener(ReactionsNeoForgeNetworking::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(ReactionsNeoForgeNetworking::onClientJoin);
        NeoForge.EVENT_BUS.addListener(ReactionsNeoForgeNetworking::onClientQuit);
        NeoForge.EVENT_BUS.addListener(ReactionsNeoForgeNetworking::onClientTick);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional()
            .playToServer(ReactionsNetworking.EyeConfigC2SPayload.TYPE, ReactionsNetworking.EyeConfigC2SPayload.STREAM_CODEC, (payload, context) -> {
                if (context.player() instanceof ServerPlayer serverPlayer) {
                    ReactionsNetworking.handleServerboundConfig(payload, serverPlayer);
                }
            })
            .playToServer(ReactionsNetworking.EyeFocusC2SPayload.TYPE, ReactionsNetworking.EyeFocusC2SPayload.STREAM_CODEC, (payload, context) -> {
                if (context.player() instanceof ServerPlayer serverPlayer) {
                    ReactionsNetworking.handleServerboundEyeFocus(payload, serverPlayer);
                }
            })
            .playToClient(ReactionsNetworking.EyeConfigS2CPayload.TYPE, ReactionsNetworking.EyeConfigS2CPayload.STREAM_CODEC, (payload, context) -> {
                ReactionsNetworking.handleClientboundConfig(payload);
            })
            .playToClient(ReactionsNetworking.EyeFocusS2CPayload.TYPE, ReactionsNetworking.EyeFocusS2CPayload.STREAM_CODEC, (payload, context) -> {
                ReactionsNetworking.handleClientboundEyeFocus(payload);
            });
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(ReactionsClient.openConfigKey().getCategory());
        event.register(ReactionsClient.openConfigKey());
    }

    private static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ReactionsNetworking.onServerPlayerJoin(player);
        }
    }

    private static void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ReactionsNetworking.onServerPlayerQuit(player);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        ReactionsNetworking.onServerTick(event.getServer());
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

    @Override
    public boolean canSendToServer() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection instanceof ICommonPacketListener listener && canUseChannel(listener, ReactionsNetworking.EyeConfigC2SPayload.TYPE);
    }

    @Override
    public boolean canSendToPlayer(ServerPlayer player) {
        return player.connection instanceof ICommonPacketListener listener && canUseChannel(listener, ReactionsNetworking.EyeConfigS2CPayload.TYPE);
    }

    @Override
    public boolean canSendEyeFocusToServer() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection instanceof ICommonPacketListener listener && canUseChannel(listener, ReactionsNetworking.EyeFocusC2SPayload.TYPE);
    }

    @Override
    public boolean canSendEyeFocusToPlayer(ServerPlayer player) {
        return player.connection instanceof ICommonPacketListener listener && canUseChannel(listener, ReactionsNetworking.EyeFocusS2CPayload.TYPE);
    }

    private static boolean canUseChannel(ICommonPacketListener listener, net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<?> type) {
        if (listener.hasChannel(type)) {
            return true;
        }
        try {
            return listener.getConnectionType().isOther();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public void sendToServer(ReactionsNetworking.EyeConfigC2SPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, ReactionsNetworking.EyeConfigS2CPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendEyeFocusToServer(ReactionsNetworking.EyeFocusC2SPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    @Override
    public void sendEyeFocusToPlayer(ServerPlayer player, ReactionsNetworking.EyeFocusS2CPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
