#!/usr/bin/env bash
# Lists every failed test, assertion or request error of a gate Bruno run, one line each:
# collection file, HTTP status, failed test (description :: error), start of the response body.
# Usage: scripts/gate/bruno-failures.sh <results-dir>
set -euo pipefail
for f in "${1:?results dir}"/*.json; do
    jq -r '
        (if type=="array" then .[0] else . end).results[]
        | . as $r
        | ([.testResults[]?, .assertionResults[]?] | map(select(.status != "pass"))) as $fails
        | select(($fails | length) > 0 or .error != null)
        | (if ($fails | length) > 0 then $fails else [{description: "request", error: .error}] end)[]
        | "\($r.test.filename)\t\($r.response.status // "-")\t\(.description // .lhsExpr) :: \(.error)\t\(($r.response.data // "") | tostring | gsub("\\s+"; " ") | .[0:200])"
    ' "$f"
done
