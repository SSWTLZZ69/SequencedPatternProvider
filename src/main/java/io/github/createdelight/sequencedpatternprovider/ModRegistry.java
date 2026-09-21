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
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.codec.ByteBufCodecs;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

public final class ModRegistry {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, SequencedPatternProviderMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, SequencedPatternProviderMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SequencedPatternProviderMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, SequencedPatternProviderMod.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB, SequencedPatternProviderMod.MOD_ID);

    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS = DeferredRegister.create(
            Registries.DATA_COMPONENT_TYPE, SequencedPatternProviderMod.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CustomData>> EXPORTED_PROVIDER_SETTINGS =
            DATA_COMPONENTS.register("exported_provider_settings", () -> DataComponentType.<CustomData>builder()
                    .persistent(CustomData.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(CustomData.CODEC)).build());

    private static final BlockBehaviour.Properties PROVIDER_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.5F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops();

    public static final DeferredHolder<Block, Block> MASTER_PROVIDER = BLOCKS.register("master_pattern_provider",
            () -> new MasterProviderBlock(PROVIDER_PROPERTIES));
    public static final DeferredHolder<Block, Block> CHILD_PROVIDER = BLOCKS.register("child_pattern_provider",
            () -> new ChildProviderBlock(PROVIDER_PROPERTIES));

    public static final DeferredHolder<Item, Item> MASTER_PROVIDER_ITEM = ITEMS.register("master_pattern_provider",
            () -> new BlockItem(MASTER_PROVIDER.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> CHILD_PROVIDER_ITEM = ITEMS.register("child_pattern_provider",
            () -> new BlockItem(CHILD_PROVIDER.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> SEQUENCE_PATTERN = ITEMS.register("sequence_pattern",
            () -> new SequencePatternItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, PartItem<SequenceEncodingTerminalPart>> SEQUENCE_ENCODING_TERMINAL = ITEMS.register(
            "sequence_encoding_terminal",
            () -> new PartItem<>(new Item.Properties(), SequenceEncodingTerminalPart.class,
                    SequenceEncodingTerminalPart::new));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.sequenced_pattern_provider.main"))
                    .icon(() -> new ItemStack(MASTER_PROVIDER_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(MASTER_PROVIDER_ITEM.get());
                        output.accept(CHILD_PROVIDER_ITEM.get());
                        output.accept(SEQUENCE_ENCODING_TERMINAL.get());
                    })
                    .build());

    public static final DeferredHolder<MenuType<?>, MenuType<SequenceEncodingTerminalMenu>> SEQUENCE_ENCODING_TERMINAL_MENU = MENUS.register(
            "sequence_encoding_terminal",
            () -> IMenuTypeExtension.create(SequenceEncodingTerminalMenu::fromNetwork));
    public static final DeferredHolder<MenuType<?>, MenuType<MasterProviderMenu>> MASTER_PROVIDER_MENU = MENUS.register(
            "master_pattern_provider",
            () -> IMenuTypeExtension.create(MasterProviderMenu::fromNetwork));
    public static final DeferredHolder<MenuType<?>, MenuType<ChildProviderMenu>> CHILD_PROVIDER_MENU = MENUS.register(
            "child_pattern_provider",
            () -> IMenuTypeExtension.create(ChildProviderMenu::fromNetwork));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MasterProviderBlockEntity>> MASTER_PROVIDER_BE = BLOCK_ENTITIES.register(
            "master_pattern_provider", () -> BlockEntityType.Builder.of(MasterProviderBlockEntity::new, MASTER_PROVIDER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChildProviderBlockEntity>> CHILD_PROVIDER_BE = BLOCK_ENTITIES.register(
            "child_pattern_provider", () -> BlockEntityType.Builder.of(ChildProviderBlockEntity::new, CHILD_PROVIDER.get()).build(null));

    private ModRegistry() {
    }

    public static void register(IEventBus bus) {
        DATA_COMPONENTS.register(bus);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        CREATIVE_TABS.register(bus);
    }
}
