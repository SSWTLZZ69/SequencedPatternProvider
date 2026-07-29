package io.github.createdelight.sequencedpatternprovider.tracking;

/**
 * Marker added to Create sequenced-assembly recipes by our mixin.
 *
 * <p>This lets the pattern provider fail closed if a development run or a
 * broken distribution did not load the mixin configuration.</p>
 */
public interface AttemptTrackingBridge {
}
