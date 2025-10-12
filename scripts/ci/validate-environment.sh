#!/bin/bash
# Validate environment health before promotion
# Usage: ./validate-environment.sh <environment>

set -e

ENVIRONMENT=$1

if [ -z "$ENVIRONMENT" ]; then
    echo "Usage: $0 <environment>"
    exit 1
fi

echo "=========================================="
echo "Validating Environment: $ENVIRONMENT"
echo "=========================================="

CLUSTER="batbern-${ENVIRONMENT}"
API_URL="https://api-${ENVIRONMENT}.batbern.ch"

if [ "$ENVIRONMENT" = "production" ]; then
    API_URL="https://api.batbern.ch"
fi

# Services to check
SERVICES=(
    "event-management-service"
    "speaker-coordination-service"
    "partner-coordination-service"
    "attendee-experience-service"
    "company-user-management-service"
)

echo ""
echo "Checking ECS services status..."
for service in "${SERVICES[@]}"; do
    echo "Checking $service..."

    # Get service status
    SERVICE_STATUS=$(aws ecs describe-services \
        --cluster "$CLUSTER" \
        --services "$service" \
        --query 'services[0].status' \
        --output text 2>/dev/null || echo "NOT_FOUND")

    if [ "$SERVICE_STATUS" != "ACTIVE" ]; then
        echo "❌ ERROR: Service $service is not active (status: $SERVICE_STATUS)"
        exit 1
    fi

    # Check running tasks
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

    if [ "$RUNNING_COUNT" -ne "$DESIRED_COUNT" ]; then
        echo "❌ ERROR: Service $service has $RUNNING_COUNT running tasks but desires $DESIRED_COUNT"
        exit 1
    fi

    echo "  ✓ Status: $SERVICE_STATUS, Tasks: $RUNNING_COUNT/$DESIRED_COUNT"
done

echo ""
echo "Checking API health endpoints..."
for service in events speakers partners content; do
    echo "Checking $service API health..."

    HEALTH_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/api/$service/health" || echo "000")

    if [ "$HEALTH_RESPONSE" != "200" ]; then
        echo "❌ ERROR: $service API health check failed (HTTP $HEALTH_RESPONSE)"
        exit 1
    fi

    echo "  ✓ $service API: HTTP $HEALTH_RESPONSE"
done

echo ""
echo "Checking database connectivity..."
DB_HEALTH=$(curl -s "$API_URL/actuator/health/db" | jq -r '.status' 2>/dev/null || echo "UNKNOWN")

if [ "$DB_HEALTH" != "UP" ]; then
    echo "❌ ERROR: Database health check failed (status: $DB_HEALTH)"
    exit 1
fi

echo "  ✓ Database: $DB_HEALTH"

echo ""
echo "Checking Redis connectivity..."
REDIS_HEALTH=$(curl -s "$API_URL/actuator/health/redis" | jq -r '.status' 2>/dev/null || echo "UNKNOWN")

if [ "$REDIS_HEALTH" != "UP" ]; then
    echo "❌ ERROR: Redis health check failed (status: $REDIS_HEALTH)"
    exit 1
fi

echo "  ✓ Redis: $REDIS_HEALTH"

echo ""
echo "=========================================="
echo "✅ Environment validation passed!"
echo "=========================================="
