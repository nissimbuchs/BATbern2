#!/usr/bin/env bash
#
# Wait until none of the given CloudFormation stacks is in an *_IN_PROGRESS state.
#
# Why (#1140): a deploy that starts while a stack of the previous deploy is still finishing
# fails with "stack ... is in UPDATE_COMPLETE_CLEANUP_IN_PROGRESS state and can not be updated".
# Deleting a VPC Lambda in the cleanup phase took ~25 min on 2026-10-27, because AWS releases
# its ENIs lazily. Back-to-back PR deploys hit the same race without any Lambda involved.
#
# The stacks are named one by one because the CI role may only describe named stacks:
# list-stacks and an unnamed describe-stacks are denied (IAM simulator, 2026-10-29). A stack
# that does not exist counts as stable. Any other error fails, so a missing permission is
# never mistaken for "nothing in progress".
#
# Usage: wait-for-stable-stacks.sh <timeout-seconds> <stack-name>...
# Exit 0 = all stacks stable. Exit 1 = still in progress at the timeout (names printed), a
# describe call failed, or no stack was named.
# WAIT_POLL_SECONDS overrides the poll interval (default 20).

set -uo pipefail

TIMEOUT="${1:?timeout in seconds required}"
shift
if [ $# -eq 0 ]; then
    echo "::error::no stack names given"
    exit 1
fi
STACKS=("$@")
POLL="${WAIT_POLL_SECONDS:-20}"

# Prints "<stack> <status>" for each stack in progress. Returns 1 if a describe call failed.
busy_stacks() {
    local stack status err
    for stack in "${STACKS[@]}"; do
        if ! status=$(aws cloudformation describe-stacks --stack-name "$stack" \
                --query 'Stacks[0].StackStatus' --output text 2>"$ERR_FILE"); then
            err=$(cat "$ERR_FILE")
            if [[ "$err" == *"does not exist"* ]]; then
                continue
            fi
            echo "::error::describe-stacks $stack failed: $err" >&2
            return 1
        fi
        if [[ "$status" == *_IN_PROGRESS ]]; then
            echo "$stack $status"
        fi
    done
}

ERR_FILE="$(mktemp)"
trap 'rm -f "$ERR_FILE"' EXIT

start=$SECONDS
while true; do
    busy="$(busy_stacks)" || exit 1
    if [ -z "$busy" ]; then
        echo "✅ None of ${#STACKS[@]} stacks in progress"
        exit 0
    fi
    elapsed=$((SECONDS - start))
    if [ "$elapsed" -ge "$TIMEOUT" ]; then
        echo "::error::Stacks still in progress after ${elapsed}s:"
        echo "$busy"
        exit 1
    fi
    echo "⏳ Waiting for stacks to settle (${elapsed}s):"
    echo "$busy" | sed 's/^/    /'
    sleep "$POLL"
done
