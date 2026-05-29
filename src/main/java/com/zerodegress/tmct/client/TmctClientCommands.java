package com.zerodegress.tmct.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.zerodegress.tmct.TooManyCraftingTrees;
import com.zerodegress.tmct.jei.JeiRecipeScanner;
import com.zerodegress.tmct.jei.JeiRecipeScanner.IngredientData;
import com.zerodegress.tmct.jei.JeiRecipeScanner.RecipeScan;
import com.zerodegress.tmct.tree.CraftingTreeCalculator;
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
        dispatcher.register(
            Commands.literal("tmct")
                .then(Commands.literal("dump-recipes")
                    .executes(context -> dumpRecipes(context, false))
                    .then(Commands.argument("includeHidden", BoolArgumentType.bool())
                        .executes(context -> dumpRecipes(context, BoolArgumentType.getBool(context, "includeHidden")))))
                .then(Commands.literal("tree")
                    .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                        .then(Commands.argument("count", LongArgumentType.longArg(1))
                            .executes(context -> exportTree(context, false, 16))
                            .then(Commands.argument("includeHidden", BoolArgumentType.bool())
                                .executes(context -> exportTree(context, BoolArgumentType.getBool(context, "includeHidden"), 16))
                                .then(Commands.argument("maxDepth", IntegerArgumentType.integer(1, 64))
                                    .executes(context -> exportTree(
                                        context,
                                        BoolArgumentType.getBool(context, "includeHidden"),
                                        IntegerArgumentType.getInteger(context, "maxDepth")
                                    )))))))
        );
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
        CommandSourceStack source = context.getSource();
        try {
            ItemInput itemInput = ItemArgument.getItem(context, "item");
            long requestedAmount = LongArgumentType.getLong(context, "count");
            ItemStack targetStack = itemInput.createItemStack(1);

            RecipeScan scan = JeiRecipeScanner.scanAll(source.registryAccess(), includeHidden);
            IngredientData target = JeiRecipeScanner.describeItemStack(source.registryAccess(), targetStack);
            var result = CraftingTreeCalculator.calculate(scan, target, requestedAmount, maxDepth);

            String targetName = target.identifier == null ? "target" : target.identifier;
            Path file = writeJson("crafting-tree-" + safeFileName(targetName) + "-" + timestamp() + ".json", result);
            source.sendSuccess(
                () -> Component.literal("Exported crafting tree for " + targetName + " x" + requestedAmount + " to " + file.toAbsolutePath()),
                false
            );
            return 1;
        } catch (Exception exception) {
            TooManyCraftingTrees.LOGGER.error("Failed to export crafting tree", exception);
            source.sendFailure(Component.literal("Failed to export crafting tree: " + exception.getMessage()));
            return 0;
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
}
