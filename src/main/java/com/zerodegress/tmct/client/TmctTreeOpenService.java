package com.zerodegress.tmct.client;

import com.zerodegress.tmct.client.gui.TreeScreen;
import com.zerodegress.tmct.jei.JeiRecipeScanner.IngredientData;
import com.zerodegress.tmct.tree.SimpleTreeCalculator;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

public final class TmctTreeOpenService {
    public static final int DEFAULT_MAX_DEPTH = 16;
    public static final boolean DEFAULT_INCLUDE_HIDDEN = false;
    public static final long DEFAULT_REQUESTED_AMOUNT = 1L;

    private static final TmctTreeOpenService INSTANCE = new TmctTreeOpenService();
    private static final Logger LOGGER = com.zerodegress.tmct.TooManyCraftingTrees.LOGGER;

    private TmctTreeOpenService() {
    }

    public static TmctTreeOpenService getInstance() {
        return INSTANCE;
    }

    public void openFromHoveredIngredient() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        try {
            ITypedIngredient<?> hovered = TmctClientRecipeService.getInstance()
                .getHoveredIngredient()
                .orElseThrow(() -> new IllegalStateException("No JEI ingredient is currently hovered."));
            IngredientData target = TmctClientRecipeService.getInstance()
                .describeTypedIngredient(minecraft.level.registryAccess(), hovered);
            this.openForIngredient(minecraft.level.registryAccess(), target, DEFAULT_REQUESTED_AMOUNT, RecipeLibraryStore.getActiveLibrary());
        } catch (Exception exception) {
            LOGGER.error("Failed to open TMCT tree screen", exception);
            minecraft.player.sendSystemMessage(Component.literal("Failed to open TMCT tree: " + exception.getMessage()));
        }
    }

    public void openForItemStack(HolderLookup.Provider registries, ItemStack stack, long requestedAmount, String recipeLibrary) {
        IngredientData target = TmctClientRecipeService.getInstance().describeItemStack(registries, stack);
        this.openForIngredient(registries, target, requestedAmount, recipeLibrary);
    }

    public void openForIngredient(HolderLookup.Provider registries, IngredientData target, long requestedAmount, String recipeLibrary) {
        Minecraft minecraft = Minecraft.getInstance();
        SimpleTreeCalculator.SimpleTreeResult result = TmctClientRecipeService.getInstance().computeSimpleTree(
            registries,
            target,
            requestedAmount,
            DEFAULT_INCLUDE_HIDDEN,
            DEFAULT_MAX_DEPTH,
            recipeLibrary
        );
        Screen parent = minecraft.screen;
        minecraft.setScreen(new TreeScreen(parent, target, recipeLibrary, DEFAULT_MAX_DEPTH, DEFAULT_INCLUDE_HIDDEN, result));
    }
}
