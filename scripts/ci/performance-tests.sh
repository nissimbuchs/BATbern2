#!/bin/bash
# Performance testing script using K6
# Tests load and performance thresholds before production deployment
set -e

TARGET_URL=$1

if [ -z "$TARGET_URL" ]; then
    echo "Usage: $0 <target_url>"
    echo "Example: $0 https://api-staging.batbern.ch"
    exit 1
fi

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}Performance Tests${NC}"
echo -e "${BLUE}================================${NC}"
echo ""
echo "Target: $TARGET_URL"
echo ""

# Check if K6 is installed
if ! command -v k6 &> /dev/null; then
    echo -e "${YELLOW}⚠ WARNING${NC}: K6 not installed, installing..."
    if [[ "$OSTYPE" == "darwin"* ]]; then
        brew install k6 || echo "Failed to install K6"
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        sudo gpg -k
        sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
        echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
        sudo apt-get update
        sudo apt-get install k6 || echo "Failed to install K6"
    fi

    if ! command -v k6 &> /dev/null; then
        echo -e "${YELLOW}⚠ WARNING${NC}: K6 not available, skipping performance tests"
        echo "Install K6 from: https://k6.io/docs/getting-started/installation/"
        exit 0
    fi
fi

# Create K6 test script
cat > /tmp/k6-load-test.js <<'EOF'
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '30s', target: 10 },   // Ramp up to 10 users
    { duration: '1m', target: 50 },    // Ramp up to 50 users
    { duration: '2m', target: 50 },    // Stay at 50 users
    { duration: '30s', target: 0 },    // Ramp down to 0 users
  ],
  thresholds: {
    'http_req_duration': ['p(95)<500'],  // 95% of requests should be below 500ms
    'http_req_failed': ['rate<0.01'],    // Error rate should be less than 1%
    'errors': ['rate<0.01'],             // Custom error rate
  },
};

export default function () {
  const BASE_URL = __ENV.TARGET_URL || 'http://localhost:8080';

  // Test 1: Health endpoint
  let healthRes = http.get(`${BASE_URL}/health`);
  let healthOk = check(healthRes, {
    'health status is 200': (r) => r.status === 200,
    'health response time < 200ms': (r) => r.timings.duration < 200,
  });
  errorRate.add(!healthOk);

  sleep(1);

  // Test 2: API endpoint (if available)
  let apiRes = http.get(`${BASE_URL}/api/events/actuator/health`);
  let apiOk = check(apiRes, {
    'api status is 200 or 404': (r) => r.status === 200 || r.status === 404,
  });
  // Don't count 404 as error (endpoint may not exist yet)

  sleep(1);
}
EOF

# Run K6 tests
echo -e "${YELLOW}Running performance tests...${NC}\n"

if k6 run --env TARGET_URL="$TARGET_URL" /tmp/k6-load-test.js; then
    echo ""
    echo -e "${GREEN}✓ Performance tests PASSED${NC}"
    echo "All performance thresholds met:"
    echo "  - P95 response time < 500ms"
    echo "  - Error rate < 1%"
    rm /tmp/k6-load-test.js
    exit 0
else
    echo ""
    echo -e "${RED}✗ Performance tests FAILED${NC}"
    echo "Performance thresholds not met"
    rm /tmp/k6-load-test.js
    exit 1
fi
