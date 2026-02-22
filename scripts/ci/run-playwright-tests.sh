#!/bin/bash
# Run Playwright E2E tests with automatic token loading
# Usage: ./scripts/ci/run-playwright-tests.sh [environment]

set -e

ENVIRONMENT=${1:-"staging"}

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}Playwright E2E Tests${NC}"
echo -e "${BLUE}================================${NC}"
echo ""
echo "Environment: $ENVIRONMENT"
echo ""

# Auto-refresh token if expired
if [ -f "./scripts/auth/refresh-token.sh" ]; then
    ./scripts/auth/refresh-token.sh "$ENVIRONMENT" || true
fi

# Load authentication token
local_config=~/.batbern/${ENVIRONMENT}.json
if [ -f "$local_config" ]; then
    echo -e "${BLUE}Loading auth token from: $local_config${NC}"
    AUTH_TOKEN=$(jq -r '.idToken' "$local_config" 2>/dev/null)

    if [ "$AUTH_TOKEN" = "null" ] || [ -z "$AUTH_TOKEN" ]; then
        echo -e "${YELLOW}WARNING: Failed to load token${NC}"
        echo "Run: ./scripts/auth/get-token.sh $ENVIRONMENT your-email your-password"
        echo "Tests requiring authentication will be skipped"
    else
        retrieved_at=$(jq -r '.retrievedAt' "$local_config" 2>/dev/null)
        expires_in=$(jq -r '.expiresIn' "$local_config" 2>/dev/null)
        echo -e "${GREEN}✓ Token loaded successfully${NC}"
        echo "Retrieved at: $retrieved_at"
        echo "Expires in: ~$(($expires_in / 60)) minutes from retrieval"
        export AUTH_TOKEN="$AUTH_TOKEN"
    fi
else
    echo -e "${YELLOW}WARNING: No auth token found${NC}"
    echo "Run: ./scripts/auth/get-token.sh $ENVIRONMENT your-email your-password"
    echo "Tests requiring authentication will be skipped"
fi

# Load per-role tokens (for Epic 8+ multi-role testing)
load_role_token() {
    local role="$1"
    local role_config=~/.batbern/${ENVIRONMENT}-${role}.json
    if [ -f "$role_config" ]; then
        ./scripts/auth/refresh-token.sh "$ENVIRONMENT" "$role" 2>/dev/null || true
        local token
        token=$(jq -r '.idToken' "$role_config" 2>/dev/null)
        if [ "$token" != "null" ] && [ -n "$token" ]; then
            echo "$token"
            return 0
        fi
    fi
    echo ""
}

ORGANIZER_AUTH_TOKEN=$(load_role_token organizer)
SPEAKER_AUTH_TOKEN=$(load_role_token speaker)
PARTNER_AUTH_TOKEN=$(load_role_token partner)

[ -z "$ORGANIZER_AUTH_TOKEN" ] && ORGANIZER_AUTH_TOKEN="$AUTH_TOKEN"

export ORGANIZER_AUTH_TOKEN SPEAKER_AUTH_TOKEN PARTNER_AUTH_TOKEN

echo -e "${BLUE}Auth tokens available:${NC}"
echo "  ORGANIZER: $([ -n "$ORGANIZER_AUTH_TOKEN" ] && echo 'yes' || echo 'no')"
echo "  SPEAKER:   $([ -n "$SPEAKER_AUTH_TOKEN" ] && echo 'yes' || echo 'no')"
echo "  PARTNER:   $([ -n "$PARTNER_AUTH_TOKEN" ] && echo 'yes' || echo 'no')"
echo ""

# Set environment-specific URLs
if [ "$ENVIRONMENT" = "staging" ]; then
    export TEST_ENV="staging"
    export E2E_BASE_URL="https://staging.batbern.ch"
    export E2E_API_URL="https://api.staging.batbern.ch"
    export E2E_AWS_REGION="eu-central-1"
elif [ "$ENVIRONMENT" = "production" ]; then
    export TEST_ENV="production"
    export E2E_BASE_URL="https://www.batbern.ch"
    export E2E_API_URL="https://api.batbern.ch"
    export E2E_AWS_REGION="eu-central-1"
else
    export TEST_ENV="development"
    export E2E_BASE_URL="http://localhost:8100"
    export E2E_API_URL="http://localhost:8000"
    export E2E_AWS_REGION="eu-central-1"
fi

echo -e "${BLUE}Test Configuration:${NC}"
echo "  Environment: $TEST_ENV"
echo "  Base URL:    $E2E_BASE_URL"
echo "  API URL:     $E2E_API_URL"
echo "  Region:      $E2E_AWS_REGION"
echo ""

# Check if we're in the correct directory
if [ ! -d "web-frontend" ]; then
    echo -e "${RED}ERROR: Must be run from project root${NC}"
    echo "Current directory: $(pwd)"
    exit 1
fi

cd web-frontend

# Check if Playwright is configured
if [ ! -f "playwright.config.ts" ] && [ ! -f "playwright.config.js" ]; then
    echo -e "${RED}ERROR: Playwright not configured${NC}"
    echo "Expected: web-frontend/playwright.config.ts or playwright.config.js"
    exit 1
fi

# Install Playwright if needed
if ! npm list @playwright/test >/dev/null 2>&1; then
    echo -e "${YELLOW}Installing Playwright...${NC}"
    npm install -D @playwright/test
fi

# Install browsers if needed
echo -e "${BLUE}Ensuring Playwright browsers are installed...${NC}"
npx playwright install --with-deps chromium

echo ""
echo -e "${BLUE}Running Playwright tests...${NC}"
echo -e "${BLUE}================================${NC}"
echo ""

# Build project list: always run chromium (organizer), add role projects when tokens available
PLAYWRIGHT_PROJECTS="--project=chromium"
[ -n "$SPEAKER_AUTH_TOKEN" ] && PLAYWRIGHT_PROJECTS="$PLAYWRIGHT_PROJECTS --project=speaker"
[ -n "$PARTNER_AUTH_TOKEN" ] && PLAYWRIGHT_PROJECTS="$PLAYWRIGHT_PROJECTS --project=partner"

echo -e "${BLUE}Running projects: $PLAYWRIGHT_PROJECTS${NC}"
echo ""

# Run tests
# shellcheck disable=SC2086
if npx playwright test $PLAYWRIGHT_PROJECTS; then
    echo ""
    echo -e "${GREEN}✅ Playwright tests PASSED${NC}"
    exit 0
else
    echo ""
    echo -e "${RED}✗ Playwright tests FAILED${NC}"
    echo ""
    echo -e "${YELLOW}Debugging tips:${NC}"
    echo "1. Check service health and deployment status"
    echo "2. Verify auth token is not expired"
    echo "3. Review test output above for specific failures"
    echo "4. View HTML report: npx playwright show-report"
    exit 1
fi
