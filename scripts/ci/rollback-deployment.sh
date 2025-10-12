#!/bin/bash
# One-click rollback deployment to previous version
# Usage: ./rollback-deployment.sh <environment>

set -e

ENVIRONMENT=$1

if [ -z "$ENVIRONMENT" ]; then
    echo "Usage: $0 <environment>"
    exit 1
fi

echo "=========================================="
echo "🔄 INITIATING ROLLBACK"
echo "Environment: $ENVIRONMENT"
echo "=========================================="

CLUSTER="batbern-${ENVIRONMENT}"

# Services to rollback
SERVICES=(
    "event-management-service"
    "speaker-coordination-service"
    "partner-coordination-service"
    "attendee-experience-service"
    "company-user-management-service"
)

echo ""
echo "WARNING: This will rollback all services to their previous task definitions"
echo "Press Ctrl+C within 10 seconds to cancel..."
sleep 10

echo ""
echo "Proceeding with rollback..."
echo ""

# Track rollback status
ROLLBACK_SUCCESS=0
ROLLBACK_FAILED=0

for service in "${SERVICES[@]}"; do
    echo "=========================================="
    echo "Rolling back: $service"
    echo "=========================================="

    # Get current and previous task definitions
    echo "Fetching service deployment history..."

    DEPLOYMENTS=$(aws ecs describe-services \
        --cluster "$CLUSTER" \
        --services "$service" \
        --query 'services[0].deployments[*].taskDefinition' \
        --output json)

    CURRENT_TASK=$(echo "$DEPLOYMENTS" | jq -r '.[0]')
    PREVIOUS_TASK=$(echo "$DEPLOYMENTS" | jq -r '.[1]')

    echo "Current task definition: $CURRENT_TASK"
    echo "Previous task definition: $PREVIOUS_TASK"

    if [ "$PREVIOUS_TASK" == "null" ] || [ -z "$PREVIOUS_TASK" ]; then
        echo "⚠️  ERROR: No previous task definition found for $service"
        echo "Cannot rollback - this may be the first deployment"
        ((ROLLBACK_FAILED++))
        echo ""
        continue
    fi

    # Check if we're already on the previous version
    if [ "$CURRENT_TASK" == "$PREVIOUS_TASK" ]; then
        echo "ℹ️  Service is already running the target version"
        echo "Skipping rollback for $service"
        ((ROLLBACK_SUCCESS++))
        echo ""
        continue
    fi

    # Perform rollback
    echo "Rolling back to: $PREVIOUS_TASK"

    if aws ecs update-service \
        --cluster "$CLUSTER" \
        --service "$service" \
        --task-definition "$PREVIOUS_TASK" \
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

# Wait for all services to stabilize
if [ $ROLLBACK_SUCCESS -gt 0 ]; then
    echo "=========================================="
    echo "Waiting for services to stabilize..."
    echo "=========================================="
    echo ""

    for service in "${SERVICES[@]}"; do
        echo "Waiting for $service..."

        if aws ecs wait services-stable \
            --cluster "$CLUSTER" \
            --services "$service" 2>/dev/null; then

            echo "✓ $service is stable"
        else
            echo "⚠️  Warning: $service did not stabilize within timeout"
        fi
    done
fi

# Verify rollback success
echo ""
echo "=========================================="
echo "Verifying rollback..."
echo "=========================================="

for service in "${SERVICES[@]}"; do
    RUNNING_COUNT=$(aws ecs describe-services \
        --cluster "$CLUSTER" \
        --services "$service" \
        --query 'services[0].runningCount' \
        --output text)

    DESIRED_COUNT=$(aws ecs describe-services \
        --cluster "$CLUSTER" \
        --services "$service" \
        --query 'services[0].desiredCount' \
        --output text)

    if [ "$RUNNING_COUNT" -eq "$DESIRED_COUNT" ]; then
        echo "✓ $service: $RUNNING_COUNT/$DESIRED_COUNT tasks running"
    else
        echo "⚠️  $service: $RUNNING_COUNT/$DESIRED_COUNT tasks running (not fully healthy)"
    fi
done

echo ""
echo "=========================================="
echo "Rollback Summary"
echo "=========================================="
echo "Successful: $ROLLBACK_SUCCESS"
echo "Failed: $ROLLBACK_FAILED"
echo ""

# Notify team
if [ -n "$SLACK_WEBHOOK_URL" ]; then
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

if [ $ROLLBACK_FAILED -gt 0 ]; then
    echo "⚠️  ROLLBACK COMPLETED WITH ERRORS"
    echo "Some services failed to rollback - manual intervention may be required"
    exit 1
else
    echo "✅ ROLLBACK COMPLETED SUCCESSFULLY"
    echo "All services rolled back to previous version"
    exit 0
fi
