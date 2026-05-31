package com.zerodegress.tmct.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zerodegress.tmct.jei.JeiRecipeScanner;
import com.zerodegress.tmct.jei.JeiRecipeScanner.IngredientData;
import com.zerodegress.tmct.jei.JeiRecipeScanner.IngredientKey;
import com.zerodegress.tmct.jei.JeiRecipeScanner.RecipeData;
import com.zerodegress.tmct.jei.JeiRecipeScanner.RecipeScan;
import com.zerodegress.tmct.tree.CraftingTreeCalculator;
import com.zerodegress.tmct.tree.SimpleTreeCalculator;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class TmctClientRecipeService {
    private static final Gson GSON = new GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create();
    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final TmctClientRecipeService INSTANCE = new TmctClientRecipeService();

    private ClientLevel cachedLevel;
    private RecipeScan visibleScan;
    private RecipeScan hiddenScan;

    private TmctClientRecipeService() {
    }

    public static TmctClientRecipeService getInstance() {
        return INSTANCE;
    }

    public synchronized void invalidateCache() {
        cachedLevel = null;
        visibleScan = null;
        hiddenScan = null;
    }

    public IngredientData describeItemStack(HolderLookup.Provider registries, ItemStack stack) {
        return JeiRecipeScanner.describeItemStack(registries, stack);
    }

    public RecipeScan getOrBuildScan(HolderLookup.Provider registries, boolean includeHidden) {
        ClientLevel level = requireLevel();
        synchronized (this) {
            if (cachedLevel != level) {
                cachedLevel = level;
                visibleScan = null;
                hiddenScan = null;
            }

            RecipeScan cachedScan = includeHidden ? hiddenScan : visibleScan;
            if (cachedScan != null) {
                return cachedScan;
            }

            RecipeScan rebuiltScan = JeiRecipeScanner.scanAll(registries, includeHidden);
            if (includeHidden) {
                hiddenScan = rebuiltScan;
            } else {
                visibleScan = rebuiltScan;
            }
            return rebuiltScan;
        }
    }

    public CraftingTreeCalculator.CraftingTreeResult computeTree(
        HolderLookup.Provider registries,
        ItemStack targetStack,
        long requestedAmount,
        boolean includeHidden,
        int maxDepth,
        String recipeLibrary
    ) {
        RecipeScan scan = getOrBuildScan(registries, includeHidden);
        IngredientData target = describeItemStack(registries, targetStack);
        return computeTree(scan, target, requestedAmount, maxDepth, recipeLibrary);
    }

    public SimpleTreeCalculator.SimpleTreeResult computeSimpleTree(
        HolderLookup.Provider registries,
        ItemStack targetStack,
        long requestedAmount,
        boolean includeHidden,
        int maxDepth,
        String recipeLibrary
    ) {
        RecipeScan scan = getOrBuildScan(registries, includeHidden);
        IngredientData target = describeItemStack(registries, targetStack);
        return computeSimpleTree(scan, target, requestedAmount, maxDepth, recipeLibrary);
    }

    public SimpleTreeCalculator.SimpleTreeResult computeSimpleTree(
        HolderLookup.Provider registries,
        IngredientData target,
        long requestedAmount,
        boolean includeHidden,
        int maxDepth,
        String recipeLibrary
    ) {
        RecipeScan scan = getOrBuildScan(registries, includeHidden);
        return computeSimpleTree(scan, target, requestedAmount, maxDepth, recipeLibrary);
    }

    public IngredientData describeTypedIngredient(HolderLookup.Provider registries, mezz.jei.api.ingredients.ITypedIngredient<?> typedIngredient) {
        return JeiRecipeScanner.describeTypedIngredient(registries, typedIngredient);
    }

    public List<IngredientData> findCraftingStations(HolderLookup.Provider registries, String recipeTypeUid, boolean includeHidden) {
        return JeiRecipeScanner.findCraftingStations(registries, recipeTypeUid, includeHidden);
    }

    public Optional<ITypedIngredient<?>> getHoveredIngredient() {
        return JeiRecipeScanner.getHoveredIngredient();
    }

    public Optional<RecipeData> findRecipeByDisplayId(
        HolderLookup.Provider registries,
        String recipeType,
        String recipeId,
        boolean includeHidden
    ) {
        return getOrBuildScan(registries, includeHidden).recipes.stream()
            .filter(recipe -> recipeType.equals(recipe.recipeType))
            .filter(recipe -> recipeId.equals(recipe.displayId()))
            .findFirst();
    }

    public ExportedFile exportTree(
        HolderLookup.Provider registries,
        ItemStack targetStack,
        long requestedAmount,
        boolean includeHidden,
        int maxDepth,
        String recipeLibrary
    ) throws IOException {
        IngredientData target = describeItemStack(registries, targetStack);
        RecipeScan scan = getOrBuildScan(registries, includeHidden);
        CraftingTreeCalculator.CraftingTreeResult result = computeTree(scan, target, requestedAmount, maxDepth, recipeLibrary);
        String targetName = target.identifier == null ? "target" : target.identifier;
        Path file = writeJson("crafting-tree-" + safeFileName(targetName) + "-" + timestamp() + ".json", result);
        return new ExportedFile(file, targetName, requestedAmount, recipeLibrary);
    }

    public ExportedFile exportSimpleTree(
        HolderLookup.Provider registries,
        ItemStack targetStack,
        long requestedAmount,
        boolean includeHidden,
        int maxDepth,
        String recipeLibrary
    ) throws IOException {
        IngredientData target = describeItemStack(registries, targetStack);
        RecipeScan scan = getOrBuildScan(registries, includeHidden);
        SimpleTreeCalculator.SimpleTreeResult result = computeSimpleTree(scan, target, requestedAmount, maxDepth, recipeLibrary);
        String targetName = target.identifier == null ? "target" : target.identifier;
        Path file = writeJson("simple-tree-" + safeFileName(targetName) + "-" + timestamp() + ".json", result);
        return new ExportedFile(file, targetName, requestedAmount, recipeLibrary);
    }

    public Path exportRecipeScan(HolderLookup.Provider registries, boolean includeHidden) throws IOException {
        RecipeScan scan = getOrBuildScan(registries, includeHidden);
        return writeJson("jei-recipes-" + timestamp() + ".json", scan);
    }

    public List<RecipeData> findCandidateRecipes(HolderLookup.Provider registries, IngredientData ingredient, boolean includeHidden) {
        RecipeScan scan = getOrBuildScan(registries, includeHidden);
        return scan.recipes.stream()
            .filter(recipe -> recipe.outputIngredients().stream().anyMatch(output -> ingredient.key.equals(output.key)))
            .collect(java.util.stream.Collectors.toMap(
                RecipeData::selectorId,
                recipe -> recipe,
                (left, right) -> left,
                java.util.LinkedHashMap::new
            ))
            .values()
            .stream()
            .sorted(Comparator.comparing(RecipeData::selectorId))
            .toList();
    }

    public List<IngredientData> collectRecipeOutputs(String recipeLibrary, RecipeData recipe) throws IOException {
        return collectRecipeOutputs(
            recipeLibrary,
            recipe.selectorId(),
            recipe.selectedOutputIngredients()
        );
    }

    public List<IngredientData> collectRecipeOutputs(String recipeLibrary, String recipeSelectorId, List<IngredientData> outputs) throws IOException {
        List<IngredientData> keyedOutputs = outputs.stream()
            .filter(ingredient -> ingredient.key != null)
            .toList();
        if (keyedOutputs.isEmpty()) {
            throw new IllegalArgumentException("Recipe has no stable JEI output keys.");
        }

        List<IngredientData> collected = new ArrayList<>();
        for (IngredientData output : keyedOutputs) {
            RecipeLibraryStore.setSelectedRecipe(recipeLibrary, output.key, recipeSelectorId);
            collected.add(output);
        }
        return List.copyOf(collected);
    }

    private static CraftingTreeCalculator.CraftingTreeResult computeTree(
        RecipeScan scan,
        IngredientData target,
        long requestedAmount,
        int maxDepth,
        String recipeLibrary
    ) {
        return CraftingTreeCalculator.calculate(
            scan,
            target,
            requestedAmount,
            maxDepth,
            ingredientKey -> selectedRecipeFromLibrary(recipeLibrary, ingredientKey),
            recipeLibrary
        );
    }

    private static SimpleTreeCalculator.SimpleTreeResult computeSimpleTree(
        RecipeScan scan,
        IngredientData target,
        long requestedAmount,
        int maxDepth,
        String recipeLibrary
    ) {
        return SimpleTreeCalculator.calculate(
            scan,
            target,
            requestedAmount,
            maxDepth,
            ingredientKey -> selectedRecipeFromLibrary(recipeLibrary, ingredientKey),
            recipeLibrary
        );
    }

    private static Optional<String> selectedRecipeFromLibrary(String libraryName, IngredientKey ingredientKey) {
        if (libraryName == null) {
            return Optional.empty();
        }
        try {
            return RecipeLibraryStore.getSelectedRecipe(libraryName, ingredientKey);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read recipe library " + libraryName, exception);
        }
    }

    private static Path writeJson(String fileName, Object value) throws IOException {
        Path exportDir = Minecraft.getInstance().gameDirectory.toPath().resolve("tmct_exports");
        Files.createDirectories(exportDir);
        Path file = exportDir.resolve(fileName);
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(value, writer);
        }
        return file;
    }

    private static ClientLevel requireLevel() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            throw new IllegalStateException("Client level is not available. Join a world and try again.");
        }
        return level;
    }

    private static String timestamp() {
        return LocalDateTime.now().format(FILE_TIME_FORMAT);
    }

    private static String safeFileName(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public record ExportedFile(Path file, String targetName, long requestedAmount, String recipeLibrary) {
    }
}
