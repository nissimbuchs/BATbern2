#!/bin/bash
# Run regression test suite against an environment
# Usage: ./regression-suite.sh <environment>

set -e

ENVIRONMENT=$1

if [ -z "$ENVIRONMENT" ]; then
    echo "Usage: $0 <environment>"
    exit 1
fi

API_URL="https://api-${ENVIRONMENT}.batbern.ch"
if [ "$ENVIRONMENT" = "production" ]; then
    API_URL="https://api.batbern.ch"
fi

echo "=========================================="
echo "Running Regression Test Suite"
echo "Environment: $ENVIRONMENT"
echo "API URL: $API_URL"
echo "=========================================="

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0

run_test() {
    local test_name=$1
    local test_command=$2

    echo ""
    echo "Running: $test_name"

    if eval "$test_command"; then
        echo "✓ PASS: $test_name"
        ((TESTS_PASSED++))
    else
        echo "✗ FAIL: $test_name"
        ((TESTS_FAILED++))
    fi
}

# Test 1: Event management workflows
run_test "Event Management API Health" \
    "curl -sf '$API_URL/api/events/health' | jq -e '.status == \"UP\"'"

run_test "Event Management Create Endpoint" \
    "curl -sf -o /dev/null -w '%{http_code}' '$API_URL/api/events' | grep -q '200\|401\|403'"

# Test 2: Speaker coordination workflows
run_test "Speaker Coordination API Health" \
    "curl -sf '$API_URL/api/speakers/health' | jq -e '.status == \"UP\"'"

run_test "Speaker Coordination List Endpoint" \
    "curl -sf -o /dev/null -w '%{http_code}' '$API_URL/api/speakers' | grep -q '200\|401\|403'"

# Test 3: Partner analytics workflows
run_test "Partner Analytics API Health" \
    "curl -sf '$API_URL/api/partners/health' | jq -e '.status == \"UP\"'"

run_test "Partner Analytics Dashboard Endpoint" \
    "curl -sf -o /dev/null -w '%{http_code}' '$API_URL/api/partners/analytics' | grep -q '200\|401\|403'"

# Test 4: Attendee experience workflows
run_test "Attendee Experience API Health" \
    "curl -sf '$API_URL/api/content/health' | jq -e '.status == \"UP\"'"

run_test "Content Discovery Search Endpoint" \
    "curl -sf -o /dev/null -w '%{http_code}' '$API_URL/api/content/search' | grep -q '200\|401\|403'"

# Test 5: Authentication flows
run_test "Authentication Endpoint Availability" \
    "curl -sf -o /dev/null -w '%{http_code}' '$API_URL/api/auth/login' | grep -q '200\|400\|401'"

# Test 6: Database connectivity
run_test "Database Health Check" \
    "curl -sf '$API_URL/actuator/health/db' | jq -e '.status == \"UP\"'"

# Test 7: Cache connectivity
run_test "Redis Health Check" \
    "curl -sf '$API_URL/actuator/health/redis' | jq -e '.status == \"UP\"'"

# Test 8: API Gateway routing
run_test "API Gateway Health" \
    "curl -sf '$API_URL/actuator/health' | jq -e '.status == \"UP\"'"

# Test 9: CORS configuration
run_test "CORS Headers Present" \
    "curl -sf -I '$API_URL/api/events/health' | grep -qi 'access-control-allow-origin'"

# Test 10: Rate limiting
run_test "Rate Limit Headers Present" \
    "curl -sf -I '$API_URL/api/events/health' | grep -qi 'x-rate-limit\|ratelimit'"

echo ""
echo "=========================================="
echo "Regression Test Results"
echo "=========================================="
echo "Passed: $TESTS_PASSED"
echo "Failed: $TESTS_FAILED"
echo ""

if [ $TESTS_FAILED -gt 0 ]; then
    echo "❌ REGRESSION TESTS FAILED"
    exit 1
else
    echo "✅ ALL REGRESSION TESTS PASSED"
    exit 0
fi
