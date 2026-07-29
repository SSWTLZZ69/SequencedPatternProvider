package io.github.createdelight.sequencedpatternprovider.tracking;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttemptTokenTest {
    private static final ResourceLocation DIMENSION = Objects.requireNonNull(ResourceLocation.tryParse("minecraft:overworld"));
    private static final BlockPos MASTER_POS = new BlockPos(12, 64, -8);
    private static final UUID ATTEMPT_ID = UUID.fromString("2be5d26b-3466-47a4-b996-86bf740a6f5d");

    @Test
    void mirrorsTokenIntoSequencedAssemblyOutput() {
        CompoundTag input = new CompoundTag();
        AttemptToken.write(input, DIMENSION, MASTER_POS, ATTEMPT_ID);

        CompoundTag output = transitionalTag();
        AttemptToken.copy(input, output);

        assertTrue(output.contains(AttemptToken.TAG_NAME));
        assertTrue(output.getCompound("SequencedAssembly").contains(AttemptToken.TAG_NAME));
        assertEquals(new AttemptToken.Address(DIMENSION, MASTER_POS, ATTEMPT_ID), AttemptToken.read(output));
    }

    @Test
    void readsNestedTokenAfterRootTagWasRebuilt() {
        CompoundTag input = new CompoundTag();
        AttemptToken.write(input, DIMENSION, MASTER_POS, ATTEMPT_ID);
        CompoundTag output = transitionalTag();
        AttemptToken.copy(input, output);

        output.remove(AttemptToken.TAG_NAME);

        assertTrue(AttemptToken.has(output));
        assertEquals(new AttemptToken.Address(DIMENSION, MASTER_POS, ATTEMPT_ID), AttemptToken.read(output));
    }

    @Test
    void copiesFromNestedOnlyIntermediateToNextStep() {
        CompoundTag firstStep = transitionalTag();
        AttemptToken.write(firstStep, DIMENSION, MASTER_POS, ATTEMPT_ID);
        firstStep.remove(AttemptToken.TAG_NAME);

        CompoundTag secondStep = transitionalTag();
        secondStep.getCompound("SequencedAssembly").putInt("Step", 2);
        AttemptToken.copy(firstStep, secondStep);

        assertEquals(new AttemptToken.Address(DIMENSION, MASTER_POS, ATTEMPT_ID), AttemptToken.read(secondStep));
        assertTrue(secondStep.contains(AttemptToken.TAG_NAME));
        assertTrue(secondStep.getCompound("SequencedAssembly").contains(AttemptToken.TAG_NAME));
    }

    @Test
    void clearRemovesBothCopiesWithoutDamagingAssemblyProgress() {
        CompoundTag tag = transitionalTag();
        AttemptToken.write(tag, DIMENSION, MASTER_POS, ATTEMPT_ID);

        AttemptToken.clear(tag);

        assertFalse(AttemptToken.has(tag));
        assertNull(AttemptToken.read(tag));
        assertNotNull(tag.getCompound("SequencedAssembly"));
        assertEquals(1, tag.getCompound("SequencedAssembly").getInt("Step"));
    }

    @Test
    void keepsParallelIntermediateAttemptsDistinctAfterRootTagsAreRemoved() {
        Set<UUID> observedAttempts = new HashSet<>();
        for (int line = 0; line < 9; line++) {
            UUID attemptId = new UUID(0x5A5A5A5A5A5A5A5AL, line + 1L);
            CompoundTag input = new CompoundTag();
            AttemptToken.write(input, DIMENSION, MASTER_POS, attemptId);

            CompoundTag intermediate = transitionalTag();
            AttemptToken.copy(input, intermediate);
            intermediate.remove(AttemptToken.TAG_NAME);

            AttemptToken.Address address = AttemptToken.read(intermediate);
            assertNotNull(address);
            observedAttempts.add(address.attemptId());
        }
        assertEquals(9, observedAttempts.size());
    }

    private static CompoundTag transitionalTag() {
        CompoundTag root = new CompoundTag();
        CompoundTag assembly = new CompoundTag();
        assembly.putString("id", "create:test_sequence");
        assembly.putInt("Step", 1);
        assembly.putFloat("Progress", 0.25f);
        root.put("SequencedAssembly", assembly);
        return root;
    }
}
