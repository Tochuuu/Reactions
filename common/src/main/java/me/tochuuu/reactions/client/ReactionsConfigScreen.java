package me.tochuuu.reactions.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

public final class ReactionsConfigScreen extends Screen {
    private static final int FACE_U = 8;
    private static final int FACE_V = 8;
    private static final int FACE_PIXELS = 8;
    private static final int SKIN_SIZE = 64;
    private static final int BASE_FACE_SIZE = 208;
    private static final int MIN_FACE_SIZE = 88;
    private static final int COMPACT_FACE_SIZE = 80;
    private static final int SIDE_PANEL_WIDTH = 260;
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
    private int panelHeight;
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
        compactLayout = this.width < 560;
        if (compactLayout) {
            initCompact();
            return;
        }

        int availableFaceWidth = this.width - SIDE_PANEL_WIDTH - 48;
        int availableFaceHeight = this.height - 86;
        faceSize = clamp(Math.min(Math.min(BASE_FACE_SIZE, availableFaceWidth), availableFaceHeight), MIN_FACE_SIZE, BASE_FACE_SIZE);
        pixelSize = Math.max(1, faceSize / FACE_PIXELS);
        faceSize = pixelSize * FACE_PIXELS;

        int contentWidth = faceSize + 18 + SIDE_PANEL_WIDTH;
        faceX = Math.max(12, this.width / 2 - contentWidth / 2);
        faceY = Math.max(34, this.height / 2 - faceSize / 2);
        panelX = faceX + faceSize + 18;
        panelY = faceY;
        panelWidth = Math.min(SIDE_PANEL_WIDTH, this.width - panelX - 12);
        panelHeight = Math.min(faceSize, this.height - panelY - 34);

        int y = panelY + 20;
        int half = (panelWidth - GAP) / 2;
        addToggle(panelX, y, half, enabledText(), () -> ReactionsClientConfig.get().enabled = !ReactionsClientConfig.get().enabled);
        addToggle(panelX + half + GAP, y, half, mouthText(), () -> ReactionsClientConfig.get().showMouth = !ReactionsClientConfig.get().showMouth);

        y += BUTTON_HEIGHT + GAP;
        addToggle(panelX, y, half, selfAnimationText(), () -> ReactionsClientConfig.get().animateSelf = !ReactionsClientConfig.get().animateSelf);
        addToggle(panelX + half + GAP, y, half, otherAnimationText(), () -> ReactionsClientConfig.get().animateOthers = !ReactionsClientConfig.get().animateOthers);

        y += BUTTON_HEIGHT + 14;
        partHeaderY = y - 10;
        addModeButton(EditMode.LEFT_EYE, panelX, y, half);
        addModeButton(EditMode.RIGHT_EYE, panelX + half + GAP, y, half);

        y += BUTTON_HEIGHT + GAP;
        addModeButton(EditMode.MOUTH, panelX, y, half);
        addModeButton(EditMode.EYEDROPPER, panelX + half + GAP, y, half);

        y += BUTTON_HEIGHT + 16;
        sizeHeaderY = y - 10;
        eyeWidthRowY = y;
        addSizeButton(panelX + panelWidth - 48, y, true, -1);
        addSizeButton(panelX + panelWidth - 22, y, true, 1);

        y += BUTTON_HEIGHT + GAP;
        eyeHeightRowY = y;
        addSizeButton(panelX + panelWidth - 48, y, false, -1);
        addSizeButton(panelX + panelWidth - 22, y, false, 1);

        y += BUTTON_HEIGHT + 16;
        detailsY = y;
        addRenderableWidget(Button.builder(Component.literal("Beta"), button -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new ReactionsBetaAnimationsScreen(this));
            }
        }).bounds(panelX, panelY + panelHeight - BUTTON_HEIGHT, half, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
            ReactionsClientConfig.reset();
            mode = EditMode.LEFT_EYE;
            rebuildWidgets();
        }).bounds(panelX + half + GAP, panelY + panelHeight - BUTTON_HEIGHT, half, BUTTON_HEIGHT).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> {
            ReactionsClientConfig.save();
            onClose();
        }).bounds(this.width / 2 - 48, this.height - 26, 96, BUTTON_HEIGHT).build());
    }

    private void initCompact() {
        faceSize = Math.min(COMPACT_FACE_SIZE, Math.max(56, Math.min(this.width - 24, this.height - 178)));
        pixelSize = Math.max(1, faceSize / FACE_PIXELS);
        faceSize = pixelSize * FACE_PIXELS;
        faceX = Math.max(8, this.width / 2 - faceSize / 2);
        faceY = 22;

        panelX = Math.max(8, this.width / 2 - Math.min(328, this.width - 16) / 2);
        panelY = faceY + faceSize + 10;
        panelWidth = Math.min(328, this.width - 16);
        panelHeight = this.height - panelY - 28;

        int buttonHeight = 18;
        int half = (panelWidth - GAP) / 2;
        int y = panelY + 18;

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
        detailsY = y + buttonHeight + 6;

        int bottomY = Math.min(this.height - 22, detailsY + 30);
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

        drawPanel(graphics, faceX - 6, faceY - 20, faceSize + 12, faceSize + 44);
        drawPanel(graphics, panelX - 6, panelY - 6, panelWidth + 12, Math.max(panelHeight + 12, 160));
        graphics.drawString(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 8, 0xFFFFFFFF);
        graphics.drawString(this.font, Component.literal("Skin UV"), faceX, faceY - 12, 0xFFE6E6E6);

        Identifier texture = skinTexture();
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, faceX, faceY, FACE_U, FACE_V, faceSize, faceSize, FACE_PIXELS, FACE_PIXELS, SKIN_SIZE, SKIN_SIZE);
        drawGrid(graphics);
        drawEyeSelection(graphics, config.leftEyeX, config.leftEyeY, config.eyeWidth, config.eyeHeight, 0xFF43D17C);
        drawEyeSelection(graphics, config.rightEyeX, config.rightEyeY, config.eyeWidth, config.eyeHeight, 0xFF4AA3FF);
        drawMouthSelection(graphics, config.leftMouthX, config.leftMouthY, config.rightMouthX, config.rightMouthY);
        drawPixelMarker(graphics, config.eyelidColorX, config.eyelidColorY, 0xFFFFC94A);

        int labelY = faceY + faceSize + 8;
        graphics.drawString(this.font, Component.literal("Active: " + mode.label), faceX, labelY, mode.color);
        graphics.drawString(this.font, Component.literal("Eyes " + config.eyeWidth + "x" + config.eyeHeight), faceX, labelY + 11, 0xFFBFC7D5);

        graphics.drawString(this.font, Component.literal("Animation"), panelX, panelY + 8, 0xFFE6E6E6);
        graphics.drawString(this.font, Component.literal("UV parts"), panelX, partHeaderY, 0xFFE6E6E6);
        graphics.drawString(this.font, Component.literal("Eye size"), panelX, sizeHeaderY, 0xFFE6E6E6);
        graphics.drawString(this.font, Component.literal("Width: " + config.eyeWidth), panelX, eyeWidthRowY + 6, 0xFFBFC7D5);
        graphics.drawString(this.font, Component.literal("Height: " + config.eyeHeight), panelX, eyeHeightRowY + 6, 0xFFBFC7D5);

        if (detailsY > 0 && detailsY < this.height - 36) {
            graphics.drawString(this.font, Component.literal("Left eye  " + uv(config.leftEyeX, config.leftEyeY)), panelX, detailsY, 0xFF43D17C);
            graphics.drawString(this.font, Component.literal("Right eye " + uv(config.rightEyeX, config.rightEyeY)), panelX, detailsY + 11, 0xFF4AA3FF);
            graphics.drawString(this.font, Component.literal("Mouth     " + uv(config.leftMouthX, config.leftMouthY) + " " + uv(config.rightMouthX, config.rightMouthY)), panelX, detailsY + 22, 0xFFFFD45A);
            graphics.drawString(this.font, Component.literal("Lid color " + uv(config.eyelidColorX, config.eyelidColorY)), panelX, detailsY + 33, 0xFFFFC94A);
        }

        if (sizeLimitMessageTicks > 0) {
            graphics.drawString(this.font, Component.literal("Eye size limit reached"), panelX, Math.min(this.height - 38, detailsY + 46), 0xFFFF6060);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && isInsideFace(event.x(), event.y())) {
            int skinX = FACE_U + (int) ((event.x() - faceX) / pixelSize);
            int skinY = FACE_V + (int) ((event.y() - faceY) / pixelSize);
            applyFaceClick(skinX, skinY);
            ReactionsClientConfig.save();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
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

    private void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xD018181C);
        graphics.fill(x, y, x + width, y + 1, 0xFF5C5C66);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF050507);
        graphics.fill(x, y, x + 1, y + height, 0xFF5C5C66);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF050507);
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

    private Identifier skinTexture() {
        if (this.minecraft != null && this.minecraft.player != null) {
            return this.minecraft.player.getSkin().body().texturePath();
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
        return Component.literal((mode == targetMode ? "> " : "") + targetMode.shortLabel);
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
        private static final Identifier DEFAULT_SKIN = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
    }
}
