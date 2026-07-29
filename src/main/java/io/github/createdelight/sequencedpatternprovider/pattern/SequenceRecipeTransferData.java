package io.github.createdelight.sequencedpatternprovider.pattern;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.fluid.FluidIngredient;
import io.github.createdelight.sequencedpatternprovider.item.SequencePatternItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record SequenceRecipeTransferData(GenericStack initialInput, GenericStack[] materials,
                                         ResourceLocation[] routeItems, int loops, GenericStack output) {
    public record ParseResult(@Nullable SequenceRecipeTransferData data, @Nullable String errorKey) {
        public boolean successful() {
            return data != null;
        }
    }

    public static ParseResult parse(SequencedAssemblyRecipe recipe) {
        int stepCount = recipe.getSequence().size();
        if (stepCount < 1 || stepCount > SequencePatternItem.MAX_MANUAL_STEPS) {
            return error("message.sequenced_pattern_provider.jei.too_many_steps");
        }

        ItemStack initial = firstItem(recipe.getIngredient());
        ItemStack result = recipe.resultPool.isEmpty() ? ItemStack.EMPTY : recipe.resultPool.get(0).getStack();
        if (initial.isEmpty() || result.isEmpty()) {
            return error("message.sequenced_pattern_provider.jei.missing_input_output");
        }

        GenericStack[] materials = new GenericStack[SequencePatternItem.MAX_MANUAL_STEPS];
        ResourceLocation[] routes = new ResourceLocation[SequencePatternItem.MAX_MANUAL_STEPS];
        for (int step = 0; step < stepCount; step++) {
            IAssemblyRecipe assembly = recipe.getSequence().get(step).getAsAssemblyRecipe();

            List<Ingredient> itemIngredients = new ArrayList<>();
            assembly.addAssemblyIngredients(itemIngredients);
            List<FluidIngredient> fluidIngredients = new ArrayList<>();
            assembly.addAssemblyFluidIngredients(fluidIngredients);
            int materialCount = itemIngredients.size() + fluidIngredients.size();
            if (materialCount > 1) {
                return error("message.sequenced_pattern_provider.jei.too_many_materials");
            }
            if (!itemIngredients.isEmpty()) {
                ItemStack material = firstItem(itemIngredients.get(0));
                if (material.isEmpty()) {
                    return error("message.sequenced_pattern_provider.jei.missing_material");
                }
                materials[step] = new GenericStack(AEItemKey.of(material), Math.max(1, material.getCount()));
            } else if (!fluidIngredients.isEmpty()) {
                FluidIngredient ingredient = fluidIngredients.get(0);
                List<FluidStack> matching = ingredient.getMatchingFluidStacks();
                if (matching.isEmpty()) {
                    return error("message.sequenced_pattern_provider.jei.missing_material");
                }
                materials[step] = new GenericStack(AEFluidKey.of(matching.get(0)), ingredient.getRequiredAmount());
            }

            Set<ItemLike> machines = new LinkedHashSet<>();
            assembly.addRequiredMachines(machines);
            routes[step] = machines.stream()
                    .map(ItemLike::asItem)
                    .filter(item -> item != Items.AIR)
                    .map(ForgeRegistries.ITEMS::getKey)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (routes[step] == null) {
                return error("message.sequenced_pattern_provider.jei.missing_machine");
            }
        }

        ItemStack output = result.copy();
        return new ParseResult(new SequenceRecipeTransferData(
                new GenericStack(AEItemKey.of(initial), Math.max(1, initial.getCount())),
                materials, routes, recipe.getLoops(),
                new GenericStack(AEItemKey.of(output), Math.max(1, output.getCount()))), null);
    }

    private static ItemStack firstItem(Ingredient ingredient) {
        for (ItemStack stack : ingredient.getItems()) {
            if (!stack.isEmpty()) return stack.copy();
        }
        return ItemStack.EMPTY;
    }

    private static ParseResult error(String key) {
        return new ParseResult(null, key);
    }
}
