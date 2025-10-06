#!/bin/bash
# Test promotion workflows (AC 1-4)
# Tests dev-to-staging and staging-to-production promotion logic

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

# Test 1.1: should_promoteToStaging_when_devTestsPass
test_promote_to_staging_when_dev_tests_pass() {
    log_test "Test 1.1: should_promoteToStaging_when_devTestsPass"

    # Check if promote-to-staging.yml workflow exists
    if [ ! -f ".github/workflows/promote-to-staging.yml" ]; then
        fail_test "promote-to-staging.yml workflow does not exist"
        return 1
    fi

    # Verify workflow has required jobs
    if ! grep -q "validate-dev-environment:" ".github/workflows/promote-to-staging.yml"; then
        fail_test "validate-dev-environment job not found in workflow"
        return 1
    fi

    if ! grep -q "run-validation-suite:" ".github/workflows/promote-to-staging.yml"; then
        fail_test "run-validation-suite job not found in workflow"
        return 1
    fi

    if ! grep -q "deploy-to-staging:" ".github/workflows/promote-to-staging.yml"; then
        fail_test "deploy-to-staging job not found in workflow"
        return 1
    fi

    pass_test "Dev-to-staging promotion workflow is properly configured"
    return 0
}

# Test 1.2: should_blockPromotion_when_devTestsFail
test_block_promotion_when_dev_tests_fail() {
    log_test "Test 1.2: should_blockPromotion_when_devTestsFail"

    # Verify workflow has proper job dependencies
    if ! grep -A 2 "run-validation-suite:" ".github/workflows/promote-to-staging.yml" | grep -q "needs: validate-dev-environment"; then
        fail_test "Validation suite job does not depend on dev environment validation"
        return 1
    fi

    # Check that promote-configuration depends on run-validation-suite
    if ! grep -A 2 "promote-configuration:" ".github/workflows/promote-to-staging.yml" | grep -q "needs: run-validation-suite"; then
        fail_test "Config promotion job does not depend on validation suite"
        return 1
    fi

    # Check that deploy-to-staging depends on promote-configuration
    # This creates a dependency chain: validate → validate-suite → promote-config → deploy
    if ! grep -A 2 "deploy-to-staging:" ".github/workflows/promote-to-staging.yml" | grep -q "needs: promote-configuration"; then
        fail_test "Deploy job does not depend on config promotion"
        return 1
    fi

    pass_test "Promotion blocking configured correctly for failed tests"
    return 0
}

# Test 2.1: should_requireApproval_when_promotingToProduction
test_require_approval_for_production() {
    log_test "Test 2.1: should_requireApproval_when_promotingToProduction"

    # Check if promote-to-production.yml workflow exists
    if [ ! -f ".github/workflows/promote-to-production.yml" ]; then
        fail_test "promote-to-production.yml workflow does not exist"
        return 1
    fi

    # Verify workflow uses manual trigger (workflow_dispatch)
    if ! grep -q "workflow_dispatch:" ".github/workflows/promote-to-production.yml"; then
        fail_test "Production promotion does not require manual trigger"
        return 1
    fi

    # Verify environment protection is configured
    if ! grep -q "environment:" ".github/workflows/promote-to-production.yml"; then
        fail_test "Production environment protection not configured"
        return 1
    fi

    if ! grep -q "name: production" ".github/workflows/promote-to-production.yml"; then
        fail_test "Production environment not specified"
        return 1
    fi

    pass_test "Production promotion requires manual approval"
    return 0
}

# Test 2.2: should_validateStaging_when_productionPromotionRequested
test_validate_staging_before_production() {
    log_test "Test 2.2: should_validateStaging_when_productionPromotionRequested"

    # Verify workflow has pre-deployment validation
    if ! grep -q "pre-deployment-validations:" ".github/workflows/promote-to-production.yml"; then
        fail_test "Pre-deployment validation job not found"
        return 1
    fi

    # Verify staging version check exists
    if ! grep -q "Verify version exists in staging" ".github/workflows/promote-to-production.yml"; then
        fail_test "Staging version verification not implemented"
        return 1
    fi

    pass_test "Staging validation configured for production promotion"
    return 0
}

# Test 3.1: should_promoteEnvironmentConfigs_when_deploying
test_promote_environment_configs() {
    log_test "Test 3.1: should_promoteEnvironmentConfigs_when_deploying"

    # Check if promote-config.sh script exists
    if [ ! -f "scripts/ci/promote-config.sh" ]; then
        fail_test "promote-config.sh script does not exist"
        return 1
    fi

    # Verify script has execute permissions
    if [ ! -x "scripts/ci/promote-config.sh" ]; then
        fail_test "promote-config.sh is not executable"
        return 1
    fi

    # Verify workflow calls the config promotion script
    if ! grep -q "promote-config.sh" ".github/workflows/promote-to-staging.yml"; then
        fail_test "Config promotion not called in staging workflow"
        return 1
    fi

    if ! grep -q "promote-config.sh" ".github/workflows/promote-to-production.yml"; then
        fail_test "Config promotion not called in production workflow"
        return 1
    fi

    pass_test "Environment config promotion is configured"
    return 0
}

# Test 3.2: should_validateConfigCompatibility_when_promoting
test_validate_config_compatibility() {
    log_test "Test 3.2: should_validateConfigCompatibility_when_promoting"

    # Check if validate-config.sh script exists
    if [ ! -f "scripts/ci/validate-config.sh" ]; then
        fail_test "validate-config.sh script does not exist"
        return 1
    fi

    # Verify script validates parameter compatibility
    if ! grep -q "validate_config_compatibility" "scripts/ci/promote-config.sh" 2>/dev/null; then
        fail_test "Config compatibility validation not implemented"
        return 1
    fi

    pass_test "Config compatibility validation is configured"
    return 0
}

# Test 4.1: should_syncFeatureFlags_when_promotingEnvironment
test_sync_feature_flags() {
    log_test "Test 4.1: should_syncFeatureFlags_when_promotingEnvironment"

    # Check if sync-feature-flags.sh script exists
    if [ ! -f "scripts/ci/sync-feature-flags.sh" ]; then
        fail_test "sync-feature-flags.sh script does not exist"
        return 1
    fi

    # Verify script has execute permissions
    if [ ! -x "scripts/ci/sync-feature-flags.sh" ]; then
        fail_test "sync-feature-flags.sh is not executable"
        return 1
    fi

    # Verify workflow calls feature flag sync
    if ! grep -q "sync-feature-flags.sh" ".github/workflows/promote-to-staging.yml"; then
        fail_test "Feature flag sync not called in staging workflow"
        return 1
    fi

    if ! grep -q "sync-feature-flags.sh" ".github/workflows/promote-to-production.yml"; then
        fail_test "Feature flag sync not called in production workflow"
        return 1
    fi

    pass_test "Feature flag synchronization is configured"
    return 0
}

# Test 4.2: should_verifyFeatureFlagState_when_deploymentCompletes
test_verify_feature_flag_state() {
    log_test "Test 4.2: should_verifyFeatureFlagState_when_deploymentCompletes"

    # Verify feature flag verification logic exists
    if ! grep -iq "verify.*feature.*flag\|validate.*feature.*flag" "scripts/ci/sync-feature-flags.sh" 2>/dev/null; then
        fail_test "Feature flag state verification not implemented"
        return 1
    fi

    pass_test "Feature flag state verification is configured"
    return 0
}

# Run all tests
echo "=========================================="
echo "Running Promotion Workflow Tests (AC 1-4)"
echo "=========================================="
echo ""

test_promote_to_staging_when_dev_tests_pass || true
test_block_promotion_when_dev_tests_fail || true
test_require_approval_for_production || true
test_validate_staging_before_production || true
test_promote_environment_configs || true
test_validate_config_compatibility || true
test_sync_feature_flags || true
test_verify_feature_flag_state || true

echo ""
echo "=========================================="
echo "Test Results"
echo "=========================================="
echo -e "${GREEN}Passed: $TESTS_PASSED${NC}"
echo -e "${RED}Failed: $TESTS_FAILED${NC}"
echo ""

if [ $TESTS_FAILED -gt 0 ]; then
    echo -e "${RED}PROMOTION TESTS FAILED${NC}"
    exit 1
else
    echo -e "${GREEN}ALL PROMOTION TESTS PASSED${NC}"
    exit 0
fi
