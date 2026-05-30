package com.zerodegress.tmct.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.zerodegress.tmct.jei.JeiRecipeScanner.IngredientKey;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RecipeLibraryStore {
    private static final Gson GSON = new GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create();
    private static final String FILE_NAME = "recipe_libraries.json";
    private static final String DEFAULT_LIBRARY_NAME = "default";

    private RecipeLibraryStore() {
    }

    public static String defaultLibraryName() {
        return DEFAULT_LIBRARY_NAME;
    }

    public static String getActiveLibrary() throws IOException {
        return loadFile().activeLibrary;
    }

    public static void setActiveLibrary(String libraryName) throws IOException {
        RecipeLibraryFile file = loadFile();
        if (!file.libraries.containsKey(libraryName)) {
            throw new IllegalArgumentException("Recipe library does not exist: " + libraryName);
        }
        file.activeLibrary = libraryName;
        saveFile(file);
    }

    public static List<String> listLibraries() throws IOException {
        return List.copyOf(loadFile().libraries.keySet());
    }

    public static boolean libraryExists(String libraryName) throws IOException {
        return loadFile().libraries.containsKey(libraryName);
    }

    public static void createLibrary(String libraryName) throws IOException {
        RecipeLibraryFile file = loadFile();
        if (file.libraries.containsKey(libraryName)) {
            throw new IllegalArgumentException("Recipe library already exists: " + libraryName);
        }
        file.libraries.put(libraryName, new RecipeLibrary(libraryName));
        saveFile(file);
    }

    public static void deleteLibrary(String libraryName) throws IOException {
        RecipeLibraryFile file = loadFile();
        if (file.libraries.remove(libraryName) == null) {
            throw new IllegalArgumentException("Recipe library does not exist: " + libraryName);
        }
        if (libraryName.equals(file.activeLibrary)) {
            file.activeLibrary = DEFAULT_LIBRARY_NAME;
        }
        file.libraries.computeIfAbsent(DEFAULT_LIBRARY_NAME, RecipeLibrary::new);
        saveFile(file);
    }

    public static Optional<String> getSelectedRecipe(String libraryName, IngredientKey ingredientKey) throws IOException {
        RecipeLibrary library = loadFile().libraries.get(libraryName);
        if (library == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(library.selectedRecipes.get(encodeIngredientKey(ingredientKey)));
    }

    public static void setSelectedRecipe(String libraryName, IngredientKey ingredientKey, String recipeId) throws IOException {
        RecipeLibraryFile file = loadFile();
        RecipeLibrary library = file.libraries.computeIfAbsent(libraryName, RecipeLibrary::new);
        library.selectedRecipes.put(encodeIngredientKey(ingredientKey), recipeId);
        library.updatedAt = Instant.now().toString();
        saveFile(file);
    }

    public static boolean clearSelectedRecipe(String libraryName, IngredientKey ingredientKey) throws IOException {
        RecipeLibraryFile file = loadFile();
        RecipeLibrary library = file.libraries.get(libraryName);
        if (library == null) {
            return false;
        }
        boolean removed = library.selectedRecipes.remove(encodeIngredientKey(ingredientKey)) != null;
        if (removed) {
            library.updatedAt = Instant.now().toString();
            saveFile(file);
        }
        return removed;
    }

    private static RecipeLibraryFile loadFile() throws IOException {
        Path path = filePath();
        if (Files.notExists(path)) {
            RecipeLibraryFile file = new RecipeLibraryFile();
            file.activeLibrary = DEFAULT_LIBRARY_NAME;
            file.libraries.put(DEFAULT_LIBRARY_NAME, new RecipeLibrary(DEFAULT_LIBRARY_NAME));
            saveFile(file);
            return file;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            RecipeLibraryFile file = GSON.fromJson(reader, RecipeLibraryFile.class);
            if (file == null) {
                file = new RecipeLibraryFile();
            }
            if (file.libraries == null) {
                file.libraries = new LinkedHashMap<>();
            }
            file.libraries.computeIfAbsent(DEFAULT_LIBRARY_NAME, RecipeLibrary::new);
            if (file.activeLibrary == null || !file.libraries.containsKey(file.activeLibrary)) {
                file.activeLibrary = DEFAULT_LIBRARY_NAME;
            }
            return file;
        } catch (JsonParseException exception) {
            throw new IOException("Failed to parse recipe library file: " + path.toAbsolutePath(), exception);
        }
    }

    private static void saveFile(RecipeLibraryFile file) throws IOException {
        file.updatedAt = Instant.now().toString();
        Files.createDirectories(filePath().getParent());
        try (Writer writer = Files.newBufferedWriter(filePath(), StandardCharsets.UTF_8)) {
            GSON.toJson(file, writer);
        }
    }

    private static Path filePath() {
        return Minecraft.getInstance().gameDirectory.toPath()
            .resolve("config")
            .resolve("too_many_crafting_trees")
            .resolve(FILE_NAME);
    }

    private static String encodeIngredientKey(IngredientKey ingredientKey) {
        return ingredientKey.typeUid + "|" + ingredientKey.uid;
    }

    private static final class RecipeLibraryFile {
        public String updatedAt;
        public String activeLibrary;
        public Map<String, RecipeLibrary> libraries = new LinkedHashMap<>();
    }

    private static final class RecipeLibrary {
        public String name;
        public String updatedAt;
        public Map<String, String> selectedRecipes = new LinkedHashMap<>();

        private RecipeLibrary(String name) {
            this.name = name;
            this.updatedAt = Instant.now().toString();
        }
    }
}
