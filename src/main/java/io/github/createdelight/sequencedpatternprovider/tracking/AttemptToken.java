package io.github.createdelight.sequencedpatternprovider.tracking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class AttemptToken {
    public static final String TAG_NAME = "SequencedPatternProviderAttempt";
    private static final String SEQUENCED_ASSEMBLY_TAG = "SequencedAssembly";
    private static final String DIMENSION = "Dimension";
    private static final String MASTER_POS = "MasterPos";
    private static final String ATTEMPT_ID = "AttemptId";

    public record Address(ResourceLocation dimension, BlockPos masterPos, UUID attemptId) {
    }

    private AttemptToken() {
    }

    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static void write(ItemStack stack, ResourceLocation dimension, BlockPos masterPos, UUID attemptId) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> write(root, dimension, masterPos, attemptId));
    }

    static void write(CompoundTag root, ResourceLocation dimension, BlockPos masterPos, UUID attemptId) {
        CompoundTag token = new CompoundTag();
        token.putString(DIMENSION, dimension.toString());
        token.putLong(MASTER_POS, masterPos.asLong());
        token.putUUID(ATTEMPT_ID, attemptId);
        writeToken(root, token);
    }

    public static void copy(ItemStack input, ItemStack output) {
        CompoundTag token = findToken(data(input));
        if (token != null) CustomData.update(DataComponents.CUSTOM_DATA, output, root -> writeToken(root, token));
    }

    static void copy(@Nullable CompoundTag inputRoot, CompoundTag outputRoot) {
        CompoundTag token = findToken(inputRoot);
        if (token != null) writeToken(outputRoot, token);
    }

    public static boolean has(ItemStack stack) {
        return has(data(stack));
    }

    static boolean has(@Nullable CompoundTag root) {
        return findToken(root) != null;
    }

    public static @Nullable Address read(ItemStack stack) {
        return read(data(stack));
    }

    static @Nullable Address read(@Nullable CompoundTag root) {
        CompoundTag token = findToken(root);
        if (token == null) return null;
        ResourceLocation dimension = ResourceLocation.tryParse(token.getString(DIMENSION));
        if (dimension == null || !token.hasUUID(ATTEMPT_ID)) return null;
        return new Address(dimension, BlockPos.of(token.getLong(MASTER_POS)), token.getUUID(ATTEMPT_ID));
    }

    public static void clear(ItemStack stack) {
        CompoundTag tag = data(stack);
        if (tag == null) return;
        clear(tag);
        if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    static void clear(CompoundTag root) {
        root.remove(TAG_NAME);
        if (root.contains(SEQUENCED_ASSEMBLY_TAG, Tag.TAG_COMPOUND)) {
            root.getCompound(SEQUENCED_ASSEMBLY_TAG).remove(TAG_NAME);
        }
    }

    private static void writeToken(CompoundTag root, CompoundTag token) {
        root.put(TAG_NAME, token.copy());

        // Once Create has produced the transitional workpiece, mirror the
        // address into the one compound every sequenced-assembly machine must
        // retain. Some processing integrations rebuild the item root tag while
        // preserving SequencedAssembly, which previously made parallel jobs
        // fall back to ambiguous tokenless matching.
        if (root.contains(SEQUENCED_ASSEMBLY_TAG, Tag.TAG_COMPOUND)) {
            root.getCompound(SEQUENCED_ASSEMBLY_TAG).put(TAG_NAME, token.copy());
        }
    }

    private static @Nullable CompoundTag findToken(@Nullable CompoundTag root) {
        if (root == null) return null;
        if (root.contains(SEQUENCED_ASSEMBLY_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag assembly = root.getCompound(SEQUENCED_ASSEMBLY_TAG);
            if (assembly.contains(TAG_NAME, Tag.TAG_COMPOUND)) {
                return assembly.getCompound(TAG_NAME);
            }
        }
        if (root.contains(TAG_NAME, Tag.TAG_COMPOUND)) {
            return root.getCompound(TAG_NAME);
        }
        return null;
    }
}
