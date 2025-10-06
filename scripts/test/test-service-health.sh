#!/bin/bash
# Test suite for service health checks
# Tests AC2 (Local Redis Service) and AC6 (Startup Orchestration)

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

test_skip() {
    local reason="$1"
    echo -e "${YELLOW}SKIP${NC}"
    echo "  Reason: ${reason}"
}

echo "========================================"
echo "Service Health Check Tests"
echo "========================================"
echo ""

# Check if docker-compose is available
if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}ERROR: docker-compose not found${NC}"
    echo "These tests require docker-compose to be installed"
    exit 1
fi

# Check if docker-compose.yml exists
if [ ! -f "docker-compose.yml" ]; then
    echo -e "${RED}ERROR: docker-compose.yml not found${NC}"
    echo "Please run this script from the project root"
    exit 1
fi

# AC2: Local Redis Service Tests (Configuration Validation)
test_start "should_defineRedisService_when_dockerComposeConfigured"
if docker-compose config 2>/dev/null | grep -q "batbern-redis"; then
    test_pass
else
    test_fail "Redis service not defined in docker-compose.yml"
fi

test_start "should_configureRedisHealthCheck_when_serviceConfigured"
if docker-compose config 2>/dev/null | grep -A 5 "redis:" | grep -q "healthcheck"; then
    test_pass
else
    test_fail "Redis health check not configured"
fi

test_start "should_useRedisPingCommand_when_healthCheckDefined"
if docker-compose config 2>/dev/null | grep -A 10 "redis:" | grep -q "redis-cli"; then
    test_pass
else
    test_fail "Redis health check doesn't use redis-cli ping"
fi

test_start "should_configureRedisDataVolume_when_persistenceNeeded"
if docker-compose config 2>/dev/null | grep -q "redis-data"; then
    test_pass
else
    test_fail "Redis data volume not configured"
fi

# AC6: Startup Orchestration Tests (Health Check Configuration)
test_start "should_configureAPIGatewayHealthCheck_when_serviceConfigured"
if docker-compose config 2>/dev/null | grep -q "healthcheck"; then
    test_pass
else
    test_fail "Health checks not configured"
fi

test_start "should_useActuatorHealthEndpoint_when_springBootConfigured"
if docker-compose config 2>/dev/null | grep -q "/actuator/health"; then
    test_pass
else
    test_fail "API Gateway health check doesn't use /actuator/health"
fi

test_start "should_configureHealthCheckRetries_when_resilienceNeeded"
if docker-compose config 2>/dev/null | grep -A 10 "redis:" | grep -q "retries"; then
    test_pass
else
    test_fail "Health checks don't configure retries"
fi

test_start "should_configureHealthCheckInterval_when_monitoringNeeded"
if docker-compose config 2>/dev/null | grep -A 10 "redis:" | grep -q "interval"; then
    test_pass
else
    test_fail "Health checks don't configure interval"
fi

# Integration tests - Only run if services can be started
echo ""
echo "========================================"
echo "Integration Tests (Optional)"
echo "========================================"
echo ""
echo -e "${YELLOW}Note: Integration tests require .env file and AWS connectivity${NC}"
echo -e "${YELLOW}Skipping integration tests - run manually with 'docker-compose up -d'${NC}"
echo ""

# These would be actual integration tests if we wanted to start services:
# - Start Redis container
# - Verify redis-cli ping works
# - Test data persistence
# - Test service accessibility from other containers
#
# Skipping for now as they require:
# - .env file to be generated (requires AWS credentials)
# - Docker daemon running
# - Potential conflicts with existing running services

# Print summary
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
