#!/bin/bash
# Master test runner for all pipeline tests (Task 1 - RED Phase)
# Runs all workflow structure tests
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}======================================${NC}"
echo -e "${BLUE}GitHub Actions Pipeline Test Suite${NC}"
echo -e "${BLUE}======================================${NC}"
echo ""

total_passed=0
total_failed=0

# Run all test scripts
test_scripts=(
    "test-workflow-triggers.sh"
    "test-parallel-builds.sh"
    "test-caching.sh"
    "test-versioning.sh"
)

for script in "${test_scripts[@]}"; do
    if [ -f "$SCRIPT_DIR/$script" ]; then
        chmod +x "$SCRIPT_DIR/$script"
        echo -e "\n${BLUE}Running $script...${NC}\n"

        if bash "$SCRIPT_DIR/$script"; then
            echo -e "${GREEN}✓ $script completed successfully${NC}"
            ((total_passed++))
        else
            echo -e "${RED}✗ $script failed${NC}"
            ((total_failed++))
        fi
    else
        echo -e "${YELLOW}⚠ $script not found${NC}"
        ((total_failed++))
    fi
done

# Final summary
echo ""
echo -e "${BLUE}======================================${NC}"
echo -e "${BLUE}Overall Test Summary${NC}"
echo -e "${BLUE}======================================${NC}"
echo -e "Test Scripts Passed: ${GREEN}$total_passed${NC}"
echo -e "Test Scripts Failed: ${RED}$total_failed${NC}"

if [ $total_failed -gt 0 ]; then
    echo -e "\n${RED}✗ Pipeline tests FAILED${NC}"
    echo -e "${YELLOW}This is expected in RED phase - implement workflows to make tests pass${NC}"
    exit 1
else
    echo -e "\n${GREEN}✓ All pipeline tests PASSED${NC}"
    exit 0
fi
