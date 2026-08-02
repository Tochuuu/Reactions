package me.tochuuu.reactions.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.function.Supplier;

public final class ReactionsConfigScreen extends Screen {
    private static final int FACE_U = 8;
    private static final int FACE_V = 8;
    private static final int FACE_PIXELS = 8;
    private static final int SKIN_SIZE = 64;
    private static final int BASE_FACE_SIZE = 160;
    private static final int DENSE_FACE_SIZE = 128;
    private static final int MIN_FACE_SIZE = 72;
    private static final int COMPACT_FACE_SIZE = 48;
    private static final int PANEL_WIDTH = 224;
    private static final int FACE_PANEL_GAP = 18;
    private static final int GAP = 8;
    private static final int BUTTON_HEIGHT = 20;
    private static final int DENSE_LAYOUT_HEIGHT = 300;
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
    private int sizeHeaderY;
    private int eyeWidthRowY;
    private int eyeHeightRowY;
    private int layoutButtonHeight = BUTTON_HEIGHT;
    private int sizeLimitMessageTicks;
    private boolean compactLayout;
    private boolean denseLayout;
    private GameProfile menuSkinProfile;
    private Supplier<PlayerSkin> menuSkinLookup;

    public ReactionsConfigScreen(Screen parent) {
        super(Component.translatable("screen.reactions.config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layoutButtonHeight = BUTTON_HEIGHT;
        denseLayout = false;
        compactLayout = this.width < PANEL_WIDTH + MIN_FACE_SIZE + 48 || this.height < 220;
        if (compactLayout) {
            initCompact();
            return;
        }

        initSideBySide(this.height < DENSE_LAYOUT_HEIGHT);
    }

    private void initSideBySide(boolean denseLayout) {
        this.denseLayout = denseLayout;
        int buttonHeight = BUTTON_HEIGHT;
        int rowGap = denseLayout ? 2 : GAP;
        int sectionGap = denseLayout ? 4 : 14;
        int actionGap = denseLayout ? 4 : 18;
        int doneY = this.height - 26;
        int controlHeight = buttonHeight * 8 + rowGap * 5 + sectionGap + actionGap;

        layoutButtonHeight = buttonHeight;
        int availableFaceWidth = this.width - PANEL_WIDTH - FACE_PANEL_GAP - 28;
        int maxFaceSize = denseLayout ? DENSE_FACE_SIZE : BASE_FACE_SIZE;
        int availableFaceHeight = this.height - (denseLayout ? 56 : 78);
        faceSize = clamp(Math.min(Math.min(maxFaceSize, availableFaceWidth), availableFaceHeight), MIN_FACE_SIZE, maxFaceSize);
        pixelSize = Math.max(1, faceSize / FACE_PIXELS);
        faceSize = pixelSize * FACE_PIXELS;

        int contentWidth = faceSize + FACE_PANEL_GAP + PANEL_WIDTH;
        faceX = Math.max(12, this.width / 2 - contentWidth / 2);
        if (denseLayout) {
            int minPanelY = 28;
            int maxPanelY = Math.max(minPanelY, doneY - controlHeight - 8);
            panelY = clamp(this.height / 2 - controlHeight / 2, minPanelY, maxPanelY);
            faceY = clamp(panelY + controlHeight / 2 - faceSize / 2, 28, Math.max(28, this.height - faceSize - buttonHeight - 10));
        } else {
            faceY = Math.max(36, Math.min(this.height - faceSize - 52, this.height / 2 - faceSize / 2));
            panelY = faceY + 4;
        }
        panelX = faceX + faceSize + FACE_PANEL_GAP;
        panelWidth = PANEL_WIDTH;

        int y = panelY;
        int half = (panelWidth - GAP) / 2;
        addToggle(panelX, y, half, enabledText(), () -> ReactionsClientConfig.get().enabled = !ReactionsClientConfig.get().enabled, buttonHeight);
        addToggle(panelX + half + GAP, y, half, mouthText(), () -> ReactionsClientConfig.get().showMouth = !ReactionsClientConfig.get().showMouth, buttonHeight);

        y += buttonHeight + rowGap;
        addToggle(panelX, y, half, selfAnimationText(), () -> ReactionsClientConfig.get().animateSelf = !ReactionsClientConfig.get().animateSelf, buttonHeight);
        addToggle(panelX + half + GAP, y, half, otherAnimationText(), () -> ReactionsClientConfig.get().animateOthers = !ReactionsClientConfig.get().animateOthers, buttonHeight);

        y += buttonHeight + rowGap;
        addToggle(panelX, y, half, mouthAnimationText(true), () -> ReactionsClientConfig.get().animateMouth = !ReactionsClientConfig.get().animateMouth, buttonHeight);
        addEyelidOptionsButton(panelX + half + GAP, y, half, buttonHeight);

        y += buttonHeight + rowGap;
        addModeButton(EditMode.LEFT_EYE, panelX, y, half, buttonHeight);
        addModeButton(EditMode.RIGHT_EYE, panelX + half + GAP, y, half, buttonHeight);

        y += buttonHeight + rowGap;
        addModeButton(EditMode.MOUTH, panelX, y, half, buttonHeight);
        addModeButton(EditMode.EYEDROPPER, panelX + half + GAP, y, half, buttonHeight);

        y += buttonHeight + sectionGap;
        sizeHeaderY = y - Math.max(4, sectionGap);
        eyeWidthRowY = y;
        addSizeButton(panelX + panelWidth - buttonHeight * 2 - 6, y, true, -1, buttonHeight);
        addSizeButton(panelX + panelWidth - buttonHeight, y, true, 1, buttonHeight);

        y += buttonHeight + rowGap;
        eyeHeightRowY = y;
        addSizeButton(panelX + panelWidth - buttonHeight * 2 - 6, y, false, -1, buttonHeight);
        addSizeButton(panelX + panelWidth - buttonHeight, y, false, 1, buttonHeight);

        int actionY = denseLayout ? y + buttonHeight + actionGap : Math.min(this.height - 50, y + buttonHeight + actionGap);
        addRenderableWidget(Button.builder(Component.translatable("gui.reactions.button.beta"), button -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new ReactionsBetaAnimationsScreen(this));
            }
        }).bounds(panelX, actionY, half, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.reactions.button.reset"), button -> {
            ReactionsClientConfig.reset();
            mode = EditMode.LEFT_EYE;
            rebuildWidgets();
        }).bounds(panelX + half + GAP, actionY, half, buttonHeight).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.reactions.button.done"), button -> {
            ReactionsClientConfig.save();
            onClose();
        }).bounds(this.width / 2 - 48, doneY, 96, buttonHeight).build());
    }

    private void initCompact() {
        boolean ultraCompact = this.height < 180;
        boolean shortWindow = this.height < 232;
        int buttonHeight = ultraCompact ? 12 : shortWindow ? 14 : 16;
        int rowGap = ultraCompact ? 1 : shortWindow ? 2 : 3;
        int topY = ultraCompact ? 12 : shortWindow ? 18 : 24;
        int faceGap = ultraCompact ? 4 : shortWindow ? 8 : 16;
        int maxFaceSize = ultraCompact ? 24 : shortWindow ? 32 : COMPACT_FACE_SIZE;
        int minFaceSize = ultraCompact ? 16 : shortWindow ? 24 : 40;
        int sizeGap = ultraCompact ? 4 : 7;
        int actionGap = ultraCompact ? 1 : shortWindow ? 3 : 6;
        int controlsHeight = buttonHeight * 8 + rowGap * 5 + sizeGap + actionGap;

        faceSize = Math.min(maxFaceSize, Math.max(minFaceSize, Math.min(this.width - 24, this.height - topY - faceGap - controlsHeight - 4)));
        pixelSize = Math.max(1, faceSize / FACE_PIXELS);
        faceSize = pixelSize * FACE_PIXELS;
        faceX = Math.max(8, this.width / 2 - faceSize / 2);
        faceY = topY;

        panelWidth = Math.min(shortWindow ? 256 : 300, this.width - 16);
        panelX = Math.max(8, this.width / 2 - panelWidth / 2);
        panelY = faceY + faceSize + faceGap;

        layoutButtonHeight = buttonHeight;
        int half = (panelWidth - GAP) / 2;
        int y = panelY;

        addToggle(panelX, y, half, enabledText(), () -> ReactionsClientConfig.get().enabled = !ReactionsClientConfig.get().enabled, buttonHeight);
        addToggle(panelX + half + GAP, y, half, mouthText(), () -> ReactionsClientConfig.get().showMouth = !ReactionsClientConfig.get().showMouth, buttonHeight);

        y += buttonHeight + rowGap;
        addModeButton(EditMode.LEFT_EYE, panelX, y, half, buttonHeight);
        addModeButton(EditMode.RIGHT_EYE, panelX + half + GAP, y, half, buttonHeight);

        y += buttonHeight + rowGap;
        addModeButton(EditMode.MOUTH, panelX, y, half, buttonHeight);
        addModeButton(EditMode.EYEDROPPER, panelX + half + GAP, y, half, buttonHeight);

        y += buttonHeight + rowGap;
        addToggle(panelX, y, half, selfAnimationText(), () -> ReactionsClientConfig.get().animateSelf = !ReactionsClientConfig.get().animateSelf, buttonHeight);
        addToggle(panelX + half + GAP, y, half, otherAnimationText(), () -> ReactionsClientConfig.get().animateOthers = !ReactionsClientConfig.get().animateOthers, buttonHeight);

        y += buttonHeight + rowGap;
        addToggle(panelX, y, half, mouthAnimationText(true), () -> ReactionsClientConfig.get().animateMouth = !ReactionsClientConfig.get().animateMouth, buttonHeight);
        addEyelidOptionsButton(panelX + half + GAP, y, half, buttonHeight);

        y += buttonHeight + sizeGap;
        sizeHeaderY = y - Math.max(4, sizeGap);
        eyeWidthRowY = y;
        addSizeButton(panelX + panelWidth - buttonHeight * 2 - 6, y, true, -1, buttonHeight);
        addSizeButton(panelX + panelWidth - buttonHeight, y, true, 1, buttonHeight);

        y += buttonHeight + rowGap;
        eyeHeightRowY = y;
        addSizeButton(panelX + panelWidth - buttonHeight * 2 - 6, y, false, -1, buttonHeight);
        addSizeButton(panelX + panelWidth - buttonHeight, y, false, 1, buttonHeight);

        int bottomY = y + buttonHeight + actionGap;
        int third = Math.max(1, (panelWidth - GAP * 2) / 3);
        addRenderableWidget(Button.builder(Component.translatable("gui.reactions.button.beta"), button -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new ReactionsBetaAnimationsScreen(this));
            }
        }).bounds(panelX, bottomY, third, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.reactions.button.reset"), button -> {
            ReactionsClientConfig.reset();
            mode = EditMode.LEFT_EYE;
            rebuildWidgets();
        }).bounds(panelX + third + GAP, bottomY, third, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.reactions.button.done"), button -> {
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
            int nextEyeWidth = width ? config.eyeWidth + delta : config.eyeWidth;
            int nextEyeHeight = width ? config.eyeHeight : config.eyeHeight + delta;
            if (!ReactionsClientConfig.isAllowedEyeSize(nextEyeWidth, nextEyeHeight)) {
                showSizeLimitMessage();
                return;
            }
            if (width) {
                config.eyeWidth = nextEyeWidth;
            } else {
                config.eyeHeight = nextEyeHeight;
            }
            ReactionsClientConfig.save();
            rebuildWidgets();
        }).bounds(x, y, size, size).build());
    }

    private void addEyelidOptionsButton(int x, int y, int width, int height) {
        addRenderableWidget(Button.builder(Component.translatable("gui.reactions.eyelid_options"), button -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new ReactionsEyelidOptionsScreen(this));
            }
        }).bounds(x, y, width, height).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ReactionsClientConfig config = ReactionsClientConfig.get();
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawString(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 12, 0xFFFFFFFF);

        Identifier texture = skinTexture();
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, faceX, faceY, FACE_U, FACE_V, faceSize, faceSize, FACE_PIXELS, FACE_PIXELS, SKIN_SIZE, SKIN_SIZE);
        drawGrid(graphics);
        drawEyeSelection(graphics, config.leftEyeX, config.leftEyeY, config.eyeWidth, config.eyeHeight, 0xFF43D17C);
        drawEyeSelection(graphics, config.rightEyeX, config.rightEyeY, config.eyeWidth, config.eyeHeight, 0xFF4AA3FF);
        drawMouthSelection(graphics, config.leftMouthX, config.leftMouthY, config.rightMouthX, config.rightMouthY);
        drawPixelMarker(graphics, config.eyelidColorX, config.eyelidColorY, 0xFFFFC94A);

        int labelY = faceY + faceSize + 8;
        if (!compactLayout && !denseLayout) {
            graphics.drawString(this.font, Component.translatable("gui.reactions.eye_size_value", config.eyeWidth, config.eyeHeight), faceX, labelY, 0xFFA0A0A0);
        }
        int rowTextOffset = Math.max(2, (layoutButtonHeight - 9) / 2);
        Component eyeSizeText = Component.translatable("gui.reactions.eye_size");
        graphics.drawString(this.font, eyeSizeText, panelX + panelWidth / 2 - this.font.width(eyeSizeText) / 2, sizeHeaderY + 2, 0xFFA0A0A0);
        graphics.drawString(this.font, Component.translatable("gui.reactions.eye_width", config.eyeWidth), panelX, eyeWidthRowY + rowTextOffset, 0xFFFFFFFF);
        graphics.drawString(this.font, Component.translatable("gui.reactions.eye_height", config.eyeHeight), panelX, eyeHeightRowY + rowTextOffset, 0xFFFFFFFF);

        if (sizeLimitMessageTicks > 0) {
            graphics.drawString(this.font, Component.translatable("gui.reactions.eye_size_limit_reached"), panelX, Math.min(this.height - 38, eyeHeightRowY + layoutButtonHeight + 4), 0xFFFF6060);
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
        if (!ReactionsClientConfig.get().showMouth) {
            return;
        }
        int color = 0xFFFFD45A;
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
        Supplier<PlayerSkin> lookup = menuSkinLookup();
        if (lookup != null) {
            PlayerSkin skin = lookup.get();
            if (skin != null && skin.body() != null) {
                return skin.body().texturePath();
            }
        }
        return MinecraftFallbacks.DEFAULT_SKIN;
    }

    private Supplier<PlayerSkin> menuSkinLookup() {
        if (this.minecraft == null) {
            return null;
        }
        Minecraft minecraft = this.minecraft;
        GameProfile profile = minecraft.getGameProfile();
        if (profile == null) {
            return null;
        }
        if (menuSkinLookup == null || menuSkinProfile != profile) {
            menuSkinProfile = profile;
            menuSkinLookup = minecraft.getSkinManager().createLookup(profile, false);
        }
        return menuSkinLookup;
    }

    private boolean isInsideFace(double mouseX, double mouseY) {
        return mouseX >= faceX && mouseX < faceX + faceSize && mouseY >= faceY && mouseY < faceY + faceSize;
    }

    private Component enabledText() {
        return toggleText("gui.reactions.mod", ReactionsClientConfig.get().enabled);
    }

    private Component selfAnimationText() {
        return toggleText("gui.reactions.self_anims", ReactionsClientConfig.get().animateSelf);
    }

    private Component otherAnimationText() {
        return toggleText("gui.reactions.other_anims", ReactionsClientConfig.get().animateOthers);
    }

    private Component mouthText() {
        return toggleText("gui.reactions.mouth", ReactionsClientConfig.get().showMouth);
    }

    private Component mouthAnimationText(boolean shortText) {
        if (shortText) {
            return Component.translatable("gui.reactions.toggle", Component.translatable("gui.reactions.mouth_anims.short"), onOffShort(ReactionsClientConfig.get().animateMouth));
        }
        return toggleText("gui.reactions.mouth_anims", ReactionsClientConfig.get().animateMouth);
    }

    private Component modeText(EditMode targetMode) {
        Component label = Component.translatable(compactLayout ? targetMode.shortLabelKey : targetMode.labelKey);
        return mode == targetMode ? Component.translatable("gui.reactions.selected", label) : label;
    }

    private static Component toggleText(String labelKey, boolean enabled) {
        return Component.translatable("gui.reactions.toggle", Component.translatable(labelKey), onOff(enabled));
    }

    private static Component onOff(boolean enabled) {
        return Component.translatable(enabled ? "gui.reactions.on" : "gui.reactions.off");
    }

    private static Component onOffShort(boolean enabled) {
        return Component.translatable(enabled ? "gui.reactions.on.short" : "gui.reactions.off.short");
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
        LEFT_EYE("gui.reactions.edit.left_eye", "gui.reactions.edit.left_eye.short", 0xFF43D17C),
        RIGHT_EYE("gui.reactions.edit.right_eye", "gui.reactions.edit.right_eye.short", 0xFF4AA3FF),
        MOUTH("gui.reactions.edit.mouth", "gui.reactions.edit.mouth", 0xFFFFD45A),
        EYEDROPPER("gui.reactions.edit.eyelid_color", "gui.reactions.edit.eyelid_color.short", 0xFFFFC94A);

        private final String labelKey;
        private final String shortLabelKey;
        private final int color;

        EditMode(String labelKey, String shortLabelKey, int color) {
            this.labelKey = labelKey;
            this.shortLabelKey = shortLabelKey;
            this.color = color;
        }
    }

    private static final class MinecraftFallbacks {
        private static final Identifier DEFAULT_SKIN = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
    }
}
