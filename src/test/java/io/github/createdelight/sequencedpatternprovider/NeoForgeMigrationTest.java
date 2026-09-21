package io.github.createdelight.sequencedpatternprovider;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.items.tools.MemoryCardItem;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import io.github.createdelight.sequencedpatternprovider.item.SequencePatternItem;
import io.github.createdelight.sequencedpatternprovider.tracking.AttemptToken;
import io.github.createdelight.sequencedpatternprovider.tracking.AttemptTrackingBridge;
import io.github.createdelight.sequencedpatternprovider.tracking.FinalOutputDrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class NeoForgeMigrationTest {
    @Test
    void probabilityBatchWaitsForCreateBeltRemovalBeforeSendingNextBase(MinecraftServer server) {
        // A detached real Create belt reproduces the interval where extraction
        // reports an empty slot but insertion still rejects the next base.
        var belt = new com.simibubi.create.content.kinetics.belt.BeltBlockEntity(
                com.simibubi.create.AllBlockEntityTypes.BELT.get(), BlockPos.ZERO,
                com.simibubi.create.AllBlocks.BELT.getDefaultState()) {
            @Override
            public void notifyUpdate() {
                // This fixture has no world or rendering/network listeners.
            }
        };
        var inventory = belt.getInventory();
        var segment = new com.simibubi.create.content.kinetics.belt.transport.ItemHandlerBeltSegment(inventory, 0);
        ItemStack result = com.simibubi.create.AllItems.PRECISION_MECHANISM.asStack();
        var transported = new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(result);
        transported.beltPosition = .5f;
        transported.insertedAt = 0;
        inventory.getTransportedItems().add(transported);
        ItemStack nextBase = com.simibubi.create.AllItems.GOLDEN_SHEET.asStack();
        var emptyHand = new net.neoforged.neoforge.items.ItemStackHandler(1);
        assertEquals(1, segment.insertItem(0, nextBase.copy(), true).getCount());
        assertTrue(emptyHand.insertItem(0, nextBase.copy(), true).isEmpty(),
                "an occupied high-priority belt lets AE fall back to the empty deployer hand");

        var drain = new FinalOutputDrain(0);
        assertFalse(drain.isReady(0, () -> segment.getStackInSlot(0).isEmpty()));
        assertFalse(drain.isReady(2, () -> segment.getStackInSlot(0).isEmpty()));
        assertEquals(1, segment.extractItem(0, 1, false).getCount());
        assertTrue(segment.getStackInSlot(0).isEmpty());
        assertFalse(inventory.canInsertAt(0), "Create still has the old stack in its removal queue");
        assertFalse(drain.isReady(3, () -> segment.getStackInSlot(0).isEmpty()));
        inventory.tick();
        assertTrue(inventory.canInsertAt(0));
        assertFalse(drain.isReady(4, () -> segment.getStackInSlot(0).isEmpty()));
        assertTrue(drain.isReady(5, () -> segment.getStackInSlot(0).isEmpty()));
        assertTrue(segment.insertItem(0, nextBase.copy(), true).isEmpty(),
                "after drainage the next base is accepted by the belt, before the hand fallback");
    }

    @Test
    void recipesLoadInNeoForge(MinecraftServer server) {
        for (String id : new String[]{"master_pattern_provider", "child_pattern_provider", "sequence_encoding_terminal",
                "test/deterministic_deploying", "test/probability_80_pressing",
                "test/probability_18_2634_pressing", "test/probability_empty_pressing"}) {
            assertTrue(server.getRecipeManager().byKey(SequencedPatternProviderMod.id(id)).isPresent(), id);
        }
    }

    @Test
    void manualPatternSurvivesComponentSerialization(MinecraftServer server) {
        var registries = server.registryAccess();
        ItemStack input = new ItemStack(Items.IRON_INGOT);
        input.set(DataComponents.CUSTOM_NAME, Component.literal("component-bearing input"));
        var initial = new GenericStack(AEItemKey.of(input), 1);
        var recipeId = SequencedPatternProviderMod.id("test/deterministic_deploying");
        var recipe = (SequencedAssemblyRecipe) server.getRecipeManager().byKey(recipeId).orElseThrow().value();
        ItemStack resultStack = recipe.resultPool.get(0).getStack();
        var output = new GenericStack(AEItemKey.of(resultStack), resultStack.getCount());
        GenericStack[] materials = new GenericStack[SequencePatternItem.MAX_MANUAL_STEPS];
        materials[0] = new GenericStack(AEItemKey.of(Items.IRON_NUGGET), 2);
        ResourceLocation[] routes = new ResourceLocation[SequencePatternItem.MAX_MANUAL_STEPS];
        routes[0] = ResourceLocation.parse("create:deployer");
        ItemStack pattern = new ItemStack(ModRegistry.SEQUENCE_PATTERN.get());
        SequencePatternItem.encodeManual(pattern, recipeId, initial, materials, routes, 2, output, registries);
        ItemStack restored = ItemStack.parseOptional(registries, (CompoundTag) pattern.save(registries));
        assertEquals(recipeId, SequencePatternItem.getRecipeId(restored));
        var definition = SequencePatternItem.getManualDefinition(restored, registries);
        assertNotNull(definition);
        assertEquals(initial, definition.initialInput());
        assertEquals(materials[0], definition.stepMaterials()[0]);
        assertEquals(routes[0], definition.routeItems()[0]);
        assertEquals(2, definition.loops());
        assertEquals(output, definition.output());

        var tooltip = new java.util.ArrayList<Component>();
        assertDoesNotThrow(() -> ((SequencePatternItem) restored.getItem()).appendHoverText(
                restored, Item.TooltipContext.of(server.overworld()), tooltip, TooltipFlag.NORMAL));
        assertFalse(tooltip.isEmpty());
    }

    @Test
    void aeMemoryCardClearsExportedProviderSettings(MinecraftServer server) {
        ItemStack card = appeng.core.definitions.AEItems.MEMORY_CARD.stack();
        CompoundTag settings = new CompoundTag();
        settings.putLong("MasterPos", BlockPos.ZERO.asLong());
        card.set(ModRegistry.EXPORTED_PROVIDER_SETTINGS.get(), CustomData.of(settings));
        card.set(AEComponents.EXPORTED_SETTINGS_SOURCE, Component.literal("provider"));
        MemoryCardItem.clearCard(card);
        assertFalse(card.has(ModRegistry.EXPORTED_PROVIDER_SETTINGS.get()));
        assertFalse(card.has(AEComponents.EXPORTED_SETTINGS_SOURCE));
    }

    @Test
    void providerInventoriesAndLinksSurviveSaveLoad(MinecraftServer server) {
        var registries = server.registryAccess();
        var child = new io.github.createdelight.sequencedpatternprovider.blockentity.ChildProviderBlockEntity(
                BlockPos.ZERO, ModRegistry.CHILD_PROVIDER.get().defaultBlockState());
        ResourceLocation route = ResourceLocation.parse("create:deployer");
        child.addSupportedMachine(route);
        child.addLinkedMaster(new BlockPos(4, 5, 6));
        child.inboundItemHandler().insertItem(0, new ItemStack(Items.IRON_INGOT, 5), false);
        CompoundTag data = new CompoundTag();
        child.saveAdditional(data, registries);
        var restored = new io.github.createdelight.sequencedpatternprovider.blockentity.ChildProviderBlockEntity(
                BlockPos.ZERO, ModRegistry.CHILD_PROVIDER.get().defaultBlockState());
        restored.loadTag(data, registries);
        assertTrue(restored.supportsAny(java.util.Set.of(route)));
        assertTrue(restored.getLinkedMasters().contains(new BlockPos(4, 5, 6)));
        assertEquals(5, restored.getInboundItemCount());
        assertTrue(restored.inboundItemHandler().extractItem(0, 5, false).isEmpty(), "return buffer is insert-only");
        assertEquals(5, restored.getInboundItemCount());
        var master = new io.github.createdelight.sequencedpatternprovider.blockentity.MasterProviderBlockEntity(
                BlockPos.ZERO, ModRegistry.MASTER_PROVIDER.get().defaultBlockState());
        CompoundTag masterData = new CompoundTag();
        master.saveAdditional(masterData, registries);
        master.loadTag(masterData, registries);
        assertEquals(0, master.getActiveJobCount());
    }

    @Test
    void createAdvanceKeepsAttemptTokenAndNewAssemblyComponent(MinecraftServer server) throws Exception {
        var id = SequencedPatternProviderMod.id("test/deterministic_deploying");
        var recipe = (SequencedAssemblyRecipe) server.getRecipeManager().byKey(id).orElseThrow().value();
        assertInstanceOf(AttemptTrackingBridge.class, recipe, "Create mixin must actually be loaded");
        var advance = SequencedAssemblyRecipe.class.getDeclaredMethod("advance", ResourceLocation.class,
                ItemStack.class, RandomSource.class);
        advance.setAccessible(true);
        ItemStack input = recipe.getIngredient().getItems()[0].copy();
        UUID attempt = UUID.randomUUID();
        AttemptToken.write(input, ResourceLocation.parse("minecraft:overworld"), BlockPos.ZERO, attempt);
        ItemStack next = (ItemStack) advance.invoke(recipe, id, input, RandomSource.create(1));
        assertEquals(attempt, AttemptToken.read(next).attemptId());
        assertEquals(id, next.get(AllDataComponents.SEQUENCED_ASSEMBLY).id());
        assertEquals(1, next.get(AllDataComponents.SEQUENCED_ASSEMBLY).step());
        ItemStack restored = ItemStack.parseOptional(server.registryAccess(), (CompoundTag) next.save(server.registryAccess()));
        assertEquals(attempt, AttemptToken.read(restored).attemptId());
        AttemptToken.clear(restored);
        assertFalse(AttemptToken.has(restored));
        assertTrue(restored.has(AllDataComponents.SEQUENCED_ASSEMBLY));
    }
}
