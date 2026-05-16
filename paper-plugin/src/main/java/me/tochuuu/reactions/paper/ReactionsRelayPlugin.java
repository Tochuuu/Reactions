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
    private static final int UPDATE = 0;
    private static final int REMOVE = 1;
    private static final int LEGACY_CONFIG_VALUE_COUNT = 8;
    private static final int CONFIG_VALUE_COUNT = 13;
    private final Map<UUID, EyeConfig> configs = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getMessenger().registerIncomingPluginChannel(this, C2S_CHANNEL, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, S2C_CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this, C2S_CHANNEL, this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, S2C_CHANNEL);
        configs.clear();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
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
            sendIfReady(receiver, update);
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
                sendIfReady(player, writeUpdate(config));
            }
        }, 20L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (configs.remove(player.getUniqueId()) == null) {
            return;
        }

        byte[] remove = writeRemove(player.getUniqueId());
        for (Player receiver : Bukkit.getOnlinePlayers()) {
            sendIfReady(receiver, remove);
        }
    }

    private void sendIfReady(Player player, byte[] message) {
        player.sendPluginMessage(this, S2C_CHANNEL, message);
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

    private record EyeConfig(UUID playerId, int entityId, byte[] values) {
    }
}
