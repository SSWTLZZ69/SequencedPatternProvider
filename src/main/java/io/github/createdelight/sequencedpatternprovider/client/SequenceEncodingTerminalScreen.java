package io.github.createdelight.sequencedpatternprovider.client;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.behaviors.EmptyingAction;
import appeng.api.config.ActionItems;
import appeng.client.gui.Icon;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.IconButton;
import appeng.api.stacks.GenericStack;
import net.neoforged.neoforge.network.PacketDistributor;
import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import io.github.createdelight.sequencedpatternprovider.menu.SequenceEncodingTerminalMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class SequenceEncodingTerminalScreen extends MEStorageScreen<SequenceEncodingTerminalMenu> {
    private static final int PANEL_BACKGROUND = 0xFF9A9FB4;
    private static final int PANEL_HIGHLIGHT = 0xFFF2F2F2;
    private static final int PANEL_SHADOW = 0xFF696D88;
    private static final int SECTION_HEADER_HEIGHT = 12;
    private final ScreenStyle encoderStyle;

    public SequenceEncodingTerminalScreen(SequenceEncodingTerminalMenu menu, Inventory inventory,
                                          Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        encoderStyle = style;
        setTextContent("dialog_title", Component.translatable(
                "gui.sequenced_pattern_provider.sequence_encoder"));

        ActionButton encodeButton = new ActionButton(ActionItems.ENCODE, menu::encode);
        widgets.add("encodePattern", encodeButton);

        widgets.add("decreaseLoops", iconButton(Icon.S_ARROW_DOWN,
                "tooltip.sequenced_pattern_provider.encoder.decrease_loops",
                () -> menu.changeLoops(-1)));
        widgets.add("increaseLoops", iconButton(Icon.S_ARROW_UP,
                "tooltip.sequenced_pattern_provider.encoder.increase_loops",
                () -> menu.changeLoops(1)));
        widgets.add("clearEncoding", iconButton(Icon.S_CLEAR,
                "tooltip.sequenced_pattern_provider.encoder.clear", menu::clearEncoding));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        // MEStorageScreen replaces dialog_title with the full item/custom name
        // every frame. Keep the compact heading inside the space before search.
        var bounds = new Rect2i(0, 0, imageWidth, imageHeight);
        int titleX = encoderStyle.getText().get("dialog_title").getPosition().resolve(bounds).getX();
        int searchX = encoderStyle.getWidget("search").resolve(bounds).getX();
        int maxWidth = Math.max(0, searchX - titleX - 4);
        String heading = Component.translatable("gui.sequenced_pattern_provider.sequence_encoder").getString();
        if (font.width(heading) > maxWidth) {
            String ellipsis = "…";
            heading = maxWidth >= font.width(ellipsis)
                    ? font.plainSubstrByWidth(heading, maxWidth - font.width(ellipsis)) + ellipsis : "";
        }
        setTextContent("dialog_title", Component.literal(heading));
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);

        // The style reserves 12 extra pixels before the stock bottom section.
        // Replace its prefixed texture rows with AE2's blank separator strip,
        // keeping the border and all bottom-anchored slots aligned at any row count.
        int bottomTop = offsetY + imageHeight - encoderStyle.getTerminalStyle().getBottom().getSrcHeight();
        Blitter.texture("guis/pattern.png").src(0, 72, 195, 1)
                .dest(offsetX, bottomTop, 195, SECTION_HEADER_HEIGHT).blit(graphics);

        // The stock pattern-terminal texture contains a three-row encoding panel. Replace just that
        // panel with a taller AE2-style recessed area before drawing our four configuration rows.
        int panelLeft = offsetX + 7;
        int panelTop = offsetY + imageHeight - 173;
        int panelRight = offsetX + 133;
        int panelBottom = offsetY + imageHeight - 95;
        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, PANEL_HIGHLIGHT);
        graphics.fill(panelLeft + 1, panelTop + 1, panelRight, panelBottom, PANEL_SHADOW);
        graphics.fill(panelLeft + 2, panelTop + 3, panelRight - 1, panelBottom - 1, PANEL_BACKGROUND);

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
                            newStack -> PacketDistributor.sendToServer(new InventoryActionPacket(
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
                137, imageHeight - 139, 0x404040);
    }

    private static IconButton iconButton(Icon icon, String tooltipKey, Runnable action) {
        IconButton button = new IconButton(ignored -> action.run()) {
            @Override
            protected Icon getIcon() {
                return icon;
            }
        };
        button.setHalfSize(true);
        button.setDisableBackground(true);
        button.setMessage(Component.translatable(tooltipKey));
        return button;
    }
}
