package io.github.createdelight.sequencedpatternprovider.compat.jei;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import io.github.createdelight.sequencedpatternprovider.SequencedPatternProviderMod;
import io.github.createdelight.sequencedpatternprovider.menu.SequenceEncodingTerminalMenu;
import io.github.createdelight.sequencedpatternprovider.pattern.SequenceRecipeTransferData;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@JeiPlugin
public final class SequencedPatternProviderJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = SequencedPatternProviderMod.id("jei_plugin");
    private static final RecipeType<SequencedAssemblyRecipe> SEQUENCED_ASSEMBLY = RecipeType.create(
            "create", "sequenced_assembly", SequencedAssemblyRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new TransferHandler(registration.getTransferHelper()),
                SEQUENCED_ASSEMBLY);
    }

    private record TransferHandler(IRecipeTransferHandlerHelper helper)
            implements IRecipeTransferHandler<SequenceEncodingTerminalMenu, SequencedAssemblyRecipe> {
        @Override
        public Class<? extends SequenceEncodingTerminalMenu> getContainerClass() {
            return SequenceEncodingTerminalMenu.class;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<MenuType<SequenceEncodingTerminalMenu>> getMenuType() {
            return Optional.of((MenuType<SequenceEncodingTerminalMenu>)
                    io.github.createdelight.sequencedpatternprovider.ModRegistry.SEQUENCE_ENCODING_TERMINAL_MENU.get());
        }

        @Override
        public RecipeType<SequencedAssemblyRecipe> getRecipeType() {
            return SEQUENCED_ASSEMBLY;
        }

        @Override
        public @Nullable IRecipeTransferError transferRecipe(SequenceEncodingTerminalMenu menu,
                                                              SequencedAssemblyRecipe recipe,
                                                              IRecipeSlotsView recipeSlots,
                                                              Player player, boolean maxTransfer,
                                                              boolean doTransfer) {
            SequenceRecipeTransferData.ParseResult parsed = SequenceRecipeTransferData.parse(recipe);
            if (!parsed.successful()) {
                return helper.createUserErrorWithTooltip(Component.translatable(parsed.errorKey()));
            }
            if (doTransfer) menu.fillFromRecipe(recipe.getId());
            return null;
        }
    }
}
