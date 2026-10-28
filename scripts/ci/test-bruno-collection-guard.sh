#!/usr/bin/env bash
#
# Behavioural test for the collection-coverage guard in run-bruno-tests.sh (#1135).
#
# The defect it prevents: run-bruno-tests.sh runs a fixed list of collections, so a new
# bruno-tests/<dir>/ that nobody adds to the list never runs, while the Bruno job stays
# green. bruno-tests/events/ sat like that from its creation until #1135.
#
# The guard is exercised by running the real script with --check-collections (no tokens,
# no Bruno CLI, no network) against the real tree and against temp copies of it.
#
# Usage: scripts/ci/test-bruno-collection-guard.sh
# Exit 0 = guard behaves. Exit 1 = it does not; the failing case is named.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$REPO_ROOT/scripts/ci/run-bruno-tests.sh"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/bruno-guard-test.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

failures=0

# run_case <name> <expected-exit> <tree-dir> [expected-substring-in-output]
run_case() {
    local name="$1" expected="$2" tree="$3" needle="${4:-}"
    local out rc
    out=$(cd "$tree" && bash "$SCRIPT" development --check-collections 2>&1)
    rc=$?
    if [ "$rc" -ne "$expected" ]; then
        echo "FAIL: $name: exit $rc, expected $expected"
        echo "$out" | sed 's/^/    /'
        failures=$((failures + 1))
        return
    fi
    if [ -n "$needle" ] && ! grep -qF -- "$needle" <<<"$out"; then
        echo "FAIL: $name: output does not mention '$needle'"
        echo "$out" | sed 's/^/    /'
        failures=$((failures + 1))
        return
    fi
    echo "ok:   $name"
}

# Fresh copy of the real tree for each mutation.
fresh_tree() {
    local dir="$WORK/$1"
    mkdir -p "$dir"
    cp -r "$REPO_ROOT/bruno-tests" "$dir/bruno-tests"
    echo "$dir"
}

# 1. The committed tree is fully registered.
run_case "real tree passes" 0 "$REPO_ROOT" "Collection coverage"

# 2. A new top-level collection that nobody registered fails, and is named.
t=$(fresh_tree unregistered)
mkdir -p "$t/bruno-tests/rogue-api"
printf 'meta {\n  name: x\n  type: http\n  seq: 1\n}\n' > "$t/bruno-tests/rogue-api/01-x.bru"
run_case "unregistered collection fails" 1 "$t" "bruno-tests/rogue-api/"

# 3. Every offender is reported, not just the first.
mkdir -p "$t/bruno-tests/another-api"
cp "$t/bruno-tests/rogue-api/01-x.bru" "$t/bruno-tests/another-api/"
run_case "all offenders reported" 1 "$t" "bruno-tests/another-api/"

# 4. .bru files only in a subfolder still count (bru run -r recurses).
t=$(fresh_tree nested)
mkdir -p "$t/bruno-tests/nested-api/sub"
printf 'meta {\n  name: x\n  type: http\n  seq: 1\n}\n' > "$t/bruno-tests/nested-api/sub/01-x.bru"
run_case "nested-only collection fails" 1 "$t" "bruno-tests/nested-api/"

# 5. A directory with no .bru files is not a collection and is ignored.
t=$(fresh_tree no-bru)
mkdir -p "$t/bruno-tests/fixtures"
echo '{}' > "$t/bruno-tests/fixtures/data.json"
run_case "directory without .bru is ignored" 0 "$t"

# 6. Removing a registered collection's directory does not trip the guard (the runner
#    reports it as skipped instead).
t=$(fresh_tree removed)
rm -rf "$t/bruno-tests/events"
run_case "missing registered directory is not an offender" 0 "$t"

if [ "$failures" -gt 0 ]; then
    echo "$failures case(s) failed"
    exit 1
fi
echo "All Bruno collection-guard cases passed"
