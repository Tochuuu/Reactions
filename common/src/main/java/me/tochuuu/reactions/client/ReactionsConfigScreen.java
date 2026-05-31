package me.tochuuu.reactions.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

public final class ReactionsConfigScreen extends Screen {
    private static final int FACE_U = 8;
    private static final int FACE_V = 8;
    private static final int FACE_PIXELS = 8;
    private static final int SKIN_SIZE = 64;
    private static final int BASE_FACE_SIZE = 192;
    private static final int MIN_FACE_SIZE = 88;
    private static final int COMPACT_FACE_SIZE = 72;
    private static final int PANEL_WIDTH = 220;
    private static final int GAP = 8;
    private static final int BUTTON_HEIGHT = 20;
    private static final int MAX_EYE_WIDTH = 2;
    private static final int MAX_EYE_HEIGHT = 3;
    private static final int SIZE_LIMIT_MESSAGE_TICKS = 60;
    private static final int MOUTH_PIXELS = 2;

    private final Screen parent;
    private EditMode mode = EditMode.LEFT_EYE;
    private int faceX;
    private int faceY;
    private int faceSize;
    private int pixelSize;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int partHeaderY;
    private int sizeHeaderY;
    private int eyeWidthRowY;
    private int eyeHeightRowY;
    private int detailsY;
    private int sizeLimitMessageTicks;
    private boolean compactLayout;

    public ReactionsConfigScreen(Screen parent) {
        super(Component.literal("Reactions"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        compactLayout = this.width < 480 || this.height < 300;
        if (compactLayout) {
            initCompact();
            return;
        }

        boolean stacked = this.width < BASE_FACE_SIZE + PANEL_WIDTH + 56;
        int availableFaceWidth = stacked ? this.width - 24 : this.width - PANEL_WIDTH - 56;
        int availableFaceHeight = stacked ? this.height - 250 : this.height - 86;
        faceSize = clamp(Math.min(Math.min(BASE_FACE_SIZE, availableFaceWidth), availableFaceHeight), MIN_FACE_SIZE, BASE_FACE_SIZE);
        pixelSize = Math.max(1, faceSize / FACE_PIXELS);
        faceSize = pixelSize * FACE_PIXELS;

        if (stacked) {
            faceX = Math.max(12, this.width / 2 - faceSize / 2);
            faceY = 32;
            panelWidth = Math.min(PANEL_WIDTH, this.width - 24);
            panelX = Math.max(12, this.width / 2 - panelWidth / 2);
            panelY = faceY + faceSize + 42;
        } else {
            int contentWidth = faceSize + 28 + PANEL_WIDTH;
            faceX = Math.max(12, this.width / 2 - contentWidth / 2);
            faceY = Math.max(36, Math.min(this.height - faceSize - 52, this.height / 2 - faceSize / 2));
            panelX = faceX + faceSize + 28;
            panelY = faceY + 4;
            panelWidth = PANEL_WIDTH;
        }

        int y = panelY;
        int half = (panelWidth - GAP) / 2;
        addToggle(panelX, y, half, enabledText(), () -> ReactionsClientConfig.get().enabled = !ReactionsClientConfig.get().enabled);
        addToggle(panelX + half + GAP, y, half, mouthText(), () -> ReactionsClientConfig.get().showMouth = !ReactionsClientConfig.get().showMouth);

        y += BUTTON_HEIGHT + GAP;
        addToggle(panelX, y, half, selfAnimationText(), () -> ReactionsClientConfig.get().animateSelf = !ReactionsClientConfig.get().animateSelf);
        addToggle(panelX + half + GAP, y, half, otherAnimationText(), () -> ReactionsClientConfig.get().animateOthers = !ReactionsClientConfig.get().animateOthers);

        y += BUTTON_HEIGHT + 16;
        partHeaderY = y - 10;
        addModeButton(EditMode.LEFT_EYE, panelX, y, half);
        addModeButton(EditMode.RIGHT_EYE, panelX + half + GAP, y, half);

        y += BUTTON_HEIGHT + GAP;
        addModeButton(EditMode.MOUTH, panelX, y, half);
        addModeButton(EditMode.EYEDROPPER, panelX + half + GAP, y, half);

        y += BUTTON_HEIGHT + 18;
        sizeHeaderY = y - 10;
        eyeWidthRowY = y;
        addSizeButton(panelX + panelWidth - 48, y, true, -1);
        addSizeButton(panelX + panelWidth - 22, y, true, 1);

        y += BUTTON_HEIGHT + GAP;
        eyeHeightRowY = y;
        addSizeButton(panelX + panelWidth - 48, y, false, -1);
        addSizeButton(panelX + panelWidth - 22, y, false, 1);

        detailsY = y + BUTTON_HEIGHT + 10;
        int actionY = Math.min(this.height - 50, detailsY + 48);
        addRenderableWidget(Button.builder(Component.literal("Beta animations..."), button -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new ReactionsBetaAnimationsScreen(this));
            }
        }).bounds(panelX, actionY, half, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
            ReactionsClientConfig.reset();
            mode = EditMode.LEFT_EYE;
            rebuildWidgets();
        }).bounds(panelX + half + GAP, actionY, half, BUTTON_HEIGHT).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> {
            ReactionsClientConfig.save();
            onClose();
        }).bounds(this.width / 2 - 48, this.height - 26, 96, BUTTON_HEIGHT).build());
    }

    private void initCompact() {
        faceSize = Math.min(COMPACT_FACE_SIZE, Math.max(48, Math.min(this.width - 24, this.height - 188)));
        pixelSize = Math.max(1, faceSize / FACE_PIXELS);
        faceSize = pixelSize * FACE_PIXELS;
        faceX = Math.max(8, this.width / 2 - faceSize / 2);
        faceY = 24;

        panelWidth = Math.min(320, this.width - 16);
        panelX = Math.max(8, this.width / 2 - panelWidth / 2);
        panelY = faceY + faceSize + 24;

        int buttonHeight = 18;
        int half = (panelWidth - GAP) / 2;
        int y = panelY;

        addToggle(panelX, y, half, enabledText(), () -> ReactionsClientConfig.get().enabled = !ReactionsClientConfig.get().enabled, buttonHeight);
        addToggle(panelX + half + GAP, y, half, mouthText(), () -> ReactionsClientConfig.get().showMouth = !ReactionsClientConfig.get().showMouth, buttonHeight);

        y += buttonHeight + 4;
        addModeButton(EditMode.LEFT_EYE, panelX, y, half, buttonHeight);
        addModeButton(EditMode.RIGHT_EYE, panelX + half + GAP, y, half, buttonHeight);

        y += buttonHeight + 4;
        addModeButton(EditMode.MOUTH, panelX, y, half, buttonHeight);
        addModeButton(EditMode.EYEDROPPER, panelX + half + GAP, y, half, buttonHeight);

        y += buttonHeight + 4;
        addToggle(panelX, y, half, selfAnimationText(), () -> ReactionsClientConfig.get().animateSelf = !ReactionsClientConfig.get().animateSelf, buttonHeight);
        addToggle(panelX + half + GAP, y, half, otherAnimationText(), () -> ReactionsClientConfig.get().animateOthers = !ReactionsClientConfig.get().animateOthers, buttonHeight);

        y += buttonHeight + 8;
        sizeHeaderY = y - 8;
        eyeWidthRowY = y;
        addSizeButton(panelX + panelWidth - 48, y, true, -1, 18);
        addSizeButton(panelX + panelWidth - 22, y, true, 1, 18);

        y += buttonHeight + 4;
        eyeHeightRowY = y;
        addSizeButton(panelX + panelWidth - 48, y, false, -1, 18);
        addSizeButton(panelX + panelWidth - 22, y, false, 1, 18);
        detailsY = 0;

        int bottomY = Math.min(this.height - 22, y + buttonHeight + 8);
        int third = Math.max(56, (panelWidth - GAP * 2) / 3);
        addRenderableWidget(Button.builder(Component.literal("Beta"), button -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new ReactionsBetaAnimationsScreen(this));
            }
        }).bounds(panelX, bottomY, third, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
            ReactionsClientConfig.reset();
            mode = EditMode.LEFT_EYE;
            rebuildWidgets();
        }).bounds(panelX + third + GAP, bottomY, third, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> {
            ReactionsClientConfig.save();
            onClose();
        }).bounds(panelX + (third + GAP) * 2, bottomY, third, buttonHeight).build());
    }

    private void addToggle(int x, int y, int width, Component text, Runnable toggle) {
        addToggle(x, y, width, text, toggle, BUTTON_HEIGHT);
    }

    private void addToggle(int x, int y, int width, Component text, Runnable toggle, int height) {
        addRenderableWidget(Button.builder(text, button -> {
            toggle.run();
            ReactionsClientConfig.save();
            rebuildWidgets();
        }).bounds(x, y, width, height).build());
    }

    private void addModeButton(EditMode targetMode, int x, int y, int width) {
        addModeButton(targetMode, x, y, width, BUTTON_HEIGHT);
    }

    private void addModeButton(EditMode targetMode, int x, int y, int width, int height) {
        addRenderableWidget(Button.builder(modeText(targetMode), button -> {
            mode = targetMode;
            rebuildWidgets();
        }).bounds(x, y, width, height).build());
    }

    private void addSizeButton(int x, int y, boolean width, int delta) {
        addSizeButton(x, y, width, delta, BUTTON_HEIGHT);
    }

    private void addSizeButton(int x, int y, boolean width, int delta, int size) {
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

        graphics.drawString(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 12, 0xFFFFFFFF);
        graphics.drawString(this.font, Component.literal("Skin UV picker"), faceX, faceY - 12, 0xFFA0A0A0);

        ResourceLocation texture = skinTexture();
        graphics.blit(RenderType::guiTextured, texture, faceX, faceY, FACE_U, FACE_V, faceSize, faceSize, FACE_PIXELS, FACE_PIXELS, SKIN_SIZE, SKIN_SIZE);
        drawGrid(graphics);
        drawEyeSelection(graphics, config.leftEyeX, config.leftEyeY, config.eyeWidth, config.eyeHeight, 0xFF43D17C);
        drawEyeSelection(graphics, config.rightEyeX, config.rightEyeY, config.eyeWidth, config.eyeHeight, 0xFF4AA3FF);
        drawMouthSelection(graphics, config.leftMouthX, config.leftMouthY, config.rightMouthX, config.rightMouthY);
        drawPixelMarker(graphics, config.eyelidColorX, config.eyelidColorY, 0xFFFFC94A);

        int labelY = faceY + faceSize + 8;
        graphics.drawString(this.font, Component.literal("Editing: " + mode.label), faceX, labelY, mode.color);
        if (!compactLayout) {
            graphics.drawString(this.font, Component.literal("Eyes " + config.eyeWidth + "x" + config.eyeHeight), faceX, labelY + 11, 0xFFA0A0A0);
        }

        if (!compactLayout) {
            graphics.drawString(this.font, Component.literal("Animation"), panelX, panelY - 11, 0xFFA0A0A0);
        }
        graphics.drawString(this.font, Component.literal("UV parts"), panelX, partHeaderY, 0xFFA0A0A0);
        graphics.drawString(this.font, Component.literal("Eye size"), panelX, sizeHeaderY, 0xFFA0A0A0);
        graphics.drawString(this.font, Component.literal("Width: " + config.eyeWidth), panelX, eyeWidthRowY + 6, 0xFFFFFFFF);
        graphics.drawString(this.font, Component.literal("Height: " + config.eyeHeight), panelX, eyeHeightRowY + 6, 0xFFFFFFFF);

        if (!compactLayout && detailsY > 0 && detailsY < this.height - 46) {
            graphics.drawString(this.font, Component.literal("Left eye: " + uv(config.leftEyeX, config.leftEyeY)), panelX, detailsY, 0xFF43D17C);
            graphics.drawString(this.font, Component.literal("Right eye: " + uv(config.rightEyeX, config.rightEyeY)), panelX, detailsY + 11, 0xFF4AA3FF);
            graphics.drawString(this.font, Component.literal("Mouth: " + uv(config.leftMouthX, config.leftMouthY) + " " + uv(config.rightMouthX, config.rightMouthY)), panelX, detailsY + 22, 0xFFFFD45A);
            graphics.drawString(this.font, Component.literal("Eyelid: " + uv(config.eyelidColorX, config.eyelidColorY)), panelX, detailsY + 33, 0xFFFFC94A);
        }

        if (sizeLimitMessageTicks > 0) {
            int messageY = compactLayout ? Math.max(20, panelY - 12) : Math.min(this.height - 38, detailsY + 46);
            graphics.drawString(this.font, Component.literal("Eye size limit reached"), panelX, messageY, 0xFFFF6060);
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
        return Component.literal("Mod " + onOff(ReactionsClientConfig.get().enabled));
    }

    private Component selfAnimationText() {
        return Component.literal("Self " + onOff(ReactionsClientConfig.get().animateSelf));
    }

    private Component otherAnimationText() {
        return Component.literal("Others " + onOff(ReactionsClientConfig.get().animateOthers));
    }

    private Component mouthText() {
        return Component.literal("Mouth " + onOff(ReactionsClientConfig.get().showMouth));
    }

    private Component modeText(EditMode targetMode) {
        return Component.literal((mode == targetMode ? "> " : "") + (compactLayout ? targetMode.shortLabel : targetMode.label));
    }

    private static String uv(int x, int y) {
        return x + "," + y;
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
        LEFT_EYE("Left eye", "L Eye", 0xFF43D17C),
        RIGHT_EYE("Right eye", "R Eye", 0xFF4AA3FF),
        MOUTH("Mouth", "Mouth", 0xFFFFD45A),
        EYEDROPPER("Eyelid color", "Lid", 0xFFFFC94A);

        private final String label;
        private final String shortLabel;
        private final int color;

        EditMode(String label, String shortLabel, int color) {
            this.label = label;
            this.shortLabel = shortLabel;
            this.color = color;
        }
    }

    private static final class MinecraftFallbacks {
        private static final ResourceLocation DEFAULT_SKIN = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
    }
}
