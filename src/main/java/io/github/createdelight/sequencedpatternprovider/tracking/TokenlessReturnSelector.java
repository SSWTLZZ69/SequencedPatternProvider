package io.github.createdelight.sequencedpatternprovider.tracking;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Selects one logical owner for a returned workpiece after an external machine
 * has removed SPP's attempt token. Successfully advanced workpieces take
 * precedence over same-step retry candidates. Multiple candidates are only
 * interchangeable when they belong to the same recipe and dispatched step.
 */
public final class TokenlessReturnSelector {
    public enum MatchKind {
        ADVANCED,
        UNPROCESSED
    }

    public record Candidate<T>(T value, ResourceLocation recipeId, int dispatchedStep, MatchKind kind) {
    }

    private TokenlessReturnSelector() {
    }

    public static <T> @Nullable T select(List<Candidate<T>> candidates) {
        List<Candidate<T>> advanced = candidates.stream()
                .filter(candidate -> candidate.kind() == MatchKind.ADVANCED)
                .toList();
        List<Candidate<T>> preferred = advanced.isEmpty() ? candidates : advanced;
        if (preferred.isEmpty() || !areFungible(preferred)) return null;
        return preferred.get(0).value();
    }

    private static <T> boolean areFungible(List<Candidate<T>> candidates) {
        Candidate<T> first = candidates.get(0);
        for (int i = 1; i < candidates.size(); i++) {
            Candidate<T> candidate = candidates.get(i);
            if (!first.recipeId().equals(candidate.recipeId())
                    || first.dispatchedStep() != candidate.dispatchedStep()) {
                return false;
            }
        }
        return true;
    }
}
