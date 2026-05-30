# Repository Guidelines

## Project Structure & Module Organization
Core mod code lives under `src/main/java/com/zerodegress/tmct`, split by responsibility:
`client/` for client commands and local storage, `jei/` for JEI scanning integration, and `tree/` for crafting-tree calculation logic. Runtime assets live in `src/main/resources/assets/too_many_crafting_trees`, while generated or templated metadata is sourced from `src/main/templates` and `src/generated/resources`. User-facing docs are in `docs/`, and the JSON contracts exported by the mod are versioned under `schemas/`.

## Build, Test, and Development Commands
Use the Gradle wrapper from the repo root.

- `./gradlew build`: compile, process resources, and run the full CI build.
- `./gradlew runClient`: launch the NeoForge client dev environment.
- `./gradlew runServer`: start the dedicated server profile with `--nogui`.
- `./gradlew runGameTestServer`: execute registered GameTests, if any exist.
- `./gradlew runData`: regenerate data-driven resources into `src/generated/resources`.

CI currently runs `./gradlew build` on Ubuntu with Temurin JDK 25.

## Coding Style & Naming Conventions
This is a Java 25 NeoForge project. Follow the existing style: 4-space indentation, braces on the same line, and concise immutable helpers where possible (`final` classes, static factories such as `create(...)`). Keep packages lowercase (`com.zerodegress.tmct.*`), classes in PascalCase, methods and fields in camelCase, and constants in `UPPER_SNAKE_CASE`. Match the mod id exactly as `too_many_crafting_trees` in assets, resource paths, and generated metadata.

## Testing Guidelines
There is no `src/test` tree yet. Prefer adding NeoForge GameTests for game-integrated behavior and keep them runnable via `runGameTestServer`. For pure tree or schema logic, introduce focused JVM tests before broad integration coverage. Name test classes after the target type, for example `RecipeResolverTest` or `CraftingTreeGameTests`.

## Commit & Pull Request Guidelines
This repo uses Jujutsu (`.jj/` is present), so use `jj` commands instead of raw `git` for status and history. Recent history follows Conventional Commit-style subjects such as `feat(scan): ...`, `refactor(tree): ...`, and `chore: ...`; keep that format and write imperative summaries. Pull requests should describe behavior changes, list schema or command-surface updates, link related issues, and include sample JSON or screenshots when command output or UI-visible behavior changes.
