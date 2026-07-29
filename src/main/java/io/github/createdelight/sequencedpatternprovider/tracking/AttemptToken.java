package io.github.createdelight.sequencedpatternprovider.tracking;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class AttemptToken {
    public static final String TAG_NAME = "SequencedPatternProviderAttempt";
    private static final String DIMENSION = "Dimension";
    private static final String MASTER_POS = "MasterPos";
    private static final String ATTEMPT_ID = "AttemptId";

    public record Address(ResourceLocation dimension, BlockPos masterPos, UUID attemptId) {
    }

    private AttemptToken() {
    }

    public static void write(ItemStack stack, ResourceLocation dimension, BlockPos masterPos, UUID attemptId) {
        CompoundTag token = new CompoundTag();
        token.putString(DIMENSION, dimension.toString());
        token.putLong(MASTER_POS, masterPos.asLong());
        token.putUUID(ATTEMPT_ID, attemptId);
        stack.getOrCreateTag().put(TAG_NAME, token);
    }

    public static void copy(ItemStack input, ItemStack output) {
        CompoundTag inputTag = input.getTag();
        if (inputTag == null || !inputTag.contains(TAG_NAME, Tag.TAG_COMPOUND)) return;
        output.getOrCreateTag().put(TAG_NAME, inputTag.getCompound(TAG_NAME).copy());
    }

    public static boolean has(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(TAG_NAME, Tag.TAG_COMPOUND);
    }

    public static @Nullable Address read(ItemStack stack) {
        if (!has(stack)) return null;
        CompoundTag token = stack.getTag().getCompound(TAG_NAME);
        ResourceLocation dimension = ResourceLocation.tryParse(token.getString(DIMENSION));
        if (dimension == null || !token.hasUUID(ATTEMPT_ID)) return null;
        return new Address(dimension, BlockPos.of(token.getLong(MASTER_POS)), token.getUUID(ATTEMPT_ID));
    }

    public static void clear(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove(TAG_NAME);
        if (tag.isEmpty()) stack.setTag(null);
    }
}
