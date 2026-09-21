package io.github.createdelight.sequencedpatternprovider;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SequencedPatternProviderConfig {
    public static final int DEFAULT_MAX_ACTIVE_JOBS = 16;
    public static final int MAX_CONFIGURED_ACTIVE_JOBS = 64;

    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec.IntValue MAX_ACTIVE_JOBS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("master_provider");
        MAX_ACTIVE_JOBS = builder
                .comment(
                        "Maximum number of active sequenced assembly jobs retained by one master provider.",
                        "Lowering this value does not delete existing jobs; a master above the new limit",
                        "simply refuses new jobs until enough existing jobs finish.")
                .defineInRange("max_active_jobs", DEFAULT_MAX_ACTIVE_JOBS, 1, MAX_CONFIGURED_ACTIVE_JOBS);
        builder.pop();
        SERVER_SPEC = builder.build();
    }

    private SequencedPatternProviderConfig() {
    }

    public static int maxActiveJobs() {
        return MAX_ACTIVE_JOBS.get();
    }
}
