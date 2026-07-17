---
name: dependabot-security-fix
description: Root-causes and genuinely fixes GitHub Dependabot security alerts in Gradle/JVM projects (Android, Kotlin, Java) — not by dismissing them, but by tracing WHY the vulnerable version is still resolving and forcing a real fix. Use this whenever the user mentions Dependabot alerts, GitHub security warnings, "N vulnerabilities" on a repo, CVEs in dependencies, the GitHub security tab, dependency-graph/SBOM weirdness, or asks why alerts "won't close" / "won't refresh" / "won't auto-dismiss" even though a version bump was already made. Also use it when a fix was already attempted (e.g. a buildscript or version-catalog pin) but alerts remain open — that's the classic symptom this skill is built to diagnose.
---

# Dependabot Security Alert Resolution

## The trap this skill exists to avoid

The fast way to make a Dependabot alert disappear is `gh api ... --method PATCH -f state=dismissed`. Don't reach for that as a first move. A dismissed alert just means a human said "ignore this" — the vulnerable code path can still be live. A **fixed** alert (`state: "fixed"`, written only by GitHub's own re-scan, never settable via PATCH) means GitHub independently confirmed the vulnerable version is genuinely gone from the resolved dependency graph. Chasing `state: "fixed"` instead of `dismissed` is the whole point of this skill — it's the difference between actually fixing the problem and hiding it from the dashboard.

Corollary: if you genuinely cannot find a real fix (no patched version exists upstream, or the only fix requires something explicitly forbidden by the project — e.g. a pinned SDK/tooling version noted in CLAUDE.md), **stop and tell the user**, don't dismiss and don't force a workaround to look done. A stopped task with an honest blocker is worth more than a green dashboard hiding a real vulnerability.

## Why "just bump the version" usually doesn't work

Most repos already have *some* pin in place by the time alerts are stubbornly stuck open — a version catalog entry, a `buildscript { dependencies { constraints {...} } }` block, a direct dependency bump. If that were enough, the alert would already be fixed. When it isn't, the pin is scoped to a configuration the vulnerable edge doesn't actually flow through. Common Gradle mismatches, all observed in practice:

- `buildscript { constraints {...} }` pins the root project's **buildscript/plugin classpath only** — it has zero effect on any module's runtime, compile, or test configurations.
- A version catalog / single-module `implementation(...)` bump doesn't reach **other modules**, or doesn't reach **test-scope** configurations (`testImplementation`), or doesn't reach a **plugin's internal tooling configs** (AGP's `unified-test-platform-*` configurations are a frequent offender — they pull their own `grpc-netty`/`sdk-common`/etc. dependency trees independent of your app code).
- The SBOM shows the patched version present (because it resolves somewhere) **and** the old vulnerable version present (because it resolves somewhere else) — this coexistence is the tell. It means a real constraint exists but doesn't cover every edge, not that GitHub's scan is stale.

The fix has to reach every configuration in every module that could resolve the dependency, which is a different (broader) mechanism than whatever produced the dual-version state in the first place.

## Workflow

### 1. Triage — what's actually open right now

```bash
gh api repos/<owner>/<repo>/dependabot/alerts --paginate \
  -q '.[] | select(.state=="open") | "\(.number)\t\(.security_vulnerability.severity)\t\(.dependency.package.name)\t\(.dependency.manifest_path)\t\(.security_vulnerability.vulnerable_version_range)\t\(.security_advisory.summary)"'
```

`scripts/list_open_alerts.sh <owner/repo>` wraps this. Group by severity if the user asked to fix "critical" or "high" only — don't touch moderate/low unless asked, and say so explicitly when you stop.

For each package, also pull `relationship` (`direct`/`transitive`) and `manifest_path` — `gh api repos/<owner>/<repo>/dependabot/alerts/<n>`. `manifest_path` is often generic (e.g. `settings.gradle.kts`) because the submitting action attributes the whole resolved graph to one file — don't over-read it as "the fix goes in this exact file."

### 2. Establish ground truth via the SBOM

```bash
gh api repos/<owner>/<repo>/dependency-graph/sbom -q '.sbom.packages[] | select(.name | test("<package-name>"; "i")) | "\(.name)\t\(.versionInfo)"'
```

This is GitHub's actual currently-tracked resolved graph — more authoritative than re-reading your own build files, because it reflects what really got submitted, including any snapshot-merging from multiple CI jobs. If you see **only** the old vulnerable version, the alert may genuinely be stale (rare) or nothing has attempted a fix yet. If you see **both** old and patched versions, that's the dual-version tell from above — proceed to root-cause tracing, the fix is real work, not a refresh problem.

### 3. Root-cause — trace which configuration actually pulls the old version

Don't loop `./gradlew dependencyInsight --configuration X` over many configurations — it's slow and some configuration names (like buildscript's `classpath`) aren't queryable that way. Instead dump the full tree once per module and grep it:

```bash
./gradlew :app:dependencies > /tmp/app_deps.txt 2>&1
grep -n -i "<vulnerable-package>" /tmp/app_deps.txt
```

`scripts/dump_dependency_tree.sh <module>` wraps this (writes to the session scratchpad). Read the surrounding lines (not just the grep hit) to walk up the tree to the actual top-level pull-in — the parent chain is what tells you *why* your existing pin doesn't reach it. In Android/AGP projects, check the `unified-test-platform-*` sections specifically; they're easy to miss because they don't correspond to any dependency you wrote.

Repeat for every module the SBOM says has the package (`:core:media:dependencies`, etc. — configurations resolve independently per module).

### 4. Write the real fix

The mechanism that reaches every configuration in every module/subproject is:

```kotlin
allprojects {
    configurations.all {
        resolutionStrategy {
            force(
                "group:artifact:patched.version",
                // ...one line per vulnerable artifact/module
            )
        }
    }
}
```

Put this in the root `build.gradle.kts`, after the `plugins {}` block. If one already exists from a prior fix, extend its `force(...)` list rather than adding a second block — comment why each entry is there (which Dependabot alert number(s), which edge pulled it in) so the next person (or you, next session) doesn't have to re-derive it.

Picking the target version: prefer a version that's **already resolving cleanly elsewhere in the SBOM** over the newest available. A dependency you've never exercised at a new major version (e.g. jumping a whole major netty line) can carry untested behavior changes; the goal here is closing a real vulnerability, not opportunistic upgrading. If the project has a CLAUDE.md or similar with explicit version pins (SDK versions, etc.), check it before choosing a fix that would violate one — if the only real fix requires violating a documented pin, that's a stop-and-report situation, not a judgment call to make silently.

### 5. Verify locally, before pushing

Two checks, in order:

```bash
./gradlew :app:dependencies > /tmp/app_deps_after.txt 2>&1
grep -n -i "<vulnerable-package>" /tmp/app_deps_after.txt
```

Every previously-bare old-version line must now show a `->` redirect arrow to the patched version (`3.16.0 -> 3.20.0`). A bare old version with no arrow means that edge is still unforced — go back to step 3, you missed a configuration or module.

Then run the actual build/test targets the project defines (e.g. `./gradlew :core:media:test`, `./gradlew assembleDebug`) — a resolution-strategy change can theoretically shift transitive behavior, so don't skip this because "it's just a version force." If a test task reports `UP-TO-DATE` when you expected it to re-verify, use `--rerun-tasks` to force a genuine re-execution before trusting the result.

### 6. Ship and wait

Commit and push (following whatever commit-message conventions the project/user has). This triggers CI and, if configured, an automatic dependency-graph re-submission — that resubmission is what lets GitHub's scanner see the new resolved graph and re-evaluate.

Poll for the run to finish by commit SHA — use the **full** SHA, not the short one; `gh run list --commit <short-sha>` can silently return nothing while the full SHA matches. A Monitor loop or a bounded `until`-poll works well here; don't just wait idle.

### 7. Confirm genuine auto-resolution — don't just trust silence

```bash
gh api repos/<owner>/<repo>/dependabot/alerts/<n> -q '"\(.number)\t\(.state)\t\(.auto_dismissed_at)\t\(.dismissed_reason)"'
```

`scripts/check_alert_states.sh <owner/repo> <n1> <n2> ...` wraps this loop for every alert number you started with. Success is `state: "fixed"` with `dismissed_reason: null` and `auto_dismissed_at: null` on every one — that combination is only produced by GitHub's own re-scan, never by a PATCH call. Also re-pull the SBOM one more time and confirm the old version is gone entirely, not just redirected in your local tree (the point is GitHub's tracked graph, not your local Gradle cache).

If some alerts still show `open` after a completed CI run + fresh SBOM, don't loop the same fix harder — go back to step 3 for those specific packages; there's likely another unreached edge you haven't found yet (a different module, a different configuration, a second unrelated dependency chain pulling the same package).

### 8. After the fact — separate real fixes from routine noise

Once alerts clear, Dependabot's ordinary version-update bot (separate from its security-update bot) may open unrelated PRs — including PRs that literally propose bumping the exact pinned versions you just added, because its version-update scanner reads literal version strings out of build files and offers newer ones regardless of whether they fix a vulnerability. Don't treat "Dependabot opened a PR" as itself a reason to merge:

- Check whether the PR corresponds to a **currently open** alert (re-run step 1) — if there are zero open alerts, it's not a security fix, it's routine housekeeping.
- Be wary of PRs that would bump only *some* modules of a multi-artifact dependency (e.g. one Netty module) to a different version than the others still pinned — merging piecemeal can fragment a `force()` list into an inconsistent, untested mixed-version state.
- Check the project's own pinned-version notes (CLAUDE.md-style) before merging anything touching a tool version (AGP, compileSdk, etc.) that's been explicitly frozen for a documented reason.

## Hard guardrails

- **Never call the Dependabot alerts PATCH/dismiss endpoint** as part of "fixing" alerts, and never suggest `dismissed_reason` values (`tolerable_risk`, `no_bandwidth`, etc.) as a resolution path, unless the user explicitly asks you to dismiss something after you've reported that no genuine fix exists. The default posture is: genuine fix, or stop and report — not a dismissal shortcut either of you can point to later as "resolved."
- **Don't touch severities the user didn't ask about.** If asked to fix "critical," don't also silently dismiss or fix "moderate" — say what's out of scope.
- **If blocked, say so plainly**, including what you tried and why it didn't reach the edge — that diagnostic is genuinely useful even when the task isn't fully done.
