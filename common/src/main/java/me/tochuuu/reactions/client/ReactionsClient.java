package me.tochuuu.reactions.client;

import com.mojang.blaze3d.platform.InputConstants;
import me.tochuuu.reactions.Reactions;
import me.tochuuu.reactions.network.ReactionsNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class ReactionsClient {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Reactions.MOD_ID, "key"));
    private static final KeyMapping OPEN_CONFIG = new KeyMapping("key.reactions.open_config", InputConstants.Type.KEYSYM, InputConstants.KEY_R, CATEGORY);
    private static boolean initialized;

    private ReactionsClient() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        ReactionsNetworking.initClient();
    }

    public static KeyMapping openConfigKey() {
        return OPEN_CONFIG;
    }

    public static void onClientTick(Minecraft client) {
        BlockInteractionEyeFocus.onClientTick(client);
        while (OPEN_CONFIG.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen == null) {
                minecraft.setScreen(new ReactionsConfigScreen(null));
            }
        }
    }
}
