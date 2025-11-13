# Integration Testing Guide

## Overview

Integration tests verify API integration with backend services. These tests require running backend services and are separate from unit tests.

## Running Integration Tests

### Prerequisites

1. **Backend Services Running:**
   - Partner Coordination Service (Story 2.7) on port 8081
   - Company Management Service on port 8082
   - User Management Service on port 8083
   - API Gateway on port 8080

2. **Test JWT Token:**
   - Valid JWT token for authentication
   - Can be obtained via `./scripts/auth/get-token.sh`

### Start Backend Services

```bash
# Option 1: Docker Compose (all services)
cd /Users/nissim/dev/bat/BATbern-2.8.1
make docker-up

# Option 2: Native development (more resource efficient)
make dev-native-up
```

### Run Integration Tests

```bash
# Set environment variables
export RUN_INTEGRATION_TESTS=true
export VITE_API_URL=http://localhost:8080/api/v1
export TEST_JWT_TOKEN=$(./scripts/auth/get-token.sh)

# Run integration tests
cd web-frontend
npm run test:integration

# Or run specific integration test file
npm test -- src/services/api/__tests__/partnerApi.integration.test.ts --run
```

### CI/CD Integration

Integration tests run in CI/CD pipeline after backend deployment:

```yaml
# .github/workflows/integration-tests.yml
- name: Run Integration Tests
  env:
    RUN_INTEGRATION_TESTS: true
    VITE_API_URL: ${{ secrets.API_URL }}
    TEST_JWT_TOKEN: ${{ secrets.TEST_JWT_TOKEN }}
  run: |
    cd web-frontend
    npm run test:integration
```

## Test Coverage

### AC10.1: Partner API Integration (6 tests)
- ✅ Fetch partners list from backend
- ✅ Paginated results with page 1 and page 2
- ✅ Filter by partnership tier (GOLD)
- ✅ Filter by active status
- ✅ Sort by company name (ascending)

### AC10.2: HTTP Enrichment - Company Data (2 tests)
- ✅ Include company data via `?include=company` parameter
- ✅ Handle missing company data gracefully

### AC10.3: HTTP Enrichment - Contact Data (3 tests)
- ✅ Include contact data via `?include=contacts` parameter
- ✅ Identify primary contact when multiple contacts exist
- ✅ Handle missing contact data gracefully

### AC10.4: Error Handling (4 tests)
- ✅ Return 404 for invalid endpoint
- ✅ Return empty content for invalid page number
- ✅ Return error for invalid filter value
- ✅ Handle network timeout

### AC10.5: JWT Token Propagation (3 tests)
- ✅ Return 401 when no JWT token provided
- ✅ Return 401 when invalid JWT token provided
- ✅ Propagate valid JWT token successfully

### Partner Statistics API (2 tests)
- ✅ Fetch statistics from backend
- ✅ Verify statistics count matches list pagination total

**Total: 20 integration tests**

## Test Data Requirements

Integration tests expect the backend to have:
- At least 5 partners in the database
- Partners with different tiers (GOLD, SILVER, BRONZE, PLATINUM, STRATEGIC)
- At least one active and one inactive partner
- Some partners with company data enrichment
- Some partners with contact data enrichment

### Seed Test Data

```bash
# Run database seed script
./scripts/ci/seed-test-data.sh

# Or use Bruno API tests to create test data
./scripts/ci/run-bruno-tests.sh partner-setup
```

## Debugging Integration Tests

### View Test Output

```bash
# Run with verbose output
npm test -- src/services/api/__tests__/partnerApi.integration.test.ts --run --reporter=verbose

# View HTML report
npx vite preview --outDir test-results
```

### Check Backend Logs

```bash
# Docker logs
docker-compose logs -f api-gateway
docker-compose logs -f partner-coordination-service

# Native logs
tail -f logs/api-gateway.log
tail -f logs/partner-coordination-service.log
```

### Verify API Endpoints

```bash
# Test Partner API directly
curl -H "Authorization: Bearer $TEST_JWT_TOKEN" \
  http://localhost:8080/api/v1/partners?page=0&size=20

# Test with filters
curl -H "Authorization: Bearer $TEST_JWT_TOKEN" \
  "http://localhost:8080/api/v1/partners?filter=partnershipLevel:GOLD&include=company,contacts"

# Test statistics endpoint
curl -H "Authorization: Bearer $TEST_JWT_TOKEN" \
  http://localhost:8080/api/v1/partners/statistics
```

## Integration Test Best Practices

1. **Isolation**: Each test should be independent and not rely on other tests
2. **Cleanup**: Tests should clean up test data after execution (if creating data)
3. **Timeouts**: Use appropriate timeouts (10s) for backend calls
4. **Error Handling**: Test both success and error scenarios
5. **Real Data**: Use real backend services, not mocks (this is integration testing)
6. **JWT Tokens**: Always use valid JWT tokens for authentication
7. **Environment Variables**: Use environment variables for configuration
8. **Skip in CI**: Skip integration tests in unit test runs (use `describe.skip`)

## Troubleshooting

### Tests Skipped

**Problem**: All integration tests show as "skipped"

**Solution**: Set `RUN_INTEGRATION_TESTS=true` environment variable

```bash
export RUN_INTEGRATION_TESTS=true
npm test -- src/services/api/__tests__/partnerApi.integration.test.ts --run
```

### 401 Unauthorized Errors

**Problem**: Tests fail with 401 errors

**Solution**: Generate valid JWT token

```bash
export TEST_JWT_TOKEN=$(./scripts/auth/get-token.sh)
```

### Connection Refused Errors

**Problem**: Tests fail with "ECONNREFUSED" errors

**Solution**: Ensure backend services are running

```bash
# Check services
docker-compose ps

# Or check native services
ps aux | grep java
```

### Timeout Errors

**Problem**: Tests fail with timeout errors

**Solution**: Increase timeout or check backend performance

```typescript
// Increase timeout in test
it('should_fetchPartners_when_backendRunning', async () => {
  // ...
}, 30000); // 30 seconds
```

## Integration Test Maintenance

### When to Update Integration Tests

1. **API Changes**: When Partner API contract changes (new endpoints, fields)
2. **HTTP Enrichment**: When adding new enrichment parameters
3. **Error Handling**: When adding new error scenarios
4. **Authentication**: When JWT token format changes
5. **Pagination**: When pagination behavior changes

### Regular Maintenance

- Run integration tests weekly against DEV environment
- Update test data seeds when schema changes
- Review and update expected response formats
- Monitor test execution time and optimize slow tests

## Resources

- **API Documentation**: `docs/api/partners-api.openapi.yml`
- **Backend Service**: Story 2.7 (Partner Coordination Service)
- **HTTP Enrichment**: ADR-004 (Backend HTTP enrichment pattern)
- **Authentication**: Story 1.11 (Security & Compliance Essentials)
