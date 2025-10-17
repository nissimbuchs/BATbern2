# Testing Strategy Execution Guide

This document explains how tests are organized, when they run, and what credentials they require in the BATbern platform.

## 🎯 Test Type Overview

The BATbern platform uses three distinct test types, each serving a different purpose and running at different stages:

```
┌─────────────────────────────────────────────────────────────────┐
│ DEVELOPMENT PHASE                                                │
├─────────────────────────────────────────────────────────────────┤
│ Unit Tests (Fastest)                                             │
│ • Location: src/test/**/*Test.java (not *IntegrationTest.java)  │
│ • Dependencies: None (mocked)                                    │
│ • Execution: ./gradlew test / npm run test:unit                 │
│ • Duration: Seconds                                              │
│ • AWS Credentials: ❌ Not required                              │
└─────────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────────┐
│ BUILD/CI PHASE                                                   │
├─────────────────────────────────────────────────────────────────┤
│ Integration Tests (Testcontainers + Mocked AWS)                 │
│ • Location: src/test/**/integration/*IntegrationTest.java        │
│ • Dependencies: Docker (Testcontainers PostgreSQL)               │
│ • Execution: ./gradlew test (includes all tests)                │
│ • Duration: Minutes                                              │
│ • AWS Credentials: ❌ Not required (mocked via TestAwsConfig)   │
└─────────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────────┐
│ DEPLOYMENT PHASE                                                 │
├─────────────────────────────────────────────────────────────────┤
│ E2E Tests (Real Environment)                                     │
│ • Location: web-frontend/e2e/, infrastructure/test/e2e/          │
│ • Dependencies: Deployed environment + AWS access                │
│ • Execution: After deployment (in deploy-staging.yml)            │
│ • Duration: Minutes                                              │
│ • AWS Credentials: ✅ Required (GitHub OIDC role)               │
└─────────────────────────────────────────────────────────────────┘
```

## 📋 Test Type Details

### 1. Unit Tests

**Purpose:** Test business logic in isolation with no external dependencies.

**Characteristics:**
- No database
- No AWS services
- No HTTP calls
- All dependencies mocked

**Examples:**
- `CompanyServiceTest.java` - Business logic validation
- `SwissUIDValidationServiceTest.java` - Validation rules
- Frontend component tests (Vitest + React Testing Library)

**Run Locally:**
```bash
# Java
./gradlew test

# TypeScript/Frontend
npm run test:unit
```

**When They Run in CI:**
- ✅ On every PR
- ✅ On every commit to main/develop
- ✅ Before building Docker images

### 2. Integration Tests

**Purpose:** Test integration between application layers with real database and mocked AWS services.

**Characteristics:**
- ✅ Real PostgreSQL (via Testcontainers)
- ✅ Real database queries and transactions
- ✅ Spring Boot context loaded
- ❌ Mocked AWS services (EventBridge, S3)
- ❌ No real AWS API calls

**Key Implementation Detail:**
Integration tests extend `AbstractIntegrationTest` which provides:
- Testcontainers PostgreSQL instance
- Shared container across test classes
- Automatic Spring Boot context configuration

**AWS Mocking Strategy:**
```java
// TestAwsConfig.java provides mocked AWS clients
@Bean
@Primary
public EventBridgeAsyncClient eventBridgeAsyncClient() {
    EventBridgeAsyncClient mockClient = Mockito.mock(EventBridgeAsyncClient.class);
    // Mocked to return successful responses
    return mockClient;
}
```

**Examples:**
- `CompanyControllerIntegrationTest.java` - REST API + Database
- `EventPublishingIntegrationTest.java` - Event publishing (mocked)
- `AuthenticationIntegrationTest.java` - Auth flows

**Run Locally:**
```bash
# Requires Docker running
./gradlew test  # Includes both unit and integration tests

# Frontend (if integration tests exist)
npm run test:integration
```

**When They Run in CI:**
- ✅ On every PR (with Docker service)
- ✅ On every commit to main/develop
- ✅ During build phase (build.yml)

### 3. E2E Tests

**Purpose:** Test complete user journeys against real deployed environments.

**Characteristics:**
- ✅ Real deployed infrastructure
- ✅ Real AWS services
- ✅ Real database
- ✅ Complete frontend + backend integration
- ✅ Requires AWS credentials

**Types:**
1. **Frontend E2E (Playwright)**
   - User interface flows
   - Cross-browser testing
   - Accessibility testing

2. **Infrastructure E2E**
   - AWS resource validation
   - Monitoring and alerting checks
   - Security configuration verification

**Examples:**
- Playwright tests in `web-frontend/e2e/`
- Infrastructure tests in `infrastructure/test/e2e/`

**Run Locally:**
```bash
# Frontend E2E against local environment
npm run test:e2e

# Against staging (requires VPN/access)
npm run test:e2e:staging

# Against production (read-only tests)
npm run test:e2e:production
```

**When They Run in CI:**
- ✅ After successful staging deployment
- ✅ After successful production deployment
- ✅ As smoke tests post-deploy
- ❌ NOT during build phase (no environment yet)

**AWS Credentials:**
Provided via GitHub Actions OIDC:
```yaml
- name: Configure AWS credentials
  uses: aws-actions/configure-aws-credentials@v4
  with:
    role-to-assume: arn:aws:iam::188701360969:role/batbern-staging-github-actions-role
    aws-region: eu-central-1
```

## 🔧 CI/CD Pipeline Test Execution

### Build Pipeline (`.github/workflows/build.yml`)

```yaml
jobs:
  build-services:
    # Runs unit + integration tests together
    # Integration tests use Testcontainers + mocked AWS
    steps:
      - name: Build and test service
        run: ./gradlew clean build test
        # ✅ Tests run here
        # ✅ No AWS credentials needed (mocked)
        # ✅ Requires Docker for Testcontainers

  build-frontend:
    steps:
      - name: Run tests
        run: npm test -- --coverage --run
        # ✅ Unit tests only
        # ✅ No AWS credentials needed

  integration-tests:
    # Placeholder for future proper integration test separation
    # Currently skips gracefully
    steps:
      - name: Run integration tests
        run: ./gradlew integrationTest || echo "Not configured yet"
```

### Deploy Staging Pipeline (`.github/workflows/deploy-staging.yml`)

```yaml
jobs:
  deploy-to-staging:
    steps:
      - name: Configure AWS credentials
        # ✅ AWS credentials provided here
        uses: aws-actions/configure-aws-credentials@v4

      - name: Deploy infrastructure
        run: npm run deploy:staging

      - name: Run smoke tests
        # ✅ E2E tests run against deployed environment
        run: ./scripts/ci/smoke-tests.sh

      - name: Run E2E infrastructure tests
        # ✅ Tests real AWS resources
        run: TEST_E2E=true TEST_ENVIRONMENT=staging npm test

      - name: Run frontend E2E tests
        # ✅ Playwright tests against deployed frontend
        run: npx playwright test
```

## 📊 Test Coverage Requirements

| Test Type | Minimum Coverage | Target Coverage |
|-----------|------------------|-----------------|
| Unit Tests | 80% | 90% |
| Integration Tests | 70% | 85% |
| E2E Tests | Critical paths only | All major flows |

## 🚀 Running Tests Locally

### Quick Commands

```bash
# Run all tests (unit + integration, requires Docker)
make test

# Run only Java tests
make test-java

# Run only TypeScript tests
make test-node

# Run with coverage reports
make test-coverage

# Run specific service tests
cd services/company-user-management-service
./gradlew test

# Run specific frontend tests
cd web-frontend
npm run test:unit          # Unit tests
npm run test:watch         # Watch mode
npm run test:coverage      # With coverage
npm run test:e2e           # E2E tests (requires running app)
```

### Prerequisites

**For Unit Tests:**
- Java 21
- Node.js 20
- No special setup

**For Integration Tests:**
- Docker Desktop running
- Testcontainers will automatically pull PostgreSQL image
- ~2GB disk space for images

**For E2E Tests:**
- Running application instance
- AWS credentials (for staging/production)
- VPN access (if required)

## 🔐 AWS Credentials - The Full Picture

### ❌ AWS Credentials NOT Required For:
- ✅ Unit tests (fully mocked)
- ✅ Integration tests (AWS services mocked via `TestAwsConfig.java`)
- ✅ Local development
- ✅ CI build pipeline
- ✅ Docker builds

**Why Integration Tests Don't Need AWS:**
```java
// All AWS clients are mocked in test profile
@TestConfiguration
@Profile("test")
public class TestAwsConfig {
    @Bean
    @Primary
    public EventBridgeAsyncClient eventBridgeAsyncClient() {
        // Returns a Mockito mock that simulates successful responses
        return Mockito.mock(EventBridgeAsyncClient.class);
    }
}
```

### ✅ AWS Credentials REQUIRED For:
- E2E tests against staging
- E2E tests against production
- Deployment pipelines
- Database migrations
- Infrastructure verification

## 🎯 Future Improvements (Phase 2)

The current setup works well but can be enhanced:

### 1. Proper Test Separation
```gradle
// Future build.gradle enhancement
test {
    useJUnitPlatform {
        excludeTags 'integration', 'e2e'
    }
}

task integrationTest(type: Test) {
    useJUnitPlatform {
        includeTags 'integration'
    }
}
```

**Benefits:**
- Faster feedback (run unit tests first)
- Explicit test categorization
- Better CI pipeline optimization

### 2. Test Tagging
```java
@Tag("integration")
class CompanyControllerIntegrationTest extends AbstractIntegrationTest {
    // Integration test code
}
```

### 3. Enhanced CI Pipeline
```yaml
jobs:
  unit-tests:
    # Fast unit tests only
    run: ./gradlew test -Dtest.exclude=integration

  integration-tests:
    needs: unit-tests
    # Integration tests with Docker
    run: ./gradlew integrationTest
```

## 📚 Related Documentation

- [Testing Strategy](./06c-testing-strategy.md) - Test architecture and patterns
- [Backend Architecture](./06-backend-architecture.md) - Production parity testing
- [Infrastructure](./02-infrastructure-deployment.md) - Deployment and environments
- [CI/CD Pipeline](../deployment/cicd-pipeline-guide.md) - Pipeline details

## 🤔 Common Questions

### Q: Why are integration tests not separated from unit tests?
**A:** They will be in Phase 2. Current implementation runs all tests together but works well because:
- Testcontainers starts quickly with container reuse
- AWS services are mocked (no credentials needed)
- Total test time is acceptable (~2-3 minutes)

### Q: Do I need LocalStack?
**A:** No! AWS services (EventBridge, S3) are mocked using Mockito in tests. LocalStack is not required.

### Q: Can I run tests without Docker?
**A:** Unit tests - yes. Integration tests - no (they require Testcontainers PostgreSQL).

### Q: Why do integration tests pass locally but I'm confused about AWS credentials?
**A:** Because integration tests don't use real AWS! They use mocked AWS clients via `TestAwsConfig.java`. Only E2E tests need real AWS credentials.

### Q: How do I know if a test needs AWS credentials?
**A:**
- **Unit test** (`*Test.java`) - ❌ No AWS
- **Integration test** (`*IntegrationTest.java`) - ❌ No AWS (mocked)
- **E2E test** (`e2e/**/*.test.ts`, `e2e/**/*.spec.ts`) - ✅ Needs AWS

### Q: What's the difference between `make test` and `./gradlew test`?
**A:**
- `make test` - Runs tests for ALL services + frontend in parallel
- `./gradlew test` - Runs tests for Java services only (from repo root)
- Service-specific: `cd services/X && ./gradlew test` - Single service

## 📝 Summary

**The Key Insight:**
Integration tests in BATbern use **mocked AWS services**, so they don't require AWS credentials. Only E2E tests (which run post-deployment) need real AWS access.

**Test Execution Pattern:**
1. **Development:** Unit tests (fast, no dependencies)
2. **CI/Build:** Unit + Integration tests (Docker only, no AWS)
3. **Deployment:** E2E tests (real AWS, full environment)

This layered approach provides fast feedback while ensuring production parity and full system validation.
