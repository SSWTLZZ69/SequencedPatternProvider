package io.github.createdelight.sequencedpatternprovider.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import io.github.createdelight.sequencedpatternprovider.menu.MasterProviderMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public final class MasterProviderScreen extends AEBaseScreen<MasterProviderMenu> {
    private final AE2Button abortButton;
    private final AE2Button clearChildrenButton;

    public MasterProviderScreen(MasterProviderMenu menu, Inventory inventory,
                                Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        abortButton = new AE2Button(0, 0, 72, 18, Component.translatable(
                "gui.sequenced_pattern_provider.master.abort"), button -> menu.abortJob());
        abortButton.setTooltip(Tooltip.create(Component.translatable(
                "tooltip.sequenced_pattern_provider.master.abort")));
        widgets.add("abortJob", abortButton);

        clearChildrenButton = new AE2Button(0, 0, 88, 18, Component.translatable(
                "gui.sequenced_pattern_provider.master.clear_children"), button -> menu.clearChildren());
        clearChildrenButton.setTooltip(Tooltip.create(Component.translatable(
                "tooltip.sequenced_pattern_provider.master.clear_children")));
        widgets.add("clearChildren", clearChildrenButton);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        abortButton.visible = menu.busy;
        abortButton.active = menu.busy;
        clearChildrenButton.active = menu.childCount > 0;
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY,
                       int mouseX, int mouseY, float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        for (Slot slot : menu.slots) {
            Icon.SLOT_BACKGROUND.getBlitter()
                    .dest(offsetX + slot.x - 1, offsetY + slot.y - 1)
                    .blit(graphics);
        }
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        Component status = menu.busy
                ? Component.translatable("gui.sequenced_pattern_provider.master.running",
                        menu.activeJobCount, menu.maxActiveJobCount)
                : Component.translatable("gui.sequenced_pattern_provider.master.idle");
        graphics.drawString(font, status, 8, 68, 0x404040, false);
        graphics.drawString(font, Component.translatable(
                "gui.sequenced_pattern_provider.master.children", menu.childCount), 8, 80, 0x404040, false);
        graphics.drawString(font, Component.translatable(
                "gui.sequenced_pattern_provider.master.pattern_count", menu.patternCount), 118, 34,
                ChatFormatting.DARK_GRAY.getColor(), false);
    }
}
