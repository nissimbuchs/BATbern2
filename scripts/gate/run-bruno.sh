#!/usr/bin/env bash
# Runs every Bruno collection against the gate gateway, one at a time, and writes per collection
# a JSON result plus a one-line summary (requests passed/failed/skipped, tests passed/total).
# The collection list is every bruno-tests/*/ directory that holds .bru files (minus
# `environments`), so a new collection cannot be missed; admin-cleanup-api runs first, as in
# scripts/ci/run-bruno-tests.sh. The gate runs the dev-only collections too (no production data).
#
# Usage: scripts/gate/run-bruno.sh <results-dir> [collection...]
# Needs: ORGANIZER_AUTH_TOKEN, SPEAKER_AUTH_TOKEN, PARTNER_AUTH_TOKEN exported.
# Exit:  0 only if every collection produced a result file with no failed request or test.
set -uo pipefail
mkdir -p "${1:?results dir}"
out=$(cd "$1" && pwd)   # absolute: bru runs inside bruno-tests/
shift
cd "$(dirname "$0")/../.."

collections=("$@")
if [ ${#collections[@]} -eq 0 ]; then
    collections=(admin-cleanup-api)
    for dir in bruno-tests/*/; do
        name=$(basename "$dir")
        case "$name" in environments|admin-cleanup-api) continue ;; esac
        [ -n "$(find "$dir" -name '*.bru' -print -quit)" ] && collections+=("$name")
    done
fi

export AUTH_TOKEN="${ORGANIZER_AUTH_TOKEN:?}"
bad=0
printf '%-28s %6s %8s %8s %8s %10s %6s\n' collection status req_ok req_fail req_skip tests secs
for c in "${collections[@]}"; do
    start=$(date +%s)
    (cd bruno-tests && bru run "$c" -r --env gate --output "$out/$c.json") > "$out/$c.log" 2>&1
    rc=$?
    secs=$(( $(date +%s) - start ))
    line=$(jq -r --arg c "$c" --arg secs "$secs" '
        (if type=="array" then .[0] else . end).summary as $s
        | [$c, (if $s.failedRequests == 0 and $s.failedTests == 0 and $s.errorRequests == 0
                then "PASS" else "FAIL" end),
           $s.passedRequests, $s.failedRequests, $s.skippedRequests,
           "\($s.passedTests)/\($s.totalTests)", $secs] | @tsv' "$out/$c.json" 2>/dev/null)
    if [ -z "$line" ]; then
        printf '%-28s %6s (no result file, see %s)\n' "$c" "ERROR" "$out/$c.log"
        bad=1
        continue
    fi
    awk -F'\t' '{printf "%-28s %6s %8s %8s %8s %10s %6s\n",$1,$2,$3,$4,$5,$6,$7}' <<<"$line"
    [[ "$line" == *$'\tPASS\t'* && "$rc" = 0 ]] || bad=1
    grep -q "Skipping invalid file" "$out/$c.log" && { echo "  WARNING: Bruno skipped an invalid .bru file in $c"; bad=1; }
done
exit "$bad"
