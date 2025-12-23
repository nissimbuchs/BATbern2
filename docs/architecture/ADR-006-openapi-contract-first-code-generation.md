# ADR-006: OpenAPI Contract-First Code Generation

**Status**: Accepted
**Date**: 2025-01-08
**Decision Makers**: Development Team
**Related Stories**: All Epic 2 CRUD stories (2.1, 2.1b, 2.2, 2.3, 2.7)

## Context

BATbern is a polyglot monorepo with:
- **Backend**: Multiple Java/Spring Boot microservices (company-user-management, event-management, partner-coordination, etc.)
- **Frontend**: React 19 + TypeScript SPA
- **API Documentation**: OpenAPI 3.1 specifications in `docs/api/*.openapi.yml`

### Problems Without Contract-First Approach

1. **Type Inconsistency**: Backend DTOs and frontend TypeScript types could drift apart
2. **Manual Boilerplate**: Creating DTOs and API interfaces manually is tedious and error-prone
3. **Documentation Lag**: OpenAPI specs often became outdated after code changes
4. **No Contract Enforcement**: No build-time validation that implementation matches specification
5. **Frontend-Backend Mismatch**: Field names, types, or validations could differ between layers
6. **Testing Gaps**: API contract tests couldn't reliably catch interface changes

### Key Requirements

1. **Single Source of Truth**: OpenAPI specification must drive both frontend and backend code
2. **Type Safety**: Guarantee that frontend types match backend DTOs
3. **Build-Time Validation**: Compilation should fail if implementation diverges from contract
4. **Architecture Compliance**: Must integrate with shared-kernel infrastructure (ErrorResponse, PaginationMetadata)
5. **Developer Experience**: Reduce boilerplate while maintaining control over business logic
6. **Maintainability**: API changes should automatically propagate to implementation

## Decision

We have adopted a **hybrid contract-first approach** using OpenAPI code generation for both frontend and backend, with the following principles:

### 1. OpenAPI Specification as Single Source of Truth

- All API contracts defined in `docs/api/*.openapi.yml` (OpenAPI 3.1 format)
- Specifications use shared-kernel types via imports (ErrorResponse, PaginationMetadata)
- Frontend and backend both generate code from the same OpenAPI specs
- Specifications maintained alongside code, versioned in Git

### 2. Backend: Generated Interfaces + Manual Implementation

**Pattern**: Generate API interfaces and DTOs, implement manually

**Technology**: [OpenAPI Generator](https://openapi-generator.tech/) with Spring Boot 3 generator

**What Gets Generated** (per service):

```
build/generated/src/main/java/
└── ch/batbern/{service}/
    ├── api/generated/          # API Interfaces
    │   ├── UsersApi.java
    │   ├── CompaniesApi.java
    │   └── EventsApi.java
    └── dto/generated/          # DTOs
        ├── UserResponse.java
        ├── CreateUserRequest.java
        ├── UpdateUserRequest.java
        └── ...
```

**Gradle Configuration** (from `company-user-management-service/build.gradle`):

```gradle
plugins {
    id 'org.openapi.generator' version '7.2.0'
}

openApiGenerate {
    generatorName = "spring"
    inputSpec = "$rootDir/docs/api/users-api.openapi.yml".toString()
    outputDir = "$buildDir/generated".toString()
    apiPackage = "ch.batbern.companyuser.api.generated"
    modelPackage = "ch.batbern.companyuser.dto.generated"

    // Import shared-kernel types (NOT generated)
    importMappings = [
        'ErrorResponse': 'ch.batbern.shared.dto.ErrorResponse',
        'PaginationMetadata': 'ch.batbern.shared.api.PaginationMetadata',
        'PaginatedResponse': 'ch.batbern.shared.dto.PaginatedResponse'
    ]

    typeMappings = [
        'ErrorResponse': 'ch.batbern.shared.dto.ErrorResponse',
        'PaginationMetadata': 'ch.batbern.shared.api.PaginationMetadata'
    ]

    schemaMappings = [
        'ErrorResponse': 'ch.batbern.shared.dto.ErrorResponse',
        'PaginationMetadata': 'ch.batbern.shared.api.PaginationMetadata'
    ]

    configOptions = [
        interfaceOnly: "true",              // Only generate interfaces
        useTags: "true",                    // Organize APIs by OpenAPI tags
        useSpringBoot3: "true",             // Spring Boot 3 annotations
        useBeanValidation: "true",          // @Valid, @NotNull
        useJakartaEe: "true",               // Jakarta EE (Spring Boot 3)
        dateLibrary: "java8",               // java.time.* types
        generateBuilders: "true",           // Generate builder pattern for DTOs
        skipDefaultInterface: "true"        // No default methods
    ]
}

// Add generated sources to compile
sourceSets.main.java.srcDirs += "$buildDir/generated/src/main/java"

// Generate before compile
compileJava.dependsOn tasks.openApiGenerate
```

**Implementation Pattern**:

```java
// Controller implements generated interface
@RestController
@RequiredArgsConstructor
public class UserController implements UsersApi {  // Generated interface

    private final UserService userService;
    private final IncludeParser includeParser;  // From shared-kernel

    @Override  // Method signature enforced by generated interface
    public ResponseEntity<UserResponse> getCurrentUser(Optional<String> include) {
        Set<String> includes = includeParser.parse(include.orElse(null));
        UserResponse user = userService.getCurrentUser(includes);
        return ResponseEntity.ok(user);
    }

    @Override
    public ResponseEntity<UserResponse> updateCurrentUser(UpdateUserRequest request) {
        // Use generated DTO
        UserResponse updated = userService.updateCurrentUser(request);
        return ResponseEntity.ok(updated);
    }
}
```

**Service Layer Returns Generated DTOs**:

```java
@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserResponseMapper responseMapper;     // Pure mapper component
    private final DomainEventPublisher eventPublisher;  // From shared-kernel

    // Service returns generated DTOs (not entities)
    public UserResponse updateCurrentUser(UpdateUserRequest request) {
        // UpdateUserRequest is generated from OpenAPI spec
        User user = getCurrentUserEntity();

        // Update from generated DTO
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }

        User saved = userRepository.save(user);

        // Publish event using shared-kernel
        eventPublisher.publishEvent(new UserUpdatedEvent(saved.getId(), saved.getEmail()));

        return responseMapper.mapToResponse(saved);  // Returns generated UserResponse DTO
    }
}
```

**Pure Mapper Pattern** (Extracted Component for Entity→DTO Conversion):

```java
/**
 * Pure mapper for converting between User entities and generated DTOs.
 *
 * Pattern: Pure Mapper
 * - Field mapping only
 * - Type conversions (LocalDateTime → OffsetDateTime)
 * - NO business logic
 * - NO repository dependencies
 * - NO complex data enrichment
 *
 * Business logic belongs in Service layer or dedicated business logic services.
 */
@Component
public class UserResponseMapper {

    public UserResponse mapToResponse(User user) {
        // Map JPA entity to generated DTO
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setCreatedAt(toOffsetDateTime(user.getCreatedAt()));  // Type conversion
        response.setUpdatedAt(toOffsetDateTime(user.getUpdatedAt()));
        // ... more field mappings
        return response;
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime localDateTime) {
        return localDateTime != null
            ? localDateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime()
            : null;
    }
}
```

**Layered Architecture & Data Flow**:

```
┌─────────────────────────────────────────────────────────────────┐
│  LAYER 1: CONTROLLER (API Boundary)                             │
│  - Accepts: Generated DTOs (from OpenAPI)                       │
│  - Returns: Generated DTOs (for OpenAPI compliance)             │
│  - Uses: Service methods (which return generated DTOs)          │
│  - Uses: Mapper (only if direct entity conversion needed)       │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  LAYER 2: MAPPER (Pure Conversion Logic)                       │
│  - toDto(Entity) → Generated DTO                                │
│  - toEntity(Generated DTO) → Entity                             │
│  - Type conversions: LocalDateTime → OffsetDateTime             │
│  - NO business logic                                            │
│  - NO repository dependencies (pure mapper pattern)             │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  LAYER 3: SERVICE (Business Logic & Data Enrichment)           │
│  - Works with: Entities internally                              │
│  - Returns: Generated DTOs to controller                        │
│  - Uses: Mapper to convert entities → DTOs before returning     │
│  - Uses: Repository for data access                             │
│  - Contains: Business logic (calculations, enrichment)          │
│  - Handles: UUID → topicCode conversions (batch queries)        │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  LAYER 4: REPOSITORY (Data Access)                             │
│  - Works with: Entities                                         │
│  - Returns: Entities                                            │
│  - JPA/Hibernate database interaction                           │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  LAYER 5: ENTITY (Domain Model)                                │
│  - JPA annotations                                              │
│  - Internal UUID + External meaningful ID (ADR-003)             │
│  - Never exposed directly to API                                │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  LAYER 6: DATABASE (PostgreSQL)                                │
└─────────────────────────────────────────────────────────────────┘
```

**Business Logic Separation**:

- **Business Logic** (colorZone, status calculation, enrichment) → Service layer (Layer 3)
- **Mapper** (Layer 2) → Pure mapping only (entity ↔ DTO conversions, type transformations)
- **Service** (Layer 3) → Returns generated DTOs (not entities), uses mapper for conversion

Example with business logic in service:

```java
@Service
public class TopicService {

    private final TopicRepository topicRepository;
    private final TopicMapper topicMapper;  // Pure mapper

    public ch.batbern.events.dto.generated.topics.Topic getTopicByCode(String topicCode) {
        Topic entity = topicRepository.findByTopicCode(topicCode)
            .orElseThrow(() -> new TopicNotFoundException(topicCode));

        // Service handles business logic and data enrichment
        var similarityScores = convertSimilarityScoresToDtos(entity.getSimilarityScores());

        // Mapper does pure entity→DTO conversion
        return topicMapper.toDtoWithSimilarityScores(entity, similarityScores);
    }

    // Business logic method in service
    public List<SimilarityScore> convertSimilarityScoresToDtos(List<Topic.SimilarityScore> entityScores) {
        // Batch fetch topics to avoid N+1 queries
        List<UUID> topicIds = entityScores.stream()
            .map(Topic.SimilarityScore::getTopicId)
            .collect(Collectors.toList());

        List<Topic> topics = topicRepository.findAllById(topicIds);
        Map<UUID, String> uuidToCodeMap = topics.stream()
            .collect(Collectors.toMap(Topic::getId, Topic::getTopicCode));

        // Convert UUID to topicCode (data enrichment)
        return entityScores.stream()
            .map(score -> {
                SimilarityScore dto = new SimilarityScore();
                dto.setTopicCode(uuidToCodeMap.get(score.getTopicId()));
                dto.setScore(score.getScore().floatValue());
                return dto;
            })
            .filter(dto -> dto.getTopicCode() != null)
            .collect(Collectors.toList());
    }

    // Pure business logic (can be static utility)
    public static TopicColorZone calculateColorZone(Integer staleness) {
        if (staleness == null) return TopicColorZone.GRAY;
        if (staleness < 50) return TopicColorZone.RED;
        else if (staleness <= 83) return TopicColorZone.YELLOW;
        else return TopicColorZone.GREEN;
    }
}
```

### 3. Frontend: Generated TypeScript Types

**Pattern**: Generate TypeScript types and paths from OpenAPI specs

**Technology**: [openapi-typescript](https://openapi-ts.dev/)

**What Gets Generated**:

```
web-frontend/src/types/generated/
├── company-api.types.ts      # From companies-api.openapi.yml
├── user-api.types.ts         # From users-api.openapi.yml
├── events-api.types.ts       # From events-api.openapi.yml
└── partners-api.types.ts     # From partners-api.openapi.yml (future)
```

**Package.json Scripts**:

```json
{
  "scripts": {
    "generate:api-types": "npm run generate:api-types:companies && npm run generate:api-types:users && npm run generate:api-types:events",
    "generate:api-types:companies": "openapi-typescript ../docs/api/companies-api.openapi.yml -o src/types/generated/company-api.types.ts && prettier --write src/types/generated/company-api.types.ts",
    "generate:api-types:users": "openapi-typescript ../docs/api/users-api.openapi.yml -o src/types/generated/user-api.types.ts && prettier --write src/types/generated/user-api.types.ts",
    "generate:api-types:events": "openapi-typescript ../docs/api/events-api.openapi.yml -o src/types/generated/events-api.types.ts && prettier --write src/types/generated/events-api.types.ts"
  }
}
```

**Generated Types Example**:

```typescript
// src/types/generated/user-api.types.ts (auto-generated)
export interface paths {
  "/api/v1/users/me": {
    get: {
      parameters: {
        query?: {
          include?: string;
        };
      };
      responses: {
        200: {
          content: {
            "application/json": components["schemas"]["UserResponse"];
          };
        };
        404: {
          content: {
            "application/json": components["schemas"]["ErrorResponse"];
          };
        };
      };
    };
    patch: {
      requestBody: {
        content: {
          "application/json": components["schemas"]["UpdateUserRequest"];
        };
      };
      responses: {
        200: {
          content: {
            "application/json": components["schemas"]["UserResponse"];
          };
        };
      };
    };
  };
}

export interface components {
  schemas: {
    UserResponse: {
      id: string;
      email: string;
      firstName?: string;
      lastName?: string;
      bio?: string;
      companyId?: string;
      roles: ("ORGANIZER" | "SPEAKER" | "SPONSOR" | "ATTENDEE")[];
      isActive: boolean;
      createdAt: string;
      updatedAt: string;
    };
    UpdateUserRequest: {
      firstName?: string;
      lastName?: string;
      bio?: string;
    };
    ErrorResponse: {
      timestamp: string;
      path: string;
      status: number;
      error: string;
      message: string;
      correlationId?: string;
      severity?: "ERROR" | "WARNING" | "INFO";
    };
  };
}
```

**Frontend Service Layer Uses Generated Types**:

```typescript
// src/services/userService.ts
import type { components } from '@/types/generated/user-api.types';
import { apiClient } from '@/lib/apiClient';

type UserResponse = components['schemas']['UserResponse'];
type UpdateUserRequest = components['schemas']['UpdateUserRequest'];

export const userService = {
  async getCurrentUser(include?: string): Promise<UserResponse> {
    const response = await apiClient.get<UserResponse>('/api/v1/users/me', {
      params: { include }
    });
    return response.data;
  },

  async updateCurrentUser(request: UpdateUserRequest): Promise<UserResponse> {
    const response = await apiClient.patch<UserResponse>('/api/v1/users/me', request);
    return response.data;
  }
};
```

**React Component Uses Service Layer**:

```typescript
// src/components/UserProfile.tsx
import { userService } from '@/services/userService';
import type { components } from '@/types/generated/user-api.types';

type UserResponse = components['schemas']['UserResponse'];

export const UserProfile: React.FC = () => {
  const [user, setUser] = useState<UserResponse | null>(null);

  const handleUpdate = async (data: components['schemas']['UpdateUserRequest']) => {
    const updated = await userService.updateCurrentUser(data);
    setUser(updated);
  };

  // Component implementation with full type safety
};
```

### 4. Shared-Kernel Integration

**Principle**: Common infrastructure types are defined once and reused everywhere.

**Shared-Kernel Types** (NOT generated from OpenAPI):

- `ch.batbern.shared.dto.ErrorResponse` - Standard error response
- `ch.batbern.shared.api.PaginationMetadata` - Pagination metadata
- `ch.batbern.shared.dto.PaginatedResponse<T>` - Generic paginated response
- `ch.batbern.shared.api.FilterParser` - MongoDB-style JSON filters
- `ch.batbern.shared.api.SortParser` - Sort string parsing
- `ch.batbern.shared.api.IncludeParser` - Resource expansion
- `ch.batbern.shared.exception.*` - Exception hierarchy

**OpenAPI Spec References Shared-Kernel Types**:

```yaml
# docs/api/users-api.openapi.yml
components:
  schemas:
    ErrorResponse:
      # Reference to shared-kernel type (imported, not generated)
      type: object
      description: "Standard error response from shared-kernel"
      # Schema definition here for documentation, but code generation uses importMappings

    PaginationMetadata:
      # Reference to shared-kernel type (imported, not generated)
      type: object
      description: "Pagination metadata from shared-kernel"

    PaginatedUserResponse:
      type: object
      properties:
        data:
          type: array
          items:
            $ref: '#/components/schemas/UserResponse'
        pagination:
          $ref: '#/components/schemas/PaginationMetadata'  # Uses shared-kernel
```

**Backend Uses Import Mappings**:

```gradle
importMappings = [
    'ErrorResponse': 'ch.batbern.shared.dto.ErrorResponse',
    'PaginationMetadata': 'ch.batbern.shared.api.PaginationMetadata'
]
```

**Frontend Manually Imports Shared Types** (if needed):

```typescript
// For ErrorResponse, the generated type matches shared-kernel structure
// No additional imports needed - types generated from OpenAPI spec
```

### 5. Code Generation Workflow

**Backend (Java/Spring)**:

1. Update OpenAPI spec: `docs/api/{service}-api.openapi.yml`
2. Build service: `./gradlew :services:{service}:build`
   - Runs `openApiGenerate` task automatically
   - Generates interfaces in `build/generated/src/main/java/`
   - Compiles with existing controller implementations
3. Fix compilation errors (if interface changed)
4. Run tests to verify contract compliance

**Frontend (TypeScript)**:

1. Update OpenAPI spec: `docs/api/{service}-api.openapi.yml` (same file as backend)
2. Generate types: `npm run generate:api-types`
   - Generates TypeScript types in `src/types/generated/`
   - Auto-formats with Prettier
3. Fix TypeScript errors (if types changed)
4. Commit generated types to Git (convention: keep generated types versioned)

**Automatic Regeneration**:

- Backend: Generated code in `build/generated/` (NOT committed, auto-regenerated on build)
- Frontend: Generated types in `src/types/generated/` (committed to Git for IDE support)

### 6. Documentation Pattern

Each service maintains an `OPENAPI-CODEGEN.md` file documenting:

- What gets generated (API interfaces, DTOs)
- What gets imported from shared-kernel
- How to regenerate code
- Implementation patterns (controller, service layer)
- Benefits and anti-patterns

**Example**: `services/company-user-management-service/OPENAPI-CODEGEN.md`

## Alternatives Considered

### Alternative 1: Code-First with Annotations

**Approach**: Use Spring annotations (@RestController, @GetMapping) and generate OpenAPI from code

**Pros**:
- No build-time code generation
- Direct implementation without interfaces

**Cons**:
- ❌ OpenAPI spec becomes secondary (not source of truth)
- ❌ Frontend types generated from backend implementation (tight coupling)
- ❌ No build-time contract enforcement
- ❌ Spec can drift from implementation
- ❌ Harder to design APIs before implementation

**Rejected because**: OpenAPI spec must drive development, not be a byproduct

### Alternative 2: Manual DTOs + Manual OpenAPI

**Approach**: Write DTOs manually, maintain OpenAPI spec separately, hope they match

**Pros**:
- No tooling required
- Full control over DTO structure

**Cons**:
- ❌ High maintenance burden (duplicate work)
- ❌ No automatic type consistency
- ❌ OpenAPI specs quickly become outdated
- ❌ No build-time validation
- ❌ Frontend types manually created or generated separately

**Rejected because**: Doesn't scale, high risk of drift

### Alternative 3: GraphQL

**Approach**: Use GraphQL schema as contract, generate types for frontend/backend

**Pros**:
- Strong type system
- Single query language
- Field-level fetching

**Cons**:
- ❌ Additional complexity (GraphQL server + REST APIs)
- ❌ Team lacks GraphQL expertise
- ❌ RESTful patterns well-established in codebase
- ❌ Harder to cache at CDN/API Gateway level
- ❌ Bruno API contract tests built for REST

**Rejected because**: REST + OpenAPI is simpler and sufficient for our needs

### Alternative 4: Full Code Generation (Controllers + Services)

**Approach**: Generate controllers and service stubs from OpenAPI

**Pros**:
- Maximum automation
- Less manual code

**Cons**:
- ❌ Loss of control over business logic structure
- ❌ Harder to integrate with shared-kernel patterns
- ❌ Generated code quality often suboptimal
- ❌ Difficult to customize for complex scenarios
- ❌ Doesn't fit DDD layered architecture

**Rejected because**: We need control over business logic, generated interfaces provide optimal balance

## Consequences

### Positive

✅ **Type Safety Across Stack**: Backend DTOs and frontend types guaranteed to match (same source)

✅ **Build-Time Contract Enforcement**: Compilation fails if implementation diverges from OpenAPI spec

✅ **Single Source of Truth**: OpenAPI spec drives both frontend and backend, eliminates drift

✅ **Reduced Boilerplate**: No manual DTO creation, interfaces auto-generated

✅ **Better API Design**: Designing OpenAPI spec first forces thinking about API contract before implementation

✅ **Improved Testing**: Bruno API contract tests can reference OpenAPI spec for validation

✅ **Frontend Efficiency**: Frontend developers know exact response shapes before backend is ready

✅ **Documentation Always Current**: OpenAPI spec is the implementation contract, can't become stale

✅ **IDE Support**: Full autocomplete and type checking for API clients and DTOs

✅ **Architecture Compliance**: Seamless integration with shared-kernel infrastructure

### Negative

⚠️ **Learning Curve**: Developers must understand OpenAPI spec syntax and code generation tools

⚠️ **Build Complexity**: Additional Gradle plugin and npm script configuration

⚠️ **Generated Code Limitations**: Sometimes generated code needs workarounds for complex scenarios

⚠️ **Frontend Generated Types Must Be Committed**: Increases repository size slightly (trade-off for IDE support)

⚠️ **Breaking Changes Cascade**: OpenAPI spec changes can cause compilation errors across frontend and backend (this is actually a feature, but requires coordination)

### Mitigation Strategies

**For Learning Curve**:
- Comprehensive OPENAPI-CODEGEN.md documentation per service
- This ADR provides architecture-level guidance
- Code examples in stories (e.g., Story 2.7)

**For Build Complexity**:
- Standardized Gradle configuration across services
- Automated code generation on build (no manual steps)

**For Generated Code Limitations**:
- Use hybrid approach (generated interfaces, manual implementation)
- Shared-kernel integration via importMappings
- Custom business logic in service layer

**For Breaking Changes**:
- OpenAPI spec changes reviewed carefully
- Bruno E2E tests catch API contract breaks
- Semantic versioning for major API changes

## References

### Implementation Examples

- **Backend Pattern**: `services/company-user-management-service/OPENAPI-CODEGEN.md`
- **Gradle Config**: `services/company-user-management-service/build.gradle` (openApiGenerate task)
- **Frontend Scripts**: `web-frontend/package.json` (generate:api-types scripts)
- **Story 2.7**: Partner Coordination Service (comprehensive OpenAPI codegen example)

### OpenAPI Specifications

- `docs/api/users-api.openapi.yml` - User Management API
- `docs/api/companies-api.openapi.yml` - Company Management API
- `docs/api/events-api.openapi.yml` - Event Management API
- `docs/api/partners-api.openapi.yml` - Partner Coordination API (Story 2.7)

### Tools

- **Backend**: [OpenAPI Generator](https://openapi-generator.tech/) - Spring Boot 3 generator
- **Frontend**: [openapi-typescript](https://openapi-ts.dev/) - TypeScript type generation
- **Specification**: [OpenAPI 3.1](https://swagger.io/specification/)

### Related ADRs

- **ADR-003**: Meaningful Identifiers in Public APIs - Dual-identifier strategy complements OpenAPI contracts
- **ADR-004**: Factor User Fields from Domain Entities - HTTP enrichment pattern used in generated controllers

## Builder Pattern for Generated DTOs

All OpenAPI generators are configured with `generateBuilders: 'true'` to enable fluent builder pattern for DTOs:

```java
// Generated DTO with builder
TopicListResponse response = TopicListResponse.builder()
    .data(topicDtos)
    .pagination(paginationMetadata)
    .build();

// Instead of manual setters
TopicListResponse response = new TopicListResponse();
response.setData(topicDtos);
response.setPagination(paginationMetadata);
```

**Benefits**:
- ✅ Immutable object construction
- ✅ Null safety (missing required fields caught at build time)
- ✅ Better IDE autocomplete
- ✅ More readable code (fluent API)

## Revision History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0 | 2025-01-08 | Initial ADR for OpenAPI contract-first code generation | Development Team |
| 1.1 | 2025-12-22 | Added Pure Mapper pattern, business logic separation, builder pattern configuration | Development Team |
