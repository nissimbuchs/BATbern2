#!/bin/bash
# API Contract Validation Script
# Ensures frontend API calls match OpenAPI spec endpoints
#
# Usage: ./scripts/ci/validate-api-contracts.sh
#
# Exit codes:
#   0 - All contracts valid
#   1 - Contract violations found

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FRONTEND_DIR="${PROJECT_ROOT}/web-frontend"
DOCS_DIR="${PROJECT_ROOT}/docs/api"

echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║     BATbern API Contract Validation                       ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Check prerequisites
echo -e "${BLUE}→ Checking prerequisites...${NC}"
if ! command -v jq &> /dev/null; then
    echo -e "${RED}  ✗ jq is required but not installed${NC}"
    echo "    Install: brew install jq (macOS) or apt-get install jq (Linux)"
    exit 1
fi
echo -e "${GREEN}  ✓ jq installed${NC}"

if ! command -v yq &> /dev/null; then
    echo -e "${YELLOW}  ⚠ yq not found, installing via npm...${NC}"
    npm install -g yq
fi
echo -e "${GREEN}  ✓ yq installed${NC}"
echo ""

# Function to extract API endpoints from OpenAPI spec
extract_endpoints() {
    local spec_file=$1
    local service_name=$2

    echo -e "${BLUE}→ Extracting endpoints from ${service_name}...${NC}"

    # Extract paths and methods from OpenAPI spec
    yq eval '.paths | to_entries | .[] | .key as $path | .value | to_entries | .[] | "\($path) \(.key | upcase)"' "${spec_file}" 2>/dev/null || {
        echo -e "${RED}  ✗ Failed to parse OpenAPI spec: ${spec_file}${NC}"
        return 1
    }
}

# Function to find API calls in frontend code
find_api_calls() {
    local api_service_dir=$1

    echo -e "${BLUE}→ Scanning frontend API service files...${NC}" >&2

    # Find all axios/fetch calls in API service files
    # Look for patterns like: axios.get('/users'), apiClient.post('/companies'), etc.
    grep -rn -E "(axios|apiClient|fetch)\.(get|post|put|patch|delete)\s*\(" "${api_service_dir}" 2>/dev/null | \
        grep -v "test\|spec\|mock" | \
        sed -E "s/.*\.(get|post|put|patch|delete)\s*\(\s*['\`\"]([^'\`\"]+)['\`\"].*/\2 \1/g" | \
        awk '{print toupper($2) " " $1}' | \
        sort -u
}

# Main validation logic
validate_service() {
    local spec_file=$1
    local service_name=$2
    local api_dir=$3

    echo ""
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║  Validating ${service_name} API Contract${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""

    # Extract endpoints from OpenAPI spec
    local spec_endpoints=$(mktemp)
    extract_endpoints "${spec_file}" "${service_name}" | sort -u > "${spec_endpoints}"

    if [ ! -s "${spec_endpoints}" ]; then
        echo -e "${RED}  ✗ No endpoints found in OpenAPI spec${NC}"
        rm -f "${spec_endpoints}"
        return 1
    fi

    local endpoint_count=$(wc -l < "${spec_endpoints}" | tr -d ' ')
    echo -e "${GREEN}  ✓ Found ${endpoint_count} endpoints in OpenAPI spec${NC}"
    echo ""

    # Find API calls in frontend code
    local frontend_calls=$(mktemp)
    find_api_calls "${api_dir}" | sort -u > "${frontend_calls}"

    if [ ! -s "${frontend_calls}" ]; then
        echo -e "${YELLOW}  ⚠ No API calls found in frontend code${NC}"
        rm -f "${spec_endpoints}" "${frontend_calls}"
        return 0
    fi

    local call_count=$(wc -l < "${frontend_calls}" | tr -d ' ')
    echo -e "${GREEN}  ✓ Found ${call_count} API calls in frontend code${NC}"
    echo ""

    # Compare frontend calls against OpenAPI spec
    local violations=0
    echo -e "${BLUE}→ Validating API calls against OpenAPI spec...${NC}"
    echo ""

    while IFS= read -r call; do
        # Skip template variables and dynamic paths
        if [[ $call =~ \$\{|\`.*\` ]]; then
            continue
        fi

        # Normalize path for comparison (remove query params)
        local normalized_call=$(echo "$call" | sed 's/?.*$//')

        # Check if call exists in spec
        if ! grep -qF "${normalized_call}" "${spec_endpoints}"; then
            echo -e "${RED}  ✗ VIOLATION: ${call}${NC}"
            echo -e "    ${YELLOW}Not found in OpenAPI spec: ${spec_file}${NC}"
            violations=$((violations + 1))
        else
            echo -e "${GREEN}  ✓ ${call}${NC}"
        fi
    done < "${frontend_calls}"

    rm -f "${spec_endpoints}" "${frontend_calls}"

    echo ""
    if [ $violations -gt 0 ]; then
        echo -e "${RED}╔════════════════════════════════════════════════════════════╗${NC}"
        echo -e "${RED}║  ${service_name}: ${violations} contract violation(s) found!${NC}"
        echo -e "${RED}╚════════════════════════════════════════════════════════════╝${NC}"
        return 1
    else
        echo -e "${GREEN}╔════════════════════════════════════════════════════════════╗${NC}"
        echo -e "${GREEN}║  ${service_name}: All API calls valid! ✓${NC}"
        echo -e "${GREEN}╚════════════════════════════════════════════════════════════╝${NC}"
        return 0
    fi
}

# Validate each service
total_violations=0

# Users API
if [ -f "${DOCS_DIR}/users-api.openapi.yml" ]; then
    if ! validate_service \
        "${DOCS_DIR}/users-api.openapi.yml" \
        "Users API" \
        "${FRONTEND_DIR}/src/services/api"; then
        total_violations=$((total_violations + 1))
    fi
fi

# Companies API
if [ -f "${DOCS_DIR}/companies-api.openapi.yml" ]; then
    if ! validate_service \
        "${DOCS_DIR}/companies-api.openapi.yml" \
        "Companies API" \
        "${FRONTEND_DIR}/src/services/api"; then
        total_violations=$((total_violations + 1))
    fi
fi

echo ""
echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
if [ $total_violations -gt 0 ]; then
    echo -e "${RED}║  Validation Failed: ${total_violations} service(s) with violations${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${YELLOW}To fix:${NC}"
    echo -e "  1. Update OpenAPI spec with missing endpoints"
    echo -e "  2. Regenerate API clients: npm run generate:api-clients"
    echo -e "  3. Use generated clients instead of manual API calls"
    echo ""
    exit 1
else
    echo -e "${GREEN}║  All API Contracts Valid! ✓${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    exit 0
fi
