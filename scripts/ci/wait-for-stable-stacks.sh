#!/usr/bin/env bash
#
# Wait until no CloudFormation stack with the given name prefix is in an *_IN_PROGRESS state.
#
# Why (#1140): a deploy that starts while a stack of the previous deploy is still finishing
# fails with "stack ... is in UPDATE_COMPLETE_CLEANUP_IN_PROGRESS state and can not be updated".
# Deleting a VPC Lambda in the cleanup phase took ~25 min on 2026-10-27, because AWS releases
# its ENIs lazily. Back-to-back PR deploys hit the same race without any Lambda involved.
#
# Usage: wait-for-stable-stacks.sh <stack-name-prefix> <timeout-seconds>
# Exit 0 = all stacks stable. Exit 1 = still in progress at the timeout (names printed).
# WAIT_POLL_SECONDS overrides the poll interval (default 20).

set -uo pipefail

PREFIX="${1:?stack name prefix required}"
TIMEOUT="${2:?timeout in seconds required}"
POLL="${WAIT_POLL_SECONDS:-20}"

IN_PROGRESS_STATES=(
    CREATE_IN_PROGRESS DELETE_IN_PROGRESS
    UPDATE_IN_PROGRESS UPDATE_COMPLETE_CLEANUP_IN_PROGRESS
    UPDATE_ROLLBACK_IN_PROGRESS UPDATE_ROLLBACK_COMPLETE_CLEANUP_IN_PROGRESS
    ROLLBACK_IN_PROGRESS REVIEW_IN_PROGRESS
    IMPORT_IN_PROGRESS IMPORT_ROLLBACK_IN_PROGRESS
)

busy_stacks() {
    aws cloudformation list-stacks \
        --stack-status-filter "${IN_PROGRESS_STATES[@]}" \
        --query "StackSummaries[?starts_with(StackName, '${PREFIX}')].[StackName,StackStatus]" \
        --output text
}

start=$SECONDS
while true; do
    busy="$(busy_stacks)" || { echo "::error::list-stacks failed"; exit 1; }
    busy="$(sed '/^[[:space:]]*$/d' <<<"$busy")"
    if [ -z "$busy" ]; then
        echo "✅ No ${PREFIX}* stack in progress"
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
