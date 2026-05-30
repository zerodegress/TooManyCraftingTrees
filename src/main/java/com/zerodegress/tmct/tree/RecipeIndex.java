package com.zerodegress.tmct.tree;

import com.zerodegress.tmct.jei.JeiRecipeScanner.IngredientData;
import com.zerodegress.tmct.jei.JeiRecipeScanner.IngredientKey;
import com.zerodegress.tmct.jei.JeiRecipeScanner.RecipeData;
import com.zerodegress.tmct.jei.JeiRecipeScanner.RecipeScan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecipeIndex {
    private final Map<IngredientKey, List<RecipeData>> byOutput = new LinkedHashMap<>();

    private RecipeIndex() {
    }

    public static RecipeIndex create(RecipeScan scan) {
        RecipeIndex index = new RecipeIndex();
        List<RecipeData> sortedRecipes = scan.recipes.stream()
            .sorted(Comparator.comparing(RecipeData::displayId))
            .toList();
        for (RecipeData recipe : sortedRecipes) {
            for (IngredientData output : recipe.outputIngredients()) {
                if (output.key != null) {
                    index.byOutput.computeIfAbsent(output.key, key -> new ArrayList<>()).add(recipe);
                }
            }
        }
        return index;
    }

    public List<RecipeData> candidatesFor(IngredientKey key) {
        return byOutput.getOrDefault(key, List.of());
    }
}