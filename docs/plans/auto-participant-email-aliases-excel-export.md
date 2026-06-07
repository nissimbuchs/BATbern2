# Plan: Auto-Participant Enrolment, Per-Event Email Aliases, Name-Badge XLSX Export

**Branch**: `feature/auto-participant-email-aliases-excel-export`
**Author**: Winston (System Architect) — drafted 2026-05-28
**Status**: Planning → Implementation

---

## 1. Motivation & Scope

Three independent enhancements bundled into one branch because they all touch the **event-detail / participants** path:

| # | Feature | Pain it removes |
|---|---|---|
| F1 | Auto-register an accepted speaker as a participant of that event (also for session main/co-speakers) | Organizers currently double-book speakers manually; missed registrations break badges and headcount |
| F2 | Per-event distribution aliases `batbernXX-speaker@batbern.ch` and `batbernXX-moderator@batbern.ch` | Organizers maintain ad-hoc mailing lists per event; reply-to-moderator is currently manual CC |
| F3 | XLSX export on event-detail → Participants tab for name-badge printing (firstName, lastName, company, role) | Manual CSV scraping for badge printers; role disambiguation is fiddly |

**Out of scope:**
- Changing the speaker-pool state machine itself (ADR-009 invariant — only `SpeakerWorkflowService.transition()` writes `speaker_pool.status`).
- Changing the SES inbound infrastructure topology (Story 10.26 already deploys MX + catch-all rule).
- Real bulk email sends in tests. Staging IS production; we test resolvers, not deliveries (see `feedback_no_real_comms_in_tests.md`).

---

## 2. Anchor in Existing Architecture

### 2.1 What we **lean on** (already shipped — no new infra needed)

| Capability | Lives in | We just need to extend it |
|---|---|---|
| SES catch-all rule `*@batbern.ch` → S3 → Forwarder Lambda | `infrastructure/lib/stacks/inbound-email-stack.ts:187` (`RouteForwardingCatchAll`) | Already matches `batbern{N}-anything@`; no CDK change required |
| Forwarder Lambda — fetches raw MIME, parses, resolves recipients, re-sends via SES `SendRawEmail` | `infrastructure/lambda/email-forwarder/index.ts` | Add new local-part regex in `address-resolver.ts` |
| `resolveRecipients(addr)` — already handles `batbern{N}@` → event registrants | `infrastructure/lambda/email-forwarder/address-resolver.ts:83` | Add `batbern{N}-speaker` and `batbern{N}-moderator` cases |
| Sender authorization (organizers-only for restricted addresses) | `infrastructure/lambda/email-forwarder/sender-auth.ts:44` | Add the two new local-parts to the rules (Story 10.32 cache reuse) |
| `EmailService` outbound (CC fan-out via `additionalEmails`) | `shared-kernel/.../service/EmailService.java` (Story 10.32) | Not needed — the Lambda re-sends raw |
| Apache POI XLSX writer pattern (`SXSSFWorkbook`, attendance export) | `services/partner-coordination-service/src/main/java/.../PartnerAttendanceExportService.java` | Copy the pattern into event-management-service |
| Session/SpeakerPool state hook for ACCEPTED transitions | `SpeakerWorkflowService.runAcceptedHook` (`services/event-management-service/.../SpeakerWorkflowService.java:526`) | Plug in `autoRegisterAcceptedSpeaker(...)` call (idempotent) |
| `SpeakerAcceptedEvent` already published on every accept | `SpeakerWorkflowService.publishStateSpecificEvents` (line 701) | Listener-based alternative (see §3.1 trade-off) |
| `RegistrationService.createRegistration()` — already idempotent on `(eventId, attendeeUsername)` | `services/event-management-service/.../RegistrationService.java:78` | Reuse — we never duplicate this logic |

### 2.2 What we **add** (small, surgical)

- `SpeakerAutoRegistrationService` (new bean in event-management-service) — listens to `SpeakerAcceptedEvent`, also called inline from co-speaker assignment paths.
- `ParticipantsExportService` (new bean) — XLSX generator.
- `GET /events/{eventCode}/participants/export.xlsx` (new endpoint).
- `GET /events/{eventCode}/distribution-list/{kind}` (new internal endpoint) — returns email lists for `speakers` and `moderator`. Called by the Lambda.
- Two regex cases in the Lambda's address-resolver + sender-auth.
- One Apache POI dependency added to `event-management-service/build.gradle`.

---

## 3. Feature Design

### 3.1 F1 — Auto-register speakers as event participants

**Goal**: when a speaker is committed to an event (accepted via pool OR added as main/co-speaker on a session), they are automatically a participant of that event.

**Trigger surface** (we hook all three so coverage is total):

| Trigger | Where | When |
|---|---|---|
| T1: speaker_pool `INVITED → ACCEPTED` | `SpeakerWorkflowService.runAcceptedHook` (line 526) | Speaker confirms via portal |
| T2: speaker_pool `READY → ACCEPTED` | Same hook (on-behalf path, 2026-05-20) | Organizer accepts on speaker's behalf |
| T3: Session main/co-speaker assignment (PRIMARY_SPEAKER created at READY, CO_SPEAKER added later) | New service method `assignSessionSpeaker(sessionId, username, role)` + READY-hook `provisionSessionAndPrimarySpeaker` (line 412) | Speaker added to a session |

**Implementation**:

```java
// services/event-management-service/src/main/java/ch/batbern/events/service/
//   SpeakerAutoRegistrationService.java
@Service
@RequiredArgsConstructor
@Slf4j
public class SpeakerAutoRegistrationService {

  private final RegistrationRepository registrationRepository;
  private final UserApiClient userApiClient;
  private final EventRepository eventRepository;

  /** Idempotent. Safe to call from any hook; no-op if already registered. */
  @Transactional
  public void autoRegisterIfAbsent(UUID eventId, String username, String triggerSource) {
    Event event = eventRepository.findById(eventId).orElseThrow();
    // Skip past events (defensive)
    if (event.getDate() != null && event.getDate().isBefore(Instant.now())) {
      log.info("Skipping auto-registration: event {} is in the past", event.getEventCode());
      return;
    }
    // Idempotency: unique constraint (event_id, attendee_username) already exists
    if (registrationRepository.findByEventIdAndAttendeeUsername(eventId, username).isPresent()) {
      return;
    }

    UserResponse user;
    try {
      user = userApiClient.getUserByUsername(username);
    } catch (UserNotFoundException e) {
      log.warn("Cannot auto-register speaker {} for {}: user not found", username, event.getEventCode());
      return; // graceful — speaker_pool transition still succeeds
    }

    Registration r = Registration.builder()
        .registrationCode(generateRegistrationCode(event.getEventCode()))
        .eventId(eventId)
        .attendeeUsername(username)
        .attendeeFirstName(user.getFirstName())
        .attendeeLastName(user.getLastName())
        .attendeeEmail(user.getEmail())
        .attendeeCompanyId(user.getCompanyId())
        .status("confirmed")  // speakers don't need a reg-confirm email
        .registrationDate(Instant.now())
        .deregistrationToken(UUID.randomUUID())
        .build();
    r.setMetadata(Map.of("autoRegisteredFrom", triggerSource));
    registrationRepository.save(r);
    log.info("Auto-registered speaker {} as participant of {} (trigger={})",
        username, event.getEventCode(), triggerSource);
  }
}
```

**Wiring**:
- `SpeakerWorkflowService.runAcceptedHook` → add `autoRegService.autoRegisterIfAbsent(eventId, username, "POOL_ACCEPTED")` after `confirmSessionUserIfPresent` (line 553).
- `SpeakerWorkflowService.provisionSessionAndPrimarySpeaker` → after `sessionUserRepository.save(speaker)` (line 448), call `autoRegService.autoRegisterIfAbsent(eventId, username, "SESSION_PRIMARY_SPEAKER")`.
- New `SessionUserService.addCoSpeaker(sessionId, username)` → calls `autoRegService.autoRegisterIfAbsent(eventId, username, "SESSION_CO_SPEAKER")`. (If the service already has a co-speaker entry-point, hook there; if not, add a thin one — surfaced via existing organizer kanban controller.)

**Why inline (not async listener)**: the user's spec says "automatisch als Teilnehmer dieses Events erfassen" — same transaction = guaranteed consistency. A listener would be eventually-consistent and observability is worse. The trade-off cost (slightly longer accept TX) is < 50ms because we already have UserApiClient cached.

**Capacity gate** (Story 10.11): speakers bypass `registration_capacity` — they're committed by the organizer, not registering normally. We set `status="confirmed"` and skip the capacity check. Documented in the service Javadoc.

**Skip rules**:
- Past events (defensive).
- User-not-found (graceful log, do not break the speaker workflow).
- Already registered (idempotent).

**Email**: auto-registration sends **no confirmation email**. Per `feedback_no_real_comms_in_tests.md` + the speaker already gets the speaker-acceptance email. Defensive bool flag `sendConfirmationEmail=false` on the new path. Newsletter auto-subscribe is also off (speaker did not opt in).

---

### 3.2 F2 — Per-event email aliases

#### `batbernXX-speaker@batbern.ch` — organizer → all main speakers of scheduled sessions of event XX

Semantics:
- Local-part regex: `^batbern(\d+)-speaker$`
- Maps to event `BATbern{N}`.
- Recipients = distinct emails of `SessionUser` rows where `session.event_id = event.id AND session.start_time IS NOT NULL AND session_user.speaker_role = 'PRIMARY_SPEAKER'`.
- Story 10.32 fan-out: also include each user's `additionalEmails`.
- Sender authorization: **organizers only** (same as `ok@`, `partner@`, `batbern{N}@`). External speakers cannot blast each other.

#### `batbernXX-moderator@batbern.ch` — anyone (typically a speaker) → event organizer

Semantics:
- Local-part regex: `^batbern(\d+)-moderator$`
- Recipients = the single `Event.organizerUsername` resolved to email via `UserApiClient.getEmailByUsername()`.
- **"Event moderator" interpretation**: BATbern has one lead organizer per event (`Event.organizerUsername`). The codebase's `SessionUser.MODERATOR` is a per-session concept (panel moderators), which is **not** what the user wants. Documenting this choice explicitly — if requirement is "the session moderator", swap `fetchEventOrganizer` for `fetchSessionModeratorByEvent` (1-line change).
- Sender authorization: **anyone** (it's a contact alias for the event — same model as `info@`, `events@`, `support@`).

#### Implementation steps

**Backend (event-management-service)** — new endpoint surfaced via OpenAPI:

```yaml
# docs/api/events-api.openapi.yml — new operation
GET /events/{eventCode}/distribution-list/{kind}:
  parameters:
    - name: kind
      enum: [speakers, moderator]
  responses:
    200:
      schema:
        type: object
        properties:
          eventCode: string
          kind: string
          emails: { type: array, items: { type: string } }
```

Controller calls `DistributionListService.resolveSpeakers(eventCode)` or `resolveModerator(eventCode)`:
- `resolveSpeakers`: `sessionUserRepository.findByEventIdAndSpeakerRoleAndScheduled(eventId, PRIMARY_SPEAKER)` → enrich via UserApiClient → flatten primary + `additionalEmails`.
- `resolveModerator`: lookup `Event.organizerUsername` → `userApiClient.getUserByUsername(username)` → flatten primary + `additionalEmails`.

Auth: marked `@PreAuthorize` allowing the Lambda's internal call path (same anonymous-with-internal-token pattern already used by `/events/{eventCode}/registrations` consumed by the forwarder).

**Lambda (`infrastructure/lambda/email-forwarder/`):**

```typescript
// address-resolver.ts — add before the bare batbern{N}@ case
const speakerMatch = localPart.match(/^batbern(\d+)-speaker$/);
if (speakerMatch) {
  return fetchEventDistributionList(`BATbern${speakerMatch[1]}`, 'speakers');
}
const moderatorMatch = localPart.match(/^batbern(\d+)-moderator$/);
if (moderatorMatch) {
  return fetchEventDistributionList(`BATbern${moderatorMatch[1]}`, 'moderator');
}

async function fetchEventDistributionList(eventCode: string, kind: 'speakers' | 'moderator'): Promise<string[]> {
  const url = `${API_GATEWAY_URL}/api/v1/events/${eventCode}/distribution-list/${kind}`;
  const response = await fetch(url);
  if (!response.ok) {
    if (response.status === 404) console.warn(`Event not found: ${eventCode}`);
    else console.error(`Failed to fetch ${kind} list for ${eventCode}: ${response.status}`);
    return [];
  }
  const data = await response.json() as { emails: string[] };
  return data.emails;
}
```

```typescript
// sender-auth.ts — extend rules
// batbern{N}-speaker → organizers only (mass-mail to speakers)
// batbern{N}-moderator → anyone (it's a contact-the-moderator inbox)
const speakerMatch = localPart.match(/^batbern(\d+)-speaker$/);
const moderatorMatch = localPart.match(/^batbern(\d+)-moderator$/);
if (moderatorMatch) return true;
if (speakerMatch) {
  const organizerEmails = await getOrganizerEmails();
  return organizerEmails.includes(senderEmail.toLowerCase());
}
```

**No DNS / SES rule changes** needed — the catch-all rule already routes both addresses to the same Lambda.

**Audit & deliverability**:
- Forwarder Lambda already logs `EmailsForwarded` / `EmailsRejected` / `EmailsUnresolved` to CloudWatch with rate-limit alarm (line 279).
- Each forwarded message keeps original `From:` rewritten to `noreply@batbern.ch` with `Reply-To` preserving the original sender — same pattern as existing `ok@`, `info@` etc. No new SES verified identity required.

---

### 3.3 F3 — Name-badge XLSX export

**Goal**: download Excel of all event participants for badge printing.

**Columns** (per user spec): `Vorname`, `Name`, `Firma`, `Rolle`
- `Rolle ∈ {Organisator, Referent, Teilnehmer}` (DE — these strings go on physical badges, German is the conference language).

**Row sources** (union, de-duplicated by username):

1. **Organizers**: `userApiClient.fetchUsersByRole("ORGANIZER")` (existing endpoint).
2. **Speakers of this event**: `sessionUserRepository.findByEventIdAndSpeakerRoleIn(eventId, [PRIMARY_SPEAKER, CO_SPEAKER])` enriched via UserApiClient.
3. **Registered attendees**: `registrationRepository.findByEventIdAndStatusIn(eventId, ["registered", "confirmed", "attended"])`.

Role precedence when a user appears in multiple sets (rare but possible — e.g. organizer also registers as attendee): `Organisator > Referent > Teilnehmer` (most "active" role wins for the badge).

**Backend**:

```java
// services/event-management-service/build.gradle — add:
implementation 'org.apache.poi:poi-ooxml:5.4.0'  // already used by partner-coordination

// services/event-management-service/src/main/java/ch/batbern/events/service/
//   ParticipantsExportService.java
@Service
@RequiredArgsConstructor
public class ParticipantsExportService {
  private final EventRepository eventRepository;
  private final RegistrationRepository registrationRepository;
  private final SessionUserRepository sessionUserRepository;
  private final UserApiClient userApiClient;

  public byte[] generateNameBadgeXlsx(String eventCode) { /* ... */ }
}

// Controller: ParticipantsController (existing or new)
@GetMapping(value = "/events/{eventCode}/participants/export.xlsx",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
@PreAuthorize("hasRole('ORGANIZER')")
public ResponseEntity<byte[]> exportNameBadges(@PathVariable String eventCode) {
  byte[] xlsx = exportService.generateNameBadgeXlsx(eventCode);
  return ResponseEntity.ok()
      .header(HttpHeaders.CONTENT_DISPOSITION,
              "attachment; filename=\"" + eventCode + "-namensschilder.xlsx\"")
      .body(xlsx);
}
```

Implementation copies `PartnerAttendanceExportService` style (SXSSFWorkbook, bold header with grey background, `try-with-resources` + `workbook.dispose()`, ByteArrayOutputStream).

**Frontend**:

```typescript
// web-frontend/src/services/eventService.ts — add
export async function exportParticipantsXlsx(eventCode: string): Promise<Blob> {
  const response = await apiClient.get(`/events/${eventCode}/participants/export.xlsx`,
                                        { responseType: 'blob' });
  return response.data;
}
```

```tsx
// web-frontend/src/components/organizer/EventPage/EventParticipantsTab.tsx
// Add button to the header row, beside the participant-count chip:
<Button
  variant="outlined"
  startIcon={<DownloadIcon />}
  onClick={handleExport}
  data-testid="participants-export-xlsx"
>
  {t('event.participants.exportNameBadges')}
</Button>
```

Handler triggers `eventService.exportParticipantsXlsx(eventCode)` → `URL.createObjectURL(blob)` → anchor click → revoke URL. Same pattern as `PartnerAnalyticsExport` button.

**i18n**: add `event.participants.exportNameBadges` to all 10 locales (frontend i18n rule from CLAUDE.md — DE+EN only is for **email templates**, not UI keys).

**OpenAPI**: update `docs/api/events-api.openapi.yml`, regenerate frontend types (`cd web-frontend && npm run generate:api-types`), commit generated types.

---

## 4. Migration / Data

- **No Flyway migration required.** All three features reuse existing tables (`registrations`, `sessions`, `session_users`, `events`, `user_profiles`). The `registrations.metadata` JSONB column already exists for the `autoRegisteredFrom` flag.
- One-time data backfill for already-accepted speakers (those currently in `speaker_pool.status = 'accepted'` but not in `registrations`): a **separate, opt-in script** under `scripts/data/backfill-speaker-registrations.sh`. Not run automatically. Documented in the PR body.

---

## 5. Test Strategy

| Layer | What we test | Lives in |
|---|---|---|
| **Unit (Java)** | `SpeakerAutoRegistrationService.autoRegisterIfAbsent` — happy path, idempotent re-call, past-event skip, user-not-found skip | `services/event-management-service/src/test/.../service/SpeakerAutoRegistrationServiceTest.java` |
| **Unit (Java)** | `ParticipantsExportService.generateNameBadgeXlsx` — column order, role precedence, dedupe, locale strings (DE) | `ParticipantsExportServiceTest.java` |
| **Unit (Java)** | `DistributionListService.resolveSpeakers/resolveModerator` — scheduled-only filter, additionalEmails fan-out, dedupe, missing-event 404 | `DistributionListServiceTest.java` |
| **Integration (Java + Testcontainers PG)** | `SpeakerWorkflowServiceIntegrationTest` extended — `INVITED→ACCEPTED` results in `registrations` row | extend existing |
| **Integration (Java + Testcontainers PG)** | `ParticipantsExportControllerIntegrationTest` — endpoint returns XLSX, correct MIME, content-disposition, role precedence | new |
| **Integration (Java + Testcontainers PG)** | `DistributionListControllerIntegrationTest` — both kinds, 404 on unknown event, organizer auth | new |
| **Lambda unit (Vitest)** | `address-resolver.test.ts` — new regex cases call `/distribution-list/...`, error paths | extend `infrastructure/test/unit/email-forwarder.test.ts` |
| **Lambda unit (Vitest)** | `sender-auth.test.ts` — `-speaker` requires organizer; `-moderator` allows anyone | extend |
| **Frontend (Vitest + RTL)** | `EventParticipantsTab.test.tsx` — button renders, click triggers download (mock URL.createObjectURL) | extend |
| **Bruno (Layer 2)** | `events-distribution-list.bru` (organizer auth, both kinds, 404) + `events-participants-export.bru` (XLSX size + MIME) | new |
| **Playwright (Layer 3)** | organizer project: navigate to event detail → Participants → click export → verify download started | `web-frontend/e2e/organizer-event-participants-export.spec.ts` |

**TDD order**: tests written first per `CLAUDE.md` Red-Green-Refactor mandate. Coverage targets: 90% on the three new services.

**Local smoke test plan** (`make dev-native-down && make dev-native-up`):

1. Bring services up; tail `/tmp/batbern-1-event-management.log`.
2. Run unit + integration tests on changed services; `tee /tmp/test-out.log`, grep for `BUILD FAILED` / `Tests run`.
3. Hit the new endpoints with curl + an organizer token from `~/.batbern/staging-organizer.json`:
   - `GET /api/v1/events/BATbern57/distribution-list/speakers` — expect JSON list (could be empty if local DB has no scheduled sessions for that event; that's a pass).
   - `GET /api/v1/events/BATbern57/distribution-list/moderator` — expect single-item list.
   - `GET /api/v1/events/BATbern57/participants/export.xlsx` — expect 200 + binary > 0 bytes; `file /tmp/x.xlsx` should report `Microsoft Excel`.
4. Frontend: open `http://localhost:8100/events/BATbern57`, switch to Participants tab, click export button, confirm file downloads.

**What we explicitly do not test locally** (per `feedback_no_real_comms_in_tests.md`):
- No real SES `SendRawEmail` calls. The Lambda is exercised in **its own Vitest suite**, not against live SES.
- No DNS or MX flips.
- No `replies@` or forwarding inbound flow — those only fully work in staging/production after the Lambda is deployed.

---

## 6. Deployment Story

| Component | Ships how |
|---|---|
| Backend Java changes (auto-registration, distribution-list endpoint, XLSX export) | Standard ECS image push via deploy-staging.yml (Tier 1 fast-path if no migration; Tier 2 hotswap otherwise — but we have no migration, so fast-path) |
| Frontend changes (export button + i18n keys) | Standard frontend pipeline |
| Lambda changes (`address-resolver.ts`, `sender-auth.ts`) | Layer-Based deploy of `InboundEmailStack` (Tier 3 — touched by changes under `infrastructure/lambda/email-forwarder/`). No CDK stack-shape change, only Lambda bundle update. |

**Risk**: the Lambda re-deploy is non-zero-downtime (re-publishes the function). SES will buffer inbound messages briefly during the swap; existing replies/forwarding will continue to work because the catch-all rule is unchanged.

---

## 7. Risk Register

| Risk | Mitigation |
|---|---|
| Auto-registration breaks the speaker-accept TX if UserApiClient is down | `getUserByUsername` already has graceful fallback. We catch and log; do not rethrow into the speaker workflow. Unit-tested. |
| Capacity overflow: speakers bypass `registration_capacity` and a fully-booked event ends up at 102/100 | This is *intentional* — speakers are committed by organizers, not registering. Documented in service Javadoc + plan §3.1. |
| Duplicate registrations on re-trigger (e.g. organizer re-accepts) | Unique constraint `(event_id, attendee_username)` + idempotency guard in service. Existing constraint, no migration. |
| Lambda regex too broad: `batbern{N}-speaker-foo@` accidentally matches | Anchored regex `^batbern(\d+)-speaker$` + `^batbern(\d+)-moderator$`. Unit-tested with negative cases. |
| `batbernXX-moderator@` accepts mail from anyone → spam vector | Existing CloudWatch alarm at 20 rejected/hour. Plus the forwarder always rewrites `From: noreply@batbern.ch`, so receiver replies route to `noreply` (silently discarded), not to the organizer's true inbox. Spam blast cost = 1 mail. Acceptable. |
| XLSX export blows memory on huge events | `SXSSFWorkbook(100)` streams 100 rows in memory; the largest BATbern event historically is ~500 participants. Negligible. |
| OpenAPI spec change requires generated-types regen | Mandatory: `cd web-frontend && npm run generate:api-types` after spec edit; committed per ADR-006. CI fails otherwise. |
| "Event moderator" interpretation differs from user intent | Documented (§3.2). 1-line resolver swap if needed. |

---

## 8. Implementation Order

1. **F3 XLSX export** (lowest risk, no inbound deps) — TDD: tests, service, controller, OpenAPI, types, frontend button + i18n.
2. **F1 auto-registration** — TDD: `SpeakerAutoRegistrationService` + tests, wire into 3 hook points, integration test through speaker workflow.
3. **F2 distribution-list endpoint** — TDD: `DistributionListService` + tests, controller, OpenAPI.
4. **F2 Lambda extension** — `address-resolver.ts` + `sender-auth.ts` + Vitest tests.
5. **Manual smoke** via `make dev-native-down && make dev-native-up`.
6. **Commit per concern** (5 commits: xlsx, auto-reg, distribution-list, lambda-resolver, docs).
7. **Push + open PR** to `develop`.

---

## 9. Acceptance Criteria

- ✅ Speaker pool ACCEPTED transition creates a `registrations` row with `status='confirmed'` and `metadata.autoRegisteredFrom='POOL_ACCEPTED'`.
- ✅ Adding a session main/co-speaker creates a `registrations` row (`SESSION_PRIMARY_SPEAKER` / `SESSION_CO_SPEAKER`).
- ✅ Re-running the same trigger does NOT duplicate the row.
- ✅ `GET /events/{eventCode}/distribution-list/speakers` returns `PRIMARY_SPEAKER` emails (with `additionalEmails`) of scheduled sessions of that event.
- ✅ `GET /events/{eventCode}/distribution-list/moderator` returns the event organizer's email(s).
- ✅ Lambda `address-resolver` unit test passes with `batbernXX-speaker@` and `batbernXX-moderator@` cases.
- ✅ Lambda `sender-auth` unit test passes: `-speaker` requires organizer, `-moderator` allows anyone.
- ✅ `GET /events/{eventCode}/participants/export.xlsx` returns a valid XLSX with columns Vorname, Name, Firma, Rolle and roles ∈ {Organisator, Referent, Teilnehmer}.
- ✅ Participants tab has a download button that triggers the XLSX.
- ✅ All new tests pass; existing tests remain green; no Flyway migration added.
- ✅ Frontend types regenerated and committed; OpenAPI spec updated.
- ✅ PR body documents what is verified locally vs what becomes effective post-deploy (Lambda redeploy).

---

## 10. Open Trade-offs (logged, not blockers)

- **Inline vs async listener for auto-registration**: chose inline for read-your-write consistency. If perf later becomes a concern (it won't — UserApiClient is cached), refactor behind `@EventListener @Async`.
- **Excel locale**: badges are German per project convention; we hardcode `{Organisator, Referent, Teilnehmer}`. If multilingual badges are ever needed, swap to a `Locale` parameter — out of scope.
- **Capacity bypass for speakers**: deliberate. Speakers are committed, not registering. Surfaced clearly in audit (`metadata.autoRegisteredFrom`).
- **"Moderator" = `Event.organizerUsername`**: BATbern semantics, not session-moderator semantics. Easy 1-line revisit if wrong.
