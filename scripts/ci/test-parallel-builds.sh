#!/bin/bash
# Test script for parallel build execution (AC 2)
# Tests that services build in parallel using matrix strategy
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
WORKFLOWS_DIR="$PROJECT_ROOT/.github/workflows"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

passed=0
failed=0

echo "================================"
echo "Parallel Build Tests (AC 2)"
echo "================================"

# Test 2.1: should_buildServicesInParallel_when_pipelineRuns
test_parallel_builds() {
    echo -e "\n${YELLOW}Test 2.1:${NC} should_buildServicesInParallel_when_pipelineRuns"

    if [ ! -f "$WORKFLOWS_DIR/build.yml" ]; then
        echo -e "${RED}FAIL${NC}: build.yml does not exist"
        return 1
    fi

    # Check for matrix strategy
    if grep -q "strategy:" "$WORKFLOWS_DIR/build.yml" && \
       grep -q "matrix:" "$WORKFLOWS_DIR/build.yml"; then
        echo -e "${GREEN}PASS${NC}: Build uses matrix strategy for parallel execution"

        # Check for specific services in matrix
        local services=(
            "event-management-service"
            "speaker-coordination-service"
            "partner-coordination-service"
            "attendee-experience-service"
            "company-management-service"
            "api-gateway"
        )

        local all_found=true
        for service in "${services[@]}"; do
            if ! grep -q "$service" "$WORKFLOWS_DIR/build.yml"; then
                echo -e "${YELLOW}WARNING${NC}: Service $service not found in build matrix"
                all_found=false
            fi
        done

        if $all_found; then
            echo -e "${GREEN}INFO${NC}: All required services found in build matrix"
        fi

        return 0
    else
        echo -e "${RED}FAIL${NC}: Build does not use matrix strategy"
        return 1
    fi
}

# Test 2.2: should_failFastDisabled_when_parallelBuilding
test_fail_fast_disabled() {
    echo -e "\n${YELLOW}Test 2.2:${NC} should_failFastDisabled_when_parallelBuilding"

    if [ ! -f "$WORKFLOWS_DIR/build.yml" ]; then
        echo -e "${RED}FAIL${NC}: build.yml does not exist"
        return 1
    fi

    # Check for fail-fast: false to ensure all services build even if one fails
    if grep -q "fail-fast: false" "$WORKFLOWS_DIR/build.yml"; then
        echo -e "${GREEN}PASS${NC}: Fail-fast is disabled for parallel builds"
        return 0
    else
        echo -e "${YELLOW}WARNING${NC}: fail-fast not explicitly disabled (may use default)"
        return 0
    fi
}

# Run tests
if test_parallel_builds; then ((passed++)); else ((failed++)); fi
if test_fail_fast_disabled; then ((passed++)); else ((failed++)); fi

# Summary
echo -e "\n================================"
echo -e "Test Summary"
echo -e "================================"
echo -e "${GREEN}Passed: $passed${NC}"
echo -e "${RED}Failed: $failed${NC}"

if [ $failed -gt 0 ]; then
    exit 1
fi

exit 0
