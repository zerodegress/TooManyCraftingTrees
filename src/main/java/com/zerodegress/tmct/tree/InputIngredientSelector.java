package com.zerodegress.tmct.tree;

import com.zerodegress.tmct.jei.JeiRecipeScanner.IngredientData;
import com.zerodegress.tmct.jei.JeiRecipeScanner.RecipeSlotData;

import java.util.List;

final class InputIngredientSelector {
    private InputIngredientSelector() {
    }

    static Selection select(
        RecipeIndex index,
        RecipeSlotData slot,
        RecipeSelector recipeSelector,
        boolean requireLibrarySelection
    ) {
        List<IngredientData> keyedIngredients = slot.ingredients.stream()
            .filter(ingredient -> ingredient.key != null)
            .toList();
        if (keyedIngredients.isEmpty()) {
            return Selection.noKeyedIngredient();
        }

        if (slot.tag != null) {
            for (IngredientData ingredient : keyedIngredients) {
                RecipeResolver.Resolution resolution = RecipeResolver.resolve(index, ingredient.key, recipeSelector, requireLibrarySelection);
                if (resolution.isSelected() && "library".equals(resolution.selectionSource)) {
                    return Selection.selected(ingredient);
                }
            }
            if (requireLibrarySelection) {
                return Selection.missingLibrarySelection();
            }
        }

        return Selection.selected(keyedIngredients.getFirst());
    }

    static final class Selection {
        private static final Selection NO_KEYED_INGREDIENT = new Selection(null, "no_keyed_ingredient");
        private static final Selection MISSING_LIBRARY_SELECTION = new Selection(null, "missing_library_selection");

        final IngredientData ingredient;
        final String status;

        private Selection(IngredientData ingredient, String status) {
            this.ingredient = ingredient;
            this.status = status;
        }

        static Selection selected(IngredientData ingredient) {
            return new Selection(ingredient, "selected");
        }

        static Selection noKeyedIngredient() {
            return NO_KEYED_INGREDIENT;
        }

        static Selection missingLibrarySelection() {
            return MISSING_LIBRARY_SELECTION;
        }

        boolean isSelected() {
            return ingredient != null;
        }
    }
}
