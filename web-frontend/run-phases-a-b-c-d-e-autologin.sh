#!/bin/bash
# Phase A + B + C + D + E test runner with autologin support
# Autologin handles authentication - no password needed!

set -e

echo "🎬 Phase A + B + C + D + E Test Runner (Autologin)"
echo "===================================================="
echo ""

# Parse arguments
UI_MODE=false
while [[ $# -gt 0 ]]; do
  case $1 in
    --ui)
      UI_MODE=true
      shift
      ;;
    *)
      break
      ;;
  esac
done

# Set environment variables
export ENVIRONMENT="development"
export TEST_ENV="development"
export E2E_BASE_URL="http://localhost:8100"
export E2E_API_URL="http://localhost:8000"
export E2E_AWS_REGION="eu-central-1"

echo "Test Configuration:"
echo "  Environment: $TEST_ENV"
echo "  Base URL:    $E2E_BASE_URL"
echo "  API URL:     $E2E_API_URL"
echo "  Region:      $E2E_AWS_REGION"
if [ "$UI_MODE" = true ]; then
  echo "  Mode:        UI (headed browser)"
else
  echo "  Mode:        Headless"
fi
echo ""

# Load authentication token (if available)
local_config=~/.batbern/${ENVIRONMENT}.json
if [ -f "$local_config" ]; then
    echo "Loading auth token from: $local_config"
    AUTH_TOKEN=$(jq -r '.idToken' "$local_config" 2>/dev/null)

    if [ "$AUTH_TOKEN" != "null" ] && [ -n "$AUTH_TOKEN" ]; then
        echo "✓ Token loaded successfully"
        export AUTH_TOKEN="$AUTH_TOKEN"
        # Debug: Show token expiration
        TOKEN_EXP=$(echo "$AUTH_TOKEN" | cut -d'.' -f2 | base64 -d 2>/dev/null | jq -r '.exp' 2>/dev/null || echo "unknown")
        if [ "$TOKEN_EXP" != "unknown" ]; then
            EXP_DATE=$(date -r "$TOKEN_EXP" "+%Y-%m-%d %H:%M:%S" 2>/dev/null || echo "unknown")
            echo "   Token expires: $EXP_DATE"
        fi
    else
        echo "⚠️  No valid token found (autologin should still work)"
    fi
else
    echo "⚠️  No auth config found at $local_config"
    echo "   Autologin should handle authentication"
fi
echo ""

# Check if services are running
echo "📡 Checking services..."
if ! curl -s http://localhost:8100 > /dev/null 2>&1; then
  echo "❌ Frontend not running at http://localhost:8100"
  echo ""
  echo "Please start services:"
  echo "  Terminal 1: make dev-native-up"
  echo "  Terminal 2: cd web-frontend && npm run dev"
  exit 1
fi

echo "✅ Frontend is running"
echo ""

# Run Phase A + B + C + D + E tests
echo "🧪 Running Phase A + B + C + D + E tests (serial execution)..."
if [ "$UI_MODE" = true ]; then
  echo "   👀 Opening browser in UI mode - you can watch the test execute"
else
  echo "   ⚡ Running in headless mode for speed"
fi
echo ""

cd /Users/nissim/dev/bat/BATbern-feature/web-frontend

# Build playwright command
# Note: Using -g to match "Phase A", "Phase B", "Phase B.5", "Phase C", "Phase D", and "Phase E"
PLAYWRIGHT_CMD="npx playwright test \
  workflows/documentation/complete-event-workflow.spec.ts \
  -g \"Phase (A|B|B\.5|C|D|E)\" \
  --project=documentation-screenshots"

if [ "$UI_MODE" = true ]; then
  PLAYWRIGHT_CMD="$PLAYWRIGHT_CMD --headed"
fi

# Add any remaining arguments
PLAYWRIGHT_CMD="$PLAYWRIGHT_CMD $@"

# Run test and capture output
eval "$PLAYWRIGHT_CMD" 2>&1 | tee /tmp/phase-a-b-c-d-e-test-run.log

TEST_RESULT=${PIPESTATUS[0]}

echo ""
echo "════════════════════════════════════════"

if [ $TEST_RESULT -eq 0 ]; then
  echo "✅ Phase A + B + C + D + E tests PASSED!"
  echo ""
  echo "📸 Screenshots captured in:"
  echo "   Phase A: docs/user-guide/assets/screenshots/workflow/phase-a-setup/"
  echo "   Phase B: docs/user-guide/assets/screenshots/workflow/phase-b-outreach/"
  echo "   Phase C: docs/user-guide/assets/screenshots/workflow/phase-c-quality/"
  echo "   Phase D: docs/user-guide/assets/screenshots/workflow/phase-d-publishing/"
  echo "   Phase E: docs/user-guide/assets/screenshots/workflow/phase-e-archival/"
  echo ""
  echo "To view screenshots:"
  echo "   open ../docs/user-guide/assets/screenshots/workflow/"
else
  echo "❌ Phase A + B + C + D + E tests FAILED"
  echo ""
  echo "Full output saved to: /tmp/phase-a-b-c-d-e-test-run.log"
  echo ""
  echo "Check error screenshot:"
  echo "   ls -lrt test-results/**/test-failed-*.png | tail -1"
  echo ""
  echo "Quick error check:"
  echo "   grep -A 5 'Error:' /tmp/phase-a-b-c-d-e-test-run.log | tail -20"
fi

echo "════════════════════════════════════════"
echo ""

exit $TEST_RESULT
