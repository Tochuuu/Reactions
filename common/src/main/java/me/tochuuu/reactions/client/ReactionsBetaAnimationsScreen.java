package me.tochuuu.reactions.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ReactionsBetaAnimationsScreen extends Screen {
    private static final int PANEL_WIDTH = 180;

    private final Screen parent;

    public ReactionsBetaAnimationsScreen(Screen parent) {
        super(Component.literal("Beta animations"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelWidth = Math.max(90, Math.min(PANEL_WIDTH, this.width - 24));
        int x = this.width / 2 - panelWidth / 2;
        int y = Math.min(Math.max(40, this.height / 2 - 24), this.height - 52);

        addRenderableWidget(Button.builder(bowShootingAnimationText(), button -> {
            ReactionsClientConfig.get().animateBowShooting = !ReactionsClientConfig.get().animateBowShooting;
            ReactionsClientConfig.save();
            rebuildWidgets();
        }).bounds(x, y, panelWidth, 20).build());

        int backY = Math.min(Math.max(y + 24, this.height - 30), this.height - 24);
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose()).bounds(this.width / 2 - 45, backY, 90, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 16, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        ReactionsClientConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private Component bowShootingAnimationText() {
        return Component.literal("Bow squint: " + onOff(ReactionsClientConfig.get().animateBowShooting));
    }

    private static String onOff(boolean enabled) {
        return enabled ? "On" : "Off";
    }
}
