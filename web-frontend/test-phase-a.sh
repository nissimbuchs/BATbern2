#!/bin/bash
# Helper script to run Phase A test with proper environment setup

set -e  # Exit on error

echo "🎬 Phase A Test Runner"
echo "====================="
echo ""

# Check if TEST_PASSWORD is set
if [ -z "$TEST_PASSWORD" ]; then
  echo "❌ Error: TEST_PASSWORD environment variable is not set"
  echo ""
  echo "Please set it before running this script:"
  echo "  export TEST_PASSWORD='your-password-here'"
  echo "  ./test-phase-a.sh"
  echo ""
  exit 1
fi

# Check if services are running
echo "📡 Checking if services are running..."
if ! curl -s http://localhost:8100 > /dev/null 2>&1; then
  echo "⚠️  Frontend not detected at http://localhost:8100"
  echo ""
  echo "Please start services first:"
  echo "  Terminal 1: make dev-native-up"
  echo "  Terminal 2: cd web-frontend && npm run dev"
  echo ""
  read -p "Continue anyway? (y/N) " -n 1 -r
  echo
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    exit 1
  fi
fi

echo "✅ Environment ready"
echo ""

# Run the test
echo "🧪 Running Phase A Test..."
echo ""

npx playwright test \
  workflows/documentation/complete-event-workflow.spec.ts \
  -g "Phase A" \
  --project=documentation-screenshots \
  "$@"

# Check results
if [ $? -eq 0 ]; then
  echo ""
  echo "✅ Phase A test passed!"
  echo ""
  echo "📸 Screenshots captured in:"
  echo "   docs/user-guide/assets/screenshots/workflow/phase-a-setup/"
  echo ""
  echo "📊 To view screenshots:"
  echo "   open docs/user-guide/assets/screenshots/workflow/phase-a-setup/"
  echo ""
else
  echo ""
  echo "❌ Phase A test failed"
  echo ""
  echo "🐛 Debugging tips:"
  echo "  1. Check error screenshot in phase-a-setup/ERROR-*.png"
  echo "  2. Run with --ui flag for interactive debugging:"
  echo "     ./test-phase-a.sh --ui"
  echo "  3. Check service logs:"
  echo "     tail -f /tmp/batbern-1-*.log"
  echo ""
fi
