# Event Management API Migration - Findings & Recommendations

**Status**: BLOCKED - Requires Architectural Planning
**Created**: 2026-01-04
**Related Stories**: BAT-19 (Events API Migration), BAT-87 (Controller Migration)
**Related ADRs**: ADR-003 (Meaningful Identifiers), ADR-006 (OpenAPI Contract-First)

## Executive Summary

Attempted migration of Event Management API controllers to OpenAPI-generated DTOs revealed **significant architectural challenges** that require dedicated planning before proceeding. The task (BAT-87) was paused after discovering the scope is 3-4 full work days with multiple blocking issues.

**Key Finding**: Previous partial migrations (Tasks 7-9) left the codebase in an **inconsistent state** with compilation errors and type conflicts that cascade when attempting to complete the migration.

## Background

### What Was Attempted (BAT-19, Tasks 1-9)

| Phase | Status | Details |
|-------|--------|---------|
| Tasks 1-2 | ✅ Complete | OpenAPI spec updates, folder structure fixes |
| Tasks 3-5 | ✅ Complete | Import updates, entity migrations, build verification |
| Tasks 6-6b | ✅ Complete | Pure Mappers created (EventMapper, SessionMapper, RegistrationMapper) |
| Task 7 | ⚠️ **Partial** | Service layer migration - RegistrationService migrated, others incomplete |
| Task 8 | ⚠️ **Partial** | Controller layer - ONLY Registration endpoints migrated, Event CRUD left untouched |
| Task 9 | ✅ Complete | Integration tests updated for Registration endpoints |
| **Task 10 (BAT-87)** | ❌ **BLOCKED** | Controller migration incomplete, requires architectural planning |

### Current State Assessment

**What Works:**
- ✅ OpenAPI spec expanded with 29+ schemas (Tasks 10a-10e completed)
- ✅ Generated DTOs available in `build/generated-events/`
- ✅ Pure Mappers exist (EventMapper, SessionMapper, RegistrationMapper)
- ✅ Registration endpoints fully migrated and working

**What's Broken:**
- ❌ Event CRUD endpoints still use manual DTOs
- ❌ EventController has type conflicts (domain.Registration vs dto.generated.Registration)
- ❌ 6 additional controllers not migrated (SessionController, EventWorkflowController, etc.)
- ❌ Codebase won't compile due to incomplete migrations

## Critical Issues Discovered

### 1. Naming Conflicts

**Problem**: Domain entity and generated DTO both named `Registration`

```java
// EventController imports
import ch.batbern.events.domain.Registration;  // Domain entity
import ch.batbern.events.dto.generated.events.Registration;  // Generated DTO - CONFLICT!
```

**Impact**:
- Cannot use simple imports - requires fully qualified names throughout
- Verbose code: `ch.batbern.events.dto.generated.events.Registration` everywhere
- Increases cognitive load and error potential

**Affected Classes**:
- `Registration` (domain vs DTO)
- Potentially others if pattern repeats

### 2. Type System Incompatibilities

**Problem**: Manual vs Generated DTOs use different types for same fields

| Field | Manual DTO Type | Generated DTO Type | Impact |
|-------|----------------|-------------------|---------|
| Date fields | `Instant` | `OffsetDateTime` | Requires conversion in all mappers |
| URLs | `String` | `URI` | URL parsing/validation logic needed |
| Enums | `String` | Custom enum types | Type conversions required |

**Example from EventResponse**:
```java
// Manual DTO
private Instant date;
private String themeImageUrl;

// Generated DTO
private OffsetDateTime date;
private URI themeImageUrl;
```

**Impact**: Can't just swap imports - requires refactoring all mapping logic.

### 3. Incomplete Service Layer Migration

**Problem**: RegistrationService migrated but EventService/SessionService not migrated

```java
// RegistrationService (MIGRATED - Task 7)
public ch.batbern.events.dto.generated.events.Registration enrichRegistrationWithUserData(Registration entity) {
    // Returns generated DTO
}

// EventService (NOT MIGRATED)
// Still uses manual DTOs or Map<String, Object>
```

**Impact**:
- EventController expects generated DTOs from service layer but doesn't get them
- Controllers stuck using `Map<String, Object>` responses instead of typed DTOs
- Business logic scattered between controller and service layers

### 4. Business Logic Embedded in Controllers

**Problem**: Controllers have complex business logic that can't be simply switched to generated DTOs

**Examples from EventController**:
- `buildBasicEventResponse()` - Manually constructs Map responses
- `associateThemeImage()` - Complex 3-phase S3 upload logic
- `applyResourceExpansions()` - Dynamic resource inclusion logic
- Cache management (Caffeine) integrated into endpoint methods
- Domain event publishing embedded in CRUD operations

**Impact**: Migration isn't just "change DTOs" - requires **refactoring business logic** out of controllers.

### 5. Scope Underestimation

**Problem**: Task BAT-87 treated as single-session work but is actually multi-day effort

**Actual Scope**:
| Controller | Endpoints Requiring Migration | Estimated Hours |
|-----------|-------------------------------|-----------------|
| EventController | 10 Event CRUD endpoints | 3-4 hours |
| SessionController | 6 session endpoints | 2-3 hours |
| EventWorkflowController | 5 workflow endpoints | 2-3 hours |
| SessionSpeakerController | 4 speaker endpoints | 2 hours |
| PublishingEngineController | 8 publishing endpoints | 3 hours |
| EventTaskController | 6 task endpoints | 2 hours |
| TaskTemplateController | 4 template endpoints | 2 hours |
| **Integration Tests** | All affected tests | 4-6 hours |
| **Total** | **43+ endpoints** | **19-28 hours** |

### 6. Test Suite Fragility

**Problem**: Integration tests tightly coupled to DTO structure

**Examples**:
- Tests use `Map<String, Object>` assertions instead of typed DTOs
- Tests import manual DTOs that should be deleted
- Test data builders use manual DTO constructors

**Impact**: Every DTO change breaks multiple tests, cascading failures.

## Migration Attempt Results

### What Was Tried (2026-01-04 Session)

1. ✅ Enhanced EventMapper with `applyUpdateRequest()` and `applyPatchRequest()`
2. ❌ Updated EventController imports → **32 compilation errors**
3. ❌ Attempted to fix type conflicts → **27 cascading errors**
4. ❌ Attempted fully qualified names → **type mismatch errors**

### Build Failure Analysis

**Initial import changes**: 32 errors
- EventResponse.fromEntity() doesn't exist (manual DTO pattern)
- BatchUpdateRequest field mismatches
- PatchEventRequest method mismatches

**After type conflict fixes**: 27 errors
- domain.Registration vs dto.Registration conflicts
- Instant vs OffsetDateTime mismatches
- String vs URI conversion errors

**Conclusion**: Wholesale import replacement doesn't work - requires systematic refactoring.

## Architectural Recommendations

### Option 1: Multi-Phase Incremental Migration (Recommended)

**Approach**: Systematic controller-by-controller migration with architectural planning

**Phase 1: Architectural Preparation** (4-6 hours)
1. Design DTO naming convention to avoid conflicts (e.g., `RegistrationDto` suffix)
2. Create comprehensive type conversion utilities
3. Design service layer contract (all services return generated DTOs)
4. Create controller base class for common patterns (caching, domain events)
5. Document business logic extraction patterns

**Phase 2: Service Layer Completion** (6-8 hours)
1. Migrate EventService to return generated DTOs
2. Migrate SessionService to return generated DTOs
3. Extract business logic from controllers to services
4. Update all service tests

**Phase 3: Controller Migration** (8-12 hours)
1. One controller at a time:
   - Replace manual DTO imports
   - Use mappers for conversions
   - Replace Map responses with typed DTOs
   - Update integration tests
   - Verify build passes
2. Controllers in order:
   a. EventController Event CRUD (highest complexity)
   b. SessionController
   c. EventWorkflowController
   d. SessionSpeakerController
   e. PublishingEngineController
   f. EventTaskController
   g. TaskTemplateController

**Phase 4: Cleanup & Validation** (2-4 hours)
1. Delete all manual DTOs (BAT-88)
2. Run full test suite
3. Update documentation
4. Code review

**Total Estimate**: 20-30 hours (3-4 full work days)

**Benefits**:
- Incremental progress tracking
- Can pause/resume at any phase
- Lower risk of cascading failures
- Better code review opportunities

### Option 2: Big Bang Refactoring (Not Recommended)

**Approach**: Try to fix all issues at once

**Risks**:
- High probability of getting stuck in compilation error loops
- Difficult to track progress
- Hard to review large changesets
- Easy to introduce bugs

**Only consider if**: Tight deadline with no alternatives

### Option 3: Defer Event Management Migration

**Approach**: Focus on simpler APIs first (Companies, Users)

**Rationale**:
- Event Management is the most complex API (43+ endpoints)
- Other APIs may be simpler and provide learning opportunities
- Can apply lessons learned to Event Management later

**Trade-offs**:
- Delays ADR compliance for core domain
- Mixed DTO patterns in codebase longer

## Specific Technical Recommendations

### 1. Resolve Naming Conflicts

**Recommended Pattern**: Use `Dto` suffix for all generated DTOs

```java
// In OpenAPI spec, change schema names:
RegistrationResponse → RegistrationDto
EventResponse → EventDto
SessionResponse → SessionDto
```

**Benefits**:
- Clear distinction between domain and DTO layers
- No import conflicts
- Aligns with common DTO naming conventions

**Impact**: Requires regenerating OpenAPI specs and DTOs.

### 2. Create Type Conversion Utility

**Recommended**: Centralized conversion for common types

```java
// TypeConversionUtil.java
public class TypeConversionUtil {
    public static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }

    public static Instant toInstant(OffsetDateTime offsetDateTime) {
        return offsetDateTime != null ? offsetDateTime.toInstant() : null;
    }

    public static URI toURI(String uriString) {
        // Safe conversion with error handling
    }

    public static String fromURI(URI uri) {
        return uri != null ? uri.toString() : null;
    }
}
```

**Benefits**:
- DRY principle - conversion logic in one place
- Consistent error handling
- Easy to unit test

### 3. Refactor Service Layer First

**Critical**: Complete service layer before touching controllers

**Pattern**:
```java
@Service
public class EventService {
    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    // ALL public methods return generated DTOs
    public ch.batbern.events.dto.generated.events.EventDto getEvent(String eventCode) {
        Event entity = eventRepository.findByEventCode(eventCode)
            .orElseThrow(() -> new EventNotFoundException(eventCode));
        return eventMapper.toDto(entity);
    }

    // Business logic in service, not mapper or controller
    public void associateThemeImage(Event event, String uploadId) {
        // Complex logic here
    }
}
```

**Benefits**:
- Controllers become thin (routing + validation only)
- Business logic properly layered
- Easier to test

### 4. Create Controller Base Class

**Recommended**: Extract common controller patterns

```java
@RestController
public abstract class BaseEventController {

    protected final DomainEventPublisher eventPublisher;
    protected final CacheManager cacheManager;
    protected final SecurityContextHelper securityContextHelper;

    // Common methods for all controllers
    protected void publishDomainEvent(DomainEvent event) { }
    protected void evictCache(String cacheName) { }
    protected String getCurrentUserId() { }
}
```

**Benefits**:
- DRY for cross-cutting concerns
- Easier to add features (logging, monitoring)
- Consistent patterns across controllers

## Risk Assessment

### High Risk Items

1. **Type Conflicts** - Naming collision between domain and DTO layers
   - **Mitigation**: Adopt DTO suffix convention

2. **Cascading Test Failures** - Integration tests tightly coupled to DTOs
   - **Mitigation**: Update tests incrementally with controllers

3. **Business Logic Extraction** - Moving logic out of controllers could introduce bugs
   - **Mitigation**: Comprehensive test coverage before refactoring

### Medium Risk Items

1. **Scope Creep** - Controllers may reveal additional complexity
   - **Mitigation**: Time-box each controller, document unknowns

2. **Type Conversion Bugs** - Instant↔OffsetDateTime, String↔URI conversions
   - **Mitigation**: Centralized conversion utility with unit tests

### Low Risk Items

1. **Build Performance** - More generated code to compile
   - **Mitigation**: Already generating DTOs, minimal impact

## Dependencies & Blockers

### Blockers

1. ❌ **Architectural Decision** - Must choose Option 1, 2, or 3
2. ❌ **Resource Allocation** - Need 20-30 hours dedicated time
3. ❌ **DTO Naming Convention** - Must resolve before proceeding

### Dependencies

1. OpenAPI spec updates (already complete via BAT-82-86)
2. Generated DTOs available (already complete)
3. Pure Mappers created (already complete)

## Next Steps

### Immediate (Before Any Code Changes)

1. **Review this findings document** with tech lead/architect
2. **Choose migration approach** (Option 1 recommended)
3. **Decide on DTO naming convention** (Dto suffix recommended)
4. **Allocate dedicated time** (3-4 full days)
5. **Create detailed task breakdown** if Option 1 chosen

### If Option 1 Chosen (Recommended)

**Week 1**:
- Day 1: Architectural preparation (naming, utilities, patterns)
- Day 2-3: Service layer completion (EventService, SessionService)
- Day 4: EventController migration + tests

**Week 2**:
- Day 1-2: Remaining 6 controllers migration
- Day 3: Integration testing, cleanup, BAT-88 (delete manual DTOs)

### If Option 3 Chosen (Defer)

1. Mark BAT-19 as blocked pending architectural decisions
2. Move to BAT-20 (Companies API) or BAT-21 (Users API)
3. Apply lessons learned to Event Management later

## Lessons Learned

### What Went Well

1. ✅ Pure Mapper pattern established (Tasks 6-6b)
2. ✅ OpenAPI spec expansion completed (Tasks 10a-10e)
3. ✅ Early detection of architectural issues (before full implementation)

### What Went Wrong

1. ❌ Underestimated scope (treated as single session vs multi-day)
2. ❌ Partial migrations left codebase in inconsistent state
3. ❌ Didn't address naming conflicts proactively
4. ❌ Attempted wholesale changes instead of incremental approach

### Recommendations for Future Migrations

1. **Always estimate in days, not hours** for API migrations
2. **Complete service layer before touching controllers**
3. **Migrate one controller at a time** with tests
4. **Establish naming conventions before code generation**
5. **Don't leave partial migrations** - finish what you start or revert completely

## Appendix A: File Impact Analysis

### Files Modified (Incomplete Changes - Need Rollback)

```
M services/event-management-service/src/main/java/ch/batbern/events/controller/EventController.java
M services/event-management-service/src/main/java/ch/batbern/events/controller/SessionController.java
M services/event-management-service/src/main/java/ch/batbern/events/mapper/EventMapper.java
M services/event-management-service/src/main/java/ch/batbern/events/mapper/RegistrationMapper.java
M services/event-management-service/src/main/java/ch/batbern/events/service/RegistrationService.java
M services/event-management-service/src/main/java/ch/batbern/events/service/SessionService.java
?? services/event-management-service/src/main/java/ch/batbern/events/controller/EventController.java.bak2
```

### Compilation Status

- **Before BAT-87 attempt**: ⚠️ Unstable (from Tasks 7-9 partial migration)
- **After BAT-87 attempt**: ❌ Broken (32+ compilation errors)
- **Recommended**: Rollback to post-BAT-18 state

## Appendix B: Linear Issue References

- **BAT-19**: Events API Migration (parent epic)
- **BAT-82**: Task 10a - Event Task schemas (✅ Complete)
- **BAT-83**: Task 10b - Publishing Engine schemas (✅ Complete)
- **BAT-84**: Task 10c - Speaker & Session schemas (✅ Complete)
- **BAT-85**: Task 10d - Event/Registration wrappers (✅ Complete)
- **BAT-86**: Task 10e - Regenerate DTOs (✅ Complete)
- **BAT-87**: Task 10f - Controller migration (❌ Blocked - this document)
- **BAT-88**: Task 10g - Delete manual DTOs (🔲 Blocked by BAT-87)

## Document History

| Date | Author | Changes |
|------|--------|---------|
| 2026-01-04 | Claude (Dev Agent) | Initial findings document created after BAT-87 investigation |

---

**Status**: AWAITING ARCHITECTURAL DECISION
**Recommended Action**: Review with tech lead, choose Option 1, allocate 3-4 days for dedicated migration sprint
