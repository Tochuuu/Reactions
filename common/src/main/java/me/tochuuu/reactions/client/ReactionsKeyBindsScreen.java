package me.tochuuu.reactions.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class ReactionsKeyBindsScreen extends Screen {
    private static final int GAP = 8;
    private static final int BUTTON_HEIGHT = 20;
    private static final int PANEL_WIDTH = 340;

    private final Screen parent;
    private final List<KeyBindRow> rows = new ArrayList<>();
    private KeyMapping selectedKey;

    public ReactionsKeyBindsScreen(Screen parent) {
        super(Component.translatable("screen.reactions.keybinds"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rows.clear();

        int buttonHeight = this.height < 160 ? 16 : BUTTON_HEIGHT;
        int rowGap = this.height < 160 ? 3 : GAP;
        int panelWidth = Math.max(180, Math.min(PANEL_WIDTH, this.width - 16));
        int resetWidth = this.width < 260 ? 44 : 58;
        int keyWidth = panelWidth - resetWidth - GAP;
        int x = this.width / 2 - panelWidth / 2;
        int rowCount = reactionsKeys().length;
        int contentHeight = rowCount * buttonHeight + (rowCount - 1) * rowGap + rowGap + buttonHeight;
        int y = clamp(this.height / 2 - contentHeight / 2 + 8, 30, Math.max(30, this.height - contentHeight - 8));

        for (KeyMapping key : reactionsKeys()) {
            addKeyBindRow(key, x, y, keyWidth, resetWidth, buttonHeight);
            y += buttonHeight + rowGap;
        }

        y += rowGap;
        addRenderableWidget(Button.builder(Component.translatable("gui.reactions.button.done"), button -> onClose())
            .bounds(this.width / 2 - 48, y, 96, buttonHeight)
            .build());

        refreshButtons();
    }

    private void addKeyBindRow(KeyMapping key, int x, int y, int keyWidth, int resetWidth, int height) {
        Button keyButton = Button.builder(keyText(key), button -> {
            selectedKey = key;
            refreshButtons();
        }).bounds(x, y, keyWidth, height).build();
        Button resetButton = Button.builder(Component.translatable("gui.reactions.button.reset"), button -> {
            key.setKey(key.getDefaultKey());
            selectedKey = null;
            saveMappings();
        }).bounds(x + keyWidth + GAP, y, resetWidth, height).build();

        rows.add(new KeyBindRow(key, keyButton, resetButton));
        addRenderableWidget(keyButton);
        addRenderableWidget(resetButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 16, 0xFFFFFFFF);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selectedKey != null) {
            selectedKey.setKey(keyCode == GLFW.GLFW_KEY_ESCAPE ? InputConstants.UNKNOWN : InputConstants.getKey(keyCode, scanCode));
            selectedKey = null;
            saveMappings();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (selectedKey != null) {
            selectedKey.setKey(InputConstants.Type.MOUSE.getOrCreate(button));
            selectedKey = null;
            saveMappings();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        saveMappings();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private void saveMappings() {
        KeyMapping.resetMapping();
        if (this.minecraft != null) {
            this.minecraft.options.save();
        }
        refreshButtons();
    }

    private void refreshButtons() {
        for (KeyBindRow row : rows) {
            row.keyButton.setMessage(keyText(row.key));
            row.resetButton.active = !row.key.isDefault();
        }
    }

    private Component keyText(KeyMapping key) {
        Component binding = key.getTranslatedKeyMessage();
        if (selectedKey == key) {
            binding = Component.literal("> ")
                .append(binding.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE))
                .append(" <")
                .withStyle(ChatFormatting.YELLOW);
        } else if (hasCollision(key)) {
            binding = Component.literal("[")
                .append(binding.copy().withStyle(ChatFormatting.WHITE))
                .append("]")
                .withStyle(ChatFormatting.YELLOW);
        }
        return Component.translatable("gui.reactions.toggle", Component.translatable(key.getName()), binding);
    }

    private boolean hasCollision(KeyMapping key) {
        if (this.minecraft == null || key.isUnbound()) {
            return false;
        }
        for (KeyMapping other : this.minecraft.options.keyMappings) {
            if (other != key && key.same(other)) {
                return true;
            }
        }
        return false;
    }

    private static KeyMapping[] reactionsKeys() {
        return new KeyMapping[] {
            ReactionsClient.manualCloseEyesKey(),
            ReactionsClient.manualLookEyesKey(),
            ReactionsClient.manualSquintEyesKey(),
            ReactionsClient.openConfigKey()
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record KeyBindRow(KeyMapping key, Button keyButton, Button resetButton) {
    }
}
