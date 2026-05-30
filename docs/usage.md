# Too Many Crafting Trees Usage

## Overview

This mod is a client-side helper mod. It reads the JEI runtime on the client, exports recipe data to JSON, and calculates crafting trees from that exported view of recipes.

Generated files are written to:

- `tmct_exports/` under the Minecraft game directory for exported recipe scans and crafting trees
- `config/too_many_crafting_trees/recipe_libraries.json` for local recipe libraries

The supported JSON formats are documented in:

- `schemas/jei-recipe-scan.schema.json`
- `schemas/crafting-tree.schema.json`
- `schemas/simpletree.schema.json`
- `schemas/recipe-libraries.schema.json`

## Command Summary

All commands are client commands under `/tmct`.

### `/tmct dump-recipes`

Export all currently visible JEI recipes.

Syntax:

```text
/tmct dump-recipes
/tmct dump-recipes <includeHidden>
```

Arguments:

- `includeHidden`: `true` or `false`
  - `false`: export the normal visible JEI recipe list
  - `true`: also include recipes that JEI marks as hidden

Output:

- A JSON file named like `jei-recipes-20260529-153000.json`
- Schema: `schemas/jei-recipe-scan.schema.json`

Example:

```text
/tmct dump-recipes
/tmct dump-recipes true
```

### `/tmct tree`

Export a crafting tree for a target item and amount.

Syntax:

```text
/tmct tree <item> <count>
/tmct tree <item> <count> library <library>
/tmct tree <item> <count> hidden <includeHidden>
/tmct tree <item> <count> hidden <includeHidden> library <library>
/tmct tree <item> <count> hidden <includeHidden> depth <maxDepth>
/tmct tree <item> <count> hidden <includeHidden> depth <maxDepth> library <library>
/tmct tree <item> <count> depth <maxDepth>
/tmct tree <item> <count> depth <maxDepth> library <library>
```

Arguments:

- `item`: a valid item argument, for example `minecraft:chest`
- `count`: requested output amount, at least `1`
- `includeHidden`: `true` or `false`
- `maxDepth`: recursion depth limit, from `1` to `64`
- `library`: a recipe library name created with `/tmct library create`

Behavior:

- If a target item has no crafting recipe candidates, the node is marked as `base`
- Without a library, if an item has exactly one candidate recipe, that recipe is selected automatically
- Without a library, if an item has multiple candidate recipes and no library selection, the node is marked as `ambiguous`
- If a library is provided, only recipes explicitly selected in that library are allowed to appear in the tree
- If a library is provided and an ingredient has candidates but no selected recipe in that library, the node is marked as `missing_library_selection`

Output:

- A JSON file named like `crafting-tree-minecraft_chest-20260529-153500.json`
- Schema: `schemas/crafting-tree.schema.json`

Examples:

```text
/tmct tree minecraft:chest 1
/tmct tree minecraft:chest 1 hidden true
/tmct tree minecraft:chest 1 library default
/tmct tree minecraft:chest 1 hidden true depth 16 library default
```

### `/tmct simpletree`

Export a simplified crafting tree for a target item and amount.

`simpletree` uses the same recipe-selection logic as `/tmct tree`, but the JSON is reduced to:

- `brief`: overall effect of the full crafting plan
- `tree`: recursive step-by-step crafting structure

The command can use a recipe library for disambiguation, but the exported JSON does not include library-specific fields.
Raw material nodes in `tree` are emitted in a minimal form with only `type` and `output`.

Syntax:

```text
/tmct simpletree <item> <count>
/tmct simpletree <item> <count> library <library>
/tmct simpletree <item> <count> hidden <includeHidden>
/tmct simpletree <item> <count> hidden <includeHidden> library <library>
/tmct simpletree <item> <count> hidden <includeHidden> depth <maxDepth>
/tmct simpletree <item> <count> hidden <includeHidden> depth <maxDepth> library <library>
/tmct simpletree <item> <count> depth <maxDepth>
/tmct simpletree <item> <count> depth <maxDepth> library <library>
```

Arguments:

- `item`: a valid item argument
- `count`: requested output amount, at least `1`
- `includeHidden`: `true` or `false`
- `maxDepth`: recursion depth limit, from `1` to `64`
- `library`: a recipe library name created with `/tmct library create`

Output:

- A JSON file named like `simple-tree-minecraft_chest-20260529-154000.json`
- Schema: `schemas/simpletree.schema.json`

Examples:

```text
/tmct simpletree minecraft:chest 1
/tmct simpletree minecraft:chest 1 library default
/tmct simpletree minecraft:chest 1 hidden true depth 16 library default
```

### `/tmct library list`

List all local recipe libraries.

Syntax:

```text
/tmct library list
```

Example:

```text
/tmct library list
```

### `/tmct library create`

Create a named recipe library.

Syntax:

```text
/tmct library create <name>
```

Example:

```text
/tmct library create mekanism_run
```

### `/tmct library delete`

Delete a named recipe library.

Syntax:

```text
/tmct library delete <name>
```

Example:

```text
/tmct library delete mekanism_run
```

### `/tmct library candidates`

List all candidate recipes that can produce an item.

This is usually the first command to run before assigning a library selection.
Candidate entries are shown as a composite selector id:

```text
<recipeType> | <recipeId>
```

This avoids ambiguity when multiple recipe types reuse the same `recipeId`, such as furnace glass and electric-furnace glass both using `minecraft:glass`.

Syntax:

```text
/tmct library candidates <item>
```

Example:

```text
/tmct library candidates minecraft:stick
```

### `/tmct library show`

Show the currently selected recipe in a library for an item.

Syntax:

```text
/tmct library show <name> <item>
```

Example:

```text
/tmct library show default minecraft:stick
```

### `/tmct library set`

Bind one output item to one specific recipe in a named library.

Syntax:

```text
/tmct library set <name> <item> <recipeId>
```

Notes:

- `recipeId` must match one of the composite selector ids shown by `/tmct library candidates <item>`
- The implementation validates that the selected recipe is actually a candidate for the item

Example:

```text
/tmct library set default minecraft:stick minecraft:crafting | minecraft:stick
```

### `/tmct library clear`

Remove a recipe selection from a library for an item.

Syntax:

```text
/tmct library clear <name> <item>
```

Example:

```text
/tmct library clear default minecraft:stick
```

## Typical Workflows

### Workflow 1: Export all JEI recipes

```text
/tmct dump-recipes true
```

Use this when you want the full scanned recipe dataset for analysis outside the game.

### Workflow 2: Export a simple tree without a library

```text
/tmct tree minecraft:chest 1
```

This works best when every intermediate output has only one candidate recipe.

### Workflow 3: Resolve ambiguity with a library

```text
/tmct library create pack_a
/tmct library candidates minecraft:stick
/tmct library set pack_a minecraft:stick minecraft:stick
/tmct tree minecraft:ladder 3 pack_a
```

This is the intended workflow when some items can be crafted in multiple ways.

### Workflow 4: Export a simplified tree for downstream tools

```text
/tmct library create pack_b
/tmct library candidates minecraft:stick
/tmct library set pack_b minecraft:stick minecraft:stick
/tmct simpletree minecraft:ladder 3 pack_b
```

Use this when you want a smaller JSON shape than the full `/tmct tree` export.

## Important Notes

- This mod depends on JEI runtime data. You must be in a world and wait for JEI to finish loading.
- All commands are client-side. They do not require server support.
- The crafting tree currently records byproducts, but does not feed byproducts back into later recipe resolution automatically.
- A recipe library does not store the full recipe body. It stores selections from output item key to recipe id.
