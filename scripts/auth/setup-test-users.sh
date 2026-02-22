#!/bin/bash
# Authenticate all test role users and store their tokens
# Usage: ./scripts/auth/setup-test-users.sh [environment]
#
# Local dev: reads credentials from .env.test.local in the project root
# CI:        reads from environment variables directly
#
# Required variable: ORGANIZER_EMAIL, ORGANIZER_PASSWORD
# Optional:          SPEAKER_EMAIL, SPEAKER_PASSWORD, PARTNER_EMAIL, PARTNER_PASSWORD

set -e

ENVIRONMENT=${1:-"staging"}

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}BATbern Multi-Role Token Setup${NC}"
echo -e "${BLUE}================================${NC}"
echo "Environment: $ENVIRONMENT"
echo ""

# Load credentials from .env.test.local if it exists (local dev only)
# Find the file relative to the project root (two directories up from scripts/auth/)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="$PROJECT_ROOT/.env.test.local"

if [ -f "$ENV_FILE" ]; then
    echo -e "${BLUE}Loading credentials from .env.test.local${NC}"
    # Safe parse: only export whitelisted variable names, never eval arbitrary content
    while IFS='=' read -r key value; do
        # Skip comments and blank lines
        [[ "$key" =~ ^[[:space:]]*# ]] && continue
        [[ -z "$key" ]] && continue
        case "$key" in
            ORGANIZER_EMAIL|ORGANIZER_PASSWORD|\
            SPEAKER_EMAIL|SPEAKER_PASSWORD|\
            PARTNER_EMAIL|PARTNER_PASSWORD)
                # Strip surrounding quotes if present
                value="${value%\"}"
                value="${value#\"}"
                value="${value%\'}"
                value="${value#\'}"
                # Only set if not already set by CI environment
                if [ -z "${!key}" ]; then
                    export "$key"="$value"
                fi
                ;;
        esac
    done < "$ENV_FILE"
else
    echo -e "${YELLOW}No .env.test.local found — using environment variables${NC}"
    echo "(Copy .env.test.local.example to .env.test.local for local dev)"
fi

echo ""

# Validate required credentials
if [ -z "$ORGANIZER_EMAIL" ] || [ -z "$ORGANIZER_PASSWORD" ]; then
    echo -e "${RED}ERROR: ORGANIZER_EMAIL and ORGANIZER_PASSWORD are required${NC}"
    echo ""
    echo "Options:"
    echo "  1. Create .env.test.local (copy from .env.test.local.example)"
    echo "  2. Set env vars: ORGANIZER_EMAIL=... ORGANIZER_PASSWORD=... $0 $ENVIRONMENT"
    exit 1
fi

# Helper: authenticate one role, skip gracefully if credentials missing
setup_role() {
    local role="$1"
    local email="$2"
    local password="$3"

    if [ -z "$email" ] || [ -z "$password" ]; then
        echo -e "${YELLOW}⚠ Skipping $role: credentials not configured${NC}"
        return 0
    fi

    echo -e "${BLUE}Setting up $role token...${NC}"
    if "$SCRIPT_DIR/get-token.sh" "$ENVIRONMENT" "$email" "$password" "$role"; then
        echo -e "${GREEN}✓ $role token ready${NC}"
    else
        echo -e "${RED}✗ Failed to get $role token${NC}"
        return 1
    fi
    echo ""
}

setup_role organizer "$ORGANIZER_EMAIL" "$ORGANIZER_PASSWORD"
setup_role speaker   "$SPEAKER_EMAIL"   "$SPEAKER_PASSWORD"
setup_role partner   "$PARTNER_EMAIL"   "$PARTNER_PASSWORD"

echo -e "${GREEN}================================${NC}"
echo -e "${GREEN}Token Setup Complete${NC}"
echo -e "${GREEN}================================${NC}"
for role in organizer speaker partner; do
    role_file=~/.batbern/${ENVIRONMENT}-${role}.json
    if [ -f "$role_file" ]; then
        echo -e "${GREEN}✓${NC} $role → $role_file"
    else
        echo -e "${YELLOW}✗${NC} $role → not configured"
    fi
done
echo ""
echo "Backward-compat file: ~/.batbern/${ENVIRONMENT}.json (organizer token)"
