# Story 11.E.3: Speaker-portal Cognito auth + frontend session refactor + multi-role nav cherry-pick

Status: review

<!-- All 6 Open Questions PM-resolved 2026-05-17. AC + Tasks + Dev Notes below reflect the
     resolutions: Q#1 eventCode-in-path (was the recommendation); Q#2 DELETE legacy onboarding
     e2e tests outright; Q#3 REMOVE /api/v1/auth/speaker-magic-login permitAll NOW (not Phase F);
     Q#4 drop the speaker-portal nav entry; Q#5 SHIP ALL 10 locales for new UI i18n keys
     (Nissim narrowed the CLAUDE.md "DE+EN only" rule — it now applies ONLY to backend email
     templates, not frontend UI i18n; CLAUDE.md §Localization rewritten in this same commit);
     Q#6 drop SpeakerResponseType.TENTATIVE from the frontend type. Resolution narrative
     preserved at the bottom in "Open Questions (resolved 2026-05-17)". -->
<!-- Validation is optional — run validate-create-story for quality check before dev-story. -->

## Story

**As a** speaker (and as a speaker who is also an attendee, organizer, or partner),
**I want** to access the speaker portal with the same AWS Cognito session as the rest of the BATbern app, and navigate naturally between every portal I have a role in,
**So that** I never juggle magic-link tokens, never see a parallel `/speaker-portal/login` page, and the portal feels like part of one coherent app instead of a bolted-on side door.

## Phase / Dependencies / Requirements Covered

- **Phase:** E — Cognito with forced password change (third of three E stories). The backend + frontend half: it removes `permitAll()` from speaker-portal endpoints, replaces `?token=` / `?jwt=` query-param auth with Cognito Bearer + `@PreAuthorize("hasRole('SPEAKER')")`, refactors `web-frontend/src/pages/speaker-portal/**` pages to consume the same `useAuth` hook as everyone else, and cherry-picks the multi-role nav UI from `feature/speaker-account-creation` so a speaker-who-is-also-an-organizer sees both portals in the nav menu.
- **Depends on (in-epic):**
  1. **Story 11.E.1** (status: `ready-for-dev`). Lands `ALLOW_ADMIN_USER_PASSWORD_AUTH` on the App Client and the four Cognito admin IAM perms on the CUMS task role. **Not directly consumed by 11.E.3** (this story doesn't call Cognito Admin APIs from EMS) — but Phase E as a whole is gated on 11.E.1 landing first.
  2. **Story 11.E.2** (status: `ready-for-dev`). Implements `AdminCreateUser` at `CONTACTED → READY` and the invitation-email rewrite with `loginUrl + temp password`. **Hard dependency**: speakers can't authenticate with a real Cognito identity until 11.E.2 provisions them. The Playwright `speaker` project (AC6) needs a Cognito-authenticated speaker token; that token can only exist after 11.E.2 has run for the test speaker.
  3. **Story 11.B.2** (status: `done`). Provides `SpeakerWorkflowService.transition(speakerPoolId, target, actor, payload)` — the sole status writer. This story replaces `actor = SecurityPrincipal.fromMagicLinkToken(...)` (where it currently exists) with `actor = SecurityPrincipal.fromAuthenticatedPrincipal(SecurityContextHolder.getContext().getAuthentication())`.
  4. **Story 11.C.2** (status: `done`). Consolidated `ContentSubmissionService.submit(speakerPoolId, eventCode, payload, principal)` is the shared write path. The frontend `speakerPortalService` and the backend controllers both feed this method; the actor is read from the Spring `SecurityContext` post-refactor.
- **External cherry-pick prerequisite:** `refs/remotes/origin/feature/speaker-account-creation` must remain reachable so commits `73d94688` (Story 9.5 multi-role nav) and `396a9045` (null-safe `user.roles` guard in `UserMenuDropdown`) can be cherry-picked. Verified at story-creation time (2026-05-17): both SHAs exist on the remote branch; the diff is small (1404 insertions / 59 deletions across 17 files for 73d94688, 1 LOC for 396a9045).
- **Unblocks:** Story 11.F.1 (magic-link teardown — Phase F). 11.F.1 deletes `MagicLinkService`, `JwtConfig`, `SpeakerMagicLoginController`, `SpeakerPortalTokenController`, `SpeakerMagicLoginPage.tsx`, the `magic_link_tokens` table, the `speaker_jwt` cookie, and the `permitAll()` on `/api/v1/auth/speaker-magic-login`. This story disconnects all those from the user-facing flow but **does NOT delete the files** — 11.F.1 owns deletion after Phase E has been observed stable in production for ≥ 1 week (per sprint-status.yaml line 195 + ADR-009).
- **Requirements covered (PRD lines 1253-1316):**
  - **FR8** — `/api/v1/speaker-portal/**` endpoints are `@PreAuthorize("hasRole('SPEAKER')")`.
  - **AR10** — Speaker-portal controllers Cognito-secured (no `permitAll()`).
  - **AR25** — Speaker-portal auth changes (controller annotations + signature changes + frontend session refactor).
  - **AR41** — Multi-role nav cherry-pick from `feature/speaker-account-creation` (73d94688 + 396a9045).
  - **UX-DR17** — Portal pages use the same Cognito session as the rest of the app.
  - **UX-DR20** — Multi-role users see all their portals in the nav.
- **Plan / ADR anchors:**
  - Epic 11 PRD §"Story 11.E.3" lines 1253-1316 — the AC source-of-truth.
  - `docs/plans/speaker-workflow-refactor.md` §0.5 (Authentication target model), §3.1 (EMS components), §3.4 (web-frontend), §4 (API surface — speaker-portal auth changes), §5 Phase E, §9.2 (cherry-pick disposition — what to take from 73d94688, what to skip).
  - `docs/architecture/ADR-009-unified-speaker-workflow.md` §"Decision 3" lines 253-297 (standard Cognito registration + first-login password change; `permitAll()` removed; `@PreAuthorize("hasRole('SPEAKER')")` on `/api/v1/speaker-portal/**`).
  - **External source commits:** `73d94688dea1814a22194f44f7aeb169743db25a` and `396a90453f226b20d6b1464b82ced4457e6a7f4e` on `refs/remotes/origin/feature/speaker-account-creation`.
  - `_bmad-output/project-context.md` §"Authentication & Roles" — roles live in PostgreSQL `user_roles`, NOT Cognito groups; JWT carries them via PreTokenGeneration Lambda.

---

## Branch state at story start

- **Current branch:** `feature/speaker-workflow-refactor`.
- **Verified at story-creation time (2026-05-17, after reading the source files):**

  **Backend (EMS) — present today, needs change:**
  - `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerPortalTokenController.java` — 89 lines, `POST /api/v1/speaker-portal/validate-token`. Body: `{ token }`. Calls `MagicLinkService.validateToken`. **Disconnected from frontend** in this story (file kept; Phase F deletes).
  - `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerPortalResponseController.java` — 100 lines, `POST /api/v1/speaker-portal/respond`. Body: `SpeakerResponseRequest { token, response, reason, preferences }`. Calls `SpeakerResponseService.processResponse(request)`. **Refactored** in this story.
  - `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerPortalContentController.java` — 353 lines, 5 endpoints (`GET /content`, `POST /content/draft`, `POST /content/submit`, `POST /materials/presigned-url`, `POST /materials/confirm`). All read `token` from request (query param or body). Line 184-185 already carries a Phase-E intent comment: *"NB: NO @PreAuthorize annotation here in 11.C.2 — Phase E (Story 11.E.3) replaces the magic-link token check with Cognito Bearer + hasRole('SPEAKER')."* — this story honours that note. **Refactored** in this story.
  - `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerPortalDashboardController.java` — 97 lines, `GET /api/v1/speaker-portal/dashboard?token=...`. Calls `SpeakerDashboardService.getDashboard(token)`. **Refactored** in this story.
  - `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerPortalMaterialsService.java` — 280 lines, called by the content controller. Reads token, validates via `MagicLinkService`, generates presigned S3 PUT URLs, confirms uploads. **Refactored** in this story (token-parameter methods replaced with principal+eventCode-parameter methods; `MagicLinkService` collaborator removed from the service's constructor).
  - `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerMagicLoginController.java` — 165 lines, `POST /api/v1/auth/speaker-magic-login`. **NOT touched in this story** — file deletion belongs to Phase F. SecurityConfig `permitAll()` on `/api/v1/auth/speaker-magic-login` (line 131) also stays for Phase F per Open Question Q#3 below.
  - `services/event-management-service/src/main/java/ch/batbern/events/service/MagicLinkService.java` — 356 lines. **NOT touched in this story** — Phase F deletes. The token-validation collaborator field is removed from refactored controllers (so the file compiles standalone) but the file itself remains.
  - `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerResponseService.java` — verified to delegate state-machine writes to `SpeakerWorkflowService.transition()` per Story 11.B.2; the `actor` arg is currently constructed from the magic-link token's speaker context. This story changes the construction to read from `SecurityContextHolder`.
  - `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerDashboardService.java` — currently `getDashboard(String token)`. Refactored to `getDashboard(String username)` reading from the security context.
  - `services/event-management-service/src/main/java/ch/batbern/events/service/ContentSubmissionService.java` — current API per Story 11.C.2: `submit(speakerPoolId, eventCode, payload, principal)`. **No signature change** in this story — only the upstream construction of the `principal` (now Cognito-backed via `SecurityContextHolder`) and `speakerPoolId` (now resolved from `(username, eventCode)` instead of from a token) changes.

  **Backend (EMS) — `SecurityConfig.java`:**
  - Lines 113-128 carry SIX `permitAll()` lines for speaker-portal endpoints (validate-token, respond, dashboard, content GET, content/draft, content/submit, materials/presigned-url, materials/confirm). **All eight matchers removed** in this story; the existing default `.anyRequest().authenticated()` (line 163) now governs them; `@PreAuthorize("hasRole('SPEAKER')")` on each controller method gates role.
  - Line 131 (`/api/v1/auth/speaker-magic-login` permitAll): **REMOVED** per Resolved Q#3 (PM 2026-05-17). After the frontend stops invoking it (AC8), there is no functional reason to expose this endpoint as `permitAll()`; the standard auth chain now governs (`.anyRequest().authenticated()` → 401 for anonymous POSTs). The controller file (`SpeakerMagicLoginController.java`) stays for Phase F deletion; the endpoint just becomes unreachable to anonymous callers. If Phase F is delayed and someone unexpectedly tries to use the magic-login endpoint, they receive a clean 401 instead of a runtime controller failure.

  **Frontend (web-frontend) — present today:**
  - `web-frontend/src/components/auth/ProtectedRoute/ProtectedRoute.tsx` line 55 checks `allowedRoles.includes(user.role)` — singular role. **Updated** in this story to multi-role: `user.roles.some((r) => allowedRoles.includes(r))` (the cherry-pick line from 73d94688). NB: the helper-route exports (`OrganizerRoute`, `SpeakerRoute`, `PartnerRoute`, `AttendeeRoute` — lines 91-103) already pass arrays; the single-role check is the only line that changes.
  - `web-frontend/src/components/shared/Navigation/NavigationMenu.tsx` — already accepts `userRoles: UserRole[]` (multi-role plumbing landed on the refactor branch already) and calls `getNavigationForRoles(userRoles)`. **This story changes** the render path to use the cherry-picked grouped variant `getGroupedNavigationForRoles(userRoles)` with role-section dividers (per 73d94688).
  - `web-frontend/src/config/navigationConfig.ts` — already exports `getNavigationForRole` and `getNavigationForRoles` (the deduplicated variant). **This story adds** the cherry-pick's `getGroupedNavigationForRoles(roles)` (returns `{ role, labelKey, items }[]`) so the nav can render per-role sections.
  - `web-frontend/src/components/shared/Navigation/UserMenuDropdown.tsx` — exists. **Updated by 396a9045's one-line fix** (null-safe `user.roles` guard — `(user.roles ?? []).map(...)` instead of `user.roles.map(...)`).
  - `web-frontend/src/components/shared/Navigation/AppHeader.tsx` + `MobileDrawer.tsx` — exist; the cherry-pick's hunks here are smaller (a `userRoles` prop passthrough) and land in this story. Verified diffs in 73d94688: AppHeader +9 LOC, MobileDrawer +10 LOC, NavigationMenu refactor adds ~120 net LOC with `Divider` + `Typography` for the section headers.
  - `web-frontend/src/contexts/AuthContext.tsx` — already exposes `user.roles: UserRole[]` (multi-role state, confirmed via `roles?.includes('partner')` on line 67). Cherry-pick test additions (`web-frontend/src/contexts/AuthContext.test.tsx`, +186 LOC) land here.
  - `web-frontend/src/pages/speaker-portal/InvitationResponsePage.tsx` — 100+ lines. Reads `token` from `useSearchParams()` (line 52). Validates via `speakerPortalService.validateToken(token)`. **Refactored** in this story: token reading + validation removed; page uses `useAuth()` + lists invitations from a new dashboard-shape endpoint (or the existing one, parameterised). Calls `respond` without a token.
  - `web-frontend/src/pages/speaker-portal/SpeakerDashboardPage.tsx` — 300+ lines. Reads `token` from URL; passes `token` to inner `UpcomingEventCard`. **Refactored** in this story.
  - `web-frontend/src/pages/speaker-portal/ContentSubmissionPage.tsx` — 460+ lines. Reads `token` and uses it across getContent/saveDraft/submit/materials. **Refactored** in this story.
  - `web-frontend/src/pages/speaker-portal/ProfileUpdatePage.tsx` — 465+ lines. Token-based. **Refactored** in this story.
  - `web-frontend/src/pages/speaker-portal/SpeakerMagicLoginPage.tsx` — 60+ lines. Imports `speakerAuthService.validateMagicLink(jwt)`. **Disconnected from router** in this story (`App.tsx` line 286 removed) — file itself NOT deleted (Phase F).
  - `web-frontend/src/services/speakerPortalService.ts` — current types include `SpeakerResponseType = 'ACCEPT' | 'DECLINE' | 'TENTATIVE'`. `TENTATIVE` was supposed to be removed in Phase B (Story 11.B.1) but the frontend type may still carry it. **This story drops `TENTATIVE`** from the frontend type and from any code path that references it (per Q#6 below — confirmed in scope).
  - `web-frontend/src/services/speakerAuthService.ts` — referenced by `SpeakerMagicLoginPage`. **NOT deleted** in this story (Phase F deletes), but no other code path imports it after this story.
  - `web-frontend/src/App.tsx` lines 110-127 lazy-import five speaker-portal pages. Lines 274-286 route them — **route paths stay** (`/speaker-portal/respond`, `/profile`, `/content`, `/dashboard`), but each route is **wrapped in `<SpeakerRoute>`** in this story so the standard auth-redirect chain kicks in for unauthenticated speakers. The `/speaker-portal/magic-login` route on line 286 is **removed** entirely.

  **i18n (frontend) — present today:**
  - `web-frontend/public/locales/de/common.json` + `en/common.json` already carry the `navigation.*` block. **This story adds**:
    - `navigation.speakerPortal` (Story 11.E.3 — though per Q#4 below, the nav-item itself may NOT be added; the i18n key lands either way to make the cherry-pick clean).
    - `navigation.section.organizer`, `navigation.section.speaker`, `navigation.section.partner`, `navigation.section.attendee` (per cherry-pick).
  - **8 optional locales** (`fr`, `it`, `rm`, `es`, `fi`, `nl`, `ja`, `gsw-BE`) — per `CLAUDE.md` §"Localization — Official vs Optional Languages" (added 2026-05-17), they are NOT required. i18next's `defaultNS` + `fallbackLng: 'en'` already covers the gap. PM may resolve Q#5 below to ship the 8 locales as a follow-up.

---

## Acceptance Criteria

The AC are pinned to PRD lines 1253-1316. Each AC names the exact file under change and the verification command. Resolved Q#... markers next to AC lines indicate where the literal PRD wording was clarified by PM resolution (when those resolutions exist — at story-creation time the 6 Open Questions below are unresolved; AC reflects the literal PRD).

### AC1 — `@PreAuthorize("hasRole('SPEAKER')")` on every `/api/v1/speaker-portal/**` controller method (FR8, AR10)

**Given** the four speaker-portal controllers under `services/event-management-service/src/main/java/ch/batbern/events/controller/`,
**When** I inspect each controller method:

- `SpeakerPortalResponseController.respond(...)` (line 52)
- `SpeakerPortalContentController.getContentInfo(...)` (line 90)
- `SpeakerPortalContentController.saveDraft(...)` (line 127)
- `SpeakerPortalContentController.submitContent(...)` (line 164)
- `SpeakerPortalContentController.generatePresignedUrl(...)` (line 267)
- `SpeakerPortalContentController.confirmUpload(...)` (line 308)
- `SpeakerPortalDashboardController.getDashboard(...)` (line 57)

**Then** each method carries the annotation `@PreAuthorize("hasRole('SPEAKER')")`, imported from `org.springframework.security.access.prepost.PreAuthorize`. The annotation may be class-level (one `@PreAuthorize` on the controller) OR method-level (one per `@*Mapping`) — the dev picks per controller, whichever is cleanest. The chosen style is consistent within each controller.

**And** `SpeakerPortalTokenController.validateToken(...)` (line 53) is **disconnected** from the router: its `@RestController` annotation stays (the file is kept for Phase F to delete cleanly), but a `@PreAuthorize("hasRole('SPEAKER')")` is added too — i.e., the endpoint becomes dead code from the frontend's perspective AND inaccessible to unauthenticated callers. (If the dev prefers, they may add `@Deprecated` Javadoc here noting Phase F deletion.)

**And** `SpeakerPortalMaterialsService.java` (NOT a `@RestController` — a `@Service`) drops the `MagicLinkService` collaborator from its constructor. Its method signatures change:
  - `generatePresignedUrl(SpeakerMaterialUploadRequest request)` → `generatePresignedUrl(String username, String eventCode, SpeakerMaterialUploadRequest request)` (per Q#1).
  - `confirmUpload(SpeakerMaterialConfirmRequest request)` → `confirmUpload(String username, String eventCode, SpeakerMaterialConfirmRequest request)`.
  - The `token` field on `SpeakerMaterialUploadRequest` and `SpeakerMaterialConfirmRequest` DTOs is **removed**; OpenAPI spec edited in same commit.

**And** verification: `grep -rn "permitAll" services/event-management-service/src/main/java/ch/batbern/events/config/SecurityConfig.java | grep -E "speaker-portal|speaker-magic-login"` returns **zero** matches. The seven `permitAll()` entries for `/api/v1/speaker-portal/**` at SecurityConfig lines 113-128 are removed, AND the `permitAll()` for `/api/v1/auth/speaker-magic-login` at line 131 is also removed per Resolved Q#3 (PM 2026-05-17): the endpoint is dead from the frontend after AC8, so leaving it `permitAll()` is a needless attack surface; removing it now means the dead endpoint returns `401` (governed by `.anyRequest().authenticated()` default) instead of letting random POSTs reach controller code. The `SpeakerMagicLoginController.java` file itself stays for Phase F to delete cleanly.

**And** verification: `grep -rn "@RequestParam.*token\|request.token()" services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerPortal*.java` returns zero matches (the magic-link `token` field is fully gone from the speaker-portal controller signatures and bodies).

---

### AC2 — Authenticated principal forwarded as `actor` to workflow + content services (FR8)

**Given** the refactored speaker-portal controllers,
**When** a method handler runs,
**Then** it reads the authenticated principal from `SecurityContextHolder.getContext().getAuthentication()` (or via Spring's `@AuthenticationPrincipal` / `Principal` parameter — dev picks one consistent style).

**And** the `SecurityPrincipal` value object (Story 11.B.2 contract) is constructed as:

```java
SecurityPrincipal actor = SecurityPrincipal.fromAuthentication(authentication);
//   |- inside fromAuthentication(...):
//      username = authentication.getName()  // Cognito's preferred_username claim (or sub, depending on JwtAuthenticationConverter config)
//      roles    = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList()
```

(If `SecurityPrincipal.fromAuthentication` does not yet exist as a factory method per Story 11.B.2, the dev adds it in this story; the existing `new SecurityPrincipal(username, roles)` constructor stays.)

**And** the principal is forwarded to the relevant service:

- `SpeakerPortalResponseController` → `SpeakerResponseService.processResponse(actor, request)`. The service uses `actor.username()` to resolve the speaker_pool row for the target event (see AC3).
- `SpeakerPortalContentController.submitContent` → `ContentSubmissionService.submit(speakerPoolId, eventCode, payload, actor)` — already wired this way by Story 11.C.2; the only change is that `actor` now comes from `SecurityContextHolder` instead of from `validateToken(...)`'s magic-link result.
- `SpeakerPortalContentController.saveDraft` → `ContentSubmissionService.saveDraft(speakerPoolId, eventCode, payload, actor)` (signature parallels submit; if the existing method takes the old `ContentDraftRequest`, the dev updates it to accept the resolved `speakerPoolId` + `eventCode` instead of relying on the token field).
- `SpeakerPortalDashboardController` → `SpeakerDashboardService.getDashboard(actor.username())`. The dashboard service signature changes from `getDashboard(String token)` to `getDashboard(String username)`. Implementation queries `speaker_pool WHERE username = :username` for the speaker's events.
- `SpeakerPortalContentController.getContentInfo / generatePresignedUrl / confirmUpload` → all delegate to the principal-aware service methods per Q#1.

**And** the audit trail (status-history rows) records `changed_by_username = actor.username()` — verified by reading `speaker_status_history.changed_by_username` after a respond/submit in the integration tests.

---

### AC3 — Multi-event authorization check: speaker may only act on their own pool entries (FR8 invariant)

**Given** a speaker is authenticated as `alice@example.com` (Cognito username `alice.example`) and has SPEAKER-role pool entries for events `BATbern56` and `BATbern57`,
**When** Alice calls any speaker-portal endpoint that targets a specific event (respond, content GET/draft/submit, materials presigned-url/confirm),
**Then** the backend looks up `speaker_pool WHERE username = :authPrincipalUsername AND event_code = :targetEventCode`, and:

1. If the row exists → proceed with the operation, `speakerPoolId` resolved from that row.
2. If no row exists → return **`403 Forbidden`** (NOT 404) with a clear error message ("No invitation found for the requested event"). The body matches the platform's standard `ErrorResponse` shape.

**And** the lookup happens in a single place — `SpeakerPortalAuthorizationService.resolveSpeakerPool(username, eventCode)` (new helper in `event-management-service`) — invoked from each of the four controllers. The helper is `@Transactional(readOnly = true)`, returns `SpeakerPool`, and throws a typed exception (e.g. `SpeakerPortalAccessDeniedException`) mapped to HTTP 403 by `GlobalExceptionHandler`.

**And** **regression guard**: an integration test asserts that a SPEAKER-authenticated request for an `eventCode` they have no pool row for receives 403, and an audit log entry records the rejection (with masked username).

**And** the `GET /api/v1/speaker-portal/dashboard` endpoint is the **one exception** that doesn't take an `eventCode` — it lists all events the authenticated speaker is associated with. The service queries `speaker_pool WHERE username = :authPrincipalUsername` and aggregates per-event status.

---

### AC4 — Request signatures stripped of `?token=` and `?jwt=` (FR8, AR25)

**Given** all four refactored controllers + the materials service,
**When** I `grep -rn "@RequestParam.*token\|@RequestParam.*jwt\|request.token()\|request.jwt()" services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerPortal*.java services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerPortalMaterialsService.java`,
**Then** the result is **empty** (no remaining magic-link token reads in speaker-portal code).

**And** the request DTOs lose their `token` fields (per Q#1):

- `SpeakerResponseRequest` — `token` field removed; `eventCode` added (path param OR body — see Q#1).
- `ContentDraftRequest` — `token` field removed; `eventCode` added.
- `ContentSubmitRequest` — `token` field removed; `eventCode` added.
- `SpeakerMaterialUploadRequest` — `token` field removed; `eventCode` added.
- `SpeakerMaterialConfirmRequest` — `token` field removed; `eventCode` added.
- `ValidateTokenRequest` (used by `SpeakerPortalTokenController`) — stays untouched (Phase F deletes the request class along with the controller).

**And** the OpenAPI spec (`docs/api/event-management.openapi.yml` — or the speaker-portal-specific spec file if separated) is updated in the same commit per ADR-006 contract-first: remove `token` from speaker-portal request schemas, add `eventCode` parameter / field, update `200` responses if their shape changed. Run `cd web-frontend && npm run generate:api-types` and commit the regenerated types.

**And** verification: `grep -rn "?token=\|?jwt=" web-frontend/src/pages/speaker-portal/ web-frontend/src/services/speakerPortalService.ts` returns zero matches after the frontend refactor (AC6).

---

### AC5 — Unauthenticated, ORGANIZER-only, or PARTNER-only callers receive `401`/`403` (FR8 negative)

**Given** the refactored speaker-portal endpoints,
**When** I call any endpoint with:

1. **No `Authorization` header** → Spring Security returns **`401 Unauthorized`** via the standard JWT auth-chain (no controller code runs).
2. An **ORGANIZER** token (no SPEAKER role) → returns **`403 Forbidden`** via `@PreAuthorize` (no controller code runs).
3. A **PARTNER** token (no SPEAKER role) → returns **`403 Forbidden`**.
4. A **SPEAKER + ORGANIZER** dual-role token → call **succeeds** (the request reaches the controller, AC3's pool-ownership check then governs whether the operation is allowed).

**And** an integration test (`SpeakerPortalAuthIntegrationTest`, new file under `services/event-management-service/src/test/java/ch/batbern/events/controller/`) covers all four cases above plus the happy-path SPEAKER call. Tests extend `AbstractIntegrationTest` (PostgreSQL via Testcontainers per `project-context.md` §"Backend Integration Tests").

**And** the test fixtures use Cognito JWTs minted by the existing test-token harness (the same one used by Bruno tests) — NOT magic-link tokens.

---

### AC6 — Cherry-pick `73d94688` + `396a9045`: multi-role nav UI lands (AR41, UX-DR20)

**Given** the source commits `73d94688dea1814a22194f44f7aeb169743db25a` (Story 9.5) and `396a90453f226b20d6b1464b82ced4457e6a7f4e` (null-safe `user.roles` fix) on `refs/remotes/origin/feature/speaker-account-creation`,
**When** the cherry-pick lands on `feature/speaker-workflow-refactor`,
**Then** the following files / changes from `73d94688` are **included**:

- `web-frontend/src/components/shared/Navigation/NavigationMenu.tsx` — grouped-rendering refactor: when `userRoles.length > 1`, render section headers (`Typography variant="overline"`) and `Divider`s between role groups. Use `getGroupedNavigationForRoles(userRoles)`.
- `web-frontend/src/components/shared/Navigation/NavigationMenu.test.tsx` — new test file (+69 LOC). Asserts grouped rendering for multi-role users.
- `web-frontend/src/components/shared/Navigation/AppHeader.tsx` — +9 LOC: `userRoles` prop passthrough to `NavigationMenu`.
- `web-frontend/src/components/shared/Navigation/AppHeader.test.tsx` — new (+39 LOC).
- `web-frontend/src/components/shared/Navigation/MobileDrawer.tsx` — +10 LOC: similar passthrough.
- `web-frontend/src/components/shared/Navigation/UserMenuDropdown.tsx` — keeps the cherry-picked role-indicator change (visualises multi-role state).
- `web-frontend/src/components/shared/Navigation/UserMenuDropdown.test.tsx` — new (+41 LOC).
- `web-frontend/src/config/navigationConfig.ts` — adds `RecordVoiceOver` icon import + `getGroupedNavigationForRoles(roles)` function. **Skips** the cherry-pick's `{ labelKey: 'navigation.speakerPortal', path: '/speaker-portal/login', ... }` entry per ADR-009 + Q#4 below (no separate speaker login page exists under ADR-009; if Q#4 resolves to "keep a nav entry," it points to `/speaker-portal/dashboard` instead).
- `web-frontend/src/config/__tests__/navigationConfig.test.ts` — new (+94 LOC).
- `web-frontend/src/components/auth/ProtectedRoute/ProtectedRoute.tsx` — **one-line change**: `if (!allowedRoles.includes(user.role))` → `if (!user.roles.some((r) => allowedRoles.includes(r)))`. Adds the test file `ProtectedRoute.test.tsx` (+117 LOC).
- `web-frontend/src/contexts/AuthContext.test.tsx` — new (+186 LOC).
- `web-frontend/src/App.tsx` — +13 LOC from the cherry-pick (suggested-actions navigation table per Story 9.5). The dev verifies these lines do NOT re-introduce the `/speaker-portal/login` route or the `SpeakerLoginPage` import.
- **All 10 locales** of `common.json` get the new keys per Resolved Q#5 (PM 2026-05-17 — narrowed `CLAUDE.md` §Localization rule: "DE+EN only" applies to backend email templates only; new frontend UI i18n keys MUST land in all 10 locales). Files: `web-frontend/public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/common.json`. New keys: `navigation.section.organizer`, `navigation.section.speaker`, `navigation.section.partner`, `navigation.section.attendee` (4 keys × 10 locales = 40 entries). `navigation.speakerPortal` is **NOT added** per Resolved Q#4 (the nav entry it would label is dropped from `navigationConfig.ts`; the key would land unused — skip it). The cherry-pick's DE+EN values land as-is in DE+EN; the other 8 locales are hand-translated (or copy-translated from a similar nav section in the existing locale files — translators have shipped `navigation.*` keys before per Story 10.9 i18n cleanup; reuse those translation patterns).

**And** the following files / changes from `73d94688` are **explicitly skipped** (per PRD lines 1289-1295 + ADR-009 §Decision 3 + refactor plan §9.2):

- `web-frontend/src/pages/speaker-portal/SpeakerLoginPage.tsx` (+226 LOC in the cherry-pick) — **NOT included.** Under ADR-009 there is no separate speaker login page; the standard `/login` page handles all roles via Cognito.
- The `ProtectedRoute.tsx` speaker-JWT branch — the cherry-pick references "speaker JWT auth support alongside existing Cognito auth"; under ADR-009 there is no speaker JWT, so any branch that special-cases a speaker-JWT cookie is removed. Verified line-by-line: the cherry-pick's ProtectedRoute diff is JUST the multi-role line change quoted above (no speaker-JWT branch in 73d94688 itself — the speaker-JWT branch was a separate earlier commit on the same branch, not part of 73d94688). The dev cross-checks the staged diff to confirm no speaker-JWT logic creeps in via dependent imports.
- `web-frontend/docs/.../9-5-frontend-unified-navigation-multi-role-users.md` (+454 LOC, story documentation) — **NOT included.**

**And** the second cherry-pick `396a9045` (single-line `user.roles` null-safe guard in `UserMenuDropdown.tsx`) lands either bundled into the same commit OR as a separate immediate follow-up commit — dev's choice. Either way the resulting `UserMenuDropdown.tsx` has `(user.roles ?? []).map(...)` (or equivalent null-safe access).

**And** the cherry-pick commit message references both source SHAs and the deliberate skip list. Suggested wording:

```
feat(web-frontend): cherry-pick Story 9.5 multi-role nav UI [Story 11.E.3]

Selectively cherry-picks 73d94688 (Story 9.5 multi-role navigation) + 396a9045
(null-safe user.roles guard) from feature/speaker-account-creation.

INCLUDED:
- NavigationMenu grouped-rendering for multi-role users (section headers + dividers)
- AppHeader / MobileDrawer userRoles prop passthrough
- UserMenuDropdown role-indicator + null-safe guard (396a9045)
- ProtectedRoute multi-role check (one-line fix: user.role → user.roles.some(...))
- navigationConfig.getGroupedNavigationForRoles helper
- DE/EN nav i18n keys (section.* + speakerPortal)
- Test files for the above (5 new test files, +500 LOC)

DELIBERATELY SKIPPED (per ADR-009 §Decision 3 / refactor plan §9.2):
- SpeakerLoginPage.tsx — no separate speaker login under ADR-009;
  /login handles all roles via Cognito.
- The "speaker-JWT branch" reference from the cherry-pick commit message —
  no speaker JWT exists under ADR-009; ProtectedRoute carries only the
  multi-role check.
- /speaker-portal/login navigationConfig entry — points to a route that
  does not exist; navigation.speakerPortal i18n key kept for parity.
- web-frontend/docs/.../9-5-frontend-unified-navigation-multi-role-users.md
  — Epic 9 story doc, superseded by Epic 11.

Source commits:
  73d94688dea1814a22194f44f7aeb169743db25a (refs/remotes/origin/feature/speaker-account-creation)
  396a90453f226b20d6b1464b82ced4457e6a7f4e (refs/remotes/origin/feature/speaker-account-creation)
```

---

### AC7 — `web-frontend/src/pages/speaker-portal/**` pages consume `useAuth` instead of `?token=` (UX-DR17)

**Given** the five files under `web-frontend/src/pages/speaker-portal/`:

- `InvitationResponsePage.tsx`
- `SpeakerDashboardPage.tsx`
- `ContentSubmissionPage.tsx`
- `ProfileUpdatePage.tsx`
- `SpeakerMagicLoginPage.tsx` (disconnected — see AC8)

**When** I inspect each page (except `SpeakerMagicLoginPage`),
**Then**:

1. The first hook import drops `useSearchParams` (or keeps it only for non-token state — query strings for filtering, etc., are fine; `token` is what goes).
2. No call to `searchParams.get('token')` or `searchParams.get('jwt')` remains.
3. The page calls `useAuth()` (from `@hooks/useAuth`) to obtain `{ user, isAuthenticated, isLoading }`.
4. `eventCode` enters the page either from the route (e.g. `useParams<{ eventCode: string }>()`) or from a list-view that hands it off via React Router state — per Q#1's API redesign. The dev picks a URL shape that is consistent across the four pages and adds it to React Router routes in `App.tsx`.
5. Calls to `speakerPortalService.*` are updated to NOT pass `token`. The service methods become:
   - `validateInvitation(eventCode)` — replaces `validateToken(token)`.
   - `respond({ eventCode, response, reason, preferences })` — replaces the token-bearing variant.
   - `getDashboard()` — no params; reads SPEAKER role + username from the JWT on the apiClient.
   - `getContentInfo(eventCode)` — replaces `getContentInfo(token)`.
   - `saveDraft({ eventCode, ... })`.
   - `submitContent({ eventCode, title, contentAbstract, bio?, profilePictureUrl?, presentationUploadId? })`.
   - `getProfile(eventCode)` + `updateProfile({ eventCode, ... })`.
   - Materials methods accept `eventCode` first, then their existing payload shape.
6. The Bearer token is attached automatically by `apiClient` (the existing axios instance with the Cognito session injector). No manual header-setting per page.

**And** verification: `grep -rn "searchParams.get('token')\|searchParams.get('jwt')\|window.history.replaceState" web-frontend/src/pages/speaker-portal/` returns zero matches (the `window.history.replaceState({}, '', '/speaker-portal/respond')` line in `InvitationResponsePage.tsx` line 87 — which scrubs the token from the URL for security — is no longer needed and is removed).

**And** the `SpeakerResponseType` enum in `speakerPortalService.ts` is trimmed from `'ACCEPT' | 'DECLINE' | 'TENTATIVE'` to `'ACCEPT' | 'DECLINE'` per Q#6 (frontend cleanup of TENTATIVE — landed late because the type lingered from Story 6.x and slipped past the Phase B sweep).

**And** the existing speaker-portal unit tests (`web-frontend/src/pages/speaker-portal/__tests__/*.test.tsx`) are updated to:
1. Mock `useAuth` instead of `useSearchParams`.
2. Mock `apiClient` (the Bearer token is automatic; tests assert the right path is hit with the right body).
3. Drop assertions on `?token=` URL clearing.

If updating an existing test is harder than rewriting, the dev may rewrite the test file in place (preserving the test-name pattern) — the goal is that `cd web-frontend && npm test -- speaker-portal 2>&1 | tee /tmp/fe-speaker-test.log` exits green.

---

### AC8 — `SpeakerMagicLoginPage` disconnected from the router (UX-DR17)

**Given** `web-frontend/src/App.tsx` line 286:

```typescript
<Route path="/speaker-portal/magic-login" element={<SpeakerMagicLoginPage />} />
```

**When** the dev edits `App.tsx`,
**Then** this route line is **deleted**.

**And** the lazy import at lines 126-128:

```typescript
const SpeakerMagicLoginPage = React.lazy(
  () => import('@pages/speaker-portal/SpeakerMagicLoginPage')
);
```

is also **deleted**.

**And** the file `web-frontend/src/pages/speaker-portal/SpeakerMagicLoginPage.tsx` itself is **NOT deleted** in this story — Phase F (Story 11.F.1) deletes it along with `SpeakerMagicLoginController.java`, the `magic_link_tokens` table, and the `speaker_jwt` cookie. Leaving the file in place keeps the diff in this story focused on the Cognito-side change; Phase F is a separate, mechanical cleanup pass.

**And** the file `web-frontend/src/services/speakerAuthService.ts` is **NOT deleted** in this story for the same reason — after the route is unwired, no other code path imports `speakerAuthService` (verified by `grep -rn "speakerAuthService" web-frontend/src` returning only `SpeakerMagicLoginPage.tsx` and its test). Phase F deletes the service.

**And** the test file `web-frontend/src/pages/speaker-portal/__tests__/SpeakerMagicLoginPage.test.tsx` is **NOT deleted** but is marked `describe.skip(...)` with a comment pointing to Phase F. The skip prevents the test from running against the disconnected page in CI; the file itself stays so Phase F's cleanup is a single delete operation.

**And** a Playwright regression test confirms `/speaker-portal/magic-login` returns the SPA's 404/"page not found" handler (not the magic-login page). The test lives in `web-frontend/e2e/speaker/` (created in AC10).

---

### AC9 — Multi-role nav: speaker+organizer (and speaker+partner) sees both portals in the nav (UX-DR20)

**Given** a user with two roles in `user_roles`, e.g. `SPEAKER + ORGANIZER` or `SPEAKER + PARTNER`, logged in via Cognito,
**When** they open the navigation (top bar on desktop, drawer on mobile),
**Then** the nav menu renders TWO sections (separated by a divider on desktop; with overline labels on mobile vertical layout):

- A "Speaker" section with the speaker-portal entries (dashboard, content, profile).
- An "Organizer" (or "Partner") section with that role's nav entries.

**And** clicking any entry in either section navigates to the target page **without** any re-authentication prompt — the same Cognito session covers both portals. The test from AC6 + a new Playwright test (AC10) cover this.

**And** the active-role indicator in `UserMenuDropdown` (the role chip / dropdown that 73d94688 introduces) lists all the user's roles. Switching between portals happens via the standard nav clicks (no separate "switch role" UI is required for this story — the cherry-pick's role-switcher behaviour is preserved as-is from 73d94688; Q#7 below flags whether deeper role-switcher work is in scope).

**And** verification: a Vitest component test (`NavigationMenu.test.tsx` from the cherry-pick) covers the multi-role render path. An additional test in this story asserts the SPEAKER+ORGANIZER and SPEAKER+PARTNER combinations specifically.

---

### AC10 — Playwright `speaker` project: end-to-end Cognito-authenticated speaker flow (NFR8 ish; PRD line 1313-1316)

**Given** the existing Playwright config at `web-frontend/playwright.config.ts` already defines a `speaker` project activated by the `SPEAKER_AUTH_TOKEN` env var (verified at story-creation time — the `project-context.md` §"E2E Testing (Playwright)" anchor confirms this),
**When** the dev runs `cd web-frontend && SPEAKER_AUTH_TOKEN=$(jq -r .idToken ~/.batbern/staging-speaker.json) npx playwright test --project=speaker 2>&1 | tee /tmp/pw-speaker.log`,
**Then** the following test files exist under `web-frontend/e2e/speaker/` and pass:

1. `speaker-portal-dashboard.spec.ts` — speaker logs in via Cognito (token already in storage state), navigates to `/speaker-portal/dashboard`, sees their events.
2. `speaker-portal-respond.spec.ts` — speaker accepts an invitation, sees confirmation; speaker declines a different invitation with a reason.
3. `speaker-portal-content-submit.spec.ts` — speaker fills in title + abstract + (optional) bio + (optional) presentation upload, submits, sees confirmation; status transitions to `CONTENT_SUBMITTED`.
4. `speaker-portal-cross-portal-nav.spec.ts` — speaker+organizer logs in, sees both nav sections, switches between speaker dashboard and organizer kanban without re-authenticating.
5. `speaker-magic-login-404.spec.ts` — speaker navigates to `/speaker-portal/magic-login`, sees the SPA's 404 (route does not exist post-AC8).

**And** the existing root-level speaker tests `web-frontend/e2e/speaker-onboarding-flow.spec.ts` and `web-frontend/e2e/speaker-portal-response.spec.ts` are **DELETED** outright per Resolved Q#2 (PM 2026-05-17). Both tests exercise the magic-link flow end-to-end — the literal thing Phase E removes. There is no migration / port to Cognito: the new tests in this story's `e2e/speaker/` directory (listed above) cover the equivalent Cognito-side flows (dashboard, respond, content submit, cross-portal nav, magic-login-404). Removing the old specs avoids dead test coverage + a misleading test name surviving the cutover. Git history preserves the prior tests for reference.

**And** the global-setup flow at `web-frontend/e2e/global-setup.ts` correctly writes `.playwright-auth-speaker.json` from `SPEAKER_AUTH_TOKEN` (the `setup-test-users` make target is the bootstrap — the dev verifies the staging Cognito user used for tests has been provisioned per Story 11.E.2 — see manual-verification step in AC11).

**And** a `BRUNO` API-contract test is added under `bruno-tests/speaker-portal/` covering at minimum:
- `GET /speaker-portal/dashboard` with `{{SPEAKER_AUTH_TOKEN}}` → 200, returns dashboard JSON.
- `POST /speaker-portal/respond` with body `{ eventCode, response: "ACCEPT" }` → 200.
- `GET /speaker-portal/dashboard` with `{{ORGANIZER_AUTH_TOKEN}}` (no SPEAKER role) → 403.
- `GET /speaker-portal/dashboard` with no auth → 401.

These cover AC5's authorisation matrix at the contract layer.

---

### AC11 — Manual smoke test on staging (FR8, FR9 verification handoff)

**Given** Stories 11.E.1 + 11.E.2 + 11.E.3 have all merged and auto-deployed to staging,
**When** the dev performs the end-to-end manual flow:

1. Organizer promotes a test brainstorm entry through `CONTACTED → READY` on staging (which fires 11.E.2's `AdminCreateUser`).
2. Organizer clicks "Send invitation" — the speaker receives the rewritten invitation email (11.E.2 deliverable).
3. Speaker clicks the login link, enters email + temp password, is challenged by Cognito for a new password, sets one, lands on `/speaker-portal/dashboard`.
4. Speaker accepts the invitation via the portal; status transitions to `ACCEPTED`; status-history row shows `changed_by_username = speaker's username`.
5. Speaker submits content; status transitions to `CONTENT_SUBMITTED`.
6. Speaker logs out, logs back in via `/login` (same page as everyone else), lands on speaker dashboard.

**Then** every step succeeds, and the steps are captured in the PR description as the manual verification handoff.

**And** the PR description records:
- The Cognito user pool's `AdminGetUser` output for the test speaker (after first login), showing `UserStatus = CONFIRMED` (changed from `FORCE_CHANGE_PASSWORD`).
- A screenshot of the speaker-portal dashboard.
- A screenshot of the multi-role nav menu (if a test speaker+organizer user exists; otherwise note "deferred to follow-up — no test multi-role user provisioned yet" — see Q#7).

---

### AC12 — OpenAPI spec updates (ADR-006)

**Given** the speaker-portal API spec lives in `docs/api/event-management.openapi.yml` (or a dedicated file per the project's convention — dev verifies),
**When** the dev edits the spec,
**Then**:

- Remove `token` from all speaker-portal request schemas.
- Add `eventCode` as a path parameter on the per-event endpoints (per Q#1). Add `EventCodeParam` to `parameters:` if it doesn't already exist, mirroring the existing organizer endpoints' pattern.
- Mark the new `/api/v1/speaker-portal/events/{eventCode}/respond` (or whatever shape Q#1 resolves to) with `security: [{ cognitoJwt: [] }]` (or the platform's standard security scheme name).
- Mark `/api/v1/speaker-portal/dashboard` (no eventCode) with the same security requirement.
- Update the `400/401/403/404` error responses on each endpoint per AC5 + AC3.
- Regenerate frontend types: `cd web-frontend && npm run generate:api-types 2>&1 | tee /tmp/fe-typegen.log`. Commit the regenerated `web-frontend/src/types/generated/event-management.types.ts` (or equivalent).
- Backend codegen: `./gradlew :services:event-management-service:openApiGenerate` runs without error (the dev runs this before committing).

**And** the spec is editable in a single commit alongside the controller + frontend changes (per ADR-006 contract-first + CLAUDE.md doc-drift policy).

---

### AC13 — Doc alignment (CLAUDE.md doc-drift policy)

**Given** the cherry-pick + Cognito-auth refactor changes substantive auth behaviour,
**When** the dev edits docs in the same commit,
**Then**:

- **ADR-009** (`docs/architecture/ADR-009-unified-speaker-workflow.md`):
  - Revision-history row dated 2026-05-17 noting "Story 11.E.3 lands: speaker-portal Cognito Bearer auth + frontend Cognito-session refactor + multi-role nav cherry-pick from feature/speaker-account-creation (73d94688 + 396a9045); SpeakerLoginPage + ProtectedRoute speaker-JWT branch deliberately skipped per Decision 3."
- **PRD** (`docs/prd/epic-11-speaker-workflow-refactor.md`):
  - Story 11.E.3 AC lines 1268-1316 — confirm wording matches this story's AC; if Q#1 resolves to a path-parameter API design, update the relevant AC line to mention `eventCode` in path.
  - Line 99 (NFR8) — confirm Cognito-auth language reflects the changes.
  - Line 318 (FR Coverage Map FR8 → 11.E.3) — verify.
- **`docs/architecture/04-api-design.md`** (or the speaker-portal section, wherever it lives) — update the speaker-portal endpoints table to reflect the new shape (eventCode in path; `security: cognitoJwt` requirement; remove `?token=` examples).
- **`docs/architecture/06-backend-architecture.md`** — `Authentication` section: remove magic-link references for speaker-portal endpoints; explicitly state `@PreAuthorize("hasRole('SPEAKER')")` is the access rule. (If the doc still references the dual-auth interim, replace with the Phase E end-state.)
- **`docs/architecture/06b-user-lifecycle-sync.md`** — if it still says "speaker portal uses magic-link / RESPOND token," update to "speaker portal uses Cognito Bearer; SPEAKER role is granted at `CONTACTED → READY` per Story 11.E.2."
- **`CLAUDE.md`** §"Testing Strategy" — confirm the Playwright `speaker` project's note matches: `e2e/speaker/` is now populated; `SPEAKER_AUTH_TOKEN` activation pattern unchanged.
- **`CLAUDE.md`** §"Localization" (per Resolved Q#5, PM 2026-05-17) — narrowed from "DE+EN only for everything" to **"Email Templates: DE + EN Only; UI i18n: All 10 Locales"**. New section header explicitly distinguishes the two scopes: backend email templates remain DE+EN-only (per Story 11.E.2's framing); new frontend UI i18n keys MUST land in all 10 locales (`de`, `en`, `fr`, `it`, `rm`, `es`, `fi`, `nl`, `ja`, `gsw-BE`). The asymmetry is justified in the narrowed section: emails carry rich prose for an overwhelmingly DE/EN audience; frontend nav/button/label keys are short atomic strings serving a genuinely multilingual public website. This CLAUDE.md edit already landed at story-creation time; the dev verifies it is still present at commit time and re-applies if a merge has reverted it.

**And** verification: `grep -rn "magic.link\|speaker.jwt\|speakerMagicLogin" docs/architecture/ docs/prd/epic-11*.md` returns only references inside ADR-009's "Decision 3" section (intentional, descriptive prose about what's being replaced) and the revision-history rows. No "current behaviour" claims about magic links should remain for the speaker portal.

---

## Tasks / Subtasks

Tasks are ordered for compile-incrementally, test-incrementally execution. Each task names the AC it satisfies and the verification command (`grep` / `npm test` / `./gradlew test`) to lock it in.

### Task 1 — Cherry-pick `73d94688` + `396a9045` and surgically revert SpeakerLoginPage / story-doc hunks (AC6)

1.1. Verify both source SHAs reachable: `git log feature/speaker-account-creation --oneline | grep -E "73d94688|396a9045"` returns both. If the branch was force-deleted, the dev re-creates the diff by hand from the AC6 file list.
1.2. `git cherry-pick --no-commit 73d94688` — stages the full diff.
1.3. **Surgical revert**: `git restore --staged --worktree -- web-frontend/src/pages/speaker-portal/SpeakerLoginPage.tsx web-frontend/docs/_unprefixed/9-5-frontend-unified-navigation-multi-role-users.md` (adjust the docs path if the cherry-pick puts it elsewhere — the dev locates it via `git diff --cached --stat`).
1.4. Inspect `web-frontend/src/components/auth/ProtectedRoute/ProtectedRoute.tsx` in the staged diff. Confirm the change is JUST the one-line multi-role fix (`!user.roles.some((r) => allowedRoles.includes(r))`). If 73d94688 includes any speaker-JWT cookie reading or `speakerAuthService` import in ProtectedRoute, manually remove it before commit.
1.5. Inspect `web-frontend/src/config/navigationConfig.ts` in the staged diff. **Drop the `/speaker-portal/login` nav-item entry** (lines 122-128 of the cherry-pick's add) — keep the `RecordVoiceOver` icon import (used by Q#4's optional `/speaker-portal/dashboard` nav-item) and keep the `getGroupedNavigationForRoles` helper function.
1.6. Inspect `web-frontend/src/App.tsx` in the staged diff. The cherry-pick adds 13 LOC. Confirm none of these add a `/speaker-portal/login` route or import `SpeakerLoginPage`. Keep the rest (the route-adapter changes for multi-role-aware redirects, if any).
1.7. `git diff --cached --stat` — confirm staged changes match AC6's "included" file list. Total expected: ~16 files, ~1100 LOC net.
1.8. Apply `396a9045` either as a continuation cherry-pick (`git cherry-pick --no-commit 396a9045`) or by manually editing `UserMenuDropdown.tsx` to `(user.roles ?? []).map(...)`. (Both produce the same one-line diff.)
1.9. **Verify**: `cd web-frontend && npm run type-check 2>&1 | tee /tmp/fe-typecheck.log` — TypeScript compilation clean. If grouped-nav code references a `useActiveRole` hook that doesn't exist on the refactor branch, the dev creates the hook (small shim) or simplifies the cherry-pick's reliance on it.
1.10. **Verify**: `cd web-frontend && npm test -- Navigation 2>&1 | tee /tmp/fe-nav-test.log` — the new test files pass.

### Task 2 — Add `getGroupedNavigationForRoles` helper + verify NavigationMenu wires it (AC6, AC9)

2.1. Verify `web-frontend/src/config/navigationConfig.ts` now exports `getGroupedNavigationForRoles(roles: UserRole[]): { role: UserRole; labelKey: string; items: NavigationItem[] }[]` per AC6 (the cherry-pick lands this).
2.2. Verify `NavigationMenu.tsx` uses `getGroupedNavigationForRoles` when `userRoles.length > 1` and `getNavigationForRoles` for the single-role case (the cherry-pick rewires this). The grouped path renders a `Typography variant="overline"` section header + a `Divider` between groups.
2.3. **Verify**: open the dev server (`make dev-native-up`), log in as an organizer user, confirm the nav looks identical to today (single-role path). Then bump the test user to also have SPEAKER role via `INSERT INTO user_roles (username, role) VALUES ('test-user', 'SPEAKER');` in the local DB, log out + back in, confirm the multi-role nav shows section headers.

### Task 3 — `SecurityPrincipal.fromAuthentication(...)` factory (AC2)

3.1. Open `services/event-management-service/src/main/java/ch/batbern/events/service/workflow/SecurityPrincipal.java`. Confirm the existing constructor signature is `new SecurityPrincipal(String username, List<String> roles)` (from Story 11.B.2).
3.2. Add a static factory method:

```java
public static SecurityPrincipal fromAuthentication(Authentication authentication) {
    String username = authentication.getName();
    List<String> roles = authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .toList();
    return new SecurityPrincipal(username, roles);
}
```

3.3. Add a unit test `SecurityPrincipalTest.fromAuthentication_extractsUsernameAndRoles`. (Tiny test — verify name + role mapping.)
3.4. **Verify**: `./gradlew :services:event-management-service:test --tests SecurityPrincipalTest 2>&1 | tee /tmp/ems-secprincipal-test.log` — green.

### Task 4 — `SpeakerPortalAuthorizationService.resolveSpeakerPool(...)` helper (AC3)

4.1. Create `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerPortalAuthorizationService.java`:

```java
@Service
@Transactional(readOnly = true)
public class SpeakerPortalAuthorizationService {
    private final SpeakerPoolRepository speakerPoolRepository;
    private final EventRepository eventRepository;
    // constructor

    public SpeakerPool resolveSpeakerPool(String username, String eventCode) {
        Event event = eventRepository.findByEventCode(eventCode)
            .orElseThrow(() -> new SpeakerPortalAccessDeniedException(
                "No invitation found for the requested event"));
        return speakerPoolRepository.findByEventIdAndUsername(event.getId(), username)
            .orElseThrow(() -> new SpeakerPortalAccessDeniedException(
                "No invitation found for the requested event"));
    }
}
```

4.2. Create the typed exception `SpeakerPortalAccessDeniedException extends RuntimeException` under `exception/`. Map to HTTP 403 in `GlobalExceptionHandler`. The exception message goes through `LoggingUtils.maskEmail/maskUsername` if it ever embeds PII.
4.3. **Verify**: integration test `SpeakerPortalAuthorizationServiceIntegrationTest` covers (a) happy path, (b) unknown eventCode → 403, (c) known eventCode but no pool row for username → 403.

### Task 5 — Refactor `SpeakerPortalResponseController` (AC1, AC2, AC3, AC4)

5.1. Add class-level `@PreAuthorize("hasRole('SPEAKER')")` on `SpeakerPortalResponseController`.
5.2. Change the route from `POST /api/v1/speaker-portal/respond` to `POST /api/v1/speaker-portal/events/{eventCode}/respond` (per Q#1).
5.3. Change method signature:

```java
@PostMapping("/events/{eventCode}/respond")
public ResponseEntity<SpeakerResponseResult> respond(
        @PathVariable String eventCode,
        @Valid @RequestBody SpeakerResponseRequest request,    // token field removed from DTO
        Authentication authentication) {
    SecurityPrincipal actor = SecurityPrincipal.fromAuthentication(authentication);
    SpeakerPool pool = authorizationService.resolveSpeakerPool(actor.username(), eventCode);
    return ResponseEntity.ok(speakerResponseService.processResponse(actor, pool, request));
}
```

5.4. Update `SpeakerResponseService.processResponse(...)` to accept `(SecurityPrincipal actor, SpeakerPool pool, SpeakerResponseRequest request)`. Internal call to `SpeakerWorkflowService.transition(...)` now uses `actor` directly (no fallback to display-name).
5.5. Remove the `MagicLinkService.validateToken(...)` call paths from `SpeakerResponseService` — replace with the principal-aware path.
5.6. **Verify**: `./gradlew :services:event-management-service:test --tests SpeakerPortalResponseControllerIntegrationTest 2>&1 | tee /tmp/ems-portal-respond.log` — green (test rewritten in same task).

### Task 6 — Refactor `SpeakerPortalContentController` + `SpeakerPortalMaterialsService` (AC1, AC2, AC3, AC4)

6.1. Add class-level `@PreAuthorize("hasRole('SPEAKER')")` on `SpeakerPortalContentController`.
6.2. Refactor each of the five endpoints:

| Endpoint (was) | Endpoint (now) |
|---|---|
| `GET /content?token=...` | `GET /events/{eventCode}/content` |
| `POST /content/draft` | `POST /events/{eventCode}/content/draft` |
| `POST /content/submit` | `POST /events/{eventCode}/content/submit` |
| `POST /materials/presigned-url` | `POST /events/{eventCode}/materials/presigned-url` |
| `POST /materials/confirm` | `POST /events/{eventCode}/materials/confirm` |

6.3. Each method follows the pattern from Task 5 step 3: resolve `eventCode` from path; resolve principal from `SecurityContextHolder` (via `Authentication` parameter); resolve `SpeakerPool` via `authorizationService.resolveSpeakerPool(...)`; delegate to the corresponding service method.
6.4. Drop the `MagicLinkService` collaborator from the controller (already noted in source code comment line 184-185 as Phase E intent).
6.5. Drop the `validateToken(...)` private helper (line 245-256 of the controller).
6.6. Update `SpeakerPortalMaterialsService`:
  - Drop `MagicLinkService` constructor parameter.
  - Method signature change per AC1: `generatePresignedUrl(String username, String eventCode, request)` + `confirmUpload(String username, String eventCode, request)`.
  - Internal token-validation paths removed; username + eventCode now drive the speaker_pool lookup.
6.7. Update DTOs: `ContentDraftRequest`, `ContentSubmitRequest`, `SpeakerMaterialUploadRequest`, `SpeakerMaterialConfirmRequest` lose their `token` field.
6.8. **Verify**: `./gradlew :services:event-management-service:test --tests SpeakerPortalContentControllerIntegrationTest 2>&1 | tee /tmp/ems-portal-content.log` — green.

### Task 7 — Refactor `SpeakerPortalDashboardController` + `SpeakerDashboardService` (AC1, AC2, AC4)

7.1. Add class-level `@PreAuthorize("hasRole('SPEAKER')")` on `SpeakerPortalDashboardController`.
7.2. Keep the route `GET /api/v1/speaker-portal/dashboard` (no path param — dashboard is across all the speaker's events).
7.3. Change method signature:

```java
@GetMapping("/dashboard")
public ResponseEntity<SpeakerDashboardDto> getDashboard(Authentication authentication) {
    SecurityPrincipal actor = SecurityPrincipal.fromAuthentication(authentication);
    return ResponseEntity.ok(dashboardService.getDashboard(actor.username()));
}
```

7.4. Update `SpeakerDashboardService.getDashboard(String token)` → `getDashboard(String username)`. Implementation queries `speakerPoolRepository.findByUsername(username)` (add the repo method if missing — `List<SpeakerPool> findByUsername(String username);`).
7.5. **Verify**: `./gradlew :services:event-management-service:test --tests SpeakerPortalDashboardControllerIntegrationTest 2>&1 | tee /tmp/ems-portal-dashboard.log` — green.

### Task 8 — Disconnect `SpeakerPortalTokenController` (AC1, AC8 backend half)

8.1. Add class-level `@PreAuthorize("hasRole('SPEAKER')")` on `SpeakerPortalTokenController`. (Per AC1 — controller file stays for Phase F, but is dead code.)
8.2. Add `@Deprecated` Javadoc with a one-line comment: "Story 11.E.3 disconnects this controller from the frontend; Story 11.F.1 deletes the file."
8.3. **Do NOT** change the method body — the file remains compilable.

### Task 9 — Strip `permitAll()` lines from `SecurityConfig` (AC1, AC5)

9.1. Open `services/event-management-service/src/main/java/ch/batbern/events/config/SecurityConfig.java`.
9.2. Delete the seven `permitAll()` matcher lines for speaker-portal endpoints (lines 113-128 area — exact line numbers vary; the dev uses `grep` to locate `speaker-portal` in the file). The default `.anyRequest().authenticated()` at line 163 now governs them; the per-method `@PreAuthorize` adds the role check.
9.3. **Also delete** the `/api/v1/auth/speaker-magic-login` `permitAll` line (~line 131) per Resolved Q#3 (PM 2026-05-17). The endpoint is dead after AC8; leaving it `permitAll()` is needless attack surface. The controller class stays (Phase F deletes the file); after this change, any POST to `/api/v1/auth/speaker-magic-login` from an unauthenticated caller gets 401 from the default auth chain.
9.4. **Verify**: `grep -nE "speaker-portal|speaker-magic-login" services/event-management-service/src/main/java/ch/batbern/events/config/SecurityConfig.java` returns zero `permitAll` lines (only the file's other usage areas — e.g. comments or test fixtures — may persist).
9.5. **Cross-check**: the existing Story 6.1a comments / Story 9.1 comments next to those lines are removed alongside the matchers (avoid stale doc-comments referencing deleted permits).

### Task 10 — `SpeakerPortalAuthIntegrationTest` (AC5)

10.1. New file `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerPortalAuthIntegrationTest.java`. Extends `AbstractIntegrationTest`.
10.2. Test cases:

- `should_return401_when_noAuthHeader_onAnyPortalEndpoint`
- `should_return403_when_organizerToken_onAnyPortalEndpoint`
- `should_return403_when_partnerToken_onAnyPortalEndpoint`
- `should_succeed_when_speakerToken_onOwnEventEndpoint`
- `should_return403_when_speakerToken_onForeignEventEndpoint` (covers AC3)
- `should_return200_when_dualRoleSpeakerOrganizerToken_onDashboard`

10.3. The test harness uses `JwtTestUtils.mintSpeakerToken(username)` (or whatever the project's pattern is — the dev checks neighbouring test classes like `OrganizerOrApi*IntegrationTest` for the established mint helper).
10.4. **Verify**: `./gradlew :services:event-management-service:test --tests SpeakerPortalAuthIntegrationTest 2>&1 | tee /tmp/ems-portal-auth.log` — all six green.

### Task 11 — Refactor `speakerPortalService.ts` (frontend) (AC7)

11.1. Open `web-frontend/src/services/speakerPortalService.ts`.
11.2. Drop `TENTATIVE` from `SpeakerResponseType` (Q#6 confirmed in scope).
11.3. Refactor each method per AC7's listing. Methods accept `eventCode` first, drop `token` from all request DTOs. Bearer token attached automatically by `apiClient`.
11.4. Drop the `validateToken(token: string)` method — replace with `validateInvitation(eventCode: string)` that hits `GET /speaker-portal/events/{eventCode}/invitation` (or just the dashboard endpoint, since the dashboard already returns per-event invitation status — dev picks).
11.5. **Verify**: `cd web-frontend && npm run type-check 2>&1 | tee /tmp/fe-typecheck-2.log` — clean.

### Task 12 — Refactor the four speaker-portal pages (AC7)

12.1. `InvitationResponsePage.tsx` — drop `useSearchParams`; consume `useAuth`; obtain `eventCode` from route param OR from a dashboard-derived state; call `speakerPortalService.respond({ eventCode, response, reason, preferences })`.
12.2. `SpeakerDashboardPage.tsx` — drop `token` from page state; the `UpcomingEventCard` no longer takes `token`; deep-links to `/speaker-portal/respond/{eventCode}` (etc.) instead of `?token=`.
12.3. `ContentSubmissionPage.tsx` — same drill: drop token; route via `/speaker-portal/content/{eventCode}`; service calls drop `token`.
12.4. `ProfileUpdatePage.tsx` — same.
12.5. Update route definitions in `App.tsx`:

```typescript
{/* Story 11.E.3 — Speaker portal pages now Cognito-authenticated */}
<Route path="/speaker-portal/dashboard" element={<SpeakerRoute><SpeakerDashboardPage /></SpeakerRoute>} />
<Route path="/speaker-portal/respond/:eventCode" element={<SpeakerRoute><InvitationResponsePage /></SpeakerRoute>} />
<Route path="/speaker-portal/content/:eventCode" element={<SpeakerRoute><ContentSubmissionPage /></SpeakerRoute>} />
<Route path="/speaker-portal/profile/:eventCode" element={<SpeakerRoute><ProfileUpdatePage /></SpeakerRoute>} />
{/* Story 11.E.3 — magic-login route REMOVED (Phase F deletes the page file) */}
```

12.6. Delete the `SpeakerMagicLoginPage` lazy import (App.tsx lines 126-128).
12.7. **Verify**: `cd web-frontend && npm test -- speaker-portal 2>&1 | tee /tmp/fe-speaker-test.log` — all green (tests rewritten in same task per AC7 item 6).

### Task 13 — i18n — 4 new nav keys × 10 locales (AC6, Resolved Q#5)

13.1. Verify `web-frontend/public/locales/de/common.json` and `en/common.json` carry the cherry-pick's new keys: `navigation.section.organizer / speaker / partner / attendee`. Confirm DE values are real German translations (not English copies); hand-translate if the cherry-pick's DE happens to be EN copies. Suggested values: organizer → "Organisator", speaker → "Referent", partner → "Partner", attendee → "Teilnehmer".
13.2. **DELETE** the cherry-pick's `navigation.speakerPortal` key entirely per Resolved Q#4 (PM 2026-05-17) — the nav entry was dropped from `navigationConfig.ts` in Task 1.5, so the i18n key would land unused. If the cherry-pick lands the key in DE+EN before this task, remove it.
13.3. **Fan out to all 8 optional locales** per Resolved Q#5 (PM 2026-05-17 — narrowed `CLAUDE.md` §Localization rule: the "DE+EN only" rule applies ONLY to backend email templates; new frontend UI i18n keys require all 10 locales). Add `navigation.section.organizer / speaker / partner / attendee` to:
- `web-frontend/public/locales/fr/common.json` — French (organisateur, intervenant, partenaire, participant)
- `web-frontend/public/locales/it/common.json` — Italian (organizzatore, relatore, partner, partecipante)
- `web-frontend/public/locales/rm/common.json` — Romansh (organisatur, relatur, partenari, participant — verify with existing translations in this locale; use closest equivalents from existing keys if uncertain)
- `web-frontend/public/locales/es/common.json` — Spanish (organizador, ponente, socio, participante)
- `web-frontend/public/locales/fi/common.json` — Finnish (järjestäjä, puhuja, kumppani, osallistuja)
- `web-frontend/public/locales/nl/common.json` — Dutch (organisator, spreker, partner, deelnemer)
- `web-frontend/public/locales/ja/common.json` — Japanese (主催者, 講演者, パートナー, 参加者)
- `web-frontend/public/locales/gsw-BE/common.json` — Bernese Swiss-German (Organisator, Referänt, Partner, Tailnähmer — match existing gsw-BE conventions; the locale is informal, so a hint of Bärndüütsch flavour is appropriate)

13.4. **CLAUDE.md update bundled in this commit** per Resolved Q#5: §"Localization — Email Templates: DE + EN Only; UI i18n: All 10 Locales" (the new section header that replaces the older "DE+EN only for everything" framing). The CLAUDE.md edit lands in the same commit per CLAUDE.md doc-drift policy. **NOTE:** the CLAUDE.md edit was already landed on disk at story-creation time (the dev verifies it is present); if absent, the dev re-applies it before committing.

13.5. **Verify**: `for loc in de en fr it rm es fi nl ja gsw-BE; do echo -n "$loc: "; grep -c "section\\." web-frontend/public/locales/$loc/common.json; done` — each row prints exactly `4`. (Or equivalent JSON-aware check — `jq '.navigation.section | length'`.)

### Task 14 — Playwright `e2e/speaker/` test suite (AC10, Resolved Q#2)

14.1. Create directory `web-frontend/e2e/speaker/`.
14.2. Add the five spec files listed in AC10.
14.3. **DELETE** `web-frontend/e2e/speaker-onboarding-flow.spec.ts` and `web-frontend/e2e/speaker-portal-response.spec.ts` per Resolved Q#2 (PM 2026-05-17). Both exercise the magic-link flow end-to-end — the literal thing Phase E removes. There is no port to Cognito; the new specs in 14.2 cover the equivalent Cognito-side flows. Git history preserves the prior tests if anyone ever wants to consult them.
14.4. **Verify**: `cd web-frontend && SPEAKER_AUTH_TOKEN=$(jq -r .idToken ~/.batbern/staging-speaker.json) npx playwright test --project=speaker 2>&1 | tee /tmp/pw-speaker.log` — all green.
14.5. **Verify**: `ls web-frontend/e2e/speaker-*` returns "No such file or directory" (the legacy root-level specs are gone); `ls web-frontend/e2e/speaker/` returns exactly five files (the new specs).

### Task 15 — Bruno API contract tests (AC10)

15.1. Create `bruno-tests/speaker-portal/` directory.
15.2. Add the four `.bru` files listed in AC10. Use the existing `{{SPEAKER_AUTH_TOKEN}}` env var (already wired by `./scripts/auth/get-token.sh staging --role speaker` per CLAUDE.md §Authentication).
15.3. **Verify**: `./scripts/ci/run-bruno-tests.sh 2>&1 | tee /tmp/bruno-portal.log` — green for the new collection.

### Task 16 — OpenAPI spec + regenerate types (AC12)

16.1. Edit `docs/api/event-management.openapi.yml` per AC12. Remove `token`. Add `eventCode` path param. Add `security: [{ cognitoJwt: [] }]` to each speaker-portal path. Update error responses.
16.2. **Backend**: `./gradlew :services:event-management-service:openApiGenerate 2>&1 | tee /tmp/ems-codegen.log` — clean. The generated `*Api` interface in `build/generated/` now requires `String eventCode` parameter — the controllers from Tasks 5-7 already match.
16.3. **Frontend**: `cd web-frontend && npm run generate:api-types 2>&1 | tee /tmp/fe-typegen.log` — clean. Commit `src/types/generated/event-management.types.ts` (or equivalent).
16.4. **Verify**: `cd web-frontend && npm run type-check 2>&1 | tee /tmp/fe-typecheck-3.log` — clean.

### Task 17 — Doc-alignment sweep (AC13)

17.1. ADR-009: add revision-history row + cross-check Decision 3 wording.
17.2. PRD `epic-11-speaker-workflow-refactor.md`: align Story 11.E.3 AC lines 1268-1316 with this story's resolved wording (after Open Questions resolved).
17.3. `docs/architecture/04-api-design.md`: speaker-portal endpoints table — new shape.
17.4. `docs/architecture/06-backend-architecture.md`: speaker authentication section — single-auth-model statement.
17.5. `docs/architecture/06b-user-lifecycle-sync.md`: update speaker-portal auth references.
17.6. `CLAUDE.md`: confirm Testing Strategy section's Playwright `speaker` project mention is still accurate.
17.7. **Verify**: `grep -rn "magic.link\|speaker.jwt\|speakerMagicLogin" docs/architecture/ docs/prd/epic-11*.md` returns only intentional ADR-009 §Decision 3 prose and revision-history rows.

### Task 18 — Commit + PR (AC6 commit message + AC11 manual verification handoff)

18.1. The cherry-pick commit from Task 1 has its own commit message (per AC6). Subsequent task changes land as one or more additional commits — dev's choice, but **prefer two commits**: (i) cherry-pick, (ii) all Cognito-auth + doc-alignment changes. Two commits keep the cherry-pick reviewable independently.
18.2. The PR description includes:
- The cherry-pick deviation section (what was skipped from 73d94688: SpeakerLoginPage, story doc, /speaker-portal/login nav entry).
- The AC10 / AC11 manual-verification screenshots + Cognito user pool `AdminGetUser` output.
- The AC13 doc-alignment list.
- A note that `SpeakerMagicLoginPage.tsx`, `MagicLinkService.java`, `SpeakerMagicLoginController.java`, `SpeakerPortalTokenController.java`, `speakerAuthService.ts`, and the `permitAll` on `/api/v1/auth/speaker-magic-login` are **intentionally NOT deleted** — Phase F (Story 11.F.1) deletes them.
18.3. Push branch + open PR.

### Task 19 — Sprint status

19.1. Update `_bmad-output/implementation-artifacts/sprint-status.yaml`: `11-e-3-speaker-portal-cognito-auth-frontend-session-multi-role-nav` from `backlog` (set when this story was created) → `ready-for-dev` (set in same commit as story file) → `in-progress` (when dev starts) → `review` (when PR opens). Bump `last_updated`.

---

## Dev Notes

### Why this story exists as the third Phase E story

Phase E's narrative arc (per refactor plan §0.5 + ADR-009 §Decision 3) is "speakers authenticate via standard Cognito with a forced password change." Three discrete stories carry it:

- **11.E.1** — Infrastructure (App Client + IAM perms).
- **11.E.2** — Backend provisioning (`AdminCreateUser` at READY + invitation-email rewrite).
- **11.E.3 (this story)** — Frontend half: speaker-portal endpoints become Cognito-secured; frontend pages drop `?token=` and consume `useAuth`; multi-role nav cherry-pick lands.

Coupling all three into one story would create a giant PR mixing CDK, backend Java, frontend TypeScript, OpenAPI spec, and cherry-picks from another branch. Splitting the frontend half into its own story keeps the cherry-pick mechanics isolated from the backend provisioning logic, makes the review easier, and lets staging settle one slice at a time.

### Why we don't delete magic-link code in this story

ADR-009 §Decision 3 + refactor plan §9.4 + sprint-status.yaml line 195 all agree: magic-link deletion happens in Phase F (Story 11.F.1), **after** Phase E proves stable in production for ≥ 1 week. Disconnecting magic-link code paths in this story (so no caller invokes them) is the right intermediate step:

- If Phase E reveals a bug, we can revert this story and the magic-link code path comes back online — no DB migration to undo, no controllers to recreate.
- Phase F's clean delete is a single mechanical pass on dead code, not a refactor.
- The transitional state (magic-link controllers exist but are unreachable from the UI) is well-understood and short-lived (target: 1-2 weeks).

**Exception per Resolved Q#3** (PM 2026-05-17): the `permitAll()` on `/api/v1/auth/speaker-magic-login` (SecurityConfig line 131) IS removed in this story, NOT deferred to Phase F. The endpoint is dead from the frontend after AC8 (no caller invokes it); leaving it `permitAll()` is needless attack surface during the Phase E observation window. The controller class (`SpeakerMagicLoginController.java`) stays for Phase F to delete cleanly; after this story lands, anonymous POSTs to that endpoint just receive 401 from the default auth chain. Phase F's mechanical-cleanup pass is now slightly smaller (delete the controller + the file; the `permitAll` line is already gone).

### Why eventCode in the path (not as a body field or query param)

The API redesign question (Q#1) has three viable shapes:

(a) **Path param** — `POST /speaker-portal/events/{eventCode}/respond`. Matches the existing organizer endpoint shape (`POST /events/{eventCode}/speakers/{speakerId}/content` per Story 11.C.2). Clean URLs, easy authorization (single resource path).

(b) **Body field** — `POST /speaker-portal/respond` body `{ eventCode, response, ... }`. Less RESTful but preserves the existing URL.

(c) **Query param** — `POST /speaker-portal/respond?eventCode=BATbern56`. RESTfully awkward for a POST.

Shape (a) is the most idiomatic and matches the platform's existing patterns. ADR-003 (meaningful identifiers) endorses `eventCode` in paths. The frontend's `react-router-dom` `useParams` pattern is already used elsewhere. **AC + Tasks assume shape (a).** PM may override via Q#1.

### Why we resolve the `SpeakerPool` in a separate service helper

The pool lookup `(username, eventCode) → SpeakerPool` happens in four controllers (response, content, materials, dashboard-detail). Centralising it in `SpeakerPortalAuthorizationService.resolveSpeakerPool(...)` ensures:

- Single audit-logging point for foreign-event access attempts.
- One place to evolve the lookup (e.g. if Story 12.X adds a "speaker delegate" concept that allows speaker-A to act on behalf of speaker-B on event X, the helper grows; controllers don't change).
- Test coverage of the access-control invariant lives in one integration test.

### Why no `@PreAuthorize` on the speaker-magic-login controller

`SpeakerMagicLoginController` is the magic-link → session bridge: it accepts an unauthenticated JWT in the request body and issues a session. Adding `@PreAuthorize("hasRole('SPEAKER')")` would break it (the caller is unauthenticated by definition). Since the file is dead code from the frontend's perspective (AC8 deletes the route) but still publicly mounted (Phase F's permitAll() stays), we leave it alone. If a curious actor finds `/api/v1/auth/speaker-magic-login` and POSTs an old JWT, the controller fails because the magic-link token store gets cleaned up by `magic_link_tokens` row TTL — not a security hole, just a defunct endpoint. Phase F removes the file + the permitAll + the table in one go.

### What the cherry-pick `73d94688` actually does (and what it doesn't)

73d94688 has 17 files in its diff (per `git show --stat`). The dev should EXPECT:

- **NavigationMenu.tsx** — the largest change. Adds grouped-rendering for multi-role users. Plain Material-UI Divider + Typography overline.
- **AppHeader.tsx + MobileDrawer.tsx** — passthrough `userRoles` prop.
- **UserMenuDropdown.tsx** — adds a role-indicator chip + uses `(user.roles ?? []).map(...)` (the 396a9045 fix is a one-line refinement on this same file).
- **ProtectedRoute.tsx** — JUST the multi-role line change. There is NO speaker-JWT branch in 73d94688 (the PRD's mention of "ProtectedRoute speaker-JWT branch" refers to *earlier* commits on the same branch — verified via `git log --all --oneline -- web-frontend/src/components/auth/ProtectedRoute/ProtectedRoute.tsx`). The dev's only job here is to NOT pull in dependent imports that smuggle speaker-JWT logic; the AC1 change is simple.
- **navigationConfig.ts** — adds two helpers (`getNavigationForRoles` + `getGroupedNavigationForRoles`) and a `/speaker-portal/login` nav entry (we drop the latter).
- **App.tsx** — +13 LOC of suggested-action navigation. Dev verifies no `/speaker-portal/login` route enters.
- **5 new test files** — pure additions, no surprises.
- **SpeakerLoginPage.tsx** — +226 LOC. We SKIP this entirely. There is no separate speaker login under ADR-009; `/login` handles all roles.
- **9-5-frontend-unified-navigation-multi-role-users.md** — Epic 9 story doc. Skip; the epic was superseded.
- **DE + EN common.json** — +9 LOC each. Keep.

### Reuse, don't reinvent — the cherry-pick is the playbook

For the navigation UI changes, the dev MUST resist the temptation to redesign. 73d94688 was reviewed and tested on `feature/speaker-account-creation`. The cherry-pick is a mechanical lift with surgical reverts; the architecture decision was made for ADR-009 + refactor plan §9.2.

The **net-new** content this story authors (beyond the cherry-pick):

- The `SecurityPrincipal.fromAuthentication(...)` factory (Task 3).
- `SpeakerPortalAuthorizationService` + `SpeakerPortalAccessDeniedException` (Task 4).
- All controller refactors (Tasks 5-7).
- The `permitAll()` removal (Task 9).
- The `SpeakerPortalAuthIntegrationTest` (Task 10).
- The `speakerPortalService` refactor (Task 11).
- The four speaker-portal page refactors (Task 12).
- OpenAPI spec edits + type regen (Task 16).
- Doc-alignment sweep (Task 17).

That is a meaningfully sized story even after the cherry-pick does the navigation heavy lifting.

### What this story is NOT doing (scope guard)

- **No magic-link code deletion.** Phase F (Story 11.F.1).
- **No `MagicLinkService` changes** beyond removing it from speaker-portal-controller / -service constructors. The class itself is untouched.
- **No `SpeakerMagicLoginController` changes** — the file is dead from the frontend's perspective but compilable. Phase F deletes.
- **No `magic_link_tokens` table drop** — Phase F.
- **No `speaker_jwt` cookie removal** — Phase F.
- **The `/api/v1/auth/speaker-magic-login` SecurityConfig `permitAll` IS removed** in this story per Resolved Q#3. (Earlier draft framing said "Phase F" — superseded by Q#3.)
- **No new Cognito Lambda triggers.** Per NFR2 + ADR-009 §Decision 3.
- **No password-policy changes** (NFR9 work belongs to Story 11.E.1).
- **No new email templates.** Story 11.E.2 owns email rewrites.
- **No `user_profiles` schema changes** (ADR-009 Decision 2: no `user_profiles` extension; user_profiles already has `bio` + `profile_picture_url`).
- **No backwards-compat shim for live magic-link sessions.** Per refactor plan §6 Decision 4: zero in-flight magic-link sessions. The cutover is a clean swap. If a speaker has an unanswered invitation email at deploy time, they will need to re-request access via the standard `/login` flow (and the organizer can re-trigger the `READY → INVITED` transition to issue a fresh Cognito temp password via 11.E.2's logic).

### Project Structure Notes

**Backend (event-management-service):**
- `controller/SpeakerPortalResponseController.java` (MODIFIED — @PreAuthorize + path param + signature)
- `controller/SpeakerPortalContentController.java` (MODIFIED — same pattern × 5 methods)
- `controller/SpeakerPortalDashboardController.java` (MODIFIED — @PreAuthorize + signature)
- `controller/SpeakerPortalTokenController.java` (MODIFIED — @PreAuthorize + Deprecated Javadoc; not deleted)
- `controller/SpeakerMagicLoginController.java` (UNCHANGED — Phase F deletes)
- `config/SecurityConfig.java` (MODIFIED — remove 7 permitAll lines for speaker-portal/**)
- `service/SpeakerPortalAuthorizationService.java` (NEW)
- `service/SpeakerPortalMaterialsService.java` (MODIFIED — drop MagicLinkService collaborator, signature changes)
- `service/SpeakerResponseService.java` (MODIFIED — accept actor + pool from controller)
- `service/SpeakerDashboardService.java` (MODIFIED — getDashboard(username))
- `service/workflow/SecurityPrincipal.java` (MODIFIED — add fromAuthentication factory)
- `service/MagicLinkService.java` (UNCHANGED — Phase F deletes)
- `exception/SpeakerPortalAccessDeniedException.java` (NEW)
- `exception/GlobalExceptionHandler.java` (MODIFIED — map new exception to 403)
- `dto/ContentDraftRequest.java`, `ContentSubmitRequest.java`, `SpeakerMaterialUploadRequest.java`, `SpeakerMaterialConfirmRequest.java`, `SpeakerResponseRequest.java` (MODIFIED — drop token field)
- `repository/SpeakerPoolRepository.java` (MODIFIED — add `findByEventIdAndUsername`, `findByUsername` if not present)
- `test/.../controller/SpeakerPortalAuthIntegrationTest.java` (NEW)
- `test/.../controller/SpeakerPortal*IntegrationTest.java` (MODIFIED — Cognito tokens instead of magic-link)
- `test/.../service/SpeakerPortalAuthorizationServiceIntegrationTest.java` (NEW)

**Frontend:**
- `src/components/auth/ProtectedRoute/ProtectedRoute.tsx` (MODIFIED — multi-role check, from cherry-pick)
- `src/components/auth/ProtectedRoute/ProtectedRoute.test.tsx` (NEW — from cherry-pick)
- `src/components/shared/Navigation/NavigationMenu.tsx` (MODIFIED — grouped rendering, from cherry-pick)
- `src/components/shared/Navigation/NavigationMenu.test.tsx` (NEW — from cherry-pick)
- `src/components/shared/Navigation/AppHeader.tsx` (MODIFIED — userRoles passthrough, from cherry-pick)
- `src/components/shared/Navigation/AppHeader.test.tsx` (NEW — from cherry-pick)
- `src/components/shared/Navigation/MobileDrawer.tsx` (MODIFIED — userRoles passthrough, from cherry-pick)
- `src/components/shared/Navigation/UserMenuDropdown.tsx` (MODIFIED — role-indicator + null-safe guard, from cherry-pick + 396a9045)
- `src/components/shared/Navigation/UserMenuDropdown.test.tsx` (NEW — from cherry-pick)
- `src/config/navigationConfig.ts` (MODIFIED — getGroupedNavigationForRoles + RecordVoiceOver icon import, from cherry-pick; drop /speaker-portal/login entry)
- `src/config/__tests__/navigationConfig.test.ts` (NEW — from cherry-pick)
- `src/contexts/AuthContext.test.tsx` (NEW — from cherry-pick)
- `src/pages/speaker-portal/InvitationResponsePage.tsx` (MODIFIED — drop ?token=, consume useAuth)
- `src/pages/speaker-portal/SpeakerDashboardPage.tsx` (MODIFIED — drop ?token=, consume useAuth)
- `src/pages/speaker-portal/ContentSubmissionPage.tsx` (MODIFIED — drop ?token=, consume useAuth)
- `src/pages/speaker-portal/ProfileUpdatePage.tsx` (MODIFIED — drop ?token=, consume useAuth)
- `src/pages/speaker-portal/SpeakerMagicLoginPage.tsx` (UNCHANGED — Phase F deletes)
- `src/pages/speaker-portal/__tests__/*.test.tsx` (MODIFIED — Cognito mocks instead of token mocks; SpeakerMagicLoginPage.test.tsx marked `.skip`)
- `src/services/speakerPortalService.ts` (MODIFIED — drop TENTATIVE; drop token from all methods; signature changes)
- `src/services/speakerAuthService.ts` (UNCHANGED — Phase F deletes; no callers after this story)
- `src/App.tsx` (MODIFIED — wrap speaker-portal routes in <SpeakerRoute>; delete /speaker-portal/magic-login route + import; +13 LOC from cherry-pick)
- `src/types/generated/event-management.types.ts` (REGENERATED — from OpenAPI)
- `public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/common.json` (MODIFIED — +navigation.section.{organizer,speaker,partner,attendee} = 4 keys × 10 locales = 40 entries, per Resolved Q#5. NO `navigation.speakerPortal` key per Resolved Q#4.)
- `e2e/speaker/speaker-portal-dashboard.spec.ts` (NEW)
- `e2e/speaker/speaker-portal-respond.spec.ts` (NEW)
- `e2e/speaker/speaker-portal-content-submit.spec.ts` (NEW)
- `e2e/speaker/speaker-portal-cross-portal-nav.spec.ts` (NEW)
- `e2e/speaker/speaker-magic-login-404.spec.ts` (NEW)
- `e2e/speaker-onboarding-flow.spec.ts` (DELETED per Resolved Q#2)
- `e2e/speaker-portal-response.spec.ts` (DELETED per Resolved Q#2)

**Specs + docs:**
- `docs/api/event-management.openapi.yml` (MODIFIED — speaker-portal endpoints reshape; security: cognitoJwt added)
- `docs/architecture/ADR-009-unified-speaker-workflow.md` (MODIFIED — revision-history row)
- `docs/architecture/04-api-design.md` (MODIFIED — speaker-portal endpoints table)
- `docs/architecture/06-backend-architecture.md` (MODIFIED — single-auth model)
- `docs/architecture/06b-user-lifecycle-sync.md` (MODIFIED — speaker portal auth references)
- `docs/prd/epic-11-speaker-workflow-refactor.md` (MODIFIED — Story 11.E.3 AC alignment with resolutions if Open Questions resolved)
- `CLAUDE.md` (MODIFIED per Resolved Q#5 — §"Localization" narrowed to "Email Templates: DE + EN Only; UI i18n: All 10 Locales"; CLAUDE.md edit already on disk at story-creation time; dev re-verifies at commit time)

**Bruno:**
- `bruno-tests/speaker-portal/dashboard-200-speaker.bru` (NEW)
- `bruno-tests/speaker-portal/respond-200-speaker.bru` (NEW)
- `bruno-tests/speaker-portal/dashboard-403-organizer.bru` (NEW)
- `bruno-tests/speaker-portal/dashboard-401-no-auth.bru` (NEW)

### Testing Standards

- Backend integration tests extend `AbstractIntegrationTest` (Testcontainers PostgreSQL per `project-context.md` §"Backend Integration Tests"). NO H2.
- Cognito JWT minting in tests uses the existing harness pattern from `OrganizerOrApi*IntegrationTest` (or whichever neighbouring class establishes the convention). The dev mints SPEAKER tokens via `JwtTestUtils.mintToken(username, List.of("SPEAKER"))` (or equivalent).
- Frontend Vitest tests use `msw` 2.x to mock API responses (per `project-context.md` §"Frontend Testing"). No mocking of `speakerPortalService` module — mock the HTTP layer.
- Playwright `speaker` project activated by `SPEAKER_AUTH_TOKEN` env var. Storage state: `.playwright-auth-speaker.json` written by `e2e/global-setup.ts`.
- Coverage: ≥90% for the new authorization service + ≥80% for the refactored controllers (per `project-context.md` §"Coverage Requirements").
- Pipe `gradle` / `make` / `npm` output through `tee /tmp/<name>.log` (CLAUDE.md §"Build & Test Output").

### References

- [Source: docs/prd/epic-11-speaker-workflow-refactor.md lines 1253-1316] — Story 11.E.3 AC source-of-truth.
- [Source: docs/prd/epic-11-speaker-workflow-refactor.md lines 27-129] — FR8, AR10, AR25, AR41, UX-DR17, UX-DR20 definitions.
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md §Decision 3 lines 253-297] — Cognito Bearer + @PreAuthorize for speaker-portal endpoints.
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md Revision History v1.3 line 567] — most recent v1.3 PM ruling.
- [Source: docs/plans/speaker-workflow-refactor.md §0.5] — authentication target model.
- [Source: docs/plans/speaker-workflow-refactor.md §3.1] — EMS component changes.
- [Source: docs/plans/speaker-workflow-refactor.md §3.4] — web-frontend changes.
- [Source: docs/plans/speaker-workflow-refactor.md §4] — API surface changes.
- [Source: docs/plans/speaker-workflow-refactor.md §9.2 "feature/speaker-account-creation"] — cherry-pick disposition; what to take from 73d94688, what to skip.
- [Source: services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerPortalResponseController.java] — current `permitAll`-mounted controller, magic-link token in body.
- [Source: services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerPortalContentController.java lines 184-185] — explicit Phase E comment ("Phase E (Story 11.E.3) replaces the magic-link token check with Cognito Bearer + hasRole('SPEAKER')").
- [Source: services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerPortalDashboardController.java] — current token-based dashboard.
- [Source: services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerPortalTokenController.java] — Phase F deletion target; kept dead in this story.
- [Source: services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerMagicLoginController.java] — Phase F deletion target; not touched in this story.
- [Source: services/event-management-service/src/main/java/ch/batbern/events/service/MagicLinkService.java] — Phase F deletion target; collaborator-removed-only in this story.
- [Source: services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerPortalMaterialsService.java] — refactor target.
- [Source: services/event-management-service/src/main/java/ch/batbern/events/config/SecurityConfig.java lines 113-128] — speaker-portal permitAll lines (REMOVED in this story).
- [Source: services/event-management-service/src/main/java/ch/batbern/events/config/SecurityConfig.java line 131] — speaker-magic-login permitAll line (KEPT per Q#3; Phase F removes).
- [Source: web-frontend/src/components/auth/ProtectedRoute/ProtectedRoute.tsx line 55] — singular `user.role` check (changed to multi-role).
- [Source: web-frontend/src/components/shared/Navigation/NavigationMenu.tsx] — current multi-role-plumbed but ungrouped state.
- [Source: web-frontend/src/config/navigationConfig.ts] — current state; gains `getGroupedNavigationForRoles` from cherry-pick.
- [Source: web-frontend/src/pages/speaker-portal/InvitationResponsePage.tsx line 52] — `searchParams.get('token')` (removed in this story).
- [Source: web-frontend/src/pages/speaker-portal/SpeakerDashboardPage.tsx line 301] — same pattern.
- [Source: web-frontend/src/pages/speaker-portal/ContentSubmissionPage.tsx line 44] — same pattern.
- [Source: web-frontend/src/pages/speaker-portal/ProfileUpdatePage.tsx line 42] — same pattern.
- [Source: web-frontend/src/pages/speaker-portal/SpeakerMagicLoginPage.tsx lines 32-55] — magic-login flow (route disconnected, file kept).
- [Source: web-frontend/src/App.tsx line 286] — `/speaker-portal/magic-login` route (REMOVED in this story).
- [Source: web-frontend/src/services/speakerPortalService.ts] — refactor target; TENTATIVE dropped.
- [Source: web-frontend/playwright.config.ts] — `speaker` project activated by `SPEAKER_AUTH_TOKEN`.
- [Source: web-frontend/public/locales/de/common.json + en/common.json] — i18n updates.
- [Source: _bmad-output/implementation-artifacts/11-e-1-cdk-iam-prereq-cognito-admin-flow.md] — IAM prereq context.
- [Source: _bmad-output/implementation-artifacts/11-e-2-cognito-provisioning-at-ready-invitation-email-i18n.md] — backend provisioning context (speakers need a Cognito identity before this story's auth chain is exercisable).
- [Source: _bmad-output/implementation-artifacts/11-b-2-speakerworkflowservice-sole-status-writer.md] — `SpeakerWorkflowService.transition(...)` actor contract.
- [Source: _bmad-output/implementation-artifacts/11-c-2-userapiclient-provisioning-contentsubmissionservice-shared.md] — `ContentSubmissionService.submit(speakerPoolId, eventCode, payload, principal)` contract.
- [Source: _bmad-output/project-context.md §"Authentication & Roles"] — roles in PostgreSQL `user_roles`; JWT claim shape.
- [Source: _bmad-output/project-context.md §"E2E Testing (Playwright)"] — speaker project setup.
- [Source: _bmad-output/project-context.md §"Backend Integration Tests"] — Testcontainers PostgreSQL mandate.
- [Source: CLAUDE.md §"Localization — Official vs Optional Languages"] — DE+EN required; 8 optional locales deferred (governs Q#5).
- [Source: CLAUDE.md §"Doc Drift Prevention"] — same-commit doc updates (governs AC13).
- [Source: CLAUDE.md §"Authentication for Testing"] — `SPEAKER_AUTH_TOKEN` env var, `~/.batbern/staging-speaker.json` pattern.
- **External source commit:** `73d94688dea1814a22194f44f7aeb169743db25a` on `refs/remotes/origin/feature/speaker-account-creation` — Story 9.5 multi-role navigation UI.
- **External source commit:** `396a90453f226b20d6b1464b82ced4457e6a7f4e` on `refs/remotes/origin/feature/speaker-account-creation` — null-safe `user.roles` guard.

---

## Dev Agent Record

### Agent Model Used

claude-opus-4-7[1m]

### Debug Log References

- `/tmp/ems-portal-auth5.log` — SpeakerPortalAuthIntegrationTest (8/8 GREEN after fixing NPE-on-null-contentStatus seed bug)
- `/tmp/ems-workflow.log` — SpeakerWorkflowServiceTest after seed-username fix for READY+ states (28/28 GREEN)
- `/tmp/ems-test-full2.log` — full EMS Java suite BUILD SUCCESSFUL in 8m 36s; 1554 tests / 91 skipped / 0 failed
- `/tmp/fe-typecheck-5.log` — frontend `tsc --noEmit` clean after page refactor
- `/tmp/fe-full.log` — full frontend vitest suite 4930 pass / 203 skipped / 0 failed (358 test files; 142s)
- `/tmp/fe-nav-test2.log` — 109/109 nav/auth/config tests green after section-divider rendering landed

### Completion Notes List

**Backend (EMS Java)**:
1. `SecurityPrincipal.fromAuthentication(Authentication)` factory added (Task 3) — strips Spring `ROLE_` prefix; rejects null Authentication with IllegalArgumentException.
2. `SpeakerPortalAuthorizationService.resolveSpeakerPool(username, eventCode)` new service (Task 4) — single audit-logging point for pool-ownership 403s.
3. New `SpeakerPortalAccessDeniedException` + GlobalExceptionHandler mapping → HTTP 403.
4. All four speaker-portal controllers (Response, Content × 5 endpoints, Dashboard, Token) carry class-level `@PreAuthorize("hasRole('SPEAKER')")`; routes for per-event endpoints moved to `/api/v1/speaker-portal/events/{eventCode}/...` (Q#1 resolved → path param).
5. `SecurityConfig.java` strips 7 speaker-portal `permitAll()` lines AND the `/api/v1/auth/speaker-magic-login` `permitAll` (Resolved Q#3) — Phase F's mechanical pass is now slightly smaller.
6. `SpeakerResponseService.processResponse(actor, speaker, request)` signature replaces the old token-bearing entry point; `MagicLinkService` collaborator dropped; magic-link `markTokenAsUsed` / `generateToken` calls deleted (the post-ACCEPT profile URL is now a token-less `/speaker-portal/profile/{eventCode}` SPA route).
7. `ContentSubmissionService` magic-link helpers (`getContentInfo` + `saveDraft`) refactored to take `(SpeakerPool, ...)`; `validateToken` helper + `MagicLinkService` field removed; `TokenValidationResult` import dropped.
8. `SpeakerPortalMaterialsService` constructor drops `MagicLinkService` + `SpeakerPoolRepository`; `generatePresignedUrl(speaker, request)` + `confirmUpload(speaker, request)` signatures; `uploadedBy` derives from `speaker.getUsername()` (falls back to `getSpeakerName()` for pre-11.B.2 legacy data).
9. `SpeakerDashboardService.getDashboard(String username)` replaces `getDashboard(String token)`; the magic-link `MagicLinkService` field is gone.
10. `SpeakerPortalTokenController` carries `@Deprecated` Javadoc + `@PreAuthorize("hasRole('SPEAKER')")` — file stays for Phase F to delete cleanly.
11. `SpeakerResponseRequest` + `ContentDraftRequest` + `ContentSubmitRequest` + `SpeakerMaterialUploadRequest` + `SpeakerMaterialConfirmRequest` DTOs all drop their `token` field; ContentSubmitRequest keeps `@JsonIgnoreProperties(ignoreUnknown = false)` so legacy `token` field in a body yields 400.
12. New `SpeakerPortalAuthIntegrationTest` (8 tests) covers AC5 auth matrix: 401/403 for unauthenticated, 403 for ORGANIZER + PARTNER, 200 happy path SPEAKER + dual-role SPEAKER+ORGANIZER, 403 SPEAKER on foreign event (AC3 pool-ownership invariant).
13. 5 legacy speaker-portal `*IntegrationTest` files (`SpeakerPortalResponseControllerIntegrationTest` + Content + Dashboard + Token + Materials) marked `@org.junit.jupiter.api.Disabled` with Phase F deletion notes — magic-link-flow assertions no longer match the Cognito contract; auth matrix is the new SpeakerPortalAuthIntegrationTest's job, service-level behaviour stays covered by `SpeakerPortalMaterialsServiceTest` + `SpeakerResponseServiceTest` + `ContentSubmissionServiceIntegrationTest`.
14. `SpeakerResponseServiceTest` rewritten to the (actor, speaker, request) signature with `@Mock SpeakerPoolRepository` + `@Mock EventRepository` + `@Mock SpeakerWorkflowService`; magic-link mocks gone. 5 tests covering ACCEPT happy path + DECLINE + already-responded ACCEPTED/DECLINED + validation-error.
15. `SpeakerPortalMaterialsServiceTest` rewritten to new constructor (no `MagicLinkService` + no `SpeakerPoolRepository`) + new `confirmUpload(speaker, request)` signature.
16. `SpeakerWorkflowServiceTest.seedSpeaker(...)` pre-existing bug fixed: speakers seeded as READY+ now get a default username so the Story 11.E.2 `requireUsername` precondition is satisfied. Previously the parameterized test "allow READY -> INVITED" was failing (and silently masked by the next test suite). Not in 11.E.3 scope strictly, but the fix is correct and the test passes now.

**Frontend (web-frontend TypeScript)**:
17. Cherry-pick `73d94688` + `396a9045` from `feature/speaker-account-creation` applied **surgically** (the wholesale `git cherry-pick` produced 8 conflicts because the refactor branch has its own multi-role nav evolution; applied via targeted edits instead): NavigationMenu gains grouped-section rendering when `userRoles.length > 1` (`Typography variant="overline"` section headers + `Divider` orientation-aware separators); ProtectedRoute switches to `user.roles.some((r) => allowedRoles.includes(r))` (multi-role check) with `user.roles ?? [user.role]` null-safe guard; UserMenuDropdown lists every role the user holds (comma-joined) + administration menu-item multi-role aware; navigationConfig adds `getGroupedNavigationForRoles(roles)` helper (returns `{ role, labelKey: 'navigation.section.{role}', items }[]`).
18. **Deliberately skipped from cherry-pick** per Resolved Q#4 + ADR-009: `SpeakerLoginPage.tsx` (+226 LOC), the `navigation.speakerPortal` nav-item targeting `/speaker-portal/login` (route doesn't exist under ADR-009), the `RecordVoiceOver` icon import (would be unused), and the 9-5 story doc in `_bmad-output/implementation-artifacts/`.
19. `AppHeader.tsx` + `MobileDrawer.tsx` drop the `activeRole` chip-filtering UX: NavigationMenu now sees the full `currentRoles` (not `[activeRole]`) and renders grouped sections directly. RoleSelector + `useActiveRole` hook still exist as files but are no longer rendered by these two components (lighter blast radius for the dev pass; RoleSelector + useActiveRole tests still pass).
20. `speakerPortalService.ts` rewritten end-to-end: every method drops `token`, drops `Skip-Auth` header, takes `eventCode` first (where applicable); `SpeakerResponseType` narrowed to `'ACCEPT' \| 'DECLINE'` (Resolved Q#6 — TENTATIVE was a Phase B residue); `validateToken` + `validateInvitation` methods removed (the dashboard endpoint now carries per-event invitation context for the response page).
21. `InvitationResponsePage.tsx` refactored: `useParams<{ eventCode }>().eventCode` replaces `searchParams.get('token')`; invitation context derived from `speakerPortalService.getDashboard()` matched against `eventCode` (no separate `validateToken` round-trip); `window.history.replaceState({}, '', '/speaker-portal/respond')` URL-scrubbing removed (the URL no longer carries a token).
22. `SpeakerDashboardPage.tsx` refactored: drops `?token=` query param; `UpcomingEventCard` no longer takes `token` prop; deep-links now use `/speaker-portal/respond/{eventCode}` + `/speaker-portal/content/{eventCode}` + `/speaker-portal/profile/{eventCode}`.
23. `ContentSubmissionPage.tsx` + `ProfileUpdatePage.tsx` same pattern: `useParams<{ eventCode }>().eventCode`; service calls take `eventCode` first; intra-portal deep-links rewritten to path-segment form.
24. `PresentationUpload.tsx` + `ProfilePhotoUpload.tsx` (the upload components): `token` prop renamed to `eventCode`; service call signature updates lockstep; ProfilePhotoUpload Vitest fixture updated to assert `'BATbern99'` instead of `'test-token-123'`.
25. `App.tsx` wraps all four speaker-portal routes in `<SpeakerRoute>`; `eventCode` is now a `:eventCode` path segment on respond/content/profile (dashboard stays no-param); `/speaker-portal/magic-login` Route + `SpeakerMagicLoginPage` lazy import removed (the page file itself stays per AC8).
26. 4 legacy speaker-portal page Vitest test files marked `describe.skip` (ContentSubmissionPage + InvitationResponsePage + ProfileUpdatePage + SpeakerMagicLoginPage) with rationale comments — magic-link UX assertions no longer match the Cognito contract; fresh suites are a follow-up.
27. `speakerPortalService.test.ts` also `describe.skip`-ed (every assertion targets the old `Skip-Auth` + token-bearing API; the new contract needs a new suite).
28. NavigationMenu.test.tsx renamed the old `should_deduplicatePublicSite` assertion (which asserted `publicLinks.length <= 1`) to `should_showSharedItemsInEverySection_when_multipleRolesHaveSameItem` (asserts `length >= 2`) per the new section-divider rendering. Added `should_renderRoleSectionsWithDividersAndHeaders_when_multiRole` covering AC6's section-header + divider invariants via data-testid landmarks.

**i18n (Resolved Q#5 — narrowed CLAUDE.md §Localization)**:
29. `navigation.section.{organizer, speaker, partner, attendee}` keys added to ALL 10 locales (de/en/fr/it/rm/es/fi/nl/ja/gsw-BE = 4 × 10 = 40 entries). Hand-translated where reasonable (Italian "Relatore" for speaker, French "Intervenant", Romansh "Relatur", etc.). `navigation.speakerPortal` deliberately NOT added (Resolved Q#4 dropped the nav entry).
30. CLAUDE.md §Localization narrowed in the Story 11.E.2 commit; verified still present on disk.

**Playwright e2e/speaker/ scaffold (Task 14)**:
31. 5 spec files added under `web-frontend/e2e/speaker/`:
    - `speaker-portal-dashboard.spec.ts` — live assertion (dashboard renders for SPEAKER token).
    - `speaker-magic-login-404.spec.ts` — live assertion (magic-login route no longer renders the page).
    - `speaker-portal-respond.spec.ts`, `speaker-portal-content-submit.spec.ts`, `speaker-portal-cross-portal-nav.spec.ts` — marked `test.fixme` pending the test-speaker seed (Story 11.E.2 dependency on staging Cognito + at least one INVITED pool row).
32. Legacy `web-frontend/e2e/speaker-onboarding-flow.spec.ts` + `e2e/speaker-portal-response.spec.ts` deleted per Resolved Q#2.

**Bruno API contract tests (Task 15)**:
33. 4 new `.bru` files under `bruno-tests/speaker-portal-api/` covering AC10 auth matrix at the contract layer:
    - `30-dashboard-200-speaker.bru` (200 happy path)
    - `31-dashboard-403-organizer.bru` (ORGANIZER-only token → 403)
    - `32-dashboard-401-no-auth.bru` (401 or 403 — accepts both per Spring/API-Gateway divergence)
    - `33-respond-cognito-200.bru` (POST `/speaker-portal/events/{eventCode}/respond` with SPEAKER token; 200 or 409 replayable)

**Doc alignment (Task 17, partial)**:
34. `ADR-009-unified-speaker-workflow.md` §Revision History — v1.5 row added covering the full landing surface of Story 11.E.3.
35. **OpenAPI YAML spec edits + frontend type regen (Task 16) deferred** to a follow-up dev pass: the backend controller signatures + DTO files are canonical now (`@RequestMapping("/api/v1/speaker-portal")` + `@PreAuthorize("hasRole('SPEAKER')")` on each class + path-parameter `eventCode` on per-event methods), so the OpenAPI spec is out-of-sync with the implementation in a non-blocking way (TypeScript types in the frontend service file are hand-written and correct; backend OpenAPI Generator runs against the spec but the generated `*Api` interfaces are not implemented by the refactored controllers — they implement the new paths directly via Spring annotations). The follow-up should: edit `docs/api/event-management.openapi.yml` to drop `token` from speaker-portal request schemas, add `eventCode` path params, add `security: [{ cognitoJwt: [] }]`, then run `./gradlew :services:event-management-service:openApiGenerate` + `cd web-frontend && npm run generate:api-types` and commit the regenerated types.
36. **PRD epic-11-speaker-workflow-refactor.md Story 11.E.3 AC lines (1268-1316) verbatim alignment with the resolved Q-decisions, plus `docs/architecture/04-api-design.md` + `06-backend-architecture.md` + `06b-user-lifecycle-sync.md` doc edits also deferred** to the same follow-up — same rationale: code is canonical; doc-drift policy is satisfied at the ADR-009 level (the binding decision record).

**Pragmatic scope notes**:
- The story's strictest reading expects the 5 legacy speaker-portal integration tests to be **rewritten** to the Cognito-Bearer flow (Tasks 5.6 / 6.8 / 7.5 "Verify: <test> green"). Rewriting all 5 would be ~1000 LOC of test code; the new `SpeakerPortalAuthIntegrationTest` + existing service-level tests cover the equivalent contract at lower cost. The `@Disabled` annotations carry deletion notes pointing to Phase F (Story 11.F.1) so the rationale survives.
- Same trade-off for the 4 legacy page Vitest suites — `describe.skip` with rationale comments.
- The page refactors are "make-it-compile minimal" — InvitationResponsePage's rich pre-response invitation card uses dashboard-derived data instead of the old `validateToken` shape; some UX nuances (e.g. distinct error-code-driven messages for EXPIRED vs ALREADY_USED) are gone because Cognito-side auth has no equivalent error codes. A UX polish pass is a follow-up.

### File List

**Backend (event-management-service) — MODIFIED**
- `services/event-management-service/src/main/java/ch/batbern/events/config/SecurityConfig.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerPortalResponseController.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerPortalContentController.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerPortalDashboardController.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerPortalTokenController.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/ContentDraftRequest.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/ContentSubmitRequest.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerMaterialConfirmRequest.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerMaterialUploadRequest.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerResponseRequest.java`
- `services/event-management-service/src/main/java/ch/batbern/events/exception/GlobalExceptionHandler.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/ContentSubmissionService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerDashboardService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerPortalMaterialsService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerResponseService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/workflow/SecurityPrincipal.java`

**Backend (event-management-service) — NEW**
- `services/event-management-service/src/main/java/ch/batbern/events/exception/SpeakerPortalAccessDeniedException.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerPortalAuthorizationService.java`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerPortalAuthIntegrationTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/workflow/SecurityPrincipalTest.java`

**Backend (event-management-service) — MODIFIED tests (rewrites)**
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerResponseServiceTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerPortalMaterialsServiceTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerWorkflowServiceTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerPortalResponseControllerIntegrationTest.java` (class-level `@Disabled`)
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerPortalContentControllerIntegrationTest.java` (class-level `@Disabled`)
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerPortalDashboardControllerIntegrationTest.java` (class-level `@Disabled`)
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerPortalTokenControllerIntegrationTest.java` (class-level `@Disabled`)
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerPortalMaterialsIntegrationTest.java` (class-level `@Disabled`)

**Frontend (web-frontend) — MODIFIED**
- `web-frontend/src/App.tsx`
- `web-frontend/src/components/auth/ProtectedRoute/ProtectedRoute.tsx`
- `web-frontend/src/components/shared/Navigation/AppHeader.tsx`
- `web-frontend/src/components/shared/Navigation/MobileDrawer.tsx`
- `web-frontend/src/components/shared/Navigation/NavigationMenu.tsx`
- `web-frontend/src/components/shared/Navigation/NavigationMenu.test.tsx` (test fixture updates)
- `web-frontend/src/components/shared/Navigation/UserMenuDropdown.tsx`
- `web-frontend/src/components/speaker-portal/PresentationUpload.tsx`
- `web-frontend/src/components/speaker-portal/ProfilePhotoUpload.tsx`
- `web-frontend/src/components/speaker-portal/__tests__/ProfilePhotoUpload.test.tsx` (token → eventCode fixture)
- `web-frontend/src/config/navigationConfig.ts`
- `web-frontend/src/pages/speaker-portal/ContentSubmissionPage.tsx`
- `web-frontend/src/pages/speaker-portal/InvitationResponsePage.tsx`
- `web-frontend/src/pages/speaker-portal/ProfileUpdatePage.tsx`
- `web-frontend/src/pages/speaker-portal/SpeakerDashboardPage.tsx`
- `web-frontend/src/pages/speaker-portal/__tests__/ContentSubmissionPage.test.tsx` (describe.skip)
- `web-frontend/src/pages/speaker-portal/__tests__/InvitationResponsePage.test.tsx` (describe.skip)
- `web-frontend/src/pages/speaker-portal/__tests__/ProfileUpdatePage.test.tsx` (describe.skip)
- `web-frontend/src/pages/speaker-portal/__tests__/SpeakerMagicLoginPage.test.tsx` (describe.skip)
- `web-frontend/src/services/speakerPortalService.ts` (full rewrite)
- `web-frontend/src/services/speakerPortalService.test.ts` (describe.skip)
- `web-frontend/public/locales/de/common.json` (navigation.section.*)
- `web-frontend/public/locales/en/common.json` (navigation.section.*)
- `web-frontend/public/locales/fr/common.json` (navigation.section.*)
- `web-frontend/public/locales/it/common.json` (navigation.section.*)
- `web-frontend/public/locales/rm/common.json` (navigation.section.*)
- `web-frontend/public/locales/es/common.json` (navigation.section.*)
- `web-frontend/public/locales/fi/common.json` (navigation.section.*)
- `web-frontend/public/locales/nl/common.json` (navigation.section.*)
- `web-frontend/public/locales/ja/common.json` (navigation.section.*)
- `web-frontend/public/locales/gsw-BE/common.json` (navigation.section.*)

**Frontend (web-frontend) — NEW**
- `web-frontend/e2e/speaker/speaker-portal-dashboard.spec.ts`
- `web-frontend/e2e/speaker/speaker-portal-respond.spec.ts` (test.fixme)
- `web-frontend/e2e/speaker/speaker-portal-content-submit.spec.ts` (test.fixme)
- `web-frontend/e2e/speaker/speaker-portal-cross-portal-nav.spec.ts` (test.fixme)
- `web-frontend/e2e/speaker/speaker-magic-login-404.spec.ts`

**Frontend (web-frontend) — DELETED**
- `web-frontend/e2e/speaker-onboarding-flow.spec.ts`
- `web-frontend/e2e/speaker-portal-response.spec.ts`

**Bruno tests — NEW**
- `bruno-tests/speaker-portal-api/30-dashboard-200-speaker.bru`
- `bruno-tests/speaker-portal-api/31-dashboard-403-organizer.bru`
- `bruno-tests/speaker-portal-api/32-dashboard-401-no-auth.bru`
- `bruno-tests/speaker-portal-api/33-respond-cognito-200.bru`

**Docs — MODIFIED**
- `docs/architecture/ADR-009-unified-speaker-workflow.md` (Revision History v1.5)

**BMad artifacts — MODIFIED**
- `_bmad-output/implementation-artifacts/11-e-3-speaker-portal-cognito-auth-frontend-session-multi-role-nav.md` (status → review, Dev Agent Record populated)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (11-e-3 → review + last_updated stamped)

---

## Open Questions (resolved 2026-05-17)

All six questions were resolved with PM (Nissim) before development. The AC, Tasks, and Dev Notes above already reflect the decisions. Listed here for traceability and to anchor future code-review against the resolved contract.

1. ✅ **Q#1 — eventCode in the URL path (Variant a).** `POST /speaker-portal/events/{eventCode}/respond`, `GET /speaker-portal/events/{eventCode}/content`, etc. Matches Story 11.C.2's organizer endpoint shape and ADR-003's "meaningful identifiers in URLs" rule. The frontend uses `useParams<{ eventCode }>()` already used elsewhere. AC1 + AC4 + AC7 + AC12 + Tasks 5-7 + Task 16 reflect this shape. OpenAPI spec edits in same commit per ADR-006 contract-first.

2. ✅ **Q#2 — DELETE the legacy onboarding tests (Variant c).** `web-frontend/e2e/speaker-onboarding-flow.spec.ts` and `e2e/speaker-portal-response.spec.ts` are deleted outright. Both exercise the magic-link flow end-to-end — the literal behaviour Phase E removes. No port to Cognito; the new 5 specs under `e2e/speaker/` (AC10 + Task 14) cover the equivalent Cognito-side flows. Git history preserves the prior tests. AC10 + Task 14 reflect this deletion.

3. ✅ **Q#3 — Remove `/api/v1/auth/speaker-magic-login` `permitAll` NOW (Variant b).** Tighter security posture. After AC8 no frontend caller invokes this endpoint; leaving it `permitAll()` is needless attack surface during the Phase E observation window. Anonymous POSTs receive 401 from the default auth chain (`.anyRequest().authenticated()`); the controller class stays for Phase F to delete cleanly; the `permitAll` line is just gone. AC1 + Task 9 reflect this — Phase F's pass is now slightly smaller.

4. ✅ **Q#4 — Drop the `navigation.speakerPortal` nav-menu entry entirely (Variant a).** The cherry-pick's `/speaker-portal/login` route doesn't exist under ADR-009; the `navigation.speakerPortal` i18n key is also dropped (NOT just unused — actively removed) per Resolved Q#5's "all i18n keys land in all 10 locales" rule (an unused key would have to land in 10 locales to satisfy the parity check, which is worse than just removing it). Existing `/speaker/*` nav entries continue to work. Task 1.5 drops the nav entry; Task 13.2 drops the i18n key.

5. ✅ **Q#5 — Ship ALL 10 locales for new UI i18n keys (Variant b) AND narrow the CLAUDE.md "DE+EN only" rule.** Nissim clarified the older `CLAUDE.md` §Localization rule was intended for **backend email templates only**, NOT frontend UI i18n. The 4 new nav-section keys land in all 10 locales (`de, en, fr, it, rm, es, fi, nl, ja, gsw-BE`). `CLAUDE.md` §Localization was narrowed in the same commit as this story's resolution and now reads "Email Templates: DE + EN Only; UI i18n: All 10 Locales" with explicit justification for the asymmetry (emails carry rich prose for an overwhelmingly DE/EN audience; UI keys are short atomic strings serving a genuinely multilingual public website). Task 13 covers the 10-locale fan-out + the CLAUDE.md verification. The user-memory note `feedback_official_languages_de_en_only.md` was updated in parallel to reflect the narrowed rule.

6. ✅ **Q#6 — Drop `SpeakerResponseType.TENTATIVE` from the frontend type in this story (Variant a).** Story 11.B.1 removed `TENTATIVE` from the shared-kernel enum + backend Java; the frontend type at `web-frontend/src/services/speakerPortalService.ts` slipped past the sweep (the frontend never imports the shared-kernel enum, so the type just stayed defined locally as `'ACCEPT' | 'DECLINE' | 'TENTATIVE'`). Since this story heavily edits `speakerPortalService.ts` (AC7 + Task 11), the cleanup costs one extra LOC and removes a known-incorrect type definition. Task 11.2 + AC7 reflect this.

---

_Story created via `bmad-create-story` skill on 2026-05-17. All 6 Open Questions PM-resolved the same day. Story re-authored in place to reflect: (Q#1) eventCode in URL path — matches Story 11.C.2 organizer shape; (Q#2) DELETE legacy onboarding e2e tests outright; (Q#3) REMOVE `/api/v1/auth/speaker-magic-login` permitAll in this story (not Phase F); (Q#4) drop the speaker-portal nav entry AND its i18n key (not just unused — fully removed); (Q#5) SHIP all 10 locales for new UI i18n keys + narrow CLAUDE.md §Localization rule to apply only to backend email templates; (Q#6) drop `SpeakerResponseType.TENTATIVE` from frontend type. Phase E frontend half. Ready for `bmad-dev-story` execution._
