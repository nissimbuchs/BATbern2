#!/bin/bash
# Complete Phase A test runner - handles auth token + test execution

set -e

echo "🎬 Phase A Complete Test Runner"
echo "================================"
echo ""

# Prompt for password securely
echo "Enter password for nissim@buchs.be:"
read -s TEST_PASSWORD
echo ""

if [ -z "$TEST_PASSWORD" ]; then
  echo "❌ Error: Password cannot be empty"
  exit 1
fi

echo "✅ Password set"
echo ""

# Export password for the test
export TEST_PASSWORD

export ENVIRONMENT="development"
export TEST_ENV="development"
export E2E_BASE_URL="http://localhost:8100"
export E2E_API_URL="http://localhost:8000"
export E2E_AWS_REGION="eu-central-1"

echo -e "${BLUE}Test Configuration:${NC}"
echo "  Environment: $TEST_ENV"
echo "  Base URL:    $E2E_BASE_URL"
echo "  API URL:     $E2E_API_URL"
echo "  Region:      $E2E_AWS_REGION"
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
echo ""

# Step 3: Run Phase A test
echo "🧪 Step 3: Running Phase A test..."
echo ""

cd web-frontend

npx playwright test \
  workflows/documentation/complete-event-workflow.spec.ts \
  -g "Phase A" \
  --project=documentation-screenshots \
  "$@"

TEST_RESULT=$?

echo ""
echo "════════════════════════════════════════"

if [ $TEST_RESULT -eq 0 ]; then
  echo "✅ Phase A test PASSED!"
  echo ""
  echo "📸 Screenshots captured in:"
  echo "   docs/user-guide/assets/screenshots/workflow/phase-a-setup/"
  echo ""
  echo "To view screenshots:"
  echo "   open ../docs/user-guide/assets/screenshots/workflow/phase-a-setup/"
else
  echo "❌ Phase A test FAILED"
  echo ""
  echo "Check error screenshot:"
  echo "   open ../docs/user-guide/assets/screenshots/workflow/phase-a-setup/ERROR-*.png"
fi

echo "════════════════════════════════════════"
echo ""

exit $TEST_RESULT
