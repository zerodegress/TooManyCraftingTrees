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

public final class SimpleTreeCalculator {
    private SimpleTreeCalculator() {
    }

    public static SimpleTreeResult calculate(
        RecipeScan scan,
        IngredientData target,
        long requestedAmount,
        int maxDepth,
        RecipeSelector recipeSelector
    ) {
        if (target.key == null) {
            throw new IllegalArgumentException("Target ingredient has no stable JEI key.");
        }

        RecipeIndex index = RecipeIndex.create(scan);
        CalculationState state = new CalculationState();
        SimpleTreeNode tree = buildNode(index, state, target, requestedAmount, 0, maxDepth, new LinkedHashSet<>(), recipeSelector);

        SimpleTreeResult result = new SimpleTreeResult();
        result.generatedAt = Instant.now().toString();
        result.includeHidden = scan.includeHidden;
        result.maxDepth = maxDepth;
        result.brief = new SimpleRecipeSummary();
        result.brief.recipeType = tree == null ? null : tree.recipeType;
        result.brief.output = simpleItem(target, requestedAmount);
        result.brief.byproducts = amountsToItems(state.byproducts);
        result.brief.inputs = amountsToItems(state.baseMaterials);
        result.tree = tree;
        return result;
    }

    private static SimpleTreeNode buildNode(
        RecipeIndex index,
        CalculationState state,
        IngredientData ingredient,
        long requestedAmount,
        int depth,
        int maxDepth,
        Set<IngredientKey> path,
        RecipeSelector recipeSelector
    ) {
        SimpleTreeNode node = new SimpleTreeNode();
        node.output = simpleItem(ingredient, requestedAmount);

        if (ingredient.key == null) {
            node.type = "raw";
            addAmount(state.baseMaterials, ingredient, requestedAmount);
            return node;
        }

        if (path.contains(ingredient.key)) {
            return rawNode(state, ingredient, requestedAmount);
        }

        if (depth > maxDepth) {
            return rawNode(state, ingredient, requestedAmount);
        }

        RecipeResolver.Resolution resolution = RecipeResolver.resolve(index, ingredient.key, recipeSelector);
        if (!resolution.isSelected()) {
            return rawNode(state, ingredient, requestedAmount);
        }

        RecipeData recipe = resolution.recipe;
        long outputPerCraft = recipe.outputAmountFor(ingredient.key);
        if (outputPerCraft <= 0) {
            return rawNode(state, ingredient, requestedAmount);
        }

        long crafts = TreeMath.ceilDiv(requestedAmount, outputPerCraft);
        node.type = "recipe";
        node.recipeId = recipe.selectorId();
        node.recipeType = recipe.recipeType;
        node.crafts = crafts;
        node.byproducts = new ArrayList<>();
        node.inputs = new ArrayList<>();

        long surplusAmount = Math.max(0, TreeMath.safeMultiply(crafts, outputPerCraft) - requestedAmount);
        if (surplusAmount > 0) {
            IngredientData outputInfo = recipe.outputIngredients().stream()
                .filter(output -> ingredient.key.equals(output.key))
                .findFirst()
                .orElse(ingredient);
            addAmount(state.byproducts, outputInfo, surplusAmount);
            node.byproducts.add(simpleItem(outputInfo, surplusAmount));
        }

        for (IngredientData output : recipe.selectedOutputIngredients()) {
            if (!ingredient.key.equals(output.key)) {
                long byproductAmount = TreeMath.safeMultiply(output.craftAmount(), crafts);
                addAmount(state.byproducts, output, byproductAmount);
                node.byproducts.add(simpleItem(output, byproductAmount));
            }
        }

        path.add(ingredient.key);
        Map<String, AmountedIngredient> groupedInputs = new LinkedHashMap<>();
        for (RecipeSlotData slot : recipe.inputSlots()) {
            Optional<IngredientData> selected = slot.firstCraftableIngredient();
            if (selected.isEmpty()) {
                continue;
            }

            IngredientData selectedIngredient = selected.get();
            long requiredAmount = TreeMath.safeMultiply(selectedIngredient.craftAmount(), crafts);
            addAmount(groupedInputs, selectedIngredient, requiredAmount);
        }
        for (AmountedIngredient groupedInput : groupedInputs.values()) {
            node.inputs.add(buildNode(
                index,
                state,
                groupedInput.ingredient,
                groupedInput.ingredient.amount,
                depth + 1,
                maxDepth,
                path,
                recipeSelector
            ));
        }
        path.remove(ingredient.key);

        return node;
    }

    private static SimpleTreeNode rawNode(CalculationState state, IngredientData ingredient, long requestedAmount) {
        SimpleTreeNode node = new SimpleTreeNode();
        node.type = "raw";
        node.output = simpleItem(ingredient, requestedAmount);
        addAmount(state.baseMaterials, ingredient, requestedAmount);
        return node;
    }

    private static List<SimpleItem> amountsToItems(Map<String, AmountedIngredient> amounts) {
        return amounts.values().stream()
            .map(amounted -> simpleItem(amounted.ingredient, amounted.ingredient.amount))
            .toList();
    }

    private static SimpleItem simpleItem(IngredientData ingredient, long amount) {
        SimpleItem item = new SimpleItem();
        item.item = preferredIdentifier(ingredient);
        item.count = amount;
        return item;
    }

    private static String preferredIdentifier(IngredientData ingredient) {
        if (ingredient.item != null) {
            return ingredient.item;
        }
        if (ingredient.fluid != null) {
            return ingredient.fluid;
        }
        if (ingredient.identifier != null) {
            return ingredient.identifier;
        }
        if (ingredient.uid != null) {
            return ingredient.uid;
        }
        return ingredient.displayName;
    }

    private static void addAmount(Map<String, AmountedIngredient> amounts, IngredientData ingredient, long amount) {
        if (amount <= 0) {
            return;
        }
        String aggregationKey = aggregationKey(ingredient);
        if (aggregationKey == null) {
            return;
        }
        amounts.compute(aggregationKey, (key, existing) -> {
            if (existing == null) {
                return new AmountedIngredient(ingredient.withAmount(amount));
            }
            existing.ingredient.amount = TreeMath.safeAdd(existing.ingredient.amount, amount);
            return existing;
        });
    }

    private static String aggregationKey(IngredientData ingredient) {
        if (ingredient.key != null) {
            return ingredient.key.toString();
        }
        String identifier = preferredIdentifier(ingredient);
        if (identifier != null) {
            return "display:" + identifier;
        }
        return null;
    }

    private static final class CalculationState {
        private final Map<String, AmountedIngredient> baseMaterials = new LinkedHashMap<>();
        private final Map<String, AmountedIngredient> byproducts = new LinkedHashMap<>();
    }

    private static final class AmountedIngredient {
        private final IngredientData ingredient;

        private AmountedIngredient(IngredientData ingredient) {
            this.ingredient = ingredient;
        }
    }

    public static final class SimpleTreeResult {
        public String generatedAt;
        public boolean includeHidden;
        public int maxDepth;
        public SimpleRecipeSummary brief;
        public SimpleTreeNode tree;
    }

    public static final class SimpleRecipeSummary {
        public String recipeType;
        public SimpleItem output;
        public List<SimpleItem> byproducts = new ArrayList<>();
        public List<SimpleItem> inputs = new ArrayList<>();
    }

    public static final class SimpleTreeNode {
        public String type;
        public String recipeId;
        public String recipeType;
        public Long crafts;
        public SimpleItem output;
        public List<SimpleItem> byproducts;
        public List<SimpleTreeNode> inputs;
    }

    public static final class SimpleItem {
        public String item;
        public long count;
    }
}