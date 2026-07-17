#!/usr/bin/env bash
# Dump a Gradle module's full dependency tree once (all configurations) to a file, then optionally
# grep it for a package name. Avoids the slow/broken pattern of looping `dependencyInsight` over
# many configurations one at a time.
# Usage: dump_dependency_tree.sh <gradle-module e.g. :app> <output-file> [grep-pattern]
set -euo pipefail

MODULE="${1:?usage: dump_dependency_tree.sh <gradle-module e.g. :app> <output-file> [grep-pattern]}"
OUT="${2:?output file path required}"
PATTERN="${3:-}"

./gradlew "${MODULE}:dependencies" > "$OUT" 2>&1

if [[ -n "$PATTERN" ]]; then
  echo "--- matches for '${PATTERN}' in ${OUT} (with surrounding line numbers) ---"
  grep -n -i "$PATTERN" "$OUT" || echo "(no matches — check the module name / package spelling)"
  echo
  echo "Read the surrounding context around each match line (Read tool, not just grep) to find the"
  echo "actual top-level dependency that pulled this in — that parent chain is the root cause."
fi
