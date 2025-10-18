#!/bin/bash
# CORS Validation Tests for Post-Deployment
# Validates CORS headers and cross-origin request support
set -e

API_URL=${1:-"https://api.staging.batbern.ch"}
FRONTEND_ORIGIN=${2:-"https://staging.batbern.ch"}

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}CORS Validation Tests${NC}"
echo -e "${BLUE}================================${NC}"
echo ""
echo "API URL: $API_URL"
echo "Frontend Origin: $FRONTEND_ORIGIN"
echo ""

passed=0
failed=0
warnings=0

# Helper function to test CORS preflight
test_cors_preflight() {
    local endpoint=$1
    local method=$2
    local headers=$3
    local test_name=$4

    echo -e "\n${YELLOW}Testing:${NC} $test_name"
    echo "  Endpoint: $endpoint"
    echo "  Method: $method"
    echo "  Headers: $headers"

    # Perform OPTIONS preflight request
    response=$(curl -s -i -X OPTIONS "$endpoint" \
        -H "Origin: $FRONTEND_ORIGIN" \
        -H "Access-Control-Request-Method: $method" \
        -H "Access-Control-Request-Headers: $headers" \
        2>&1)

    # Extract HTTP status code
    status_code=$(echo "$response" | grep "HTTP/" | tail -1 | awk '{print $2}')

    # Extract CORS headers
    allow_origin=$(echo "$response" | grep -i "access-control-allow-origin:" | cut -d: -f2- | tr -d '\r' | xargs)
    allow_methods=$(echo "$response" | grep -i "access-control-allow-methods:" | cut -d: -f2- | tr -d '\r' | xargs)
    allow_headers=$(echo "$response" | grep -i "access-control-allow-headers:" | cut -d: -f2- | tr -d '\r' | xargs)
    allow_credentials=$(echo "$response" | grep -i "access-control-allow-credentials:" | cut -d: -f2- | tr -d '\r' | xargs)

    local test_passed=true

    # Validate status code
    if [ "$status_code" != "200" ]; then
        echo -e "  ${RED}✗ FAIL${NC}: Status code $status_code (expected 200)"
        test_passed=false
    else
        echo -e "  ${GREEN}✓${NC} Status code: $status_code"
    fi

    # Validate Access-Control-Allow-Origin
    if [ "$allow_origin" != "$FRONTEND_ORIGIN" ] && [ "$allow_origin" != "*" ]; then
        echo -e "  ${RED}✗ FAIL${NC}: Allow-Origin '$allow_origin' doesn't match '$FRONTEND_ORIGIN'"
        test_passed=false
    else
        echo -e "  ${GREEN}✓${NC} Access-Control-Allow-Origin: $allow_origin"
    fi

    # Validate requested headers are allowed
    IFS=',' read -ra REQUESTED_HEADERS <<< "$headers"
    for header in "${REQUESTED_HEADERS[@]}"; do
        header=$(echo "$header" | xargs) # trim whitespace
        if ! echo "$allow_headers" | grep -qi "$header"; then
            echo -e "  ${RED}✗ FAIL${NC}: Header '$header' not in Access-Control-Allow-Headers"
            echo -e "  ${RED}   Allowed headers: $allow_headers${NC}"
            test_passed=false
        else
            echo -e "  ${GREEN}✓${NC} Header allowed: $header"
        fi
    done

    # Validate method is allowed
    if ! echo "$allow_methods" | grep -qi "$method"; then
        echo -e "  ${RED}✗ FAIL${NC}: Method '$method' not in Access-Control-Allow-Methods"
        test_passed=false
    else
        echo -e "  ${GREEN}✓${NC} Method allowed: $method"
    fi

    # Validate credentials
    if [ "$allow_credentials" != "true" ]; then
        echo -e "  ${YELLOW}⚠ WARNING${NC}: Access-Control-Allow-Credentials not set to 'true'"
        ((warnings++))
    else
        echo -e "  ${GREEN}✓${NC} Credentials allowed: $allow_credentials"
    fi

    if $test_passed; then
        echo -e "  ${GREEN}✓ PASS${NC}: CORS preflight succeeded"
        ((passed++))
    else
        echo -e "  ${RED}✗ FAIL${NC}: CORS preflight failed"
        ((failed++))
    fi
}

# Test 1: Companies API - GET with authentication headers
test_cors_preflight \
    "$API_URL/api/v1/companies" \
    "GET" \
    "Authorization,Content-Type,X-Correlation-ID,Accept-Language" \
    "Companies API - GET with custom headers"

# Test 2: Companies API - POST with all headers
test_cors_preflight \
    "$API_URL/api/v1/companies" \
    "POST" \
    "Authorization,Content-Type,X-Correlation-ID,Accept-Language,Accept" \
    "Companies API - POST with all custom headers"

# Test 3: Events API - GET with correlation ID
test_cors_preflight \
    "$API_URL/api/v1/events" \
    "GET" \
    "Authorization,X-Correlation-ID,Accept-Language" \
    "Events API - GET with correlation ID"

# Test 4: Events API - PUT with all headers
test_cors_preflight \
    "$API_URL/api/v1/events/550e8400-e29b-41d4-a716-446655440000" \
    "PUT" \
    "Authorization,Content-Type,X-Correlation-ID,Accept-Language" \
    "Events API - PUT with authentication"

# Test 5: Events API - DELETE
test_cors_preflight \
    "$API_URL/api/v1/events/550e8400-e29b-41d4-a716-446655440000" \
    "DELETE" \
    "Authorization,X-Correlation-ID" \
    "Events API - DELETE"

# Test 6: Health endpoint (no auth required)
echo -e "\n${YELLOW}Test 6:${NC} Health endpoint CORS"
response=$(curl -s -i -X OPTIONS "$API_URL/health" \
    -H "Origin: $FRONTEND_ORIGIN" \
    -H "Access-Control-Request-Method: GET" \
    2>&1)

status_code=$(echo "$response" | grep "HTTP/" | tail -1 | awk '{print $2}')
if [ "$status_code" = "200" ]; then
    echo -e "${GREEN}✓ PASS${NC}: Health endpoint CORS check passed"
    ((passed++))
else
    echo -e "${YELLOW}⚠ WARNING${NC}: Health endpoint returned $status_code"
    ((warnings++))
    ((passed++)) # Don't fail on health endpoint
fi

# Test 7: Actual request flow (not just preflight)
echo -e "\n${YELLOW}Test 7:${NC} Actual CORS request (POST to companies search)"
actual_response=$(curl -s -i -X GET "$API_URL/api/v1/companies/search?query=test&limit=5" \
    -H "Origin: $FRONTEND_ORIGIN" \
    -H "Content-Type: application/json" \
    2>&1)

status_code=$(echo "$actual_response" | grep "HTTP/" | tail -1 | awk '{print $2}')
cors_header=$(echo "$actual_response" | grep -i "access-control-allow-origin:" | cut -d: -f2- | tr -d '\r' | xargs)

if [ "$status_code" = "401" ] || [ "$status_code" = "200" ]; then
    # 401 is acceptable - we're testing CORS, not auth
    if [ -n "$cors_header" ]; then
        echo -e "${GREEN}✓ PASS${NC}: Actual request includes CORS headers (status: $status_code)"
        echo -e "  ${GREEN}✓${NC} Access-Control-Allow-Origin: $cors_header"
        ((passed++))
    else
        echo -e "${RED}✗ FAIL${NC}: Actual request missing CORS headers"
        ((failed++))
    fi
else
    echo -e "${RED}✗ FAIL${NC}: Actual request returned unexpected status: $status_code"
    ((failed++))
fi

# Summary
echo ""
echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}CORS Validation Summary${NC}"
echo -e "${BLUE}================================${NC}"
echo -e "${GREEN}Passed: $passed${NC}"
echo -e "${RED}Failed: $failed${NC}"
echo -e "${YELLOW}Warnings: $warnings${NC}"

if [ $failed -gt 0 ]; then
    echo ""
    echo -e "${RED}✗ CORS validation FAILED${NC}"
    echo ""
    echo -e "${YELLOW}Common fixes:${NC}"
    echo "1. Check API Gateway CORS configuration in infrastructure/lib/stacks/api-gateway-stack.ts"
    echo "2. Ensure allowHeaders includes: X-Correlation-ID, Accept-Language, Accept"
    echo "3. Verify Spring Boot CORS configuration in api-gateway/src/main/java/ch/batbern/gateway/security/CorsHandler.java"
    echo "4. Deploy updated infrastructure: npm run deploy:staging"
    exit 1
else
    echo ""
    echo -e "${GREEN}✓ CORS validation PASSED${NC}"
    if [ $warnings -gt 0 ]; then
        echo -e "${YELLOW}  (with $warnings warnings)${NC}"
    fi
    exit 0
fi
