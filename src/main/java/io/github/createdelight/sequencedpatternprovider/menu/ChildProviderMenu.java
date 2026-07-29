package io.github.createdelight.sequencedpatternprovider.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.api.config.LockCraftingMode;
import appeng.api.stacks.GenericStack;
import io.github.createdelight.sequencedpatternprovider.ModRegistry;
import io.github.createdelight.sequencedpatternprovider.blockentity.ChildProviderBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

public final class ChildProviderMenu extends AEBaseMenu {
    private static final String ACTION_TOGGLE_BLOCKING = "toggleBlocking";
    private static final String ACTION_CLEAR_MACHINES = "clearMachines";
    private static final String ACTION_NEXT_LOCK_MODE = "nextLockMode";
    private static final String ACTION_PREVIOUS_LOCK_MODE = "previousLockMode";

    private final ChildProviderBlockEntity child;

    @GuiSync(0)
    public boolean blockingMode;
    @GuiSync(1)
    public boolean networkActive;
    @GuiSync(2)
    public boolean outputTargetAvailable;
    @GuiSync(3)
    public int supportedMachineCount;
    @GuiSync(4)
    public int inboundItemCount;
    @GuiSync(5)
    public int outboundItemCount;
    @GuiSync(6)
    public int inboundFluidAmount;
    @GuiSync(7)
    public int outboundFluidAmount;
    @GuiSync(8)
    public String supportedMachineSummary = "-";
    @GuiSync(9)
    public LockCraftingMode lockCraftingMode = LockCraftingMode.NONE;
    @GuiSync(10)
    public LockCraftingMode craftingLockedReason = LockCraftingMode.NONE;
    @GuiSync(11)
    public GenericStack unlockStack;

    public ChildProviderMenu(int id, Inventory inventory, ChildProviderBlockEntity child) {
        super(ModRegistry.CHILD_PROVIDER_MENU.get(), id, inventory, child);
        this.child = child;
        createPlayerInventorySlots(inventory);
        registerClientAction(ACTION_TOGGLE_BLOCKING, child::toggleBlockingMode);
        registerClientAction(ACTION_CLEAR_MACHINES, child::clearSupportedMachines);
        registerClientAction(ACTION_NEXT_LOCK_MODE, () -> child.cycleLockCraftingMode(false));
        registerClientAction(ACTION_PREVIOUS_LOCK_MODE, () -> child.cycleLockCraftingMode(true));
    }

    public static ChildProviderMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buffer) {
        var pos = buffer.readBlockPos();
        if (!(inventory.player.level().getBlockEntity(pos) instanceof ChildProviderBlockEntity child)) {
            throw new IllegalStateException("Sequence child provider is missing at " + pos);
        }
        return new ChildProviderMenu(id, inventory, child);
    }

    public void toggleBlockingMode() {
        if (isClientSide()) sendClientAction(ACTION_TOGGLE_BLOCKING);
        else child.toggleBlockingMode();
    }

    public void clearSupportedMachines() {
        if (isClientSide()) sendClientAction(ACTION_CLEAR_MACHINES);
        else child.clearSupportedMachines();
    }

    public void cycleLockCraftingMode(boolean reverse) {
        String action = reverse ? ACTION_PREVIOUS_LOCK_MODE : ACTION_NEXT_LOCK_MODE;
        if (isClientSide()) sendClientAction(action);
        else child.cycleLockCraftingMode(reverse);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            blockingMode = child.isBlockingMode();
            networkActive = child.getMainNode().isActive();
            outputTargetAvailable = child.hasOutputTarget();
            supportedMachineCount = child.getSupportedMachineCount();
            supportedMachineSummary = child.getSupportedMachineSummary();
            inboundItemCount = child.getInboundItemCount();
            outboundItemCount = child.getOutboundItemCount();
            inboundFluidAmount = child.getInboundFluidAmount();
            outboundFluidAmount = child.getOutboundFluidAmount();
            lockCraftingMode = child.getLockCraftingMode();
            craftingLockedReason = child.getCraftingLockedReason();
            unlockStack = child.getUnlockStack();
        }
        super.broadcastChanges();
    }
}
