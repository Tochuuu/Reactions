package me.tochuuu.reactions.paper;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ReactionsRelayPlugin extends JavaPlugin implements PluginMessageListener, Listener {
    private static final String C2S_CHANNEL = "reactions:eye_config_c2s";
    private static final String S2C_CHANNEL = "reactions:eye_config_s2c";
    private static final String FOCUS_C2S_CHANNEL = "reactions:eye_focus_c2s";
    private static final String FOCUS_S2C_CHANNEL = "reactions:eye_focus_s2c";
    private static final int UPDATE = 0;
    private static final int REMOVE = 1;
    private static final int EYE_FOCUS_NONE = 0;
    private static final int EYE_FOCUS_BLOCK = 1;
    private static final int EYE_FOCUS_ENTITY = 2;
    private static final int LEGACY_CONFIG_VALUE_COUNT = 8;
    private static final int CONFIG_VALUE_COUNT = 13;
    private final Map<UUID, EyeConfig> configs = new HashMap<>();
    private final Map<UUID, EyeFocus> focuses = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getMessenger().registerIncomingPluginChannel(this, C2S_CHANNEL, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, FOCUS_C2S_CHANNEL, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, S2C_CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(this, FOCUS_S2C_CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this, C2S_CHANNEL, this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this, FOCUS_C2S_CHANNEL, this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, S2C_CHANNEL);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, FOCUS_S2C_CHANNEL);
        configs.clear();
        focuses.clear();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (FOCUS_C2S_CHANNEL.equals(channel)) {
            handleFocusMessage(player, message);
            return;
        }

        if (!C2S_CHANNEL.equals(channel)) {
            return;
        }
        EyeConfig config = readClientConfig(player, message);
        if (config == null) {
            return;
        }

        configs.put(player.getUniqueId(), config);
        byte[] update = writeUpdate(config);
        for (Player receiver : Bukkit.getOnlinePlayers()) {
            sendIfReady(receiver, S2C_CHANNEL, update);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player player = event.getPlayer();
            if (!player.isOnline()) {
                return;
            }
            for (EyeConfig config : configs.values()) {
                sendIfReady(player, S2C_CHANNEL, writeUpdate(config));
            }
            for (EyeFocus focus : focuses.values()) {
                sendIfReady(player, FOCUS_S2C_CHANNEL, writeFocusUpdate(focus));
            }
        }, 20L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        boolean removedConfig = configs.remove(player.getUniqueId()) != null;
        boolean removedFocus = focuses.remove(player.getUniqueId()) != null;
        if (!removedConfig && !removedFocus) {
            return;
        }

        byte[] configRemove = writeRemove(player.getUniqueId());
        byte[] focusRemove = writeFocusRemove(player.getUniqueId());
        for (Player receiver : Bukkit.getOnlinePlayers()) {
            if (removedConfig) {
                sendIfReady(receiver, S2C_CHANNEL, configRemove);
            }
            if (removedFocus) {
                sendIfReady(receiver, FOCUS_S2C_CHANNEL, focusRemove);
            }
        }
    }

    private void handleFocusMessage(Player player, byte[] message) {
        if (message.length < 1) {
            return;
        }

        int focus = clamp(message[0], -100, 100);
        int mode = message.length > 1 ? clampFocusMode(message[1] & 0xFF) : EYE_FOCUS_BLOCK;
        byte[] payload;
        if (focus == 0 || mode == EYE_FOCUS_NONE) {
            focuses.remove(player.getUniqueId());
            payload = writeFocusRemove(player.getUniqueId());
        } else {
            EyeFocus state = new EyeFocus(player.getUniqueId(), player.getEntityId(), focus, mode);
            focuses.put(player.getUniqueId(), state);
            payload = writeFocusUpdate(state);
        }

        for (Player receiver : Bukkit.getOnlinePlayers()) {
            sendIfReady(receiver, FOCUS_S2C_CHANNEL, payload);
        }
    }

    private void sendIfReady(Player player, String channel, byte[] message) {
        player.sendPluginMessage(this, channel, message);
    }

    private static EyeConfig readClientConfig(Player player, byte[] message) {
        if (message.length < 16 + 1 + LEGACY_CONFIG_VALUE_COUNT) {
            return null;
        }

        int[] index = {16};
        int ignoredEntityId = readVarInt(message, index);
        if (ignoredEntityId < 0 || index[0] + LEGACY_CONFIG_VALUE_COUNT > message.length) {
            return null;
        }

        int valueCount = index[0] + CONFIG_VALUE_COUNT <= message.length ? CONFIG_VALUE_COUNT : LEGACY_CONFIG_VALUE_COUNT;
        return new EyeConfig(player.getUniqueId(), player.getEntityId(), Arrays.copyOfRange(message, index[0], index[0] + valueCount));
    }

    private static byte[] writeUpdate(EyeConfig config) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 + 16 + 5 + config.values().length);
        out.write(UPDATE);
        writeUuid(out, config.playerId());
        writeVarInt(out, config.entityId());
        out.writeBytes(config.values());
        return out.toByteArray();
    }

    private static byte[] writeRemove(UUID playerId) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 + 16);
        out.write(REMOVE);
        writeUuid(out, playerId);
        return out.toByteArray();
    }

    private static byte[] writeFocusUpdate(EyeFocus focus) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 + 16 + 5 + 2);
        out.write(UPDATE);
        writeUuid(out, focus.playerId());
        writeVarInt(out, focus.entityId());
        out.write(clamp(focus.focus(), -100, 100));
        out.write(clampFocusMode(focus.mode()));
        return out.toByteArray();
    }

    private static byte[] writeFocusRemove(UUID playerId) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 + 16);
        out.write(REMOVE);
        writeUuid(out, playerId);
        return out.toByteArray();
    }

    private static void writeUuid(ByteArrayOutputStream out, UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        out.writeBytes(buffer.array());
    }

    private static int readVarInt(byte[] data, int[] index) {
        int value = 0;
        int position = 0;
        while (index[0] < data.length) {
            int current = data[index[0]++] & 0xFF;
            value |= (current & 0x7F) << position;
            if ((current & 0x80) == 0) {
                return value;
            }
            position += 7;
            if (position >= 35) {
                return -1;
            }
        }
        return -1;
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & -128) != 0) {
            out.write(value & 127 | 128);
            value >>>= 7;
        }
        out.write(value);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampFocusMode(int mode) {
        return mode == EYE_FOCUS_BLOCK || mode == EYE_FOCUS_ENTITY ? mode : EYE_FOCUS_NONE;
    }

    private record EyeConfig(UUID playerId, int entityId, byte[] values) {
    }

    private record EyeFocus(UUID playerId, int entityId, int focus, int mode) {
    }
}
