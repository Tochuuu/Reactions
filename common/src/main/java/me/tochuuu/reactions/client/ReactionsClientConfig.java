package me.tochuuu.reactions.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import me.tochuuu.reactions.Reactions;
import me.tochuuu.reactions.network.ReactionsNetworking;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class ReactionsClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ReactionsClientConfig instance;

    public boolean enabled = true;
    public boolean animateSelf = true;
    public boolean animateOthers = true;
    public boolean animateBowShooting = false;
    public boolean showMouth = true;
    public int leftEyeX = 9;
    public int leftEyeY = 12;
    public int rightEyeX = 13;
    public int rightEyeY = 12;
    public int leftMouthX = 11;
    public int leftMouthY = 14;
    public int rightMouthX = 12;
    public int rightMouthY = 14;
    public int eyelidColorX = 10;
    public int eyelidColorY = 11;
    public int eyeWidth = 2;
    public int eyeHeight = 1;
    public int movementPixels = 1;
    public int blinkIntervalTicks = 90;
    public int blinkDurationTicks = 4;
    public Map<String, PlayerOverride> playerOverrides = new HashMap<>();

    public static ReactionsClientConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        Path path = configPath();
        Path legacyPath = legacyConfigPath();
        if (!Files.exists(path) && Files.exists(legacyPath)) {
            path = legacyPath;
        }
        if (!Files.exists(path)) {
            instance = new ReactionsClientConfig();
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            ReactionsClientConfig loaded = GSON.fromJson(reader, ReactionsClientConfig.class);
            instance = loaded == null ? new ReactionsClientConfig() : loaded;
            instance.clamp();
            if (path.equals(legacyPath)) {
                save();
            }
        } catch (IOException | JsonSyntaxException ignored) {
            instance = new ReactionsClientConfig();
        }
    }

    public static void save() {
        ReactionsClientConfig config = get();
        config.clamp();

        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(config, writer);
            }
            ReactionsNetworking.sendLocalConfigToServer();
        } catch (IOException ignored) {
        }
    }

    public static void reset() {
        instance = new ReactionsClientConfig();
        save();
    }

    public PlayerOverride playerOverride(String playerName) {
        if (playerName == null || playerOverrides == null) {
            return null;
        }
        return playerOverrides.get(playerKey(playerName));
    }

    public PlayerOverride ensurePlayerOverride(String playerName) {
        if (playerOverrides == null) {
            playerOverrides = new HashMap<>();
        }
        return playerOverrides.computeIfAbsent(playerKey(playerName), ignored -> PlayerOverride.from(this));
    }

    public void removePlayerOverride(String playerName) {
        if (playerOverrides != null) {
            playerOverrides.remove(playerKey(playerName));
        }
    }

    private static Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(Reactions.MOD_ID + ".json");
    }

    private static Path legacyConfigPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(Reactions.MOD_ID + "-client.json");
    }

    private void clamp() {
        if (playerOverrides == null) {
            playerOverrides = new HashMap<>();
        }
        eyeWidth = clamp(eyeWidth, 1, 2);
        eyeHeight = clamp(eyeHeight, 1, 3);
        leftEyeX = clamp(leftEyeX, 8, 16 - eyeWidth);
        leftEyeY = clamp(leftEyeY, 8, 16 - eyeHeight);
        rightEyeX = clamp(rightEyeX, 8, 16 - eyeWidth);
        rightEyeY = clamp(rightEyeY, 8, 16 - eyeHeight);
        leftMouthX = clamp(leftMouthX, 8, 15);
        leftMouthY = clamp(leftMouthY, 8, 15);
        rightMouthX = clamp(rightMouthX, 8, 15);
        rightMouthY = clamp(rightMouthY, 8, 15);
        eyelidColorX = clamp(eyelidColorX, 0, 63);
        eyelidColorY = clamp(eyelidColorY, 0, 63);
        movementPixels = clamp(movementPixels, 0, 4);
        blinkIntervalTicks = clamp(blinkIntervalTicks, 20, 400);
        blinkDurationTicks = clamp(blinkDurationTicks, 1, 20);
        playerOverrides.values().forEach(PlayerOverride::clamp);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String playerKey(String playerName) {
        return playerName.trim().toLowerCase(Locale.ROOT);
    }

    public static final class PlayerOverride {
        public boolean enabled = true;
        public int leftEyeX = 9;
        public int leftEyeY = 12;
        public int rightEyeX = 13;
        public int rightEyeY = 12;
        public boolean showMouth = true;
        public int leftMouthX = 11;
        public int leftMouthY = 14;
        public int rightMouthX = 12;
        public int rightMouthY = 14;
        public int eyelidColorX = 10;
        public int eyelidColorY = 11;
        public int eyeWidth = 2;
        public int eyeHeight = 1;

        private static PlayerOverride from(ReactionsClientConfig config) {
            PlayerOverride override = new PlayerOverride();
            override.leftEyeX = config.leftEyeX;
            override.leftEyeY = config.leftEyeY;
            override.rightEyeX = config.rightEyeX;
            override.rightEyeY = config.rightEyeY;
            override.showMouth = config.showMouth;
            override.leftMouthX = config.leftMouthX;
            override.leftMouthY = config.leftMouthY;
            override.rightMouthX = config.rightMouthX;
            override.rightMouthY = config.rightMouthY;
            override.eyelidColorX = config.eyelidColorX;
            override.eyelidColorY = config.eyelidColorY;
            override.eyeWidth = config.eyeWidth;
            override.eyeHeight = config.eyeHeight;
            return override;
        }

        private void clamp() {
            eyeWidth = ReactionsClientConfig.clamp(eyeWidth, 1, 2);
            eyeHeight = ReactionsClientConfig.clamp(eyeHeight, 1, 3);
            leftEyeX = ReactionsClientConfig.clamp(leftEyeX, 8, 16 - eyeWidth);
            leftEyeY = ReactionsClientConfig.clamp(leftEyeY, 8, 16 - eyeHeight);
            rightEyeX = ReactionsClientConfig.clamp(rightEyeX, 8, 16 - eyeWidth);
            rightEyeY = ReactionsClientConfig.clamp(rightEyeY, 8, 16 - eyeHeight);
            leftMouthX = ReactionsClientConfig.clamp(leftMouthX, 8, 15);
            leftMouthY = ReactionsClientConfig.clamp(leftMouthY, 8, 15);
            rightMouthX = ReactionsClientConfig.clamp(rightMouthX, 8, 15);
            rightMouthY = ReactionsClientConfig.clamp(rightMouthY, 8, 15);
            eyelidColorX = ReactionsClientConfig.clamp(eyelidColorX, 0, 63);
            eyelidColorY = ReactionsClientConfig.clamp(eyelidColorY, 0, 63);
        }
    }
}
