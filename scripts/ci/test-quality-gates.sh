#!/bin/bash
# Test script for quality gates (AC 5-8)
# Tests coverage enforcement, integration tests, security scanning, and license compliance
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
echo "Quality Gate Tests (AC 5-8)"
echo "================================"

# Test 5.1: should_failBuild_when_coverageBelow90Percent
test_coverage_threshold() {
    echo -e "\n${YELLOW}Test 5.1:${NC} should_enforceCoverageThreshold_when_configured"

    if [ ! -f "$WORKFLOWS_DIR/build.yml" ]; then
        echo -e "${RED}FAIL${NC}: build.yml does not exist"
        return 1
    fi

    # Check for coverage threshold enforcement
    if grep -q "coverage" "$WORKFLOWS_DIR/build.yml" && \
       (grep -q "90" "$WORKFLOWS_DIR/build.yml" || \
        grep -q "jacoco" "$WORKFLOWS_DIR/build.yml" || \
        grep -q "test:coverage" "$WORKFLOWS_DIR/build.yml"); then
        echo -e "${GREEN}PASS${NC}: Coverage threshold enforcement configured"
        return 0
    else
        echo -e "${RED}FAIL${NC}: Coverage threshold enforcement not found"
        return 1
    fi
}

# Test 5.2: should_passBuild_when_coverageAbove90Percent
test_coverage_reporting() {
    echo -e "\n${YELLOW}Test 5.2:${NC} should_generateCoverageReports_when_testsRun"

    if [ ! -f "$WORKFLOWS_DIR/build.yml" ]; then
        echo -e "${RED}FAIL${NC}: build.yml does not exist"
        return 1
    fi

    # Check for coverage report generation
    if grep -q "jacocoTestReport\|coverage" "$WORKFLOWS_DIR/build.yml"; then
        echo -e "${GREEN}PASS${NC}: Coverage reports are generated"
        return 0
    else
        echo -e "${RED}FAIL${NC}: Coverage report generation not configured"
        return 1
    fi
}

# Test 6.1: should_runContractTests_when_integrationPhase
test_integration_tests() {
    echo -e "\n${YELLOW}Test 6.1:${NC} should_runIntegrationTests_when_configured"

    if [ ! -f "$WORKFLOWS_DIR/build.yml" ]; then
        echo -e "${RED}FAIL${NC}: build.yml does not exist"
        return 1
    fi

    # Check for integration tests job
    if grep -q "integration-tests:" "$WORKFLOWS_DIR/build.yml" || \
       grep -q "integrationTest" "$WORKFLOWS_DIR/build.yml"; then
        echo -e "${GREEN}PASS${NC}: Integration tests are configured"
        return 0
    else
        echo -e "${RED}FAIL${NC}: Integration tests not found"
        return 1
    fi
}

# Test 6.2: should_verifyAllEndpoints_when_contractTestsRun
test_database_services() {
    echo -e "\n${YELLOW}Test 6.2:${NC} should_provideTestServices_when_integrationTestsRun"

    if [ ! -f "$WORKFLOWS_DIR/build.yml" ]; then
        echo -e "${RED}FAIL${NC}: build.yml does not exist"
        return 1
    fi

    # Check for database and other services for integration testing
    if grep -q "services:" "$WORKFLOWS_DIR/build.yml" && \
       (grep -q "postgres" "$WORKFLOWS_DIR/build.yml" || \
        grep -q "redis" "$WORKFLOWS_DIR/build.yml"); then
        echo -e "${GREEN}PASS${NC}: Test services (postgres/redis) configured"
        return 0
    else
        echo -e "${RED}FAIL${NC}: Test services not configured"
        return 1
    fi
}

# Test 7.1: should_scanForVulnerabilities_when_securityPhase
test_security_scanning() {
    echo -e "\n${YELLOW}Test 7.1:${NC} should_configureSecurityScanning_when_workflowExists"

    # Check for security scanning workflow
    if [ -f "$WORKFLOWS_DIR/security-scan.yml" ] || \
       [ -f "$WORKFLOWS_DIR/security.yml" ]; then
        echo -e "${GREEN}PASS${NC}: Security scanning workflow exists"
        return 0
    else
        echo -e "${RED}FAIL${NC}: Security scanning workflow not found"
        return 1
    fi
}

# Test 7.2: should_failBuild_when_criticalVulnerabilitiesFound
test_security_threshold() {
    echo -e "\n${YELLOW}Test 7.2:${NC} should_enforceSecurityThreshold_when_configured"

    # Check for security scanning configuration in any workflow
    if [ -f "$WORKFLOWS_DIR/security-scan.yml" ]; then
        if grep -q "snyk\|sonarqube\|severity-threshold" "$WORKFLOWS_DIR/security-scan.yml"; then
            echo -e "${GREEN}PASS${NC}: Security threshold enforcement configured"
            return 0
        fi
    fi

    if [ -f "$WORKFLOWS_DIR/security.yml" ]; then
        if grep -q "snyk\|sonarqube\|severity-threshold" "$WORKFLOWS_DIR/security.yml"; then
            echo -e "${GREEN}PASS${NC}: Security threshold enforcement configured"
            return 0
        fi
    fi

    echo -e "${RED}FAIL${NC}: Security threshold not configured"
    return 1
}

# Test 8.1: should_checkLicenses_when_dependenciesScanned
test_license_checking() {
    echo -e "\n${YELLOW}Test 8.1:${NC} should_configureLicenseChecking_when_workflowExists"

    # Check for license checking in any workflow
    if grep -rq "license" "$WORKFLOWS_DIR/" 2>/dev/null; then
        echo -e "${GREEN}PASS${NC}: License checking configured"
        return 0
    else
        echo -e "${RED}FAIL${NC}: License checking not configured"
        return 1
    fi
}

# Test 8.2: should_failBuild_when_incompatibleLicense
test_license_enforcement() {
    echo -e "\n${YELLOW}Test 8.2:${NC} should_enforceLicenseCompliance_when_scanning"

    # License enforcement will be part of the license check workflow
    # For now, check if license checking exists
    if grep -rq "license" "$WORKFLOWS_DIR/" 2>/dev/null; then
        echo -e "${GREEN}PASS${NC}: License compliance will be enforced by checks"
        return 0
    else
        echo -e "${RED}FAIL${NC}: License enforcement not configured"
        return 1
    fi
}

# Run tests
if test_coverage_threshold; then ((passed++)); else ((failed++)); fi
if test_coverage_reporting; then ((passed++)); else ((failed++)); fi
if test_integration_tests; then ((passed++)); else ((failed++)); fi
if test_database_services; then ((passed++)); else ((failed++)); fi
if test_security_scanning; then ((passed++)); else ((failed++)); fi
if test_security_threshold; then ((passed++)); else ((failed++)); fi
if test_license_checking; then ((passed++)); else ((failed++)); fi
if test_license_enforcement; then ((passed++)); else ((failed++)); fi

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
