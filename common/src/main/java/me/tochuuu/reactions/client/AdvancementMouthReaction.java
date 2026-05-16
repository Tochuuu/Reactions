package me.tochuuu.reactions.client;

public final class AdvancementMouthReaction {
    private static final long DURATION_MILLIS = 3000L;
    private static long activeUntilMillis;

    private AdvancementMouthReaction() {
    }

    public static void trigger() {
        activeUntilMillis = System.currentTimeMillis() + DURATION_MILLIS;
    }

    public static boolean active() {
        return System.currentTimeMillis() < activeUntilMillis;
    }
}
