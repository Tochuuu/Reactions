package me.tochuuu.reactions.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

public final class AdvancementMouthReaction {
    private static final long DURATION_MILLIS = 3000L;
    private static final String[] VANILLA_ADVANCEMENT_PHRASES = {
        " has made the advancement ",
        " has completed the challenge ",
        " has reached the goal "
    };
    private static final java.util.Map<Integer, Long> ACTIVE_UNTIL_BY_ENTITY = new java.util.HashMap<>();
    private static long activeUntilMillis;

    private AdvancementMouthReaction() {
    }

    public static void trigger() {
        activeUntilMillis = System.currentTimeMillis() + DURATION_MILLIS;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            trigger(minecraft.player.getId());
        }
    }

    public static void trigger(int entityId) {
        ACTIVE_UNTIL_BY_ENTITY.put(entityId, System.currentTimeMillis() + DURATION_MILLIS);
    }

    public static boolean active() {
        return System.currentTimeMillis() < activeUntilMillis;
    }

    public static boolean active(int entityId) {
        Long activeUntil = ACTIVE_UNTIL_BY_ENTITY.get(entityId);
        if (activeUntil == null) {
            return false;
        }
        if (System.currentTimeMillis() < activeUntil) {
            return true;
        }
        ACTIVE_UNTIL_BY_ENTITY.remove(entityId);
        return false;
    }

    public static void triggerFromChat(Component message) {
        if (message.getContents() instanceof TranslatableContents translatable && translatable.getKey().startsWith("chat.type.advancement.")) {
            Object[] args = translatable.getArgs();
            if (args.length > 0) {
                triggerPlayerNamed(componentText(args[0]));
            }
            return;
        }

        String text = message.getString();
        for (String phrase : VANILLA_ADVANCEMENT_PHRASES) {
            int phraseIndex = text.indexOf(phrase);
            if (phraseIndex > 0) {
                triggerPlayerNamed(text.substring(0, phraseIndex));
                return;
            }
        }
    }

    private static String componentText(Object value) {
        if (value instanceof Component component) {
            return component.getString();
        }
        return String.valueOf(value);
    }

    private static void triggerPlayerNamed(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || name == null || name.isBlank()) {
            return;
        }

        String cleanName = stripFormatting(name);
        for (AbstractClientPlayer player : minecraft.level.players()) {
            String playerName = player.getName().getString();
            if (cleanName.equals(playerName) || cleanName.endsWith(playerName)) {
                trigger(player.getId());
                return;
            }
        }
    }

    private static String stripFormatting(String value) {
        return value.replaceAll("\u00A7.", "").trim();
    }
}
