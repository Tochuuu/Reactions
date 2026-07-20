package me.tochuuu.reactions.fabric.server;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ReactionsFabricServerRelay implements ModInitializer {
    private static final Identifier EYE_CONFIG_C2S = Identifier.fromNamespaceAndPath("reactions", "eye_config_c2s");
    private static final Identifier EYE_CONFIG_S2C = Identifier.fromNamespaceAndPath("reactions", "eye_config_s2c");
    private static final Identifier EYE_FOCUS_C2S = Identifier.fromNamespaceAndPath("reactions", "eye_focus_c2s");
    private static final Identifier EYE_FOCUS_S2C = Identifier.fromNamespaceAndPath("reactions", "eye_focus_s2c");
    private static final int UPDATE = 0;
    private static final int REMOVE = 1;
    private static final int MIN_EYE_FOCUS = -101;
    private static final int MAX_EYE_FOCUS = 101;
    private static final int LEGACY_CONFIG_VALUE_COUNT = 8;
    private static final int CONFIG_VALUE_COUNT = 13;
    private static final int SERVER_SYNC_RETRY_TICKS = 20 * 30;
    private static final Map<UUID, EyeConfig> CONFIGS = new HashMap<>();
    private static final Map<UUID, EyeFocus> FOCUSES = new HashMap<>();
    private static final Map<UUID, Integer> PENDING_SYNC = new HashMap<>();

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(EyeConfigC2SPayload.TYPE, EyeConfigC2SPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EyeConfigS2CPayload.TYPE, EyeConfigS2CPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(EyeFocusC2SPayload.TYPE, EyeFocusC2SPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EyeFocusS2CPayload.TYPE, EyeFocusS2CPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(EyeConfigC2SPayload.TYPE, (payload, context) -> handleConfig(context.player(), payload.config()));
        ServerPlayNetworking.registerGlobalReceiver(EyeFocusC2SPayload.TYPE, (payload, context) -> handleFocus(context.player(), payload.focus()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PENDING_SYNC.put(handler.player.getUUID(), SERVER_SYNC_RETRY_TICKS));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> removeConfig(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(ReactionsFabricServerRelay::retrySync);
    }

    private static void handleConfig(ServerPlayer source, EyeConfig config) {
        EyeConfig serverConfig = config.withPlayer(source);
        CONFIGS.put(source.getUUID(), serverConfig);
        sendKnownConfigs(source);
        broadcast(source.level().getServer(), EyeConfigS2CPayload.update(serverConfig));
    }

    private static void handleFocus(ServerPlayer source, int focus) {
        int clampedFocus = clamp(focus, MIN_EYE_FOCUS, MAX_EYE_FOCUS);
        if (clampedFocus == 0) {
            FOCUSES.remove(source.getUUID());
        } else {
            FOCUSES.put(source.getUUID(), new EyeFocus(source.getUUID(), source.getId(), clampedFocus));
        }
        broadcastFocus(source.level().getServer(), clampedFocus == 0 ? EyeFocusS2CPayload.remove(source.getUUID()) : EyeFocusS2CPayload.update(new EyeFocus(source.getUUID(), source.getId(), clampedFocus)));
    }

    private static void removeConfig(ServerPlayer player) {
        CONFIGS.remove(player.getUUID());
        FOCUSES.remove(player.getUUID());
        PENDING_SYNC.remove(player.getUUID());
        broadcast(player.level().getServer(), EyeConfigS2CPayload.remove(player.getUUID()));
        broadcastFocus(player.level().getServer(), EyeFocusS2CPayload.remove(player.getUUID()));
    }

    private static void retrySync(MinecraftServer server) {
        if (PENDING_SYNC.isEmpty() || server.getTickCount() % 10 != 0) {
            return;
        }

        Iterator<Map.Entry<UUID, Integer>> iterator = PENDING_SYNC.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || entry.getValue() <= 0) {
                iterator.remove();
                continue;
            }

            entry.setValue(entry.getValue() - 10);
            if (canSendConfig(player)) {
                if (sendKnownConfigs(player)) {
                    iterator.remove();
                }
            }
        }
    }

    private static boolean sendKnownConfigs(ServerPlayer player) {
        if (!canSendConfig(player)) {
            PENDING_SYNC.put(player.getUUID(), SERVER_SYNC_RETRY_TICKS);
            return false;
        }
        boolean sentAll = true;
        for (EyeConfig config : CONFIGS.values()) {
            sentAll &= send(player, EyeConfigS2CPayload.update(config));
        }
        for (EyeFocus focus : FOCUSES.values()) {
            sentAll &= sendFocus(player, EyeFocusS2CPayload.update(focus));
        }
        if (!sentAll) {
            PENDING_SYNC.put(player.getUUID(), SERVER_SYNC_RETRY_TICKS);
        }
        return sentAll;
    }

    private static void broadcast(MinecraftServer server, EyeConfigS2CPayload payload) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!send(player, payload)) {
                PENDING_SYNC.put(player.getUUID(), SERVER_SYNC_RETRY_TICKS);
            }
        }
    }

    private static void broadcastFocus(MinecraftServer server, EyeFocusS2CPayload payload) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!sendFocus(player, payload)) {
                PENDING_SYNC.put(player.getUUID(), SERVER_SYNC_RETRY_TICKS);
            }
        }
    }

    private static boolean canSendConfig(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, EyeConfigS2CPayload.TYPE) || CONFIGS.containsKey(player.getUUID());
    }

    private static boolean send(ServerPlayer player, EyeConfigS2CPayload payload) {
        if (!canSendConfig(player)) {
            return false;
        }
        try {
            if (ServerPlayNetworking.canSend(player, EyeConfigS2CPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            } else {
                player.connection.send(new ClientboundCustomPayloadPacket(payload));
            }
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean sendFocus(ServerPlayer player, EyeFocusS2CPayload payload) {
        if (!canSendFocus(player)) {
            return false;
        }
        try {
            if (ServerPlayNetworking.canSend(player, EyeFocusS2CPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            } else {
                player.connection.send(new ClientboundCustomPayloadPacket(payload));
            }
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean canSendFocus(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, EyeFocusS2CPayload.TYPE) || CONFIGS.containsKey(player.getUUID());
    }

    private static void writeConfig(RegistryFriendlyByteBuf buf, EyeConfig config) {
        buf.writeUUID(config.playerId());
        buf.writeVarInt(config.entityId());
        for (int value : config.values()) {
            buf.writeByte(value);
        }
    }

    private static EyeConfig readConfig(RegistryFriendlyByteBuf buf) {
        UUID playerId = buf.readUUID();
        int entityId = buf.readVarInt();
        int valueCount = buf.readableBytes() >= CONFIG_VALUE_COUNT ? CONFIG_VALUE_COUNT : LEGACY_CONFIG_VALUE_COUNT;
        int[] values = new int[valueCount];
        for (int i = 0; i < values.length; i++) {
            values[i] = buf.readUnsignedByte();
        }
        return new EyeConfig(playerId, entityId, values);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record EyeConfig(UUID playerId, int entityId, int[] values) {
        private EyeConfig withPlayer(ServerPlayer player) {
            return new EyeConfig(player.getUUID(), player.getId(), values);
        }
    }

    private record EyeFocus(UUID playerId, int entityId, int focus) {
        private EyeFocus {
            focus = clamp(focus, MIN_EYE_FOCUS, MAX_EYE_FOCUS);
        }
    }

    private record EyeConfigC2SPayload(EyeConfig config) implements CustomPacketPayload {
        private static final Type<EyeConfigC2SPayload> TYPE = new Type<>(EYE_CONFIG_C2S);
        private static final StreamCodec<RegistryFriendlyByteBuf, EyeConfigC2SPayload> STREAM_CODEC = StreamCodec.ofMember(EyeConfigC2SPayload::write, EyeConfigC2SPayload::read);

        private static EyeConfigC2SPayload read(RegistryFriendlyByteBuf buf) {
            return new EyeConfigC2SPayload(readConfig(buf));
        }

        private void write(RegistryFriendlyByteBuf buf) {
            writeConfig(buf, config);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private record EyeConfigS2CPayload(int action, EyeConfig config, UUID playerId) implements CustomPacketPayload {
        private static final Type<EyeConfigS2CPayload> TYPE = new Type<>(EYE_CONFIG_S2C);
        private static final StreamCodec<RegistryFriendlyByteBuf, EyeConfigS2CPayload> STREAM_CODEC = StreamCodec.ofMember(EyeConfigS2CPayload::write, EyeConfigS2CPayload::read);

        private static EyeConfigS2CPayload update(EyeConfig config) {
            return new EyeConfigS2CPayload(UPDATE, config, null);
        }

        private static EyeConfigS2CPayload remove(UUID playerId) {
            return new EyeConfigS2CPayload(REMOVE, null, playerId);
        }

        private static EyeConfigS2CPayload read(RegistryFriendlyByteBuf buf) {
            int action = buf.readUnsignedByte();
            if (action == UPDATE) {
                return update(readConfig(buf));
            }
            return remove(buf.readUUID());
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeByte(action);
            if (action == UPDATE) {
                writeConfig(buf, config);
            } else {
                buf.writeUUID(playerId);
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private record EyeFocusC2SPayload(int focus) implements CustomPacketPayload {
        private static final Type<EyeFocusC2SPayload> TYPE = new Type<>(EYE_FOCUS_C2S);
        private static final StreamCodec<RegistryFriendlyByteBuf, EyeFocusC2SPayload> STREAM_CODEC = StreamCodec.ofMember(EyeFocusC2SPayload::write, EyeFocusC2SPayload::read);

        private static EyeFocusC2SPayload read(RegistryFriendlyByteBuf buf) {
            return new EyeFocusC2SPayload(buf.readByte());
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeByte(clamp(focus, MIN_EYE_FOCUS, MAX_EYE_FOCUS));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private record EyeFocusS2CPayload(int action, EyeFocus focus, UUID playerId) implements CustomPacketPayload {
        private static final Type<EyeFocusS2CPayload> TYPE = new Type<>(EYE_FOCUS_S2C);
        private static final StreamCodec<RegistryFriendlyByteBuf, EyeFocusS2CPayload> STREAM_CODEC = StreamCodec.ofMember(EyeFocusS2CPayload::write, EyeFocusS2CPayload::read);

        private static EyeFocusS2CPayload update(EyeFocus focus) {
            return new EyeFocusS2CPayload(UPDATE, focus, null);
        }

        private static EyeFocusS2CPayload remove(UUID playerId) {
            return new EyeFocusS2CPayload(REMOVE, null, playerId);
        }

        private static EyeFocusS2CPayload read(RegistryFriendlyByteBuf buf) {
            int action = buf.readUnsignedByte();
            UUID playerId = buf.readUUID();
            if (action == UPDATE) {
                return update(new EyeFocus(playerId, buf.readVarInt(), buf.readByte()));
            }
            return remove(playerId);
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeByte(action);
            if (action == UPDATE) {
                buf.writeUUID(focus.playerId());
                buf.writeVarInt(focus.entityId());
                buf.writeByte(focus.focus());
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
