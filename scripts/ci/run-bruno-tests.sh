#!/bin/bash
# Run Bruno API tests in headless CI mode
# Validates API contracts match OpenAPI specifications
#
# Usage:
#   run-bruno-tests.sh <environment> [auth_token] [options]
#
# Options:
#   --collection NAME    Run only the named collection (iterate one folder at a time).
#                        Useful for hardening a single entity. Example:
#                          run-bruno-tests.sh staging --collection companies-api
#   --cleanup-only       Run only the cleanup .bru files (00-pretest-cleanup.bru,
#                        99-posttest-cleanup.bru) in each collection. Belt-and-suspenders
#                        after a Bruno runner crash to ensure test data is wiped.
#   --no-bail            Disable early-exit on script-level errors. Collection-level
#                        failures already accumulate without bailing (see exit-code logic
#                        at the bottom), so this is rarely needed — mainly a future-proof
#                        flag for callers who want a single, predictable exit point.

set -e

# --- Argument parsing ---
# Handle help before any other parsing so `--help` doesn't get treated as ENVIRONMENT
for arg in "$@"; do
    if [ "$arg" = "-h" ] || [ "$arg" = "--help" ]; then
        sed -n '/^# Usage:/,/^$/p' "$0" | sed 's/^# \{0,1\}//'
        exit 0
    fi
done

ENVIRONMENT=${1:-"development"}
AUTH_TOKEN=""
TARGET_COLLECTION=""
CLEANUP_ONLY=0

# Consume the optional positional auth_token arg (skipped if next arg is a flag)
shift || true
if [ $# -gt 0 ] && [[ "$1" != --* ]]; then
    AUTH_TOKEN="$1"
    shift
fi

# Parse named flags
while [ $# -gt 0 ]; do
    case "$1" in
        --collection)
            TARGET_COLLECTION="$2"
            shift 2
            ;;
        --collection=*)
            TARGET_COLLECTION="${1#--collection=}"
            shift
            ;;
        --cleanup-only)
            CLEANUP_ONLY=1
            shift
            ;;
        --no-bail)
            set +e
            shift
            ;;
        *)
            echo "Unknown option: $1" >&2
            echo "Run with --help for usage" >&2
            exit 2
            ;;
    esac
done

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}Bruno API Contract Tests${NC}"
echo -e "${BLUE}================================${NC}"
echo ""
echo "Environment:        $ENVIRONMENT"
[ -n "$TARGET_COLLECTION" ] && echo "Target collection:  $TARGET_COLLECTION"
[ "$CLEANUP_ONLY" = "1" ] && echo "Mode:               cleanup-only"

# Try to load token from local config if not provided
if [ -z "$AUTH_TOKEN" ]; then
    # Auto-refresh token if expired
    if [ -f "./scripts/auth/refresh-token.sh" ]; then
        ./scripts/auth/refresh-token.sh "$ENVIRONMENT" || true
    fi

    local_config=~/.batbern/${ENVIRONMENT}.json
    if [ -f "$local_config" ]; then
        echo -e "${BLUE}Loading auth token from local config: $local_config${NC}"
        # Use ID token (contains 'aud' claim required by JWT validator)
        # API Gateway validates ID tokens with audience (client_id) claim
        AUTH_TOKEN=$(jq -r '.idToken' "$local_config" 2>/dev/null)

        if [ "$AUTH_TOKEN" = "null" ] || [ -z "$AUTH_TOKEN" ]; then
            echo -e "${YELLOW}WARNING: Failed to load token from local config${NC}"
            echo "Run: ./scripts/auth/get-token.sh $ENVIRONMENT your-email your-password"
            AUTH_TOKEN=""
        else
            # Check if token is expired
            retrieved_at=$(jq -r '.retrievedAt' "$local_config" 2>/dev/null)
            expires_in=$(jq -r '.expiresIn' "$local_config" 2>/dev/null)
            echo -e "${GREEN}✓ Token loaded successfully${NC}"
            echo "Retrieved at: $retrieved_at"
            echo "Expires in: ~$(($expires_in / 60)) minutes from retrieval"
        fi
    else
        echo -e "${YELLOW}WARNING: No auth token found${NC}"
        echo "Usage: $0 <environment> [auth_token]"
        echo "Or run: ./scripts/auth/get-token.sh $ENVIRONMENT your-email your-password"
        echo "Tests requiring authentication may fail"
    fi
fi

# Load per-role tokens (for Epic 8+ multi-role testing)
# Exports ORGANIZER_AUTH_TOKEN, SPEAKER_AUTH_TOKEN, PARTNER_AUTH_TOKEN.
#
# Resolution order:
#   1. If the role's *_AUTH_TOKEN env var is already set (CI path — workflow
#      authenticated each role via aws cognito-idp initiate-auth and exported
#      the ID token into the step env), keep that value.
#   2. Otherwise, look at ~/.batbern/${env}-${role}.json (local-dev path —
#      written by scripts/auth/get-token.sh).
load_role_token() {
    local role="$1"
    local role_upper
    role_upper=$(echo "$role" | tr '[:lower:]' '[:upper:]')
    local env_var_name="${role_upper}_AUTH_TOKEN"
    local existing="${!env_var_name:-}"
    if [ -n "$existing" ]; then
        echo "$existing"
        return 0
    fi

    local role_config=~/.batbern/${ENVIRONMENT}-${role}.json
    if [ -f "$role_config" ]; then
        ./scripts/auth/refresh-token.sh "$ENVIRONMENT" "$role" >/dev/null 2>&1 || true
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

# Fall back: if organizer role file missing, use legacy AUTH_TOKEN
if [ -z "$ORGANIZER_AUTH_TOKEN" ]; then
    ORGANIZER_AUTH_TOKEN="$AUTH_TOKEN"
fi

export ORGANIZER_AUTH_TOKEN SPEAKER_AUTH_TOKEN PARTNER_AUTH_TOKEN

echo -e "${BLUE}Auth tokens available:${NC}"
echo "  ORGANIZER: $([ -n "$ORGANIZER_AUTH_TOKEN" ] && echo 'yes' || echo 'no')"
echo "  SPEAKER:   $([ -n "$SPEAKER_AUTH_TOKEN" ] && echo 'yes' || echo 'no')"
echo "  PARTNER:   $([ -n "$PARTNER_AUTH_TOKEN" ] && echo 'yes' || echo 'no')"
echo ""

# Check if Bruno CLI is installed
if ! command -v bru &> /dev/null; then
    echo -e "${YELLOW}Installing Bruno CLI...${NC}"
    npm install -g @usebruno/cli || {
        echo -e "${RED}Failed to install Bruno CLI${NC}"
        echo "Install manually: npm install -g @usebruno/cli"
        exit 1
    }
fi

# Verify Bruno tests directory exists
if [ ! -d "bruno-tests" ]; then
    echo -e "${RED}ERROR: bruno-tests directory not found${NC}"
    echo "Expected path: $(pwd)/bruno-tests"
    exit 1
fi

passed=0
failed=0
skipped=0

# Test collection directories
# admin-cleanup-api MUST run first — verifies the cleanup endpoint's authorization +
# routing + 400-validation chain through the gateway. Plan F2 mandates "runs FIRST in
# run-bruno-tests.sh so authorization regressions are caught before any test creates
# state to clean."
# speaker-portal-api requires the E2E token helper endpoint (@Profile dev/local/test only)
# and is therefore excluded on staging/production.
collections=(
    "admin-cleanup-api"
    "file-upload-api"
    "companies-api"
    "users-api"
    "events-api"
    "partners-api"
    "tasks-api"
)
if [ "$ENVIRONMENT" = "development" ] || [ "$ENVIRONMENT" = "local" ] || [ "$ENVIRONMENT" = "test" ]; then
    collections+=("speaker-portal-api")
fi

# If --collection set, narrow to just that one (validate it's in the list to prevent typos)
if [ -n "$TARGET_COLLECTION" ]; then
    found=0
    for c in "${collections[@]}"; do
        if [ "$c" = "$TARGET_COLLECTION" ]; then found=1; break; fi
    done
    # Also allow new collections that aren't yet in the default list (e.g. admin-cleanup-api
    # during early hardening) as long as the directory exists.
    if [ "$found" = "0" ] && [ ! -d "bruno-tests/$TARGET_COLLECTION" ]; then
        echo -e "${RED}ERROR:${NC} --collection $TARGET_COLLECTION not found in default list and no such directory under bruno-tests/"
        echo "Available collections:"
        printf '  %s\n' "${collections[@]}"
        exit 2
    fi
    collections=("$TARGET_COLLECTION")
fi

# Helper: run cleanup .bru files in a collection.
# Defensive — failure here is non-fatal because the cleanup endpoint itself
# returns 200/204/404 on success and the .bru files assert oneOf those.
# Used both in --cleanup-only mode and as a post-failure sweep after a regular run.
run_cleanup_for_collection() {
    local collection="$1"
    local collection_path="bruno-tests/$collection"
    local cleanup_files=("00-pretest-cleanup.bru" "99-posttest-cleanup.bru")
    for f in "${cleanup_files[@]}"; do
        if [ -f "$collection_path/$f" ]; then
            echo -e "${BLUE}  Cleanup:${NC} $collection/$f"
            (cd bruno-tests && bru run "$collection/$f" --env "$ENVIRONMENT" 2>&1) || true
        fi
    done
}

# Run tests for each collection
for collection in "${collections[@]}"; do
    collection_path="bruno-tests/$collection"

    if [ ! -d "$collection_path" ]; then
        echo -e "${YELLOW}⚠ Skipping:${NC} $collection (directory not found)"
        ((skipped++))
        continue
    fi

    # Cleanup-only mode: just run the cleanup .bru files for each collection
    if [ "$CLEANUP_ONLY" = "1" ]; then
        echo -e "\n${BLUE}Cleanup-only:${NC} $collection"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        run_cleanup_for_collection "$collection"
        continue
    fi

    echo -e "\n${BLUE}Running tests:${NC} $collection"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    # Set environment variable for Bruno
    export AUTH_TOKEN="$AUTH_TOKEN"

    # Run Bruno tests from the bruno-tests root, targeting specific folder
    # Use -r for recursive execution of all tests in the folder
    if (cd bruno-tests && bru run "$collection" -r --env "$ENVIRONMENT" --output "../results-${collection}.json" 2>&1); then
        echo -e "${GREEN}✓ PASS${NC}: $collection tests passed"
        ((passed++))

        # Display summary if results file exists
        results_file="results-${collection}.json"
        if [ -f "$results_file" ]; then
            total=$(jq -r '.summary.totalTests // 0' "$results_file" 2>/dev/null || echo "0")
            passed_tests=$(jq -r '.summary.passedTests // 0' "$results_file" 2>/dev/null || echo "0")
            failed_tests=$(jq -r '.summary.failedTests // 0' "$results_file" 2>/dev/null || echo "0")

            echo "  Total: $total | Passed: $passed_tests | Failed: $failed_tests"

            # Show failed test details
            if [ "$failed_tests" -gt 0 ]; then
                echo -e "${RED}  Failed tests:${NC}"
                jq -r '.tests[] | select(.status == "failed") | "    - \(.name): \(.error)"' "$results_file" 2>/dev/null || true
            fi

            rm -f "$results_file"
        fi
    else
        echo -e "${RED}✗ FAIL${NC}: $collection tests failed"
        ((failed++))

        # Try to show error details
        results_file="results-${collection}.json"
        if [ -f "$results_file" ]; then
            echo -e "${RED}  Error details:${NC}"
            jq -r '.error // "Unknown error"' "$results_file" 2>/dev/null || cat "$results_file"
            rm -f "$results_file"
        fi

        # Defense layer: re-run cleanup .bru files so 99-posttest-cleanup
        # executes even if Bruno's runner aborted mid-collection. Two layers:
        # (1) the 99- file already runs at the end of bru run -r in the happy path;
        # (2) if Bruno crashed before reaching it, this catches the orphan.
        echo -e "${YELLOW}  Running posttest cleanup defensively...${NC}"
        run_cleanup_for_collection "$collection"
    fi
done

# Summary
echo ""
echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}Bruno Test Summary${NC}"
echo -e "${BLUE}================================${NC}"
echo -e "${GREEN}Collections Passed: $passed${NC}"
echo -e "${RED}Collections Failed: $failed${NC}"
echo -e "${YELLOW}Collections Skipped: $skipped${NC}"

if [ $failed -gt 0 ]; then
    echo ""
    echo -e "${RED}✗ Bruno API tests FAILED${NC}"
    echo ""
    echo -e "${YELLOW}Debugging tips:${NC}"
    echo "1. Check API Gateway and services are deployed and healthy"
    echo "2. Verify auth token is valid and not expired"
    echo "3. Review individual test failures above"
    echo "4. Run locally: $0 $ENVIRONMENT --collection <name>"
    exit 1
else
    echo ""
    echo -e "${GREEN}✓ Bruno API tests PASSED${NC}"
    if [ $skipped -gt 0 ]; then
        echo -e "${YELLOW}  ($skipped collection(s) skipped)${NC}"
    fi
    exit 0
fi
