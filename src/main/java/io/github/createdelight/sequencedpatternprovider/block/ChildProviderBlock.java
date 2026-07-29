package io.github.createdelight.sequencedpatternprovider.block;

import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.items.tools.quartz.QuartzCuttingKnifeItem;
import appeng.util.InteractionUtil;
import appeng.util.Platform;
import io.github.createdelight.sequencedpatternprovider.blockentity.ChildProviderBlockEntity;
import io.github.createdelight.sequencedpatternprovider.interaction.ProviderMemoryCardInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public final class ChildProviderBlock extends BaseEntityBlock {
    public static final EnumProperty<PushDirection> PUSH_DIRECTION = PatternProviderBlock.PUSH_DIRECTION;

    public ChildProviderBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PUSH_DIRECTION, PushDirection.ALL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(PUSH_DIRECTION);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof ChildProviderBlockEntity child && stack.hasCustomHoverName()) {
            child.setCustomName(stack.getHoverName());
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand == InteractionHand.MAIN_HAND
                && !ProviderMemoryCardInteraction.isMemoryCard(player.getMainHandItem())
                && ProviderMemoryCardInteraction.isMemoryCard(player.getOffhandItem())) {
            return InteractionResult.PASS;
        }
        if (hand == InteractionHand.MAIN_HAND
                && InteractionUtil.isInAlternateUseMode(player)
                && !(player.getMainHandItem().getItem() instanceof QuartzCuttingKnifeItem)
                && player.getOffhandItem().getItem() instanceof QuartzCuttingKnifeItem) {
            return InteractionResult.PASS;
        }
        if (InteractionUtil.isInAlternateUseMode(player)
                && player.getItemInHand(hand).getItem() instanceof QuartzCuttingKnifeItem) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof ChildProviderBlockEntity child)) {
            return InteractionResult.PASS;
        }
        InteractionResult memoryCardResult = ProviderMemoryCardInteraction.useOnChild(
                level, child, player, hand);
        if (memoryCardResult != InteractionResult.PASS) return memoryCardResult;
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (InteractionUtil.canWrenchRotate(held)) {
            if (!level.isClientSide) {
                setSide(level, pos, state, hit.getDirection());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player.isShiftKeyDown() && held.isEmpty()) {
            if (!level.isClientSide) {
                child.clearSupportedMachines();
                player.displayClientMessage(Component.translatable("message.sequenced_pattern_provider.child.capabilities_cleared"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        // Any registered item can act as a route marker.
        if (!held.isEmpty()) {
            var id = ForgeRegistries.ITEMS.getKey(held.getItem());
            if (id != null) {
                if (!level.isClientSide) {
                    child.addSupportedMachine(id);
                    player.displayClientMessage(Component.translatable("message.sequenced_pattern_provider.child.capability_added", id), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        if (held.isEmpty()) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                NetworkHooks.openScreen(serverPlayer,
                        new SimpleMenuProvider((id, inventory, ignored) ->
                                new io.github.createdelight.sequencedpatternprovider.menu.ChildProviderMenu(
                                        id, inventory, child), child.displayName()),
                        buffer -> buffer.writeBlockPos(pos));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    private static void setSide(Level level, BlockPos pos, BlockState state, Direction clickedSide) {
        Direction currentSide = state.getValue(PUSH_DIRECTION).getDirection();
        PushDirection nextDirection;
        if (currentSide == clickedSide.getOpposite()) {
            nextDirection = PushDirection.fromDirection(clickedSide);
        } else if (currentSide == clickedSide) {
            nextDirection = PushDirection.ALL;
        } else if (currentSide == null) {
            nextDirection = PushDirection.fromDirection(clickedSide.getOpposite());
        } else {
            nextDirection = PushDirection.fromDirection(Platform.rotateAround(currentSide, clickedSide));
        }

        level.setBlockAndUpdate(pos, state.setValue(PUSH_DIRECTION, nextDirection));
        if (level.getBlockEntity(pos) instanceof ChildProviderBlockEntity child) {
            child.onPushDirectionChanged();
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChildProviderBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return (tickerLevel, pos, tickerState, blockEntity) -> {
            if (blockEntity instanceof ChildProviderBlockEntity child) child.serverTick();
        };
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock() && level.getBlockEntity(pos) instanceof ChildProviderBlockEntity child) {
            child.dropStoredItems();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
