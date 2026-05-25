#!/bin/bash
# One-click rollback of all BATbern microservices to a known-good image.
#
# Resolution order for the rollback target:
#   1. ECR `staging-stable` tag (promoted by deploy-staging.yml after Bruno passes).
#      Robust across time: works even hours or days after a bad deploy, because the
#      tag persists.
#   2. `deployments[1]` from `aws ecs describe-services` (the previous task definition
#      still tracked by ECS). Only useful immediately after a bad deploy, before ECS
#      drops the old deployment. Used as fallback when `staging-stable` is missing
#      (e.g. the very first deploy after this script lands).
#
# Usage:
#   ./scripts/ci/rollback-deployment.sh <environment> [--yes] [--dry-run]
#
# Flags:
#   --yes      skip the 10-second interactive grace period (for CI / scripted use)
#   --dry-run  print what would happen, do not call aws ecs update-service
#
# Examples:
#   ./scripts/ci/rollback-deployment.sh staging                    # interactive
#   ./scripts/ci/rollback-deployment.sh staging --yes              # CI auto-rollback
#   ./scripts/ci/rollback-deployment.sh staging --yes --dry-run    # verify wiring
#
# Refs: docs/plans/bruno-staging-hardening.md §A5

set -e

ENVIRONMENT=""
SKIP_GRACE="false"
DRY_RUN="false"

for arg in "$@"; do
    case "$arg" in
        --yes) SKIP_GRACE="true" ;;
        --dry-run) DRY_RUN="true" ;;
        -*) echo "Unknown flag: $arg" >&2; exit 2 ;;
        *) [ -z "$ENVIRONMENT" ] && ENVIRONMENT="$arg" || { echo "Multiple positional args: $arg" >&2; exit 2; } ;;
    esac
done

if [ -z "$ENVIRONMENT" ]; then
    echo "Usage: $0 <environment> [--yes] [--dry-run]" >&2
    exit 2
fi

echo "=========================================="
echo "🔄 INITIATING ROLLBACK"
echo "Environment: $ENVIRONMENT"
[ "$DRY_RUN" = "true" ] && echo "Mode: DRY RUN (no ECS changes will be applied)"
echo "=========================================="

CLUSTER="batbern-${ENVIRONMENT}"

# (service-name, ecs-pattern-prefix) pairs. The ECS-side service name has a stack-
# generated suffix; we use a list-and-filter approach to find the real name at runtime.
SERVICES=(
    "event-management-service"
    "speaker-coordination-service"
    "partner-coordination-service"
    "attendee-experience-service"
    "company-user-management-service"
)

if [ "$SKIP_GRACE" = "true" ]; then
    echo "--yes supplied; skipping interactive grace period."
else
    echo ""
    echo "WARNING: This will rollback all services to their previous task definitions"
    echo "Press Ctrl+C within 10 seconds to cancel..."
    sleep 10
fi

echo ""
echo "Proceeding with rollback..."
echo ""

ROLLBACK_SUCCESS=0
ROLLBACK_FAILED=0
ROLLBACK_SKIPPED=0

# ─── helpers ─────────────────────────────────────────────────────────────────

# Returns the immutable SHA-based tag (e.g. abc1234-staging.55) that shares a manifest
# with `staging-stable` in the given repo. Empty string if no stable tag exists or
# the manifest is only tagged with mutable labels.
resolve_stable_tag() {
    local repo="$1"

    local digest
    digest=$(aws ecr describe-images \
        --repository-name "$repo" \
        --image-ids imageTag=staging-stable \
        --query 'imageDetails[0].imageDigest' \
        --output text 2>/dev/null)

    if [ -z "$digest" ] || [ "$digest" = "None" ]; then
        return 0  # no staging-stable → empty stdout
    fi

    # All tags pointing to this manifest (whitespace-separated)
    local tags
    tags=$(aws ecr describe-images \
        --repository-name "$repo" \
        --image-ids imageDigest="$digest" \
        --query 'imageDetails[0].imageTags' \
        --output text 2>/dev/null)

    # Pick the immutable per-build tag, e.g. <7hex>-staging.<int>. Skip the mutable
    # `staging-current` / `staging-stable` / `latest` labels.
    echo "$tags" | tr '[:space:]' '\n' \
        | grep -E '^[a-f0-9]{7}-(staging|production)\.[0-9]+$' \
        | head -1
}

# Builds a new task-definition JSON by taking the current one and swapping all
# container image tags to the supplied one. Stdout is the JSON ready for
# `aws ecs register-task-definition --cli-input-json`.
build_rollback_task_def_json() {
    local current_arn="$1"
    local new_tag="$2"

    aws ecs describe-task-definition \
        --task-definition "$current_arn" \
        --query 'taskDefinition' \
        --output json \
        | jq --arg new_tag "$new_tag" '
            del(
                .taskDefinitionArn, .revision, .status, .requiresAttributes,
                .compatibilities, .registeredAt, .registeredBy,
                .deregisteredAt, .enableFaultInjection
            )
            | .containerDefinitions = (.containerDefinitions | map(
                .image |= (sub(":[^:/]+$"; ":" + $new_tag))
            ))
        '
}

# ─── main loop ───────────────────────────────────────────────────────────────

for service in "${SERVICES[@]}"; do
    echo "=========================================="
    echo "Rolling back: $service"
    echo "=========================================="

    REPO="batbern/${ENVIRONMENT}/${service}"

    # Find the ACTUAL ECS service name in the cluster — it has a CloudFormation-
    # generated suffix (e.g. BATbern-staging-EventManagement-ServiceD69D759B-xxx).
    # `describe-services --services <bare-name>` does not work for stack-suffixed
    # services. List + filter instead.
    ECS_SERVICE_ARN=$(aws ecs list-services \
        --cluster "$CLUSTER" \
        --query "serviceArns[?contains(@, '${service%-service}') || contains(@, '$service')] | [0]" \
        --output text 2>/dev/null || echo "")

    if [ -z "$ECS_SERVICE_ARN" ] || [ "$ECS_SERVICE_ARN" = "None" ]; then
        echo "⚠️  Could not locate ECS service for $service in cluster $CLUSTER — skipping"
        ((ROLLBACK_SKIPPED++))
        echo ""
        continue
    fi

    ECS_SERVICE_NAME=$(basename "$ECS_SERVICE_ARN")

    # ─── 1. Resolve target image tag from staging-stable ECR tag ─────────────
    STABLE_TAG=$(resolve_stable_tag "$REPO")
    TARGET_TASK_DEF=""

    if [ -n "$STABLE_TAG" ]; then
        echo "📌 staging-stable resolves to immutable tag: $STABLE_TAG"

        CURRENT_TASK_DEF_ARN=$(aws ecs describe-services \
            --cluster "$CLUSTER" \
            --services "$ECS_SERVICE_NAME" \
            --query 'services[0].taskDefinition' \
            --output text)

        echo "   Current task definition: $CURRENT_TASK_DEF_ARN"

        if [ "$DRY_RUN" = "true" ]; then
            echo "🟡 DRY RUN: would register new task def with image tag '$STABLE_TAG'"
            echo "🟡 DRY RUN: would update-service '$ECS_SERVICE_NAME' to use it"
            ((ROLLBACK_SUCCESS++))
            echo ""
            continue
        fi

        NEW_TASK_DEF_JSON=$(build_rollback_task_def_json "$CURRENT_TASK_DEF_ARN" "$STABLE_TAG")

        if [ -z "$NEW_TASK_DEF_JSON" ] || [ "$NEW_TASK_DEF_JSON" = "null" ]; then
            echo "⚠️  Failed to build rollback task definition JSON — falling back to deployments[1]"
        else
            NEW_TASK_DEF_ARN=$(echo "$NEW_TASK_DEF_JSON" \
                | aws ecs register-task-definition \
                    --cli-input-json file:///dev/stdin \
                    --query 'taskDefinition.taskDefinitionArn' \
                    --output text 2>&1) || NEW_TASK_DEF_ARN=""

            if [ -n "$NEW_TASK_DEF_ARN" ] && [[ "$NEW_TASK_DEF_ARN" == arn:aws:ecs:* ]]; then
                TARGET_TASK_DEF="$NEW_TASK_DEF_ARN"
                echo "   Registered new task definition: $TARGET_TASK_DEF"
            else
                echo "⚠️  register-task-definition failed: $NEW_TASK_DEF_ARN"
                echo "   Falling back to deployments[1] lookup"
            fi
        fi
    else
        echo "ℹ️  staging-stable tag not found in $REPO — falling back to deployments[1]"
    fi

    # ─── 2. Fallback: deployments[1] (previous active deployment) ────────────
    if [ -z "$TARGET_TASK_DEF" ]; then
        DEPLOYMENTS=$(aws ecs describe-services \
            --cluster "$CLUSTER" \
            --services "$ECS_SERVICE_NAME" \
            --query 'services[0].deployments[*].taskDefinition' \
            --output json)

        CURRENT_TASK=$(echo "$DEPLOYMENTS" | jq -r '.[0]')
        PREVIOUS_TASK=$(echo "$DEPLOYMENTS" | jq -r '.[1]')

        echo "   Current task definition:  $CURRENT_TASK"
        echo "   Previous task definition: $PREVIOUS_TASK"

        if [ "$PREVIOUS_TASK" = "null" ] || [ -z "$PREVIOUS_TASK" ]; then
            echo "⚠️  ERROR: no previous task definition tracked for $service"
            echo "   This may be the first deployment, or ECS has dropped the old"
            echo "   deployment. Cannot rollback without staging-stable either."
            ((ROLLBACK_FAILED++))
            echo ""
            continue
        fi

        if [ "$CURRENT_TASK" = "$PREVIOUS_TASK" ]; then
            echo "ℹ️  Service is already running the previous task definition; nothing to do"
            ((ROLLBACK_SUCCESS++))
            echo ""
            continue
        fi

        TARGET_TASK_DEF="$PREVIOUS_TASK"
    fi

    # ─── 3. Apply the rollback ───────────────────────────────────────────────
    if [ "$DRY_RUN" = "true" ]; then
        echo "🟡 DRY RUN: would update-service '$ECS_SERVICE_NAME' → $TARGET_TASK_DEF"
        ((ROLLBACK_SUCCESS++))
        echo ""
        continue
    fi

    echo "Rolling back to: $TARGET_TASK_DEF"

    if aws ecs update-service \
        --cluster "$CLUSTER" \
        --service "$ECS_SERVICE_NAME" \
        --task-definition "$TARGET_TASK_DEF" \
        --force-new-deployment \
        --no-cli-pager > /dev/null 2>&1; then

        echo "✓ Rollback initiated for $service"
        ((ROLLBACK_SUCCESS++))
    else
        echo "❌ Failed to initiate rollback for $service"
        ((ROLLBACK_FAILED++))
    fi

    echo ""
done

# Wait for all services to stabilize (skip in dry-run)
if [ "$DRY_RUN" != "true" ] && [ $ROLLBACK_SUCCESS -gt 0 ]; then
    echo "=========================================="
    echo "Waiting for services to stabilize..."
    echo "=========================================="
    echo ""

    for service in "${SERVICES[@]}"; do
        ECS_SERVICE_ARN=$(aws ecs list-services \
            --cluster "$CLUSTER" \
            --query "serviceArns[?contains(@, '${service%-service}') || contains(@, '$service')] | [0]" \
            --output text 2>/dev/null || echo "")
        [ -z "$ECS_SERVICE_ARN" ] || [ "$ECS_SERVICE_ARN" = "None" ] && continue

        ECS_SERVICE_NAME=$(basename "$ECS_SERVICE_ARN")
        echo "Waiting for $service ($ECS_SERVICE_NAME)..."

        if aws ecs wait services-stable \
            --cluster "$CLUSTER" \
            --services "$ECS_SERVICE_NAME" 2>/dev/null; then

            echo "✓ $service is stable"
        else
            echo "⚠️  Warning: $service did not stabilize within timeout"
        fi
    done
fi

# Verify rollback (skip in dry-run)
if [ "$DRY_RUN" != "true" ]; then
    echo ""
    echo "=========================================="
    echo "Verifying rollback..."
    echo "=========================================="

    for service in "${SERVICES[@]}"; do
        ECS_SERVICE_ARN=$(aws ecs list-services \
            --cluster "$CLUSTER" \
            --query "serviceArns[?contains(@, '${service%-service}') || contains(@, '$service')] | [0]" \
            --output text 2>/dev/null || echo "")
        [ -z "$ECS_SERVICE_ARN" ] || [ "$ECS_SERVICE_ARN" = "None" ] && continue

        ECS_SERVICE_NAME=$(basename "$ECS_SERVICE_ARN")

        RUNNING_COUNT=$(aws ecs describe-services \
            --cluster "$CLUSTER" \
            --services "$ECS_SERVICE_NAME" \
            --query 'services[0].runningCount' \
            --output text)

        DESIRED_COUNT=$(aws ecs describe-services \
            --cluster "$CLUSTER" \
            --services "$ECS_SERVICE_NAME" \
            --query 'services[0].desiredCount' \
            --output text)

        if [ "$RUNNING_COUNT" -eq "$DESIRED_COUNT" ]; then
            echo "✓ $service: $RUNNING_COUNT/$DESIRED_COUNT tasks running"
        else
            echo "⚠️  $service: $RUNNING_COUNT/$DESIRED_COUNT tasks running (not fully healthy)"
        fi
    done
fi

echo ""
echo "=========================================="
echo "Rollback Summary"
echo "=========================================="
echo "Successful: $ROLLBACK_SUCCESS"
echo "Failed:     $ROLLBACK_FAILED"
echo "Skipped:    $ROLLBACK_SKIPPED"
echo ""

# Notify team (skip in dry-run)
if [ "$DRY_RUN" != "true" ] && [ -n "${SLACK_WEBHOOK_URL:-}" ]; then
    STATUS="success"
    EMOJI="✅"
    if [ $ROLLBACK_FAILED -gt 0 ]; then
        STATUS="partial"
        EMOJI="⚠️"
    fi

    curl -X POST "$SLACK_WEBHOOK_URL" \
        -H 'Content-Type: application/json' \
        -d "{
            \"text\": \"$EMOJI Rollback completed for $ENVIRONMENT\",
            \"blocks\": [{
                \"type\": \"section\",
                \"text\": {
                    \"type\": \"mrkdwn\",
                    \"text\": \"*Rollback Complete*\n\nEnvironment: $ENVIRONMENT\nSuccessful: $ROLLBACK_SUCCESS\nFailed: $ROLLBACK_FAILED\nStatus: $STATUS\"
                }
            }]
        }" 2>/dev/null || true
fi

if [ "$DRY_RUN" = "true" ]; then
    echo "✅ DRY RUN COMPLETED (no changes applied)"
    exit 0
elif [ $ROLLBACK_FAILED -gt 0 ]; then
    echo "⚠️  ROLLBACK COMPLETED WITH ERRORS"
    echo "Some services failed to rollback - manual intervention may be required"
    exit 1
else
    echo "✅ ROLLBACK COMPLETED SUCCESSFULLY"
    echo "All services rolled back to previous version"
    exit 0
fi
