package io.github.createdelight.sequencedpatternprovider.blockentity;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkBlockEntity;
import com.mojang.logging.LogUtils;
import io.github.createdelight.sequencedpatternprovider.ModRegistry;
import io.github.createdelight.sequencedpatternprovider.SequencedPatternProviderConfig;
import io.github.createdelight.sequencedpatternprovider.item.SequencePatternItem;
import io.github.createdelight.sequencedpatternprovider.pattern.AssemblyStepDescriptor;
import io.github.createdelight.sequencedpatternprovider.pattern.SequencePatternDetails;
import io.github.createdelight.sequencedpatternprovider.probability.ProbabilityPlan;
import io.github.createdelight.sequencedpatternprovider.tracking.AttemptToken;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MasterProviderBlockEntity extends AENetworkBlockEntity implements ICraftingProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_PERSISTED_ACTIVE_JOBS = SequencedPatternProviderConfig.MAX_CONFIGURED_ACTIVE_JOBS;
    private final Map<ResourceLocation, SequencePatternDetails> patternDetailsCache = new HashMap<>();
    private final Map<AEItemKey, Long> pendingIntermediateArrivals = new HashMap<>();
    private final Map<AEItemKey, Long> observedIntermediateAmounts = new HashMap<>();
    private final Map<AEItemKey, Long> masterExtractionCredits = new HashMap<>();
    private final Set<UUID> watchedAttemptIds = new LinkedHashSet<>();
    private final Set<Item> trackedWorkpieceItems = new LinkedHashSet<>();
    private final Set<AEItemKey> loggedAmbiguousTokenlessReturns = new HashSet<>();
    private final Map<ProbabilityKey, Long> probabilityRemainders = new HashMap<>();
    private boolean inventoryTrackingDirty = true;
    private final ItemStackHandler patterns = new ItemStackHandler(9) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return SequencePatternItem.isEncoded(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            patternDetailsCache.clear();
            blockingInputsByChild.clear();
            ICraftingProvider.requestUpdate(getMainNode());
            saveChanges();
        }
    };
    private final List<BlockPos> children = new ArrayList<>();
    private final Map<Set<ResourceLocation>, Integer> nextChildIndexByRoute = new HashMap<>();
    private final Map<BlockPos, Set<AEKey>> blockingInputsByChild = new HashMap<>();
    private final List<ActiveJob> jobs = new ArrayList<>();
    private int patternRefreshTicks;

    public MasterProviderBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.MASTER_PROVIDER_BE.get(), pos, state);
        getMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(2.0)
                .setVisualRepresentation(ModRegistry.MASTER_PROVIDER_ITEM.get())
                .addService(ICraftingProvider.class, this);
    }

    @Override
    public void onReady() {
        super.onReady();
        ICraftingProvider.requestUpdate(getMainNode());
    }

    public boolean addChild(BlockPos pos) {
        if (level == null || !(level.getBlockEntity(pos) instanceof ChildProviderBlockEntity)) return false;
        if (children.contains(pos)) return false;
        children.add(pos.immutable());
        nextChildIndexByRoute.clear();
        blockingInputsByChild.clear();
        ICraftingProvider.requestUpdate(getMainNode());
        saveChanges();
        return true;
    }

    public void serverTick() {
        if (level == null || !getMainNode().isActive()) return;

        // Child routes can change independently after linking. Refresh the patterns
        // periodically so AE stops advertising recipes whose required route vanished.
        if (++patternRefreshTicks >= 20) {
            patternRefreshTicks = 0;
            patternDetailsCache.clear();
            blockingInputsByChild.clear();
            ICraftingProvider.requestUpdate(getMainNode());
        }

        if (jobs.isEmpty()) {
            pendingIntermediateArrivals.clear();
            clearInventoryTracking();
            return;
        }
        refreshInventoryTracking();
        reconcileNetworkInventoryLedger();
        boolean changed = processChangedNetworkItems();
        boolean providerAvailabilityChanged = false;
        for (int index = 0; index < jobs.size(); ) {
            ActiveJob job = jobs.get(index);
            SequencePatternDetails details = findPatternDetails(job.recipeId);
            if (details == null || !details.recipeId().equals(job.recipeId)) {
                releaseJobLock(job);
                returnUndispatchedInputs(job);
                jobs.remove(index);
                inventoryTrackingDirty = true;
                changed = true;
                providerAvailabilityChanged = true;
                continue;
            }

            if (!job.dispatched) {
                changed |= dispatchCurrentStep(job, details);
                index++;
                continue;
            }

            if (job.step < details.totalSteps() - 1) {
                index++;
                continue;
            }

            if (job.resultObserved) {
                LOGGER.info("SPP completed active job at {}: recipe={}, finalStep={}, "
                                + "resultObserved={}, child={}",
                        worldPosition, job.recipeId, job.step,
                        job.resultObserved, job.lastChildPos);
                jobs.remove(index);
                inventoryTrackingDirty = true;
                changed = true;
                providerAvailabilityChanged = true;
            } else index++;
        }
        if (providerAvailabilityChanged) ICraftingProvider.requestUpdate(getMainNode());
        refreshInventoryTracking();
        if (changed) saveChanges();
    }

    private boolean dispatchCurrentStep(ActiveJob job, SequencePatternDetails details) {
        AssemblyStepDescriptor descriptor = details.stepDescriptor(job.step);
        List<GenericStack> dispatch = new ArrayList<>();
        List<Integer> consumedIndices = new ArrayList<>();
        if (!job.workpiece.isEmpty()) {
            dispatch.add(GenericStack.fromItemStack(job.workpiece.copy()));
        }
        SequencePatternDetails.PlannedInput[] plan = details.plannedInputs();
        for (int i = 0; i < plan.length; i++) {
            if (!job.consumed[i] && plan[i].step() == job.step) {
                if (job.step == 0 && i == 0) {
                    GenericStack taggedWorkpiece = tagInitialWorkpiece(job, details, job.inputs.get(i));
                    if (taggedWorkpiece == null) return false;
                    dispatch.add(taggedWorkpiece);
                } else {
                    dispatch.addAll(job.inputs.get(i));
                }
                consumedIndices.add(i);
            }
        }
        if (dispatch.stream().anyMatch(stack -> stack == null)) return false;

        // Publish the expected return state before the child can send the batch.
        job.dispatched = true;
        inventoryTrackingDirty = true;
        if (!dispatchToAvailableChild(job, descriptor, dispatch)) {
            job.dispatched = false;
            inventoryTrackingDirty = true;
            return false;
        }

        consumedIndices.forEach(index -> job.consumed[index] = true);
        job.workpiece = ItemStack.EMPTY;
        inventoryTrackingDirty = true;
        return true;
    }

    private @Nullable GenericStack tagInitialWorkpiece(ActiveJob job, SequencePatternDetails details,
                                                        List<GenericStack> selected) {
        if (level == null || selected.size() != 1) return null;
        GenericStack input = selected.get(0);
        if (!(input.what() instanceof AEItemKey itemKey) || input.amount() != 1) return null;
        ItemStack tagged = itemKey.toStack(1);
        AttemptToken.write(tagged, level.dimension().location(), worldPosition, job.attemptId);
        if (!details.recipe().getIngredient().test(tagged)) return null;
        return GenericStack.fromItemStack(tagged);
    }

    private boolean dispatchToAvailableChild(ActiveJob job, AssemblyStepDescriptor descriptor,
                                             List<GenericStack> dispatch) {
        if (level == null) return false;
        removeMissingChildren();
        int childCount = children.size();
        if (childCount == 0) return false;

        Set<ResourceLocation> routeKeys = Set.copyOf(descriptor.routeKeys());
        SequencePatternDetails details = findPatternDetails(job.recipeId);
        if (details == null) return false;
        GenericStack expectedResult;
        if (job.step < details.totalSteps() - 1) {
            expectedResult = new GenericStack(AEItemKey.of(details.recipe().getTransitionalItem()), 1);
        } else if (!job.plannedOutput.isEmpty()) {
            expectedResult = GenericStack.fromItemStack(job.plannedOutput);
        } else {
            expectedResult = details.getPrimaryOutput();
        }
        boolean exclusiveChildLease = details.probabilityPlan().batchSize() > 1;
        String unlockToken = UUID.randomUUID().toString();
        int startIndex = Math.floorMod(nextChildIndexByRoute.getOrDefault(routeKeys, 0), childCount);
        for (int offset = 0; offset < childCount; offset++) {
            int index = (startIndex + offset) % childCount;
            BlockPos childPos = children.get(index);
            if (exclusiveChildLease && isChildReservedByAnotherJob(childPos, job)) continue;
            if (level.getBlockEntity(childPos) instanceof ChildProviderBlockEntity child
                    && child.getMainNode().isActive()
                    && child.supportsAny(routeKeys)
                    && child.accept(dispatch, getBlockingInputTypes(child), unlockToken, expectedResult)) {
                nextChildIndexByRoute.put(routeKeys, (index + 1) % childCount);
                job.lastChildPos = childPos;
                job.unlockToken = unlockToken;
                job.resultObserved = false;
                LOGGER.debug("SPP dispatched sequence step at {}: recipe={}, step={}, child={}, expectedResult={}",
                        worldPosition, job.recipeId, job.step, childPos, expectedResult.what());
                return true;
            }
        }
        return false;
    }

    private boolean isChildReservedByAnotherJob(BlockPos childPos, ActiveJob currentJob) {
        for (ActiveJob other : jobs) {
            if (other != currentJob && other.dispatched && childPos.equals(other.lastChildPos)) return true;
        }
        return false;
    }

    private Set<AEKey> getBlockingInputTypes(ChildProviderBlockEntity child) {
        return blockingInputsByChild.computeIfAbsent(child.getBlockPos(), ignored -> {
            Set<AEKey> result = new LinkedHashSet<>();
            for (int slot = 0; slot < patterns.getSlots(); slot++) {
                SequencePatternDetails details = SequencePatternDetails.fromStack(patterns.getStackInSlot(slot), level);
                if (details == null) continue;

                for (SequencePatternDetails.PlannedInput input : details.plannedInputs()) {
                    if (!child.supportsAny(details.stepDescriptor(input.step()).routeKeys())) continue;
                    for (GenericStack choice : input.choices()) {
                        if (choice != null) result.add(choice.what().dropSecondary());
                    }
                }

                AEItemKey transitional = AEItemKey.of(details.recipe().getTransitionalItem());
                if (transitional != null) {
                    for (int step = 1; step < details.totalSteps(); step++) {
                        if (child.supportsAny(details.stepDescriptor(step).routeKeys())) {
                            result.add(transitional.dropSecondary());
                            break;
                        }
                    }
                }
            }
            return Set.copyOf(result);
        });
    }

    private boolean hasMatchingChild(AssemblyStepDescriptor descriptor) {
        if (level == null) return false;
        removeMissingChildren();
        for (BlockPos pos : children) {
            if (level.getBlockEntity(pos) instanceof ChildProviderBlockEntity child
                    && child.getMainNode().isActive()
                    && child.supportsAny(descriptor.routeKeys())) return true;
        }
        return false;
    }

    private void removeMissingChildren() {
        if (level != null && children.removeIf(pos -> level.isLoaded(pos)
                && !(level.getBlockEntity(pos) instanceof ChildProviderBlockEntity))) {
            nextChildIndexByRoute.clear();
            blockingInputsByChild.clear();
        }
    }

    public void clearChildren() {
        children.clear();
        nextChildIndexByRoute.clear();
        blockingInputsByChild.clear();
        ICraftingProvider.requestUpdate(getMainNode());
        saveChanges();
    }

    public ItemStackHandler getPatternInventory() {
        return patterns;
    }

    public int getActiveJobCount() {
        return jobs.size();
    }

    public int getMaxActiveJobCount() {
        return SequencedPatternProviderConfig.maxActiveJobs();
    }

    public boolean hasActiveJobs() {
        return !jobs.isEmpty();
    }

    public int getChildCount() {
        return children.size();
    }

    public int getPatternCount() {
        int count = 0;
        for (int slot = 0; slot < patterns.getSlots(); slot++) {
            if (!patterns.getStackInSlot(slot).isEmpty()) count++;
        }
        return count;
    }

    private void recordPendingArrival(AEItemKey itemKey, long amount) {
        pendingIntermediateArrivals.merge(itemKey, amount, Long::sum);
    }

    /**
     * Compares the current AE cache with one local baseline. Extractions made by
     * this master are credited back into the delta, so extracting one workpiece
     * while another identical workpiece returns in the same tick still records
     * one arrival.
     */
    private void reconcileNetworkInventoryLedger() {
        IGrid grid = getMainNode().getGrid();
        if (grid == null) return;

        KeyCounter cached = grid.getStorageService().getCachedInventory();
        Set<AEItemKey> seenKeys = new LinkedHashSet<>();
        for (Item trackedItem : trackedWorkpieceItems) {
            AEItemKey fuzzyKey = AEItemKey.of(trackedItem);
            for (Object2LongMap.Entry<AEKey> entry : cached.findFuzzy(fuzzyKey, FuzzyMode.IGNORE_ALL)) {
                if (!(entry.getKey() instanceof AEItemKey itemKey)) continue;
                seenKeys.add(itemKey);
                reconcileLedgerKey(itemKey, entry.getLongValue());
            }
        }
        Set<AEItemKey> knownKeys = new LinkedHashSet<>(observedIntermediateAmounts.keySet());
        knownKeys.addAll(masterExtractionCredits.keySet());
        for (AEItemKey itemKey : knownKeys) {
            if (isTrackedItem(itemKey) && !seenKeys.contains(itemKey)) {
                reconcileLedgerKey(itemKey, 0);
            }
        }
    }

    private void reconcileLedgerKey(AEItemKey itemKey, long currentAmount) {
        long previousAmount = observedIntermediateAmounts.getOrDefault(itemKey, 0L);
        long masterExtracted = masterExtractionCredits.getOrDefault(itemKey, 0L);
        masterExtractionCredits.remove(itemKey);
        long netArrivals = currentAmount - previousAmount + masterExtracted;
        if (netArrivals > 0 && isWatchedItem(itemKey)) {
            recordPendingArrival(itemKey, netArrivals);
        } else if (netArrivals < 0) {
            // A player or another network consumer removed a previously observed
            // arrival before this master could claim it. Do not retain a phantom
            // pending event indefinitely.
            long removed = -netArrivals;
            pendingIntermediateArrivals.computeIfPresent(itemKey,
                    (ignored, pending) -> pending > removed ? pending - removed : null);
        }
        observedIntermediateAmounts.put(itemKey, currentAmount);
    }

    private void refreshInventoryTracking() {
        if (!inventoryTrackingDirty) return;
        inventoryTrackingDirty = false;

        Set<UUID> desiredAttemptIds = new LinkedHashSet<>();
        Set<Item> newlyTrackedWorkpieceItems = new LinkedHashSet<>();
        for (ActiveJob job : jobs) {
            SequencePatternDetails details = findPatternDetails(job.recipeId);
            if (details == null) continue;
            newlyTrackedWorkpieceItems.add(details.recipe().getTransitionalItem().getItem());
            AEItemKey initialWorkpiece = getInitialWorkpieceKey(job);
            if (initialWorkpiece != null) newlyTrackedWorkpieceItems.add(initialWorkpiece.getItem());
            if (!job.dispatched) continue;
            desiredAttemptIds.add(job.attemptId);
        }

        watchedAttemptIds.clear();
        watchedAttemptIds.addAll(desiredAttemptIds);
        pendingIntermediateArrivals.keySet().removeIf(key -> !isWatchedItem(key));
        loggedAmbiguousTokenlessReturns.retainAll(pendingIntermediateArrivals.keySet());

        if (jobs.isEmpty()) {
            clearInventoryTracking();
            return;
        }

        Set<Item> addedWorkpieceItems = new LinkedHashSet<>(newlyTrackedWorkpieceItems);
        addedWorkpieceItems.removeAll(trackedWorkpieceItems);
        trackedWorkpieceItems.clear();
        trackedWorkpieceItems.addAll(newlyTrackedWorkpieceItems);
        observedIntermediateAmounts.keySet().removeIf(key -> !isTrackedItem(key));
        masterExtractionCredits.keySet().removeIf(key -> !isTrackedItem(key));

        // Establish a baseline only for newly tracked recipe types. Never
        // rebuild it when a job merely advances a step, or a return inserted
        // earlier in the same tick could be absorbed into the baseline.
        if (addedWorkpieceItems.isEmpty()) return;
        IGrid grid = getMainNode().getGrid();
        if (grid != null) {
            KeyCounter availableStacks = grid.getStorageService().getCachedInventory();
            for (Object2LongMap.Entry<AEKey> entry : availableStacks) {
                if (entry.getKey() instanceof AEItemKey itemKey
                        && addedWorkpieceItems.contains(itemKey.getItem())) {
                    // Only an item carrying this master's exact attempt token
                    // can be distinguished from pre-existing network stock
                    // during baseline creation. Tokenless compatibility items
                    // are handled only when a later positive delta proves that
                    // they actually returned after dispatch.
                    if (AttemptToken.read(itemKey.toStack()) == null || !isWatchedItem(itemKey)) {
                        observedIntermediateAmounts.putIfAbsent(itemKey, entry.getLongValue());
                    }
                }
            }
        }
    }

    private void clearInventoryTracking() {
        watchedAttemptIds.clear();
        trackedWorkpieceItems.clear();
        observedIntermediateAmounts.clear();
        masterExtractionCredits.clear();
        pendingIntermediateArrivals.clear();
        loggedAmbiguousTokenlessReturns.clear();
        inventoryTrackingDirty = false;
    }

    private boolean isWatchedItem(AEItemKey itemKey) {
        if (level == null) return false;
        ItemStack stack = itemKey.toStack();
        AttemptToken.Address attempt = AttemptToken.read(stack);
        if (attempt != null) {
            return attempt.dimension().equals(level.dimension().location())
                    && attempt.masterPos().equals(worldPosition)
                    && watchedAttemptIds.contains(attempt.attemptId());
        }
        return !findTokenlessReturnCandidates(stack).isEmpty();
    }

    private boolean isTrackedItem(AEItemKey itemKey) {
        return trackedWorkpieceItems.contains(itemKey.getItem());
    }

    private boolean processChangedNetworkItems() {
        if (pendingIntermediateArrivals.isEmpty()) return false;
        Map<AEItemKey, Long> arrivals = Map.copyOf(pendingIntermediateArrivals);
        pendingIntermediateArrivals.clear();
        boolean changed = false;
        for (Map.Entry<AEItemKey, Long> arrival : arrivals.entrySet()) {
            long claimed = claimReturnedWorkpieces(arrival.getKey(), arrival.getValue());
            long remaining = arrival.getValue() - claimed;
            if (remaining > 0 && isWatchedItem(arrival.getKey())) {
                // The storage can report a result before it becomes extractable,
                // or before another concurrently-dispatched job reaches the
                // matching state. Keep the known arrival instead of losing it.
                pendingIntermediateArrivals.merge(arrival.getKey(), remaining, Long::sum);
            }
            changed |= claimed > 0;
        }
        return changed;
    }

    private long claimReturnedWorkpieces(AEItemKey itemKey, long arrivalCount) {
        IGrid grid = getMainNode().getGrid();
        if (grid == null || level == null || arrivalCount <= 0) return 0;
        ItemStack stack = itemKey.toStack();
        AttemptToken.Address attempt = AttemptToken.read(stack);
        ActiveJob matchedJob;
        boolean tokenlessFallback = false;
        if (attempt != null) {
            if (!attempt.dimension().equals(level.dimension().location())
                    || !attempt.masterPos().equals(worldPosition)) return 0;
            matchedJob = findJobByAttempt(attempt.attemptId());
            if (matchedJob == null || !matchesReturnedWorkpiece(stack, matchedJob)) return 0;
        } else {
            List<ActiveJob> candidates = findTokenlessReturnCandidates(stack);
            if (candidates.size() != 1) {
                if (candidates.size() > 1 && loggedAmbiguousTokenlessReturns.add(itemKey)) {
                    LOGGER.warn("SPP refused ambiguous tokenless intermediate at {}: item={}, candidates={}; "
                                    + "waiting for an exact attempt token instead of assigning the item to the wrong job",
                            worldPosition, itemKey, candidates.size());
                }
                return 0;
            }
            loggedAmbiguousTokenlessReturns.remove(itemKey);
            matchedJob = candidates.get(0);
            tokenlessFallback = true;
        }

        MEStorage storage = grid.getStorageService().getInventory();
        SequencePatternDetails details = findPatternDetails(matchedJob.recipeId);
        if (details == null) return 0;
        int returnedStep = getAssemblyStep(stack, matchedJob.recipeId,
                details.recipe().getTransitionalItem().getItem());
        boolean advanced = matchedJob.step < details.totalSteps() - 1 && returnedStep == matchedJob.step + 1;
        boolean unprocessed = returnedStep == matchedJob.step && matchedJob.step > 0;
        if (matchedJob.step == 0 && matchesInitialWorkpiece(stack, matchedJob, details)) unprocessed = true;

        long extracted = storage.extract(itemKey, 1, Actionable.MODULATE, IActionSource.empty());
        if (extracted != 1) return 0;
        masterExtractionCredits.merge(itemKey, extracted, Long::sum);
        ItemStack claimedStack = itemKey.toStack(1);
        if (tokenlessFallback) {
            AttemptToken.write(claimedStack, level.dimension().location(), worldPosition, matchedJob.attemptId);
        }

        if (advanced) {
            int previousStep = matchedJob.step;
            notifyChildUnlock(matchedJob, 1);
            advanceJobWithIntermediate(matchedJob, claimedStack, returnedStep);
            LOGGER.debug("SPP claimed returned intermediate at {}: recipe={}, step={} -> {}, "
                            + "child={}, remainingArrival={}, tokenlessFallback={}",
                    worldPosition, matchedJob.recipeId, previousStep, returnedStep,
                    matchedJob.lastChildPos, arrivalCount - 1, tokenlessFallback);
        } else if (unprocessed) {
            int retryStep = matchedJob.step;
            releaseJobLock(matchedJob);
            matchedJob.workpiece = claimedStack;
            matchedJob.dispatched = false;
            matchedJob.resultObserved = false;
            inventoryTrackingDirty = true;
            LOGGER.debug("SPP reclaimed unprocessed workpiece at {}: recipe={}, step={}, attempt={}, "
                            + "child={}, tokenlessFallback={}; retrying the same attempt",
                    worldPosition, matchedJob.recipeId, retryStep, matchedJob.attemptId,
                    matchedJob.lastChildPos, tokenlessFallback);
        }
        return 1;
    }

    private @Nullable ActiveJob findJobByAttempt(UUID attemptId) {
        for (ActiveJob job : jobs) {
            if (job.dispatched && job.attemptId.equals(attemptId)) return job;
        }
        return null;
    }

    private List<ActiveJob> findTokenlessReturnCandidates(ItemStack stack) {
        List<ActiveJob> candidates = new ArrayList<>();
        for (ActiveJob job : jobs) {
            if (matchesReturnedWorkpiece(stack, job)) candidates.add(job);
        }
        return candidates;
    }

    private boolean matchesReturnedWorkpiece(ItemStack stack, ActiveJob job) {
        if (!job.dispatched) return false;
        SequencePatternDetails details = findPatternDetails(job.recipeId);
        if (details == null) return false;
        int returnedStep = getAssemblyStep(stack, job.recipeId,
                details.recipe().getTransitionalItem().getItem());
        if (job.step < details.totalSteps() - 1 && returnedStep == job.step + 1) return true;
        if (job.step > 0 && returnedStep == job.step) return true;
        return job.step == 0 && matchesInitialWorkpiece(stack, job, details);
    }

    private int getAssemblyStep(ItemStack stack, ResourceLocation recipeId, Item transitionalItem) {
        if (stack.getItem() != transitionalItem) return -1;
        CompoundTag assembly = stack.getTagElement("SequencedAssembly");
        if (assembly == null || !recipeId.toString().equals(assembly.getString("id"))) return -1;
        return assembly.getInt("Step");
    }

    private boolean matchesInitialWorkpiece(ItemStack stack, ActiveJob job, SequencePatternDetails details) {
        AEItemKey initialWorkpiece = getInitialWorkpieceKey(job);
        return initialWorkpiece != null
                && stack.getItem() == initialWorkpiece.getItem()
                && details.recipe().getIngredient().test(stack);
    }

    private @Nullable AEItemKey getInitialWorkpieceKey(ActiveJob job) {
        if (job.inputs.isEmpty() || job.inputs.get(0).size() != 1) return null;
        AEKey key = job.inputs.get(0).get(0).what();
        return key instanceof AEItemKey itemKey ? itemKey : null;
    }

    private void notifyChildUnlock(ActiveJob job, long amount) {
        if (level == null || job.lastChildPos == null || job.unlockToken == null) return;
        if (level.getBlockEntity(job.lastChildPos) instanceof ChildProviderBlockEntity child) {
            child.unlockResult(job.unlockToken, amount);
        }
    }

    private void releaseJobLock(ActiveJob job) {
        notifyChildUnlock(job, Long.MAX_VALUE);
    }

    private void advanceJobWithIntermediate(ActiveJob job, ItemStack workpiece, int returnedStep) {
        job.workpiece = workpiece;
        job.step = returnedStep;
        job.dispatched = false;
        inventoryTrackingDirty = true;
    }

    /**
     * Called from the Create final-step mixin. A non-null return value, including
     * ItemStack.EMPTY, means this tracked attempt was valid and its planned
     * result must replace Create's random result.
     */
    public @Nullable ItemStack completeAttempt(UUID attemptId, ResourceLocation recipeId) {
        if (level == null || level.isClientSide) return null;
        for (ActiveJob job : jobs) {
            if (!job.attemptId.equals(attemptId) || !job.recipeId.equals(recipeId)) continue;
            SequencePatternDetails details = findPatternDetails(recipeId);
            if (details == null || !job.dispatched || job.step != details.totalSteps() - 1) return null;
            if (!job.resultObserved) {
                job.resultObserved = true;
                releaseJobLock(job);
                inventoryTrackingDirty = true;
                LOGGER.debug("SPP completed tracked attempt at {}: recipe={}, attempt={}, result={}",
                        worldPosition, recipeId, attemptId,
                        job.plannedOutput.isEmpty() ? "EMPTY" : job.plannedOutput);
                saveChanges();
            }
            return job.plannedOutput.copy();
        }
        return null;
    }

    public void abortJob(Player player) {
        if (jobs.isEmpty()) return;
        for (ActiveJob job : jobs) {
            releaseJobLock(job);
            returnUndispatchedInputs(job);
        }
        jobs.clear();
        clearInventoryTracking();
        ICraftingProvider.requestUpdate(getMainNode());
        saveChanges();
        player.displayClientMessage(Component.translatable("message.sequenced_pattern_provider.master.aborted"), true);
    }

    private void returnUndispatchedInputs(ActiveJob job) {
        IGrid grid = getMainNode().getGrid();
        if (grid != null) {
            MEStorage storage = grid.getStorageService().getInventory();
            for (int i = 0; i < job.inputs.size(); i++) {
                if (job.consumed[i]) continue;
                for (GenericStack stack : job.inputs.get(i)) {
                    storage.insert(stack.what(), stack.amount(), Actionable.MODULATE, IActionSource.empty());
                }
            }
            if (!job.workpiece.isEmpty()) {
                ItemStack returnedWorkpiece = job.workpiece.copy();
                AttemptToken.clear(returnedWorkpiece);
                storage.insert(AEItemKey.of(returnedWorkpiece), returnedWorkpiece.getCount(),
                        Actionable.MODULATE, IActionSource.empty());
            }
        }
    }

    public void onBlockRemoved() {
        if (level == null || level.isClientSide) return;
        for (ActiveJob job : jobs) returnUndispatchedInputs(job);
        jobs.clear();
        clearInventoryTracking();
        for (int slot = 0; slot < patterns.getSlots(); slot++) {
            ItemStack pattern = patterns.extractItem(slot, 1, false);
            if (!pattern.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), pattern);
            }
        }
    }

    private @Nullable SequencePatternDetails findPatternDetails(ResourceLocation recipeId) {
        if (level == null) return null;
        SequencePatternDetails cached = patternDetailsCache.get(recipeId);
        if (cached != null) return cached;
        for (int slot = 0; slot < patterns.getSlots(); slot++) {
            ItemStack stack = patterns.getStackInSlot(slot);
            if (recipeId.equals(SequencePatternItem.getRecipeId(stack))) {
                SequencePatternDetails details = SequencePatternDetails.fromStack(stack, level);
                if (details != null) patternDetailsCache.put(recipeId, details);
                return details;
            }
        }
        return null;
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        if (level == null) return Collections.emptyList();
        List<IPatternDetails> result = new ArrayList<>();
        for (int slot = 0; slot < patterns.getSlots(); slot++) {
            SequencePatternDetails details = SequencePatternDetails.fromStack(patterns.getStackInSlot(slot), level);
            if (details != null && details.supportsAttemptTracking() && hasRouteForEveryStep(details)) result.add(details);
        }
        return result;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (!(patternDetails instanceof SequencePatternDetails details)
                || inputHolder.length != details.plannedInputs().length
                || !details.supportsAttemptTracking()
                || !hasRouteForEveryStep(details)) return false;

        int batchSize = details.probabilityPlan().batchSize();
        if (batchSize < 1 || jobs.size() + batchSize > getMaxActiveJobCount()) return false;

        List<List<List<GenericStack>>> splitInputs = splitInputsByAttempt(details, inputHolder, batchSize);
        if (splitInputs == null || !canTagInitialWorkpieces(details, splitInputs)) return false;
        List<ItemStack> plannedOutputs = allocatePlannedOutputs(details);
        if (plannedOutputs.size() != batchSize) return false;

        UUID batchId = UUID.randomUUID();
        for (int attempt = 0; attempt < batchSize; attempt++) {
            ActiveJob job = new ActiveJob(details.recipeId(), splitInputs.get(attempt),
                    new boolean[inputHolder.length], UUID.randomUUID(), batchId, plannedOutputs.get(attempt));
            jobs.add(job);
        }
        LOGGER.debug("SPP accepted probability batch at {}: recipe={}, batch={}, attempts={}, activeJobs={}",
                worldPosition, details.recipeId(), batchId, batchSize, jobs.size());
        inventoryTrackingDirty = true;
        ICraftingProvider.requestUpdate(getMainNode());
        saveChanges();
        return true;
    }

    private @Nullable List<List<List<GenericStack>>> splitInputsByAttempt(SequencePatternDetails details,
                                                                           KeyCounter[] inputHolder,
                                                                           int batchSize) {
        List<List<List<GenericStack>>> attempts = new ArrayList<>(batchSize);
        for (int attempt = 0; attempt < batchSize; attempt++) {
            List<List<GenericStack>> inputs = new ArrayList<>(inputHolder.length);
            for (int input = 0; input < inputHolder.length; input++) inputs.add(new ArrayList<>());
            attempts.add(inputs);
        }

        SequencePatternDetails.PlannedInput[] plan = details.plannedInputs();
        for (int inputIndex = 0; inputIndex < inputHolder.length; inputIndex++) {
            Map<AEKey, Long> remaining = new java.util.LinkedHashMap<>();
            for (Object2LongMap.Entry<AEKey> entry : inputHolder[inputIndex]) {
                if (entry.getLongValue() > 0 && plan[inputIndex].isValid(entry.getKey(), level)) {
                    remaining.merge(entry.getKey(), entry.getLongValue(), Long::sum);
                }
            }
            if (remaining.isEmpty()) return null;

            for (int attempt = 0; attempt < batchSize; attempt++) {
                AEKey selectedKey = null;
                long selectedAmount = 0;
                for (Map.Entry<AEKey, Long> entry : remaining.entrySet()) {
                    long required = plan[inputIndex].requiredAmountPerAttempt(entry.getKey());
                    if (required > 0 && entry.getValue() >= required) {
                        selectedKey = entry.getKey();
                        selectedAmount = required;
                        break;
                    }
                }
                if (selectedKey == null) return null;
                remaining.put(selectedKey, remaining.get(selectedKey) - selectedAmount);
                attempts.get(attempt).get(inputIndex).add(new GenericStack(selectedKey, selectedAmount));
            }
            if (remaining.values().stream().anyMatch(amount -> amount != 0)) return null;
        }
        return attempts;
    }

    private List<ItemStack> allocatePlannedOutputs(SequencePatternDetails details) {
        ProbabilityPlan plan = details.probabilityPlan();
        ProbabilityKey key = new ProbabilityKey(details.recipeId(), plan.targetResultIndex());
        ProbabilityPlan.Allocation allocation = plan.allocate(probabilityRemainders.getOrDefault(key, 0L));
        probabilityRemainders.put(key, allocation.nextRemainder());

        List<ItemStack> outputs = new ArrayList<>(plan.batchSize());
        for (int i = 0; i < allocation.targetCount(); i++) outputs.add(plan.targetOutput());
        while (outputs.size() < plan.batchSize()) outputs.add(plan.rollNonTarget(level.random));
        Collections.shuffle(outputs, new java.util.Random(level.random.nextLong()));
        return outputs;
    }

    private boolean canTagInitialWorkpieces(SequencePatternDetails details,
                                             List<List<List<GenericStack>>> attempts) {
        if (level == null) return false;
        for (List<List<GenericStack>> attempt : attempts) {
            if (attempt.isEmpty() || attempt.get(0).size() != 1) return false;
            GenericStack input = attempt.get(0).get(0);
            if (!(input.what() instanceof AEItemKey itemKey) || input.amount() != 1) return false;
            ItemStack tagged = itemKey.toStack(1);
            AttemptToken.write(tagged, level.dimension().location(), worldPosition, new UUID(0, 0));
            if (!details.recipe().getIngredient().test(tagged)) return false;
        }
        return true;
    }

    private boolean hasRouteForEveryStep(SequencePatternDetails details) {
        for (int step = 0; step < details.stepsPerLoop(); step++) {
            if (!hasMatchingChild(details.stepDescriptor(step))) return false;
        }
        return true;
    }

    @Override
    public boolean isBusy() {
        return jobs.size() >= getMaxActiveJobCount();
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return AECableType.SMART;
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Patterns", patterns.serializeNBT());
        ListTag childList = new ListTag();
        children.forEach(pos -> childList.add(LongTag.valueOf(pos.asLong())));
        tag.put("Children", childList);
        ListTag jobList = new ListTag();
        jobs.forEach(job -> jobList.add(job.save()));
        tag.put("Jobs", jobList);
        ListTag remainderList = new ListTag();
        probabilityRemainders.forEach((key, remainder) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Recipe", key.recipeId().toString());
            entry.putInt("TargetResult", key.targetResultIndex());
            entry.putLong("Remainder", remainder);
            remainderList.add(entry);
        });
        tag.put("ProbabilityRemainders", remainderList);
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        if (tag.contains("Patterns")) {
            patterns.deserializeNBT(tag.getCompound("Patterns"));
        } else if (tag.contains("Pattern")) {
            patterns.setStackInSlot(0, ItemStack.of(tag.getCompound("Pattern")));
        }
        children.clear();
        nextChildIndexByRoute.clear();
        blockingInputsByChild.clear();
        ListTag childList = tag.getList("Children", Tag.TAG_LONG);
        childList.forEach(value -> children.add(BlockPos.of(((LongTag) value).getAsLong())));
        jobs.clear();
        if (tag.contains("Jobs", Tag.TAG_LIST)) {
            ListTag jobList = tag.getList("Jobs", Tag.TAG_COMPOUND);
            // Load up to the largest supported configured capacity even if the
            // current setting is lower, so reducing the limit cannot discard jobs.
            for (int i = 0; i < jobList.size() && jobs.size() < MAX_PERSISTED_ACTIVE_JOBS; i++) {
                ActiveJob loaded = ActiveJob.load(jobList.getCompound(i));
                if (loaded != null) jobs.add(loaded);
            }
        } else if (tag.contains("Job", Tag.TAG_COMPOUND)) {
            ActiveJob loaded = ActiveJob.load(tag.getCompound("Job"));
            if (loaded != null) jobs.add(loaded);
        }
        probabilityRemainders.clear();
        ListTag remainderList = tag.getList("ProbabilityRemainders", Tag.TAG_COMPOUND);
        for (int i = 0; i < remainderList.size(); i++) {
            CompoundTag entry = remainderList.getCompound(i);
            ResourceLocation recipeId = ResourceLocation.tryParse(entry.getString("Recipe"));
            if (recipeId != null) {
                probabilityRemainders.put(new ProbabilityKey(recipeId, entry.getInt("TargetResult")),
                        entry.getLong("Remainder"));
            }
        }
        inventoryTrackingDirty = true;
    }

    private static final class ActiveJob {
        private final ResourceLocation recipeId;
        private final List<List<GenericStack>> inputs;
        private final boolean[] consumed;
        private final UUID attemptId;
        private final UUID batchId;
        private final ItemStack plannedOutput;
        private int step;
        private boolean dispatched;
        private ItemStack workpiece = ItemStack.EMPTY;
        private @Nullable BlockPos lastChildPos;
        private @Nullable String unlockToken;
        private boolean resultObserved;

        private ActiveJob(ResourceLocation recipeId, List<List<GenericStack>> inputs, boolean[] consumed,
                          UUID attemptId, UUID batchId, ItemStack plannedOutput) {
            this.recipeId = recipeId;
            this.inputs = inputs;
            this.consumed = consumed;
            this.attemptId = attemptId;
            this.batchId = batchId;
            this.plannedOutput = plannedOutput.copy();
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Recipe", recipeId.toString());
            tag.putUUID("AttemptId", attemptId);
            tag.putUUID("BatchId", batchId);
            tag.putInt("Step", step);
            tag.putBoolean("Dispatched", dispatched);
            if (lastChildPos != null) tag.putLong("LastChild", lastChildPos.asLong());
            if (unlockToken != null) tag.putString("UnlockToken", unlockToken);
            tag.putBoolean("ResultObserved", resultObserved);
            tag.putBoolean("PlannedEmpty", plannedOutput.isEmpty());
            if (!plannedOutput.isEmpty()) tag.put("PlannedOutput", plannedOutput.save(new CompoundTag()));
            if (!workpiece.isEmpty()) tag.put("Workpiece", workpiece.save(new CompoundTag()));
            ListTag inputList = new ListTag();
            for (int i = 0; i < inputs.size(); i++) {
                CompoundTag inputTag = new CompoundTag();
                inputTag.putBoolean("Consumed", consumed[i]);
                ListTag stacks = new ListTag();
                for (GenericStack stack : inputs.get(i)) {
                    CompoundTag stackTag = stack.what().toTagGeneric();
                    stackTag.putLong("Amount", stack.amount());
                    stacks.add(stackTag);
                }
                inputTag.put("Stacks", stacks);
                inputList.add(inputTag);
            }
            tag.put("Inputs", inputList);
            return tag;
        }

        private static @Nullable ActiveJob load(CompoundTag tag) {
            ResourceLocation recipe = ResourceLocation.tryParse(tag.getString("Recipe"));
            if (recipe == null) return null;
            ListTag inputList = tag.getList("Inputs", Tag.TAG_COMPOUND);
            List<List<GenericStack>> inputs = new ArrayList<>();
            boolean[] consumed = new boolean[inputList.size()];
            for (int i = 0; i < inputList.size(); i++) {
                CompoundTag inputTag = inputList.getCompound(i);
                consumed[i] = inputTag.getBoolean("Consumed");
                List<GenericStack> selected = new ArrayList<>();
                ListTag stacks = inputTag.getList("Stacks", Tag.TAG_COMPOUND);
                for (int j = 0; j < stacks.size(); j++) {
                    CompoundTag stackTag = stacks.getCompound(j);
                    AEKey key = AEKey.fromTagGeneric(stackTag);
                    if (key != null) selected.add(new GenericStack(key, stackTag.getLong("Amount")));
                }
                inputs.add(selected);
            }
            UUID attemptId = tag.hasUUID("AttemptId") ? tag.getUUID("AttemptId") : UUID.randomUUID();
            UUID batchId = tag.hasUUID("BatchId") ? tag.getUUID("BatchId") : attemptId;
            ItemStack plannedOutput = tag.contains("PlannedOutput", Tag.TAG_COMPOUND)
                    ? ItemStack.of(tag.getCompound("PlannedOutput")) : ItemStack.EMPTY;
            ActiveJob job = new ActiveJob(recipe, inputs, consumed, attemptId, batchId, plannedOutput);
            job.step = tag.getInt("Step");
            job.dispatched = tag.getBoolean("Dispatched");
            job.lastChildPos = tag.contains("LastChild", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("LastChild")) : null;
            job.unlockToken = tag.contains("UnlockToken", Tag.TAG_STRING) ? tag.getString("UnlockToken") : null;
            job.resultObserved = tag.getBoolean("ResultObserved");
            job.workpiece = tag.contains("Workpiece") ? ItemStack.of(tag.getCompound("Workpiece")) : ItemStack.EMPTY;
            return job;
        }
    }

    private record ProbabilityKey(ResourceLocation recipeId, int targetResultIndex) {
    }

}
