# Changelog

All notable changes to the BATbern Shared Kernel will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2024-12-20

### Added
- Initial release of the BATbern Shared Kernel
- Domain value objects: EventId, SpeakerId, CompanyId, UserId
- Domain events: DomainEvent base class, EventCreatedEvent, SpeakerInvitedEvent
- Swiss UID validation for CompanyId
- Validation utilities with comprehensive validation methods
- Error handling utilities with structured error responses
- Logging utilities with MDC support and sensitive data masking
- Date/time utilities with Swiss timezone support and business day calculations
- EventBridge integration for domain event publishing
- Spring Boot auto-configuration for EventBridge
- Retry logic with exponential backoff for event publishing
- Batch event publishing support
- LocalStack test support for integration testing
- 90%+ test coverage for all components
- GitHub Actions CI/CD pipeline
- Comprehensive documentation and examples

### Technical Details
- Java 21 LTS
- Spring Boot 3.3.5
- AWS SDK 2.25.0
- Gradle 9.1.0
- JUnit 5 for testing
- AssertJ for test assertions
- Testcontainers for integration testing
- LocalStack for AWS service simulation
- JaCoCo for code coverage

## [Unreleased]

### Planned
- Additional domain events for other bounded contexts
- GraphQL subscription support for real-time event streaming
- Event replay functionality for debugging
- Enhanced monitoring and metrics collection
- Support for event versioning and migration
- Integration with AWS X-Ray for distributed tracing
- Performance optimizations for high-volume event publishing

---

For migration guides and breaking changes, see [MIGRATION.md](MIGRATION.md)