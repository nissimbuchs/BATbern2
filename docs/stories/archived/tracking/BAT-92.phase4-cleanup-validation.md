# Story: Phase 4 - Cleanup & Validation

**Linear Issue**: [BAT-92](https://linear.app/batbern/issue/BAT-92) ← **PRIMARY SOURCE**

**Story File**: This file contains **ONLY** dev implementation notes

---

## ⚠️ IMPORTANT: Story Content Location

This file contains **ONLY** dev implementation notes. For story content, see Linear:

- **User Story**: [Linear description](https://linear.app/batbern/issue/BAT-92)
- **Acceptance Criteria**: [Linear issue](https://linear.app/batbern/issue/BAT-92) (see ACs section)
- **Tasks/Subtasks**: [Linear task checklists](https://linear.app/batbern/issue/BAT-92)
- **QA Results**: [Linear comments](https://linear.app/batbern/issue/BAT-92)
- **Status**: [Linear workflow state](https://linear.app/batbern/issue/BAT-92)

---

## Dev Agent Record

### Agent Model Used
{To be filled by dev agent}

### Template References

**Implementation Patterns to Use**:
- Frontend type generation: Existing OpenAPI generation scripts
- Bruno test patterns: Existing API contract tests

**Existing Code References**:
- Phases 1-3: All migrated code
- Frontend: `web-frontend/package.json` (generate:api-types script)

### Test Implementation Details (HOW to test)

**CRITICAL**: Full regression testing required - all tests must pass.

#### Test File Locations (Exact Paths)

**Backend Tests**:
- All: `services/event-management-service/src/test/` (unit + integration)

**Frontend Tests**:
- All: `web-frontend/src/**/*.test.{ts,tsx}`

**E2E Tests**:
- Bruno: `bruno-tests/**/*.bru`

#### Test Execution Commands

```bash
# Backend full suite
./gradlew :services:event-management-service:test

# Frontend full suite
cd web-frontend && npm test

# Bruno API contract tests
./scripts/ci/run-bruno-tests.sh
```

### Story-Specific Implementation

#### Task 4.1: Delete Manual DTOs

**Base Directory**:
`services/event-management-service/src/main/java/ch/batbern/events/dto/`

**46 Files to Delete** (organized by category):

**Event DTOs (4 files)**:
```bash
rm services/event-management-service/src/main/java/ch/batbern/events/dto/EventResponse.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/CreateEventRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/UpdateEventRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/PatchEventRequest.java
```

**Session DTOs (4 files)**:
```bash
rm services/event-management-service/src/main/java/ch/batbern/events/dto/SessionResponse.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/CreateSessionRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/UpdateSessionRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/SessionSpeakerResponse.java
```

**Registration DTOs (3 files)**:
```bash
rm services/event-management-service/src/main/java/ch/batbern/events/dto/RegistrationResponse.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/CreateRegistrationResponse.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/PatchRegistrationRequest.java
```

**Batch DTOs (4 files)**:
```bash
rm services/event-management-service/src/main/java/ch/batbern/events/dto/BatchUpdateRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/BatchImportSessionRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/BatchImportSessionResult.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/SessionImportDetail.java
```

**Workflow DTOs (4 files)**:
```bash
rm services/event-management-service/src/main/java/ch/batbern/events/dto/TransitionStateRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/WorkflowStatusDto.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/AssignSpeakerRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerConfirmationRequest.java
```

**Publishing DTOs (4 files)**:
```bash
rm services/event-management-service/src/main/java/ch/batbern/events/dto/PublishingStatusResponse.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/PublishPhaseResponse.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/PublishPreviewResponse.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/PublishValidationError.java
```

**Task DTOs (5 files)**:
```bash
rm services/event-management-service/src/main/java/ch/batbern/events/dto/EventTaskResponse.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/TaskTemplateResponse.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/CreateEventTaskRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/CreateTaskTemplateRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/UpdateTaskTemplateRequest.java
```

**Speaker Pool DTOs (3 files)**:
```bash
rm services/event-management-service/src/main/java/ch/batbern/events/dto/AddSpeakerToPoolRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerPoolResponse.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerContentResponse.java
```

**Additional DTOs (15+ more files from plan - search dto/ directory for complete list)**

**KEEP (Internal DTOs - DO NOT DELETE)**:
```bash
# KEEP these 2 files - they are internal utilities, not API DTOs
# services/event-management-service/src/main/java/ch/batbern/events/dto/TopicFilterRequest.java
# services/event-management-service/src/main/java/ch/batbern/events/dto/TopicUsageHistoryWithEventDetails.java
```

**Verification Commands**:
```bash
# Count remaining DTOs (should be ~2)
find services/event-management-service/src/main/java/ch/batbern/events/dto/ -name "*.java" | wc -l

# Verify no imports of deleted DTOs (should return 0)
grep -r "import ch.batbern.events.dto.EventResponse" services/event-management-service/src/

# Verify compilation still works
./gradlew :services:event-management-service:compileJava
```

#### Task 4.2: Frontend Type Regeneration

**Commands**:
```bash
cd web-frontend

# Regenerate types from updated OpenAPI spec
npm run generate:api-types

# Verify generated types
ls src/types/api/events.types.ts

# Check for renamed types
grep "export interface EventResponse" src/types/api/events.types.ts
grep "export interface SessionResponse" src/types/api/events.types.ts
```

**Frontend Files to Update** (search for old type names):
```bash
# Find files using old Event type (should now use EventResponse)
grep -r "import.*Event.*from.*events.types" web-frontend/src/

# Common locations:
# - web-frontend/src/services/eventService.ts
# - web-frontend/src/hooks/useEvent.ts
# - web-frontend/src/components/Events/*.tsx
```

**Example Frontend Update**:
```typescript
// BEFORE:
import { Event, Session } from '@/types/api/events.types';

// AFTER:
import { EventResponse, SessionResponse } from '@/types/api/events.types';

// Update all type annotations:
// const event: Event → const event: EventResponse
// const sessions: Session[] → const sessions: SessionResponse[]
```

**Verification**:
```bash
# Type check
npm run type-check

# Run tests
npm test
```

#### Task 4.3: Full Test Suite Execution

**Backend Tests**:
```bash
./gradlew :services:event-management-service:test

# Expected output:
# - ~80+ tests passing (unit + integration)
# - Coverage >80% integration, >90% unit
# - BUILD SUCCESSFUL

# Generate coverage report
./gradlew :services:event-management-service:jacocoTestReport
# View: services/event-management-service/build/reports/jacoco/test/html/index.html
```

**Frontend Tests**:
```bash
cd web-frontend
npm test

# Expected output:
# - All 2777 tests passing
# - No new failures from type name changes
```

**Bruno API Contract Tests**:
```bash
./scripts/ci/run-bruno-tests.sh

# If failures due to renamed types, update Bruno test assertions:
# - bruno-tests/events/*.bru
# - Update JSON schema references from Event to EventResponse
```

#### Task 4.4: Documentation Updates

**File 1: Migration Plan**
```bash
# File: docs/plans/backend-dto-openapi-migration-plan.md
# Changes:
# - Mark Story 3 (Events API) as COMPLETE
# - Add lessons learned section with:
#   - Response/Request naming convention insight
#   - TypeConversionUtil utility value
#   - Service layer contract pattern success
```

**File 2: ADR-006**
```bash
# File: docs/architecture/ADR-006-openapi-contract-first-code-generation.md
# Changes:
# - Add section: "Response/Request Naming Convention"
# - Document TypeConversionUtil pattern
# - Add example of pure mapper pattern
```

**File 3: Story BAT-19**
```bash
# File: docs/stories/BAT-19.events-api-migration.md (if exists)
# Changes:
# - Mark all tasks as complete
# - Add migration completion summary
# - Document final outcomes (46 DTOs deleted, type-safe codebase)
```

#### Task 4.5: Linear Issue Updates

**Commands**:
```bash
# Use Linear MCP to update issues:
# BAT-19: Status → Done, add completion comment
# BAT-87: Status → Done, add "All 7 controllers migrated successfully"
# BAT-88: Status → Done, add "46 manual DTOs deleted, 2 internal exceptions kept"
```

**Example Linear Comment for BAT-19**:
```markdown
## Migration Complete ✅

The Event Management Service OpenAPI migration is now complete across all 4 phases:

**Phase 1**: OpenAPI schemas renamed to Response/Request pattern
**Phase 2**: Service layer returns typed DTOs (EventResponse, SessionResponse, etc.)
**Phase 3**: All 7 controllers migrated to use generated DTOs
**Phase 4**: 46 manual DTOs deleted, frontend types regenerated, full test suite passing

**Outcomes**:
- ✅ Zero Map<String, Object> in codebase
- ✅ Full type safety across backend
- ✅ ADR-006 compliant (OpenAPI contract-first)
- ✅ ~80+ tests passing
- ✅ No regressions detected

**Total Effort**: ~24 hours (within 20-30 hour estimate)
```

### API Contracts (OpenAPI Excerpts)

No changes - OpenAPI spec finalized in Phase 1.

### Database Schema (SQL)

No database changes - cleanup phase only.

### Implementation Approach
{To be filled by dev agent during implementation}

### Debug Log
See: `.ai/debug-log.md#BAT-92` for detailed implementation debugging

### Completion Notes
{To be filled by dev agent}

### File List
**Created**:
- None

**Modified**:
- docs/plans/backend-dto-openapi-migration-plan.md
- docs/architecture/ADR-006-openapi-contract-first-code-generation.md
- docs/stories/BAT-19.events-api-migration.md
- web-frontend/src/types/api/events.types.ts (regenerated)
- web-frontend/src/services/*.ts (type imports)
- web-frontend/src/components/**/*.tsx (type annotations)

**Deleted**:
- 46 manual DTO files (see Task 4.1 for complete list)

### Change Log
- {date}: {change}

### Deployment Notes
Migration complete - ready for production deployment. All endpoints now use generated DTOs with full type safety.

### Status
Backlog
