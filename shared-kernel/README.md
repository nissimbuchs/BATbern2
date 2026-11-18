# BATbern Shared Kernel

Shared domain types, events, and utilities for the BATbern platform services (`api-gateway`,
`services/*`). It is a Gradle module of this monorepo, not a separate repository; the services
consume it as a project dependency.

## What is in it

Every class under `src/main/java/ch/batbern/shared`, by package (listed from the source tree
2026-11-18; regenerate the table from the tree when a package changes, never by hand):

| Package | Classes |
|---|---|
| `api` | `DeterministicPaging`, `FieldSelector`, `FilterCriteria`, `FilterOperator`, `FilterParser`, `IncludeParser`, `PaginationMetadata`, `PaginationParams`, `PaginationUtils`, `SortCriteria`, `SortDirection`, `SortParser` |
| `config` | `AwsSesConfig`, `S3EndpointOverride`, `SharedObjectMapperConfig` |
| `contract` | `ContractCallFailedException`, `ContractCalls`, `ContractRefusedException` |
| `dto` | `ErrorResponse`, `PaginatedResponse` |
| `events` | `DomainEvent`, `DomainEventPublisher`, `EventCreatedEvent`, `EventWorkflowTransitionEvent`, `SessionTimingAssignedEvent`, `SpeakerAcceptedEvent`, `SpeakerAddedToPoolEvent`, `SpeakerInvitationSentEvent`, `SpeakerInvitedEvent`, `SpeakerPromotedToReadyEvent`, `SpeakerResponseReceivedEvent`, `SpeakerWorkflowStateChangeEvent`, `SpringDomainEventPublisher` |
| `exception` | `BATbernException`, `ClientErrorClassifier`, `ErrorCode`, `InvalidStateTransitionException`, `NotFoundException`, `ServiceException`, `UnauthorizedException`, `ValidationException` |
| `exceptions` | `DomainException`, `ValidationException` |
| `mail` | `ComposedMail`, `MailContext`, `MailRedaction`, `MailTrigger`, `OutboundMail`, `OutboundMailRecorder` |
| `search` | `SearchPredicates`, `SearchTerms` |
| `security` | `AuditLog`, `AuditLoggingAspect`, `ClientIp`, `JwtRolesConverter`, `S2sHeaders` |
| `service` | `CapturedEmail`, `EmailService`, `IcsCalendarService`, `LocalEmailCapture`, `ReservedEmailRecipientException`, `SlugGenerationService`, `TestMarkedEmailException` |
| `types` | `EventWorkflowState`, `Role`, `SpeakerResponseType`, `SpeakerWorkflowState` |
| `upload` | `ImageUrlFetcher`, `UploadException`, `UploadPurposeHandler`, `UploadRecord`, `UploadRecordRepository`, `UploadRules`, `UploadService`, `UploadStatus`, `UploadStatusConverter` |
| `util` | `CorrelationIdGenerator`, `EmailContentTestMarker`, `ReservedEmailDomain` |
| `utils` | `CloudFrontUrlBuilder`, `DateTimeUtils`, `EmailAddresses`, `LogContext`, `LoggingUtils`, `S3Links`, `ValidationUtils` |

`exception` (the `BATbernException` hierarchy with `ErrorCode`) and `exceptions` (`DomainException`)
both exist and both carry a `ValidationException`; import the one the surrounding code uses.

There are no typed ID wrappers (`EventId`, `UserId` and the like do not exist). Cross-service
references use meaningful string IDs (`eventCode`, `username`, `companyName`, ADR-003); UUIDs
stay inside one service.

## Requirements

- Java 21
- Gradle from the repository's wrapper (`./gradlew` at the root, see
  `gradle/wrapper/gradle-wrapper.properties`)

## Using it from a service

```groovy
// <service>/build.gradle
implementation project(':shared-kernel')
testImplementation testFixtures(project(':shared-kernel'))   // ClientErrorContract, test probes
```

## Usage

### Shared Types

```java
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.types.SpeakerResponseType;

// Workflow states shared by the services (UPPER_CASE in Java and JSON, lowercase in the DB)
EventWorkflowState state = EventWorkflowState.AGENDA_PUBLISHED;
SpeakerWorkflowState speaker = SpeakerWorkflowState.ACCEPTED;
```

### Domain Events

```java
import ch.batbern.shared.events.*;

// Create an event
EventCreatedEvent event = EventCreatedEvent.builder()
    .createdEventCode("BATbern60")
    .title("BATbern 60")
    .eventType("EVENING")
    .eventDate(LocalDate.of(2026, 11, 6))
    .venue("Kornhausforum")
    .organizerId("john.doe")
    .build();

// Publish to the listeners in the same service (@EventListener, @TransactionalEventListener)
@Autowired
private DomainEventPublisher eventPublisher;

eventPublisher.publish(event);
```

Events stay in the process: there is no message bus (Story 17.2 part B, ADR-014). A listener
that throws is logged at ERROR and does not fail the caller.

### Validation Utilities

```java
import ch.batbern.shared.utils.ValidationUtils;
import ch.batbern.shared.exceptions.ValidationException;

// Validate email
ValidationUtils.validateEmail("user@batbern.ch");

// Validate required fields
ValidationUtils.validateRequired(value, "fieldName");

// Validate string length
ValidationUtils.validateLength(text, 5, 100, "description");
```

### Error Handling

```java
import ch.batbern.shared.dto.ErrorResponse;               // standard error body of every service
import ch.batbern.shared.exception.NotFoundException;     // BATbernException hierarchy, with ErrorCode + severity
import ch.batbern.shared.exception.ValidationException;
import ch.batbern.shared.exception.ClientErrorClassifier;

throw new NotFoundException("Event", "BATbern60");

// In a catch-all @ExceptionHandler(Exception.class): client mistakes (missing parameter/header,
// 404, 405, 415, NUL byte) keep their 4xx at severity LOW instead of a 500 CRITICAL (#1148).
Optional<ClientErrorClassifier.ClientError> clientError = ClientErrorClassifier.classify(ex);
if (clientError.isPresent()) {
    return clientError.get().toResponseEntity(request.getRequestURI(), correlationId);
}
```

Every service's handler is checked against this with `ClientErrorContract.verify(handler)` from
the shared-kernel test fixtures.

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
```

### Environment Variables

```bash
# AWS Credentials (for production)
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key

# AWS Region
export AWS_REGION=eu-central-1
```

## Testing

### Running Tests

Run from the repository root:

```bash
# Run all tests
./gradlew :shared-kernel:test

# Run specific test class
./gradlew :shared-kernel:test --tests "*ValidationUtilsTest"

# Run with coverage
./gradlew :shared-kernel:test :shared-kernel:jacocoTestReport

# Check the coverage threshold (minimum 0.60 instruction coverage, shared-kernel/build.gradle)
./gradlew :shared-kernel:jacocoTestCoverageVerification
```

## Architecture

### Domain events

There is no event sourcing; events are notifications inside one service. All domain events
extend `DomainEvent` and include:
- Aggregate ID
- Event type and version
- Occurrence timestamp
- Correlation/causation IDs

### In-process delivery

`SpringDomainEventPublisher` hands each event to Spring's `ApplicationEventPublisher`. Nothing
leaves the service; cross-service calls go through `/internal/**` (ADR-016).

## Development

### Building from Source

From the repository root (all Gradle commands run there):

```bash
./gradlew :shared-kernel:build
./gradlew :shared-kernel:publishToMavenLocal
```

### Code Style

See `docs/architecture/coding-standards.md`.

## Versioning

We use [Semantic Versioning](https://semver.org/):
- MAJOR: Incompatible API changes
- MINOR: Backward-compatible functionality
- PATCH: Backward-compatible bug fixes

## License

This project is proprietary software owned by BATbern. All rights reserved.

## Support

For questions and support:
- Technical issues: info@batbern.ch
- Business inquiries: info@batbern.ch

## Contributing

This is an internal project. For contribution guidelines, please refer to the internal documentation.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for a list of changes.

## Authors

- BATbern Platform Team

---

© 2024 BATbern. All rights reserved.