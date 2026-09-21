package io.github.createdelight.sequencedpatternprovider.pattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import io.github.createdelight.sequencedpatternprovider.SequencedPatternProviderConfig;
import io.github.createdelight.sequencedpatternprovider.item.SequencePatternItem;
import io.github.createdelight.sequencedpatternprovider.probability.ProbabilityPlan;
import io.github.createdelight.sequencedpatternprovider.tracking.AttemptTrackingBridge;
import io.github.createdelight.sequencedpatternprovider.tracking.AttemptToken;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class SequencePatternDetails implements IPatternDetails {
    public record PlannedInput(int step, GenericStack[] choices, long perAttemptMultiplier, int batchSize,
                               Matcher matcher) implements IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return choices;
        }

        @Override
        public long getMultiplier() {
            return Math.multiplyExact(perAttemptMultiplier, batchSize);
        }

        @Override
        public boolean isValid(AEKey key, Level level) {
            return matcher.matches(key);
        }

        @Override
        public AEKey getRemainingKey(AEKey key) {
            return null;
        }

        public long requiredAmountPerAttempt(AEKey selectedKey) {
            for (GenericStack choice : choices) {
                if (choice.what().equals(selectedKey)) {
                    return Math.multiplyExact(choice.amount(), perAttemptMultiplier);
                }
            }
            return Math.multiplyExact(choices[0].amount(), perAttemptMultiplier);
        }
    }

    @FunctionalInterface
    public interface Matcher {
        boolean matches(AEKey key);
    }

    private final AEItemKey definition;
    private final ResourceLocation recipeId;
    private final SequencedAssemblyRecipe recipe;
    private final PlannedInput[] inputs;
    private final GenericStack[] outputs;
    private final int totalSteps;
    private final AssemblyStepDescriptor[] steps;
    private final ProbabilityPlan probabilityPlan;

    private SequencePatternDetails(AEItemKey definition, ResourceLocation recipeId, SequencedAssemblyRecipe recipe,
                                   PlannedInput[] inputs, GenericStack[] outputs, int totalSteps,
                                   AssemblyStepDescriptor[] steps, ProbabilityPlan probabilityPlan) {
        this.definition = definition;
        this.recipeId = recipeId;
        this.recipe = recipe;
        this.inputs = inputs;
        this.outputs = outputs;
        this.totalSteps = totalSteps;
        this.steps = steps;
        this.probabilityPlan = probabilityPlan;
    }

    public static @Nullable SequencePatternDetails fromStack(ItemStack patternStack, Level level) {
        ResourceLocation recipeId = SequencePatternItem.getRecipeId(patternStack);
        if (recipeId == null) return null;
        Recipe<?> found = level.getRecipeManager().byKey(recipeId).map(net.minecraft.world.item.crafting.RecipeHolder::value).orElse(null);
        if (!(found instanceof SequencedAssemblyRecipe recipe)) return null;
        if (recipe.getSequence().isEmpty() || recipe.getLoops() < 1 || recipe.resultPool.isEmpty()) return null;

        SequencePatternItem.ManualDefinition manual = SequencePatternItem.getManualDefinition(patternStack, level.registryAccess());
        if (manual != null) return fromManual(patternStack, recipeId, recipe, manual);

        ProcessingOutput result = recipe.resultPool.get(0);
        if (result.getStack().isEmpty()) return null;
        ProbabilityPlan probabilityPlan = ProbabilityPlan.create(recipe, result.getStack(),
                SequencedPatternProviderConfig.maxActiveJobs());
        if (probabilityPlan == null) return null;

        List<PlannedInput> inputs = new ArrayList<>();
        PlannedInput base = itemInput(0, recipe.getIngredient(), probabilityPlan.batchSize());
        if (base == null) return null;
        inputs.add(base);

        int totalSteps = recipe.getSequence().size() * recipe.getLoops();
        for (int step = 0; step < totalSteps; step++) {
            SequencedRecipe<?> sequenced = recipe.getSequence().get(step % recipe.getSequence().size());
            IAssemblyRecipe assembly = sequenced.getAsAssemblyRecipe();
            List<Ingredient> itemIngredients = new ArrayList<>();
            assembly.addAssemblyIngredients(itemIngredients);
            for (Ingredient ingredient : itemIngredients) {
                PlannedInput input = itemInput(step, ingredient, probabilityPlan.batchSize());
                if (input == null) return null;
                inputs.add(input);
            }

            List<SizedFluidIngredient> fluidIngredients = new ArrayList<>();
            assembly.addAssemblyFluidIngredients(fluidIngredients);
            for (SizedFluidIngredient ingredient : fluidIngredients) {
                PlannedInput input = fluidInput(step, ingredient, probabilityPlan.batchSize());
                if (input == null) return null;
                inputs.add(input);
            }
        }

        ItemStack output = probabilityPlan.targetOutput();
        GenericStack[] outputs = {new GenericStack(AEItemKey.of(output),
                Math.multiplyExact((long) output.getCount(), probabilityPlan.guaranteedTargetCount()))};
        AssemblyStepDescriptor[] steps = recipe.getSequence().stream()
                .map(AssemblyStepDescriptor::from)
                .toArray(AssemblyStepDescriptor[]::new);
        return new SequencePatternDetails(AEItemKey.of(patternStack), recipeId, recipe,
                inputs.toArray(PlannedInput[]::new), outputs, totalSteps, steps, probabilityPlan);
    }

    private static @Nullable SequencePatternDetails fromManual(ItemStack patternStack, ResourceLocation recipeId,
                                                                SequencedAssemblyRecipe recipe,
                                                                SequencePatternItem.ManualDefinition manual) {
        if (!(manual.initialInput().what() instanceof AEItemKey)
                || manual.initialInput().amount() != 1
                || !(manual.output().what() instanceof AEItemKey outputKey)) return null;
        ItemStack selectedOutput = outputKey.toStack((int) Math.min(Integer.MAX_VALUE, manual.output().amount()));
        ProbabilityPlan probabilityPlan = ProbabilityPlan.create(recipe, selectedOutput,
                SequencedPatternProviderConfig.maxActiveJobs());
        if (probabilityPlan == null) return null;
        int stepCount = 0;
        while (stepCount < manual.routeItems().length && manual.routeItems()[stepCount] != null) stepCount++;
        if (stepCount == 0) return null;
        for (int i = stepCount; i < manual.routeItems().length; i++) {
            if (manual.routeItems()[i] != null) return null;
        }

        List<PlannedInput> inputs = new ArrayList<>();
        inputs.add(exactInput(0, manual.initialInput(), probabilityPlan.batchSize()));
        int totalSteps = stepCount * manual.loops();
        for (int step = 0; step < totalSteps; step++) {
            GenericStack material = manual.stepMaterials()[step % stepCount];
            if (material != null) inputs.add(exactInput(step, material, probabilityPlan.batchSize()));
        }

        AssemblyStepDescriptor[] steps = new AssemblyStepDescriptor[stepCount];
        for (int i = 0; i < stepCount; i++) {
            ResourceLocation route = manual.routeItems()[i];
            steps[i] = new AssemblyStepDescriptor(route, java.util.Set.of(route));
        }
        ItemStack targetOutput = probabilityPlan.targetOutput();
        GenericStack promisedOutput = new GenericStack(AEItemKey.of(targetOutput),
                Math.multiplyExact((long) targetOutput.getCount(), probabilityPlan.guaranteedTargetCount()));
        return new SequencePatternDetails(AEItemKey.of(patternStack), recipeId, recipe,
                inputs.toArray(PlannedInput[]::new), new GenericStack[]{promisedOutput}, totalSteps, steps,
                probabilityPlan);
    }

    private static PlannedInput exactInput(int step, GenericStack stack, int batchSize) {
        GenericStack unit = new GenericStack(stack.what(), 1);
        return new PlannedInput(step, new GenericStack[]{unit}, stack.amount(), batchSize,
                key -> key.equals(stack.what()));
    }

    private static @Nullable PlannedInput itemInput(int step, Ingredient ingredient, int batchSize) {
        ItemStack[] stacks = ingredient.getItems();
        if (stacks.length == 0) return null;
        GenericStack[] choices = Arrays.stream(stacks)
                .filter(stack -> !stack.isEmpty())
                .map(stack -> new GenericStack(AEItemKey.of(stack), Math.max(1, stack.getCount())))
                .toArray(GenericStack[]::new);
        if (choices.length == 0) return null;
        return new PlannedInput(step, choices, 1, batchSize,
                key -> key instanceof AEItemKey itemKey && itemKey.matches(ingredient));
    }

    private static @Nullable PlannedInput fluidInput(int step, SizedFluidIngredient ingredient, int batchSize) {
        List<FluidStack> stacks = Arrays.asList(ingredient.getFluids());
        if (stacks.isEmpty()) return null;
        GenericStack[] choices = stacks.stream()
                .filter(stack -> !stack.isEmpty())
                .map(stack -> new GenericStack(AEFluidKey.of(stack), ingredient.amount()))
                .toArray(GenericStack[]::new);
        if (choices.length == 0) return null;
        return new PlannedInput(step, choices, 1, batchSize,
                key -> key instanceof AEFluidKey fluidKey && ingredient.test(fluidKey.toStack(ingredient.amount())));
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public IInput[] getInputs() {
        return inputs;
    }

    @Override
    public List<GenericStack> getOutputs() {
        return List.of(outputs);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof SequencePatternDetails details
                && definition.equals(details.definition);
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }

    public PlannedInput[] plannedInputs() {
        return inputs;
    }

    public ResourceLocation recipeId() {
        return recipeId;
    }

    public SequencedAssemblyRecipe recipe() {
        return recipe;
    }

    public int totalSteps() {
        return totalSteps;
    }

    public int stepsPerLoop() {
        return steps.length;
    }

    public AssemblyStepDescriptor stepDescriptor(int absoluteStep) {
        return steps[absoluteStep % steps.length];
    }

    public ProbabilityPlan probabilityPlan() {
        return probabilityPlan;
    }

    public boolean supportsAttemptTracking() {
        if (!(recipe instanceof AttemptTrackingBridge)
                || inputs.length == 0 || inputs[0].choices().length == 0
                || !(inputs[0].choices()[0].what() instanceof AEItemKey inputKey)
                || inputs[0].requiredAmountPerAttempt(inputKey) != 1) return false;
        ItemStack tagged = inputKey.toStack(1);
        AttemptToken.write(tagged, Level.OVERWORLD.location(), BlockPos.ZERO,
                new UUID(0, 0));
        return recipe.getIngredient().test(tagged);
    }
}
