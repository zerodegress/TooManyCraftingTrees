# Too Many Crafting Trees

Too Many Crafting Trees is a client-side NeoForge mod that reads recipe data from the JEI runtime and exports machine-readable crafting information for analysis outside the game.

The current focus of the project is:

- exporting scanned JEI recipe data
- building crafting trees for a target item
- building a simplified crafting tree format for downstream tooling
- selecting preferred recipes through local named recipe libraries

## Status

This project is currently **WIP**.

Expect active iteration, schema changes, command changes, and behavior changes while the data model and tree calculation rules are still being stabilized.

## Vibe Coding

This project is being developed with a **vibe coding** workflow.

That means development is intentionally fast, exploratory, and iterative. Some design choices may be provisional, some internals may be reshaped quickly, and documentation/schema updates may follow rapid implementation changes. Stability is improving, but the project should still be treated as experimental.

## What It Does

The mod currently provides client commands under `/tmct` for:

- exporting JEI recipes to JSON
- exporting a detailed crafting tree to JSON
- exporting a simplified crafting tree to JSON
- managing local recipe libraries used to disambiguate recipes

Generated JSON schemas live under [schemas](./schemas/), and the user-facing command documentation lives under [docs](./docs/).

## Documentation

- Command usage: [docs/usage.md](./docs/usage.md)
- JSON schema reference: [docs/json-schemas.md](./docs/json-schemas.md)

## Development Notes

- This is a client-side helper mod, not a server gameplay mod
- JEI runtime data is required for the export commands to work
- The project currently targets NeoForge and uses local JSON exports as its main integration surface

## Warning

Do not assume the current JSON outputs are final just because schemas exist. The schemas describe the current contract of the project, but while the project is still WIP, that contract can still evolve.
