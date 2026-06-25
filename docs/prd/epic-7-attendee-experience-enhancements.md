---
stepsCompleted: [1, 2, 3]
inputDocuments:
  - _bmad-output/brainstorming/brainstorming-session-2026-06-06.md
  - docs/prd-enhanced.md
  - _bmad-output/project-context.md
---

# Epic 7: Attendee Experience — Right-Sized Contribution & Touchpoints

**Status:** 📋 **READY FOR STORY DETAILING** — rewritten 2026-06-09 (was: 📦 DEFERRED)
**Last Updated:** 2026-06-09

**Right-sized (2026-06-09):** Epic 7 was originally scoped as "Attendee Experience Enhancements" — personal engagement dashboards, content bookmarking, granular notification preferences, and a mobile PWA with offline capabilities. A 2026-02 brainstorming round had already flagged that vision as far too ambitious for this community (a recommendation engine for 9–18 sessions/year). The 2026-06-06 brainstorming session ("Attendee experience improvements for a small, low-frequency community") generated 25 ideas; 20 living ideas were published as GitHub issues (`#746–#765`, label `idea-vote`) and the organizer committee voted with 👍 reactions. **This epic is the right-sized replacement: the five ideas that won 3 or 4 votes, each composing onto existing machinery with near-zero recurring organizer effort.** Same precedent as the Epic 8 simplification (QuickSight/MS Graph → a table + an `.ics` file).

The old over-scoped scope (dashboards / bookmarks / PWA / recommendation engine) is captured in [What Was Cut from the Original Epic 7 Scope](#what-was-cut-from-the-original-epic-7-scope) and is not coming back.

---

## Epic Overview

**Epic Goal**: Give attendees a felt reason to log in — a *voice* and a *return channel* — by adding five organizer-voted features. The session thesis (ideas #10/#25): the login was pointless because it gated *consumption* (content is rightly free); instead, gate *participation* — and identity exists so a contribution can become a dialogue. Anonymous users lose nothing; logged-in users gain a voice and become reachable.

**Deliverable**: Five focused features, no new backend service:
1. Logged-in attendees suggest future topics into the existing topic pool.
2. Logged-in attendees self-nominate as speakers into the existing speaker workflow.
3. Registered attendees get a "slides are online" email when materials auto-publish.
4. Logged-in attendees thank the volunteer organizers in one click.
5. A time-boxed per-session Q&A after each event freezes into the archive forever.

**The emergent architecture (session's structural insight)** — a three-layer engine that adds *zero* recurring organizer work: **attendees contribute → speakers structure → agent curates / organizers approve**. Everything compounds on machinery that already exists.

**Architecture Context**:
- **No new service.** Features land in existing services:
  - 7.1 Topics From the Floor → `partner-coordination-service` (owns `topic_suggestions` / `topic_votes`, Story 2.7 / 8.2)
  - 7.2 "I Could Speak on That" → `event-management-service` (`speaker_pool`, 8-state `SpeakerWorkflowService` — the speaker workflow was unified into event-management per Epic 11 / ADR-009; `events` + `speaker_pool` share one DB, so the topic-set/published check is a local read)
  - 7.3 Slides-Online Mail → `event-management-service` (auto-publishing) + AWS SES
  - 7.4 Thank-the-Organizers → `event-management-service`
  - 7.5 The Apéro Continues → `event-management-service` → archive
- **Identity**: contributions are login-gated, riding on "Continue with Google" (Epic 12 / ADR-010, live). Default role `ATTENDEE` is sufficient for all contribution endpoints. **Exception:** Story 7.4 (Thank-the-Organizers) is intentionally anonymous-allowed with an abuse guard — see [Resolved Decisions](#resolved-decisions).
- **Speaker workflow integrity (ADR-009)**: `speaker_pool.status` is written **only** via `SpeakerWorkflowService.transition`. Self-nominations enter at `IDENTIFIED` — never at `READY` (the provisioning gate).
- **Email**: DE + EN templates only (email-localization rule); reuse the SES pattern from Story 6.5.
- **Cross-service identity (ADR-003)**: meaningful IDs (`eventCode`, `username`), no UUID leakage, no cross-service FK constraints.

**Prerequisites (all live):**
- ✅ Topic pool + voting (`topic_suggestions`, `topic_votes`) — Story 2.7 / Epic 8.2
- ✅ Unified speaker 8-state workflow + `promote` endpoint — Epic 11 / ADR-009
- ✅ Auto-publishing (speakers @ 30d, agenda @ 14d, materials) — BAT-16 / Epic 5
- ✅ SES email rendering with DE/EN locale chain — Story 6.5
- ✅ Google SSO login — Epic 12 / ADR-010
- ✅ Public archive with per-event/session pages — Epic 4

---

## Requirements Inventory

### Functional Requirements

- **FR1**: A logged-in attendee can suggest a future event topic (title + short rationale).
- **FR2**: Attendee suggestions flow into the existing `topic_suggestions` pool, tagged `source = community` to distinguish them from partner-sourced topics.
- **FR3**: Organizers triage attendee topics in the existing topic-suggestion admin UI — no new admin surface.
- **FR4**: Topic suggestion is login-gated, giving organizers a return channel to the suggester.
- **FR5**: Once an event's topic is set and the event is published, a logged-in attendee can self-nominate as a speaker (session title + abstract).
- **FR6**: A self-nomination creates a `speaker_pool` entry at `IDENTIFIED` via `SpeakerWorkflowService.transition` — never at `READY`, and never auto-provisioning a Cognito user / SPEAKER role.
- **FR7**: Organizers triage self-nominations through the existing 8-state speaker workflow (promote path unchanged).
- **FR8**: A default organizer task ("Newsletter: Slides Are Online", due ~2 weeks after the event) is auto-created with the event's task set; the organizer manually sends a "slides are online" email to the event's active registrants from that task.
- **FR9**: The slides-online email reuses the existing newsletter send with a new DE+EN template, sent once per event, in the attendee's web-language preference (de* → German, en → English, else German fallback); recipients honour the global email opt-out.
- **FR10**: After an event, any attendee (anonymous allowed) can send a one-click thank-you, optionally with a short note, to the volunteer organizers — protected by an abuse guard (rate-limit + Turnstile on anonymous submissions).
- **FR11**: Thank-yous are surfaced as an aggregate counter / appreciation wall. Logged-in attendees are deduped to one thank-you per event; anonymous thank-yous are rate-limited (clap-style increment) to prevent inflation.
- **FR12**: For a time-boxed window (~2 weeks) after an event, logged-in attendees can post open questions per session and answer others' questions asynchronously.
- **FR13**: When the window closes, the Q&A freezes (read-only) and attaches permanently to that session in the archive.
- **FR14**: The post-event Q&A is login-gated and is explicitly the *digital afterglow* — it never digitizes or intrudes on the physical apéro.

### Non-Functional Requirements

- **NFR1 (no new service)**: All features compose onto existing services. No new backend service is created.
- **NFR2 (near-zero organizer effort)**: Each feature self-runs once built; the organizer role is review/approve at most. The binding constraint confirmed in the session is *volunteer organizer attention*.
- **NFR3 (low-friction identity)**: Login-gated contributions ride on Google SSO (Epic 12); `ATTENDEE` role suffices.
- **NFR4 (workflow integrity)**: `speaker_pool.status` written only via `SpeakerWorkflowService.transition` (ADR-009); meaningful cross-service IDs, no UUID leakage, no cross-service FK constraints (ADR-003).
- **NFR5 (localization)**: Email templates DE + EN only; all new frontend UI i18n keys populated in all 10 locales.
- **NFR6 (Sacred-Three guardrails, idea #18)**: (1) the physical apéro stays unstructured — no app intrusion into the in-person ritual; (2) topics stay lessons-learned-driven, never hype/vendor; (3) features that change the event's character are rejected by default.
- **NFR7 (cadence-match, idea #24)**: only event-triggered communications — no always-on feed that a 3×/year cadence would make look abandoned.

### Additional Requirements (Architecture)

- New columns/tables are created only by the story that needs them (no upfront schema). New Flyway migrations are always forward-only and higher-numbered; never edit an applied migration.
- Login-gated contribution endpoints must be permitted in **both** the api-gateway `SecurityConfig` and the owning service's `SecurityConfig` (all profile chains).
- Login-gated contribution surfaces render behind the lazy `<MuiLayout>` boundary, so MUI usage there is fine. Any attendee-facing element that appears on a **public** (pre-login) page must remain Tailwind-only (no-MUI-on-public-pages bundle boundary).
- Scheduled window-close (Story 7.5) uses the existing ShedLock-backed scheduler pattern; class-scoped `@MockBean LockProvider` in tests (committed lock rows survive rollback otherwise).

---

## Epic List

### Epic 7: Attendee Experience — Right-Sized Contribution & Touchpoints
Give attendees a voice and a return channel via five organizer-voted features that compose onto existing machinery with near-zero recurring organizer effort. Replaces the deferred dashboards/PWA/recommendation-engine scope.
**FRs covered:** FR1–FR14

### FR Coverage Map

| FR | Story | Summary |
|----|-------|---------|
| FR1–FR4 | 7.1 | Login-gated attendee topic suggestions → existing topic pool (`source = community`) |
| FR5–FR7 | 7.2 | Login-gated speaker self-nomination → `speaker_pool` at `IDENTIFIED` via `SpeakerWorkflowService.transition` |
| FR8–FR9 | 7.3 | Event-triggered slides-online email (DE+EN), once per event, on materials auto-publish |
| FR10–FR11 | 7.4 | One-click thank-you to organizers + aggregate appreciation counter |
| FR12–FR14 | 7.5 | ~2-week per-session post-event Q&A that freezes into the archive |

---

## Epic 7 Stories

### Story 7.1: Topics From the Floor

**Story file**: `_bmad-output/implementation-artifacts/7-1-topics-from-the-floor.md` (to be created via `bmad-create-story`)
**Status**: to-be-created
**Source idea**: #05 "Topics From the Floor" — 4 votes (GitHub #750)
**Service**: `partner-coordination-service`

**User Story:**
As a **logged-in attendee**, I want to suggest a future event topic, so that the 200 practitioners in the room become a sensing network for what BATbern should cover next — and so an organizer can ask me *why*.

**Scope (what it is):**
- A "Suggest a topic" form (title + short rationale) on a login-gated attendee surface.
- Suggestions write into the **existing** `topic_suggestions` table, tagged `source = community` (vs `partner`).
- Organizers triage attendee topics in the **existing** topic-suggestion admin UI; the `source` tag shows provenance.
- Login-gated (`ATTENDEE` role) — per thesis #10/#25, so organizers have a return channel.
- Reuses the partner topic-voting machinery (builds on idea #05; #13 attendee-voting is a later, separate idea — out of scope here).

**What's NOT in scope:**
- Attendee *voting* on topics (idea #13, 0 votes — not in this epic).
- A new admin surface or a separate community-topic moderation queue.
- Notifying an attendee when "their" topic becomes the next event (that is idea #13's loop-closer — deferred).

**Architecture:**
```
Logged-in attendee → "Suggest a topic" form (MuiLayout)
       │  POST /api/v1/topics/suggestions  (ATTENDEE)
       ▼
partner-coordination-service
  TopicSuggestionController → TopicSuggestionService
       │  insert topic_suggestions (source = 'community',
       │  suggested_by_username = <jwt username>)
       ▼
  Existing organizer topic-suggestion admin UI
  shows community + partner suggestions, source-tagged
```

**Acceptance Criteria:**

**Given** a logged-in attendee on the topic-suggestion surface
**When** they submit a valid topic (title + rationale)
**Then** a `topic_suggestions` row is created with `source = community` and `suggested_by_username` = their username
**And** the suggestion appears in the existing organizer topic-suggestion UI, visibly tagged as community-sourced.

**Given** an anonymous (not logged-in) visitor
**When** they attempt to POST a topic suggestion
**Then** the request is rejected with 401 (login-gated at both api-gateway and service `SecurityConfig`).

**Given** an organizer viewing the topic-suggestion admin UI
**When** community and partner suggestions coexist
**Then** each is distinguishable by `source`, and existing partner-suggestion behaviour is unchanged.

**Given** a submitted topic with an empty title or rationale exceeding the max length
**When** validation runs
**Then** the API returns 400 (via the explicit `MethodArgumentNotValidException` handler) and no row is created.

---

### Story 7.2: "I Could Speak on That"

**Story file**: `_bmad-output/implementation-artifacts/7-2-i-could-speak-on-that.md` (to be created via `bmad-create-story`)
**Status**: to-be-created
**Source idea**: #06 "I Could Speak on That" — 4 votes (GitHub #751)
**Service**: `event-management-service` (unified speaker workflow per Epic 11 / ADR-009 — NOT speaker-coordination-service)
**Depends on**: 7.1 (extends the same login-gated contribution surface) — buildable independently if 7.1's surface is stubbed.

**User Story:**
As a **logged-in attendee**, once the next event's topic is set and the event is published, I want to raise my hand with a session title and abstract, so that BATbern's speaker pipeline becomes pull-and-push instead of only organizer-sourced — without me needing to know an organizer.

**Self-nomination window opens** as soon as the event's topic is set and the event is published (the earliest public signal — maximizes the nomination window; organizers triage nominations against a still-forming program, which is acceptable). Window closes when the event starts.

**Quality bar (MVP):** self-nominations land in the organizer pool **raw** — no agent pre-screen. Decoupled from the abstract-quality agent; add agent scoring later only if volume warrants.

**Scope (what it is):**
- Once the event's topic is set and the event is published, a logged-in attendee can self-nominate: session title + abstract.
- Creates a `speaker_pool` entry at **`IDENTIFIED`** via `SpeakerWorkflowService.transition`, marked `source = self_nomination`.
- Organizers triage self-nominations through the **existing** 8-state workflow exactly like organizer-sourced candidates; promotion to `READY` (with Cognito provisioning + SPEAKER role + `session_users`) remains organizer-only via the existing `promote` endpoint.
- Self-nomination shares the contribution surface built in 7.1 ("suggest a topic" + "I could speak on that").

**What's NOT in scope:**
- Any auto-provisioning at nomination time (no Cognito user, no SPEAKER role, no `session_users` row).
- Reserving a slot or guaranteeing a self-nominee gets on stage (idea #21 "Young Voices on Stage", 0 votes — separate).
- A new state in the 8-state machine.

> ⚠️ **Design correction to the brainstorming text.** Idea #06 said "→ speaker_pool at READY." Per ADR-009, `READY` is the **provisioning gate** reachable only via `POST /events/{code}/speakers/{id}/promote`. A self-nominee must enter at `IDENTIFIED` and be promoted by an organizer like any other candidate. This is a deliberate, recorded deviation.

**Architecture:**
```
Logged-in attendee (event topic set + published)
       │  POST /api/v1/events/{eventCode}/speakers/self-nominate  (ATTENDEE)
       │     { sessionTitle, abstract }
       ▼
event-management-service
  SelfNominationController → create speaker_pool row (defaults to IDENTIFIED)
       │  speaker_pool row: status = identified, source = self_nomination,
       │  proposed_by_username = <jwt username>
       │  (subsequent status changes ONLY via SpeakerWorkflowService.transition)
       ▼
  Existing organizer speaker-pool / brainstorming UI
  organizer triages → (later) promote → READY  [unchanged path]
```

**Acceptance Criteria:**

**Given** an event whose topic is set and which is published, and a logged-in attendee
**When** they submit a self-nomination (title + abstract)
**Then** a `speaker_pool` entry is created at status `IDENTIFIED` via `SpeakerWorkflowService.transition`, tagged `source = self_nomination` with their username recorded
**And** no Cognito user, SPEAKER role, or `session_users` row is created
**And** the abstract is stored raw, without agent pre-screening.

**Given** a self-nominated `speaker_pool` entry
**When** an organizer views the speaker pool
**Then** the self-nomination appears alongside organizer-sourced candidates and can be promoted via the existing `promote` endpoint, with the standard provisioning happening only at the transition into `READY`.

**Given** an attempt to self-nominate for an event whose topic is not yet set or which is not published
**When** the request is processed
**Then** it is rejected (409/422) and no `speaker_pool` row is created.

**Given** an anonymous visitor
**When** they attempt to self-nominate
**Then** the request is rejected with 401.

---

### Story 7.3: "The Slides Are Online" Mail

**Story file**: `_bmad-output/implementation-artifacts/7-3-slides-online-mail.md` (to be created via `bmad-create-story`)
**Status**: to-be-created
**Source idea**: #07 "The Slides Are Online" Mail — 3 votes (GitHub #752)
**Service**: `event-management-service` + AWS SES

**User Story:**
As an **attendee who registered for an event**, I want an email the day the slides go online, so that I get the one post-event message I'll actually open — turning 3 website visits/year into 6 without asking more commitment of me.

**Scope (what it is):**
- A default organizer task ("Newsletter: Slides Are Online", due ~2 weeks after the event) is auto-created with the event's task set (existing `EventTaskService` template mechanism — zero engine change).
- From that task the organizer manually sends a "slides are online" email to the event's **active registrants** (`registered`/`confirmed`/`attended` — the mail fires post-event, so people who actually attended are included; `waitlist`/`cancelled` excluded), reusing the existing newsletter-send infra with a NEW DE+EN `slides-online` template.
- Per-recipient locale = web-language preference (de* → German, en → English, **else German fallback**); recipients honour the global email opt-out; double-send guarded.
- Event-anchored, never a recurring digest (cadence-match #24).

**What's NOT in scope:**
- An automated materials-publish trigger (none exists; resolved to task + manual send).
- Sending to the global newsletter-subscriber pool (recipients are this event's registrants).
- Per-session granular notifications.

**Architecture:**
```
Event task set auto-created → "Newsletter: Slides Are Online" task (due event +14d)
       │  (existing EventTaskService template seed — no engine change)
       ▼
Organizer opens task → POST /api/v1/events/{eventCode}/newsletter/send
       │  templateKey = slides-online, recipients = active registrants
       ▼
  NewsletterEmailService (reused: paged, throttled, audited, double-send guard)
       │  per recipient: skip opt-out; locale de*/en/German-fallback
       ▼
  AWS SES  → DE/EN "slides-online" template
```

**Acceptance Criteria:**

**Given** an event with the auto-created "Newsletter: Slides Are Online" task
**When** the organizer sends from that task
**Then** each active registrant (`registered`/`confirmed`/`attended`, not opted out) receives one "slides are online" email, in German if their language preference starts with `de`, English if `en`, else German fallback.

**Given** a slides-online send already completed for an event
**When** the organizer attempts to send again
**Then** the double-send guard prevents a second send.

**Given** a registrant who has globally opted out of email
**When** the send runs
**Then** that recipient is excluded.

**Given** the SES send fails transiently for one recipient
**When** the failure occurs
**Then** it is logged/marked failed without blocking the other recipients.

---

### Story 7.4: Thank-the-Organizers

**Story file**: `_bmad-output/implementation-artifacts/7-4-thank-the-organizers.md` (to be created via `bmad-create-story`)
**Status**: to-be-created
**Source idea**: #02 Thank-the-Organizers Button — 3 votes (GitHub #747)
**Service**: `event-management-service`

**User Story:**
As **any attendee** (logged in or not), I want to thank the volunteer organizers in one click after an event, so that the people who've run BATbern for 20 years — free, ad-free, on their own time — finally receive the gratitude attendees said they had no way to express.

**Gating (Resolved Decision):** anonymous-allowed — a thank-you is the one contribution where friction hurts most, so it is deliberately NOT login-gated. Protected by an abuse guard: rate-limit + Turnstile on anonymous submissions.

**Scope (what it is):**
- After an event (live/completed), any attendee can send a one-click thank-you, with an optional short note.
- Logged-in attendees are deduped to one thank-you per event; anonymous thank-yous increment a counter (clap-style), rate-limited per session/IP + Turnstile to prevent inflation.
- Surfaced as a **public aggregate counter**; free-text notes are **organizer-visible only** (no public note wall, no approval queue).
- Lives on the public event/archive page, Tailwind-only (no MUI). Near-zero build; no new service.

**What's NOT in scope:**
- Per-organizer targeting or rating.
- Public leaderboards / gamification.
- Identity verification of anonymous senders beyond the abuse guard.

**Architecture:**
```
Any attendee (event live/completed)
       │  POST /api/v1/events/{eventCode}/thanks   (public)
       │     { note? }  + Turnstile token (anonymous) ; JWT if logged in
       ▼
event-management-service
  OrganizerThanksController → OrganizerThanksService
       │  if logged in: upsert (unique: eventCode + username)
       │  if anonymous: validate Turnstile + rate-limit → increment counter
       ▼
  Aggregate counter + appreciation wall
  (organizer view; public count optional)
```

**Acceptance Criteria:**

**Given** a logged-in attendee and an event that is live or completed
**When** they tap "thank the organizers" (optionally with a note)
**Then** a thank-you is recorded for that event and the aggregate counter increments by one.

**Given** a logged-in attendee who has already thanked the organizers for an event
**When** they submit again
**Then** the count does not double-increment (one per attendee per event); an existing note may be updated.

**Given** an anonymous visitor with a valid Turnstile token within the rate limit
**When** they tap "thank the organizers"
**Then** the aggregate counter increments (clap-style); a missing/invalid Turnstile token or exceeding the rate limit is rejected without incrementing.

**Given** an organizer viewing the appreciation surface
**When** thank-yous exist for an event
**Then** they see the aggregate count and any submitted notes.

---

### Story 7.5: The Apéro Continues

**Story file**: `_bmad-output/implementation-artifacts/7-5-the-apero-continues.md` (to be created via `bmad-create-story`)
**Status**: to-be-created
**Source idea**: #01 The Apéro Continues — 3 votes (GitHub #746)
**Service**: `event-management-service` → archive

**User Story:**
As a **logged-in attendee**, I want a time-boxed Q&A per session after an event, so that the open questions that currently have nowhere to live get answered — and become part of the session's permanent record in the archive.

**Window (Resolved Decision):** opens automatically when the event completes, default **14 days**; organizers may extend or close it early (optional override). Close is automatic via the ShedLock-backed scheduler.

**Moderation floor (Resolved Decision):** ships on **login-accountability + organizer takedown** — every post is attributable to a logged-in user, and organizers can remove a post. Agent-assisted curation (idea #23) is a later layer, not a launch dependency.

**Scope (what it is):**
- A per-session Q&A that opens automatically when an event completes and stays open 14 days (organizer-overridable).
- During the window, logged-in attendees post questions and answer others' questions asynchronously (attendees + speakers).
- Organizers can remove any post (takedown) and extend/close the window early.
- When the window closes (scheduled), the Q&A freezes to read-only and attaches permanently to that session in the public archive.
- Window open/close is automatic (ShedLock-backed scheduler) — near-zero organizer effort.
- It is the **digital afterglow**, explicitly distinct from the physical apéro (Sacred-Three #18).

**What's NOT in scope:**
- An always-on forum (would die at this scale — the time-box is the point).
- Any feature touching or digitizing the in-person apéro.
- Agent-assisted moderation/summarisation (idea #23, 2 votes — a later automation layer; MVP relies on login accountability + organizer takedown).

**Architecture:**
```
Event completes (event-management EVENT_COMPLETED)
       │  → open Q&A window for each session (opens_at, closes_at = +14d default)
       ▼
event-management-service
  SessionQnaController → SessionQnaService   (window OPEN)
       │  logged-in attendees post questions / answers
       │  organizers: takedown post, extend / close-early
       ▼
  ShedLock scheduler closes window at closes_at
       │  status → FROZEN (read-only)
       ▼
  Frozen Q&A rendered on the session's archive page (permanent)
```

**Acceptance Criteria:**

**Given** an event transitions to completed
**When** the completion is processed
**Then** a Q&A window opens for each of its sessions with a close time 14 days out (default).

**Given** an open Q&A window
**When** an organizer extends it, closes it early, or removes a post
**Then** the window close time / post visibility updates accordingly, and the change takes effect immediately.

**Given** an open Q&A window and a logged-in attendee
**When** they post a question or an answer
**Then** it is stored and visible to other logged-in attendees within the window.

**Given** an anonymous visitor
**When** they attempt to post a question or answer
**Then** the request is rejected with 401 (reading frozen Q&A in the archive remains public).

**Given** a Q&A window whose close time has passed
**When** the scheduled close job runs
**Then** the window freezes to read-only, no further posts are accepted, and the frozen thread is attached to the session's archive page permanently.

**Given** the scheduled close job and a test context
**When** the job runs under test
**Then** the `LockProvider` is mocked at class scope so committed lock rows do not leak across tests.

---

## What Was Cut from the Original Epic 7 Scope

The pre-2026-06 Epic 7 ("Attendee Experience Enhancements") was deferred precisely because it was over-scoped for a community of ~200 attendees, ~3 events/year, ~3 website touchpoints/year. Explicitly cut and **not** part of this epic:

- **Personal engagement dashboard** — no recurring engagement to display for a 3×/year audience.
- **Content bookmarking** — content is free and findable; bookmarking solves a problem nobody stated.
- **Granular notification preferences** — replaced by a single event-triggered email (7.3) that respects cadence-match.
- **Mobile PWA with offline capabilities** — heavy infrastructure for a thrice-yearly visit.
- **Recommendation engine** — the canonical over-scope (recommendations over 9–18 sessions/year).
- **Advanced personal settings page** (former Story 5.2 / 7.2) — beyond profile edit, no demand.

Also deliberately rejected in the 2026-06-06 session (not cut from old scope, but ruled out for this epic):
- **"Who's coming" opt-in attendee list** (idea #09) — killed by user decision.
- **Attendees as welcome-committee hosts** — regulars promote outward; they don't want assigned social duties.
- **Instagram/TikTok community channels** (cadence-match #24) — a 3×/year feed looks abandoned and damages the brand; reach the young via people (ideas #20/#21), not feeds.

---

## Success Metrics

- **Contribution Loop adoption (7.1 + 7.2):** ≥ N community topic suggestions and ≥ M self-nominations per event cycle (baseline established after the first event post-launch).
- **Self-nomination → stage conversion (7.2):** at least one self-nominated speaker promoted to `READY` and presenting within the first 3 event cycles.
- **Touchpoint lift (7.3):** measurable increase in post-event archive/session visits attributable to the slides-online email (open/click vs baseline newsletter).
- **Gratitude signal (7.4):** thank-you count per event > 0; qualitative organizer feedback that the gratitude gap is closed.
- **Archive enrichment (7.5):** ≥ 1 frozen Q&A thread attached per session per event; questions answered within the window.
- **Guardrail compliance:** zero features that alter the physical event character; organizer recurring effort unchanged (no new standing tasks introduced).

---

## Technical Compliance

- **No new service** (NFR1): all five stories extend existing services.
- **ADR-009 (speaker workflow):** 7.2 writes `speaker_pool.status` only via `SpeakerWorkflowService.transition`; enters at `IDENTIFIED`; provisioning stays organizer-only at `promote`.
- **ADR-003 (identifiers):** meaningful cross-service IDs (`eventCode`, `username`); no UUID leakage; no cross-service FK constraints.
- **Auth:** contribution endpoints (7.1, 7.2, 7.5) role-gated in **both** api-gateway and owning-service `SecurityConfig` (all profile chains); `ATTENDEE` role suffices; rides Google SSO. **Exception:** 7.4 thank-you is a public (token-credential-free) endpoint — `permitAll` in both gateway + service configs, protected by rate-limit + Turnstile on anonymous submissions (Turnstile already wired for public submit flows).
- **Email (Story 6.5 pattern):** 7.3 DE + EN templates only; `de*` → German else EN.
- **i18n:** all new frontend UI keys populated in all 10 locales; EN + DE first-class.
- **Bundle boundary:** contribution surfaces live behind `<MuiLayout>` (MUI OK); any pre-login public element stays Tailwind-only.
- **Flyway:** new columns/tables only where a story needs them; forward-only, higher-numbered migrations; never edit an applied migration; exclude `**/db/migration/**` from repo-wide sweeps.
- **Scheduler (7.5):** ShedLock pattern; class-scoped `@MockBean LockProvider` in tests.
- **GlobalExceptionHandler:** explicit `MethodArgumentNotValidException` handler so validation failures return 400 not 500.
- **Staging = production (test hygiene):** no Bruno/E2E test may trigger real outbound email (7.3) or leave contribution test data behind — always add cleanup.

---

## Guardrails (carried from the brainstorming session)

- **Sacred Three (idea #18):** (1) the physical apéro stays unstructured — 7.5 is the *digital* afterglow only; (2) topics stay lessons-learned-driven, never hype/vendor; (3) reject by default any feature that changes the event's character.
- **Cadence-Match (idea #24):** event-triggered communications only — no always-on feed.
- **Login = reachability (thesis #10/#25):** contributions are gated not to restrict, but to enable dialogue (organizers can reply; a suggestion can become a conversation, a conversation a speaker).

---

## Provenance — Organizer Vote (2026-06-06)

20 living ideas published as GitHub issues (`nissimbuchs/BATbern2`, label `idea-vote`), organizers voted with 👍. This epic implements the 3-and-4-vote winners:

| Idea | GitHub | 👍 | Story |
|------|--------|----|-------|
| #05 Topics From the Floor | #750 | 4 | 7.1 |
| #06 "I Could Speak on That" | #751 | 4 | 7.2 |
| #07 "The Slides Are Online" Mail | #752 | 3 | 7.3 |
| #02 Thank-the-Organizers | #747 | 3 | 7.4 |
| #01 The Apéro Continues | #746 | 3 | 7.5 |

Below-bar ideas (≤ 2 votes) are not in this epic and remain available for a future round: #04 Open Question at Registration (2), #16 One-Tap Forwarding Kit (2), #19 LinkedIn-Native (2), #20 Bring-Your-Junior (2), #23 Curation Agent (2), #15 Register-on-Topic (1), and the 0-vote ideas (#03, #08, #11, #12, #13, #14, #17, #21).

---

## Resolved Decisions

_Resolved with the PM 2026-06-09; folded into the stories above._

1. **7.4 Thank-the-Organizers — gating:** **Anonymous allowed.** A thank-you is the one contribution where friction hurts most, so it is deliberately not login-gated. Protected by an abuse guard (rate-limit + Turnstile on anonymous submissions). Logged-in attendees are deduped to one per event; anonymous submissions increment a rate-limited clap-style counter.

2. **7.4 + 7.5 service home:** **`event-management-service`.** Both features are strongly event-lifecycle-bound (keyed by `eventCode`, triggered by event live/completed), so they live with the event lifecycle rather than spreading attendee features into `attendee-experience-service`.

3. **7.2 self-nomination trigger:** **As soon as the event's topic is set and the event is published.** The earliest public signal, maximizing the nomination window; organizers triage nominations against a still-forming program, which is accepted. Window closes when the event starts.

4. **7.5 Q&A window + moderation floor:** **14-day default, organizer-overridable** (extend / close early). Ships on **login-accountability + organizer takedown**; agent-assisted curation (idea #23) is a later layer, not a launch dependency.

5. **7.2 self-nomination quality bar:** **Raw triage for the MVP** — no agent pre-screen. Keeps 7.2 decoupled from the abstract-quality agent; add agent scoring later only if nomination volume warrants.
