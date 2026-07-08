package me.tochuuu.reactions.network;

import me.tochuuu.reactions.Reactions;
import me.tochuuu.reactions.client.ReactionsClientConfig;
import me.tochuuu.reactions.client.RemoteEyeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ReactionsNetworking {
    public static final ResourceLocation EYE_CONFIG_C2S = new ResourceLocation(Reactions.MOD_ID, "eye_config_c2s");
    public static final ResourceLocation EYE_CONFIG_S2C = new ResourceLocation(Reactions.MOD_ID, "eye_config_s2c");
    public static final ResourceLocation EYE_FOCUS_C2S = new ResourceLocation(Reactions.MOD_ID, "eye_focus_c2s");
    public static final ResourceLocation EYE_FOCUS_S2C = new ResourceLocation(Reactions.MOD_ID, "eye_focus_s2c");
    private static final int UPDATE = 0;
    private static final int REMOVE = 1;
    private static final int MIN_EYE_FOCUS = -101;
    private static final int MAX_EYE_FOCUS = 101;
    private static final int CLIENT_SYNC_RETRY_TICKS = 20 * 30;
    private static final int SERVER_SYNC_RETRY_TICKS = 20 * 30;
    private static final Map<Integer, RemoteEyeConfig> CLIENT_CONFIGS = new HashMap<>();
    private static final Map<UUID, RemoteEyeConfig> CLIENT_CONFIGS_BY_UUID = new HashMap<>();
    private static final Map<Integer, Integer> CLIENT_EYE_FOCUSES = new HashMap<>();
    private static final Map<UUID, Integer> CLIENT_EYE_FOCUSES_BY_UUID = new HashMap<>();
    private static final Map<UUID, RemoteEyeConfig> SERVER_CONFIGS = new HashMap<>();
    private static final Map<UUID, EyeFocusState> SERVER_EYE_FOCUSES = new HashMap<>();
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

    public static void handleServerboundEyeFocus(EyeFocusC2SPayload payload, ServerPlayer serverPlayer) {
        int focus = clamp(payload.focus(), MIN_EYE_FOCUS, MAX_EYE_FOCUS);
        if (focus == 0) {
            SERVER_EYE_FOCUSES.remove(serverPlayer.getUUID());
        } else {
            SERVER_EYE_FOCUSES.put(serverPlayer.getUUID(), new EyeFocusState(serverPlayer.getUUID(), serverPlayer.getId(), focus));
        }
        sendEyeFocusToReceivers(serverPlayer, focus);
    }

    public static void handleClientboundConfig(EyeConfigS2CPayload payload) {
        if (payload.action() == UPDATE) {
            applyRemoteConfig(payload.config());
        } else if (payload.action() == REMOVE && payload.playerId() != null) {
            removeRemoteConfig(payload.playerId());
        }
    }

    public static void handleClientboundEyeFocus(EyeFocusS2CPayload payload) {
        if (payload.action() == UPDATE) {
            applyRemoteEyeFocus(payload.playerId(), payload.entityId(), payload.focus());
        } else if (payload.action() == REMOVE && payload.playerId() != null) {
            removeRemoteEyeFocus(payload.playerId());
        }
    }

    public static void onServerPlayerJoin(ServerPlayer player) {
        SERVER_PENDING_SYNC.put(player.getUUID(), SERVER_SYNC_RETRY_TICKS);
    }

    public static void onServerPlayerQuit(ServerPlayer player) {
        SERVER_CONFIGS.remove(player.getUUID());
        SERVER_EYE_FOCUSES.remove(player.getUUID());
        SERVER_PENDING_SYNC.remove(player.getUUID());
        sendRemoveToReceivers(player);
        sendEyeFocusRemoveToReceivers(player);
    }

    public static void onServerTick(MinecraftServer server) {
        retryServerSync(server);
    }

    public static void onClientJoin() {
        CLIENT_CONFIGS.clear();
        CLIENT_CONFIGS_BY_UUID.clear();
        CLIENT_EYE_FOCUSES.clear();
        CLIENT_EYE_FOCUSES_BY_UUID.clear();
        requestLocalConfigSync();
    }

    public static void onClientQuit() {
        CLIENT_CONFIGS.clear();
        CLIENT_CONFIGS_BY_UUID.clear();
        CLIENT_EYE_FOCUSES.clear();
        CLIENT_EYE_FOCUSES_BY_UUID.clear();
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

    public static int remoteEyeFocus(int entityId) {
        Integer focus = CLIENT_EYE_FOCUSES.get(entityId);
        if (focus != null) {
            return focus;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return 0;
        }

        for (Player player : minecraft.level.players()) {
            if (player.getId() == entityId) {
                focus = CLIENT_EYE_FOCUSES_BY_UUID.get(player.getUUID());
                if (focus != null) {
                    CLIENT_EYE_FOCUSES.put(entityId, focus);
                    return focus;
                }
            }
        }
        return 0;
    }

    public static boolean hasServerConfig(UUID playerId) {
        return SERVER_CONFIGS.containsKey(playerId);
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

    public static void sendLocalEyeFocus(int focus) {
        int clampedFocus = clamp(focus, MIN_EYE_FOCUS, MAX_EYE_FOCUS);
        syncIntegratedServerHostEyeFocus(clampedFocus);
        if (platform != null && platform.canSendEyeFocusToServer()) {
            platform.sendEyeFocusToServer(new EyeFocusC2SPayload(clampedFocus));
        }
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

        Integer entityId = assignedEntityId(minecraft.player);
        if (entityId == null) {
            return false;
        }

        platform.sendToServer(new EyeConfigC2SPayload(localConfig(minecraft.player.getUUID(), entityId)));
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

        Integer hostEntityId = assignedEntityId(host);
        if (hostEntityId == null) {
            return;
        }

        RemoteEyeConfig hostConfig = localConfig(host.getUUID(), hostEntityId);
        if (hostConfig.equals(SERVER_CONFIGS.get(host.getUUID()))) {
            return;
        }

        SERVER_CONFIGS.put(host.getUUID(), hostConfig);
        sendUpdateToReceivers(host, hostConfig);
    }

    private static void syncIntegratedServerHostEyeFocus(int focus) {
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

        Integer hostEntityId = assignedEntityId(host);
        if (hostEntityId == null) {
            return;
        }

        EyeFocusState current = SERVER_EYE_FOCUSES.get(host.getUUID());
        if (focus == 0) {
            if (current == null) {
                return;
            }
            SERVER_EYE_FOCUSES.remove(host.getUUID());
        } else {
            if (current != null && current.focus() == focus && current.entityId() == hostEntityId) {
                return;
            }
            SERVER_EYE_FOCUSES.put(host.getUUID(), new EyeFocusState(host.getUUID(), hostEntityId, focus));
        }
        sendEyeFocusToReceivers(host, focus);
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
                boolean sentAll = true;
                for (RemoteEyeConfig config : SERVER_CONFIGS.values()) {
                    sentAll &= trySendUpdate(player, config);
                }
                if (sentAll) {
                    iterator.remove();
                }
            }
        }
    }

    private static void sendUpdateToReceivers(ServerPlayer source, RemoteEyeConfig config) {
        for (ServerPlayer player : source.level().getServer().getPlayerList().getPlayers()) {
            if (canSendToPlayer(player)) {
                if (!trySendUpdate(player, config)) {
                    queueServerSync(player);
                }
            } else {
                queueServerSync(player);
            }
        }
    }

    private static void sendKnownConfigs(ServerPlayer player) {
        if (!canSendToPlayer(player)) {
            queueServerSync(player);
            return;
        }
        boolean sentAll = true;
        for (RemoteEyeConfig config : SERVER_CONFIGS.values()) {
            sentAll &= trySendUpdate(player, config);
        }
        for (EyeFocusState focus : SERVER_EYE_FOCUSES.values()) {
            sentAll &= trySendEyeFocusUpdate(player, focus);
        }
        if (!sentAll) {
            queueServerSync(player);
        }
    }

    private static void sendRemoveToReceivers(ServerPlayer source) {
        for (ServerPlayer player : source.level().getServer().getPlayerList().getPlayers()) {
            if (canSendToPlayer(player)) {
                sendRemove(player, source.getUUID());
            }
        }
    }

    private static void sendEyeFocusToReceivers(ServerPlayer source, int focus) {
        Integer sourceEntityId = assignedEntityId(source);
        if (sourceEntityId == null) {
            return;
        }

        for (ServerPlayer player : source.level().getServer().getPlayerList().getPlayers()) {
            if (canSendEyeFocusToPlayer(player)) {
                if (!trySendEyeFocus(player, source.getUUID(), sourceEntityId, focus)) {
                    queueServerSync(player);
                }
            } else {
                queueServerSync(player);
            }
        }
    }

    private static void sendEyeFocusRemoveToReceivers(ServerPlayer source) {
        for (ServerPlayer player : source.level().getServer().getPlayerList().getPlayers()) {
            if (canSendEyeFocusToPlayer(player)) {
                sendEyeFocusRemove(player, source.getUUID());
            }
        }
    }

    private static boolean canSendToPlayer(ServerPlayer player) {
        return platform != null && platform.canSendToPlayer(player);
    }

    private static boolean canSendEyeFocusToPlayer(ServerPlayer player) {
        return platform != null && platform.canSendEyeFocusToPlayer(player);
    }

    private static boolean trySendUpdate(ServerPlayer player, RemoteEyeConfig config) {
        try {
            platform.sendToPlayer(player, EyeConfigS2CPayload.update(config));
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean trySendEyeFocusUpdate(ServerPlayer player, EyeFocusState focus) {
        return trySendEyeFocus(player, focus.playerId(), focus.entityId(), focus.focus());
    }

    private static boolean trySendEyeFocus(ServerPlayer player, UUID sourcePlayerId, int sourceEntityId, int focus) {
        try {
            if (focus == 0) {
                platform.sendEyeFocusToPlayer(player, EyeFocusS2CPayload.remove(sourcePlayerId));
            } else {
                platform.sendEyeFocusToPlayer(player, EyeFocusS2CPayload.update(sourcePlayerId, sourceEntityId, focus));
            }
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void sendRemove(ServerPlayer player, UUID playerId) {
        try {
            platform.sendToPlayer(player, EyeConfigS2CPayload.remove(playerId));
        } catch (RuntimeException ignored) {
        }
    }

    private static void sendEyeFocusRemove(ServerPlayer player, UUID playerId) {
        try {
            platform.sendEyeFocusToPlayer(player, EyeFocusS2CPayload.remove(playerId));
        } catch (RuntimeException ignored) {
        }
    }

    private static void queueServerSync(ServerPlayer player) {
        SERVER_PENDING_SYNC.put(player.getUUID(), SERVER_SYNC_RETRY_TICKS);
    }

    private static void writeUpdateBody(FriendlyByteBuf buf, RemoteEyeConfig config) {
        buf.writeUUID(config.playerId());
        buf.writeVarInt(config.entityId());
        buf.writeByte(config.leftEyeX());
        buf.writeByte(config.leftEyeY());
        buf.writeByte(config.rightEyeX());
        buf.writeByte(config.rightEyeY());
        buf.writeBoolean(config.mouthEnabled());
        buf.writeByte(config.leftMouthX());
        buf.writeByte(config.leftMouthY());
        buf.writeByte(config.rightMouthX());
        buf.writeByte(config.rightMouthY());
        buf.writeByte(config.eyelidColorX());
        buf.writeByte(config.eyelidColorY());
        buf.writeByte(config.eyeWidth());
        buf.writeByte(config.eyeHeight());
    }

    private static RemoteEyeConfig readUpdateBody(FriendlyByteBuf buf) {
        UUID playerId = buf.readUUID();
        int entityId = buf.readVarInt();
        int leftEyeX = buf.readUnsignedByte();
        int leftEyeY = buf.readUnsignedByte();
        int rightEyeX = buf.readUnsignedByte();
        int rightEyeY = buf.readUnsignedByte();
        if (buf.readableBytes() <= 4) {
            return new RemoteEyeConfig(
                playerId,
                entityId,
                leftEyeX,
                leftEyeY,
                rightEyeX,
                rightEyeY,
                false,
                11,
                14,
                12,
                14,
                buf.readUnsignedByte(),
                buf.readUnsignedByte(),
                buf.readUnsignedByte(),
                buf.readUnsignedByte()
            );
        }

        return new RemoteEyeConfig(
            playerId,
            entityId,
            leftEyeX,
            leftEyeY,
            rightEyeX,
            rightEyeY,
            buf.readBoolean(),
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
            config.showMouth,
            config.leftMouthX,
            config.leftMouthY,
            config.rightMouthX,
            config.rightMouthY,
            config.eyelidColorX,
            config.eyelidColorY,
            config.eyeWidth,
            config.eyeHeight
        );
    }

    private static Integer assignedEntityId(Player player) {
        try {
            return player.getId();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private static RemoteEyeConfig withPlayerIdentity(RemoteEyeConfig config, UUID playerId, int entityId) {
        return new RemoteEyeConfig(
            playerId,
            entityId,
            config.leftEyeX(),
            config.leftEyeY(),
            config.rightEyeX(),
            config.rightEyeY(),
            config.mouthEnabled(),
            config.leftMouthX(),
            config.leftMouthY(),
            config.rightMouthX(),
            config.rightMouthY(),
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

    private static void applyRemoteEyeFocus(UUID playerId, int entityId, int focus) {
        int clampedFocus = clamp(focus, MIN_EYE_FOCUS, MAX_EYE_FOCUS);
        if (clampedFocus == 0) {
            removeRemoteEyeFocus(playerId);
            return;
        }
        CLIENT_EYE_FOCUSES.put(entityId, clampedFocus);
        if (playerId != null) {
            CLIENT_EYE_FOCUSES_BY_UUID.put(playerId, clampedFocus);
        }
    }

    private static void removeRemoteEyeFocus(UUID playerId) {
        CLIENT_EYE_FOCUSES_BY_UUID.remove(playerId);
        CLIENT_EYE_FOCUSES.entrySet().removeIf(entry -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return false;
            }
            for (Player player : minecraft.level.players()) {
                if (player.getId() == entry.getKey()) {
                    return playerId.equals(player.getUUID());
                }
            }
            return false;
        });
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record EyeFocusState(UUID playerId, int entityId, int focus) {
    }

    public interface Platform {
        boolean canSendToServer();

        boolean canSendToPlayer(ServerPlayer player);

        boolean canSendEyeFocusToServer();

        boolean canSendEyeFocusToPlayer(ServerPlayer player);

        void sendToServer(EyeConfigC2SPayload payload);

        void sendToPlayer(ServerPlayer player, EyeConfigS2CPayload payload);

        void sendEyeFocusToServer(EyeFocusC2SPayload payload);

        void sendEyeFocusToPlayer(ServerPlayer player, EyeFocusS2CPayload payload);
    }

    public record EyeConfigC2SPayload(RemoteEyeConfig config) {
        public static EyeConfigC2SPayload read(FriendlyByteBuf buf) {
            return new EyeConfigC2SPayload(readUpdateBody(buf));
        }

        public void write(FriendlyByteBuf buf) {
            writeUpdateBody(buf, config);
        }
    }

    public record EyeConfigS2CPayload(int action, RemoteEyeConfig config, UUID playerId) {
        public static EyeConfigS2CPayload update(RemoteEyeConfig config) {
            return new EyeConfigS2CPayload(UPDATE, config, null);
        }

        public static EyeConfigS2CPayload remove(UUID playerId) {
            return new EyeConfigS2CPayload(REMOVE, null, playerId);
        }

        public static EyeConfigS2CPayload read(FriendlyByteBuf buf) {
            int action = buf.readUnsignedByte();
            if (action == UPDATE) {
                return update(readUpdateBody(buf));
            }
            return remove(buf.readUUID());
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeByte(action);
            if (action == UPDATE) {
                writeUpdateBody(buf, config);
            } else {
                buf.writeUUID(playerId);
            }
        }
    }

    public record EyeFocusC2SPayload(int focus) {
        public static EyeFocusC2SPayload read(FriendlyByteBuf buf) {
            return new EyeFocusC2SPayload(buf.readByte());
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeByte(clamp(focus, MIN_EYE_FOCUS, MAX_EYE_FOCUS));
        }
    }

    public record EyeFocusS2CPayload(int action, UUID playerId, int entityId, int focus) {
        public static EyeFocusS2CPayload update(UUID playerId, int entityId, int focus) {
            return new EyeFocusS2CPayload(UPDATE, playerId, entityId, clamp(focus, MIN_EYE_FOCUS, MAX_EYE_FOCUS));
        }

        public static EyeFocusS2CPayload remove(UUID playerId) {
            return new EyeFocusS2CPayload(REMOVE, playerId, 0, 0);
        }

        public static EyeFocusS2CPayload read(FriendlyByteBuf buf) {
            int action = buf.readUnsignedByte();
            UUID playerId = buf.readUUID();
            if (action == UPDATE) {
                return update(playerId, buf.readVarInt(), buf.readByte());
            }
            return remove(playerId);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeByte(action);
            buf.writeUUID(playerId);
            if (action == UPDATE) {
                buf.writeVarInt(entityId);
                buf.writeByte(focus);
            }
        }
    }
}
