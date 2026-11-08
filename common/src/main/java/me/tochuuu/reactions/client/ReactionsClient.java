package me.tochuuu.reactions.client;

import com.mojang.blaze3d.platform.InputConstants;
import me.tochuuu.reactions.Reactions;
import me.tochuuu.reactions.network.ReactionsNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class ReactionsClient {
    private static final int DOUBLE_TAP_TICKS = 8;
    private static final int HOLD_ACTIVATION_DELAY_TICKS = 1;
    private static final int LOOK_RELEASE_GRACE_TICKS = 2;
    private static final String CATEGORY = "key.category.reactions.key";
    private static final KeyMapping OPEN_CONFIG = new KeyMapping("key.reactions.open_config", InputConstants.Type.KEYSYM, InputConstants.KEY_R, CATEGORY);
    private static final KeyMapping MANUAL_CLOSE_EYES = new KeyMapping("key.reactions.manual_close_eyes", InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), CATEGORY);
    private static final KeyMapping MANUAL_SQUINT_EYES = new KeyMapping("key.reactions.manual_squint_eyes", InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), CATEGORY);
    private static final KeyMapping MANUAL_LOOK_EYES = new KeyMapping("key.reactions.manual_look_eyes", InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), CATEGORY);
    private static final KeyMapping MANUAL_RAISE_EYEBROW = new KeyMapping("key.reactions.manual_raise_eyebrow", InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), CATEGORY);
    private static final HeldKeyState CLOSE_EYES_STATE = new HeldKeyState();
    private static final HeldKeyState SQUINT_EYES_STATE = new HeldKeyState();
    private static final HeldKeyState LOOK_EYES_STATE = new HeldKeyState();
    private static final HeldKeyState RAISE_EYEBROW_STATE = new HeldKeyState();
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

    public static KeyMapping manualRaiseEyebrowKey() {
        return MANUAL_RAISE_EYEBROW;
    }

    public static boolean manualEyeKeysUnbound() {
        return MANUAL_CLOSE_EYES.isUnbound() || MANUAL_SQUINT_EYES.isUnbound() || MANUAL_LOOK_EYES.isUnbound()
            || ReactionsClientConfig.get().animateSkepticalExpression && MANUAL_RAISE_EYEBROW.isUnbound();
    }

    public static ManualEyeControl manualEyeControl() {
        ManualEyeControl.Pose pose = ManualEyeControl.Pose.OPEN;
        if (CLOSE_EYES_STATE.active()) {
            pose = CLOSE_EYES_STATE.tripleTapHold ? ManualEyeControl.Pose.CLOSE_LEFT
                : CLOSE_EYES_STATE.doubleTapHold ? ManualEyeControl.Pose.CLOSE_RIGHT : ManualEyeControl.Pose.CLOSE_BOTH;
        } else if (SQUINT_EYES_STATE.active()) {
            pose = SQUINT_EYES_STATE.doubleTapHold ? ManualEyeControl.Pose.WIDEN : ManualEyeControl.Pose.SQUINT;
        }
        int look = LOOK_EYES_STATE.active() ? LOOK_EYES_STATE.doubleTapHold ? 1 : -1 : 0;
        int eyebrow = ReactionsClientConfig.get().animateSkepticalExpression && RAISE_EYEBROW_STATE.active()
            ? RAISE_EYEBROW_STATE.doubleTapHold ? 1 : -1 : 0;
        return new ManualEyeControl(pose, look, eyebrow);
    }

    public static void onClientTick(Minecraft client) {
        BlockInteractionEyeFocus.onClientTick(client);
        CLOSE_EYES_STATE.update(MANUAL_CLOSE_EYES);
        SQUINT_EYES_STATE.update(MANUAL_SQUINT_EYES);
        LOOK_EYES_STATE.update(MANUAL_LOOK_EYES, LOOK_RELEASE_GRACE_TICKS);
        RAISE_EYEBROW_STATE.update(MANUAL_RAISE_EYEBROW);
        ReactionsNetworking.sendLocalManualEyeControl(manualEyeControl().networkValue());
        while (OPEN_CONFIG.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen == null) {
                minecraft.setScreen(new ReactionsConfigScreen(null));
            }
        }
    }

    public record ManualEyeControl(Pose pose, int look, int eyebrow) {
        // Preserve codes 0-13; additional codes pack independent pose, gaze and brow states.
        private static final ManualEyeControl[] LEGACY_CONTROLS = {
            new ManualEyeControl(Pose.OPEN, 0, 0),
            new ManualEyeControl(Pose.CLOSE_BOTH, 0, 0),
            new ManualEyeControl(Pose.CLOSE_RIGHT, 0, 0),
            new ManualEyeControl(Pose.SQUINT, 0, 0),
            new ManualEyeControl(Pose.WIDEN, 0, 0),
            new ManualEyeControl(Pose.OPEN, -1, 0),
            new ManualEyeControl(Pose.OPEN, 1, 0),
            new ManualEyeControl(Pose.SQUINT, -1, 0),
            new ManualEyeControl(Pose.SQUINT, 1, 0),
            new ManualEyeControl(Pose.WIDEN, -1, 0),
            new ManualEyeControl(Pose.WIDEN, 1, 0),
            new ManualEyeControl(Pose.CLOSE_LEFT, 0, 0),
            new ManualEyeControl(Pose.OPEN, 0, -1),
            new ManualEyeControl(Pose.OPEN, 0, 1)
        };
        public static final ManualEyeControl NONE = LEGACY_CONTROLS[0];

        public enum Pose {
            OPEN,
            CLOSE_BOTH,
            CLOSE_RIGHT,
            SQUINT,
            WIDEN,
            CLOSE_LEFT
        }

        public static ManualEyeControl fromNetwork(int value) {
            if (value >= 0 && value < LEGACY_CONTROLS.length) {
                return LEGACY_CONTROLS[value];
            }
            int packed = value - LEGACY_CONTROLS.length;
            Pose[] poses = Pose.values();
            if (packed < 0 || packed >= poses.length * 9) {
                return NONE;
            }
            return new ManualEyeControl(poses[packed % poses.length], packed / poses.length % 3 - 1, packed / (poses.length * 3) - 1);
        }

        public int networkValue() {
            for (int i = 0; i < LEGACY_CONTROLS.length; i++) {
                if (equals(LEGACY_CONTROLS[i])) {
                    return i;
                }
            }
            return LEGACY_CONTROLS.length + pose.ordinal() + Pose.values().length * (look + 1) + Pose.values().length * 3 * (eyebrow + 1);
        }

        public boolean active() {
            return pose != Pose.OPEN || look != 0 || eyebrow != 0;
        }

        public boolean closesEyes() {
            return pose == Pose.CLOSE_BOTH || pose == Pose.CLOSE_RIGHT || pose == Pose.CLOSE_LEFT;
        }

        public boolean squintsEyes() {
            return pose == Pose.SQUINT;
        }

        public boolean surprisesEyes() {
            return pose == Pose.WIDEN;
        }

        public boolean looksLeft() {
            return look == -1;
        }

        public boolean looksRight() {
            return look == 1;
        }

        public boolean raisesLeftEyebrow() {
            return eyebrow == -1;
        }

        public boolean raisesRightEyebrow() {
            return eyebrow == 1;
        }

        public boolean affectsLeftEye() {
            return pose == Pose.CLOSE_BOTH || pose == Pose.CLOSE_LEFT || pose == Pose.SQUINT || pose == Pose.WIDEN;
        }

        public boolean affectsRightEye() {
            return pose == Pose.CLOSE_BOTH || pose == Pose.CLOSE_RIGHT || pose == Pose.SQUINT || pose == Pose.WIDEN;
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
