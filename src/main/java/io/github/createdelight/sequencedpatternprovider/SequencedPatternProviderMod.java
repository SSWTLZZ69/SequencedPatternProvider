package io.github.createdelight.sequencedpatternprovider;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.blockentity.AEBaseBlockEntity;
import io.github.createdelight.sequencedpatternprovider.pattern.SequencePatternDecoder;
import io.github.createdelight.sequencedpatternprovider.part.SequenceEncodingTerminalPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;
import appeng.init.client.InitScreens;
import io.github.createdelight.sequencedpatternprovider.client.SequenceEncodingTerminalScreen;
import io.github.createdelight.sequencedpatternprovider.client.MasterProviderScreen;
import io.github.createdelight.sequencedpatternprovider.client.ChildProviderScreen;

@Mod(SequencedPatternProviderMod.MOD_ID)
public final class SequencedPatternProviderMod {
    public static final String MOD_ID = "sequenced_pattern_provider";
    public SequencedPatternProviderMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,
                SequencedPatternProviderConfig.SERVER_SPEC);
        SequenceEncodingTerminalPart.registerModels();
        ModRegistry.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.addListener(this::missingMappings);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            PatternDetailsHelper.registerDecoder(new SequencePatternDecoder());
            AEBaseBlockEntity.registerBlockEntityItem(ModRegistry.MASTER_PROVIDER_BE.get(), ModRegistry.MASTER_PROVIDER_ITEM.get());
            AEBaseBlockEntity.registerBlockEntityItem(ModRegistry.CHILD_PROVIDER_BE.get(), ModRegistry.CHILD_PROVIDER_ITEM.get());
        });
    }

    private void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            InitScreens.register(ModRegistry.SEQUENCE_ENCODING_TERMINAL_MENU.get(),
                    SequenceEncodingTerminalScreen::new, "/screens/sequenced_pattern_provider_terminal.json");
            InitScreens.register(ModRegistry.MASTER_PROVIDER_MENU.get(),
                    MasterProviderScreen::new, "/screens/sequenced_pattern_provider_master.json");
            InitScreens.register(ModRegistry.CHILD_PROVIDER_MENU.get(),
                    ChildProviderScreen::new, "/screens/sequenced_pattern_provider_child.json");
        });
    }

    private void missingMappings(MissingMappingsEvent event) {
        Item aeGuide = ForgeRegistries.ITEMS.getValue(new ResourceLocation("ae2", "guide"));
        Item aeMemoryCard = ForgeRegistries.ITEMS.getValue(new ResourceLocation("ae2", "memory_card"));
        for (var mapping : event.getMappings(Registries.ITEM, MOD_ID)) {
            if (mapping.getKey().getPath().equals("guide") && aeGuide != null) {
                mapping.remap(aeGuide);
            } else if (mapping.getKey().getPath().equals("provider_link") && aeMemoryCard != null) {
                mapping.remap(aeMemoryCard);
            }
        }
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
