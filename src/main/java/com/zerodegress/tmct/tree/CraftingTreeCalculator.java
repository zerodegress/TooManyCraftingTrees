package com.zerodegress.tmct.tree;

import com.zerodegress.tmct.jei.JeiRecipeScanner.IngredientData;
import com.zerodegress.tmct.jei.JeiRecipeScanner.IngredientKey;
import com.zerodegress.tmct.jei.JeiRecipeScanner.RecipeData;
import com.zerodegress.tmct.jei.JeiRecipeScanner.RecipeScan;
import com.zerodegress.tmct.jei.JeiRecipeScanner.RecipeSlotData;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CraftingTreeCalculator {
    private CraftingTreeCalculator() {
    }

    public static CraftingTreeResult calculate(RecipeScan scan, IngredientData target, long requestedAmount, int maxDepth) {
        return calculate(scan, target, requestedAmount, maxDepth, ingredientKey -> Optional.empty(), null);
    }

    public static CraftingTreeResult calculate(
        RecipeScan scan,
        IngredientData target,
        long requestedAmount,
        int maxDepth,
        RecipeSelector recipeSelector,
        String recipeLibrary
    ) {
        if (target.key == null) {
            throw new IllegalArgumentException("Target ingredient has no stable JEI key.");
        }

        RecipeIndex index = RecipeIndex.create(scan);
        CalculationState state = new CalculationState();
        CraftingTreeNode root = buildNode(index, state, target, requestedAmount, 0, maxDepth, new LinkedHashSet<>(), recipeSelector);

        CraftingTreeResult result = new CraftingTreeResult();
        result.generatedAt = Instant.now().toString();
        result.includeHidden = scan.includeHidden;
        result.recipeLibrary = recipeLibrary;
        result.maxDepth = maxDepth;
        result.scannedRecipeCount = scan.recipeCount;
        result.target = target.withAmount(requestedAmount);
        result.requestedAmount = requestedAmount;
        result.root = root;
        result.baseMaterials = List.copyOf(state.baseMaterials.values());
        result.byproducts = List.copyOf(state.byproducts.values());
        result.unresolved = List.copyOf(state.unresolved);
        return result;
    }

    private static CraftingTreeNode buildNode(
        RecipeIndex index,
        CalculationState state,
        IngredientData ingredient,
        long requestedAmount,
        int depth,
        int maxDepth,
        Set<IngredientKey> path,
        RecipeSelector recipeSelector
    ) {
        CraftingTreeNode node = new CraftingTreeNode();
        node.ingredient = ingredient.withAmount(requestedAmount);
        node.requestedAmount = requestedAmount;
        node.depth = depth;

        if (ingredient.key == null) {
            node.status = "unkeyed";
            state.unresolved.add(UnresolvedIngredient.of(ingredient, requestedAmount, "ingredient has no JEI key"));
            addAmount(state.baseMaterials, ingredient, requestedAmount);
            return node;
        }

        if (path.contains(ingredient.key)) {
            node.status = "cycle";
            state.unresolved.add(UnresolvedIngredient.of(ingredient, requestedAmount, "cycle detected"));
            return node;
        }

        if (depth > maxDepth) {
            node.status = "depth_limit";
            state.unresolved.add(UnresolvedIngredient.of(ingredient, requestedAmount, "max depth exceeded"));
            return node;
        }

        RecipeResolver.Resolution resolution = RecipeResolver.resolve(index, ingredient.key, recipeSelector);
        node.candidateRecipeCount = resolution.candidates.size();
        node.candidateRecipes = resolution.candidates.stream().map(RecipeData::selectorId).distinct().toList();

        switch (resolution.status) {
            case NO_CANDIDATES -> {
                node.status = "base";
                addAmount(state.baseMaterials, ingredient, requestedAmount);
                return node;
            }
            case MISSING_LIBRARY_RECIPE -> {
                node.status = "missing_library_recipe";
                state.unresolved.add(UnresolvedIngredient.of(
                    ingredient,
                    requestedAmount,
                    "recipe library selected unavailable recipe " + resolution.preferredRecipeId
                ));
                return node;
            }
            case AMBIGUOUS -> {
                node.status = "ambiguous";
                state.unresolved.add(UnresolvedIngredient.of(
                    ingredient,
                    requestedAmount,
                    "multiple candidate recipes require an explicit recipe library selection"
                ));
                return node;
            }
            case SELECTED -> {}
        }

        RecipeData recipe = resolution.recipe;
        long outputPerCraft = recipe.outputAmountFor(ingredient.key);
        if (outputPerCraft <= 0) {
            node.status = "invalid_recipe_output";
            state.unresolved.add(UnresolvedIngredient.of(ingredient, requestedAmount, "selected recipe has no matching output amount"));
            addAmount(state.baseMaterials, ingredient, requestedAmount);
            return node;
        }

        long crafts = TreeMath.ceilDiv(requestedAmount, outputPerCraft);
        long producedAmount = TreeMath.safeMultiply(crafts, outputPerCraft);

        node.status = "crafted";
        node.selectedRecipeId = recipe.selectorId();
        node.selectedRecipeType = recipe.recipeType;
        node.selectedRecipeSource = resolution.selectionSource;
        node.outputPerCraft = outputPerCraft;
        node.crafts = crafts;
        node.producedAmount = producedAmount;
        node.surplusAmount = Math.max(0, producedAmount - requestedAmount);

        if (node.surplusAmount > 0) {
            IngredientData outputInfo = recipe.outputIngredients().stream()
                .filter(output -> ingredient.key.equals(output.key))
                .findFirst()
                .orElse(ingredient);
            addAmount(state.byproducts, outputInfo, node.surplusAmount);
        }

        for (IngredientData output : recipe.selectedOutputIngredients()) {
            if (!ingredient.key.equals(output.key)) {
                addAmount(state.byproducts, output, TreeMath.safeMultiply(output.craftAmount(), crafts));
            }
        }

        path.add(ingredient.key);
        Map<IngredientKey, GroupedInput> groupedInputs = new LinkedHashMap<>();
        for (RecipeSlotData slot : recipe.inputSlots()) {
            Optional<IngredientData> selected = slot.firstCraftableIngredient();
            if (selected.isEmpty()) {
                CraftingTreeInput input = new CraftingTreeInput();
                input.slotName = slot.slotName;
                input.alternatives = slot.ingredients;
                input.status = "no_keyed_ingredient";
                state.unresolved.add(UnresolvedIngredient.of(ingredient, requestedAmount, "input slot has no keyed ingredient"));
                node.inputs.add(input);
                continue;
            }

            IngredientData selectedIngredient = selected.get();
            long requiredAmount = TreeMath.safeMultiply(selectedIngredient.craftAmount(), crafts);
            GroupedInput groupedInput = groupedInputs.get(selectedIngredient.key);
            if (groupedInput == null) {
                groupedInput = GroupedInput.create(slot.slotName, selectedIngredient, slot.ingredients);
                groupedInputs.put(selectedIngredient.key, groupedInput);
            } else {
                groupedInput.merge(slot.slotName, slot.ingredients);
            }
            groupedInput.requiredAmount = TreeMath.safeAdd(groupedInput.requiredAmount, requiredAmount);
        }
        for (GroupedInput groupedInput : groupedInputs.values()) {
            CraftingTreeInput input = groupedInput.toInput();
            input.child = buildNode(index, state, input.selected, input.requiredAmount, depth + 1, maxDepth, path, recipeSelector);
            node.inputs.add(input);
        }
        path.remove(ingredient.key);

        return node;
    }

    private static void addAmount(Map<IngredientKey, AmountedIngredient> amounts, IngredientData ingredient, long amount) {
        if (ingredient.key == null || amount <= 0) {
            return;
        }
        amounts.compute(ingredient.key, (key, existing) -> {
            if (existing == null) {
                return new AmountedIngredient(ingredient.withAmount(amount));
            }
            existing.ingredient.amount = TreeMath.safeAdd(existing.ingredient.amount, amount);
            return existing;
        });
    }

    private static void mergeAlternatives(CraftingTreeInput input, List<IngredientData> alternatives) {
        Map<String, IngredientData> merged = new LinkedHashMap<>();
        for (IngredientData existing : input.alternatives) {
            merged.put(alternativeKey(existing), existing);
        }
        for (IngredientData alternative : alternatives) {
            merged.putIfAbsent(alternativeKey(alternative), alternative);
        }
        input.alternatives = List.copyOf(merged.values());
    }

    private static String alternativeKey(IngredientData ingredient) {
        if (ingredient.key != null) {
            return ingredient.key.toString();
        }
        if (ingredient.identifier != null) {
            return ingredient.identifier;
        }
        if (ingredient.uid != null) {
            return ingredient.uid;
        }
        return ingredient.displayName;
    }

    private static final class CalculationState {
        private final Map<IngredientKey, AmountedIngredient> baseMaterials = new LinkedHashMap<>();
        private final Map<IngredientKey, AmountedIngredient> byproducts = new LinkedHashMap<>();
        private final List<UnresolvedIngredient> unresolved = new ArrayList<>();
    }

    public static final class CraftingTreeResult {
        public String generatedAt;
        public boolean includeHidden;
        public String recipeLibrary;
        public int maxDepth;
        public int scannedRecipeCount;
        public IngredientData target;
        public long requestedAmount;
        public CraftingTreeNode root;
        public List<AmountedIngredient> baseMaterials = new ArrayList<>();
        public List<AmountedIngredient> byproducts = new ArrayList<>();
        public List<UnresolvedIngredient> unresolved = new ArrayList<>();
    }

    public static final class CraftingTreeNode {
        public IngredientData ingredient;
        public long requestedAmount;
        public int depth;
        public String status;
        public String selectedRecipeId;
        public String selectedRecipeType;
        public String selectedRecipeSource;
        public int candidateRecipeCount;
        public List<String> candidateRecipes = new ArrayList<>();
        public long outputPerCraft;
        public long crafts;
        public long producedAmount;
        public long surplusAmount;
        public List<CraftingTreeInput> inputs = new ArrayList<>();
    }

    public static final class CraftingTreeInput {
        public String status;
        public String slotName;
        public IngredientData selected;
        public long requiredAmount;
        public List<IngredientData> alternatives = new ArrayList<>();
        public CraftingTreeNode child;
    }

    public static final class AmountedIngredient {
        public IngredientData ingredient;

        public AmountedIngredient(IngredientData ingredient) {
            this.ingredient = ingredient;
        }
    }

    public static final class UnresolvedIngredient {
        public IngredientData ingredient;
        public long amount;
        public String reason;

        public static UnresolvedIngredient of(IngredientData ingredient, long amount, String reason) {
            UnresolvedIngredient unresolved = new UnresolvedIngredient();
            unresolved.ingredient = ingredient.withAmount(amount);
            unresolved.amount = amount;
            unresolved.reason = reason;
            return unresolved;
        }
    }

    private static final class GroupedInput {
        private final CraftingTreeInput input = new CraftingTreeInput();
        private long requiredAmount;
        private int slotCount;

        private static GroupedInput create(String slotName, IngredientData selected, List<IngredientData> alternatives) {
            GroupedInput grouped = new GroupedInput();
            grouped.input.status = "selected";
            grouped.input.slotName = slotName;
            grouped.input.selected = selected;
            grouped.input.alternatives = List.copyOf(alternatives);
            grouped.slotCount = 1;
            return grouped;
        }

        private void merge(String slotName, List<IngredientData> alternatives) {
            slotCount++;
            if (slotCount > 1) {
                input.slotName = null;
            } else if (input.slotName != null && !input.slotName.equals(slotName)) {
                input.slotName = null;
            }
            mergeAlternatives(input, alternatives);
        }

        private CraftingTreeInput toInput() {
            input.requiredAmount = requiredAmount;
            return input;
        }
    }
}