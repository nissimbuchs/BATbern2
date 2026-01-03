# Batch Registration API Usage Guide

**Story**: BAT-12 - API Contract - Participant Batch Registration
**API Version**: v1
**Last Updated**: 2025-12-25

---

## Overview

The Batch Registration API allows organizers to import historical participant attendance data in bulk. This endpoint creates multiple event registrations for a single participant in one transaction.

**Endpoint**: `POST /api/v1/events/batch_registrations`

**Use Case**: Historical data migration for 2,307 participants

---

## Authentication

**Required**: JWT Bearer token (AWS Cognito)
**Required Role**: ORGANIZER

```bash
# Get auth token (see scripts/auth/get-token.sh)
export AUTH_TOKEN="your-jwt-token"
```

---

## Request Format

### Request Body

```json
{
  "participantEmail": "adrian.buerki@centrisag.ch",
  "firstName": "Adrian",
  "lastName": "Bürki",
  "registrations": [
    {
      "eventCode": "BATbern17",
      "status": "attended"
    },
    {
      "eventCode": "BATbern25",
      "status": "attended"
    },
    {
      "eventCode": "BATbern31",
      "status": "attended"
    }
  ]
}
```

### Field Validation

| Field | Type | Required | Validation | Example |
|-------|------|----------|------------|---------|
| `participantEmail` | string | Yes | Valid email, max 255 chars | `adrian.buerki@centrisag.ch` |
| `firstName` | string | Yes | Max 100 chars | `Adrian` |
| `lastName` | string | Yes | Max 100 chars | `Bürki` |
| `registrations` | array | Yes | Min 1, max 100 items | See below |
| `registrations[].eventCode` | string | Yes | Pattern: `^BATbern\d+$` | `BATbern25` |
| `registrations[].status` | string | Yes | Enum: `registered`, `attended`, `cancelled` | `attended` |

---

## Response Formats

### Success Response (200 OK)

**Full Success** (all registrations created):
```json
{
  "username": "adrian.buerki",
  "totalRegistrations": 3,
  "successfulRegistrations": 3,
  "failedRegistrations": [],
  "errors": []
}
```

**Partial Success** (some registrations failed):
```json
{
  "username": "adrian.buerki",
  "totalRegistrations": 3,
  "successfulRegistrations": 2,
  "failedRegistrations": [
    {
      "eventCode": "BATbern99",
      "reason": "Event not found"
    }
  ],
  "errors": ["Event BATbern99 not found"]
}
```

### Error Responses

#### 400 Bad Request - Invalid Email

```json
{
  "error": "Validation failed",
  "message": "Invalid email format",
  "code": "VALIDATION_ERROR",
  "details": [
    {
      "field": "participantEmail",
      "message": "must be a well-formed email address"
    }
  ]
}
```

#### 400 Bad Request - Empty Registrations

```json
{
  "error": "Validation failed",
  "message": "Registrations array cannot be empty",
  "code": "VALIDATION_ERROR",
  "details": [
    {
      "field": "registrations",
      "message": "must contain at least 1 item"
    }
  ]
}
```

#### 400 Bad Request - Too Many Registrations

```json
{
  "error": "Validation failed",
  "message": "Too many registrations in batch",
  "code": "VALIDATION_ERROR",
  "details": [
    {
      "field": "registrations",
      "message": "must contain at most 100 items"
    }
  ]
}
```

#### 401 Unauthorized

```json
{
  "error": "UNAUTHORIZED",
  "errorCode": "UNAUTHORIZED",
  "message": "Authentication token is missing or invalid",
  "timestamp": "2025-01-15T10:30:00Z"
}
```

#### 403 Forbidden

```json
{
  "error": "Forbidden",
  "message": "This operation requires ORGANIZER role",
  "code": "INSUFFICIENT_PERMISSIONS"
}
```

#### 500 Internal Server Error

```json
{
  "error": "INTERNAL_SERVER_ERROR",
  "errorCode": "INTERNAL_ERROR",
  "message": "An unexpected error occurred",
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

## Example Usage

### cURL Example

```bash
curl -X POST https://api.batbern.ch/api/v1/events/batch_registrations \
  -H "Authorization: Bearer ${AUTH_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "participantEmail": "adrian.buerki@centrisag.ch",
    "firstName": "Adrian",
    "lastName": "Bürki",
    "registrations": [
      {
        "eventCode": "BATbern17",
        "status": "attended"
      },
      {
        "eventCode": "BATbern25",
        "status": "attended"
      },
      {
        "eventCode": "BATbern31",
        "status": "attended"
      }
    ]
  }'
```

### TypeScript Example (Frontend)

```typescript
import { api } from '@/services/api';
import type {
  BatchRegistrationRequest,
  BatchRegistrationResponse
} from '@/types/generated/events-api.types';

async function importParticipantAttendance(
  email: string,
  firstName: string,
  lastName: string,
  eventCodes: string[]
): Promise<BatchRegistrationResponse> {
  const request: BatchRegistrationRequest = {
    participantEmail: email,
    firstName: firstName,
    lastName: lastName,
    registrations: eventCodes.map(code => ({
      eventCode: code,
      status: 'attended'
    }))
  };

  const response = await api.post<BatchRegistrationResponse>(
    '/events/batch_registrations',
    request
  );

  return response.data;
}

// Usage
const result = await importParticipantAttendance(
  'adrian.buerki@centrisag.ch',
  'Adrian',
  'Bürki',
  ['BATbern17', 'BATbern25', 'BATbern31']
);

console.log(`Created ${result.successfulRegistrations} of ${result.totalRegistrations} registrations`);
if (result.failedRegistrations.length > 0) {
  console.warn('Some registrations failed:', result.errors);
}
```

### Java Example (Backend)

```java
import ch.batbern.events.dto.generated.BatchRegistrationRequest;
import ch.batbern.events.dto.generated.BatchRegistrationResponse;
import ch.batbern.events.dto.generated.BatchRegistrationItem;

public class BatchRegistrationService {

    public BatchRegistrationResponse importParticipantAttendance(
            String email,
            String firstName,
            String lastName,
            List<String> eventCodes) {

        var request = new BatchRegistrationRequest()
            .participantEmail(email)
            .firstName(firstName)
            .lastName(lastName)
            .registrations(
                eventCodes.stream()
                    .map(code -> new BatchRegistrationItem()
                        .eventCode(code)
                        .status(BatchRegistrationItem.StatusEnum.ATTENDED))
                    .toList()
            );

        // Call endpoint via RestTemplate or WebClient
        return restTemplate.postForObject(
            "/events/batch_registrations",
            request,
            BatchRegistrationResponse.class
        );
    }
}
```

---

## API Behavior

### User Creation (Get-or-Create)

- If a user with the given email doesn't exist, the API will create a new user
- Username is generated from email (e.g., `adrian.buerki@centrisag.ch` → `adrian.buerki`)
- If username collision occurs, a suffix is added (e.g., `adrian.buerki.2`)

### Idempotency

- Duplicate registrations are skipped automatically
- Calling the endpoint multiple times with the same data is safe
- Already registered users will not be re-registered for the same event

### Partial Success Handling

- The API uses "partial success" pattern
- Some registrations may succeed while others fail
- HTTP 200 is returned even if some registrations fail
- Check `successfulRegistrations` vs `totalRegistrations` to detect partial failures
- `failedRegistrations` array contains details about which registrations failed and why

---

## Testing

### Bruno API Tests

Contract tests are available in `bruno-tests/events-api/`:

- `50-batch-registration-success.bru` - Full success scenario
- `51-batch-registration-partial-success.bru` - Partial success with some failures
- `52-batch-registration-invalid-email.bru` - Invalid email validation
- `53-batch-registration-empty-array.bru` - Empty registrations array validation
- `54-batch-registration-unauthorized.bru` - Authentication requirement

Run tests:
```bash
cd bruno-tests
bruno run events-api/50-batch-registration-success.bru
```

---

## Performance

**Target**: <500ms (P95) for 100 registrations

**Optimization Tips**:
- Batch size limit: 100 registrations per request (prevents timeout)
- For large imports (2,307 participants), process in batches
- Use concurrent requests for faster processing (max 5 concurrent requests)

---

## Related Documentation

- [OpenAPI Specification](../docs/api/events-api.openapi.yml) - Complete API contract
- [Swagger UI](http://localhost:8080/swagger-ui.html) - Interactive API documentation
- [Story BAT-12](../docs/stories/BAT-12.api-participant-batch-registration.md) - Story details
- [Story BAT-13](../docs/stories/BAT-13.frontend-participant-batch-import.md) - Frontend implementation
- [Story BAT-14](../docs/stories/BAT-14.backend-participant-batch-registration.md) - Backend implementation

---

## Support

For questions or issues:
- Check [Troubleshooting Guide](../docs/guides/troubleshooting.md)
- Review [API Design Standards](../docs/architecture/api-design.md)
- Contact: platform@batbern.ch
