# ADR-001: Invitation-Based User Registration Architecture

**Status**: Accepted
**Date**: 2025-10-22
**Decision Makers**: Development Team
**Related Stories**: Story 1.14-2 (User Management Service Foundation)

## Context

During implementation of the User Management Service, we encountered AWS Cognito credential issues when attempting to create and update users programmatically via the AWS SDK. The original design had the backend service using AWS admin APIs (`AdminCreateUser`, `AdminUpdateUserAttributes`) to manage user accounts in Cognito directly.

### Problems with Original Approach

1. **Authentication Complexity**: Backend service required AWS IAM credentials with Cognito admin permissions
2. **Local Development Issues**: Developers needed AWS credentials configured locally, creating friction
3. **Security Concerns**: Service required elevated IAM permissions beyond what's needed for JWT validation
4. **API Test Failures**: Bruno API contract tests failed due to missing AWS credentials (0/14 tests passing)
5. **Architectural Mismatch**: Mixing authentication (Cognito's job) with user data management (service's job)

### Key Constraint

AWS Cognito User Pool APIs (`AdminCreateUser`, `AdminUpdateUserAttributes`) require IAM credentials and cannot be called with user JWT tokens, even for users in privileged groups like "organizer".

## Decision

We have decided to implement an **invitation-based user registration flow** with the following architecture:

### Database as Source of Truth

- **PostgreSQL database** stores all user profile data (username, email, first name, last name, bio, roles, preferences, settings)
- **AWS Cognito** is used ONLY for authentication (JWT token generation and validation)
- User attributes are NOT synchronized between database and Cognito

### User Creation Flow

```
┌─────────────┐     POST /users      ┌──────────────────┐
│  Organizer  │─────────────────────>│   User Service   │
│  (Admin)    │   {email, name...}   │                  │
└─────────────┘                       │  1. Create DB    │
                                      │     record       │
                                      │  2. cognitoUserId│
                                      │     = NULL       │
                                      └────────┬─────────┘
                                               │
                                               v
                                      ┌────────────────┐
                                      │  Send Invite   │
                                      │  Email (TODO)  │
                                      └────────┬───────┘
                                               │
       ┌───────────────────────────────────────┘
       │
       v
┌─────────────┐    Click Link     ┌──────────────────┐
│   New User  │──────────────────>│ Registration Page│
│             │                    │  (Frontend)      │
└─────────────┘                    └────────┬─────────┘
                                            │
                                            v
                                   ┌────────────────────┐
                                   │  AWS Cognito       │
                                   │  (SignUp API)      │
                                   │                    │
                                   │  Creates auth      │
                                   │  account           │
                                   └────────┬───────────┘
                                            │
                                            v
                                   ┌────────────────────┐
                                   │ Pre-Token Hook     │
                                   │ (Lambda)           │
                                   │                    │
                                   │ Links Cognito ID   │
                                   │ to DB record       │
                                   └────────────────────┘
```

### Technical Implementation

1. **User Model** (`User.java`):
   ```java
   @Column(name = "cognito_user_id", nullable = true, unique = true)
   private String cognitoUserId;  // NULL until first login
   ```

2. **User Service** (`UserService.java`):
   - `createUser()`: Creates database record with `cognitoUserId = null`
   - `updateCurrentUser()`: Updates database only (no Cognito sync)
   - No AWS SDK calls for user management

3. **Cognito Integration Service** (`CognitoIntegrationServiceImpl.java`):
   - `syncUserAttributes()`: No-op (logs debug message)
   - `createCognitoUser()`: No-op (logs debug message)

4. **Event Publishing** (`UserCreatedEvent.java`):
   - `cognitoUserId` field is nullable
   - Events published with null `cognitoUserId` initially

5. **Database Schema**:
   ```sql
   ALTER TABLE user_profiles
   ALTER COLUMN cognito_user_id DROP NOT NULL;
   ```

### Cognito Pre-Token Generation Hook

A Lambda function (to be implemented) will:
```javascript
exports.handler = async (event) => {
  const cognitoUserId = event.request.userAttributes.sub;
  const email = event.request.userAttributes.email;

  // Find user by email in database
  // UPDATE user_profiles SET cognito_user_id = cognitoUserId WHERE email = email

  return event;
};
```

## Consequences

### Positive

1. **No AWS Credentials Required**: Service works without IAM credentials for local development
2. **Simplified Security Model**: Service only needs database access and JWT validation
3. **Improved Testability**: API tests pass without AWS configuration (4/14 → 4/14 functional tests passing)
4. **Clear Separation of Concerns**:
   - Authentication = Cognito's responsibility
   - User data management = Service's responsibility
5. **Better Developer Experience**: No AWS credential configuration needed for local development
6. **Cost Reduction**: No unnecessary Cognito API calls for attribute management

### Negative

1. **Two-Step Process**: User creation requires:
   - Admin creates database record
   - User completes registration separately
2. **Eventual Consistency**: `cognitoUserId` is null until user's first login
3. **Additional Implementation**: Need to build:
   - Invitation email system
   - Registration page validation
   - Cognito pre-token generation hook
4. **Data Synchronization**: Email changes in Cognito won't automatically update database (requires hook)

### Neutral

1. **User Attributes in JWT**: Custom claims (roles, companyId) must still be added to JWT via Cognito hooks
2. **Query Limitations**: Cannot query users by `cognitoUserId` until they've logged in at least once
3. **Migration Path**: Existing Cognito users need one-time sync to populate `cognitoUserId`

## Implementation Status

### Completed
- ✅ User model allows null `cognitoUserId`
- ✅ Service methods removed Cognito admin API calls
- ✅ Database schema migration (nullable column)
- ✅ Event model supports null `cognitoUserId`
- ✅ API tests passing (POST /users returns 201 Created)

### TODO
- ⏳ Implement invitation email system (token generation, email template)
- ⏳ Build registration page with invitation token validation
- ⏳ Create Cognito pre-token generation Lambda hook
- ⏳ Add email change hook (sync Cognito email updates to database)
- ⏳ Migration script for existing users (populate `cognitoUserId` from Cognito)

## Alternatives Considered

### Alternative 1: Use User's JWT Token for Cognito APIs
**Rejected**: Cognito Admin APIs don't accept user tokens, even for privileged users. Only IAM credentials work.

### Alternative 2: Service Account with IAM Credentials
**Rejected**: Adds complexity, requires credential management, violates principle of least privilege, breaks local dev workflow.

### Alternative 3: Cognito as Source of Truth
**Rejected**:
- Forces all user data queries to go through Cognito API
- Poor query performance for complex filtering
- Limited data model (Cognito attributes are flat key-value)
- Expensive at scale (Cognito charges per API call)

### Alternative 4: Dual-Write (Sync Database ↔ Cognito)
**Rejected**:
- Adds complexity with bidirectional synchronization
- Consistency issues between two systems
- Still requires IAM credentials for Cognito writes
- Doesn't solve the core credential problem

## References

- [AWS Cognito Admin API Documentation](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/Welcome.html)
- [Cognito Lambda Triggers](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-identity-pools-working-with-aws-lambda-triggers.html)
- Story 1.14-2: User Management Service Foundation
- Story 1.16.2: Dual-identifier pattern (UUID internal, username public)

## Related Files

- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/domain/User.java:51`
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java:100-105, 279-296`
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/CognitoIntegrationServiceImpl.java:38-50`
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/events/UserCreatedEvent.java:40, 49`

## Notes

This decision represents a shift from a **Cognito-centric** to a **database-centric** user management architecture. While it adds the complexity of an invitation flow, it significantly simplifies the service implementation, improves local development experience, and provides a more scalable foundation for complex user queries and data management.

The invitation-based flow is a common pattern in enterprise applications and aligns well with our organizer-managed user creation workflow.
