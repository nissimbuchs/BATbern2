# Story: Backend DTO Migration: Speakers API (ADR-003 + ADR-006)

**Linear Issue**: [BAT-18](https://linear.app/batbern/issue/BAT-18/backend-dto-migration-speakers-api-adr-003-adr-006) ← **PRIMARY SOURCE**

**Story File**: This file contains **ONLY** dev implementation notes

---

## ⚠️ IMPORTANT: Story Content Location

This file contains **ONLY** dev implementation notes. For story content, see Linear:

- **User Story**: [Linear description](https://linear.app/batbern/issue/BAT-18)
- **Acceptance Criteria**: [Linear issue](https://linear.app/batbern/issue/BAT-18) (see checkboxes)
- **Tasks/Subtasks**: [Linear subtasks](https://linear.app/batbern/issue/BAT-18)
- **QA Results**: [Linear comments](https://linear.app/batbern/issue/BAT-18)
- **Status**: [Linear workflow state](https://linear.app/batbern/issue/BAT-18)

---

## Dev Agent Record

### Agent Model Used
{To be filled by dev agent}

### Template References

**Implementation Patterns to Use**:
- Backend: `docs/templates/backend/spring-boot-service-foundation.md`
- Backend: `docs/templates/backend/integration-test-pattern.md`

**Existing Code References**:
- Reference Implementation: Story 1 (Topics API Migration) - BAT-17 COMPLETE
  - TopicMapper: `services/event-management-service/src/main/java/ch/batbern/events/mapper/TopicMapper.java`
  - TopicService: `services/event-management-service/src/main/java/ch/batbern/events/service/TopicService.java`
  - TopicController: `services/event-management-service/src/main/java/ch/batbern/events/controller/TopicController.java`
  - Integration Tests: `services/event-management-service/src/test/integration/controller/TopicControllerIntegrationTest.java`

### Migration Plan Reference

**Source**: `docs/plans/backend-dto-openapi-migration-plan.md#story-2-speakers-api-migration`

**Standard Migration Sequence** (10 steps from plan):
1. Update OpenAPI spec (ADR-003)
2. Configure code generation
3. Run build to generate DTOs
4. Create Pure Mapper class
5. Update service layer
6. Update controller layer
7. Delete manual DTOs
8. Update entity (if needed for ADR-003)
9. Regenerate frontend types
10. Run tests & commit

### OpenAPI Spec Changes

**File**: `docs/api/speakers-api.openapi.yml`

**UUID → Meaningful ID Mapping**:
| Current Field | Change To | Rationale |
|---------------|-----------|-----------|
| `SpeakerStatusResponse.speakerId: uuid` | `speakerUsername: string` | ADR-004: User is source of truth |
| `SpeakerPoolEntry.id: uuid` | Remove or use composite key | `eventCode + username` is unique |
| `SpeakerPoolEntry.eventId: uuid` | `eventCode: string` | Meaningful event identifier |
| `SpeakerPoolEntry.sessionId: uuid` | `sessionCode: string` | Meaningful session identifier |

### Build Configuration

**Add to**: `services/event-management-service/build.gradle`

```gradle
task openApiGenerateSpeakers(type: org.openapitools.generator.gradle.plugin.tasks.GenerateTask) {
    inputSpec = "$rootDir/docs/api/speakers-api.openapi.yml"
    outputDir = "$buildDir/generated-speakers"
    generatorName = 'java'
    library = 'native'
    modelPackage = 'ch.batbern.events.dto.generated.speakers'
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

compileJava.dependsOn tasks.openApiGenerateSpeakers
sourceSets.main.java.srcDirs += "$buildDir/generated-speakers/src/main/java"
```

### Manual DTOs to Delete

**After migration is complete**:
```bash
rm services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerStatusResponse.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerPoolResponse.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/StatusHistoryItem.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/StatusSummaryResponse.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/UpdateStatusRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/AddSpeakerToPoolRequest.java
```

### Test Implementation Details

**CRITICAL**: All backend integration tests MUST use PostgreSQL via Testcontainers. NEVER use H2.

#### Test File Locations

**Backend Tests**:
- Integration: `services/event-management-service/src/test/integration/controller/SpeakerControllerIntegrationTest.java`
- Integration: `services/event-management-service/src/test/integration/controller/SpeakerPoolControllerIntegrationTest.java`
- Unit: `services/event-management-service/src/test/unit/service/SpeakerServiceTest.java`
- Unit: `services/event-management-service/src/test/unit/mapper/SpeakerMapperTest.java`

**Frontend Tests**:
- `web-frontend/src/components/speakers/SpeakerManagement.test.tsx`
- `web-frontend/src/services/speakerService.test.ts`

#### Test Configuration

All integration tests MUST extend `AbstractIntegrationTest` which provides PostgreSQL via Testcontainers.

```java
@Transactional
class SpeakerControllerIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void should_returnSpeakerStatus_when_validUsernameProvided() throws Exception {
        // Test implementation
    }
}
```

### Story-Specific Implementation

**Deviations from Templates**:
```java
// To be filled during implementation
// ONLY code that differs from standard patterns
```

### Implementation Approach
{To be filled by dev agent during implementation}

### Debug Log
See: `.ai/debug-log.md#bat-18` for detailed implementation debugging

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
{Special deployment considerations}

### Status
Draft
