package io.github.createdelight.sequencedpatternprovider;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.blockentity.AEBaseBlockEntity;
import io.github.createdelight.sequencedpatternprovider.client.ClientColorRegistration;
import io.github.createdelight.sequencedpatternprovider.pattern.SequencePatternDecoder;
import io.github.createdelight.sequencedpatternprovider.part.SequenceEncodingTerminalPart;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(SequencedPatternProviderMod.MOD_ID)
public final class SequencedPatternProviderMod {
    public static final String MOD_ID = "sequenced_pattern_provider";
    public SequencedPatternProviderMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER,
                SequencedPatternProviderConfig.SERVER_SPEC);
        SequenceEncodingTerminalPart.registerModels();
        ModRegistry.register(modBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientColorRegistration.register(modBus);
        }
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::registerCapabilities);
        modBus.addListener(this::registerPartCapabilities);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            PatternDetailsHelper.registerDecoder(new SequencePatternDecoder());
            AEBaseBlockEntity.registerBlockEntityItem(ModRegistry.MASTER_PROVIDER_BE.get(), ModRegistry.MASTER_PROVIDER_ITEM.get());
            AEBaseBlockEntity.registerBlockEntityItem(ModRegistry.CHILD_PROVIDER_BE.get(), ModRegistry.CHILD_PROVIDER_ITEM.get());
        });
    }

    private void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModRegistry.MASTER_PROVIDER_BE.get(), (be, side) -> be);
        event.registerBlockEntity(appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModRegistry.CHILD_PROVIDER_BE.get(), (be, side) -> be);
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                ModRegistry.CHILD_PROVIDER_BE.get(), (be, side) -> be.inboundItemHandler());
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                ModRegistry.CHILD_PROVIDER_BE.get(), (be, side) -> be.inboundFluidHandler());
    }

    private void registerPartCapabilities(appeng.api.parts.RegisterPartCapabilitiesEvent event) {
        event.register(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                (part, side) -> part.getPatternInventory().toItemHandler(), SequenceEncodingTerminalPart.class);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
