#!/bin/bash
# Run Playwright UI E2E tests against a deployed environment, with automatic token loading.
# docs/plans/playwright-staging-hardening.md §A7
#
# Usage:
#   run-playwright-tests.sh <environment> [options]
#
# Environments:
#   development  localhost:8100 (local stack)        — local dev tokens
#   staging      www.batbern.ch / api.batbern.ch     — production (staging-account) tokens
#   production   www.batbern.ch / api.batbern.ch     — production tokens
#   beta         beta.batbern.ch / api.batbern.ch    — frontend-only canary on the PRODUCTION
#                backend; reuses the staging role tokens. ⚠ shares PRODUCTION data — run only
#                frontend-safe specs against it.
#
# Options:
#   --project NAME       chromium | speaker | partner. Default: every project whose role
#                        token is available (chromium always; speaker/partner when present).
#   --scope SCOPE        smoke | gate | quarantine | all. Routes the tag grep (plan §A6):
#                          smoke      → --grep @smoke  --grep-invert @quarantine (per-deploy gate)
#                          gate       → --grep @gate   --grep-invert @quarantine (nightly full run)
#                          quarantine → --grep @quarantine (nightly re-test to promote settled flakes)
#                          all        → everything (local default)
#   --slice GREP         Run one entity slice: positive --grep GREP (still inverts
#                        @quarantine). Takes precedence over --scope's positive selector.
#   --cleanup-only       Run no tests; fire only globalSetup + globalTeardown so the
#                        canonical-prefix sweep executes (belt-and-suspenders after a crash).
#
# Examples:
#   run-playwright-tests.sh staging --scope smoke
#   run-playwright-tests.sh staging --scope gate
#   run-playwright-tests.sh development --slice companies
#   run-playwright-tests.sh staging --cleanup-only

set -e

for arg in "$@"; do
    if [ "$arg" = "-h" ] || [ "$arg" = "--help" ]; then
        sed -n '/^# Usage:/,/^$/p' "$0" | sed 's/^# \{0,1\}//'
        exit 0
    fi
done

ENVIRONMENT=${1:-"staging"}
shift || true

PROJECT=""          # empty → auto-build from available role tokens (backward compatible)
SCOPE="all"
SLICE=""
CLEANUP_ONLY=0

while [ $# -gt 0 ]; do
    case "$1" in
        --project) PROJECT="$2"; shift 2 ;;
        --project=*) PROJECT="${1#--project=}"; shift ;;
        --scope) SCOPE="$2"; shift 2 ;;
        --scope=*) SCOPE="${1#--scope=}"; shift ;;
        --slice) SLICE="$2"; shift 2 ;;
        --slice=*) SLICE="${1#--slice=}"; shift ;;
        --cleanup-only) CLEANUP_ONLY=1; shift ;;
        *) echo "Unknown option: $1" >&2; echo "Run with --help for usage" >&2; exit 2 ;;
    esac
done

# Token environment: beta is a frontend-only canary on the PRODUCTION backend/Cognito, so its
# auth tokens are the production (staging-account) role tokens — reuse the `staging` token files.
TOKEN_ENV="$ENVIRONMENT"
[ "$ENVIRONMENT" = "beta" ] && TOKEN_ENV="staging"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}Playwright UI E2E Tests${NC}"
echo -e "${BLUE}================================${NC}"
echo ""
echo "Environment: $ENVIRONMENT"
echo "Scope:       $SCOPE"
[ -n "$PROJECT" ] && echo "Project:     $PROJECT"
[ -n "$SLICE" ] && echo "Slice:       $SLICE"
[ "$CLEANUP_ONLY" = "1" ] && echo "Mode:        cleanup-only"
echo ""

# Auto-refresh token if expired (uses TOKEN_ENV → staging tokens for the beta canary)
if [ -f "./scripts/auth/refresh-token.sh" ]; then
    ./scripts/auth/refresh-token.sh "$TOKEN_ENV" || true
fi

# Load legacy organizer token (also exported as AUTH_TOKEN for API-integration specs)
local_config=~/.batbern/${TOKEN_ENV}.json
if [ -f "$local_config" ]; then
    echo -e "${BLUE}Loading auth token from: $local_config${NC}"
    AUTH_TOKEN=$(jq -r '.idToken' "$local_config" 2>/dev/null)
    if [ "$AUTH_TOKEN" = "null" ] || [ -z "$AUTH_TOKEN" ]; then
        echo -e "${YELLOW}WARNING: Failed to load token${NC}"
        AUTH_TOKEN=""
    else
        echo -e "${GREEN}✓ Token loaded successfully${NC}"
        export AUTH_TOKEN="$AUTH_TOKEN"
    fi
else
    echo -e "${YELLOW}WARNING: No legacy auth token at $local_config${NC}"
fi

# Load per-role tokens (Epic 8+ multi-role testing)
load_role_token() {
    local role="$1"
    local role_config=~/.batbern/${TOKEN_ENV}-${role}.json
    if [ -f "$role_config" ]; then
        ./scripts/auth/refresh-token.sh "$TOKEN_ENV" "$role" 2>/dev/null || true
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

[ -z "$ORGANIZER_AUTH_TOKEN" ] && ORGANIZER_AUTH_TOKEN="$AUTH_TOKEN"

export ORGANIZER_AUTH_TOKEN SPEAKER_AUTH_TOKEN PARTNER_AUTH_TOKEN

echo -e "${BLUE}Auth tokens available:${NC}"
echo "  ORGANIZER: $([ -n "$ORGANIZER_AUTH_TOKEN" ] && echo 'yes' || echo 'no')"
echo "  SPEAKER:   $([ -n "$SPEAKER_AUTH_TOKEN" ] && echo 'yes' || echo 'no')"
echo "  PARTNER:   $([ -n "$PARTNER_AUTH_TOKEN" ] && echo 'yes' || echo 'no')"
echo ""

# Set environment-specific URLs
if [ "$ENVIRONMENT" = "staging" ]; then
    export TEST_ENV="staging"
    export E2E_BASE_URL="https://www.batbern.ch"
    export E2E_API_URL="https://api.batbern.ch"
    export E2E_AWS_REGION="eu-central-1"
elif [ "$ENVIRONMENT" = "beta" ]; then
    # Beta = the frontend-only canary SPA on beta.batbern.ch served against the SAME
    # production API / Cognito / DB as www. Use it to exercise frontend-only changes on
    # real infra before promoting to prod. Tokens are the production (staging) role tokens
    # (TOKEN_ENV=staging above). ⚠ Beta shares PRODUCTION data — only frontend-safe specs.
    export TEST_ENV="beta"
    export E2E_BASE_URL="https://beta.batbern.ch"
    export E2E_API_URL="https://api.batbern.ch"
    export E2E_AWS_REGION="eu-central-1"
elif [ "$ENVIRONMENT" = "production" ]; then
    export TEST_ENV="production"
    # Canonical host is www.* (matches the edge-readiness poll + playwright.config). The
    # apex batbern.ch redirects, which would break the asset-hash poll if this branch ran.
    export E2E_BASE_URL="https://www.batbern.ch"
    export E2E_API_URL="https://api.batbern.ch"
    export E2E_AWS_REGION="eu-central-1"
else
    export TEST_ENV="development"
    export E2E_BASE_URL="http://localhost:8100"
    export E2E_API_URL="http://localhost:8000"
    export E2E_AWS_REGION="eu-central-1"
fi

echo -e "${BLUE}Test Configuration:${NC}"
echo "  Environment: $TEST_ENV"
echo "  Base URL:    $E2E_BASE_URL"
echo "  API URL:     $E2E_API_URL"
echo ""

# Check we're in the project root
if [ ! -d "web-frontend" ]; then
    echo -e "${RED}ERROR: Must be run from project root${NC}"
    echo "Current directory: $(pwd)"
    exit 1
fi

# ── Edge-readiness poll (plan §A1, OQ-2) ──────────────────────────────────────────────
# The frontend is "build once, deploy everywhere" (runtime config via GET /api/v1/config)
# with NO embedded git SHA and NO /version marker, and it is built SELECTIVELY (only when
# the frontend changed), so a SHA-match poll is architecturally impossible. We instead
# prove CloudFront serves a COHERENT bundle: index.html (200) references a hashed entry
# asset that is itself fetchable (200). This catches the stale-index / un-propagated-asset
# race right after a deploy. Skipped for local dev (the webServer block handles readiness).
wait_for_edge_ready() {
    local base="$1"
    local max_attempts=20
    local attempt=1
    local delay=5
    echo -e "${BLUE}Edge-readiness poll: $base${NC}"
    while [ "$attempt" -le "$max_attempts" ]; do
        local html
        html=$(curl -fsSL --max-time 15 "$base/" 2>/dev/null || true)
        if [ -n "$html" ]; then
            local asset
            asset=$(echo "$html" | grep -oE '/assets/[A-Za-z0-9._-]+\.(js|css)' | head -1)
            if [ -z "$asset" ]; then
                echo -e "${GREEN}✓ index served (no hashed asset ref found) — proceeding${NC}"
                return 0
            fi
            local code
            code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 "$base$asset" || echo "000")
            if [ "$code" = "200" ]; then
                echo -e "${GREEN}✓ edge ready: $asset → 200 (attempt $attempt)${NC}"
                return 0
            fi
            echo -e "${YELLOW}  attempt $attempt: $asset → $code (retry in ${delay}s)${NC}"
        else
            echo -e "${YELLOW}  attempt $attempt: index.html not served yet (retry in ${delay}s)${NC}"
        fi
        sleep "$delay"
        attempt=$((attempt + 1))
        [ "$delay" -lt 30 ] && delay=$((delay * 2))
    done
    echo -e "${RED}✗ edge not ready after $max_attempts attempts (~5 min)${NC}" >&2
    return 1
}

if [ "$TEST_ENV" != "development" ]; then
    wait_for_edge_ready "$E2E_BASE_URL"
fi

cd web-frontend

if [ ! -f "playwright.config.ts" ] && [ ! -f "playwright.config.js" ]; then
    echo -e "${RED}ERROR: Playwright not configured${NC}"
    exit 1
fi

# Ensure deps + browsers
if ! npm list @playwright/test >/dev/null 2>&1; then
    echo -e "${YELLOW}Installing Playwright...${NC}"
    npm install -D @playwright/test
fi
echo -e "${BLUE}Ensuring Playwright browsers are installed...${NC}"
if [ "${CI:-}" = "true" ]; then
    npx playwright install --with-deps chromium
else
    npx playwright install chromium >/dev/null 2>&1 || true
fi

# ── Build the project list ────────────────────────────────────────────────────────────
PROJECT_ARGS=()
if [ -n "$PROJECT" ]; then
    PROJECT_ARGS+=("--project=$PROJECT")
else
    # Backward compatible: chromium always; role projects when their tokens are present.
    PROJECT_ARGS+=("--project=chromium")
    [ -n "$SPEAKER_AUTH_TOKEN" ] && PROJECT_ARGS+=("--project=speaker")
    [ -n "$PARTNER_AUTH_TOKEN" ] && PROJECT_ARGS+=("--project=partner")
fi

# ── Build the grep routing (plan §A6) ─────────────────────────────────────────────────
GREP_ARGS=()
if [ "$CLEANUP_ONLY" = "1" ]; then
    # Match no test → 0 tests run, but globalSetup + globalTeardown still fire, so the
    # canonical-prefix sweep in global-teardown.ts executes. Bruno's --cleanup-only analogue.
    GREP_ARGS+=(--grep "@__cleanup_only_no_match__")
else
    if [ -n "$SLICE" ]; then
        GREP_ARGS+=(--grep "$SLICE" --grep-invert "@quarantine")
    elif [ "$SCOPE" = "smoke" ]; then
        GREP_ARGS+=(--grep "@smoke" --grep-invert "@quarantine")
    elif [ "$SCOPE" = "gate" ]; then
        GREP_ARGS+=(--grep "@gate" --grep-invert "@quarantine")
    elif [ "$SCOPE" = "quarantine" ]; then
        # Re-test only the quarantined specs (nightly) so settled flakes can be promoted.
        GREP_ARGS+=(--grep "@quarantine")
    elif [ "$SCOPE" = "all" ]; then
        : # everything (local default) — quarantined specs included only here
    else
        echo -e "${RED}ERROR: --scope must be smoke|gate|quarantine|all (got '$SCOPE')${NC}" >&2
        exit 2
    fi
fi

echo ""
echo -e "${BLUE}Running:${NC} npx playwright test ${PROJECT_ARGS[*]} ${GREP_ARGS[*]}"
echo -e "${BLUE}================================${NC}"
echo ""

set +e
npx playwright test "${PROJECT_ARGS[@]}" "${GREP_ARGS[@]}"
EXIT_CODE=$?
set -e

echo ""
if [ "$EXIT_CODE" -eq 0 ]; then
    echo -e "${GREEN}✅ Playwright tests PASSED${NC}"
    exit 0
else
    echo -e "${RED}✗ Playwright tests FAILED (exit $EXIT_CODE)${NC}"
    echo ""
    echo -e "${YELLOW}Debugging tips:${NC}"
    echo "1. Check service health and deployment status"
    echo "2. Verify auth token is not expired"
    echo "3. Review test output above; view HTML report: npx playwright show-report"
    exit "$EXIT_CODE"
fi
