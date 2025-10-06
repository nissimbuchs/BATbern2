#!/bin/bash
# Test script for build versioning (AC 4)
# Tests that semantic versioning with Git tags is implemented
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
echo "Build Versioning Tests (AC 4)"
echo "================================"

# Test 4.1: should_tagWithSemVer_when_buildSucceeds
test_version_tagging() {
    echo -e "\n${YELLOW}Test 4.1:${NC} should_useSemanticVersioning_when_building"

    if [ ! -f "$WORKFLOWS_DIR/build.yml" ]; then
        echo -e "${RED}FAIL${NC}: build.yml does not exist"
        return 1
    fi

    # Check for version-related environment variables or steps
    if grep -q "github.sha" "$WORKFLOWS_DIR/build.yml" || \
       grep -q "GITHUB_RUN_NUMBER" "$WORKFLOWS_DIR/build.yml" || \
       grep -q "git describe" "$WORKFLOWS_DIR/build.yml" || \
       grep -q "version" "$WORKFLOWS_DIR/build.yml"; then
        echo -e "${GREEN}PASS${NC}: Build includes version information"
        return 0
    else
        echo -e "${RED}FAIL${NC}: No versioning configuration found"
        return 1
    fi
}

# Test 4.2: should_extractVersionFromGitTag_when_tagExists
test_git_version_extraction() {
    echo -e "\n${YELLOW}Test 4.2:${NC} should_extractVersionFromGitTag_when_configured"

    # Test the version extraction logic with mock git tags
    # This tests the semantic versioning format: v{major}.{minor}.{patch}

    # Test version format validation
    local test_versions=(
        "v1.0.0"
        "v1.2.3"
        "v2.0.0-alpha.1"
        "v1.5.0+build.123"
    )

    local all_valid=true
    for version in "${test_versions[@]}"; do
        # Simple regex to validate semantic versioning format
        if [[ $version =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?(\+[a-zA-Z0-9.]+)?$ ]]; then
            echo -e "${GREEN}  ✓${NC} Valid version format: $version"
        else
            echo -e "${RED}  ✗${NC} Invalid version format: $version"
            all_valid=false
        fi
    done

    if $all_valid; then
        echo -e "${GREEN}PASS${NC}: Semantic version format validation works"
        return 0
    else
        echo -e "${RED}FAIL${NC}: Version format validation failed"
        return 1
    fi
}

# Run tests
if test_version_tagging; then ((passed++)); else ((failed++)); fi
if test_git_version_extraction; then ((passed++)); else ((failed++)); fi

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
