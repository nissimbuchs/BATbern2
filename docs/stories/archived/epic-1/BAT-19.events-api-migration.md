# Story: Backend DTO Migration: Events API (ADR-003 + ADR-006)

**Linear Issue**: [BAT-19](https://linear.app/batbern/issue/BAT-19/backend-dto-migration-events-api-adr-003-adr-006) ← **PRIMARY SOURCE**

**Story File**: This file contains **ONLY** dev implementation notes

---

## ⚠️ IMPORTANT: Story Content Location

This file contains **ONLY** dev implementation notes. For story content, see Linear:

- **User Story**: [Linear description](https://linear.app/batbern/issue/BAT-19)
- **Acceptance Criteria**: [Linear issue](https://linear.app/batbern/issue/BAT-19) (see checkboxes)
- **Tasks/Subtasks**: [Linear subtasks](https://linear.app/batbern/issue/BAT-19)
- **QA Results**: [Linear comments](https://linear.app/batbern/issue/BAT-19)
- **Status**: [Linear workflow state](https://linear.app/batbern/issue/BAT-19)

---

## Dev Agent Record

### Agent Model Used
{To be filled by dev agent}

### Template References

**Implementation Patterns to Use**:
- Backend: `docs/templates/backend/spring-boot-service-foundation.md`
- Backend: `docs/templates/backend/integration-test-pattern.md`
- Backend: `docs/templates/backend/flyway-migration-pattern.md`

**Existing Code References**:
- Reference Implementations:
  - BAT-17: Topics API Migration (COMPLETE)
  - BAT-18: Speakers API Migration
  - Event entity with eventCode: `services/event-management-service/src/main/java/ch/batbern/events/domain/Event.java`

### Migration Plan Reference

**Source**: `docs/plans/backend-dto-openapi-migration-plan.md#story-3-events-api-migration`

**Critical Issue - Risk 5**: Inconsistent Generated Folder Structure
Current event-management-service has:
- `openApiGenerate` (events-api) → `build/generated` ❌
- `openApiGenerateTopics` → `build/generated-topics` ✅
- `openApiGenerateUsersClient` → `build/generated-users-client` ✅

**This story MUST fix the inconsistency**.

### OpenAPI Spec Changes

**File**: `docs/api/events-api.openapi.yml`

**UUID → Meaningful ID Mapping**:
| Current Field | Change To | Example | Rationale |
|---------------|-----------|---------|-----------|
| `EventResponse.topicId: uuid` | `topicCode: string` | `cloud-architecture-patterns` | Reference by meaningful ID |
| `SessionResponse.id: uuid` | `sessionCode: string` | `batbern56-session-1` | Meaningful session identifier |
| `RegistrationResponse.id: uuid` | `registrationCode: string` | `REG-2024-001` | Unique registration identifier |

### Build Configuration Changes

**Fix in**: `services/event-management-service/build.gradle`

**BEFORE** (Inconsistent):
```gradle
task openApiGenerate(type: org.openapitools.generator.gradle.plugin.tasks.GenerateTask) {
    outputDir = "$buildDir/generated"  // ❌ No subdirectory
    modelPackage = 'ch.batbern.events.dto.generated'  // ❌ No package subdirectory
}
```

**AFTER** (Consistent):
```gradle
task openApiGenerateEvents(type: org.openapitools.generator.gradle.plugin.tasks.GenerateTask) {
    inputSpec = "$rootDir/docs/api/events-api.openapi.yml"
    outputDir = "$buildDir/generated-events"  // ✅ Consistent with Topics/Speakers
    generatorName = 'java'
    library = 'native'
    modelPackage = 'ch.batbern.events.dto.generated.events'  // ✅ Subdirectory
    generateModels = true
    generateApis = false
    generateModelTests = false
    generateModelDocumentation = false
    configOptions = [
        generateBuilders: 'true',
        dateLibrary: 'java8',
        serializationLibrary: 'jackson',
        useJakartaEe: 'true',
        openApiNullable: 'false'
    ]
}

compileJava.dependsOn tasks.openApiGenerateEvents
sourceSets.main.java.srcDirs += "$buildDir/generated-events/src/main/java"
```

### Import Updates Required

**Find & Replace**:
- Old: `import ch.batbern.events.dto.generated.*;`
- New: `import ch.batbern.events.dto.generated.events.*;`

**Files to Update**:
- All Event controllers
- All Event services
- All integration tests using generated DTOs

### Entity Migrations Required

**Session Entity** - Add `sessionCode` field:
```sql
-- VXX__add_session_code_column.sql
ALTER TABLE sessions
ADD COLUMN session_code VARCHAR(255) UNIQUE NOT NULL;

CREATE INDEX idx_sessions_session_code ON sessions(session_code);

COMMENT ON COLUMN sessions.session_code IS 'Meaningful identifier (e.g., batbern56-session-1)';
```

**Registration Entity** - Add `registrationCode` field:
```sql
-- VXX__add_registration_code_column.sql
ALTER TABLE registrations
ADD COLUMN registration_code VARCHAR(255) UNIQUE NOT NULL;

CREATE INDEX idx_registrations_registration_code ON registrations(registration_code);

COMMENT ON COLUMN registrations.registration_code IS 'Unique registration identifier (e.g., REG-2024-001)';
```

### Manual DTOs to Delete

**After migration is complete** (see Appendix C in plan):
```bash
# Event DTOs
rm services/event-management-service/src/main/java/ch/batbern/events/dto/EventResponse.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/CreateEventRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/UpdateEventRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/PatchEventRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/WorkflowStatusDto.java

# Session DTOs
rm services/event-management-service/src/main/java/ch/batbern/events/dto/SessionResponse.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/CreateSessionRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/UpdateSessionRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/SessionSpeakerResponse.java

# Registration DTOs
rm services/event-management-service/src/main/java/ch/batbern/events/dto/RegistrationResponse.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/CreateRegistrationResponse.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/PatchRegistrationRequest.java

# Batch DTOs
rm services/event-management-service/src/main/java/ch/batbern/events/dto/BatchUpdateRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/BatchImportSessionRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/BatchImportSessionResult.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/SessionImportDetail.java

# Workflow DTOs
rm services/event-management-service/src/main/java/ch/batbern/events/dto/AssignSpeakerRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerConfirmationRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/TransitionStateRequest.java
```

### Test Implementation Details

**CRITICAL**: All backend integration tests MUST use PostgreSQL via Testcontainers. NEVER use H2.

#### Test File Locations

**Backend Tests**:
- Integration: `services/event-management-service/src/test/integration/controller/EventControllerIntegrationTest.java`
- Integration: `services/event-management-service/src/test/integration/controller/SessionControllerIntegrationTest.java`
- Integration: `services/event-management-service/src/test/integration/controller/RegistrationControllerIntegrationTest.java`
- Unit: `services/event-management-service/src/test/unit/mapper/EventMapperTest.java`
- Unit: `services/event-management-service/src/test/unit/mapper/SessionMapperTest.java`
- Unit: `services/event-management-service/src/test/unit/mapper/RegistrationMapperTest.java`

**Frontend Tests** (major impact expected):
- `web-frontend/src/components/events/EventManagement.test.tsx`
- `web-frontend/src/components/events/SessionManagement.test.tsx`
- `web-frontend/src/services/eventService.test.ts`
- `web-frontend/src/services/sessionService.test.ts`
- `web-frontend/src/services/registrationService.test.ts`

### Story-Specific Implementation

**Deviations from Templates**:
```java
// To be filled during implementation
// ONLY code that differs from standard patterns
```

### Implementation Approach
{To be filled by dev agent during implementation}

### Debug Log
See: `.ai/debug-log.md#bat-19` for detailed implementation debugging

### Completion Notes
{To be filled by dev agent}

### File List
**Created**:
- {files}

**Modified**:
- {files}

**Deleted**:
- {files}

### Change Log
- {date}: {change}

### Deployment Notes
**Database Migrations Required**:
- VXX__add_session_code_column.sql
- VXX__add_registration_code_column.sql

**Breaking Changes**:
- API responses now use meaningful codes instead of UUIDs
- Frontend must update all event/session/registration API calls

### Status
Draft
