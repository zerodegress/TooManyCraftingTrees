# TMCT JSON Schemas

## Overview

TMCT currently writes three kinds of JSON documents:

1. JEI recipe scans from `/tmct dump-recipes`
2. Crafting trees from `/tmct tree`
3. Local recipe libraries in `config/too_many_crafting_trees/recipe_libraries.json`

The canonical schemas are:

- `schemas/common.schema.json`
- `schemas/jei-recipe-scan.schema.json`
- `schemas/crafting-tree.schema.json`
- `schemas/recipe-libraries.schema.json`

`common.schema.json` contains shared building blocks used by the other schemas.

## Shared Types

### `ingredientKey`

Stable lookup key for an ingredient inside TMCT.

Fields:

- `typeUid`: JEI ingredient type id, for example item stack or fluid stack
- `uid`: JEI recipe-context uid for that ingredient

Example:

```json
{
  "typeUid": "minecraft:item_stack",
  "uid": "minecraft:stick"
}
```

### `ingredientData`

Serialized description of one ingredient, item stack, or fluid stack.

Fields:

- `typeUid`: JEI ingredient type id
- `uid`: stable JEI uid for recipe matching
- `identifier`: JEI textual identifier when available
- `displayName`: localized display name
- `ingredientClass`: Java class name of the represented ingredient object
- `amount`: stack size or fluid amount
- `countable`: whether TMCT could determine a numeric amount
- `item`: item registry id when the ingredient is an item
- `fluid`: fluid registry id when the ingredient is a fluid
- `key`: `ingredientKey` object or `null`
- `serialized`: serialized ingredient payload when codec export succeeds
- `serializationError`: codec error text if serialization failed

Example:

```json
{
  "typeUid": "minecraft:item_stack",
  "uid": "minecraft:oak_planks",
  "identifier": "minecraft:oak_planks",
  "displayName": "Oak Planks",
  "ingredientClass": "net.minecraft.world.item.ItemStack",
  "amount": 4,
  "countable": true,
  "item": "minecraft:oak_planks",
  "fluid": null,
  "key": {
    "typeUid": "minecraft:item_stack",
    "uid": "minecraft:oak_planks"
  },
  "serialized": {
    "id": "minecraft:oak_planks",
    "count": 4
  },
  "serializationError": null
}
```

### `recipeSlotData`

One displayed slot from a JEI recipe layout.

Fields:

- `role`: JEI role, usually `INPUT`, `OUTPUT`, `CRAFTING_STATION`, or `RENDER_ONLY`
- `slotName`: JEI-provided slot name if available
- `source`: optional source tag; fallback-generated slots use `ingredient_supplier_fallback`
- `displayed`: currently displayed ingredient in that slot, or `null`
- `ingredients`: all possible ingredients for the slot

### `recipeData`

One full scanned JEI recipe entry.

Fields:

- `recipeId`: JEI recipe identifier when available
- `recipeType`: JEI recipe type uid
- `recipeClass`: declared recipe class from the recipe type
- `recipeObjectClass`: runtime class of the recipe instance
- `categoryTitle`: JEI category display title
- `categoryClass`: JEI category class name
- `categoryWidth`: JEI layout width
- `categoryHeight`: JEI layout height
- `inputs`: flattened input ingredient list
- `outputs`: flattened output ingredient list
- `craftingStations`: crafting station ingredients
- `renderOnly`: render-only ingredients
- `slots`: detailed JEI slot list
- `extra`: extra metadata gathered from the recipe object
- `errors`: per-recipe scan errors
- `sortKey`: stable sort key used by TMCT

## JEI Recipe Scan

Schema:

- `schemas/jei-recipe-scan.schema.json`

This is the top-level structure written by `/tmct dump-recipes`.

Fields:

- `generatedAt`: ISO-8601 timestamp
- `includeHidden`: whether hidden JEI recipes were included
- `recipeTypeCount`: number of distinct recipe types scanned
- `recipeCount`: total number of scanned recipes
- `recipes`: array of `recipeData`
- `errors`: scan-level errors

Example:

```json
{
  "generatedAt": "2026-05-29T08:34:12Z",
  "includeHidden": true,
  "recipeTypeCount": 12,
  "recipeCount": 248,
  "recipes": [
    {
      "recipeId": "minecraft:oak_planks",
      "recipeType": "minecraft:crafting",
      "recipeClass": "net.minecraft.world.item.crafting.CraftingRecipe",
      "recipeObjectClass": "net.minecraft.world.item.crafting.ShapelessRecipe",
      "categoryTitle": "Crafting",
      "categoryClass": "mezz.jei.library.plugins.vanilla.crafting.CraftingCategory",
      "categoryWidth": 116,
      "categoryHeight": 54,
      "inputs": [],
      "outputs": [],
      "craftingStations": [],
      "renderOnly": [],
      "slots": [],
      "extra": {
        "toString": "minecraft:oak_planks"
      },
      "errors": [],
      "sortKey": "minecraft:oak_planks"
    }
  ],
  "errors": []
}
```

## Crafting Tree

Schema:

- `schemas/crafting-tree.schema.json`

This is the top-level structure written by `/tmct tree`.

Fields:

- `generatedAt`: ISO-8601 timestamp
- `includeHidden`: whether hidden JEI recipes were available during tree generation
- `recipeLibrary`: library name used for recipe selection, or `null`
- `maxDepth`: recursion depth limit used for this export
- `scannedRecipeCount`: recipe count in the scanned JEI dataset used for the tree
- `target`: `ingredientData` for the requested output item
- `requestedAmount`: requested count for the root target
- `root`: root `craftingTreeNode`
- `baseMaterials`: array of terminal materials with no selected recipe path
- `byproducts`: array of summarized byproducts
- `unresolved`: array of unresolved ingredients and reasons

### `craftingTreeNode`

One node in the recursive crafting tree.

Fields:

- `ingredient`: requested ingredient at this node
- `requestedAmount`: required amount at this node
- `depth`: recursion depth
- `status`: node result
  - `crafted`: recipe selected and expanded
  - `base`: no recipe candidates
  - `ambiguous`: multiple candidates and no explicit library selection
  - `missing_library_recipe`: library selected a recipe id that is not present in current scan candidates
  - `invalid_recipe_output`: selected recipe did not expose matching output amount
  - `depth_limit`: recursion stopped at the configured limit
  - `cycle`: cycle detected in the dependency path
  - `unkeyed`: ingredient had no stable JEI key
- `selectedRecipeId`: selected recipe id, or `null`
- `selectedRecipeType`: selected recipe type, or `null`
- `selectedRecipeSource`: why this recipe was chosen
  - `library`: chosen from the selected recipe library
  - `only_candidate`: chosen automatically because it was the only candidate
- `candidateRecipeCount`: number of candidate recipes producing this output
- `candidateRecipes`: candidate recipe ids
- `outputPerCraft`: amount produced by one craft of the selected recipe
- `crafts`: number of craft operations required
- `producedAmount`: total produced amount
- `surplusAmount`: target output surplus from rounding to whole crafts
- `inputs`: array of `craftingTreeInput`

### `craftingTreeInput`

One input edge from a node to a child node.

Fields:

- `status`: input resolution result
  - `selected`: one ingredient alternative was selected
  - `no_keyed_ingredient`: no stable keyed alternative was available
- `slotName`: JEI slot name if available
- `selected`: selected ingredient alternative, or `null`
- `requiredAmount`: amount required for this input after multiplying by craft count
- `alternatives`: all possible alternatives from the recipe slot
- `child`: child `craftingTreeNode`, or `null`

### `byproducts`

Top-level summary of generated byproducts.

Current behavior:

- Surplus of the requested target output is added as a byproduct
- Other outputs of the selected recipe are also added as byproducts
- Byproducts are summarized, but are not yet fed back into later resolution

Example:

```json
{
  "generatedAt": "2026-05-29T08:40:00Z",
  "includeHidden": false,
  "recipeLibrary": "default",
  "maxDepth": 16,
  "scannedRecipeCount": 248,
  "target": {
    "typeUid": "minecraft:item_stack",
    "uid": "minecraft:chest",
    "identifier": "minecraft:chest",
    "displayName": "Chest",
    "ingredientClass": "net.minecraft.world.item.ItemStack",
    "amount": 1,
    "countable": true,
    "item": "minecraft:chest",
    "fluid": null,
    "key": {
      "typeUid": "minecraft:item_stack",
      "uid": "minecraft:chest"
    },
    "serialized": {
      "id": "minecraft:chest",
      "count": 1
    },
    "serializationError": null
  },
  "requestedAmount": 1,
  "root": {
    "ingredient": {
      "typeUid": "minecraft:item_stack",
      "uid": "minecraft:chest",
      "identifier": "minecraft:chest",
      "displayName": "Chest",
      "ingredientClass": "net.minecraft.world.item.ItemStack",
      "amount": 1,
      "countable": true,
      "item": "minecraft:chest",
      "fluid": null,
      "key": {
        "typeUid": "minecraft:item_stack",
        "uid": "minecraft:chest"
      },
      "serialized": {
        "id": "minecraft:chest",
        "count": 1
      },
      "serializationError": null
    },
    "requestedAmount": 1,
    "depth": 0,
    "status": "crafted",
    "selectedRecipeId": "minecraft:chest",
    "selectedRecipeType": "minecraft:crafting",
    "selectedRecipeSource": "only_candidate",
    "candidateRecipeCount": 1,
    "candidateRecipes": [
      "minecraft:chest"
    ],
    "outputPerCraft": 1,
    "crafts": 1,
    "producedAmount": 1,
    "surplusAmount": 0,
    "inputs": []
  },
  "baseMaterials": [],
  "byproducts": [],
  "unresolved": []
}
```

## Recipe Libraries

Schema:

- `schemas/recipe-libraries.schema.json`

This is the local client config used for named recipe-selection libraries.

Top-level fields:

- `updatedAt`: last write timestamp for the whole file
- `libraries`: object keyed by library name

### `recipeLibrary`

Fields:

- `name`: library name
- `updatedAt`: last write timestamp for this library
- `selectedRecipes`: mapping from encoded ingredient key to selected recipe id

The encoded ingredient key format is:

```text
<typeUid>|<uid>
```

Example:

```json
{
  "updatedAt": "2026-05-29T08:50:00Z",
  "libraries": {
    "default": {
      "name": "default",
      "updatedAt": "2026-05-29T08:50:00Z",
      "selectedRecipes": {
        "minecraft:item_stack|minecraft:stick": "minecraft:stick"
      }
    },
    "pack_a": {
      "name": "pack_a",
      "updatedAt": "2026-05-29T08:51:00Z",
      "selectedRecipes": {
        "minecraft:item_stack|minecraft:chest": "minecraft:chest"
      }
    }
  }
}
```

## Simple End-to-End Examples

### Example 1: Scan recipes and inspect schema

Commands:

```text
/tmct dump-recipes true
```

Produced file:

- `tmct_exports/jei-recipes-*.json`

Schema:

- `schemas/jei-recipe-scan.schema.json`

### Example 2: Build a tree without a library

Commands:

```text
/tmct tree minecraft:chest 1
```

Expected result:

- Works when every encountered output has at most one candidate recipe
- If some node has multiple candidates, that node becomes `ambiguous`

### Example 3: Fix ambiguity with a library

Commands:

```text
/tmct library create my_pack
/tmct library candidates minecraft:stick
/tmct library set my_pack minecraft:stick minecraft:stick
/tmct tree minecraft:ladder 3 my_pack
```

Expected result:

- The tree export records `"recipeLibrary": "my_pack"`
- Any node for `minecraft:stick` will use the library-selected recipe if present
