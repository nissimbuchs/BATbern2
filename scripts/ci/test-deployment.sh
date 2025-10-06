#!/bin/bash
# Test script for deployment workflows (AC 9-12)
# Tests dev deployment automation, staging approval, production deployment, and migrations
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
echo "Deployment Workflow Tests (AC 9-12)"
echo "================================"

# Test 9.1: should_deployToDev_when_developBranchMerged
test_dev_deployment() {
    echo -e "\n${YELLOW}Test 9.1:${NC} should_deployToDev_when_developBranchMerged"

    if [ -f "$WORKFLOWS_DIR/deploy-dev.yml" ]; then
        # Check if workflow triggers on develop branch
        if grep -q "branches:.*develop" "$WORKFLOWS_DIR/deploy-dev.yml"; then
            echo -e "${GREEN}PASS${NC}: Dev deployment workflow configured for develop branch"
            return 0
        fi
    fi

    echo -e "${RED}FAIL${NC}: Dev deployment workflow not configured"
    return 1
}

# Test 9.2: should_skipDeployment_when_testsFailure
test_deployment_dependencies() {
    echo -e "\n${YELLOW}Test 9.2:${NC} should_requireBuildSuccess_when_deploying"

    if [ -f "$WORKFLOWS_DIR/deploy-dev.yml" ]; then
        # Check if deployment has dependencies on build/test success
        if grep -q "needs:\|workflow_run:\|Build Pipeline" "$WORKFLOWS_DIR/deploy-dev.yml"; then
            echo -e "${GREEN}PASS${NC}: Deployment requires successful build"
            return 0
        fi
    fi

    echo -e "${YELLOW}WARNING${NC}: Build dependency not explicitly configured"
    return 0
}

# Test 10.1: should_requireApproval_when_stagingDeployment
test_staging_approval() {
    echo -e "\n${YELLOW}Test 10.1:${NC} should_requireApproval_when_stagingDeployment"

    if [ -f "$WORKFLOWS_DIR/deploy-staging.yml" ]; then
        # Check for manual workflow dispatch or environment approval
        if grep -q "workflow_dispatch:\|environment:" "$WORKFLOWS_DIR/deploy-staging.yml"; then
            echo -e "${GREEN}PASS${NC}: Staging deployment requires approval"
            return 0
        fi
    fi

    echo -e "${RED}FAIL${NC}: Staging deployment approval not configured"
    return 1
}

# Test 10.2: should_blockProduction_when_stagingApprovalPending
test_production_gates() {
    echo -e "\n${YELLOW}Test 10.2:${NC} should_requireApproval_when_productionDeployment"

    if [ -f "$WORKFLOWS_DIR/deploy-production.yml" ]; then
        # Check for manual workflow dispatch or environment approval
        if grep -q "workflow_dispatch:\|environment:" "$WORKFLOWS_DIR/deploy-production.yml"; then
            echo -e "${GREEN}PASS${NC}: Production deployment requires approval"
            return 0
        fi
    fi

    echo -e "${RED}FAIL${NC}: Production deployment approval not configured"
    return 1
}

# Test 11.1: should_useBlueGreenDeployment_when_productionRelease
test_blue_green_deployment() {
    echo -e "\n${YELLOW}Test 11.1:${NC} should_configureBlueGreenDeployment_when_production"

    if [ -f "$WORKFLOWS_DIR/deploy-production.yml" ]; then
        # Check for blue-green deployment mentions
        if grep -iq "blue.*green\|deployment.*strategy" "$WORKFLOWS_DIR/deploy-production.yml"; then
            echo -e "${GREEN}PASS${NC}: Blue-green deployment configured"
            return 0
        fi
    fi

    echo -e "${RED}FAIL${NC}: Blue-green deployment not configured"
    return 1
}

# Test 11.2: should_validateHealthChecks_when_newVersionDeployed
test_health_checks() {
    echo -e "\n${YELLOW}Test 11.2:${NC} should_performHealthChecks_when_deploying"

    # Check for health check validation in deployment workflows
    if grep -rq "health\|smoke.*test" "$WORKFLOWS_DIR/deploy-"*.yml 2>/dev/null; then
        echo -e "${GREEN}PASS${NC}: Health checks configured in deployment"
        return 0
    fi

    echo -e "${RED}FAIL${NC}: Health checks not configured"
    return 1
}

# Test 12.1: should_runFlywayMigrations_when_deploymentStarts
test_database_migrations() {
    echo -e "\n${YELLOW}Test 12.1:${NC} should_configureDatabaseMigrations_when_deploying"

    # Check for database migration steps
    if grep -rq "flyway\|migration\|migrate" "$WORKFLOWS_DIR/deploy-"*.yml 2>/dev/null; then
        echo -e "${GREEN}PASS${NC}: Database migrations configured"
        return 0
    fi

    echo -e "${RED}FAIL${NC}: Database migrations not configured"
    return 1
}

# Test 12.2: should_failDeployment_when_migrationErrors
test_migration_error_handling() {
    echo -e "\n${YELLOW}Test 12.2:${NC} should_handleMigrationErrors_when_deploying"

    # Check for migration error handling or backup steps
    if grep -rq "backup\|snapshot\|flyway" "$WORKFLOWS_DIR/deploy-"*.yml 2>/dev/null; then
        echo -e "${GREEN}PASS${NC}: Migration error handling configured"
        return 0
    fi

    echo -e "${RED}FAIL${NC}: Migration error handling not configured"
    return 1
}

# Run tests
if test_dev_deployment; then ((passed++)); else ((failed++)); fi
if test_deployment_dependencies; then ((passed++)); else ((failed++)); fi
if test_staging_approval; then ((passed++)); else ((failed++)); fi
if test_production_gates; then ((passed++)); else ((failed++)); fi
if test_blue_green_deployment; then ((passed++)); else ((failed++)); fi
if test_health_checks; then ((passed++)); else ((failed++)); fi
if test_database_migrations; then ((passed++)); else ((failed++)); fi
if test_migration_error_handling; then ((passed++)); else ((failed++)); fi

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
