package com.zerodegress.tmct.jei;

import com.zerodegress.tmct.TooManyCraftingTrees;
import com.zerodegress.tmct.client.RecipeLibraryStore;
import com.zerodegress.tmct.client.TmctClientRecipeService;
import com.zerodegress.tmct.jei.JeiRecipeScanner.IngredientData;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public final class TmctJeiRecipeLibraryButtonFactory implements IRecipeButtonControllerFactory {
    private static final float ICON_SCALE = 0.75F;
    private final IDrawable icon;

    public TmctJeiRecipeLibraryButtonFactory(IDrawable icon) {
        this.icon = icon;
    }

    @Override
    public <T> IIconButtonController createButtonController(IRecipeLayoutDrawable<T> recipeLayoutDrawable) {
        return new RecipeLibraryButtonController<>(icon, recipeLayoutDrawable);
    }

    public static TmctJeiRecipeLibraryButtonFactory create() {
        IDrawable icon = TmctJeiPlugin.getJeiHelpers()
            .getGuiHelper()
            .createDrawableItemLike(Items.WRITABLE_BOOK);
        return new TmctJeiRecipeLibraryButtonFactory(new ScaledDrawable(icon, ICON_SCALE));
    }

    private static final class RecipeLibraryButtonController<T> implements IIconButtonController {
        private final IDrawable icon;
        private final IRecipeLayoutDrawable<T> recipeLayoutDrawable;

        private RecipeLibraryButtonController(IDrawable icon, IRecipeLayoutDrawable<T> recipeLayoutDrawable) {
            this.icon = icon;
            this.recipeLayoutDrawable = recipeLayoutDrawable;
        }

        @Override
        public void initState(IButtonState state) {
            state.setIcon(icon);
        }

        @Override
        public void updateState(IButtonState state) {
            state.setIcon(icon);
            state.setForcePressed(this.isCurrentlySelected());
        }

        @Override
        public boolean onPress(IJeiUserInput input) {
            if (input.isSimulate()) {
                return true;
            }

            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || minecraft.player == null) {
                return true;
            }

            try {
                String activeLibrary = RecipeLibraryStore.getActiveLibrary();
                HolderLookup.Provider registries = minecraft.level.registryAccess();
                String selectorId = JeiRecipeScanner.recipeSelectorId(
                    recipeLayoutDrawable.getRecipeCategory(),
                    recipeLayoutDrawable.getRecipe()
                );
                List<IngredientData> outputs = JeiRecipeScanner.selectedOutputIngredients(
                    registries,
                    recipeLayoutDrawable.getRecipeSlotsView()
                );
                TmctClientRecipeService recipeService = TmctClientRecipeService.getInstance();
                if (recipeService.isRecipeSelected(activeLibrary, selectorId, outputs)) {
                    List<IngredientData> cleared = recipeService.clearRecipeOutputs(activeLibrary, outputs);
                    minecraft.player.sendSystemMessage(
                        Component.literal(
                            "Removed recipe " + selectorId + " from library " + activeLibrary + " for " + displayOutputs(cleared)
                        )
                    );
                } else {
                    List<IngredientData> collected = recipeService.collectRecipeOutputs(activeLibrary, selectorId, outputs);
                    minecraft.player.sendSystemMessage(
                        Component.literal(
                            "Collected recipe " + selectorId + " into library " + activeLibrary + " for " + displayOutputs(collected)
                        )
                    );
                }
            } catch (Exception exception) {
                TooManyCraftingTrees.LOGGER.error("Failed to collect JEI recipe into library", exception);
                minecraft.player.sendSystemMessage(Component.literal("Failed to collect recipe into library: " + exception.getMessage()));
            }
            return true;
        }

        @Override
        public void getTooltips(ITooltipBuilder tooltip) {
            try {
                String activeLibrary = RecipeLibraryStore.getActiveLibrary();
                tooltip.add(Component.literal(this.isCurrentlySelected() ? "Remove recipe from active library" : "Collect recipe into active library"));
                tooltip.add(Component.literal("Active library: " + activeLibrary));
            } catch (IOException exception) {
                tooltip.add(Component.literal("Active library unavailable"));
            }
        }

        private boolean isCurrentlySelected() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return false;
            }
            try {
                String activeLibrary = RecipeLibraryStore.getActiveLibrary();
                String selectorId = JeiRecipeScanner.recipeSelectorId(
                    recipeLayoutDrawable.getRecipeCategory(),
                    recipeLayoutDrawable.getRecipe()
                );
                List<IngredientData> outputs = JeiRecipeScanner.selectedOutputIngredients(
                    minecraft.level.registryAccess(),
                    recipeLayoutDrawable.getRecipeSlotsView()
                );
                return TmctClientRecipeService.getInstance().isRecipeSelected(activeLibrary, selectorId, outputs);
            } catch (Exception exception) {
                return false;
            }
        }

        private static String displayOutputs(List<IngredientData> outputs) {
            return outputs.stream()
                .map(ingredient -> ingredient.displayName != null ? ingredient.displayName : ingredient.identifier)
                .distinct()
                .collect(Collectors.joining(", "));
        }
    }

    private record ScaledDrawable(IDrawable delegate, float scale) implements IDrawable {
        @Override
        public int getWidth() {
            return delegate.getWidth();
        }

        @Override
        public int getHeight() {
            return delegate.getHeight();
        }

        @Override
        public void draw(net.minecraft.client.gui.GuiGraphicsExtractor guiGraphics, int xOffset, int yOffset) {
            int scaledWidth = Math.round(delegate.getWidth() * scale);
            int scaledHeight = Math.round(delegate.getHeight() * scale);
            float insetX = (delegate.getWidth() - scaledWidth) / 2.0F;
            float insetY = (delegate.getHeight() - scaledHeight) / 2.0F;

            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().translate(xOffset + insetX, yOffset + insetY);
            guiGraphics.pose().scale(scale, scale);
            delegate.draw(guiGraphics, 0, 0);
            guiGraphics.pose().popMatrix();
        }
    }
}
