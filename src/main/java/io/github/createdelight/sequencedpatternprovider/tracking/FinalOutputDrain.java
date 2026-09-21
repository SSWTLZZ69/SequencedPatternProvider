package io.github.createdelight.sequencedpatternprovider.tracking;

import java.util.function.BooleanSupplier;

/** Waits for machine output to settle and leave the destination before releasing a batch lease. */
public final class FinalOutputDrain {
    private final long completedAt;
    private long clearSince = -1;

    public FinalOutputDrain(long completedAt) {
        this.completedAt = completedAt;
    }

    public boolean isReady(long gameTime, BooleanSupplier destinationClear) {
        // Create queues belt replacements/removals until its next tick. The
        // advance callback runs before the replacement is even enqueued.
        if (gameTime - completedAt < 2) return false;
        if (!destinationClear.getAsBoolean()) {
            clearSince = -1;
            return false;
        }
        if (clearSince < 0) clearSince = gameTime;
        // An extracted stack is already invisible to getStackAtOffset while
        // still blocking canInsertAt until BeltInventory processes toRemove.
        return gameTime - clearSince >= 2;
    }
}
