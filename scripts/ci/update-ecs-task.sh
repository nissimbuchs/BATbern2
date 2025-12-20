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

# Register new task definition
NEW_TASK_ARN=$(echo $NEW_TASK_DEF | \
  aws ecs register-task-definition \
    --region $REGION \
    --cli-input-json file:///dev/stdin \
    --query 'taskDefinition.taskDefinitionArn' \
    --output text)

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

# Wait for service stability (with timeout)
aws ecs wait services-stable \
  --cluster $CLUSTER \
  --services $SERVICE \
  --region $REGION

echo "🎉 Service $SERVICE updated successfully!"
