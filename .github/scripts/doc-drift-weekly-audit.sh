#!/usr/bin/env bash
# doc-drift-weekly-audit.sh
# Lists the commits of a fixed weekly window that touched mapped source paths without
# changing any doc mapped to them. Rules: .github/scripts/doc_drift.py (#1214).
#
# Env:
#   MAPPINGS_FILE  mapping rules (default .github/doc-drift-mappings.yml)
#   SINCE, UNTIL   window as ISO timestamps; default previous Monday 00:00 UTC to this
#                  Monday 00:00 UTC (DRIFT_NOW overrides "now" for tests)
#   GAPS_FILE      output, one "sha|subject|docs" line per gap plus a count line
#                  (default /tmp/doc-drift-gaps.txt)
# Prints WINDOW_SINCE=, WINDOW_UNTIL= and GAPS_COUNT=.

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
MAPPINGS_FILE="${MAPPINGS_FILE:-.github/doc-drift-mappings.yml}"
GAPS_FILE="${GAPS_FILE:-/tmp/doc-drift-gaps.txt}"

read -r default_since default_until < <(python3 "$SCRIPT_DIR/doc_drift.py" window ${DRIFT_NOW:+--now "$DRIFT_NOW"})
SINCE="${SINCE:-$default_since}"
UNTIL="${UNTIL:-$default_until}"
echo "WINDOW_SINCE=$SINCE"
echo "WINDOW_UNTIL=$UNTIL"

python3 "$SCRIPT_DIR/doc_drift.py" --mappings "$MAPPINGS_FILE" \
  audit --since "$SINCE" --until "$UNTIL" --gaps-file "$GAPS_FILE"
