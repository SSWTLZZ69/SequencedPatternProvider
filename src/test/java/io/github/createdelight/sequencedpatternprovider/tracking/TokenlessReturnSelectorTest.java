package io.github.createdelight.sequencedpatternprovider.tracking;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TokenlessReturnSelectorTest {
    private static final ResourceLocation PRECISION = Objects.requireNonNull(
            ResourceLocation.tryParse("createdelight:sequenced_assembly/precision_mechanism"));
    private static final ResourceLocation STURDY = Objects.requireNonNull(
            ResourceLocation.tryParse("create:sequenced_assembly/sturdy_sheet"));

    @Test
    void selectsOldestOfEquivalentParallelJobs() {
        String selected = TokenlessReturnSelector.select(List.of(
                candidate("first", PRECISION, 0, TokenlessReturnSelector.MatchKind.ADVANCED),
                candidate("second", PRECISION, 0, TokenlessReturnSelector.MatchKind.ADVANCED),
                candidate("third", PRECISION, 0, TokenlessReturnSelector.MatchKind.ADVANCED)
        ));

        assertEquals("first", selected);
    }

    @Test
    void prefersSuccessfulAdvanceOverSameStepRetry() {
        String selected = TokenlessReturnSelector.select(List.of(
                candidate("retry", PRECISION, 1, TokenlessReturnSelector.MatchKind.UNPROCESSED),
                candidate("advanced", PRECISION, 0, TokenlessReturnSelector.MatchKind.ADVANCED)
        ));

        assertEquals("advanced", selected);
    }

    @Test
    void refusesDifferentRecipesOrDispatchedSteps() {
        assertNull(TokenlessReturnSelector.select(List.of(
                candidate("precision", PRECISION, 0, TokenlessReturnSelector.MatchKind.ADVANCED),
                candidate("sturdy", STURDY, 0, TokenlessReturnSelector.MatchKind.ADVANCED)
        )));
        assertNull(TokenlessReturnSelector.select(List.of(
                candidate("step0", PRECISION, 0, TokenlessReturnSelector.MatchKind.UNPROCESSED),
                candidate("step1", PRECISION, 1, TokenlessReturnSelector.MatchKind.UNPROCESSED)
        )));
    }

    private static TokenlessReturnSelector.Candidate<String> candidate(
            String value, ResourceLocation recipeId, int step, TokenlessReturnSelector.MatchKind kind) {
        return new TokenlessReturnSelector.Candidate<>(value, recipeId, step, kind);
    }
}
