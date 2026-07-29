package io.github.createdelight.sequencedpatternprovider.mixin;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import io.github.createdelight.sequencedpatternprovider.tracking.AttemptCompletionService;
import io.github.createdelight.sequencedpatternprovider.tracking.AttemptTrackingBridge;
import io.github.createdelight.sequencedpatternprovider.tracking.AttemptToken;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

// Run before compatibility mixins that also replace advance()'s return value.
// They generally copy the current result, so applying our token first lets them
// preserve it together with their own NBT instead of cancelling this injector.
@Mixin(value = SequencedAssemblyRecipe.class, priority = 1100, remap = false)
public abstract class SequencedAssemblyRecipeMixin implements AttemptTrackingBridge {
    @Shadow
    protected ResourceLocation id;

    @Shadow
    protected List<SequencedRecipe<?>> sequence;

    @Shadow
    protected int loops;

    @Inject(method = "advance", at = @At("HEAD"), cancellable = true)
    private void spp$completeTrackedFinalAttempt(ItemStack input, CallbackInfoReturnable<ItemStack> cir) {
        if (!AttemptToken.has(input) || !spp$isFinalStep(input)) return;
        ItemStack planned = AttemptCompletionService.complete(input, id);
        if (planned != null) cir.setReturnValue(planned);
    }

    @Inject(method = "advance", at = @At("RETURN"), cancellable = true)
    private void spp$propagateAttemptToken(ItemStack input, CallbackInfoReturnable<ItemStack> cir) {
        if (!AttemptToken.has(input) || spp$isFinalStep(input)) return;
        ItemStack output = cir.getReturnValue();
        if (output.isEmpty()) return;
        AttemptToken.copy(input, output);
        cir.setReturnValue(output);
    }

    private boolean spp$isFinalStep(ItemStack input) {
        if (sequence.isEmpty() || loops < 1) return false;
        int step = 0;
        CompoundTag assembly = input.getTagElement("SequencedAssembly");
        if (assembly != null) step = Math.max(0, assembly.getInt("Step"));
        return (step + 1) / sequence.size() >= loops;
    }
}
