package me.tochuuu.reactions.client;

import me.tochuuu.reactions.network.ReactionsNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class BlockInteractionEyeFocus {
    private static final int RECENT_BLOCK_TICKS = 8;
    private static final double MAX_FOCUS_ANGLE = Math.toRadians(55.0D);
    private static final float FOCUS_DEAD_ZONE = 0.18F;
    private static final float BLOCK_FOCUS_STEP = 0.22F;
    private static final float READING_FOCUS_STEP = 0.08F;
    private static final float FOCUS_RELEASE_STEP = 0.18F;
    private static final float READING_FOCUS_AMOUNT = 0.38F;
    private static final int DIRECT_BLOCK_FOCUS_SIGNAL = 101;
    private static final float DIRECT_BLOCK_FOCUS_AMOUNT = 0.0F;
    private static final int READING_FOCUS_CYCLE_TICKS = 52;
    private static BlockPos lastLookedBlock;
    private static BlockPos activeBlock;
    private static int lastLookedBlockTick;
    private static int ticks;
    private static int lastSentFocus = Integer.MIN_VALUE;
    private static float localFocusAmount;
    private static boolean localDirectFocus;
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
        if (isReadingScreen(client.gui.screen())) {
            activeBlock = null;
            hadBlockInteractionScreen = false;
            updateFocus(readingFocus(), false, READING_FOCUS_STEP);
            return;
        }

        if (!isBlockInteractionScreen(client.gui.screen())) {
            activeBlock = null;
            hadBlockInteractionScreen = false;
            updateFocus(0.0F, false, FOCUS_RELEASE_STEP);
            return;
        }

        if (!hadBlockInteractionScreen) {
            activeBlock = ticks - lastLookedBlockTick <= RECENT_BLOCK_TICKS ? lastLookedBlock : null;
            hadBlockInteractionScreen = true;
        }

        FocusTarget focusTarget = activeBlock == null ? FocusTarget.NONE : calculateFocus(player, activeBlock);
        updateFocus(focusTarget.amount(), focusTarget.direct(), BLOCK_FOCUS_STEP);
    }

    public static float localFocusAmount() {
        return localFocusAmount;
    }

    public static boolean localDirectFocus() {
        return localDirectFocus;
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

    private static boolean isReadingScreen(Screen screen) {
        if (screen == null) {
            return false;
        }

        String screenName = screen.getClass().getName();
        return screenName.endsWith("BookViewScreen")
            || screenName.endsWith("BookEditScreen")
            || screenName.endsWith("SignEditScreen")
            || screenName.endsWith("HangingSignEditScreen");
    }

    private static float readingFocus() {
        int phase = Math.floorMod(ticks, READING_FOCUS_CYCLE_TICKS);
        if (phase < 18) {
            return -READING_FOCUS_AMOUNT;
        }
        if (phase < 26) {
            return 0.0F;
        }
        if (phase < 44) {
            return READING_FOCUS_AMOUNT;
        }
        return 0.0F;
    }

    private static FocusTarget calculateFocus(LocalPlayer player, BlockPos blockPos) {
        Vec3 eyePosition = player.getEyePosition();
        Vec3 toBlock = Vec3.atCenterOf(blockPos).subtract(eyePosition);
        Vec3 look = player.getViewVector(1.0F);

        double targetLength = Math.sqrt(toBlock.x * toBlock.x + toBlock.z * toBlock.z);
        double lookLength = Math.sqrt(look.x * look.x + look.z * look.z);
        if (targetLength < 0.001D || lookLength < 0.001D) {
            return FocusTarget.NONE;
        }

        double targetX = toBlock.x / targetLength;
        double targetZ = toBlock.z / targetLength;
        double lookX = look.x / lookLength;
        double lookZ = look.z / lookLength;
        double side = lookZ * targetX - lookX * targetZ;
        double forward = lookX * targetX + lookZ * targetZ;
        float focus = (float) clamp(Math.atan2(side, forward) / MAX_FOCUS_ANGLE, -1.0D, 1.0D);
        if (Math.abs(focus) < FOCUS_DEAD_ZONE) {
            return forward > 0.0D ? FocusTarget.DIRECT : FocusTarget.NONE;
        }
        return new FocusTarget(focus, false);
    }

    private static void updateFocus(float focus, boolean directFocus, float step) {
        localDirectFocus = directFocus;
        localFocusAmount = approach(localFocusAmount, focus, step);
        int quantizedFocus = directFocus ? DIRECT_BLOCK_FOCUS_SIGNAL : Math.round(localFocusAmount * 100.0F);
        if (quantizedFocus != lastSentFocus) {
            lastSentFocus = quantizedFocus;
            ReactionsNetworking.sendLocalEyeFocus(quantizedFocus);
        }
    }

    private static void clear() {
        lastLookedBlock = null;
        activeBlock = null;
        lastLookedBlockTick = 0;
        hadBlockInteractionScreen = false;
        localDirectFocus = false;
        setFocus(0.0F);
    }

    private static void setFocus(float focus) {
        localDirectFocus = false;
        localFocusAmount = focus;
        int quantizedFocus = Math.round(focus * 100.0F);
        if (quantizedFocus != lastSentFocus) {
            lastSentFocus = quantizedFocus;
            ReactionsNetworking.sendLocalEyeFocus(quantizedFocus);
        }
    }

    private static float approach(float current, float target, float step) {
        if (current < target) {
            return Math.min(target, current + step);
        }
        if (current > target) {
            return Math.max(target, current - step);
        }
        return current;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record FocusTarget(float amount, boolean direct) {
        private static final FocusTarget NONE = new FocusTarget(0.0F, false);
        private static final FocusTarget DIRECT = new FocusTarget(DIRECT_BLOCK_FOCUS_AMOUNT, true);
    }
}
