package io.github.createdelight.sequencedpatternprovider.probability;

import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * A deterministic batch contract for one selected result in a Create
 * sequenced-assembly result pool.
 */
public final class ProbabilityPlan {
    private static final int MAX_DECIMAL_SCALE = 6;
    private static final long SURPLUS_PERCENT_DENOMINATOR = 100;

    public record WeightedOutcome(ItemStack stack, long weight, boolean target) {
        public WeightedOutcome {
            stack = stack.copy();
        }
    }

    public record Allocation(int targetCount, long nextRemainder) {
    }

    private final int targetResultIndex;
    private final ItemStack targetOutput;
    private final long numerator;
    private final long denominator;
    private final int batchSize;
    private final int guaranteedTargetCount;
    private final List<WeightedOutcome> outcomes;
    private final long nonTargetWeight;

    private ProbabilityPlan(int targetResultIndex, ItemStack targetOutput, long numerator, long denominator,
                            int batchSize, int guaranteedTargetCount, List<WeightedOutcome> outcomes,
                            long nonTargetWeight) {
        this.targetResultIndex = targetResultIndex;
        this.targetOutput = targetOutput.copy();
        this.numerator = numerator;
        this.denominator = denominator;
        this.batchSize = batchSize;
        this.guaranteedTargetCount = guaranteedTargetCount;
        this.outcomes = List.copyOf(outcomes);
        this.nonTargetWeight = nonTargetWeight;
    }

    public static @Nullable ProbabilityPlan create(SequencedAssemblyRecipe recipe, ItemStack selectedOutput,
                                                   int maxBatchSize) {
        if (selectedOutput.isEmpty() || maxBatchSize < 1 || recipe.resultPool.isEmpty()) return null;

        int targetIndex = -1;
        int commonScale = 0;
        List<BigDecimal> decimalWeights = new ArrayList<>(recipe.resultPool.size());
        for (int i = 0; i < recipe.resultPool.size(); i++) {
            ProcessingOutput result = recipe.resultPool.get(i);
            BigDecimal weight = BigDecimal.valueOf(result.getChance()).stripTrailingZeros();
            if (weight.signum() <= 0) return null;
            commonScale = Math.max(commonScale, Math.max(0, weight.scale()));
            decimalWeights.add(weight);
            if (targetIndex < 0 && sameResult(result.getStack(), selectedOutput)) targetIndex = i;
        }
        if (targetIndex < 0) return null;
        commonScale = Math.min(MAX_DECIMAL_SCALE, commonScale);

        List<Long> weights = new ArrayList<>(decimalWeights.size());
        long totalWeight = 0;
        for (BigDecimal decimalWeight : decimalWeights) {
            long weight;
            try {
                weight = decimalWeight.movePointRight(commonScale).setScale(0, java.math.RoundingMode.HALF_UP)
                        .longValueExact();
                totalWeight = Math.addExact(totalWeight, weight);
            } catch (ArithmeticException exception) {
                return null;
            }
            if (weight <= 0) return null;
            weights.add(weight);
        }

        long targetWeight = 0;
        ItemStack targetOutput = recipe.resultPool.get(targetIndex).getStack();
        for (int i = 0; i < weights.size(); i++) {
            if (sameResult(recipe.resultPool.get(i).getStack(), targetOutput)) {
                targetWeight = Math.addExact(targetWeight, weights.get(i));
            }
        }
        long divisor = BigInteger.valueOf(targetWeight).gcd(BigInteger.valueOf(totalWeight)).longValueExact();
        long numerator = targetWeight / divisor;
        long denominator = totalWeight / divisor;
        int batchSize = chooseBatchSize(numerator, denominator, maxBatchSize);
        if (batchSize < 1) return null;
        int guaranteed = (int) (Math.multiplyExact((long) batchSize, numerator) / denominator);
        if (guaranteed < 1) return null;

        List<WeightedOutcome> outcomes = new ArrayList<>(weights.size());
        long nonTargetWeight = 0;
        for (int i = 0; i < weights.size(); i++) {
            boolean target = sameResult(recipe.resultPool.get(i).getStack(), targetOutput);
            outcomes.add(new WeightedOutcome(recipe.resultPool.get(i).getStack(), weights.get(i), target));
            if (!target) nonTargetWeight = Math.addExact(nonTargetWeight, weights.get(i));
        }
        return new ProbabilityPlan(targetIndex, targetOutput, numerator,
                denominator, batchSize, guaranteed, outcomes, nonTargetWeight);
    }

    private static boolean sameResult(ItemStack recipeOutput, ItemStack selectedOutput) {
        return recipeOutput.getCount() == selectedOutput.getCount()
                && ItemStack.isSameItemSameTags(recipeOutput, selectedOutput);
    }

    private static int chooseBatchSize(long numerator, long denominator, int maxBatchSize) {
        int bestBatch = -1;
        double bestSurplusRatio = Double.POSITIVE_INFINITY;
        for (int batch = 1; batch <= maxBatchSize; batch++) {
            long expectedNumerator = Math.multiplyExact((long) batch, numerator);
            long guaranteed = expectedNumerator / denominator;
            if (guaranteed < 1) continue;
            long surplusNumerator = expectedNumerator - guaranteed * denominator;
            if (surplusNumerator * SURPLUS_PERCENT_DENOMINATOR <= guaranteed * denominator) return batch;
            double surplusRatio = (double) surplusNumerator / (double) (guaranteed * denominator);
            if (surplusRatio < bestSurplusRatio) {
                bestSurplusRatio = surplusRatio;
                bestBatch = batch;
            }
        }
        return bestBatch;
    }

    public Allocation allocate(long previousRemainder) {
        long normalizedRemainder = Math.floorMod(previousRemainder, denominator);
        long total = Math.addExact(normalizedRemainder,
                Math.multiplyExact((long) batchSize, numerator));
        return new Allocation((int) (total / denominator), total % denominator);
    }

    public ItemStack rollNonTarget(RandomSource random) {
        if (nonTargetWeight <= 0) return targetOutput.copy();
        long roll = Math.floorMod(random.nextLong(), nonTargetWeight);
        for (WeightedOutcome outcome : outcomes) {
            if (outcome.target()) continue;
            roll -= outcome.weight();
            if (roll < 0) return outcome.stack().copy();
        }
        return ItemStack.EMPTY;
    }

    public int targetResultIndex() {
        return targetResultIndex;
    }

    public ItemStack targetOutput() {
        return targetOutput.copy();
    }

    public long numerator() {
        return numerator;
    }

    public long denominator() {
        return denominator;
    }

    public int batchSize() {
        return batchSize;
    }

    public int guaranteedTargetCount() {
        return guaranteedTargetCount;
    }
}
