# CI/CD GitHub Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a GitHub Actions CI workflow that lints, runs unit tests, assembles a debug APK, and uploads it as a downloadable artifact on every PR and push to `main`.

**Architecture:** Single workflow file (`.github/workflows/ci.yml`) with one job (`build-and-check`) running all three Gradle tasks sequentially in a single job so Gradle's build cache can share compiled bytecode across lint, test, and assemble. Gradle home and build-cache are persisted between runs via `actions/cache@v4`. Artifacts (APK, lint report, test report) are uploaded to the Actions run — not to GitHub Releases.

**Tech Stack:** GitHub Actions, Gradle 8, AGP 9.0.1, JDK 21 (Temurin), `actions/checkout@v4`, `actions/setup-java@v4`, `actions/cache@v4`, `actions/upload-artifact@v4`

---

## File Map

| File | Change |
|---|---|
| `gradle.properties` | Add `org.gradle.caching=true` |
| `.github/workflows/ci.yml` | Create — full workflow |

---

### Task 1: Enable Gradle build cache

Gradle's build cache is off by default for CI. Adding one line to `gradle.properties` activates it so cached task outputs survive across workflow runs (via the cache step in the workflow).

**Files:**
- Modify: `gradle.properties`

- [ ] **Step 1: Add the build cache flag**

Open `gradle.properties` and append one line so the file reads:

```properties
android.useAndroidX=true
android.enableJetifier=false
android.newDsl=true
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.disallowKotlinSourceSets=false
org.gradle.caching=true
```

- [ ] **Step 2: Verify the flag is picked up**

Run a quick Gradle help task to confirm no property errors:

```bash
./gradlew help --no-daemon 2>&1 | tail -5
```

Expected: last lines end with `BUILD SUCCESSFUL` and no `Unknown property` warnings.

- [ ] **Step 3: Commit**

```bash
git add gradle.properties
git commit -m "build: enable Gradle build cache for CI"
```

---

### Task 2: Create the GitHub Actions workflow

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Create the workflows directory**

```bash
mkdir -p .github/workflows
```

- [ ] **Step 2: Create `.github/workflows/ci.yml`**

Create the file with this exact content:

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build-and-check:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Cache Gradle home
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-home-${{ hashFiles('gradle/libs.versions.toml') }}
          restore-keys: |
            ${{ runner.os }}-gradle-home-

      - name: Cache Gradle build cache
        uses: actions/cache@v4
        with:
          path: ~/.gradle/build-cache
          key: ${{ runner.os }}-gradle-build-${{ hashFiles('gradle/libs.versions.toml') }}
          restore-keys: |
            ${{ runner.os }}-gradle-build-

      - name: Make gradlew executable
        run: chmod +x gradlew

      - name: Run lint
        run: ./gradlew lint --no-daemon --stacktrace

      - name: Run unit tests
        run: ./gradlew :core:media:test --no-daemon --stacktrace

      - name: Assemble debug APK
        run: ./gradlew assembleDebug --no-daemon --stacktrace

      - name: Upload debug APK
        uses: actions/upload-artifact@v4
        with:
          name: laconical-player-debug-${{ github.run_number }}
          path: app/build/outputs/apk/debug/app-debug.apk
          retention-days: 30

      - name: Upload lint report
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: lint-report
          path: app/build/reports/lint-results-debug.html
          retention-days: 30

      - name: Upload test report
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-report
          path: core/media/build/reports/tests/
          retention-days: 30
```

- [ ] **Step 3: Verify YAML syntax locally**

```bash
python3 -c "import yaml, sys; yaml.safe_load(open('.github/workflows/ci.yml')); print('YAML OK')"
```

Expected: `YAML OK`

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add GitHub Actions workflow for lint, test, and debug build"
```

---

### Task 3: Push and verify CI passes

- [ ] **Step 1: Push to main**

```bash
git push origin main
```

- [ ] **Step 2: Open the Actions tab**

Navigate to `https://github.com/Btema2/Laconical-Player/actions` and watch the `CI` workflow run triggered by the push.

- [ ] **Step 3: Confirm all steps are green**

The `build-and-check` job should show:
- Checkout ✓
- Set up JDK 21 ✓
- Cache Gradle home ✓ (cache miss on first run — that's expected)
- Cache Gradle build cache ✓ (cache miss on first run — expected)
- Make gradlew executable ✓
- Run lint ✓
- Run unit tests ✓
- Assemble debug APK ✓
- Upload debug APK ✓
- Upload lint report ✓
- Upload test report ✓

- [ ] **Step 4: Download and verify the APK artifact**

In the completed workflow run, scroll to the **Artifacts** section at the bottom. Download `laconical-player-debug-<N>` and confirm it contains `app-debug.apk`.

- [ ] **Step 5: Confirm second run uses the cache**

On the second CI run (any subsequent push), the cache steps should say `Cache restored from key: ...` rather than `Cache not found`. This confirms the Gradle home cache is working and subsequent builds will be faster.

---

## Troubleshooting

**`AAPT2` or `compileSdk 35` errors:** The ubuntu-latest runner may be missing `platforms;android-35`. Fix by adding this step after "Make gradlew executable":

```yaml
- name: Install Android SDK platform 35
  run: $ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager "platforms;android-35" "build-tools;35.0.0"
```

**Lint finds real issues:** Fix them before merging. The lint report artifact contains the full HTML report for details.

**`gradlew: Permission denied`:** The `chmod +x gradlew` step handles this, but if gradlew is somehow excluded from git tracking, run `git update-index --chmod=+x gradlew` locally and commit.
