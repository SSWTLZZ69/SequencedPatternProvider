package io.github.createdelight.sequencedpatternprovider.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import io.github.createdelight.sequencedpatternprovider.ModRegistry;
import io.github.createdelight.sequencedpatternprovider.blockentity.MasterProviderBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class MasterProviderMenu extends AEBaseMenu {
    private static final String ACTION_ABORT = "abortJob";
    private static final String ACTION_CLEAR_CHILDREN = "clearChildren";

    private final MasterProviderBlockEntity master;

    @GuiSync(0)
    public boolean busy;
    @GuiSync(1)
    public int activeJobCount;
    @GuiSync(2)
    public int childCount;
    @GuiSync(3)
    public int patternCount;
    @GuiSync(4)
    public int maxActiveJobCount;

    public MasterProviderMenu(int id, Inventory inventory, MasterProviderBlockEntity master) {
        super(ModRegistry.MASTER_PROVIDER_MENU.get(), id, inventory, master);
        this.master = master;

        for (int slot = 0; slot < master.getPatternInventory().getSlots(); slot++) {
            addSlot(new SlotItemHandler(master.getPatternInventory(), slot, 0, 0) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !MasterProviderMenu.this.master.hasActiveJobs() && super.mayPlace(stack);
                }

                @Override
                public boolean mayPickup(Player player) {
                    return !MasterProviderMenu.this.master.hasActiveJobs() && super.mayPickup(player);
                }
            }, SlotSemantics.ENCODED_PATTERN);
        }
        createPlayerInventorySlots(inventory);
        registerClientAction(ACTION_ABORT, this::abortJobServer);
        registerClientAction(ACTION_CLEAR_CHILDREN, this::clearChildrenServer);
    }

    public static MasterProviderMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buffer) {
        var pos = buffer.readBlockPos();
        if (!(inventory.player.level().getBlockEntity(pos) instanceof MasterProviderBlockEntity master)) {
            throw new IllegalStateException("Sequence master provider is missing at " + pos);
        }
        return new MasterProviderMenu(id, inventory, master);
    }

    public void abortJob() {
        if (isClientSide()) {
            sendClientAction(ACTION_ABORT);
        } else {
            abortJobServer();
        }
    }

    private void abortJobServer() {
        master.abortJob(getPlayer());
    }

    public void clearChildren() {
        if (isClientSide()) {
            sendClientAction(ACTION_CLEAR_CHILDREN);
        } else {
            clearChildrenServer();
        }
    }

    private void clearChildrenServer() {
        master.clearChildren();
        getPlayer().displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.sequenced_pattern_provider.link.master_connections_cleared"), true);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            busy = master.hasActiveJobs();
            activeJobCount = master.getActiveJobCount();
            maxActiveJobCount = master.getMaxActiveJobCount();
            childCount = master.getChildCount();
            patternCount = master.getPatternCount();
        }
        super.broadcastChanges();
    }
}
