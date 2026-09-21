package io.github.createdelight.sequencedpatternprovider.block;

import appeng.items.tools.quartz.QuartzCuttingKnifeItem;
import appeng.util.InteractionUtil;
import io.github.createdelight.sequencedpatternprovider.blockentity.MasterProviderBlockEntity;
import io.github.createdelight.sequencedpatternprovider.interaction.ProviderMemoryCardInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.core.component.DataComponents;
import org.jetbrains.annotations.Nullable;

public final class MasterProviderBlock extends BaseEntityBlock {
    public static final MapCodec<MasterProviderBlock> CODEC = simpleCodec(MasterProviderBlock::new);

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult result = interact(state, level, pos, player, hand, hit);
        if (result == InteractionResult.PASS) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return result.consumesAction() ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.FAIL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                 BlockHitResult hit) {
        return interact(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
    }

    public MasterProviderBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (stack.has(DataComponents.CUSTOM_NAME)
                && level.getBlockEntity(pos) instanceof MasterProviderBlockEntity master) {
            master.setName(stack.getHoverName().getString());
            master.saveChanges();
        }
    }

    private InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
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
        InteractionResult memoryCardResult = ProviderMemoryCardInteraction.useOnMaster(
                level, pos, player, hand);
        if (memoryCardResult != InteractionResult.PASS) return memoryCardResult;
        if (hand != InteractionHand.MAIN_HAND || !(level.getBlockEntity(pos) instanceof MasterProviderBlockEntity master)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider((id, inventory, ignored) ->
                            new io.github.createdelight.sequencedpatternprovider.menu.MasterProviderMenu(
                                    id, inventory, master),
                            master.getName()),
                    buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MasterProviderBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return (tickerLevel, pos, tickerState, blockEntity) -> {
            if (blockEntity instanceof MasterProviderBlockEntity master) master.serverTick();
        };
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock() && level.getBlockEntity(pos) instanceof MasterProviderBlockEntity master) {
            master.onBlockRemoved();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
