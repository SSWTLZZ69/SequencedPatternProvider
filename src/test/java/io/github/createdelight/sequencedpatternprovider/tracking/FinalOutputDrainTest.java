package io.github.createdelight.sequencedpatternprovider.tracking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FinalOutputDrainTest {
    @Test
    void resultComputationCannotReleaseTheNextAttemptBeforeOutputIsPublished() {
        var drain = new FinalOutputDrain(100);
        assertFalse(drain.isReady(100, () -> true));
        assertFalse(drain.isReady(101, () -> true));
        // Create publishes the result after advance() returned.
        assertFalse(drain.isReady(102, () -> false));
        assertFalse(drain.isReady(200, () -> false));
        assertFalse(drain.isReady(201, () -> true));
        assertFalse(drain.isReady(202, () -> true));
        assertTrue(drain.isReady(203, () -> true));
    }

    @Test
    void invisibleOutputMustStayClearAcrossMachineTicks() {
        var drain = new FinalOutputDrain(0);
        assertFalse(drain.isReady(2, () -> true));
        assertFalse(drain.isReady(3, () -> false));
        assertFalse(drain.isReady(4, () -> true));
        assertFalse(drain.isReady(4, () -> true), "repeated polling is not a machine tick");
        assertFalse(drain.isReady(5, () -> true));
        assertTrue(drain.isReady(6, () -> true));
    }

    @Test
    void emptyProbabilityOutcomeStillAllowsMachineCleanupThenCompletes() {
        var drain = new FinalOutputDrain(0);
        for (int time = 0; time < 4; time++) assertFalse(drain.isReady(time, () -> true));
        assertTrue(drain.isReady(4, () -> true));
    }
}
