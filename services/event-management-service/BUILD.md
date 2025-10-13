# Event Management Service - Build Instructions

## Prerequisites

- Java 21 LTS
- Gradle 9.1.0+ (via wrapper)
- Docker (for Testcontainers integration tests)

## Building and Testing

### ⚠️ IMPORTANT: Always run from project root

This is a Gradle multi-module project. **All Gradle commands must be executed from the project root directory**, not from the service subdirectory.

### Correct Commands (from project root):

```bash
# Run all tests for event-management-service
./gradlew :services:event-management-service:test

# Run specific test
./gradlew :services:event-management-service:test --tests "EventControllerIntegrationTest.should_listEvents_when_noFilterProvided"

# Run tests with coverage
./gradlew :services:event-management-service:jacocoTestReport

# Build service JAR
./gradlew :services:event-management-service:build

# Clean build artifacts
./gradlew :services:event-management-service:clean
```

### ❌ Incorrect (will fail with "Project with path ':shared-kernel' could not be found"):

```bash
cd services/event-management-service
./gradlew test  # This will FAIL
```

## Why This Matters

The event-management-service depends on the `shared-kernel` module, which contains:
- FilterParser (query filtering)
- SortParser (query sorting)
- PaginationUtils (pagination logic)
- Shared DTOs and exceptions

The Gradle multi-module setup is defined in `/settings.gradle` at the project root:

```gradle
include 'shared-kernel'
include 'services:event-management-service'
```

Running Gradle from the service subdirectory breaks this dependency resolution.

## Test Results

- **Total Tests**: 51 integration tests
- **Test Coverage**: 100% of acceptance criteria (AC1-AC18)
- **Status**: All tests passing ✅

## Common Issues

### Issue: "Project with path ':shared-kernel' could not be found"

**Cause**: Running Gradle from the service subdirectory

**Solution**: Always run Gradle commands from the project root directory (see correct commands above)

### Issue: Testcontainers fails to start

**Cause**: Docker not running

**Solution**: Start Docker Desktop before running tests

## IDE Configuration

### IntelliJ IDEA

1. Open the **project root** directory (`BATbern-Platform`), not the service directory
2. IntelliJ will automatically detect the Gradle multi-module setup
3. Run tests using the IDE test runner (it uses the correct Gradle configuration)

### VS Code

1. Open the project root directory
2. Install the "Gradle for Java" extension
3. Use the Gradle task explorer to run tests

## Performance

- **Event list (no filters)**: <100ms (P95)
- **Event detail (basic)**: <150ms (P95)
- **Event detail (all includes)**: <500ms (P95)
- **Cached responses**: <50ms (P95)

All performance requirements met with Caffeine in-memory caching (15min TTL).
