package com.zerodegress.tmct.tree;

import com.zerodegress.tmct.jei.JeiRecipeScanner.IngredientKey;
import com.zerodegress.tmct.jei.JeiRecipeScanner.RecipeData;

import java.util.List;
import java.util.Optional;

public final class RecipeResolver {
    private RecipeResolver() {
    }

    public enum Status {
        SELECTED,
        NO_CANDIDATES,
        MISSING_LIBRARY_SELECTION,
        MISSING_LIBRARY_RECIPE,
        AMBIGUOUS
    }

    public static final class Resolution {
        public final Status status;
        public final RecipeData recipe;
        public final String selectionSource;
        public final List<RecipeData> candidates;
        public final String preferredRecipeId;

        private Resolution(Status status, RecipeData recipe, String selectionSource, List<RecipeData> candidates, String preferredRecipeId) {
            this.status = status;
            this.recipe = recipe;
            this.selectionSource = selectionSource;
            this.candidates = candidates;
            this.preferredRecipeId = preferredRecipeId;
        }

        public boolean isSelected() {
            return status == Status.SELECTED;
        }
    }

    public static Resolution resolve(
        RecipeIndex index,
        IngredientKey ingredientKey,
        RecipeSelector recipeSelector,
        boolean requireLibrarySelection
    ) {
        List<RecipeData> candidates = index.candidatesFor(ingredientKey);
        String preferredRecipeId = recipeSelector.selectedRecipeId(ingredientKey).orElse(null);

        if (candidates.isEmpty()) {
            return new Resolution(Status.NO_CANDIDATES, null, null, candidates, preferredRecipeId);
        }

        if (preferredRecipeId != null) {
            Optional<RecipeData> selectedRecipe = candidates.stream()
                .filter(candidate -> preferredRecipeId.equals(candidate.selectorId()))
                .findFirst();
            if (selectedRecipe.isEmpty()) {
                return new Resolution(Status.MISSING_LIBRARY_RECIPE, null, null, candidates, preferredRecipeId);
            }
            return new Resolution(Status.SELECTED, selectedRecipe.get(), "library", candidates, preferredRecipeId);
        }

        if (requireLibrarySelection) {
            return new Resolution(Status.MISSING_LIBRARY_SELECTION, null, null, candidates, null);
        }

        if (candidates.size() == 1) {
            return new Resolution(Status.SELECTED, candidates.getFirst(), "only_candidate", candidates, preferredRecipeId);
        }

        return new Resolution(Status.AMBIGUOUS, null, null, candidates, preferredRecipeId);
    }
}
