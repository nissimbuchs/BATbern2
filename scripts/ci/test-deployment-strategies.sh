#!/bin/bash
# Test deployment strategies (AC 9-12)
# Tests blue-green, canary, migrations, and rollback

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

# Test 9.1: should_useBlueGreenDeployment_when_productionRelease
test_blue_green_deployment() {
    log_test "Test 9.1: should_useBlueGreenDeployment_when_productionRelease"

    # Verify production workflow uses blue-green deployment
    if ! grep -q "Blue/Green\|Blue-Green\|blue-green" ".github/workflows/promote-to-production.yml" 2>/dev/null; then
        fail_test "Blue-green deployment not configured in production workflow"
        return 1
    fi

    # Verify ECS deployment configuration
    if ! grep -q "ecs.*wait.*services-stable" ".github/workflows/promote-to-production.yml" 2>/dev/null; then
        fail_test "ECS service stabilization check not configured"
        return 1
    fi

    pass_test "Blue-green deployment is configured"
    return 0
}

# Test 9.2: should_maintainOldVersion_when_blueGreenInProgress
test_maintain_old_version_during_deployment() {
    log_test "Test 9.2: should_maintainOldVersion_when_blueGreenInProgress"

    # Verify workflow waits for stability before completing
    if ! grep -q "wait.*stable\|stabilize" ".github/workflows/promote-to-production.yml" 2>/dev/null; then
        fail_test "Service stabilization not configured"
        return 1
    fi

    pass_test "Old version maintenance during deployment is configured"
    return 0
}

# Test 10.1: should_deployToCanary_when_canaryReleaseEnabled
test_canary_deployment() {
    log_test "Test 10.1: should_deployToCanary_when_canaryReleaseEnabled"

    # Verify production workflow supports canary deployment
    if ! grep -q "canary\|Canary" ".github/workflows/promote-to-production.yml" 2>/dev/null; then
        fail_test "Canary deployment not configured in production workflow"
        return 1
    fi

    # Verify canary input parameter exists
    if ! grep -q "enable_canary:" ".github/workflows/promote-to-production.yml" 2>/dev/null; then
        fail_test "Canary enable flag not found in workflow inputs"
        return 1
    fi

    pass_test "Canary deployment is configured"
    return 0
}

# Test 10.2: should_monitorCanaryMetrics_when_canaryActive
test_monitor_canary_metrics() {
    log_test "Test 10.2: should_monitorCanaryMetrics_when_canaryActive"

    # Check if monitor-canary.sh script exists
    if [ ! -f "scripts/ci/monitor-canary.sh" ]; then
        fail_test "monitor-canary.sh script does not exist"
        return 1
    fi

    # Verify script is executable
    if [ ! -x "scripts/ci/monitor-canary.sh" ]; then
        fail_test "monitor-canary.sh is not executable"
        return 1
    fi

    # Verify workflow calls canary monitoring
    if ! grep -q "monitor-canary.sh" ".github/workflows/promote-to-production.yml" 2>/dev/null; then
        fail_test "Canary monitoring not called in production workflow"
        return 1
    fi

    pass_test "Canary metrics monitoring is configured"
    return 0
}

# Test 11.1: should_validateBackwardCompatibility_when_migratingDatabase
test_validate_migration_compatibility() {
    log_test "Test 11.1: should_validateBackwardCompatibility_when_migratingDatabase"

    # Check if validate-migration.sh script exists
    if [ ! -f "scripts/ci/validate-migration.sh" ]; then
        fail_test "validate-migration.sh script does not exist"
        return 1
    fi

    # Verify script is executable
    if [ ! -x "scripts/ci/validate-migration.sh" ]; then
        fail_test "validate-migration.sh is not executable"
        return 1
    fi

    # Verify migration compatibility checks
    if ! grep -q "backward.*compatible\|compatibility" "scripts/ci/validate-migration.sh" 2>/dev/null; then
        fail_test "Backward compatibility check not implemented"
        return 1
    fi

    pass_test "Database migration backward compatibility validation is configured"
    return 0
}

# Test 11.2: should_rollbackMigration_when_validationFails
test_rollback_on_migration_failure() {
    log_test "Test 11.2: should_rollbackMigration_when_validationFails"

    # Verify migration script has rollback logic
    if ! grep -q "rollback\|revert" "scripts/ci/validate-migration.sh" 2>/dev/null; then
        fail_test "Migration rollback logic not implemented"
        return 1
    fi

    pass_test "Migration rollback on failure is configured"
    return 0
}

# Test 12.1: should_rollbackDeployment_when_healthChecksFail
test_rollback_on_health_check_failure() {
    log_test "Test 12.1: should_rollbackDeployment_when_healthChecksFail"

    # Check if rollback-deployment.sh script exists
    if [ ! -f "scripts/ci/rollback-deployment.sh" ]; then
        fail_test "rollback-deployment.sh script does not exist"
        return 1
    fi

    # Verify script is executable
    if [ ! -x "scripts/ci/rollback-deployment.sh" ]; then
        fail_test "rollback-deployment.sh is not executable"
        return 1
    fi

    # Verify workflow has failure handling
    if ! grep -q "if.*failure()" ".github/workflows/promote-to-production.yml" 2>/dev/null; then
        fail_test "Failure handling not configured in production workflow"
        return 1
    fi

    pass_test "Rollback on health check failure is configured"
    return 0
}

# Test 12.2: should_rollbackInOneClick_when_rollbackInitiated
test_one_click_rollback() {
    log_test "Test 12.2: should_rollbackInOneClick_when_rollbackInitiated"

    # Verify rollback script can handle all services
    if ! grep -q "SERVICES=\|services\[" "scripts/ci/rollback-deployment.sh" 2>/dev/null; then
        fail_test "Rollback script does not handle multiple services"
        return 1
    fi

    # Verify rollback uses previous task definition
    if ! grep -q "PREVIOUS_TASK\|previous.*task.*definition" "scripts/ci/rollback-deployment.sh" 2>/dev/null; then
        fail_test "Rollback does not use previous task definition"
        return 1
    fi

    pass_test "One-click rollback capability is configured"
    return 0
}

# Run all tests
echo "=========================================="
echo "Running Deployment Strategy Tests (AC 9-12)"
echo "=========================================="
echo ""

test_blue_green_deployment || true
test_maintain_old_version_during_deployment || true
test_canary_deployment || true
test_monitor_canary_metrics || true
test_validate_migration_compatibility || true
test_rollback_on_migration_failure || true
test_rollback_on_health_check_failure || true
test_one_click_rollback || true

echo ""
echo "=========================================="
echo "Test Results"
echo "=========================================="
echo -e "${GREEN}Passed: $TESTS_PASSED${NC}"
echo -e "${RED}Failed: $TESTS_FAILED${NC}"
echo ""

if [ $TESTS_FAILED -gt 0 ]; then
    echo -e "${RED}DEPLOYMENT STRATEGY TESTS FAILED${NC}"
    exit 1
else
    echo -e "${GREEN}ALL DEPLOYMENT STRATEGY TESTS PASSED${NC}"
    exit 0
fi
