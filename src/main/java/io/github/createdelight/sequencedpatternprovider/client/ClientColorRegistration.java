package io.github.createdelight.sequencedpatternprovider.client;

import appeng.api.util.AEColor;
import io.github.createdelight.sequencedpatternprovider.ModRegistry;
import io.github.createdelight.sequencedpatternprovider.SequencedPatternProviderMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SequencedPatternProviderMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientColorRegistration {
    private ClientColorRegistration() {
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> AEColor.TRANSPARENT.getVariantByTintIndex(tintIndex),
                ModRegistry.SEQUENCE_ENCODING_TERMINAL.get());
    }
}
