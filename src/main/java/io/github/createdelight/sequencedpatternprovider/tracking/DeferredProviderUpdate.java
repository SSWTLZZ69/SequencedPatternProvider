package io.github.createdelight.sequencedpatternprovider.tracking;

/**
 * Coalesces server-thread requests until the owning block entity's next active tick.
 * Never flush from a crafting callback: AE2 may still be iterating its providers.
 */
public final class DeferredProviderUpdate {
    private boolean pending;

    public void request() {
        pending = true;
    }

    public void flush(Runnable refresh) {
        if (!pending) return;
        // A refresh can request another update; leave that request for a later tick.
        pending = false;
        try {
            refresh.run();
        } catch (RuntimeException | Error failure) {
            pending = true;
            throw failure;
        }
    }
}
