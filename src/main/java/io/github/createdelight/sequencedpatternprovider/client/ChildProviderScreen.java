package io.github.createdelight.sequencedpatternprovider.client;

import appeng.api.config.Settings;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.YesNo;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.SettingToggleButton;
import io.github.createdelight.sequencedpatternprovider.menu.ChildProviderMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class ChildProviderScreen extends AEBaseScreen<ChildProviderMenu> {
    private final SettingToggleButton<YesNo> blockingModeButton;
    private final SettingToggleButton<LockCraftingMode> lockCraftingModeButton;
    private final Button clearMachinesButton;

    public ChildProviderScreen(ChildProviderMenu menu, Inventory inventory,
                               Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        blockingModeButton = new SettingToggleButton<>(Settings.BLOCKING_MODE, YesNo.NO,
                (button, rightClick) -> menu.toggleBlockingMode());
        addToLeftToolbar(blockingModeButton);

        lockCraftingModeButton = new SettingToggleButton<>(Settings.LOCK_CRAFTING_MODE,
                LockCraftingMode.NONE,
                (button, rightClick) -> menu.cycleLockCraftingMode(rightClick));
        addToLeftToolbar(lockCraftingModeButton);

        clearMachinesButton = Button.builder(Component.translatable(
                        "gui.sequenced_pattern_provider.child.clear"), button -> menu.clearSupportedMachines())
                .bounds(0, 0, 48, 18)
                .build();
        clearMachinesButton.setTooltip(Tooltip.create(Component.translatable(
                "tooltip.sequenced_pattern_provider.child.clear")));
        widgets.add("clearMachines", clearMachinesButton);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        blockingModeButton.set(menu.blockingMode ? YesNo.YES : YesNo.NO);
        lockCraftingModeButton.set(menu.lockCraftingMode);
        clearMachinesButton.active = menu.supportedMachineCount > 0;
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        graphics.drawString(font, Component.translatable(
                "gui.sequenced_pattern_provider.child.processing_types", menu.supportedMachineCount),
                8, 32, 0x404040, false);

        String summary = font.plainSubstrByWidth(menu.supportedMachineSummary, 156);
        graphics.drawString(font, summary, 8, 44, 0x606060, false);
        graphics.drawString(font, Component.translatable(
                        "gui.sequenced_pattern_provider.child.network",
                        Component.translatable(menu.networkActive
                                ? "gui.sequenced_pattern_provider.common.online"
                                : "gui.sequenced_pattern_provider.common.offline")),
                8, 58, 0x404040, false);
        graphics.drawString(font, Component.translatable(
                        "gui.sequenced_pattern_provider.child.front",
                        Component.translatable(menu.outputTargetAvailable
                                ? "gui.sequenced_pattern_provider.common.ready"
                                : "gui.sequenced_pattern_provider.common.missing")),
                8, 70, 0x404040, false);
        graphics.drawString(font, Component.translatable(
                        "gui.sequenced_pattern_provider.child.return_buffer",
                        menu.inboundItemCount, menu.inboundFluidAmount),
                8, 84, 0x404040, false);
        graphics.drawString(font, Component.translatable(
                        "gui.sequenced_pattern_provider.child.send_buffer",
                        menu.outboundItemCount, menu.outboundFluidAmount),
                8, 96, 0x404040, false);
        if (menu.craftingLockedReason != LockCraftingMode.NONE) {
            graphics.drawString(font, Component.translatable(
                            "gui.sequenced_pattern_provider.child.crafting_locked"),
                    8, 110, 0xB03030, false);
        }
    }
}
