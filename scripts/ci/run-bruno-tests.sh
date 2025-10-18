#!/bin/bash
# Run Bruno API tests in headless CI mode
# Validates API contracts match OpenAPI specifications
set -e

ENVIRONMENT=${1:-"staging"}
AUTH_TOKEN=${2:-""}

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}Bruno API Contract Tests${NC}"
echo -e "${BLUE}================================${NC}"
echo ""
echo "Environment: $ENVIRONMENT"

# Try to load token from local config if not provided
if [ -z "$AUTH_TOKEN" ]; then
    # Auto-refresh token if expired
    if [ -f "./scripts/auth/refresh-token.sh" ]; then
        ./scripts/auth/refresh-token.sh "$ENVIRONMENT" || true
    fi

    local_config=~/.batbern/${ENVIRONMENT}.json
    if [ -f "$local_config" ]; then
        echo -e "${BLUE}Loading auth token from local config: $local_config${NC}"
        # Use ACCESS token for HTTP API with JWT Authorizer (OAuth2 best practice)
        # HTTP API validates access tokens with client_id claim, not ID tokens
        AUTH_TOKEN=$(jq -r '.accessToken' "$local_config" 2>/dev/null)

        if [ "$AUTH_TOKEN" = "null" ] || [ -z "$AUTH_TOKEN" ]; then
            echo -e "${YELLOW}WARNING: Failed to load token from local config${NC}"
            echo "Run: ./scripts/auth/get-token.sh $ENVIRONMENT your-email your-password"
            AUTH_TOKEN=""
        else
            # Check if token is expired
            retrieved_at=$(jq -r '.retrievedAt' "$local_config" 2>/dev/null)
            expires_in=$(jq -r '.expiresIn' "$local_config" 2>/dev/null)
            echo -e "${GREEN}✓ Token loaded successfully${NC}"
            echo "Retrieved at: $retrieved_at"
            echo "Expires in: ~$(($expires_in / 60)) minutes from retrieval"
        fi
    else
        echo -e "${YELLOW}WARNING: No auth token found${NC}"
        echo "Usage: $0 <environment> [auth_token]"
        echo "Or run: ./scripts/auth/get-token.sh $ENVIRONMENT your-email your-password"
        echo "Tests requiring authentication may fail"
    fi
fi
echo ""

# Check if Bruno CLI is installed
if ! command -v bru &> /dev/null; then
    echo -e "${YELLOW}Installing Bruno CLI...${NC}"
    npm install -g @usebruno/cli || {
        echo -e "${RED}Failed to install Bruno CLI${NC}"
        echo "Install manually: npm install -g @usebruno/cli"
        exit 1
    }
fi

# Verify Bruno tests directory exists
if [ ! -d "bruno-tests" ]; then
    echo -e "${RED}ERROR: bruno-tests directory not found${NC}"
    echo "Expected path: $(pwd)/bruno-tests"
    exit 1
fi

passed=0
failed=0
skipped=0

# Test collection directories
collections=(
    #"events-api"
    "companies-api"
)

# Run tests for each collection
for collection in "${collections[@]}"; do
    collection_path="bruno-tests/$collection"

    if [ ! -d "$collection_path" ]; then
        echo -e "${YELLOW}⚠ Skipping:${NC} $collection (directory not found)"
        ((skipped++))
        continue
    fi

    echo -e "\n${BLUE}Running tests:${NC} $collection"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    # Set environment variable for Bruno
    export AUTH_TOKEN="$AUTH_TOKEN"

    # Use shared environments directory (relative to collection directory)
    env_file="../environments/${ENVIRONMENT}.bru"

    # Run Bruno tests (must run from within the collection directory)
    if (cd "$collection_path" && bru run --env-file "$env_file" --output results.json 2>&1); then
        echo -e "${GREEN}✓ PASS${NC}: $collection tests passed"
        ((passed++))

        # Display summary if results file exists
        if [ -f "$collection_path/results.json" ]; then
            total=$(jq -r '.summary.totalTests // 0' "$collection_path/results.json" 2>/dev/null || echo "0")
            passed_tests=$(jq -r '.summary.passedTests // 0' "$collection_path/results.json" 2>/dev/null || echo "0")
            failed_tests=$(jq -r '.summary.failedTests // 0' "$collection_path/results.json" 2>/dev/null || echo "0")

            echo "  Total: $total | Passed: $passed_tests | Failed: $failed_tests"

            # Show failed test details
            if [ "$failed_tests" -gt 0 ]; then
                echo -e "${RED}  Failed tests:${NC}"
                jq -r '.tests[] | select(.status == "failed") | "    - \(.name): \(.error)"' "$collection_path/results.json" 2>/dev/null || true
            fi

            rm -f "$collection_path/results.json"
        fi
    else
        echo -e "${RED}✗ FAIL${NC}: $collection tests failed"
        ((failed++))

        # Try to show error details
        if [ -f "$collection_path/results.json" ]; then
            echo -e "${RED}  Error details:${NC}"
            jq -r '.error // "Unknown error"' "$collection_path/results.json" 2>/dev/null || cat "$collection_path/results.json"
            rm -f "$collection_path/results.json"
        fi
    fi
done

# Summary
echo ""
echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}Bruno Test Summary${NC}"
echo -e "${BLUE}================================${NC}"
echo -e "${GREEN}Collections Passed: $passed${NC}"
echo -e "${RED}Collections Failed: $failed${NC}"
echo -e "${YELLOW}Collections Skipped: $skipped${NC}"

if [ $failed -gt 0 ]; then
    echo ""
    echo -e "${RED}✗ Bruno API tests FAILED${NC}"
    echo ""
    echo -e "${YELLOW}Debugging tips:${NC}"
    echo "1. Check API Gateway and services are deployed and healthy"
    echo "2. Verify auth token is valid and not expired"
    echo "3. Review individual test failures above"
    echo "4. Run locally: bru run bruno-tests/<collection> --env $ENVIRONMENT"
    exit 1
else
    echo ""
    echo -e "${GREEN}✓ Bruno API tests PASSED${NC}"
    if [ $skipped -gt 0 ]; then
        echo -e "${YELLOW}  ($skipped collection(s) skipped)${NC}"
    fi
    exit 0
fi
