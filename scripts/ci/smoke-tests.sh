#!/bin/bash
# Smoke tests for post-deployment validation
# Tests critical endpoints to ensure basic functionality
# Note: We don't use 'set -e' here because we want to run all tests
# and report a summary at the end, not exit on first failure

FRONTEND_URL=$1
API_URL=$2
CDN_URL=${3:-"https://cdn.batbern.ch"}

if [ -z "$FRONTEND_URL" ] || [ -z "$API_URL" ]; then
    echo "Usage: $0 <frontend_url> <api_url> [cdn_url]"
    echo "Example: $0 https://www.batbern.ch https://api.batbern.ch https://cdn.batbern.ch"
    exit 1
fi

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}Smoke Tests${NC}"
echo -e "${BLUE}================================${NC}"
echo ""
echo "Frontend: $FRONTEND_URL"
echo "API: $API_URL"
echo ""

passed=0
failed=0

# Test 1: Frontend is accessible
echo -e "${YELLOW}Test 1:${NC} Frontend accessibility"
response=$(curl -s -o /dev/null -w "%{http_code}" "$FRONTEND_URL" || echo "000")
if [ "$response" = "200" ] || [ "$response" = "301" ] || [ "$response" = "302" ]; then
    echo -e "${GREEN}✓ PASS${NC}: Frontend returned $response"
    ((passed++))
else
    echo -e "${RED}✗ FAIL${NC}: Frontend returned $response (expected 200/301/302)"
    ((failed++))
fi

# Test 2: API Gateway health
echo -e "\n${YELLOW}Test 2:${NC} API Gateway health"
response=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/actuator/health" || echo "000")
if [ "$response" = "200" ]; then
    echo -e "${GREEN}✓ PASS${NC}: API health endpoint returned $response"

    # Check health status
    health_response=$(curl -s "$API_URL/actuator/health" || echo "{}")
    status=$(echo "$health_response" | jq -r '.status' 2>/dev/null || echo "UNKNOWN")
    echo "  Health status: $status"
    ((passed++))
else
    echo -e "${RED}✗ FAIL${NC}: API health endpoint returned $response (expected 200)"
    ((failed++))
fi

# Test 3: Service health checks (proxied via API Gateway's ServiceHealthController)
# Each microservice exposes /actuator/health on its Service Connect DNS; the API Gateway
# proxies these at /services/{name}/health (see api-gateway/.../ServiceHealthController.java).
echo -e "\n${YELLOW}Test 3:${NC} Service health checks"
services=("event-management" "speaker-coordination" "partner-coordination" "attendee-experience" "company-user-management")
service_tests_failed=0

for service in "${services[@]}"; do
    endpoint="$API_URL/services/$service/health"
    response=$(curl -s -o /dev/null -w "%{http_code}" "$endpoint" || echo "000")

    if [ "$response" = "200" ]; then
        echo -e "  ${GREEN}✓${NC} $service is healthy"
    else
        echo -e "  ${RED}✗${NC} $service health check returned $response (expected 200) — $endpoint"
        service_tests_failed=$((service_tests_failed + 1))
    fi
done

if [ $service_tests_failed -eq 0 ]; then
    echo -e "${GREEN}✓ PASS${NC}: All service health checks passed"
    ((passed++))
else
    echo -e "${RED}✗ FAIL${NC}: $service_tests_failed service health check(s) failed"
    ((failed++))
fi

# Tests for database and cache connectivity are intentionally omitted here.
# Spring Boot Actuator already includes DB and Redis health indicators in /actuator/health
# (validated by Test 2). Re-checking them via separate URLs added noise and false warnings
# for endpoints that never existed; the aggregated check is authoritative.

# Test 4: CDN image serving + Lambda@Edge resize
# Validates that CloudFront can serve images AND that the Lambda@Edge resize function
# initialises correctly. A missing 'sharp' module crashes the Lambda at init and returns
# 503 for ALL CDN requests — including plain pass-throughs — so this test catches that
# entire class of bug immediately after each storage-stack deploy.
echo -e "\n${YELLOW}Test 4:${NC} CDN image serving and Lambda@Edge resize"

# Find a known image path from the most recent event via the public API
SAMPLE_IMAGE_PATH=$(curl -s "$API_URL/api/events?status=COMPLETED&size=1" \
    | jq -r '.content[0].photoPath // empty' 2>/dev/null || echo "")

if [ -z "$SAMPLE_IMAGE_PATH" ]; then
    # Fall back to a well-known fixture path present in every environment
    SAMPLE_IMAGE_PATH="events/BATbern58/photos/663b409c-a40a-4dc5-85b5-dd3c6412bae0.jpg"
fi

# 6a: Plain CDN fetch (no resize params) — Lambda@Edge must pass through to S3
plain_status=$(curl -s -o /dev/null -w "%{http_code}" \
    --max-time 10 "$CDN_URL/$SAMPLE_IMAGE_PATH" || echo "000")
if [ "$plain_status" = "200" ]; then
    echo -e "  ${GREEN}✓${NC} Plain image fetch: $plain_status"
    ((passed++))
else
    echo -e "  ${RED}✗ FAIL${NC}: Plain image fetch returned $plain_status (expected 200)"
    echo -e "      URL: $CDN_URL/$SAMPLE_IMAGE_PATH"
    ((failed++))
fi

# 6b: Resize request — Lambda@Edge must load sharp and return image/webp.
#
# Randomise w/h per run so each deploy hits a fresh CloudFront cache key. The
# Lambda's graceful fallback (returns original jpeg if sharp fails) emits the
# upstream cache-control headers (max-age=31536000, immutable), so a single
# regression would otherwise poison one fixed cache entry forever — the test
# would keep seeing the stale jpeg long after the Lambda recovered. A random
# cache-buster guarantees this test always reflects the *current* Lambda state.
rand_w=$((150 + RANDOM % 350))
rand_h=$((150 + RANDOM % 350))
resize_url="$CDN_URL/$SAMPLE_IMAGE_PATH?w=$rand_w&h=$rand_h&fit=cover"
resize_response=$(curl -s -D - -o /dev/null --max-time 15 "$resize_url" || echo "")
resize_status=$(echo "$resize_response" | grep "^HTTP" | awk '{print $2}' | tr -d '\r')
resize_ct=$(echo "$resize_response" | grep -i "^content-type:" | tr -d '\r' | head -1)

if [ "$resize_status" = "200" ] && echo "$resize_ct" | grep -qi "image/webp"; then
    echo -e "  ${GREEN}✓${NC} Resize+WebP: $resize_status, $resize_ct"
    ((passed++))
else
    echo -e "  ${RED}✗ FAIL${NC}: Resize request returned HTTP $resize_status, Content-Type: $resize_ct"
    echo -e "      Expected HTTP 200 + content-type: image/webp"
    echo -e "      URL: $resize_url"
    echo -e "      This usually means the Lambda@Edge function is missing 'sharp' in its package."
    ((failed++))
fi

# Summary
echo ""
echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}Smoke Test Summary${NC}"
echo -e "${BLUE}================================${NC}"
echo -e "${GREEN}Passed: $passed${NC}"
echo -e "${RED}Failed: $failed${NC}"

if [ $failed -gt 0 ]; then
    echo ""
    echo -e "${RED}✗ Smoke tests FAILED${NC}"
    exit 1
else
    echo ""
    echo -e "${GREEN}✓ Smoke tests PASSED${NC}"
    exit 0
fi
