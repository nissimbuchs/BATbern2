#!/bin/bash
#
# Generate E2E test tokens for speaker onboarding tests
#
# Usage:
#   ./scripts/e2e/generate-speaker-tokens.sh [--event-code EVENT_CODE]
#
# Prerequisites:
#   - Backend services must be running (make dev-native-up)
#   - Test seed data should be present (make dev-seed-data)
#
# Output:
#   Exports environment variables with tokens for E2E tests
#

set -e

# Default values
API_URL="${E2E_API_URL:-http://localhost:8002}"
EVENT_CODE="${1:-BAT-SEED-2026}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Generating E2E test tokens...${NC}"
echo "API URL: $API_URL"
echo "Event Code: $EVENT_CODE"
echo ""

# Call the E2E test token endpoint
RESPONSE=$(curl -s -X POST \
    "${API_URL}/api/v1/e2e-test/tokens/generate-e2e-set?eventCode=${EVENT_CODE}" \
    -H "Content-Type: application/json")

# Check if the request was successful
if echo "$RESPONSE" | grep -q '"tokens"'; then
    echo -e "${GREEN}✅ Tokens generated successfully!${NC}"
    echo ""

    # Extract and display tokens
    echo "Generated tokens:"
    echo "$RESPONSE" | jq -r '.tokens | to_entries[] | "  \(.key): \(.value)"'
    echo ""

    # Display export commands
    echo -e "${YELLOW}Copy these commands to set environment variables:${NC}"
    echo ""
    echo "$RESPONSE" | jq -r '.exportCommands'

    # Save to a file for easy sourcing
    EXPORT_FILE="/tmp/e2e-speaker-tokens.env"
    echo "$RESPONSE" | jq -r '.tokens | to_entries[] | "export \(.key)=\u0027\(.value)\u0027"' > "$EXPORT_FILE"
    echo ""
    echo -e "${GREEN}Tokens saved to: ${EXPORT_FILE}${NC}"
    echo "To use: source $EXPORT_FILE"
    echo ""

    # Show speaker info
    echo "Speaker details:"
    echo "$RESPONSE" | jq -r '
        if .onboardingSpeaker then "  Onboarding: \(.onboardingSpeaker.name) (\(.onboardingSpeaker.status))" else empty end,
        if .profileSpeaker then "  Profile: \(.profileSpeaker.name) (hasSession: \(.profileSpeaker.hasSession))" else empty end,
        if .contentSpeaker then "  Content: \(.contentSpeaker.name)" else empty end,
        if .noSessionSpeaker then "  No Session: \(.noSessionSpeaker.name)" else empty end
    '
else
    echo -e "${RED}❌ Failed to generate tokens${NC}"
    echo "Response: $RESPONSE"
    echo ""
    echo "Make sure:"
    echo "  1. Backend services are running (make dev-native-up)"
    echo "  2. Test data is seeded (make dev-seed-data)"
    echo "  3. The event code exists: $EVENT_CODE"
    exit 1
fi
