package me.tochuuu.reactions.client;

import me.tochuuu.reactions.network.ReactionsNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class BlockInteractionEyeFocus {
    private static final int RECENT_BLOCK_TICKS = 8;
    private static final int ENTITY_SCAN_INTERVAL_TICKS = 4;
    private static final double MAX_FOCUS_ANGLE = Math.toRadians(55.0D);
    private static final double ENTITY_FOCUS_RANGE = 6.0D;
    private static final double ENTITY_MAX_FOCUS_ANGLE = Math.toRadians(85.0D);
    private static final float ENTITY_FOCUS_STEP = 0.08F;
    private static final float FOCUS_DEAD_ZONE = 0.18F;
    private static BlockPos lastLookedBlock;
    private static BlockPos activeBlock;
    private static int lastLookedBlockTick;
    private static int ticks;
    private static int lastSentFocus = Integer.MIN_VALUE;
    private static int lastSentMode = Integer.MIN_VALUE;
    private static float entityFocusTarget;
    private static float entityFocusAmount;
    private static float localFocusAmount;
    private static int localFocusMode = ReactionsNetworking.EYE_FOCUS_NONE;
    private static boolean hadBlockInteractionScreen;

    private BlockInteractionEyeFocus() {
    }

    public static void onClientTick(Minecraft client) {
        ticks++;
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            clear();
            return;
        }

        rememberLookedBlock(client);
        if (isBlockInteractionScreen(client.screen)) {
            if (!hadBlockInteractionScreen) {
                activeBlock = ticks - lastLookedBlockTick <= RECENT_BLOCK_TICKS ? lastLookedBlock : null;
                hadBlockInteractionScreen = true;
            }

            entityFocusTarget = 0.0F;
            entityFocusAmount = 0.0F;
            updateFocus(activeBlock == null ? 0.0F : calculateFocus(player, activeBlock), ReactionsNetworking.EYE_FOCUS_BLOCK);
            return;
        }

        if (client.screen != null) {
            activeBlock = null;
            hadBlockInteractionScreen = false;
            entityFocusTarget = 0.0F;
            entityFocusAmount = 0.0F;
            updateFocus(0.0F, ReactionsNetworking.EYE_FOCUS_NONE);
            return;
        }

        activeBlock = null;
        hadBlockInteractionScreen = false;
        if (ticks % ENTITY_SCAN_INTERVAL_TICKS == 0) {
            entityFocusTarget = entityFocusTarget(client, player);
        }
        entityFocusAmount = approach(entityFocusAmount, entityFocusTarget, ENTITY_FOCUS_STEP);
        updateFocus(entityFocusAmount, ReactionsNetworking.EYE_FOCUS_ENTITY);
    }

    public static int localFocusMode() {
        return localFocusMode;
    }

    public static float localFocusAmount() {
        return localFocusAmount;
    }

    private static void rememberLookedBlock(Minecraft client) {
        if (client.hitResult instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
            lastLookedBlock = blockHit.getBlockPos();
            lastLookedBlockTick = ticks;
        }
    }

    private static boolean isBlockInteractionScreen(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?>)) {
            return false;
        }

        String screenName = screen.getClass().getName();
        return !screenName.endsWith("InventoryScreen") && !screenName.endsWith("CreativeModeInventoryScreen");
    }

    private static float calculateFocus(LocalPlayer player, BlockPos blockPos) {
        Vec3 eyePosition = player.getEyePosition();
        Vec3 toBlock = Vec3.atCenterOf(blockPos).subtract(eyePosition);
        Vec3 look = player.getViewVector(1.0F);

        double targetLength = Math.sqrt(toBlock.x * toBlock.x + toBlock.z * toBlock.z);
        double lookLength = Math.sqrt(look.x * look.x + look.z * look.z);
        if (targetLength < 0.001D || lookLength < 0.001D) {
            return 0.0F;
        }

        double targetX = toBlock.x / targetLength;
        double targetZ = toBlock.z / targetLength;
        double lookX = look.x / lookLength;
        double lookZ = look.z / lookLength;
        double side = lookZ * targetX - lookX * targetZ;
        double forward = lookX * targetX + lookZ * targetZ;
        float focus = (float) clamp(Math.atan2(side, forward) / MAX_FOCUS_ANGLE, -1.0D, 1.0D);
        return Math.abs(focus) < FOCUS_DEAD_ZONE ? 0.0F : focus;
    }

    private static float entityFocusTarget(Minecraft client, LocalPlayer player) {
        if (client.hitResult instanceof EntityHitResult entityHit && isFocusableEntity(player, entityHit.getEntity())) {
            return calculateFocus(player, entityHit.getEntity());
        }

        Entity bestEntity = null;
        float bestFocus = 0.0F;
        double bestScore = 0.0D;
        AABB searchBox = player.getBoundingBox().inflate(ENTITY_FOCUS_RANGE);
        List<Entity> entities = player.level().getEntities(player, searchBox, entity -> isFocusableEntity(player, entity));
        for (Entity entity : entities) {
            float focus = calculateFocus(player, entity);
            if (Math.abs(focus) < FOCUS_DEAD_ZONE) {
                continue;
            }

            double distance = player.distanceToSqr(entity);
            double score = Math.abs(focus) * 2.0D + Math.max(0.0D, ENTITY_FOCUS_RANGE * ENTITY_FOCUS_RANGE - distance) / (ENTITY_FOCUS_RANGE * ENTITY_FOCUS_RANGE);
            if (score > bestScore) {
                bestScore = score;
                bestFocus = focus;
                bestEntity = entity;
            }
        }
        return bestEntity == null ? 0.0F : bestFocus;
    }

    private static boolean isFocusableEntity(LocalPlayer player, Entity entity) {
        return entity instanceof LivingEntity
            && entity != player
            && entity.isAlive()
            && !entity.isInvisible()
            && player.distanceToSqr(entity) <= ENTITY_FOCUS_RANGE * ENTITY_FOCUS_RANGE;
    }

    private static float calculateFocus(LocalPlayer player, Entity entity) {
        Vec3 eyePosition = player.getEyePosition();
        Vec3 targetPosition = entity.position().add(0.0D, entity.getBbHeight() * 0.65D, 0.0D);
        Vec3 toEntity = targetPosition.subtract(eyePosition);
        Vec3 look = player.getViewVector(1.0F);

        double targetLength = Math.sqrt(toEntity.x * toEntity.x + toEntity.z * toEntity.z);
        double lookLength = Math.sqrt(look.x * look.x + look.z * look.z);
        if (targetLength < 0.001D || lookLength < 0.001D) {
            return 0.0F;
        }

        double targetX = toEntity.x / targetLength;
        double targetZ = toEntity.z / targetLength;
        double lookX = look.x / lookLength;
        double lookZ = look.z / lookLength;
        double side = lookZ * targetX - lookX * targetZ;
        double forward = lookX * targetX + lookZ * targetZ;
        double angle = Math.atan2(side, forward);
        if (Math.abs(angle) > ENTITY_MAX_FOCUS_ANGLE) {
            return 0.0F;
        }

        float focus = (float) clamp(angle / MAX_FOCUS_ANGLE, -1.0D, 1.0D);
        return Math.abs(focus) < FOCUS_DEAD_ZONE ? 0.0F : focus;
    }

    private static void updateFocus(float focus, int mode) {
        int resolvedMode = Math.abs(focus) < FOCUS_DEAD_ZONE ? ReactionsNetworking.EYE_FOCUS_NONE : mode;
        float resolvedFocus = resolvedMode == ReactionsNetworking.EYE_FOCUS_NONE ? 0.0F : focus;
        localFocusMode = resolvedMode;
        localFocusAmount = resolvedFocus;
        int quantizedFocus = Math.round(resolvedFocus * 100.0F);
        if (quantizedFocus != lastSentFocus || resolvedMode != lastSentMode) {
            lastSentFocus = quantizedFocus;
            lastSentMode = resolvedMode;
            ReactionsNetworking.sendLocalEyeFocus(quantizedFocus, resolvedMode);
        }
    }

    private static void clear() {
        lastLookedBlock = null;
        activeBlock = null;
        lastLookedBlockTick = 0;
        hadBlockInteractionScreen = false;
        entityFocusTarget = 0.0F;
        entityFocusAmount = 0.0F;
        updateFocus(0.0F, ReactionsNetworking.EYE_FOCUS_NONE);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float approach(float value, float target, float step) {
        if (value < target) {
            return Math.min(value + step, target);
        }
        if (value > target) {
            return Math.max(value - step, target);
        }
        return value;
    }
}
