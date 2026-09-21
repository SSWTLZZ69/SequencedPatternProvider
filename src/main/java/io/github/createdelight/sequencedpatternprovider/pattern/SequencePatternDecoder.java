package io.github.createdelight.sequencedpatternprovider.pattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetailsDecoder;
import appeng.api.stacks.AEItemKey;
import io.github.createdelight.sequencedpatternprovider.item.SequencePatternItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class SequencePatternDecoder implements IPatternDetailsDecoder {
    @Override
    public boolean isEncodedPattern(ItemStack stack) {
        return SequencePatternItem.isEncoded(stack);
    }

    @Override
    public IPatternDetails decodePattern(AEItemKey key, Level level) {
        return SequencePatternDetails.fromStack(key.toStack(), level);
    }

    @Override
    public IPatternDetails decodePattern(ItemStack stack, Level level) {
        return SequencePatternDetails.fromStack(stack, level);
    }
}
