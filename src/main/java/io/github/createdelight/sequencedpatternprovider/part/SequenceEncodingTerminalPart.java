package io.github.createdelight.sequencedpatternprovider.part;

import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.parts.PartModels;
import appeng.api.stacks.AEItemKey;
import appeng.parts.PartModel;
import appeng.parts.encoding.PatternEncodingTerminalPart;
import appeng.core.definitions.AEItems;
import appeng.util.ConfigInventory;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import io.github.createdelight.sequencedpatternprovider.ModRegistry;
import io.github.createdelight.sequencedpatternprovider.SequencedPatternProviderMod;
import io.github.createdelight.sequencedpatternprovider.item.SequencePatternItem;
import io.github.createdelight.sequencedpatternprovider.menu.SequenceEncodingTerminalMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import org.jetbrains.annotations.Nullable;

public final class SequenceEncodingTerminalPart extends PatternEncodingTerminalPart {
    private static final ResourceLocation MODEL_BASE = SequencedPatternProviderMod.id(
            "part/sequence_encoding_terminal_base");
    private static final ResourceLocation MODEL_OFF = SequencedPatternProviderMod.id(
            "part/sequence_encoding_terminal_off");
    private static final ResourceLocation MODEL_ON = SequencedPatternProviderMod.id(
            "part/sequence_encoding_terminal_on");
    private static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    private static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    private static final IPartModel MODELS_HAS_CHANNEL = new PartModel(
            MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    private final ConfigInventory materials = ConfigInventory.configStacks(SequencePatternItem.MAX_MANUAL_STEPS)
            .changeListener(this::onConfigurationChanged).allowOverstacking(true).build();
    private final ConfigInventory routes = ConfigInventory.configTypes(SequencePatternItem.MAX_MANUAL_STEPS)
            .slotFilter(key -> key instanceof AEItemKey).changeListener(this::onConfigurationChanged).build();
    private final ConfigInventory initialInput = ConfigInventory.configStacks(1)
            .slotFilter(key -> key instanceof AEItemKey).changeListener(this::onConfigurationChanged).allowOverstacking(true).build();
    private final ConfigInventory finalOutput = ConfigInventory.configStacks(1)
            .slotFilter(key -> key instanceof AEItemKey).changeListener(this::onConfigurationChanged).allowOverstacking(true).build();
    private final AppEngInternalInventory patternInventory = new AppEngInternalInventory(this, 2, 64,
            new IAEItemFilter() {
                @Override
                public boolean allowInsert(appeng.api.inventories.InternalInventory inv, int slot, ItemStack stack) {
                    return slot == 0 ? AEItems.BLANK_PATTERN.is(stack)
                            : slot == 1 && SequencePatternItem.isEncoded(stack);
                }
            });
    private int loops = 1;
    private @Nullable ResourceLocation selectedRecipeId;

    public SequenceEncodingTerminalPart(IPartItem<?> partItem) {
        super(partItem);
        patternInventory.setMaxStackSize(1, 1);
    }

    public static void registerModels() {
        PartModels.registerModels(MODEL_BASE, MODEL_OFF, MODEL_ON);
    }

    @Override
    public IPartModel getStaticModels() {
        return selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    public ConfigInventory getMaterials() {
        return materials;
    }

    public ConfigInventory getRoutes() {
        return routes;
    }

    public ConfigInventory getInitialInput() {
        return initialInput;
    }

    public ConfigInventory getFinalOutput() {
        return finalOutput;
    }

    public AppEngInternalInventory getPatternInventory() {
        return patternInventory;
    }

    public int getLoops() {
        return loops;
    }

    public void setLoops(int loops) {
        this.loops = Math.max(1, Math.min(64, loops));
        onConfigurationChanged();
    }

    public @Nullable ResourceLocation getSelectedRecipeId() {
        return selectedRecipeId;
    }

    public void setSelectedRecipeId(@Nullable ResourceLocation selectedRecipeId) {
        this.selectedRecipeId = selectedRecipeId;
        markForSave();
    }

    private void onConfigurationChanged() {
        selectedRecipeId = null;
        markForSave();
    }

    @Override
    public MenuType<?> getMenuType(Player player) {
        return ModRegistry.SEQUENCE_ENCODING_TERMINAL_MENU.get();
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer && isActive()) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider((id, inventory, ignored) ->
                            new SequenceEncodingTerminalMenu(id, inventory, this),
                            getName()),
                    buffer -> {
                        buffer.writeBlockPos(getBlockEntity().getBlockPos());
                        buffer.writeEnum(getSide());
                    });
        }
        return true;
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        for (ItemStack stack : patternInventory) {
            if (!stack.isEmpty()) drops.add(stack);
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        materials.clear();
        routes.clear();
        initialInput.clear();
        finalOutput.clear();
        patternInventory.clear();
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        materials.readFromChildTag(data, "SequenceMaterials", registries);
        routes.readFromChildTag(data, "SequenceRoutes", registries);
        initialInput.readFromChildTag(data, "SequenceInitial", registries);
        finalOutput.readFromChildTag(data, "SequenceOutput", registries);
        patternInventory.readFromNBT(data, "SequencePatterns", registries);
        loops = Math.max(1, data.getInt("SequenceLoops"));
        selectedRecipeId = ResourceLocation.tryParse(data.getString("SequenceSelectedRecipe"));
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        materials.writeToChildTag(data, "SequenceMaterials", registries);
        routes.writeToChildTag(data, "SequenceRoutes", registries);
        initialInput.writeToChildTag(data, "SequenceInitial", registries);
        finalOutput.writeToChildTag(data, "SequenceOutput", registries);
        patternInventory.writeToNBT(data, "SequencePatterns", registries);
        data.putInt("SequenceLoops", loops);
        if (selectedRecipeId != null) data.putString("SequenceSelectedRecipe", selectedRecipeId.toString());
    }

}
