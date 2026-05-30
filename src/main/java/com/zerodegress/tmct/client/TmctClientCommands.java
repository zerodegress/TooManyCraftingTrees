package com.zerodegress.tmct.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.zerodegress.tmct.TooManyCraftingTrees;
import com.zerodegress.tmct.jei.JeiRecipeScanner;
import com.zerodegress.tmct.jei.JeiRecipeScanner.IngredientData;
import com.zerodegress.tmct.jei.JeiRecipeScanner.RecipeData;
import com.zerodegress.tmct.jei.JeiRecipeScanner.RecipeScan;
import com.zerodegress.tmct.tree.CraftingTreeCalculator;
import com.zerodegress.tmct.tree.SimpleTreeCalculator;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = TooManyCraftingTrees.MODID, value = Dist.CLIENT)
public final class TmctClientCommands {
    private static final Gson GSON = new GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create();
    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private TmctClientCommands() {
    }

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        register(event.getDispatcher(), event);
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("tmct");

        root.then(Commands.literal("dump-recipes")
            .executes(context -> dumpRecipes(context, false))
            .then(Commands.argument("includeHidden", BoolArgumentType.bool())
                .executes(context -> dumpRecipes(context, BoolArgumentType.getBool(context, "includeHidden")))));

        root.then(Commands.literal("tree")
            .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                .then(Commands.argument("count", LongArgumentType.longArg(1))
                    .executes(context -> exportTree(context, false, 16))
                    .then(Commands.argument("library", StringArgumentType.word())
                        .executes(context -> exportTree(
                            context,
                            false,
                            16,
                            StringArgumentType.getString(context, "library")
                        )))
                    .then(Commands.argument("includeHidden", BoolArgumentType.bool())
                        .executes(context -> exportTree(context, BoolArgumentType.getBool(context, "includeHidden"), 16))
                        .then(Commands.argument("library", StringArgumentType.word())
                            .executes(context -> exportTree(
                                context,
                                BoolArgumentType.getBool(context, "includeHidden"),
                                16,
                                StringArgumentType.getString(context, "library")
                            )))
                        .then(Commands.argument("maxDepth", IntegerArgumentType.integer(1, 64))
                            .executes(context -> exportTree(
                                context,
                                BoolArgumentType.getBool(context, "includeHidden"),
                                IntegerArgumentType.getInteger(context, "maxDepth")
                            ))
                            .then(Commands.argument("library", StringArgumentType.word())
                                .executes(context -> exportTree(
                                    context,
                                    BoolArgumentType.getBool(context, "includeHidden"),
                                    IntegerArgumentType.getInteger(context, "maxDepth"),
                                    StringArgumentType.getString(context, "library")
                                ))))))));

        root.then(Commands.literal("simpletree")
            .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                .then(Commands.argument("count", LongArgumentType.longArg(1))
                    .executes(context -> exportSimpleTree(context, false, 16))
                    .then(Commands.argument("library", StringArgumentType.word())
                        .executes(context -> exportSimpleTree(
                            context,
                            false,
                            16,
                            StringArgumentType.getString(context, "library")
                        )))
                    .then(Commands.argument("includeHidden", BoolArgumentType.bool())
                        .executes(context -> exportSimpleTree(context, BoolArgumentType.getBool(context, "includeHidden"), 16))
                        .then(Commands.argument("library", StringArgumentType.word())
                            .executes(context -> exportSimpleTree(
                                context,
                                BoolArgumentType.getBool(context, "includeHidden"),
                                16,
                                StringArgumentType.getString(context, "library")
                            )))
                        .then(Commands.argument("maxDepth", IntegerArgumentType.integer(1, 64))
                            .executes(context -> exportSimpleTree(
                                context,
                                BoolArgumentType.getBool(context, "includeHidden"),
                                IntegerArgumentType.getInteger(context, "maxDepth")
                            ))
                            .then(Commands.argument("library", StringArgumentType.word())
                                .executes(context -> exportSimpleTree(
                                    context,
                                    BoolArgumentType.getBool(context, "includeHidden"),
                                    IntegerArgumentType.getInteger(context, "maxDepth"),
                                    StringArgumentType.getString(context, "library")
                                ))))))));

        root.then(Commands.literal("library")
            .then(Commands.literal("list")
                .executes(TmctClientCommands::listLibraries))
            .then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(TmctClientCommands::createLibrary)))
            .then(Commands.literal("delete")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(TmctClientCommands::deleteLibrary)))
            .then(Commands.literal("show")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                        .executes(TmctClientCommands::showLibraryEntry))))
            .then(Commands.literal("set")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                        .then(Commands.argument("recipeId", StringArgumentType.greedyString())
                            .executes(TmctClientCommands::setLibraryEntry)))))
            .then(Commands.literal("clear")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                        .executes(TmctClientCommands::clearLibraryEntry))))
            .then(Commands.literal("candidates")
                .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                    .executes(TmctClientCommands::showCandidates))));

        dispatcher.register(root);
    }

    private static int dumpRecipes(CommandContext<CommandSourceStack> context, boolean includeHidden) {
        CommandSourceStack source = context.getSource();
        try {
            RecipeScan scan = JeiRecipeScanner.scanAll(source.registryAccess(), includeHidden);
            Path file = writeJson("jei-recipes-" + timestamp() + ".json", scan);
            source.sendSuccess(
                () -> Component.literal("Exported " + scan.recipeCount + " JEI recipes to " + file.toAbsolutePath()),
                false
            );
            return scan.recipeCount;
        } catch (Exception exception) {
            TooManyCraftingTrees.LOGGER.error("Failed to export JEI recipes", exception);
            source.sendFailure(Component.literal("Failed to export JEI recipes: " + exception.getMessage()));
            return 0;
        }
    }

    private static int exportTree(CommandContext<CommandSourceStack> context, boolean includeHidden, int maxDepth) {
        return exportTree(context, includeHidden, maxDepth, null);
    }

    private static int exportTree(CommandContext<CommandSourceStack> context, boolean includeHidden, int maxDepth, String libraryName) {
        CommandSourceStack source = context.getSource();
        try {
            ItemInput itemInput = ItemArgument.getItem(context, "item");
            long requestedAmount = LongArgumentType.getLong(context, "count");
            ItemStack targetStack = itemInput.createItemStack(1);

            RecipeScan scan = JeiRecipeScanner.scanAll(source.registryAccess(), includeHidden);
            IngredientData target = JeiRecipeScanner.describeItemStack(source.registryAccess(), targetStack);
            String selectedLibrary = libraryName == null ? null : requireLibrary(source, libraryName);
            var result = CraftingTreeCalculator.calculate(
                scan,
                target,
                requestedAmount,
                maxDepth,
                ingredientKey -> selectedRecipeFromLibrary(selectedLibrary, ingredientKey),
                selectedLibrary
            );

            String targetName = target.identifier == null ? "target" : target.identifier;
            Path file = writeJson("crafting-tree-" + safeFileName(targetName) + "-" + timestamp() + ".json", result);
            String librarySuffix = selectedLibrary == null ? "" : " using library " + selectedLibrary;
            source.sendSuccess(
                () -> Component.literal(
                    "Exported crafting tree for " + targetName + " x" + requestedAmount + librarySuffix + " to " + file.toAbsolutePath()
                ),
                false
            );
            return 1;
        } catch (Exception exception) {
            TooManyCraftingTrees.LOGGER.error("Failed to export crafting tree", exception);
            source.sendFailure(Component.literal("Failed to export crafting tree: " + exception.getMessage()));
            return 0;
        }
    }

    private static int exportSimpleTree(CommandContext<CommandSourceStack> context, boolean includeHidden, int maxDepth) {
        return exportSimpleTree(context, includeHidden, maxDepth, null);
    }

    private static int exportSimpleTree(CommandContext<CommandSourceStack> context, boolean includeHidden, int maxDepth, String libraryName) {
        CommandSourceStack source = context.getSource();
        try {
            ItemInput itemInput = ItemArgument.getItem(context, "item");
            long requestedAmount = LongArgumentType.getLong(context, "count");
            ItemStack targetStack = itemInput.createItemStack(1);

            RecipeScan scan = JeiRecipeScanner.scanAll(source.registryAccess(), includeHidden);
            IngredientData target = JeiRecipeScanner.describeItemStack(source.registryAccess(), targetStack);
            String selectedLibrary = libraryName == null ? null : requireLibrary(source, libraryName);
            var result = SimpleTreeCalculator.calculate(
                scan,
                target,
                requestedAmount,
                maxDepth,
                ingredientKey -> selectedRecipeFromLibrary(selectedLibrary, ingredientKey),
                selectedLibrary
            );

            String targetName = target.identifier == null ? "target" : target.identifier;
            Path file = writeJson("simple-tree-" + safeFileName(targetName) + "-" + timestamp() + ".json", result);
            String librarySuffix = selectedLibrary == null ? "" : " using library " + selectedLibrary;
            source.sendSuccess(
                () -> Component.literal(
                    "Exported simple tree for " + targetName + " x" + requestedAmount + librarySuffix + " to " + file.toAbsolutePath()
                ),
                false
            );
            return 1;
        } catch (Exception exception) {
            TooManyCraftingTrees.LOGGER.error("Failed to export simple tree", exception);
            source.sendFailure(Component.literal("Failed to export simple tree: " + exception.getMessage()));
            return 0;
        }
    }

    private static int listLibraries(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            List<String> names = RecipeLibraryStore.listLibraries();
            source.sendSuccess(() -> Component.literal("Recipe libraries: " + String.join(", ", names)), false);
            return names.size();
        } catch (Exception exception) {
            return failCommand(source, "Failed to list recipe libraries", exception);
        }
    }

    private static int createLibrary(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String libraryName = StringArgumentType.getString(context, "name");
        try {
            RecipeLibraryStore.createLibrary(libraryName);
            source.sendSuccess(() -> Component.literal("Created recipe library " + libraryName), false);
            return 1;
        } catch (Exception exception) {
            return failCommand(source, "Failed to create recipe library", exception);
        }
    }

    private static int deleteLibrary(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String libraryName = StringArgumentType.getString(context, "name");
        try {
            RecipeLibraryStore.deleteLibrary(libraryName);
            source.sendSuccess(() -> Component.literal("Deleted recipe library " + libraryName), false);
            return 1;
        } catch (Exception exception) {
            return failCommand(source, "Failed to delete recipe library", exception);
        }
    }

    private static int showLibraryEntry(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String libraryName = StringArgumentType.getString(context, "name");
        try {
            ItemStack stack = ItemArgument.getItem(context, "item").createItemStack(1);
            IngredientData ingredient = JeiRecipeScanner.describeItemStack(source.registryAccess(), stack);
            if (ingredient.key == null) {
                throw new IllegalArgumentException("Item has no stable JEI key.");
            }
            String selectedLibrary = requireLibrary(source, libraryName);
            Optional<String> selectedRecipe = RecipeLibraryStore.getSelectedRecipe(selectedLibrary, ingredient.key);
            String message = selectedRecipe
                .map(recipeId -> "Library " + selectedLibrary + " selects " + recipeId + " for " + displayName(ingredient))
                .orElse("Library " + selectedLibrary + " has no selection for " + displayName(ingredient));
            source.sendSuccess(() -> Component.literal(message), false);
            return selectedRecipe.isPresent() ? 1 : 0;
        } catch (Exception exception) {
            return failCommand(source, "Failed to show recipe library entry", exception);
        }
    }

    private static int setLibraryEntry(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String libraryName = StringArgumentType.getString(context, "name");
        String recipeId = StringArgumentType.getString(context, "recipeId");
        try {
            ItemStack stack = ItemArgument.getItem(context, "item").createItemStack(1);
            IngredientData ingredient = JeiRecipeScanner.describeItemStack(source.registryAccess(), stack);
            if (ingredient.key == null) {
                throw new IllegalArgumentException("Item has no stable JEI key.");
            }

            RecipeScan scan = JeiRecipeScanner.scanAll(source.registryAccess(), true);
            List<RecipeData> candidates = findCandidateRecipes(scan, ingredient);
            RecipeData selectedRecipe = candidates.stream()
                .filter(candidate -> recipeId.equals(candidate.selectorId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "Recipe " + recipeId + " is not a candidate for " + displayName(ingredient)
                ));

            RecipeLibraryStore.setSelectedRecipe(libraryName, ingredient.key, selectedRecipe.selectorId());
            source.sendSuccess(
                () -> Component.literal(
                    "Library " + libraryName + " selects " + selectedRecipe.selectorId() + " for " + displayName(ingredient)
                ),
                false
            );
            return 1;
        } catch (Exception exception) {
            return failCommand(source, "Failed to set recipe library entry", exception);
        }
    }

    private static int clearLibraryEntry(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String libraryName = StringArgumentType.getString(context, "name");
        try {
            ItemStack stack = ItemArgument.getItem(context, "item").createItemStack(1);
            IngredientData ingredient = JeiRecipeScanner.describeItemStack(source.registryAccess(), stack);
            if (ingredient.key == null) {
                throw new IllegalArgumentException("Item has no stable JEI key.");
            }

            String selectedLibrary = requireLibrary(source, libraryName);
            boolean removed = RecipeLibraryStore.clearSelectedRecipe(selectedLibrary, ingredient.key);
            String message = removed
                ? "Cleared recipe selection for " + displayName(ingredient) + " from library " + selectedLibrary
                : "Library " + selectedLibrary + " had no selection for " + displayName(ingredient);
            source.sendSuccess(() -> Component.literal(message), false);
            return removed ? 1 : 0;
        } catch (Exception exception) {
            return failCommand(source, "Failed to clear recipe library entry", exception);
        }
    }

    private static int showCandidates(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            ItemStack stack = ItemArgument.getItem(context, "item").createItemStack(1);
            IngredientData ingredient = JeiRecipeScanner.describeItemStack(source.registryAccess(), stack);
            if (ingredient.key == null) {
                throw new IllegalArgumentException("Item has no stable JEI key.");
            }

            RecipeScan scan = JeiRecipeScanner.scanAll(source.registryAccess(), true);
            List<RecipeData> candidates = findCandidateRecipes(scan, ingredient);
            if (candidates.isEmpty()) {
                source.sendSuccess(() -> Component.literal("No candidate recipes for " + displayName(ingredient)), false);
                return 0;
            }

            String message = candidates.stream()
                .map(RecipeData::selectorId)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
            source.sendSuccess(() -> Component.literal("Candidate recipes for " + displayName(ingredient) + ": " + message), false);
            return candidates.size();
        } catch (Exception exception) {
            return failCommand(source, "Failed to list candidate recipes", exception);
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

    private static String timestamp() {
        return LocalDateTime.now().format(FILE_TIME_FORMAT);
    }

    private static String safeFileName(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String requireLibrary(CommandSourceStack source, String libraryName) throws IOException {
        if (!RecipeLibraryStore.libraryExists(libraryName)) {
            throw new IllegalArgumentException("Recipe library does not exist: " + libraryName);
        }
        return libraryName;
    }

    private static Optional<String> selectedRecipeFromLibrary(String libraryName, JeiRecipeScanner.IngredientKey ingredientKey) {
        if (libraryName == null) {
            return Optional.empty();
        }
        try {
            return RecipeLibraryStore.getSelectedRecipe(libraryName, ingredientKey);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read recipe library " + libraryName, exception);
        }
    }

    private static List<RecipeData> findCandidateRecipes(RecipeScan scan, IngredientData ingredient) {
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

    private static String displayName(IngredientData ingredient) {
        return ingredient.displayName != null ? ingredient.displayName : ingredient.identifier;
    }

    private static int failCommand(CommandSourceStack source, String logMessage, Exception exception) {
        TooManyCraftingTrees.LOGGER.error(logMessage, exception);
        source.sendFailure(Component.literal(logMessage + ": " + exception.getMessage()));
        return 0;
    }
}
