# BATbern Shared Kernel

[![CI/CD](https://github.com/batbern/shared-kernel/actions/workflows/ci.yml/badge.svg)](https://github.com/batbern/shared-kernel/actions/workflows/ci.yml)
[![Coverage](https://codecov.io/gh/batbern/shared-kernel/branch/main/graph/badge.svg)](https://codecov.io/gh/batbern/shared-kernel)
[![License](https://img.shields.io/badge/license-proprietary-red.svg)](LICENSE)

Shared domain types, events, and utilities for the BATbern platform microservices ecosystem.

## Overview

The Shared Kernel provides common Domain-Driven Design (DDD) building blocks that are shared across all BATbern platform microservices:

- **Domain Value Objects**: Strongly-typed identifiers (EventId, SpeakerId, CompanyId, UserId)
- **Domain Events**: Event sourcing support with EventBridge integration
- **Common Utilities**: Validation, error handling, logging, and date/time utilities
- **Swiss Compliance**: Built-in support for Swiss UID validation and Swiss timezone handling

## Requirements

- Java 21 or higher
- Gradle 8.5 or higher
- AWS Account (for EventBridge integration)

## Installation

### Maven

```xml
<dependency>
    <groupId>ch.batbern</groupId>
    <artifactId>shared-kernel</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'ch.batbern:shared-kernel:1.0.0'
```

## Usage

### Domain Value Objects

```java
import ch.batbern.shared.types.*;

// Create strongly-typed identifiers
EventId eventId = EventId.generate();
EventId fromString = EventId.from("550e8400-e29b-41d4-a716-446655440000");

// Company ID with Swiss UID validation
CompanyId companyId = CompanyId.from("CHE-123.456.789");

// User ID from AWS Cognito
UserId userId = UserId.from("cognito-user-id-123");

// Speaker ID
SpeakerId speakerId = SpeakerId.generate();
```

### Domain Events

```java
import ch.batbern.shared.events.*;
import ch.batbern.shared.types.*;

// Create an event
EventCreatedEvent event = EventCreatedEvent.builder()
    .createdEventId(EventId.generate())
    .title("BATbern 2024")
    .eventType("CONFERENCE")
    .eventDate(LocalDate.of(2024, 6, 15))
    .venue("Bern Convention Center")
    .organizerId(UserId.from("organizer-123"))
    .build();

// Publish to EventBridge
@Autowired
private DomainEventPublisher eventPublisher;

eventPublisher.publish(event);

// Async publishing
CompletableFuture<Void> future = eventPublisher.publishAsync(event);

// Batch publishing
List<DomainEvent<?>> events = Arrays.asList(event1, event2, event3);
eventPublisher.publishBatch(events);
```

### Validation Utilities

```java
import ch.batbern.shared.utils.ValidationUtils;
import ch.batbern.shared.exceptions.ValidationException;

// Validate Swiss UID
try {
    ValidationUtils.validateSwissUID("CHE-123.456.789");
} catch (ValidationException e) {
    // Handle validation error
}

// Validate email
ValidationUtils.validateEmail("user@batbern.ch");

// Validate required fields
ValidationUtils.validateRequired(value, "fieldName");

// Validate string length
ValidationUtils.validateLength(text, 5, 100, "description");
```

### Error Handling

```java
import ch.batbern.shared.utils.ErrorHandlingUtils;
import ch.batbern.shared.utils.ErrorResponse;

try {
    // Some operation
} catch (Exception e) {
    // Create error response
    ErrorResponse response = ErrorHandlingUtils.createErrorResponse(e, "/api/events");

    // Format error message
    String message = ErrorHandlingUtils.formatErrorMessage(e);

    // Check error type
    if (ErrorHandlingUtils.isClientError(e)) {
        // Return 400 Bad Request
    } else if (ErrorHandlingUtils.isRetryableError(e)) {
        // Retry the operation
    }
}
```

### Logging Utilities

```java
import ch.batbern.shared.utils.LoggingUtils;

// Structured logging
Map<String, Object> context = Map.of(
    "eventId", "123",
    "userId", "456",
    "action", "createEvent"
);

logger.info(LoggingUtils.formatStructuredMessage("Event created", context));

// Set trace context for distributed tracing
LoggingUtils.setTraceContext("trace-123", "correlation-456");

// Mask sensitive data
Map<String, Object> sensitiveData = Map.of(
    "email", "user@example.com",
    "creditCard", "1234-5678-9012-3456"
);
Map<String, Object> masked = LoggingUtils.maskSensitiveData(sensitiveData);
// Result: {"email": "u***@example.com", "creditCard": "1234-****-****-3456"}

// Measure performance
long duration = LoggingUtils.measureTime(() -> {
    // Some operation
});
```

### Date/Time Utilities

```java
import ch.batbern.shared.utils.DateTimeUtils;

// Swiss timezone support
ZonedDateTime swissTime = DateTimeUtils.nowSwiss();
ZonedDateTime converted = DateTimeUtils.toSwissTime(Instant.now());

// Format dates in Swiss format
String formatted = DateTimeUtils.formatSwissDate(LocalDate.now()); // "15.01.2024"

// Business day calculations
LocalDate nextBusinessDay = DateTimeUtils.addBusinessDays(LocalDate.now(), 3);
long businessDays = DateTimeUtils.businessDaysBetween(start, end);

// Quarter calculations
String quarter = DateTimeUtils.getQuarter(LocalDate.now()); // "Q1 2024"

// Parse flexible date formats
TemporalAccessor parsed = DateTimeUtils.parseFlexible("15.01.2024");
```

## Configuration

### Spring Boot Application Properties

```yaml
# AWS Configuration
aws:
  region: eu-central-1
  eventbridge:
    bus-name: batbern-events
    source: batbern.event-management
    retry:
      max-attempts: 3
      delay-ms: 1000

# For LocalStack testing
aws:
  eventbridge:
    endpoint: http://localhost:4566
```

### Environment Variables

```bash
# AWS Credentials (for production)
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key

# AWS Region
export AWS_REGION=eu-central-1

# EventBridge Configuration
export AWS_EVENTBRIDGE_BUS_NAME=batbern-events
```

## Testing

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "*ValidationUtilsTest"

# Run with coverage
./gradlew test jacocoTestReport

# Check coverage threshold (90%)
./gradlew jacocoTestCoverageVerification
```

### Integration Testing with LocalStack

The project includes integration tests using LocalStack for AWS services:

```java
@Testcontainers
@SpringBootTest
class EventBridgeIntegrationTest {
    @Container
    static LocalStackContainer localStack = new LocalStackContainer()
        .withServices(EVENTS);

    // Tests run against LocalStack
}
```

## Architecture

### Domain-Driven Design

The Shared Kernel follows DDD principles:
- **Value Objects**: Immutable objects with validation
- **Domain Events**: Capture business events
- **Ubiquitous Language**: Swiss-specific terminology

### Event Sourcing

All domain events extend `DomainEvent` and include:
- Aggregate ID
- Event type and version
- Occurrence timestamp
- Correlation/causation IDs

### AWS EventBridge Integration

Events are published to AWS EventBridge for cross-service communication:
- Automatic retry with exponential backoff
- Batch publishing support
- Dead letter queue for failed events

## Development

### Building from Source

```bash
# Clone the repository
git clone https://github.com/batbern/shared-kernel.git
cd shared-kernel

# Build the project
./gradlew build

# Publish to local Maven repository
./gradlew publishToMavenLocal
```

### Code Style

The project follows Java best practices:
- Immutable value objects
- Builder pattern for complex objects
- Comprehensive null checks
- Meaningful exception messages

## Versioning

We use [Semantic Versioning](https://semver.org/):
- MAJOR: Incompatible API changes
- MINOR: Backward-compatible functionality
- PATCH: Backward-compatible bug fixes

## License

This project is proprietary software owned by BATbern. All rights reserved.

## Support

For questions and support:
- Technical issues: tech@batbern.ch
- Business inquiries: info@batbern.ch

## Contributing

This is an internal project. For contribution guidelines, please refer to the internal documentation.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for a list of changes.

## Authors

- BATbern Platform Team

---

© 2024 BATbern. All rights reserved.