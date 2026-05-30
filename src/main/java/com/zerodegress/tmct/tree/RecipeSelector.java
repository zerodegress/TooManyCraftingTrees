package com.zerodegress.tmct.tree;

import com.zerodegress.tmct.jei.JeiRecipeScanner.IngredientKey;

import java.util.Optional;

@FunctionalInterface
public interface RecipeSelector {
    Optional<String> selectedRecipeId(IngredientKey ingredientKey);
}