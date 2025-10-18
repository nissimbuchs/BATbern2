# E2E Testing Guide

## Overview

This guide covers the comprehensive end-to-end testing framework for the BATbern platform. The testing framework validates the full stack from frontend to backend across all environments.

**Purpose**: Prevent production incidents by validating:
- CORS configuration
- Header propagation
- API contracts
- Infrastructure configuration
- Service health

## Testing Layers

```
┌─────────────────────────────────────────────────────────────────┐
│ Layer 4: Infrastructure Validation (TypeScript + AWS SDK)      │
│ Validates AWS resources are configured correctly                │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Layer 3: Playwright E2E Tests (Browser-based)                  │
│ Real browser testing with network interception                  │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Layer 2: Bruno API Contract Tests (API Testing)                │
│ Validates API contracts match OpenAPI specifications            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Layer 1: Shell Script Tests (Smoke + CORS + Headers)           │
│ Fast validation of critical paths                               │
└─────────────────────────────────────────────────────────────────┘
```

## Layer 1: Shell Script Tests

### CORS Validation Tests

**Location**: `scripts/ci/cors-validation-tests.sh`

**Purpose**: Validates that API Gateway accepts cross-origin requests with all required custom headers.

**Usage**:
```bash
# Test staging
./scripts/ci/cors-validation-tests.sh https://api.staging.batbern.ch https://staging.batbern.ch

# Test production
./scripts/ci/cors-validation-tests.sh https://api.batbern.ch https://www.batbern.ch
```

**What it tests**:
- ✅ OPTIONS preflight requests with custom headers
- ✅ X-Correlation-ID header acceptance
- ✅ Accept-Language header acceptance
- ✅ Multiple custom headers together
- ✅ CORS headers in responses
- ✅ Actual request flow (not just preflight)

**Required headers validated**:
- `Authorization`
- `Content-Type`
- `X-Correlation-ID`
- `Accept-Language`
- `Accept`

**Exit codes**:
- `0` - All CORS tests passed
- `1` - One or more CORS tests failed

### Header Propagation Tests

**Location**: `scripts/ci/header-propagation-tests.sh`

**Purpose**: Validates that custom headers flow through the entire stack (Frontend → AWS API Gateway → Spring Boot Gateway → Microservices).

**Usage**:
```bash
# Without auth token (limited tests)
./scripts/ci/header-propagation-tests.sh https://api.staging.batbern.ch

# With auth token (full tests)
./scripts/ci/header-propagation-tests.sh https://api.staging.batbern.ch eyJhbGc...
```

**What it tests**:
- ✅ X-Correlation-ID header acceptance
- ✅ Accept-Language header acceptance
- ✅ Multiple headers together
- ✅ Companies API with full header set (if token provided)
- ✅ Events API with custom headers (if token provided)
- ✅ POST requests with custom headers
- ✅ Header case-insensitivity

### Smoke Tests

**Location**: `scripts/ci/smoke-tests.sh`

**Purpose**: Fast sanity checks of critical infrastructure components.

**Usage**:
```bash
./scripts/ci/smoke-tests.sh https://staging.batbern.ch https://api.staging.batbern.ch
```

**What it tests**:
- ✅ Frontend accessibility
- ✅ API Gateway health
- ✅ Service health checks
- ✅ Database connectivity
- ✅ Cache connectivity

---

## Layer 2: Bruno API Contract Tests

**Location**: `bruno-tests/`

**Purpose**: Validate that deployed APIs match OpenAPI specifications.

### Collections

#### Companies API Tests
**Location**: `bruno-tests/companies-api/`

**Tests**:
1. **01-list-companies-cors.bru** - List companies with CORS headers
2. **02-search-companies.bru** - Search companies with headers
3. **03-create-company-cors.bru** - POST request with CORS headers

**Environment Variables**:
- `baseUrl` - API base URL
- `authToken` - JWT authentication token

#### Event Management Tests
**Location**: `bruno-tests/event-management-service/`

23 tests covering full event management API lifecycle.

### Running Bruno Tests

**Locally** (with Bruno GUI):
```bash
# Open Bruno
bruno

# Load collection: bruno-tests/companies-api
# Select environment: staging
# Run tests
```

**CI/CD** (headless):
```bash
# Install Bruno CLI globally
npm install -g @usebruno/cli

# Run tests for specific environment
./scripts/ci/run-bruno-tests.sh staging

# Run with auth token
./scripts/ci/run-bruno-tests.sh staging "eyJhbGc..."
```

**What it validates**:
- ✅ CORS headers are accepted
- ✅ Response structure matches OpenAPI spec
- ✅ Pagination metadata is correct
- ✅ Error responses match expected format
- ✅ All HTTP methods work correctly

---

## Layer 3: Playwright E2E Tests

**Location**: `web-frontend/e2e/`

**Purpose**: Real browser testing to validate full frontend-to-backend integration.

### Test Suites

#### CORS Validation Tests
**Location**: `web-frontend/e2e/api-integration/cors-validation.spec.ts`

**What it tests**:
- ✅ GET requests with X-Correlation-ID
- ✅ Requests with Accept-Language
- ✅ Multiple custom headers together
- ✅ OPTIONS preflight requests
- ✅ Authenticated requests with all headers
- ✅ Console error tracking for CORS issues
- ✅ Header propagation through stack

#### Companies API Integration Tests
**Location**: `web-frontend/e2e/api-integration/companies-api-integration.spec.ts`

**What it tests**:
- ✅ Unauthenticated requests return 401
- ✅ List companies with pagination
- ✅ Search companies
- ✅ POST requests with all headers
- ✅ Accept-Language header respect
- ✅ Error handling (404, 400)

### Running Playwright Tests

**Locally**:
```bash
cd web-frontend

# Install Playwright
npm install -D @playwright/test
npx playwright install --with-deps chromium

# Run tests against staging
E2E_BASE_URL=https://staging.batbern.ch \
E2E_API_URL=https://api.staging.batbern.ch \
npx playwright test

# Run with auth token
E2E_AUTH_TOKEN="eyJhbGc..." npx playwright test

# Run specific test file
npx playwright test e2e/api-integration/cors-validation.spec.ts
```

**CI/CD**:
```bash
# Automated in .github/workflows/deploy-staging.yml
# Runs automatically after deployment
```

---

## Layer 4: Infrastructure Validation Tests

**Location**: `infrastructure/test/e2e/`

**Purpose**: Validate AWS infrastructure configuration matches requirements.

### Test Suites

#### API Gateway CORS Configuration
**Location**: `infrastructure/test/e2e/api-gateway-cors-validation.test.ts`

**What it validates**:
- ✅ API Gateway has CORS enabled
- ✅ All required custom headers are allowed
- ✅ Correct origins based on environment
- ✅ Credentials are allowed
- ✅ Required HTTP methods are allowed

#### Service Health Validation
**Location**: `infrastructure/test/e2e/service-health-validation.test.ts`

**What it validates**:
- ✅ ECS cluster is deployed
- ✅ All microservices are deployed
- ✅ Services are running with desired count
- ✅ Database stack is deployed
- ✅ API Gateway stack is deployed
- ✅ No failed deployments

### Running Infrastructure Tests

```bash
cd infrastructure

# Install dependencies
npm install

# Run infrastructure validation tests against staging
TEST_E2E=true \
TEST_ENVIRONMENT=staging \
AWS_PROFILE=batbern-staging \
npm test -- test/e2e/

# Run specific test
TEST_E2E=true \
TEST_ENVIRONMENT=staging \
npm test -- test/e2e/api-gateway-cors-validation.test.ts
```

---

## CI/CD Integration

### Deployment Workflow

Both `deploy-staging.yml` and `deploy-production.yml` execute tests in this order:

```yaml
1. Deploy infrastructure
2. Wait for services to stabilize
3. Run smoke tests ✓
4. Run CORS validation tests ✓
5. Run header propagation tests ✓
6. Run Bruno API contract tests ✓
7. Run infrastructure validation tests ✓
8. Run Playwright E2E tests ✓
```

**Test execution time**:
- Smoke tests: ~30 seconds
- CORS validation: ~1 minute
- Header propagation: ~30 seconds
- Bruno tests: ~5 minutes
- Infrastructure tests: ~3 minutes
- Playwright tests: ~10 minutes

**Total**: ~20 minutes post-deployment validation

### Failure Handling

If any test fails:
1. Deployment is marked as failed
2. Database backup is available for rollback
3. Detailed error logs are captured
4. Team is notified

---

## Troubleshooting

### CORS Test Failures

**Symptom**: CORS validation tests fail with "header not allowed"

**Common causes**:
1. API Gateway CORS configuration missing headers
2. Spring Boot CORS handler not configured
3. CloudFront cache serving old responses

**Fix**:
```bash
# 1. Check API Gateway configuration
cat infrastructure/lib/stacks/api-gateway-stack.ts | grep allowHeaders

# 2. Verify Spring Boot CORS config
cat api-gateway/src/main/java/ch/batbern/gateway/security/CorsHandler.java

# 3. Deploy updated configuration
npm run deploy:staging -- BATbern-staging-ApiGateway
```

### Bruno Test Failures

**Symptom**: Bruno tests fail with 401 Unauthorized

**Common causes**:
1. Auth token expired (tokens expire after 1 hour)
2. Using wrong environment token
3. Cognito user pool mismatch

**Fix**:
```bash
# Get fresh token from staging
cd bruno-tests
./get-staging-token.sh your-email@example.com your-password

# Update environment file
# Edit bruno-tests/companies-api/environments/staging.bru
```

### Playwright Test Failures

**Symptom**: Playwright tests timeout or fail

**Common causes**:
1. Service not fully deployed
2. Network connectivity issues
3. Environment variables not set

**Fix**:
```bash
# Check service health
AWS_PROFILE=batbern-staging aws ecs describe-services \
  --cluster batbern-staging \
  --services company-user-management-service

# Verify environment variables
echo $E2E_BASE_URL
echo $E2E_API_URL

# Run with debug logging
DEBUG=pw:api npx playwright test
```

---

## Best Practices

### 1. Run Tests Locally Before Deploying

```bash
# Test CORS locally
./scripts/ci/cors-validation-tests.sh https://api.staging.batbern.ch https://staging.batbern.ch

# Test with Bruno
./scripts/ci/run-bruno-tests.sh staging

# Test with Playwright
cd web-frontend
E2E_BASE_URL=https://staging.batbern.ch npx playwright test
```

### 2. Keep Auth Tokens Fresh

Tokens expire after 1 hour. Get fresh tokens before testing:

```bash
# Staging
./bruno-tests/get-staging-token.sh your-email password

# Production
AWS_PROFILE=batbern-prod aws cognito-idp initiate-auth ...
```

### 3. Monitor Test Results

All tests generate detailed output:
- ✅ Passed tests are green
- ❌ Failed tests are red with error details
- ⚠️ Warnings are yellow (non-critical issues)

### 4. Update Tests When API Changes

When adding new headers or endpoints:
1. Update OpenAPI specifications
2. Add Bruno tests for new endpoints
3. Update CORS validation scripts
4. Add Playwright tests for frontend integration

---

## Preventing Future CORS Issues

### Pre-Deployment Checklist

Before deploying new features:

- [ ] Review API Gateway CORS configuration
- [ ] Verify all custom headers are in allowHeaders list
- [ ] Test locally with CORS validation script
- [ ] Run Bruno tests against staging
- [ ] Run Playwright tests with new headers
- [ ] Check infrastructure validation tests pass

### Monitoring

Monitor these metrics post-deployment:

- API Gateway 4xx/5xx error rates
- CORS preflight failure rates
- Frontend console error rates
- Service health check status

---

## Additional Resources

- [Bruno Documentation](https://www.usebruno.com/docs)
- [Playwright Documentation](https://playwright.dev)
- [AWS API Gateway CORS](https://docs.aws.amazon.com/apigateway/latest/developerguide/how-to-cors.html)
- [OpenAPI Specification](https://spec.openapis.org/oas/latest.html)

---

## Summary

This comprehensive testing framework provides multiple layers of validation:

1. **Fast shell scripts** for immediate feedback (~2 minutes)
2. **Bruno contract tests** for API validation (~5 minutes)
3. **Playwright E2E tests** for real browser testing (~10 minutes)
4. **Infrastructure tests** for AWS resource validation (~3 minutes)

**Total post-deployment validation time**: ~20 minutes

**Result**: Zero CORS-related production incidents ✅
