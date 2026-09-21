package io.github.createdelight.sequencedpatternprovider.client;

import appeng.api.stacks.GenericStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

final class SequenceAmountScreen extends Screen {
    private final Screen parent;
    private final GenericStack current;
    private final Consumer<GenericStack> callback;
    private EditBox amount;

    SequenceAmountScreen(Screen parent, GenericStack current, Consumer<GenericStack> callback) {
        super(Component.translatable("gui.sequenced_pattern_provider.set_amount"));
        this.parent = parent;
        this.current = current;
        this.callback = callback;
    }

    @Override
    protected void init() {
        amount = new EditBox(font, width / 2 - 60, height / 2 - 10, 120, 20,
                Component.translatable("gui.sequenced_pattern_provider.set_amount"));
        amount.setValue(Long.toString(current.amount()));
        amount.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        addRenderableWidget(amount);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> apply())
                .bounds(width / 2 - 60, height / 2 + 18, 58, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(width / 2 + 2, height / 2 + 18, 58, 20).build());
        setInitialFocus(amount);
    }

    private void apply() {
        try {
            long value = Long.parseLong(amount.getValue());
            if (value > 0) callback.accept(new GenericStack(current.what(), value));
        } catch (NumberFormatException ignored) {
        }
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            apply();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 42, 0xFFFFFF);
        graphics.drawCenteredString(font, current.what().getDisplayName(), width / 2, height / 2 - 28, 0xDDDDDD);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
