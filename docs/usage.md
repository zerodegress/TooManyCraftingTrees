# Too Many Crafting Trees Usage

## Overview

This mod is a client-side helper mod. It reads the JEI runtime on the client, exports recipe data to JSON, and calculates crafting trees from that exported view of recipes.

Generated files are written to:

- `tmct_exports/` under the Minecraft game directory for exported recipe scans and crafting trees
- `config/too_many_crafting_trees/recipe_libraries.json` for local recipe libraries

The supported JSON formats are documented in:

- `schemas/jei-recipe-scan.schema.json`
- `schemas/crafting-tree.schema.json`
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
/tmct tree <item> <count> <library>
/tmct tree <item> <count> <includeHidden>
/tmct tree <item> <count> <includeHidden> <library>
/tmct tree <item> <count> <includeHidden> <maxDepth>
/tmct tree <item> <count> <includeHidden> <maxDepth> <library>
```

Arguments:

- `item`: a valid item argument, for example `minecraft:chest`
- `count`: requested output amount, at least `1`
- `includeHidden`: `true` or `false`
- `maxDepth`: recursion depth limit, from `1` to `64`
- `library`: a recipe library name created with `/tmct library create`

Behavior:

- If a target item has no crafting recipe candidates, the node is marked as `base`
- If it has exactly one candidate recipe, that recipe is selected automatically
- If it has multiple candidate recipes and no library selection, the node is marked as `ambiguous`
- If a library is provided and contains a selected recipe for that item, the library selection is used

Output:

- A JSON file named like `crafting-tree-minecraft_chest-20260529-153500.json`
- Schema: `schemas/crafting-tree.schema.json`

Examples:

```text
/tmct tree minecraft:chest 1
/tmct tree minecraft:chest 1 true
/tmct tree minecraft:chest 1 default
/tmct tree minecraft:chest 1 true 16 default
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

- `recipeId` must match one of the values shown by `/tmct library candidates <item>`
- The implementation validates that the selected recipe is actually a candidate for the item

Example:

```text
/tmct library set default minecraft:stick minecraft:stick
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

## Important Notes

- This mod depends on JEI runtime data. You must be in a world and wait for JEI to finish loading.
- All commands are client-side. They do not require server support.
- The crafting tree currently records byproducts, but does not feed byproducts back into later recipe resolution automatically.
- A recipe library does not store the full recipe body. It stores selections from output item key to recipe id.
