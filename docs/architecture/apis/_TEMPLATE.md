# {Domain Name} API

> **Template Instructions**: Copy this template for new domains. Replace `{placeholders}`. Keep it minimal - just contracts, no implementation code.

## Overview

{Brief description of domain - 2-3 sentences}

**Base Path**: `/api/v1/{resource}`

**Related Wireframes**:
- `story-X.Y-name.md` - {Purpose}

**Authentication Required**: {Yes/No/Partial}

---

## Endpoints Summary

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| GET | `/api/v1/{resource}` | List/search resources | ✅ |
| GET | `/api/v1/{resource}/{id}` | Get single resource | ✅ |
| POST | `/api/v1/{resource}` | Create resource | ✅ |
| PUT | `/api/v1/{resource}/{id}` | Replace resource | ✅ |
| PATCH | `/api/v1/{resource}/{id}` | Update resource | ✅ |
| DELETE | `/api/v1/{resource}/{id}` | Delete resource | ✅ |

**Total Endpoints**: {X}

---

## 1. {Endpoint Name}

**Endpoint**: `{METHOD} /api/v1/{path}`

**Purpose**: {What this endpoint does}

**Roles**: {Who can access - ORGANIZER, SPEAKER, PARTNER, ATTENDEE, or ALL}

**Query Parameters** (if GET):
- `filter` - JSON filter syntax (see [design-principles.md](../design-principles.md#filtering))
- `sort` - Sort order (e.g., `-createdAt,+title`)
- `page` - Page number (default: 1)
- `limit` - Items per page (default: 20, max: 100)
- `fields` - Field selection (e.g., `id,title,date`)
- `include` - Related resources (e.g., `speakers,venue`)

**Request** (if POST/PUT/PATCH):
```json
{
  "field1": "value",
  "field2": 42,
  "nested": {
    "field": "value"
  }
}
```

**Validation**:
- `field1`: Required, string, min 2, max 255
- `field2`: Optional, integer, min 0
- `nested.field`: {Constraints}

**Response** (`200 OK` / `201 Created`):
```json
{
  "id": "uuid-123",
  "field1": "value",
  "field2": 42,
  "createdAt": "2024-10-04T10:30:00Z",
  "updatedAt": "2024-10-04T10:30:00Z"
}
```

**Errors**:
- `400` - Validation failed
- `401` - Authentication required
- `403` - Insufficient permissions
- `404` - Resource not found
- `409` - Duplicate/conflict
- `422` - Business rule violation
- `429` - Rate limit exceeded

**Integration**: {AWS services or external systems}

---

## 2. {Another Endpoint}

{Repeat structure above for each endpoint}

---

## Security Notes

### Rate Limiting
{Any domain-specific rate limits beyond global defaults}

### Access Control
{Domain-specific authorization rules}

### Data Sensitivity
{Any PII or sensitive data handled}

---

## Integration Details

### AWS Services
- **{Service}**: {Purpose}

### Domain Events (if applicable)
- **{EventName}**: Published when {trigger}

### Caching Strategy
- **Duration**: {X minutes}
- **Invalidation**: {When cleared}

---

## Related Documentation
- [API Design Principles](../design-principles.md)
- [Story X.Y](../../../stories/{story-file}.md)
