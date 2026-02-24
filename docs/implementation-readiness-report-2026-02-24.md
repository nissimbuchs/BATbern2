---
stepsCompleted: [step-01-document-discovery, step-02-prd-analysis, step-03-epic-coverage-validation, step-04-ux-alignment, step-05-epic-quality-review, step-06-final-assessment]
scope: epic-10 / story-10-1
status: NEEDS WORK
---

# Implementation Readiness Assessment Report

**Date:** 2026-02-24
**Project:** BATbern
**Scope:** Epic 10 — Story 10.1 (Event Management Administration Page)

---

## Step 1: Document Inventory

### Documents Selected for Assessment

| # | File | Type |
|---|------|------|
| 1 | `docs/prd/epic-10-additional-stories.md` | PRD |
| 2 | `docs/stories/archived/epic-10/10-1-event-management-administration-page.md` | Story |
| 3 | `docs/architecture/06-backend-architecture.md` | Architecture |
| 4 | `docs/architecture/05-frontend-architecture.md` | Architecture |
| 5 | `docs/architecture/03-data-architecture.md` | Architecture |
| 6 | `docs/architecture/ADR-003-meaningful-identifiers-public-apis.md` | ADR |
| 7 | `docs/architecture/ADR-006-openapi-contract-first-code-generation.md` | ADR |
| 8 | `docs/architecture/ADR-008-simplified-api-gateway-routing.md` | ADR |
| 9 | `docs/architecture/ADR-004-factor-user-fields-from-domain-entities.md` | ADR |

### Gaps Noted
- ⚠️ No UX design document for Story 10.1 (inline descriptions only)
- ⚠️ No Epic 10-specific architecture document (using project-wide arch docs as baseline)

---

## Step 3: Epic Coverage Validation

### FR Coverage Matrix

| FR | Requirement (summary) | Story 10.1 Coverage | Status |
|----|-----------------------|---------------------|--------|
| FR1 | `/organizer/admin` page with 4 MUI Tabs | AC1 + Task 2 | ✅ Covered |
| FR2 | Administration user menu item (organizer only) | AC1 + Task 2 | ✅ Covered |
| FR3 | ORGANIZER role guard | AC1 + Task 2 | ✅ Covered |
| FR4 | Tab index in `?tab=N` URL param | AC1 + Task 2 | ✅ Covered |
| FR5 | Event Types tab — extract + all edit functionality preserved | AC2 + Task 3 | ✅ Covered |
| FR6 | `/organizer/event-types` redirects to `/organizer/admin?tab=0` | AC2 + Task 2 | ✅ Covered |
| FR7 | Import Data tab — all 5 batch modals | AC3 + Task 4 | ✅ Covered |
| FR8 | Each import type in its own Card with description + button | AC3 + Task 4 | ✅ Covered |
| FR9 | Import buttons removed from original pages | AC3 + Task 4 | ✅ Covered |
| FR10 | Task Templates tab — standalone management | AC5 + Task 5 | ✅ Covered |
| FR11 | Default templates read-only | AC5 + Task 5 | ✅ Covered |
| FR12 | Custom templates: create / edit / delete | AC5 + Task 5 | ✅ Covered |
| FR13 | Create reuses `CustomTaskModal` (eventId=null) | AC5 + Task 5 | ✅ Covered |
| FR14 | Edit uses new `TaskTemplateEditModal` | AC5 + Task 5 | ✅ Covered |
| FR15 | Delete with confirm dialog | AC5 + Task 5 | ✅ Covered |
| FR16 | `email_templates` DB table — Flyway V62 | AC4 + Task 1a | ✅ Covered |
| FR17 | 22 templates seeded idempotently on startup | AC4 + Task 1c | ✅ Covered |
| FR18 | REST API `/api/v1/email-templates` (5 operations) | AC4 + Task 1e | ✅ Covered |
| FR19 | System templates not deletable (400 on attempt) | AC4 + Task 1d | ✅ Covered |
| FR20 | Email senders use DB with classpath fallback | AC4 + Task 1h | ✅ Covered |
| FR21 | Category filter in Email Templates tab | AC6 + Task 6 | ✅ Covered |
| FR22 | DE / EN language toggle (client-side) | AC6 + Task 6 | ✅ Covered |
| FR23 | Template Preview — sandboxed iframe | AC6 + Task 6 | ✅ Covered |
| FR24 | Template Edit — subject + monospace body + variable chips | AC6 + Task 6 | ✅ Covered |
| FR25 | [+ New Template] for custom email templates | AC6 + Task 6 | ✅ Covered |
| FR26 | i18n: `menu.administration` key | Task 2 | ✅ Covered |
| FR27 | i18n: `emailTemplates.*` keys | Task 6 | ✅ Covered |

**Coverage: 27/27 FRs — 100%**

---

### Gaps and ADR Compliance Issues Found

#### 🔴 GAP-1 (CRITICAL): ADR-003 Violated — UUID in Email Template API Path

**ADR-003** mandates meaningful identifiers in all public APIs. The story defines `GET/PUT/DELETE /api/v1/email-templates/{id}` using UUID.

Every entity with a meaningful alternate key must use it in the API path. The `EmailTemplate` entity has `template_key` (unique) and `locale` — the natural meaningful identifier is the `(templateKey, locale)` pair.

**Required fix**: Redesign path to use `templateKey` + `locale`:
- `GET /api/v1/email-templates/{templateKey}/{locale}` — single template
- `PUT /api/v1/email-templates/{templateKey}/{locale}` — update
- `DELETE /api/v1/email-templates/{templateKey}/{locale}` — delete custom

UUID `id` used for internal DB PK only (never in API paths). The `create` POST body already contains `key + locale` so that's fine.

**Impact**: All affected tasks: Task 1a (DB unique constraint on key+locale already correct), Task 1b (repository method `findByTemplateKeyAndLocale` already correct), Task 1e (controller path parameters need redesign), Task 1g (OpenAPI spec needs updated paths), Task 6 (frontend service URLs must change).

---

#### 🔴 GAP-2 (CRITICAL): ADR-006 Task Ordering — OpenAPI Spec Must Come First

**ADR-006** is contract-first: the OpenAPI spec MUST be written and committed BEFORE any backend implementation begins. The story places Task 1g (OpenAPI spec update) AFTER Tasks 1b–1f (entity, repository, service, controller, DTOs).

This inverts the contract-first requirement. The correct order is:
1. **Task 1g first**: Define `EmailTemplateResponse`, `CreateEmailTemplateRequest`, `UpdateEmailTemplateRequest`, and all 5 endpoint paths in `docs/api/events.openapi.yml`
2. **Regenerate types**: `npm run generate:api-types` (frontend types ready immediately)
3. **Gradle generates** backend interfaces automatically on `./gradlew build`
4. **Then Tasks 1b–1f**: implement to satisfy the generated interfaces

**Required fix**: Renumber tasks — current 1g becomes the first task. DTOs in Task 1f are then generated (not handwritten Java files) — story should remove `EmailTemplateResponse.java`, `CreateEmailTemplateRequest.java`, `UpdateEmailTemplateRequest.java` from Task 1f since they will be generated from the spec, not hand-coded.

---

#### 🔴 GAP-3 (CRITICAL): ADR-006 Pattern — Mapper Component Missing

Per ADR-006's layered architecture, every entity requires a **pure mapper component** for entity→DTO conversion (e.g., `UserResponseMapper`, `TopicMapper`). The story defines the controller and service but omits:

- `EmailTemplateMapper.java` — pure mapper, no business logic, no repository deps
- Controller must implement the **generated** `EmailTemplatesApi` interface

**Required fix**: Add to new files list:
- `controller/EmailTemplateController.java` — implements generated `EmailTemplatesApi`
- `service/mapper/EmailTemplateMapper.java` — pure mapper component

---

#### 🟡 GAP-4 (HIGH): ADR-008 — API Gateway Routing Not Addressed

The story modifies `event-management-service` with new endpoints at `/api/v1/email-templates`. The API Gateway routes all traffic to backend services — if `email-templates` routing is not configured, requests will 404 at the gateway layer.

The story has no task for updating API Gateway routing configuration. This needs verification:
- If `/api/v1/email-templates` is already covered by a wildcard route to `event-management-service`, no change needed
- If routing is endpoint-specific, a new route entry is required

**Required fix**: Add a verification subtask to Task 1e: confirm `email-templates` path is routed to event-management-service in the API Gateway config. If not, add routing entry.

---

#### 🟡 GAP-5 (MEDIUM): Newsletter Category Has No Seed Templates

FR21 defines a "Newsletter" category filter in the Email Templates tab. However, **zero** newsletter classpath templates exist to be seeded — the `email-templates/` directory contains only `speaker-*` and `registration-*` files.

The `EmailTemplateSeedService` will seed 22 templates into the SPEAKER and REGISTRATION categories. The Newsletter category will be permanently empty at first launch.

This is either:
- (a) Intentional — category scaffolded for future use (should be documented)
- (b) A gap — Newsletter category should be removed from the initial filter UI until templates exist

**Required fix**: Clarify intent in story. If intentional, document it. If not, remove Newsletter from the category filter in AC6 / Task 6.

---

#### 🟢 GAP-6 (LOW): `(template_key, locale)` DB Constraint Needs Composite UNIQUE

The current DB schema defines `template_key VARCHAR(100) NOT NULL UNIQUE`. However, the same template key legitimately exists in two locales (`speaker-invitation-de` and `speaker-invitation-en` would both have `template_key = 'speaker-invitation'`).

The UNIQUE constraint should be `UNIQUE(template_key, locale)`, not `UNIQUE(template_key)` alone.

**Required fix**: Update Task 1a migration to: `CONSTRAINT email_templates_key_locale_unique UNIQUE (template_key, locale)`

(Note: This is already partially addressed in Task 1a's index definition but the story text says `template_key VARCHAR UNIQUE` which contradicts it.)

---

### Coverage Statistics

- Total PRD FRs: 27
- FRs covered in Story 10.1: 27
- FR coverage: **100%**
- ADR compliance gaps: 3 critical, 1 high, 1 medium, 1 low
- NFR coverage: 10/12 explicitly addressed (NFR3 and NFR4 have gaps documented above)

---

## Step 5: Epic Quality Review

### Epic 10 — Structure Validation

#### User Value Focus

| Check | Result |
|-------|--------|
| Epic title user-centric? | ⚠️ **"Additional Stories"** is a container label, not a user outcome. Acceptable as an internal grouping name but not ideal. Suggested alternative: *"Event Management Configuration Hub"* |
| Epic goal describes user outcome? | ✅ "Consolidate scattered admin configuration into discoverable interfaces" — organizer benefit is clear |
| Organizers benefit from this epic independently? | ✅ Yes — single-story epic delivers complete value on its own |

#### Epic Independence

✅ Epic 10 has no forward dependencies. All prerequisites (Epics 2, 5, 6) are fully complete. Epic 10 does not require any future epic to function.

---

### Story 10.1 — Quality Assessment

#### Best Practices Compliance

| Check | Result |
|-------|--------|
| Delivers user value? | ✅ Organizers gain a consolidated admin hub |
| Independently completable? | ✅ All referenced components exist (CustomTaskModal, batch import modals, EventTypeConfigurationForm) |
| No forward dependencies? | ✅ None found |
| DB tables created when needed? | ✅ Flyway V62 created in this story only |
| ACs testable? | ✅ All 6 ACs are specific and verifiable |
| Traceability to FRs? | ✅ All 27 FRs trace to ACs |

---

### 🔴 Critical Violations

**None found.** The story is structurally sound and independently completable.

---

### 🟠 Major Issues

#### QUAL-1: Story Significantly Oversized (Risk: Scope Creep / Stalled Delivery)

Story 10.1 combines **6 distinct ACs** spanning backend infrastructure (email template API, DB, seed service, 4 updated email senders) and frontend (4 new tabs, 6 new components, 2 new hooks, 1 new service). By the project's historical standards, this is equivalent to 2–3 separate stories:

- **Slice A** (fast, frontend-only): Admin page scaffold + Event Types tab + Import Data tab + Task Templates tab (~3–5 days)
- **Slice B** (backend): Email Templates backend — DB, API, seeder, email service updates (~4–6 days)
- **Slice C** (frontend): Email Templates frontend — CRUD UI, preview, edit modal (~2–3 days)

**Risk**: If the story hits a blocker on the email templates backend (e.g., ADR compliance changes from GAP-1/GAP-2), the entire story — including the already-complete tabs 1–3 — cannot be marked done and merged.

**Recommendation**: Consider splitting into Story 10.1 (tabs 1–3, frontend-only) and Story 10.2 (email templates backend + frontend). This is not a blocking issue — the story *can* be executed as written — but delivery risk is higher.

---

#### QUAL-2: AC4 Missing Error Conditions

AC4 defines the "happy path" for email templates backend in detail but omits error scenarios that a developer needs to handle:

- What happens when an email sender cannot find the template in DB **and** classpath fallback also fails? (Silent fallback to empty string? Exception? Log + skip?)
- What happens if `EmailTemplateSeedService` fails mid-seeding on startup? (Partial state — some templates seeded, some not — is the service still functional?)
- AC4 says "classpath fallback" but does not specify whether the fallback is silent (info log) or noisy (warn/error log)

**Recommendation**: Add error condition coverage to AC4:
- Fallback behavior: "If DB lookup fails AND classpath fallback fails → log ERROR + use empty string for body (email still sent with subject only)"
- Seed failure: "If seed fails for any template, log ERROR per template, continue seeding remaining templates (partial seed is acceptable)"

---

#### QUAL-3: AC6 Missing Validation Scenario for HTML Edit

The email template edit flow (AC6, Task 6) allows organizers to save arbitrary HTML in the `htmlBody` field. There is no validation described for:

- Empty subject (should reject)
- Empty HTML body (should reject or warn)
- Malformed HTML (should accept — we don't enforce valid HTML, only sanitize for preview)

**Recommendation**: Add to AC6: "Saving an email template requires a non-empty subject and non-empty htmlBody. Empty fields show inline validation error."

---

### 🟡 Minor Concerns

#### QUAL-4: AC5 "confirm dialog" Type Unspecified

AC5 says delete uses a "confirm dialog" but doesn't specify whether this is `window.confirm()` or an MUI Dialog component. Other parts of the codebase use `window.confirm()` for simpler delete flows (noted in task board). Consistency required.

**Recommendation**: Align with existing pattern. If `window.confirm()` is used elsewhere for single-item deletes, use it here too. If MUI Dialog is preferred, specify it and ensure it matches the pattern in `EventTasksTab`.

#### QUAL-5: Epic Title

"Additional Stories" is a poor epic label — it's a backlog container, not a product capability. This causes confusion in sprint planning ("what is Epic 10 about?").

**Recommendation**: Rename to "Event Management Configuration Hub" or "Admin & Configuration Consolidation".

---

### Best Practices Compliance Checklist

- [x] Epic delivers user value
- [x] Epic can function independently
- [x] No forward dependencies
- [x] Database tables created when needed
- [x] Traceability to FRs maintained
- [⚠️] Story appropriately sized — **oversized, see QUAL-1**
- [⚠️] Acceptance criteria complete — **error conditions missing, see QUAL-2/QUAL-3**
- [⚠️] Epic title user-centric — **"Additional Stories" is weak, see QUAL-5**

---

## Step 4: UX Alignment Assessment

### UX Document Status

**Not Found** — No dedicated UX design document for Epic 10 / Story 10.1.

The story is entirely UI-focused (new organizer page, 4 tabs, 6 new modal components). UI intent is described inline in the story's AC and task prose, not in a formal wireframe or UX spec.

The adjacent Epic 5 UX flow (`docs/wireframes/archived/epic-5/5.5-content-review-task-system-ux-flow.md`) provides relevant context for Task Templates (it documents the task board and `CustomTaskModal` patterns used in Epic 5), but does not cover the new Admin page structure.

---

### UX Implied Assessment

Story 10.1 is **heavily UI-focused** — it creates one new page and 6 new modal/tab components. UX is strongly implied. The following UI elements are described in prose and carry ambiguity without wireframes:

| Component | Inline Description Quality | Risk |
|-----------|---------------------------|------|
| `EventManagementAdminPage` — tab layout | Described (4 tabs, MUI Tabs) | Low — clear |
| `ImportDataTab` — Card grid | "5 Cards, each with title, description, button" | Low — clear |
| `TaskTemplatesTab` — two-section list | Default (read-only) + Custom (CRUD) | Low — clear |
| `EmailTemplatesTab` — category filter + list | Category ToggleButtonGroup + table/list | **Medium** — column structure undefined |
| `EmailTemplatePreviewModal` | Sandboxed iframe + variables chips | Low — clear |
| `EmailTemplateEditModal` — HTML body editor | Monospace TextField, min 20 rows | **Medium** — no guidance on toolbar/actions for long HTML |

---

### Alignment Issues

#### UX ↔ PRD

No formal misalignments. The inline prose in the story is consistent with PRD scope. However two ambiguities exist:

1. **Email Templates list layout**: AC6 says "Template list with columns: key, subject, last updated" but doesn't specify column widths, how long keys/subjects are truncated, or mobile behaviour. Developer will need to decide.
2. **TaskTemplatesTab — delete confirmation**: AC5 says "confirm dialog" but doesn't specify whether this is a `window.confirm()` (used in current codebase for simpler confirmations) or an MUI Dialog. Inconsistency risk with existing patterns.

#### UX ↔ Architecture

No structural misalignments. Architecture decisions (MUI, React Query, apiClient pattern) all support the described UI. One note:

- **iframe sandboxing**: `sandbox="allow-same-origin"` is specified for email preview. This allows the preview HTML to reference inline styles but blocks scripts — correct choice. However `allow-same-origin` could theoretically allow the iframe to access parent-window cookies if the HTML contains malicious JavaScript loaded from the same origin. The safer value is `sandbox` with no attributes, or `sandbox="allow-popups"` only. Worth a security review of the sandbox attributes before implementation.

---

### Warnings

⚠️ **W1** — No UX spec for a significantly UI-heavy story. Developer will make layout/interaction decisions without designer guidance. Acceptable for internal admin tooling but noted.

⚠️ **W2** — Email template HTML editor is described as a monospace TextField. For templates with 200+ lines of HTML, this will be difficult to use. No guidance on minimum/maximum height or whether a scrollable container is expected. This is a usability gap for the primary feature of this story (email template editing).

---

## Step 2: PRD Analysis

### Functional Requirements Extracted

| # | FR | Source |
|---|-----|--------|
| FR1 | New page at `/organizer/admin` with 4 MUI Tabs: Event Types, Import Data, Task Templates, Email Templates | AC1 |
| FR2 | "Administration" menu item in organizer UserMenuDropdown (organizer role only), navigates to `/organizer/admin` | AC1 |
| FR3 | ORGANIZER role guard on the administration page | AC1 |
| FR4 | Tab index persisted in URL query param `?tab=N` | AC1 |
| FR5 | Event Types tab: extract from `EventTypeConfigurationAdmin.tsx`; all edit functionality for FULL_DAY / AFTERNOON / EVENING preserved | AC2 |
| FR6 | `/organizer/event-types` route redirects to `/organizer/admin?tab=0` | AC2 |
| FR7 | Import Data tab consolidates all 5 batch import modals: Events, Sessions, Companies, Speakers, Participants | AC3 |
| FR8 | Each import type presented in its own Card with title, description, and trigger button | AC3 |
| FR9 | Import buttons/modals removed from `EventManagementDashboard`, `CompanyManagementScreen`, `UserList` | AC3 |
| FR10 | Task Templates tab: standalone management outside of event form context | AC5 |
| FR11 | Default templates shown as read-only list (name, triggerState chip, due date offset) | AC5 |
| FR12 | Custom task templates support create / edit / delete from the admin tab | AC5 |
| FR13 | Create task template reuses `CustomTaskModal` with `eventId=null` | AC5 |
| FR14 | Edit task template uses new `TaskTemplateEditModal` | AC5 |
| FR15 | Delete task template: confirm dialog → `taskService.deleteTemplate()` | AC5 |
| FR16 | `email_templates` DB table — Flyway V62 migration | AC4 |
| FR17 | 22 existing classpath HTML templates seeded at service startup via `EmailTemplateSeedService` (`@PostConstruct`, idempotent) | AC4 |
| FR18 | REST API `/api/v1/email-templates`: GET list (optional `?category=`), GET `/{id}`, POST create, PUT `/{id}` update, DELETE `/{id}` | AC4 |
| FR19 | System templates (`isSystemTemplate=true`) cannot be deleted; returns 400 on attempt | AC4 |
| FR20 | Email senders (Invitation, Reminder, Acceptance, Registration) updated to load subject + htmlBody from DB; classpath fallback if not found | AC4 |
| FR21 | Email Templates frontend: category filter (Speaker / Registration / Newsletter / Task Reminders) | AC6 |
| FR22 | DE / EN language toggle on Email Templates tab (client-side filter) | AC6 |
| FR23 | Template Preview: subject + sandboxed iframe rendering `htmlBody` + `{{variable}}` chips | AC6 |
| FR24 | Template Edit: subject TextField + monospace HTML textarea + detected `{{variable}}` chips | AC6 |
| FR25 | [+ New Template] action for custom email templates only | AC6 |
| FR26 | i18n: `menu.administration` key in `de/common.json` + `en/common.json` | Task 2 |
| FR27 | i18n: `emailTemplates.*` keys in `de/organizer.json` + `en/organizer.json` | Task 6 |

**Total FRs: 27**

---

### Non-Functional Requirements Extracted

| # | NFR | Source |
|---|-----|--------|
| NFR1 | TDD: email template backend tests (controller integration test + seed service test) written before implementation | CLAUDE.md / Story testing strategy |
| NFR2 | ADR-006 compliance: OpenAPI spec updated in `docs/api/events.openapi.yml` BEFORE backend implementation; frontend types regenerated via `npm run generate:api-types` | ADR-006 |
| NFR3 | ADR-003 compliance: public API path identifiers must be meaningful (⚠️ UUID `/{id}` path may violate — see gap below) | ADR-003 |
| NFR4 | ADR-008 compliance: API Gateway routing updated to route `/api/v1/email-templates` to event-management-service | ADR-008 |
| NFR5 | ADR-004 compliance: `EmailTemplate` entity must not embed denormalized user/author fields | ADR-004 |
| NFR6 | Backward compatibility: existing email sending must continue to work via classpath fallback | Epic goal / Success criteria |
| NFR7 | Seed service idempotency: no duplicate templates on service restart | FR17 |
| NFR8 | Security: all `/api/v1/email-templates` endpoints restricted to ORGANIZER role | AC4 |
| NFR9 | i18n: all visible UI strings provided in both `de` and `en` locales | Task 2, Task 6 |
| NFR10 | iframe preview sandbox: `sandbox="allow-same-origin"` prevents script execution in email HTML previews | Task 6 |
| NFR11 | DB constraint: `template_key` + `locale` must be unique (composite uniqueness) | FR16 |
| NFR12 | Subject field length: max 500 characters (DB schema constraint) | FR16 |

**Total NFRs: 12**

---

### Additional Requirements / Constraints

- **No new microservices**: all backend changes land in `event-management-service`
- **Flyway sequential**: V62 is next available migration version (last is V61)
- **Seeding source**: exactly 22 classpath files in `email-templates/` — naming convention `{key}-{locale}.html`
- **Category derivation**: key-prefix mapping (speaker-* → SPEAKER, registration-* → REGISTRATION) must be explicit and tested
- **`template_key` uniqueness**: scoped per `(template_key, locale)` pair, not globally per key

---

### PRD Completeness Assessment

The PRD and story together are **well-structured** with clear scope boundaries. Requirements are traceable to ACs. However three gaps are already visible at this stage:

1. **ADR-003 potential violation**: API uses `/{id}` (UUID) in path. ADR-003 mandates meaningful identifiers in public APIs (e.g., `eventCode` not UUID). The email template identifier is the `templateKey` — this should be examined in the epic coverage step.
2. **Newsletter category has no templates**: FR21 lists Newsletter as a category but no `newsletter-*` templates exist in classpath. The category exists in the DB schema but will be empty at seed time. This is either an undocumented gap or intentional.
3. **No UX specification**: Tab layout, card design, and table column structure are described in prose only. Developer will need to infer layout decisions.

---

## Step 6: Summary and Recommendations

### Overall Readiness Status

## ⛔ NEEDS WORK

Story 10.1 has **3 critical ADR compliance violations** that must be resolved before implementation begins. The story is structurally sound, all 27 FRs are covered, and prerequisites are fully met — but the email template backend (Task 1) as currently written will not pass code review against project ADRs.

The frontend scope (Tasks 2–5) is ready for implementation immediately. The email templates backend (Task 1 + Task 6) requires story corrections first.

---

### Issue Summary Table

| # | Severity | Category | Issue | Impact if Ignored |
|---|----------|----------|-------|-------------------|
| GAP-1 | 🔴 Critical | ADR-003 | API path uses UUID `/{id}` instead of meaningful `/{templateKey}/{locale}` | Code review rejection; API contract mismatch; frontend service URLs broken |
| GAP-2 | 🔴 Critical | ADR-006 | Task ordering inverted — OpenAPI spec written after implementation instead of before | Generated interfaces missing; DTOs hand-coded instead of generated; frontend types not available early |
| GAP-3 | 🔴 Critical | ADR-006 | `EmailTemplateMapper.java` missing; controller does not implement generated `EmailTemplatesApi` interface | Non-standard service structure; will fail pattern review |
| GAP-4 | 🟡 High | ADR-008 | No verification task for API Gateway routing of `/api/v1/email-templates` | Runtime 404 at gateway; silent failure not caught in story testing |
| QUAL-1 | 🟠 Major | Story size | Story combines 3 logical slices (frontend tabs 1-3, email BE, email FE) | High WIP; any BE blocker stalls already-complete FE work |
| QUAL-2 | 🟠 Major | AC clarity | AC4 missing error conditions for seed failure + double fallback failure | Developer makes arbitrary choices; inconsistent behavior in production |
| QUAL-3 | 🟠 Major | AC clarity | AC6 missing validation requirements for subject/htmlBody | Empty templates can be saved; no inline validation |
| GAP-5 | 🟡 Medium | Scope | Newsletter category has zero seed templates | Empty category confuses users at launch |
| QUAL-4 | 🟡 Minor | UX pattern | Delete confirm dialog type unspecified (`window.confirm` vs MUI Dialog) | Inconsistency with existing patterns |
| GAP-6 | 🟢 Low | DB schema | Story prose says `template_key UNIQUE` but should be `UNIQUE(template_key, locale)` | Seed service will fail inserting second locale for same key |
| QUAL-5 | 🟢 Minor | Epic | Epic title "Additional Stories" is a container label, not a user outcome | Sprint planning confusion |
| W1 | ⚠️ Warning | UX | No UX spec for a significantly UI-heavy story | Developer makes layout decisions without designer input |
| W2 | ⚠️ Warning | UX | Email HTML editor usability unclear for 200+ line templates | Poor editing experience for primary feature |

**Total: 3 critical, 1 high, 3 major, 1 medium, 2 minor, 1 low, 2 warnings — 13 findings**

---

### Critical Issues Requiring Immediate Action

#### 1. Fix API Path Design (GAP-1 — ADR-003)

The email template API must use meaningful path identifiers, not UUIDs:

```
# Current (violates ADR-003)
GET    /api/v1/email-templates/{id}
PUT    /api/v1/email-templates/{id}
DELETE /api/v1/email-templates/{id}

# Required (ADR-003 compliant)
GET    /api/v1/email-templates/{templateKey}/{locale}
PUT    /api/v1/email-templates/{templateKey}/{locale}
DELETE /api/v1/email-templates/{templateKey}/{locale}
```

UUID `id` is a DB-internal PK only. The `GET /` list response may include `id` for internal reference but must never be used in API paths.

#### 2. Reorder Tasks for Contract-First Development (GAP-2 — ADR-006)

The story's Task 1 must be reordered so the OpenAPI spec comes first:

```
# Required order
Task 1a: OpenAPI spec (events.openapi.yml) — add EmailTemplate schemas + all 5 endpoint paths
Task 1b: npm run generate:api-types  (frontend types ready)
Task 1c: ./gradlew build (generates EmailTemplatesApi interface)
Task 1d: Flyway migration V62
Task 1e: EmailTemplate.java JPA entity + EmailTemplateRepository.java
Task 1f: EmailTemplateService.java + EmailTemplateSeedService.java
Task 1g: EmailTemplateController.java — IMPLEMENTS generated EmailTemplatesApi interface
Task 1h: Update email senders (SpeakerInvitation*, SpeakerReminder*, etc.)
# DTOs (EmailTemplateResponse, CreateEmailTemplateRequest, UpdateEmailTemplateRequest) are GENERATED
# from the spec — remove them from the "new files" list
```

#### 3. Add EmailTemplateMapper (GAP-3 — ADR-006)

Add to the backend new files list:
- `service/mapper/EmailTemplateMapper.java` — pure mapper, no business logic, converts `EmailTemplate` entity → `EmailTemplateResponse` DTO

Update controller declaration: `public class EmailTemplateController implements EmailTemplatesApi`

---

### Recommended Next Steps

1. **Update Story 10.1** to fix the 3 critical gaps above before any implementation starts. The SM should revise the story Tasks (reorder, add mapper, fix API paths) and update AC4 with error conditions and AC6 with validation requirements.

2. **Clarify Newsletter category** — decide: remove it from the initial UI (recommended, keep scope honest) or document it as an empty placeholder. Add one line to AC6 stating the decision.

3. **Add API Gateway routing verification** — add a subtask to Task 1 (or a new Task 7): verify that `/api/v1/email-templates` is covered by the existing event-management-service routing rule in the API Gateway config. If not, add the route.

4. **Consider story split** — splitting Story 10.1 (tabs 1–3, frontend-only) from Story 10.2 (email templates) reduces delivery risk. Tabs 1–3 have zero backend blockers and can be delivered immediately. This is optional but recommended.

5. **Frontend work can start now** — Tasks 2, 3, 4, and 5 (page scaffold, Event Types tab, Import Data tab, Task Templates tab) are ready for implementation without any changes. The developer may start these while the story is being updated for the email templates backend.

---

### Final Note

This assessment identified **13 issues** across **4 categories** (ADR compliance, story quality, scope gaps, UX). The 3 critical issues are all in the email templates backend scope (AC4 + Task 1) and must be resolved before that task begins. The rest of the story — approximately 60% of the work — is implementation-ready as written.

**Assessor:** Winston (BATbern Architect Agent)
**Date:** 2026-02-24
**Story:** 10.1 — Event Management Administration Page
**Verdict:** ⛔ NEEDS WORK — Fix 3 critical ADR gaps in email template backend spec before Task 1 begins
