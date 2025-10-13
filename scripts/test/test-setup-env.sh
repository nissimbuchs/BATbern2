#!/bin/bash
# Test suite for sync-backend-config.sh script
# Tests AC3 (Auto-Generated Environment) and AC9 (AWS Credentials)

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counters
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

# Test helper functions
test_start() {
    local test_name="$1"
    TESTS_RUN=$((TESTS_RUN + 1))
    echo -n "Test ${TESTS_RUN}: ${test_name}... "
}

test_pass() {
    TESTS_PASSED=$((TESTS_PASSED + 1))
    echo -e "${GREEN}PASS${NC}"
}

test_fail() {
    local reason="$1"
    TESTS_FAILED=$((TESTS_FAILED + 1))
    echo -e "${RED}FAIL${NC}"
    echo "  Reason: ${reason}"
}

# AC3: Auto-Generated Environment Tests
test_start "should_existAndBeExecutable_when_scriptPresent"
if [ -x "./scripts/config/sync-backend-config.sh" ]; then
    test_pass
else
    test_fail "sync-backend-config.sh not found or not executable"
fi

test_start "should_validateAWSCLI_when_prerequisitesChecked"
if grep -q "command -v aws" "./scripts/config/sync-backend-config.sh"; then
    test_pass
else
    test_fail "sync-backend-config.sh doesn't check for AWS CLI"
fi

test_start "should_validateJQ_when_prerequisitesChecked"
if grep -q "command -v jq" "./scripts/config/sync-backend-config.sh"; then
    test_pass
else
    test_fail "sync-backend-config.sh doesn't check for jq"
fi

test_start "should_fetchDatabaseEndpoint_when_setupEnvRuns"
if grep -q "DatabaseEndpoint" "./scripts/config/sync-backend-config.sh"; then
    test_pass
else
    test_fail "sync-backend-config.sh doesn't fetch DatabaseEndpoint"
fi

test_start "should_retrieveCredentialsFromSecretsManager_when_setupEnvRuns"
if grep -q "secretsmanager get-secret-value" "./scripts/config/sync-backend-config.sh"; then
    test_pass
else
    test_fail "sync-backend-config.sh doesn't retrieve credentials from Secrets Manager"
fi

test_start "should_fetchCognitoConfig_when_setupEnvRuns"
if grep -q "UserPoolId" "./scripts/config/sync-backend-config.sh"; then
    test_pass
else
    test_fail "sync-backend-config.sh doesn't fetch Cognito configuration"
fi

test_start "should_generateEnvFile_when_allOutputsRetrieved"
if grep -q "cat > \${ENV_FILE}" "./scripts/config/sync-backend-config.sh" || grep -q "cat > .env" "./scripts/config/sync-backend-config.sh"; then
    test_pass
else
    test_fail "sync-backend-config.sh doesn't generate .env file"
fi

# AC9: AWS Credentials Tests
test_start "should_validateAWSCredentials_when_setupEnvStarts"
if grep -q "aws sts get-caller-identity" "./scripts/config/sync-backend-config.sh"; then
    test_pass
else
    test_fail "sync-backend-config.sh doesn't validate AWS credentials"
fi

test_start "should_failWithError_when_stackNotDeployed"
if grep -q "Could not fetch database endpoint" "./scripts/config/sync-backend-config.sh" && \
   grep -q "exit 1" "./scripts/config/sync-backend-config.sh"; then
    test_pass
else
    test_fail "sync-backend-config.sh doesn't properly handle missing stack"
fi

test_start "should_useAWSProfile_when_profileConfigured"
if grep -q "AWS_PROFILE" "./scripts/config/sync-backend-config.sh"; then
    test_pass
else
    test_fail "sync-backend-config.sh doesn't support AWS_PROFILE"
fi

test_start "should_useAWSRegion_when_regionConfigured"
if grep -q "AWS_REGION" "./scripts/config/sync-backend-config.sh"; then
    test_pass
else
    test_fail "sync-backend-config.sh doesn't support AWS_REGION"
fi

test_start "should_extractDatabaseCredentials_when_secretRetrieved"
if grep -q "jq -r '.username'" "./scripts/config/sync-backend-config.sh" && \
   grep -q "jq -r '.password'" "./scripts/config/sync-backend-config.sh"; then
    test_pass
else
    test_fail "sync-backend-config.sh doesn't extract username/password from secret"
fi

test_start "should_generateDatabaseURL_when_credentialsAvailable"
if grep -q "DATABASE_URL=" "./scripts/config/sync-backend-config.sh"; then
    test_pass
else
    test_fail "sync-backend-config.sh doesn't generate DATABASE_URL"
fi

# Print summary
echo ""
echo "========================================"
echo "Test Summary"
echo "========================================"
echo "Tests run:    ${TESTS_RUN}"
echo -e "Tests passed: ${GREEN}${TESTS_PASSED}${NC}"
if [ ${TESTS_FAILED} -gt 0 ]; then
    echo -e "Tests failed: ${RED}${TESTS_FAILED}${NC}"
    exit 1
else
    echo -e "Tests failed: ${TESTS_FAILED}"
    echo ""
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
fi
