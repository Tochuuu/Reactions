package me.tochuuu.reactions.client;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemUseAnimation;

import java.util.HashMap;
import java.util.Map;

public final class PlayerActionAnimationState {
    private static final float FALL_SURPRISE_DISTANCE = 3.0F;
    private static final float VISIBLE_FALL_SURPRISE_DISTANCE = 1.2F;
    private static final double FALL_SURPRISE_SPEED = -0.35D;
    private static final int FALL_SURPRISE_MIN_TICKS = 30;
    private static final int LANDING_BLINK_TICKS = 7;
    private static final Snapshot EMPTY = new Snapshot(MouthUseAnimation.NONE, false, false);
    private static final Map<Integer, Snapshot> SNAPSHOTS = new HashMap<>();
    private static final Map<Integer, Float> FALL_DISTANCE_PEAKS = new HashMap<>();
    private static final Map<Integer, Float> VISIBLE_FALL_DISTANCE_PEAKS = new HashMap<>();
    private static final Map<Integer, Double> LAST_VISIBLE_Y = new HashMap<>();
    private static final Map<Integer, Double> VISIBLE_VERTICAL_SPEEDS = new HashMap<>();
    private static final Map<Integer, Integer> FALL_SAMPLE_TICKS = new HashMap<>();
    private static final Map<Integer, Integer> FALL_STARTED_AT = new HashMap<>();
    private static final Map<Integer, Float> LANDING_BLINK_UNTIL = new HashMap<>();

    private PlayerActionAnimationState() {
    }

    public static void capture(Avatar player, AvatarRenderState state) {
        int entityId = player.getId();
        MouthUseAnimation mouthUseAnimation = mouthUseAnimation(player);
        FallState fallState = fallState(player, state);
        SNAPSHOTS.put(entityId, new Snapshot(mouthUseAnimation, fallState.surprise(), fallState.landingBlink()));
    }

    public static Snapshot snapshot(int entityId) {
        return SNAPSHOTS.getOrDefault(entityId, EMPTY);
    }

    private static MouthUseAnimation mouthUseAnimation(Avatar player) {
        if (!player.isUsingItem()) {
            return MouthUseAnimation.NONE;
        }

        ItemUseAnimation animation = player.getUseItem().getUseAnimation();
        if (animation == ItemUseAnimation.EAT) {
            return MouthUseAnimation.EATING;
        }
        if (animation == ItemUseAnimation.DRINK) {
            return MouthUseAnimation.DRINKING;
        }
        return MouthUseAnimation.NONE;
    }

    private static FallState fallState(Avatar player, AvatarRenderState state) {
        int entityId = player.getId();
        updateVisibleFallState(player);

        boolean onGround = isFallInterrupted(player);
        float previousPeak = Math.max(
            FALL_DISTANCE_PEAKS.getOrDefault(entityId, 0.0F),
            VISIBLE_FALL_DISTANCE_PEAKS.getOrDefault(entityId, 0.0F)
        );
        int fallingTicks = 0;
        if (onGround) {
            if (previousPeak >= FALL_SURPRISE_DISTANCE) {
                LANDING_BLINK_UNTIL.put(entityId, state.ageInTicks + LANDING_BLINK_TICKS);
            }
            FALL_DISTANCE_PEAKS.remove(entityId);
            VISIBLE_FALL_DISTANCE_PEAKS.remove(entityId);
            FALL_STARTED_AT.remove(entityId);
        } else {
            int fallStartedAt = FALL_STARTED_AT.computeIfAbsent(entityId, ignored -> player.tickCount);
            fallingTicks = Math.max(0, player.tickCount - fallStartedAt);
            FALL_DISTANCE_PEAKS.put(entityId, Math.max(previousPeak, (float) player.fallDistance));
        }

        Float blinkUntil = LANDING_BLINK_UNTIL.get(entityId);
        boolean landingBlink = blinkUntil != null && state.ageInTicks < blinkUntil;
        if (blinkUntil != null && !landingBlink) {
            LANDING_BLINK_UNTIL.remove(entityId);
        }

        boolean surprise = !onGround && fallingTicks >= FALL_SURPRISE_MIN_TICKS && isFallingFastEnough(player);
        return new FallState(surprise, landingBlink);
    }

    private static void updateVisibleFallState(Avatar player) {
        int entityId = player.getId();
        int tick = player.tickCount;
        Integer lastTick = FALL_SAMPLE_TICKS.put(entityId, tick);
        Double lastY = LAST_VISIBLE_Y.put(entityId, player.getY());
        if (lastTick == null || lastY == null || lastTick == tick) {
            return;
        }

        int elapsedTicks = Math.max(1, tick - lastTick);
        double visibleSpeed = (player.getY() - lastY) / elapsedTicks;
        VISIBLE_VERTICAL_SPEEDS.put(entityId, visibleSpeed);
        if (isFallInterrupted(player)) {
            return;
        }

        if (visibleSpeed < -0.01D) {
            float visibleDrop = (float) -visibleSpeed * elapsedTicks;
            float previousPeak = VISIBLE_FALL_DISTANCE_PEAKS.getOrDefault(entityId, 0.0F);
            VISIBLE_FALL_DISTANCE_PEAKS.put(entityId, previousPeak + visibleDrop);
        }
    }

    private static boolean isFallInterrupted(Avatar player) {
        return player.onGround()
            || player.isInWater()
            || player.isPassenger()
            || player.isFallFlying();
    }

    private static boolean isFallingFastEnough(Avatar player) {
        int entityId = player.getId();
        return player.fallDistance >= FALL_SURPRISE_DISTANCE
            || VISIBLE_FALL_DISTANCE_PEAKS.getOrDefault(entityId, 0.0F) >= VISIBLE_FALL_SURPRISE_DISTANCE
            || player.getDeltaMovement().y <= FALL_SURPRISE_SPEED
            || VISIBLE_VERTICAL_SPEEDS.getOrDefault(entityId, 0.0D) <= FALL_SURPRISE_SPEED;
    }

    public enum MouthUseAnimation {
        NONE,
        EATING,
        DRINKING
    }

    public record Snapshot(MouthUseAnimation mouthUseAnimation, boolean fallingSurprise, boolean landingBlink) {
    }

    private record FallState(boolean surprise, boolean landingBlink) {
    }
}
