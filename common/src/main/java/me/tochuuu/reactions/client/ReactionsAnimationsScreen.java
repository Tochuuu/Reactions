package me.tochuuu.reactions.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import me.tochuuu.reactions.client.PlayerEyeRenderLayer.PreviewAnimation;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ReactionsAnimationsScreen extends Screen {
    private static final int PANEL_WIDTH = 340;
    private static final ReactionsClientConfig DEFAULT_ANIMATIONS = new ReactionsClientConfig();
    private final Screen parent;
    private final List<AnimationOption> animationOptions;
    private Category category = Category.EYES;
    private int page;
    private int pageCount;
    private int footerY;
    private boolean previewOpen;
    private int previewIndex;
    private int previewX;
    private int previewY;
    private int previewSize;
    private net.minecraft.client.model.PlayerModel previewModel;
    private boolean previewSlim;
    private long previewStartedAt;
    private GameProfile menuSkinProfile;
    private ResourceLocation menuSkinTexture;

    public ReactionsAnimationsScreen(Screen parent) {
        super(Component.translatable("screen.reactions.animations"));
        this.parent = parent;
        this.animationOptions = java.util.Arrays.stream(Category.values()).flatMap(tab -> options(tab).stream()).toList();
    }

    @Override
    protected void init() {
        int panelWidth = Math.max(1, Math.min(PANEL_WIDTH, this.width - 24));
        int x = this.width / 2 - panelWidth / 2;
        int buttonHeight = this.height < 160 ? 12 : this.height < 180 ? 16 : 20;
        int gap = this.height < 160 ? 2 : 4;
        int step = buttonHeight + gap;
        footerY = this.height - buttonHeight - 8;
        if (previewOpen) {
            initPreview(x, panelWidth, buttonHeight, gap);
            return;
        }
        int columns = this.width < 360 ? 2 : 4;
        int tabWidth = (panelWidth - gap * (columns - 1)) / columns;
        int tabsY = titleY() + 18;
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            Category tab = categories[i];
            Button button = addRenderableWidget(Button.builder(Component.translatable(tab.key), selected -> {
                category = tab;
                page = 0;
                rebuildWidgets();
            }).bounds(x + i % columns * (tabWidth + gap), tabsY + i / columns * step, tabWidth, buttonHeight).build());
            button.active = category != tab;
        }

        int y = tabsY + ((categories.length + columns - 1) / columns) * step + gap;
        int pagerY = footerY - step;
        int rows = Math.max(1, (pagerY - y) / step);
        List<AnimationOption> options = options();
        pageCount = (options.size() + rows - 1) / rows;
        page = Math.max(0, Math.min(page, pageCount - 1));
        for (int i = page * rows; i < Math.min(options.size(), (page + 1) * rows); i++) {
            AnimationOption option = options.get(i);
            addRenderableWidget(Button.builder(option.text(), button -> {
                option.toggle.accept(ReactionsClientConfig.get());
                ReactionsClientConfig.save();
                button.setMessage(option.text());
            }).bounds(x, y + (i - page * rows) * step, panelWidth, buttonHeight).build());
        }

        if (pageCount > 1) {
            Button previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> {
                page--;
                rebuildWidgets();
            }).bounds(x, pagerY, 32, buttonHeight).tooltip(Tooltip.create(Component.translatable("gui.reactions.previous_page"))).build());
            previous.active = page > 0;
            Button next = addRenderableWidget(Button.builder(Component.literal(">"), button -> {
                page++;
                rebuildWidgets();
            }).bounds(x + panelWidth - 32, pagerY, 32, buttonHeight).tooltip(Tooltip.create(Component.translatable("gui.reactions.next_page"))).build());
            next.active = page + 1 < pageCount;
        }
        int third = (panelWidth - gap * 2) / 3;
        Button reset = addRenderableWidget(Button.builder(Component.translatable("gui.reactions.button.reset"), button -> resetAnimations())
            .bounds(x, footerY, third, buttonHeight).build());
        reset.active = category != Category.EYEBROWS;
        addRenderableWidget(Button.builder(Component.translatable("gui.reactions.button.preview"), button -> {
            previewIndex = allOptions().stream().map(AnimationOption::animation).toList().indexOf(options.get(page * rows).animation);
            previewOpen = true;
            previewStartedAt = Util.getMillis();
            rebuildWidgets();
        }).bounds(x + third + gap, footerY, third, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.reactions.button.back"), button -> onClose())
            .bounds(x + (third + gap) * 2, footerY, panelWidth - (third + gap) * 2, buttonHeight).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Component heading = previewOpen ? Component.translatable("screen.reactions.animation_preview") : this.title;
        graphics.drawString(this.font, heading, this.width / 2 - this.font.width(heading) / 2, titleY(), 0xFFFFFFFF);
        if (previewOpen) {
            drawPreview(graphics);
        } else if (pageCount > 1) {
            Component pages = Component.translatable("gui.reactions.page", page + 1, pageCount);
            graphics.drawString(this.font, pages, this.width / 2 - this.font.width(pages) / 2,
                footerY - (this.height < 160 ? 13 : this.height < 180 ? 18 : 22), 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        if (previewOpen) {
            previewOpen = false;
            rebuildWidgets();
            return;
        }
        ReactionsClientConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private int titleY() {
        return this.height < 180 ? 6 : 12;
    }

    private void resetAnimations() {
        ReactionsClientConfig config = ReactionsClientConfig.get();
        for (AnimationOption option : allOptions()) {
            if (option.changedFromDefault()) {
                option.toggle.accept(config);
            }
        }
        ReactionsClientConfig.save();
        rebuildWidgets();
    }

    private void initPreview(int x, int panelWidth, int buttonHeight, int gap) {
        int controlsY = footerY - buttonHeight - gap;
        int top = titleY() + 18;
        previewSize = Math.max(8, Math.min(128, Math.min(panelWidth, controlsY - gap - top)));
        previewX = this.width / 2 - previewSize / 2;
        previewY = top + Math.max(0, (controlsY - gap - top - previewSize) / 2);
        AnimationOption option = allOptions().get(previewIndex);
        addRenderableWidget(Button.builder(Component.literal("<"), button -> selectPreview(-1))
            .bounds(x, controlsY, 24, buttonHeight).tooltip(Tooltip.create(Component.translatable("gui.reactions.previous_animation"))).build());
        addRenderableWidget(Button.builder(option.text(), button -> {
            option.toggle.accept(ReactionsClientConfig.get());
            ReactionsClientConfig.save();
            previewStartedAt = Util.getMillis();
            button.setMessage(option.text());
        }).bounds(x + 24 + gap, controlsY, panelWidth - 48 - gap * 2, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> selectPreview(1))
            .bounds(x + panelWidth - 24, controlsY, 24, buttonHeight).tooltip(Tooltip.create(Component.translatable("gui.reactions.next_animation"))).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.reactions.button.back"), button -> onClose())
            .bounds(this.width / 2 - 45, footerY, 90, buttonHeight).build());
    }

    private void selectPreview(int step) {
        previewIndex = Math.floorMod(previewIndex + step, allOptions().size());
        previewStartedAt = Util.getMillis();
        rebuildWidgets();
    }

    private void drawPreview(GuiGraphics graphics) {

        PlayerEyeRenderLayer.PreviewState state = new PlayerEyeRenderLayer.PreviewState(allOptions().get(previewIndex).animation);
        ResourceLocation skin = previewSkin();
        state.ageInTicks = (Util.getMillis() - previewStartedAt) / 50.0F;
        boolean slim = this.minecraft.player != null ? "slim".equals(this.minecraft.player.getModelName())
            : "slim".equals(DefaultPlayerSkin.getSkinModelName(this.minecraft.getUser().getGameProfile().getId()));
        if (previewModel == null || previewSlim != slim) {
            previewSlim = slim;
            previewModel = new net.minecraft.client.model.PlayerModel(this.minecraft.getEntityModels().bakeLayer(
                slim ? net.minecraft.client.model.geom.ModelLayers.PLAYER_SLIM : net.minecraft.client.model.geom.ModelLayers.PLAYER), slim);
        }
        com.mojang.blaze3d.vertex.PoseStack pose = graphics.pose();
        graphics.enableScissor(previewX, previewY, previewX + previewSize, previewY + previewSize);
        graphics.flush();
        pose.pushPose();
        float zoom = previewSize / 0.62F;
        pose.translate(previewX + previewSize / 2.0F, previewY + previewSize / 2.0F + zoom * 0.25F, 50.0F);
        pose.scale(zoom, zoom, -zoom);
        com.mojang.blaze3d.platform.Lighting.setupForEntityInInventory();
        try {
            net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers = this.minecraft.renderBuffers().bufferSource();
            previewModel.head.render(pose, buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutout(skin)),
                0x00F000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            previewModel.hat.render(pose, buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutout(skin)),
                0x00F000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            new PlayerEyeRenderLayer(new net.minecraft.client.renderer.entity.RenderLayerParent<net.minecraft.client.player.AbstractClientPlayer, net.minecraft.client.model.PlayerModel<net.minecraft.client.player.AbstractClientPlayer>>() {
                @Override
                public net.minecraft.client.model.PlayerModel<net.minecraft.client.player.AbstractClientPlayer> getModel() { return previewModel; }
                @Override
                public net.minecraft.resources.ResourceLocation getTextureLocation(net.minecraft.client.player.AbstractClientPlayer player) { return skin; }
            }).renderPreview(pose, buffers, 0x00F000F0, skin, state);
            buffers.endBatch();
            graphics.flush();
        } finally {
            pose.popPose();
            graphics.disableScissor();
            com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
        }
    }

    private ResourceLocation previewSkin() {
        if (this.minecraft != null) {
            if (this.minecraft.player != null) {
                return this.minecraft.player.getSkinTextureLocation();
            }
            GameProfile profile = this.minecraft.getUser().getGameProfile();
            if (profile != null) {
                if (menuSkinTexture == null || menuSkinProfile != profile) {
                    menuSkinProfile = profile;
                    menuSkinTexture = this.minecraft.getSkinManager().getInsecureSkinLocation(profile);
                }
                if (menuSkinTexture != null) {
                    return menuSkinTexture;
                }
            }
        }
        return DefaultPlayerSkin.getDefaultSkin();
    }

    private List<AnimationOption> allOptions() {
        return animationOptions;
    }

    private List<AnimationOption> options() {
        return options(category);
    }

    private List<AnimationOption> options(Category category) {
        return switch (category) {
            case EYES -> List.of(
                new AnimationOption(PreviewAnimation.BLINKING, "gui.reactions.anim.blinking", c -> c.animateBlinking, c -> c.animateBlinking = !c.animateBlinking),
                new AnimationOption(PreviewAnimation.IDLE_LOOKING, "gui.reactions.anim.idle_looking", c -> c.animateIdleLooking, c -> c.animateIdleLooking = !c.animateIdleLooking),
                new AnimationOption(PreviewAnimation.IDLE_LOOK_BLINKING, "gui.reactions.anim.idle_look_blinking", c -> c.animateIdleLookBlinking, c -> c.animateIdleLookBlinking = !c.animateIdleLookBlinking),
                new AnimationOption(PreviewAnimation.AFK, "gui.reactions.anim.tired", c -> c.animateAfk, c -> c.animateAfk = !c.animateAfk),
                new AnimationOption(PreviewAnimation.SLEEPING, "gui.reactions.anim.sleeping", c -> c.animateSleeping, c -> c.animateSleeping = !c.animateSleeping),
                new AnimationOption(PreviewAnimation.SPYGLASS, "gui.reactions.anim.spyglass", c -> c.animateSpyglass, c -> c.animateSpyglass = !c.animateSpyglass),
                new AnimationOption(PreviewAnimation.SUN_SQUINT, "gui.reactions.anim.sun_squint", c -> c.animateSunSquint, c -> c.animateSunSquint = !c.animateSunSquint));
            case REACTIONS -> List.of(
                new AnimationOption(PreviewAnimation.LOW_HEALTH, "gui.reactions.anim.low_health", c -> c.animateLowHealth, c -> c.animateLowHealth = !c.animateLowHealth),
                new AnimationOption(PreviewAnimation.DAMAGE, "gui.reactions.anim.damage", c -> c.animateDamage, c -> c.animateDamage = !c.animateDamage),
                new AnimationOption(PreviewAnimation.FALLING, "gui.reactions.anim.falling", c -> c.animateFalling, c -> c.animateFalling = !c.animateFalling),
                new AnimationOption(PreviewAnimation.LANDING, "gui.reactions.anim.landing", c -> c.animateLanding, c -> c.animateLanding = !c.animateLanding),
                new AnimationOption(PreviewAnimation.BLOCK_FOCUS, "gui.reactions.anim.block_focus", c -> c.animateBlockFocus, c -> c.animateBlockFocus = !c.animateBlockFocus),
                new AnimationOption(PreviewAnimation.VEHICLE_LOOK, "gui.reactions.anim.vehicle_look", c -> c.animateVehicleLook, c -> c.animateVehicleLook = !c.animateVehicleLook),
                new AnimationOption(PreviewAnimation.BOW_SQUINT, "gui.reactions.bow_squint", c -> c.animateBowShooting, c -> c.animateBowShooting = !c.animateBowShooting));
            case MOUTH -> List.of(
                new AnimationOption(PreviewAnimation.EATING, "gui.reactions.anim.eating", c -> c.animateEating, c -> c.animateEating = !c.animateEating),
                new AnimationOption(PreviewAnimation.DRINKING, "gui.reactions.anim.drinking", c -> c.animateDrinking, c -> c.animateDrinking = !c.animateDrinking),
                new AnimationOption(PreviewAnimation.HORN, "gui.reactions.anim.horn", c -> c.animateHorn, c -> c.animateHorn = !c.animateHorn),
                new AnimationOption(PreviewAnimation.ADVANCEMENTS, "gui.reactions.anim.advancements", c -> c.animateAdvancements, c -> c.animateAdvancements = !c.animateAdvancements));
            case MANUAL -> List.of(
                new AnimationOption(PreviewAnimation.MANUAL_CLOSING, "gui.reactions.anim.manual_closing", c -> c.animateManualClosing, c -> c.animateManualClosing = !c.animateManualClosing),
                new AnimationOption(PreviewAnimation.MANUAL_SQUINTING, "gui.reactions.anim.manual_squinting", c -> c.animateManualSquinting, c -> c.animateManualSquinting = !c.animateManualSquinting),
                new AnimationOption(PreviewAnimation.MANUAL_WIDENING, "gui.reactions.anim.manual_widening", c -> c.animateManualWidening, c -> c.animateManualWidening = !c.animateManualWidening),
                new AnimationOption(PreviewAnimation.MANUAL_LOOKING, "gui.reactions.anim.manual_looking", c -> c.animateManualLooking, c -> c.animateManualLooking = !c.animateManualLooking),
                new AnimationOption(PreviewAnimation.SKEPTICAL, "gui.reactions.skeptical_expression", c -> c.animateSkepticalExpression, c -> c.animateSkepticalExpression = !c.animateSkepticalExpression));
            case EYEBROWS -> List.of(
                new AnimationOption(PreviewAnimation.EYEBROWS, "gui.reactions.eyebrows", c -> c.showEyebrows, c -> c.showEyebrows = !c.showEyebrows));
        };
    }

    private enum Category {
        EYES("gui.reactions.anim.category.eyes"),
        REACTIONS("gui.reactions.anim.category.reactions"),
        MOUTH("gui.reactions.anim.category.mouth"),
        MANUAL("gui.reactions.anim.category.manual"),
        EYEBROWS("gui.reactions.eyebrows");

        private final String key;

        Category(String key) {
            this.key = key;
        }
    }

    private record AnimationOption(PreviewAnimation animation, String key, Predicate<ReactionsClientConfig> enabled, Consumer<ReactionsClientConfig> toggle) {
        private Component text() {
            var label = Component.translatable(key);
            if (changedFromDefault()) {
                label.withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE);
            }
            return Component.translatable("gui.reactions.toggle", label,
                Component.translatable(enabled.test(ReactionsClientConfig.get()) ? "gui.reactions.on.short" : "gui.reactions.off.short"));
        }

        private boolean changedFromDefault() {
            return animation != PreviewAnimation.EYEBROWS
                && enabled.test(ReactionsClientConfig.get()) != enabled.test(DEFAULT_ANIMATIONS);
        }
    }
}
