# Epic 15 — Post-Event #2 Hardening

**Author:** Winston (System Architect) · **Date:** 2026-06-20 · **Source:** `docs/specs/event-2-feedback-quick-spec.md` (Nissim's feedback from the 2nd live event)

## Epic goal

Close the remaining bugs and enhancements from the second live event that are too large for the quick-wins batch. The small/low-risk items (#1, #8b, #12, #13, #14, #15, #16, #17) shipped on branch `feature/event-2-feedback-quick-wins`; this epic covers the **architectural / multi-layer** items.

**Design principle (Nissim):** no backward compatibility (one prod, one user) — forward solutions only. But staging **is** production, so every story must be independently deployable without endangering prod (Flyway: never edit applied migrations; additive-then-cutover; beta-canary frontend-only changes where possible).

## Context & constraints (carry into every story)

- ADR-003 (meaningful IDs in public APIs, no UUIDs), ADR-004 (no duplicated user fields; enrich from CUMS at read time).
- No MUI on public pages (Tailwind-only bundle boundary).
- Email templates: DE + EN only; UI i18n keys: all 10 locales.
- TDD mandatory; integration tests use Testcontainers PostgreSQL (never H2).
- Reusable assets already built in the quick-wins batch: `LogoBadge` (white-chip logo wrapper), `EventEditionLabel`.

---

## Story 15.1 — Replace WebSockets with REST polling for live agenda control

**Item #2.** Decision: **drop WebSockets entirely** (web + watch); deliver live updates via REST polling.

**Why:** in-memory STOMP `SimpleBroker` + no ALB sticky sessions + multi-task Fargate ⇒ reconnects desync. Presenter already half-polls (`usePresentationData.ts`).

**Scope:**
- New `GET /api/v1/events/{eventCode}/live-timing` — current session, per-slot scheduled-vs-actual offsets, organizer-presence flag, monotonic `version` + `ETag`/`Last-Modified`.
- New `POST /api/v1/events/{eventCode}/live-timing/actions` `{type: END_SESSION|EXTEND_SESSION|DELAY_TO_PREVIOUS}` — moves the cascade logic out of `WatchWebSocketController` into a plain service; bumps `version`.
- Consumers poll adaptively (3–5s while LIVE, backoff when idle/hidden, `If-None-Match` → 304s): presenter, live-control, public countdown, **watch app** (separate App Store release train — reuse `OfflineActionQueue`).
- Teardown PR: remove `WebSocketConfig`, `/ws`, `/api/v1/watch/ws`, `JwtStompInterceptor`, frontend STOMP/SockJS client, watch `WebSocketClient/Service`, `sockjs`/`stompjs` deps — only after both consumers are off WS.

**Acceptance criteria:**
- AC1 — GIVEN a LIVE event, WHEN an organizer ends/extends a session, THEN `POST live-timing/actions` recomputes downstream offsets and bumps `version`; a subsequent `GET live-timing` reflects the change within one poll interval.
- AC2 — GIVEN no change since the last poll, WHEN a consumer polls with `If-None-Match`, THEN it receives 304 (no body).
- AC3 — GIVEN two Fargate tasks, WHEN a consumer's polls hit different tasks, THEN results are identical (no per-task in-memory state).
- AC4 — WHEN the WS endpoints are removed, THEN presenter + live-control + watch still reflect live changes via polling (no STOMP).

**Phasing:** P1 add endpoints + migrate web (verify on beta). P2 migrate watch (release train). P3 delete WS. Each independently shippable.

---

## Story 15.2 — Per-event editable event-type config + dynamic agenda ✅ DONE (2026-06-21)

**Item #3.** Decision: keep the **same knobs** as today's preset event types, but add an **"Edit event type"** action in slot assignment that edits a **per-event copy** (copy-on-edit) — never the shared template. Extend knobs to cover apéro-at-end and 2 afternoon breaks.

**As-built (deviations + key facts for 15.3):**
- Migrations **V121** (`event_agenda_config` + apéro columns on `event_types` template) + **V122** (`sessions_session_type_check` adds `'aperitif'`). Migration head is now **V122**.
- `AgendaConfig` interface unifies the template (`EventTypeConfiguration`) and the per-event override (`EventAgendaConfig`); `AgendaConfigResolver.resolve(event)` is the single fallback point routed through by **all three** `computeTimeline` consumers (`getTimetable` read, `StructuralSessionService` persistence, `SessionTimingService` auto-assign).
- Apéro is a NEW structural slot type (`TimetableSlot.Type.APERITIF`, `session_type='aperitif'`). **It was not enough to add it in one place** — every surface that enumerates structural session types needs it: backend `Session.STRUCTURAL_SESSION_TYPES` + `TimetableService`/`SessionTimingService`/`NewsletterEmailService`; frontend `DragDropSlotAssignment`, `SpeakersSessionsTable`, public `EventProgram`/`SessionCards`/`SpeakerGrid`/`EventCard`, `SessionEditModal`, live-control `AgendaList`, presenter `usePresentationSections`/`AgendaView`, and `scheduleTimeline`/`SchedulePreview`. (Adding a new structural type in 15.3 must touch the same set.)
- Apéro on/off is a **checkbox** (stored as `aperitif_slots` 0/1). Template defaults: afternoon + evening apéro **ON @ 90 min / position `end`**, full_day OFF (intentional behaviour change). Apéro is the **final segment, after moderation-end**.
- The "Edit event type" dialog **reuses** `EventTypeConfigurationForm` + live `SchedulePreview` (per-event mode via `eventCode` → `useAgendaConfig`); the admin template editor uses the same form (template mode). No bespoke dialog.
- **Assignment is still by `Session.startTime` ↔ computed `SPEAKER_SLOT.startTime` (HH:MM/Instant match) — there is NO slotKey yet. That is 15.3's job.** The dialog warns (does not block) when editing config on an event that already has assigned speakers, because re-timing can orphan them — exactly the desync 15.3 removes.
- Branch `feat/story-15-2-per-event-event-type-config` is pushed but **not yet merged**. 15.3 should branch after 15.2 merges (or off the 15.2 branch).

**Scope:**
- New `event_agenda_config` table (FK → event, one row/event) mirroring `EventTypeConfiguration` columns **plus** `aperitif_slots`, `aperitif_duration`, `aperitif_position` and count-driven break placement. Additive migration.
- Resolver: read `event_agenda_config` if present, else the shared template (zero behaviour change until first edit).
- Generalize `TimetableService.computeTimeline()` to fold an ordered segment list derived from the (per-event) knobs, computing each slot start from `event start + Σ preceding durations`.
- New GET/PUT agenda-config endpoints; "Edit event type" dialog writes only the per-event copy.

**Acceptance criteria:**
- AC1 — GIVEN an event with no override, WHEN the timetable renders, THEN it is identical to today (template-backed).
- AC2 — GIVEN an organizer edits the event type, WHEN they save, THEN a per-event `event_agenda_config` row is created/updated and the shared template is unchanged.
- AC3 — GIVEN an afternoon event configured with apéro-at-end + 2 breaks, WHEN the timetable computes, THEN slots reflect that structure with correct derived times.
- AC4 — schema-fitness test: editing one event's config never affects another event or the template.

**Depends on / pairs with:** 15.3.

---

## Story 15.3 — Stable slot assignment: dynamic build, insert/swap, mobile tap-to-place

**Items #7 + #6.**

**Scope:**
- Address slots by deterministic `slotKey` (segment-type + ordinal), not HH:MM string match; times derive from start + durations (15.2).
- Drop **between** slots → insert a speaker slot + reflow following times; drop **onto occupied** → swap assignments. One `assignSessionToSlot`/`reorderSlots` path with optimistic update + rollback.
- Extract the existing tap-to-select/tap-to-place interaction into a shared hook (`useTapToAssign`) and drive the **speaker pool** from it on touch viewports (today the pool uses HTML5 drag, dead on mobile — #6). Desktop keeps drag.

**Hand-off from 15.2 (read before starting):**
- Today the agenda grid (`DragDropSlotAssignment`) keys slots by `toTimeStr(startTime)` (HH:MM) and assignment is persisted as `Session.startTime`; `TimetableSlot` carries a 1-based `slotIndex` but no `slotKey`. 15.3 introduces the deterministic `slotKey` (segment-type + ordinal) and an `assignSessionToSlot`/`reorderSlots` path.
- `computeTimeline` (in `TimetableService`) is the single timeline algorithm; the local frontend mirror is `scheduleTimeline.buildTimeline`. Keep them in sync (both already do apéro + count-driven even-split breaks). Reflow-on-duration-change (AC4) is a `computeTimeline` property already — slots derive from `event start + Σ preceding durations`.
- Mobile tap-to-assign already exists partially in `DragDropSlotAssignment` (`handleTraySelect`/`handleSlotTap`, "armed slot", from 14.G.3); extract that into `useTapToAssign` rather than rebuilding.
- Once slotKey lands, **remove the 15.2 "assignment may move" warning** in the Edit-event-type dialog (`DragDropSlotAssignment` passes it via the form's `warning` prop) — reflow makes it obsolete.
- Migration head is **V122** → next is V123.

**Acceptance criteria:**
- AC1 — GIVEN a desktop organizer, WHEN they drop a speaker between two slots, THEN a slot is inserted and following slot times reflow.
- AC2 — WHEN they drop onto an occupied slot, THEN the two session assignments swap.
- AC3 — GIVEN a touch device, WHEN the organizer taps a speaker in the pool then taps a slot, THEN it is assigned (no drag needed).
- AC4 — shrinking an earlier slot's duration reflows all later slot times consistently.

**Depends on:** 15.2.

---

## Story 15.4 — Warn on company ↔ email-domain mismatch for registrants

**Item #4.**

**Scope:**
- Add `email_domains` (set) to Company; seed from the most common domains among each company's existing users (one-off backfill), organizer-editable. Additive migration.
- On registration, compare the email domain to the company's `email_domains`; persist a non-blocking `company_email_mismatch` flag (generic/personal emails are legitimate — warn, don't block).
- Surface a ⚠ badge + tooltip + quick "change company / confirm" on the organizer participants list.

**Acceptance criteria:**
- AC1 — GIVEN a company with `email_domains=[swisscom.com]`, WHEN `john@gmail.com` registers under it, THEN the registration succeeds and is flagged.
- AC2 — WHEN the domain matches, THEN no flag is set.
- AC3 — organizer participants list shows the ⚠ badge only for flagged rows.

---

## Story 15.5 — Speaker company logo on presenter + live control

**Item #5.**

**Scope:**
- Ensure session-speaker enrichment populates `companyLogoUrl` (resolve username → user → companyId → company logo via cached `UserApiClient`/companies path; no cross-service JPQL — ADR-004).
- Render the logo next to the speaker on `LiveControl/ActiveSessionCard` and the presenter speaker block, wrapped in the existing **`LogoBadge`** (white chip — reuses the #8b component; fixes legibility on dark surfaces). Request a thumbnail variant (`?w=`≤512) to avoid the resize-Lambda 503 on large sources.

**Acceptance criteria:**
- AC1 — GIVEN a speaker whose company has a logo, WHEN the presenter/live-control renders, THEN the logo shows inside a white chip.
- AC2 — GIVEN a transparent logo on the dark presenter background, THEN it stays legible.
- AC3 — GIVEN no logo, THEN a graceful fallback (no broken image).

---

## Story 15.6 — ~~Fix present-mode "Agenda" title overlap (Vanessa #790)~~ ✅ DONE

**Item #8a — already fixed** (Nissim confirmed 2026-06-20; resolved by commit `80195136`). No work in this epic; retained as a numbering placeholder.

---

## Story 15.7 — Notify speaker + moderator on new Q&A question

**Item #11.**

**Scope:**
- After a new **top-level** question is persisted (`SessionQnaService`), resolve the session's `PRIMARY_SPEAKER` + `MODERATOR` usernames (`SessionUserRepository`) → emails (cached `UserApiClient`).
- Throttle to a digest (≤1 "N new question(s)" email per recipient per session per ~10–15 min) to avoid a live-session flood; deep-link to the session Q&A. New DE+EN template `qna-new-questions-{de|en}.html`.
- Respect opt-out preferences; mind ShedLock test-flakiness pattern for any scheduled flush.

**Acceptance criteria:**
- AC1 — GIVEN a session with a primary speaker + moderator, WHEN an attendee posts a new question, THEN both receive a notification (digest-windowed).
- AC2 — WHEN a flurry of questions arrives within the window, THEN at most one email per recipient is sent for that window.
- AC3 — replies (non-top-level) and self-posts do not notify.
- AC4 — staging-safe: no real outbound mail in Bruno/E2E (test guard).

---

## Story 15.8 — LinkedIn post drafts (before / during / after)

**Item #10.** Decision: **draft-only** (direct publish is a later phase).

**Scope:**
- `POST /events/{code}/linkedin-drafts?phase=BEFORE|DURING|AFTER&sessionSlug=…` → DE + EN copy, reusing the existing AI-prompt machinery (organizer-editable prompt). BEFORE = teaser; DURING = per-session "happening now" + suggested image(s) (theme/speaker/event photos, thumbnail CDN variant); AFTER = recap + slides-online + appreciation.
- Output shape `{text, imageUrls[]}` so a later direct-publish phase needs no reshaping. Frontend "LinkedIn drafts" panel with copy-to-clipboard per language. Gated by `FEATURES_AI_ENABLED`. Covered by 15.9's checklist.

**Acceptance criteria:**
- AC1 — WHEN an organizer requests a BEFORE/DURING/AFTER draft, THEN DE + EN copy is returned with phase-appropriate content.
- AC2 — DURING drafts surface selectable images from existing event/session/speaker assets.
- AC3 — no auto-posting occurs (draft-only).

---

## Story 15.9 — OWASP LLM + Agentic security checklist in CI

**Item #9.**

**Scope:**
- `docs/security/owasp-llm-agentic-checklist.md` mapping relevant OWASP Top-10-for-LLM (LLM01 prompt injection, LLM02 insecure output handling, LLM06 sensitive-info disclosure, LLM08 excessive agency) + Agentic threats to the AI surface (OpenAI content-gen endpoints, organizer-editable prompts, the new 15.8 LinkedIn-draft generator).
- Targeted automated tests in CI: prompt-injection probes (can't exfiltrate system prompt / cross-org data), output-handling (AI output treated as untrusted — escaped/stored, never executed), no secrets/PII interpolated into prompts; organizer prompts can't reach system-level instructions.
- New advisory (non-blocking, ZAP-style) `ai-security` CI job; flip-to-blocking follow-up once green.

**Acceptance criteria:**
- AC1 — the checklist doc maps each AI endpoint to its threats + mitigation status.
- AC2 — prompt-injection + output-handling tests run in CI and pass.
- AC3 — a secret/PII-in-prompt scan fails the (advisory) job if violated.

---

## Sequencing recommendation

1. **15.5** (reuses `LogoBadge`) — next-live-event polish. (15.6/#790 already fixed.)
2. **15.7** (Q&A notify) — additive, self-contained.
3. **15.1** (WebSockets → polling) — de-risks the next live event's most fragile surface; phased.
4. **15.2 → 15.3** (agenda model + slots) — one coherent change, biggest effort.
5. **15.4** (company/email) — additive data-quality.
6. **15.8 + 15.9** (LinkedIn drafts + AI security) — paired (9 covers 8's surface).
