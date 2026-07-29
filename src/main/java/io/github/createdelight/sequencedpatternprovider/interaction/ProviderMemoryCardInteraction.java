package io.github.createdelight.sequencedpatternprovider.interaction;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.util.InteractionUtil;
import io.github.createdelight.sequencedpatternprovider.blockentity.ChildProviderBlockEntity;
import io.github.createdelight.sequencedpatternprovider.blockentity.MasterProviderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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

    private ProviderMemoryCardInteraction() {
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
                CompoundTag data = new CompoundTag();
                data.putString(DIMENSION, level.dimension().location().toString());
                data.putLong(MASTER_POS, pos.asLong());
                memoryCard.setMemoryCardContents(stack, MASTER_LINK_SETTINGS, data);
                memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
            }
        } else if (!level.isClientSide) {
            memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static InteractionResult useOnChild(Level level, ChildProviderBlockEntity child,
                                               Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof IMemoryCard memoryCard)) {
            return InteractionResult.PASS;
        }

        if (InteractionUtil.isInAlternateUseMode(player)) {
            if (!level.isClientSide) {
                memoryCard.setMemoryCardContents(stack, CHILD_SETTINGS,
                        child.exportMemoryCardSettings());
                memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide) {
            String settingsName = memoryCard.getSettingsName(stack);
            CompoundTag data = memoryCard.getData(stack);
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

        boolean added = master.addChild(child.getBlockPos());
        player.displayClientMessage(Component.translatable(added
                ? "message.sequenced_pattern_provider.link.added"
                : "message.sequenced_pattern_provider.link.exists", child.displayName()), true);
    }
}
