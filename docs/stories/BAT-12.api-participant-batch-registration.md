# BAT-12: API Contract - Participant Batch Registration

**Linear**: [BAT-12](https://linear.app/batbern/issue/BAT-12)
**Status**: Backlog
**Epic**: Epic 3 - Historical Data Migration
**Project**: [Epic 3: Historical Data Migration](https://linear.app/batbern/project/epic-3-historical-data-migration-168670d74297)
**Created**: 2025-12-25

---

## Story

**As a** system integrator (frontend and backend teams),
**I want** a clear API contract for participant batch registration,
**so that** frontend can implement batch import UI while backend implements the batch registration endpoint independently.

**API Contract Focus:** This story defines the batch registration endpoint (`POST /events/batch_registrations`) that accepts participant data with multiple event registrations and returns detailed results.

---

## Dependencies

**Blocking Dependencies:** None - this is the first story in the sequence

**Blocks:**
- ⚠️ [BAT-13](https://linear.app/batbern/issue/BAT-13) (Frontend) is BLOCKED until this story is Done
- ⚠️ [BAT-14](https://linear.app/batbern/issue/BAT-14) (Backend) is BLOCKED until this story is Done

---

## Domain Context

**Primary Domain**: Event Management Domain

**API Gateway Routes**:
- `POST /api/v1/events/batch_registrations` - Create multiple event registrations for a participant

**Service**: Event Management Service

---

## API Contract Specification

### OpenAPI Specification

**Endpoint**: `POST /api/v1/events/batch_registrations`

**Request Schema**:
```json
{
  "participantEmail": "adrian.buerki@centrisag.ch",
  "firstName": "Adrian",
  "lastName": "Bürki",
  "registrations": [
    { "eventCode": "BATbern17", "status": "attended" },
    { "eventCode": "BATbern25", "status": "attended" },
    { "eventCode": "BATbern31", "status": "attended" }
  ]
}
```

**Success Response** (200 OK):
```json
{
  "username": "adrian.buerki",
  "totalRegistrations": 3,
  "successfulRegistrations": 3,
  "failedRegistrations": [],
  "errors": []
}
```

**Partial Success Response** (200 OK):
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

**Error Responses**:
- `400 Bad Request` - Invalid request data
- `401 Unauthorized` - Missing or invalid authentication
- `403 Forbidden` - Insufficient permissions (requires ORGANIZER role)
- `500 Internal Server Error` - Server error

### Request Validation Rules

- `participantEmail`: Required, valid email format, max 255 characters
- `firstName`: Required, max 100 characters
- `lastName`: Required, max 100 characters
- `registrations`: Required, min 1 item, max 100 items
- `registrations[].eventCode`: Required, pattern `^BATbern\d+$`
- `registrations[].status`: Required, enum `["registered", "attended", "cancelled"]`

### Authentication & Authorization

- **Authentication**: JWT via AWS Cognito (required)
- **Required Role**: ORGANIZER
- **Token Validation**: Standard JWT validation via API Gateway

---

## Acceptance Criteria

1. **OpenAPI Specification Complete**
   - [ ] OpenAPI 3.0 spec created and syntactically valid
   - [ ] Request schema fully defined with validation rules
   - [ ] Response schemas defined for all scenarios
   - [ ] Error responses documented

2. **Contract Tests Defined**
   - [ ] Request validation test scenarios documented
   - [ ] Response schema validation test scenarios documented
   - [ ] Error scenario test cases documented
   - [ ] Authentication test cases documented

3. **Generated Artifacts**
   - [ ] TypeScript types generated for frontend
   - [ ] Java DTOs generated for backend
   - [ ] API documentation generated (Swagger UI)

4. **Gateway Configuration**
   - [ ] API Gateway route configured
   - [ ] Request validation enabled
   - [ ] Authentication integration configured
   - [ ] Rate limiting rules defined

---

## Tasks / Subtasks

- [ ] Task 1: OpenAPI Specification Definition
  - [ ] Define endpoint path and method
  - [ ] Define request schema with validation
  - [ ] Define response schemas (success, partial, error)
  - [ ] Validate OpenAPI spec syntax
  - [ ] Add authentication requirements

- [ ] Task 2: Write Contract Tests (RED Phase)
  - [ ] Write failing tests for request validation
  - [ ] Write failing tests for response schemas
  - [ ] Write failing tests for error scenarios
  - [ ] Write failing tests for authentication
  - [ ] Verify contract tests run and fail appropriately

- [ ] Task 3: Type Generation
  - [ ] Generate TypeScript types from OpenAPI spec
  - [ ] Generate Java DTOs from OpenAPI spec
  - [ ] Verify generated types are correct
  - [ ] Commit generated types to repository

- [ ] Task 4: Documentation Generation
  - [ ] Generate Swagger UI documentation
  - [ ] Create API usage examples
  - [ ] Document authentication flow
  - [ ] Create Bruno/Postman collection for testing

---

## Dev Notes - Implementation Guide

### OpenAPI Spec Location

**File**: `docs/api/events-api.openapi.yml`

**Section to Add**:
```yaml
paths:
  /api/v1/events/batch_registrations:
    post:
      tags:
        - Event Registrations
      summary: Create batch event registrations for a participant
      description: |
        Creates multiple event registrations for a single participant in one transaction.
        Used for bulk importing historical attendance data.

        **Behavior**:
        - Creates user if doesn't exist (get-or-create by email)
        - Skips duplicate registrations (idempotent)
        - Returns partial success if some registrations fail

        **Use Case**: Historical data migration for 2,307 participants
      operationId: createBatchRegistrations
      security:
        - CognitoAuth: []
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/BatchRegistrationRequest'
            examples:
              historicalAttendee:
                value:
                  participantEmail: "adrian.buerki@centrisag.ch"
                  firstName: "Adrian"
                  lastName: "Bürki"
                  registrations:
                    - eventCode: "BATbern17"
                      status: "attended"
                    - eventCode: "BATbern25"
                      status: "attended"
                    - eventCode: "BATbern31"
                      status: "attended"
              syntheticEmail:
                value:
                  participantEmail: "max.muster@batbern.ch"
                  firstName: "Max"
                  lastName: "Muster"
                  registrations:
                    - eventCode: "BATbern1"
                      status: "attended"
      responses:
        '200':
          description: Batch registrations processed (full or partial success)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/BatchRegistrationResponse'
        '400':
          description: Invalid request data
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
        '401':
          description: Unauthorized
        '403':
          description: Forbidden - requires ORGANIZER role
        '500':
          description: Internal server error

components:
  schemas:
    BatchRegistrationRequest:
      type: object
      required:
        - participantEmail
        - firstName
        - lastName
        - registrations
      properties:
        participantEmail:
          type: string
          format: email
          maxLength: 255
          description: Participant email (used for user lookup/creation)
        firstName:
          type: string
          maxLength: 100
          description: Participant first name
        lastName:
          type: string
          maxLength: 100
          description: Participant last name
        registrations:
          type: array
          minItems: 1
          maxItems: 100
          items:
            $ref: '#/components/schemas/BatchRegistrationItem'

    BatchRegistrationItem:
      type: object
      required:
        - eventCode
        - status
      properties:
        eventCode:
          type: string
          pattern: '^BATbern\d+$'
          description: Event code in format BATbernN where N is event number
          example: "BATbern25"
        status:
          type: string
          enum: [registered, attended, cancelled]
          description: Registration status (use 'attended' for historical data)
          example: "attended"

    BatchRegistrationResponse:
      type: object
      properties:
        username:
          type: string
          description: Username of created/found participant
          example: "adrian.buerki"
        totalRegistrations:
          type: integer
          description: Total number of registrations attempted
          example: 3
        successfulRegistrations:
          type: integer
          description: Number of registrations successfully created
          example: 3
        failedRegistrations:
          type: array
          items:
            $ref: '#/components/schemas/FailedRegistration'
        errors:
          type: array
          items:
            type: string
          description: List of error messages

    FailedRegistration:
      type: object
      properties:
        eventCode:
          type: string
          example: "BATbern99"
        reason:
          type: string
          example: "Event not found"
```

### Type Generation Commands

```bash
# Generate TypeScript types for frontend
cd web-frontend
npm run generate:api-types

# Generated files:
# - web-frontend/src/types/generated/api/BatchRegistrationRequest.ts
# - web-frontend/src/types/generated/api/BatchRegistrationResponse.ts
# - web-frontend/src/types/generated/api/BatchRegistrationItem.ts
# - web-frontend/src/types/generated/api/FailedRegistration.ts
```

### Contract Test Framework

**Tool**: OpenAPI Validator + RestAssured (backend) / MSW + OpenAPI (frontend)

**Test Strategy**:
- Backend validates implementation matches OpenAPI spec
- Frontend validates mock responses match OpenAPI spec
- Both teams can work independently against the contract

---

## Definition of Done Checklist

### Contract Complete
- [ ] OpenAPI 3.0 specification is syntactically valid
- [ ] All endpoints have complete request schemas
- [ ] All endpoints have complete response schemas
- [ ] All validation rules documented in spec
- [ ] All error scenarios defined with codes and messages
- [ ] Authentication/authorization requirements specified

### Generated Artifacts
- [ ] TypeScript types generated for frontend
- [ ] Java DTOs generated for backend
- [ ] Swagger UI documentation accessible
- [ ] Bruno/Postman collection created

### Contract Tests Ready
- [ ] Contract tests written for all scenarios
- [ ] Request schema validation tests written
- [ ] Response schema validation tests written
- [ ] Error scenario tests written
- [ ] All contract tests documented

---

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-12-25 | 0.1 | Initial API contract creation | Bob (SM) |

---

## Dev Agent Record

_This section will be populated during implementation by the dev agent._

### Agent Model Used
_To be filled by dev agent_

### Implementation Approach
_To be filled by dev agent_

### Contract Validation Results
_To be filled by dev agent_

### Generated Artifacts
_To be filled by dev agent:_
- OpenAPI spec location
- Generated TypeScript types location
- Generated Java DTOs location
- Swagger UI URL
- Bruno collection location
