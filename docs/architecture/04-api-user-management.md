# User Management API

**Last Updated**: 2025-11-02
**ADR References**:
- [ADR-002: Generic File Upload Service](./ADR-002-generic-file-upload-service.md)
- [ADR-003: Meaningful Identifiers in Public APIs](./ADR-003-meaningful-identifiers-public-apis.md)
- [ADR-004: Factor User Fields from Domain Entities](./ADR-004-factor-user-fields-from-domain-entities.md)

**Important**: Profile pictures use the Generic File Upload Service (ADR-002). User entity is the single source of truth for email, name, bio, and photo (ADR-004).

This document outlines the User Management Service API, which handles user profiles, preferences, settings, role management, and comprehensive GDPR compliance capabilities.

## Overview

The User Management API provides endpoints for:
- User profile CRUD operations with full lifecycle management
- Advanced query patterns (filter, sort, pagination, resource expansion)
- User search with autocomplete and Caffeine caching
- User preferences and settings management
- Role management with business rules enforcement
- Activity history tracking and analytics
- Profile picture management via Generic File Upload Service (ADR-002)
- GDPR compliance (data export, cascade deletion, audit logging)
- Get-or-create pattern for domain service integration

**Architecture Context**: User entity owns the user-company relationship via `User.companyId` field. This is a one-way dependency from User Service → Company Service. Partnership and employee status managed by respective domain services.

## API Endpoints

### User Profile Management

#### Get Current User

```yaml
GET /api/v1/users/me
tags: [User Management]
summary: Get current authenticated user
operationId: getCurrentUser
description: |
  Retrieve the profile of the currently authenticated user.

  **Resource Expansion**:
  - ?include=company: Expands company details
  - ?include=settings: Includes the user's settings (display, notifications, privacy; Story 4.9)
  - ?include=roles: Includes role information
  - ?include=activity: Includes recent activity history

  **Performance**: <100ms (P95)

security:
  - BearerAuth: []
parameters:
  - name: include
    in: query
    description: Comma-separated list of resources to include
    required: false
    schema:
      type: string
      example: 'company,preferences,settings'
responses:
  '200':
    description: User profile retrieved successfully
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/UserResponse'
  '401':
    description: Unauthorized
  '500':
    description: Internal server error
```

#### Update a User's Profile

```yaml
PATCH /api/v1/users/{username}
tags: [User Management]
summary: Partially update a user's profile
operationId: patchUser
description: |
  Story 4.10: one endpoint for self-service and organizers (replaces `PATCH /users/me` and
  `PUT /users/{username}`; ADR-013 §1, PATCH is the default mutation). Only the fields present
  change.

  **Authorization**: the user themself (`@userOwnership.isCurrentUser(#username)`: the JWT's
  `custom:username`, or, without that claim, the user whose `cognito_user_id` is the token
  subject) or ORGANIZER/ADMIN for any user; anyone else 403.

  **Consent**: `termsAccepted` (Story 12.11, write-once) is accepted only on the caller's own
  profile; for another user it is 403.

  **Database Only**: stored in PostgreSQL only (ADR-001, no Cognito sync)

  **Events Published**: UserUpdatedEvent, actor = the caller's username (the user themself or
  the organizer)

  **Cache Invalidation**: user search caches cleared on update

security:
  - BearerAuth: []
requestBody:
  required: true
  content:
    application/json:
      schema:
        $ref: '#/components/schemas/UpdateUserRequest'
responses:
  '200': UserResponse
  '400': validation error
  '403': not the user and not an organizer/admin, or consent for another user
  '404': user not found
  '409': EMAIL_DUPLICATE, the new email is already another user's address (user_profiles_email_key / uq_user_profiles_email_lower)
```

`GET /api/v1/users/me` stays the self read (the login bootstrap): the frontend takes the
username for the write from it, never from the token.

#### List Users (Admin/Organizer)

```yaml
GET /api/v1/users
tags: [User Management]
summary: List users with advanced query support
operationId: listUsers
description: |
  Retrieve a paginated list of users with optional filtering, sorting and resource expansion.

  **Authorization**: ORGANIZER role required

  **Filter Syntax Examples**:
  - Single filter: {"role":"SPEAKER"}
  - Multiple fields: {"role":"SPEAKER","isActive":true}
  - Company filter: {"companyName":"GoogleZH"}
  - Logical operators: {"$or":[{"role":"SPEAKER"},{"role":"PARTNER"}]}

  **Sort Syntax**:
  - Ascending: lastName or +lastName
  - Descending: -createdAt
  - Multiple fields: lastName,-createdAt

  **Resource Expansion**:
  - ?include=company: Expands company details
  - ?include=roles: Includes full role information
  - ?include=settings: Includes the user's settings

  **Performance**:
  - Basic query: <100ms (P95)
  - With all includes: <150ms (P95)

security:
  - BearerAuth: []
parameters:
  - name: filter
    in: query
    description: MongoDB-style JSON filter criteria
    required: false
    schema:
      type: string
      example: '{"role":"SPEAKER","isActive":true}'
  - name: sort
    in: query
    description: Sort fields (comma-separated, prefix with - for descending)
    required: false
    schema:
      type: string
      example: '-createdAt,lastName'
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
  - name: include
    in: query
    description: Comma-separated list of resources to include
    required: false
    schema:
      type: string
      example: 'company,roles,preferences'
  - name: role
    in: query
    description: Filter by specific role (deprecated, use filter parameter)
    required: false
    schema:
      type: string
      enum: [ORGANIZER, SPEAKER, PARTNER, ATTENDEE]
  - name: company
    in: query
    description: Filter by company ID (deprecated, use filter parameter)
    required: false
    schema:
      type: string

      description: Meaningful identifier (see ADR-003)
responses:
  '200':
    description: Successful response with paginated users
    headers:
      X-Cache-Status:
        description: Cache hit status (HIT or MISS)
        schema:
          type: string
          enum: [HIT, MISS]
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/PaginatedUserResponse'
  '400':
    description: Bad request - invalid filter syntax or parameters
  '401':
    description: Unauthorized
  '403':
    description: Forbidden - requires ORGANIZER role
  '500':
    description: Internal server error
```

#### Get User by ID

```yaml
GET /api/v1/users/{id}
tags: [User Management]
summary: Get user details
operationId: getUserById
description: |
  Retrieve detailed information about a specific user.

  **Resource Expansion**: Supports ?include parameter

  **Performance**: <150ms (P95)

security:
  - BearerAuth: []
parameters:
  - name: id
    in: path
    description: User UUID
    required: true
    schema:
      type: string

      description: Meaningful identifier (see ADR-003)
  - name: include
    in: query
    description: Comma-separated list of resources to include
    required: false
    schema:
      type: string
      example: 'company,roles'
responses:
  '200':
    description: User details retrieved successfully
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/UserResponse'
  '401':
    description: Unauthorized
  '404':
    description: User not found
  '500':
    description: Internal server error
```

### User Search

#### Search Users with Autocomplete

```yaml
GET /api/v1/users/search
tags: [User Search]
summary: Search users with autocomplete
operationId: searchUsers
description: |
  Search users by name or email with autocomplete functionality.

  **Caching**:
  - Caffeine in-memory cache with 10-minute TTL
  - Cache key includes query, role filter, and limit
  - Automatic cache invalidation on user updates

  **Performance**:
  - Cached response: <50ms (P95)
  - Cache miss: <100ms (P95)

  **Default Results**: 20 users (configurable via limit parameter)

security:
  - BearerAuth: []
parameters:
  - name: query
    in: query
    description: Search query (minimum 1 character)
    required: true
    schema:
      type: string
      minLength: 1
      example: 'John'
  - name: role
    in: query
    description: Filter by specific role
    required: false
    schema:
      type: string
      enum: [ORGANIZER, SPEAKER, PARTNER, ATTENDEE]
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
            $ref: '#/components/schemas/UserSearchResponse'
  '400':
    description: Bad request - query too short
  '401':
    description: Unauthorized
  '500':
    description: Internal server error
```

### Settings (one resource since Story 4.9)

Display (theme, language, timezone), notifications (channels, frequency, Q&A digest, quiet
hours) and privacy are one resource. Story 4.9 merged the former `/users/me/preferences` into it
and replaced the full-replace PUT with a partial PATCH (ADR-013). The storage is unchanged: the
`UserPreferences` and `UserSettings` embeddables on `user_profiles`. The timezone is stored with
the other settings; before, the frontend sent it to `/users/me/preferences`, which dropped it.
Other services read the settings with `?include=settings` on `/internal/users/{username}`.

#### Get User Settings

```yaml
GET /api/v1/users/me/settings
tags: [User Settings]
operationId: getUserSettings
security:
  - BearerAuth: []
responses:
  '200':
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/UserSettings'   # all fields of both groups
  '401':
    description: Unauthorized
```

#### Change User Settings

```yaml
PATCH /api/v1/users/me/settings
tags: [User Settings]
operationId: updateUserSettings
description: |
  Partial update: only the fields in the body change. Returns the whole settings object.
  An unknown enum value, a malformed quiet hour or a timezone that is not an IANA zone id
  answers 400.
security:
  - BearerAuth: []
requestBody:
  required: true
  content:
    application/json:
      schema:
        $ref: '#/components/schemas/UpdateUserSettingsRequest'   # all optional, no defaults
responses:
  '200':
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/UserSettings'
  '400':
    description: Bad request - validation error
  '401':
    description: Unauthorized
```

### Role Management

#### Get User Roles

```yaml
GET /api/v1/users/{id}/roles
tags: [Role Management]
summary: Get user roles
operationId: getUserRoles
description: |
  Retrieve roles for a specific user.

  **Authorization**: ORGANIZER role required

security:
  - BearerAuth: []
parameters:
  - name: id
    in: path
    required: true
    schema:
      type: string

      description: Meaningful identifier (see ADR-003)
responses:
  '200':
    description: Roles retrieved successfully
    content:
      application/json:
        schema:
          type: object
          properties:
            username:
              type: string

              description: Meaningful identifier (see ADR-003)
            roles:
              type: array
              items:
                type: string
                enum: [ORGANIZER, SPEAKER, PARTNER, ATTENDEE]
  '401':
    description: Unauthorized
  '403':
    description: Forbidden - requires ORGANIZER role
  '404':
    description: User not found
  '500':
    description: Internal server error
```

#### Update User Roles

```yaml
PUT /api/v1/users/{id}/roles
tags: [Role Management]
summary: Update user roles
operationId: updateUserRoles
description: |
  Update roles for a specific user.

  **Authorization**: ORGANIZER role required

  **Business Rules**:
  - System must maintain at least 2 active ORGANIZER role users
  - Removing ORGANIZER role from user requires approval if only 2 organizers remain

  **Events Published**: UserRoleChangedEvent (in-process domain event)

  **Cache Invalidation**: All user caches cleared on role update

security:
  - BearerAuth: []
parameters:
  - name: id
    in: path
    required: true
    schema:
      type: string

      description: Meaningful identifier (see ADR-003)
requestBody:
  required: true
  content:
    application/json:
      schema:
        type: object
        required:
          - roles
        properties:
          roles:
            type: array
            items:
              type: string
              enum: [ORGANIZER, SPEAKER, PARTNER, ATTENDEE]
            minItems: 1
responses:
  '200':
    description: Roles updated successfully
    content:
      application/json:
        schema:
          type: object
          properties:
            username:
              type: string

              description: Meaningful identifier (see ADR-003)
            roles:
              type: array
              items:
                type: string
  '400':
    description: Bad request - validation error or business rule violation
  '401':
    description: Unauthorized
  '403':
    description: Forbidden - requires ORGANIZER role
  '404':
    description: User not found
  '409':
    description: Conflict - would violate minimum organizers rule
  '500':
    description: Internal server error
```

### Activity History

#### Get User Activity History

```yaml
GET /api/v1/users/{id}/activity
tags: [Activity History]
summary: Get user activity history
operationId: getUserActivity
description: |
  Retrieve paginated activity history for a specific user.

  **Activity Types**: event_registered, session_attended, topic_voted, content_viewed, etc.

  **Timeframe Filtering**: Support for date range queries

security:
  - BearerAuth: []
parameters:
  - name: id
    in: path
    required: true
    schema:
      type: string

      description: Meaningful identifier (see ADR-003)
  - name: timeframe
    in: query
    description: Filter by timeframe
    required: false
    schema:
      type: string
      enum: [24h, 7d, 30d, 90d, all]
      default: 30d
  - name: activityType
    in: query
    description: Filter by activity type
    required: false
    schema:
      type: string
  - name: page
    in: query
    required: false
    schema:
      type: integer
      minimum: 1
      default: 1
  - name: limit
    in: query
    required: false
    schema:
      type: integer
      minimum: 1
      maximum: 100
      default: 20
responses:
  '200':
    description: Activity history retrieved successfully
    content:
      application/json:
        schema:
          type: object
          properties:
            data:
              type: array
              items:
                $ref: '#/components/schemas/ActivityHistory'
            pagination:
              $ref: '#/components/schemas/PaginationMetadata'
  '401':
    description: Unauthorized
  '403':
    description: Forbidden - can only view own activity unless ORGANIZER
  '404':
    description: User not found
  '500':
    description: Internal server error
```

### Profile Picture Management

**Uploads (ADR-002, Story 17.11).** Files go through one contract, `uploads-api`:
`POST /api/v1/uploads/{purpose}` (presigned PUT URL), `PUT {uploadUrl}` straight to S3, and
`POST /api/v1/uploads/{purpose}/{uploadId}/confirm` (with a `target` it attaches the file). The
company logo is purpose `company-logo`. Profile pictures still use their own operations below until
Story 17.11a moves them to purpose `profile-picture`. The former `/api/v1/logos/*` operations are
gone (404).

**Profile picture (Story 4.10).** One set for the user themself and for organizers:
`POST /api/v1/users/{username}/picture/presigned-url` → PUT to S3 →
`POST /api/v1/users/{username}/picture/confirm` (associates the picture), and
`DELETE /api/v1/users/{username}/picture`. Authorization: the user themself
(`@userOwnership.isCurrentUser(#username)`) or ORGANIZER. The `/users/me/picture*` twins are
gone; `/users/me/...` now matches the `{username}` templates with the username `me`, which no
user has (`chk_username_format`): 403 for a non-organizer, 400 for an organizer, nothing
changes. The full contract is `docs/api/users-api.openapi.yml`.

### GDPR Compliance

#### Delete User (GDPR)

```yaml
DELETE /api/v1/users/{id}
tags: [GDPR Compliance]
summary: Delete user and all associated data
operationId: deleteUser
description: |
  Delete a user and cascade deletion across all domain services.

  **Authorization**: ORGANIZER role or self-deletion

  **Business Rules**:
  - Cannot delete last ORGANIZER user
  - Cascade deletion across Event, Speaker, Partner, Attendee services
  - Audit logging for GDPR compliance

  **Events Published**: UserDeletedEvent (in-process domain event)

  **Deletion Scope**:
  - User profile and authentication
  - Activity history
  - Preferences and settings
  - Profile picture (S3)
  - Associated domain data (events, sessions, registrations)

security:
  - BearerAuth: []
parameters:
  - name: id
    in: path
    required: true
    schema:
      type: string

      description: Meaningful identifier (see ADR-003)
responses:
  '204':
    description: User deleted successfully
  '401':
    description: Unauthorized
  '403':
    description: Forbidden - cannot delete last organizer or insufficient permissions
  '404':
    description: User not found
  '500':
    description: Internal server error
```

### Domain Service Integration

#### Get or Create User

```yaml
POST /api/v1/users/get-or-create
tags: [Domain Integration]
summary: Get or create user (for domain services)
operationId: getOrCreateUser
description: |
  Idempotent endpoint for domain services to get existing user or create new user.

  **Use Cases**:
  - Speaker Service: Create user account when speaker registers
  - Partner Service: Create user account for partner contact
  - Attendee Service: Create user account during event registration

  **Idempotency**: Safe to call multiple times with same email

  **Database Only**: Creates user_profiles record only (per ADR-001, users register in Cognito separately via Story 1.2.3)

  **Performance**: <200ms (P95)

security:
  - BearerAuth: []
requestBody:
  required: true
  content:
    application/json:
      schema:
        $ref: '#/components/schemas/GetOrCreateUserRequest'
responses:
  '200':
    description: User retrieved or created
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/GetOrCreateUserResponse'
  '400':
    description: Bad request - validation error
  '401':
    description: Unauthorized
  '500':
    description: Internal server error
```

## Schemas

### UserResponse

```yaml
UserResponse:
  type: object
  required:
    - id
    - email
    - firstName
    - lastName
    - roles
    - isActive
    - createdAt
    - updatedAt
  properties:
    id:
      type: string

      description: Meaningful identifier (see ADR-003)
      example: john.doe
    cognitoUserId:
      type: string
      example: cognito-user-123
    email:
      type: string
      format: email
      example: john.doe@example.com
    firstName:
      type: string
      example: John
    lastName:
      type: string
      example: Doe
    bio:
      type: string
      example: Software engineer passionate about cloud architecture
    companyName:
      type: string

      description: Meaningful identifier (see ADR-003)
      example: GoogleZH
    roles:
      type: array
      items:
        type: string
        enum: [ORGANIZER, SPEAKER, PARTNER, ATTENDEE]
    profilePictureUrl:
      type: string
      format: uri
      example: https://cdn.batbern.ch/profile-pictures/550e8400.jpg
    isActive:
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
    lastLoginAt:
      type: string
      format: date-time
      example: 2025-01-20T14:30:00Z
    company:
      $ref: '#/components/schemas/Company'
    preferences:
      $ref: '#/components/schemas/UserPreferences'
    settings:
      $ref: '#/components/schemas/UserSettings'
```

### UserPreferences

```yaml
UserPreferences:
  type: object
  properties:
    theme:
      type: string
      enum: [light, dark, auto]
      default: auto
    language:
      type: string
      enum: [de, en, fr, it]
      default: de
    emailNotifications:
      type: boolean
      default: true
    inAppNotifications:
      type: boolean
      default: true
    pushNotifications:
      type: boolean
      default: false
    notificationFrequency:
      type: string
      enum: [immediate, daily_digest, weekly_digest]
      default: immediate
    quietHoursStart:
      type: string
      pattern: '^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$'
      example: '22:00'
    quietHoursEnd:
      type: string
      pattern: '^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$'
      example: '07:00'
```

### UserSettings

```yaml
UserSettings:
  type: object
  properties:
    profileVisibility:
      type: string
      enum: [public, members_only, private]
      default: members_only
    showEmail:
      type: boolean
      default: false
    showCompany:
      type: boolean
      default: true
    showActivityHistory:
      type: boolean
      default: false
    allowMessaging:
      type: boolean
      default: true
    allowCalendarSync:
      type: boolean
      default: false
    timezone:
      type: string
      example: Europe/Zurich
    twoFactorEnabled:
      type: boolean
      default: false
```

### UpdateUserRequest

```yaml
UpdateUserRequest:
  type: object
  properties:
    firstName:
      type: string
      minLength: 2
      maxLength: 100
    lastName:
      type: string
      minLength: 2
      maxLength: 100
    email:
      type: string
      format: email
    bio:
      type: string
      maxLength: 2000
```

### UserSearchResponse

```yaml
UserSearchResponse:
  type: object
  required:
    - id
    - email
    - firstName
    - lastName
  properties:
    id:
      type: string

      description: Meaningful identifier (see ADR-003)
    email:
      type: string
    firstName:
      type: string
    lastName:
      type: string
    companyName:
      type: string

      description: Meaningful identifier (see ADR-003)
    roles:
      type: array
      items:
        type: string
    profilePictureUrl:
      type: string
      format: uri
```

### GetOrCreateUserRequest

```yaml
GetOrCreateUserRequest:
  type: object
  required:
    - email
    - firstName
    - lastName
  properties:
    email:
      type: string
      format: email
    firstName:
      type: string
    lastName:
      type: string
    companyName:
      type: string

      description: Meaningful identifier (see ADR-003)
    createIfMissing:
      type: boolean
      default: true
    cognitoSync:
      type: boolean
      default: true
```

### GetOrCreateUserResponse

```yaml
GetOrCreateUserResponse:
  type: object
  required:
    - userId
    - created
    - user
  properties:
    username:
      type: string

      description: Meaningful identifier (see ADR-003)
    created:
      type: boolean
      description: True if user was created, false if existing
    cognitoUserId:
      type: string
      description: Cognito user ID (only if created)
    user:
      $ref: '#/components/schemas/UserResponse'
```

### ActivityHistory

```yaml
ActivityHistory:
  type: object
  properties:
    id:
      type: string

      description: Meaningful identifier (see ADR-003)
    username:
      type: string

      description: Meaningful identifier (see ADR-003)
    activityType:
      type: string
      example: event_registered
    entityType:
      type: string
      example: event
    entityId:
      type: string
      example: evt-123
    description:
      type: string
      example: Registered for BATbern Developer Meetup
    metadata:
      type: object
      additionalProperties: true
    timestamp:
      type: string
      format: date-time
```

### PaginatedUserResponse

```yaml
PaginatedUserResponse:
  type: object
  required:
    - data
    - pagination
  properties:
    data:
      type: array
      items:
        $ref: '#/components/schemas/UserResponse'
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
      example: 150
    totalPages:
      type: integer
      description: Total number of pages
      example: 8
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
- User search cache TTL: 10 minutes (expireAfterWrite)
- Max cache size: 1000 entries
- Eviction policy: LRU (Least Recently Used)
- Cache key format: `user_search:{query}:{role}:{limit}`
- Cache invalidation: Automatic on user create/update/delete operations

**Performance Benefits**:
- Cached search response: <50ms (P95)
- Cache miss response: <100ms (P95)
- Reduces database load for frequently searched users
- X-Cache-Status header indicates HIT or MISS

### Domain Events

The service raises these domain events in process (`DomainEventPublisher`, shared kernel). No
other service consumes them and there is no message bus (Story 17.2 part B, 2026-11):

- `UserCreatedEvent`: When new user is created
- `UserUpdatedEvent`: When user data is modified
- `UserRoleChangedEvent`: When user roles are updated
- `UserDeletedEvent`: When user is removed (GDPR)

### Advanced Query Patterns

**Filter Syntax** (MongoDB-style JSON):
```json
{
  "role": "SPEAKER",
  "isActive": true,
  "companyName": "GoogleZH"
}
```

**Supported Operators**:
- `$eq`, `$ne`: Equality/inequality
- `$gt`, `$gte`, `$lt`, `$lte`: Comparison (for dates, numeric fields)
- `$in`, `$nin`: Membership
- `$contains`: Substring search (case-insensitive, for name/email)
- `$and`, `$or`, `$not`: Logical operators

**Sort Syntax**:
- Ascending: `lastName` or `+lastName`
- Descending: `-createdAt`
- Multiple fields: `lastName,-createdAt`

**Pagination**:
- 1-indexed pages (page=1 is first page)
- Default limit: 20 items per page
- Maximum limit: 100 items per page
- Response includes: `totalItems`, `totalPages`, `hasNext`, `hasPrev`

**No field selection**: the list has no `fields` parameter (removed 2026-11-09, no caller used it;
ADR-013 §3 offers `fields` only when a caller needs it). An unknown query parameter is ignored.

**Resource Expansion**:
- `?include=company`: Expands company details
- `?include=roles`: Includes full role information
- `?include=settings`: Includes the user's settings (display, notifications, privacy)
- `?include=activity`: Includes recent activity history
- Multiple expansions: `?include=company,roles,settings`
- Performance target: <150ms (P95) with all expansions

### Security & Authorization

**Authentication**:
- JWT-based authentication via AWS Cognito
- Bearer token required for all endpoints
- Token claims: `sub` (Cognito user ID), `email`, `custom:role` (roles from database)

**Authorization Levels**:
- Self (the user themself, or ORGANIZER/ADMIN): GET /users/me (self only), PATCH /users/{username}, /users/{username}/picture* (pictures: self or ORGANIZER), /users/me/settings (self only)
- ORGANIZER: GET /users (list all), GET /users/{id}, role management, DELETE /users (GDPR deletion)
- Self-service: Users can delete their own account

**Role-Based Access Control**:
- Implemented with Spring Security `@PreAuthorize` annotations
- Role extraction from JWT `custom:role` claim (populated by PreTokenGeneration Lambda from database)
- Method-level security enforcement

**Business Rules**:
- Minimum 2 active ORGANIZER users enforced at service layer
- Role changes require approval for critical roles
- GDPR deletion prevents deletion of last organizer

### Cognito Integration

Per **[ADR-001](./ADR-001-invitation-based-user-registration.md)**, Cognito integration is **unidirectional**: Cognito → Database only.

**Database-Only Operations**:
- User creation → Creates user_profiles record only (users register in Cognito via Story 1.2.3)
- User update → Updates database only (NO Cognito sync)
- Role change → Updates role_assignments table only (roles fetched by PreTokenGeneration Lambda on next login)
- User deletion → Soft delete in database only (is_active = false)

**Cognito Operations** (automatic via Lambda triggers):
- PostConfirmation Lambda → Creates database user on email verification
- PreTokenGeneration Lambda → Adds `custom:role` claim from database to JWT

**JWT Custom Claims**:
- `custom:role`: Comma-separated list of roles from database (e.g., "ATTENDEE,SPEAKER")
- `custom:language`: User's preferred language
- `custom:newsletter_optin`: Newsletter opt-in status

### GDPR Compliance

**Data Export**:
- Complete user profile export in JSON format
- Includes all activity history
- Includes all preferences and settings
- Audit logging of export requests

**Cascade Deletion**:
1. User initiates deletion request
2. System validates business rules (not last organizer)
3. Events published to all domain services
4. Each service deletes associated data
5. Audit log entry created
6. User profile soft-deleted with deletion timestamp (is_active = false)
7. Note: Cognito user NOT automatically disabled (per ADR-001, database-only operations)

**Audit Logging**:
- All GDPR-related operations logged to CloudWatch
- Includes: user ID, timestamp, requester, operation type, IP address
- Retention: 7 years for compliance

### Database Design

**User Profiles Table Schema** (simplified — the canonical, full schema lives in
`06b-user-lifecycle-sync.md` §Database Schema; preferences/settings columns omitted here):
```sql
CREATE TABLE user_profiles (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,       -- meaningful ID (ADR-003)
    cognito_user_id VARCHAR(255) UNIQUE,         -- NULL until first login for pre-invited users (JIT links it)
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    bio TEXT,
    company_id VARCHAR(36),
    profile_picture_url VARCHAR(2048),
    profile_picture_s3_key VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    terms_accepted_at TIMESTAMP WITH TIME ZONE,         -- Story 12.11 (V17): write-once ToS consent; NULL = onboarding gate
    picture_import_attempted_at TIMESTAMP WITH TIME ZONE, -- Story 12.12 (V18/V19): one-time federated avatar import claim
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    last_login_at TIMESTAMP,
    deleted_at TIMESTAMP
);

-- Performance Indexes
CREATE INDEX idx_user_email ON user_profiles(email);
CREATE INDEX idx_user_company ON user_profiles(company_id);
CREATE INDEX idx_user_active ON user_profiles(is_active);
CREATE INDEX idx_cognito_user_id ON user_profiles(cognito_user_id);

-- Full-text search index for name/email autocomplete
CREATE INDEX idx_user_search ON user_profiles
    USING gin(to_tsvector('english', first_name || ' ' || last_name || ' ' || email));
```

**Role Assignments Table Schema**:
```sql
CREATE TABLE role_assignments (
    user_id UUID NOT NULL,
    role VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role),
    FOREIGN KEY (user_id) REFERENCES user_profiles(id) ON DELETE CASCADE
);

CREATE INDEX idx_role_assignments_role ON role_assignments(role);
```

**Database Features**:
- Unique constraint on email (enforced at DB level)
- Unique constraint on cognito_user_id
- Automatic timestamp management with triggers
- UUID-based primary keys for distributed system compatibility
- Soft delete support with deleted_at timestamp
- Full-text search support for autocomplete
- Optimized indexes for common query patterns
