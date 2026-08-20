#!/bin/bash
# Smoke tests for post-deployment validation
# Tests critical endpoints to ensure basic functionality
# Note: We don't use 'set -e' here because we want to run all tests
# and report a summary at the end, not exit on first failure

FRONTEND_URL=$1
API_URL=$2
CDN_URL=${3:-"https://cdn.batbern.ch"}

if [ -z "$FRONTEND_URL" ] || [ -z "$API_URL" ]; then
    echo "Usage: $0 <frontend_url> <api_url> [cdn_url]"
    echo "Example: $0 https://www.batbern.ch https://api.batbern.ch https://cdn.batbern.ch"
    exit 1
fi

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}Smoke Tests${NC}"
echo -e "${BLUE}================================${NC}"
echo ""
echo "Frontend: $FRONTEND_URL"
echo "API: $API_URL"
echo ""

passed=0
failed=0

# Test 1: Frontend is accessible
echo -e "${YELLOW}Test 1:${NC} Frontend accessibility"
response=$(curl -s -o /dev/null -w "%{http_code}" "$FRONTEND_URL" || echo "000")
if [ "$response" = "200" ] || [ "$response" = "301" ] || [ "$response" = "302" ]; then
    echo -e "${GREEN}✓ PASS${NC}: Frontend returned $response"
    ((passed++))
else
    echo -e "${RED}✗ FAIL${NC}: Frontend returned $response (expected 200/301/302)"
    ((failed++))
fi

# Test 2: API Gateway health
echo -e "\n${YELLOW}Test 2:${NC} API Gateway health"
response=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/actuator/health" || echo "000")
if [ "$response" = "200" ]; then
    echo -e "${GREEN}✓ PASS${NC}: API health endpoint returned $response"

    # Check health status
    health_response=$(curl -s "$API_URL/actuator/health" || echo "{}")
    status=$(echo "$health_response" | jq -r '.status' 2>/dev/null || echo "UNKNOWN")
    echo "  Health status: $status"
    ((passed++))
else
    echo -e "${RED}✗ FAIL${NC}: API health endpoint returned $response (expected 200)"
    ((failed++))
fi

# Test 3: Service health checks (proxied via API Gateway's ServiceHealthController)
# Each microservice exposes /actuator/health on its Service Connect DNS; the API Gateway
# proxies these at /services/{name}/health (see api-gateway/.../ServiceHealthController.java).
#
# A service deliberately parked at desiredCount 0 is SKIPPED rather than failed.
# attendee-experience is parked (docs/plans/aws-cost-reduction.md tier 1 — it is an empty
# application), and hardcoding it out of the list here would rot the moment it comes back
# for Epic 7. So the parked state is detected from ECS rather than assumed: whatever the
# cluster says has 0 desired tasks is expected to be unreachable.
#
# If the ECS lookup is unavailable (no credentials, no cluster), every service in the list
# is checked — the fallback is deliberately the STRICTER behaviour, so a missing lookup can
# never silently turn this gate off.
echo -e "\n${YELLOW}Test 3:${NC} Service health checks"
services=("event-management" "speaker-coordination" "partner-coordination" "attendee-experience" "company-user-management")
service_tests_failed=0

# Map of ECS service display names -> desired count, e.g. "AttendeeExperience 0".
ecs_cluster="batbern-${ENVIRONMENT:-staging}"
parked_services=""
if command -v aws >/dev/null 2>&1; then
    if service_arns=$(aws ecs list-services --cluster "$ecs_cluster" --query 'serviceArns' --output text 2>/dev/null) \
        && [ -n "$service_arns" ]; then
        # shellcheck disable=SC2086
        parked_services=$(aws ecs describe-services --cluster "$ecs_cluster" \
            --services $service_arns \
            --query 'services[?desiredCount==`0`].serviceName' --output text 2>/dev/null || echo "")
        if [ -n "$parked_services" ]; then
            echo -e "  ${YELLOW}note${NC}: parked at 0 tasks, will be skipped: $parked_services"
        fi
    else
        echo -e "  ${YELLOW}note${NC}: could not read ECS desired counts — checking every service"
    fi
fi

# Health-endpoint name -> the fragment that appears in the ECS service name.
#
# Deliberately an explicit map rather than a CamelCase derivation: four of the five do
# derive mechanically, but company-user-management's ECS service is called
# CompanyManagement (no "User"), so a derivation silently fails to match exactly one
# service — and it would fail in the direction of checking a parked service and reporting
# a false failure, which is confusing rather than safe.
ecs_name_for() {
    case "$1" in
        event-management)        echo "EventManagement" ;;
        speaker-coordination)    echo "SpeakerCoordination" ;;
        partner-coordination)    echo "PartnerCoordination" ;;
        attendee-experience)     echo "AttendeeExperience" ;;
        company-user-management) echo "CompanyManagement" ;;
        *)                       echo "" ;;
    esac
}

for service in "${services[@]}"; do
    ecs_name=$(ecs_name_for "$service")
    if [ -n "$parked_services" ] && [ -n "$ecs_name" ] && echo "$parked_services" | grep -q "$ecs_name"; then
        echo -e "  ${YELLOW}−${NC} $service skipped (parked at 0 tasks)"
        continue
    fi

    endpoint="$API_URL/services/$service/health"
    response=$(curl -s -o /dev/null -w "%{http_code}" "$endpoint" || echo "000")

    if [ "$response" = "200" ]; then
        echo -e "  ${GREEN}✓${NC} $service is healthy"
    else
        echo -e "  ${RED}✗${NC} $service health check returned $response (expected 200) — $endpoint"
        service_tests_failed=$((service_tests_failed + 1))
    fi
done

if [ $service_tests_failed -eq 0 ]; then
    echo -e "${GREEN}✓ PASS${NC}: All service health checks passed"
    ((passed++))
else
    echo -e "${RED}✗ FAIL${NC}: $service_tests_failed service health check(s) failed"
    ((failed++))
fi

# Tests for database and cache connectivity are intentionally omitted here.
# Spring Boot Actuator already includes DB and Redis health indicators in /actuator/health
# (validated by Test 2). Re-checking them via separate URLs added noise and false warnings
# for endpoints that never existed; the aggregated check is authoritative.

# Test 4: CDN image serving + Lambda@Edge resize
# Validates that CloudFront can serve images AND that the Lambda@Edge resize function
# initialises correctly. A missing 'sharp' module crashes the Lambda at init and returns
# 503 for ALL CDN requests — including plain pass-throughs — so this test catches that
# entire class of bug immediately after each storage-stack deploy.
echo -e "\n${YELLOW}Test 4:${NC} CDN image serving and Lambda@Edge resize"

# Find a CURRENT image path via the event-photos API. This endpoint returns the event's
# LIVE photo keys, so the test survives photo re-uploads. The previous approach (a
# /api/events?status=COMPLETED lookup that returns no rows + a hardcoded UUID fixture)
# rotted when BATbern58's photos were re-uploaded with new UUIDs (2026-06-12) — the dead
# key 404'd at the S3 origin, the Lambda passed the 403 through, and Test 4 failed even
# though the resize Lambda was perfectly healthy. Derive the path from displayUrl so it
# works regardless of CDN host.
SAMPLE_IMAGE_PATH=""
for ev in BATbern58 BATbern57 BATbern56 BATbern55; do
    SAMPLE_IMAGE_PATH=$(curl -s --max-time 10 "$API_URL/api/v1/events/$ev/photos" \
        | jq -r '.[0].displayUrl // empty' 2>/dev/null | sed -E 's#^https?://[^/]+/##')
    [ -n "$SAMPLE_IMAGE_PATH" ] && break
done

if [ -z "$SAMPLE_IMAGE_PATH" ]; then
    # No fixture discoverable — skip rather than emit a false CDN failure. (Service/API
    # health is already asserted by Tests 2-3, so we are not masking an outage here.)
    echo -e "  ${YELLOW}⚠ SKIP${NC}: no current event photo found via the API — skipping CDN resize check"
else
    # 6a: Plain CDN fetch (no resize params) — Lambda@Edge must pass through to S3
    plain_status=$(curl -s -o /dev/null -w "%{http_code}" \
        --max-time 10 "$CDN_URL/$SAMPLE_IMAGE_PATH" || echo "000")
    if [ "$plain_status" = "200" ]; then
        echo -e "  ${GREEN}✓${NC} Plain image fetch: $plain_status"
        ((passed++))
    else
        echo -e "  ${RED}✗ FAIL${NC}: Plain image fetch returned $plain_status (expected 200)"
        echo -e "      URL: $CDN_URL/$SAMPLE_IMAGE_PATH"
        ((failed++))
    fi

    # 6b: Resize request — Lambda@Edge must load sharp and return image/webp.
    #
    # Randomise w/h per run so each deploy hits a fresh CloudFront cache key. The
    # Lambda's graceful fallback (returns original jpeg if sharp fails) emits the
    # upstream cache-control headers (max-age=31536000, immutable), so a single
    # regression would otherwise poison one fixed cache entry forever — the test
    # would keep seeing the stale jpeg long after the Lambda recovered. A random
    # cache-buster guarantees this test always reflects the *current* Lambda state.
    # Retried, because a deploy that changes this function re-replicates it to every
    # CloudFront edge location, and requests can 503 until that finishes. CloudFormation
    # says so itself during the deploy: "DELETE_FAILED (skipped) | AWS::Lambda::Version |
    # ImageResizeFnCurrentVersion... (this will take a few minutes to recover)".
    #
    # A single immediate assertion therefore raced the rollout and failed two consecutive
    # production deploys on 2026-08-12 while the CDN was in fact healthy minutes later.
    # Retrying converts that flake into a real signal: if resize is still not serving WebP
    # after the window below, something is genuinely broken.
    #
    # w/h are randomised PER ATTEMPT, not once: CloudFront caches error responses briefly,
    # so reusing one cache key could return a cached 503 for the whole retry loop and hide
    # a recovery. A fresh key each time always reflects the current Lambda state.
    resize_max_attempts=8
    resize_delay=15
    resize_ok=false
    resize_attempt=1
    while [ "$resize_attempt" -le "$resize_max_attempts" ]; do
        rand_w=$((150 + RANDOM % 350))
        rand_h=$((150 + RANDOM % 350))
        resize_url="$CDN_URL/$SAMPLE_IMAGE_PATH?w=$rand_w&h=$rand_h&fit=cover"
        resize_response=$(curl -s -D - -o /dev/null --max-time 15 "$resize_url" || echo "")
        resize_status=$(echo "$resize_response" | grep "^HTTP" | awk '{print $2}' | tr -d '\r')
        resize_ct=$(echo "$resize_response" | grep -i "^content-type:" | tr -d '\r' | head -1)

        if [ "$resize_status" = "200" ] && echo "$resize_ct" | grep -qi "image/webp"; then
            resize_ok=true
            break
        fi

        if [ "$resize_attempt" -lt "$resize_max_attempts" ]; then
            echo -e "  ${YELLOW}…${NC} attempt $resize_attempt/$resize_max_attempts: HTTP ${resize_status:-000} ${resize_ct} — edge function may still be replicating, retrying in ${resize_delay}s"
            sleep "$resize_delay"
        fi
        resize_attempt=$((resize_attempt + 1))
    done

    if [ "$resize_ok" = "true" ]; then
        echo -e "  ${GREEN}✓${NC} Resize+WebP: $resize_status, $resize_ct (attempt $resize_attempt/$resize_max_attempts)"
        ((passed++))
    else
        echo -e "  ${RED}✗ FAIL${NC}: Resize still not serving WebP after $resize_max_attempts attempts over ~$((resize_max_attempts * resize_delay))s"
        echo -e "      Last response: HTTP $resize_status, Content-Type: $resize_ct"
        echo -e "      Expected HTTP 200 + content-type: image/webp"
        echo -e "      URL: $resize_url"
        echo -e "      Interpreting this:"
        echo -e "        503 / no response  → the Lambda@Edge crashed at init (a missing native"
        echo -e "                              module, e.g. @img/sharp-linux-x64 absent from the bundle)"
        echo -e "        200 + image/jpeg   → the function ran but sharp failed, so it fell back to"
        echo -e "                              passing the original through"
        echo -e "      Check the Lambda@Edge logs in us-east-1 (and the region nearest the runner)."
        ((failed++))
    fi
fi

# Summary
echo ""
echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}Smoke Test Summary${NC}"
echo -e "${BLUE}================================${NC}"
echo -e "${GREEN}Passed: $passed${NC}"
echo -e "${RED}Failed: $failed${NC}"

if [ $failed -gt 0 ]; then
    echo ""
    echo -e "${RED}✗ Smoke tests FAILED${NC}"
    exit 1
else
    echo ""
    echo -e "${GREEN}✓ Smoke tests PASSED${NC}"
    exit 0
fi
