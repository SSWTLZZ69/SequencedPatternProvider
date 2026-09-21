package io.github.createdelight.sequencedpatternprovider.client;

import appeng.api.util.AEColor;
import io.github.createdelight.sequencedpatternprovider.ModRegistry;
import net.minecraft.util.FastColor;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.bus.api.IEventBus;

public final class ClientColorRegistration {
    private ClientColorRegistration() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientColorRegistration::registerScreens);
        modBus.addListener(ClientColorRegistration::registerItemColors);
    }

    public static void registerScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        appeng.init.client.InitScreens.register(event, ModRegistry.SEQUENCE_ENCODING_TERMINAL_MENU.get(),
                SequenceEncodingTerminalScreen::new, "/screens/sequenced_pattern_provider_terminal.json");
        appeng.init.client.InitScreens.register(event, ModRegistry.MASTER_PROVIDER_MENU.get(),
                MasterProviderScreen::new, "/screens/sequenced_pattern_provider_master.json");
        appeng.init.client.InitScreens.register(event, ModRegistry.CHILD_PROVIDER_MENU.get(),
                ChildProviderScreen::new, "/screens/sequenced_pattern_provider_child.json");
    }

    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> FastColor.ARGB32.opaque(
                        AEColor.TRANSPARENT.getVariantByTintIndex(tintIndex)),
                ModRegistry.SEQUENCE_ENCODING_TERMINAL.get());
    }
}
