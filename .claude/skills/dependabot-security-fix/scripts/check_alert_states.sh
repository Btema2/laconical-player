#!/usr/bin/env bash
# Check the current state of specific Dependabot alerts by number, after a fix has shipped and CI has run.
# Success looks like: state=fixed, dismissed_reason=null, auto_dismissed_at=null on every line —
# that combination only comes from GitHub's own re-scan, never from a manual PATCH dismiss.
# Usage: check_alert_states.sh <owner/repo> <alert-number> [alert-number ...]
set -euo pipefail

REPO="${1:?usage: check_alert_states.sh <owner/repo> <alert-number> [alert-number ...]}"
shift

if [[ $# -eq 0 ]]; then
  echo "Provide at least one alert number." >&2
  exit 1
fi

printf 'number\tstate\tauto_dismissed_at\tdismissed_reason\n'
for n in "$@"; do
  gh api "repos/${REPO}/dependabot/alerts/${n}" \
    -q '"\(.number)\t\(.state)\t\(.auto_dismissed_at)\t\(.dismissed_reason)"'
done
