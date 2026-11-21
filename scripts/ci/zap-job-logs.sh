#!/usr/bin/env bash
# zap-job-logs.sh <out-dir>
# Saves the console log of every ZAP scan job of this workflow run as <out-dir>/zap-<scan>.log,
# the per-rule PASS / WARN-NEW / FAIL-NEW lines projectdoc's per-rule matrix reads (#1228).
#
# The ZAP actions print those lines only to the job's step output. The `zap.out` the scan jobs
# used to copy is ZAP's own daemon log, written inside the container (/zap/zap.out), so it never
# existed on the runner. A job's log can be fetched once the job has finished, so this runs in
# the summary job, after all scans.
#
# Env: GH_TOKEN (actions: read), REPO (owner/name), RUN_ID.
# Job names: "ZAP API — <api>" → zap-<api>.log, "ZAP Frontend Baseline" → zap-frontend.log.
# Exits 1 when a scan job's log cannot be fetched or carries no rule line, so a broken
# capture fails visibly instead of silently falling back.

set -euo pipefail

out_dir="${1:?usage: zap-job-logs.sh <out-dir>}"
mkdir -p "$out_dir"

jobs=$(gh api --paginate "repos/$REPO/actions/runs/$RUN_ID/jobs" \
  --jq '.jobs[] | select(.name | startswith("ZAP ")) | "\(.id)\t\(.name)"')

if [[ -z "$jobs" ]]; then
  echo "::error::No ZAP scan jobs found in run $RUN_ID"
  exit 1
fi

failed=0
while IFS=$'\t' read -r id name; do
  case "$name" in
    "ZAP API — "*) scan="${name#ZAP API — }" ;;
    "ZAP Frontend Baseline") scan="frontend" ;;
    *) continue ;;
  esac
  file="$out_dir/zap-$scan.log"
  # GitHub prefixes every log line with an ISO timestamp; the parser wants the bare line.
  if ! gh api "repos/$REPO/actions/jobs/$id/logs" | sed -E 's/^[0-9]{4}-[0-9]{2}-[0-9]{2}T[^ ]+ //' > "$file"; then
    echo "::error::Could not fetch the log of job '$name' ($id)"
    failed=1
    continue
  fi
  rules=$(grep -cE '^(PASS|WARN-NEW|FAIL-NEW|IGNORE): ' "$file" || true)
  if [[ "$rules" -eq 0 ]]; then
    echo "::error::The log of job '$name' has no PASS/WARN/FAIL rule line"
    failed=1
  else
    echo "$name: $rules rule lines → $file"
  fi
done <<< "$jobs"

exit "$failed"
