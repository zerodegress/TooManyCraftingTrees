package com.zerodegress.tmct.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.zerodegress.tmct.TooManyCraftingTrees;
import com.zerodegress.tmct.jei.JeiRecipeScanner.IngredientData;
import com.zerodegress.tmct.jei.JeiRecipeScanner.RecipeData;
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
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = TooManyCraftingTrees.MODID, value = Dist.CLIENT)
public final class TmctClientCommands {
    private static final TmctClientRecipeService RECIPE_SERVICE = TmctClientRecipeService.getInstance();

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

        root.then(buildTreeCommand("tree", event, false));
        root.then(buildTreeCommand("simpletree", event, true));

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

    private static LiteralArgumentBuilder<CommandSourceStack> buildTreeCommand(
        String literal,
        RegisterClientCommandsEvent event,
        boolean simple
    ) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal(literal);
        RequiredArgumentBuilder<CommandSourceStack, Long> count = Commands.argument("count", LongArgumentType.longArg(1));
        count.executes(context -> simple ? exportSimpleTree(context, false, 16) : exportTree(context, false, 16));
        attachTreeBranches(count, event, simple);
        command.then(Commands.argument("item", ItemArgument.item(event.getBuildContext())).then(count));
        return command;
    }

    private static void attachTreeBranches(
        RequiredArgumentBuilder<CommandSourceStack, Long> count,
        RegisterClientCommandsEvent event,
        boolean simple
    ) {
        LiteralArgumentBuilder<CommandSourceStack> library = Commands.literal("library");
        library.then(Commands.argument("library", StringArgumentType.word())
            .executes(context -> simple
                ? exportSimpleTree(context, false, 16, StringArgumentType.getString(context, "library"))
                : exportTree(context, false, 16, StringArgumentType.getString(context, "library"))));

        LiteralArgumentBuilder<CommandSourceStack> depth = Commands.literal("depth");
        RequiredArgumentBuilder<CommandSourceStack, Integer> maxDepth = Commands.argument("maxDepth", IntegerArgumentType.integer(1, 64));
        maxDepth.executes(context -> simple
            ? exportSimpleTree(context, false, IntegerArgumentType.getInteger(context, "maxDepth"))
            : exportTree(context, false, IntegerArgumentType.getInteger(context, "maxDepth")));
        LiteralArgumentBuilder<CommandSourceStack> depthLibrary = Commands.literal("library");
        depthLibrary.then(Commands.argument("library", StringArgumentType.word())
            .executes(context -> simple
                ? exportSimpleTree(
                    context,
                    false,
                    IntegerArgumentType.getInteger(context, "maxDepth"),
                    StringArgumentType.getString(context, "library")
                )
                : exportTree(
                    context,
                    false,
                    IntegerArgumentType.getInteger(context, "maxDepth"),
                    StringArgumentType.getString(context, "library")
                )));
        maxDepth.then(depthLibrary);
        depth.then(maxDepth);

        LiteralArgumentBuilder<CommandSourceStack> hidden = Commands.literal("hidden");
        RequiredArgumentBuilder<CommandSourceStack, Boolean> includeHidden = Commands.argument("includeHidden", BoolArgumentType.bool());
        includeHidden.executes(context -> simple
            ? exportSimpleTree(context, BoolArgumentType.getBool(context, "includeHidden"), 16)
            : exportTree(context, BoolArgumentType.getBool(context, "includeHidden"), 16));
        LiteralArgumentBuilder<CommandSourceStack> hiddenLibrary = Commands.literal("library");
        hiddenLibrary.then(Commands.argument("library", StringArgumentType.word())
            .executes(context -> simple
                ? exportSimpleTree(
                    context,
                    BoolArgumentType.getBool(context, "includeHidden"),
                    16,
                    StringArgumentType.getString(context, "library")
                )
                : exportTree(
                    context,
                    BoolArgumentType.getBool(context, "includeHidden"),
                    16,
                    StringArgumentType.getString(context, "library")
                )));
        includeHidden.then(hiddenLibrary);
        LiteralArgumentBuilder<CommandSourceStack> hiddenDepth = Commands.literal("depth");
        RequiredArgumentBuilder<CommandSourceStack, Integer> hiddenMaxDepth = Commands.argument("maxDepth", IntegerArgumentType.integer(1, 64));
        hiddenMaxDepth.executes(context -> simple
            ? exportSimpleTree(
                context,
                BoolArgumentType.getBool(context, "includeHidden"),
                IntegerArgumentType.getInteger(context, "maxDepth")
            )
            : exportTree(
                context,
                BoolArgumentType.getBool(context, "includeHidden"),
                IntegerArgumentType.getInteger(context, "maxDepth")
            ));
        LiteralArgumentBuilder<CommandSourceStack> hiddenDepthLibrary = Commands.literal("library");
        hiddenDepthLibrary.then(Commands.argument("library", StringArgumentType.word())
            .executes(context -> simple
                ? exportSimpleTree(
                    context,
                    BoolArgumentType.getBool(context, "includeHidden"),
                    IntegerArgumentType.getInteger(context, "maxDepth"),
                    StringArgumentType.getString(context, "library")
                )
                : exportTree(
                    context,
                    BoolArgumentType.getBool(context, "includeHidden"),
                    IntegerArgumentType.getInteger(context, "maxDepth"),
                    StringArgumentType.getString(context, "library")
                )));
        hiddenMaxDepth.then(hiddenDepthLibrary);
        hiddenDepth.then(hiddenMaxDepth);
        includeHidden.then(hiddenDepth);

        count.then(library);
        count.then(depth);
        count.then(hidden);
    }

    private static int dumpRecipes(CommandContext<CommandSourceStack> context, boolean includeHidden) {
        CommandSourceStack source = context.getSource();
        try {
            Path file = RECIPE_SERVICE.exportRecipeScan(source.registryAccess(), includeHidden);
            int recipeCount = RECIPE_SERVICE.getOrBuildScan(source.registryAccess(), includeHidden).recipeCount;
            source.sendSuccess(
                () -> Component.literal("Exported " + recipeCount + " JEI recipes to " + file.toAbsolutePath()),
                false
            );
            return recipeCount;
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

            String selectedLibrary = libraryName == null ? null : requireLibrary(source, libraryName);
            TmctClientRecipeService.ExportedFile export = RECIPE_SERVICE.exportTree(
                source.registryAccess(),
                targetStack,
                requestedAmount,
                includeHidden,
                maxDepth,
                selectedLibrary
            );
            String librarySuffix = selectedLibrary == null ? "" : " using library " + selectedLibrary;
            source.sendSuccess(
                () -> Component.literal(
                    "Exported crafting tree for "
                        + export.targetName()
                        + " x"
                        + requestedAmount
                        + librarySuffix
                        + " to "
                        + export.file().toAbsolutePath()
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

            String selectedLibrary = libraryName == null ? null : requireLibrary(source, libraryName);
            TmctClientRecipeService.ExportedFile export = RECIPE_SERVICE.exportSimpleTree(
                source.registryAccess(),
                targetStack,
                requestedAmount,
                includeHidden,
                maxDepth,
                selectedLibrary
            );
            String librarySuffix = selectedLibrary == null ? "" : " using library " + selectedLibrary;
            source.sendSuccess(
                () -> Component.literal(
                    "Exported simple tree for "
                        + export.targetName()
                        + " x"
                        + requestedAmount
                        + librarySuffix
                        + " to "
                        + export.file().toAbsolutePath()
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
            IngredientData ingredient = RECIPE_SERVICE.describeItemStack(source.registryAccess(), stack);
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
            IngredientData ingredient = RECIPE_SERVICE.describeItemStack(source.registryAccess(), stack);
            if (ingredient.key == null) {
                throw new IllegalArgumentException("Item has no stable JEI key.");
            }

            List<RecipeData> candidates = RECIPE_SERVICE.findCandidateRecipes(source.registryAccess(), ingredient, true);
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
            IngredientData ingredient = RECIPE_SERVICE.describeItemStack(source.registryAccess(), stack);
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
            IngredientData ingredient = RECIPE_SERVICE.describeItemStack(source.registryAccess(), stack);
            if (ingredient.key == null) {
                throw new IllegalArgumentException("Item has no stable JEI key.");
            }

            List<RecipeData> candidates = RECIPE_SERVICE.findCandidateRecipes(source.registryAccess(), ingredient, true);
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

    private static String requireLibrary(CommandSourceStack source, String libraryName) throws IOException {
        if (!RecipeLibraryStore.libraryExists(libraryName)) {
            throw new IllegalArgumentException("Recipe library does not exist: " + libraryName);
        }
        return libraryName;
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
