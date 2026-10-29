#!/usr/bin/env bash
#
# Behavioural test for wait-for-stable-stacks.sh (#1140).
#
# The defect it prevents: a deploy started while a stack of the previous deploy was still in
# UPDATE_COMPLETE_CLEANUP_IN_PROGRESS (a VPC Lambda delete takes ~25 min) failed with
# "stack ... is in UPDATE_COMPLETE_CLEANUP_IN_PROGRESS state and can not be updated".
#
# A stub `aws` on PATH answers `describe-stacks --stack-name X` from $WORK/state/X, one line per
# call (the last line repeats). MISSING answers like CloudFormation for a stack that does not
# exist, DENIED like IAM for a call the role may not make. The CI role may only describe named
# stacks: list-stacks and an unnamed describe-stacks are denied (measured 2026-10-29), so the
# stub denies them too.
#
# Usage: scripts/ci/test-wait-for-stable-stacks.sh
# Exit 0 = script behaves. Exit 1 = it does not; the failing case is named.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$REPO_ROOT/scripts/ci/wait-for-stable-stacks.sh"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/wait-stacks-test.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

failures=0

mkdir -p "$WORK/bin"
cat > "$WORK/bin/aws" <<'STUB'
#!/usr/bin/env bash
deny() {
    echo "An error occurred (AccessDenied) when calling the $1 operation: not authorized" >&2
    exit 254
}
[ "$2" = "list-stacks" ] && deny ListStacks
stack=""
while [ $# -gt 0 ]; do
    [ "$1" = "--stack-name" ] && stack="$2"
    shift
done
[ -z "$stack" ] && deny DescribeStacks
echo x >> "$STUB_DIR/calls"
n=$(cat "$STUB_DIR/count.$stack" 2>/dev/null || echo 0)
n=$((n + 1))
echo "$n" > "$STUB_DIR/count.$stack"
status=$(sed -n "${n}p" "$STUB_DIR/state/$stack")
[ -z "$status" ] && status=$(tail -n 1 "$STUB_DIR/state/$stack")
case "$status" in
    MISSING)
        echo "An error occurred (ValidationError) when calling the DescribeStacks operation: Stack with id $stack does not exist" >&2
        exit 254 ;;
    DENIED) deny DescribeStacks ;;
    *) echo "$status" ;;
esac
STUB
chmod +x "$WORK/bin/aws"

# run_case <name> <expected-exit> <expected-calls> <stack=status,status,...>...
run_case() {
    local name="$1" expected="$2" calls="$3"
    shift 3
    rm -rf "$WORK/state" "$WORK"/count.* "$WORK/calls"
    mkdir -p "$WORK/state"
    local stacks=() spec
    for spec in "$@"; do
        stacks+=("${spec%%=*}")
        tr ',' '\n' <<<"${spec#*=}" > "$WORK/state/${spec%%=*}"
    done
    local out rc
    out=$(PATH="$WORK/bin:$PATH" STUB_DIR="$WORK" WAIT_POLL_SECONDS=0 \
        bash "$SCRIPT" 5 "${stacks[@]}" 2>&1)
    rc=$?
    local made
    made=0
    [ -f "$WORK/calls" ] && made=$(wc -l < "$WORK/calls")
    if [ "$rc" -ne "$expected" ]; then
        echo "FAIL: $name: exit $rc, expected $expected"
        echo "$out" | sed 's/^/    /'
        failures=$((failures + 1))
    elif [ -n "$calls" ] && [ "$made" -ne "$calls" ]; then
        echo "FAIL: $name: $made describe-stacks calls, expected $calls"
        failures=$((failures + 1))
    else
        echo "ok:   $name"
    fi
}

run_case "returns at once when every stack is stable" 0 2 \
    "BATbern-staging-Network=UPDATE_COMPLETE" \
    "BATbern-staging-InboundEmail=UPDATE_COMPLETE"
run_case "waits through cleanup until the stack is stable" 0 6 \
    "BATbern-staging-Network=UPDATE_COMPLETE" \
    "BATbern-staging-InboundEmail=UPDATE_COMPLETE_CLEANUP_IN_PROGRESS,UPDATE_COMPLETE_CLEANUP_IN_PROGRESS,UPDATE_COMPLETE"
run_case "treats a stack that does not exist as stable" 0 2 \
    "BATbern-staging-DNS=MISSING" \
    "BATbern-staging-Network=UPDATE_COMPLETE"
run_case "fails when the role may not describe a stack" 1 "" \
    "BATbern-staging-Network=DENIED"
run_case "fails when the stack stays in progress past the timeout" 1 "" \
    "BATbern-staging-InboundEmail=UPDATE_IN_PROGRESS"
run_case "fails without any stack name" 1 ""

if [ "$failures" -gt 0 ]; then
    echo "$failures case(s) failed"
    exit 1
fi
echo "all cases passed"
