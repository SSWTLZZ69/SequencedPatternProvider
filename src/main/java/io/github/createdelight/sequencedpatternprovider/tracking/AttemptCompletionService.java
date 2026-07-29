package io.github.createdelight.sequencedpatternprovider.tracking;

import io.github.createdelight.sequencedpatternprovider.blockentity.MasterProviderBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

public final class AttemptCompletionService {
    private AttemptCompletionService() {
    }

    public static @Nullable ItemStack complete(ItemStack workpiece, ResourceLocation recipeId) {
        AttemptToken.Address address = AttemptToken.read(workpiece);
        if (address == null) return null;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, address.dimension());
        ServerLevel level = server.getLevel(dimensionKey);
        if (level == null || !(level.getBlockEntity(address.masterPos()) instanceof MasterProviderBlockEntity master)) {
            return null;
        }
        ItemStack result = master.completeAttempt(address.attemptId(), recipeId);
        if (result != null) AttemptToken.clear(result);
        return result;
    }
}
