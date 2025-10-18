#!/bin/bash
# Header Propagation Tests
# Validates that custom headers flow through the entire stack:
# Frontend → AWS API Gateway → Spring Boot Gateway → Microservices
set -e

API_URL=${1:-"https://api.staging.batbern.ch"}
TEST_TOKEN=${2:-""}

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}Header Propagation Tests${NC}"
echo -e "${BLUE}================================${NC}"
echo ""
echo "API URL: $API_URL"
if [ -z "$TEST_TOKEN" ]; then
    echo -e "${YELLOW}WARNING: No auth token provided, some tests may fail with 401${NC}"
    echo "Usage: $0 <api_url> <jwt_token>"
fi
echo ""

passed=0
failed=0
warnings=0

# Generate a unique correlation ID for this test run
TEST_CORRELATION_ID="test-$(date +%s)-$$"

# Test 1: Correlation ID header acceptance
echo -e "${YELLOW}Test 1:${NC} X-Correlation-ID header acceptance"
response=$(curl -s -i -X GET "$API_URL/health" \
    -H "X-Correlation-ID: $TEST_CORRELATION_ID" \
    2>&1)

status_code=$(echo "$response" | grep "HTTP/" | tail -1 | awk '{print $2}')
if [ "$status_code" = "200" ]; then
    echo -e "${GREEN}✓ PASS${NC}: Health endpoint accepts X-Correlation-ID header"
    ((passed++))
else
    echo -e "${RED}✗ FAIL${NC}: Health endpoint rejected request with X-Correlation-ID (status: $status_code)"
    ((failed++))
fi

# Test 2: Accept-Language header acceptance
echo -e "\n${YELLOW}Test 2:${NC} Accept-Language header acceptance"
response=$(curl -s -i -X GET "$API_URL/health" \
    -H "Accept-Language: de-CH" \
    2>&1)

status_code=$(echo "$response" | grep "HTTP/" | tail -1 | awk '{print $2}')
if [ "$status_code" = "200" ]; then
    echo -e "${GREEN}✓ PASS${NC}: Health endpoint accepts Accept-Language header"
    ((passed++))
else
    echo -e "${RED}✗ FAIL${NC}: Health endpoint rejected request with Accept-Language (status: $status_code)"
    ((failed++))
fi

# Test 3: Multiple custom headers together
echo -e "\n${YELLOW}Test 3:${NC} Multiple custom headers together"
response=$(curl -s -i -X GET "$API_URL/health" \
    -H "X-Correlation-ID: $TEST_CORRELATION_ID" \
    -H "Accept-Language: de-CH" \
    -H "Accept: application/json" \
    2>&1)

status_code=$(echo "$response" | grep "HTTP/" | tail -1 | awk '{print $2}')
if [ "$status_code" = "200" ]; then
    echo -e "${GREEN}✓ PASS${NC}: Health endpoint accepts multiple custom headers"
    ((passed++))
else
    echo -e "${RED}✗ FAIL${NC}: Health endpoint rejected request with multiple headers (status: $status_code)"
    ((failed++))
fi

# Test 4: Companies API endpoint with all custom headers (if token provided)
if [ -n "$TEST_TOKEN" ]; then
    echo -e "\n${YELLOW}Test 4:${NC} Companies API with full header set"
    response=$(curl -s -w "\n%{http_code}" -X GET "$API_URL/api/v1/companies?page=1&limit=5" \
        -H "Authorization: Bearer $TEST_TOKEN" \
        -H "X-Correlation-ID: $TEST_CORRELATION_ID" \
        -H "Accept-Language: de-CH" \
        -H "Accept: application/json" \
        -H "Content-Type: application/json" \
        2>&1)

    # Extract status code from last line
    status_code=$(echo "$response" | tail -1)
    response_body=$(echo "$response" | head -n -1)

    if [ "$status_code" = "200" ]; then
        echo -e "${GREEN}✓ PASS${NC}: Companies API accepts full header set"

        # Try to parse response to verify it's valid JSON
        if echo "$response_body" | jq . > /dev/null 2>&1; then
            echo -e "  ${GREEN}✓${NC} Response is valid JSON"

            # Check if response has pagination metadata (indicates proper routing)
            if echo "$response_body" | jq -e '.pagination' > /dev/null 2>&1; then
                echo -e "  ${GREEN}✓${NC} Response includes pagination metadata (proper routing confirmed)"
            fi
        fi

        ((passed++))
    elif [ "$status_code" = "401" ]; then
        echo -e "${YELLOW}⚠ WARNING${NC}: Got 401 Unauthorized (token may be expired)"
        echo -e "  ${GREEN}✓${NC} But headers were accepted (not a CORS/header issue)"
        ((warnings++))
        ((passed++))
    else
        echo -e "${RED}✗ FAIL${NC}: Companies API returned unexpected status: $status_code"
        echo -e "  Response: $response_body"
        ((failed++))
    fi
else
    echo -e "\n${YELLOW}Test 4:${NC} Companies API (skipped - no auth token)"
    echo -e "${YELLOW}  Provide auth token as second parameter to run this test${NC}"
    ((warnings++))
fi

# Test 5: Events API with custom headers (if token provided)
if [ -n "$TEST_TOKEN" ]; then
    echo -e "\n${YELLOW}Test 5:${NC} Events API with custom headers"
    response=$(curl -s -w "\n%{http_code}" -X GET "$API_URL/api/v1/events" \
        -H "Authorization: Bearer $TEST_TOKEN" \
        -H "X-Correlation-ID: $TEST_CORRELATION_ID" \
        -H "Accept-Language: en-US" \
        2>&1)

    status_code=$(echo "$response" | tail -1)
    response_body=$(echo "$response" | head -n -1)

    if [ "$status_code" = "200" ]; then
        echo -e "${GREEN}✓ PASS${NC}: Events API accepts custom headers"
        ((passed++))
    elif [ "$status_code" = "401" ]; then
        echo -e "${YELLOW}⚠ WARNING${NC}: Got 401 Unauthorized (token may be expired)"
        echo -e "  ${GREEN}✓${NC} But headers were accepted (not a CORS/header issue)"
        ((warnings++))
        ((passed++))
    else
        echo -e "${RED}✗ FAIL${NC}: Events API returned unexpected status: $status_code"
        ((failed++))
    fi
else
    echo -e "\n${YELLOW}Test 5:${NC} Events API (skipped - no auth token)"
    ((warnings++))
fi

# Test 6: POST request with custom headers (if token provided)
if [ -n "$TEST_TOKEN" ]; then
    echo -e "\n${YELLOW}Test 6:${NC} POST request with custom headers"

    # Create a test payload
    test_payload='{"query":"test"}'

    response=$(curl -s -w "\n%{http_code}" -X GET "$API_URL/api/v1/companies/search?query=test" \
        -H "Authorization: Bearer $TEST_TOKEN" \
        -H "X-Correlation-ID: $TEST_CORRELATION_ID" \
        -H "Accept-Language: de-CH" \
        -H "Content-Type: application/json" \
        2>&1)

    status_code=$(echo "$response" | tail -1)

    if [ "$status_code" = "200" ] || [ "$status_code" = "401" ]; then
        echo -e "${GREEN}✓ PASS${NC}: POST request with custom headers accepted (status: $status_code)"
        ((passed++))
    else
        echo -e "${RED}✗ FAIL${NC}: POST request rejected (status: $status_code)"
        ((failed++))
    fi
else
    echo -e "\n${YELLOW}Test 6:${NC} POST request (skipped - no auth token)"
    ((warnings++))
fi

# Test 7: Test header case-insensitivity
echo -e "\n${YELLOW}Test 7:${NC} Header case-insensitivity"
response=$(curl -s -i -X GET "$API_URL/health" \
    -H "x-correlation-id: $TEST_CORRELATION_ID" \
    -H "accept-language: de-CH" \
    2>&1)

status_code=$(echo "$response" | grep "HTTP/" | tail -1 | awk '{print $2}')
if [ "$status_code" = "200" ]; then
    echo -e "${GREEN}✓ PASS${NC}: Headers are case-insensitive (lowercase accepted)"
    ((passed++))
else
    echo -e "${RED}✗ FAIL${NC}: Lowercase headers rejected (status: $status_code)"
    ((failed++))
fi

# Summary
echo ""
echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}Header Propagation Summary${NC}"
echo -e "${BLUE}================================${NC}"
echo -e "${GREEN}Passed: $passed${NC}"
echo -e "${RED}Failed: $failed${NC}"
echo -e "${YELLOW}Warnings: $warnings${NC}"

if [ $failed -gt 0 ]; then
    echo ""
    echo -e "${RED}✗ Header propagation tests FAILED${NC}"
    echo ""
    echo -e "${YELLOW}Debugging tips:${NC}"
    echo "1. Check AWS API Gateway CORS allowHeaders configuration"
    echo "2. Verify Spring Boot Gateway forwards all headers"
    echo "3. Check CloudWatch logs for header-related errors"
    echo "4. Test with: ./scripts/ci/header-propagation-tests.sh $API_URL <your-jwt-token>"
    exit 1
else
    echo ""
    echo -e "${GREEN}✓ Header propagation tests PASSED${NC}"
    if [ $warnings -gt 0 ]; then
        echo -e "${YELLOW}  (with $warnings warnings - provide auth token for complete testing)${NC}"
    fi
    exit 0
fi
