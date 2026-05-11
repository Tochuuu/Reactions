package me.tochuuu.reactions.network;

import me.tochuuu.reactions.Reactions;
import me.tochuuu.reactions.client.ReactionsClientConfig;
import me.tochuuu.reactions.client.RemoteEyeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ReactionsNetworking {
    private static final ResourceLocation EYE_CONFIG_C2S = ResourceLocation.fromNamespaceAndPath(Reactions.MOD_ID, "eye_config_c2s");
    private static final ResourceLocation EYE_CONFIG_S2C = ResourceLocation.fromNamespaceAndPath(Reactions.MOD_ID, "eye_config_s2c");
    private static final int UPDATE = 0;
    private static final int REMOVE = 1;
    private static final int CLIENT_SYNC_RETRY_TICKS = 20 * 30;
    private static final int SERVER_SYNC_RETRY_TICKS = 20 * 30;
    private static final Map<Integer, RemoteEyeConfig> CLIENT_CONFIGS = new HashMap<>();
    private static final Map<UUID, RemoteEyeConfig> CLIENT_CONFIGS_BY_UUID = new HashMap<>();
    private static final Map<UUID, RemoteEyeConfig> SERVER_CONFIGS = new HashMap<>();
    private static final Map<UUID, Integer> SERVER_PENDING_SYNC = new HashMap<>();
    private static Platform platform;
    private static int clientSyncTicksRemaining;
    private static int clientSyncCooldown;
    private static boolean clientInitialized;

    private ReactionsNetworking() {
    }

    public static void setPlatform(Platform platform) {
        ReactionsNetworking.platform = platform;
    }

    public static void initClient() {
        if (clientInitialized) {
            return;
        }
        clientInitialized = true;
    }

    public static void handleServerboundConfig(EyeConfigC2SPayload payload, ServerPlayer serverPlayer) {
        RemoteEyeConfig serverConfig = withPlayerIdentity(payload.config(), serverPlayer.getUUID(), serverPlayer.getId());
        SERVER_CONFIGS.put(serverPlayer.getUUID(), serverConfig);
        sendKnownConfigs(serverPlayer);
        sendUpdateToReceivers(serverPlayer, serverConfig);
    }

    public static void handleClientboundConfig(EyeConfigS2CPayload payload) {
        if (payload.action() == UPDATE) {
            applyRemoteConfig(payload.config());
        } else if (payload.action() == REMOVE && payload.playerId() != null) {
            removeRemoteConfig(payload.playerId());
        }
    }

    public static void onServerPlayerJoin(ServerPlayer player) {
        SERVER_PENDING_SYNC.put(player.getUUID(), SERVER_SYNC_RETRY_TICKS);
    }

    public static void onServerPlayerQuit(ServerPlayer player) {
        SERVER_CONFIGS.remove(player.getUUID());
        SERVER_PENDING_SYNC.remove(player.getUUID());
        sendRemoveToReceivers(player);
    }

    public static void onServerTick(MinecraftServer server) {
        retryServerSync(server);
    }

    public static void onClientJoin() {
        CLIENT_CONFIGS.clear();
        CLIENT_CONFIGS_BY_UUID.clear();
        requestLocalConfigSync();
    }

    public static void onClientQuit() {
        CLIENT_CONFIGS.clear();
        CLIENT_CONFIGS_BY_UUID.clear();
        clientSyncTicksRemaining = 0;
        clientSyncCooldown = 0;
    }

    public static void onClientTick() {
        retryClientSync();
    }

    public static RemoteEyeConfig remoteConfig(int entityId) {
        RemoteEyeConfig config = CLIENT_CONFIGS.get(entityId);
        if (config != null) {
            return config;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }

        for (Player player : minecraft.level.players()) {
            if (player.getId() == entityId) {
                config = CLIENT_CONFIGS_BY_UUID.get(player.getUUID());
                if (config != null) {
                    RemoteEyeConfig resolved = withPlayerIdentity(config, player.getUUID(), entityId);
                    CLIENT_CONFIGS.put(entityId, resolved);
                    return resolved;
                }
            }
        }
        return null;
    }

    public static boolean hasRemoteConfig(int entityId) {
        return remoteConfig(entityId) != null;
    }

    public static void applyRemoteConfig(RemoteEyeConfig config) {
        CLIENT_CONFIGS.put(config.entityId(), config);
        if (config.playerId() != null) {
            CLIENT_CONFIGS_BY_UUID.put(config.playerId(), config);
        }
    }

    public static boolean canSyncWithServer() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && platform != null && platform.canSendToServer();
    }

    public static void sendLocalConfigToServer() {
        requestLocalConfigSync();
    }

    private static void requestLocalConfigSync() {
        clientSyncTicksRemaining = CLIENT_SYNC_RETRY_TICKS;
        clientSyncCooldown = 0;
        syncIntegratedServerHostConfig();
        trySendLocalConfigToServer();
    }

    private static void retryClientSync() {
        syncIntegratedServerHostConfig();
        if (clientSyncTicksRemaining <= 0) {
            return;
        }

        clientSyncTicksRemaining--;
        if (clientSyncCooldown > 0) {
            clientSyncCooldown--;
            return;
        }

        if (trySendLocalConfigToServer()) {
            clientSyncTicksRemaining = 0;
        } else {
            clientSyncCooldown = 10;
        }
    }

    private static boolean trySendLocalConfigToServer() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || platform == null || !platform.canSendToServer()) {
            return false;
        }

        platform.sendToServer(new EyeConfigC2SPayload(localConfig(minecraft.player.getUUID(), minecraft.player.getId())));
        return true;
    }

    private static void syncIntegratedServerHostConfig() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getSingleplayerServer() == null) {
            return;
        }

        ServerPlayer host = null;
        for (ServerPlayer player : minecraft.getSingleplayerServer().getPlayerList().getPlayers()) {
            if (player.getUUID().equals(minecraft.player.getUUID())) {
                host = player;
                break;
            }
        }
        if (host == null) {
            return;
        }

        RemoteEyeConfig hostConfig = localConfig(host.getUUID(), host.getId());
        if (hostConfig.equals(SERVER_CONFIGS.get(host.getUUID()))) {
            return;
        }

        SERVER_CONFIGS.put(host.getUUID(), hostConfig);
        sendUpdateToReceivers(host, hostConfig);
    }

    private static void retryServerSync(MinecraftServer server) {
        if (SERVER_PENDING_SYNC.isEmpty() || server.getTickCount() % 10 != 0) {
            return;
        }

        Iterator<Map.Entry<UUID, Integer>> iterator = SERVER_PENDING_SYNC.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            ServerPlayer player = null;
            for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
                if (candidate.getUUID().equals(entry.getKey())) {
                    player = candidate;
                    break;
                }
            }
            if (player == null || entry.getValue() <= 0) {
                iterator.remove();
                continue;
            }

            entry.setValue(entry.getValue() - 10);
            if (canSendToPlayer(player)) {
                for (RemoteEyeConfig config : SERVER_CONFIGS.values()) {
                    sendUpdate(player, config);
                }
                iterator.remove();
            }
        }
    }

    private static void sendUpdateToReceivers(ServerPlayer source, RemoteEyeConfig config) {
        for (ServerPlayer player : source.level().getServer().getPlayerList().getPlayers()) {
            if (canSendToPlayer(player)) {
                sendUpdate(player, config);
            }
        }
    }

    private static void sendKnownConfigs(ServerPlayer player) {
        if (!canSendToPlayer(player)) {
            return;
        }
        for (RemoteEyeConfig config : SERVER_CONFIGS.values()) {
            sendUpdate(player, config);
        }
    }

    private static void sendRemoveToReceivers(ServerPlayer source) {
        for (ServerPlayer player : source.level().getServer().getPlayerList().getPlayers()) {
            if (canSendToPlayer(player)) {
                sendRemove(player, source.getUUID());
            }
        }
    }

    private static boolean canSendToPlayer(ServerPlayer player) {
        return platform != null && platform.canSendToPlayer(player);
    }

    private static void sendUpdate(ServerPlayer player, RemoteEyeConfig config) {
        platform.sendToPlayer(player, EyeConfigS2CPayload.update(config));
    }

    private static void sendRemove(ServerPlayer player, UUID playerId) {
        platform.sendToPlayer(player, EyeConfigS2CPayload.remove(playerId));
    }

    private static void writeUpdateBody(RegistryFriendlyByteBuf buf, RemoteEyeConfig config) {
        buf.writeUUID(config.playerId());
        buf.writeVarInt(config.entityId());
        buf.writeByte(config.leftEyeX());
        buf.writeByte(config.leftEyeY());
        buf.writeByte(config.rightEyeX());
        buf.writeByte(config.rightEyeY());
        buf.writeByte(config.eyelidColorX());
        buf.writeByte(config.eyelidColorY());
        buf.writeByte(config.eyeWidth());
        buf.writeByte(config.eyeHeight());
    }

    private static RemoteEyeConfig readUpdateBody(RegistryFriendlyByteBuf buf) {
        return new RemoteEyeConfig(
            buf.readUUID(),
            buf.readVarInt(),
            buf.readUnsignedByte(),
            buf.readUnsignedByte(),
            buf.readUnsignedByte(),
            buf.readUnsignedByte(),
            buf.readUnsignedByte(),
            buf.readUnsignedByte(),
            buf.readUnsignedByte(),
            buf.readUnsignedByte()
        );
    }

    private static RemoteEyeConfig localConfig(UUID playerId, int entityId) {
        ReactionsClientConfig config = ReactionsClientConfig.get();
        return new RemoteEyeConfig(
            playerId,
            entityId,
            config.leftEyeX,
            config.leftEyeY,
            config.rightEyeX,
            config.rightEyeY,
            config.eyelidColorX,
            config.eyelidColorY,
            config.eyeWidth,
            config.eyeHeight
        );
    }

    private static RemoteEyeConfig withPlayerIdentity(RemoteEyeConfig config, UUID playerId, int entityId) {
        return new RemoteEyeConfig(
            playerId,
            entityId,
            config.leftEyeX(),
            config.leftEyeY(),
            config.rightEyeX(),
            config.rightEyeY(),
            config.eyelidColorX(),
            config.eyelidColorY(),
            config.eyeWidth(),
            config.eyeHeight()
        );
    }

    private static void removeRemoteConfig(UUID playerId) {
        CLIENT_CONFIGS_BY_UUID.remove(playerId);
        CLIENT_CONFIGS.entrySet().removeIf(entry -> playerId.equals(entry.getValue().playerId()));
    }

    public interface Platform {
        boolean canSendToServer();

        boolean canSendToPlayer(ServerPlayer player);

        void sendToServer(EyeConfigC2SPayload payload);

        void sendToPlayer(ServerPlayer player, EyeConfigS2CPayload payload);
    }

    public record EyeConfigC2SPayload(RemoteEyeConfig config) implements CustomPacketPayload {
        public static final Type<EyeConfigC2SPayload> TYPE = new Type<>(EYE_CONFIG_C2S);
        public static final StreamCodec<RegistryFriendlyByteBuf, EyeConfigC2SPayload> STREAM_CODEC = StreamCodec.ofMember(EyeConfigC2SPayload::write, EyeConfigC2SPayload::read);

        private static EyeConfigC2SPayload read(RegistryFriendlyByteBuf buf) {
            return new EyeConfigC2SPayload(readUpdateBody(buf));
        }

        private void write(RegistryFriendlyByteBuf buf) {
            writeUpdateBody(buf, config);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record EyeConfigS2CPayload(int action, RemoteEyeConfig config, UUID playerId) implements CustomPacketPayload {
        public static final Type<EyeConfigS2CPayload> TYPE = new Type<>(EYE_CONFIG_S2C);
        public static final StreamCodec<RegistryFriendlyByteBuf, EyeConfigS2CPayload> STREAM_CODEC = StreamCodec.ofMember(EyeConfigS2CPayload::write, EyeConfigS2CPayload::read);

        public static EyeConfigS2CPayload update(RemoteEyeConfig config) {
            return new EyeConfigS2CPayload(UPDATE, config, null);
        }

        public static EyeConfigS2CPayload remove(UUID playerId) {
            return new EyeConfigS2CPayload(REMOVE, null, playerId);
        }

        private static EyeConfigS2CPayload read(RegistryFriendlyByteBuf buf) {
            int action = buf.readUnsignedByte();
            if (action == UPDATE) {
                return update(readUpdateBody(buf));
            }
            return remove(buf.readUUID());
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeByte(action);
            if (action == UPDATE) {
                writeUpdateBody(buf, config);
            } else {
                buf.writeUUID(playerId);
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
