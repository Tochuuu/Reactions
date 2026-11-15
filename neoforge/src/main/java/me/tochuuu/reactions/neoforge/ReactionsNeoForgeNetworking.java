package me.tochuuu.reactions.neoforge;

import me.tochuuu.reactions.network.ReactionsNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class ReactionsNeoForgeNetworking implements ReactionsNetworking.Platform {
    private static final ReactionsNeoForgeNetworking INSTANCE = new ReactionsNeoForgeNetworking();
    private static boolean initialized;

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
            .playToServer(ReactionsNetworking.ManualEyeC2SPayload.TYPE, ReactionsNetworking.ManualEyeC2SPayload.STREAM_CODEC, (payload, context) -> {
                if (context.player() instanceof ServerPlayer serverPlayer) {
                    ReactionsNetworking.handleServerboundManualEye(payload, serverPlayer);
                }
            })
            .playToClient(ReactionsNetworking.EyeConfigS2CPayload.TYPE, ReactionsNetworking.EyeConfigS2CPayload.STREAM_CODEC, (payload, context) -> {
                ReactionsNetworking.handleClientboundConfig(payload);
            })
            .playToClient(ReactionsNetworking.EyeFocusS2CPayload.TYPE, ReactionsNetworking.EyeFocusS2CPayload.STREAM_CODEC, (payload, context) -> {
                ReactionsNetworking.handleClientboundEyeFocus(payload);
            })
            .playToClient(ReactionsNetworking.ManualEyeS2CPayload.TYPE, ReactionsNetworking.ManualEyeS2CPayload.STREAM_CODEC, (payload, context) -> {
                ReactionsNetworking.handleClientboundManualEye(payload);
            });
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

    @Override
    public boolean canSendToServer() {
        return FMLEnvironment.dist.isClient() && ReactionsNeoForgeClient.canSendToServer(ReactionsNetworking.EyeConfigC2SPayload.TYPE);
    }

    @Override
    public boolean canSendToPlayer(ServerPlayer player) {
        return player.connection instanceof ICommonPacketListener listener && canUseChannel(listener, ReactionsNetworking.EyeConfigS2CPayload.TYPE);
    }

    @Override
    public boolean canSendEyeFocusToServer() {
        return FMLEnvironment.dist.isClient() && ReactionsNeoForgeClient.canSendToServer(ReactionsNetworking.EyeFocusC2SPayload.TYPE);
    }

    @Override
    public boolean canSendEyeFocusToPlayer(ServerPlayer player) {
        return player.connection instanceof ICommonPacketListener listener && canUseChannel(listener, ReactionsNetworking.EyeFocusS2CPayload.TYPE);
    }

    @Override
    public boolean canSendManualEyeToServer() {
        return FMLEnvironment.dist.isClient() && ReactionsNeoForgeClient.canSendToServer(ReactionsNetworking.ManualEyeC2SPayload.TYPE);
    }

    @Override
    public boolean canSendManualEyeToPlayer(ServerPlayer player) {
        return player.connection instanceof ICommonPacketListener listener
            && (canUseAdvertisedChannel(listener, ReactionsNetworking.ManualEyeS2CPayload.TYPE)
            || ReactionsNetworking.hasManualEyeCapability(player.getUUID()) && isOtherConnection(listener));
    }

    static boolean canUseChannel(ICommonPacketListener listener, net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<?> type) {
        return canUseAdvertisedChannel(listener, type) || isOtherConnection(listener);
    }

    private static boolean canUseAdvertisedChannel(ICommonPacketListener listener, net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<?> type) {
        return listener.hasChannel(type);
    }

    private static boolean isOtherConnection(ICommonPacketListener listener) {
        try {
            return listener.getConnectionType().isOther();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public void sendToServer(ReactionsNetworking.EyeConfigC2SPayload payload) {
        if (FMLEnvironment.dist.isClient()) {
            ReactionsNeoForgeClient.sendToServer(payload);
        }
    }

    @Override
    public void sendToPlayer(ServerPlayer player, ReactionsNetworking.EyeConfigS2CPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendEyeFocusToServer(ReactionsNetworking.EyeFocusC2SPayload payload) {
        if (FMLEnvironment.dist.isClient()) {
            ReactionsNeoForgeClient.sendToServer(payload);
        }
    }

    @Override
    public void sendEyeFocusToPlayer(ServerPlayer player, ReactionsNetworking.EyeFocusS2CPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendManualEyeToServer(ReactionsNetworking.ManualEyeC2SPayload payload) {
        if (FMLEnvironment.dist.isClient()) {
            ReactionsNeoForgeClient.sendToServer(payload);
        }
    }

    @Override
    public void sendManualEyeToPlayer(ServerPlayer player, ReactionsNetworking.ManualEyeS2CPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
