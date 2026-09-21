package io.github.createdelight.sequencedpatternprovider.compat.jade;

import io.github.createdelight.sequencedpatternprovider.SequencedPatternProviderMod;
import io.github.createdelight.sequencedpatternprovider.block.ChildProviderBlock;
import io.github.createdelight.sequencedpatternprovider.block.MasterProviderBlock;
import io.github.createdelight.sequencedpatternprovider.blockentity.ChildProviderBlockEntity;
import io.github.createdelight.sequencedpatternprovider.blockentity.MasterProviderBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin("sequenced_pattern_provider")
public final class SequencedPatternProviderJadePlugin implements IWailaPlugin {
    private static final StatusProvider STATUS_PROVIDER = new StatusProvider();

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(STATUS_PROVIDER, MasterProviderBlockEntity.class);
        registration.registerBlockDataProvider(STATUS_PROVIDER, ChildProviderBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(STATUS_PROVIDER, MasterProviderBlock.class);
        registration.registerBlockComponent(STATUS_PROVIDER, ChildProviderBlock.class);
    }

    private static final class StatusProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        private static final ResourceLocation UID = SequencedPatternProviderMod.id("jade_status");

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            BlockEntity blockEntity = accessor.getBlockEntity();
            if (blockEntity instanceof MasterProviderBlockEntity master) {
                writeOnlineStatus(data, master.getMainNode().isActive());
                data.putInt("Children", master.getChildCount());
                data.putInt("Patterns", master.getPatternCount());
                data.putInt("Jobs", master.getActiveJobCount());
            } else if (blockEntity instanceof ChildProviderBlockEntity child) {
                writeOnlineStatus(data, child.getMainNode().isActive());
                data.putInt("Masters", child.getLinkedMasters().size());
            }
        }

        private static void writeOnlineStatus(CompoundTag data, boolean online) {
            data.putBoolean("Online", online);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("Online")) return;

            boolean online = data.getBoolean("Online");
            tooltip.add(Component.translatable(online
                    ? "jade.sequenced_pattern_provider.status.online"
                    : "jade.sequenced_pattern_provider.status.offline"));

            if (data.contains("Children")) {
                tooltip.add(Component.translatable("jade.sequenced_pattern_provider.master.children",
                        data.getInt("Children")));
                tooltip.add(Component.translatable("jade.sequenced_pattern_provider.master.patterns",
                        data.getInt("Patterns"), 9));
                tooltip.add(Component.translatable("jade.sequenced_pattern_provider.master.jobs",
                        data.getInt("Jobs")));
            } else if (data.contains("Masters")) {
                tooltip.add(Component.translatable("jade.sequenced_pattern_provider.child.masters",
                        data.getInt("Masters")));
            }
        }
    }
}
