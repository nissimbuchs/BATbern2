#!/bin/bash
# Test suite for docker-compose startup and orchestration
# Tests AC6 (Startup Orchestration) and AC7 (Single Command Startup)

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

echo "========================================"
echo "Docker Compose Startup Tests"
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

# AC7: Single Command Startup Tests
test_start "should_validateDockerComposeConfig_when_syntaxChecked"
if docker-compose config > /dev/null 2>&1; then
    test_pass
else
    test_fail "docker-compose.yml has syntax errors"
fi

test_start "should_defineAllServices_when_configParsed"
SERVICES=$(docker-compose config --services 2>/dev/null | wc -l | tr -d ' ')
if [ "$SERVICES" -ge 3 ]; then
    test_pass
else
    test_fail "Expected at least 3 services, found: $SERVICES"
fi

test_start "should_defineRedisService_when_servicesListed"
if docker-compose config --services 2>/dev/null | grep -q "redis"; then
    test_pass
else
    test_fail "Redis service not defined"
fi

test_start "should_defineAPIGatewayService_when_servicesListed"
if docker-compose config --services 2>/dev/null | grep -q "api-gateway"; then
    test_pass
else
    test_fail "API Gateway service not defined"
fi

test_start "should_defineWebFrontendService_when_servicesListed"
if docker-compose config --services 2>/dev/null | grep -q "web-frontend"; then
    test_pass
else
    test_fail "Web Frontend service not defined"
fi

# AC6: Startup Orchestration Tests
test_start "should_defineServiceDependencies_when_orchestrationNeeded"
if docker-compose config 2>/dev/null | grep -q "depends_on"; then
    test_pass
else
    test_fail "No service dependencies defined"
fi

test_start "should_configureHealthChecks_when_dependenciesUsed"
if docker-compose config 2>/dev/null | grep -q "condition: service_healthy"; then
    test_pass
else
    test_fail "No health check conditions defined"
fi

test_start "should_defineVolumes_when_persistenceNeeded"
if docker-compose config --volumes 2>/dev/null | grep -q "redis-data"; then
    test_pass
else
    test_fail "Redis data volume not defined"
fi

test_start "should_defineGradleCache_when_buildOptimizationNeeded"
if docker-compose config --volumes 2>/dev/null | grep -q "gradle-cache"; then
    test_pass
else
    test_fail "Gradle cache volume not defined"
fi

test_start "should_defineNetwork_when_serviceDiscoveryNeeded"
if docker-compose config 2>/dev/null | grep -q "batbern-network"; then
    test_pass
else
    test_fail "batbern-network not defined"
fi

test_start "should_useBridgeDriver_when_networkConfigured"
if docker-compose config 2>/dev/null | grep -A 2 "batbern-network" | grep -q "driver: bridge"; then
    test_pass
else
    test_fail "Network doesn't use bridge driver"
fi

test_start "should_exposeRedisPort_when_localAccessNeeded"
if docker-compose config 2>/dev/null | grep -q "6379"; then
    test_pass
else
    test_fail "Redis port 6379 not exposed"
fi

test_start "should_exposeAPIGatewayPort_when_externalAccessNeeded"
if docker-compose config 2>/dev/null | grep -q "8080"; then
    test_pass
else
    test_fail "API Gateway port 8080 not exposed"
fi

test_start "should_exposeFrontendPort_when_browserAccessNeeded"
if docker-compose config 2>/dev/null | grep -q "3000"; then
    test_pass
else
    test_fail "Frontend port 3000 not exposed"
fi

# Dockerfile.dev existence tests
test_start "should_haveAPIGatewayDockerfile_when_hotReloadNeeded"
if [ -f "api-gateway/Dockerfile.dev" ]; then
    test_pass
else
    test_fail "api-gateway/Dockerfile.dev not found"
fi

test_start "should_haveFrontendDockerfile_when_hotReloadNeeded"
if [ -f "web-frontend/Dockerfile.dev" ]; then
    test_pass
else
    test_fail "web-frontend/Dockerfile.dev not found"
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
