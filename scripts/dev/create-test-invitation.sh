#!/bin/bash
# =============================================================================
# Create Test Invitation (Direct DB) - Story 6.2
# =============================================================================
# Creates a test invitation directly in the database for testing the
# speaker response portal without requiring authentication.
#
# Usage: ./scripts/dev/create-test-invitation.sh
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
DOCKER_CONTAINER="batbern-dev-postgres"
DB_NAME="batbern_development"
DB_USER="postgres"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:8100}"
API_URL="${API_URL:-http://localhost:8000/api/v1}"

# Generate unique test data
TEST_TOKEN=$(openssl rand -hex 32)
TEST_USERNAME="test-speaker-$(date +%s)"
TEST_EVENT="BATbern2026"
EXPIRES_AT=$(date -v+14d -u +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -d "+14 days" -u +"%Y-%m-%dT%H:%M:%SZ")

echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   Create Test Invitation (Direct DB) - Story 6.2          ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo

# Function to run SQL via docker
run_sql() {
    docker exec -i "$DOCKER_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -A "$@"
}

echo -e "${BLUE}Step 1: Checking database connection...${NC}"
if ! docker exec "$DOCKER_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -c "SELECT 1" > /dev/null 2>&1; then
    echo -e "${RED}Cannot connect to database. Make sure PostgreSQL is running:${NC}"
    echo -e "  docker compose -f docker-compose-dev.yml up -d"
    exit 1
fi
echo -e "${GREEN}✓ Database connection OK${NC}"
echo

echo -e "${BLUE}Step 2: Creating test speaker in speaker pool...${NC}"
docker exec -i "$DOCKER_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" << EOF > /dev/null 2>&1 || true
INSERT INTO speakers (id, username, first_name, last_name, email, company, bio, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    '${TEST_USERNAME}',
    'Test',
    'Speaker',
    '${TEST_USERNAME}@example.com',
    'Test Company AG',
    'A test speaker for invitation flow testing.',
    NOW(),
    NOW()
)
ON CONFLICT (username) DO NOTHING;
EOF
echo -e "${GREEN}✓ Test speaker created: ${TEST_USERNAME}${NC}"
echo

echo -e "${BLUE}Step 3: Creating test invitation...${NC}"
RESULT=$(docker exec -i "$DOCKER_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" << EOF 2>&1
INSERT INTO speaker_invitations (
    id,
    username,
    event_code,
    response_token,
    invitation_status,
    expires_at,
    created_at,
    updated_at,
    created_by,
    reminder_count,
    personal_message
)
VALUES (
    gen_random_uuid(),
    '${TEST_USERNAME}',
    '${TEST_EVENT}',
    '${TEST_TOKEN}',
    'sent',
    '${EXPIRES_AT}'::timestamp,
    NOW(),
    NOW(),
    'test-organizer',
    0,
    'We are thrilled to invite you to speak at BATbern 2026! Your expertise in software architecture and cloud-native development makes you an ideal speaker for our community. We chose you because of your innovative contributions to the tech community and your ability to explain complex topics in an engaging way.'
);
EOF
)

if [[ "$RESULT" == *"INSERT"* ]]; then
    echo -e "${GREEN}✓ Test invitation created${NC}"
else
    echo -e "${YELLOW}Note: ${RESULT}${NC}"
fi
echo

echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}                    TEST INVITATION READY                      ${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo
echo -e "${CYAN}Speaker:${NC} ${TEST_USERNAME}"
echo -e "${CYAN}Event:${NC} ${TEST_EVENT}"
echo -e "${CYAN}Token:${NC} ${TEST_TOKEN}"
echo -e "${CYAN}Expires:${NC} ${EXPIRES_AT}"
echo
echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}RESPONSE PORTAL URL:${NC}"
echo
echo -e "  ${CYAN}${FRONTEND_URL}/respond/${TEST_TOKEN}${NC}"
echo
echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo
echo -e "${BLUE}API Test Commands:${NC}"
echo -e "  # Get invitation details:"
echo -e "  curl -s ${API_URL}/invitations/respond/${TEST_TOKEN} | jq ."
echo
echo -e "  # Accept with preferences:"
echo -e "  curl -s -X POST ${API_URL}/invitations/respond/${TEST_TOKEN} \\"
echo -e "    -H 'Content-Type: application/json' \\"
echo -e "    -d '{\"responseType\": \"ACCEPTED\", \"preferences\": {\"preferredTimeSlot\": \"MORNING\"}}' | jq ."
echo
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo

# Ask to open in browser
if command -v open &> /dev/null; then
    echo -e -n "${YELLOW}Open in browser? [Y/n] ${NC}"
    read -r OPEN_BROWSER
    if [[ ! "$OPEN_BROWSER" =~ ^[Nn]$ ]]; then
        open "${FRONTEND_URL}/respond/${TEST_TOKEN}"
        echo -e "${GREEN}✓ Opened in browser${NC}"
    fi
fi

echo
echo -e "${GREEN}Done!${NC}"
