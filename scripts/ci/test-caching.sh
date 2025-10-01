#!/bin/bash
# Test script for dependency caching (AC 3)
# Tests that Gradle, npm, and Docker layer caching is configured
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
echo "Dependency Caching Tests (AC 3)"
echo "================================"

# Test 3.1: should_useCachedDependencies_when_cacheHit
test_gradle_caching() {
    echo -e "\n${YELLOW}Test 3.1a:${NC} should_useGradleCache_when_configured"

    if [ ! -f "$WORKFLOWS_DIR/build.yml" ]; then
        echo -e "${RED}FAIL${NC}: build.yml does not exist"
        return 1
    fi

    # Check for Gradle caching configuration
    if grep -q "cache: 'gradle'" "$WORKFLOWS_DIR/build.yml" || \
       grep -q "actions/cache.*gradle" "$WORKFLOWS_DIR/build.yml"; then
        echo -e "${GREEN}PASS${NC}: Gradle caching is configured"
        return 0
    else
        echo -e "${RED}FAIL${NC}: Gradle caching not configured"
        return 1
    fi
}

test_npm_caching() {
    echo -e "\n${YELLOW}Test 3.1b:${NC} should_useNpmCache_when_configured"

    if [ ! -f "$WORKFLOWS_DIR/build.yml" ]; then
        echo -e "${RED}FAIL${NC}: build.yml does not exist"
        return 1
    fi

    # Check for npm caching configuration
    if grep -q "cache: 'npm'" "$WORKFLOWS_DIR/build.yml" || \
       grep -q "actions/cache.*npm" "$WORKFLOWS_DIR/build.yml"; then
        echo -e "${GREEN}PASS${NC}: npm caching is configured"
        return 0
    else
        echo -e "${RED}FAIL${NC}: npm caching not configured"
        return 1
    fi
}

# Test 3.2: should_downloadDependencies_when_cacheMiss
test_cache_restore_keys() {
    echo -e "\n${YELLOW}Test 3.2:${NC} should_haveCacheRestoreKeys_when_configuring"

    if [ ! -f "$WORKFLOWS_DIR/build.yml" ]; then
        echo -e "${RED}FAIL${NC}: build.yml does not exist"
        return 1
    fi

    # Check if cache actions use setup-java or setup-node with cache parameter
    # These actions handle restore keys automatically
    local has_cache_config=false

    if grep -q "cache:" "$WORKFLOWS_DIR/build.yml"; then
        echo -e "${GREEN}PASS${NC}: Cache configuration present with automatic restore keys"
        has_cache_config=true
        return 0
    fi

    if ! $has_cache_config; then
        echo -e "${RED}FAIL${NC}: No cache configuration found"
        return 1
    fi
}

# Run tests
if test_gradle_caching; then ((passed++)); else ((failed++)); fi
if test_npm_caching; then ((passed++)); else ((failed++)); fi
if test_cache_restore_keys; then ((passed++)); else ((failed++)); fi

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
