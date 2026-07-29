package io.github.createdelight.sequencedpatternprovider.client;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.behaviors.EmptyingAction;
import appeng.api.config.ActionItems;
import appeng.client.gui.Icon;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.api.stacks.GenericStack;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import io.github.createdelight.sequencedpatternprovider.menu.SequenceEncodingTerminalMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class SequenceEncodingTerminalScreen extends MEStorageScreen<SequenceEncodingTerminalMenu> {
    public SequenceEncodingTerminalScreen(SequenceEncodingTerminalMenu menu, Inventory inventory,
                                          Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        ActionButton encodeButton = new ActionButton(ActionItems.ENCODE, menu::encode);
        widgets.add("encodePattern", encodeButton);
    }

    @Override
    public void init() {
        super.init();
        int minusY = topPos + imageHeight - 168;
        int plusY = topPos + imageHeight - 150;
        Button decreaseLoops = Button.builder(Component.literal("-"), button -> menu.changeLoops(-1))
                .bounds(leftPos + 128, minusY, 12, 12).build();
        decreaseLoops.setTooltip(Tooltip.create(Component.translatable(
                "tooltip.sequenced_pattern_provider.encoder.decrease_loops")));
        addRenderableWidget(decreaseLoops);
        Button increaseLoops = Button.builder(Component.literal("+"), button -> menu.changeLoops(1))
                .bounds(leftPos + 128, plusY, 12, 12).build();
        increaseLoops.setTooltip(Tooltip.create(Component.translatable(
                "tooltip.sequenced_pattern_provider.encoder.increase_loops")));
        addRenderableWidget(increaseLoops);
        Button clearEncoding = Button.builder(Component.literal("×"), button -> menu.clearEncoding())
                .bounds(leftPos + 167, topPos + imageHeight - 142, 16, 16).build();
        clearEncoding.setTooltip(Tooltip.create(Component.translatable(
                "tooltip.sequenced_pattern_provider.encoder.clear")));
        addRenderableWidget(clearEncoding);
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        for (Slot slot : menu.getConfigurationSlots()) {
            Icon.SLOT_BACKGROUND.getBlitter()
                    .dest(offsetX + slot.x - 1, offsetY + slot.y - 1)
                    .blit(graphics);
        }
    }

    @Override
    protected EmptyingAction getEmptyingAction(Slot slot, ItemStack carried) {
        if (menu.isMaterialSlot(slot)) {
            EmptyingAction action = ContainerItemStrategies.getEmptyingAction(carried);
            if (action != null) return action;
        }
        return super.getEmptyingAction(slot, carried);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> tooltip = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        Component help = menu.getSlotHelp(hoveredSlot);
        if (help != null) {
            tooltip.add(Component.empty());
            tooltip.add(help.copy().withStyle(ChatFormatting.DARK_GRAY));
        }
        return tooltip;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (minecraft.options.keyPickItem.matchesMouse(button)) {
            Slot slot = hoveredSlot;
            if (menu.canModifyAmountForSlot(slot)) {
                GenericStack current = GenericStack.fromItemStack(slot.getItem());
                if (current != null) {
                    minecraft.setScreen(new SequenceAmountScreen(this, current,
                            newStack -> NetworkHandler.instance().sendToServer(new InventoryActionPacket(
                                    InventoryAction.SET_FILTER, slot.index,
                                    GenericStack.wrapInItemStack(newStack)))));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        graphics.drawCenteredString(font, Component.literal("×" + menu.loops),
                134, imageHeight - 130, 0x404040);
    }
}
