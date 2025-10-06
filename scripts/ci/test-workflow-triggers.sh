#!/bin/bash
# Test script for GitHub Actions workflow triggers (AC 1)
# Tests that workflows trigger correctly on push/PR events
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
echo "Workflow Trigger Tests (AC 1)"
echo "================================"

# Test 1.1: should_triggerBuild_when_pushToDevelop
test_push_to_develop() {
    echo -e "\n${YELLOW}Test 1.1:${NC} should_triggerBuild_when_pushToDevelop"

    if [ ! -f "$WORKFLOWS_DIR/build.yml" ]; then
        echo -e "${RED}FAIL${NC}: build.yml does not exist"
        return 1
    fi

    # Check if workflow triggers on push to develop
    if grep -q "branches:.*develop" "$WORKFLOWS_DIR/build.yml" && \
       grep -q "push:" "$WORKFLOWS_DIR/build.yml"; then
        echo -e "${GREEN}PASS${NC}: Workflow triggers on push to develop"
        return 0
    else
        echo -e "${RED}FAIL${NC}: Workflow does not trigger on push to develop"
        return 1
    fi
}

# Test 1.2: should_triggerBuild_when_pushToMain
test_push_to_main() {
    echo -e "\n${YELLOW}Test 1.2:${NC} should_triggerBuild_when_pushToMain"

    if [ ! -f "$WORKFLOWS_DIR/build.yml" ]; then
        echo -e "${RED}FAIL${NC}: build.yml does not exist"
        return 1
    fi

    # Check if workflow triggers on push to main
    if grep -q "branches:.*main" "$WORKFLOWS_DIR/build.yml" && \
       grep -q "push:" "$WORKFLOWS_DIR/build.yml"; then
        echo -e "${GREEN}PASS${NC}: Workflow triggers on push to main"
        return 0
    else
        echo -e "${RED}FAIL${NC}: Workflow does not trigger on push to main"
        return 1
    fi
}

# Run tests
if test_push_to_develop; then ((passed++)); else ((failed++)); fi
if test_push_to_main; then ((passed++)); else ((failed++)); fi

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
