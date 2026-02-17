#!/bin/bash
# Epic 9 Migration Script — Migrate Epic 6 staging speakers to Cognito JWT auth
#
# Usage: ./epic9-migration.sh [--dry-run]
#
# Environment variables:
#   STAGING_API_URL      Base URL of the staging API (e.g. https://api-staging.batbern.ch)
#   STAGING_AUTH_TOKEN   Bearer token for organizer authentication
#
# If STAGING_API_URL or STAGING_AUTH_TOKEN are not set, the script will prompt for them.

set -e

# ── Colour helpers ────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Colour

# ── Argument parsing ──────────────────────────────────────────────────────────
DRY_RUN=false

usage() {
    echo "Usage: $0 [--dry-run]"
    echo ""
    echo "Options:"
    echo "  --dry-run   Validate all speakers without creating Cognito accounts or sending emails"
    echo ""
    echo "Environment variables:"
    echo "  STAGING_API_URL      Base URL of the staging API"
    echo "  STAGING_AUTH_TOKEN   Bearer token for organizer authentication"
    exit 1
}

for arg in "$@"; do
    case "$arg" in
        --dry-run)
            DRY_RUN=true
            ;;
        --help|-h)
            usage
            ;;
        *)
            echo -e "${RED}ERROR: Unknown argument: $arg${NC}"
            usage
            ;;
    esac
done

# ── Resolve environment variables ─────────────────────────────────────────────
if [ -z "$STAGING_API_URL" ]; then
    echo -e "${YELLOW}STAGING_API_URL not set.${NC}"
    read -rp "Enter staging API URL (e.g. https://api-staging.batbern.ch): " STAGING_API_URL
fi

if [ -z "$STAGING_AUTH_TOKEN" ]; then
    echo -e "${YELLOW}STAGING_AUTH_TOKEN not set.${NC}"
    read -rsp "Enter staging organizer auth token: " STAGING_AUTH_TOKEN
    echo ""
fi

if [ -z "$STAGING_API_URL" ] || [ -z "$STAGING_AUTH_TOKEN" ]; then
    echo -e "${RED}ERROR: STAGING_API_URL and STAGING_AUTH_TOKEN are required.${NC}"
    exit 1
fi

# ── Dependency check ──────────────────────────────────────────────────────────
if ! command -v jq &>/dev/null; then
    echo -e "${RED}ERROR: 'jq' is required but not installed.${NC}"
    echo "Install with: brew install jq (macOS) or apt-get install jq (Linux)"
    exit 1
fi

if ! command -v curl &>/dev/null; then
    echo -e "${RED}ERROR: 'curl' is required but not installed.${NC}"
    exit 1
fi

# ── Run migration ─────────────────────────────────────────────────────────────
ENDPOINT="${STAGING_API_URL}/api/v1/admin/migrations/epic9?dryRun=${DRY_RUN}"

echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}Epic 9 Migration — BATbern${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""
echo "API URL:  ${STAGING_API_URL}"
echo "Dry run:  ${DRY_RUN}"
echo ""

if [ "$DRY_RUN" = "true" ]; then
    echo -e "${YELLOW}⚠️  DRY-RUN MODE — no accounts will be created, no emails will be sent${NC}"
    echo ""
fi

echo -e "Calling ${ENDPOINT} ..."
echo ""

HTTP_RESPONSE=$(curl --silent --show-error --write-out "HTTPSTATUS:%{http_code}" \
    --request POST \
    --header "Authorization: Bearer ${STAGING_AUTH_TOKEN}" \
    --header "Content-Type: application/json" \
    "${ENDPOINT}")

HTTP_BODY=$(echo "$HTTP_RESPONSE" | sed -e 's/HTTPSTATUS:.*//g')
HTTP_STATUS=$(echo "$HTTP_RESPONSE" | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')

if [ "$HTTP_STATUS" -ne 200 ]; then
    echo -e "${RED}ERROR: Migration endpoint returned HTTP ${HTTP_STATUS}${NC}"
    echo ""
    echo "Response body:"
    echo "$HTTP_BODY" | jq . 2>/dev/null || echo "$HTTP_BODY"
    exit 1
fi

# ── Pretty-print report ───────────────────────────────────────────────────────
echo -e "${GREEN}✅ Migration complete (HTTP ${HTTP_STATUS})${NC}"
echo ""
echo -e "${BLUE}─── Summary ───────────────────────────────────${NC}"
echo "$HTTP_BODY" | jq '{
    total: .total,
    provisionedNew: .provisionedNew,
    extended: .extended,
    emailsSent: .emailsSent,
    errors: .errors
}'

echo ""
echo -e "${BLUE}─── Per-Speaker Results ────────────────────────${NC}"
echo "$HTTP_BODY" | jq '.results[] | {
    email: .email,
    name: .speakerName,
    outcome: .outcome,
    detail: .detail
}'

# ── Exit code ─────────────────────────────────────────────────────────────────
ERRORS=$(echo "$HTTP_BODY" | jq -r '.errors // 0')
if [ "$ERRORS" -gt 0 ]; then
    echo ""
    echo -e "${YELLOW}⚠️  Migration completed with ${ERRORS} error(s). Review the per-speaker results above.${NC}"
    exit 0
fi

echo ""
echo -e "${GREEN}✅ Migration completed successfully with no errors.${NC}"
exit 0
