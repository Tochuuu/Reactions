package me.tochuuu.reactions.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

public final class ReactionsConfigScreen extends Screen {
    private static final int FACE_U = 8;
    private static final int FACE_V = 8;
    private static final int FACE_PIXELS = 8;
    private static final int SKIN_SIZE = 64;
    private static final int BASE_FACE_SIZE = 192;
    private static final int MIN_FACE_SIZE = 96;
    private static final int COMPACT_FACE_SIZE = 64;
    private static final int PANEL_WIDTH = 168;
    private static final int MAX_EYE_WIDTH = 2;
    private static final int MAX_EYE_HEIGHT = 3;
    private static final int SIZE_LIMIT_MESSAGE_TICKS = 60;
    private static final int MOUTH_PIXELS = 2;

    private final Screen parent;
    private EditMode mode = EditMode.LEFT_EYE;
    private int faceX;
    private int faceY;
    private int eyeSizeHeaderY;
    private int faceSize;
    private int pixelSize;
    private int sizeLimitMessageTicks;
    private boolean compactLayout;

    public ReactionsConfigScreen(Screen parent) {
        super(Component.literal("Reactions"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        compactLayout = this.width < 360 || this.height < 360;
        if (compactLayout) {
            initCompact();
            return;
        }

        boolean stacked = this.width < BASE_FACE_SIZE + 24 + PANEL_WIDTH + 24;
        int availableFaceWidth = stacked ? this.width - 24 : this.width - PANEL_WIDTH - 48;
        int availableFaceHeight = stacked ? this.height - 274 : this.height - 112;
        faceSize = clamp(Math.min(Math.min(BASE_FACE_SIZE, availableFaceWidth), availableFaceHeight), MIN_FACE_SIZE, BASE_FACE_SIZE);
        pixelSize = Math.max(1, faceSize / FACE_PIXELS);
        faceSize = pixelSize * FACE_PIXELS;

        int panelX;
        int y;
        if (stacked) {
            faceX = Math.max(12, this.width / 2 - faceSize / 2);
            faceY = 30;
            panelX = Math.max(12, this.width / 2 - PANEL_WIDTH / 2);
            y = faceY + faceSize + 56;
        } else {
            int contentWidth = faceSize + 24 + PANEL_WIDTH;
            faceX = Math.max(12, this.width / 2 - contentWidth / 2);
            faceY = Math.max(34, this.height / 2 - faceSize / 2);
            panelX = faceX + faceSize + 24;
            y = faceY;
        }

        addRenderableWidget(Button.builder(enabledText(), button -> {
            ReactionsClientConfig.get().enabled = !ReactionsClientConfig.get().enabled;
            ReactionsClientConfig.save();
            rebuildWidgets();
        }).bounds(panelX, y, PANEL_WIDTH, 20).build());

        y += 24;
        addModeButton(EditMode.LEFT_EYE, panelX, y);
        y += 24;
        addModeButton(EditMode.RIGHT_EYE, panelX, y);
        y += 24;
        addModeButton(EditMode.EYEDROPPER, panelX, y);
        y += 24;
        addModeButton(EditMode.MOUTH, panelX, y);

        y += 30;
        addRenderableWidget(Button.builder(selfAnimationText(), button -> {
            ReactionsClientConfig.get().animateSelf = !ReactionsClientConfig.get().animateSelf;
            ReactionsClientConfig.save();
            rebuildWidgets();
        }).bounds(panelX, y, PANEL_WIDTH, 20).build());

        y += 24;
        addRenderableWidget(Button.builder(otherAnimationText(), button -> {
            ReactionsClientConfig.get().animateOthers = !ReactionsClientConfig.get().animateOthers;
            ReactionsClientConfig.save();
            rebuildWidgets();
        }).bounds(panelX, y, PANEL_WIDTH, 20).build());

        y += 24;
        addRenderableWidget(Button.builder(mouthText(), button -> {
            ReactionsClientConfig config = ReactionsClientConfig.get();
            config.showMouth = !config.showMouth;
            ReactionsClientConfig.save();
            rebuildWidgets();
        }).bounds(panelX, y, PANEL_WIDTH, 20).build());

        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Beta animations..."), button -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new ReactionsBetaAnimationsScreen(this));
            }
        }).bounds(panelX, y, PANEL_WIDTH, 20).build());

        y += 32;
        eyeSizeHeaderY = y - 12;
        addSizeButton(panelX + 104, y, true, -1);
        addSizeButton(panelX + 132, y, true, 1);
        y += 24;
        addSizeButton(panelX + 104, y, false, -1);
        addSizeButton(panelX + 132, y, false, 1);

        int buttonY = Math.min(Math.max(y + 32, this.height - 30), this.height - 24);
        addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
            ReactionsClientConfig.reset();
            mode = EditMode.LEFT_EYE;
            rebuildWidgets();
        }).bounds(this.width / 2 - 105, buttonY, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> {
            ReactionsClientConfig.save();
            onClose();
        }).bounds(this.width / 2 + 15, buttonY, 90, 20).build());
    }

    private void initCompact() {
        faceSize = Math.min(COMPACT_FACE_SIZE, Math.max(48, Math.min(this.width - 24, this.height - 176)));
        pixelSize = Math.max(1, faceSize / FACE_PIXELS);
        faceSize = pixelSize * FACE_PIXELS;
        faceX = Math.max(8, this.width / 2 - faceSize / 2);
        faceY = 24;

        int gap = 8;
        int buttonHeight = 18;
        int rowStep = 19;
        int buttonWidth = Math.max(80, Math.min(148, (this.width - 24 - gap) / 2));
        int contentWidth = buttonWidth * 2 + gap;
        int leftX = Math.max(8, this.width / 2 - contentWidth / 2);
        int rightX = leftX + buttonWidth + gap;
        int y = faceY + faceSize + 8;

        addRenderableWidget(Button.builder(enabledText(), button -> {
            ReactionsClientConfig.get().enabled = !ReactionsClientConfig.get().enabled;
            ReactionsClientConfig.save();
            rebuildWidgets();
        }).bounds(leftX, y, buttonWidth, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("Beta animations..."), button -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new ReactionsBetaAnimationsScreen(this));
            }
        }).bounds(rightX, y, buttonWidth, buttonHeight).build());

        y += rowStep;
        addModeButton(EditMode.LEFT_EYE, leftX, y, buttonWidth, buttonHeight);
        addModeButton(EditMode.RIGHT_EYE, rightX, y, buttonWidth, buttonHeight);

        y += rowStep;
        addModeButton(EditMode.EYEDROPPER, leftX, y, buttonWidth, buttonHeight);
        addModeButton(EditMode.MOUTH, rightX, y, buttonWidth, buttonHeight);

        y += rowStep;
          addRenderableWidget(Button.builder(selfAnimationText(), button -> {
              ReactionsClientConfig.get().animateSelf = !ReactionsClientConfig.get().animateSelf;
              ReactionsClientConfig.save();
              rebuildWidgets();
          }).bounds(leftX, y, buttonWidth, buttonHeight).build());

        y += rowStep;
        addRenderableWidget(Button.builder(otherAnimationText(), button -> {
            ReactionsClientConfig.get().animateOthers = !ReactionsClientConfig.get().animateOthers;
            ReactionsClientConfig.save();
            rebuildWidgets();
        }).bounds(leftX, y, buttonWidth, buttonHeight).build());
        addRenderableWidget(Button.builder(mouthText(), button -> {
            ReactionsClientConfig config = ReactionsClientConfig.get();
            config.showMouth = !config.showMouth;
            ReactionsClientConfig.save();
            rebuildWidgets();
        }).bounds(rightX, y, buttonWidth, buttonHeight).build());

        y += rowStep;
        addSizeButton(leftX, y, true, -1, buttonWidth, buttonHeight);
        addSizeButton(rightX, y, true, 1, buttonWidth, buttonHeight);

        y += rowStep;
        addSizeButton(leftX, y, false, -1, buttonWidth, buttonHeight);
        addSizeButton(rightX, y, false, 1, buttonWidth, buttonHeight);

        y += rowStep;
          addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
              ReactionsClientConfig.reset();
              mode = EditMode.LEFT_EYE;
              rebuildWidgets();
          }).bounds(this.width / 2 - buttonWidth / 2, y, buttonWidth, buttonHeight).build());

        y += rowStep;
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> {
            ReactionsClientConfig.save();
            onClose();
        }).bounds(this.width / 2 - buttonWidth / 2, Math.min(y, this.height - buttonHeight - 4), buttonWidth, buttonHeight).build());
    }

    private void addModeButton(EditMode targetMode, int x, int y) {
        addModeButton(targetMode, x, y, PANEL_WIDTH);
    }

    private void addModeButton(EditMode targetMode, int x, int y, int width) {
        addModeButton(targetMode, x, y, width, 20);
    }

    private void addModeButton(EditMode targetMode, int x, int y, int width, int height) {
        addRenderableWidget(Button.builder(modeText(targetMode), button -> {
            mode = targetMode;
            rebuildWidgets();
        }).bounds(x, y, width, height).build());
    }

    private void addSizeButton(int x, int y, boolean width, int delta) {
        addSizeButton(x, y, width, delta, 80);
    }

    private void addSizeButton(int x, int y, boolean width, int delta, int buttonWidth) {
        addSizeButton(x, y, width, delta, buttonWidth, 20);
    }

    private void addSizeButton(int x, int y, boolean width, int delta, int buttonWidth, int buttonHeight) {
        int size = buttonHeight;
        addRenderableWidget(Button.builder(Component.literal(delta < 0 ? "-" : "+"), button -> {
            ReactionsClientConfig config = ReactionsClientConfig.get();
            if (width) {
                if (delta > 0 && config.eyeWidth >= MAX_EYE_WIDTH) {
                    showSizeLimitMessage();
                    return;
                }
                config.eyeWidth += delta;
            } else {
                if (delta > 0 && config.eyeHeight >= MAX_EYE_HEIGHT) {
                    showSizeLimitMessage();
                    return;
                }
                config.eyeHeight += delta;
            }
            ReactionsClientConfig.save();
            rebuildWidgets();
        }).bounds(x, y, size, size).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ReactionsClientConfig config = ReactionsClientConfig.get();
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 16, 0xFFFFFF);

        ResourceLocation texture = skinTexture();
        graphics.blit(texture, faceX, faceY, faceSize, faceSize, FACE_U, FACE_V, FACE_PIXELS, FACE_PIXELS, SKIN_SIZE, SKIN_SIZE);
        drawGrid(graphics);
        drawEyeSelection(graphics, config.leftEyeX, config.leftEyeY, config.eyeWidth, config.eyeHeight, 0xFF43D17C);
        drawEyeSelection(graphics, config.rightEyeX, config.rightEyeY, config.eyeWidth, config.eyeHeight, 0xFF4AA3FF);
        drawMouthSelection(graphics, config.leftMouthX, config.leftMouthY, config.rightMouthX, config.rightMouthY);
        drawPixelMarker(graphics, config.eyelidColorX, config.eyelidColorY, 0xFFFFC94A);

        if (!compactLayout) {
            int labelY = faceY + faceSize + 8;
            graphics.drawString(this.font, Component.literal("Click the face to set " + mode.label), faceX, labelY, 0xFFFFFFFF);
            graphics.drawString(this.font, Component.literal("Eyes: " + config.eyeWidth + "x" + config.eyeHeight), faceX, labelY + 12, 0xFFBFC7D5);
            graphics.drawString(this.font, Component.literal("Mouth: " + config.leftMouthX + "," + config.leftMouthY + " + " + config.rightMouthX + "," + config.rightMouthY), faceX, labelY + 24, 0xFFBFC7D5);
            graphics.drawString(this.font, Component.literal("Eyelid color: " + config.eyelidColorX + ", " + config.eyelidColorY), faceX, labelY + 36, 0xFFBFC7D5);
            if (sizeLimitMessageTicks > 0) {
                graphics.drawString(this.font, Component.literal("Cannot make eyes bigger"), faceX, labelY + 48, 0xFFFF4040);
            }

            int panelX = this.width < BASE_FACE_SIZE + 24 + PANEL_WIDTH + 24 ? Math.max(12, this.width / 2 - PANEL_WIDTH / 2) : faceX + faceSize + 24;
            graphics.drawString(this.font, Component.literal("Eye size"), panelX, eyeSizeHeaderY, 0xBFC7D5);
            graphics.drawString(this.font, Component.literal("Width"), panelX, eyeSizeHeaderY + 14, 0xBFC7D5);
            graphics.drawString(this.font, Component.literal("Height"), panelX, eyeSizeHeaderY + 38, 0xBFC7D5);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInsideFace(mouseX, mouseY)) {
            int skinX = FACE_U + (int) ((mouseX - faceX) / pixelSize);
            int skinY = FACE_V + (int) ((mouseY - faceY) / pixelSize);
            applyFaceClick(skinX, skinY);
            ReactionsClientConfig.save();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void applyFaceClick(int skinX, int skinY) {
        ReactionsClientConfig config = ReactionsClientConfig.get();
        if (mode == EditMode.LEFT_EYE) {
            config.leftEyeX = skinX;
            config.leftEyeY = skinY;
        } else if (mode == EditMode.RIGHT_EYE) {
            config.rightEyeX = skinX;
            config.rightEyeY = skinY;
        } else if (mode == EditMode.MOUTH) {
            config.leftMouthX = clamp(skinX, FACE_U, FACE_U + FACE_PIXELS - MOUTH_PIXELS);
            config.leftMouthY = clamp(skinY, FACE_V, FACE_V + FACE_PIXELS - 1);
            config.rightMouthX = config.leftMouthX + 1;
            config.rightMouthY = config.leftMouthY;
        } else {
            config.eyelidColorX = skinX;
            config.eyelidColorY = skinY;
        }
    }

    private void drawGrid(GuiGraphics graphics) {
        for (int i = 0; i <= FACE_PIXELS; i++) {
            int line = faceX + i * pixelSize;
            graphics.fill(line, faceY, line + 1, faceY + faceSize, 0x66000000);
            line = faceY + i * pixelSize;
            graphics.fill(faceX, line, faceX + faceSize, line + 1, 0x66000000);
        }
        graphics.renderOutline(faceX, faceY, faceSize, faceSize, 0xFFFFFFFF);
    }

    private void drawEyeSelection(GuiGraphics graphics, int skinX, int skinY, int width, int height, int color) {
        int x = faceX + (skinX - FACE_U) * pixelSize;
        int y = faceY + (skinY - FACE_V) * pixelSize;
        int w = width * pixelSize;
        int h = height * pixelSize;
        graphics.fill(x, y, x + w, y + h, color & 0x55FFFFFF);
        graphics.renderOutline(x, y, w, h, color);
    }

    private void drawMouthSelection(GuiGraphics graphics, int leftSkinX, int leftSkinY, int rightSkinX, int rightSkinY) {
        int color = ReactionsClientConfig.get().showMouth ? 0xFFFFD45A : 0xFFBFA44A;
        drawPixelSelection(graphics, leftSkinX, leftSkinY, color);
        drawPixelSelection(graphics, rightSkinX, rightSkinY, color);
    }

    private void drawPixelSelection(GuiGraphics graphics, int skinX, int skinY, int color) {
        int x = faceX + (skinX - FACE_U) * pixelSize;
        int y = faceY + (skinY - FACE_V) * pixelSize;
        if (x < faceX || y < faceY || x >= faceX + faceSize || y >= faceY + faceSize) {
            return;
        }
        graphics.fill(x, y, x + pixelSize, y + pixelSize, color & 0x66FFFFFF);
        graphics.renderOutline(x, y, pixelSize, pixelSize, color);
    }

    private void drawPixelMarker(GuiGraphics graphics, int skinX, int skinY, int color) {
        int x = faceX + (skinX - FACE_U) * pixelSize;
        int y = faceY + (skinY - FACE_V) * pixelSize;
        if (x < faceX || y < faceY || x >= faceX + faceSize || y >= faceY + faceSize) {
            return;
        }
        int inset = Math.max(3, pixelSize / 4);
        graphics.fill(x + inset, y + inset, x + pixelSize - inset, y + pixelSize - inset, color);
        graphics.renderOutline(x + inset - 1, y + inset - 1, pixelSize - (inset - 1) * 2, pixelSize - (inset - 1) * 2, 0xFF000000);
    }

    private ResourceLocation skinTexture() {
        if (this.minecraft != null && this.minecraft.player != null) {
            return this.minecraft.player.getSkin().texture();
        }
        return MinecraftFallbacks.DEFAULT_SKIN;
    }

    private boolean isInsideFace(double mouseX, double mouseY) {
        return mouseX >= faceX && mouseX < faceX + faceSize && mouseY >= faceY && mouseY < faceY + faceSize;
    }

    private Component enabledText() {
        return Component.literal("Mod: " + (ReactionsClientConfig.get().enabled ? "Enabled" : "Disabled"));
    }

    private Component selfAnimationText() {
        return Component.literal("Self animations: " + onOff(ReactionsClientConfig.get().animateSelf));
    }

    private Component otherAnimationText() {
        return Component.literal("Other animations: " + onOff(ReactionsClientConfig.get().animateOthers));
    }

    private Component mouthText() {
        return Component.literal("Mouth: " + onOff(ReactionsClientConfig.get().showMouth));
    }

    private Component modeText(EditMode targetMode) {
        return Component.literal((mode == targetMode ? "> " : "") + targetMode.label);
    }

    private static String onOff(boolean enabled) {
        return enabled ? "On" : "Off";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void tick() {
        if (sizeLimitMessageTicks > 0) {
            sizeLimitMessageTicks--;
        }
    }

    private void showSizeLimitMessage() {
        sizeLimitMessageTicks = SIZE_LIMIT_MESSAGE_TICKS;
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5F));
        }
    }

    @Override
    public void onClose() {
        ReactionsClientConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private enum EditMode {
        LEFT_EYE("left eye"),
        RIGHT_EYE("right eye"),
        EYEDROPPER("eyelid color"),
        MOUTH("mouth");

        private final String label;

        EditMode(String label) {
            this.label = label;
        }
    }

    private static final class MinecraftFallbacks {
        private static final ResourceLocation DEFAULT_SKIN = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
    }
}
