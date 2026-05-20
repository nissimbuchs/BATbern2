# Story 11.G.1: Speaker self-service company info (with organizer review)

Status: ready-for-dev

<!-- Validation is optional — run validate-create-story for quality check before dev-story. -->

## Story

**As a** speaker whose company logo, display name, and website appear on the public BATbern site beside my session,
**I want** to update those fields from my existing speaker profile page, with an organizer reviewing my change before it goes live,
**So that** I don't have to email an organizer to keep my company's public footprint current, and the organizer team still gets a sign-off gate.

## Phase / Dependencies / Requirements Covered

- **Phase:** G — Speaker-side company self-service. **Net-new product capability** introduced on the refactor branch (not in the original `docs/plans/speaker-workflow-refactor.md` or ADR-009 scope). Phase G is independent of Phases A–D and F. It transitively depends on **Phase E** (Cognito provisioning, Pattern 3b DB-fallback for `custom:role`, `<SpeakerRoute>` guard, multi-role nav, Cognito-flow email templates) — all of which are already done on `feature/speaker-workflow-refactor`, so Phase G can land in any order relative to A–D and F.
- **Dependencies:**
  1. Existing CUMS company-management surface — `Company` entity, `PUT /api/v1/companies/{name}`, presigned-logo upload (`POST /api/v1/logos/presigned-url`), and the organizer-facing `/organizer/companies` UI (`CompanyManagementScreen.tsx`, `CompanyDetailView.tsx`, `CompanyForm.tsx`). **All already in production.**
  2. Existing speaker profile page `web-frontend/src/pages/speaker-portal/ProfileUpdatePage.tsx` at route `/speaker-portal/profile` — uses `useTranslation('common')` with `speakerPortal.profile.*` keys; reads `user` via `GET /api/v1/users/me?include=company`. The company-edit section is added inside this page (Q4 resolution).
  3. Existing `event_tasks` table + EMS task controller (`EventTaskController`) — supports unassigned tasks (`assigned_organizer_username = NULL`) already; surfaced on the organizer Task Board's "All Tasks" tab. **Schema note:** the cross-event-FK column is `event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE`, NOT `event_code` — Q1 resolution requires an EMS migration to drop `NOT NULL` so cross-event tasks can be persisted (see AC5).
  4. Speaker → company linkage: `User.companyId` stores `companyName` (ADR-003), exposed to the frontend as `user.companyName`. Read via `getUserProfile(['company'])` in `ProfileUpdatePage`.
  5. JWT `custom:role` containing `SPEAKER` (or Pattern 3b DB-fallback per Story 11.E.7) — used by `@PreAuthorize` and the frontend `<SpeakerRoute>` guard from `@components/auth/ProtectedRoute` (landed in Story 11.E.3).
- **Requirements covered:**
  - **Net-new (G-prefixed):** **FR-G1** (speaker self-service company-info editing with organizer review), **AR-G1** (new `company_update_requests` table + 4 REST endpoints), **AR-G2** (cross-service `EventTaskApiClient` from CUMS calling EMS, plus EMS migration to allow cross-event tasks), **UX-DR-G1** (company-edit section on the existing speaker profile page + organizer review panel + badge + filter chip on `/organizer/companies`), **NFR-G1** (audit-trail immutability on decided proposals + first-decision-wins concurrency rule).
  - **Inherited from Epic 11 (apply because of the surfaces this story touches):** **FR8** (speaker endpoints require Cognito Bearer + SPEAKER role — the new submit endpoint inherits this via `@PreAuthorize("hasRole('SPEAKER') and @companyAuth.isSelf(#name)")`), **FR12** (`User.companyId` stores `companyName`, ADR-003 — `@companyAuth.isSelf` literally reads it), **NFR4** (audit-trail integrity — `submitted_by_username` / `reviewed_by_username` / `reviewed_at` / `decision_note`), **NFR6** (Testcontainers integration tests), **NFR7** (doc-drift policy — see AC13), **NFR10** (i18n: UI keys × 10 locales, email templates DE+EN only), **UX-DR17** (no `?token=` / `?jwt=` on speaker portal — standard Cognito session), **UX-DR22** (10-locale parity for new UI copy).
  - The Epic 11 PRD §"Phase G" entry tracks the G-prefixed reqs and the FR/NFR/AR/UX-DR Coverage Maps at the end of the epic list 11.G.1 under each G-prefixed requirement.
- **PM-resolved decisions (2026-05-20):**
  1. **Q1 — `event_tasks.event_code` nullability:** The column is actually `event_id UUID NOT NULL REFERENCES events(id)`, not `event_code`. **Resolution:** add an EMS Flyway migration `V<n>__make_event_tasks_event_id_nullable.sql` that drops the `NOT NULL` constraint on `event_id` (the FK to `events.id` is preserved — it just no longer fires when `event_id IS NULL`). The `EventTask` JPA entity's `@Column(name = "event_id", nullable = false, columnDefinition = "UUID")` flips to `nullable = true`. No new column is added; the existing one becomes nullable so cross-event tasks (like Company-Info-Update-Review) can be persisted. See AC5.
  2. **Q2 — Pending logo on reject:** No S3 cleanup. The file-upload state machine (`PENDING → CONFIRMED → ASSOCIATED`) reaps unassociated objects in a future cleanup story. Settled.
  3. **Q3 — Multiple speakers per company:** Bare 409 (`PENDING_UPDATE_EXISTS`) is sufficient for MVP. We do NOT leak the colleague's identity. Revisit only if a user reports confusion.
  4. **Q4 — UI placement (NEW direction 2026-05-20):** The speaker-side edit-company functionality lives as a **section on the existing `/speaker-portal/profile` page**, NOT as a new `/speaker-portal/company` route. This avoids duplicating page chrome, sidesteps the diff-panel visual-design open questions, and lets speakers manage their own profile + their company's public footprint in one place. AC7 + AC8 are rewritten accordingly. No new route, no new top-nav link.
- **Plan / design anchors:**
  - Pattern reference: `docs/api/speakers-api.openapi.yml` Story 5.5 review-queue + `action: APPROVE|REJECT + feedback` shape — mirrored here.
  - Pattern reference for AFTER_COMMIT side-effects: existing CUMS `@TransactionalEventListener` examples on User-lifecycle events.

---

## Acceptance Criteria

The AC are pinned to the approved plan, the resolved PM decisions (2026-05-20), and the actual file locations on `feature/speaker-workflow-refactor`. Each AC names the exact file(s) under change.

### AC1 — New `company_update_requests` table + JPA entity + repository (CUMS) (AR-G1)

**Given** the company-user-management service is built,
**When** the Flyway migration runs,
**Then** the next sequential migration `V<n>__create_company_update_requests.sql` (where `<n>` is the next free version — currently `V16` per `services/company-user-management-service/src/main/resources/db/migration/`, dev confirms at implementation) creates the table:

```sql
CREATE TABLE company_update_requests (
    id                       UUID PRIMARY KEY,
    company_name             VARCHAR(12)  NOT NULL REFERENCES companies(name),
    proposed_display_name    VARCHAR(255),
    proposed_website         VARCHAR(500),
    proposed_logo_url        VARCHAR(1000),
    proposed_logo_s3_key     VARCHAR(500),
    proposed_logo_upload_id  UUID,
    status                   VARCHAR(20)  NOT NULL,
    submitted_by_username    VARCHAR(100) NOT NULL,
    submitted_at             TIMESTAMP    NOT NULL,
    reviewed_by_username     VARCHAR(100),
    reviewed_at              TIMESTAMP,
    decision_note            TEXT,
    task_id                  UUID,
    CONSTRAINT chk_cur_status CHECK (status IN ('PENDING','APPROVED','REJECTED'))
);
CREATE UNIQUE INDEX idx_cur_one_open
    ON company_update_requests(company_name) WHERE status = 'PENDING';
CREATE INDEX idx_cur_status ON company_update_requests(status);
```

**And** a new JPA entity `services/company-user-management-service/src/main/java/ch/batbern/companyuser/domain/CompanyUpdateProposal.java` exists (class name `CompanyUpdateProposal` — chosen to avoid clash with the existing `UpdateCompanyRequest` DTO; table name `company_update_requests` retained as PM agreed). The status field maps to an enum `CompanyUpdateProposalStatus { PENDING, APPROVED, REJECTED }`.

**And** the unique partial index enforces "at most one PENDING per company" at the DB layer. A second `INSERT` while one is open raises `org.postgresql.util.PSQLException` (unique constraint), mapped by the service to HTTP `409 Conflict` per AC2.

**And** a Spring Data JPA repository `CompanyUpdateProposalRepository` exists with: `findByCompanyNameAndStatus(String, CompanyUpdateProposalStatus)`, `findCurrentByCompanyName(String name)` (returns the most-recent row regardless of status, used by the speaker page + organizer panel), `findAllByStatus(CompanyUpdateProposalStatus)`, and standard `findById`.

---

### AC2 — Submission endpoint `POST /api/v1/companies/{name}/update-requests` (FR-G1, AR-G1)

**Given** the OpenAPI spec `docs/api/companies.openapi.yml`,
**When** I read the paths,
**Then** a new path `/companies/{name}/update-requests` with `operationId: submitCompanyUpdateProposal`, tags `[Companies]`, `security: [{ BearerAuth: [] }]`, and the path parameter `name` (pattern matches the existing companies path parameter) is defined.

**And** the request schema `#/components/schemas/SubmitCompanyUpdateRequest` has exactly:

| Field | Type | Required | Validation |
|---|---|---|---|
| `displayName` | string | no | `maxLength: 255` |
| `website` | string | no | `format: uri`, `maxLength: 500` |
| `logoUploadId` | string (uuid) | no | references file-upload state-machine row |

`additionalProperties: false`. **At least one of the three fields must be present** — validated by a `@AssertTrue` on the DTO or a service-layer check that throws `MethodArgumentNotValidException`.

**And** the controller `services/company-user-management-service/src/main/java/ch/batbern/companyuser/web/CompanyUpdateProposalController.java`:

```java
@PostMapping("/{name}/update-requests")
@PreAuthorize("hasRole('SPEAKER') and @companyAuth.isSelf(#name)")
public ResponseEntity<CompanyUpdateProposalResponse> submit(
        @PathVariable String name,
        @Valid @RequestBody SubmitCompanyUpdateRequest request) { ... }
```

**And** a new authorisation helper bean `@Component("companyAuth")` with method `boolean isSelf(String companyName)` reads `SecurityContextHelper.getCurrentUsername()`, looks up the user's `companyId` (which stores `companyName` per ADR-003), and returns `true` iff it matches. Returns `403 Forbidden` when the speaker targets a different company.

**And** the service `CompanyUpdateProposalService.submit(...)`:
1. Verifies the company exists (404 otherwise).
2. Inserts a row with `status=PENDING`, `submitted_by_username=<jwt.principal>`, `submitted_at=now()`.
3. On `DataIntegrityViolationException` (unique-PENDING index hit): rethrows as a `ConflictException` mapped to HTTP `409` by `GlobalExceptionHandler`. Body: `{ "code": "PENDING_UPDATE_EXISTS", "details": { "companyName": "..." } }`.
4. Calls `EventTaskApiClient.createUnassignedTask(...)` (see AC5). Failure logged but does not roll back the insert.
5. Returns `201 Created` with the new `CompanyUpdateProposalResponse` body.

**And** the documented error responses include: `400` (validation — empty body, all-null fields, bad website URI, unknown fields), `401` (no auth), `403` (speaker not on this company), `404` (company unknown), `409` (existing PENDING).

---

### AC3 — Decision endpoint `POST /api/v1/companies/{name}/update-requests/{id}/decision` (FR-G1)

**Given** the OpenAPI spec,
**When** I read the paths,
**Then** a new path `/companies/{name}/update-requests/{id}/decision` exists with `operationId: decideCompanyUpdateProposal`, `security: [{ BearerAuth: [] }]`, path parameters `name` and `id` (uuid).

**And** the request schema `#/components/schemas/DecideCompanyUpdateRequest` has exactly:

| Field | Type | Required | Validation |
|---|---|---|---|
| `action` | enum `APPROVE \| REJECT` | yes | |
| `decisionNote` | string | required if `action == REJECT` | `maxLength: 2000` |

`additionalProperties: false`. The conditional required is enforced server-side (`@AssertTrue` on the DTO or a service check).

**And** the controller method:

```java
@PostMapping("/{name}/update-requests/{id}/decision")
@PreAuthorize("hasRole('ORGANIZER')")
public ResponseEntity<CompanyUpdateProposalResponse> decide(
        @PathVariable String name,
        @PathVariable UUID id,
        @Valid @RequestBody DecideCompanyUpdateRequest request) { ... }
```

**And** the service `CompanyUpdateProposalService.decide(...)` runs **inside a single `@Transactional` method**. On `action == APPROVE`:
1. Load the proposal by `id`; verify `companyName == name` (404 mismatch) and `status == PENDING` (`409` otherwise).
2. Load the `Company` row.
3. For each non-null `proposed_*` field, copy to the live column: `company.setDisplayName(p.getProposedDisplayName())`, `setWebsite(...)`, and for logo: set both `logoUrl` and `logoS3Key` (paired). If `proposed_logo_upload_id` is present, call the existing logo-association service (the same path `PUT /companies/{name}` uses today via `logoUploadId`).
4. Save the company; set proposal `status=APPROVED`, `reviewed_by_username=<jwt.principal>`, `reviewed_at=now()`, `decision_note=request.decisionNote` (optional on approve).
5. After the transaction commits, fire the speaker-notification email (AC9) and the EMS task-completion call (AC6) via `@TransactionalEventListener(AFTER_COMMIT)` — so an email/task-call failure doesn't roll back the approve.

On `action == REJECT`:
1. Load + verify as above.
2. **Do not touch the `companies` row.**
3. Set proposal `status=REJECTED`, `reviewed_by_username`, `reviewed_at`, `decision_note` (required — `400` if absent).
4. Same after-commit hooks for email + task completion.

**And** documented error responses: `400` (validation, missing `decisionNote` on reject, unknown action), `401`, `403` (not ORGANIZER), `404` (proposal not found, or `companyName` path mismatch), `409` (proposal not in PENDING).

---

### AC4 — Read endpoints `GET /update-requests/current` + `GET /update-requests?status=PENDING`

**Given** the OpenAPI spec,
**When** I read the paths,
**Then**:
- `GET /api/v1/companies/{name}/update-requests/current` (`operationId: getCurrentCompanyUpdateProposal`) — returns the most-recent row for that company regardless of status (or `204 No Content` if none ever existed). `@PreAuthorize("hasRole('SPEAKER') and @companyAuth.isSelf(#name) or hasRole('ORGANIZER')")`. Used by the speaker page banner + the organizer review panel.
- `GET /api/v1/companies/update-requests?status=PENDING` (`operationId: listCompanyUpdateProposals`) — returns the array of pending proposals (lightweight DTO: `{ companyName, displayName, submittedAt, submittedByUsername, id }`). `@PreAuthorize("hasRole('ORGANIZER')")`. Used by the organizer filter chip / count badge.

**And** both endpoints' response DTOs are documented as `CompanyUpdateProposalResponse` (full shape) and `CompanyUpdateProposalSummary[]` (list shape).

---

### AC5 — CUMS → EMS unassigned EventTask creation on submission (AR-G2)

**Given** a speaker successfully submits a proposal,
**When** the service completes the insert,
**Then** `EventTaskApiClient.createUnassignedTask(...)` is called against the EMS endpoint that creates an `event_tasks` row.

**And** a new HTTP client `services/company-user-management-service/src/main/java/ch/batbern/companyuser/client/EventTaskApiClient.java` is added, following the same JWT-propagation pattern as the existing cross-service clients (`UserApiClient`, etc. in shared-kernel) — reuses the request `SecurityContext` so the JWT is forwarded.

**And** a new EMS Flyway migration `V<n>__make_event_tasks_event_id_nullable.sql` (next sequential version after `V78` per `services/event-management-service/src/main/resources/db/migration/`; dev confirms at implementation) executes `ALTER TABLE event_tasks ALTER COLUMN event_id DROP NOT NULL;`. The existing FK `event_tasks.event_id REFERENCES events(id) ON DELETE CASCADE` is preserved — it simply does not fire when `event_id IS NULL` (standard PostgreSQL FK behaviour on nullable columns).

**And** the `EventTask` JPA entity (`services/event-management-service/src/main/java/ch/batbern/events/domain/EventTask.java`) is updated: `@Column(name = "event_id", nullable = false, columnDefinition = "UUID")` → `@Column(name = "event_id", columnDefinition = "UUID")` (drop `nullable = false`). The `private UUID eventId;` field stays. The `EventTaskService.createTask(...)` signature is extended to accept a nullable `eventId`; existing callers that pass non-null values are unaffected.

**And** the EMS POST endpoint that creates a task is reused (no new endpoint). The task-creation request body sent by `EventTaskApiClient.createUnassignedTask(...)` maps to the **actual** `event_tasks` schema (column names verified in `V22__Add_task_system.sql`):

| Field name in EMS create-task DTO | DB column | Value for company-info-update task |
|---|---|---|
| `eventId` | `event_id` (UUID, now nullable) | `null` |
| `taskName` | `task_name` (NOT NULL, VARCHAR 255) | `"Company Info Update Review — <displayName-or-companyName>"` |
| `triggerState` | `trigger_state` (NOT NULL, VARCHAR 50) | `"company_info_update"` (new sentinel value — does NOT map to a workflow-state enum; documented in `06a-workflow-state-machines.md` as "non-workflow trigger value used for cross-event organizer tasks") |
| `assignedOrganizerUsername` | `assigned_organizer_username` (nullable) | `null` |
| `status` | `status` (default `'todo'`) | `'todo'` |
| `notes` | `notes` (TEXT) | `"Pending company info update from <speaker username>. Review at /organizer/companies/<name>?reviewRequest=<id>"` |
| `dueDate` | `due_date` (nullable) | `null` |
| `templateId` | `template_id` (nullable) | `null` |

**And** on success, the returned `taskId` (UUID) is persisted on `company_update_requests.task_id` via a separate small update — failure to persist `task_id` is logged but does not roll back the proposal insert.

**And** on EMS error (5xx, network failure, timeout): the failure is logged at WARN with a correlation ID; the proposal insert is **not rolled back**; `task_id` stays `NULL`; the speaker still gets a `201 Created` response. The badge on `/organizer/companies` (AC9) remains the primary discovery surface — the task is a redundant convenience.

**And** the `EventTaskRepository` queries used by the organizer Task Board's "All Tasks" tab must handle `event_id IS NULL` rows gracefully (no NPE on the `eventId`-based grouping). Audit the existing query methods in `EventTaskRepository.java` during implementation and add `event_id IS NULL` test rows to the existing `EventTaskRepositoryTest` to verify.

---

### AC6 — Task completion on decision

**Given** the organizer issues a decision (APPROVE or REJECT),
**When** the `@TransactionalEventListener(AFTER_COMMIT)` fires post-decision,
**Then** `EventTaskApiClient.markCompleted(taskId)` is called (if `task_id` is non-null) — sets the EMS task `status = 'completed'` and stamps `completed_date = now()`, `completed_by_username = <organizer-username>` per the existing `event_tasks` columns.

**And** the decision outcome (approved vs. rejected) is conveyed by appending to the existing `notes` column (the actual EMS schema field — there is no `description` column): e.g. `"\n\nDecision: APPROVED by <organizer> on <date>"` or `"\n\nDecision: REJECTED by <organizer> on <date> — <decisionNote>"`. The `status` itself stays `'completed'` either way.

**And** failure to mark the task completed (EMS down, task missing, etc.) is logged at WARN and does not roll back the decision. The badge on `/organizer/companies` clears as soon as the proposal row leaves PENDING — that is the source of truth, not the EMS task.

---

### AC7 — Company-info section on the existing speaker profile page (UX-DR-G1, Q4)

**Given** the existing speaker profile page `web-frontend/src/pages/speaker-portal/ProfileUpdatePage.tsx`,
**When** a speaker with a non-null `user.companyName` navigates to `/speaker-portal/profile`,
**Then** a new `<Card>` section titled **"My Company"** (`t('speakerPortal.profile.companySection.title')`) is rendered below the existing "Basic Info" card. Visually consistent with the existing cards on the same page (same `Card` from `@/components/public/ui/card`, same heading style, same `text-zinc-100` / `text-zinc-400` palette).

**And** **no new route, no new page, no new top-nav link** is added. `PublicNavigation.tsx` is left as-is. The user reaches this section by clicking the existing "My Profile" link (`/speaker-portal/profile`) and scrolling — same hands-off pattern as today's photo upload.

**And** the new section:
1. Reads the live company display fields from `profileData.user.company` (returned by `getUserProfile(['company'])` — already requested by `ProfileUpdatePage`; no second fetch needed for current values).
2. Fetches the current proposal via a new helper `getCurrentCompanyUpdateProposal(user.companyName)` (returns `null` on 204) added to `web-frontend/src/services/api/companyApi.ts`. TanStack Query key: `['company-update-proposal-current', user.companyName]`, `staleTime: 0`.
3. **If no PENDING proposal:** renders an inline edit form with **exactly three fields** — `displayName` (text), `website` (URL), `logo` (image upload via existing presigned-S3 flow → reuses `ProfilePhotoUpload`-style pattern but scoped to company logo, OR reuses the logo-upload UX from the organizer `CompanyForm`). The form lives directly in `ProfileUpdatePage.tsx` (NOT inside `CompanyForm.tsx` — see Dev Notes). Submit calls `submitCompanyUpdateProposal(user.companyName, { displayName?, website?, logoUploadId? })`. On 201, the section transitions to the pending state per AC8.
4. **If a PENDING proposal exists:** renders the AC8 read-only banner state instead of the form.

**And** the section's three fields validate inline using the same patterns as the existing profile fields:
- `displayName`: optional, trimmed, `maxLength: 255`.
- `website`: optional, must match a URL regex if non-empty (frontend hint only — server is authoritative per AC2).
- `logo`: optional; the file picker calls `companyApi.requestLogoUpload()` (existing) to obtain a presigned PUT URL and a `logoUploadId`. Successful upload stores the `logoUploadId` in local state; submit sends it on the proposal payload.

**And** the section's submit button is disabled when none of the three fields are dirty (`displayName` unchanged, `website` unchanged, no logo staged) — the "at least one field" rule from AC2 is enforced client-side too.

**And** the section is hidden when `user.companyName` is null or empty — speakers who aren't linked to a company can't see or trigger the flow.

---

### AC8 — Pending state in the "My Company" section blocks resubmit (UX-DR-G1)

**Given** a speaker has an outstanding `PENDING` proposal,
**When** they navigate to `/speaker-portal/profile` and scroll to the "My Company" section,
**Then** the three form fields are **NOT rendered** — instead the section shows a pending state:
1. An info `<Card>` with an amber border (`border-amber-800 bg-amber-900/20`, mirroring the existing "complete profile" hint on the same page) and text: `t('speakerPortal.profile.companySection.pendingBanner', { date })` → `"You have a company-info update awaiting organizer review (submitted {date})."`
2. Below the banner, an expandable `<details>` (or a click-toggle) labelled `t('speakerPortal.profile.companySection.viewSubmitted')` → `"View what I submitted"` that, when expanded, shows the diff between the live company values and the proposed values:
   - `displayName`: live → proposed (only if proposed is non-null).
   - `website`: live → proposed (only if proposed is non-null).
   - `logo`: side-by-side `<img>` thumbnails (live vs. proposed) if a proposed logo exists.
3. No submit button is rendered in this state.

**And** the pending state clears (the section reverts to the editable form) once the decision lands — verified by the next refetch of the `['company-update-proposal-current', user.companyName]` query. The query is invalidated explicitly when the speaker re-enters the page (`useEffect` on mount) and a TanStack Query `staleTime: 0` refetch fires on window-focus.

**And** if the speaker reloads while their own submit is in-flight (between local submit and server 201), a small `<BATbernLoader />` placeholder is shown in the section, mirroring the existing loading-state pattern on the same page.

---

### AC9 — Organizer badge + filter chip on `/organizer/companies` (UX-DR-G1)

**Given** the existing `web-frontend/src/components/shared/Company/CompanyManagementScreen.tsx`,
**When** the screen mounts and the organizer has at least one pending proposal,
**Then** a new filter chip `[Pending updates (N)]` is rendered above the list, where `N` is the count from `companyApi.listPendingProposals()` (the new endpoint per AC4). Toggling it filters the list to only companies with a pending proposal. The existing `[All]` chip and other filters keep their current behaviour.

**And** each affected row in the list shows a small chip `[● Update pending]` (left-aligned next to the company name). The pending-list query is shared via TanStack Query cache to avoid double-fetching.

**And** in `web-frontend/src/components/shared/Company/CompanyDetailView.tsx`, when the company has a `PENDING` proposal, a new **review panel** is rendered with these visual specifics (pinned to remove implementation ambiguity):

1. **Mount point.** The review panel mounts **above** the existing "Edit company" form on the same detail page (between the company-header summary and the existing organizer-edit form). It is its own MUI `<Card>` with a coloured border (`borderColor: 'warning.main'`) so it visually stands out.

2. **Layout.** **Horizontal side-by-side** at viewport `≥ md` (768 px) — two columns labelled "Current" (left) / "Proposed" (right). Below `md` it stacks vertically (Current above, Proposed below). Implementation via MUI `<Grid container spacing={2}>` with `<Grid item xs={12} md={6}>`.

3. **Field visibility.** Show **only the fields that changed** (e.g. if only `displayName` differs, only that row is rendered). Unchanged fields are not shown — keeps the panel compact when a speaker only edited one thing. A small caption underneath lists which fields are unchanged (`"website and logo unchanged"`).

4. **Logo diff rendering.** Two `<img>` thumbnails of equal size (`width: 96, height: 96, objectFit: 'contain'`), side-by-side, with captions "Current logo" / "Proposed logo". No overlay / click-to-compare — keep it simple. If the speaker did not propose a new logo, the row is omitted entirely (per #3).

5. **Approve/Reject buttons.** A horizontal `<ButtonGroup>` below the diff rows. `[Approve]` is the primary button (MUI `variant="contained"`, `color="success"`). `[Reject]` is secondary (`variant="outlined"`, `color="error"`). `[Reject]` opens an MUI `<Dialog>` requiring a `decisionNote` textarea (max 2000 chars, validated client-side and server-side per AC3). `[Approve]` posts immediately with an empty `decisionNote`.

Submitting calls `companyApi.decideProposal(name, id, { action, decisionNote })`. On success, the panel disappears, the company row refetches, and the badge/filter count drops via TanStack Query invalidation.

**And** the URL `/organizer/companies/{name}?reviewRequest={id}` (deep-linked from the EventTask description, AC5) auto-scrolls the review panel into view (`useEffect` + `useRef` + `scrollIntoView({ behavior: 'smooth', block: 'start' })`) and applies a brief highlight pulse on the Approve/Reject buttons. If `reviewRequest` doesn't match the current PENDING (e.g. someone else just decided it), a toast explains "this update has already been reviewed."

**And** the existing organizer "Edit company" form on the same detail page is **left exactly as-is** — organizer-direct edits remain immediate (no review gate). The new review panel is additive.

---

### AC10 — Speaker notification email on decision (DE + EN only)

**Given** a decision lands and the `@TransactionalEventListener(AFTER_COMMIT)` fires,
**When** the speaker's preferred language is read from `User.preferredLanguage` and falls back to `'en'` if not set or not `de`/`en` (per `CLAUDE.md` email-locale rule — DE + EN only),
**Then** one of two emails is sent to `submitted_by_username`'s email address:
- On APPROVE: subject `"Your company info update has been approved"` (or DE equivalent). Body summarises which fields changed and links to the public site (if `is_publishable` for the speaker's session).
- On REJECT: subject `"Your company info update needs adjustments"` (or DE equivalent). Body includes the `decisionNote` verbatim and a link back to `/speaker-portal/profile#my-company` (the existing profile page; the `#my-company` fragment is the anchor on the new section per AC7) to resubmit.

**And** templates live at `services/company-user-management-service/src/main/resources/email-templates/`:
- `company-update-approved.{html,txt}` — DE + EN files only.
- `company-update-rejected.{html,txt}` — DE + EN files only.

**And** no email is sent to organizers on submission (PM decision §2 of resolved questions — badge + EventTask are the surfaces; avoid inbox noise).

---

### AC11 — i18n: all 10 locales populated for new UI keys

**Given** the new keys are added to the existing `common.json` namespace (no new namespace is introduced — Q4 placement on the existing profile page makes a new namespace unnecessary),
**When** the build runs,
**Then** `web-frontend/public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/common.json` carries:

**Speaker side (under `speakerPortal.profile.companySection`):**
- `title` (`"My Company"`)
- `description` (one-line helper text)
- `displayNameLabel`, `websiteLabel`, `logoLabel`
- `displayNamePlaceholder`, `websitePlaceholder`
- `submitButton` (`"Submit for review"`)
- `submitDisabledHint` (`"Change at least one field"`)
- `submitSuccess` (`"Submitted — an organizer will review"`)
- `pendingBanner` (with `{date}` interpolation per AC8)
- `viewSubmitted` (`"View what I submitted"`)
- `diffLabels.current`, `diffLabels.proposed`
- `errors.websiteInvalid`, `errors.displayNameTooLong`, `errors.logoTooLarge`, `errors.submitFailed`

**Organizer side (under `company.reviewPanel` and `company.filters` + `company.chip`):**
- `company.reviewPanel.title`, `company.reviewPanel.currentLabel`, `company.reviewPanel.proposedLabel`
- `company.reviewPanel.approveButton`, `company.reviewPanel.rejectButton`
- `company.reviewPanel.rejectDialogTitle`, `company.reviewPanel.rejectDialogNoteLabel`, `company.reviewPanel.rejectDialogNoteRequired`
- `company.reviewPanel.alreadyReviewedToast` (for the deep-link race)
- `company.reviewPanel.fieldsUnchangedCaption` (with `{fields}` interpolation, e.g. `"website and logo unchanged"`)
- `company.filters.pendingUpdates` (with `{count}` interpolation)
- `company.chip.updatePending`
- `company.approveSuccess`, `company.rejectSuccess`

**And** EN + DE are first-class quality (hand-authored by the dev). The other 8 locales (fr, it, rm, es, fi, nl, ja, gsw-BE) get straight translations and land in the same commit — per `CLAUDE.md` §"Localization — Email Templates: DE + EN Only; UI i18n: All 10 Locales".

**And** before adding a new key, the dev greps existing keys in `common.json` — if an existing key with the same English value exists (e.g. for `current` / `proposed` if used elsewhere), reuse it per the project's i18n key-reuse rule.

---

### AC12 — Tests

**Backend (CUMS) — extends `AbstractIntegrationTest` (real Postgres via Testcontainers):**
- `CompanyUpdateProposalServiceTest` (unit, Mockito): approve copies fields atomically; reject leaves company untouched; both stamp `reviewedBy`/`reviewedAt`/`decisionNote` correctly.
- `CompanyUpdateProposalControllerIntegrationTest`: full HTTP cycle for submit / get-current / list-pending / decide. Covers:
  - `@PreAuthorize` — speaker can only submit for their own company (403 otherwise); organizer cannot submit (403); both can read; only organizer can decide.
  - Second `POST /update-requests` while one PENDING exists → `409 PENDING_UPDATE_EXISTS`.
  - Second `POST /decision` on an already-APPROVED or already-REJECTED proposal → `409` (proposal not in PENDING) **AND** the existing `reviewed_by_username` / `reviewed_at` / `decision_note` are observably unchanged (audit-trail immutability per NFR-G1).
  - Reject without `decisionNote` → `400`.
  - Approve: companies-row state observably changed after commit.
  - Reject: companies-row state observably unchanged after commit.
  - Submit with an empty body or all-null fields → `400`.
  - Speaker reads their own proposal via `GET /update-requests/current` → `200`; speaker B (different company) reads → `403`.
- `EventTaskApiClientTest` (WireMock): JWT propagation; graceful handling of 5xx / timeouts (speaker still gets 201). Verifies the request payload uses the actual EMS column names per AC5 (`taskName`, `triggerState=company_info_update`, `notes`, `eventId=null`, `dueDate=null`).

**Backend (EMS):**
- `EventTaskRepositoryTest`: add a test verifying that an `event_tasks` row with `event_id IS NULL` is fetched correctly and surfaces on the existing Task Board "All Tasks" query without NPE.
- `EventTaskServiceIntegrationTest`: add a test creating a task with `eventId=null` and asserting persistence + read-back via the existing endpoint.

**Frontend (Vitest + React Testing Library):**
- `ProfileUpdatePage.test.tsx`: **extend** the existing test file with cases for the new "My Company" section:
  - Renders the section only when `user.companyName` is non-empty.
  - Renders the editable 3-field form when no PENDING proposal.
  - Renders the pending banner + diff when a PENDING proposal exists; no submit button.
  - Submit happy path (mocked `companyApi.submitCompanyUpdateProposal` → 201) transitions the section to pending state.
  - Submit button is disabled when no field is dirty (the "at least one field" client-side guard).
- `CompanyManagementScreen.test.tsx`: filter chip count matches API; filter narrows the list; row chip renders for affected rows only.
- `CompanyDetailView.test.tsx`: review panel renders only when PENDING; Approve calls correct API; Reject requires note and surfaces validation when empty; visual pinpoints from AC9 — review panel is above the existing Edit form; horizontal grid at `md+`; only changed fields shown; deep-link `?reviewRequest={id}` triggers scroll-into-view (via mock).

**E2E (Playwright):**
- `web-frontend/e2e/speaker/profile-edit-company.spec.ts` (speaker project): log in as speaker → navigate to `/speaker-portal/profile` → scroll to "My Company" section → edit `displayName` + stage a new logo → submit → pending banner appears on next render.
- `web-frontend/e2e/organizer/company-update-review.spec.ts` (chromium organizer project): log in as organizer → see badge + filter count on `/organizer/companies` → open detail → approve → live data reflects new values → companies-row API returns the new `displayName` → EMS task is marked `completed` (via direct API check, since the Task Board page may not be in scope of this E2E).

**Bruno API contract tests:**
- `bruno-tests/companies/submit-update-proposal.bru` — happy path (201) + 409 (existing PENDING) + 400 (empty body) + 403 (wrong company).
- `bruno-tests/companies/decide-update-proposal.bru` — approve + reject paths + 409 (already-decided proposal) + 400 (reject without decisionNote).
- `bruno-tests/companies/list-pending-updates.bru` — organizer list endpoint.
- `bruno-tests/companies/get-current-update-proposal.bru` — happy path + 204 (none exists yet).

Coverage thresholds per project standards: backend integration ≥ 80%, frontend unit ≥ 90% for new business logic.

---

### AC13 — Documentation updates (NFR7 — doc-drift policy)

**Given** the doc-drift policy in `CLAUDE.md` §"Doc Drift Prevention" and Epic 11 NFR7,
**When** this story's commit lands,
**Then** the following documentation is updated in the same commit (or `[no-doc]` is appended to the commit message only if the dev has verified each entry below is genuinely unaffected — that is NOT the case for this story):

1. **`docs/api/companies.openapi.yml`** — the 4 new paths (`POST /companies/{name}/update-requests`, `GET /companies/{name}/update-requests/current`, `GET /companies/update-requests`, `POST /companies/{name}/update-requests/{id}/decision`) and the 4 new schemas (`SubmitCompanyUpdateRequest`, `DecideCompanyUpdateRequest`, `CompanyUpdateProposalResponse`, `CompanyUpdateProposalSummary`) are added.

2. **`docs/architecture/03-data-architecture.md`** — add the `company_update_requests` table to the CUMS schema section, including the unique partial index. Document the cross-reference: rows reference `companies(name)` (intra-service FK ✅) and `task_id` references the EMS `event_tasks(id)` (cross-service — stored but no FK constraint, per ADR-003).

3. **`docs/architecture/06-backend-architecture.md`** — document the new `EventTaskApiClient` (CUMS → EMS) as the first inverse-direction cross-service HTTP client (existing clients flow EMS → CUMS). Note the JWT-propagation pattern and the best-effort error semantics on submit (5xx from EMS doesn't roll back the CUMS proposal insert).

4. **`docs/architecture/06a-workflow-state-machines.md`** — add a short note that `event_tasks.trigger_state = 'company_info_update'` is a **non-workflow sentinel** trigger value used for cross-event organizer tasks. It does NOT correspond to a workflow-state enum value and does NOT participate in any state-machine transition.

5. **`docs/wireframes/sitemap.md`** — under the existing speaker portal section, update the `/speaker-portal/profile` entry to mention the new "My Company" section (no new route is added — the existing route's content grows). No mermaid update needed (no new node).

6. **`.github/doc-drift-mappings.yml`** — add mappings:
   - `services/company-user-management-service/src/main/java/ch/batbern/companyuser/domain/CompanyUpdateProposal.java` → `docs/architecture/03-data-architecture.md`
   - `services/company-user-management-service/src/main/java/ch/batbern/companyuser/client/EventTaskApiClient.java` → `docs/architecture/06-backend-architecture.md`
   - `services/event-management-service/src/main/resources/db/migration/V<n>__make_event_tasks_event_id_nullable.sql` → `docs/architecture/03-data-architecture.md`
   - `services/company-user-management-service/src/main/resources/db/migration/V<n>__create_company_update_requests.sql` → `docs/architecture/03-data-architecture.md`

7. **`docs/prd/epic-11-speaker-workflow-refactor.md`** — the Phase G entry (currently at L1546+) is updated to reflect the Q4 placement (no new page, section on existing profile page). The four G-prefixed requirements (FR-G1, AR-G1, AR-G2, UX-DR-G1) are added to the formal Requirements Inventory blocks at the top of the epic (L23–L312), and the FR / NFR / AR / UX-DR Coverage Maps at the bottom of the epic (L1681–L1700) are extended to map each G-prefixed requirement → 11.G.1. A net-new **NFR-G1** is added for audit-trail immutability + first-decision-wins concurrency (per AC12 NFR-G1 test).

---

## Dev Notes

- **OpenAPI-first:** edit `docs/api/companies.openapi.yml` first, then regenerate (`./gradlew :services:company-user-management-service:openApiGenerateCompanies` + `cd web-frontend && npm run generate:api-types`). Commit the generated frontend types (`src/types/generated/` is committed) but NOT the backend `build/generated/` (gitignored per ADR-006).
- **Naming clash avoidance:** the new Java class is `CompanyUpdateProposal` (entity) + `CompanyUpdateProposalService` / `Controller` / `Repository`. The DB table stays `company_update_requests` (PM agreed). The existing organizer-side `UpdateCompanyRequest` DTO is **untouched**.
- **Speaker authorisation seam:** the new `@Component("companyAuth")` helper bean is the cleanest way to enforce "speakers can only target their own company" via SpEL in `@PreAuthorize("... and @companyAuth.isSelf(#name)")`. Pattern: similar to existing `@speakerAuth` / `@partnerAuth` helpers if they exist; otherwise create new.
- **Speaker UI placement (Q4 resolution):** the speaker edit happens **inline on `ProfileUpdatePage.tsx`**, not on a new page. Do NOT create `SpeakerCompanyEditPage.tsx`. Do NOT add a top-nav link in `PublicNavigation.tsx`. The "My Company" section is a new `<Card>` block added directly to the existing page, after the "Basic Info" card. This deliberately sidesteps the need to reuse `CompanyForm.tsx` in speaker mode — instead, render the three fields inline (similar to how Basic Info renders `firstName` / `lastName` / `bio` inline today). `CompanyForm.tsx` is **untouched** by this story.
- **EMS schema change (Q1 resolution):** add `V<n>__make_event_tasks_event_id_nullable.sql` in EMS dropping `NOT NULL` on `event_tasks.event_id`. The existing FK is preserved (FKs on nullable columns are legal SQL — only enforced when the value is non-null). Verify Flyway sequence at impl time (last is `V78__add_task_cancellation_fields.sql`; choose `V79` unless another concurrent story claims it). Audit `EventTaskRepository` query methods for any `eventId.toString()` / `.getEventId().equals(...)` calls that would NPE on `null` and guard them. The existing Task Board "All Tasks" tab is the read surface — ensure cross-event tasks render with a `"—"` placeholder where the event column would go.
- **EMS create-task DTO compatibility:** the EMS task-creation endpoint already accepts the field shape used in AC5 (the existing `CreateEventTaskRequest` DTO — audit at impl time to confirm field names match `taskName` / `triggerState` / `notes` / `eventId` / `assignedOrganizerUsername` / `dueDate` / `templateId`). If the existing DTO doesn't accept `eventId=null`, extend the OpenAPI spec (`docs/api/events-api.openapi.yml`) accordingly. The `trigger_state = 'company_info_update'` value is a new sentinel — register it in the same place where the existing trigger-state values live (likely a Java constants class or comment in `EventTask.java`).
- **Logo upload reuse:** zero changes to the presigned-URL flow. The new section calls the existing `companyApi.requestLogoUpload()` → `axios.put(uploadUrl, file)` → confirm, then passes the resulting `logoUploadId` to `submitCompanyUpdateProposal`. The backend records it on `proposed_logo_upload_id` and, on approve, calls the same logo-association code path that `PUT /companies/{name}` uses today.
- **`AFTER_COMMIT` hooks:** use `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` for both the email and the EMS task call on decision — so an email/task-call failure doesn't roll back the approve/reject. Pattern reference: existing similar listeners in CUMS / EMS.
- **i18n key reuse:** before adding a new key, grep existing keys in `common.json`. If a key with the same English value already exists, reuse it (per memory feedback "key reuse: always reuse rather than create a duplicate"). For the speaker side, prefer extending the existing `speakerPortal.profile` block in `common.json`; for the organizer side, extend the existing `company` block.
- **Audit-trail immutability (NFR-G1):** the second-decide-attempt 409 path (AC12 test) is the only thing that prevents an organizer from silently overwriting `reviewed_by_username` / `reviewed_at` / `decision_note`. The service-layer check is `if (proposal.status != PENDING) throw new ConflictException(...)` BEFORE writing any field. Do not rely on the DB to enforce this — there is no unique constraint that would (the unique partial index is for PENDING only).
- **Build output discipline:** pipe gradle / make output through `tee /tmp/11g1-build.log` then grep — per project-context.md.
- **Commit message:** `feat(11.G.1): speaker self-service company info with organizer review` — conventional commits. Doc updates (Epic 11 PRD, OpenAPI spec, architecture docs, sitemap, doc-drift-mappings) ride in the same commit per AC13, so no `[no-doc]` suffix needed.

---

## Open Questions

All four Open Questions are **RESOLVED** as of 2026-05-20 by PM (Nissim). Kept here so future readers see the question + the decision + the binding reasoning, in line with the project's resolved-questions convention.

1. **`event_tasks` schema for cross-event tasks. — RESOLVED.** The original wording incorrectly said "`event_code` nullability" — there is no `event_code` column on `event_tasks`. The actual column is `event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE`. **PM decision:** drop the `NOT NULL` constraint on `event_id` (small migration; FK is preserved because PostgreSQL FKs only fire on non-null values). No new column is added. No sentinel "global event" row is seeded. The `EventTask` JPA entity drops `nullable = false`. The repository queries are audited for `null`-handling and the existing Task Board "All Tasks" tab renders cross-event rows with a placeholder where the event column would go. See AC5.

2. **Pending logo: keep on reject? — RESOLVED.** PM decided we do NOT clean up rejected logo S3 objects. The file-upload state machine's `PENDING → CONFIRMED → ASSOCIATED` states already let us reap unassociated objects in a future cleanup story. Settled.

3. **Multiple speakers sharing one company. — RESOLVED.** Bare 409 (`PENDING_UPDATE_EXISTS`) is sufficient for MVP. We do NOT show the second speaker WHO their colleague is — that would leak info if two unrelated speakers happened to share the same employer in the database. Revisit only if a user reports confusion. The 409 body's `details.companyName` is enough for the speaker to recognise the conflict.

4. **Speaker UI placement: new page or section on profile page? — RESOLVED (NEW DIRECTION).** Originally the story specified a new `/speaker-portal/company` page with a new top-nav link and a reused `CompanyForm.tsx` in speaker mode. **PM decision 2026-05-20:** drop the new page. Add a "My Company" section to the existing `/speaker-portal/profile` page (`ProfileUpdatePage.tsx`). No new route, no new top-nav link, no `CompanyForm.tsx` reuse — render the three fields inline on the profile page. This unifies the speaker's self-service surface area (profile + company) in one place and removes design ambiguity around the speaker page's chrome. Organizer side (badge, filter chip, review panel on `/organizer/companies`) is unchanged. AC7 + AC8 rewritten accordingly.

### Additional clarifications (not questions — just dev-facing reminders)

- **`User.companyId` is empty.** A speaker without a company assignment (rare on the refactor branch, possible from legacy data) does not see the "My Company" section (the `user.companyName` falsy guard in AC7 hides it). They also can't hit the endpoint (the `companyAuth.isSelf(null)` check returns false → 403). This is intended — they need an organizer to set their `companyId` first.
- **Speaker who is also organizer.** A user with both `SPEAKER` and `ORGANIZER` roles can both submit a proposal (on the profile page) and approve/reject one (on `/organizer/companies`). The `@PreAuthorize` uses `hasRole('SPEAKER')` for submit and `hasRole('ORGANIZER')` for decide — they pass both. They could in theory approve their own submission. This is acceptable for MVP — the audit trail still records who submitted vs who decided. If they're the same person, that's visible in the UI later.
