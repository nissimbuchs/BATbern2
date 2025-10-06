# BATbern API Documentation

This directory contains the complete REST API documentation for the BATbern Event Management Platform.

## Overview

The BATbern API follows RESTful design principles with consistent patterns across all resources. This documentation is organized by domain for easy navigation and maintenance.

**Total APIs**: ~250 consolidated endpoints (reduced from 731 through systematic consolidation)

## Organization

APIs are organized by domain in the following folders:

| Domain | Description | File | Endpoints |
|--------|-------------|------|-----------|
| **Authentication** | Login, registration, password reset, email verification | [auth/](./auth/) | ~15 |
| **Events** | Event CRUD, workflow, publishing, registration | [events/](./events/) | ~25 |
| **Partners** | Partner analytics, meetings, voting, data management | [partners/](./partners/) | ~20 |
| **Speakers** | Speaker profiles, invitations, materials, matching | [speakers/](./speakers/) | ~18 |
| **Content** | Content search, archive, downloads, discovery | [content/](./content/) | ~15 |
| **Topics** | Topic backlog, similarity analysis, AI suggestions | [topics/](./topics/) | ~12 |
| **Companies** | Company CRUD, search, logos, verification | [companies/](./companies/) | ~10 |
| **Organizers** | Organizer dashboards, notifications, management | [organizers/](./organizers/) | ~12 |
| **Attendees** | Attendee registration, dashboard, library | [attendees/](./attendees/) | ~10 |
| **Notifications** | Email templates, escalation rules, delivery | [notifications/](./notifications/) | ~8 |
| **Admin** | System configuration, feature flags, access control | [admin/](./admin/) | ~15 |

## API Design Principles

### 1. RESTful Resource Design

All APIs follow standard REST conventions:

```
GET    /api/v1/{resource}           - List/search resources
GET    /api/v1/{resource}/{id}      - Get single resource
POST   /api/v1/{resource}           - Create new resource
PUT    /api/v1/{resource}/{id}      - Replace resource (full update)
PATCH  /api/v1/{resource}/{id}      - Update resource (partial update)
DELETE /api/v1/{resource}/{id}      - Delete resource
```

### 2. Query Parameters

Standard query parameters are supported across all GET endpoints:

**Filtering**:
```
GET /api/v1/events?filter={"status":"published","year":2024}
```

**Sorting**:
```
GET /api/v1/events?sort=-createdAt,+title
```

**Pagination**:
```
GET /api/v1/events?page=2&limit=50
```

**Field Selection** (reduce payload):
```
GET /api/v1/events?fields=id,title,date
```

**Resource Expansion** (include related data):
```
GET /api/v1/events/{id}?include=venue,speakers,sessions
```

See [design-principles.md](./design-principles.md) for complete details.

### 3. Authentication

All endpoints (except public landing pages) require authentication via JWT Bearer token:

```http
Authorization: Bearer <jwt-token>
```

See [auth/authentication.md](./auth/authentication.md) for details.

### 4. Error Responses

All errors follow a consistent format:

```json
{
  "timestamp": "2025-10-04T10:30:00Z",
  "path": "/api/v1/events/123",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "correlationId": "abc-123-def-456",
  "details": {
    "validationErrors": {
      "title": "Title is required",
      "date": "Date must be in the future"
    }
  }
}
```

See [design-principles.md](./design-principles.md) for complete error handling documentation.

### 5. Versioning

All APIs are versioned in the URL path:

```
/api/v1/{resource}  - Current version
/api/v2/{resource}  - Future version (when breaking changes needed)
```

Deprecated API versions are maintained for 12 months with deprecation warnings in response headers.

## Quick Start

### 1. Authentication

First, obtain a JWT token via login:

```bash
POST /api/v1/auth/login
{
  "email": "user@example.com",
  "password": "password123"
}

Response:
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "...",
  "expiresIn": 3600,
  "user": { ... }
}
```

### 2. Make API Requests

Use the access token in all subsequent requests:

```bash
GET /api/v1/events?filter={"status":"published"}&page=1&limit=20
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 3. Handle Errors

Check HTTP status codes and parse error responses:

```javascript
if (response.status === 400) {
  const error = await response.json();
  console.error('Validation errors:', error.details.validationErrors);
}
```

## API Consolidation

This API design represents a consolidation from 731 individual endpoints to ~250 reusable, well-designed APIs. Key consolidation techniques:

1. **Field Selection**: `?fields=` replaces specialized GET endpoints
2. **Resource Expansion**: `?include=` replaces sub-resource GET endpoints
3. **Rich Filtering**: JSON filter syntax replaces specialized search endpoints
4. **Standard CRUD**: Consistent patterns across all resources
5. **Bulk Operations**: Array-based PATCH for batch updates

See [../api-consolidation-analysis.md](../api-consolidation-analysis.md) for complete consolidation details.

## Migration Guide

If you're migrating from the old fragmented API design:

1. **Frontend Changes**: Use `?include=` parameter instead of multiple API calls
2. **Filter Syntax**: Migrate from query string params to JSON filter syntax
3. **Bulk Operations**: Batch updates using PATCH with arrays
4. **Field Selection**: Reduce payload size using `?fields=` parameter

See each domain's README for specific migration guidance.

## Tools & Testing

### OpenAPI/Swagger

Complete OpenAPI 3.1 specification: [openapi.yaml](./openapi.yaml) (coming soon)

### Postman Collection

Import the Postman collection for easy API testing: [batbern-api.postman_collection.json](./batbern-api.postman_collection.json) (coming soon)

### Mock Server

Run a local mock server for development:
```bash
npm run api:mock
```

## Contributing

When adding new API endpoints:

1. Follow the design principles in [design-principles.md](./design-principles.md)
2. Add documentation to the appropriate domain folder
3. Update this README with endpoint counts
4. Add examples to the domain's README
5. Consider consolidation opportunities before adding new endpoints

## Support

For API questions or issues:
- Check the domain-specific documentation
- Review [design-principles.md](./design-principles.md)
- See example implementations in each domain folder
- Open an issue in the project repository

## License

Copyright © 2024 BATbern. All rights reserved.
