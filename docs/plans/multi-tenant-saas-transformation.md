# Multi-Tenant SaaS Transformation Plan

**Status:** Architecture Exploration
**Date:** 2026-03-13
**Author:** Winston (Architect Agent) + Nissim

## Vision

Transform the BATbern event management platform into a white-label, multi-tenant SaaS service. Each tenant operates under their own branding, custom domain, and isolated data — while the platform runs as a single deployment with a single CI/CD pipeline.

## Key Architectural Decisions

### 1. Single CI/CD Pipeline (Non-Negotiable)

The pipeline must remain identical regardless of tenant count. Tenants are **data, not infrastructure**. Provisioning new tenants is a runtime operation, never a deployment change.

**Implication:** No per-tenant schemas, stacks, or build steps.

### 2. Data Isolation: Shared DB, Shared Schema + Row-Level Security

Every table gains a `tenant_id UUID NOT NULL` column. PostgreSQL Row-Level Security (RLS) enforces isolation at the database level, preventing cross-tenant data leakage even if application code omits a filter.

```sql
ALTER TABLE events ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON events
  USING (tenant_id = current_setting('app.current_tenant')::UUID);
```

At the start of each request, the service sets:
```sql
SET app.current_tenant = '<tenant-uuid>';
```

**Why not schema-per-tenant:** Flyway would need to run N migrations per deploy (one per tenant schema), creating linear deployment cost and rollback complexity. This violates the single-pipeline constraint.

**Indexes:** Composite indexes `(tenant_id, ...)` on all major query paths to maintain performance.

### 3. Authentication: Cognito Pool-Per-Tenant

Each tenant gets its own AWS Cognito User Pool, enabling:
- Same email address across different tenants
- Tenant-specific auth policies (MFA, password rules)
- Clean deletion (remove tenant = remove pool)
- Zero cross-tenant user leakage

The API Gateway dynamically resolves which Cognito pool to validate JWTs against, based on the tenant resolved from the request.

### 4. Tenant Resolution: Host Header Based

```
newclient.yoursaas.com  →  tenant: "newclient"   (default subdomain)
newclient.com           →  tenant: "newclient"   (custom domain, premium)
```

Resolution flow:
1. Request arrives with `Host` header
2. API Gateway looks up `tenant_registry` table (cached)
3. Injects `X-Tenant-Id` header into downstream service calls
4. Each service sets `TenantContext` from the header

### 5. Branding & White-Labeling

Tenant configuration stored as JSONB in `tenant_registry`:
- Display name, logo URL, favicon
- Primary/secondary colors, font choices
- Email template overrides
- Language defaults
- Custom footer text, legal links

Frontend loads tenant config at bootstrap via `/api/tenant/config` (resolved by domain). CSS custom properties drive theming. All hardcoded "BATbern" references become `{{tenant.displayName}}`.

### 6. Custom Domain Automation (CloudFront + ACM)

Automated flow when a tenant configures a custom domain:
1. Request ACM certificate for the domain
2. Return DNS validation CNAME to the tenant
3. Tenant adds CNAME to their DNS provider
4. ACM validates and issues certificate
5. Add domain as CloudFront alternate domain name
6. Tenant points A/AAAA records to CloudFront
7. Update `tenant_registry` with custom domain

All via AWS SDK — no infrastructure-as-code changes needed.

## Core Data Model

### Tenant Registry

```sql
CREATE TABLE tenant_registry (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug              VARCHAR(63) UNIQUE NOT NULL,
    display_name      VARCHAR(255) NOT NULL,
    custom_domain     VARCHAR(255) UNIQUE,
    platform_subdomain VARCHAR(63) UNIQUE NOT NULL,
    cognito_pool_id   VARCHAR(255) NOT NULL,
    cognito_client_id VARCHAR(255) NOT NULL,
    cognito_region    VARCHAR(20) DEFAULT 'eu-central-1',
    branding_config   JSONB NOT NULL DEFAULT '{}',
    feature_flags     JSONB NOT NULL DEFAULT '{}',
    billing_plan      VARCHAR(50) DEFAULT 'STARTER',
    status            VARCHAR(20) DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);
```

## Architecture Overview

```
                    ┌──────────────────┐
                    │   CloudFront     │
                    │  (custom domains)│
                    └────────┬─────────┘
                             │
                    ┌────────▼─────────┐
                    │   API Gateway    │
                    │  Tenant Resolver │──→ tenant_registry (cached)
                    │  Multi-pool JWT  │
                    └────────┬─────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │ Service A │  │ Service B │  │ Service C │
        │ +Tenant   │  │ +Tenant   │  │ +Tenant   │
        │  Context  │  │  Context  │  │  Context  │
        └─────┬─────┘  └─────┬─────┘  └─────┬─────┘
              │              │              │
              └──────────────┼──────────────┘
                             ▼
                    ┌─────────────────┐
                    │   PostgreSQL    │
                    │  Shared Schema  │
                    │  RLS Enforced   │
                    │  tenant_id col  │
                    └─────────────────┘
```

## BATbern Migration Strategy

BATbern becomes Tenant #1 with a seamless migration:

1. Add `tenant_id` column to all tables with default = BATbern's UUID
2. Backfill all existing rows
3. Add NOT NULL constraint
4. Enable RLS policies
5. Create BATbern entry in `tenant_registry`
6. Designate existing Cognito pool as BATbern's tenant pool
7. Map `batbern.ch` as custom domain

Users experience zero disruption — same URL, same login, same data.

## Tenant Provisioning Flow

Runtime operation (not pipeline):

1. Create `tenant_registry` row
2. Create Cognito User Pool + App Client (AWS SDK)
3. Store Cognito IDs in registry
4. Seed default branding config
5. Create admin user in tenant's pool
6. (If custom domain) Initiate ACM cert + CloudFront config

## TenantContext Threading (Explored)

### Design Principles

- Follows the same ThreadLocal pattern already used for `SecurityContextHolder` and `CorrelationId` / MDC
- Lives in `shared-kernel` — usable by all services without duplication
- Consolidates the currently-duplicated `SecurityContextHelper` (exists in 3 services) into shared-kernel

### Components

| Component | Location | Responsibility |
|-----------|----------|----------------|
| `TenantContext` | shared-kernel | ThreadLocal holder (set/get/require/clear) |
| `TenantInfo` | shared-kernel | Immutable record: tenantId, slug, displayName |
| `TenantResolutionFilter` | api-gateway | Resolves tenant from Host header, sets context + MDC, adds X-Tenant-Id/X-Tenant-Slug headers |
| `TenantContextFilter` | shared-kernel | Extracts X-Tenant-Id from gateway request, sets TenantContext |
| `TenantAwareDataSource` | shared-kernel | Wraps DataSource; on each connection checkout runs `SET app.current_tenant = '<uuid>'` for RLS |
| `TenantAwareTaskDecorator` | shared-kernel | Propagates TenantContext across @Async / thread pool boundaries |

### Request Flow

```
CloudFront → API Gateway
  ├─ TenantResolutionFilter: Host → tenant_registry lookup → TenantContext.set() + MDC + X-Tenant-Id header
  ├─ SecurityFilter: JWT validated against tenant's Cognito pool (pool ID from tenant_registry)
  ├─ RequestTransformer: adds X-User-Id, X-User-Email, X-User-Role (existing)
  ▼
Downstream Service
  ├─ TenantContextFilter: reads X-Tenant-Id → TenantContext.set()
  ├─ CorrelationIdFilter (existing)
  ├─ SecurityFilter (existing)
  ▼
Service Layer → TenantAwareDataSource → SET app.current_tenant → PostgreSQL RLS enforces isolation
```

### Cross-Service Propagation

Existing `*ApiClient` classes already forward JWT via `SecurityContextHolder`. They gain two additional headers:
- `X-Tenant-Id` — tenant UUID
- `X-Tenant-Slug` — tenant slug

### Async Propagation

`TenantAwareTaskDecorator` captures TenantInfo before task submission, restores it in the worker thread, and clears on completion. Applied to all `@Async` executors.

### Testing

`AbstractIntegrationTest` gains a `TestTenantContext` utility that sets a fixed test tenant UUID and runs `SET app.current_tenant` before each test. All existing tests continue to work as BATbern = tenant #1.

### Consolidation

`SecurityContextHelper` (currently duplicated in event-management, company-user-management, partner-coordination) moves to `shared-kernel/security/SecurityContextHelper.java` as part of this work.

## Migration Roadmap (Explored)

### Guiding Principle

BATbern keeps working at every phase boundary. Each phase is independently deployable and rollback-safe.

### Phase 0: Foundation — Shared Kernel Prep (~1 week)

**Risk: None | BATbern impact: Zero**

- Move `SecurityContextHelper` from 3 services into `shared-kernel`
- Add `TenantContext`, `TenantInfo` to shared-kernel (unused yet)
- Add `TenantContextFilter` to shared-kernel (disabled via config flag)
- No behavior change — just code preparation

### Phase 1: Tenant Registry + API Gateway Resolution (~1-2 weeks)

**Risk: Low | BATbern impact: Zero (backward compatible)**

- Create `tenant_registry` table
- Insert BATbern as tenant #1 with existing Cognito pool config
- Add `TenantResolutionFilter` to API Gateway
  - Resolves `Host: *.batbern.ch` → BATbern tenant
  - Fallback: if no tenant found, default to BATbern (backward compat)
  - Adds `X-Tenant-Id` header to downstream calls
- Enable `TenantContextFilter` in each service
- Add `tenantId` to MDC for structured logging

### Phase 2: Database Tenant Isolation (~3-4 weeks) — CRITICAL PATH

**Risk: Medium | BATbern impact: Requires careful migration window**

See detailed Phase 2 breakdown below.

### Phase 3: Multi-Pool Cognito Auth (~2 weeks)

**Risk: Medium | BATbern impact: Auth path changes**

- Refactor API Gateway security filter to be multi-pool aware
- Resolve tenant → load Cognito pool config → validate JWT against that pool's JWKS
- Cache JWKS per pool
- BATbern's existing pool becomes its tenant pool
- VPC-internal bypass continues to work unchanged

### Phase 4: Extract BATbern Branding → Tenant Config (~2-3 weeks)

**Risk: Low | BATbern impact: Visual parity must be exact**

Backend:
- Replace `"BATbern" + eventNumber` in EventController (3 places) with configurable prefix
- Replace `"BATbern"` CloudWatch namespace with tenant-aware namespace
- Move email sender name/address to tenant config

Frontend:
- Create `/api/tenant/config` endpoint returning branding JSON
- Load at app bootstrap before render
- Replace hardcoded values: document.title, PWA manifest, CSS colors (→ custom properties), i18n `app.name`, `BATbernLoader` → generic `AppLoader`
- Parameterize email templates

Acceptance: Deploy this phase, BATbern looks and works identically. Values come from config instead of hardcoded.

### Phase 5: Tenant Provisioning Service (~2-3 weeks)

**Risk: Low | BATbern impact: None (new capability)**

- Management API: CRUD tenants, initiate custom domain setup
- Provisioning flow: create registry row → create Cognito pool → create admin user → return credentials
- Custom domain: request ACM cert → return DNS validation records → poll → add CloudFront alternate domain

### Phase 6: Tenant Management UI (~2-3 weeks)

**Risk: Low | BATbern impact: None**

- Admin panel for platform operators (super-admin role)
- Tenant list, branding editor, domain status, usage dashboards

### Phase 7: Billing, Rate Limiting, Feature Gating (~3-4 weeks)

**Risk: Low-Medium | BATbern impact: BATbern gets unlimited founder tier**

- Billing integration (Stripe or similar)
- Per-tenant rate limits at API Gateway
- Feature flags from `tenant_registry.feature_flags` JSONB

### Timeline

```
Phase 0 ─── Phase 1 ─── Phase 2 ──────── Phase 3 ─── Phase 4 ─── Phase 5 ─── Phase 6/7
 Prep     Tenant Reg   DB Migration    Multi-Auth   Branding   Provisioning  Billing/UI
 [1w]      [1-2w]       [3-4w]          [2w]         [2-3w]     [2-3w]       [3-4w]
                           ▲
                    Critical path
```

Total: ~15-20 weeks for a small team.

---

## Phase 2 Detail: Database Tenant Isolation

### Table Inventory

**Company-User-Management Service (8 tables):**

| Table | Foreign Keys | Notes |
|-------|-------------|-------|
| companies | — | Core entity |
| user_profiles | — | Dual-identifier (UUID + username) |
| role_assignments | → user_profiles | CASCADE delete |
| activity_history | → user_profiles | CASCADE delete |
| logos | — | Shared table (IF NOT EXISTS in both services) |
| shedlock | — | Infrastructure — SKIP tenant_id |
| watch_pairings | → user_profiles.username | Unique pairing tokens |
| presentation_settings | — | Single-row config |

**Event-Management Service (28 tables):**

| Table | Foreign Keys | Notes |
|-------|-------------|-------|
| events | — | Core entity, event_code unique |
| sessions | → events | CASCADE |
| registrations | → events | CASCADE, supports waitlist |
| logos | — | Shared (IF NOT EXISTS) — same as company-user |
| session_users | → sessions | Cross-service ref to user_profiles |
| topics | — | Full-text search vectors |
| topic_usage_history | → topics, events | CASCADE |
| speaker_pool | → events | CASCADE |
| speaker_outreach_history | → speaker_pool | CASCADE |
| task_templates | — | Reusable templates, seeded |
| event_tasks | → events, task_templates | CASCADE |
| speaker_status_history | → speaker_pool, events | CASCADE |
| session_timing_history | → sessions | CASCADE |
| speaker_slot_preferences | → speaker_pool, events | CASCADE |
| publishing_versions | → events | CASCADE, content snapshots |
| publishing_config | → events | CASCADE, unique per event |
| shedlock | — | Infrastructure — SKIP |
| notifications | — | ADR-003: meaningful IDs, no FKs |
| speakers | — | Cross-service ref via username |
| session_materials | → sessions | CASCADE |
| speaker_invitation_tokens | → speaker_pool | CASCADE |
| speaker_content_submissions | → speaker_pool, sessions | CASCADE |
| email_templates | — | DB-backed, seeded on startup |
| newsletter_subscribers | — | Unsubscribe tokens |
| newsletter_sends | → events | |
| newsletter_recipients | → newsletter_sends | CASCADE |
| ai_generation_log | — | Cost monitoring |
| event_photos | → events.event_code | CASCADE |
| event_teaser_images | → events.event_code | CASCADE |
| ai_prompts | — | Organizer-editable, 3 rows |
| speaker_reminder_log | → speaker_pool, events | CASCADE |
| speaker_arrivals | — | Idempotent (event_code + username) |

**Partner-Coordination Service (6 tables):**

| Table | Foreign Keys | Notes |
|-------|-------------|-------|
| partners | — | Uses company_name (ADR-003) |
| topic_votes | → topic_suggestions | Composite PK |
| topic_suggestions | — | ADR-003, no FKs |
| partner_meetings | — | Spring/autumn/ad_hoc |
| partner_meeting_attendance | → partner_meetings, partners | CASCADE |
| partner_notes | → partners | CASCADE |

### Tables to SKIP (no tenant_id needed)

- `shedlock` — Infrastructure table for distributed locking. Shared across all services, not tenant-specific data.

### Tables needing special consideration

- `logos` — Exists in two services (IF NOT EXISTS). Must coordinate migration to avoid conflicts. Tenant-scoped.
- `task_templates` — Seeded on startup. **Decision: global defaults + tenant overrides.** `tenant_id IS NULL` = platform default; tenant-specific rows override. RLS policy: `USING (tenant_id = current_setting('app.current_tenant')::UUID OR tenant_id IS NULL)`.
- `email_templates` — Same pattern as task_templates. Global defaults + tenant overrides.
- `ai_prompts` — Same pattern. Global defaults + tenant overrides.
- `presentation_settings` — One row per tenant.

### Migration Sub-Steps

**Step 2a — Add columns (non-breaking, fast):**

```sql
-- BATbern tenant UUID (fixed, deterministic)
-- '00000000-0000-4000-a000-000000000001'

ALTER TABLE events ADD COLUMN tenant_id UUID
    DEFAULT '00000000-0000-4000-a000-000000000001';
-- Repeat for all ~40 tables (excluding shedlock)
```

PostgreSQL 11+ makes `ADD COLUMN ... DEFAULT` near-instant (no full table rewrite). Safe for large tables.

**Step 2b — Backfill + constrain:**

```sql
UPDATE events SET tenant_id = '00000000-0000-4000-a000-000000000001'
    WHERE tenant_id IS NULL;
ALTER TABLE events ALTER COLUMN tenant_id SET NOT NULL;
```

**Step 2c — RLS policies + indexes:**

```sql
ALTER TABLE events ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON events
    USING (tenant_id = current_setting('app.current_tenant')::UUID);

-- Performance: composite indexes on hot query paths
CREATE INDEX idx_events_tenant ON events (tenant_id);
CREATE INDEX idx_events_tenant_status ON events (tenant_id, status);
CREATE INDEX idx_events_tenant_date ON events (tenant_id, event_date);
```

**Step 2d — TenantAwareDataSource activation:**

- Wrap each service's DataSource
- On connection checkout: `SET app.current_tenant = '<uuid>'`
- CRITICAL: Service DB role must NOT be superuser (superusers bypass RLS)
- Update `AbstractIntegrationTest` to set test tenant context

### Unique Constraints Update

**Decision: ALL uniqueness is tenant-scoped.** No global uniqueness except `tenant_registry.slug` itself. This applies to event codes, emails, usernames, company names — everything.

```sql
-- Before: event_code is globally unique
ALTER TABLE events DROP CONSTRAINT events_event_code_key;
-- After: event_code is unique per tenant
ALTER TABLE events ADD CONSTRAINT events_tenant_event_code_unique
    UNIQUE (tenant_id, event_code);

-- All unique constraints follow the same pattern:
-- user_profiles.username       → UNIQUE (tenant_id, username)
-- user_profiles.email          → UNIQUE (tenant_id, email)
-- partners.company_name        → UNIQUE (tenant_id, company_name)
-- newsletter_subscribers.email → UNIQUE (tenant_id, email)
-- registrations (per event)    → UNIQUE (tenant_id, event_id, email)
-- etc.
```

This allows different tenants to have overlapping identifiers. A user `jane@example.com` can exist independently in multiple tenants (each with their own Cognito pool).

### Foreign Key Considerations

Most FKs use CASCADE delete and reference parent tables within the same service. Since parent and child both get `tenant_id`, RLS on the parent automatically scopes the children. No FK changes needed — the data integrity is maintained because:
1. Parent row has tenant_id = X
2. Child row has tenant_id = X (from the same insert context)
3. RLS ensures you only see rows where tenant_id matches your context

### Risk Mitigation

| Risk | Mitigation |
|------|------------|
| RLS breaks existing queries | Feature flag to disable RLS; test with BATbern data before enabling |
| ADD COLUMN locks large tables | PG 11+ ADD COLUMN DEFAULT is metadata-only, near-instant |
| Migration fails mid-way | Each sub-step (2a, 2b, 2c, 2d) is a separate Flyway migration, independently rollback-able |
| Cross-service references break | Cross-service refs use meaningful IDs (ADR-003), not FKs — RLS doesn't affect them |
| Unique constraint changes break inserts | Deploy app code that includes tenant_id in inserts BEFORE changing constraints |

### Migration Ordering

```
1. Deploy Phase 1 (tenant registry + resolution) — tenant context available but unused
2. Deploy app code that writes tenant_id on all INSERT/UPDATE (default to BATbern UUID)
3. Run Flyway Step 2a (add columns with defaults)
4. Run Flyway Step 2b (backfill + NOT NULL)
5. Run Flyway Step 2c (RLS + indexes)
6. Deploy TenantAwareDataSource (Step 2d)
7. Update unique constraints
8. Verify: all queries return same results as before
```

Steps 2-4 can ship in a single deploy. Steps 5-7 in the next deploy. Step 8 is QA.

## Tenant Management & AWS Cognito Provisioning (Explored)

### Platform Management Service (New Microservice)

A dedicated `platform-management-service` is the **only** service with elevated AWS permissions for Cognito, ACM, and CloudFront. No other service can create pools, users, or domains.

**Responsibilities:**
- Tenant CRUD (tenant_registry)
- Cognito pool lifecycle (create/update/delete pools, clients, domains)
- Custom domain automation (ACM certificate + CloudFront alternate domain)
- Platform super-admin endpoints
- Billing integration (future)

### New Role: SUPER_ADMIN

Existing roles: `ORGANIZER`, `SPEAKER`, `PARTNER`, `ATTENDEE` — all tenant-scoped.

New role: `SUPER_ADMIN` — platform-level, cross-tenant access. Stored in a separate `platform_admins` table outside tenant RLS scope. Authenticates against a **platform Cognito pool** (not any tenant's pool).

```java
public enum Role {
    SUPER_ADMIN,   // Platform operator — cross-tenant
    ORGANIZER,     // Tenant-scoped
    SPEAKER,       // Tenant-scoped
    PARTNER,       // Tenant-scoped
    ATTENDEE       // Tenant-scoped
}
```

### IAM Policy for Platform Management Service

The ECS task role for this service requires elevated permissions. All other services remain unchanged.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "CognitoPoolLifecycle",
      "Effect": "Allow",
      "Action": [
        "cognito-idp:CreateUserPool",
        "cognito-idp:UpdateUserPool",
        "cognito-idp:DeleteUserPool",
        "cognito-idp:DescribeUserPool",
        "cognito-idp:ListUserPools",
        "cognito-idp:CreateUserPoolClient",
        "cognito-idp:UpdateUserPoolClient",
        "cognito-idp:DeleteUserPoolClient",
        "cognito-idp:CreateUserPoolDomain",
        "cognito-idp:DeleteUserPoolDomain",
        "cognito-idp:DescribeUserPoolDomain"
      ],
      "Resource": "*"
    },
    {
      "Sid": "CognitoUserAdmin",
      "Effect": "Allow",
      "Action": [
        "cognito-idp:AdminCreateUser",
        "cognito-idp:AdminSetUserPassword",
        "cognito-idp:AdminGetUser",
        "cognito-idp:AdminUpdateUserAttributes"
      ],
      "Resource": "arn:aws:cognito-idp:eu-central-1:188701360969:userpool/*"
    },
    {
      "Sid": "LambdaTriggerAttachment",
      "Effect": "Allow",
      "Action": [
        "lambda:GetFunction",
        "lambda:AddPermission",
        "lambda:RemovePermission"
      ],
      "Resource": "arn:aws:lambda:eu-central-1:188701360969:function:batbern-*"
    },
    {
      "Sid": "CustomDomainCertificates",
      "Effect": "Allow",
      "Action": [
        "acm:RequestCertificate",
        "acm:DescribeCertificate",
        "acm:DeleteCertificate",
        "acm:ListCertificates"
      ],
      "Resource": "*"
    },
    {
      "Sid": "CloudFrontDomainManagement",
      "Effect": "Allow",
      "Action": [
        "cloudfront:GetDistribution",
        "cloudfront:UpdateDistribution",
        "cloudfront:GetDistributionConfig"
      ],
      "Resource": "arn:aws:cloudfront::188701360969:distribution/*"
    },
    {
      "Sid": "KmsForCognitoTriggers",
      "Effect": "Allow",
      "Action": [
        "kms:CreateKey",
        "kms:CreateGrant",
        "kms:Encrypt",
        "kms:Decrypt",
        "kms:DescribeKey",
        "kms:EnableKeyRotation"
      ],
      "Resource": "*",
      "Condition": {
        "StringEquals": {
          "kms:ViaService": "cognito-idp.eu-central-1.amazonaws.com"
        }
      }
    }
  ]
}
```

**IAM constraints:**
- `CreateUserPool` requires `Resource: *` (Cognito limitation)
- `AdminCreateUser` scoped to `userpool/*` (all pools in account)
- Lambda permissions scoped to `batbern-*` functions only
- KMS scoped via condition to Cognito-related operations only
- CloudFront scoped to account's distributions

### Lambda Trigger Reuse Strategy

All tenant pools attach the **same** shared Lambda triggers. Triggers already receive `userPoolId` in the event payload — they just need tenant awareness:

```javascript
// Pre-Token Generation Lambda (modified for multi-tenant)
exports.handler = async (event) => {
    const userPoolId = event.userPoolId;
    const tenantId = await resolveTenantByPool(userPoolId);  // New lookup
    const roles = await fetchUserRoles(event.userName, tenantId);
    // ... existing role enrichment logic
};
```

During provisioning, the service grants each shared Lambda permission to be invoked by the new pool:
```
lambda:AddPermission → batbern-*-pre-token-generation
                      → batbern-*-post-confirmation
                      → batbern-*-pre-authentication
                      → batbern-*-post-authentication
                      → batbern-*-pre-signup
                      → batbern-*-custom-email-sender
```

### Tenant Provisioning Flow

```
SUPER_ADMIN → POST /platform/tenants
{
    "slug": "newclient",
    "displayName": "New Client Events",
    "adminEmail": "admin@newclient.com",
    "brandingConfig": { ... }
}

Execution Steps:
 1. Validate slug uniqueness in tenant_registry
 2. Generate tenant UUID
 3. Create Cognito User Pool ("batbern-tenant-{slug}")
 4. Create User Pool Client (no secret, SPA, same auth flows)
 5. Create User Pool Domain ("batbern-{slug}")
 6. Attach shared Lambda triggers + grant invoke permissions
 7. Insert tenant_registry row (pool IDs, branding, status=ACTIVE)
 8. Seed tenant defaults (copy global templates with new tenant_id)
 9. Create admin user (AdminCreateUser + sync to DB with ORGANIZER role)
10. Return tenant details + admin temporary password
```

### Custom Domain Setup (Separate Step)

```
SUPER_ADMIN → POST /platform/tenants/{id}/custom-domain
{ "domain": "newclient.com" }

 1. Request ACM certificate for newclient.com
 2. Return DNS validation CNAME records to caller
 3. Caller instructs tenant to add CNAME to their DNS
 4. Poll ACM for validation (async, webhook or polling endpoint)
 5. On validation: add newclient.com as CloudFront alternate domain
 6. Instruct tenant to point A/AAAA to CloudFront distribution
 7. Update tenant_registry with custom_domain
```

### Tenant Management UI

```
/platform/                          ← SUPER_ADMIN only
├── /platform/tenants               ← Tenant list (status, domain, user count)
├── /platform/tenants/new           ← Create tenant wizard
├── /platform/tenants/:id           ← Tenant detail
│   ├── Overview                    ← Status, usage, created date
│   ├── Branding                    ← Logo, colors, name, email config
│   ├── Domain                      ← Custom domain setup + DNS status
│   ├── Authentication              ← Cognito pool status, MFA policy
│   ├── Users                       ← Admin user management
│   ├── Billing                     ← Plan, usage, invoices (future)
│   └── Danger Zone                 ← Deactivate / delete tenant
└── /platform/settings              ← Platform-wide defaults
```

**Access control:**
- `/platform/*` requires `SUPER_ADMIN` role
- Platform admins authenticate against the **platform Cognito pool**
- Platform admin login at separate URL (e.g., `admin.yoursaas.com`)

### Platform Cognito Pool

A dedicated Cognito pool for platform operators, separate from all tenant pools:
- Created via CDK (infrastructure-managed, not runtime-provisioned)
- Contains only SUPER_ADMIN users
- Has its own Pre-Token-Generation trigger that sets `SUPER_ADMIN` role
- API Gateway distinguishes platform pool from tenant pools via the `Host` header:
  - `admin.yoursaas.com` → validate against platform pool
  - Everything else → resolve tenant → validate against tenant's pool

## Billing Architecture (Explored)

### Pricing Model: Tiered Feature + Volume

```
Free:       1 event, up to 50 attendees, basic features         — €0/month
Starter:    5 events/year, 200 attendees/event                  — €79/month
Pro:        Unlimited events, 500 attendees/event,              — €199/month
            speaker portal, partner coordination
Business:   Unlimited events, unlimited attendees,              — €499/month
            custom domain, white-label emails,
            API access, priority support
Enterprise: Custom SLA, SSO, data residency                     — Custom
Founder:    All features, no limits, no billing (BATbern only)
```

### Billing Provider: Stripe

Stripe handles subscriptions, invoicing, payment methods, webhooks, and customer portal.

**Alternative European providers considered:**
- Mollie (Netherlands) — strong in EU, simpler API, good for SEPA
- Paddle (UK) — acts as Merchant of Record, handles EU VAT automatically
- Lago (France, open-source) — self-hosted billing engine, pairs with any payment processor

See "European Billing Provider Alternatives" section below for detailed comparison.

### Metered Usage

| Metric | Source | Metering Point |
|--------|--------|---------------|
| Events created | `events` table | `EventService.createEvent()` |
| Attendees per event | `registrations` table | `RegistrationService.register()` |
| Active users (organizers) | `role_assignments` | `UserService.createUser()` |
| Email sends/month | SES integration | `EmailService.send()` |
| Storage used | S3 | `FileUploadService.upload()` |
| AI generations/month | `ai_generation_log` | `AiService.generate()` |
| Custom domain | `tenant_registry` | `TenantService.setCustomDomain()` |

### Data Model

```sql
-- Extends tenant_registry
ALTER TABLE tenant_registry ADD COLUMN stripe_customer_id VARCHAR(255);
ALTER TABLE tenant_registry ADD COLUMN stripe_subscription_id VARCHAR(255);
-- billing_plan already exists: FREE, STARTER, PRO, BUSINESS, ENTERPRISE, FOUNDER
-- billing_status: ACTIVE, PAST_DUE, SUSPENDED, CANCELLED

-- Usage tracking (aggregated monthly)
CREATE TABLE tenant_usage (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID NOT NULL REFERENCES tenant_registry(id),
    period_start             DATE NOT NULL,
    period_end               DATE NOT NULL,
    events_created           INTEGER DEFAULT 0,
    total_registrations      INTEGER DEFAULT 0,
    peak_attendees_per_event INTEGER DEFAULT 0,
    email_sends              INTEGER DEFAULT 0,
    storage_bytes            BIGINT DEFAULT 0,
    ai_generations           INTEGER DEFAULT 0,
    active_users             INTEGER DEFAULT 0,
    UNIQUE (tenant_id, period_start)
);

-- Plan definitions
CREATE TABLE billing_plans (
    id                           VARCHAR(50) PRIMARY KEY,
    display_name                 VARCHAR(100) NOT NULL,
    stripe_price_id              VARCHAR(255),
    max_events_per_year          INTEGER,       -- NULL = unlimited
    max_attendees_per_event      INTEGER,
    max_active_users             INTEGER,
    max_storage_bytes            BIGINT,
    max_email_sends_per_month    INTEGER,
    max_ai_generations_per_month INTEGER,
    features                     JSONB NOT NULL DEFAULT '{}',
    is_custom_domain_allowed     BOOLEAN DEFAULT FALSE,
    sort_order                   INTEGER DEFAULT 0
);
```

### Limit Enforcement

Limits checked at the point of action via `BillingLimitService` in shared-kernel:

```java
// shared-kernel
public record BillingLimits(
    String currentPlan,
    String billingStatus,
    Integer maxEventsPerYear,
    Integer maxAttendeesPerEvent,
    Integer maxActiveUsers,
    Long maxStorageBytes,
    Integer maxEmailSendsPerMonth,
    Integer maxAiGenerationsPerMonth,
    boolean customDomainAllowed,
    Set<String> enabledFeatures
) {}
```

Each service calls `BillingLimitService.getLimits(tenantId)` before gated operations. Cache refreshes on Stripe webhook events and every 5 minutes. Exceeded limits throw `PlanLimitExceededException` with upgrade prompt.

### Stripe Integration

**Tenant creation:** Create Stripe Customer → store `stripe_customer_id`. If paid plan: create Subscription.

**Plan changes:** Update Stripe Subscription (proration handled by Stripe) → webhook confirms → update `billing_plan`.

**Payment failure flow:**
```
invoice.payment_failed → PAST_DUE → warning banner (14-day grace)
                       → SUSPENDED → read-only (no new events/emails)
                       → CANCELLED (after 90 days) → all access blocked
```

**Webhook endpoint:** `POST /platform/webhooks/stripe` (no auth, Stripe signature verified)
- `invoice.payment_succeeded` → ACTIVE
- `invoice.payment_failed` → PAST_DUE
- `customer.subscription.updated` → sync plan
- `customer.subscription.deleted` → CANCELLED

**Usage reporting:** Daily scheduled job reports metered usage (email sends, AI generations) to Stripe for overage billing.

### BATbern as Founder Tenant

```sql
INSERT INTO billing_plans (id, display_name, features, is_custom_domain_allowed)
VALUES ('FOUNDER', 'Founder', '{"all_features": true}', TRUE);
-- All limits NULL (unlimited), no Stripe customer, no billing
```

### Billing UI

**Tenant organizers** (`/settings/billing`):
- Current plan + usage meters (progress bars toward limits)
- Upgrade → Stripe Checkout or embedded Pricing Table
- Payment method → Stripe Customer Portal (embedded)
- Invoice history → Stripe Customer Portal
- Cancel subscription

**SUPER_ADMIN** (`/platform/tenants/:id` → Billing tab):
- Override plan (bypass Stripe for special deals)
- Manual suspend / reactivate
- Usage history charts
- Revenue per tenant

### European Billing Provider Alternatives

| Provider | HQ | Approach | EU VAT | SEPA | Pros | Cons |
|----------|-----|---------|--------|------|------|------|
| **Stripe** | US (EU entity in Ireland) | Payment processor | You handle VAT (or use Stripe Tax add-on) | Yes | Best API, ecosystem, docs. De facto standard. | US company, Stripe Tax is extra cost |
| **Mollie** | Netherlands | Payment processor | You handle VAT | Yes (native) | EU-native, strong SEPA/iDEAL, simpler API, lower fees for EU cards | Weaker subscription management, less ecosystem |
| **Paddle** | UK | Merchant of Record | Paddle handles all VAT/tax | Yes | Zero VAT headache — Paddle is the legal seller, handles invoicing, tax remittance globally | Higher fees (~5%+), less control over billing flow, UK post-Brexit |
| **Lago** | France (open-source) | Billing engine (self-hosted) | You handle VAT | Pairs with any PSP | Full control, open-source, usage-based billing native, no per-transaction fees | Must self-host, pair with Stripe/Mollie for actual payments, more ops work |
| **Chargebee** | US (EU presence) | Subscription management | Chargebee Tax add-on | Yes | Strong subscription lifecycle, dunning, revenue recognition | Enterprise pricing, heavier than needed |

**Recommendation for a Swiss SaaS:**

If VAT handling is a priority (selling to EU + Swiss customers with different VAT rates), **Paddle** is compelling — it acts as the Merchant of Record, meaning Paddle sells to the customer on your behalf and handles all tax compliance. You just receive net payouts. This eliminates the entire VAT registration and filing burden across EU member states.

If you want maximum control and the best developer experience, **Stripe + Stripe Tax** is the safe choice. Swiss company, EU customers — you'd need Swiss VAT registration regardless, and Stripe Tax can calculate and report the correct rates.

If you want EU-native with strong SEPA: **Mollie** for payments + **Lago** for subscription/billing logic is an interesting combination — fully European, open-source billing engine, and Mollie handles the actual money movement.

**Decision:** Stripe selected as primary provider. Can be swapped later since the billing logic is abstracted behind `BillingService` in the platform-management-service.

## Feature Gating (Explored)

### Feature Matrix by Plan

| Feature | Free | Starter | Pro | Business | Enterprise |
|---------|------|---------|-----|----------|------------|
| Event creation | 1/yr | 5/yr | Unlimited | Unlimited | Unlimited |
| Attendees per event | 50 | 200 | 500 | Unlimited | Unlimited |
| Event state machine | Yes | Yes | Yes | Yes | Yes |
| Registration + QR codes | Yes | Yes | Yes | Yes | Yes |
| Task management | Basic | Yes | Yes | Yes | Yes |
| Speaker portal | No | No | Yes | Yes | Yes |
| Speaker invitations | No | Basic | Yes | Yes | Yes |
| Partner coordination | No | No | Yes | Yes | Yes |
| Topic voting | No | No | Yes | Yes | Yes |
| Newsletter / emails | No | 500/mo | 5000/mo | Unlimited | Unlimited |
| AI generation | No | 10/mo | 100/mo | Unlimited | Unlimited |
| Auto-publishing (CDN) | No | No | Yes | Yes | Yes |
| Presentation mode | No | Yes | Yes | Yes | Yes |
| Photo gallery | No | Yes | Yes | Yes | Yes |
| Custom domain | No | No | No | Yes | Yes |
| White-label emails | No | No | No | Yes | Yes |
| Custom email templates | No | No | Modify | Full | Full |
| API access | No | No | No | Yes | Yes |
| SSO (SAML/OIDC) | No | No | No | No | Yes |
| Data export | CSV | CSV | CSV+JSON | Full API | Full API |

### Two Types of Gates

**Boolean Feature Gates:** Does the tenant have access? Defined as `Feature` enum:
`SPEAKER_PORTAL`, `PARTNER_COORDINATION`, `TOPIC_VOTING`, `AUTO_PUBLISHING`, `NEWSLETTER`, `AI_GENERATION`, `CUSTOM_DOMAIN`, `WHITE_LABEL_EMAILS`, `CUSTOM_EMAIL_TEMPLATES`, `PRESENTATION_MODE`, `PHOTO_GALLERY`, `API_ACCESS`, `SSO`, `DATA_EXPORT_JSON`

**Quota Gates:** How much can they use? Already covered in `BillingLimits` record (billing section).

### Three-Layer Enforcement

**Layer 1 — Backend (hard enforcement):**

```java
// shared-kernel
@Component
public class FeatureGate {
    public void requireFeature(Feature feature);  // throws FeatureNotAvailableException
    public boolean hasFeature(Feature feature);    // soft check
}
```

`FeatureNotAvailableException` returns HTTP 403:
```json
{
    "error": "FEATURE_NOT_AVAILABLE",
    "feature": "SPEAKER_PORTAL",
    "currentPlan": "STARTER",
    "requiredPlan": "PRO",
    "upgradeUrl": "/settings/billing"
}
```

**Layer 2 — API Gateway (route-level, optional):**

Blocks entire route prefixes for tenants without the feature (e.g., `/api/v1/speaker-portal/` → requires `SPEAKER_PORTAL`). Performance optimization; services still enforce as defense-in-depth.

**Layer 3 — Frontend (UX gating):**

```typescript
GET /api/tenant/features → {
    "enabledFeatures": ["SPEAKER_PORTAL", ...],
    "limits": { "maxEventsPerYear": null, "emailSendsRemaining": 4823, ... },
    "currentPlan": "PRO",
    "upgradeAvailable": true
}
```

**UX principle:** Don't hide gated features — show them **locked with an upgrade prompt**. This drives conversions.

```tsx
{hasFeature('SPEAKER_PORTAL')
    ? <NavItem to="/speakers" />
    : <UpgradePromptNavItem feature="SPEAKER_PORTAL" requiredPlan="PRO" />}
```

### Plan-to-Feature Mapping

Stored in `billing_plans.features` JSONB:

```json
// PRO example
{ "enabled": ["SPEAKER_PORTAL", "PARTNER_COORDINATION", "TOPIC_VOTING",
              "AUTO_PUBLISHING", "NEWSLETTER", "AI_GENERATION", ...] }

// FOUNDER (BATbern)
{ "all_features": true }
```

### Per-Tenant Overrides

`tenant_registry.feature_flags` JSONB for custom deals, trials, partnerships:

```json
{
    "override_enabled": ["SPEAKER_PORTAL"],
    "override_disabled": [],
    "trial_features": { "AI_GENERATION": { "expires": "2026-05-01" } }
}
```

**Resolution order:**
1. `override_disabled` → blocked
2. `override_enabled` → allowed
3. `trial_features` → allowed if not expired
4. `billing_plans.features` → plan default
5. `all_features: true` → allowed

### Caching

- Caffeine local cache per service instance
- Key: `tenant_id` → `BillingLimits` (includes `enabledFeatures` set)
- TTL: 5 minutes
- Invalidation: Stripe webhook, SUPER_ADMIN override
- No external cache needed — plan changes are infrequent

## Rate Limiting & Resource Quotas (Explored)

Per-tenant API throttling at the API Gateway using Bucket4j (token bucket) with Caffeine cache.

| Plan | Requests/sec | Burst |
|------|-------------|-------|
| Free | 10 | 20 |
| Starter | 50 | 100 |
| Pro | 200 | 400 |
| Business | 500 | 1000 |
| Enterprise | Custom | Custom |

- `RateLimitFilter` runs after `TenantResolutionFilter`, keyed by `tenant_id`
- Limits loaded from `billing_plans`
- No external Redis needed at current scale; use Bucket4j PostgreSQL backend if multi-instance API Gateway requires distributed limiting
- Exceeded: `429 Too Many Requests` with `Retry-After` header

## Monitoring & Observability (Explored)

Existing stack (CloudWatch + Micrometer + MDC correlation IDs) handles multi-tenant with minimal changes:

1. **Structured logging:** `tenantId` + `tenantSlug` added to MDC in Phase 1. Every log line includes tenant. CloudWatch Insights: `| filter tenantId = "xxx"`
2. **Metrics dimension:** Add `tenant` tag to Micrometer metrics. CloudWatch dashboards filterable by tenant. Note: high-cardinality dimensions increase cost — viable up to a few hundred tenants; beyond that, evaluate Prometheus
3. **Per-tenant dashboard:** SUPER_ADMIN sees aggregated metrics. Tenant organizers see their own usage via billing usage meters
4. **Alerting:** Existing service-level CloudWatch alarms unchanged. Tenant-specific alerts only for Enterprise tier

No new infrastructure needed.

## Backup & Disaster Recovery (Explored)

**Shared-schema advantage:** Single RDS snapshot backs up all tenants.

- **Standard backup:** RDS automated snapshots (14-day retention) — covers all tenants
- **Per-tenant logical backup (Enterprise):** Nightly `pg_dump` filtered by `tenant_id` → S3. Enables single-tenant restore without affecting others
- **Point-in-time recovery:** Restore RDS snapshot to temporary instance → extract single tenant → import back. Slow but complete
- **Tenant deletion:** Soft-delete (`status = 'DELETED'`), retain 90 days, then hard-delete via scheduled job. RLS prevents accidental cross-tenant deletion
- **Tenant data export (GDPR portability):** Async job exports all tenant data as JSON/ZIP → S3 presigned download URL

## GDPR & Data Residency (Explored)

### Legal Framework

- Platform operator = Data Processor; each tenant = Data Controller
- Standard DPA template accepted on tenant signup
- Swiss FADP (nDSG) applies; broadly aligned with GDPR
- Sub-processors: AWS, Stripe — both provide standard DPAs

### Right to Deletion (Tenant Offboarding)

Tenant deletion must cascade to:
- All database rows (`DELETE WHERE tenant_id = X`)
- S3 objects (tenant-prefixed: logos, photos, materials)
- Cognito user pool (delete entire pool)
- Stripe customer (cancel + delete)
- CloudWatch logs (expire via retention policy — cannot selectively delete)
- Backups (old backups expire via retention policy — standard practice)

### Data Residency

- **Current:** All data in eu-central-1 (Frankfurt). Satisfies EU/Swiss requirements
- **Future (Enterprise):** AWS Zurich region (eu-central-2) available if Swiss-only data residency required. Would need separate RDS + deployment — only for Enterprise contracts that justify the cost
- **Decision:** Don't build multi-region now. Document eu-central-1 residency. Add as Enterprise feature on demand

### Tenant-Specific Privacy Config

Added to `branding_config` JSONB in `tenant_registry`:

```json
{
    "privacyPolicyUrl": "https://newclient.com/privacy",
    "imprintUrl": "https://newclient.com/imprint",
    "cookieConsentEnabled": true
}
```

### Audit Trail

Extend existing `AuditLoggingAspect` in shared-kernel to log all cross-tenant access (SUPER_ADMIN operations). Provides compliance audit trail.

---

## Summary

All architectural topics have been explored. This plan covers:

1. **Core Decisions:** Shared schema + RLS, Cognito pool-per-tenant, single CI/CD pipeline, runtime provisioning
2. **TenantContext Threading:** ThreadLocal holder in shared-kernel, filter chain, RLS activation, cross-service propagation
3. **Migration Roadmap:** 7 phases (~15-20 weeks), Phase 2 (database) is critical path
4. **Phase 2 Detail:** ~44 tables across 3 services, sub-step migration ordering, unique constraint updates
5. **Tenant Management:** Platform Management Service with elevated IAM, SUPER_ADMIN role, provisioning flow
6. **Billing:** Tiered pricing (Free→Enterprise), Stripe integration, limit enforcement, usage metering
7. **Feature Gating:** Three-layer enforcement (backend/gateway/frontend), per-tenant overrides, caching
8. **Rate Limiting:** Bucket4j per-tenant throttling at API Gateway
9. **Monitoring:** Existing CloudWatch + Micrometer with tenant dimension added
10. **Backup/DR:** RDS snapshots + per-tenant logical backup for Enterprise
11. **GDPR:** DPA template, deletion cascade, eu-central-1 residency, privacy config per tenant
