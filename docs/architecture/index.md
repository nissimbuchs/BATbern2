# BATbern Event Management Platform Architecture

## Overview

This document outlines the complete fullstack architecture for **BATbern Event Management Platform**, including backend systems, frontend implementation, and their integration. It serves as the single source of truth for AI-driven development, ensuring consistency across the entire technology stack.

## Architecture Documentation Structure

The architecture documentation has ten numbered sections (01 to 10). Section 04 is an overview
plus seven per-domain parts, and section 06 has four companion documents (06a to 06d). Supporting
files and the ADRs are listed further down. The 2024–25 documents (03, 04,
05, parts of 06) still contain drift; #1211 tracks it.

### Core Architecture Documents

1. **[01-system-overview.md](./01-system-overview.md)** - System overview: services, clients,
   request path, mail, AI assist, technology summary
2. **[02-infrastructure-deployment.md](./02-infrastructure-deployment.md)** - CDK stacks, ECS
   sizing, API gateway tiers, CI/CD and production deploys, beta canary, inbound mail, backups
3. **[03-data-architecture.md](./03-data-architecture.md)** - Data architecture and schemas
4. **[04-api-design.md](./04-api-design.md)** - API design narrative, with per-domain parts:
   [04-api-core.md](./04-api-core.md), [04-api-event-management.md](./04-api-event-management.md),
   [04-api-speaker-coordination.md](./04-api-speaker-coordination.md),
   [04-api-partner-coordination.md](./04-api-partner-coordination.md),
   [04-api-company-management.md](./04-api-company-management.md),
   [04-api-user-management.md](./04-api-user-management.md),
   [04-api-attendee-experience.md](./04-api-attendee-experience.md).
   These are narratives; the API contract is the OpenAPI specs in `docs/api/*.openapi.yml`
5. **[05-frontend-architecture.md](./05-frontend-architecture.md)** - React component
   architecture, role-adaptive UI, state management and routing
6. **[06-backend-architecture.md](./06-backend-architecture.md)** - Service patterns,
   authentication and authorization, error handling
   - **[06a-workflow-state-machines.md](./06a-workflow-state-machines.md)** - Event and speaker
     workflow state machines
   - **[06b-user-lifecycle-sync.md](./06b-user-lifecycle-sync.md)** - User lifecycle and
     Cognito/database sync patterns
   - **[06c-testing-strategy.md](./06c-testing-strategy.md)** - Testing strategy and production
     parity
   - **[06d-notification-system.md](./06d-notification-system.md)** - Notification and mail
     delivery
7. **[07-development-standards.md](./07-development-standards.md)** - Local setup, development
   commands, TDD process
8. **[08-operations-security.md](./08-operations-security.md)** - Security, monitoring and
   alarms, operations, accessibility
9. **[09-aiml-architecture.md](./09-aiml-architecture.md)** - AI assist as built (OpenAI-backed,
   organizer-only, advisory); the 2025 ML vision is kept as a marked historical section
10. **[10-watch-app-development-patterns.md](./10-watch-app-development-patterns.md)** - Watch
    app (watchOS) development patterns

### Supporting Documents

- **[tech-stack.md](./tech-stack.md)** - Technology choices and versions
- **[coding-standards.md](./coding-standards.md)** - Coding, testing and review standards
- **[source-tree.md](./source-tree.md)** - Repository layout
- **[epic-6-speaker-onboarding-plan.md](./epic-6-speaker-onboarding-plan.md)** - Historical,
  superseded by ADR-009

## Quick Navigation

### For Developers
- **Getting Started**: Start with [07-development-standards.md](./07-development-standards.md) for setup and coding standards
- **API Reference**: The contract is the OpenAPI specs in `docs/api/*.openapi.yml` (code is generated from them, ADR-006); [04-api-design.md](./04-api-design.md) is the narrative
- **Frontend Development**: Check [05-frontend-architecture.md](./05-frontend-architecture.md) for React patterns
- **Backend Development**: Review [06-backend-architecture.md](./06-backend-architecture.md) for service patterns

### For DevOps/Infrastructure
- **Deployment**: Reference [02-infrastructure-deployment.md](./02-infrastructure-deployment.md)
- **Monitoring and alarms**: See [08-operations-security.md](./08-operations-security.md)
- **Security**: Check [08-operations-security.md](./08-operations-security.md)

### For Product/Business
- **System Overview**: Start with [01-system-overview.md](./01-system-overview.md)
- **Data Models**: Review [03-data-architecture.md](./03-data-architecture.md) for business entities

### For Accessibility/Compliance
- **Accessibility Guidelines**: See [08-operations-security.md](./08-operations-security.md)
- **Performance Standards**: Reference [08-operations-security.md](./08-operations-security.md)
- **Security Requirements**: Check [08-operations-security.md](./08-operations-security.md)

## Technology Versions

The platform maintains a **single source of truth** for all technology versions in **[versions.json](../versions.json)**. It is generated from the actual dependency files, so architecture docs cite it instead of restating version numbers that then drift.

### Version Management

- **📋 Current Versions**: See [versions.json](../versions.json) for exact technology versions
- **📝 Version Ranges**: Architecture docs use `major.x` notation (e.g., `5.x` = version 5.x+)
- **🛠️ Regenerating**: after changing a dependency, run `node scripts/update-versions.js` and commit the result alongside the dependency change
- **✅ CI Verification**: `.github/workflows/sync-versions.yml` runs `node scripts/update-versions.js --check` and fails the PR on drift

**CI verifies, it does not edit.** Nothing commits `versions.json` for you, and that is deliberate
(#991). The workflow used to run the generator and push the result onto the contributor's branch.
Because the push was authored by `github-actions[bot]`, it re-triggered every `pull_request`
workflow, and those re-runs land at `action_required` under this repository's approval policy — so
the required checks attached to a commit whose runs never started and the PR silently stalled as
`MERGEABLE` + `BLOCKED`. The commits it pushed were also usually meaningless: the generator stamps
`lastUpdated` with today's date, that date was compared as though it were a version, and the entire
diff was frequently just the date moving.

Since nothing reads `versions.json` programmatically — it is a human-readable reference cited from
these docs — its only requirement is being correct, and a check that fails is the right way to
enforce that.

### Key Technologies

For detailed technology stack information, see [tech-stack.md](./tech-stack.md).

**Current Major Versions:**
- **Frontend**: React 19.x, TypeScript 6.x, MUI 7.x, Vite 8.x
- **Backend**: Java 21 LTS, Spring Boot 4.0.x, PostgreSQL 15, Caffeine 3.x, Gradle 9.x
- **Infrastructure**: AWS CDK 2.x, Single-AZ deployment (cost-optimized)
- **Testing**: JUnit 6.x, Vitest 5.x, Playwright 1.x, Testcontainers 2.x, Bruno (API contract tests)

## Key Architectural Decisions

### Architecture Decision Records (ADRs)

The architecture is governed by the ADRs in `docs/architecture/ADR-*.md`. ADR-011 is not on
`develop` (it exists only on an unmerged branch).

- **[ADR-001](./ADR-001-invitation-based-user-registration.md)**: Invitation-Based User Registration
- **ADR-002**: Generic File Upload Service (presigned-URL pattern)
- **ADR-003**: Meaningful Identifiers in Public APIs (`username`, `eventCode`, `companyName`; no UUIDs in public APIs)
- **ADR-004**: Factor User Fields from Domain Entities (User is single source of truth for email, name, bio, photo, company)
- **[ADR-005](./ADR-005-anonymous-event-registration.md)**: Anonymous Event Registration. Stub; superseded by ADR-007, kept because code and applied migrations cite it
- **[ADR-006](./ADR-006-openapi-contract-first-code-generation.md)**: OpenAPI Contract-First Code Generation
- **ADR-007**: Unified User Profile (one User per person; no duplicate identity entities)
- **[ADR-008](./ADR-008-simplified-api-gateway-routing.md)**: Simplified API Gateway Routing. The AWS HTTP API proxies everything, CORS only at the edge, authorization only in the services (update 2026-10-27, #1147)
- **ADR-009**: Unified Speaker Workflow — single 8-state machine (`IDENTIFIED → CONTACTED → READY → INVITED → ACCEPTED → CONTENT_SUBMITTED → QUALITY_REVIEWED` + `DECLINED`), SPEAKER as a User role (no `Speaker` entity, no `speakers` table), standard Cognito with `FORCE_CHANGE_PASSWORD` on first login (no magic-link / dual-auth). Supersedes the legacy 10-state model and the magic-link auth stack. See `ADR-009-unified-speaker-workflow.md` and the implementation plan in `docs/plans/speaker-workflow-refactor.md`.
- **ADR-010**: Federated Identity via Cognito (Google SSO over OIDC) — **delivered 2026-06-04 (Epic 12)**. Cognito as OIDC broker on the custom domain `auth.batbern.ch` (backend JWT contract unchanged), existing accounts link via `AdminLinkProviderForUser` in `PreSignUp_ExternalProvider`, new Google users auto-provision as ATTENDEE via canonical JIT and pass the ToS consent gate (Pattern C). Inactive-blocking moved to the API-Gateway `is_active` gate; `companyId` removed from the token (resolved via user-api). Runtime kill-switch: `FEATURES_SSO_ENABLED`. **Deferred tail (backlog Story 12.10)**: Apple/generic OIDC + trigger-retirement cleanup to the target set `PreSignUp`, `PreTokenGeneration`, `CustomEmailSender` — PostConfirmation/PreAuthentication remain deployed until then. See `ADR-010-federated-identity-via-cognito.md` and the delivery record in `docs/plans/sso-oidc-federation.md`.
- **[ADR-012](./ADR-012-self-nomination-proposals.md)**: Pre-READY speaker proposals live in `session_proposals`, never on `speaker_pool`
- **[ADR-013](./ADR-013-rest-api-crud-conventions.md)**: REST API CRUD Conventions
- **[ADR-014](./ADR-014-backend-service-consolidation.md)**: Backend Service Consolidation. EMS + CUMS primary, partner-coordination separate, speaker-coordination and attendee-experience dormant (operational state 2026-11-04 appended)
- **[ADR-015](./ADR-015-test-data-markers-and-email-send-guards.md)**: Test-Data Markers and Email Send-Guards
- **[ADR-016](./ADR-016-service-directions-and-service-to-service-calls.md)**: Service Directions and Service-to-Service Calls. CUMS upstream of all, EMS upstream of PCS, `/internal` only, user JWT forwarded when present and never required, pull feeds instead of an event bus, profile merge as the one callback

### System Shape

- **One monorepo**: Gradle multi-project (`shared-kernel`, `api-gateway`, `services/*`) plus npm
  projects (`web-frontend`, `infrastructure`), and the watchOS app in `apps/BATbern-watch`. See
  [source-tree.md](./source-tree.md).
- **Services** (ADR-014): event-management (EMS) and company-user-management (CUMS) carry almost
  all logic; partner-coordination stays a separate service; speaker-coordination and
  attendee-experience are dormant.
- **Stack**: React 19 + TypeScript + MUI, Java 21 + Spring Boot 4.0.x, PostgreSQL 15 on RDS,
  ECS Fargate (ARM64), Cognito, SES, S3 + CloudFront, AWS CDK, GitHub Actions.

Details: [01-system-overview.md](./01-system-overview.md).

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2024-12-20 | 1.0 | Initial full-stack architecture document | Winston (Architect) |
| 2025-01-XX | 2.0 | Consolidated architecture structure (24 → 9 files) | Winston (Architect) |
| 2026-11-04 | 3.0 | Document list, ADR list, versions and system shape brought in line with the code (#1211) | Doc audit |
