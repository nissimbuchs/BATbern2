# Quick-Spec — Post-Event #2 Feedback (Bugs & Enhancements)

**Author:** Winston (System Architect) · **Date:** 2026-06-20 · **Source:** Nissim — feedback from the 2nd live event on the platform

## How to read this

- Each item below has: **Problem → Current state (with file refs) → Solution design → Data/API impact → Prod-safety / phasing → Decision**.
- **Design principle (Nissim):** _no backward compatibility_. One prod, one user (us). Forward solutions only — we delete and reshape rather than dual-path. **But** staging **is** prod, so every change must still be **independently deployable without endangering prod** (Flyway: never edit applied migrations; additive-then-cutover where a column/table changes).
- Effort: **S** ≈ ≤1 day, **M** ≈ 2–4 days, **L** ≈ 1–2 weeks.
- This is a story-detailing input, not a story itself. Group into epics/stories after review.

## Summary

| # | Item | Type | Area | Effort | Risk |
|---|------|------|------|--------|------|
| 1 | Close registration + waitlist on deadline | bug | Registration | S | low |
| 2 | Replace unstable WebSockets (live agenda control) | bug/arch | Real-time | L | med |
| 3 | Flexible event types — per-event editable copy | enh | Agenda model | M | med |
| 7 | Slot assignment: dynamic build + insert/swap | enh | Agenda model | M | med |
| 6 | Speaker-pool drag fails on mobile → tap-to-place | bug | Agenda UI | S | low |
| 4 | Company ↔ email-domain mismatch warning | enh | Data quality | M | low |
| 5 | Speaker company logo on presenter + live control | enh | Live ops | S | low |
| 8a | Vanessa #790 — "Agenda" title overlaps in present mode | bug | Present | S | low |
| 8b | Vanessa #791 — white background behind transparent logos | bug | Present/web | S | low |
| 9 | OWASP LLM + Agentic checklist in CI | enh | Security/CI | M | low |
| 10 | LinkedIn post drafts (before/during/after) | enh | Marketing | M | low |
| 11 | Notify speaker + moderator on new Q&A | enh | Notifications | S | low |
| 12 | Thank-you submitter stored as Cognito sub instead of username (capture bug) | bug | Backend + FE | S | low |
| 13 | Thank-you hearts missing in mobile hamburger | bug | Frontend | S | low |
| 14 | Homepage contact → info@batbern.ch | bug | Frontend | XS | low |
| 15 | Show "BATbern <n>" above title everywhere | enh | Frontend | S | low |
| 16 | Q&A author portrait fetch 404s for attendees (public endpoint is speaker-scoped) | bug | Frontend | S | low |
| 17 | Featured thank-you note missing from partner marquee | bug | (data) | XS | low |

---

## 1 — Close registration & waitlist on the deadline date

> **Status: ✅ Implemented.** Backend (authoritative): `RegistrationService.assertRegistrationOpen()` guards both public paths (`createRegistration` + `createRegistrationForAuthenticatedUser`), NOT the organizer `addParticipant`; new `RegistrationClosedException` → 409 `details.code=REGISTRATION_CLOSED` (+ `deadline`) via `GlobalExceptionHandler`; falls back to event start when no deadline. 2 new integration tests pass (register + waitlist both closed). Frontend: `isRegistrationClosed()` in `homePagePhase.ts` hides the hero CTA + shows a "registration closed" banner; new `public.registrationClosed` i18n key in all 10 locales; 5 new phase tests pass; type-check clean. **Note:** chose not to add the new error code to the OpenAPI spec as a discrete schema (the shared `ErrorResponse` already carries `details.code`); add an example in a doc pass if desired.

**Problem.** Registration and waitlist stay open past the intended deadline. Closing should happen automatically on the configured registration-deadline date.

**Current state.** `registrationDeadline` **already exists** but is **never enforced** — open/closed is driven by workflow state + capacity only.
- `services/event-management-service/.../domain/Event.java:71` — `registrationDeadline` Instant.
- `services/event-management-service/.../service/RegistrationService.java:148-161` — capacity check only; no deadline check.
- `web-frontend/src/pages/public/HomePage.tsx:105-109` + `homePagePhase.ts:156` — gate on `workflowState ∈ {AGENDA_PUBLISHED, EVENT_LIVE}`, not on the deadline.
- Banner: `web-frontend/src/components/public/RegistrationStatusBanner.tsx:102-109`.

**Solution design.**
1. **Backend (authoritative):** in `RegistrationService`, reject new registrations **and** waitlist joins when `now > registrationDeadline` → return a typed `REGISTRATION_CLOSED` 409 (distinct from `EVENT_FULL`). Deadline closes *both* paths (one date, no separate waitlist deadline).
2. **Public gate:** derive `registrationOpen = workflowOpen && now <= registrationDeadline` in `homePagePhase.ts`; flip the banner/CTA to a "registration closed on {date}" state.
3. **Defaulting:** if `registrationDeadline` is null, fall back to event start (so a missing deadline never leaves it open forever).

**Data/API.** No schema change (field exists). Add the new error code to the events OpenAPI spec + regenerate types.

**Prod-safety.** Pure logic add; ship backend + frontend together. Add an integration test asserting 409 after the deadline for both register and waitlist.

---

## 2 — Replace unstable WebSockets for live agenda control

**Decision (Q1):** **REST polling only. Drop WebSockets entirely** (web + watch).

**Problem.** Live control (organizer shifts the whole agenda when a session runs long/short) and the watch app rely on STOMP/WebSockets that drop and desync.

**Current state — root cause confirmed.**
- STOMP over SockJS (`/ws`) for web + raw WS (`/api/v1/watch/ws`) for watch; **in-memory `SimpleBroker`** (not externalized).
  - `services/event-management-service/.../config/WebSocketConfig.java:40-74`
  - `services/event-management-service/.../watch/WatchWebSocketController.java:104-141` (END/EXTEND/DELAY actions)
  - `services/event-management-service/.../watch/JwtStompInterceptor.java:35-67`
- **No ALB sticky sessions**; multi-task Fargate → reconnect lands on a task with no subscription state. ALB idle timeout 120s (`infrastructure/lib/stacks/api-gateway-service-stack.ts:256-259`); watch only stays up via a 20s client heartbeat.
- Presenter **already** polls REST every 60s and uses STOMP only as a cache-invalidation ping: `web-frontend/src/hooks/usePresentationData.ts:63-104`.
- Watch: `apps/BATbern-watch/.../Data/WebSocketClient.swift`, `WebSocketService.swift` — fully WS-dependent, no REST fallback.

**Solution design — "command via REST, read via poll".**
1. **Single source of truth endpoint:** `GET /api/v1/events/{eventCode}/live-timing` returning the live agenda projection: current session, per-slot scheduled vs actual start/end (offsets), organizer-presence flag, a monotonically increasing `version` (or `ETag`/`Last-Modified`). Cheap, cacheable, served by any task (stateless).
2. **Mutations stay REST** (they already are conceptually): `POST .../live-timing/actions` with `{type: END_SESSION | EXTEND_SESSION | DELAY_TO_PREVIOUS, ...}`. Each mutation bumps `version` and recomputes downstream offsets (cascade logic moves from the WS controller into a plain service).
3. **Consumers poll adaptively:**
   - Presenter / live-control / public countdown: poll every **3–5s while the event is LIVE**, back off to 30–60s when not live or tab hidden. Use `If-None-Match` so unchanged polls are 304s (near-zero cost).
   - Watch app: same `live-timing` endpoint, poll ~5s while screen active / session running; longer when wrist down. Reuse the existing `OfflineActionQueue` for action replay.
4. **Teardown (forward, no compat):** remove `WebSocketConfig`, the `/ws` + `/api/v1/watch/ws` endpoints, `JwtStompInterceptor`, the frontend STOMP/SockJS client, and the watch `WebSocketClient/Service`. Drop the `sockjs/stompjs` deps.

**Data/API.** New `live-timing` GET + `actions` POST in events OpenAPI; remove the WS contract. No DB change (timing already persisted on sessions).

**Prod-safety / phasing.**
- **P1:** add `GET live-timing` + `POST actions` alongside the existing WS path (additive). Migrate the **web** presenter + live-control to poll. Verify on `beta.batbern.ch` (frontend-only canary) under a real LIVE-ish event.
- **P2:** migrate the **watch app** (separate App Store release train — see watch-app memory; bump build, don't archive uncommitted) to poll.
- **P3:** once both consumers are off WS, delete the WS server + client code in a dedicated cleanup PR.
- This keeps every step independently shippable; WS is removed only after nothing depends on it.

---

## 3 + 7 — Per-event editable agenda type & dynamic slot assignment

**Decision (Q2):** Keep **exactly the same knobs** as today's preset event types. Add an **"Edit event type" action inside slot assignment** that edits **a per-event copy** of the config — never the shared standard template. Extend the knob set to cover the new structural needs (apéro at end, 2 afternoon breaks).

**Problem.**
- (#3) Event types are a shared template; an organizer can't tailor one event's structure (apéro at the end, two breaks in an afternoon event) without mutating the global template.
- (#7) Slot assignment is fragile (absolute-time string matching) and lacks insert-between / swap-on-occupied; agenda should be built dynamically from start time + slot length + technical slots.

**Current state.**
- `EventTypeConfiguration` is a **shared template entity** keyed by enum (`full_day`/`afternoon`/`evening`); Event references it by enum: `services/event-management-service/.../entity/EventTypeConfiguration.java`, `domain/Event.java:137-140`, migrations `V10`/`V11`.
  - Knobs: `slot_duration`, `min/max_slots`, `theoretical_slots_am`, `break_slots`, `lunch_slots`, `break_duration`, `lunch_duration`, `moderation_start/end_duration`, `typical_start/end_time`, `default_capacity`.
- Timetable is **computed virtually** (no slot table) by a **hardcoded** algorithm `moderation → AM slots → break → lunch → PM slots → moderation`: `services/event-management-service/.../service/TimetableService.java:78-212`; DTO `dto/TimetableSlot.java`.
- Assignment UI uses **native HTML5 drag** (not @dnd-kit, though @dnd-kit is installed): `web-frontend/src/components/SlotAssignment/DragDropSlotAssignment/DragDropSlotAssignment.tsx`, `.../UnassignedSpeakersList/UnassignedSpeakersList.tsx`; hook `hooks/useSlotAssignment/useSlotAssignment.ts`.

**Solution design.**
1. **Per-event config copy (copy-on-edit):** new table `event_agenda_config` (FK → event, one row per event) holding the **same columns** as `EventTypeConfiguration` **plus** the new knobs below. Seed lazily: the first time an organizer opens **Edit event type** in slot assignment, copy the standard template's values into this row; thereafter the event reads its own copy. Events with no override continue to read the shared template (resolver: `event_agenda_config` if present, else template). The "Edit event type" dialog writes **only** to the per-event copy.
2. **New knobs for the requested structures** (added to both the template defaults and the per-event copy): `aperitif_slots` + `aperitif_duration` + `aperitif_position` (start/end, default end), and make break placement count-driven so an afternoon type can carry **2 breaks**. Keep them as simple knobs (not a freeform ordered list) per the decision.
3. **Dynamic timetable from knobs:** generalize `TimetableService.computeTimeline()` to fold an **ordered list of structural segments derived from the (per-event) knobs** — `[moderation_start, (AM speaker slots), break×n, lunch?, (PM speaker slots), break, aperitif?, moderation_end]` — computing each slot's start purely from `event start + Σ preceding durations`. No absolute times stored for technical slots; speaker-session times derive from their slot index.
4. **Stable slot identity (#7):** address slots by **deterministic slot key** (segment-type + ordinal), not by HH:MM string match. Assignment writes `{slotKey → sessionSlug}`; times are recomputed, so shrinking/extending earlier slots reflows the rest.
5. **Insert-between & swap (#7):** on drop **between** two slots → insert a speaker slot at that index and reflow following times; on drop **onto an occupied** slot → swap the two session assignments. Both go through one `assignSessionToSlot`/`reorderSlots` service path with optimistic update + rollback (extend `useSlotAssignment`).

**Data/API.** New `event_agenda_config` table (additive migration) + new agenda-config GET/PUT endpoints + timetable response keyed by `slotKey`. Regenerate FE + BE types.

**Prod-safety / phasing.**
- **P1 (backend, additive):** add table + resolver (template-fallback) + extended `computeTimeline`; default behavior identical to today when no override exists. Ship behind the resolver — zero visible change.
- **P2 (frontend):** "Edit event type" dialog (writes per-event copy) + apéro/break knobs.
- **P3 (frontend):** migrate assignment to `slotKey` addressing + insert/swap; pairs with #6.

---

## 6 — Speaker pool: no drag on mobile → reuse tap-to-place

**Problem.** The speaker pool uses HTML5 drag, which is dead on touch. Slot assignment **already has** a working mobile **tap-to-select → tap-to-place** pattern; reuse it for the pool.

**Current state.**
- Pool drag (desktop-only): `UnassignedSpeakersList.tsx:197` (`draggable`), tap handler only wired on mobile at `DragDropSlotAssignment.tsx:505`.
- Working mobile pattern: `DragDropSlotAssignment.tsx:344-360` (`handleTraySelect` → "armed" slot → `handleSlotTap`), styling `:607,:629`.

**Solution design.** Extract the select/place interaction into a small shared hook (e.g. `useTapToAssign`) holding `selectedSessionSlug` + "armed slot" state, and drive **both** the pool cards and the slot grid from it on touch viewports. On desktop keep drag; on touch, tapping a pool card selects it and highlights droppable slots, tapping a slot places it — same `assignSessionToSlot` path. Land this together with the #7 `slotKey` refactor so there's one assignment path.

**Prod-safety.** Frontend-only; verify on beta with a touch device / emulation.

---

## 4 — Warn on company ↔ email-domain mismatch for registered users

**Problem.** Registrants pick a company but their email domain may not match it (e.g. `john@gmail.com` registered under "Swisscom"). Organizers want a non-blocking warning + a way to spot/fix these.

**Current state.**
- `Company` has **no email-domain field**: `services/company-user-management-service/.../domain/Company.java` (has name/displayName/swissUID/website/logoUrl — no domain).
- Registration stores denormalized `attendeeEmail` + `attendeeCompanyId`: `services/event-management-service/.../domain/Registration.java:166-176`.

**Solution design.**
1. **Add `email_domains` to Company** (a small set, e.g. `["swisscom.com","swisscom.ch"]`) — many companies have several. Seed initial values by aggregating the most common domains already seen among each company's existing users (one-off backfill script), organizer-editable thereafter.
2. **Mismatch check** in `RegistrationService` (and/or a CUMS helper): on register, compare the email domain to the company's `email_domains`; if no match, persist a lightweight `company_email_mismatch = true` flag on the registration (don't block — generic/personal-email attendees are legitimate).
3. **Surface to organizers** on the participants list (event-detail) as a ⚠ badge with tooltip ("email domain doesn't match {company}"), with a quick "change company" / "confirm anyway" action. Optional inline hint at registration time is a follow-up, not required.

**Data/API.** Additive: `company.email_domains` column + flag on registration + backfill. New field in companies + events OpenAPI.

**Prod-safety.** Fully additive; flag defaults false. Backfill script is read-mostly. Ship backend first, then the organizer badge.

---

## 5 — Speaker company logo on presenter + live control

**Problem.** Presenter and live-control show speaker names but not their company logo.

**Current state.**
- `Company.logoUrl` exists (`company-user-management-service/.../domain/Company.java:63`).
- The watch/session speaker type **already carries** `companyLogoUrl?` (`web-frontend/src/types/generated/events-api.types.ts:~4033`) — enrichment pattern exists from Q&A.
- Live-control render: `web-frontend/src/components/organizer/LiveControl/ActiveSessionCard.tsx:33-39,168` (names only). Presenter: analogous component under the `/present` route.

**Solution design.** Ensure the session-speaker enrichment populates `companyLogoUrl` (resolve speaker `username` → user → `companyId` → company `logoUrl`, via the existing cached `UserApiClient`/companies path — no JPQL cross-service join per ADR-004). Render the logo next to the speaker name in `ActiveSessionCard` and the presenter speaker block. Apply the **white-background treatment from #8b** so transparent logos stay legible on the dark presenter background.

**Prod-safety.** Mostly frontend; backend enrichment is additive. Note the CDN resize Lambda 503s on large sources >~1200px — request a **thumbnail-sized** logo variant (`?w=` ≤512), not the raw asset.

---

## 8 — Vanessa's open GitHub bugs

### 8a — #790: "Agenda" heading overlaps agenda items in present mode
> **Status: ✅ Already fixed** (Nissim confirmed 2026-06-20 — resolved by commit `80195136`). No further work; not part of Epic 15.

### 8b — #791: white background behind transparent speaker logos

> **Status: ✅ Implemented.** New MUI-free `LogoBadge` (`components/shared/LogoBadge/`) — white rounded chip — applied to the speaker company logo in presentation `SpeakerCard` (covers `TwoSpeakerCard`, which reuses it). Reusable by item 5 (live-control logos) later. **#8a (#790 present-mode title overlap) NOT done** — needs reproduction on BATbern59 to confirm whether the recent `80195136` fix already covers it.
**Problem.** Logos uploaded with transparent backgrounds (Swisscom, Energie Suisse, Adesso on BATbern59) are unreadable on the dark website/presentation background.
**Solution design.** Render company/speaker logos inside a **rounded white "chip"** wrapper (white bg + small padding + radius) wherever a logo sits on a dark surface — presenter, live control, and any dark public surface. Pure CSS treatment at the render site (a shared `<LogoBadge>` wrapper); no asset reprocessing, no migration. Reuse the same wrapper introduced in #5.

---

## 9 — OWASP Top-10-for-LLM + Agentic checklist in the build

**Problem.** CI has solid web AppSec (ZAP, Trivy, npm audit, Dependabot, SonarCloud) but **nothing covering the AI surface**.

**Current state.**
- Security CI: `.github/workflows/security-scan.yml` (ZAP, weekly+manual, non-blocking), `scripts/security-scan.sh` (Trivy→SARIF), `make audit-security`, Dependabot.
- **AI surface to cover:** OpenAI-backed `POST /events/{code}/ai/description`, `/ai/theme-image`, `/ai/theme-image/apply`, and **organizer-editable prompts** (`/ai-prompts` CRUD) — `docs/api/events-api.openapi.yml`. Plus the new **LinkedIn-draft** generator (#10). Feature-flagged `FEATURES_AI_ENABLED`.

**Solution design.** Hybrid (matches the existing advisory pattern, not a hard blocker initially):
1. **Checklist doc** `docs/security/owasp-llm-agentic-checklist.md` mapping the relevant **OWASP Top 10 for LLM Apps** (esp. LLM01 Prompt Injection, LLM02 Insecure Output Handling, LLM06 Sensitive Info Disclosure, LLM08 Excessive Agency) and **OWASP Agentic threats** items to each AI endpoint + the organizer-prompt feature, with the mitigation in place / owed.
2. **Targeted automated tests** wired into CI: (a) prompt-injection probes against the AI endpoints (assert the model can't be steered to exfiltrate system prompt / other-org data); (b) output-handling tests (AI output is treated as untrusted — rendered/stored escaped, never executed); (c) a scan asserting **no secrets/PII are interpolated into prompts** and that organizer-editable prompts can't reach system-level instructions.
3. **CI job** `ai-security` mirroring ZAP's advisory (non-blocking) posture, with a flip-to-blocking follow-up once green.

**Prod-safety.** CI/test + docs only; no runtime change.

---

## 10 — LinkedIn post drafts (before / during / after)

**Decision (Q3):** **Draft-only.** Direct LinkedIn publishing is a later phase.

**Problem.** Organizers want LinkedIn copy to post before, during, and after sessions — the "during" posts with images.

**Current state.** No LinkedIn/social integration. Reusable: OpenAI content-gen + organizer-editable prompts, DE/EN email-template/render infra, and rich event/session/speaker data incl. `linkedInUrl` + speaker/theme/event photos. Related idea: GitHub idea-vote **#761** "LinkedIn-Native BATbern (agent-drafted)".

**Solution design.**
1. **Draft generator** reusing the existing AI-prompt machinery: `POST /events/{code}/linkedin-drafts?phase=BEFORE|DURING|AFTER&sessionSlug=…` returning DE + EN copy. Templates per phase:
   - **BEFORE:** event teaser — title, date, venue, headline speakers/topics, registration link.
   - **DURING:** per-session "happening now" — session title, speaker + company, one-line hook + **suggested image(s)** (theme image, speaker photo, or an uploaded event photo).
   - **AFTER:** thank-you / recap — highlights, slides-online link, appreciation.
2. **Image handling for DURING:** surface selectable images from existing event/session/speaker assets (served via CDN, thumbnail variant to dodge the resize-Lambda 503); the draft references chosen image URLs for the organizer to download/attach. No upload-to-LinkedIn yet.
3. **Surface:** a "LinkedIn drafts" panel on event-detail with copy-to-clipboard per language; organizer-editable prompt (like other AI prompts).
4. **Forward hook:** keep the generation output a clean `{text, imageUrls[]}` so a later "direct publish" phase can post via the LinkedIn API without reshaping anything.

**Prod-safety.** Additive endpoint + frontend panel; gated by `FEATURES_AI_ENABLED`. Covered by the #9 AI-security checklist.

---

## 11 — Notify speaker + moderator on new Q&A question

**Problem.** When an attendee posts a new question to a session, the speaker and moderator aren't notified.

**Current state.** No notification hook in the Q&A post path: `services/event-management-service/.../service/SessionQnaService.java:~182` (creates `SessionQnaPost`, then nothing). Roles resolvable via `SessionUser.speakerRole` (`PRIMARY_SPEAKER`, `MODERATOR`) — `domain/SessionUser.java:77-79`. Email infra + DE/EN templating exist.

**Solution design.**
1. After persisting a **top-level** question (skip threaded replies / self-posts), resolve the session's `PRIMARY_SPEAKER` + `MODERATOR` usernames via `SessionUserRepository`, then their emails via the cached `UserApiClient`.
2. **Throttle to avoid a live-session flood:** batch into a **digest** — at most one "you have N new question(s)" email per recipient per session per ~10–15 min window (a short-lived scheduled flush or a "last-notified-at" guard), with a deep link to the session Q&A. New DE+EN email template `qna-new-questions-{de|en}.html` (emails are DE/EN only per the localization rule).
3. Respect existing notification/opt-out preferences if present.

**Data/API.** New email template + a small "last notified at"/pending-count tracking (column or in-memory with ShedLock-guarded flush — mind the ShedLock test-flakiness pattern). No public API change.

**Prod-safety.** Additive. **Staging-is-prod:** ensure E2E/Bruno tests can't trigger real outbound mail — gate sends behind the test guard used elsewhere.

---

## 12 — Thank-you cards show a Cognito sub instead of the submitter's name

> **Status: ✅ Backend implemented** (branch `feature/event-2-feedback-quick-wins`). Added `SecurityContextHelper.getCurrentUsernameOrNull()`; switched `OrganizerThanksController` (the bug) + `EventPhotoController`, `TaskTemplateController`, `AdminSettingsController` (same latent bug) off `authentication.getName()`; new migration `V116__fix_organizer_thanks_username_from_cognito_sub.sql`; new test `should_storeCanonicalUsername_when_jwtPrincipalNameIsCognitoSub`. EMS compiles clean; all 7 thanks integration tests pass (incl. the new SSO-principal one). **Frontend ✅** — `EventAppreciationTab` now renders the shared `UserAvatar` (avatar + resolved name) instead of the raw author string; type-check clean.

**Decision (Q4 + follow-up):** The architecture is already right — **the DB stores only the username and resolves the name from CUMS at read time** (ADR-004). The visible UUID is a **data bug at capture time** (wrong identity stored), plus a small frontend display upgrade. **Anonymous handling is unchanged** — anonymous users may still submit a note (keep the existing clap-style anonymous-note path).

**Symptom.** Organizer Appreciation tab renders the submitter as `c334a852-10c1-70d2-f403-136e0a60acf7`. The API returned `thankedByUsername = c334a852-…` with `thankedByFirstName/LastName/CompanyName = null`, even though the note was submitted by `nissim.buchs`.

**Root cause (confirmed).** The submit controller captures the JWT **principal name**, which is the Cognito **`sub`**, not the canonical username:
- `services/event-management-service/.../controller/OrganizerThanksController.java:107-114` — `resolveUsername()` returns `authentication.getName()` (= `sub` when `custom:username` isn't the principal claim, e.g. Google-SSO / JIT users).
- So `organizer_thanks.thanked_by_username` stored the sub. Read-time enrichment (`OrganizerThanksService.java:107-130` → `loadAuthorPortraits` → `findThanksAuthorPortraitsByUsernames`) joins `user_profiles WHERE username = '<sub>'` → no match → all name fields null → the card falls back to the raw value.
- This is the **Pattern 3b twin** (empty `custom:username` → principal falls back to `sub`), already solved elsewhere via `SecurityContextHelper.getCurrentUsername()` with a DB-fallback by `cognito_user_id = jwt.sub` (commit `2719ab47`). This controller bypassed that helper.

**Architecture confirmation (no change needed).** `OrganizerThanks` stores **only** `thanked_by_username` (`domain/OrganizerThanks.java:50`) — no duplicated profile columns. First/last/company are resolved from CUMS at read time. ✅ Keep as-is.

**Solution design.**
1. **Fix capture (the real bug):** in `OrganizerThanksController.resolveUsername()`, replace `authentication.getName()` with the shared `SecurityContextHelper.getCurrentUsername()` (Pattern 3b twin — resolves `custom:username`, DB-fallback by sub) so new submissions store `nissim.buchs`. Keep the null-for-anonymous handling. Audit any other EMS controller still using `authentication.getName()` for identity capture and switch them too.
2. **Repair existing data (forward, not a migration edit):** remap stored sub-shaped values to canonical usernames — `UPDATE organizer_thanks t SET thanked_by_username = up.username FROM user_profiles up WHERE up.cognito_user_id = t.thanked_by_username` (a new higher-numbered forward migration, or a one-off via prod tunnel given it's tiny — single row today). Never edit an applied migration.
3. **Frontend polish:** replace the ad-hoc `authorName()` string in `EventAppreciationTab.tsx:48-54` with the app's **shared user-display component** (avatar + resolved name); with capture fixed, names resolve and the raw-value fallback never shows. Anonymous notes keep the existing "Anonymous" chip.

**Out of scope (explicit):** anonymous note submission is unchanged — anonymous users may still leave a note (count + organizer-visible note), exactly as today.

**Prod-safety.** Capture fix + frontend are additive; the data repair is a forward migration (or a single-row tunnel fix). Add a test asserting a logged-in submit stores the canonical username (not the sub) for an SSO-style principal.

---

## 13 — Thank-you hearts missing from mobile hamburger

> **Status: ✅ Implemented.** Moved `ThankOrganizersNavButton` out of the mobile CTA strip into the primary nav-links group in `PublicNavigation.tsx` (right after "Past Events"), so the hearts are reliably visible at the top of the hamburger. PublicNavigation tests green (11).

**Problem.** The thank-you hearts entry shows on mobile generally but not inside the hamburger menu.

**Current state.** The button *is* present in the hamburger markup but buried after the identity block and possibly gated differently: `web-frontend/src/components/public/Navigation/PublicNavigation.tsx:290-293` (mobile) vs `:146-152` (desktop); component `ThankOrganizersNavButton.tsx`.

**Solution design.** Verify the live behavior on a device; the likely cause is ordering/gating (`thankableEventCode` resolution or placement after the identity section). Move the hearts entry into the primary mobile nav-links group (alongside Home/About/Past Events) so it's reliably visible, with the same `thankableEventCode` gate as desktop.

**Prod-safety.** Frontend-only; verify on beta.

---

## 14 — Retire `berner-architekten-treffen.ch` entirely → `@batbern.ch`

> **Status: ✅ Implemented** (scope expanded by Nissim from "footer" to "retire the old domain everywhere").
> **Swapped** (live/functional): `PublicFooter`, `AboutPage`, `PrivacyPage`, `SupportPage` (+ tests); SES sender in `cognito-stack` + `event/company/partner-management-stack` from-domain (now `batbern.ch`); `users-api.openapi.yml` examples + committed `user-api.types.ts`; `User.java` doc + `UserAdditionalEmailsIntegrationTest` + `RegistrationEmailServiceTest` sample data; `api-gateway` test config; current docs (`README`, `shared-kernel/README`, `docs/user-guide/**`).
> **⚠️ SES sender deviation:** Nissim asked for `no-reply@batbern.ch`; I used **`noreply@batbern.ch`** to match the already-verified prod sender (a wrong/unverified sender bounces ALL mail). Confirm before merge — and confirm `batbern.ch` is a verified SES **domain** identity so the non-prod from-domain also sends. Needs a **CDK deploy** (infra change, not frontend-only).
> **Intentionally preserved** (history, not rewritten): `_bmad-output/*` incident artifacts, `docs/plans/user-identity-collision-fix-plan.md`, the 2026-05-20 `email-forwarder.test.ts` regression literal + `sender-auth.ts` comment, decommissioned `apps/BATspa-old/*`, archived wireframes/stories.
> **Doc follow-up:** `docs/architecture/02,06` + `docs/guides/aws-ses-configuration.md`, `aws-setup-guide.md` still describe the old SES domain — update in a doc pass (they document historical setup steps).

### (original: Homepage contact → info@batbern.ch)

**Problem.** Footer shows the legacy address.

**Current state.** `web-frontend/src/components/public/Footer/PublicFooter.tsx:26` → `info@berner-architekten-treffen.ch` (both `href` and link text).

**Solution design.** Change both to `info@batbern.ch`. **Sweep** the repo for other occurrences of the old address (email templates, about/privacy pages, infra config) and update consistently — but **exclude `**/db/migration/**`** from any find-and-replace (applied-migration rule).

**Prod-safety.** Trivial frontend (+ any template) change.

---

## 15 — Show "BATbern <number>" above the title everywhere the event appears

> **Status: ✅ Implemented.** New MUI-free `EventEditionLabel` (`components/shared/EventEditionLabel/`) renders "BATbern <n>" (brand string, no i18n). Wired as an eyebrow above the title in: homepage hero (`HeroSection`), attendee "My Past Events" + speaker "My Sessions" active & past cards. Archive `EventCard` already shows the code; public event detail reuses the hero. Type-check clean.

**Problem.** The edition number isn't consistently shown. It should appear before/above the title on the public website, the speaker "My Sessions", and the attendee "My Past Events".

**Current state.** Shown only on archive cards (`EventCard.tsx:122,146`); missing from homepage hero (`HeroSection.tsx:220`), attendee dashboard (`AttendeeDashboardPage.tsx:40`), speaker dashboard (`SpeakerDashboardPage.tsx:90`).

**Solution design.** Introduce a tiny shared `<EventEditionLabel>` (renders `BATbern <n>` — display form with a space, derived from `eventCode`/`eventNumber`) and place it as an eyebrow/kicker above the title in: homepage hero, event detail, archive (reuse), speaker My-Sessions, attendee My-Past-Events. One component → consistent placement and styling everywhere.

**Prod-safety.** Frontend-only; verify on beta. (Public pages are Tailwind-only — keep the component MUI-free so it can live on the homepage bundle.)

---

## 16 — Q&A author portrait fetch 404s for attendee authors

> **Status: ✅ Implemented.** (Reported mid-batch: homepage logs `404 /api/v1/public/users/daniel.schwarz` even though his Q&A name renders.)

**Root cause.** `SessionQnaThread` renders each post's author via `SpeakerDisplay`, which lazy-loads the portrait from `GET /api/v1/public/users/{username}` (`useUserPortrait`). That endpoint (`PublicUserService`) is **SPEAKER-scoped by design** — it 404s for any non-speaker to prevent attendee-identity enumeration. Q&A authors are attendees, so the portrait fetch always 404s. The name already renders fine (server-side `QnaAuthorProjection` enrichment), making the fetch pure noise.

**Fix.** Added a `lazyLoadPortrait` prop (default `true`) to `SpeakerDisplay`; `SessionQnaThread` passes `lazyLoadPortrait={false}`, so Q&A authors render an initials avatar and never hit the speaker-only endpoint. Type-check + 212 affected FE tests green.

## 17 — Featured thank-you note missing from the partner marquee

> **Status: ✅ Fixed by #12's V116 migration (no extra code).**

**Root cause.** Same as #12. The public marquee (`TestimonialSection` → `useFeaturedThanks` → `GET /thanks/featured` → `OrganizerThanksRepository.findFeaturedRandom`) does `INNER JOIN user_profiles up ON up.username = ot.thanked_by_username`. Nissim's featured note stored the Cognito **sub** in `thanked_by_username` (the #12 capture bug), which has no matching `user_profiles.username`, so the INNER JOIN drops it → it never reaches the marquee.

**Fix.** `V116__fix_organizer_thanks_username_from_cognito_sub.sql` remaps the stored sub → canonical username; the join then matches and the note appears. The frontend marquee is already correctly wired (verified). **Takes effect once V116 runs in prod.**

## Suggested grouping into epics

- **Epic A — Live event operations:** #2 (real-time rework), #5 (speaker logos), #8a/#8b (present-mode fixes), #11 (Q&A notify). _The highest-impact cluster for the next live event._
- **Epic B — Agenda authoring:** #3 + #7 + #6 (per-event config + dynamic slots + mobile tap). _One coherent data-model + UI change._
- **Epic C — Public site polish:** #1 (deadline), #13, #14, #15, #12. _Mostly small, fast wins._
- **Epic D — Data quality:** #4 (company/email match).
- **Epic E — Growth & safety:** #10 (LinkedIn drafts), #9 (AI security checklist). _Naturally paired — #9 covers #10's new AI surface._

## Open questions for the PM (carry into story detailing)

1. **Registration deadline (#1):** is the deadline date-only (closes 23:59 local) or a precise timestamp? And do we want a visible countdown to the deadline on the public page, or just the closed state?
2. **Agenda knobs (#3):** confirm the exact new knob set — is it `aperitif (on/off + position)` + `break count` only, or do you also want configurable apéro/break durations per event?
3. **Company domains (#4):** should the mismatch warning ever **block** registration for known corporate domains, or always warn-only? And who maintains a company's domain list — organizers, or auto-learned from attendees?
4. **Q&A notifications (#11):** email only, or also an in-app/portal indicator for speakers? And is a ~10–15 min digest window acceptable, or do you want near-immediate per-question emails?
5. **LinkedIn (#10):** for the "during" image, do you want the platform to auto-compose a branded image (title + speaker over the theme image), or just surface existing photos to attach manually?
