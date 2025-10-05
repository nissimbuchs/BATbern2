# API Design Principles

This document defines the design principles, conventions, and standards for all BATbern APIs.

## Table of Contents

1. [RESTful Design](#restful-design)
2. [URL Structure](#url-structure)
3. [HTTP Methods](#http-methods)
4. [Query Parameters](#query-parameters)
5. [Request/Response Format](#requestresponse-format)
6. [Error Handling](#error-handling)
7. [Authentication & Authorization](#authentication--authorization)
8. [Versioning](#versioning)
9. [Pagination](#pagination)
10. [Rate Limiting](#rate-limiting)
11. [Caching](#caching)
12. [Security](#security)

---

## RESTful Design

### Resource-Oriented URLs

Every API endpoint represents a resource or collection of resources:

```
✅ GOOD:
GET  /api/v1/events           - Collection of events
GET  /api/v1/events/123       - Single event
POST /api/v1/events           - Create event

❌ BAD:
GET  /api/v1/getAllEvents
POST /api/v1/createEvent
GET  /api/v1/eventById?id=123
```

### Resource Naming

- Use **plural nouns** for collections: `/events`, `/speakers`, `/topics`
- Use **kebab-case** for multi-word resources: `/partner-analytics`, `/event-registrations`
- Keep URLs short and intuitive
- Avoid deep nesting (max 2 levels): `/events/{id}/sessions` ✅, `/events/{id}/sessions/{id}/speakers/{id}` ❌

### Nested Resources

For sub-resources, use nesting when the relationship is strong:

```
GET /api/v1/events/{eventId}/sessions       - Sessions belong to an event
GET /api/v1/events/{eventId}/registrations  - Registrations belong to an event
```

For weak relationships, use query parameters:

```
GET /api/v1/sessions?eventId=123            - Sessions can exist independently
GET /api/v1/users?companyId=456             - Users can exist without a company
```

---

## URL Structure

### Standard Format

```
https://{host}/api/{version}/{resource}[/{id}][?queryParams]

Examples:
https://api.batbern.ch/api/v1/events
https://api.batbern.ch/api/v1/events/123
https://api.batbern.ch/api/v1/events/123?include=speakers,venue
https://api.batbern.ch/api/v1/events?filter={"status":"published"}
```

### URL Components

| Component | Description | Example |
|-----------|-------------|---------|
| `{host}` | API domain | `api.batbern.ch` |
| `/api` | API prefix | Always `/api` |
| `{version}` | API version | `v1`, `v2` |
| `{resource}` | Resource type | `events`, `speakers` |
| `{id}` | Resource identifier | UUID or numeric ID |
| `?queryParams` | Query string | Filtering, sorting, pagination |

---

## HTTP Methods

### Standard CRUD Operations

| Method | Purpose | Request Body | Idempotent | Safe |
|--------|---------|--------------|------------|------|
| **GET** | Retrieve resource(s) | No | Yes | Yes |
| **POST** | Create new resource | Yes | No | No |
| **PUT** | Replace entire resource | Yes | Yes | No |
| **PATCH** | Update part of resource | Yes | Yes | No |
| **DELETE** | Remove resource | No | Yes | No |

### Method Usage

**GET** - Retrieve data:
```http
GET /api/v1/events/123
GET /api/v1/events?filter={"status":"published"}
```

**POST** - Create new resource:
```http
POST /api/v1/events
Content-Type: application/json

{
  "title": "BATbern 2025",
  "date": "2025-05-15",
  "venue": "Bern Convention Center"
}
```

**PUT** - Full replacement (all fields required):
```http
PUT /api/v1/events/123
Content-Type: application/json

{
  "title": "BATbern 2025 Updated",
  "date": "2025-05-16",
  "venue": "Bern Convention Center",
  "description": "Annual tech conference",
  ... (all other required fields)
}
```

**PATCH** - Partial update (only changed fields):
```http
PATCH /api/v1/events/123
Content-Type: application/json

{
  "title": "BATbern 2025 Updated",
  "date": "2025-05-16"
}
```

**DELETE** - Remove resource:
```http
DELETE /api/v1/events/123
```

### Custom Actions

For operations that don't fit CRUD, use POST with action in URL:

```http
POST /api/v1/events/123/publish
POST /api/v1/events/123/workflow/advance
POST /api/v1/speakers/123/invite
POST /api/v1/topics/123/archive
```

---

## Query Parameters

All GET endpoints support standard query parameters for filtering, sorting, pagination, field selection, and resource expansion.

### Filtering

Use JSON filter syntax for complex queries:

```http
GET /api/v1/events?filter={"status":"published","year":2024}
GET /api/v1/speakers?filter={"$or":[{"industry":"tech"},{"industry":"finance"}]}
```

**Supported Operators**:

| Operator | Description | Example |
|----------|-------------|---------|
| `$eq` | Equals (default) | `{"status":"published"}` or `{"status":{"$eq":"published"}}` |
| `$ne` | Not equals | `{"status":{"$ne":"draft"}}` |
| `$gt` | Greater than | `{"attendees":{"$gt":100}}` |
| `$gte` | Greater than or equal | `{"attendees":{"$gte":100}}` |
| `$lt` | Less than | `{"year":{"$lt":2025}}` |
| `$lte` | Less than or equal | `{"year":{"$lte":2024}}` |
| `$in` | In array | `{"status":{"$in":["published","archived"]}}` |
| `$nin` | Not in array | `{"status":{"$nin":["draft","deleted"]}}` |
| `$like` | Pattern match (case-insensitive) | `{"title":{"$like":"%conference%"}}` |
| `$and` | Logical AND | `{"$and":[{"status":"published"},{"year":2024}]}` |
| `$or` | Logical OR | `{"$or":[{"status":"published"},{"featured":true}]}` |

### Sorting

Sort results using `+` for ascending, `-` for descending:

```http
GET /api/v1/events?sort=-createdAt          # Descending by createdAt
GET /api/v1/events?sort=+title,-date        # Multiple fields
```

### Pagination

Use `page` and `limit` parameters:

```http
GET /api/v1/events?page=2&limit=50
```

**Default values**:
- `page`: 1 (first page)
- `limit`: 20 (max: 100)

**Response includes pagination metadata**:
```json
{
  "data": [...],
  "pagination": {
    "page": 2,
    "limit": 50,
    "total": 250,
    "totalPages": 5,
    "hasNext": true,
    "hasPrev": true
  }
}
```

### Field Selection

Request only specific fields to reduce payload size:

```http
GET /api/v1/events?fields=id,title,date,venue
```

**Benefits**:
- Reduced bandwidth usage
- Faster response times
- Lower mobile data consumption

### Resource Expansion

Include related resources in a single request:

```http
GET /api/v1/events/123?include=speakers,sessions,venue
```

**Without expansion** (requires 4 API calls):
```http
GET /api/v1/events/123
GET /api/v1/events/123/speakers
GET /api/v1/events/123/sessions
GET /api/v1/events/123/venue
```

**With expansion** (requires 1 API call):
```http
GET /api/v1/events/123?include=speakers,sessions,venue
```

**Response includes expanded data**:
```json
{
  "id": "123",
  "title": "BATbern 2025",
  "date": "2025-05-15",
  "speakers": [
    { "id": "456", "name": "John Doe", ... },
    { "id": "789", "name": "Jane Smith", ... }
  ],
  "sessions": [...],
  "venue": { "id": "999", "name": "Bern Convention Center", ... }
}
```

### Combining Parameters

All query parameters can be combined:

```http
GET /api/v1/events?filter={"status":"published","year":2024}&sort=-date&page=1&limit=20&fields=id,title,date&include=speakers,venue
```

---

## Request/Response Format

### Content Type

All requests and responses use JSON:

```http
Content-Type: application/json
Accept: application/json
```

### Request Body Format

**POST/PUT/PATCH requests**:

```json
{
  "title": "BATbern 2025",
  "date": "2025-05-15",
  "venue": {
    "name": "Bern Convention Center",
    "address": "Mingerstrasse 6, 3014 Bern"
  }
}
```

### Response Format

**Single Resource**:
```json
{
  "id": "123",
  "title": "BATbern 2025",
  "date": "2025-05-15",
  "status": "published",
  "createdAt": "2024-01-15T10:00:00Z",
  "updatedAt": "2024-10-01T14:30:00Z"
}
```

**Collection**:
```json
{
  "data": [
    { "id": "123", "title": "Event 1", ... },
    { "id": "456", "title": "Event 2", ... }
  ],
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 45,
    "totalPages": 3,
    "hasNext": true,
    "hasPrev": false
  }
}
```

### Date/Time Format

All timestamps use ISO 8601 format (UTC):

```json
{
  "createdAt": "2024-10-04T10:30:00Z",
  "eventDate": "2025-05-15T09:00:00Z"
}
```

---

## Error Handling

### HTTP Status Codes

| Code | Meaning | When to Use |
|------|---------|-------------|
| **200 OK** | Success | GET, PUT, PATCH successful |
| **201 Created** | Resource created | POST successful |
| **204 No Content** | Success, no body | DELETE successful |
| **400 Bad Request** | Validation error | Invalid request data |
| **401 Unauthorized** | Not authenticated | Missing/invalid JWT token |
| **403 Forbidden** | Not authorized | Insufficient permissions |
| **404 Not Found** | Resource missing | Resource doesn't exist |
| **409 Conflict** | Conflict | Duplicate resource, constraint violation |
| **422 Unprocessable Entity** | Semantic error | Business rule violation |
| **429 Too Many Requests** | Rate limit exceeded | Too many requests |
| **500 Internal Server Error** | Server error | Unexpected server failure |
| **503 Service Unavailable** | Service down | Maintenance, overload |

### Error Response Format

All errors follow a consistent structure:

```json
{
  "timestamp": "2025-10-04T10:30:00Z",
  "path": "/api/v1/events",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "correlationId": "abc-123-def-456",
  "details": {
    "validationErrors": {
      "title": "Title is required",
      "date": "Date must be in the future",
      "venue.name": "Venue name is required"
    }
  }
}
```

### Error Codes

Standard error codes for categorization:

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `VALIDATION_ERROR` | 400 | Request validation failed |
| `AUTHENTICATION_REQUIRED` | 401 | JWT token missing or invalid |
| `AUTHORIZATION_FAILED` | 403 | Insufficient permissions |
| `RESOURCE_NOT_FOUND` | 404 | Resource doesn't exist |
| `DUPLICATE_RESOURCE` | 409 | Resource already exists |
| `BUSINESS_RULE_VIOLATION` | 422 | Business logic constraint |
| `RATE_LIMIT_EXCEEDED` | 429 | Too many requests |
| `INTERNAL_ERROR` | 500 | Unexpected server error |
| `SERVICE_UNAVAILABLE` | 503 | Service temporarily down |

### Validation Errors

Validation errors include field-level details:

```json
{
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": {
    "validationErrors": {
      "email": "Invalid email format",
      "password": "Password must be at least 8 characters",
      "age": "Age must be a positive number"
    }
  }
}
```

---

## Authentication & Authorization

### JWT Bearer Token

All authenticated endpoints require a JWT token in the `Authorization` header:

```http
GET /api/v1/events
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

### Token Acquisition

Obtain tokens via the login endpoint:

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123"
}

Response:
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "...",
  "expiresIn": 3600,
  "user": {
    "id": "123",
    "email": "user@example.com",
    "role": "ORGANIZER"
  }
}
```

### Token Refresh

Refresh expired access tokens:

```http
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "..."
}

Response:
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 3600
}
```

### Role-Based Access Control

API endpoints enforce role-based permissions:

| Role | Permissions |
|------|-------------|
| **ORGANIZER** | Full access to all resources |
| **SPEAKER** | Read events, manage own submissions |
| **PARTNER** | Read events, access partner analytics |
| **ATTENDEE** | Read public events, manage registrations |

### Authorization Errors

```json
{
  "status": 403,
  "error": "AUTHORIZATION_FAILED",
  "message": "Insufficient permissions to access this resource",
  "details": {
    "requiredRole": "ORGANIZER",
    "userRole": "SPEAKER"
  }
}
```

---

## Versioning

### URL-Based Versioning

All APIs are versioned in the URL path:

```
/api/v1/events  - Version 1
/api/v2/events  - Version 2 (future breaking changes)
```

### Deprecation Policy

- Old versions are maintained for **12 months** after new version release
- Deprecated endpoints include a warning header:
  ```http
  Deprecation: true
  Sunset: 2026-01-01T00:00:00Z
  Link: <https://api.batbern.ch/api/v2/events>; rel="successor-version"
  ```

### Breaking vs Non-Breaking Changes

**Breaking changes** (require new version):
- Removing endpoints or fields
- Changing field types
- Changing authentication requirements
- Changing error response format

**Non-breaking changes** (same version):
- Adding new endpoints
- Adding optional fields
- Adding query parameters
- Adding error codes

---

## Pagination

### Cursor vs Offset Pagination

**Offset Pagination** (default):
```http
GET /api/v1/events?page=2&limit=20
```

Response:
```json
{
  "data": [...],
  "pagination": {
    "page": 2,
    "limit": 20,
    "total": 150,
    "totalPages": 8,
    "hasNext": true,
    "hasPrev": true
  }
}
```

**Cursor Pagination** (for real-time data):
```http
GET /api/v1/notifications?cursor=abc123&limit=50
```

Response:
```json
{
  "data": [...],
  "pagination": {
    "cursor": "def456",
    "limit": 50,
    "hasNext": true
  }
}
```

---

## Rate Limiting

### Limits

Rate limits vary by authentication status and endpoint:

| User Type | Rate Limit | Window |
|-----------|------------|--------|
| Unauthenticated | 60 requests | 1 minute |
| Authenticated | 1000 requests | 1 minute |
| Admin/Organizer | 5000 requests | 1 minute |

### Rate Limit Headers

All responses include rate limit headers:

```http
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 945
X-RateLimit-Reset: 1696243200
```

### Rate Limit Exceeded

```json
{
  "status": 429,
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "Too many requests. Please try again later.",
  "details": {
    "limit": 1000,
    "retryAfter": 60
  }
}
```

---

## Caching

### Cache-Control Headers

GET responses include caching directives:

```http
# Cacheable for 15 minutes
Cache-Control: public, max-age=900

# Never cache
Cache-Control: no-store, no-cache, must-revalidate

# Cache with validation
Cache-Control: public, max-age=3600
ETag: "abc123"
Last-Modified: Wed, 04 Oct 2024 10:30:00 GMT
```

### Conditional Requests

Use `If-None-Match` or `If-Modified-Since` to avoid redundant transfers:

```http
GET /api/v1/events/123
If-None-Match: "abc123"

Response: 304 Not Modified (if unchanged)
Response: 200 OK (if changed, with new ETag)
```

---

## Security

### HTTPS Only

All API requests must use HTTPS. HTTP requests are rejected:

```
http://api.batbern.ch/api/v1/events → 403 Forbidden
https://api.batbern.ch/api/v1/events → 200 OK
```

### CORS

Cross-Origin Resource Sharing (CORS) is enabled for allowed origins:

```http
Access-Control-Allow-Origin: https://app.batbern.ch
Access-Control-Allow-Methods: GET, POST, PUT, PATCH, DELETE
Access-Control-Allow-Headers: Authorization, Content-Type
Access-Control-Max-Age: 86400
```

### Security Headers

All responses include security headers:

```http
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Content-Security-Policy: default-src 'self'
```

### Input Validation

All input is validated:
- **SQL Injection**: Prevented via parameterized queries (JPA/Hibernate)
- **XSS**: Prevented via output encoding
- **Command Injection**: Input sanitization
- **Path Traversal**: Whitelist validation

### Sensitive Data

Sensitive fields are never logged or included in error responses:
- Passwords
- JWT tokens
- API keys
- Personal identification numbers

---

## Examples

### Complete Request/Response Example

**Request**:
```http
GET /api/v1/events?filter={"status":"published","year":2024}&sort=-date&page=1&limit=10&include=speakers,venue
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
Accept: application/json
```

**Response**:
```http
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: public, max-age=900
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 945
X-RateLimit-Reset: 1696243200
ETag: "abc123def456"

{
  "data": [
    {
      "id": "evt-001",
      "title": "BATbern 2024 Fall Conference",
      "date": "2024-09-15T09:00:00Z",
      "status": "published",
      "year": 2024,
      "speakers": [
        {
          "id": "spk-001",
          "name": "Dr. Anna Schmidt",
          "company": "SwissTech AG",
          "title": "AI and the Future of Work"
        }
      ],
      "venue": {
        "id": "ven-001",
        "name": "Bern Convention Center",
        "address": "Mingerstrasse 6, 3014 Bern",
        "capacity": 500
      },
      "createdAt": "2024-01-15T10:00:00Z",
      "updatedAt": "2024-08-20T14:30:00Z"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 10,
    "total": 45,
    "totalPages": 5,
    "hasNext": true,
    "hasPrev": false
  }
}
```

---

## Conclusion

Following these design principles ensures:

✅ **Consistency** - Predictable patterns across all APIs
✅ **Scalability** - Efficient filtering, pagination, and caching
✅ **Performance** - Reduced requests via field selection and expansion
✅ **Security** - Authentication, authorization, and input validation
✅ **Developer Experience** - Clear documentation and error messages
✅ **Maintainability** - Standard conventions and versioning

For domain-specific API documentation, see the respective folders in [apis/](./README.md).
