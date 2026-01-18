#!/bin/bash
# =============================================================================
# Test Speaker Invitation Flow - Story 6.2
# =============================================================================
# This script automates testing of the speaker invitation response portal:
# 1. Creates a test speaker (if needed)
# 2. Sends an invitation to the speaker
# 3. Outputs the response portal URL for testing
#
# Usage: ./scripts/dev/test-speaker-invitation.sh [EVENT_CODE]
# Default EVENT_CODE: BATbern2026
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
API_URL="${API_URL:-http://localhost:8000/api/v1}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:8100}"
EVENT_CODE="${1:-BATbern2026}"
TOKEN_FILE="${HOME}/.batbern/token.txt"

echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║     Test Speaker Invitation Flow - Story 6.2              ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo

# Check for auth token
if [ ! -f "$TOKEN_FILE" ]; then
    echo -e "${YELLOW}No auth token found at $TOKEN_FILE${NC}"
    echo -e "${YELLOW}Please run: ./scripts/auth/get-token.sh staging your-email your-password${NC}"

    # Try alternative token location
    if [ -f "/tmp/batbern-token.txt" ]; then
        TOKEN_FILE="/tmp/batbern-token.txt"
        echo -e "${GREEN}Found token at $TOKEN_FILE${NC}"
    else
        echo -e "${RED}Cannot proceed without authentication.${NC}"
        exit 1
    fi
fi

AUTH_TOKEN=$(cat "$TOKEN_FILE")
AUTH_HEADER="Authorization: Bearer $AUTH_TOKEN"

echo -e "${BLUE}Step 1: Checking event exists...${NC}"
EVENT_CHECK=$(curl -s "${API_URL}/events/${EVENT_CODE}" | jq -r '.eventCode // .code // empty' 2>/dev/null)
if [ -z "$EVENT_CHECK" ]; then
    echo -e "${RED}Event ${EVENT_CODE} not found. Available events:${NC}"
    curl -s "${API_URL}/events" | jq '.data[] | .eventCode' 2>/dev/null || echo "Could not list events"
    exit 1
fi
echo -e "${GREEN}✓ Event ${EVENT_CODE} exists${NC}"
echo

# Generate a unique test speaker username
TEST_SPEAKER="test-speaker-$(date +%s)"
TEST_EMAIL="${TEST_SPEAKER}@example.com"

echo -e "${BLUE}Step 2: Creating test speaker...${NC}"
# First, check if speaker pool has speakers we can use
EXISTING_SPEAKERS=$(curl -s "${API_URL}/events/${EVENT_CODE}/speakers" \
    -H "$AUTH_HEADER" | jq -r '.content[0].username // empty' 2>/dev/null)

if [ -n "$EXISTING_SPEAKERS" ]; then
    TEST_SPEAKER="$EXISTING_SPEAKERS"
    echo -e "${GREEN}✓ Using existing speaker: ${TEST_SPEAKER}${NC}"
else
    # Create a new speaker in the pool
    CREATE_RESULT=$(curl -s -X POST "${API_URL}/events/${EVENT_CODE}/speakers" \
        -H "$AUTH_HEADER" \
        -H "Content-Type: application/json" \
        -d "{
            \"username\": \"${TEST_SPEAKER}\",
            \"firstName\": \"Test\",
            \"lastName\": \"Speaker\",
            \"email\": \"${TEST_EMAIL}\",
            \"company\": \"Test Company\",
            \"expertise\": [\"Software Architecture\", \"Cloud Computing\"]
        }" 2>/dev/null)

    if echo "$CREATE_RESULT" | jq -e '.username' > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Created test speaker: ${TEST_SPEAKER}${NC}"
    else
        # Try to use the username as-is if creation failed (might already exist)
        echo -e "${YELLOW}Could not create speaker, trying with existing...${NC}"
        # Get first available speaker from pool
        POOL_SPEAKER=$(curl -s "${API_URL}/speakers" \
            -H "$AUTH_HEADER" | jq -r '.[0].username // empty' 2>/dev/null)
        if [ -n "$POOL_SPEAKER" ]; then
            TEST_SPEAKER="$POOL_SPEAKER"
            echo -e "${GREEN}✓ Using speaker from pool: ${TEST_SPEAKER}${NC}"
        fi
    fi
fi
echo

echo -e "${BLUE}Step 3: Sending invitation to ${TEST_SPEAKER}...${NC}"
INVITATION_RESULT=$(curl -s -X POST "${API_URL}/events/${EVENT_CODE}/invitations" \
    -H "$AUTH_HEADER" \
    -H "Content-Type: application/json" \
    -d "{
        \"username\": \"${TEST_SPEAKER}\",
        \"personalMessage\": \"We are excited to invite you to speak at ${EVENT_CODE}! Your expertise in software architecture would be a perfect fit for our audience. We chose you because of your innovative approach to cloud-native development.\",
        \"expirationDays\": 14
    }" 2>/dev/null)

# Check for errors
if echo "$INVITATION_RESULT" | jq -e '.error' > /dev/null 2>&1; then
    ERROR_MSG=$(echo "$INVITATION_RESULT" | jq -r '.message')
    echo -e "${RED}Error sending invitation: ${ERROR_MSG}${NC}"

    # If duplicate, try to get existing invitation
    if [[ "$ERROR_MSG" == *"already"* ]] || [[ "$ERROR_MSG" == *"duplicate"* ]]; then
        echo -e "${YELLOW}Checking for existing invitation...${NC}"
        EXISTING=$(curl -s "${API_URL}/events/${EVENT_CODE}/invitations" \
            -H "$AUTH_HEADER" | jq -r ".content[] | select(.username == \"${TEST_SPEAKER}\") | .responseToken" 2>/dev/null | head -1)
        if [ -n "$EXISTING" ]; then
            RESPONSE_TOKEN="$EXISTING"
            echo -e "${GREEN}✓ Found existing invitation${NC}"
        else
            exit 1
        fi
    else
        exit 1
    fi
else
    RESPONSE_TOKEN=$(echo "$INVITATION_RESULT" | jq -r '.responseToken // empty')
    INVITATION_ID=$(echo "$INVITATION_RESULT" | jq -r '.id // empty')
    INVITATION_STATUS=$(echo "$INVITATION_RESULT" | jq -r '.invitationStatus // empty')

    if [ -z "$RESPONSE_TOKEN" ]; then
        echo -e "${RED}No response token in result:${NC}"
        echo "$INVITATION_RESULT" | jq .
        exit 1
    fi

    echo -e "${GREEN}✓ Invitation sent successfully!${NC}"
    echo -e "  Invitation ID: ${INVITATION_ID}"
    echo -e "  Status: ${INVITATION_STATUS}"
fi
echo

echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}Test the Speaker Response Portal:${NC}"
echo
echo -e "${YELLOW}Response Token:${NC}"
echo -e "  ${RESPONSE_TOKEN}"
echo
echo -e "${YELLOW}Response Portal URL:${NC}"
echo -e "  ${FRONTEND_URL}/respond/${RESPONSE_TOKEN}"
echo
echo -e "${YELLOW}API Endpoints:${NC}"
echo -e "  GET  ${API_URL}/invitations/respond/${RESPONSE_TOKEN}"
echo -e "  POST ${API_URL}/invitations/respond/${RESPONSE_TOKEN}"
echo
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo

# Optionally open in browser
if command -v open &> /dev/null; then
    echo -e "${YELLOW}Open in browser? [y/N]${NC}"
    read -r -n 1 OPEN_BROWSER
    echo
    if [[ "$OPEN_BROWSER" =~ ^[Yy]$ ]]; then
        open "${FRONTEND_URL}/respond/${RESPONSE_TOKEN}"
    fi
fi

echo -e "${GREEN}Done!${NC}"
