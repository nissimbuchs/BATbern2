# OpenAPI DTO Migration Guide

**Purpose**: Step-by-step guide for migrating existing manual DTOs to OpenAPI-generated DTOs

**Related ADR**: [ADR-006: OpenAPI Contract-First Code Generation](../architecture/ADR-006-openapi-contract-first-code-generation.md)

**Last Updated**: 2026-01-04

---

## Overview

This guide provides the practical step-by-step process for migrating existing manual DTOs to OpenAPI-generated DTOs. For the architectural patterns and decisions behind this approach, see **[ADR-006](../architecture/ADR-006-openapi-contract-first-code-generation.md)**.

**When to use this guide**:
- Migrating an existing API from manual DTOs to generated DTOs
- Adding OpenAPI code generation to a service
- Coordinating ADR-003 (meaningful IDs) with ADR-006 (generated DTOs)

**Prerequisites**:
- Understand Pure Mapper pattern (see ADR-006, Section 2)
- Understand Layered Architecture (see ADR-006, Diagram in Section 2)
- Have OpenAPI spec for the API (or create one first)

---

## Standard Migration Sequence (10 Steps)

Each API migration follows this **exact sequence**:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Step 1: UPDATE OPENAPI SPEC (ADR-003)                                      │
│  - Replace UUIDs with meaningful identifiers (topicCode, eventCode, etc.)   │
│  - Update path parameters (/{id} → /{topicCode})                            │
│  - Add pattern validation (^[a-z0-9-]+$)                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│  Step 2: CONFIGURE CODE GENERATION                                          │
│  - Add openApiGenerate task to build.gradle                                 │
│  - Set generateBuilders: 'true'                                             │
│  - Configure modelPackage: 'ch.batbern.*.dto.generated.*'                   │
├─────────────────────────────────────────────────────────────────────────────┤
│  Step 3: RUN BUILD TO GENERATE DTOs                                         │
│  - ./gradlew :services:<service>:compileJava                                │
│  - Verify generated classes in build/generated-*/                           │
├─────────────────────────────────────────────────────────────────────────────┤
│  Step 4: CREATE PURE MAPPER CLASS                                           │
│  - Create XxxMapper.java for entity↔DTO conversion                          │
│  - NO repository dependencies (Pure Mapper pattern)                         │
│  - Implement toDto(Entity) → Generated DTO                                  │
│  - Implement toEntity(Generated DTO) → Entity                               │
│  - Handle type conversions (LocalDateTime → OffsetDateTime)                 │
│  - Delegate business logic to Service static methods                        │
├─────────────────────────────────────────────────────────────────────────────┤
│  Step 5: UPDATE SERVICE LAYER                                               │
│  - Add business logic methods (static if pure functions)                    │
│    - calculateColorZone(), calculateStatus()                                │
│    - convertSimilarityScoresToDtos() (batch queries)                        │
│  - Inject Mapper into service constructor                                   │
│  - Change method signatures to return Generated DTOs                        │
│  - Use mapper.toDto() when returning to controller                          │
│  - Keep internal logic working with entities                                │
│  - Remove all manual DTO returns                                            │
├─────────────────────────────────────────────────────────────────────────────┤
│  Step 6: UPDATE CONTROLLER LAYER                                            │
│  - Update imports: ch.batbern.*.dto.* → ch.batbern.*.dto.generated.*        │
│  - Inject Mapper (if direct entity access needed)                           │
│  - Update method signatures to accept/return Generated DTOs                 │
│  - Call service methods (which now return Generated DTOs)                   │
│  - Remove manual DTO conversions                                            │
├─────────────────────────────────────────────────────────────────────────────┤
│  Step 7: DELETE MANUAL DTOs                                                 │
│  - Remove old manual DTO files from src/main/java/.../dto/                  │
│  - Verify no remaining imports (compile check)                              │
├─────────────────────────────────────────────────────────────────────────────┤
│  Step 8: UPDATE ENTITY (if needed for ADR-003)                              │
│  - Add meaningful ID field (topicCode, eventCode)                           │
│  - Create Flyway migration                                                  │
│  - Add repository methods (findByCode, etc.)                                │
├─────────────────────────────────────────────────────────────────────────────┤
│  Step 9: REGENERATE FRONTEND TYPES                                          │
│  - npm run generate:api-types                                               │
│  - Fix TypeScript errors in components                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│  Step 10: RUN TESTS & COMMIT                                                │
│  - Backend: ./gradlew :services:<service>:test                              │
│  - Frontend: npm test                                                       │
│  - Update Bruno API tests                                                   │
│  - Commit changes                                                           │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Step 1: Update OpenAPI Spec (ADR-003 Coordination)

### 1.1 Reconcile Fields with Existing Manual DTOs

**CRITICAL**: Before generating DTOs, the OpenAPI spec must be reconciled with manual DTOs. The OpenAPI spec becomes the source of truth - any discrepancy must be resolved first.

**Process**:
1. Load existing manual DTO classes
2. Load OpenAPI spec schemas
3. Compare field-by-field
4. Document every discrepancy
5. Make decisions on conflicts
6. Update OpenAPI spec to be authoritative

**Example Reconciliation Table**:

| Field | OpenAPI Spec | Manual DTO | Decision |
|-------|--------------|------------|----------|
| `description` maxLength | 5000 | 2000 | ✅ Use 5000 (more generous) |
| `logoUploadId` | ❌ MISSING | ✅ String | ✅ Add to OpenAPI spec |
| `keywords` | ✅ string[] | ❌ MISSING | ❌ Remove from spec (not implemented) |

**Reference**: See `docs/plans/backend-dto-openapi-migration-plan.md` Appendix D for detailed reconciliation examples.

### 1.2 Replace UUIDs with Meaningful Identifiers

**For ADR-003 compliance**, replace all UUID fields with meaningful string identifiers:

**Before**:
```yaml
components:
  schemas:
    Topic:
      properties:
        id:
          type: string
          format: uuid  # ❌ ADR-003 violation
    SimilarityScore:
      properties:
        topicId:
          type: string
          format: uuid  # ❌ ADR-003 violation
```

**After**:
```yaml
components:
  schemas:
    Topic:
      properties:
        topicCode:
          type: string
          pattern: '^[a-z0-9-]+$'  # ✅ ADR-003 compliant
          example: 'cloud-architecture-patterns'
    SimilarityScore:
      properties:
        topicCode:
          type: string
          pattern: '^[a-z0-9-]+$'  # ✅ ADR-003 compliant
```

### 1.3 Update Path Parameters

**Before**: `GET /topics/{id}`
**After**: `GET /topics/{topicCode}`

```yaml
paths:
  /topics/{topicCode}:  # ✅ Meaningful identifier
    parameters:
      - name: topicCode
        in: path
        required: true
        schema:
          type: string
          pattern: '^[a-z0-9-]+$'
```

---

## Step 2: Configure Code Generation

### 2.1 Add OpenAPI Generator Task to build.gradle

**IMPORTANT**: Use consistent naming and folder structure across all services.

**Consistent Pattern**:
- Task name: `openApiGenerate{ApiName}` (e.g., `openApiGenerateTopics`, `openApiGenerateSpeakers`)
- Output dir: `build/generated-{api-name}` (e.g., `build/generated-topics`)
- Model package: `ch.batbern.{service}.dto.generated.{api-name}` (e.g., `dto.generated.topics`)

**Example**:
```gradle
plugins {
    id 'org.openapi.generator' version '7.2.0'
}

task openApiGenerateTopics(type: org.openapitools.generator.gradle.plugin.tasks.GenerateTask) {
    inputSpec = "$rootDir/docs/api/topics-api.openapi.yml"
    outputDir = "$buildDir/generated-topics"
    generatorName = 'java'
    library = 'native'
    modelPackage = 'ch.batbern.events.dto.generated.topics'
    generateModels = true
    generateApis = false
    generateModelTests = false
    generateModelDocumentation = false
    configOptions = [
        generateBuilders: 'true',           // ✅ CRITICAL: Enable builder pattern
        dateLibrary: 'java8',
        serializationLibrary: 'jackson',
        useJakartaEe: 'true',
        openApiNullable: 'false'
    ]
}

compileJava.dependsOn tasks.openApiGenerateTopics
sourceSets.main.java.srcDirs += "$buildDir/generated-topics/src/main/java"
```

### 2.2 Verify Consistent Configuration Across Services

**Check for inconsistencies** (Risk from migration plan):

| Service | Task Name | Output Dir | Model Package |
|---------|-----------|-----------|---------------|
| event-management | `openApiGenerateTopics` | `build/generated-topics` ✅ | `dto.generated.topics` ✅ |
| event-management | `openApiGenerateEvents` | `build/generated-events` ✅ | `dto.generated.events` ✅ |
| event-management | ~~`openApiGenerate`~~ | ~~`build/generated`~~ ❌ | ~~`dto.generated`~~ ❌ |

**Fix inconsistencies immediately** to avoid package collisions.

---

## Step 3: Run Build & Verify Generated DTOs

```bash
# Run build
./gradlew :services:event-management-service:compileJava

# Verify generated files
ls services/event-management-service/build/generated-topics/src/main/java/ch/batbern/events/dto/generated/topics/

# Expected output:
# Topic.java
# TopicBuilder.java (if generateBuilders: 'true')
# CreateTopicRequest.java
# UpdateTopicRequest.java
# SimilarityScore.java
# etc.
```

**Verify**:
- ✅ Generated DTOs exist in correct package
- ✅ Builder pattern is available (`.builder()` method exists)
- ✅ Jackson annotations present (`@JsonProperty`, etc.)
- ✅ Validation annotations present (`@NotNull`, `@Size`, etc.)

---

## Step 4: Create Pure Mapper Class

### 4.1 Pure Mapper Pattern (from ADR-006)

**Definition**: A mapper class that performs ONLY entity↔DTO conversions, with NO business logic and NO repository dependencies.

**What Pure Mappers DO**:
- ✅ Entity → DTO field mapping
- ✅ DTO → Entity field mapping
- ✅ Type conversions (LocalDateTime → OffsetDateTime)
- ✅ Null handling

**What Pure Mappers DO NOT DO**:
- ❌ Business logic (calculations, validations)
- ❌ Database queries (no repository dependencies)
- ❌ Data enrichment (batch fetching related entities)

**Reference**: See [ADR-006, Section 2: Pure Mapper Pattern](../architecture/ADR-006-openapi-contract-first-code-generation.md#2-backend-generated-interfaces--manual-implementation)

### 4.2 Implementation Template

```java
package ch.batbern.events.mapper;

import ch.batbern.events.domain.Topic;
import ch.batbern.events.dto.generated.topics.Topic as TopicDto;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Pure mapper for converting between Topic entities and generated DTOs.
 *
 * Pattern: Pure Mapper (ADR-006)
 * - Field mapping only
 * - Type conversions (LocalDateTime → OffsetDateTime)
 * - NO business logic (delegate to TopicService)
 * - NO repository dependencies
 * - NO complex data enrichment
 */
@Component
public class TopicMapper {

    /**
     * Convert Topic entity to generated DTO.
     * Business logic (colorZone, status calculation) handled by TopicService.
     */
    public TopicDto toDto(Topic entity) {
        return TopicDto.builder()
            .topicCode(entity.getTopicCode())
            .title(entity.getTitle())
            .description(entity.getDescription())
            .category(entity.getCategory())
            .createdDate(toOffsetDateTime(entity.getCreatedAt()))
            .lastUsedDate(toOffsetDateTime(entity.getLastUsedAt()))
            .usageCount(entity.getUsageCount())
            .stalenessScore(entity.getStalenessScore())
            .active(entity.isActive())
            .createdAt(toOffsetDateTime(entity.getCreatedAt()))
            .updatedAt(toOffsetDateTime(entity.getUpdatedAt()))
            // colorZone, status, similarityScores set by service layer
            .build();
    }

    /**
     * Convert generated DTO to Topic entity.
     */
    public Topic toEntity(CreateTopicRequest dto) {
        Topic entity = new Topic();
        entity.setTitle(dto.getTitle());
        entity.setDescription(dto.getDescription());
        entity.setCategory(dto.getCategory());
        entity.setTopicCode(generateTopicCode(dto.getTitle())); // Simple utility
        return entity;
    }

    // Pure type conversion utility
    private OffsetDateTime toOffsetDateTime(LocalDateTime localDateTime) {
        return localDateTime != null
            ? localDateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime()
            : null;
    }

    // Simple utility (no business logic)
    private String generateTopicCode(String title) {
        return title.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
    }
}
```

---

## Step 5: Update Service Layer

### 5.1 Extract Business Logic from Mapper to Service

**Before** (business logic in mapper - ❌ ANTI-PATTERN):
```java
@Component
public class TopicMapper {
    @Autowired  // ❌ Mapper should NOT have repository dependencies
    private TopicRepository topicRepository;

    public TopicDto toDto(Topic entity) {
        var dto = // ... field mapping

        // ❌ Business logic in mapper (should be in service)
        dto.setColorZone(calculateColorZone(entity.getStalenessScore()));

        // ❌ Database query in mapper (should be in service)
        var similarTopics = topicRepository.findSimilarTopics(entity.getId());
        dto.setSimilarityScores(convertScores(similarTopics));

        return dto;
    }
}
```

**After** (business logic in service - ✅ CORRECT):
```java
@Service
@Transactional
@RequiredArgsConstructor
public class TopicService {

    private final TopicRepository topicRepository;
    private final TopicMapper topicMapper;  // Pure mapper, no business logic

    public ch.batbern.events.dto.generated.topics.Topic getTopicByCode(String topicCode) {
        // 1. Fetch entity
        Topic entity = topicRepository.findByTopicCode(topicCode)
            .orElseThrow(() -> new TopicNotFoundException(topicCode));

        // 2. Business logic: calculate derived fields
        TopicColorZone colorZone = calculateColorZone(entity.getStalenessScore());
        TopicStatus status = calculateStatus(entity);

        // 3. Business logic: data enrichment (batch query to avoid N+1)
        List<SimilarityScore> similarityScores = convertSimilarityScoresToDtos(
            entity.getSimilarityScores()
        );

        // 4. Pure mapper: entity → DTO conversion
        var dto = topicMapper.toDto(entity);

        // 5. Set business logic results
        dto.setColorZone(colorZone);
        dto.setStatus(status);
        dto.setSimilarityScores(similarityScores);

        return dto;
    }

    // Business logic method (can be static if pure function)
    public static TopicColorZone calculateColorZone(Integer staleness) {
        if (staleness == null) return TopicColorZone.GRAY;
        if (staleness < 50) return TopicColorZone.RED;
        else if (staleness <= 83) return TopicColorZone.YELLOW;
        else return TopicColorZone.GREEN;
    }

    // Business logic method (requires repository - instance method)
    public List<SimilarityScore> convertSimilarityScoresToDtos(
        List<Topic.SimilarityScore> entityScores
    ) {
        // Batch fetch topics to avoid N+1 queries
        List<UUID> topicIds = entityScores.stream()
            .map(Topic.SimilarityScore::getTopicId)
            .collect(Collectors.toList());

        List<Topic> topics = topicRepository.findAllById(topicIds);
        Map<UUID, String> uuidToCodeMap = topics.stream()
            .collect(Collectors.toMap(Topic::getId, Topic::getTopicCode));

        // Convert UUID to topicCode (data enrichment for ADR-003)
        return entityScores.stream()
            .map(score -> SimilarityScore.builder()
                .topicCode(uuidToCodeMap.get(score.getTopicId()))
                .score(score.getScore().floatValue())
                .build())
            .filter(dto -> dto.getTopicCode() != null)
            .collect(Collectors.toList());
    }
}
```

**Reference**: See [ADR-006, Section 2: Layered Architecture](../architecture/ADR-006-openapi-contract-first-code-generation.md#layered-architecture--data-flow)

---

## Step 6: Update Controller Layer

### 6.1 Update Imports

**Before**:
```java
import ch.batbern.events.dto.TopicResponse;  // ❌ Manual DTO
import ch.batbern.events.dto.CreateTopicRequest;  // ❌ Manual DTO
```

**After**:
```java
import ch.batbern.events.dto.generated.topics.Topic;  // ✅ Generated DTO
import ch.batbern.events.dto.generated.topics.CreateTopicRequest;  // ✅ Generated DTO
```

### 6.2 Update Method Signatures

**Before**:
```java
@RestController
@RequestMapping("/api/v1/topics")
public class TopicController {

    @GetMapping("/{id}")
    public ResponseEntity<TopicResponse> getTopicById(@PathVariable UUID id) {
        // ❌ Manual DTO, UUID identifier
    }
}
```

**After**:
```java
@RestController
@RequestMapping("/api/v1/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;

    @GetMapping("/{topicCode}")
    public ResponseEntity<Topic> getTopicByCode(@PathVariable String topicCode) {
        // ✅ Generated DTO, meaningful identifier (ADR-003)
        Topic topic = topicService.getTopicByCode(topicCode);
        return ResponseEntity.ok(topic);
    }

    @PostMapping
    public ResponseEntity<Topic> createTopic(@Valid @RequestBody CreateTopicRequest request) {
        // ✅ @Valid annotation for automatic validation (from generated DTO annotations)
        Topic created = topicService.createTopic(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
```

---

## Step 7: Delete Manual DTOs

### 7.1 Deletion Checklist

**Before deleting**, verify:
- ✅ All controllers use generated DTOs
- ✅ All services return generated DTOs
- ✅ All integration tests use generated DTOs
- ✅ No compilation errors

**Delete manual DTO files**:
```bash
rm services/event-management-service/src/main/java/ch/batbern/events/dto/TopicResponse.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/CreateTopicRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/UpdateTopicRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/TopicFilterRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/OverrideStalenesRequest.java
# ... delete all manual DTOs for this API
```

### 7.2 Verify No Remaining Imports

```bash
# Compile to verify no missing imports
./gradlew :services:event-management-service:compileJava

# If compilation fails, check for missed imports:
grep -r "import.*dto.TopicResponse" services/event-management-service/src/
```

---

## Step 8: Update Entity (if needed for ADR-003)

### 8.1 Add Meaningful Code Field to Entity

**Example**: Add `topicCode` field to Topic entity

```java
@Entity
@Table(name = "topics")
public class Topic {

    @Id
    @GeneratedValue
    private UUID id;  // Internal ID (not exposed in API)

    @Column(name = "topic_code", unique = true, nullable = false)
    private String topicCode;  // External ID (ADR-003 compliant)

    // ... other fields

    // Auto-generate on creation
    @PrePersist
    public void prePersist() {
        if (this.topicCode == null && this.title != null) {
            this.topicCode = generateTopicCode(this.title);
        }
    }

    private String generateTopicCode(String title) {
        return title.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
    }
}
```

### 8.2 Create Flyway Migration

**File**: `services/event-management-service/src/main/resources/db/migration/V24__add_topic_code_column.sql`

```sql
-- Add topicCode column to topics table (ADR-003)
ALTER TABLE topics
ADD COLUMN topic_code VARCHAR(255);

-- Populate existing rows with generated codes
UPDATE topics
SET topic_code = LOWER(REGEXP_REPLACE(
    REGEXP_REPLACE(title, '[^a-zA-Z0-9]+', '-', 'g'),
    '^-|-$', '', 'g'
));

-- Add unique constraint and NOT NULL
ALTER TABLE topics
ALTER COLUMN topic_code SET NOT NULL;

ALTER TABLE topics
ADD CONSTRAINT uk_topics_topic_code UNIQUE (topic_code);

-- Add index for lookups
CREATE INDEX idx_topics_topic_code ON topics(topic_code);

-- Add comment
COMMENT ON COLUMN topics.topic_code IS 'Meaningful identifier for external API (ADR-003)';
```

### 8.3 Update Repository

```java
public interface TopicRepository extends JpaRepository<Topic, UUID> {

    Optional<Topic> findByTopicCode(String topicCode);

    boolean existsByTopicCode(String topicCode);

    // ... other methods
}
```

**Reference**: See [Flyway Migration Guide](flyway-migration-guide.md) for detailed migration patterns.

---

## Step 9: Regenerate Frontend Types

### 9.1 Run Type Generation

```bash
cd web-frontend
npm run generate:api-types
```

**This generates TypeScript types from the same OpenAPI specs**:
```typescript
// web-frontend/src/types/generated/topics-api.types.ts
export interface components {
  schemas: {
    Topic: {
      topicCode: string;  // ✅ Matches backend generated DTO
      title: string;
      description?: string;
      // ... exact same fields as backend DTO
    };
  };
}
```

### 9.2 Update Frontend Components

**Before**:
```typescript
// ❌ Manual type definition
interface Topic {
  id: string;  // UUID
  title: string;
}
```

**After**:
```typescript
// ✅ Generated type import
import type { components } from '@/types/generated/topics-api.types';

type Topic = components['schemas']['Topic'];

export const TopicList: React.FC = () => {
  const [topics, setTopics] = useState<Topic[]>([]);

  // ✅ Type-safe API call
  const fetchTopics = async () => {
    const response = await topicService.getTopics();
    setTopics(response);  // TypeScript validates structure
  };
};
```

---

## Step 10: Run Tests & Commit

### 10.1 Backend Tests

```bash
# Run all backend tests
./gradlew :services:event-management-service:test

# Verify coverage
./gradlew :services:event-management-service:jacocoTestReport
open services/event-management-service/build/reports/jacoco/test/html/index.html
```

**CRITICAL**: All integration tests MUST use PostgreSQL via Testcontainers (see ADR-006).

### 10.2 Frontend Tests

```bash
cd web-frontend
npm test
```

### 10.3 Bruno API Tests

Update Bruno test collections to use new identifiers:

**Before**:
```
GET {{baseUrl}}/api/v1/topics/{{topicId}}
```

**After**:
```
GET {{baseUrl}}/api/v1/topics/{{topicCode}}
```

### 10.4 Commit

```bash
git add .
git commit -m "feat(topics): migrate to OpenAPI-generated DTOs (ADR-003 + ADR-006)

- Update OpenAPI spec to use topicCode (ADR-003)
- Configure OpenAPI Generator with builders enabled
- Create TopicMapper (Pure Mapper pattern)
- Extract business logic to TopicService
- Update controller to use generated DTOs
- Delete manual DTOs (5 files)
- Add topicCode to Topic entity (Flyway V24)
- Regenerate frontend types
- All tests passing (17/17 integration tests)"
```

---

## Common Pitfalls & Solutions

### Pitfall 1: Business Logic in Mapper

**Problem**: Mapper contains calculations, database queries, or business rules.

**Solution**: Move to Service layer (see Step 5). Mappers should be **pure** (field mapping + type conversion only).

**Reference**: [ADR-006, Section 2: Pure Mapper Pattern](../architecture/ADR-006-openapi-contract-first-code-generation.md#pure-mapper-pattern)

### Pitfall 2: Inconsistent Folder Structure

**Problem**: Different generation tasks use different output directories (e.g., `build/generated` vs `build/generated-topics`).

**Solution**: Standardize on `build/generated-{api-name}` pattern for all services (see Step 2.2).

**Reference**: See migration plan Risk 5 for consequences.

### Pitfall 3: Forgetting to Update Frontend Types

**Problem**: Backend OpenAPI changes, but frontend types not regenerated → runtime errors.

**Solution**: Always run `npm run generate:api-types` after OpenAPI spec changes (Step 9).

### Pitfall 4: OpenAPI Spec Out of Sync with Manual DTOs

**Problem**: OpenAPI spec and manual DTOs have different fields → generated DTOs won't compile.

**Solution**: Reconcile fields BEFORE generating (Step 1.1). Create reconciliation table and make decisions.

### Pitfall 5: Using Map<String, Object> in Controllers

**Problem**: Controller methods return `Map<String, Object>` instead of typed DTOs.

**Solution**: Always use generated DTOs for request/response types. If OpenAPI spec is missing a schema, add it.

**Example of violation**:
```java
// ❌ WRONG - no type safety
@PostMapping("/events/{eventCode}/topics")
public ResponseEntity<Map<String, Object>> selectTopic(
    @RequestBody Map<String, String> request
) {
    // ...
}
```

**Correct**:
```java
// ✅ RIGHT - type-safe with generated DTOs
@PostMapping("/events/{eventCode}/topics")
public ResponseEntity<TopicSelectionResponse> selectTopic(
    @Valid @RequestBody SelectTopicForEventRequest request
) {
    // ...
}
```

---

## Definition of Done (Per Migration)

### OpenAPI & Code Generation
- [ ] OpenAPI spec updated (ADR-003 compliant if needed)
- [ ] All request/response schemas defined in OpenAPI spec
- [ ] Backend DTOs regenerated from OpenAPI spec
- [ ] Frontend types regenerated from OpenAPI spec

### Backend Implementation
- [ ] Backend uses generated DTOs only (no manual DTOs for that API)
- [ ] **CRITICAL:** No `Map<String, Object>` or `Map<String, String>` for request/response types
- [ ] All manual DTOs deleted for migrated API domain
- [ ] Controllers use `@Valid` annotation for automatic validation
- [ ] Mapper classes follow Pure Mapper pattern (no business logic, no repository dependencies)

### Frontend Implementation
- [ ] Frontend types regenerated and exported in type definition files
- [ ] Service layer uses typed responses (not `void` or `any`)
- [ ] All API calls properly typed with generated request/response types

### Testing & Quality
- [ ] All unit tests pass
- [ ] All integration tests pass (using PostgreSQL via Testcontainers)
- [ ] Integration tests use generated DTOs (not Maps)
- [ ] Bruno API contract tests updated and pass
- [ ] No UUID fields exposed in API responses (except documented exceptions)

### Documentation
- [ ] OPENAPI-CODEGEN.md updated for the service (if applicable)
- [ ] Migration completed in tracking document/plan

---

## References

### Related Documentation
- **[ADR-006: OpenAPI Contract-First Code Generation](../architecture/ADR-006-openapi-contract-first-code-generation.md)** - Architectural patterns and decisions
- **[Coding Standards](../architecture/coding-standards.md)** - TDD practices, testing requirements
- **[Service Foundation Pattern Guide](service-foundation-pattern.md)** - Standard service structure
- **[Flyway Migration Guide](flyway-migration-guide.md)** - Database migration patterns

### Implementation Examples
- **Topics API Migration** (BAT-17) - Complete reference implementation
  - Mapper: `services/event-management-service/src/main/java/ch/batbern/events/mapper/TopicMapper.java`
  - Service: `services/event-management-service/src/main/java/ch/batbern/events/service/TopicService.java`
  - Tests: `services/event-management-service/src/test/integration/controller/TopicControllerIntegrationTest.java`

### Tools
- **[OpenAPI Generator](https://openapi-generator.tech/)** - Backend DTO generation
- **[openapi-typescript](https://openapi-ts.dev/)** - Frontend type generation
- **[OpenAPI 3.1 Specification](https://swagger.io/specification/)** - OpenAPI standard

---

## Revision History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0 | 2026-01-04 | Initial migration guide created from backend DTO migration plan | Development Team |
