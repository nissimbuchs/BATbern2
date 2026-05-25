#!/bin/bash
set -e

# Fast-path ECS deployment script
# Bypasses CloudFormation by directly updating ECS task definitions
# Usage: ./update-ecs-task.sh <cluster> <service> <image-tag>

CLUSTER=$1
SERVICE=$2
IMAGE_TAG=$3
REGION=${AWS_REGION:-eu-central-1}

if [ -z "$CLUSTER" ] || [ -z "$SERVICE" ] || [ -z "$IMAGE_TAG" ]; then
  echo "Usage: $0 <cluster> <service> <image-tag>"
  echo "Example: $0 batbern-staging BATbern-staging-EventManagement-ServiceXXXX abc1234-staging.567"
  exit 1
fi

echo "🚀 Fast-path deployment: $SERVICE with image tag $IMAGE_TAG"

# Get current task definition
CURRENT_TASK_DEF=$(aws ecs describe-services \
  --cluster $CLUSTER \
  --services $SERVICE \
  --region $REGION \
  --query 'services[0].taskDefinition' \
  --output text)

if [ "$CURRENT_TASK_DEF" = "None" ] || [ -z "$CURRENT_TASK_DEF" ]; then
  echo "::error::Service not found: $SERVICE"
  exit 1
fi

echo "📋 Current task definition: $CURRENT_TASK_DEF"

# Get task definition JSON and update image tag
TASK_DEF_JSON=$(aws ecs describe-task-definition \
  --task-definition $CURRENT_TASK_DEF \
  --region $REGION \
  --query 'taskDefinition')

# Extract ECR repository from current image
ECR_REPO=$(echo $TASK_DEF_JSON | jq -r '.containerDefinitions[0].image' | cut -d: -f1)

echo "📦 ECR repository: $ECR_REPO"
echo "🏷️  New image tag: $IMAGE_TAG"

# Create new task definition with updated image
NEW_TASK_DEF=$(echo $TASK_DEF_JSON | jq \
  --arg IMAGE "$ECR_REPO:$IMAGE_TAG" \
  'del(.taskDefinitionArn, .revision, .status, .requiresAttributes, .compatibilities, .registeredAt, .registeredBy) |
   .containerDefinitions[0].image = $IMAGE')

# Write task definition to temp file (more reliable than stdin pipe)
TEMP_TASK_FILE=$(mktemp)
echo "$NEW_TASK_DEF" > "$TEMP_TASK_FILE"

# Register new task definition
NEW_TASK_ARN=$(aws ecs register-task-definition \
  --region $REGION \
  --cli-input-json "file://$TEMP_TASK_FILE" \
  --query 'taskDefinition.taskDefinitionArn' \
  --output text)

# Clean up temp file
rm -f "$TEMP_TASK_FILE"

echo "✅ Registered new task definition: $NEW_TASK_ARN"

# Update service to use new task definition
aws ecs update-service \
  --cluster $CLUSTER \
  --service $SERVICE \
  --task-definition $NEW_TASK_ARN \
  --region $REGION \
  --force-new-deployment \
  --query 'service.serviceName' \
  --output text

echo "⏳ Waiting for service to stabilize..."

# Manual stability poll instead of `aws ecs wait services-stable`.
#
# The CLI waiter is hard-coded to 40 attempts × 15s = 10 min, with no flag
# to extend. The api-gateway service routinely takes 10–12 min to fully
# settle Service Connect endpoints during a deploy (see the inline comment
# in infrastructure/lib/constructs/domain-service-construct.ts:230-235 —
# minHealthyPercent=50 was lowered specifically because this hangs).
#
# Real-world example: run 26404227760 had the api-gateway service reach
# `rolloutState: COMPLETED` at 15:08:49 UTC — 10 seconds AFTER the
# default waiter timed out at 15:08:39 UTC. The deploy was successful but
# the workflow reported failure.
#
# This loop polls `describe-services` for `rolloutState == COMPLETED`,
# with a configurable timeout (default 20 min) and 10s polling interval.
# Override via STABILITY_TIMEOUT_SECS env var.

STABILITY_TIMEOUT_SECS="${STABILITY_TIMEOUT_SECS:-1200}"
STABILITY_POLL_INTERVAL_SECS=10
elapsed=0

while [ "$elapsed" -lt "$STABILITY_TIMEOUT_SECS" ]; do
  ROLLOUT_STATE=$(aws ecs describe-services \
    --cluster "$CLUSTER" \
    --services "$SERVICE" \
    --region "$REGION" \
    --query 'services[0].deployments[?status==`PRIMARY`].rolloutState | [0]' \
    --output text 2>/dev/null)

  case "$ROLLOUT_STATE" in
    COMPLETED)
      echo "🎉 Service $SERVICE reached COMPLETED rolloutState (elapsed ${elapsed}s)"
      break
      ;;
    FAILED)
      echo "::error::Service $SERVICE rolloutState is FAILED — deployment did not succeed"
      aws ecs describe-services \
        --cluster "$CLUSTER" \
        --services "$SERVICE" \
        --region "$REGION" \
        --query 'services[0].events[0:5].{at:createdAt,msg:message}'
      exit 1
      ;;
    IN_PROGRESS|"")
      # IN_PROGRESS is the normal mid-deploy state; "" can briefly appear
      # between the describe call and the deployment object being created.
      printf "  rolloutState=%s elapsed=%ds (timeout %ds)\n" \
        "${ROLLOUT_STATE:-PENDING}" "$elapsed" "$STABILITY_TIMEOUT_SECS"
      ;;
    *)
      printf "  unexpected rolloutState=%s elapsed=%ds — continuing to poll\n" \
        "$ROLLOUT_STATE" "$elapsed"
      ;;
  esac

  sleep "$STABILITY_POLL_INTERVAL_SECS"
  elapsed=$((elapsed + STABILITY_POLL_INTERVAL_SECS))
done

if [ "$elapsed" -ge "$STABILITY_TIMEOUT_SECS" ]; then
  echo "::error::Service $SERVICE did not reach COMPLETED within ${STABILITY_TIMEOUT_SECS}s — final rolloutState=${ROLLOUT_STATE}"
  echo "::error::Last ECS events:"
  aws ecs describe-services \
    --cluster "$CLUSTER" \
    --services "$SERVICE" \
    --region "$REGION" \
    --query 'services[0].events[0:5].{at:createdAt,msg:message}'
  exit 1
fi

echo "🎉 Service $SERVICE updated successfully!"
