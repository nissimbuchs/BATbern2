---
date: 2026-05-20
project: BATbern
scope: Story 11.G.1 — Speaker self-service company info (with organizer review)
stepsCompleted: [step-01-document-discovery, step-02-prd-analysis, step-03-epic-coverage-validation, step-04-ux-alignment, step-05-epic-quality-review, step-06-final-assessment]
status: complete
---

# Implementation Readiness Assessment Report

**Date:** 2026-05-20
**Project:** BATbern
**Scope:** Story 11.G.1 (`_bmad-output/implementation-artifacts/11-g-1-speaker-company-self-service.md`)
**Epic:** 11 — Unified Speaker Workflow Refactor (Phase G — Speaker-side company self-service)
**Assessor role:** Product Manager (requirements traceability + planning-gap detection)

---

## Step 1 — Document Discovery

### Scope rationale

Story 11.G.1 is explicitly scoped on the `feature/speaker-workflow-refactor`
branch as **Phase G — net-new product capability**, decoupled from Phases A–F
(per Epic 11 PRD §"Phase G" and the story header). The discovery below is
narrowed to the documents that govern this story; Epic 11's A–F phases and
Epics 1–10 are referenced only where they constrain 11.G.1.

### Document inventory

#### A. PRD / Epic Documents (in-scope)

**Whole documents:**
- `docs/prd/epic-11-speaker-workflow-refactor.md` (1710 lines) — canonical PRD; Phase G section at L1546–L1710, Story 11.G.1 summary + ACs at L1557–L1710.

**Sharded:** none. (Epic 11 is single-file.)

**Other epics (out of scope but listed for completeness):**
- `docs/prd/epic-1-foundation-stories.md`
- `docs/prd/epic-2-entity-crud-domain-services.md`
- `docs/prd/epic-3-historical-data-migration.md`
- `docs/prd/epic-4-public-website-content-discovery.md`
- `docs/prd/epic-5-enhanced-organizer-workflows.md`
- `docs/prd/epic-6-speaker-portal-support.md`
- `docs/prd/epic-7-attendee-experience-enhancements.md`
- `docs/prd/epic-8-partner-coordination.md`
- `docs/prd/epic-9-speaker-authentication.md` (superseded by Epic 11 per ADR-009)
- `docs/prd/epic-10-additional-stories.md`
- `docs/prd/epic-backlog-infrastructure-enhancements.md`

#### B. Story File (canonical detail)

- `_bmad-output/implementation-artifacts/11-g-1-speaker-company-self-service.md` (342 lines) — referenced from Epic 11 §"Phase G" as "Story spec:" pointer. Status: **draft** in story frontmatter; **ready-for-dev** in `_bmad-output/implementation-artifacts/sprint-status.yaml` L198 (see Issue 1 below).

#### C. Plan / Design Documents

- `docs/plans/speaker-workflow-refactor.md` (887 lines) — refactor master plan; Phase G is explicitly **out of plan scope** (story header L17 confirms: "Net-new product capability introduced on the refactor branch (not in the original `docs/plans/speaker-workflow-refactor.md` or ADR-009 scope)").
- `~/.claude/plans/i-just-want-to-jiggly-iverson.md` — referenced from story header as "the approved design plan this story implements (PM Q&A resolved 2026-05-20)". **Lives outside the repo** (user's local plans folder). This is a single-machine artifact that the dev team and reviewers will not be able to read. See Issue 2.

#### D. Architecture Documents (in-scope for 11.G.1)

**Whole documents:**
- `docs/architecture/03-data-architecture.md` — DB schema source of truth; needs `company_update_requests` addition.
- `docs/architecture/04-api-company-management.md` — CUMS API; will receive 4 new endpoints.
- `docs/architecture/04-api-event-management.md` — EMS task API; surfaces the cross-service EventTask creation.
- `docs/architecture/04-api-user-management.md` — referenced for `User.companyId = companyName` (ADR-003).
- `docs/architecture/05-frontend-architecture.md` — frontend conventions; new `/speaker-portal/company` route.
- `docs/architecture/06-backend-architecture.md` — cross-service HTTP-client pattern (new `EventTaskApiClient`).
- `docs/architecture/06b-user-lifecycle-sync.md` — Pattern 3b reference (speaker role resolution).
- `docs/architecture/ADR-001-invitation-based-user-registration.md`
- `docs/architecture/ADR-003-meaningful-identifiers-public-apis.md` — governs `companyName` as cross-service ID.
- `docs/architecture/ADR-004-factor-user-fields-from-domain-entities.md`
- `docs/architecture/ADR-006-openapi-contract-first-code-generation.md` — governs the OpenAPI spec edits.
- `docs/architecture/ADR-007-unified-user-profile.md`
- `docs/architecture/ADR-009-unified-speaker-workflow.md` — Phases A–F charter; Phase G is *not* in this ADR's scope.
- `docs/architecture/06a-workflow-state-machines.md` — not affected by 11.G.1.
- `docs/architecture/coding-standards.md`, `tech-stack.md`, `source-tree.md` — cross-cutting.

**Sharded:** none.

#### E. UX / Wireframes / Front-End Spec

- `docs/front-end-spec.md` — top-level FE spec (not story-specific).
- `docs/wireframes/` — design assets; no 11.G.1-specific wireframe present (see Issue 3).
- `docs/wireframes/sitemap.md`, `sitemap-mermaid.md` — site map will need a `/speaker-portal/company` entry.

#### F. API Contracts (OpenAPI)

- `docs/api/companies.openapi.yml` — needs 4 new paths (`POST .../update-requests`, `GET .../update-requests/current`, `GET .../update-requests`, `POST .../update-requests/{id}/decision`) plus schemas.
- `docs/api/events-api.openapi.yml` — needs (per Open Question §4) `event_code` nullability decision on `event_tasks`.

#### G. Sprint Status

- `_bmad-output/implementation-artifacts/sprint-status.yaml` — story listed at L198 with status `ready-for-dev`.

---

### Critical issues found

#### ⚠️ Issue 1 — Status divergence between story file and sprint-status.yaml

- Story file frontmatter (`11-g-1-speaker-company-self-service.md` L3): `Status: draft`
- Sprint-status.yaml L198: `11-g-1-speaker-company-self-service: ready-for-dev`

These must agree before dev pick-up. The sprint-status comment is detailed (PM-resolved decisions dated 2026-05-20), which suggests `ready-for-dev` is the intended state and the story file frontmatter is stale.

**Recommended action:** update the story file `Status:` to `ready-for-dev` (or revert sprint-status if the story is actually still in draft).

#### ⚠️ Issue 2 — Approved design plan lives outside the repo

The story header (L23) names `~/.claude/plans/i-just-want-to-jiggly-iverson.md` as the
approved design plan it implements. This file is not in the repo, so reviewers and
dev cannot access it. The PM-resolved decisions (2026-05-20) on logo cleanup,
submission-history, EventTask.event_code nullability, etc. were captured in that plan.

**Recommended action:** copy the approved plan into the repo (e.g.
`docs/plans/speaker-self-service-company-info.md`) or fold its decisions verbatim
into the story file's "Open Questions / Resolved Decisions" section so the binding
PM rulings travel with the story.

#### ⚠️ Issue 3 — No wireframe / UX mockup for the speaker page or organizer review panel

`docs/wireframes/` has no file for `/speaker-portal/company` or the side-by-side
**Current vs Proposed** organizer review panel. The story describes the UX in prose
(public-portal chrome, top-nav placement after "My Sessions", read-only banner with
expandable diff, badge + filter chip) but does not link a visual.

**Recommended action:** confirm whether a wireframe is required for this story.
Given that the speaker page reuses `CompanyForm.tsx` in `userRole='speaker'` mode
and the organizer panel mounts inside the existing `CompanyDetailView`, this may
be sufficiently low-novelty to skip — but the diff panel is genuinely new UI.

#### ℹ️ Issue 4 — Project-level PRD-enhanced.md vs PRD folder (out of scope but flagged)

`docs/prd-enhanced.md` exists alongside the `docs/prd/` folder. For Epic 11 / Story
11.G.1 this is non-blocking (Epic 11 is unambiguously in `docs/prd/epic-11-...md`),
but the wider planning corpus has a duplicate-format ambiguity that should be
resolved separately.

---

### Missing documents (warnings)

None blocking for 11.G.1. The story file is detailed (342 lines, 12+ AC, file paths
named) and references existing surfaces (`CompanyForm.tsx`, `CompanyDetailView.tsx`,
`PublicNavigation.tsx`, `event_tasks` table) that are all in the codebase.

---

### Document inventory — summary table

| Type | Path | Status | Notes |
|---|---|---|---|
| Epic | `docs/prd/epic-11-speaker-workflow-refactor.md` | ✅ Present | Phase G §L1546+ |
| Story | `_bmad-output/implementation-artifacts/11-g-1-speaker-company-self-service.md` | ⚠️ Status mismatch | `draft` in file vs `ready-for-dev` in sprint-status |
| Plan | `~/.claude/plans/i-just-want-to-jiggly-iverson.md` | ⚠️ Outside repo | Approved design plan not committed |
| Refactor plan | `docs/plans/speaker-workflow-refactor.md` | ✅ Present | Phase G explicitly out of scope |
| Architecture | `docs/architecture/*.md` (multiple) | ✅ Present | Doc updates needed (see Steps 2–5) |
| OpenAPI | `docs/api/companies.openapi.yml` | ✅ Present | Needs 4 new paths |
| UX | `docs/wireframes/` | ⚠️ No 11.G.1 mockup | Review-panel UI is genuinely new |
| Sprint status | `_bmad-output/implementation-artifacts/sprint-status.yaml` | ✅ Present | L198 |

---

## Step 2 — PRD Analysis

### Approach

Story 11.G.1 sits inside Epic 11's broader requirements inventory. The PRD
defines formal **FR1–FR13**, **NFR1–NFR10**, **AR1–AR43**, **UX-DR1–UX-DR23**
for Phases A–F (the speaker-workflow refactor proper). Phase G then
introduces new **G-prefixed** requirements that are referenced narratively
but **not formally added to the requirements inventory**. The analysis below
extracts (a) the inherited requirements that still apply to 11.G.1 because
of the surfaces it touches, and (b) the G-prefixed new requirements that
the story itself introduces.

### Functional Requirements

#### Net-new for Story 11.G.1 (G-prefixed)

| ID | Requirement (extracted from story L21 and Epic L1551, L1566–L1570) |
|---|---|
| **FR-G1** | Speaker self-service company-info editing with organizer review. A speaker can update `displayName`, `website`, and `logo` for their own company via a public-portal-styled page. The update is held as a PENDING `company_update_request`; an organizer must APPROVE or REJECT before the change is applied to the canonical `companies` row. |

#### Inherited / cross-cutting from Epic 11 (apply because of the speaker-portal + role surfaces 11.G.1 touches)

| ID | Source | How it applies to 11.G.1 |
|---|---|---|
| **FR8** | Epic 11 L56 | Speaker-portal endpoints require Cognito Bearer + SPEAKER role; `permitAll()` not used. The new `POST /companies/{name}/update-requests` honours this with `@PreAuthorize("hasRole('SPEAKER') and @companyAuth.isSelf(#name)")`. |
| **FR12** | Epic 11 L74 | `User.companyId` stores `companyName` (ADR-003). 11.G.1's `companyAuth.isSelf` helper relies on this. |
| (Pattern 3b) | CLAUDE.md / 06b | JWT `custom:role` may be empty in local-dev; DB-fallback resolves SPEAKER role. 11.G.1's `@PreAuthorize` must inherit the shared-kernel converter. |

### Non-Functional Requirements

#### Net-new for Story 11.G.1

**None formally defined.** The story file at L1 names no NFR-G. This is a gap (see Issue 5 below).

#### Inherited from Epic 11 (apply to 11.G.1)

| ID | Source | How it applies to 11.G.1 |
|---|---|---|
| **NFR4** | Epic 11 L100 | Audit-trail integrity. 11.G.1's `submitted_by_username` / `reviewed_by_username` / `decision_note` columns satisfy this — but only if `@TransactionalEventListener(AFTER_COMMIT)` and AC10 verifications hold. |
| **NFR6** | Epic 11 L112 | Test parity — Testcontainers-backed integration tests for submit / get-current / list / decide. Story AC10 explicitly requires this. |
| **NFR7** | Epic 11 L116 | Doc-drift policy — Phase A doc-alignment style. 11.G.1 introduces new endpoints (`docs/api/companies.openapi.yml`), new schema (`docs/architecture/03-data-architecture.md`), new cross-service pattern (`docs/architecture/06-backend-architecture.md`), new UI route (`docs/wireframes/sitemap.md`). The story should list these doc updates as acceptance items. (See coverage check in Step 3.) |
| **NFR10** | Epic 11 L127 + CLAUDE.md | i18n: all 10 locales for UI keys; email templates `de + en` only. Story AC11 explicitly enforces this. |

### Architecture Requirements (ARs)

#### Net-new for Story 11.G.1 (G-prefixed)

| ID | Requirement (extracted from story L21 and Epic L1567–L1570) |
|---|---|
| **AR-G1** | New `company_update_requests` table in CUMS + 4 new REST endpoints: `POST /companies/{name}/update-requests`, `GET /companies/{name}/update-requests/current`, `GET /companies/update-requests` (status filter), `POST /companies/{name}/update-requests/{id}/decision`. Unique partial index `(company_name) WHERE status='PENDING'` enforces single-open-request invariant at DB. |
| **AR-G2** | Cross-service EventTask creation from CUMS — new `EventTaskApiClient` in CUMS calls EMS to insert an unassigned `event_tasks` row on submit; marks it `completed` on decision. EMS failure logged but does NOT roll back the proposal. |

#### Inherited from Epic 11 (apply to 11.G.1)

| ID | Source | How it applies to 11.G.1 |
|---|---|---|
| **ADR-003** | `04-api-design` / project-context L82–L91 | Cross-service IDs: `companyName` is the cross-service ID, not UUID. `event_tasks.event_code` is meaningful, not UUID. Story AC5 honours this. |
| **ADR-004** | `factor-user-fields` | Domain entities don't duplicate user fields. The `company_update_requests` table stores `submitted_by_username` / `reviewed_by_username` (not user UUIDs and not user-profile copies). Story AC1 honours this. |
| **ADR-006** | `openapi-contract-first` | Contract-first OpenAPI edits before code. The story DOES require `docs/api/companies.openapi.yml` edits (AC2), but the PRD section does not explicitly say "spec lands before code" — implicit only. |
| **ADR-007** | `unified-user-profile` | Speaker page must read profile from `/users/me`, not duplicate user fields. Indirectly relevant (the page edits *company* not user). |

### UX Design Requirements

#### Net-new for Story 11.G.1 (G-prefixed)

| ID | Requirement (extracted from story L21 and Epic L1568–L1570) |
|---|---|
| **UX-DR-G1** | (a) New speaker page `/speaker-portal/company` rendered in **public-portal** chrome (not organizer admin chrome) — reuses `CompanyForm.tsx` in `userRole='speaker'` mode, restricted to 3 fields. (b) Top-nav link added in `PublicNavigation.tsx` after "My Sessions" — explicitly NOT inside a dropdown (PM direction). (c) Pending banner blocks resubmit, with "View what I submitted" expandable diff. (d) Organizer-side `[● Update pending]` row chip + `[Pending updates (N)]` filter chip on `/organizer/companies`. (e) Side-by-side **Current vs Proposed** review panel inside `CompanyDetailView` with `[Approve]` / `[Reject]` buttons (Reject requires `decisionNote` textarea). (f) Deep-link `/organizer/companies/{name}?reviewRequest={id}` from EventTask description auto-scrolls panel; toast if already reviewed. |

#### Inherited from Epic 11 (apply to 11.G.1)

| ID | Source | How it applies to 11.G.1 |
|---|---|---|
| **UX-DR17** | Epic 11 L284 | Speaker portal pages use standard Cognito session — no `?token=` or `?jwt=`. The new `/speaker-portal/company` route inherits `SpeakerRoute` guard (story AC7). |
| **UX-DR22** | Epic 11 L301 | Translations for new UI copy in all 10 locales. Story AC11 enforces this. |

### Additional Requirements & Constraints (extracted from story L19–L30)

- **Phase independence:** Phase G is independent of Phases A–F and can land in any order relative to them.
- **Reuse constraints:** Story explicitly requires reusing existing surfaces — `CompanyForm.tsx`, `CompanyDetailView.tsx`, `event_tasks` table, presigned-S3 logo-upload flow, `companyAuth.isSelf`-style helper. No new tables or services beyond those named.
- **Out-of-scope decisions (PM-resolved 2026-05-20, captured in story header):**
  - No logo S3 cleanup on reject (file-upload state machine reaps).
  - No submission history on speaker page.
  - `event_tasks.event_code` nullability resolved at impl time (Open Question §4).
  - No organizer-side email on submission (badge + EventTask are the surfaces — PM resolved §2).
- **Pattern reference:** Story 5.5 speaker review-queue + `action: APPROVE|REJECT + feedback` shape — explicitly mirrored.

### Counts

| Class | Count for 11.G.1 (new) | Count inherited that apply | Total in play |
|---|---|---|---|
| FRs | 1 (FR-G1) | 2 (FR8, FR12) | 3 |
| NFRs | 0 | 4 (NFR4, NFR6, NFR7, NFR10) | 4 |
| ARs | 2 (AR-G1, AR-G2) | 4 ADRs | 6 |
| UX-DRs | 1 (UX-DR-G1, multi-part) | 2 (UX-DR17, UX-DR22) | 3 |

### PRD Completeness Assessment

The PRD is **substantively complete** for 11.G.1 — every behaviour, every endpoint shape, every UI surface, and every cross-service interaction is described in either the Phase G summary (Epic L1546–L1710) or the dedicated story file (12+ ACs, 342 lines, file paths named down to the JPA entity class). However, structural gaps weaken traceability:

#### ⚠️ Issue 5 — G-prefixed requirements never added to the formal inventory

`docs/prd/epic-11-speaker-workflow-refactor.md` defines FR1–FR13, NFR1–NFR10, AR1–AR43, UX-DR1–UX-DR23 as the project's canonical requirements inventory (L23–L312). Phase G adds **FR-G1, AR-G1, AR-G2, UX-DR-G1** but only mentions them narratively (L1551, L1566). They are NOT:
- listed in the §"Functional Requirements" code block (L26–L82),
- listed in the §"Non-Functional Requirements" code block (L88–L132),
- listed in the §"Additional Requirements (Architecture)" code block (L134–L228),
- listed in the §"UX Design Requirements" code block (L232–L312),
- present in the FR Coverage Map / NFR Coverage / AR Coverage / UX-DR Coverage blocks at the bottom of the epic (L1681–L1700) — these all stop at FR13/NFR10/AR43/UX-DR23.

A future reader auditing requirement coverage will not find FR-G1 unless they happen to read Phase G end-to-end. **Recommended:** add the four G-prefixed reqs to the four inventory blocks at the top of the epic, and extend the coverage maps at the bottom to list them under 11.G.1.

#### ⚠️ Issue 6 — No formal NFR-G defined

11.G.1 has implicit non-functional requirements that are not captured anywhere as NFRs:
- **Performance:** pending-check query must not regress `/organizer/companies` listing latency (the `[● Update pending]` chip needs a per-row join or pre-fetch).
- **Concurrency:** the "first decision wins, others get a toast" behaviour (story AC text under deep-link AC) is a real concurrency rule but has no NFR backing — only an AC.
- **Audit-trail integrity for decisions:** inherited NFR4 covers state transitions; the story should explicitly state that `reviewed_by_username` + `reviewed_at` + `decision_note` constitute the audit trail and are immutable after the row is `APPROVED|REJECTED`.

These can be captured as story-local NFR-G1 / NFR-G2 / NFR-G3 or as explicit inheritance of NFR4. **Recommended:** add a one-paragraph "Inherited NFRs" note to the story file enumerating which Epic-11 NFRs apply (NFR4, NFR6, NFR7, NFR10) and what story-local NFRs (if any) are net-new.

#### ⚠️ Issue 7 — Story header claim "None of the existing FR/AR/NFR apply directly" is too sweeping

Story L21 says: *"None of the existing FR/AR/NFR in `docs/prd/epic-11-speaker-workflow-refactor.md` apply directly — this is net-new scope."*

That is **not accurate** for FR8 (Cognito Bearer + SPEAKER role on speaker-portal endpoints — the new submit endpoint must inherit this), FR12 (`User.companyId = companyName` — `companyAuth.isSelf` literally reads this), NFR4 (audit trail), NFR6 (test parity), NFR7 (doc-alignment policy), NFR10 (i18n), UX-DR17 (no `?token=` on speaker-portal), UX-DR22 (10-locale parity). The story DOES honour all these in its ACs, but the header copy under-sells the inheritance.

**Recommended:** rewrite story L21 to read something like: *"Net-new G-prefixed requirements (FR-G1, AR-G1, AR-G2, UX-DR-G1) plus inherited FR8/FR12 and NFR4/NFR6/NFR7/NFR10 and UX-DR17/UX-DR22."*

---

## Step 3 — Epic/Story Coverage Validation

### Approach

For Story 11.G.1 there is only **one story** covering the four G-prefixed requirements (FR-G1, AR-G1, AR-G2, UX-DR-G1). The relevant traceability check is whether the **12 ACs** in the story file actually cover all aspects of those G-prefixed requirements plus the inherited Epic-11 requirements that apply. Below is the requirement-to-AC matrix; gaps are flagged.

### Requirement-to-AC coverage matrix

| Req | Aspect | Story AC(s) | Status |
|---|---|---|---|
| **FR-G1** | Speaker can submit 3-field update for own company | AC2 (POST + `@PreAuthorize` + `@companyAuth.isSelf`) | ✅ |
| FR-G1 | Pending state held until organizer review | AC1 (status enum + unique-PENDING index), AC2 (status=PENDING insert) | ✅ |
| FR-G1 | Organizer can APPROVE — copies fields atomically | AC3 (transactional copy + status=APPROVED + reviewedBy/At) | ✅ |
| FR-G1 | Organizer can REJECT — companies row untouched | AC3 (REJECT branch + decisionNote required) | ✅ |
| FR-G1 | One-open-request invariant per company | AC1 (unique partial index), AC2 (409 PENDING_UPDATE_EXISTS mapping) | ✅ |
| **AR-G1** | New `company_update_requests` table | AC1 (full DDL provided) | ✅ |
| AR-G1 | Java entity + repository | AC1 (`CompanyUpdateProposal` entity + repository named) | ✅ |
| AR-G1 | 4 REST endpoints | AC2 (submit), AC3 (decide), AC4 (current + list) | ✅ |
| AR-G1 | OpenAPI spec edits | AC2/AC3/AC4 each reference `docs/api/companies.openapi.yml` | ✅ |
| **AR-G2** | New `EventTaskApiClient` in CUMS | AC5 names file path + JWT-propagation pattern | ✅ |
| AR-G2 | Create unassigned EventTask on submit | AC5 (payload + `assignedOrganizerUsername=null`) | ⚠️ **see Issue 8** (field-name + event_id mismatch with actual schema) |
| AR-G2 | Mark task `completed` on decision | AC6 (`markCompleted(taskId)` + `AFTER_COMMIT` listener) | ✅ |
| AR-G2 | EMS failure does not roll back proposal | AC5 (best-effort note) | ✅ |
| **UX-DR-G1** | Speaker `/speaker-portal/company` page in public-portal chrome | AC7 (route + page wrapped in SpeakerRoute, public-portal chrome explicit) | ✅ |
| UX-DR-G1 | Top-nav link in `PublicNavigation.tsx` after "My Sessions", not in dropdown | AC7 (code snippet shown) | ✅ |
| UX-DR-G1 | Reuses `CompanyForm.tsx` in `userRole='speaker'` mode (3 fields only) | AC7 + Dev Notes | ⚠️ **see Issue 9** (field-hiding behavior is partially implemented; story acknowledges but is vague on what's "already there") |
| UX-DR-G1 | Pending banner blocks resubmit | AC8 (read-only + banner + expandable diff) | ✅ |
| UX-DR-G1 | Organizer `[● Update pending]` row chip + `[Pending updates (N)]` filter chip | AC9 | ✅ |
| UX-DR-G1 | Side-by-side **Current vs Proposed** review panel inside `CompanyDetailView` | AC9 (review panel + Approve/Reject) | ✅ |
| UX-DR-G1 | Deep-link `?reviewRequest={id}` auto-scrolls | AC9 (deep-link + toast on already-reviewed) | ✅ |
| **NFR4** (audit) | `submitted_by`/`reviewed_by`/`reviewed_at`/`decision_note` immutable | AC1 (columns), AC3 (stamping) | ⚠️ **see Issue 10** (immutability not asserted in any test) |
| **NFR6** (test parity) | Testcontainers-backed integration tests | AC12 (extends `AbstractIntegrationTest`) | ✅ |
| **NFR7** (doc-drift) | Architecture/data-model docs updated in the same commit | Dev Notes mention OpenAPI spec; **NO mention of `03-data-architecture.md` or `06-backend-architecture.md` updates** | ❌ **see Issue 11** |
| **NFR10** (i18n) | All 10 locales for UI keys; emails DE+EN only | AC10 (emails DE+EN), AC11 (10-locale UI keys) | ✅ |
| **FR8** (Cognito Bearer + SPEAKER role on speaker-portal endpoints) | New submit endpoint inherits Cognito Bearer + `hasRole('SPEAKER')` | AC2 (`@PreAuthorize("hasRole('SPEAKER') and @companyAuth.isSelf(#name)")`) | ✅ |
| **FR12** (`User.companyId = companyName`) | `companyAuth.isSelf` reads it | AC2 | ✅ |
| **UX-DR17** (no `?token=` on speaker portal) | New `/speaker-portal/company` route uses standard Cognito session | AC7 (wrapped in `<SpeakerRoute>`, no token mention) | ✅ |
| **UX-DR22** (10-locale parity) | New keys land in all 10 locales same commit | AC11 | ✅ |

### Coverage gaps & issues

#### ❌ Issue 8 — AC5 payload doesn't match the actual `event_tasks` schema

This is the single biggest blocker discovered in this readiness check. The story repeatedly refers to fields on `event_tasks` that **do not exist** in the codebase. I verified the actual schema at `services/event-management-service/src/main/resources/db/migration/V22__Add_task_system.sql` and the JPA entity at `services/event-management-service/src/main/java/ch/batbern/events/domain/EventTask.java`.

**Discrepancies:**

| Story claim (AC5, sprint-status entry, story header L18) | Actual schema |
|---|---|
| `eventCode: null` (column allegedly `event_code`, allegedly nullable per Open Q #1) | Column is **`event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE`** — there is no `event_code` column, the column is UUID, and it is NOT NULL with a hard FK to `events.id`. |
| Story Open Q #1: "*existing `event_tasks` table may currently require an `event_code` value*" | Phrasing is wrong — there is no `event_code` column at all. The required column is `event_id`. |
| Task payload: `name`, `description`, `dueAt` | Actual columns: `task_name` (NOT NULL), `notes` (TEXT), `due_date`. No `description` column. |
| Story claim L18 ("event_tasks table support for unassigned tasks (`assigned_organizer_username = NULL` — confirmed nullable per Story 11.D.1 review)") | ✅ Correct — `assigned_organizer_username VARCHAR(100)` is nullable. |
| (Not in story but required) | `trigger_state VARCHAR(50) NOT NULL` — story task payload doesn't say what value to use. Existing values include `'topic_selection'`, `'agenda_published'`, `'event_live'`, etc. A new value like `'company_info_update'` would have to be introduced. |

**Implications:**
1. Either the migration must DROP the `event_id NOT NULL` constraint AND DROP the `REFERENCES events(id)` FK (much bigger schema change than the story acknowledges) — this would compromise integrity for the 100% of `event_tasks` rows that ARE event-scoped.
2. Or the migration must ADD a new `event_code VARCHAR` column with semantics "if NULL, task is cross-event" (and the existing `event_id` column either becomes nullable too, or stays NOT NULL and a sentinel "global" event row is seeded — the story explicitly says "preferred over seeding a `GLOBAL` sentinel event row").
3. Or `event_tasks` is the wrong table for this — perhaps a separate `organizer_tasks` table or a separate "global tasks" surface is needed.
4. The Java entity (`UUID eventId`) and `EventTaskRepository`/`EventTaskService`/`EventTaskController` all assume an event scope. The Kanban / Task Board surfaces that the story relies on for "All Tasks" tab are built on these event-scoped contracts. Cross-event tasks may not actually render correctly on the Board.

**Required before "ready for dev":**
- PM + architect decision on which of options 1/2/3 above is correct.
- Story header + AC5 + AC6 + Dev Notes rewritten to match the chosen approach.
- Open Question #1 reframed using the correct column name (`event_id`) and stated as a binding constraint, not "dev confirms at impl time" — the migration path materially differs.
- If new `trigger_state` value is needed: list it explicitly in AC5 and confirm with the EMS owner.

#### ⚠️ Issue 9 — `CompanyForm.tsx` `userRole='speaker'` mode is only partially implemented

I verified: `CompanyForm.tsx` L97 declares `userRole?: 'organizer' | 'speaker'` and L125 uses it in a single read-only check (`(userRole === 'speaker' && initialData?.name === userCompanyId)`). **There is no existing field-visibility branching** for hiding `industry`, `description`, `swissUID` when `userRole === 'speaker'`. The Dev Notes in AC7 (L325) acknowledge this with "Audit it during implementation — the field-visibility logic may already partially exist."

The risk: the story's "reuses `CompanyForm.tsx` in `userRole='speaker'` mode" framing suggests "small extension". The actual change is "add 3 field-visibility branches + tests + verify no regression for the existing organizer use of the same component."

**Required before dev:** add a sub-bullet to AC7 stating *"the field-visibility branches for hiding `industry`, `description`, `swissUID` when `userRole === 'speaker'` do NOT yet exist in `CompanyForm.tsx` and are added by this story"* — so dev doesn't think it's a no-op.

#### ⚠️ Issue 10 — Audit-trail immutability is implied but not tested

NFR4 (inherited) demands audit-trail integrity. The story's AC10 covers stamping `reviewed_by_username` / `reviewed_at` / `decision_note` on the decision; AC12 tests cover "approve copies fields atomically; reject leaves company untouched." Neither AC nor test asserts that a subsequent decide-call on the SAME proposal cannot overwrite those fields (or that a second decide attempt is rejected with 409 — which AC3 says, but no test verifies it).

**Recommended:** add a sub-bullet to AC12 backend integration tests: *"second `POST /decision` on the same proposal (already APPROVED or REJECTED) returns `409` and does not mutate `reviewed_by_username`, `reviewed_at`, or `decision_note`."*

#### ❌ Issue 11 — Story does not list architecture-doc updates required by NFR7

NFR7 (Epic 11 L116, plus CLAUDE.md "Doc Drift Prevention") requires architecture and PRD docs to land in the same commit as the code. The story:
- ✅ Mentions OpenAPI spec edit (`docs/api/companies.openapi.yml`).
- ✅ Mentions PRD entry (Epic 11 `Phase G — Speaker-side company self-service`).
- ❌ Does NOT mention adding `company_update_requests` to `docs/architecture/03-data-architecture.md`.
- ❌ Does NOT mention adding the `EventTaskApiClient` cross-service-client pattern to `docs/architecture/06-backend-architecture.md` (this is the first CUMS→EMS HTTP client; the existing patterns are CUMS-being-called-from EMS).
- ❌ Does NOT mention updating `docs/wireframes/sitemap.md` for the new `/speaker-portal/company` route.
- ❌ Does NOT mention checking `.github/doc-drift-mappings.yml` and adding mappings for the new endpoints.

**Recommended:** add **AC13 — Doc updates** with explicit bullets for each of the four doc files above. Without this, the doc-drift auditor will flag this commit and the story will need a follow-up PR.

### Coverage statistics

| Class | Total in play | Covered by ACs | Partial / Gap | Not covered |
|---|---|---|---|---|
| FRs | 3 (FR-G1 + FR8/FR12 inherited) | 3 | 0 | 0 |
| NFRs | 4 (NFR4/6/7/10 inherited) | 3 | 1 (NFR4 lacks test) | 1 (NFR7 doc updates) |
| ARs | 2 (AR-G1, AR-G2) | 1 (AR-G1 fully) | 1 (AR-G2 schema mismatch) | 0 |
| UX-DRs | 3 (UX-DR-G1 + UX-DR17/22 inherited) | 3 | 0 (1 implementation-detail risk) | 0 |
| **Total** | **12** | **10** | **2** | **1** |

**Coverage percentage:** 10/12 = 83 % full coverage; **2 partial** (one of them a hard blocker — AR-G2 schema mismatch); **1 missing** (NFR7 doc updates).

---

## Step 4 — UX Alignment

### UX document status

- `docs/front-end-spec.md` (top-level FE spec) — **does not mention** `/speaker-portal/company`, `SpeakerCompanyEditPage`, the side-by-side review panel, or the `[Pending updates (N)]` filter chip. Cross-grep returned zero hits for 11.G.1-specific terms.
- `docs/wireframes/sitemap.md` — last updated 2026-05-15. Has a top-of-file warning that "screen-level details below each Epic 6 / Epic 8 section may diverge from actual implementation." Does not list `/speaker-portal/company`.
- `docs/wireframes/sitemap-mermaid.md` — no mention of speaker-portal routes for this feature.
- `docs/wireframes/` — no 11.G.1-specific image/HTML mockup found.
- `docs/user-guide/` — no doc for the speaker company self-service flow (would be a Phase G follow-up).

**UX document found?** Partially — there is a project-wide spec + sitemap, but **no story-specific UX artifact** for 11.G.1.

### UX ↔ PRD alignment

The prose in the Epic 11 §"Phase G" section (L1626–L1672) describes the UX in enough detail that an experienced dev can implement it without a wireframe **for the speaker-portal page** — it's a 3-field form with a pending banner, visually consistent with `SpeakerDashboardPage` / `ProfileUpdatePage`, both of which exist in production. Same for the organizer row chip + filter chip — these are straightforward additions to existing `CompanyManagementScreen.tsx`.

The **side-by-side Current vs Proposed diff panel** inside `CompanyDetailView` is the one piece that is *genuinely new visual design* with no precedent in the codebase or wireframes. The story describes it in prose but does not specify:
- vertical or horizontal split?
- how is the logo diff rendered? (two images side-by-side, or one above the other, or a click-to-compare overlay?)
- where exactly does the diff panel mount in `CompanyDetailView` — above the existing "Edit company" form, below it, in a tab?
- does the diff panel show *changed fields only* (with unchanged fields collapsed) or *all 3 editable fields* always?
- on mobile, does the side-by-side become stacked? what's the breakpoint?

### UX ↔ Architecture alignment

| UX expectation | Architectural support | Status |
|---|---|---|
| Pending banner — TanStack Query refetch on decision | `companyApi.getCurrentCompanyUpdateProposal` returns 204 when none; `staleTime: 0` on the proposal query | ✅ Standard pattern, no architecture gap |
| Public-portal chrome (not organizer admin) | `SpeakerDashboardPage` + `ProfileUpdatePage` already use it; `<SpeakerRoute>` guards | ✅ Pattern exists |
| Top-nav link added to `PublicNavigation.tsx` after "My Sessions" | Component verified at `web-frontend/src/components/public/Navigation/PublicNavigation.tsx`; `isSpeaker` check is the standard pattern | ✅ |
| Filter chip + row chip on `/organizer/companies` | `CompanyManagementScreen.tsx` verified to exist; existing filter-chip pattern in screen | ✅ |
| Side-by-side review panel inside `CompanyDetailView` | `CompanyDetailView.tsx` verified to exist; no existing diff-rendering primitive in the codebase | ⚠️ New visual — see below |
| Deep-link auto-scroll | `useSearchParams` + `useRef` + `scrollIntoView` — standard React pattern | ✅ |
| 10-locale i18n | `web-frontend/public/locales/*/companies.json` + new `speaker-company.json` namespace; story matches established patterns from Story 10.9 | ✅ |

### Warnings

#### ⚠️ Issue 12 — No wireframe / mockup for the Current vs. Proposed review panel

This is genuinely new visual design with no precedent. The story's prose leaves the visual specifics open (split orientation, mobile behaviour, logo-diff rendering, mount point, all-fields-vs-changed-only). Without a wireframe or visual mockup, two developers implementing this will produce two different things. The MVP risk is low (the data shown is simple), but the *review burden* will be high — PM/UX will likely send patches after the first implementation lands.

**Recommended (cheap path):** add a simple ASCII or rendered-table mockup to AC9, pinning the 5 open visual choices above. Even a 10-line ASCII sketch in the story file resolves this.

**Alternative (slower path):** request a wireframe from UX. Given Phase G is net-new and the feature is small, the ASCII-in-story path is probably sufficient.

#### ⚠️ Issue 13 — `docs/wireframes/sitemap.md` doesn't list `/speaker-portal/company`

The sitemap is the project's canonical inventory of user-facing routes. Adding `/speaker-portal/company` to the speaker-portal section is a small but real NFR7 obligation (doc-drift policy). The story does not mention this.

**Recommended:** fold this into the proposed AC13 in Issue 11 — sitemap.md gets the new route alongside the architecture-doc updates.

### Alignment issues

No hard misalignments between UX prose and the architecture beyond Issue 12 (open visual choices) and Issue 13 (sitemap entry). The story's reuse strategy — `CompanyForm.tsx` in speaker mode, public-portal chrome from `SpeakerDashboardPage`, filter-chip pattern from existing screens, deep-link via search params — is all grounded in real code that I verified exists.

---

## Step 5 — Epic / Story Quality Review

### A. User-value focus

| Question | Story 11.G.1 verdict |
|---|---|
| Is the story user-centric? | ✅ User story format ("As a speaker… I want… So that…") |
| Does the story deliver user-visible outcome? | ✅ Speaker can self-update logo/displayName/website without emailing organizer; organizer gets a sign-off gate. |
| Is "with organizer review" a value-add or a tech-debt detour? | ✅ Value-add — the review is explicitly part of the requirement, not bolted on as scaffolding. |
| Could this be a technical-only milestone in disguise? | ❌ No — clear user value on both speaker and organizer sides. |

### B. Story independence

| Dependency claim | Verification |
|---|---|
| Story L17: "Phase G is independent of Phases A–F and can land in any order relative to them." | ⚠️ **Mostly true but not fully** — see Issue 14 below. |
| Story L20: depends only on existing organizer company-management surface | ✅ Verified — `CompanyForm.tsx`, `CompanyDetailView.tsx`, `CompanyManagementScreen.tsx`, `PUT /api/v1/companies/{name}`, `POST /api/v1/logos/presigned-url` all exist in production. |
| Story L21: depends on existing `event_tasks` table + EMS task controller | ✅ `EventTaskController.java`, `EventTaskService.java`, `EventTaskRepository.java` exist. ⚠️ But schema is `event_id NOT NULL` not `event_code` — see Issue 8 above. |
| Story L22: depends on `User.companyId` storing `companyName` (ADR-003) | ✅ Existing ADR-003 contract. |
| Story L23: depends on JWT `custom:role` containing `SPEAKER` (or Pattern 3b DB-fallback) | ✅ Pattern 3b landed in 11.E.7 per CLAUDE.md. |

#### ⚠️ Issue 14 — Hidden dependency on 11.E.3 (Cognito-secured speaker portal)

Story 11.G.1 claims independence from Phases A–F. In practice:
- AC2 uses `@PreAuthorize("hasRole('SPEAKER') and @companyAuth.isSelf(#name)")` — this **requires** the Pattern 3b DB-fallback for SPEAKER role resolution in local-dev, which landed in **Story 11.E.7**.
- AC7 wraps the new route in `<SpeakerRoute>` — which requires the `<SpeakerRoute>` component from `@components/auth/ProtectedRoute`, which landed in **Story 11.E.3** as part of the Cognito-secured speaker portal cherry-pick. Prior to 11.E.3, speaker routes used magic-link auth.
- AC10 sends emails using the email-template-locale chain that landed in **Story 11.E.2** (Cognito-flow email template UPDATE for staging/prod-seeded rows).

So Phase G **is independent of Phases B–D** (state-machine consolidation, entity simplification, organizer kanban) but **transitively depends on Phase E** (Cognito provisioning + multi-role nav + DB-fallback for roles). On `feature/speaker-workflow-refactor`, all of 11.E.1, 11.E.2, 11.E.3, 11.E.7 are done — so this is **not a blocker** in practice, only a doc-accuracy issue.

**Recommended:** rewrite story L17 to read: *"Phase G is independent of Phases A–D and F; it transitively depends on Phase E (Cognito provisioning, Pattern 3b role fallback, multi-role nav, email template overhaul). On `feature/speaker-workflow-refactor`, all of these are already done, so Phase G can land in any order relative to A–D and F."*

### C. AC quality (BDD format, testability, specificity)

| AC | Given/When/Then format | Testable | Specific | Notes |
|---|---|---|---|---|
| AC1 — table + entity + repo | ✅ "Given … When … Then …" | ✅ Migration + JPA test | ✅ Full DDL shown | Strong AC. |
| AC2 — submit endpoint | ✅ Multiple G/W/T blocks | ✅ Integration test covers full HTTP cycle | ✅ OpenAPI shape + controller annotation shown | Strong AC. Validation states "at least one of the three fields must be present" — testable via `@AssertTrue` or service check; story says "validated by a `@AssertTrue` on the DTO or a service-layer check" — slight ambiguity (which one?). Minor concern. |
| AC3 — decide endpoint | ✅ G/W/T format | ✅ Each branch (approve/reject) testable | ✅ Service-method behaviour enumerated step-by-step | Strong AC. |
| AC4 — read endpoints | ✅ G/W/T format | ✅ Coverage by `CompanyUpdateProposalControllerIntegrationTest` | ✅ `@PreAuthorize` documented | Strong AC. |
| AC5 — EventTask creation | ✅ G/W/T format | ⚠️ **Test exists (WireMock) but the payload field names don't match the actual schema** — Issue 8 | ❌ Schema-mismatched (see Issue 8) | **Hard blocker** until reframed. |
| AC6 — task completion | ✅ G/W/T format | ✅ Best-effort behaviour tested in same WireMock test | ✅ AFTER_COMMIT pattern named | Strong AC. |
| AC7 — speaker portal page | ✅ G/W/T format | ✅ Vitest covers render | ⚠️ Field-hiding-via-userRole change to `CompanyForm.tsx` understated — Issue 9 | Generally strong; one detail risk. |
| AC8 — pending banner | ✅ G/W/T format | ✅ Vitest covers state transitions | ✅ Diff content described | Strong AC. |
| AC9 — organizer badge + chip + review panel | ✅ G/W/T format | ✅ Vitest + Playwright | ⚠️ Visual layout of diff panel under-specified — Issue 12 | Strong on behaviour, weak on visual layout. |
| AC10 — email | ✅ G/W/T format | ✅ Locale fallback testable | ✅ Subject lines + body summary given | Strong AC. |
| AC11 — i18n | ✅ G/W/T format | ✅ Build-time key check | ✅ All 10 locales + key list given | Strong AC. |
| AC12 — tests | n/a — meta-AC enumerating test files | ✅ | ✅ Five test layers named | Strong meta-AC; **missing** the audit-immutability check called out in Issue 10. |

### D. Story sizing

| Dimension | Estimate | Risk |
|---|---|---|
| Backend changes | 1 migration + 1 entity + 1 repo + 1 service + 1 controller + 1 OpenAPI spec edit + 1 new cross-service HTTP client + 1 `companyAuth` helper + 2 email templates × 2 (DE+EN) × 2 (html+txt) + AFTER_COMMIT listener | Medium |
| Frontend changes | 1 new page + 1 new route + nav-link in `PublicNavigation.tsx` + 2 modified components (`CompanyManagementScreen`, `CompanyDetailView`) + `CompanyForm.tsx` speaker-mode field hiding + 1 new namespace × 10 locales + key additions to 2 existing namespaces × 10 locales | Medium-large |
| Test changes | 1 unit + 1 integration + 1 WireMock + 3 Vitest + 2 Playwright + 3 Bruno | Medium |
| Doc changes (per NFR7 — not in current ACs) | 1 OpenAPI + 1 PRD §Phase G refresh + 1 architecture-data + 1 architecture-backend + 1 sitemap | Medium |

**Total: large story** — at the upper end of single-PR scope. If team velocity is constrained, consider splitting into 11.G.1.A (backend + OpenAPI + emails) and 11.G.1.B (frontend + i18n + E2E). **Recommendation:** keep as one story if the dev has > 2 days continuous focus; split if not.

### E. Forward dependencies

No forward dependencies found. Story 11.G.1 does not reference any not-yet-written story or any planned-but-future epic.

### F. Database-creation timing

The story correctly creates `company_update_requests` in the same Flyway migration that this story owns — not in a previous "setup" story. ✅ Per BMad best practice.

### G. Open questions tracking

The story file has 3 numbered Open Questions at L334. PM-resolution status:
- **Q1** (`event_tasks.event_code` nullability) — story says "Dev confirms the current schema at implementation start" → **misframed**, see Issue 8. Needs PM-resolution BEFORE dev pickup, not at impl-time.
- **Q2** (logo cleanup on reject) — explicitly marked "resolved 2026-05-20". ✅
- **Q3** (multiple speakers per company) — marked as "Recommend leaving the bare 409 for the MVP and revisiting if a user reports confusion." This is a recommendation, not a resolution. Should be marked Resolved or Deferred with PM sign-off.

#### ⚠️ Issue 15 — Open Question Q1 needs PM resolution before dev pickup

Per Issue 8, Q1 cannot be resolved by dev at impl-time because the column doesn't exist with the claimed name and the implications affect AC5 + AC6 wording and the EMS schema. PM/architect must make the call (option 1: nullable event_id + drop FK; option 2: add new event_code column; option 3: use a different table; option 4: seed sentinel "global" event row).

### Quality summary by severity

#### 🔴 Critical violations

- **None at the structural level** — story is well-formed, user-centric, has clear value, is correctly sized, has no forward dependencies, follows BDD AC pattern.

#### 🟠 Major issues (from Steps 3–5)

- **Issue 8** (Step 3) — AR-G2 schema mismatch (event_tasks columns). **Blocks dev pickup.**
- **Issue 11** (Step 3) — NFR7 doc updates not in ACs.
- **Issue 14** (this step) — Hidden Phase E dependency mis-described as independent.
- **Issue 15** (this step) — Open Question Q1 needs PM resolution, not impl-time decision.

#### 🟡 Minor concerns

- **Issue 1** (Step 1) — story-status drift (`draft` vs `ready-for-dev`).
- **Issue 2** (Step 1) — design plan lives outside repo.
- **Issue 5** (Step 2) — G-prefixed reqs missing from formal inventory.
- **Issue 6** (Step 2) — no formal NFR-G defined.
- **Issue 7** (Step 2) — "None of the existing FR/AR/NFR apply" framing too sweeping.
- **Issue 9** (Step 3) — `CompanyForm.tsx` speaker-mode change understated.
- **Issue 10** (Step 3) — Audit-trail immutability not tested.
- **Issue 12** (Step 4) — Diff panel visual under-specified.
- **Issue 13** (Step 4) — Sitemap entry missing from doc updates.
- **Q3 status** — recommendation not a resolution.

---

## Step 6 — Final Assessment

### Overall readiness status

🟠 **NEEDS WORK** — story is **mostly ready** but has **one hard blocker** (Issue 8 — `event_tasks` schema mismatch) and several minor doc/scoping issues that should be addressed before "ready-for-dev" status can be honestly claimed.

The story file itself is high-quality: detailed (342 lines), grounded in real codebase files (every component path verified to exist), uses BDD AC format, sized correctly, no forward dependencies, real user value, PM-resolved decisions captured. The 12 ACs cover the four G-prefixed requirements substantively. The blocker is a single architectural premise — that `event_tasks.event_code` exists and is the right hook — that does not hold against the actual schema.

### Issues by severity (15 total)

| # | Severity | Title | Step |
|---|---|---|---|
| 8 | 🔴 Hard blocker | AR-G2: `event_tasks` schema mismatch (`event_code` doesn't exist; `event_id NOT NULL` with hard FK) | Step 3 |
| 11 | 🟠 Major | NFR7 doc-update obligations not in any AC | Step 3 |
| 14 | 🟠 Major | Story claims Phase A–F independence but transitively depends on Phase E | Step 5 |
| 15 | 🟠 Major | Open Question Q1 needs PM resolution before dev pickup, not impl-time | Step 5 |
| 1 | 🟡 Minor | Status drift: story file `draft` vs sprint-status `ready-for-dev` | Step 1 |
| 2 | 🟡 Minor | Approved design plan lives outside the repo (`~/.claude/plans/...`) | Step 1 |
| 3 | 🟡 Minor | No wireframe for new speaker page or review panel | Step 1 |
| 4 | ℹ️ Note | `docs/prd-enhanced.md` vs `docs/prd/` (out of scope) | Step 1 |
| 5 | 🟡 Minor | G-prefixed reqs (FR-G1 / AR-G1 / AR-G2 / UX-DR-G1) missing from formal inventory in Epic 11 PRD | Step 2 |
| 6 | 🟡 Minor | No formal NFR-G defined for story-local non-functionals | Step 2 |
| 7 | 🟡 Minor | Story L21 "None of the existing FR/AR/NFR apply" framing is too sweeping | Step 2 |
| 9 | 🟡 Minor | `CompanyForm.tsx` `userRole='speaker'` field-hiding change is understated | Step 3 |
| 10 | 🟡 Minor | Audit-trail immutability (NFR4) not tested | Step 3 |
| 12 | 🟡 Minor | "Current vs Proposed" diff panel visual layout under-specified | Step 4 |
| 13 | 🟡 Minor | `docs/wireframes/sitemap.md` doesn't list `/speaker-portal/company` | Step 4 |

### Critical issues requiring immediate action

#### 1. Resolve Issue 8 — `event_tasks` schema mismatch — BEFORE dev pickup

**Symptom:** Story AC5 + AC6 + Open Q #1 describe `event_tasks.event_code` (a column that does not exist) and `eventCode=null` (a value that cannot be set). The real schema is `event_tasks.event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE` plus required `trigger_state VARCHAR(50) NOT NULL`, `task_name VARCHAR(255) NOT NULL`, `notes TEXT` (not `name`/`description`/`dueAt`/`eventCode`).

**Recommended action:**
- PM + EMS-owner architectural decision among:
  - **(a) drop `event_id NOT NULL` and FK** — allows null, simple migration, but loses referential integrity on the entire `event_tasks` table for 100% of rows that ARE event-scoped. Probably unacceptable.
  - **(b) add a new `event_code VARCHAR(12)` column** — keep `event_id` NOT NULL by seeding a `GLOBAL` sentinel event row OR make `event_id` nullable too. Either way: bigger change than the story claims.
  - **(c) introduce a new `organizer_tasks` table** dedicated to cross-event company-tasks. Cleanest separation but adds a new table.
  - **(d) reuse `event_tasks` by seeding a sentinel "BATglobal" event row** — pragmatic but breaks the semantic that an EventTask is event-scoped. UI surfaces (Task Board) may not render this well.
- Rewrite story header L18, AC5, AC6, and Open Q #1 to match the chosen option. Use the actual column names (`task_name`, `notes`, `due_date`, `trigger_state`).
- If a new `trigger_state` value is needed (e.g. `'company_info_update'`), enumerate it explicitly in AC5.

#### 2. Resolve Issue 14 — Phase E dependency

**Symptom:** Story claims independence from Phases A–F. Truth: depends on Phase E (Pattern 3b, `<SpeakerRoute>`, Cognito-flow emails).

**Recommended action:** rewrite story L17 to: *"Phase G is independent of Phases A–D and F; transitively depends on Phase E (which is already done on `feature/speaker-workflow-refactor`)."*

#### 3. Resolve Issue 11 — doc-update obligations in ACs

**Symptom:** NFR7 requires `03-data-architecture.md`, `06-backend-architecture.md`, `docs/wireframes/sitemap.md`, and `.github/doc-drift-mappings.yml` updates in the same commit. None of these are in current ACs.

**Recommended action:** add **AC13 — Doc updates** with explicit bullets for each file, mirroring how stories 11.A.1 / 11.E.5 handled doc-drift.

#### 4. Resolve Issue 1 — status reconciliation

**Symptom:** Story file `Status: draft`, sprint-status `ready-for-dev`. Honest answer is "not yet ready-for-dev" because of Issue 8.

**Recommended action:** flip sprint-status back to `draft` until Issues 8/11/14/15 are resolved, then flip BOTH files to `ready-for-dev` in the same commit.

### Recommended next steps (in order)

1. **PM/architect huddle** on Issue 8 — pick one of options (a)/(b)/(c)/(d). Document decision in the story file and in this readiness report.
2. **Rewrite story L17–L24** to (i) match the chosen Issue-8 option, (ii) honestly state the Phase-E inheritance per Issue 14, (iii) tighten the "inherited requirements" framing per Issue 7.
3. **Add AC13 — Doc updates** per Issue 11 + Issue 13.
4. **Add to AC12** the second-decide-attempt 409 test per Issue 10.
5. **Add a 5-line clarification block to AC9** pinning the diff-panel visual choices per Issue 12.
6. **Add a 1-line clarification to AC7 Dev Notes** that `CompanyForm.tsx` field-hiding for speaker mode is net-new per Issue 9.
7. **Copy `~/.claude/plans/i-just-want-to-jiggly-iverson.md`** into the repo at `docs/plans/speaker-self-service-company-info.md` per Issue 2.
8. **Update Epic 11 PRD** to add FR-G1 / AR-G1 / AR-G2 / UX-DR-G1 to the formal inventory blocks and the coverage maps per Issue 5.
9. **Flip story status to `ready-for-dev`** in both the story file and sprint-status.yaml — same commit as items 1–8.

### Final note

This assessment identified **15 issues** across **6 categories** (document discovery, requirements inventory, AC coverage, UX alignment, story quality, traceability). **1 is a hard blocker** for dev pickup (Issue 8 — schema mismatch). **3 are major** (Issues 11, 14, 15) and should be resolved before pickup. **The remaining 11 are minor** and can be addressed during implementation or in the same prep commit that fixes the blockers.

After applying the 9 next steps above, Story 11.G.1 will be genuinely **READY** for dev pickup. The story content itself is high-quality work — the issues are mostly traceability and one fact-check failure that's exactly the class of error this readiness check exists to catch.

---

**Assessment complete:** 2026-05-20 · Assessor: Claude (Opus 4.7, 1M ctx) · Workflow: `bmad-check-implementation-readiness`

---

## Step 7 — Resolution Applied (post-assessment)

PM decisions captured 2026-05-20 by Nissim, all 15 issues fixed in-place. New verdict: **READY for dev pickup.**

### PM decisions

| # | Question | Resolution |
|---|---|---|
| Q1 | event_tasks cross-event nullability | Allow null on the **actual** column (`event_id`, not `event_code`). New EMS Flyway migration `V<n>__make_event_tasks_event_id_nullable.sql` drops `NOT NULL`; FK is preserved (standard nullable-FK behaviour). `EventTask` JPA `nullable=false` flag flipped. Repository queries audited for null safety. |
| Q2 | Logo cleanup on reject | No cleanup. File-upload state machine reaps unassociated objects in a future story. Settled. |
| Q3 | Multi-speaker 409 message | Bare 409 (`PENDING_UPDATE_EXISTS`) is sufficient. Don't leak the colleague's identity. |
| Q4 | Speaker UI placement (NEW DIRECTION) | Drop the new `/speaker-portal/company` page. Add a **"My Company" section inline on the existing `/speaker-portal/profile` page** (`ProfileUpdatePage.tsx`), below the existing "Basic Info" card. No new route, no new top-nav link, no reuse of `CompanyForm.tsx` (render the three fields inline). Organizer side unchanged. |

### Files modified in this resolution pass

1. **`_bmad-output/implementation-artifacts/11-g-1-speaker-company-self-service.md`** (story file):
   - Status: `draft` → `ready-for-dev`
   - User story: rewritten to reflect profile-page placement.
   - Header: Phase E inheritance now stated honestly; inherited requirements (FR8/FR12/NFR4/6/7/10, UX-DR17/22) enumerated.
   - **AC5:** schema mismatch fixed — explicit table of actual EMS column names (`task_name`, `notes`, `trigger_state='company_info_update'` sentinel, `event_id=null`), plus the EMS migration to drop `NOT NULL`.
   - **AC6:** `description` → `notes` (actual EMS column name).
   - **AC7:** rewritten — inline "My Company" section on `ProfileUpdatePage.tsx`; no new page/route/nav-link; `CompanyForm.tsx` left untouched.
   - **AC8:** rewritten — pending state in the same section, amber-bordered banner, expandable diff (changed fields only, side-by-side logo thumbnails).
   - **AC9:** five diff-panel visual choices pinned (mount above Edit form; horizontal at ≥ md, stacks below; changed-fields-only; logo as two equal thumbnails; MUI ButtonGroup + Dialog for Reject).
   - **AC10:** rejection email link updated to `/speaker-portal/profile#my-company`.
   - **AC11:** i18n keys land under existing `common.json` (`speakerPortal.profile.companySection.*` + `company.reviewPanel.*` / `company.filters.*` / `company.chip.*`) — no new namespace.
   - **AC12:** added second-decide-409 immutability test (NFR-G1); added EMS `EventTaskRepository` null-safety tests; renamed Playwright spec to `e2e/speaker/profile-edit-company.spec.ts`.
   - **AC13 (NEW):** explicit doc-update list — `03-data-architecture.md`, `06-backend-architecture.md`, `06a-workflow-state-machines.md`, `docs/wireframes/sitemap.md`, `.github/doc-drift-mappings.yml`, Epic 11 PRD inventory + coverage maps.
   - Dev Notes: rewritten — drops `CompanyForm.tsx` reuse, documents EMS schema change + sentinel `trigger_state`, audit-trail immutability rationale.
   - Open Questions: all 4 marked **RESOLVED** with PM rationale; off-repo design-plan reference removed.
2. **`docs/prd/epic-11-speaker-workflow-refactor.md`** (Epic 11 PRD):
   - **FR-G1** added to the formal Functional Requirements block (after FR13).
   - **NFR-G1** added (audit-trail immutability + first-decision-wins) after NFR10.
   - **AR-G1** + **AR-G2** added as a new "Phase G" block after AR43.
   - **UX-DR-G1** added as a new "Phase G" block after UX-DR23 with seven sub-points (a–g).
   - **Phase G section** rewritten: independence framing corrected (transitive Phase E dependency stated); inherited requirements listed; net-new G-prefixed requirements listed.
   - **AC summary** in Phase G rewritten: EMS migration acknowledged; profile-page placement replaces speaker-page wording; review-panel visual pinpoints folded in; NFR-G1 test in the test pyramid; Playwright spec renamed.
   - **Coverage Maps** at L1783+ extended to map FR-G1, NFR-G1, AR-G1 + AR-G2, UX-DR-G1 → 11.G.1.
3. **`_bmad-output/implementation-artifacts/sprint-status.yaml`**:
   - `last_updated` bumped to 2026-05-20 with a one-line summary of the readiness sweep.
   - 11.G.1 status comment fully rewritten to reflect Q1–Q4 PM resolutions, profile-page placement, the new EMS migration on `event_id`, AC13 doc updates, NFR-G1, and 13 (not 12) ACs.
4. **`docs/implementation-readiness-report-2026-05-20.md`** (this file):
   - This Step 7 appendix added so future readers see the issues + the resolutions.

### Files NOT modified (deliberate)

- **`docs/api/companies.openapi.yml`** — left for the dev pass per ADR-006 contract-first.
- **`docs/api/events-api.openapi.yml`** — left for the dev pass (any DTO change for EMS create-task lands during implementation).
- **`docs/architecture/03-data-architecture.md` / `06-backend-architecture.md` / `06a-workflow-state-machines.md`** — left for the dev pass per AC13 (doc updates ride in the same commit as the code).
- **`docs/wireframes/sitemap.md` / `.github/doc-drift-mappings.yml`** — left for the dev pass per AC13.
- **`docs/plans/`** — the off-repo `~/.claude/plans/i-just-want-to-jiggly-iverson.md` was NOT copied into the repo. Per Q4, the design changed materially (no new page), so the original plan no longer represents the chosen direction. The story file's PM-resolved-decisions block is the binding contract going forward.
- **`web-frontend/src/components/shared/Company/CompanyForm.tsx`** — explicitly untouched. The Q4 direction renders three fields inline on the profile page; no `CompanyForm.tsx` change is needed.

### Issues, post-resolution

| # | Title | Status |
|---|---|---|
| 1 | Status drift `draft` vs `ready-for-dev` | ✅ Resolved (story file flipped) |
| 2 | Off-repo design plan | ✅ Resolved (reference removed; PM decisions now live in story Open Questions block) |
| 3 | No wireframe for review panel | ✅ Resolved (AC9 visual choices pinned; speaker side no longer needs one because it lives on an existing page) |
| 4 | `prd-enhanced.md` vs `prd/` ambiguity | ℹ️ Out of scope, deferred to project-level cleanup |
| 5 | G-prefixed reqs missing from formal inventory | ✅ Resolved (Epic 11 PRD updated) |
| 6 | No formal NFR-G | ✅ Resolved (NFR-G1 added to Epic 11 PRD + Coverage Map) |
| 7 | "None of FR/AR/NFR apply" framing | ✅ Resolved (story header now lists inherited FR8/FR12/NFR4/6/7/10/UX-DR17/22) |
| 8 | AR-G2 schema mismatch (event_tasks `event_code` ≠ reality) | ✅ Resolved (AC5 + Q1 + story Dev Notes + Epic 11 AC summary all corrected to `event_id` + actual columns) |
| 9 | `CompanyForm.tsx` speaker-mode understated | ✅ Resolved (Q4 direction makes this moot — `CompanyForm.tsx` untouched) |
| 10 | Audit-trail immutability not tested | ✅ Resolved (AC12 test added; NFR-G1 codified) |
| 11 | NFR7 doc updates not in ACs | ✅ Resolved (AC13 added with 7 explicit doc-update items) |
| 12 | Diff-panel visual under-specified | ✅ Resolved (AC9 pins all 5 open visual choices) |
| 13 | Sitemap missing new route | ✅ Resolved (AC13 includes sitemap update; no new route to add — existing `/speaker-portal/profile` content grows) |
| 14 | Phase E dependency framing | ✅ Resolved (Phase / Dependencies block in story + Epic 11 Phase G overview) |
| 15 | Q1 needs PM resolution | ✅ Resolved (Q1 marked RESOLVED with PM rationale + migration concretely specified) |

### Final verdict

🟢 **READY for dev pickup.** All 15 issues resolved. Story file (now 13 ACs) is consistent with the actual codebase schema, names real components and migrations, has explicit doc-update obligations in AC13, and carries PM-binding decisions on all 4 Open Questions. Epic 11 PRD's formal requirements inventory now lists FR-G1 / NFR-G1 / AR-G1 / AR-G2 / UX-DR-G1, and the Coverage Maps trace each to Story 11.G.1.

Next concrete step: dev pickup via `/bmad-dev-story` for `_bmad-output/implementation-artifacts/11-g-1-speaker-company-self-service.md`.

---

**Resolution complete:** 2026-05-20 · Applied by Claude (Opus 4.7, 1M ctx) on PM (Nissim) instruction.



