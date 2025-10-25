# Company Management API

This document outlines the Company Management Service API, which handles centralized company entity management with Swiss business validation, advanced querying capabilities, and verification workflows.

## Overview

The Company Management API provides endpoints for:
- Company CRUD operations with full lifecycle management
- Advanced query patterns (filter, sort, pagination, field selection, resource expansion)
- Company search with autocomplete and Caffeine caching
- Swiss UID (Unternehmens-Identifikationsnummer) validation
- Company verification workflows
- Event-driven architecture with EventBridge integration

**Architecture Context**: Companies are fully decoupled from partners and users (v2.2 refactoring). Partnership is now a first-class aggregate in Partner Coordination Service (Epic 8) with `Partnership.companyId` foreign key reference.

## API Endpoints

### Company Operations

#### List and Search Companies (Advanced Query)

```yaml
GET /api/v1/companies
tags: [Companies]
summary: List and search companies with advanced query support
operationId: listCompanies
description: |
  Retrieve a paginated list of companies with optional filtering, sorting,
  field selection, and resource expansion.

  **Filter Syntax Examples**:
  - Single filter: {"industry":"Technology"}
  - Multiple fields: {"industry":"Technology","isVerified":true}
  - Logical operators: {"$or":[{"industry":"Technology"},{"industry":"Finance"}]}
  - Comparison: {"createdAt":{"$gte":"2025-01-01T00:00:00Z"}}
  - Contains: {"name":{"$contains":"Bern"}}

  **Sort Syntax**:
  - Ascending: name or +name
  - Descending: -name
  - Multiple fields: industry,-createdAt

  **Field Selection**:
  - Specific fields: ?fields=id,name,industry
  - All fields: omit fields parameter

  **Resource Expansion**:
  - Statistics: ?include=statistics (totalEvents, totalSpeakers, totalPartners)
  - Logo: ?include=logo (url, s3Key, fileId)
  - Multiple: ?include=statistics,logo

  **Performance**:
  - Basic query: <100ms (P95)
  - With all includes: <200ms (P95)

parameters:
  - name: filter
    in: query
    description: MongoDB-style JSON filter criteria
    required: false
    schema:
      type: string
      example: '{"industry":"Technology","isVerified":true}'
  - name: sort
    in: query
    description: Sort fields (comma-separated, prefix with - for descending)
    required: false
    schema:
      type: string
      example: '-createdAt,name'
  - name: page
    in: query
    description: Page number (1-indexed)
    required: false
    schema:
      type: integer
      minimum: 1
      default: 1
  - name: limit
    in: query
    description: Items per page (max 100)
    required: false
    schema:
      type: integer
      minimum: 1
      maximum: 100
      default: 20
  - name: fields
    in: query
    description: Comma-separated field names for sparse fieldsets
    required: false
    schema:
      type: string
      example: 'id,name,industry,isVerified'
  - name: include
    in: query
    description: Comma-separated list of resources to include
    required: false
    schema:
      type: string
      example: 'statistics,logo'
responses:
  '200':
    description: Successful response with paginated companies
    headers:
      X-Cache-Status:
        description: Cache hit status (HIT or MISS)
        schema:
          type: string
          enum: [HIT, MISS]
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/PaginatedCompanyResponse'
  '400':
    description: Bad request - invalid filter syntax or parameters
  '401':
    description: Unauthorized
  '500':
    description: Internal server error
```

#### Create Company

```yaml
POST /api/v1/companies
tags: [Companies]
summary: Create a new company
operationId: createCompany
security:
  - BearerAuth: []
description: |
  Create a new company with complete company data.

  **Validation Rules**:
  - Company name must be unique
  - Swiss UID format (if provided): CHE-XXX.XXX.XXX
  - Website must be valid URL (if provided)

  **Events Published**: CompanyCreatedEvent to EventBridge

  **Performance**: <200ms (P95)

requestBody:
  required: true
  content:
    application/json:
      schema:
        $ref: '#/components/schemas/CreateCompanyRequest'
responses:
  '201':
    description: Company created successfully
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/CompanyResponse'
  '400':
    description: Bad request - validation error
  '401':
    description: Unauthorized
  '403':
    description: Forbidden - insufficient permissions
  '409':
    description: Conflict - company name already exists
  '500':
    description: Internal server error
```

### Company Search & Verification

#### Search Companies with Autocomplete

```yaml
GET /api/v1/companies/search
tags: [Company Search]
summary: Search companies with autocomplete
operationId: searchCompanies
description: |
  Search companies by name with autocomplete functionality.

  **Caching**:
  - Caffeine in-memory cache with 15-minute TTL
  - Cache key includes both query and limit
  - Automatic cache invalidation on company updates

  **Performance**:
  - Cached response: <50ms (P95)
  - Cache miss: <100ms (P95)

  **Default Results**: 20 companies (configurable via limit parameter)

parameters:
  - name: query
    in: query
    description: Search query (minimum 1 character)
    required: true
    schema:
      type: string
      minLength: 1
      example: 'Swiss'
  - name: limit
    in: query
    description: Maximum number of results (default 20)
    required: false
    schema:
      type: integer
      minimum: 1
      maximum: 100
      default: 20
responses:
  '200':
    description: Search results returned successfully
    headers:
      X-Cache-Status:
        description: Cache hit status (HIT or MISS)
        schema:
          type: string
          enum: [HIT, MISS]
    content:
      application/json:
        schema:
          type: array
          items:
            $ref: '#/components/schemas/CompanySearchResponse'
  '400':
    description: Bad request - query too short
  '401':
    description: Unauthorized
  '500':
    description: Internal server error
```

#### Validate Swiss UID

```yaml
GET /api/v1/companies/validate-uid
tags: [Company Verification]
summary: Validate Swiss UID format
operationId: validateUID
description: |
  Validates Swiss company UID (Unternehmens-Identifikationsnummer) format.

  **Expected Format**: CHE-XXX.XXX.XXX

  **Validation Rules**:
  - Must start with "CHE-"
  - Followed by 9 digits in XXX.XXX.XXX format
  - Total length: 15 characters

  **Future Enhancement**: Integration with Swiss Business Registry for real-time validation

parameters:
  - name: uid
    in: query
    description: Swiss UID to validate
    required: true
    schema:
      type: string
      pattern: '^CHE-\d{3}\.\d{3}\.\d{3}$'
      example: 'CHE-123.456.789'
responses:
  '200':
    description: Validation result returned
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/UIDValidationResponse'
  '400':
    description: Bad request - missing UID parameter
  '401':
    description: Unauthorized
  '500':
    description: Internal server error
```

#### Verify Company

```yaml
POST /api/v1/companies/{id}/verify
tags: [Company Verification]
summary: Verify company
operationId: verifyCompany
description: |
  Marks a company as verified by an ORGANIZER.

  **Authorization**: ORGANIZER role required

  **Events Published**: CompanyVerifiedEvent to EventBridge

  **Idempotency**: Safe to call multiple times (no error if already verified)

security:
  - BearerAuth: []
parameters:
  - name: id
    in: path
    required: true
    schema:
      type: string
      format: uuid
responses:
  '200':
    description: Company verified successfully
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/CompanyResponse'
  '401':
    description: Unauthorized
  '403':
    description: Forbidden - requires ORGANIZER role
  '404':
    description: Company not found
  '500':
    description: Internal server error
```

## Schemas

### CompanyResponse

```yaml
CompanyResponse:
  type: object
  required:
    - id
    - name
    - isVerified
    - createdAt
    - updatedAt
  properties:
    id:
      type: string
      format: uuid
      example: 550e8400-e29b-41d4-a716-446655440000
    name:
      type: string
      maxLength: 255
      example: Swiss IT Solutions AG
    displayName:
      type: string
      maxLength: 255
      example: Swiss IT Solutions
    swissUID:
      type: string
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
      maxLength: 5000
      example: Leading Swiss IT consulting firm specializing in cloud-native architectures
    isVerified:
      type: boolean
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
      description: User ID who created this company
      example: auth0|user_abc123
    statistics:
      $ref: '#/components/schemas/CompanyStatistics'
    logo:
      $ref: '#/components/schemas/CompanyLogo'
```

### CompanyStatistics

```yaml
CompanyStatistics:
  type: object
  description: Company statistics (included when ?include=statistics)
  properties:
    totalEvents:
      type: integer
      description: Total number of events the company participated in
      example: 12
    totalSpeakers:
      type: integer
      description: Total number of speakers from this company
      example: 8
    totalPartners:
      type: integer
      description: Total number of partnership engagements
      example: 5
```

### CompanyLogo

```yaml
CompanyLogo:
  type: object
  description: Company logo details (included when ?include=logo)
  properties:
    url:
      type: string
      format: uri
      description: CloudFront CDN URL for logo (cdn.batbern.ch for production, cdn.staging.batbern.ch for staging)
      example: https://cdn.batbern.ch/logos/2025/550e8400-e29b-41d4-a716-446655440000/logo.png
    s3Key:
      type: string
      description: S3 object key
      example: logos/2025/550e8400-e29b-41d4-a716-446655440000/logo.png
    fileId:
      type: string
      description: Internal file identifier
      example: file-123
```

### CompanySearchResponse

```yaml
CompanySearchResponse:
  type: object
  required:
    - id
    - name
    - isVerified
  properties:
    id:
      type: string
      format: uuid
      example: 550e8400-e29b-41d4-a716-446655440000
    name:
      type: string
      example: Swiss IT Solutions AG
    displayName:
      type: string
      example: Swiss IT Solutions
    swissUID:
      type: string
      example: CHE-123.456.789
    industry:
      type: string
      example: Technology
    isVerified:
      type: boolean
      example: true
```

### CreateCompanyRequest

```yaml
CreateCompanyRequest:
  type: object
  required:
    - name
  properties:
    name:
      type: string
      minLength: 2
      maxLength: 255
      example: Swiss IT Solutions AG
    displayName:
      type: string
      maxLength: 255
      example: Swiss IT Solutions
    swissUID:
      type: string
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
      maxLength: 5000
      example: Leading Swiss IT consulting firm
```

### UpdateCompanyRequest

```yaml
UpdateCompanyRequest:
  type: object
  properties:
    name:
      type: string
      maxLength: 255
      example: Swiss IT Solutions AG
    displayName:
      type: string
      maxLength: 255
      example: Swiss IT Solutions
    swissUID:
      type: string
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
      maxLength: 5000
      example: Leading Swiss IT consulting firm
```

### UIDValidationResponse

```yaml
UIDValidationResponse:
  type: object
  required:
    - valid
    - uid
    - message
  properties:
    valid:
      type: boolean
      example: true
    uid:
      type: string
      example: CHE-123.456.789
    message:
      type: string
      example: Valid Swiss UID format
```

### PaginatedCompanyResponse

```yaml
PaginatedCompanyResponse:
  type: object
  required:
    - data
    - pagination
  properties:
    data:
      type: array
      items:
        $ref: '#/components/schemas/CompanyResponse'
    pagination:
      $ref: '#/components/schemas/PaginationMetadata'
```

### PaginationMetadata

```yaml
PaginationMetadata:
  type: object
  required:
    - page
    - limit
    - totalItems
    - totalPages
    - hasNext
    - hasPrev
  properties:
    page:
      type: integer
      description: Current page (1-indexed)
      example: 1
    limit:
      type: integer
      description: Items per page
      example: 20
    totalItems:
      type: integer
      description: Total number of items
      example: 100
    totalPages:
      type: integer
      description: Total number of pages
      example: 5
    hasNext:
      type: boolean
      description: Whether there is a next page
      example: true
    hasPrev:
      type: boolean
      description: Whether there is a previous page
      example: false
```

## Architecture Patterns

### Caching Strategy

**Caffeine In-Memory Cache**:
- Cache provider: Caffeine (application-level in-memory)
- Search cache TTL: 15 minutes (expireAfterWrite)
- Max cache size: 1000 entries
- Eviction policy: LRU (Least Recently Used)
- Cache key format: `search:{query}:{limit}`
- Cache invalidation: Automatic on company create/update/delete operations

**Performance Benefits**:
- Cached search response: <50ms (P95)
- Cache miss response: <100ms (P95)
- Reduces database load for frequently searched queries
- X-Cache-Status header indicates HIT or MISS

### Event-Driven Architecture

**EventBridge Integration**:
- Event bus: `batbern-{environment}-event-bus`
- Event source: `ch.batbern.company`
- Published events:
  - `CompanyCreatedEvent`: When new company is created
  - `CompanyUpdatedEvent`: When company data is modified
  - `CompanyDeletedEvent`: When company is removed
  - `CompanyVerifiedEvent`: When company verification status changes

**Event Schema Example**:
```json
{
  "companyId": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Swiss IT Solutions AG",
  "industry": "Technology",
  "isVerified": true,
  "eventTimestamp": "2025-01-15T10:00:00Z"
}
```

**Event Consumers**:
- Partner Coordination Service: Updates partnership records
- Event Management Service: Updates speaker/sponsor affiliations
- Analytics Service: Tracks company engagement metrics
- Notification Service: Sends verification status updates

### Advanced Query Patterns

**Filter Syntax** (MongoDB-style JSON):
```json
{
  "industry": "Technology",
  "isVerified": true,
  "createdAt": {"$gte": "2025-01-01T00:00:00Z"}
}
```

**Supported Operators**:
- `$eq`, `$ne`: Equality/inequality
- `$gt`, `$gte`, `$lt`, `$lte`: Comparison
- `$in`, `$nin`: Membership
- `$contains`: Substring search (case-insensitive)
- `$and`, `$or`, `$not`: Logical operators

**Sort Syntax**:
- Ascending: `name` or `+name`
- Descending: `-name`
- Multiple fields: `industry,-createdAt`

**Pagination**:
- 1-indexed pages (page=1 is first page)
- Default limit: 20 items per page
- Maximum limit: 100 items per page
- Response includes: `totalItems`, `totalPages`, `hasNext`, `hasPrev`

**Field Selection** (Sparse Fieldsets):
- Request specific fields: `?fields=id,name,industry`
- Reduces payload size for large result sets
- Always includes `id` field regardless of selection

**Resource Expansion**:
- `?include=statistics`: Adds totalEvents, totalSpeakers, totalPartners
- `?include=logo`: Adds logo URL, S3 key, file ID
- `?include=statistics,logo`: Combines multiple expansions
- Performance target: <200ms (P95) with all expansions

### Security & Authorization

**Authentication**:
- JWT-based authentication via AWS Cognito
- Bearer token required for all endpoints
- Token claims: `sub` (Cognito user ID), `email`, `custom:role` (roles from database)

**Authorization Levels**:
- Public (authenticated): GET endpoints for listing/searching companies
- ORGANIZER/SPEAKER/PARTNER: POST company creation
- ORGANIZER only: PUT/PATCH/DELETE operations, company verification

**Role-Based Access Control**:
- Implemented with Spring Security `@PreAuthorize` annotations
- Role extraction from JWT `custom:role` claim (populated by PreTokenGeneration Lambda from database)
- Method-level security enforcement

### Database Design

**Companies Table Schema**:
```sql
CREATE TABLE companies (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255),
    swiss_uid VARCHAR(20),
    website VARCHAR(500),
    industry VARCHAR(100),
    description TEXT,
    logo_url VARCHAR(500),
    logo_s3_key VARCHAR(500),
    logo_file_id VARCHAR(255),
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255) NOT NULL
);

-- Performance Indexes
CREATE INDEX idx_company_name ON companies(name);
CREATE INDEX idx_company_swiss_uid ON companies(swiss_uid);
CREATE INDEX idx_company_is_verified ON companies(is_verified);
CREATE INDEX idx_company_created_at ON companies(created_at DESC);
```

**Database Features**:
- Unique constraint on company name (enforced at DB level)
- Automatic timestamp management with triggers
- UUID-based primary keys for distributed system compatibility
- Optimized indexes for common query patterns
