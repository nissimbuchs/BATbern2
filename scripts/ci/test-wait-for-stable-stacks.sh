#!/usr/bin/env bash
#
# Behavioural test for wait-for-stable-stacks.sh (#1140).
#
# The defect it prevents: a deploy started while a stack of the previous deploy was still in
# UPDATE_COMPLETE_CLEANUP_IN_PROGRESS (a VPC Lambda delete takes ~25 min) failed with
# "stack ... is in UPDATE_COMPLETE_CLEANUP_IN_PROGRESS state and can not be updated".
#
# A stub `aws` on PATH replays a scripted sequence of list-stacks answers, one per call.
#
# Usage: scripts/ci/test-wait-for-stable-stacks.sh
# Exit 0 = script behaves. Exit 1 = it does not; the failing case is named.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$REPO_ROOT/scripts/ci/wait-for-stable-stacks.sh"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/wait-stacks-test.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

failures=0

# Stub: each call prints the next line of $WORK/answers (tab-separated "name status" pairs on
# one line, or empty), and records the call.
mkdir -p "$WORK/bin"
cat > "$WORK/bin/aws" <<'STUB'
#!/usr/bin/env bash
n=$(cat "$STUB_DIR/calls" 2>/dev/null || echo 0)
n=$((n + 1))
echo "$n" > "$STUB_DIR/calls"
line=$(sed -n "${n}p" "$STUB_DIR/answers")
[ -z "$line" ] && line=$(tail -n 1 "$STUB_DIR/answers")
printf '%b\n' "$line"
STUB
chmod +x "$WORK/bin/aws"

# run_case <name> <expected-exit> <expected-calls> <answers...>
run_case() {
    local name="$1" expected="$2" calls="$3"
    shift 3
    rm -f "$WORK/calls"
    printf '%s\n' "$@" > "$WORK/answers"
    local out rc
    out=$(PATH="$WORK/bin:$PATH" STUB_DIR="$WORK" WAIT_POLL_SECONDS=0 \
        bash "$SCRIPT" BATbern-staging- 5 2>&1)
    rc=$?
    local made
    made=$(cat "$WORK/calls" 2>/dev/null || echo 0)
    if [ "$rc" -ne "$expected" ]; then
        echo "FAIL: $name: exit $rc, expected $expected"
        echo "$out" | sed 's/^/    /'
        failures=$((failures + 1))
    elif [ -n "$calls" ] && [ "$made" -ne "$calls" ]; then
        echo "FAIL: $name: $made list-stacks calls, expected $calls"
        failures=$((failures + 1))
    else
        echo "ok:   $name"
    fi
}

run_case "returns at once when nothing is in progress" 0 1 ""
run_case "waits through cleanup until the stack is stable" 0 3 \
    'BATbern-staging-InboundEmail\tUPDATE_COMPLETE_CLEANUP_IN_PROGRESS' \
    'BATbern-staging-InboundEmail\tUPDATE_COMPLETE_CLEANUP_IN_PROGRESS' \
    ''
run_case "fails when the stack stays in progress past the timeout" 1 "" \
    'BATbern-staging-InboundEmail\tUPDATE_IN_PROGRESS'

if [ "$failures" -gt 0 ]; then
    echo "$failures case(s) failed"
    exit 1
fi
echo "all cases passed"
