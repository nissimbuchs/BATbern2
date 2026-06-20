# Story 15.7: Notify speaker + moderator on new Q&A question (with per-user frequency preference)

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **session speaker, co-speaker, or moderator**,
I want **to be emailed when attendees post new questions in my session's Q&A — at a cadence I control (live digest, once a day, or off)**,
so that **I can follow and respond to audience questions without keeping the Q&A page open, while keeping control of my inbox**.

> Source: `docs/prd/epic-15-post-event-2-hardening.md` → **Story 15.7 (Item #11)**.
> Epic 15 = post-Event-#2 architectural hardening. Design principle (Nissim): **no backward
> compatibility (one prod, one user) — forward solutions only.** But **staging IS production**, so
> every part of this story must be independently deployable without endangering prod (additive
> migrations; never edit applied migrations; no real outbound mail from tests).

> **Scope note:** the spec's "notify speaker + moderator" was expanded during story planning
> (Nissim, 2026-06-20) into a **per-user three-way frequency preference**. See **Resolved
> Decisions** at the end. This makes the story span **CUMS** (new preference + OpenAPI + 10-locale
> UI) and **EMS** (dual-cadence digest engine). It is structured in two independently-deployable
> parts, A then B.

## Acceptance Criteria

**Preference plumbing (Part A — CUMS + frontend):**
1. **AC1** — A new user preference `qnaNotificationFrequency` exists with values `live` | `daily` | `off`, **default `live`**, persisted in CUMS, exposed on the users-API preferences object, and editable from the user profile/settings UI. New + existing users who never touch it are treated as `live`.
2. **AC2** — The settings control is a 3-option selector (Live / Daily / Off) with i18n keys present in **all 10 locales** (`de, en, fr, it, rm, es, fi, nl, ja, gsw-BE`); EN + DE first-class copy.
3. **AC3** — EMS can read any recipient's `qnaNotificationFrequency` via the cached `UserApiClient` (15-min Caffeine), defaulting to `live` when the field is absent/null.

**Digest engine (Part B — EMS):**
4. **AC4** — GIVEN an **OPEN** session Q&A window with a primary speaker, co-speaker(s), and moderator, WHEN an attendee posts a new **top-level** question, THEN each of those recipients whose preference is **not `off`** receives an email notification (digest-windowed per their cadence — AC5/AC6).
5. **AC5 (LIVE)** — A recipient set to `live` receives at most **one** "N new question(s)" email per session per **15-minute** window; the first email arrives within ~one flush interval (≤5 min) of the first new question.
6. **AC6 (DAILY)** — A recipient set to `daily` receives at most **one** Q&A email per session per **24 hours**.
7. **AC7 (OFF)** — A recipient set to `off` is never emailed.
8. **AC8 (freeze wins)** — Once a session's Q&A window is **FROZEN** (read-only, after `closesAt` or organizer early-close), **no** digest (LIVE or DAILY) is ever generated for that window — even for questions posted before freeze whose digest had not yet fired.
9. **AC9 (exclusions)** — A reply (`parentPostId != null`) does not notify. A self-post does not notify its own author, but **does** still notify the session's other recipients. Panelists (`PANELIST`) are **not** notified.
10. **AC10 (enrichment, ADR-004)** — Recipient email + preferred language + the Q&A preference are resolved at send time from CUMS via the cached `UserApiClient`, never duplicated/stored on the notification row. Email is rendered **DE + EN only**; any non-DE/EN preference falls back to EN.
11. **AC11 (multi-task safety)** — The digest flush is guarded by ShedLock so it fires once across the multi-task Fargate deployment, and its integration test does not leak a committed lock row across tests.
12. **AC12 (staging-safe)** — No real outbound mail is sent in `test`/CI/Bruno (no SES call); the flush must not email real recipients during Bruno/E2E runs.

## Tasks / Subtasks

### Part A — Preference plumbing (CUMS + frontend) — ship first; additive, inert until Part B

- [x] **Task A1 — CUMS schema (additive migration)** (AC: 1)
  - [x] Add `V20__add_qna_notification_frequency_preference.sql`.
  - [x] `ALTER TABLE user_profiles ADD COLUMN pref_qna_notification_frequency VARCHAR(10) DEFAULT 'live' CHECK (… IN ('live','daily','off'))`. **Nullable** (NOT `NOT NULL`) to mirror the sibling `pref_*` columns — Hibernate writes NULLs to every `pref_` column when the `UserPreferences` embeddable is null, so a NOT NULL column would 500 on `setPreferences(null)`. App/read default supplies `live`.
  - [x] No applied CUMS migration edited.
- [x] **Task A2 — CUMS entity + service mapping** (AC: 1)
  - [x] Added `qnaNotificationFrequency` (String, `@Builder.Default = "live"`) to `UserPreferences` `@Embeddable`, `@Column(name = "pref_qna_notification_frequency", length = 10)`.
  - [x] Wired write (`updateCurrentUserPreferences`, unguarded like siblings — PUT is full-replace; DTO default `live` so never null) + read (`mapPreferencesToDTO`, null-coalesce to `live`).
- [x] **Task A3 — users-API OpenAPI + regen** (AC: 1, 3)
  - [x] Added `qnaNotificationFrequency` to the `UserPreferences` schema. **Enum values QUOTED** (`["live","daily","off"]`) — unquoted `off` is a YAML 1.1 boolean and the generator emitted `FALSE("false")`; quoting fixes it to `OFF("off")`.
  - [x] Regenerated backend DTOs (`openApiGenerateUsers`) + frontend types (`generate:api-types:users`); frontend types committed.
- [x] **Task A4 — Frontend settings control + i18n** (AC: 1, 2)
  - [x] 3-option selector (Live / Daily / Off) added to `UserSettingsTab.tsx` (below the existing notification-frequency group); local `UserPreferences` type + `userAccountApi` read/write mappers + a `mapQnaNotificationFrequency` helper handle the UPPER↔lower case bridge.
  - [x] i18n keys (`qnaFrequency`, `qnaFrequencyHelp`, `qnaFrequencyLive`, `qnaFrequencyDaily`, `qnaFrequencyOff`) added under `settings.notifications` in **all 10 locales** (gsw-BE had no `notifications` block — added one with the 5 keys).
  - [x] Vitest: 2 new tests (renders 3 options defaulting to live; save calls `updateUserPreferences` with `qnaNotificationFrequency: 'OFF'`).

### Part B — Digest engine (EMS) — ship after A is live

> **Implemented 2026-06-20. Deviations from the spec (both improvements, captured here):**
> 1. **Recipient preference read via `getQnaNotificationFrequency` (new `UserApiClient` method) over `?include=preferences`**, mirroring the proven `getPreferredLanguage` — NOT by extending `notification.UserPreferences`. That DTO is consumed by `getPreferences()`, which hits `/users/{username}/preferences` — an endpoint that does not exist in CUMS (only the legacy `NotificationService` with a `.bak` twin uses it). The `?include=preferences` path is live and cached.
> 2. **No lookback / candidate-window-by-posts query.** Freeze-wins means only OPEN windows ever notify, and OPEN windows are bounded by active events, so the flush simply scans `findByStatus(OPEN)`. Simpler and correct.
> 3. Recipients resolved via `findBySessionId` + role filter (co-speakers can be many; the `Optional` finder would miss extras).

- [x] **Task B1 — Notification-state schema (additive migration)** (AC: 5, 6, 11)
  - [x] Add `V117__create_qna_notification_state.sql` (EMS HEAD is `V116`).
  - [x] Table `session_qna_notification`, composite PK `(window_id, recipient_username)`. Columns: `window_id UUID NOT NULL`, `recipient_username VARCHAR(100) NOT NULL`, `last_notified_at TIMESTAMP NULL`, `notified_through TIMESTAMP NULL` (high-water mark = `created_at` of the newest post already covered by a sent digest), `created_at TIMESTAMP NOT NULL DEFAULT now()`, `updated_at TIMESTAMP NOT NULL DEFAULT now()`.
  - [x] FK `window_id` → `session_qna_window(id)` **ON DELETE CASCADE** (same-service UUID FK ✅ ADR-003). **No** FK / no UUID on `recipient_username` (cross-service meaningful ID — ADR-003). Index on `window_id`.
- [x] **Task B2 — Entity + repository** (AC: 5, 6, 8, 9)
  - [x] `domain/SessionQnaNotification.java` — composite key `(windowId, recipientUsername)`; `@PrePersist/@PreUpdate` timestamps.
  - [x] `repository/SessionQnaNotificationRepository.java` — `findByWindowIdAndRecipientUsername(UUID, String)`.
  - [x] `SessionQnaWindowRepository` — add a finder for **OPEN** windows with recent activity, e.g. `findByStatus(QnaWindowStatus.OPEN)` (a `findByStatusAndClosesAtBefore` already exists at `:27` — mirror its style). The flush filters to `status == OPEN` (AC8 freeze-wins).
  - [x] `SessionQnaPostRepository` — add: (a) candidate windows = `SELECT DISTINCT p.windowId FROM SessionQnaPost p WHERE p.parentPostId IS NULL AND p.removedAt IS NULL AND p.createdAt >= :cutoff`; (b) per-recipient new-post count + `MAX(createdAt)` over top-level, non-removed posts in a window with `createdAt > :since` AND `postedByUsername <> :recipient`. Read the repo first; add, don't alter existing finders.
- [x] **Task B3 — `QnaNotificationService` (single scheduled flush, per-recipient cadence)** (AC: 4–11)
  - [x] New `service/QnaNotificationService.java`. **No inline send from `SessionQnaService.addPost`** — trigger is the scheduled flush. (Keeps the attendee POST path unchanged → zero regression risk; recipients resolved at send time.)
  - [x] **One** flush: `@Scheduled(cron = "${qna.scheduled.notify.cron:0 */5 * * * *}")` (every 5 min) + `@SchedulerLock(name = "qnaDigestFlush", lockAtMostFor = "15m", lockAtLeastFor = "30s")` + `@Transactional`. Model on `SessionQnaScheduledService.java:34-36`.
  - [x] Config props (`application.yml`, beside the existing `qna.*`): `qna.notify.live-window-minutes` (default `15`), `qna.notify.daily-window-hours` (default `24`), `qna.notify.lookback-hours` (default `6`).
  - [x] `flushPending()`:
    1. `cutoff = now - lookbackHours`; load candidate window ids (B2 query).
    2. Intersect with **OPEN** windows only (AC8). For each: `sessionId = window.getSessionId()`; load `Session` (slug/title) + `Event` (eventCode/title/number) for the deep link.
    3. Recipients = distinct usernames from `findBySessionIdAndSpeakerRole(sessionId, PRIMARY_SPEAKER | CO_SPEAKER | MODERATOR)` — **not** `PANELIST` (AC9). (Use `findBySessionIdInAndSpeakerRole`/three calls.)
    4. Per recipient:
       - Read `qnaNotificationFrequency` via `UserApiClient` (default `live`). `off` → skip (AC7). `live` → `throttle = liveWindowMinutes`; `daily` → `throttle = dailyWindowHours`.
       - Load-or-create `session_qna_notification` row. **Throttle:** if `last_notified_at != null && now - last_notified_at < throttle` → skip.
       - **Count new (AC5/6/9):** `since = notified_through`; count top-level, non-removed posts with `createdAt > since` AND `postedByUsername != recipient`. If `0` → skip.
       - Resolve email (`getEmailByUsername`) — empty → skip. Resolve locale (`getPreferredLanguage` → DE/EN, clone `SlidesOnlineEmailService` normalize).
       - Render + send (Task B4). On success: `last_notified_at = now`, `notified_through = max(createdAt of counted posts)`; save.
       - **Per-recipient try/catch** — one SES error must not abort the loop or the other recipients (mirror `SlidesOnlineEmailService`/`RegistrationCleanupService`).
- [x] **Task B4 — Email templates (DE + EN only)** (AC: 4, 10)
  - [x] `qna-new-questions-de.html` + `qna-new-questions-en.html` under `services/event-management-service/src/main/resources/email-templates/`. Clone `slides-online-{de,en}.html` (hero + body + CTA + footer, table layout, Plus Jakarta Sans, HTML-entity umlauts). First line `<!-- subject: ... {{count}} ... -->`. Singular/plural via Mustache conditional (`{{#isMultiple}}…{{/isMultiple}}`).
  - [x] No template migration — `EmailTemplateSeedService` auto-seeds `email-templates/*.html` on startup, idempotent (`EmailTemplateSeedService.java:42`). Verify `deriveCategory("qna-new-questions")` lands sensibly.
  - [x] Render via the canonical path (copy `SlidesOnlineEmailService.renderMail`): `findByKeyAndLocale("qna-new-questions", locale)` → `replaceVariables` → `mergeWithLayout(content, "batbern-default", locale)` → `replaceVariables` → `emailService.sendHtmlEmail(to, List.of(), subject, html)`.
  - [x] Vars: `{{count}}`, `{{isMultiple}}`, `{{eventTitle}}`, `{{eventNumber}}`, `{{sessionTitle}}`, `{{qnaLink}}`, `{{recipientName}}`, plus layout vars `{{logoUrl}}`, `{{eventUrl}}`, `{{currentYear}}`. `qnaLink = baseUrl + "/events/" + eventCode` (public event page hosts `SessionQnaThread`; append a session anchor if one exists in `web-frontend/src/components/public/Event/`, else the event page is the correct fallback). `baseUrl` = the `@Value` frontend base URL used by `SlidesOnlineEmailService`.
- [x] **Task B5 — EMS UserPreferences DTO** (AC: 3, 10)
  - [x] Add `qnaNotificationFrequency` (String, default `"live"`) to `ch.batbern.events.notification.UserPreferences` and the `UserApiClientImpl` deserialization of `GET /api/v1/users/{username}/preferences` (`UserApiClientImpl.java:240-297`). Tolerate the field being absent (older payloads) → default `live`.
- [x] **Task B6 — Tests (TDD red→green→refactor)** (AC: 4–12)
  - [x] `QnaNotificationServiceIntegrationTest extends AbstractIntegrationTest` (Testcontainers PostgreSQL, never H2; `@Transactional`).
  - [x] **ShedLock test-leak guard (AC11):** class-scoped `@MockitoBean private LockProvider lockProvider;` + `@BeforeEach` `when(lockProvider.lock(any())).thenReturn(Optional.of(mock(SimpleLock.class)));` — copy from `SessionQnaIntegrationTest.java:90-91,100`. NOT a component-scanned `@TestConfiguration @Primary` bean.
  - [x] Mock `UserApiClient` (email, language, **qnaNotificationFrequency**). `@MockitoBean EmailService` (or spy) to assert recipient/subject (real send is a no-op in `test` profile, `sesClient == null`).
  - [x] Cases: AC4 (one question → speaker+co-speaker+moderator notified); AC5 LIVE (5 questions in 15 min → 1 email saying "5"; re-flush inside 15 min → none; flush after 15 min with new questions → one more counting only new, water-mark respected); AC6 DAILY (≤1 per 24h); AC7 OFF (skipped); AC8 freeze (FROZEN window → no email even with pending questions); AC9 (reply no-notify; self-post by speaker doesn't notify speaker but notifies moderator; PANELIST excluded); AC10 (no email → skip; `fr` → EN render); AC11 (two flushes don't double-send).
  - [x] Naming `should_<behavior>_when_<condition>`.
- [x] **Task B7 — Bruno / staging safety** (AC: 12)
  - [x] Prefer NOT adding a Bruno test that could trigger a real digest. If added: post to a session whose recipients are reserved-domain/test users (so `EmailService.assertSendable` blocks) OR a session with no assigned speaker/moderator (no recipients), + cleanup. Grep Bruno output for `Skipping invalid file`; prose in `docs {}` only.
- [x] **Task B8 — Doc-drift** — consult `.github/doc-drift-mappings.yml` for docs tied to scheduler/preferences paths; update in the same commit or add `[no-doc]`.

## Dev Notes

### Chosen approach (and why)

**One scheduled flush every 5 min; per-recipient throttle derived from their `qnaNotificationFrequency`; candidate windows filtered to `status = OPEN`.**

- *Single flush, two cadences:* `live` → 15-min throttle, `daily` → 24h throttle, `off` → skip. There is **no separate daily cron** — "once a day" is realized as a ≤24h per-recipient throttle on the same flush. This is simpler and correct given **freeze-wins** (AC8): since Q&A windows freeze ~`closesAt` and a frozen window never produces a digest, the DAILY cadence is effectively a guard for the (uncommon) long-lived open window; a fixed evening-roll-up job would add machinery for almost no benefit. (Resolved Decision #1.)
- *Flush, not inline send:* satisfies digest windowing, keeps `SessionQnaService.addPost` untouched (no regression on the hot path), resolves recipients + their preference at send time (a late co-speaker/moderator assignment is honoured).
- *State table:* per-`(window, recipient)` `last_notified_at` (throttle) + `notified_through` (so "N **new**" is accurate, not cumulative). Bounded lookback scan (`lookback-hours`, default 6h) keeps the candidate query cheap.

### Source tree — files this story touches

**NEW (Part A — CUMS/frontend):**
- `services/company-user-management-service/src/main/resources/db/migration/V20__add_qna_notification_frequency_preference.sql`

**UPDATE (Part A):**
- `services/company-user-management-service/.../domain/UserPreferences.java` (add String field)
- `services/company-user-management-service/.../service/UserService.java` (`getCurrentUserPreferences` ~`:1203`, `updateCurrentUserPreferences` `:1229-1262`)
- `docs/api/users-api.openapi.yml` (`UserPreferences` schema ~`:2274-2305`) → regen backend + frontend types
- `web-frontend/src/components/user/UserSettingsTab/UserSettingsTab.tsx` (new selector, model on `:668-695`)
- `web-frontend/src/services/api/userAccountApi.ts` (already PATCHes preferences — likely no change beyond the new field flowing through generated types)
- `web-frontend/public/locales/{10 locales}/userManagement.json` (`settings.notifications` keys)

**NEW (Part B — EMS):**
- `services/event-management-service/src/main/resources/db/migration/V117__create_qna_notification_state.sql`
- `.../domain/SessionQnaNotification.java`, `.../repository/SessionQnaNotificationRepository.java`
- `.../service/QnaNotificationService.java`
- `.../resources/email-templates/qna-new-questions-{de,en}.html`
- `.../test/java/.../service/QnaNotificationServiceIntegrationTest.java`

**UPDATE (Part B):**
- `.../repository/SessionQnaPostRepository.java` (candidate-window + count queries — add only)
- `.../repository/SessionQnaWindowRepository.java` (OPEN-window finder — add only)
- `.../client/UserApiClient(+Impl).java` + `.../notification/UserPreferences.java` (read `qnaNotificationFrequency`)
- `.../resources/application.yml` (`qna.notify.*` + `qna.scheduled.notify.cron`)

**DO NOT TOUCH (contracts you build on):** `SessionQnaService.addPost` (`:150-184`, persist `:176`; top-level = `parentPostId == null`; no hook there by design), `SessionQnaController` POST (`:54-65`).

### Key facts pinned during analysis (cite when implementing)

- **Q&A window status:** `QnaWindowStatus { OPEN, FROZEN }` (UPPER_CASE Java/JSON, lowercase DB via `QnaWindowStatusConverter`). `SessionQnaWindow`: `status` (`:52-53`), `sessionId` (`:46`, in-service UUID FK), `closesAt` (`:59`), default OPEN (`:77-78`). Freeze set by `SessionQnaScheduledService.freezeExpiredWindows()` or organizer early-close. Repo `findByStatusAndClosesAtBefore` (`SessionQnaWindowRepository.java:27`), `findBySessionId` (`:19`).
- **Posts:** `SessionQnaPost.parentPostId` null ⇒ top-level (`:45-47`); author `postedByUsername` (`:50`); `windowId` (`:43`); `removedAt` soft-delete (`:56`, visible ⇒ null); `createdAt` (`:59`, `@PrePersist`).
- **Recipients:** `SessionUserRepository.findBySessionIdAndSpeakerRole(UUID, SessionUser.SpeakerRole)` (`:51-52`); batch `findBySessionIdInAndSpeakerRole` (`:62-63`). Enum `SpeakerRole { PRIMARY_SPEAKER, CO_SPEAKER, MODERATOR, PANELIST }` (`SessionUser.java:178-183`); username `:67-68`. Use the first three; exclude PANELIST.
- **Email send:** `EmailService.sendHtmlEmail(to, cc, subject, html)` async (`shared-kernel/.../EmailService.java:103-106`); `replaceVariables` Mustache + conditionals (`:443-478`); `test` profile `sesClient == null` ⇒ no send (AC12); `assertSendable` throws on reserved domains (`:507-513`).
- **Template seeding:** `EmailTemplateSeedService` `@PostConstruct` globs `classpath*:email-templates/*.html`, parses `{key}-{de|en}.html`, reads `<!-- subject: -->`, idempotent insert (`:42`).
- **CUMS preferences:** `UserPreferences` `@Embeddable`, `pref_`-prefixed columns on `user_profiles`, **String-typed** enums (no converter). CUMS HEAD migration `V19` → next `V20`. OpenAPI `UserPreferences` schema ~`users-api.openapi.yml:2274-2305`; sibling `notificationFrequency` enum `immediate|daily_digest|weekly_digest` (lowercase tokens — match this). Read/write: `UserService.getCurrentUserPreferences`/`updateCurrentUserPreferences`. Regen: `openApiGenerateUsers` + `generate:api-types:users`.
- **EMS consumer:** `UserApiClientImpl` calls `GET /api/v1/users/{username}/preferences` (`:240-297`), deserializes `ch.batbern.events.notification.UserPreferences` (String fields), Caffeine 15-min (`CacheConfig.java`). Add the new field here, tolerate absent → `live`.
- **Frontend:** edit at `UserSettingsTab.tsx` (radio group `:668-695`), PATCH via `userAccountApi.updateUserPreferences` (`:149-175`). i18n namespace `settings.notifications` in `userManagement.json` across 10 locales.
- **Clone target for the whole render+send+locale+failure-isolation flow:** `SlidesOnlineEmailService` (Story 7.3).

### ShedLock (AC11) — the test-flakiness trap

`@SchedulerLock(lockAtLeastFor=...)` writes a **committed** `shedlock` row that survives test rollback → order-dependent scheduled-service tests. Fix = class-scoped `@MockitoBean LockProvider` returning an always-granted no-op lock (NOT a component-scanned `@TestConfiguration @Primary`, which leaks → `NoUniqueBeanDefinitionException`). Pattern: `SessionQnaIntegrationTest.java:90-91,100`. Prod wiring `ShedLockConfig` (`JdbcTemplateLockProvider…usingDbTime()`, `:48-56`); table `V31__Add_shedlock_table.sql`.

### Testing standards summary

- TDD (red→green→refactor). Integration tests extend `AbstractIntegrationTest` (singleton Testcontainers PG, `.withReuse(true)`), `@Transactional`. Never H2/`@DataJpaTest`.
- Coverage: business logic ≥90%, APIs ≥80%. Every AC ≥1 test; AC5/6/8/9 need multiple.
- Run from repo root; pipe through `tee /tmp/<name>.log` then grep. Single test: `./gradlew :services:event-management-service:test --tests QnaNotificationServiceIntegrationTest`. CUMS: `./gradlew :services:company-user-management-service:test`.
- Frontend test asserts namespace-stripped i18n keys, not locale strings.

### Project Structure Notes

- Two services + frontend, deliberately phased: **Part A** (preference, additive everywhere, default `live`, no behaviour beyond a new settings control) ships and verifies first; **Part B** (the engine that reads it) ships after. Each is independently prod-deployable.
- ADR-003: `recipient_username VARCHAR(100)` meaningful ID, no FK/UUID across services; only same-service FK is `window_id → session_qna_window(id)`.
- ADR-004: email/locale/preference enriched from CUMS at read time (cached), never persisted on the notification row.
- Localization split (CLAUDE.md): backend email = DE + EN only; frontend UI keys = all 10 locales.

### References

- [Source: docs/prd/epic-15-post-event-2-hardening.md#Story 15.7]
- [Source: services/event-management-service/.../service/SessionQnaService.java#addPost L150-184]
- [Source: services/event-management-service/.../domain/SessionQnaPost.java#L40-70]
- [Source: services/event-management-service/.../domain/SessionQnaWindow.java#L46-78; QnaWindowStatus.java; SessionQnaWindowRepository.java#L19-27]
- [Source: services/event-management-service/.../repository/SessionUserRepository.java#L51-63; domain/SessionUser.java#L178-183]
- [Source: services/event-management-service/.../service/SessionQnaScheduledService.java#L34-36 (@Scheduled+@SchedulerLock); config/ShedLockConfig.java#L48-56]
- [Source: services/event-management-service/.../service/SlidesOnlineEmailService.java (render/send/locale clone); service/EmailTemplateSeedService.java#L42]
- [Source: shared-kernel/.../service/EmailService.java#L103-106, L443-478, L507-513]
- [Source: services/event-management-service/.../client/impl/UserApiClientImpl.java#L240-297; notification/UserPreferences.java; config/CacheConfig.java]
- [Source: services/company-user-management-service/.../domain/UserPreferences.java; service/UserService.java#L1203,1229-1262; controller/UserPreferencesController.java]
- [Source: docs/api/users-api.openapi.yml#UserPreferences (~L2274-2305), GET/PUT /users/me/preferences (~L790,816)]
- [Source: web-frontend/src/components/user/UserSettingsTab/UserSettingsTab.tsx#L668-695; services/api/userAccountApi.ts#L149-175; public/locales/*/userManagement.json#settings.notifications]
- [Source: test SessionQnaIntegrationTest.java#L90-91,100 — @MockitoBean LockProvider]
- [Source: _bmad-output/project-context.md — ADR-003/004, DE/EN email rule, 10-locale UI rule, ShedLock test pattern, never-edit-applied-migration, Bruno docs{} rule]

## Resolved Decisions

1. **Per-user three-way frequency preference, single flush.** The opt-out is a dedicated `qnaNotificationFrequency` preference (`live` / `daily` / `off`), not the generic email flag. **Why:** Nissim wants per-person control specific to Q&A. Realized as one 5-min flush with a per-recipient throttle (`live` = 15 min, `daily` = 24 h, `off` = skip) rather than two cron jobs — simpler, and sufficient given freeze-wins (below).
2. **Default = `live`.** New and existing recipients who never change the setting get live digests during their sessions. **Why:** matches the original spec intent that speakers/moderators are notified; users can dial down to `daily`/`off`.
3. **Recipients = primary speaker + co-speakers + moderator (not panelists),** each honouring their own preference. **Why:** co-speakers co-present and field questions; panelists are incidental.
4. **Freeze wins everywhere.** A FROZEN Q&A window never generates a digest, for LIVE or DAILY, even with questions pending. **Why:** Nissim's explicit choice. Consequence (accepted): because windows freeze ~1 h after a session, the DAILY cadence will rarely fire for typical events — it is a guard for long-lived open windows, not a daily event recap.

## Open Questions

(none outstanding — the four planning questions were resolved above. If, after seeing it live, the DAILY cadence feels pointless given freeze-wins, the cleanest follow-up would be to drop `daily` to a two-way `live`/`off` toggle — a small preference-enum change, noted here so it isn't rediscovered later.)

## Dev Agent Record

### Agent Model Used

claude-opus-4-8[1m] (Amelia / dev-story)

### Debug Log References

- `/tmp/cums-pref-test2.log` — UserPreferencesControllerIntegrationTest 11/11 pass (1 pre-existing @Disabled skipped).
- `/tmp/cums-full.log` — full CUMS suite BUILD SUCCESSFUL, 0 failures.
- `/tmp/fe-settings-test.log` — UserSettingsTab 15/15; `/tmp/fe-typecheck.log` — tsc clean.

### Completion Notes List

**Story COMPLETE — Part A + Part B implemented, all tests green.** Part A committed as `1d82829f`. Part B follows (separate commit).

**Part B (EMS digest engine):** single 5-min `@SchedulerLock`'d `QnaNotificationService.flushPending()` scans OPEN windows; per recipient (primary speaker + co-speakers + moderator, via `findBySessionId` + role filter) derives a throttle from `qnaNotificationFrequency` (`live`=15min, `daily`=24h, `off`=skip), counts new top-level non-self posts since the `(window,recipient)` water mark, and sends one DE/EN digest. 11 integration tests cover AC4–AC10 (incl. freeze-wins, self-post/reply/panelist exclusions, watermark, non-DE/EN→EN, missing-email skip). Full EMS suite green (0 failures). ShedLock test-leak avoided via class-scoped `@MockitoBean LockProvider`; the flush cron is disabled in the `test` profile (`qna.scheduled.notify.cron=-`) so it can't fire mid-suite and leak committed rows. No Bruno/E2E posts Q&A questions, so nothing test-driven can trigger a real send on staging (AC12).

**Part A (CUMS preference + frontend) — COMPLETE, all tests green.**

Two footguns hit and fixed during GREEN (both captured as Change Log + task notes):
1. **YAML boolean `off`** — `enum: [live, daily, off]` made the generator emit `QnaNotificationFrequencyEnum.FALSE("false")` (YAML 1.1 reads `off` as boolean false). Fixed by quoting: `["live","daily","off"]` → `OFF("off")`.
2. **NOT NULL vs null embeddable** — original migration used `NOT NULL`, but the service legitimately saves a null `UserPreferences` embeddable (Hibernate writes NULLs to every `pref_` column) → `DataIntegrityViolation`/500. Sibling `pref_*` columns are nullable; made the new column nullable too (DEFAULT + CHECK; CHECK passes on NULL). V20 was edited freely — it is brand-new on this branch, never applied to any shared env.

PUT `/users/me/preferences` is full-replace (not PATCH): an omitted `qnaNotificationFrequency` deserializes to the DTO default `live`, exactly like every sibling field. The shipped frontend always sends the field; the only "omitted" case is a stale browser tab, which self-corrects on the user's next save. A test documents this (`should_defaultQnaNotificationFrequencyToLive_when_omittedFromFullReplaceUpdate`).

### File List

**Part A — backend (CUMS):**
- `services/company-user-management-service/src/main/resources/db/migration/V20__add_qna_notification_frequency_preference.sql` (NEW)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/domain/UserPreferences.java` (entity field)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java` (read+write mapping)
- `docs/api/users-api.openapi.yml` (UserPreferences schema)
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/controller/UserPreferencesControllerIntegrationTest.java` (4 new tests)

**Part A — frontend:**
- `web-frontend/src/types/generated/user-api.types.ts` (regenerated)
- `web-frontend/src/types/userAccount.types.ts` (local UserPreferences type)
- `web-frontend/src/services/api/userAccountApi.ts` (read/write mappers + `mapQnaNotificationFrequency`)
- `web-frontend/src/components/user/UserSettingsTab/UserSettingsTab.tsx` (selector + form default)
- `web-frontend/src/components/user/UserSettingsTab/UserSettingsTab.test.tsx` (2 new tests + shared prefs mock)
- `web-frontend/public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/userManagement.json` (5 keys × 10 locales)

**Part B — backend (EMS):**
- `services/event-management-service/src/main/resources/db/migration/V117__create_qna_notification_state.sql` (NEW)
- `services/event-management-service/src/main/java/ch/batbern/events/domain/SessionQnaNotification.java` (NEW)
- `services/event-management-service/src/main/java/ch/batbern/events/domain/SessionQnaNotificationId.java` (NEW)
- `services/event-management-service/src/main/java/ch/batbern/events/repository/SessionQnaNotificationRepository.java` (NEW)
- `services/event-management-service/src/main/java/ch/batbern/events/service/QnaNotificationService.java` (NEW)
- `services/event-management-service/src/main/resources/email-templates/qna-new-questions-{de,en}.html` (NEW)
- `services/event-management-service/src/main/java/ch/batbern/events/repository/SessionQnaWindowRepository.java` (+`findByStatus`)
- `services/event-management-service/src/main/java/ch/batbern/events/repository/SessionQnaPostRepository.java` (+`findNewTopLevelQuestionsForRecipient`)
- `services/event-management-service/src/main/java/ch/batbern/events/client/UserApiClient.java` + `client/impl/UserApiClientImpl.java` (+`getQnaNotificationFrequency`)
- `services/event-management-service/src/test/java/ch/batbern/events/service/QnaNotificationServiceIntegrationTest.java` (NEW, 11 tests)
- `services/event-management-service/src/test/resources/application-test.properties` (disable flush cron in tests)
- `docs/architecture/06d-notification-system.md` (Q&A digest job + preference field — doc-drift)

## Change Log

| Date | Change |
|------|--------|
| 2026-06-20 | Story created (ready-for-dev). |
| 2026-06-20 | Scope expanded to per-user `qnaNotificationFrequency` preference (live/daily/off, default live); freeze-wins; recipients = speaker+co-speakers+moderator. Resolved Decisions recorded. |
| 2026-06-20 | **Part A implemented** (CUMS preference + OpenAPI + frontend selector + 10-locale i18n). Fixed YAML-`off`-as-boolean enum-gen bug (quoted values) and NOT-NULL-vs-null-embeddable migration bug (nullable column). All CUMS + frontend tests green. Committed `1d82829f`. |
| 2026-06-20 | **Part B implemented** (EMS digest engine: V117 state table, `QnaNotificationService` 5-min ShedLock flush with per-recipient cadence throttle, DE/EN templates, `UserApiClient.getQnaNotificationFrequency`). 11 new integration tests (AC4–AC10); full EMS suite green (0 failures). Flush cron disabled in `test` profile. Doc-drift: `06d-notification-system.md` updated. Story → review. |
