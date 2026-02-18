# "Laconical Music Player" - Project Rules

## Tech Stack
- **Language:** Kotlin (Latest).
- **UI:** Jetpack Compose (Material 3).
- **Architecture:** Clean Architecture, Multi-module, MVI (Model-View-Intent).
- **DI:** Hilt.
- **Async:** Coroutines & Flow (No LiveData, No RxJava).
- **Build:** Gradle Kotlin DSL + Version Catalogs (libs.versions.toml).
- **Audio:** Media3 (ExoPlayer).

## AGP 9.0 & Gradle 9.1 Compatibility
- **Built-in Kotlin:** Use AGP built-in Kotlin support. Do NOT apply `org.jetbrains.kotlin.android` plugin.
- **SourceSets Fix:** Keep `android.disallowKotlinSourceSets=false` in `gradle.properties` for KSP compatibility.
- **JVM Toolchain:** Always use Java 21 (`jvmToolchain(21)`).
- **Compose:** Use the new `org.jetbrains.kotlin.plugin.compose` plugin instead of `kotlinCompilerExtensionVersion`.

## Coding Standards (Senior Level)
- **Functional Style:** Prefer immutable `val`, functional chains (`map`, `filter`), and excessive use of Kotlin standard library extensions.
- **Compose:** Use `StateFlow` for UI state. All UI components must be previewable.
- **Comments:** NO redundant comments. Short and precise.
- **Formatting:** Kotlin official style guide. trailing commas enabled.

## Antigravity Agent Protocol
1. **Plan First:** Before writing code, generate a `Task List` and `Implementation Plan` artifact.
2. **Atomic Changes:** Do not touch files unrelated to the current task.
3. **Verification:** Always verify code compilation before finishing the task.
4. **No Slop:** Do not generate placeholder code like `// TODO: Implement later` unless explicitly told. Write complete, working implementation or ask for clarification.