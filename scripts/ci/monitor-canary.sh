#!/bin/bash
# Monitor canary deployment metrics
# Usage: ./monitor-canary.sh <canary_percentage>

set -e

CANARY_PERCENTAGE=$1

if [ -z "$CANARY_PERCENTAGE" ]; then
    echo "Usage: $0 <canary_percentage>"
    exit 1
fi

echo "=========================================="
echo "Monitoring Canary Deployment"
echo "Canary Traffic: ${CANARY_PERCENTAGE}%"
echo "=========================================="

ENVIRONMENT="production"
CLUSTER="batbern-${ENVIRONMENT}"
MONITORING_DURATION=300  # 5 minutes
SAMPLE_INTERVAL=30       # 30 seconds

# CloudWatch metric namespace
NAMESPACE="BATbern/Production"

# Thresholds for canary validation
ERROR_RATE_THRESHOLD=1.0     # Max 1% error rate
LATENCY_THRESHOLD_MS=500     # Max 500ms P95 latency
MIN_SUCCESS_RATE=99.0        # Min 99% success rate

echo ""
echo "Monitoring configuration:"
echo "- Duration: ${MONITORING_DURATION}s"
echo "- Sample interval: ${SAMPLE_INTERVAL}s"
echo "- Error rate threshold: ${ERROR_RATE_THRESHOLD}%"
echo "- Latency threshold: ${LATENCY_THRESHOLD_MS}ms"
echo "- Min success rate: ${MIN_SUCCESS_RATE}%"

# Services to monitor
SERVICES=(
    "event-management-service"
    "speaker-coordination-service"
    "partner-coordination-service"
    "attendee-experience-service"
    "company-management-service"
)

START_TIME=$(date +%s)
END_TIME=$((START_TIME + MONITORING_DURATION))
SAMPLES=0
FAILURES=0

echo ""
echo "Starting canary monitoring at $(date)"
echo ""

while [ $(date +%s) -lt $END_TIME ]; do
    ((SAMPLES++))
    echo "Sample #$SAMPLES at $(date +%H:%M:%S)"

    SAMPLE_FAILED=false

    # Check each service health
    for service in "${SERVICES[@]}"; do
        echo -n "  Checking $service... "

        # Check task count
        RUNNING_TASKS=$(aws ecs describe-services \
            --cluster "$CLUSTER" \
            --services "$service" \
            --query 'services[0].runningCount' \
            --output text 2>/dev/null || echo "0")

        DESIRED_TASKS=$(aws ecs describe-services \
            --cluster "$CLUSTER" \
            --services "$service" \
            --query 'services[0].desiredCount' \
            --output text 2>/dev/null || echo "0")

        if [ "$RUNNING_TASKS" != "$DESIRED_TASKS" ]; then
            echo "❌ Task count mismatch: $RUNNING_TASKS/$DESIRED_TASKS"
            SAMPLE_FAILED=true
            continue
        fi

        # Check health endpoint
        API_URL="https://api.batbern.ch"
        SERVICE_NAME=$(echo "$service" | sed 's/-service$//')
        HEALTH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/api/$SERVICE_NAME/health" 2>/dev/null || echo "000")

        if [ "$HEALTH_STATUS" != "200" ]; then
            echo "❌ Health check failed: HTTP $HEALTH_STATUS"
            SAMPLE_FAILED=true
            continue
        fi

        echo "✓ OK ($RUNNING_TASKS tasks, HTTP $HEALTH_STATUS)"
    done

    # Check CloudWatch metrics for canary
    echo -n "  Checking CloudWatch metrics... "

    # Get error rate from CloudWatch (example query)
    ERROR_RATE=$(aws cloudwatch get-metric-statistics \
        --namespace "$NAMESPACE" \
        --metric-name ErrorRate \
        --dimensions Name=Environment,Value=$ENVIRONMENT \
        --statistics Average \
        --start-time "$(date -u -d '5 minutes ago' +%Y-%m-%dT%H:%M:%S)" \
        --end-time "$(date -u +%Y-%m-%dT%H:%M:%S)" \
        --period 300 \
        --query 'Datapoints[0].Average' \
        --output text 2>/dev/null || echo "0.0")

    if [ "$ERROR_RATE" = "None" ] || [ -z "$ERROR_RATE" ]; then
        ERROR_RATE="0.0"
    fi

    # Compare error rate (using awk for floating point comparison)
    if awk "BEGIN {exit !($ERROR_RATE > $ERROR_RATE_THRESHOLD)}"; then
        echo "❌ Error rate too high: ${ERROR_RATE}%"
        SAMPLE_FAILED=true
    else
        echo "✓ Error rate: ${ERROR_RATE}%"
    fi

    # Record failure if any check failed
    if [ "$SAMPLE_FAILED" = true ]; then
        ((FAILURES++))
        echo "  ⚠️  Sample #$SAMPLES FAILED"
    else
        echo "  ✓ Sample #$SAMPLES PASSED"
    fi

    echo ""

    # Don't sleep on last iteration
    if [ $(date +%s) -lt $((END_TIME - SAMPLE_INTERVAL)) ]; then
        sleep $SAMPLE_INTERVAL
    fi
done

echo "=========================================="
echo "Canary Monitoring Complete"
echo "=========================================="
echo "Total samples: $SAMPLES"
echo "Failed samples: $FAILURES"

SUCCESS_RATE=$(awk "BEGIN {printf \"%.2f\", (($SAMPLES - $FAILURES) / $SAMPLES) * 100}")
echo "Success rate: ${SUCCESS_RATE}%"
echo ""

# Determine if canary is healthy
if awk "BEGIN {exit !($SUCCESS_RATE < $MIN_SUCCESS_RATE)}"; then
    echo "❌ CANARY FAILED"
    echo "Success rate ${SUCCESS_RATE}% is below threshold ${MIN_SUCCESS_RATE}%"
    echo "Recommend rolling back deployment"
    exit 1
else
    echo "✅ CANARY HEALTHY"
    echo "Safe to proceed with traffic scaling"
    exit 0
fi
