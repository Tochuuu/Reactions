package me.tochuuu.reactions.client;

import com.mojang.blaze3d.platform.InputConstants;
import me.tochuuu.reactions.Reactions;
import me.tochuuu.reactions.network.ReactionsNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class ReactionsClient {
    private static final int DOUBLE_TAP_TICKS = 8;
    private static final int HOLD_ACTIVATION_DELAY_TICKS = 1;
    private static final int LOOK_RELEASE_GRACE_TICKS = 2;
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Reactions.MOD_ID, "key"));
    private static final KeyMapping OPEN_CONFIG = new KeyMapping("key.reactions.open_config", InputConstants.Type.KEYSYM, InputConstants.KEY_R, CATEGORY);
    private static final KeyMapping MANUAL_CLOSE_EYES = new KeyMapping("key.reactions.manual_close_eyes", InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), CATEGORY);
    private static final KeyMapping MANUAL_SQUINT_EYES = new KeyMapping("key.reactions.manual_squint_eyes", InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), CATEGORY);
    private static final KeyMapping MANUAL_LOOK_EYES = new KeyMapping("key.reactions.manual_look_eyes", InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), CATEGORY);
    private static final HeldKeyState CLOSE_EYES_STATE = new HeldKeyState();
    private static final HeldKeyState SQUINT_EYES_STATE = new HeldKeyState();
    private static final HeldKeyState LOOK_EYES_STATE = new HeldKeyState();
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

    public static KeyMapping manualLookEyesKey() {
        return MANUAL_LOOK_EYES;
    }

    public static boolean manualEyeKeysUnbound() {
        return MANUAL_CLOSE_EYES.isUnbound() || MANUAL_SQUINT_EYES.isUnbound() || MANUAL_LOOK_EYES.isUnbound();
    }

    public static ManualEyeControl manualEyeControl() {
        if (CLOSE_EYES_STATE.active()) {
            if (CLOSE_EYES_STATE.tripleTapHold) {
                return ManualEyeControl.CLOSE_LEFT;
            }
            return CLOSE_EYES_STATE.doubleTapHold ? ManualEyeControl.CLOSE_ONE : ManualEyeControl.CLOSE_BOTH;
        }
        if (SQUINT_EYES_STATE.active()) {
            if (LOOK_EYES_STATE.active()) {
                if (SQUINT_EYES_STATE.doubleTapHold) {
                    return LOOK_EYES_STATE.doubleTapHold ? ManualEyeControl.SURPRISE_LOOK_RIGHT : ManualEyeControl.SURPRISE_LOOK_LEFT;
                }
                return LOOK_EYES_STATE.doubleTapHold ? ManualEyeControl.SQUINT_LOOK_RIGHT : ManualEyeControl.SQUINT_LOOK_LEFT;
            }
            return SQUINT_EYES_STATE.doubleTapHold ? ManualEyeControl.SURPRISE_BOTH : ManualEyeControl.SQUINT_BOTH;
        }
        if (LOOK_EYES_STATE.active()) {
            return LOOK_EYES_STATE.doubleTapHold ? ManualEyeControl.LOOK_RIGHT : ManualEyeControl.LOOK_LEFT;
        }
        return ManualEyeControl.NONE;
    }

    public static void onClientTick(Minecraft client) {
        BlockInteractionEyeFocus.onClientTick(client);
        CLOSE_EYES_STATE.update(MANUAL_CLOSE_EYES);
        SQUINT_EYES_STATE.update(MANUAL_SQUINT_EYES);
        LOOK_EYES_STATE.update(MANUAL_LOOK_EYES, LOOK_RELEASE_GRACE_TICKS);
        ReactionsNetworking.sendLocalManualEyeControl(manualEyeControl().networkValue());
        while (OPEN_CONFIG.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gui.screen() == null) {
                minecraft.gui.setScreen(new ReactionsConfigScreen(null));
            }
        }
    }

    public enum ManualEyeControl {
        NONE,
        CLOSE_BOTH,
        CLOSE_ONE,
        SQUINT_BOTH,
        SURPRISE_BOTH,
        LOOK_LEFT,
        LOOK_RIGHT,
        SQUINT_LOOK_LEFT,
        SQUINT_LOOK_RIGHT,
        SURPRISE_LOOK_LEFT,
        SURPRISE_LOOK_RIGHT,
        CLOSE_LEFT;

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
            return this == CLOSE_BOTH || this == CLOSE_ONE || this == CLOSE_LEFT;
        }

        public boolean squintsEyes() {
            return this == SQUINT_BOTH || this == SQUINT_LOOK_LEFT || this == SQUINT_LOOK_RIGHT;
        }

        public boolean surprisesEyes() {
            return this == SURPRISE_BOTH || this == SURPRISE_LOOK_LEFT || this == SURPRISE_LOOK_RIGHT;
        }

        public boolean looksLeft() {
            return this == LOOK_LEFT || this == SQUINT_LOOK_LEFT || this == SURPRISE_LOOK_LEFT;
        }

        public boolean looksRight() {
            return this == LOOK_RIGHT || this == SQUINT_LOOK_RIGHT || this == SURPRISE_LOOK_RIGHT;
        }

        public boolean affectsLeftEye() {
            return this == CLOSE_BOTH || this == CLOSE_LEFT || this == SQUINT_BOTH || this == SURPRISE_BOTH || this == SQUINT_LOOK_LEFT || this == SQUINT_LOOK_RIGHT || this == SURPRISE_LOOK_LEFT || this == SURPRISE_LOOK_RIGHT;
        }

        public boolean affectsRightEye() {
            return this == CLOSE_BOTH || this == CLOSE_ONE || this == SQUINT_BOTH || this == SURPRISE_BOTH || this == SQUINT_LOOK_LEFT || this == SQUINT_LOOK_RIGHT || this == SURPRISE_LOOK_LEFT || this == SURPRISE_LOOK_RIGHT;
        }
    }

    private static final class HeldKeyState {
        private boolean wasDown;
        private boolean active;
        private boolean doubleTapHold;
        private boolean tripleTapHold;
        private int lastTapTick = -DOUBLE_TAP_TICKS * 2;
        private int pressedAtTick = -DOUBLE_TAP_TICKS * 2;
        private int releaseGraceTicks;
        private int tapCount;
        private int ticks;

        private void update(KeyMapping key) {
            update(key, 0);
        }

        private void update(KeyMapping key, int releaseGraceTicks) {
            ticks++;
            boolean down = key.isDown();
            if (down && !wasDown) {
                tapCount = ticks - lastTapTick <= DOUBLE_TAP_TICKS ? Math.min(3, tapCount + 1) : 1;
                doubleTapHold = tapCount >= 2;
                tripleTapHold = tapCount >= 3;
                active = doubleTapHold;
                this.releaseGraceTicks = 0;
                pressedAtTick = ticks;
                lastTapTick = ticks;
            } else if (down && !active && ticks - pressedAtTick >= HOLD_ACTIVATION_DELAY_TICKS) {
                active = true;
            } else if (!down) {
                if (wasDown && active && releaseGraceTicks > 0) {
                    this.releaseGraceTicks = releaseGraceTicks;
                } else if (this.releaseGraceTicks > 0) {
                    this.releaseGraceTicks--;
                }
                active = false;
                if (this.releaseGraceTicks <= 0) {
                    doubleTapHold = false;
                    tripleTapHold = false;
                }
            }
            wasDown = down;
        }

        private boolean active() {
            return active || releaseGraceTicks > 0;
        }
    }
}
