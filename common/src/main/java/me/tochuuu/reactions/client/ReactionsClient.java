package me.tochuuu.reactions.client;

import com.mojang.blaze3d.platform.InputConstants;
import me.tochuuu.reactions.Reactions;
import me.tochuuu.reactions.network.ReactionsNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class ReactionsClient {
    private static final int DOUBLE_TAP_TICKS = 8;
    private static final int HOLD_ACTIVATION_DELAY_TICKS = 1;
    private static final String CATEGORY = "key.category.reactions.key";
    private static final KeyMapping OPEN_CONFIG = new KeyMapping("key.reactions.open_config", InputConstants.Type.KEYSYM, InputConstants.KEY_R, CATEGORY);
    private static final KeyMapping MANUAL_CLOSE_EYES = new KeyMapping("key.reactions.manual_close_eyes", InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), CATEGORY);
    private static final KeyMapping MANUAL_SQUINT_EYES = new KeyMapping("key.reactions.manual_squint_eyes", InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), CATEGORY);
    private static final HeldKeyState CLOSE_EYES_STATE = new HeldKeyState();
    private static final HeldKeyState SQUINT_EYES_STATE = new HeldKeyState();
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

    public static KeyMapping manualCloseEyesKey() {
        return MANUAL_CLOSE_EYES;
    }

    public static KeyMapping manualSquintEyesKey() {
        return MANUAL_SQUINT_EYES;
    }

    public static boolean manualEyeKeysUnbound() {
        return MANUAL_CLOSE_EYES.isUnbound() || MANUAL_SQUINT_EYES.isUnbound();
    }

    public static ManualEyeControl manualEyeControl() {
        if (CLOSE_EYES_STATE.active()) {
            return CLOSE_EYES_STATE.oneEyeHold ? ManualEyeControl.CLOSE_ONE : ManualEyeControl.CLOSE_BOTH;
        }
        if (SQUINT_EYES_STATE.active()) {
            return SQUINT_EYES_STATE.oneEyeHold ? ManualEyeControl.SQUINT_ONE : ManualEyeControl.SQUINT_BOTH;
        }
        return ManualEyeControl.NONE;
    }

    public static void onClientTick(Minecraft client) {
        BlockInteractionEyeFocus.onClientTick(client);
        CLOSE_EYES_STATE.update(MANUAL_CLOSE_EYES);
        SQUINT_EYES_STATE.update(MANUAL_SQUINT_EYES);
        ReactionsNetworking.sendLocalManualEyeControl(manualEyeControl().networkValue());
        while (OPEN_CONFIG.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen == null) {
                minecraft.setScreen(new ReactionsConfigScreen(null));
            }
        }
    }

    public enum ManualEyeControl {
        NONE,
        CLOSE_BOTH,
        CLOSE_ONE,
        SQUINT_BOTH,
        SQUINT_ONE;

        public static ManualEyeControl fromNetwork(int value) {
            ManualEyeControl[] values = values();
            return value >= 0 && value < values.length ? values[value] : NONE;
        }

        public int networkValue() {
            return ordinal();
        }

        public boolean active() {
            return this != NONE;
        }

        public boolean closesEyes() {
            return this == CLOSE_BOTH || this == CLOSE_ONE;
        }

        public boolean squintsEyes() {
            return this == SQUINT_BOTH || this == SQUINT_ONE;
        }

        public boolean affectsLeftEye() {
            return this == CLOSE_BOTH || this == SQUINT_BOTH;
        }

        public boolean affectsRightEye() {
            return this != NONE;
        }
    }

    private static final class HeldKeyState {
        private boolean wasDown;
        private boolean active;
        private boolean oneEyeHold;
        private int lastTapTick = -DOUBLE_TAP_TICKS * 2;
        private int pressedAtTick = -DOUBLE_TAP_TICKS * 2;
        private int ticks;

        private void update(KeyMapping key) {
            ticks++;
            boolean down = key.isDown();
            if (down && !wasDown) {
                oneEyeHold = ticks - lastTapTick <= DOUBLE_TAP_TICKS;
                active = oneEyeHold;
                pressedAtTick = ticks;
                lastTapTick = ticks;
            } else if (down && !active && ticks - pressedAtTick >= HOLD_ACTIVATION_DELAY_TICKS) {
                active = true;
            } else if (!down) {
                active = false;
                oneEyeHold = false;
            }
            wasDown = down;
        }

        private boolean active() {
            return active;
        }
    }
}
