package me.tochuuu.reactions.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.function.Supplier;

public final class ReactionsEyelidOptionsScreen extends Screen {
    private static final int FACE_U = 8;
    private static final int FACE_V = 8;
    private static final int FACE_PIXELS = 8;
    private static final int SKIN_SIZE = 64;
    private static final int PANEL_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_PREVIEW_BLINK_TICKS = 32;
    private static final float SMALL_EYELID_TINT_FACTOR = 0xB0 / 255.0F;
    private static final float LARGE_EYELID_TINT_FACTOR = 0xD0 / 255.0F;
    private static final float EYELID_COLUMN_SHADE_RANGE = 0.08F;
    private static final float EYELID_TOP_SUBTLE_DARK_FACTOR = 0.97F;

    private final Screen parent;
    private int previewX;
    private int previewY;
    private int previewSize;
    private int previewPixelSize;
    private boolean sliderPreviewActive;
    private int previewBlinkTicks;
    private GameProfile menuSkinProfile;
    private Supplier<PlayerSkin> menuSkinLookup;

    public ReactionsEyelidOptionsScreen(Screen parent) {
        super(Component.translatable("screen.reactions.eyelid_options"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        sliderPreviewActive = false;
        boolean compact = this.height < 190;
        boolean veryCompact = this.height < 140;
        int buttonHeight = veryCompact ? 12 : compact ? 16 : BUTTON_HEIGHT;
        int gap = veryCompact ? 2 : compact ? 5 : 8;
        int previewGap = veryCompact ? 3 : gap;
        int sideMargin = 8;
        int panelWidth = Math.max(1, Math.min(PANEL_WIDTH, this.width - sideMargin * 2));
        int topMargin = veryCompact ? 18 : 30;
        int bottomMargin = 6;
        int controlHeight = buttonHeight * 3 + gap * 2;
        int availablePreviewHeight = Math.max(16, this.height - topMargin - bottomMargin - controlHeight - previewGap);

        previewPixelSize = Math.max(2, Math.min(Math.min(compact ? 6 : 8, (this.width - 24) / FACE_PIXELS), availablePreviewHeight / FACE_PIXELS));
        previewSize = previewPixelSize * FACE_PIXELS;
        int contentHeight = previewSize + previewGap + controlHeight;
        int y = clamp(this.height / 2 - contentHeight / 2, topMargin, Math.max(topMargin, this.height - contentHeight - bottomMargin));
        int x = this.width / 2 - panelWidth / 2;

        previewX = this.width / 2 - previewSize / 2;
        previewY = y;
        y += previewSize + previewGap;

        int half = Math.max(1, (panelWidth - gap) / 2);
        int rightWidth = Math.max(1, panelWidth - half - gap);
        addRenderableWidget(Button.builder(texturedLidsText(), button -> {
            ReactionsClientConfig.get().texturedEyelids = !ReactionsClientConfig.get().texturedEyelids;
            ReactionsClientConfig.save();
            triggerButtonBlink();
            rebuildWidgets();
        }).bounds(x, y, half, buttonHeight).build());
        addRenderableWidget(Button.builder(eyelidTintText(), button -> {
            ReactionsClientConfig config = ReactionsClientConfig.get();
            config.cleanEyelidColor = !config.cleanEyelidColor;
            if (!config.cleanEyelidColor && config.eyelidTintIntensity <= 0) {
                config.eyelidTintIntensity = 50;
            }
            ReactionsClientConfig.save();
            triggerButtonBlink();
            rebuildWidgets();
        }).bounds(x + half + gap, y, rightWidth, buttonHeight).build());

        y += buttonHeight + gap;
        ReactionsClientConfig config = ReactionsClientConfig.get();
        EyelidTintSlider slider = new EyelidTintSlider(x, y, panelWidth, buttonHeight, config.eyelidTintIntensity);
        slider.active = !config.cleanEyelidColor;
        addRenderableWidget(slider);

        y += buttonHeight + gap;
        int backWidth = Math.min(90, panelWidth);
        addRenderableWidget(Button.builder(Component.translatable("gui.reactions.button.back"), button -> onClose()).bounds(this.width / 2 - backWidth / 2, y, backWidth, buttonHeight).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ReactionsClientConfig config = ReactionsClientConfig.get();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.text(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 16, 0xFFFFFFFF);
        drawPreview(graphics, skinTexture(), config, previewClosed());
    }

    @Override
    public void tick() {
        if (previewBlinkTicks > 0) {
            previewBlinkTicks--;
        }
    }

    @Override
    public void onClose() {
        ReactionsClientConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private void triggerButtonBlink() {
        previewBlinkTicks = BUTTON_PREVIEW_BLINK_TICKS;
    }

    private boolean previewClosed() {
        return sliderPreviewActive || previewBlinkTicks > 0;
    }

    private void drawPreview(GuiGraphicsExtractor graphics, Identifier texture, ReactionsClientConfig config, boolean closed) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, previewX, previewY, FACE_U, FACE_V, previewSize, previewSize, FACE_PIXELS, FACE_PIXELS, SKIN_SIZE, SKIN_SIZE);
        if (closed) {
            drawClosedEye(graphics, texture, config.leftEyeX, config.leftEyeY, config);
            drawClosedEye(graphics, texture, config.rightEyeX, config.rightEyeY, config);
        }
        graphics.outline(previewX, previewY, previewSize, previewSize, 0xFFFFFFFF);
    }

    private void drawClosedEye(GuiGraphicsExtractor graphics, Identifier texture, int skinX, int skinY, ReactionsClientConfig config) {
        int eyeWidth = Math.max(1, config.eyeWidth);
        int eyeHeight = Math.max(1, config.eyeHeight);
        int x = previewX + (skinX - FACE_U) * previewPixelSize;
        int y = previewY + (skinY - FACE_V) * previewPixelSize;
        if (eyeHeight == 1) {
            drawEyelidTile(graphics, texture, x, y, eyeWidth * previewPixelSize, previewPixelSize, eyelidTintAlpha(config, 0, 1, 0, 1), config);
            return;
        }

        for (int row = 0; row < eyeHeight; row++) {
            for (int column = 0; column < eyeWidth; column++) {
                drawEyelidTile(graphics, texture, x + column * previewPixelSize, y + row * previewPixelSize, previewPixelSize, previewPixelSize, eyelidTintAlpha(config, row, eyeHeight, column, eyeWidth), config);
            }
        }
    }

    private void drawEyelidTile(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int width, int height, int tintAlpha, ReactionsClientConfig config) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, clamp(config.eyelidColorX, 0, SKIN_SIZE - 1), clamp(config.eyelidColorY, 0, SKIN_SIZE - 1), width, height, 1, 1, SKIN_SIZE, SKIN_SIZE);
        if (tintAlpha > 0) {
            graphics.fill(x, y, x + width, y + height, tintAlpha << 24);
        }
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

    private Component texturedLidsText() {
        return Component.translatable("gui.reactions.toggle", Component.translatable("gui.reactions.textured_lids.short"), onOffShort(ReactionsClientConfig.get().texturedEyelids));
    }

    private Component eyelidTintText() {
        return Component.translatable("gui.reactions.toggle", Component.translatable("gui.reactions.eyelid_tint.short"), onOffShort(!ReactionsClientConfig.get().cleanEyelidColor));
    }

    private static Component onOffShort(boolean enabled) {
        return Component.translatable(enabled ? "gui.reactions.on.short" : "gui.reactions.off.short");
    }

    private static int eyelidTintAlpha(ReactionsClientConfig config, int row, int rows, int column, int columns) {
        if (config.cleanEyelidColor || config.eyelidTintIntensity <= 0) {
            return 0;
        }
        float targetFactor = config.eyeHeight > 1 ? LARGE_EYELID_TINT_FACTOR : SMALL_EYELID_TINT_FACTOR;
        float intensity = clamp(config.eyelidTintIntensity, 0, 100) / 100.0F;
        float baseFactor = 1.0F + (targetFactor - 1.0F) * intensity;
        return clamp(Math.round(255.0F * (1.0F - baseFactor * eyelidTileShadeFactor(config, row, rows, column, columns))), 0, 255);
    }

    private static float eyelidTileShadeFactor(ReactionsClientConfig config, int row, int rows, int column, int columns) {
        if (!config.texturedEyelids) {
            return 1.0F;
        }
        float rowFactor = 1.0F;
        if (rows > 1) {
            float rowProgress = row / (float) (rows - 1);
            rowFactor = EYELID_TOP_SUBTLE_DARK_FACTOR + (1.0F - EYELID_TOP_SUBTLE_DARK_FACTOR) * rowProgress;
        }
        float columnFactor = 1.0F;
        if (columns > 1) {
            float columnProgress = column / (float) (columns - 1);
            columnFactor += (0.5F - Math.abs(columnProgress - 0.5F)) * EYELID_COLUMN_SHADE_RANGE;
            columnFactor -= columnProgress * EYELID_COLUMN_SHADE_RANGE * 0.5F;
        }
        return rowFactor * columnFactor;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private final class EyelidTintSlider extends AbstractSliderButton {
        private EyelidTintSlider(int x, int y, int width, int height, int tintIntensity) {
            super(x, y, width, height, Component.empty(), clamp(tintIntensity, 0, 100) / 100.0D);
            updateMessage();
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            sliderPreviewActive = true;
            super.onClick(event, doubleClick);
        }

        @Override
        protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
            sliderPreviewActive = true;
            super.onDrag(event, dragX, dragY);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("gui.reactions.eyelid_tint_intensity", Math.round(this.value * 100.0D)));
        }

        @Override
        protected void applyValue() {
            int tintIntensity = clamp((int) Math.round(this.value * 100.0D), 0, 100);
            ReactionsClientConfig.get().eyelidTintIntensity = tintIntensity;
        }

        @Override
        public void onRelease(MouseButtonEvent event) {
            super.onRelease(event);
            sliderPreviewActive = false;
            ReactionsClientConfig.save();
        }
    }

    private static final class MinecraftFallbacks {
        private static final Identifier DEFAULT_SKIN = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
    }
}
