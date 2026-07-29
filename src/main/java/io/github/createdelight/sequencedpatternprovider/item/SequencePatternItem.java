package io.github.createdelight.sequencedpatternprovider.item;

import io.github.createdelight.sequencedpatternprovider.pattern.SequencePatternDetails;
import io.github.createdelight.sequencedpatternprovider.pattern.AssemblyStepDescriptor;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import com.simibubi.create.foundation.fluid.FluidIngredient;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class SequencePatternItem extends Item {
    private static final String RECIPE_ID = "RecipeId";
    private static final String MANUAL = "Manual";
    public static final int MAX_MANUAL_STEPS = 10;

    public record ManualDefinition(GenericStack initialInput, GenericStack[] stepMaterials,
                                   ResourceLocation[] routeItems, int loops, GenericStack output) {
    }

    public SequencePatternItem(Properties properties) {
        super(properties);
    }

    public static boolean isEncoded(ItemStack stack) {
        return stack.getItem() instanceof SequencePatternItem && stack.hasTag() && stack.getTag().contains(RECIPE_ID);
    }

    public static @Nullable ResourceLocation getRecipeId(ItemStack stack) {
        if (!isEncoded(stack)) return null;
        return ResourceLocation.tryParse(stack.getTag().getString(RECIPE_ID));
    }

    public static void encode(ItemStack stack, ResourceLocation recipeId) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(RECIPE_ID, recipeId.toString());
        tag.remove(MANUAL);
    }

    public static void encodeManual(ItemStack stack, ResourceLocation recipeId, GenericStack initialInput,
                                    GenericStack[] stepMaterials, ResourceLocation[] routeItems,
                                    int loops, GenericStack output) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(RECIPE_ID, recipeId.toString());
        CompoundTag manual = new CompoundTag();
        manual.put("Initial", GenericStack.writeTag(initialInput));
        manual.put("Output", GenericStack.writeTag(output));
        manual.putInt("Loops", loops);

        ListTag materials = new ListTag();
        for (int i = 0; i < Math.min(MAX_MANUAL_STEPS, stepMaterials.length); i++) {
            if (stepMaterials[i] == null) continue;
            CompoundTag entry = GenericStack.writeTag(stepMaterials[i]);
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
    }

    public static boolean isManual(ItemStack stack) {
        return isEncoded(stack) && stack.getTag().contains(MANUAL, Tag.TAG_COMPOUND);
    }

    public static @Nullable ManualDefinition getManualDefinition(ItemStack stack) {
        if (!isManual(stack)) return null;
        CompoundTag manual = stack.getTag().getCompound(MANUAL);
        GenericStack initial = GenericStack.readTag(manual.getCompound("Initial"));
        GenericStack output = GenericStack.readTag(manual.getCompound("Output"));
        if (initial == null || !(initial.what() instanceof AEItemKey)
                || output == null || !(output.what() instanceof AEItemKey)) return null;

        GenericStack[] materials = new GenericStack[MAX_MANUAL_STEPS];
        ListTag materialTags = manual.getList("Materials", Tag.TAG_COMPOUND);
        for (int i = 0; i < materialTags.size(); i++) {
            CompoundTag entry = materialTags.getCompound(i);
            int slot = entry.getInt("Slot");
            if (slot >= 0 && slot < materials.length) materials[slot] = GenericStack.readTag(entry);
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
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
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
                    if (isManual(stack)) appendManualTooltip(stack, details, tooltip);
                    else appendRecipeTooltip(details, tooltip);
                }
            }
        }
        tooltip.add(Component.translatable("tooltip.sequenced_pattern_provider.sequence_pattern.clear")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) replaceWithBlank(player, hand, stack);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!context.getLevel().isClientSide) {
            replaceWithBlank(player, context.getHand(), context.getItemInHand());
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    private static void replaceWithBlank(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, AEItems.BLANK_PATTERN.stack(stack.getCount()));
    }

    private static void appendManualTooltip(ItemStack stack, SequencePatternDetails details, List<Component> tooltip) {
        ManualDefinition manual = getManualDefinition(stack);
        if (manual == null) return;
        tooltip.add(Component.translatable("tooltip.sequenced_pattern_provider.sequence_pattern.output",
                manual.output().what().getDisplayName()).withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("tooltip.sequenced_pattern_provider.sequence_pattern.input",
                formatGeneric(manual.initialInput())).withStyle(ChatFormatting.GRAY));
        appendProbabilityTooltip(details, tooltip);
        tooltip.add(Component.translatable("tooltip.sequenced_pattern_provider.sequence_pattern.structure",
                details.stepsPerLoop(), manual.loops(), details.totalSteps()).withStyle(ChatFormatting.GOLD));
        for (int i = 0; i < details.stepsPerLoop(); i++) {
            String cost = manual.stepMaterials()[i] == null ? "-" : formatGeneric(manual.stepMaterials()[i]);
            tooltip.add(Component.translatable("tooltip.sequenced_pattern_provider.sequence_pattern.step",
                    i + 1, manual.routeItems()[i], manual.routeItems()[i], cost).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static String formatGeneric(GenericStack stack) {
        return stack.what().getDisplayName().getString() + " "
                + stack.what().formatAmount(stack.amount(), appeng.api.stacks.AmountFormat.FULL);
    }

    private static void appendRecipeTooltip(SequencePatternDetails details, List<Component> tooltip) {
        var recipe = details.recipe();
        ItemStack output = recipe.resultPool.get(0).getStack();
        tooltip.add(Component.translatable("tooltip.sequenced_pattern_provider.sequence_pattern.output", output.getHoverName())
                .withStyle(ChatFormatting.GREEN));
        appendProbabilityTooltip(details, tooltip);
        if (recipe.resultPool.size() > 1) {
            String alternatives = recipe.resultPool.stream().skip(1).limit(6)
                    .map(result -> result.getStack().getHoverName().getString())
                    .collect(Collectors.joining(", "));
            tooltip.add(Component.translatable("tooltip.sequenced_pattern_provider.sequence_pattern.alternatives", alternatives)
                    .withStyle(ChatFormatting.RED));
        }
        tooltip.add(Component.translatable("tooltip.sequenced_pattern_provider.sequence_pattern.input",
                        formatIngredient(recipe.getIngredient())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sequenced_pattern_provider.sequence_pattern.structure",
                        recipe.getSequence().size(), recipe.getLoops(), details.totalSteps())
                .withStyle(ChatFormatting.GOLD));

        for (int index = 0; index < recipe.getSequence().size(); index++) {
            var step = recipe.getSequence().get(index);
            AssemblyStepDescriptor descriptor = AssemblyStepDescriptor.from(step);
            IAssemblyRecipe assembly = step.getAsAssemblyRecipe();
            List<Ingredient> itemIngredients = new ArrayList<>();
            List<FluidIngredient> fluidIngredients = new ArrayList<>();
            assembly.addAssemblyIngredients(itemIngredients);
            assembly.addAssemblyFluidIngredients(fluidIngredients);

            List<String> costs = new ArrayList<>();
            itemIngredients.stream().map(SequencePatternItem::formatIngredient).forEach(costs::add);
            fluidIngredients.stream().map(SequencePatternItem::formatFluidIngredient).forEach(costs::add);
            String machines = descriptor.routeKeys().stream().map(ResourceLocation::toString)
                    .collect(Collectors.joining(", "));
            String costText = costs.isEmpty() ? "-" : String.join(", ", costs);
            tooltip.add(Component.translatable("tooltip.sequenced_pattern_provider.sequence_pattern.step",
                            index + 1, descriptor.serializerId(), machines, costText)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
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

    private static String formatFluidIngredient(FluidIngredient ingredient) {
        return ingredient.getMatchingFluidStacks().stream().findFirst()
                .map(fluid -> fluid.getDisplayName().getString() + " " + ingredient.getRequiredAmount() + "mB")
                .orElse("?");
    }
}
