package me.tochuuu.reactions.neoforge;

import me.tochuuu.reactions.Reactions;
import me.tochuuu.reactions.client.ReactionsClient;
import me.tochuuu.reactions.network.ReactionsNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

public final class ReactionsNeoForgeNetworking implements ReactionsNetworking.Platform {
    private static final ReactionsNeoForgeNetworking INSTANCE = new ReactionsNeoForgeNetworking();
    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(Reactions.MOD_ID, "main"),
        () -> PROTOCOL_VERSION,
        NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION),
        NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION)
    );
    private static int nextPacketId;
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
        registerMessages();

        MinecraftForge.EVENT_BUS.addListener(ReactionsNeoForgeNetworking::onPlayerJoin);
        MinecraftForge.EVENT_BUS.addListener(ReactionsNeoForgeNetworking::onPlayerQuit);
        MinecraftForge.EVENT_BUS.addListener(ReactionsNeoForgeNetworking::onServerTick);
    }

    public static void initClient(IEventBus modEventBus) {
        if (clientInitialized) {
            return;
        }
        clientInitialized = true;

        modEventBus.addListener(ReactionsNeoForgeNetworking::registerKeyMappings);
        MinecraftForge.EVENT_BUS.addListener(ReactionsNeoForgeNetworking::onClientJoin);
        MinecraftForge.EVENT_BUS.addListener(ReactionsNeoForgeNetworking::onClientQuit);
        MinecraftForge.EVENT_BUS.addListener(ReactionsNeoForgeNetworking::onClientTick);
    }

    private static void registerMessages() {
        CHANNEL.registerMessage(nextPacketId++, ReactionsNetworking.EyeConfigC2SPayload.class, ReactionsNetworking.EyeConfigC2SPayload::write, ReactionsNetworking.EyeConfigC2SPayload::read, ReactionsNeoForgeNetworking::handleConfigToServer, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextPacketId++, ReactionsNetworking.EyeConfigS2CPayload.class, ReactionsNetworking.EyeConfigS2CPayload::write, ReactionsNetworking.EyeConfigS2CPayload::read, ReactionsNeoForgeNetworking::handleConfigToClient, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextPacketId++, ReactionsNetworking.EyeFocusC2SPayload.class, ReactionsNetworking.EyeFocusC2SPayload::write, ReactionsNetworking.EyeFocusC2SPayload::read, ReactionsNeoForgeNetworking::handleFocusToServer, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextPacketId++, ReactionsNetworking.EyeFocusS2CPayload.class, ReactionsNetworking.EyeFocusS2CPayload::write, ReactionsNetworking.EyeFocusS2CPayload::read, ReactionsNeoForgeNetworking::handleFocusToClient, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private static void handleConfigToServer(ReactionsNetworking.EyeConfigC2SPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ReactionsNetworking.handleServerboundConfig(payload, player);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleFocusToServer(ReactionsNetworking.EyeFocusC2SPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ReactionsNetworking.handleServerboundEyeFocus(payload, player);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleConfigToClient(ReactionsNetworking.EyeConfigS2CPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ReactionsNetworking.handleClientboundConfig(payload));
        context.setPacketHandled(true);
    }

    private static void handleFocusToClient(ReactionsNetworking.EyeFocusS2CPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ReactionsNetworking.handleClientboundEyeFocus(payload));
        context.setPacketHandled(true);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
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

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ReactionsNetworking.onServerTick(event.getServer());
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ReactionsClient.onClientTick(Minecraft.getInstance());
            ReactionsNetworking.onClientTick();
        }
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
        return connection != null && canUseChannel(connection.getConnection());
    }

    @Override
    public boolean canSendToPlayer(ServerPlayer player) {
        return canUseChannel(player.connection.connection);
    }

    @Override
    public boolean canSendEyeFocusToServer() {
        return canSendToServer();
    }

    @Override
    public boolean canSendEyeFocusToPlayer(ServerPlayer player) {
        return canSendToPlayer(player);
    }

    private static boolean canUseChannel(Connection connection) {
        return connection != null && CHANNEL.isRemotePresent(connection);
    }

    @Override
    public void sendToServer(ReactionsNetworking.EyeConfigC2SPayload payload) {
        CHANNEL.sendToServer(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, ReactionsNetworking.EyeConfigS2CPayload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    @Override
    public void sendEyeFocusToServer(ReactionsNetworking.EyeFocusC2SPayload payload) {
        CHANNEL.sendToServer(payload);
    }

    @Override
    public void sendEyeFocusToPlayer(ServerPlayer player, ReactionsNetworking.EyeFocusS2CPayload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }
}
