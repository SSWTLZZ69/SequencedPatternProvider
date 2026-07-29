package io.github.createdelight.sequencedpatternprovider.pattern;

import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashSet;
import java.util.Set;

public record AssemblyStepDescriptor(ResourceLocation serializerId, Set<ResourceLocation> requiredMachines) {
    public static AssemblyStepDescriptor from(SequencedRecipe<?> step) {
        ResourceLocation serializerId = ForgeRegistries.RECIPE_SERIALIZERS.getKey(step.getRecipe().getSerializer());
        IAssemblyRecipe assembly = step.getAsAssemblyRecipe();
        Set<ItemLike> machines = new LinkedHashSet<>();
        assembly.addRequiredMachines(machines);
        Set<ResourceLocation> machineIds = new LinkedHashSet<>();
        for (ItemLike machine : machines) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(machine.asItem());
            if (id != null) machineIds.add(id);
        }
        return new AssemblyStepDescriptor(serializerId, Set.copyOf(machineIds));
    }

    public Set<ResourceLocation> routeKeys() {
        return requiredMachines.isEmpty() && serializerId != null ? Set.of(serializerId) : requiredMachines;
    }
}
