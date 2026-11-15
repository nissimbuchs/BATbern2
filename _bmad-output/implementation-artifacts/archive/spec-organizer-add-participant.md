# Spec: Organizer "Add Participant" (manually confirm a user onto an event)

**Status:** Ready for Development
**Goal:** Let an organizer pick an existing BATbern user and add them to an event **directly as a `confirmed` participant** — skipping the self-registration + email-confirmation dance — from the event's **Registrations** tab.

**Why:** Today the only organizer-initiated registrations are *Enrol organizers/partners* (bulk stakeholders, programmatic) and *Promote-from-waitlist* (waitlist only). There is no way to add an arbitrary person (a VIP, a late phone/email request, a colleague) as a confirmed attendee. The pieces already exist — this composes them.

---

## Reuse map (recompose, not rewrite — NFR9)

| Need | Reuse |
|------|-------|
| User picker (search by name/email) | `web-frontend/src/components/shared/UserAutocomplete.tsx` (→ `useUserSearch` → `GET /users/search`), as used by `SpeakerDrawer/PromoteSpeakerSubView.tsx` |
| Create a `confirmed` registration | Model on `RegistrationService.createInternalRegistration` (`RegistrationService.java:~528`) — but a **real-attendee** variant (see AC4) |
| Capacity / dedupe / status conventions | `RegistrationService` authenticated-path (`:~610-650`): `CAPACITY_STATUSES`, `countByEventIdAndStatusIn`, lowercase status, cancelled→delete+recreate |
| Organizer endpoint home | `ParticipantsController.java` (already `/api/v1/events/{eventCode}/participants/*`) |
| Frontend service + cache invalidation | `web-frontend/src/services/api/eventRegistrationService.ts` (`promoteFromWaitlist` pattern + its `invalidateQueries`) |
| Button location | `EventParticipantsTab.tsx` (next to the existing "Enrol" button) |

---

## Backend

### FR1 — `POST /api/v1/events/{eventCode}/participants` (organizer-only)
- New method in `ParticipantsController.java`, `@PreAuthorize("hasRole('ORGANIZER')")`.
- Body `AddParticipantRequest`: `{ "username": string, "force": boolean (default false), "notify": boolean (default true) }`.
- Delegates to `RegistrationService.addParticipant(event, username, force, notify)`.
- Returns `201` with the created registration (reuse `RegistrationResponse`); `404` unknown event/user; `409` duplicate or capacity-full (see ACs).

### FR2 — `RegistrationService.addParticipant(Event, String username, boolean force, boolean notify)`
- Resolve the user via `userApiClient.getUserByUsername(username)` (`404` → propagate as not-found).
- **Dedupe** (mirror authenticated path): existing non-`cancelled` registration → throw `IllegalStateException` → `409`; existing `cancelled` → delete then recreate.
- **Capacity:** `activeCount = countByEventIdAndStatusIn(eventId, CAPACITY_STATUSES)`; if `capacity != null && activeCount >= capacity && !force` → throw a capacity-exceeded exception → `409` (message names the count/capacity). When `force=true`, create anyway (organizer override).
- Build `Registration` with `status="confirmed"`, real attendee fields from the user, `registrationDate=now()`. **Audit metadata** `addedByOrganizer=<currentUsername>` — but **NOT** `Registration.AUTO_REGISTERED_FROM_KEY` (that key excludes a row from `realAttendeeCount`; a manually-added participant IS a real attendee and should count for capacity + the delete-guard).
- `deregistrationToken` auto-generates via the existing `@PrePersist`.
- If `notify`, send a **dedicated** "added by the organizing team" confirmation via
  `RegistrationEmailService.sendOrganizerAddedConfirmation` (template `registration-organizer-added-{de,en}`).
  This is NOT the double-opt-in registration-confirmation email and NOT the waitlist-promotion
  email: the place is already confirmed, so there is no "please confirm" CTA, no waitlist
  narrative, and no registration code — just a "you're registered, your place is confirmed"
  notice with the event details, a calendar (.ics) invite, and the self-service deregistration
  link. (Corrected 2026-06-16 after the first cut wrongly reused the waitlist-promotion email.)

### FR3 — OpenAPI + types
- Add the operation + `AddParticipantRequest` schema to `docs/api/events-api.openapi.yml`.
- **Regenerate frontend types** (`npm run generate:api-types`) and commit `events-api.types.ts` — CI's "Verify API types are up-to-date" step fails otherwise.

## Frontend

### FR4 — `eventRegistrationService.ts`: `addParticipant(eventCode, { username, force?, notify? })`
- `apiClient.post('/events/${eventCode}/participants', body)`. On success the caller invalidates the same query keys `promoteFromWaitlist` does (participant list + event counts).

### FR5 — `AddParticipantDialog.tsx` (new, under `EventPage/`)
- MUI Dialog: `UserAutocomplete` (`value`/`onChange`), an optional "Notify by email" checkbox (default on), Cancel + Add buttons. `data-testid="add-participant-dialog"`, `add-participant-confirm`, `add-participant-user`.
- **Existing users only** — the picker searches `/users/search`; there is **no** free-text "add by email / new person" path (decision, 2026-06-15). The Add button is disabled until a user is selected.
- On Add: call the service. On `409 capacity` → inline confirm ("Event is full — add anyway?") that re-submits with `force=true` (NFR1: consequential action confirmed). On `409 duplicate` → inline error "already registered".
- Success → close, snackbar, list refetch.

### FR6 — `EventParticipantsTab.tsx`: "Add participant" button + action-row relayout
- Add a 4th `Button` (`startIcon={<PersonAddIcon />}`, `data-testid="add-participant-button"`, i18n `eventPage.participantsTab.addParticipant`) that opens `AddParticipantDialog`, alongside the existing three: Export name badges (XLSX), Export (DOCX), Enroll Organizers & Partners.
- **Relayout the header (FR6a):** today the action buttons share the title's line via the outer `space-between` Stack (`EventParticipantsTab.tsx:143-191`). With a 4th button this is too wide on desktop. Split the header into two stacked rows:
  - **Row 1:** icon + title + count `Chip` only.
  - **Row 2 (new line, below the title):** the action-button group `<Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} flexWrap="wrap">` holding all **four** buttons — a single row on desktop (`sm+`), stacked full-width on mobile (`xs`, each button already `width:{xs:'100%',sm:'auto'}`).
  - Drop the outer Stack's `justifyContent="space-between"`/`md:'row'`; the header is now `direction="column"` with the two rows. Keep `mb: 3`.
- i18n key `eventPage.participantsTab.addParticipant` in **all 10 locales**.

---

## Acceptance Criteria

- **AC1** Given an organizer on Registrations, When they click "Add participant", pick a user, and confirm, Then a `confirmed` registration is created and the user appears in the list under the *Confirmed* filter.
- **AC2** Given the chosen user is already actively registered, When the organizer confirms, Then the API returns `409` and the dialog shows "already registered" (no duplicate row).
- **AC3** Given the chosen user has a `cancelled` registration, When added, Then the cancelled row is replaced by a new `confirmed` one.
- **AC4** Given a manually-added participant, When the event's `realAttendeeCount` / delete-guard is evaluated, Then this participant **counts as real** (row has no `AUTO_REGISTERED_FROM_KEY`).
- **AC5** Given the event is at capacity, When the organizer adds without force, Then `409` is returned; When they confirm "add anyway", Then `force=true` creates the confirmed registration over capacity.
- **AC6** Given `notify=true`, When the participant is added, Then a registration-confirmation email (with deregistration link) is sent to their address; Given `notify=false`, Then no email is sent.
- **AC7** Given a non-organizer principal, When `POST /participants` is called, Then `403`.
- **AC8** Given an unknown `username` or `eventCode`, Then `404`.

## Tests
- **Integration** (`ParticipantsControllerIntegrationTest`, Testcontainers): AC1/AC2/AC3/AC5/AC7/AC8 + real-attendee count (AC4).
- **Service unit** (`RegistrationServiceTest`): capacity force vs reject, cancelled→recreate, metadata marker, notify on/off.
- **Frontend unit**: `AddParticipantDialog` renders, search→select→submit calls service, capacity-force path, duplicate error.
- **E2E `@smoke` + `@gate`** (`e2e/.../add-participant.spec.ts`, prod-safe throwaway event): organizer adds a participant via the dialog → appears confirmed → `afterAll` cleanup via fixture cascade.

---

## Resolved Decisions (2026-06-15)

1. **Existing users only — no add-by-email.** Participants are picked from existing BATbern users via `UserAutocomplete`/`/users/search`. There is no free-text "add a new person by email" path; the anonymous `getOrCreateUser` route used by public self-registration is deliberately NOT reused here.
2. **Over-capacity → organizer override (with confirm).** Adding when full returns `409`; the dialog offers an explicit "add anyway" that re-submits with `force=true`. No hard block.
3. **Notify by default on.** `notify=true` by default (attendee gets a confirmation + deregistration link); the organizer can untick it.
4. **Confirmed only.** The button always creates a `confirmed` registration — no "add as pending" option.
