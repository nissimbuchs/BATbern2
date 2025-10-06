# Core API Design

This document outlines the foundational API design patterns, external integrations, and common components shared across all BATbern Event Management Platform domain APIs.

## OpenAPI Specification

```yaml
openapi: 3.0.0
info:
  title: BATbern Event Management Platform API
  version: 1.0.0
  description: Comprehensive API for the BATbern Event Management Platform supporting organizers, speakers, partners, and attendees
servers:
  - url: https://api.batbern.ch
    description: Production API
  - url: https://api-staging.batbern.ch
    description: Staging API
  - url: https://api-dev.batbern.ch
    description: Development API

security:
  - BearerAuth: []
```

## Domain API Documentation

The BATbern API is organized by domain-driven design principles. Each domain has its own detailed API documentation:

- **[Event Management API](04-api-event-management.md)** - Event lifecycle, workflows, slots, and organizer operations
- **[Speaker Coordination API](04-api-speaker-coordination.md)** - Speaker management, preferences, quality review, and materials
- **[Partner Coordination API](04-api-partner-coordination.md)** - Partner relationships, topic voting, and meeting coordination
- **[Attendee Experience API](04-api-attendee-experience.md)** - Registration, content discovery, and attendee dashboard
- **[Company Management API](04-api-company-management.md)** - Company profiles, logos, and file storage

## External APIs

### AWS Cognito API

- **Purpose:** Multi-role user authentication and authorization management
- **Documentation:** https://docs.aws.amazon.com/cognito/latest/developerguide/
- **Base URL(s):** https://cognito-idp.eu-central-1.amazonaws.com/
- **Authentication:** AWS IAM roles and policies
- **Rate Limits:** 10,000 requests per second per user pool

**Key Endpoints Used:**
- `POST /oauth2/token` - JWT token generation and refresh
- `POST /admin/createUser` - Programmatic user creation for organizers
- `GET /oauth2/userInfo` - User profile and role information

**Integration Notes:** Custom attributes store BATbern-specific roles (organizer, speaker, partner, attendee). Lambda triggers handle user registration workflows and role assignment validation.

### AWS SES API

- **Purpose:** Transactional email delivery for notifications, invitations, and newsletter distribution
- **Documentation:** https://docs.aws.amazon.com/ses/latest/dg/
- **Base URL(s):** https://email.eu-central-1.amazonaws.com/
- **Authentication:** AWS IAM service roles
- **Rate Limits:** 14 emails per second (adjustable based on reputation)

**Key Endpoints Used:**
- `POST /v2/email/outbound-emails` - Send transactional emails
- `POST /v2/email/bulk-emails` - Newsletter distribution
- `POST /v2/email/templates` - Email template management

**Integration Notes:** Templates stored for speaker invitations, event notifications, and partner reports. Bounce and complaint handling integrated with user management.

### AWS S3 API

- **Purpose:** File storage for logos, presentations, handouts, and historical content
- **Documentation:** https://docs.aws.amazon.com/s3/latest/API/
- **Base URL(s):** https://s3.eu-central-1.amazonaws.com/
- **Authentication:** AWS IAM service roles with bucket policies
- **Rate Limits:** 3,500 PUT/POST requests per second per prefix

**Key Endpoints Used:**
- `PUT /bucket/{key}` - File upload with metadata
- `GET /bucket/{key}` - File retrieval with access control
- `POST /bucket?delete` - Batch file deletion for content management

**Integration Notes:** Presigned URLs for direct browser uploads. Lifecycle policies for archival content. CloudFront integration for global CDN distribution.

## API Design Principles

### Authentication & Authorization
- **JWT Bearer Tokens:** AWS Cognito-issued tokens with custom claims
- **Role-Based Access Control:** Fine-grained permissions per user type
- **Rate Limiting:** Per-user and per-endpoint rate limits
- **API Key Management:** Service-to-service authentication

### Data Consistency
- **Eventual Consistency:** Cross-service data synchronization via events
- **Optimistic Locking:** Concurrent update conflict resolution
- **Idempotency:** Safe retry mechanisms for critical operations
- **Data Validation:** Schema validation at API gateway level

### Performance Optimization
- **Response Caching:** Redis caching for frequently accessed data
- **Pagination:** Consistent pagination patterns across all list endpoints
- **Field Selection:** GraphQL-style field selection for large objects
- **Bulk Operations:** Batch endpoints for high-volume operations

### Error Handling
- **Standard HTTP Status Codes:** Consistent error response patterns
- **Detailed Error Messages:** Structured error objects with context
- **Correlation IDs:** Request tracing across service boundaries
- **Circuit Breakers:** Fault tolerance for external service calls

## Common Components & Schemas

### Security Schemes

```yaml
components:
  securitySchemes:
    BearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
```

### Common Schemas

```yaml
Pagination:
  type: object
  properties:
    limit:
      type: integer
      default: 20
      maximum: 100
    offset:
      type: integer
      default: 0
    total:
      type: integer
      description: Total number of items
    hasMore:
      type: boolean
      description: Whether more items are available

ErrorResponse:
  type: object
  properties:
    error:
      type: object
      properties:
        code:
          type: string
          description: Machine-readable error code
        message:
          type: string
          description: Human-readable error message
        details:
          type: object
          additionalProperties: true
          description: Additional error context
        timestamp:
          type: string
          format: date-time
        requestId:
          type: string
          format: uuid
          description: Correlation ID for tracing
        path:
          type: string
          description: Request path that generated the error
        severity:
          type: string
          enum: [LOW, MEDIUM, HIGH, CRITICAL]

Venue:
  type: object
  properties:
    name:
      type: string
    address:
      type: string
    city:
      type: string
    postalCode:
      type: string
    capacity:
      type: integer

Session:
  type: object
  properties:
    id:
      type: string
      format: uuid
    title:
      type: string
    description:
      type: string
    startTime:
      type: string
      format: date-time
    endTime:
      type: string
      format: date-time
    speakers:
      type: array
      items:
        $ref: '#/components/schemas/SessionSpeaker'
    materials:
      type: array
      items:
        $ref: '#/components/schemas/ContentMetadata'

SessionSpeaker:
  type: object
  properties:
    speakerId:
      type: string
      format: uuid
    workflowState:
      $ref: '#/components/schemas/SpeakerWorkflowState'
    invitedAt:
      type: string
      format: date-time
    confirmedAt:
      type: string
      format: date-time
    declinedAt:
      type: string
      format: date-time

SpeakerProfile:
  type: object
  properties:
    bio:
      type: string
      maxLength: 500
    photoUrl:
      type: string
      format: uri
    expertiseAreas:
      type: array
      items:
        type: string
    socialLinks:
      type: object
      properties:
        linkedin:
          type: string
          format: uri
        twitter:
          type: string
          format: uri
        website:
          type: string
          format: uri

CompanyLogo:
  type: object
  properties:
    fileId:
      type: string
      format: uuid
    url:
      type: string
      format: uri
    thumbnailUrl:
      type: string
      format: uri
    uploadedAt:
      type: string
      format: date-time

ContentMetadata:
  type: object
  properties:
    fileId:
      type: string
      format: uuid
    filename:
      type: string
    contentType:
      type: string
      enum: [presentation, logo, speaker_photo, speaker_cv, event_photo, archive_material]
    mimeType:
      type: string
    fileSizeBytes:
      type: integer
      format: int64
    uploadedBy:
      type: string
      format: uuid
    uploadedAt:
      type: string
      format: date-time
    cdnUrl:
      type: string
      format: uri
      description: CloudFront CDN URL for file access
    status:
      type: string
      enum: [pending, uploading, completed, failed, deleted]
    checksum:
      type: string
      description: SHA-256 checksum for integrity verification
```

## API Versioning Strategy

All APIs use URL-based versioning with the format `/api/v{version}/...`:
- Current version: `v1`
- Version changes trigger new major version only for breaking changes
- Backward-compatible additions made to existing version
- Deprecated endpoints maintained for minimum 6 months with deprecation headers

## Cross-Cutting Concerns

### Request/Response Headers

**Standard Request Headers:**
- `Authorization: Bearer {token}` - JWT authentication token
- `X-Correlation-ID: {uuid}` - Request correlation ID (auto-generated if not provided)
- `Accept-Language: {locale}` - Preferred response language (en, de)
- `Content-Type: application/json` - Request content type

**Standard Response Headers:**
- `X-Correlation-ID: {uuid}` - Request correlation ID for tracing
- `X-RateLimit-Limit: {number}` - Rate limit threshold
- `X-RateLimit-Remaining: {number}` - Remaining requests in window
- `X-RateLimit-Reset: {timestamp}` - Rate limit window reset time

### Rate Limiting

All endpoints enforce rate limiting based on user role:
- **Attendee:** 100 requests per minute
- **Speaker:** 200 requests per minute
- **Partner:** 300 requests per minute
- **Organizer:** 500 requests per minute

Rate limit exceeded returns `429 Too Many Requests` with retry-after header.

### Caching Strategy

- **GET endpoints:** Support `ETag` and `If-None-Match` headers
- **Cache-Control headers:** Set appropriately per endpoint
- **CDN caching:** Static content (logos, archived materials) cached at edge
- **API caching:** Redis-backed for frequently accessed data (15-minute TTL)

### Pagination

All list endpoints support consistent pagination parameters:
- `limit` - Items per page (default: 20, max: 100)
- `offset` - Number of items to skip (default: 0)
- Alternative: `cursor` - Opaque cursor for cursor-based pagination

Response includes pagination metadata:
```json
{
  "data": [...],
  "pagination": {
    "limit": 20,
    "offset": 0,
    "total": 150,
    "hasMore": true
  }
}
```

### Field Selection

Large object endpoints support field selection via `fields` query parameter:
- `fields=id,title,status` - Return only specified fields
- Reduces payload size and improves performance
- Nested field selection: `fields=id,venue.name,venue.city`

### Internationalization

All text responses support English (en) and German (de) via `Accept-Language` header:
- Error messages localized based on locale
- Email templates use recipient's preferred language
- Date/time formatting follows locale conventions
