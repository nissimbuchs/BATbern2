# User Management API

This document outlines the User Management Service API, which handles user profiles, preferences, settings, role management, and comprehensive GDPR compliance capabilities.

## Overview

The User Management API provides endpoints for:
- User profile CRUD operations with full lifecycle management
- Advanced query patterns (filter, sort, pagination, field selection, resource expansion)
- User search with autocomplete and Caffeine caching
- User preferences and settings management
- Role management with business rules enforcement
- Activity history tracking and analytics
- Profile picture upload via S3 presigned URLs
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
  - ?include=preferences: Includes user preferences
  - ?include=settings: Includes account settings
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

#### Update Current User

```yaml
PUT /api/v1/users/me
tags: [User Management]
summary: Update current user profile
operationId: updateCurrentUser
description: |
  Update the profile of the currently authenticated user.

  **Validation Rules**:
  - Email must be valid format and unique
  - Names: 2-100 characters
  - Bio: max 2000 characters

  **Database Only**: User updates stored in PostgreSQL only (per ADR-001, NO Cognito sync)

  **Events Published**: UserUpdatedEvent to EventBridge

  **Cache Invalidation**: All user caches cleared on update

security:
  - BearerAuth: []
requestBody:
  required: true
  content:
    application/json:
      schema:
        $ref: '#/components/schemas/UpdateUserRequest'
responses:
  '200':
    description: User updated successfully
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/UserResponse'
  '400':
    description: Bad request - validation error
  '401':
    description: Unauthorized
  '409':
    description: Conflict - email already exists
  '500':
    description: Internal server error
```

#### List Users (Admin/Organizer)

```yaml
GET /api/v1/users
tags: [User Management]
summary: List users with advanced query support
operationId: listUsers
description: |
  Retrieve a paginated list of users with optional filtering, sorting,
  field selection, and resource expansion.

  **Authorization**: ADMIN or ORGANIZER role required

  **Filter Syntax Examples**:
  - Single filter: {"role":"SPEAKER"}
  - Multiple fields: {"role":"SPEAKER","isActive":true}
  - Company filter: {"companyId":"550e8400-e29b-41d4-a716-446655440000"}
  - Logical operators: {"$or":[{"role":"SPEAKER"},{"role":"PARTNER"}]}

  **Sort Syntax**:
  - Ascending: lastName or +lastName
  - Descending: -createdAt
  - Multiple fields: lastName,-createdAt

  **Field Selection**:
  - Specific fields: ?fields=id,email,firstName,lastName,roles
  - All fields: omit fields parameter

  **Resource Expansion**:
  - ?include=company: Expands company details
  - ?include=roles: Includes full role information
  - ?include=preferences: Includes user preferences

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
  - name: fields
    in: query
    description: Comma-separated field names for sparse fieldsets
    required: false
    schema:
      type: string
      example: 'id,email,firstName,lastName,roles'
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
      format: uuid
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
    description: Forbidden - requires ADMIN or ORGANIZER role
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
      format: uuid
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

### Preferences and Settings

#### Get User Preferences

```yaml
GET /api/v1/users/me/preferences
tags: [User Preferences]
summary: Get current user preferences
operationId: getUserPreferences
description: |
  Retrieve preferences for the currently authenticated user.

  Includes: theme, language, notification settings, quiet hours

security:
  - BearerAuth: []
responses:
  '200':
    description: Preferences retrieved successfully
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/UserPreferences'
  '401':
    description: Unauthorized
  '500':
    description: Internal server error
```

#### Update User Preferences

```yaml
PUT /api/v1/users/me/preferences
tags: [User Preferences]
summary: Update current user preferences
operationId: updateUserPreferences
description: |
  Update preferences for the currently authenticated user.

  **Validation**: All preference values validated against allowed enums/formats

security:
  - BearerAuth: []
requestBody:
  required: true
  content:
    application/json:
      schema:
        $ref: '#/components/schemas/UserPreferences'
responses:
  '200':
    description: Preferences updated successfully
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/UserPreferences'
  '400':
    description: Bad request - validation error
  '401':
    description: Unauthorized
  '500':
    description: Internal server error
```

#### Get User Settings

```yaml
GET /api/v1/users/me/settings
tags: [User Settings]
summary: Get current user settings
operationId: getUserSettings
description: |
  Retrieve account settings for the currently authenticated user.

  Includes: privacy controls, account preferences, security settings

security:
  - BearerAuth: []
responses:
  '200':
    description: Settings retrieved successfully
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/UserSettings'
  '401':
    description: Unauthorized
  '500':
    description: Internal server error
```

#### Update User Settings

```yaml
PUT /api/v1/users/me/settings
tags: [User Settings]
summary: Update current user settings
operationId: updateUserSettings
description: |
  Update account settings for the currently authenticated user.

  **Privacy Controls**: Profile visibility, contact information display
  **Security Settings**: Two-factor authentication enablement

security:
  - BearerAuth: []
requestBody:
  required: true
  content:
    application/json:
      schema:
        $ref: '#/components/schemas/UserSettings'
responses:
  '200':
    description: Settings updated successfully
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/UserSettings'
  '400':
    description: Bad request - validation error
  '401':
    description: Unauthorized
  '500':
    description: Internal server error
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

  **Authorization**: ADMIN or ORGANIZER role required

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
    description: Roles retrieved successfully
    content:
      application/json:
        schema:
          type: object
          properties:
            userId:
              type: string
              format: uuid
            roles:
              type: array
              items:
                type: string
                enum: [ORGANIZER, SPEAKER, PARTNER, ATTENDEE]
  '401':
    description: Unauthorized
  '403':
    description: Forbidden - requires ADMIN or ORGANIZER role
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

  **Authorization**: ADMIN or ORGANIZER role required

  **Business Rules**:
  - System must maintain at least 2 active ORGANIZER role users
  - Removing ORGANIZER role from user requires approval if only 2 organizers remain

  **Events Published**: UserRoleChangedEvent to EventBridge

  **Cache Invalidation**: All user caches cleared on role update

security:
  - BearerAuth: []
parameters:
  - name: id
    in: path
    required: true
    schema:
      type: string
      format: uuid
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
            userId:
              type: string
              format: uuid
            roles:
              type: array
              items:
                type: string
  '400':
    description: Bad request - validation error or business rule violation
  '401':
    description: Unauthorized
  '403':
    description: Forbidden - requires ADMIN or ORGANIZER role
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
      format: uuid
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
    description: Forbidden - can only view own activity unless ADMIN/ORGANIZER
  '404':
    description: User not found
  '500':
    description: Internal server error
```

### Profile Picture

#### Upload Profile Picture

```yaml
POST /api/v1/users/me/picture
tags: [Profile Picture]
summary: Upload profile picture
operationId: uploadProfilePicture
description: |
  Generate presigned S3 upload URL for profile picture.

  **File Constraints**:
  - Max size: 5 MB
  - Allowed formats: PNG, JPG, JPEG
  - Recommended dimensions: 400x400 to 1000x1000 pixels

  **Upload Process**:
  1. Client calls this endpoint to get presigned URL
  2. Client uploads directly to S3 using presigned URL
  3. Client calls confirm endpoint with file ID

security:
  - BearerAuth: []
requestBody:
  required: true
  content:
    application/json:
      schema:
        type: object
        required:
          - filename
          - fileSizeBytes
          - mimeType
        properties:
          filename:
            type: string
            maxLength: 255
          fileSizeBytes:
            type: integer
            minimum: 1
            maximum: 5242880  # 5 MB
          mimeType:
            type: string
            enum: [image/png, image/jpeg, image/jpg]
responses:
  '200':
    description: Presigned upload URL generated successfully
    content:
      application/json:
        schema:
          type: object
          properties:
            uploadUrl:
              type: string
              format: uri
              description: Presigned S3 upload URL (valid for 15 minutes)
            fileId:
              type: string
              description: File identifier for confirmation
            expiresIn:
              type: integer
              description: URL expiration time in seconds
              example: 900
  '400':
    description: Bad request - invalid file size or type
  '401':
    description: Unauthorized
  '500':
    description: Internal server error
```

### GDPR Compliance

#### Delete User (GDPR)

```yaml
DELETE /api/v1/users/{id}
tags: [GDPR Compliance]
summary: Delete user and all associated data
operationId: deleteUser
description: |
  Delete a user and cascade deletion across all domain services.

  **Authorization**: ADMIN role or self-deletion

  **Business Rules**:
  - Cannot delete last ORGANIZER user
  - Cascade deletion across Event, Speaker, Partner, Attendee services
  - Audit logging for GDPR compliance

  **Events Published**: UserDeletedEvent to EventBridge

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
      format: uuid
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
      format: uuid
      example: 550e8400-e29b-41d4-a716-446655440000
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
    companyId:
      type: string
      format: uuid
      example: 550e8400-e29b-41d4-a716-446655440001
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
      format: uuid
    email:
      type: string
    firstName:
      type: string
    lastName:
      type: string
    companyId:
      type: string
      format: uuid
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
    companyId:
      type: string
      format: uuid
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
    userId:
      type: string
      format: uuid
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
      format: uuid
    userId:
      type: string
      format: uuid
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

### Event-Driven Architecture

**EventBridge Integration**:
- Event bus: `batbern-{environment}-event-bus`
- Event source: `ch.batbern.user`
- Published events:
  - `UserCreatedEvent`: When new user is created
  - `UserUpdatedEvent`: When user data is modified
  - `UserRoleChangedEvent`: When user roles are updated
  - `UserDeletedEvent`: When user is removed (GDPR)

**Event Schema Example**:
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "email": "john.doe@example.com",
  "roles": ["SPEAKER", "ATTENDEE"],
  "companyId": "550e8400-e29b-41d4-a716-446655440001",
  "eventTimestamp": "2025-01-15T10:00:00Z"
}
```

**Event Consumers**:
- Speaker Coordination Service: Associates speaker records with user accounts
- Partner Coordination Service: Associates partner contacts with user accounts
- Event Management Service: Updates event registrations and attendance
- Notification Service: Triggers welcome emails and notification setup
- Analytics Service: Tracks user engagement metrics

### Advanced Query Patterns

**Filter Syntax** (MongoDB-style JSON):
```json
{
  "role": "SPEAKER",
  "isActive": true,
  "companyId": "550e8400-e29b-41d4-a716-446655440000"
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

**Field Selection** (Sparse Fieldsets):
- Request specific fields: `?fields=id,email,firstName,lastName,roles`
- Reduces payload size for large result sets
- Always includes `id` field regardless of selection

**Resource Expansion**:
- `?include=company`: Expands company details
- `?include=roles`: Includes full role information
- `?include=preferences`: Includes user preferences
- `?include=settings`: Includes account settings
- `?include=activity`: Includes recent activity history
- Multiple expansions: `?include=company,roles,preferences`
- Performance target: <150ms (P95) with all expansions

### Security & Authorization

**Authentication**:
- JWT-based authentication via AWS Cognito
- Bearer token required for all endpoints
- Token claims: `sub` (Cognito user ID), `email`, `custom:role` (roles from database)

**Authorization Levels**:
- Public (authenticated): GET /users/me, PUT /users/me, preferences, settings
- ADMIN/ORGANIZER: GET /users (list all), GET /users/{id}, role management
- ADMIN only: DELETE /users (GDPR deletion)
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

**User Profiles Table Schema**:
```sql
CREATE TABLE user_profiles (
    id UUID PRIMARY KEY,
    cognito_user_id VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    bio TEXT,
    company_id VARCHAR(36),
    profile_picture_url VARCHAR(2048),
    profile_picture_s3_key VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
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
