package io.github.createdelight.sequencedpatternprovider.tracking;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DeferredProviderUpdateTest {
    @Test
    void dispatchDoesNotInvalidateAeProviderIterator() {
        var providers = new ArrayList<>(List.of("master", "other"));
        Runnable refresh = () -> {
            providers.remove("master");
            providers.add("master");
        };
        // Reproduce the old synchronous refresh while the CPU retains its iterator.
        var original = providers.iterator();
        original.next();
        refresh.run();
        assertThrows(ConcurrentModificationException.class, original::next);

        var update = new DeferredProviderUpdate();
        var dispatch = providers.iterator();
        var visited = new ArrayList<String>();
        while (dispatch.hasNext()) {
            visited.add(dispatch.next());
            // Multiple CPUs/batches can accept jobs before the block's next tick.
            update.request();
            update.request();
        }
        assertEquals(List.of("other", "master"), visited);
        update.flush(refresh);
        assertEquals(List.of("other", "master"), providers);
    }

    @Test
    void coalescesRequestsAndWaitsWhileOwnerIsInactive() {
        var update = new DeferredProviderUpdate();
        var calls = new AtomicInteger();
        update.flush(calls::incrementAndGet);
        assertEquals(0, calls.get());
        for (int i = 0; i < 10000; i++) update.request();
        // Inactive/unloaded owners do not flush or enqueue server tasks.
        assertEquals(0, calls.get());
        update.flush(calls::incrementAndGet);
        update.flush(calls::incrementAndGet);
        assertEquals(1, calls.get());
        update.request();
        update.flush(calls::incrementAndGet);
        assertEquals(2, calls.get());
    }

    @Test
    void reentrantRequestWaitsForNextTick() {
        var update = new DeferredProviderUpdate();
        var calls = new AtomicInteger();
        update.request();
        update.flush(() -> {
            calls.incrementAndGet();
            update.request();
        });
        assertEquals(1, calls.get());
        update.flush(calls::incrementAndGet);
        update.flush(calls::incrementAndGet);
        assertEquals(2, calls.get());
    }

    @Test
    void failedRefreshRetainsRequestAndPropagatesFailure() {
        var update = new DeferredProviderUpdate();
        var failure = new IllegalStateException("refresh failed");
        var calls = new AtomicInteger();
        update.request();
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> update.flush(() -> { throw failure; })));
        update.flush(calls::incrementAndGet);
        assertEquals(1, calls.get());
    }
}
