# OpenAPI Refactoring Plan

## Executive Summary

This plan addresses three critical improvements to the BATbern OpenAPI specifications:

1. **Type Inheritance Pattern** - Eliminate field duplication across Request/Response types
2. **Events API Split** - Decompose the 4,896-line events-api into focused domain APIs
3. **Common Schemas** - Centralize shared types and eliminate duplication

**Impact**: Reduce total API specification lines by ~30%, improve maintainability, enable better code generation.

---

## Part 1: Type Inheritance Pattern

### Current Problem

Request and Response types duplicate field definitions, creating maintenance burden:

```yaml
# Current: 3 separate definitions of the same fields
CompanyResponse:
  properties:
    name: { type: string, pattern: '^[A-Za-z0-9]+$' }
    displayName: { type: string }
    swissUID: { type: string, pattern: '^CHE-\d{3}\.\d{3}\.\d{3}$' }
    # ... 8 more fields

CreateCompanyRequest:
  properties:
    name: { type: string, pattern: '^[A-Za-z0-9]+$' }  # DUPLICATED
    displayName: { type: string }                       # DUPLICATED
    swissUID: { type: string, pattern: '^CHE-\d{3}\.\d{3}\.\d{3}$' }  # DUPLICATED
    # ... same fields again

UpdateCompanyRequest:
  properties:
    name: { type: string, pattern: '^[A-Za-z0-9]+$' }  # DUPLICATED AGAIN
    # ... same fields yet again
```

### Proposed Pattern: Three-Tier Schema Hierarchy

```
┌─────────────────────────────────────────────────────────────┐
│  TIER 1: Base Schema (Domain Core)                          │
│  ─────────────────────────────────                          │
│  Contains: Core domain fields with validation               │
│  Example: CompanyBase, UserBase, EventBase                  │
│  Purpose: Single source of truth for field definitions      │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ allOf (inheritance)
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  TIER 2: Response Schema (Domain + Metadata)                │
│  ──────────────────────────────────────────                 │
│  Contains: Base + audit fields + computed fields            │
│  Example: CompanyResponse, UserResponse, EventResponse      │
│  Adds: createdAt, updatedAt, statistics, embedded resources │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  TIER 2: Request Schemas (Domain + Context)                 │
│  ─────────────────────────────────────────                  │
│  Contains: Base + operation-specific fields                 │
│  Example: CreateCompanyRequest, UpdateCompanyRequest        │
│  Adds: uploadIds, operation flags, different required[]     │
└─────────────────────────────────────────────────────────────┘
```

### Implementation Example

```yaml
schemas:
  # ═══════════════════════════════════════════════════════════
  # TIER 1: Base Schema
  # ═══════════════════════════════════════════════════════════
  CompanyBase:
    type: object
    description: |
      Core company fields shared across all request/response types.
      Story 1.16.2: Uses company name as unique identifier.
    properties:
      name:
        type: string
        description: Company name (unique identifier, alphanumeric only)
        pattern: '^[A-Za-z0-9]+$'
        minLength: 2
        maxLength: 255
        example: SwissITSolutionsAG
      displayName:
        type: string
        description: Human-readable display name
        maxLength: 255
        example: Swiss IT Solutions AG
      swissUID:
        type: string
        description: Swiss company UID (Unternehmens-Identifikationsnummer)
        pattern: '^CHE-\d{3}\.\d{3}\.\d{3}$'
        example: CHE-123.456.789
      website:
        type: string
        format: uri
        maxLength: 500
        example: https://swiss-it.ch
      industry:
        type: string
        maxLength: 100
        example: Technology
      description:
        type: string
        maxLength: 2000
        example: Leading Swiss IT consulting firm

  # ═══════════════════════════════════════════════════════════
  # TIER 2: Response Schema (extends Base)
  # ═══════════════════════════════════════════════════════════
  CompanyResponse:
    description: Company response with audit metadata and optional expansions
    allOf:
      - $ref: '#/components/schemas/CompanyBase'
      - type: object
        required:
          - name
          - isVerified
          - createdAt
          - updatedAt
        properties:
          isVerified:
            type: boolean
            description: Whether company has been verified by an organizer
            example: true
          createdAt:
            type: string
            format: date-time
            example: 2025-01-15T10:00:00Z
          updatedAt:
            type: string
            format: date-time
            example: 2025-01-15T10:00:00Z
          createdBy:
            type: string
            description: Username of creator (Story 1.16.2)
            pattern: '^[a-z]+\.[a-z]+(\.[0-9]+)?$'
            example: john.doe
          statistics:
            $ref: '#/components/schemas/CompanyStatistics'
          logo:
            $ref: '#/components/schemas/CompanyLogo'

  # ═══════════════════════════════════════════════════════════
  # TIER 2: Create Request (extends Base + operation fields)
  # ═══════════════════════════════════════════════════════════
  CreateCompanyRequest:
    description: Request to create a new company
    allOf:
      - $ref: '#/components/schemas/CompanyBase'
      - type: object
        required:
          - name
        properties:
          logoUploadId:
            type: string
            description: Upload ID from three-phase upload (optional)
            example: abc123-def456

  # ═══════════════════════════════════════════════════════════
  # TIER 2: Update Request (Base fields, all optional)
  # ═══════════════════════════════════════════════════════════
  UpdateCompanyRequest:
    description: Request to update company (all fields optional)
    allOf:
      - $ref: '#/components/schemas/CompanyBase'
      - type: object
        properties:
          logoUploadId:
            type: string
            description: New logo upload ID (replaces existing)
```

### APIs to Refactor

| API File | Base Types to Create | Estimated Reduction |
|----------|---------------------|---------------------|
| `companies-api.openapi.yml` | CompanyBase | ~150 lines |
| `users-api.openapi.yml` | UserBase, UserPreferencesBase, UserSettingsBase | ~300 lines |
| `events-api.openapi.yml` | EventBase, SessionBase, RegistrationBase | ~400 lines |
| `speakers-api.openapi.yml` | SpeakerProfileBase | ~100 lines |
| `partners-api.openapi.yml` | PartnerBase | ~100 lines |
| `attendees-api.openapi.yml` | AttendeeBase | ~50 lines |
| `topics-api.openapi.yml` | TopicBase | ~80 lines |
| **Total** | | **~1,180 lines** |

---

## Part 2: Events API Decomposition

### Current State

The `events-api.openapi.yml` is **4,896 lines** (41% of all API specs) and contains:

- 33 endpoints across 9 different concerns
- 55 schema definitions
- Mixed bounded contexts (Events, Sessions, Registrations, Topics, Speakers)

### Problem: Bounded Context Violations

```
events-api.openapi.yml currently contains:
├── Events (core)           → Event Management bounded context ✓
├── Sessions                → Event Management bounded context ✓
├── Registrations           → Attendee Experience bounded context ✗
├── Topics                  → DUPLICATED in topics-api.openapi.yml ✗
├── Speaker Pool            → Speaker Coordination bounded context ✗
├── Speaker Outreach        → Speaker Coordination bounded context ✗
├── Event Types             → Event Management bounded context ✓
├── Workflow                → Event Management bounded context ✓
└── Analytics               → Cross-cutting concern ✗
```

### Proposed Split

```
docs/api/
├── common-schemas.openapi.yml    [NEW] - Shared types (200 lines)
│
├── events-api.openapi.yml        [REFACTORED] - Core events only (~1,500 lines)
│   └── /events, /events/{eventCode}, /events/types, /events/{eventCode}/workflow/*
│
├── sessions-api.openapi.yml      [NEW] - Session management (~600 lines)
│   └── /events/{eventCode}/sessions/*
│
├── registrations-api.openapi.yml [NEW] - Registration management (~800 lines)
│   └── /events/{eventCode}/registrations/*, /events/registrations, /events/batch_registrations
│
├── speaker-pool-api.openapi.yml  [NEW] - Speaker pool & outreach (~500 lines)
│   └── /events/{eventCode}/speakers/pool/*, /events/{eventCode}/speakers/{speakerId}/outreach
│
├── topics-api.openapi.yml        [KEEP] - Already exists (780 lines)
│   └── REMOVE duplicate /topics endpoints from events-api
│
├── analytics-api.openapi.yml     [NEW] - Event analytics (~200 lines)
│   └── /events/{eventCode}/analytics
│
├── speakers-api.openapi.yml      [KEEP] - Speaker profiles
├── partners-api.openapi.yml      [KEEP] - Partner management
├── attendees-api.openapi.yml     [KEEP] - Attendee experience
├── companies-api.openapi.yml     [KEEP] - Company management
├── users-api.openapi.yml         [KEEP] - User management
├── file-upload-api.openapi.yml   [KEEP] - File uploads
└── auth-endpoints.openapi.yml    [KEEP] - Authentication
```

### Endpoint Migration Map

| Current Location | New Location | Endpoints |
|-----------------|--------------|-----------|
| events-api | events-api | `/events`, `/events/current`, `/events/{eventCode}`, `/events/types/*`, `/events/{eventCode}/workflow/*`, `/events/{eventCode}/publish` |
| events-api | sessions-api | `/events/{eventCode}/sessions`, `/events/{eventCode}/sessions/batch-import`, `/events/{eventCode}/sessions/{sessionSlug}`, `/events/{eventCode}/sessions/{sessionSlug}/speakers/*` |
| events-api | registrations-api | `/events/{eventCode}/registrations`, `/events/{eventCode}/registrations/{registrationCode}`, `/events/{eventCode}/registrations/confirm`, `/events/batch_registrations`, `/events/registrations` |
| events-api | speaker-pool-api | `/events/{eventCode}/speakers/pool`, `/events/{eventCode}/speakers/pool/{speakerId}`, `/events/{eventCode}/speakers/{speakerId}/outreach` |
| events-api | **DELETE** | `/topics/*` (duplicated in topics-api.openapi.yml) |
| events-api | analytics-api | `/events/{eventCode}/analytics` |

### Critical: Topic Endpoint Duplication

**Issue Discovered**: Both `events-api.openapi.yml` (lines 2601-3053) and `topics-api.openapi.yml` define `/topics` endpoints.

**Resolution**:
1. Keep `topics-api.openapi.yml` as the authoritative source (uses OpenAPI 3.1.0)
2. Delete `/topics/*` endpoints from `events-api.openapi.yml`
3. Reference `topics-api.openapi.yml` from events documentation

---

## Part 3: Common Schemas File

### Purpose

Centralize schemas that are duplicated across multiple API files.

### Currently Duplicated Schemas

| Schema | Duplicated In | Lines Saved |
|--------|---------------|-------------|
| `ErrorResponse` | All 9 APIs | ~90 lines |
| `PaginationMetadata` | 6 APIs | ~36 lines |
| `PaginatedResponse` | 6 APIs | ~60 lines |
| `BearerAuth` | All 9 APIs | ~27 lines |
| `BadRequest` response | All 9 APIs | ~45 lines |
| `Unauthorized` response | All 9 APIs | ~45 lines |
| `Forbidden` response | All 9 APIs | ~45 lines |
| `NotFound` response | All 9 APIs | ~45 lines |
| `InternalServerError` response | All 9 APIs | ~45 lines |
| **Total** | | **~438 lines** |

### common-schemas.openapi.yml Structure

```yaml
openapi: 3.1.0
info:
  title: BATbern Common Schemas
  version: 1.0.0
  description: |
    Shared schemas, responses, and security definitions used across all BATbern APIs.
    Import these schemas using $ref to ensure consistency.

components:
  schemas:
    # ═══════════════════════════════════════════════════════════
    # Error Handling
    # ═══════════════════════════════════════════════════════════
    ErrorResponse:
      type: object
      required: [error, message, timestamp]
      properties:
        error:
          type: string
          example: BAD_REQUEST
        message:
          type: string
          example: Validation failed
        timestamp:
          type: string
          format: date-time
        details:
          type: array
          items:
            $ref: '#/components/schemas/ValidationError'
        traceId:
          type: string
          description: Correlation ID for debugging

    ValidationError:
      type: object
      properties:
        field:
          type: string
        message:
          type: string
        rejectedValue:
          type: string

    # ═══════════════════════════════════════════════════════════
    # Pagination
    # ═══════════════════════════════════════════════════════════
    PaginationMetadata:
      type: object
      required: [page, limit, totalItems, totalPages]
      properties:
        page:
          type: integer
          description: Current page number (1-indexed)
          minimum: 1
          example: 1
        limit:
          type: integer
          description: Items per page
          example: 20
        totalItems:
          type: integer
          description: Total number of items
          example: 150
        totalPages:
          type: integer
          description: Total number of pages
          example: 8
        hasNext:
          type: boolean
          example: true
        hasPrevious:
          type: boolean
          example: false

    # ═══════════════════════════════════════════════════════════
    # Audit Fields (for embedding in responses)
    # ═══════════════════════════════════════════════════════════
    AuditMetadata:
      type: object
      properties:
        createdAt:
          type: string
          format: date-time
        updatedAt:
          type: string
          format: date-time
        createdBy:
          type: string
          description: Username of creator
          pattern: '^[a-z]+\.[a-z]+(\.[0-9]+)?$'
        updatedBy:
          type: string
          description: Username of last modifier
          pattern: '^[a-z]+\.[a-z]+(\.[0-9]+)?$'

  responses:
    BadRequest:
      description: Invalid request parameters
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ErrorResponse'

    Unauthorized:
      description: Authentication required
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ErrorResponse'

    Forbidden:
      description: Insufficient permissions
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ErrorResponse'

    NotFound:
      description: Resource not found
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ErrorResponse'

    UnprocessableEntity:
      description: Business rule violation
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ErrorResponse'

    InternalServerError:
      description: Unexpected server error
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ErrorResponse'

  securitySchemes:
    BearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
      description: AWS Cognito JWT token
```

### Referencing Common Schemas

```yaml
# In events-api.openapi.yml
components:
  schemas:
    EventResponse:
      allOf:
        - $ref: 'common-schemas.openapi.yml#/components/schemas/AuditMetadata'
        - $ref: '#/components/schemas/EventBase'
        - type: object
          properties:
            # event-specific fields

  responses:
    BadRequest:
      $ref: 'common-schemas.openapi.yml#/components/responses/BadRequest'
```

---

## Part 4: Implementation Approach

### Phase 1: Foundation (Week 1)

**Goal**: Create common schemas and establish patterns

| Step | Task | Files Changed | Risk |
|------|------|---------------|------|
| 1.1 | Create `common-schemas.openapi.yml` | +1 new file | Low |
| 1.2 | Upgrade all APIs to OpenAPI 3.1.0 | 9 files | Low |
| 1.3 | Standardize pagination to 1-indexed | All APIs | Medium |
| 1.4 | Standardize enum casing to UPPER_CASE | events-api, topics-api | Medium |

**Validation**: Run `npm run generate:api-types` after each step

### Phase 2: Type Inheritance (Week 2)

**Goal**: Implement three-tier schema hierarchy

| Step | Task | API | Complexity |
|------|------|-----|------------|
| 2.1 | Refactor companies-api.openapi.yml | companies-api | Low |
| 2.2 | Refactor users-api.openapi.yml | users-api | Medium |
| 2.3 | Refactor speakers-api.openapi.yml | speakers-api | Low |
| 2.4 | Refactor partners-api.openapi.yml | partners-api | Low |
| 2.5 | Refactor attendees-api.openapi.yml | attendees-api | Low |
| 2.6 | Refactor topics-api.openapi.yml | topics-api | Low |

**Validation**: Regenerate TypeScript types, verify no breaking changes

### Phase 3: Events API Decomposition (Week 3-4)

**Goal**: Split events-api into focused domain APIs

| Step | Task | New File | Lines |
|------|------|----------|-------|
| 3.1 | Extract sessions to sessions-api.openapi.yml | sessions-api | ~600 |
| 3.2 | Extract registrations to registrations-api.openapi.yml | registrations-api | ~800 |
| 3.3 | Extract speaker pool to speaker-pool-api.openapi.yml | speaker-pool-api | ~500 |
| 3.4 | Extract analytics to analytics-api.openapi.yml | analytics-api | ~200 |
| 3.5 | Delete duplicate /topics endpoints from events-api | events-api | -450 |
| 3.6 | Apply type inheritance to remaining events-api | events-api | -400 |
| 3.7 | Apply type inheritance to new split APIs | All new APIs | - |

**Validation**:
1. Run Bruno API contract tests
2. Verify frontend type generation
3. Check service implementations still compile

### Phase 4: Cleanup & Documentation (Week 5)

| Step | Task |
|------|------|
| 4.1 | Update `docs/api/README.md` with new API structure |
| 4.2 | Update frontend type generation scripts |
| 4.3 | Create API cross-reference documentation |
| 4.4 | Update architecture diagrams |
| 4.5 | Update CLAUDE.md with new structure |

---

## Risk Mitigation

### Breaking Changes

**Risk**: Renaming schemas or changing structure breaks code generation

**Mitigation**:
1. Keep old schema names as aliases during transition
2. Use `deprecated: true` for old schemas
3. Run full test suite after each change
4. Incremental rollout (one API at a time)

### Code Generation Compatibility

**Risk**: Some OpenAPI generators don't handle `allOf` well

**Mitigation**:
1. Test with openapi-generator (Java) and openapi-typescript (frontend)
2. Use simple inheritance (max 2 levels)
3. Avoid `oneOf`/`anyOf` unless necessary
4. Document generator-specific workarounds

### Cross-File References

**Risk**: `$ref` to external files may not work with all tools

**Mitigation**:
1. Test cross-file refs with all toolchain components
2. Use relative paths consistently
3. Bundle specs for tools that don't support external refs
4. Consider using Redocly or Swagger CLI for bundling

---

## Success Metrics

| Metric | Current | Target | Improvement |
|--------|---------|--------|-------------|
| Total API spec lines | 11,791 | ~9,500 | -19% |
| events-api.openapi.yml lines | 4,896 | ~1,500 | -69% |
| Duplicated field definitions | ~150 | 0 | -100% |
| Duplicated schema definitions | ~40 | 0 | -100% |
| Number of API files | 9 | 14 | +5 focused APIs |
| OpenAPI version consistency | Mixed (3.0.3/3.1.0) | 3.1.0 | 100% consistent |
| Pagination consistency | Mixed (0/1-indexed) | 1-indexed | 100% consistent |
| Enum casing consistency | Mixed | UPPER_CASE | 100% consistent |

---

## Appendix A: File Size Analysis

### Current State

```
File                              Lines    % of Total
──────────────────────────────────────────────────────
events-api.openapi.yml            4,896    41.5%   ← PROBLEM
users-api.openapi.yml             1,934    16.4%
companies-api.openapi.yml         1,047     8.9%
speakers-api.openapi.yml          1,001     8.5%
topics-api.openapi.yml              780     6.6%
partners-api.openapi.yml            778     6.6%
attendees-api.openapi.yml           501     4.2%
file-upload-api.openapi.yml         495     4.2%
auth-endpoints.openapi.yml          359     3.0%
──────────────────────────────────────────────────────
Total                            11,791   100.0%
```

### Target State

```
File                              Lines    % of Total
──────────────────────────────────────────────────────
users-api.openapi.yml             1,600    16.8%
events-api.openapi.yml            1,500    15.8%   ← FIXED
companies-api.openapi.yml           900     9.5%
speakers-api.openapi.yml            850     8.9%
registrations-api.openapi.yml       800     8.4%
topics-api.openapi.yml              700     7.4%
partners-api.openapi.yml            650     6.8%
sessions-api.openapi.yml            600     6.3%
speaker-pool-api.openapi.yml        500     5.3%
attendees-api.openapi.yml           450     4.7%
file-upload-api.openapi.yml         450     4.7%
auth-endpoints.openapi.yml          300     3.2%
analytics-api.openapi.yml           200     2.1%
common-schemas.openapi.yml          200     2.1%
──────────────────────────────────────────────────────
Total                             9,500   100.0%
```

---

## Appendix B: Type Inheritance Templates

### Template: Simple Entity

```yaml
# Base type
{Entity}Base:
  type: object
  properties:
    # Core domain fields only

# Response type
{Entity}Response:
  allOf:
    - $ref: '#/components/schemas/{Entity}Base'
    - $ref: 'common-schemas.openapi.yml#/components/schemas/AuditMetadata'
    - type: object
      required: [id]  # or meaningful identifier
      properties:
        # Response-only fields (computed, embedded)

# Create request
Create{Entity}Request:
  allOf:
    - $ref: '#/components/schemas/{Entity}Base'
    - type: object
      required: [requiredField1, requiredField2]
      properties:
        # Create-only fields (uploadIds, etc.)

# Update request
Update{Entity}Request:
  allOf:
    - $ref: '#/components/schemas/{Entity}Base'
  # All fields optional for partial updates
```

### Template: Paginated Response

```yaml
Paginated{Entity}Response:
  type: object
  required: [items, pagination]
  properties:
    items:
      type: array
      items:
        $ref: '#/components/schemas/{Entity}Response'
    pagination:
      $ref: 'common-schemas.openapi.yml#/components/schemas/PaginationMetadata'
```

---

*Document created: 2025-01-29*
*Author: Winston (Holistic System Architect)*
*Status: Draft - Pending Review*
