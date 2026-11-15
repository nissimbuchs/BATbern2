#!/usr/bin/env bash
# Runs every Bruno collection against the gate gateway, one at a time, and writes per collection
# a JSON result plus a one-line summary (requests passed/failed/skipped, tests passed/total).
# The order and the collection set mirror scripts/ci/run-bruno-tests.sh (collections, then the
# dev-only ones, which the gate may run because it holds no production data).
#
# Usage: scripts/gate/run-bruno.sh <results-dir> [collection...]
# Needs: ORGANIZER_AUTH_TOKEN, SPEAKER_AUTH_TOKEN, PARTNER_AUTH_TOKEN exported.
set -uo pipefail
cd "$(dirname "$0")/../.."
out="${1:?results dir}"; shift
mkdir -p "$out"

collections=("$@")
if [ ${#collections[@]} -eq 0 ]; then
    collections=(admin-cleanup-api file-upload-api companies-api users-api tasks-api event-types-api
        event-topics-api events-crud-api events sessions-api speaker-pool-api event-full-workflow-api
        partners-api partner-meetings-api
        speaker-portal-api event-publishing-api participants-api newsletter-api notifications-api
        ai-prompts-api session-import-api company-get-or-create-api event-media-api)
fi

export AUTH_TOKEN="${ORGANIZER_AUTH_TOKEN:?}"
printf '%-28s %6s %8s %8s %8s %10s %6s\n' collection status req_ok req_fail req_skip tests secs
for c in "${collections[@]}"; do
    start=$(date +%s)
    (cd bruno-tests && bru run "$c" -r --env gate --output "$out/$c.json") > "$out/$c.log" 2>&1
    rc=$?
    secs=$(( $(date +%s) - start ))
    jq -r --arg c "$c" --arg rc "$rc" --arg secs "$secs" '
        (if type=="array" then .[0] else . end).summary as $s
        | [$c, (if $rc=="0" then "PASS" else "FAIL" end), $s.passedRequests, $s.failedRequests,
           $s.skippedRequests, "\($s.passedTests)/\($s.totalTests)", $secs]
        | @tsv' "$out/$c.json" 2>/dev/null | awk -F'\t' '{printf "%-28s %6s %8s %8s %8s %10s %6s\n",$1,$2,$3,$4,$5,$6,$7}' \
        || printf '%-28s %6s (no result file, see %s)\n' "$c" "ERROR" "$out/$c.log"
    grep -q "Skipping invalid file" "$out/$c.log" && echo "  WARNING: Bruno skipped an invalid .bru file in $c"
done
