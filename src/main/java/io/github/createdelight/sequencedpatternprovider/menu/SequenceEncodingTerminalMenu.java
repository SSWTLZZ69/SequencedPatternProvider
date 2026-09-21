package io.github.createdelight.sequencedpatternprovider.menu;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import io.github.createdelight.sequencedpatternprovider.ModRegistry;
import io.github.createdelight.sequencedpatternprovider.SequencedPatternProviderConfig;
import io.github.createdelight.sequencedpatternprovider.item.SequencePatternItem;
import io.github.createdelight.sequencedpatternprovider.probability.ProbabilityPlan;
import io.github.createdelight.sequencedpatternprovider.pattern.SequenceRecipeTransferData;
import io.github.createdelight.sequencedpatternprovider.part.SequenceEncodingTerminalPart;
import net.minecraft.core.Direction;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;

public final class SequenceEncodingTerminalMenu extends MEStorageMenu {
    public static final SlotSemantic MATERIALS_FIRST = SlotSemantics.register("SPP_MATERIALS_FIRST", false);
    public static final SlotSemantic ROUTES_FIRST = SlotSemantics.register("SPP_ROUTES_FIRST", false);
    public static final SlotSemantic MATERIALS_SECOND = SlotSemantics.register("SPP_MATERIALS_SECOND", false);
    public static final SlotSemantic ROUTES_SECOND = SlotSemantics.register("SPP_ROUTES_SECOND", false);
    public static final SlotSemantic INITIAL_INPUT = SlotSemantics.register("SPP_INITIAL_INPUT", false);
    public static final SlotSemantic FINAL_OUTPUT = SlotSemantics.register("SPP_FINAL_OUTPUT", false);

    private static final String ACTION_ENCODE = "encodeSequence";
    private static final String ACTION_CLEAR = "clearSequence";
    private static final String ACTION_CHANGE_LOOPS = "changeLoops";
    private static final String ACTION_FILL_FROM_RECIPE = "fillFromRecipe";

    private final SequenceEncodingTerminalPart part;
    private final List<FakeSlot> materialSlots = new ArrayList<>();
    private final List<FakeSlot> routeSlots = new ArrayList<>();
    private final List<Slot> configurationSlots = new ArrayList<>();
    private final FakeSlot initialInputSlot;
    private final FakeSlot finalOutputSlot;
    private final AppEngSlot blankPatternSlot;
    private final AppEngSlot encodedPatternSlot;

    @GuiSync(40)
    public int loops = 1;

    public SequenceEncodingTerminalMenu(int id, Inventory inventory, SequenceEncodingTerminalPart part) {
        super(ModRegistry.SEQUENCE_ENCODING_TERMINAL_MENU.get(), id, inventory, part, true);
        this.part = part;

        var materials = part.getMaterials().createMenuWrapper();
        var routes = part.getRoutes().createMenuWrapper();
        for (int i = 0; i < 5; i++) {
            FakeSlot slot = (FakeSlot) addSlot(new FakeSlot(materials, i), MATERIALS_FIRST);
            materialSlots.add(slot);
            configurationSlots.add(slot);
        }
        for (int i = 0; i < 5; i++) {
            FakeSlot slot = (FakeSlot) addSlot(new FakeSlot(routes, i), ROUTES_FIRST);
            routeSlots.add(slot);
            configurationSlots.add(slot);
        }
        for (int i = 5; i < 10; i++) {
            FakeSlot slot = (FakeSlot) addSlot(new FakeSlot(materials, i), MATERIALS_SECOND);
            materialSlots.add(slot);
            configurationSlots.add(slot);
        }
        for (int i = 5; i < 10; i++) {
            FakeSlot slot = (FakeSlot) addSlot(new FakeSlot(routes, i), ROUTES_SECOND);
            routeSlots.add(slot);
            configurationSlots.add(slot);
        }

        initialInputSlot = (FakeSlot) addSlot(new FakeSlot(part.getInitialInput().createMenuWrapper(), 0), INITIAL_INPUT);
        finalOutputSlot = (FakeSlot) addSlot(new FakeSlot(part.getFinalOutput().createMenuWrapper(), 0), FINAL_OUTPUT);
        configurationSlots.add(initialInputSlot);
        configurationSlots.add(finalOutputSlot);
        initialInputSlot.setEmptyTooltip(() -> List.of(Component.translatable(
                "tooltip.sequenced_pattern_provider.encoder.initial_input")));
        finalOutputSlot.setEmptyTooltip(() -> List.of(Component.translatable(
                "tooltip.sequenced_pattern_provider.encoder.final_output")));
        blankPatternSlot = (AppEngSlot) addSlot(new AppEngSlot(part.getPatternInventory(), 0), SlotSemantics.BLANK_PATTERN);
        encodedPatternSlot = (AppEngSlot) addSlot(new AppEngSlot(part.getPatternInventory(), 1), SlotSemantics.ENCODED_PATTERN);
        for (int i = 0; i < materialSlots.size(); i++) {
            int step = i + 1;
            materialSlots.get(i).setEmptyTooltip(() -> List.of(Component.translatable(
                    "tooltip.sequenced_pattern_provider.encoder.material_slot", step)));
            routeSlots.get(i).setEmptyTooltip(() -> List.of(Component.translatable(
                    "tooltip.sequenced_pattern_provider.encoder.route_slot", step)));
        }
        registerClientAction(ACTION_ENCODE, this::encode);
        registerClientAction(ACTION_CLEAR, this::clearEncoding);
        registerClientAction(ACTION_CHANGE_LOOPS, Integer.class, this::changeLoopsServer);
        registerClientAction(ACTION_FILL_FROM_RECIPE, String.class, this::fillFromRecipeServer);
    }

    public static SequenceEncodingTerminalMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buffer) {
        var pos = buffer.readBlockPos();
        var side = buffer.readEnum(Direction.class);
        var found = appeng.api.parts.PartHelper.getPart(inventory.player.level(), pos, side);
        if (!(found instanceof SequenceEncodingTerminalPart part)) {
            throw new IllegalStateException("Sequence encoding terminal part is missing at " + pos + " / " + side);
        }
        return new SequenceEncodingTerminalMenu(id, inventory, part);
    }

    public boolean isMaterialSlot(net.minecraft.world.inventory.Slot slot) {
        return materialSlots.contains(slot);
    }

    public List<Slot> getConfigurationSlots() {
        return configurationSlots;
    }

    public Component getSlotHelp(Slot slot) {
        int index = materialSlots.indexOf(slot);
        if (index >= 0) {
            return Component.translatable("tooltip.sequenced_pattern_provider.encoder.material_slot", index + 1);
        }
        index = routeSlots.indexOf(slot);
        if (index >= 0) {
            return Component.translatable("tooltip.sequenced_pattern_provider.encoder.route_slot", index + 1);
        }
        if (slot == initialInputSlot) {
            return Component.translatable("tooltip.sequenced_pattern_provider.encoder.initial_input");
        }
        if (slot == finalOutputSlot) {
            return Component.translatable("tooltip.sequenced_pattern_provider.encoder.final_output");
        }
        return null;
    }

    public boolean canModifyAmountForSlot(net.minecraft.world.inventory.Slot slot) {
        return materialSlots.contains(slot) || slot == initialInputSlot || slot == finalOutputSlot;
    }

    public void encode() {
        if (isClientSide()) {
            sendClientAction(ACTION_ENCODE);
            return;
        }

        GenericStack initial = part.getInitialInput().getStack(0);
        GenericStack output = part.getFinalOutput().getStack(0);
        if (initial == null || !(initial.what() instanceof AEItemKey initialKey)
                || output == null || !(output.what() instanceof AEItemKey outputKey)) {
            fail("message.sequenced_pattern_provider.encoder.need_input_output");
            return;
        }

        int stepCount = 0;
        ResourceLocation[] routeIds = new ResourceLocation[SequencePatternItem.MAX_MANUAL_STEPS];
        GenericStack[] materials = new GenericStack[SequencePatternItem.MAX_MANUAL_STEPS];
        for (int i = 0; i < routeIds.length; i++) {
            var routeKey = part.getRoutes().getKey(i);
            if (routeKey instanceof AEItemKey itemKey) {
                routeIds[i] = BuiltInRegistries.ITEM.getKey(itemKey.getItem());
                stepCount = i + 1;
            }
            materials[i] = part.getMaterials().getStack(i);
        }
        if (stepCount == 0) {
            fail("message.sequenced_pattern_provider.encoder.need_step");
            return;
        }
        for (int i = 0; i < stepCount; i++) {
            if (routeIds[i] == null) {
                fail("message.sequenced_pattern_provider.encoder.route_gap");
                return;
            }
        }

        ItemStack initialStack = initialKey.toStack((int) Math.min(Integer.MAX_VALUE, initial.amount()));
        ItemStack outputStack = outputKey.toStack((int) Math.min(Integer.MAX_VALUE, output.amount()));
        int expectedSteps = stepCount;
        List<RecipeHolder<SequencedAssemblyRecipe>> matches = getPlayer().level().getRecipeManager()
                .<net.neoforged.neoforge.items.wrapper.RecipeWrapper, SequencedAssemblyRecipe>getAllRecipesFor(AllRecipeTypes.SEQUENCED_ASSEMBLY.getType()).stream()
                .filter(recipe -> recipe.value().getIngredient().test(initialStack))
                .filter(recipe -> recipe.value().getLoops() == part.getLoops())
                .filter(recipe -> recipe.value().getSequence().size() == expectedSteps)
                .filter(recipe -> recipe.value().resultPool.stream()
                        .anyMatch(result -> ItemStack.isSameItemSameComponents(result.getStack(), outputStack)))
                .toList();
        ResourceLocation selectedRecipeId = part.getSelectedRecipeId();
        if (selectedRecipeId != null) {
            matches = matches.stream().filter(recipe -> recipe.id().equals(selectedRecipeId)).toList();
        }
        if (matches.size() != 1) {
            fail(matches.isEmpty()
                    ? "message.sequenced_pattern_provider.encoder.no_recipe"
                    : "message.sequenced_pattern_provider.encoder.ambiguous_recipe", matches.size());
            return;
        }
        if (ProbabilityPlan.create(matches.get(0).value(), outputStack,
                SequencedPatternProviderConfig.maxActiveJobs()) == null) {
            fail("message.sequenced_pattern_provider.encoder.probability_unsupported",
                    SequencedPatternProviderConfig.maxActiveJobs());
            return;
        }

        ItemStack encoded = encodedPatternSlot.getItem();
        if (encoded.isEmpty()) {
            ItemStack blank = blankPatternSlot.getItem();
            if (!AEItems.BLANK_PATTERN.is(blank)) {
                fail("message.sequenced_pattern_provider.encoder.need_blank");
                return;
            }
            part.getPatternInventory().extractItem(0, 1, false);
            encoded = new ItemStack(ModRegistry.SEQUENCE_PATTERN.get());
        } else if (!SequencePatternItem.isEncoded(encoded)) {
            fail("message.sequenced_pattern_provider.encoder.output_blocked");
            return;
        } else {
            encoded = encoded.copy();
            encoded.setCount(1);
        }

        SequencePatternItem.encodeManual(encoded, matches.get(0).id(), initial,
                materials, routeIds, part.getLoops(), output, getPlayer().registryAccess());
        part.setSelectedRecipeId(matches.get(0).id());
        part.getPatternInventory().setItemDirect(1, encoded);
        part.markForSave();
        broadcastChanges();
    }

    public void clearEncoding() {
        if (isClientSide()) {
            sendClientAction(ACTION_CLEAR);
            return;
        }

        ItemStack encoded = encodedPatternSlot.getItem();
        if (encoded.getItem() instanceof SequencePatternItem) {
            part.getPatternInventory().setItemDirect(1, ItemStack.EMPTY);
            returnBlankPattern(AEItems.BLANK_PATTERN.stack(encoded.getCount()));
        }

        part.getMaterials().clear();
        part.getRoutes().clear();
        part.getInitialInput().clear();
        part.getFinalOutput().clear();
        part.setLoops(1);
        part.setSelectedRecipeId(null);
        loops = 1;
        part.markForSave();
        broadcastChanges();
    }

    private void returnBlankPattern(ItemStack returned) {
        ItemStack existing = blankPatternSlot.getItem();
        if (existing.isEmpty()) {
            part.getPatternInventory().setItemDirect(0, returned);
            return;
        }
        if (AEItems.BLANK_PATTERN.is(existing)
                && existing.getCount() + returned.getCount() <= existing.getMaxStackSize()) {
            ItemStack combined = existing.copy();
            combined.grow(returned.getCount());
            part.getPatternInventory().setItemDirect(0, combined);
            return;
        }
        if (!getPlayer().getInventory().add(returned)) {
            getPlayer().drop(returned, false);
        }
    }

    public void changeLoops(int delta) {
        if (isClientSide()) sendClientAction(ACTION_CHANGE_LOOPS, delta);
        else changeLoopsServer(delta);
    }

    public void fillFromRecipe(ResourceLocation recipeId) {
        if (isClientSide()) {
            sendClientAction(ACTION_FILL_FROM_RECIPE, recipeId.toString());
        } else {
            fillFromRecipeServer(recipeId.toString());
        }
    }

    private void fillFromRecipeServer(String recipeIdText) {
        ResourceLocation recipeId = ResourceLocation.tryParse(recipeIdText);
        if (recipeId == null || !(getPlayer().level().getRecipeManager().byKey(recipeId).map(RecipeHolder::value).orElse(null)
                instanceof SequencedAssemblyRecipe recipe)) {
            fail("message.sequenced_pattern_provider.jei.recipe_missing");
            return;
        }
        SequenceRecipeTransferData.ParseResult parsed = SequenceRecipeTransferData.parse(recipe);
        if (!parsed.successful()) {
            fail(parsed.errorKey());
            return;
        }
        SequenceRecipeTransferData data = parsed.data();
        part.getInitialInput().setStack(0, data.initialInput());
        part.getFinalOutput().setStack(0, data.output());
        for (int i = 0; i < SequencePatternItem.MAX_MANUAL_STEPS; i++) {
            part.getMaterials().setStack(i, data.materials()[i]);
            ResourceLocation route = data.routeItems()[i];
            var item = route == null ? null : BuiltInRegistries.ITEM.get(route);
            part.getRoutes().setStack(i, item == null || item == net.minecraft.world.item.Items.AIR
                    ? null : new GenericStack(AEItemKey.of(item), 1));
        }
        part.setLoops(data.loops());
        part.setSelectedRecipeId(recipeId);
        loops = part.getLoops();
        broadcastChanges();
    }

    private void changeLoopsServer(int delta) {
        part.setLoops(part.getLoops() + Math.max(-1, Math.min(1, delta)));
        loops = part.getLoops();
    }

    private void fail(String key, Object... args) {
        getPlayer().displayClientMessage(Component.translatable(key, args), true);
    }

    @Override
    public void onSlotChange(net.minecraft.world.inventory.Slot slot) {
        super.onSlotChange(slot);
        if (slot != encodedPatternSlot || !isServerSide()) return;
        SequencePatternItem.ManualDefinition manual = SequencePatternItem.getManualDefinition(slot.getItem(), getPlayer().registryAccess());
        if (manual == null) return;

        part.getInitialInput().setStack(0, manual.initialInput());
        part.getFinalOutput().setStack(0, manual.output());
        for (int i = 0; i < SequencePatternItem.MAX_MANUAL_STEPS; i++) {
            part.getMaterials().setStack(i, manual.stepMaterials()[i]);
            ResourceLocation route = manual.routeItems()[i];
            var item = route == null ? null : BuiltInRegistries.ITEM.get(route);
            part.getRoutes().setStack(i, item == null ? null : new GenericStack(AEItemKey.of(item), 1));
        }
        part.setLoops(manual.loops());
        part.setSelectedRecipeId(SequencePatternItem.getRecipeId(slot.getItem()));
        loops = part.getLoops();
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) loops = part.getLoops();
        super.broadcastChanges();
    }
}
