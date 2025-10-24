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

We have decided to implement a **database-centric user management architecture** with the following principles:

### Database as Source of Truth

- **PostgreSQL database** stores all user profile data (username, email, first name, last name, bio, roles, preferences, settings)
- **AWS Cognito** is used ONLY for authentication (JWT token generation and validation)
- **NO Cognito Groups**: Roles stored exclusively in database `role_assignments` table
- **Unidirectional sync**: Cognito → Database only (via Lambda triggers)

### Two User Creation Paths

#### Path 1: Self-Registration (Story 1.2.3)

```
┌─────────────┐   Registration    ┌──────────────────┐
│   New User  │──────────────────>│  Cognito SignUp  │
│             │  {email, pw, ...} │                  │
└─────────────┘                    └────────┬─────────┘
                                            │
                                            v
                                   ┌────────────────────┐
                                   │ PostConfirmation   │
                                   │ Lambda Trigger     │
                                   │                    │
                                   │ 1. Create DB user  │
                                   │ 2. Assign ATTENDEE │
                                   │    role (default)  │
                                   └────────────────────┘
```

#### Path 2: Admin Invitation (Future Story)

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
                                   │ PreTokenGeneration │
                                   │ Lambda Hook        │
                                   │                    │
                                   │ Links Cognito ID   │
                                   │ to DB record       │
                                   └────────────────────┘
```

### Technical Implementation

#### Self-Registration Path (Story 1.2.3 + 1.2.5)

1. **User Model** (`User.java`):
   ```java
   @Column(name = "cognito_user_id", nullable = false, unique = true)
   private String cognitoUserId;  // Always populated for self-registered users
   ```

2. **PostConfirmation Lambda** (Story 1.2.5):
   ```javascript
   exports.handler = async (event) => {
     const { sub, email, given_name, family_name } = event.request.userAttributes;

     // Create user in database
     await db.query(`
       INSERT INTO user_profiles (cognito_user_id, email, first_name, last_name, username)
       VALUES ($1, $2, $3, $4, $5)
       ON CONFLICT (cognito_user_id) DO NOTHING
     `, [sub, email, given_name, family_name, generateUsername(given_name, family_name)]);

     // Assign default ATTENDEE role
     const userId = await getUserId(sub);
     await db.query(`
       INSERT INTO role_assignments (user_id, role)
       VALUES ($1, 'ATTENDEE')
     `, [userId]);

     return event;
   };
   ```

3. **PreTokenGeneration Lambda** (Story 1.2.5):
   ```javascript
   exports.handler = async (event) => {
     const cognitoUserId = event.request.userAttributes.sub;

     // Fetch roles from database
     const roles = await db.query(`
       SELECT r.role
       FROM role_assignments r
       JOIN user_profiles u ON r.user_id = u.id
       WHERE u.cognito_user_id = $1
     `, [cognitoUserId]);

     // Add roles to JWT as custom claim (NO Cognito Groups)
     event.response = {
       claimsOverrideDetails: {
         claimsToAddOrOverride: {
           'custom:role': roles.map(r => r.role).join(',')
         }
       }
     };

     return event;
   };
   ```

4. **Spring Security JWT Configuration**:
   ```java
   @Bean
   public JwtAuthenticationConverter jwtAuthenticationConverter() {
       JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
       converter.setJwtGrantedAuthoritiesConverter(jwt -> {
           String rolesString = jwt.getClaimAsString("custom:role");
           return Arrays.stream(rolesString.split(","))
               .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
               .collect(Collectors.toList());
       });
       return converter;
   }
   ```

#### Admin Invitation Path (Future)

1. **User Service** (`UserService.java`):
   - `createUser()`: Creates database record with `cognitoUserId = null` (for invited users not yet registered)
   - `updateCurrentUser()`: Updates database only (no Cognito sync)
   - No AWS SDK calls for user management

2. **Cognito Integration Service** (`CognitoIntegrationServiceImpl.java`):
   - `syncUserAttributes()`: No-op (logs debug message)
   - `createCognitoUser()`: No-op (logs debug message)

3. **PreTokenGeneration Lambda** (Enhancement):
   ```javascript
   // Link invited user to Cognito account by email
   if (user.cognitoUserId === null) {
     await db.query(`
       UPDATE user_profiles
       SET cognito_user_id = $1
       WHERE email = $2
     `, [cognitoUserId, email]);
   }
   ```

## Consequences

### Positive

1. **No AWS Credentials Required**: Service works without IAM credentials for local development
2. **Simplified Security Model**: Service only needs database access and JWT validation
3. **Improved Testability**: API tests pass without AWS configuration
4. **Clear Separation of Concerns**:
   - Authentication = Cognito's responsibility
   - User data + roles = Database responsibility
   - NO dual storage (Cognito Groups eliminated)
5. **Better Developer Experience**: No AWS credential configuration needed for local development
6. **Cost Reduction**: No Cognito API calls for user/role management
7. **Single Source of Truth**: Database only for user data and roles (no sync drift)
8. **Self-Service Registration**: Users can register directly (Story 1.2.3) without admin intervention

### Negative

1. **Lambda Triggers Required**: PostConfirmation and PreTokenGeneration hooks are critical infrastructure
2. **Database Dependency**: JWT generation requires database availability (graceful degradation needed)
3. **Future Invitation Flow**: Will require additional implementation for admin-invited users
4. **Migration Complexity**: Need to handle both self-registered (cognitoUserId populated) and invited (cognitoUserId=null) users

### Neutral

1. **User Attributes in JWT**: Custom claims (roles) added to JWT via PreTokenGeneration Lambda
2. **Role Management**: Roles in database only, synced to JWT at login time (not stored in Cognito Groups)
3. **Two Registration Paths**: Self-registration (immediate) and invitation-based (future) supported

## Implementation Status

### Completed (Story 1.14-2)
- ✅ Database schema: `user_profiles` table with `cognito_user_id NOT NULL`
- ✅ Database schema: `role_assignments` table for user roles
- ✅ User Service: Database-only user management (no Cognito SDK calls)
- ✅ Event model supports `cognitoUserId` field
- ✅ API tests passing (POST /users returns 201 Created)

### In Progress (Story 1.2.5)
- ⏳ PostConfirmation Lambda: Create user in database on Cognito signup
- ⏳ PreTokenGeneration Lambda: Add `custom:role` claim from database
- ⏳ Spring Security: JWT converter to extract roles from `custom:role` claim

### Planned (Story 1.2.3)
- ⏳ Frontend registration wizard (Step 1: credentials, Step 2: review)
- ⏳ Cognito SignUp integration (frontend → Cognito direct)
- ⏳ Email verification flow

### Future (Invitation System)
- ⏳ Invitation email system (token generation, email template)
- ⏳ Registration page with invitation token validation
- ⏳ PreTokenGeneration enhancement: Link invited users by email

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

**Story 1.14-2 (User Management Service):**
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/domain/User.java:51` - `cognitoUserId` field
- `services/company-user-management-service/src/main/resources/db/migration/V4__create_user_profiles_table.sql` - Database schema
- `services/company-user-management-service/src/main/resources/db/migration/V5__create_role_assignments_table.sql` - Roles schema
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java` - Database-only user management

**Story 1.2.5 (User Sync):**
- `infrastructure/lib/lambda/triggers/post-confirmation.ts` - Creates DB user on signup
- `infrastructure/lib/lambda/triggers/pre-token-generation.ts` - Adds roles to JWT
- `docs/stories/1.2.5-user-sync-reconciliation-implementation.md` - Implementation story

**Story 1.2.3 (Account Creation):**
- `docs/stories/1.2.3-implement-account-creation-flow.md` - Registration UI story

## Notes

This decision represents a shift from a **Cognito-centric** to a **database-centric** user management architecture.

### Key Architectural Principles

1. **Database as Single Source of Truth**: All user data and roles stored in PostgreSQL
2. **Cognito for Authentication Only**: JWT generation and validation only
3. **NO Cognito Groups**: Roles managed exclusively via `role_assignments` table
4. **Unidirectional Sync**: Cognito → Database via Lambda triggers (PostConfirmation, PreTokenGeneration)
5. **Two Registration Paths**:
   - **Self-registration** (Story 1.2.3): User signs up → Cognito → Lambda creates DB record
   - **Admin invitation** (Future): Admin creates DB record → User completes Cognito signup → Lambda links via email

### Benefits

- **Simplified implementation**: No AWS SDK credentials needed for local development
- **Better testability**: API tests pass without AWS configuration
- **No sync drift**: Single source of truth eliminates consistency issues
- **Scalable queries**: Complex user queries in PostgreSQL, not limited by Cognito API
- **Cost-effective**: No Cognito API calls for user/role management

The database-centric approach provides a solid foundation for complex user management while keeping authentication concerns properly separated in Cognito.
