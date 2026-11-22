package me.tochuuu.reactions.client;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class PlayerActionAnimationState {
    private static final float FALL_SURPRISE_DISTANCE = 3.0F;
    private static final float VISIBLE_FALL_SURPRISE_DISTANCE = 1.2F;
    private static final double FALL_SURPRISE_SPEED = -0.35D;
    private static final int FALL_SURPRISE_MIN_TICKS = 30;
    private static final int LANDING_BLINK_TICKS = 7;
    private static final double SUN_SQUINT_ENTER_DOT = Math.cos(Math.toRadians(12.0D));
    private static final double SUN_SQUINT_EXIT_DOT = Math.cos(Math.toRadians(15.0D));
    private static final double SUN_VISIBILITY_DISTANCE = 256.0D;
    private static final Snapshot EMPTY = new Snapshot(MouthUseAnimation.NONE, false, false, 0.0F, false);
    private static final Map<Avatar, SunVisibilitySample> SUN_VISIBILITY = new WeakHashMap<>();
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

    public static void capture(Avatar player, AvatarRenderState state, float partialTick) {
        int entityId = player.getId();
        MouthUseAnimation mouthUseAnimation = mouthUseAnimation(player);
        FallState fallState = fallState(player, state);
        SNAPSHOTS.put(entityId, new Snapshot(mouthUseAnimation, fallState.surprise(), fallState.landingBlink(), mountedYawDelta(player, state, partialTick), sunSquint(player, partialTick)));
    }

    public static Snapshot snapshot(int entityId) {
        return SNAPSHOTS.getOrDefault(entityId, EMPTY);
    }

    private static boolean sunSquint(Avatar player, float partialTick) {
        ReactionsClientConfig config = ReactionsClientConfig.get();
        Level level = player.level();
        if (!config.enabled || !config.animateSunSquint || player.isSleeping() || player.isDeadOrDying()
            || level.dimensionType().skybox() != DimensionType.Skybox.OVERWORLD
            || level.getRainLevel(partialTick) > 0.2F || level.getThunderLevel(partialTick) > 0.2F
            || player.isEyeInFluid(FluidTags.WATER) || player.isEyeInFluid(FluidTags.LAVA)) {
            SUN_VISIBILITY.remove(player);
            return false;
        }

        Vec3 eyePosition = player.getEyePosition(partialTick);
        float sunAngle = level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, eyePosition) * Mth.DEG_TO_RAD;
        // Match SkyRenderer's Y(-90 degrees) * X(sunAngle) rotation of the sun.
        Vec3 sunDirection = new Vec3(-Math.sin(sunAngle), Math.cos(sunAngle), 0.0D);
        Vec3 look = Vec3.directionFromRotation(player.getXRot(partialTick), Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot));
        SunVisibilitySample previous = SUN_VISIBILITY.get(player);
        double threshold = previous != null && previous.visible ? SUN_SQUINT_EXIT_DOT : SUN_SQUINT_ENTER_DOT;
        if (sunDirection.y <= 0.05D || look.dot(sunDirection) < threshold) {
            SUN_VISIBILITY.remove(player);
            return false;
        }

        // Recheck facing every frame, but raycast at most once per player tick.
        if (previous != null && previous.tick == player.tickCount) {
            return previous.visible;
        }
        boolean visible = level.clip(new ClipContext(eyePosition, eyePosition.add(sunDirection.scale(SUN_VISIBILITY_DISTANCE)),
            ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, player)).getType() == HitResult.Type.MISS;
        SUN_VISIBILITY.put(player, new SunVisibilitySample(player.tickCount, visible));
        return visible;
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
        if (animation == ItemUseAnimation.TOOT_HORN) {
            return MouthUseAnimation.WHISTLING;
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

    private static float mountedYawDelta(Avatar player, AvatarRenderState state, float partialTick) {
        if (!state.isPassenger) {
            return 0.0F;
        }

        Entity vehicle = player.getVehicle();
        // Being rideable via /ride alone does not make an entity a constrained mount.
        if (vehicle == null || !(vehicle instanceof AbstractBoat) && vehicle.getControllingPassenger() == null) {
            return 0.0F;
        }
        float referenceYaw = vehicle.getYRot(partialTick);
        float headYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
        return Mth.wrapDegrees(headYaw - referenceYaw);
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
        DRINKING,
        WHISTLING
    }

    public record Snapshot(MouthUseAnimation mouthUseAnimation, boolean fallingSurprise, boolean landingBlink, float mountedYawDelta, boolean sunSquint) {
    }

    private record SunVisibilitySample(int tick, boolean visible) {
    }

    private record FallState(boolean surprise, boolean landingBlink) {
    }
}
