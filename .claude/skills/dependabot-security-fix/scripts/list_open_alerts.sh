#!/usr/bin/env bash
# List open Dependabot alerts for a repo, tab-separated: number, severity, package, manifest, vulnerable range, summary.
# Usage: list_open_alerts.sh <owner/repo> [severity-filter]
#   severity-filter: optional, one of critical|high|moderate|low. Omit to list all open alerts.
set -euo pipefail

REPO="${1:?usage: list_open_alerts.sh <owner/repo> [severity-filter]}"
SEVERITY="${2:-}"

QUERY='.[] | select(.state=="open")'
if [[ -n "$SEVERITY" ]]; then
  QUERY="$QUERY | select(.security_vulnerability.severity==\"$SEVERITY\")"
fi
QUERY="$QUERY | \"\(.number)\t\(.security_vulnerability.severity)\t\(.dependency.package.name)\t\(.dependency.manifest_path)\t\(.security_vulnerability.vulnerable_version_range)\t\(.security_advisory.summary)\""

gh api "repos/${REPO}/dependabot/alerts" --paginate -q "$QUERY"
