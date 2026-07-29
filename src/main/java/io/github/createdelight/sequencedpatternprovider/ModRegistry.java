package io.github.createdelight.sequencedpatternprovider;

import io.github.createdelight.sequencedpatternprovider.block.ChildProviderBlock;
import io.github.createdelight.sequencedpatternprovider.block.MasterProviderBlock;
import io.github.createdelight.sequencedpatternprovider.blockentity.ChildProviderBlockEntity;
import io.github.createdelight.sequencedpatternprovider.blockentity.MasterProviderBlockEntity;
import io.github.createdelight.sequencedpatternprovider.item.SequencePatternItem;
import io.github.createdelight.sequencedpatternprovider.menu.SequenceEncodingTerminalMenu;
import io.github.createdelight.sequencedpatternprovider.menu.MasterProviderMenu;
import io.github.createdelight.sequencedpatternprovider.menu.ChildProviderMenu;
import io.github.createdelight.sequencedpatternprovider.part.SequenceEncodingTerminalPart;
import appeng.items.parts.PartItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.common.extensions.IForgeMenuType;

public final class ModRegistry {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SequencedPatternProviderMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SequencedPatternProviderMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SequencedPatternProviderMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, SequencedPatternProviderMod.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB, SequencedPatternProviderMod.MOD_ID);

    private static final BlockBehaviour.Properties PROVIDER_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.5F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops();

    public static final RegistryObject<Block> MASTER_PROVIDER = BLOCKS.register("master_pattern_provider",
            () -> new MasterProviderBlock(PROVIDER_PROPERTIES));
    public static final RegistryObject<Block> CHILD_PROVIDER = BLOCKS.register("child_pattern_provider",
            () -> new ChildProviderBlock(PROVIDER_PROPERTIES));

    public static final RegistryObject<Item> MASTER_PROVIDER_ITEM = ITEMS.register("master_pattern_provider",
            () -> new BlockItem(MASTER_PROVIDER.get(), new Item.Properties()));
    public static final RegistryObject<Item> CHILD_PROVIDER_ITEM = ITEMS.register("child_pattern_provider",
            () -> new BlockItem(CHILD_PROVIDER.get(), new Item.Properties()));
    public static final RegistryObject<Item> SEQUENCE_PATTERN = ITEMS.register("sequence_pattern",
            () -> new SequencePatternItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<PartItem<SequenceEncodingTerminalPart>> SEQUENCE_ENCODING_TERMINAL = ITEMS.register(
            "sequence_encoding_terminal",
            () -> new PartItem<>(new Item.Properties(), SequenceEncodingTerminalPart.class,
                    SequenceEncodingTerminalPart::new));

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.sequenced_pattern_provider.main"))
                    .icon(() -> new ItemStack(MASTER_PROVIDER_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(MASTER_PROVIDER_ITEM.get());
                        output.accept(CHILD_PROVIDER_ITEM.get());
                        output.accept(SEQUENCE_ENCODING_TERMINAL.get());
                    })
                    .build());

    public static final RegistryObject<MenuType<SequenceEncodingTerminalMenu>> SEQUENCE_ENCODING_TERMINAL_MENU = MENUS.register(
            "sequence_encoding_terminal",
            () -> IForgeMenuType.create(SequenceEncodingTerminalMenu::fromNetwork));
    public static final RegistryObject<MenuType<MasterProviderMenu>> MASTER_PROVIDER_MENU = MENUS.register(
            "master_pattern_provider",
            () -> IForgeMenuType.create(MasterProviderMenu::fromNetwork));
    public static final RegistryObject<MenuType<ChildProviderMenu>> CHILD_PROVIDER_MENU = MENUS.register(
            "child_pattern_provider",
            () -> IForgeMenuType.create(ChildProviderMenu::fromNetwork));

    public static final RegistryObject<BlockEntityType<MasterProviderBlockEntity>> MASTER_PROVIDER_BE = BLOCK_ENTITIES.register(
            "master_pattern_provider", () -> BlockEntityType.Builder.of(MasterProviderBlockEntity::new, MASTER_PROVIDER.get()).build(null));
    public static final RegistryObject<BlockEntityType<ChildProviderBlockEntity>> CHILD_PROVIDER_BE = BLOCK_ENTITIES.register(
            "child_pattern_provider", () -> BlockEntityType.Builder.of(ChildProviderBlockEntity::new, CHILD_PROVIDER.get()).build(null));

    private ModRegistry() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        CREATIVE_TABS.register(bus);
    }
}
