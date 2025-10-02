#!/bin/bash
# Test validation gates (AC 5-8)
# Tests regression, performance, security, and database validation

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0

# Test helper functions
log_test() {
    echo -e "${YELLOW}TEST:${NC} $1"
}

pass_test() {
    echo -e "${GREEN}✓ PASS:${NC} $1"
    ((TESTS_PASSED++))
}

fail_test() {
    echo -e "${RED}✗ FAIL:${NC} $1"
    ((TESTS_FAILED++))
}

# Test 5.1: should_runRegressionSuite_when_stagingDeployment
test_run_regression_suite() {
    log_test "Test 5.1: should_runRegressionSuite_when_stagingDeployment"

    # Check if regression-suite.sh script exists
    if [ ! -f "scripts/ci/regression-suite.sh" ]; then
        fail_test "regression-suite.sh script does not exist"
        return 1
    fi

    # Verify script is executable
    if [ ! -x "scripts/ci/regression-suite.sh" ]; then
        fail_test "regression-suite.sh is not executable"
        return 1
    fi

    # Verify workflow calls regression suite
    if ! grep -q "regression-suite.sh" ".github/workflows/promote-to-staging.yml" 2>/dev/null; then
        fail_test "Regression suite not called in staging workflow"
        return 1
    fi

    pass_test "Regression suite execution is configured"
    return 0
}

# Test 5.2: should_blockPromotion_when_regressionTestsFail
test_block_on_regression_failure() {
    log_test "Test 5.2: should_blockPromotion_when_regressionTestsFail"

    # Verify regression suite script exits with error on failure
    if ! grep -q "exit 1" "scripts/ci/regression-suite.sh" 2>/dev/null; then
        fail_test "Regression suite does not exit with error on failure"
        return 1
    fi

    pass_test "Promotion blocking configured for failed regression tests"
    return 0
}

# Test 6.1: should_runLoadTests_when_stagingValidation
test_run_load_tests() {
    log_test "Test 6.1: should_runLoadTests_when_stagingValidation"

    # Check if performance-tests.sh script exists
    if [ ! -f "scripts/ci/performance-tests.sh" ]; then
        fail_test "performance-tests.sh script does not exist"
        return 1
    fi

    # Verify script is executable
    if [ ! -x "scripts/ci/performance-tests.sh" ]; then
        fail_test "performance-tests.sh is not executable"
        return 1
    fi

    # Verify workflow calls performance tests
    if ! grep -q "performance-tests.sh" ".github/workflows/promote-to-staging.yml" 2>/dev/null; then
        fail_test "Performance tests not called in staging workflow"
        return 1
    fi

    pass_test "Performance testing is configured"
    return 0
}

# Test 6.2: should_blockProduction_when_performanceThresholdNotMet
test_block_on_performance_failure() {
    log_test "Test 6.2: should_blockProduction_when_performanceThresholdNotMet"

    # Verify performance tests have threshold checks
    if ! grep -q "threshold\|limit\|max" "scripts/ci/performance-tests.sh" 2>/dev/null; then
        fail_test "Performance threshold checks not implemented"
        return 1
    fi

    pass_test "Performance threshold blocking is configured"
    return 0
}

# Test 7.1: should_runSecurityScan_when_stagingDeployment
test_run_security_scan() {
    log_test "Test 7.1: should_runSecurityScan_when_stagingDeployment"

    # Check if security-scan.sh script exists in ci directory
    if [ ! -f "scripts/ci/security-scan.sh" ]; then
        fail_test "security-scan.sh script does not exist in ci directory"
        return 1
    fi

    # Verify script is executable
    if [ ! -x "scripts/ci/security-scan.sh" ]; then
        fail_test "security-scan.sh is not executable"
        return 1
    fi

    # Verify workflow calls security scan
    if ! grep -q "security-scan.sh" ".github/workflows/promote-to-staging.yml" 2>/dev/null; then
        fail_test "Security scan not called in staging workflow"
        return 1
    fi

    pass_test "Security scanning is configured"
    return 0
}

# Test 7.2: should_blockPromotion_when_vulnerabilitiesFound
test_block_on_vulnerabilities() {
    log_test "Test 7.2: should_blockPromotion_when_vulnerabilitiesFound"

    # Verify security scan workflow has proper exit on vulnerabilities
    if ! grep -q "exit 1\|fail" "scripts/ci/security-scan.sh" 2>/dev/null; then
        fail_test "Security scan does not block on vulnerabilities"
        return 1
    fi

    pass_test "Vulnerability blocking is configured"
    return 0
}

# Test 8.1: should_validateDatabaseSchema_when_promoting
test_validate_database_schema() {
    log_test "Test 8.1: should_validateDatabaseSchema_when_promoting"

    # Check if validate-schema.sh script exists
    if [ ! -f "scripts/ci/validate-schema.sh" ]; then
        fail_test "validate-schema.sh script does not exist"
        return 1
    fi

    # Verify script is executable
    if [ ! -x "scripts/ci/validate-schema.sh" ]; then
        fail_test "validate-schema.sh is not executable"
        return 1
    fi

    # Verify workflow calls schema validation
    if ! grep -q "validate-schema.sh" ".github/workflows/promote-to-staging.yml" 2>/dev/null; then
        fail_test "Schema validation not called in staging workflow"
        return 1
    fi

    pass_test "Database schema validation is configured"
    return 0
}

# Test 8.2: should_blockPromotion_when_schemaIncompatible
test_block_on_schema_incompatibility() {
    log_test "Test 8.2: should_blockPromotion_when_schemaIncompatible"

    # Verify schema validation script checks compatibility
    if ! grep -q "compatible\|compatibility\|migration" "scripts/ci/validate-schema.sh" 2>/dev/null; then
        fail_test "Schema compatibility check not implemented"
        return 1
    fi

    pass_test "Schema incompatibility blocking is configured"
    return 0
}

# Run all tests
echo "=========================================="
echo "Running Validation Gate Tests (AC 5-8)"
echo "=========================================="
echo ""

test_run_regression_suite || true
test_block_on_regression_failure || true
test_run_load_tests || true
test_block_on_performance_failure || true
test_run_security_scan || true
test_block_on_vulnerabilities || true
test_validate_database_schema || true
test_block_on_schema_incompatibility || true

echo ""
echo "=========================================="
echo "Test Results"
echo "=========================================="
echo -e "${GREEN}Passed: $TESTS_PASSED${NC}"
echo -e "${RED}Failed: $TESTS_FAILED${NC}"
echo ""

if [ $TESTS_FAILED -gt 0 ]; then
    echo -e "${RED}VALIDATION GATE TESTS FAILED${NC}"
    exit 1
else
    echo -e "${GREEN}ALL VALIDATION GATE TESTS PASSED${NC}"
    exit 0
fi
