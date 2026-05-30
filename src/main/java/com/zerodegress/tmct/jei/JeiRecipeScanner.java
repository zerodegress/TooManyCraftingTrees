package com.zerodegress.tmct.jei;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeLookup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class JeiRecipeScanner {
    private static volatile IJeiRuntime runtime;

    private JeiRecipeScanner() {
    }

    public static void setRuntime(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    public static void clearRuntime() {
        runtime = null;
    }

    public static boolean isRuntimeAvailable() {
        return runtime != null;
    }

    public static RecipeScan scanAll(HolderLookup.Provider registries, boolean includeHidden) {
        IJeiRuntime currentRuntime = runtime;
        if (currentRuntime == null) {
            throw new IllegalStateException("JEI runtime is not available yet. Join a world and wait for JEI to finish loading.");
        }

        IRecipeManager recipeManager = currentRuntime.getRecipeManager();
        RecipeScan scan = new RecipeScan();
        scan.generatedAt = Instant.now().toString();
        scan.includeHidden = includeHidden;

        currentRuntime.getJeiHelpers()
            .getAllRecipeTypes()
            .sorted(Comparator.comparing(recipeType -> recipeType.getUid().toString()))
            .forEach(recipeType -> scanType(registries, currentRuntime, recipeManager, recipeType, includeHidden, scan));

        scan.recipeCount = scan.recipes.size();
        scan.recipeTypeCount = (int) scan.recipes.stream().map(recipe -> recipe.recipeType).distinct().count();
        resolveAllSlotTags(scan);
        return scan;
    }

    public static IngredientData describeItemStack(HolderLookup.Provider registries, ItemStack stack) {
        IJeiRuntime currentRuntime = runtime;
        if (currentRuntime == null) {
            throw new IllegalStateException("JEI runtime is not available yet. Join a world and wait for JEI to finish loading.");
        }

        ItemStack normalizedStack = stack.copyWithCount(1);
        ITypedIngredient<ItemStack> typedIngredient = currentRuntime.getIngredientManager()
            .createTypedIngredient(VanillaTypes.ITEM_STACK, normalizedStack, true)
            .orElseThrow(() -> new IllegalArgumentException("Item is not a valid JEI ingredient: " + stack));
        return describeTypedIngredient(registries, currentRuntime.getIngredientManager(), typedIngredient);
    }

    private static <T> void scanType(
        HolderLookup.Provider registries,
        IJeiRuntime currentRuntime,
        IRecipeManager recipeManager,
        IRecipeType<T> recipeType,
        boolean includeHidden,
        RecipeScan scan
    ) {
        IRecipeCategory<T> category;
        try {
            category = recipeManager.getRecipeCategory(recipeType);
        } catch (RuntimeException exception) {
            scan.errors.add("Failed to get category for recipe type " + recipeType.getUid() + ": " + exception.getMessage());
            return;
        }

        List<T> recipes;
        try {
            IRecipeLookup<T> lookup = recipeManager.createRecipeLookup(recipeType);
            Stream<T> stream = includeHidden ? lookup.includeHidden().get() : lookup.get();
            recipes = stream.toList();
        } catch (RuntimeException exception) {
            scan.errors.add("Failed to enumerate recipe type " + recipeType.getUid() + ": " + exception.getMessage());
            return;
        }

        IFocusGroup emptyFocus = currentRuntime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
        for (T recipe : recipes) {
            scan.recipes.add(scanRecipe(registries, currentRuntime.getIngredientManager(), recipeManager, category, recipeType, recipe, emptyFocus));
        }
    }

    private static <T> RecipeData scanRecipe(
        HolderLookup.Provider registries,
        IIngredientManager ingredientManager,
        IRecipeManager recipeManager,
        IRecipeCategory<T> category,
        IRecipeType<T> recipeType,
        T recipe,
        IFocusGroup emptyFocus
    ) {
        RecipeData data = new RecipeData();
        data.recipeType = recipeType.getUid().toString();
        data.recipeClass = recipeType.getRecipeClass().getName();
        data.recipeObjectClass = recipe.getClass().getName();
        data.categoryTitle = componentString(category.getTitle());
        data.categoryClass = category.getClass().getName();
        data.categoryWidth = category.getWidth();
        data.categoryHeight = category.getHeight();
        data.extra = createRecipeExtra(recipe);

        try {
            Identifier identifier = category.getIdentifier(recipe);
            data.recipeId = identifier == null ? null : identifier.toString();
        } catch (RuntimeException exception) {
            data.errors.add("Failed to read recipe identifier: " + exception.getMessage());
        }

        try {
            var ingredients = recipeManager.getRecipeIngredients(category, recipe);
            data.inputs = describeTypedIngredients(registries, ingredientManager, ingredients.getIngredients(RecipeIngredientRole.INPUT));
            data.outputs = describeTypedIngredients(registries, ingredientManager, ingredients.getIngredients(RecipeIngredientRole.OUTPUT));
            data.craftingStations = describeTypedIngredients(registries, ingredientManager, ingredients.getIngredients(RecipeIngredientRole.CRAFTING_STATION));
            data.renderOnly = describeTypedIngredients(registries, ingredientManager, ingredients.getIngredients(RecipeIngredientRole.RENDER_ONLY));
        } catch (RuntimeException exception) {
            data.errors.add("Failed to read recipe ingredients: " + exception.getMessage());
        }

        data.slots = scanSlots(registries, ingredientManager, recipeManager, category, recipe, emptyFocus, data);
        if (data.slots.isEmpty()) {
            addFallbackSlots(data);
        }

        data.sortKey = (data.recipeId == null ? data.recipeType + "/" + data.recipeObjectClass : data.recipeId);
        return data;
    }

    private static <T> List<RecipeSlotData> scanSlots(
        HolderLookup.Provider registries,
        IIngredientManager ingredientManager,
        IRecipeManager recipeManager,
        IRecipeCategory<T> category,
        T recipe,
        IFocusGroup emptyFocus,
        RecipeData data
    ) {
        try {
            Optional<IRecipeLayoutDrawable<T>> layout = recipeManager.createRecipeLayoutDrawable(category, recipe, emptyFocus);
            if (layout.isEmpty()) {
                return List.of();
            }

            List<RecipeSlotData> slots = new ArrayList<>();
            for (IRecipeSlotView slotView : layout.get().getRecipeSlotsView().getSlotViews()) {
                RecipeSlotData slot = new RecipeSlotData();
                slot.role = slotView.getRole().name();
                slot.slotName = slotView.getSlotName().orElse(null);
                slot.ingredients = describeTypedIngredients(
                    registries,
                    ingredientManager,
                    slotView.getAllIngredients().toList()
                );
                slot.displayed = slotView.getDisplayedIngredient()
                    .map(typedIngredient -> describeTypedIngredient(registries, ingredientManager, typedIngredient))
                    .orElse(null);
                slots.add(slot);
            }
            return slots;
        } catch (RuntimeException exception) {
            data.errors.add("Failed to read recipe layout slots: " + exception.getMessage());
            return List.of();
        }
    }

    private static Map<Set<String>, String> buildTagLookup() {
        Map<Set<String>, String> lookup = new HashMap<>();
        BuiltInRegistries.ITEM.getTags().forEach(namedTag -> {
            Set<String> itemIds = namedTag.stream()
                .map(holder -> BuiltInRegistries.ITEM.getKey(holder.value()).toString())
                .collect(Collectors.toCollection(TreeSet::new));
            if (!itemIds.isEmpty()) {
                lookup.put(itemIds, namedTag.key().location().toString());
            }
        });
        return lookup;
    }

    private static void resolveAllSlotTags(RecipeScan scan) {
        Map<Set<String>, String> tagLookup = buildTagLookup();
        for (RecipeData recipe : scan.recipes) {
            for (RecipeSlotData slot : recipe.slots) {
                if (slot.ingredients.size() < 2) {
                    continue;
                }
                Set<String> slotItemIds = new TreeSet<>();
                for (IngredientData ingredient : slot.ingredients) {
                    if (ingredient.item != null) {
                        slotItemIds.add(ingredient.item);
                    }
                }
                if (slotItemIds.size() < 2) {
                    continue;
                }
                String tag = tagLookup.get(slotItemIds);
                if (tag != null) {
                    slot.tag = tag;
                    slot.tagSource = "reverse_lookup";
                }
            }
        }
    }

    private static void addFallbackSlots(RecipeData data) {
        for (IngredientData ingredient : data.inputs) {
            data.slots.add(fallbackSlot(RecipeIngredientRole.INPUT, ingredient));
        }
        for (IngredientData ingredient : data.outputs) {
            data.slots.add(fallbackSlot(RecipeIngredientRole.OUTPUT, ingredient));
        }
        for (IngredientData ingredient : data.craftingStations) {
            data.slots.add(fallbackSlot(RecipeIngredientRole.CRAFTING_STATION, ingredient));
        }
        for (IngredientData ingredient : data.renderOnly) {
            data.slots.add(fallbackSlot(RecipeIngredientRole.RENDER_ONLY, ingredient));
        }
    }

    private static RecipeSlotData fallbackSlot(RecipeIngredientRole role, IngredientData ingredient) {
        RecipeSlotData slot = new RecipeSlotData();
        slot.role = role.name();
        slot.source = "ingredient_supplier_fallback";
        slot.ingredients = List.of(ingredient);
        slot.displayed = ingredient;
        return slot;
    }

    private static List<IngredientData> describeTypedIngredients(
        HolderLookup.Provider registries,
        IIngredientManager ingredientManager,
        List<ITypedIngredient<?>> typedIngredients
    ) {
        List<IngredientData> result = new ArrayList<>(typedIngredients.size());
        for (ITypedIngredient<?> typedIngredient : typedIngredients) {
            result.add(describeTypedIngredient(registries, ingredientManager, typedIngredient));
        }
        return result;
    }

    private static <T> IngredientData describeTypedIngredient(
        HolderLookup.Provider registries,
        IIngredientManager ingredientManager,
        ITypedIngredient<T> typedIngredient
    ) {
        IngredientData data = new IngredientData();
        IIngredientType<T> type = typedIngredient.getType();
        T ingredient = typedIngredient.getIngredient();
        IIngredientHelper<T> helper = ingredientManager.getIngredientHelper(type);

        data.typeUid = safeString(type.getUid());
        data.ingredientClass = ingredient == null ? null : ingredient.getClass().getName();
        data.displayName = safe(() -> helper.getDisplayName(ingredient));
        data.identifier = safeIdentifier(() -> helper.getIdentifier(ingredient));
        data.uid = safeObject(() -> helper.getUid(typedIngredient, UidContext.Recipe));
        if (data.uid == null) {
            data.uid = data.identifier;
        }
        data.countable = true;

        Long helperAmount = safeLong(() -> helper.getAmount(ingredient));
        if (helperAmount != null && helperAmount >= 0) {
            data.amount = helperAmount;
        } else {
            data.countable = false;
            data.amount = fallbackAmount(ingredient);
        }

        if (ingredient instanceof ItemStack itemStack) {
            data.item = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();
            data.amount = itemStack.getCount();
            data.countable = true;
            data.serialized = encode(ItemStack.CODEC, registries, itemStack, data);
        } else if (ingredient instanceof FluidStack fluidStack) {
            data.fluid = BuiltInRegistries.FLUID.getKey(fluidStack.getFluid()).toString();
            data.amount = fluidStack.getAmount();
            data.countable = true;
            data.serialized = encode(FluidStack.CODEC, registries, fluidStack, data);
        }

        data.key = data.uid == null ? null : new IngredientKey(data.typeUid, data.uid);
        return data;
    }

    private static JsonObject createRecipeExtra(Object recipe) {
        JsonObject extra = new JsonObject();
        extra.addProperty("toString", String.valueOf(recipe));

        Object recipeValue = recipe;
        if (recipe instanceof RecipeHolder<?> holder) {
            extra.addProperty("holderId", holder.id().identifier().toString());
            recipeValue = holder.value();
        }

        if (recipeValue instanceof Recipe<?> minecraftRecipe) {
            add(extra, "minecraftRecipeClass", recipeValue.getClass().getName());
            add(extra, "group", minecraftRecipe.group());
            add(extra, "special", minecraftRecipe.isSpecial());
            add(extra, "showNotification", minecraftRecipe.showNotification());
            add(extra, "recipeType", registryKey(BuiltInRegistries.RECIPE_TYPE.getKey(minecraftRecipe.getType())));
            add(extra, "serializer", registryKey(BuiltInRegistries.RECIPE_SERIALIZER.getKey(minecraftRecipe.getSerializer())));
            add(extra, "recipeBookCategory", String.valueOf(minecraftRecipe.recipeBookCategory()));
            add(extra, "displayCount", minecraftRecipe.display().size());
        }

        return extra;
    }

    private static <T> JsonElement encode(Codec<T> codec, HolderLookup.Provider registries, T value, IngredientData data) {
        DataResult<JsonElement> result = codec.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), value);
        result.error().ifPresent(error -> data.serializationError = error.message());
        return result.result().orElse(null);
    }

    private static long fallbackAmount(Object ingredient) {
        if (ingredient instanceof ItemStack itemStack) {
            return itemStack.getCount();
        }
        if (ingredient instanceof FluidStack fluidStack) {
            return fluidStack.getAmount();
        }
        return 1;
    }

    private static String componentString(Component component) {
        return component == null ? null : component.getString();
    }

    private static String registryKey(Identifier identifier) {
        return identifier == null ? null : identifier.toString();
    }

    private static String safeString(String value) {
        return value == null ? null : value;
    }

    private static String safeIdentifier(ThrowingSupplier<Identifier> supplier) {
        Identifier identifier = safeValue(supplier);
        return identifier == null ? null : identifier.toString();
    }

    private static String safeObject(ThrowingSupplier<Object> supplier) {
        Object value = safeValue(supplier);
        return value == null ? null : String.valueOf(value);
    }

    private static String safe(ThrowingSupplier<String> supplier) {
        return safeValue(supplier);
    }

    private static Long safeLong(ThrowingSupplier<Long> supplier) {
        return safeValue(supplier);
    }

    private static <T> T safeValue(ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static void add(JsonObject object, String key, String value) {
        if (value != null) {
            object.addProperty(key, value);
        }
    }

    private static void add(JsonObject object, String key, boolean value) {
        object.addProperty(key, value);
    }

    private static void add(JsonObject object, String key, int value) {
        object.addProperty(key, value);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get();
    }

    public static final class RecipeScan {
        public String generatedAt;
        public boolean includeHidden;
        public int recipeTypeCount;
        public int recipeCount;
        public List<RecipeData> recipes = new ArrayList<>();
        public List<String> errors = new ArrayList<>();
    }

    public static final class RecipeData {
        public String recipeId;
        public String recipeType;
        public String recipeClass;
        public String recipeObjectClass;
        public String categoryTitle;
        public String categoryClass;
        public int categoryWidth;
        public int categoryHeight;
        public List<IngredientData> inputs = new ArrayList<>();
        public List<IngredientData> outputs = new ArrayList<>();
        public List<IngredientData> craftingStations = new ArrayList<>();
        public List<IngredientData> renderOnly = new ArrayList<>();
        public List<RecipeSlotData> slots = new ArrayList<>();
        public JsonObject extra;
        public List<String> errors = new ArrayList<>();
        public String sortKey;

        public List<RecipeSlotData> inputSlots() {
            return slots.stream()
                .filter(slot -> RecipeIngredientRole.INPUT.name().equals(slot.role))
                .toList();
        }

        public long outputAmountFor(IngredientKey key) {
            long amount = amountFromSlots(key);
            if (amount > 0) {
                return amount;
            }
            return outputs.stream()
                .filter(ingredient -> key.equals(ingredient.key))
                .mapToLong(IngredientData::craftAmount)
                .sum();
        }

        public List<IngredientData> outputIngredients() {
            Map<IngredientKey, IngredientData> unique = new LinkedHashMap<>();
            for (RecipeSlotData slot : slots) {
                if (RecipeIngredientRole.OUTPUT.name().equals(slot.role)) {
                    for (IngredientData ingredient : slot.ingredients) {
                        if (ingredient.key != null) {
                            unique.putIfAbsent(ingredient.key, ingredient);
                        }
                    }
                }
            }
            for (IngredientData ingredient : outputs) {
                if (ingredient.key != null) {
                    unique.putIfAbsent(ingredient.key, ingredient);
                }
            }
            return List.copyOf(unique.values());
        }

        public List<IngredientData> selectedOutputIngredients() {
            List<IngredientData> selectedOutputs = new ArrayList<>();
            for (RecipeSlotData slot : slots) {
                if (RecipeIngredientRole.OUTPUT.name().equals(slot.role)) {
                    IngredientData selected = slot.displayed == null ? null : slot.displayed;
                    if (selected == null) {
                        selected = slot.ingredients.stream().findFirst().orElse(null);
                    }
                    if (selected != null && selected.key != null) {
                        selectedOutputs.add(selected);
                    }
                }
            }
            if (!selectedOutputs.isEmpty()) {
                return List.copyOf(selectedOutputs);
            }
            return outputs.stream()
                .filter(ingredient -> ingredient.key != null)
                .toList();
        }

        public String displayId() {
            return recipeId == null ? recipeType + " / " + recipeObjectClass : recipeId;
        }

        public String selectorId() {
            String baseId = displayId();
            return recipeType == null ? baseId : recipeType + " | " + baseId;
        }

        private long amountFromSlots(IngredientKey key) {
            long amount = 0;
            for (RecipeSlotData slot : slots) {
                if (RecipeIngredientRole.OUTPUT.name().equals(slot.role)) {
                    Optional<IngredientData> match = slot.ingredients.stream()
                        .filter(ingredient -> key.equals(ingredient.key))
                        .findFirst();
                    if (match.isPresent()) {
                        amount += match.get().craftAmount();
                    }
                }
            }
            return amount;
        }
    }

    public static final class RecipeSlotData {
        public String role;
        public String slotName;
        public String source;
        public String tag;
        public String tagSource;
        public IngredientData displayed;
        public List<IngredientData> ingredients = new ArrayList<>();

        public Optional<IngredientData> firstCraftableIngredient() {
            return ingredients.stream()
                .filter(ingredient -> ingredient.key != null)
                .findFirst();
        }
    }

    public static final class IngredientData {
        public String typeUid;
        public String uid;
        public String identifier;
        public String displayName;
        public String ingredientClass;
        public long amount;
        public boolean countable;
        public String item;
        public String fluid;
        public IngredientKey key;
        public JsonElement serialized;
        public String serializationError;

        public long craftAmount() {
            return amount > 0 ? amount : 1;
        }

        public IngredientData withAmount(long newAmount) {
            IngredientData copy = shallowCopy();
            copy.amount = newAmount;
            copy.countable = true;
            return copy;
        }

        public IngredientData shallowCopy() {
            IngredientData copy = new IngredientData();
            copy.typeUid = typeUid;
            copy.uid = uid;
            copy.identifier = identifier;
            copy.displayName = displayName;
            copy.ingredientClass = ingredientClass;
            copy.amount = amount;
            copy.countable = countable;
            copy.item = item;
            copy.fluid = fluid;
            copy.key = key;
            copy.serialized = serialized;
            copy.serializationError = serializationError;
            return copy;
        }
    }

    public static final class IngredientKey {
        public final String typeUid;
        public final String uid;

        public IngredientKey(String typeUid, String uid) {
            this.typeUid = typeUid;
            this.uid = uid;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof IngredientKey that)) {
                return false;
            }
            return Objects.equals(typeUid, that.typeUid) && Objects.equals(uid, that.uid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(typeUid, uid);
        }

        @Override
        public String toString() {
            return typeUid + ":" + uid;
        }
    }
}
