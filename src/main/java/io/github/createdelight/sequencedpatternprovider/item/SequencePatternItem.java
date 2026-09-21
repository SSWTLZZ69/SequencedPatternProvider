package io.github.createdelight.sequencedpatternprovider.item;

import io.github.createdelight.sequencedpatternprovider.pattern.SequencePatternDetails;
import io.github.createdelight.sequencedpatternprovider.pattern.AssemblyStepDescriptor;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.EncodedPatternItem;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class SequencePatternItem extends EncodedPatternItem<SequencePatternDetails> {
    private static final String RECIPE_ID = "RecipeId";
    private static final String MANUAL = "Manual";
    public static final int MAX_MANUAL_STEPS = 10;

    public record ManualDefinition(GenericStack initialInput, GenericStack[] stepMaterials,
                                   ResourceLocation[] routeItems, int loops, GenericStack output) {
    }

    public SequencePatternItem(Properties properties) {
        super(properties, (key, level) -> SequencePatternDetails.fromStack(key.toStack(), level), null);
    }

    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static boolean isEncoded(ItemStack stack) {
        return stack.getItem() instanceof SequencePatternItem && data(stack).contains(RECIPE_ID);
    }

    public static @Nullable ResourceLocation getRecipeId(ItemStack stack) {
        if (!isEncoded(stack)) return null;
        return ResourceLocation.tryParse(data(stack).getString(RECIPE_ID));
    }

    public static void encode(ItemStack stack, ResourceLocation recipeId) {
        CompoundTag tag = data(stack);
        tag.putString(RECIPE_ID, recipeId.toString());
        tag.remove(MANUAL);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void encodeManual(ItemStack stack, ResourceLocation recipeId, GenericStack initialInput,
                                    GenericStack[] stepMaterials, ResourceLocation[] routeItems,
                                    int loops, GenericStack output, HolderLookup.Provider registries) {
        CompoundTag tag = data(stack);
        tag.putString(RECIPE_ID, recipeId.toString());
        CompoundTag manual = new CompoundTag();
        manual.put("Initial", GenericStack.writeTag(registries, initialInput));
        manual.put("Output", GenericStack.writeTag(registries, output));
        manual.putInt("Loops", loops);

        ListTag materials = new ListTag();
        for (int i = 0; i < Math.min(MAX_MANUAL_STEPS, stepMaterials.length); i++) {
            if (stepMaterials[i] == null) continue;
            CompoundTag entry = GenericStack.writeTag(registries, stepMaterials[i]);
            entry.putInt("Slot", i);
            materials.add(entry);
        }
        manual.put("Materials", materials);

        ListTag routes = new ListTag();
        for (int i = 0; i < Math.min(MAX_MANUAL_STEPS, routeItems.length); i++) {
            if (routeItems[i] == null) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", i);
            entry.putString("Id", routeItems[i].toString());
            routes.add(entry);
        }
        manual.put("Routes", routes);
        tag.put(MANUAL, manual);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static boolean isManual(ItemStack stack) {
        return isEncoded(stack) && data(stack).contains(MANUAL, Tag.TAG_COMPOUND);
    }

    public static @Nullable ManualDefinition getManualDefinition(ItemStack stack, HolderLookup.Provider registries) {
        if (!isManual(stack)) return null;
        CompoundTag manual = data(stack).getCompound(MANUAL);
        GenericStack initial = GenericStack.readTag(registries, manual.getCompound("Initial"));
        GenericStack output = GenericStack.readTag(registries, manual.getCompound("Output"));
        if (initial == null || !(initial.what() instanceof AEItemKey)
                || output == null || !(output.what() instanceof AEItemKey)) return null;

        GenericStack[] materials = new GenericStack[MAX_MANUAL_STEPS];
        ListTag materialTags = manual.getList("Materials", Tag.TAG_COMPOUND);
        for (int i = 0; i < materialTags.size(); i++) {
            CompoundTag entry = materialTags.getCompound(i);
            int slot = entry.getInt("Slot");
            if (slot >= 0 && slot < materials.length) materials[slot] = GenericStack.readTag(registries, entry);
        }

        ResourceLocation[] routes = new ResourceLocation[MAX_MANUAL_STEPS];
        ListTag routeTags = manual.getList("Routes", Tag.TAG_COMPOUND);
        for (int i = 0; i < routeTags.size(); i++) {
            CompoundTag entry = routeTags.getCompound(i);
            int slot = entry.getInt("Slot");
            ResourceLocation route = ResourceLocation.tryParse(entry.getString("Id"));
            if (slot >= 0 && slot < routes.length) routes[slot] = route;
        }
        return new ManualDefinition(initial, materials, routes,
                Math.max(1, manual.getInt("Loops")), output);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.sequenced_pattern_provider.sequence_pattern.encoded");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Level level = context.level();
        ResourceLocation id = getRecipeId(stack);
        if (id == null) {
            tooltip.add(Component.translatable("tooltip.sequenced_pattern_provider.sequence_pattern.invalid").withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.literal(id.toString()).withStyle(ChatFormatting.AQUA));
            if (level != null) {
                SequencePatternDetails details = SequencePatternDetails.fromStack(stack, level);
                if (details == null) {
                    tooltip.add(Component.translatable("tooltip.sequenced_pattern_provider.sequence_pattern.invalid").withStyle(ChatFormatting.RED));
                } else {
                    if (isManual(stack)) appendManualDetails(stack, details, tooltip, context.registries());
                    else appendRecipeDetails(details, tooltip);
                }
            }
        }
        tooltip.add(Component.translatable("tooltip.sequenced_pattern_provider.sequence_pattern.clear")
                .withStyle(ChatFormatting.GRAY));
    }

    private static void appendManualDetails(ItemStack stack, SequencePatternDetails details, List<Component> tooltip, HolderLookup.Provider registries) {
        ManualDefinition manual = getManualDefinition(stack, registries);
        if (manual == null) return;
        appendContractSummary(details, tooltip);
        appendProbabilityTooltip(details, tooltip);
        tooltip.add(Component.translatable("tooltip.sequenced_pattern_provider.sequence_pattern.structure",
                details.stepsPerLoop(), manual.loops(), details.totalSteps()).withStyle(ChatFormatting.GOLD));
        for (int i = 0; i < details.stepsPerLoop(); i++) {
            String cost = manual.stepMaterials()[i] == null ? "-" : formatGeneric(manual.stepMaterials()[i]);
            ResourceLocation route = manual.routeItems()[i];
            String routeText = route == null ? "-" : formatRoute(route);
            tooltip.add(Component.translatable("tooltip.sequenced_pattern_provider.sequence_pattern.manual_step",
                    i + 1, routeText, cost).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static String formatGeneric(GenericStack stack) {
        return stack.what().getDisplayName().getString() + " "
                + stack.what().formatAmount(stack.amount(), appeng.api.stacks.AmountFormat.FULL);
    }

    private static void appendRecipeDetails(SequencePatternDetails details, List<Component> tooltip) {
        var recipe = details.recipe();
        appendContractSummary(details, tooltip);
        appendProbabilityTooltip(details, tooltip);
        if (recipe.resultPool.size() > 1) {
            String alternatives = recipe.resultPool.stream().skip(1).limit(6)
                    .map(result -> result.getStack().getHoverName().getString())
                    .collect(Collectors.joining(", "));
            tooltip.add(Component.translatable("tooltip.sequenced_pattern_provider.sequence_pattern.alternatives", alternatives)
                    .withStyle(ChatFormatting.RED));
        }
        tooltip.add(Component.translatable("tooltip.sequenced_pattern_provider.sequence_pattern.structure",
                        recipe.getSequence().size(), recipe.getLoops(), details.totalSteps())
                .withStyle(ChatFormatting.GOLD));

        for (int index = 0; index < recipe.getSequence().size(); index++) {
            var step = recipe.getSequence().get(index);
            AssemblyStepDescriptor descriptor = AssemblyStepDescriptor.from(step);
            IAssemblyRecipe assembly = step.getAsAssemblyRecipe();
            List<Ingredient> itemIngredients = new ArrayList<>();
            List<SizedFluidIngredient> fluidIngredients = new ArrayList<>();
            assembly.addAssemblyIngredients(itemIngredients);
            assembly.addAssemblyFluidIngredients(fluidIngredients);

            List<String> costs = new ArrayList<>();
            itemIngredients.stream().map(SequencePatternItem::formatIngredient).forEach(costs::add);
            fluidIngredients.stream().map(SequencePatternItem::formatSizedFluidIngredient).forEach(costs::add);
            String machines = descriptor.routeKeys().stream().map(SequencePatternItem::formatRoute)
                    .collect(Collectors.joining(", "));
            String costText = costs.isEmpty() ? "-" : String.join(", ", costs);
            tooltip.add(Component.translatable("tooltip.sequenced_pattern_provider.sequence_pattern.step",
                            index + 1, descriptor.serializerId().toString(), machines, costText)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void appendContractSummary(SequencePatternDetails details, List<Component> tooltip) {
        GenericStack output = details.getPrimaryOutput();
        if (output != null) {
            tooltip.add(Component.translatable("tooltip.sequenced_pattern_provider.sequence_pattern.output",
                    formatGeneric(output)).withStyle(ChatFormatting.WHITE));
        }

        SequencePatternDetails.PlannedInput[] inputs = details.plannedInputs();
        if (inputs.length == 0 || inputs[0].choices().length == 0) return;
        GenericStack choice = inputs[0].choices()[0];
        long amount = Math.multiplyExact(choice.amount(), inputs[0].getMultiplier());
        long perAttemptAmount = Math.multiplyExact(choice.amount(), inputs[0].perAttemptMultiplier());
        tooltip.add(Component.translatable("tooltip.sequenced_pattern_provider.sequence_pattern.input",
                formatGeneric(new GenericStack(choice.what(), amount)),
                choice.what().formatAmount(perAttemptAmount, appeng.api.stacks.AmountFormat.FULL))
                .withStyle(ChatFormatting.GRAY));
    }

    private static String formatRoute(ResourceLocation route) {
        var item = BuiltInRegistries.ITEM.get(route);
        return item == null ? route.toString() : item.getDescription().getString();
    }

    private static void appendProbabilityTooltip(SequencePatternDetails details, List<Component> tooltip) {
        var plan = details.probabilityPlan();
        double chance = (double) plan.numerator() / (double) plan.denominator();
        tooltip.add(Component.translatable("tooltip.sequenced_pattern_provider.sequence_pattern.chance",
                        String.format(java.util.Locale.ROOT, "%.4f%%", chance * 100.0D))
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.sequenced_pattern_provider.sequence_pattern.batch",
                        plan.batchSize(), details.getPrimaryOutput().amount())
                .withStyle(ChatFormatting.AQUA));
    }

    private static String formatIngredient(Ingredient ingredient) {
        ItemStack[] choices = ingredient.getItems();
        if (choices.length == 0) return "?";
        return Arrays.stream(choices).limit(3)
                .map(choice -> choice.getHoverName().getString())
                .collect(Collectors.joining(" / "));
    }

    private static String formatSizedFluidIngredient(SizedFluidIngredient ingredient) {
        return Arrays.stream(ingredient.getFluids()).findFirst()
                .map(fluid -> fluid.getHoverName().getString() + " " + ingredient.amount() + "mB")
                .orElse("?");
    }
}
