# Changelog

All notable changes to the BATbern Shared Kernel will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased] — speaker-workflow-refactor

### Removed
- `SpeakerWorkflowState.SLOT_ASSIGNED` — replaced by derived flag `is_slot_assigned := session.start_time IS NOT NULL` per ADR-009 §0.1.
- `SpeakerWorkflowState.CONFIRMED` — replaced by derived flag `is_publishable := quality_reviewed AND is_slot_assigned` per ADR-009 §0.1.
- `SpeakerWorkflowState.OVERFLOW` — capacity enforced at the invitation step (slot-capacity gate replaces overflow parking lane) per ADR-009 §0.7.
- `SpeakerWorkflowState.WITHDREW` — collapsed into `DECLINED` with reason recorded in `speaker_status_history` per ADR-009 §0.7.
- `SpeakerResponseType.TENTATIVE` — speakers respond ACCEPT or DECLINE only per ADR-009 §0.6.

### Added
- `SpeakerPromotedToReadyEvent` — signals the `CONTACTED → READY` provisioning gate (User created/looked-up + SPEAKER role granted + Cognito provisioning in Phase E). Consumed by Phase E (Story 11.E.2) to send the Cognito invitation email.

### Changed
- Javadoc on `SpeakerWorkflowState.CONTACTED` now describes "still brainstorming" semantics.
- Javadoc on `SpeakerWorkflowState.READY` now describes the provisioning gate.
- Javadoc on `SpeakerResponseType.ACCEPT` / `DECLINE` no longer references magic-link token consumption (Cognito session is the auth from Phase E onward).

### Migration notes
- Downstream services (`event-management-service`, `web-frontend`) WILL fail to compile against this version of shared-kernel until they are updated. The compile failures are intentional signals for Story 11.B.2 (workflow-service single-writer) and Story 11.B.3 (Flyway migration + OpenAPI tighten).
- DB-level migration of legacy `speaker_pool.status` values is owned by Story 11.B.3.
- See `docs/architecture/ADR-009-unified-speaker-workflow.md` for the full target state model.

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