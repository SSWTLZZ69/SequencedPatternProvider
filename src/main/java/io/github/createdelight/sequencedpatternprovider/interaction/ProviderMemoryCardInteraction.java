package io.github.createdelight.sequencedpatternprovider.interaction;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.util.InteractionUtil;
import appeng.api.ids.AEComponents;
import appeng.items.tools.MemoryCardItem;
import io.github.createdelight.sequencedpatternprovider.ModRegistry;
import net.minecraft.world.item.component.CustomData;
import io.github.createdelight.sequencedpatternprovider.blockentity.ChildProviderBlockEntity;
import io.github.createdelight.sequencedpatternprovider.blockentity.MasterProviderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Adds SPP-specific behavior to AE2 memory cards without requiring the blocks
 * to inherit AE2's block implementation.
 */
public final class ProviderMemoryCardInteraction {
    private static final String MASTER_LINK_SETTINGS =
            "memory_card.sequenced_pattern_provider.master_link";
    private static final String CHILD_SETTINGS =
            "memory_card.sequenced_pattern_provider.child_provider";
    private static final String MASTER_POS = "MasterPos";
    private static final String DIMENSION = "Dimension";
    private static final String CHILDREN = "Children";

    private ProviderMemoryCardInteraction() {
    }

    private static void setContents(ItemStack stack, String name, CompoundTag data) {
        MemoryCardItem.clearCard(stack);
        CompoundTag stored = data.copy();
        stored.putString("SettingsName", name);
        stack.set(ModRegistry.EXPORTED_PROVIDER_SETTINGS.get(), CustomData.of(stored));
        stack.set(AEComponents.EXPORTED_SETTINGS_SOURCE, Component.translatable(name));
    }

    private static CompoundTag getData(ItemStack stack) {
        return stack.getOrDefault(ModRegistry.EXPORTED_PROVIDER_SETTINGS.get(), CustomData.EMPTY).copyTag();
    }

    private static String getSettingsName(ItemStack stack) {
        return getData(stack).getString("SettingsName");
    }

    public static boolean isMemoryCard(ItemStack stack) {
        return stack.getItem() instanceof IMemoryCard;
    }

    public static InteractionResult useOnMaster(Level level, BlockPos pos, Player player,
                                                InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof IMemoryCard memoryCard)) {
            return InteractionResult.PASS;
        }

        if (InteractionUtil.isInAlternateUseMode(player)) {
            if (!level.isClientSide) {
                CompoundTag data = level.getBlockEntity(pos) instanceof MasterProviderBlockEntity master
                        ? master.exportMemoryCardSettings()
                        : new CompoundTag();
                if (!data.contains(DIMENSION, Tag.TAG_STRING)) {
                    data.putString(DIMENSION, level.dimension().location().toString());
                    data.putLong(MASTER_POS, pos.asLong());
                }
                setContents(stack, MASTER_LINK_SETTINGS, data);
                memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
            }
        } else if (!level.isClientSide) {
            String settingsName = getSettingsName(stack);
            CompoundTag data = getData(stack);
            if (MASTER_LINK_SETTINGS.equals(settingsName)
                    && data.contains(CHILDREN, Tag.TAG_LIST)
                    && level.getBlockEntity(pos) instanceof MasterProviderBlockEntity master) {
                importMasterLinks(level, master, player, data);
            } else {
                memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void importMasterLinks(Level level, MasterProviderBlockEntity master,
                                          Player player, CompoundTag data) {
        if (!level.dimension().location().toString().equals(data.getString(DIMENSION))) {
            player.displayClientMessage(Component.translatable(
                    "message.sequenced_pattern_provider.link.dimension"), true);
            return;
        }

        MasterProviderBlockEntity.ChildLinkImportResult result = master.importMemoryCardSettings(data);
        player.displayClientMessage(Component.translatable(
                "message.sequenced_pattern_provider.master.links_copied",
                result.added(), result.existing(), result.deferred(), result.skipped()), true);
    }

    public static InteractionResult useOnChild(Level level, ChildProviderBlockEntity child,
                                               Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof IMemoryCard memoryCard)) {
            return InteractionResult.PASS;
        }

        if (InteractionUtil.isInAlternateUseMode(player)) {
            if (!level.isClientSide) {
                setContents(stack, CHILD_SETTINGS,
                        child.exportMemoryCardSettings());
                memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide) {
            String settingsName = getSettingsName(stack);
            CompoundTag data = getData(stack);
            if (MASTER_LINK_SETTINGS.equals(settingsName)) {
                linkChild(level, child, player, data);
            } else if (CHILD_SETTINGS.equals(settingsName)) {
                child.importMemoryCardSettings(data);
                memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
            } else {
                memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void linkChild(Level level, ChildProviderBlockEntity child, Player player,
                                  CompoundTag data) {
        if (!level.dimension().location().toString().equals(data.getString(DIMENSION))) {
            player.displayClientMessage(Component.translatable(
                    "message.sequenced_pattern_provider.link.dimension"), true);
            return;
        }

        BlockPos masterPos = BlockPos.of(data.getLong(MASTER_POS));
        if (!(level.getBlockEntity(masterPos) instanceof MasterProviderBlockEntity master)) {
            player.displayClientMessage(Component.translatable(
                    "message.sequenced_pattern_provider.link.master_missing"), true);
            return;
        }

        if (!master.isLinkNetworkCompatible(child)) {
            player.displayClientMessage(Component.translatable(
                    "message.sequenced_pattern_provider.link.network"), true);
            return;
        }

        boolean added = master.addChild(child.getBlockPos());
        player.displayClientMessage(Component.translatable(added
                ? "message.sequenced_pattern_provider.link.added"
                : "message.sequenced_pattern_provider.link.exists", child.displayName()), true);
    }
}
