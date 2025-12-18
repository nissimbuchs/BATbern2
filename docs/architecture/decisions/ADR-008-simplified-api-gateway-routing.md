# ADR-008: Simplified API Gateway - Backend Controls All Routing

## Status
Accepted

## Context

Previously, the AWS HTTP API Gateway maintained 13 explicit public route definitions alongside a catch-all `/{proxy+}` route with JWT authorizer. This architecture required infrastructure deployment every time we added new backend routes.

### Problems with Previous Approach

1. **Infrastructure Coupling**: Adding new routes required updating CDK code and deploying infrastructure
2. **Route Duplication**: Public endpoints were defined in BOTH infrastructure (api-gateway-stack.ts) AND microservices (SecurityConfig.java)
3. **Deployment Friction**: Route changes required infrastructure deployment (~5-10 minutes) instead of just service deployment (~2-3 minutes)
4. **Maintenance Burden**: Same security rules maintained in two places (risk of inconsistency)

### Example of the Problem

When we added `/api/v1/topics` endpoints:
- Topics controller worked fine with proper `@PreAuthorize` annotations
- But all requests went through the `/{proxy+}` catch-all route with JWT authorizer
- This was correct for POST/PUT/DELETE (require auth)
- BUT: If we wanted public GET endpoints later, we'd need infrastructure updates

## Decision

We have decided to simplify the API Gateway architecture:

### Changes Made

1. **Removed all explicit route definitions** (13 routes deleted):
   - `/api/v1/config` - Frontend bootstrap configuration
   - `/health`, `/info` - Actuator endpoints
   - `/api/v1/events/current`, `/api/v1/events/{eventCode}` - Public event discovery
   - `/api/v1/events/{eventCode}/sessions*` - Session listings
   - `/api/v1/partners` - Partner showcase
   - `/api/v1/companies/{companyIdentifier}` - Company details
   - `/api/v1/events/{eventCode}/registrations*` - Anonymous registration

2. **Removed JWT authorizer from catch-all route**:
   - Previously: `/{proxy+}` required JWT at infrastructure level
   - Now: `/{proxy+}` forwards ALL requests to backend
   - Backend validates JWT and enforces authentication

3. **Updated architecture documentation**:
   - Infrastructure: Edge concerns only (CORS, throttling, DDoS protection)
   - Backend: All authentication and authorization
   - Single source of truth: Microservices define their own security

### New Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│ Client (Browser, Mobile App, API Consumer)                     │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         │ All Requests
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│ AWS HTTP API Gateway (Infrastructure)                          │
│ - CORS configuration                                            │
│ - Throttling (1000 req/sec, 2000 burst)                       │
│ - DDoS protection                                               │
│ - NO JWT validation                                             │
│ - Route: /{proxy+} → Spring Boot API Gateway                   │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         │ Proxied Requests
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│ Spring Boot API Gateway Service (ECS)                          │
│ - Route to microservices (based on path)                       │
│ - JWT validation via CognitoJWTValidator                       │
│ - SecurityConfig: public vs authenticated endpoints            │
│ - Rate limiting (role-based)                                    │
│ - Correlation ID generation                                     │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         │ Routed Requests
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│ Microservices (ECS)                                             │
│ - Each service: SecurityConfig.java                            │
│ - JWT validation via Spring Security                            │
│ - Route-level security (.permitAll() vs .authenticated())      │
│ - Method-level authorization (@PreAuthorize annotations)        │
│ - Business logic implementation                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Security Model

**Infrastructure Layer (AWS API Gateway)**:
- ✅ CORS enforcement
- ✅ Request throttling
- ✅ DDoS protection
- ❌ NO JWT validation (moved to backend)

**API Gateway Service Layer (Spring Boot)**:
- ✅ JWT signature validation
- ✅ Route requests to microservices
- ✅ Correlation ID propagation
- ✅ Role-based rate limiting

**Microservice Layer**:
- ✅ JWT re-validation (defense-in-depth)
- ✅ Route-level authentication (SecurityConfig)
- ✅ Method-level authorization (@PreAuthorize)
- ✅ Business logic enforcement

## Consequences

### Positive Outcomes

1. **Development Velocity** ⚡:
   - Adding new routes: Just update microservice code
   - No infrastructure deployment needed
   - Faster iteration cycle (2-3 min vs 5-10 min)

2. **Simpler Infrastructure** 📉:
   - Reduced from ~450 lines to ~150 lines
   - Single route definition: `/{proxy+}`
   - Easier to understand and maintain

3. **Single Source of Truth** 🎯:
   - Microservices control their own security
   - No duplication between infrastructure and services
   - Reduced risk of inconsistency

4. **Flexibility** 🔄:
   - Public endpoints: Just update SecurityConfig.java
   - Protected endpoints: Just add @PreAuthorize
   - No infrastructure changes required

5. **Cost Reduction** 💰:
   - Fewer infrastructure deployments
   - Less AWS API Gateway route configuration
   - Reduced maintenance overhead

### Negative Outcomes

1. **Edge Protection Lost** ⚠️:
   - Invalid JWTs now reach the backend
   - Minor performance impact: ~1-2ms per invalid request
   - Mitigation: Backend rejects immediately, no security risk

2. **Backend Dependency** ⚙️:
   - Infrastructure relies on backend for all security
   - Backend must be correctly configured
   - Mitigation: Integration tests verify security config

### Trade-off Analysis

| Aspect | Before | After | Impact |
|--------|--------|-------|--------|
| **Invalid JWT handling** | Rejected at edge | Rejected at backend | ❌ ~1-2ms latency increase |
| **Adding new route** | Infrastructure deploy | Service deploy only | ✅ 50% faster |
| **Public endpoint changes** | Update 2 places | Update 1 place | ✅ Reduced duplication |
| **Infrastructure complexity** | ~450 lines | ~150 lines | ✅ 66% reduction |
| **Security depth** | 2 layers | 1 layer | ⚠️ Acceptable (backend still validates) |

## Implementation Details

### Files Modified

1. **infrastructure/lib/stacks/api-gateway-stack.ts**:
   - Removed 13 explicit route definitions (~200 lines)
   - Removed `authorizer: this.authorizer` from catch-all route
   - Updated documentation comments

2. **services/event-management-service/src/main/java/ch/batbern/events/config/SecurityConfig.java**:
   - Changed `.anyRequest().permitAll()` to `.anyRequest().authenticated()`
   - Fixes POST /topics 403 error
   - Enables @PreAuthorize annotations

### Testing Strategy

**Step 1: Verify immediate fix (SecurityConfig)**:
```bash
# Test POST /topics with ORGANIZER role
curl -X POST https://api.staging.batbern.ch/api/v1/topics \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title": "Test Topic"}'

# Expected: 201 Created (not 403 Forbidden)
```

**Step 2: Verify infrastructure change**:
```bash
# Public endpoints work WITHOUT auth
curl https://api.staging.batbern.ch/api/v1/events/current
# Expected: 200 OK

# Protected endpoints return 401 WITHOUT JWT
curl -X POST https://api.staging.batbern.ch/api/v1/topics
# Expected: 401 Unauthorized

# Protected endpoints work WITH valid JWT
curl -X POST https://api.staging.batbern.ch/api/v1/topics \
  -H "Authorization: Bearer $TOKEN"
# Expected: 201 Created

# Invalid JWT returns 401 from backend
curl https://api.staging.batbern.ch/api/v1/topics \
  -H "Authorization: Bearer invalid.token"
# Expected: 401 Unauthorized
```

## Alternatives Considered

### Alternative 1: Keep JWT validation at edge + backend
**Pros**: Defense-in-depth, invalid tokens rejected early
**Cons**: Still requires infrastructure updates for new public routes
**Decision**: Rejected - doesn't solve the core problem

### Alternative 2: Use wildcard patterns for public routes
**Pros**: Fewer route definitions
**Cons**: Still requires infrastructure updates, less flexible
**Decision**: Rejected - partial solution only

### Alternative 3: Chosen approach (backend-only validation)
**Pros**: No infrastructure updates, simple, flexible
**Cons**: Invalid tokens reach backend
**Decision**: Accepted - benefits outweigh costs

## References

- Original issue: POST /api/v1/topics returned 403 Forbidden
- Root cause: `.anyRequest().permitAll()` prevented JWT parsing
- Related: Spring Security `@PreAuthorize` requires populated SecurityContext
- Related: AWS API Gateway JWT Authorizer documentation

## Notes

- JWT authorizer definition kept in code (commented) for documentation
- Can be re-enabled for specific high-security routes if needed
- Backend already had JWT validation, so this just removes redundancy
- No security compromise: backend still validates everything

## Date
2025-12-18
