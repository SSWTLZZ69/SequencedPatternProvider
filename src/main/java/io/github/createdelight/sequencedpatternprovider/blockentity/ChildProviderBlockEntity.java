package io.github.createdelight.sequencedpatternprovider.blockentity;

import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.block.crafting.PushDirection;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.helpers.patternprovider.PatternProviderTarget;
import com.mojang.logging.LogUtils;
import appeng.me.helpers.MachineSource;
import io.github.createdelight.sequencedpatternprovider.ModRegistry;
import io.github.createdelight.sequencedpatternprovider.block.ChildProviderBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class ChildProviderBlockEntity extends AENetworkedBlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ItemStackHandler outboundItems = new ItemStackHandler(9) {
        @Override
        protected void onContentsChanged(int slot) {
            saveChanges();
        }
    };
    private final ItemStackHandler inboundItems = new ItemStackHandler(9) {
        @Override
        protected void onContentsChanged(int slot) {
            saveChanges();
        }
    };
    private final FluidTank outboundFluid = new FluidTank(16_000, stack -> true) {
        @Override
        protected void onContentsChanged() {
            saveChanges();
        }
    };
    private final FluidTank inboundFluid = new FluidTank(16_000, stack -> true) {
        @Override
        protected void onContentsChanged() {
            saveChanges();
        }
    };

    private final IItemHandler inboundItemCapability = new InsertOnlyItemHandler(inboundItems);
    private final IFluidHandler inboundFluidCapability = new FillOnlyFluidHandler(inboundFluid);
    private final IActionSource actionSource;

    private Component customName;
    private final Set<ResourceLocation> supportedMachines = new LinkedHashSet<>();
    private final Set<BlockPos> linkedMasters = new LinkedHashSet<>();
    private boolean blockingMode;
    private LockCraftingMode lockCraftingMode = LockCraftingMode.NONE;
    private boolean redstonePowered;
    private boolean redstoneStateInitialized;
    private boolean pulseLocked;
    private boolean pulseArmed;
    private @Nullable String unlockToken;
    private @Nullable GenericStack unlockStack;
    private @Nullable Direction activeOutputSide;
    private @Nullable Direction lastAcceptedOutputSide;
    private int nextOutputSideIndex;

    public ChildProviderBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.CHILD_PROVIDER_BE.get(), pos, state);
        getMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(1.0)
                .setVisualRepresentation(ModRegistry.CHILD_PROVIDER_ITEM.get());
        actionSource = new MachineSource(getMainNode()::getNode);
    }

    public void setCustomName(Component customName) {
        setName(customName.getString());
    }

    @Override
    public void setName(String name) {
        super.setName(name);
        this.customName = name == null || name.isBlank() ? null : Component.literal(name);
        invalidateLinkedMasterCaches();
        saveChanges();
        markForUpdate();
    }

    public Component statusMessage() {
        String capabilities = supportedMachines.isEmpty() ? "-" : supportedMachines.stream()
                .map(ResourceLocation::toString).collect(Collectors.joining(", "));
        return Component.translatable("message.sequenced_pattern_provider.child.status", displayName(), capabilities,
                getMainNode().isActive() ? "ON" : "OFF");
    }

    public Component displayName() {
        return customName == null ? Component.translatable("block.sequenced_pattern_provider.child_pattern_provider") : customName;
    }

    @Override
    public Component getName() {
        return displayName();
    }

    @Override
    public @Nullable Component getCustomName() {
        return customName;
    }

    /**
     * Records one master that may dispatch through this shared child.
     * The child is many-to-many; this is coordination metadata, not ownership.
     */
    public boolean addLinkedMaster(BlockPos masterPos) {
        boolean changed = linkedMasters.add(masterPos.immutable());
        if (changed) {
            invalidateLinkedMasterCaches();
            saveChanges();
            markForUpdate();
        }
        return changed;
    }

    public boolean removeLinkedMaster(BlockPos masterPos) {
        boolean changed = linkedMasters.remove(masterPos);
        if (changed) {
            invalidateLinkedMasterCaches();
            saveChanges();
            markForUpdate();
        }
        return changed;
    }

    public Set<BlockPos> getLinkedMasters() {
        pruneMissingMasters();
        return Set.copyOf(linkedMasters);
    }

    public boolean hasMultipleLinkedMasters() {
        pruneMissingMasters();
        return linkedMasters.size() > 1;
    }

    /** Invalidates cached shared-child state in every loaded linked master. */
    public void invalidateLinkedMasterCaches() {
        if (level == null) return;
        for (BlockPos masterPos : linkedMasters) {
            if (level.getBlockEntity(masterPos) instanceof MasterProviderBlockEntity master) {
                master.onLinkedChildConfigurationChanged(worldPosition);
            }
        }
    }

    private void pruneMissingMasters() {
        if (level == null) return;
        if (linkedMasters.removeIf(pos -> level.isLoaded(pos)
                && !(level.getBlockEntity(pos) instanceof MasterProviderBlockEntity))) {
            invalidateLinkedMasterCaches();
            saveChanges();
            markForUpdate();
        }
    }

    public boolean addSupportedMachine(ResourceLocation id) {
        boolean changed = supportedMachines.add(id);
        if (changed) {
            invalidateLinkedMasterCaches();
            saveChanges();
        }
        return changed;
    }

    public void clearSupportedMachines() {
        supportedMachines.clear();
        invalidateLinkedMasterCaches();
        saveChanges();
    }

    public boolean isBlockingMode() {
        return blockingMode;
    }

    public void toggleBlockingMode() {
        blockingMode = !blockingMode;
        saveChanges();
    }

    public LockCraftingMode getLockCraftingMode() {
        return lockCraftingMode;
    }

    public LockCraftingMode getCraftingLockedReason() {
        if (lockCraftingMode == LockCraftingMode.LOCK_WHILE_LOW && !redstonePowered) {
            return LockCraftingMode.LOCK_WHILE_LOW;
        }
        if (lockCraftingMode == LockCraftingMode.LOCK_WHILE_HIGH && redstonePowered) {
            return LockCraftingMode.LOCK_WHILE_HIGH;
        }
        if (lockCraftingMode == LockCraftingMode.LOCK_UNTIL_PULSE && pulseLocked) {
            return LockCraftingMode.LOCK_UNTIL_PULSE;
        }
        if (lockCraftingMode == LockCraftingMode.LOCK_UNTIL_RESULT && unlockToken != null) {
            return LockCraftingMode.LOCK_UNTIL_RESULT;
        }
        return LockCraftingMode.NONE;
    }

    public @Nullable GenericStack getUnlockStack() {
        return unlockStack;
    }

    public void cycleLockCraftingMode(boolean reverse) {
        LockCraftingMode[] values = LockCraftingMode.values();
        int direction = reverse ? -1 : 1;
        lockCraftingMode = values[Math.floorMod(lockCraftingMode.ordinal() + direction, values.length)];
        resetCraftingLock();
        saveChanges();
    }

    private boolean isCraftingLocked() {
        return getCraftingLockedReason() != LockCraftingMode.NONE;
    }

    private void resetCraftingLock() {
        pulseLocked = false;
        pulseArmed = false;
        unlockToken = null;
        unlockStack = null;
    }

    private void onBatchAccepted(String token, GenericStack expectedResult) {
        resetCraftingLock();
        if (lockCraftingMode == LockCraftingMode.LOCK_UNTIL_PULSE) {
            pulseLocked = true;
            pulseArmed = !redstonePowered;
        } else if (lockCraftingMode == LockCraftingMode.LOCK_UNTIL_RESULT) {
            unlockToken = token;
            unlockStack = expectedResult;
        }
    }

    public void unlockResult(String token, long amount) {
        if (lockCraftingMode != LockCraftingMode.LOCK_UNTIL_RESULT
                || unlockToken == null || !unlockToken.equals(token) || unlockStack == null || amount <= 0) {
            return;
        }
        long remaining = unlockStack.amount() - amount;
        if (remaining <= 0) {
            resetCraftingLock();
        } else {
            unlockStack = new GenericStack(unlockStack.what(), remaining);
        }
        saveChanges();
    }

    public boolean isWaitingForResult(String token) {
        return lockCraftingMode == LockCraftingMode.LOCK_UNTIL_RESULT
                && unlockToken != null && unlockToken.equals(token);
    }

    private void updateRedstoneState() {
        if (level == null) return;
        boolean powered = level.hasNeighborSignal(worldPosition);
        if (!redstoneStateInitialized) {
            redstonePowered = powered;
            redstoneStateInitialized = true;
            if (pulseLocked && !powered) pulseArmed = true;
            return;
        }
        if (pulseLocked) {
            if (!powered) {
                pulseArmed = true;
            } else if (!redstonePowered && pulseArmed) {
                resetCraftingLock();
                saveChanges();
            }
        }
        redstonePowered = powered;
    }

    public int getSupportedMachineCount() {
        return supportedMachines.size();
    }

    public String getSupportedMachineSummary() {
        if (supportedMachines.isEmpty()) return "-";
        return new TreeSet<>(supportedMachines.stream().map(ResourceLocation::toString).toList()).stream()
                .collect(Collectors.joining(", "));
    }

    public boolean hasOutputTarget() {
        return findOutputTarget() != null;
    }

    public int getInboundItemCount() {
        return countItems(inboundItems);
    }

    public int getOutboundItemCount() {
        return countItems(outboundItems);
    }

    public int getInboundFluidAmount() {
        return inboundFluid.getFluidAmount();
    }

    public int getOutboundFluidAmount() {
        return outboundFluid.getFluidAmount();
    }

    public boolean supportsAny(Set<ResourceLocation> routeKeys) {
        if (routeKeys.stream().anyMatch(supportedMachines::contains)) return true;
        if (customName == null) return false;
        String name = customName.getString().trim();
        if (name.equals("*")) return true;
        ResourceLocation namedRoute = ResourceLocation.tryParse(name);
        return namedRoute != null && routeKeys.contains(namedRoute);
    }

    public boolean canAccept(List<GenericStack> stacks, Set<AEKey> blockingInputTypes) {
        return findAcceptingOutputTarget(stacks, blockingInputTypes) != null;
    }

    private @Nullable OutputTarget findAcceptingOutputTarget(List<GenericStack> stacks,
                                                               Set<AEKey> blockingInputTypes) {
        if (isCraftingLocked() || hasPendingOutput()) return null;
        for (OutputTarget outputTarget : findOutputTargets()) {
            if (canAcceptIntoTarget(outputTarget.target(), stacks, blockingInputTypes)) {
                return outputTarget;
            }
        }
        return null;
    }

    private boolean canAcceptIntoTarget(PatternProviderTarget target, List<GenericStack> stacks,
                                        Set<AEKey> blockingInputTypes) {
        if (blockingMode) {
            Set<AEKey> inputTypes = new LinkedHashSet<>(blockingInputTypes);
            inputTypes.addAll(stacks.stream()
                    .filter(stack -> stack != null && stack.amount() > 0)
                    .map(GenericStack::what)
                    .map(AEKey::dropSecondary)
                    .collect(Collectors.toSet()));
            if (target.containsPatternInput(inputTypes)) return false;
        }

        ItemStackHandler itemSimulation = new ItemStackHandler(9);
        itemSimulation.deserializeNBT(level.registryAccess(), outboundItems.serializeNBT(level.registryAccess()));
        FluidTank fluidSimulation = new FluidTank(outboundFluid.getCapacity());
        fluidSimulation.readFromNBT(level.registryAccess(), outboundFluid.writeToNBT(level.registryAccess(), new CompoundTag()));

        for (GenericStack stack : stacks) {
            if (stack == null || stack.amount() <= 0
                    || target.insert(stack.what(), stack.amount(), Actionable.SIMULATE) <= 0) {
                return false;
            }
            if (stack.what() instanceof AEItemKey itemKey) {
                ItemStack remaining = itemKey.toStack(safeInt(stack.amount()));
                for (int slot = 0; slot < itemSimulation.getSlots() && !remaining.isEmpty(); slot++) {
                    remaining = itemSimulation.insertItem(slot, remaining, false);
                }
                if (!remaining.isEmpty()) return false;
            } else if (stack.what() instanceof AEFluidKey fluidKey) {
                FluidStack fluid = fluidKey.toStack(safeInt(stack.amount()));
                if (fluidSimulation.fill(fluid, IFluidHandler.FluidAction.EXECUTE) != fluid.getAmount()) return false;
            } else {
                return false;
            }
        }
        return true;
    }

    public boolean accept(List<GenericStack> stacks, Set<AEKey> blockingInputTypes,
                          String unlockToken, GenericStack expectedResult) {
        OutputTarget outputTarget = findAcceptingOutputTarget(stacks, blockingInputTypes);
        if (outputTarget == null) return false;
        activeOutputSide = outputTarget.side();
        lastAcceptedOutputSide = outputTarget.side();
        for (GenericStack stack : stacks) {
            if (stack.what() instanceof AEItemKey itemKey) {
                ItemStack remaining = itemKey.toStack(safeInt(stack.amount()));
                for (int slot = 0; slot < outboundItems.getSlots() && !remaining.isEmpty(); slot++) {
                    remaining = outboundItems.insertItem(slot, remaining, false);
                }
            } else if (stack.what() instanceof AEFluidKey fluidKey) {
                outboundFluid.fill(fluidKey.toStack(safeInt(stack.amount())), IFluidHandler.FluidAction.EXECUTE);
            }
        }
        onBatchAccepted(unlockToken, expectedResult);
        LOGGER.info("SPP child accepted batch at {}: side={}, stacks={}, outboundItems={}, outboundFluid={}, "
                        + "expectedResult={}",
                worldPosition, activeOutputSide, stacks, getOutboundItemCount(), getOutboundFluidAmount(),
                expectedResult);
        pushPendingOutput();
        saveChanges();
        return true;
    }

    public @Nullable Direction getLastAcceptedOutputSide() {
        return lastAcceptedOutputSide;
    }

    public boolean isOutputClear(@Nullable Direction side, Set<AEKey> outputTypes) {
        if (hasPendingOutput()) return false;
        if (side != null) {
            PatternProviderTarget target = getTarget(side);
            return target != null && !target.containsPatternInput(outputTypes);
        }
        // Old saved jobs did not record the output side. Check all possible
        // destinations conservatively until those jobs finish.
        List<OutputTarget> targets = findOutputTargets();
        return !targets.isEmpty()
                && targets.stream().noneMatch(target -> target.target().containsPatternInput(outputTypes));
    }

    public void serverTick() {
        if (level == null) return;
        pruneMissingMasters();
        updateRedstoneState();
        if (!getMainNode().isActive()) return;
        pushPendingOutput();

        IGrid grid = getMainNode().getGrid();
        if (grid == null) return;
        MEStorage storage = grid.getStorageService().getInventory();
        IActionSource source = IActionSource.empty();

        for (int slot = 0; slot < inboundItems.getSlots(); slot++) {
            ItemStack stack = inboundItems.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            long inserted = storage.insert(AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE, source);
            if (inserted > 0) inboundItems.extractItem(slot, safeInt(inserted), false);
        }

        FluidStack fluid = inboundFluid.getFluid();
        if (!fluid.isEmpty()) {
            long inserted = storage.insert(AEFluidKey.of(fluid), fluid.getAmount(), Actionable.MODULATE, source);
            if (inserted > 0) inboundFluid.drain(safeInt(inserted), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private boolean hasPendingOutput() {
        if (!outboundFluid.isEmpty()) return true;
        for (int slot = 0; slot < outboundItems.getSlots(); slot++) {
            if (!outboundItems.getStackInSlot(slot).isEmpty()) return true;
        }
        return false;
    }

    private @Nullable OutputTarget findOutputTarget() {
        List<OutputTarget> targets = findOutputTargets();
        return targets.isEmpty() ? null : targets.get(0);
    }

    private List<OutputTarget> findOutputTargets() {
        if (level == null || level.isClientSide) return List.of();
        Direction configuredSide = getConfiguredOutputSide();
        if (configuredSide != null) {
            PatternProviderTarget target = getTarget(configuredSide);
            return target == null ? List.of() : List.of(new OutputTarget(configuredSide, target));
        }

        if (activeOutputSide != null) {
            PatternProviderTarget target = getTarget(activeOutputSide);
            if (target != null) return List.of(new OutputTarget(activeOutputSide, target));
        }

        List<OutputTarget> targets = new ArrayList<>(Direction.values().length);
        Direction[] directions = Direction.values();
        for (int offset = 0; offset < directions.length; offset++) {
            Direction side = directions[Math.floorMod(nextOutputSideIndex + offset, directions.length)];
            PatternProviderTarget target = getTarget(side);
            if (target != null) targets.add(new OutputTarget(side, target));
        }
        return targets;
    }

    private @Nullable PatternProviderTarget getTarget(Direction outputSide) {
        if (level == null) return null;
        BlockPos targetPos = worldPosition.relative(outputSide);
        return PatternProviderTarget.get(level, targetPos, level.getBlockEntity(targetPos),
                outputSide.getOpposite(), actionSource);
    }

    private boolean pushPendingOutput() {
        if (!hasPendingOutput()) {
            activeOutputSide = null;
            return false;
        }
        OutputTarget outputTarget = findOutputTarget();
        if (outputTarget == null) return false;
        activeOutputSide = outputTarget.side();
        PatternProviderTarget target = outputTarget.target();

        boolean changed = false;
        for (int slot = 0; slot < outboundItems.getSlots(); slot++) {
            ItemStack stack = outboundItems.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            long inserted = target.insert(AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE);
            if (inserted > 0) {
                LOGGER.info("SPP child pushed item at {}: side={}, stack={}, inserted={}, remaining={}",
                        worldPosition, activeOutputSide, stack, inserted, stack.getCount() - inserted);
                outboundItems.extractItem(slot, safeInt(inserted), false);
                changed = true;
            }
        }

        FluidStack fluid = outboundFluid.getFluid();
        if (!fluid.isEmpty()) {
            long inserted = target.insert(AEFluidKey.of(fluid), fluid.getAmount(), Actionable.MODULATE);
            if (inserted > 0) {
                LOGGER.info("SPP child pushed fluid at {}: side={}, stack={}, inserted={}, remaining={}",
                        worldPosition, activeOutputSide, fluid, inserted, fluid.getAmount() - inserted);
                outboundFluid.drain(safeInt(inserted), IFluidHandler.FluidAction.EXECUTE);
                changed = true;
            }
        }
        if (!hasPendingOutput()) {
            finishOutputBatch(outputTarget.side());
        }
        return changed;
    }

    private void finishOutputBatch(Direction outputSide) {
        if (getConfiguredOutputSide() == null) {
            nextOutputSideIndex = Math.floorMod(outputSide.ordinal() + 1, Direction.values().length);
        }
        activeOutputSide = null;
        saveChanges();
    }

    public void dropStoredItems() {
        if (level == null || level.isClientSide) return;
        for (ItemStackHandler handler : List.of(outboundItems, inboundItems)) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.extractItem(slot, Integer.MAX_VALUE, false);
                if (!stack.isEmpty()) Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            }
        }
    }

    private @Nullable Direction getConfiguredOutputSide() {
        PushDirection pushDirection = getBlockState().hasProperty(ChildProviderBlock.PUSH_DIRECTION)
                ? getBlockState().getValue(ChildProviderBlock.PUSH_DIRECTION)
                : PushDirection.ALL;
        return pushDirection.getDirection();
    }

    public void onPushDirectionChanged() {
        activeOutputSide = null;
        saveChanges();
    }

    public CompoundTag exportMemoryCardSettings() {
        CompoundTag tag = new CompoundTag();
        ListTag capabilities = new ListTag();
        supportedMachines.forEach(id -> capabilities.add(StringTag.valueOf(id.toString())));
        tag.put("SupportedMachines", capabilities);
        tag.putBoolean("BlockingMode", blockingMode);
        tag.putString("LockCraftingMode", lockCraftingMode.name());
        PushDirection pushDirection = getBlockState().hasProperty(ChildProviderBlock.PUSH_DIRECTION)
                ? getBlockState().getValue(ChildProviderBlock.PUSH_DIRECTION)
                : PushDirection.ALL;
        tag.putString("PushDirection", pushDirection.name());
        if (customName != null) {
            tag.putString("CustomName", Component.Serializer.toJson(customName, level.registryAccess()));
        }
        return tag;
    }

    public void importMemoryCardSettings(CompoundTag tag) {
        supportedMachines.clear();
        ListTag capabilities = tag.getList("SupportedMachines", Tag.TAG_STRING);
        capabilities.forEach(value -> {
            ResourceLocation id = ResourceLocation.tryParse(value.getAsString());
            if (id != null) supportedMachines.add(id);
        });
        blockingMode = tag.getBoolean("BlockingMode");
        try {
            lockCraftingMode = LockCraftingMode.valueOf(tag.getString("LockCraftingMode"));
        } catch (IllegalArgumentException ignored) {
            lockCraftingMode = LockCraftingMode.NONE;
        }
        resetCraftingLock();
        if (tag.contains("CustomName", Tag.TAG_STRING)) {
            Component importedName = Component.Serializer.fromJson(tag.getString("CustomName"), level.registryAccess());
            setName(importedName == null ? "" : importedName.getString());
        } else {
            setName("");
        }

        PushDirection pushDirection;
        try {
            pushDirection = PushDirection.valueOf(tag.getString("PushDirection"));
        } catch (IllegalArgumentException ignored) {
            pushDirection = PushDirection.ALL;
        }
        if (level != null && getBlockState().hasProperty(ChildProviderBlock.PUSH_DIRECTION)) {
            level.setBlockAndUpdate(worldPosition,
                    getBlockState().setValue(ChildProviderBlock.PUSH_DIRECTION, pushDirection));
        }
        redstoneStateInitialized = false;
        onPushDirectionChanged();
        invalidateLinkedMasterCaches();
        markForUpdate();
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        Direction outputSide = getConfiguredOutputSide();
        return direction == outputSide ? AECableType.NONE : AECableType.SMART;
    }

    public IItemHandler inboundItemHandler() { return inboundItemCapability; }
    public IFluidHandler inboundFluidHandler() { return inboundFluidCapability; }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("OutboundItems", outboundItems.serializeNBT(registries));
        tag.put("InboundItems", inboundItems.serializeNBT(registries));
        tag.put("OutboundFluid", outboundFluid.writeToNBT(registries, new CompoundTag()));
        tag.put("InboundFluid", inboundFluid.writeToNBT(registries, new CompoundTag()));
        ListTag capabilities = new ListTag();
        supportedMachines.forEach(id -> capabilities.add(StringTag.valueOf(id.toString())));
        tag.put("SupportedMachines", capabilities);
        ListTag masterList = new ListTag();
        linkedMasters.forEach(pos -> masterList.add(LongTag.valueOf(pos.asLong())));
        tag.put("LinkedMasters", masterList);
        tag.putBoolean("BlockingMode", blockingMode);
        tag.putString("LockCraftingMode", lockCraftingMode.name());
        tag.putBoolean("PulseLocked", pulseLocked);
        tag.putBoolean("PulseArmed", pulseArmed);
        if (unlockToken != null) tag.putString("UnlockToken", unlockToken);
        if (unlockStack != null) tag.put("UnlockStack", GenericStack.writeTag(registries, unlockStack));
        if (activeOutputSide != null) tag.putString("ActiveOutputSide", activeOutputSide.getName());
        tag.putInt("NextOutputSideIndex", nextOutputSideIndex);
        if (customName != null) tag.putString("CustomName", Component.Serializer.toJson(customName, registries));
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        outboundItems.deserializeNBT(registries, tag.getCompound("OutboundItems"));
        inboundItems.deserializeNBT(registries, tag.getCompound("InboundItems"));
        outboundFluid.readFromNBT(registries, tag.getCompound("OutboundFluid"));
        inboundFluid.readFromNBT(registries, tag.getCompound("InboundFluid"));
        supportedMachines.clear();
        ListTag capabilities = tag.getList("SupportedMachines", Tag.TAG_STRING);
        capabilities.forEach(value -> {
            ResourceLocation id = ResourceLocation.tryParse(value.getAsString());
            if (id != null) supportedMachines.add(id);
        });
        linkedMasters.clear();
        ListTag masterList = tag.getList("LinkedMasters", Tag.TAG_LONG);
        masterList.forEach(value -> linkedMasters.add(BlockPos.of(((LongTag) value).getAsLong())));
        blockingMode = tag.getBoolean("BlockingMode");
        try {
            lockCraftingMode = LockCraftingMode.valueOf(tag.getString("LockCraftingMode"));
        } catch (IllegalArgumentException ignored) {
            lockCraftingMode = LockCraftingMode.NONE;
        }
        pulseLocked = tag.getBoolean("PulseLocked");
        pulseArmed = tag.getBoolean("PulseArmed");
        unlockToken = tag.contains("UnlockToken", Tag.TAG_STRING) ? tag.getString("UnlockToken") : null;
        unlockStack = tag.contains("UnlockStack", Tag.TAG_COMPOUND)
                ? GenericStack.readTag(registries, tag.getCompound("UnlockStack")) : null;
        activeOutputSide = tag.contains("ActiveOutputSide", Tag.TAG_STRING)
                ? Direction.byName(tag.getString("ActiveOutputSide")) : null;
        nextOutputSideIndex = Math.floorMod(tag.getInt("NextOutputSideIndex"), Direction.values().length);
        redstoneStateInitialized = false;
        if (tag.contains("CustomName", Tag.TAG_STRING)) {
            customName = Component.Serializer.fromJson(tag.getString("CustomName"), registries);
        } else {
            Component inheritedName = super.getCustomName();
            customName = inheritedName == null || inheritedName.getString().isBlank() ? null : inheritedName;
        }
    }

    private static int countItems(ItemStackHandler handler) {
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            count += handler.getStackInSlot(slot).getCount();
        }
        return count;
    }

    private static int safeInt(long amount) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, amount));
    }

    private record OutputTarget(Direction side, PatternProviderTarget target) {
    }

    private record InsertOnlyItemHandler(IItemHandler delegate) implements IItemHandler {
        public int getSlots() { return delegate.getSlots(); }
        public @NotNull ItemStack getStackInSlot(int slot) { return delegate.getStackInSlot(slot); }
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) { return delegate.insertItem(slot, stack, simulate); }
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        public int getSlotLimit(int slot) { return delegate.getSlotLimit(slot); }
        public boolean isItemValid(int slot, @NotNull ItemStack stack) { return delegate.isItemValid(slot, stack); }
    }

    private record FillOnlyFluidHandler(IFluidHandler delegate) implements IFluidHandler {
        public int getTanks() { return delegate.getTanks(); }
        public @NotNull FluidStack getFluidInTank(int tank) { return delegate.getFluidInTank(tank); }
        public int getTankCapacity(int tank) { return delegate.getTankCapacity(tank); }
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) { return delegate.isFluidValid(tank, stack); }
        public int fill(FluidStack resource, FluidAction action) { return delegate.fill(resource, action); }
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
    }
}
