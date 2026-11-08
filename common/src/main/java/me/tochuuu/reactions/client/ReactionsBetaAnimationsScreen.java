package me.tochuuu.reactions.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ReactionsBetaAnimationsScreen extends Screen {
    private static final int PANEL_WIDTH = 180;

    private final Screen parent;

    public ReactionsBetaAnimationsScreen(Screen parent) {
        super(Component.translatable("screen.reactions.beta_animations"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelWidth = Math.max(90, Math.min(PANEL_WIDTH, this.width - 24));
        int x = this.width / 2 - panelWidth / 2;
        int buttonHeight = this.height < 120 ? 12 : this.height < 160 ? 16 : 20;
        int gap = this.height < 120 ? 2 : this.height < 160 ? 4 : 8;
        int step = buttonHeight + gap;
        int contentHeight = buttonHeight * 4 + gap * 3;
        int y = Math.max(32, Math.min(this.height / 2 - contentHeight / 2, this.height - contentHeight - 8));

        addRenderableWidget(Button.builder(bowShootingAnimationText(), button -> {
            ReactionsClientConfig.get().animateBowShooting = !ReactionsClientConfig.get().animateBowShooting;
            ReactionsClientConfig.save();
            rebuildWidgets();
        }).bounds(x, y, panelWidth, buttonHeight).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.reactions.toggle", Component.translatable("gui.reactions.eyebrows"), onOff(ReactionsClientConfig.get().showEyebrows)), button -> {
            ReactionsClientConfig.get().showEyebrows = !ReactionsClientConfig.get().showEyebrows;
            ReactionsClientConfig.save();
            rebuildWidgets();
        }).bounds(x, y + step, panelWidth, buttonHeight).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.reactions.toggle", Component.translatable("gui.reactions.skeptical_expression"), onOff(ReactionsClientConfig.get().animateSkepticalExpression)), button -> {
            ReactionsClientConfig.get().animateSkepticalExpression = !ReactionsClientConfig.get().animateSkepticalExpression;
            ReactionsClientConfig.save();
            rebuildWidgets();
        }).bounds(x, y + step * 2, panelWidth, buttonHeight).build());

        int backY = Math.max(y + step * 3, this.height - buttonHeight - 8);
        addRenderableWidget(Button.builder(Component.translatable("gui.reactions.button.back"), button -> onClose()).bounds(this.width / 2 - 45, backY, 90, buttonHeight).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 16, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        ReactionsClientConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private Component bowShootingAnimationText() {
        return Component.translatable("gui.reactions.toggle", Component.translatable("gui.reactions.bow_squint"), onOff(ReactionsClientConfig.get().animateBowShooting));
    }

    private static Component onOff(boolean enabled) {
        return Component.translatable(enabled ? "gui.reactions.on" : "gui.reactions.off");
    }
}
