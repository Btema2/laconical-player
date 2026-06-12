# CI/CD with GitHub Actions — Design Spec

**Date:** 2026-06-12
**Status:** Approved

## Goal

Set up a GitHub Actions CI pipeline that validates every PR and push to `main`, and produces a downloadable debug APK without cluttering the GitHub Releases tab.

## Triggers

```yaml
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
```

Fires on every PR update and every direct push to `main`. No other branches.

## Workflow File

Single file: `.github/workflows/ci.yml`

## Runner & Environment

- Runner: `ubuntu-latest` (Android SDK pre-installed)
- JDK: 21, Temurin distribution via `actions/setup-java@v4` (matches project's `jvmTarget = JVM_21`)
- No extra Android SDK setup required

## Caching Strategy

Two Gradle cache entries keyed on `runner.os` + hash of `gradle/libs.versions.toml`:

| Cache name | Paths |
|---|---|
| Gradle home | `~/.gradle/caches`, `~/.gradle/wrapper` |
| Build cache | `~/.gradle/build-cache` |

Fallback key (prefix match) ensures partial cache hits still restore. Running all three Gradle tasks in one job lets Gradle share compiled bytecode across lint, test, and assemble — no redundant recompilation.

## Job: `build-and-check`

Steps in order:

| Step | Action / Command |
|---|---|
| Checkout | `actions/checkout@v4` |
| Setup JDK 21 | `actions/setup-java@v4` (Temurin) |
| Restore Gradle caches | `actions/cache@v4` (×2) |
| Make gradlew executable | `chmod +x gradlew` |
| Lint | `./gradlew lint --no-daemon --stacktrace` |
| Unit tests | `./gradlew :core:media:test --no-daemon --stacktrace` |
| Assemble debug | `./gradlew assembleDebug --no-daemon --stacktrace` |
| Upload APK | `actions/upload-artifact@v4` |
| Upload lint report | `actions/upload-artifact@v4` (if: always()) |
| Upload test report | `actions/upload-artifact@v4` (if: always()) |

`--no-daemon`: CI runners are ephemeral; daemon buys nothing and wastes memory.
`--stacktrace`: surfaces full failure details in the Actions log.

## Artifacts

All artifacts expire after **30 days**. None are published to GitHub Releases.

| Artifact name | Source path | Condition |
|---|---|---|
| `laconical-player-debug-<run_number>` | `app/build/outputs/apk/debug/app-debug.apk` | on success |
| `lint-report` | `app/build/reports/lint-results-debug.html` | always |
| `test-report` | `core/media/build/reports/tests/` | always |

The `<run_number>` suffix keeps each run's APK distinct and downloadable from the Actions tab.

## Secrets & Signing

No secrets required. The `app/build.gradle.kts` signing config is already guarded:

```kotlin
if (localProperties.getProperty("release.keystore.path") != null) {
    signingConfig = signingConfigs.getByName("release")
}
```

`assembleDebug` uses Android's default debug keystore, which is generated automatically on the runner. No keystore secrets need to be stored in GitHub.

## Out of Scope

- Release APK builds (separate future concern; requires keystore secrets in GitHub)
- Firebase App Distribution or any external artifact hosting
- Instrumented tests (require an Android emulator, significantly slower)
- Code coverage reporting
