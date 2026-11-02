# Story 16.9: Partner meeting mails in the mail center

Status: review

## Story

As an **organizer**,
I want **partner meeting invites and cancellations to appear in the event's "What went out" with their RSVPs, and to write a plain mail to all partners from the same composer as every other audience**,
so that **the partner side of an event is visible to the whole team like everything else, and partners stop being the one audience that only has a hard-coded invite**.

> Source: `docs/prd/epic-16-mail-center.md` → **Story 16.9** (owner request 2026-10-30) and owner decision #1163 (2026-10-31: keep the single To: line).
> **Depends on 16.1a** (recorder SPI) and **16.8** (composer, adapters). Start after 16.8a and 16.8b are on `develop`.
> **UX reference:** `docs/ux/mail-center-spec.md` → "Composer" → "Partner".

## Acceptance Criteria

### Part A — record what partner-coordination sends

1. **AC1 (recorded)** — GIVEN a partner meeting invite is sent, THEN it appears in the event's "What went out" within one minute: audience Partners, template `partner-meeting-invite`, sender = the organizer who clicked, recipient count = the number of addresses on the To: line, and the RSVP summary of that meeting (accepted / declined / tentative / no answer) alongside. A cancellation (deleting a meeting whose invite was sent) appears the same way with `partner-meeting-cancellation`.
2. **AC2 (best effort)** — GIVEN EMS is unreachable or answers with an error, THEN the invite is still sent, `sendInvite` returns as today, and one WARN line is logged with the meeting id and the HTTP status or exception class (no addresses, no body). No retry.
3. **AC3 (one To: line unchanged)** — The invite and cancellation still go out as one mail with every partner and organizer address on the To: line (owner decision #1163). The record splits that line into one `mail_recipient` row per address, so 16.1b's delivery status (matched by SES Message-ID **and** address) works per partner.
4. **AC4 (internal endpoint)** — EMS `POST /internal/mails` accepts the 16.1a `MailContext` + `OutboundMail` payload and records it through `JpaOutboundMailRecorder`. It needs no JWT, is not routed by the gateway (which denies `/internal/**`), accepts audience `PARTNERS` only (400 otherwise), and enforces size limits (400). It is not in a public OpenAPI spec, like `InternalRsvpController`.
5. **AC5 (per meeting)** — The partner meeting detail panel shows the mails of that meeting (same list component, filtered by meeting).

### Part B — plain partner mail through the composer

6. **AC6 (composer)** — "+ New mail → Partners" opens `MailComposer`. Its template list has:
   1. "Partner meeting invite with calendar": selecting it shows this event's meetings and the existing invite action (`PartnerMeetingInviteAction`, with its confirmation), plus the hint that its text is still fixed; no preview, no test send, no "use as starting point" (spec);
   2. the templates of the new category **PARTNER** (seeded: `partner-info`).
7. **AC7 (same as every audience)** — A plain partner mail supports template choice, preview, test send to organizers and send to recipients, like every other audience (16.8 AC1–AC6). It is one mail, so the preview has no ‹ › stepping (as venue: `personalized = false`).
8. **AC8 (recipients, owner decision 2026-11-01)** — ONE mail with every PARTNER-role user (CUMS) on the To: line, sorted by company then name, the same shape as the meeting invite (#1163). Users without an email are listed under `skipped[]`. Organizers are not added (they get the test send, and the mail is visible in "What went out"). The record splits the To: line into one `mail_recipient` row per address (same rule as AC3), so delivery status stays per address.
9. **AC9 (category PARTNER)** — The admin template editor lists, filters and creates PARTNER templates; the label is translated in all 10 locales; the API enum includes `PARTNER` everywhere it lists categories.
10. **AC10 (mail safety)** — No test sends real mail. The partner service's HTTP recorder is tested with a mock server; partner invites are never triggered by Bruno or Playwright against staging. Verified 2026-11-01: `bruno-tests/partner-meetings-api/09-delete-meeting.bru` deletes a meeting whose invite was never sent, and `e2e/organizer/partner-meetings.spec.ts:152` mocks `send-invite` with `page.route`. Keep it that way.

## Tasks / Subtasks

### Part A — recording (PR 1, `feature/16-9a-partner-mail-record`)

- [x] **Task 1 — Shared kernel** (AC: 1, 5)
  - [x] `MailContext` gains a nullable `reference` (what the mail is about, e.g. `partner-meeting:{meetingId}`) and `withReference(String)`. Only `MailContext.java` calls the canonical constructor (3 places, verified); update them and `MailContextTest`.
- [x] **Task 2 — Migration** (AC: 1, 5)
  - [x] EMS `V{next}__mail_message_reference.sql`: `ALTER TABLE mail_message ADD COLUMN reference VARCHAR(100)`; index `(event_code, reference)`. **Re-check the head before numbering** (V136 at drafting; 16.2 and 16.5 also add migrations; whichever ships first takes the number).
  - [x] Cross-service reference by value, no FK (ADR-003). The meeting id is the partner meeting's public id (`/partner-meetings/{meetingId}`), so it may appear in the API.
- [x] **Task 3 — RED: tests first** (AC: 1–5, 10)
  - [x] EMS `InternalMailControllerIntegrationTest` (`AbstractIntegrationTest`): records a PARTNERS mail with 5 To addresses → 1 `mail_message` (with `reference`), 5 `mail_recipient` rows, `recipient_count = 5`; non-PARTNERS audience → 400; oversized body → 400; reachable without a token (assert the filter chain lets it through).
  - [x] EMS `JpaOutboundMailRecorder` test: a comma-separated To is split, trimmed, empty parts dropped; a single address unchanged (no regression for the other audiences).
  - [x] EMS `MailMessageQueryService` test: `reference` filter; summary exposes `reference`.
  - [x] Partner `HttpOutboundMailRecorderTest` (`MockRestServiceServer`): posts the payload without `Authorization`; 503, timeout and connection refused → no exception, one WARN (AC2).
  - [x] Partner `PartnerInviteEmailServiceTest`: invite and cancellation pass a `MailContext` with audience PARTNERS, the template keys, the event code, the sender and `partner-meeting:{id}`; the To: line is unchanged (AC3).
- [x] **Task 4 — EMS internal endpoint** (AC: 3, 4)
  - [x] `mailcenter/InternalMailController`: `@PostMapping("/internal/mails")`, plain request record (`InternalMailRequest`), `@Valid`, limits: subject ≤ 1000, body ≤ 2 MB, ≤ 500 addresses. Maps to `MailContext` + `OutboundMail`, calls `JpaOutboundMailRecorder.record`. Returns 202.
  - [x] `SecurityConfig` (EMS): `.requestMatchers("/internal/**").permitAll()` in **every** filter chain (two `@Profile("!test")` chains at `SecurityConfig.java:65` and `:190`; check the test-profile config too). Comment as in CUMS `SecurityConfig.java:79-81`. `EndpointAuthorizationGuard` excludes `/internal/` (`shared-kernel/src/testFixtures/.../EndpointAuthorizationGuard.java:36`); no `@PreAuthorize` needed.
  - [x] `JpaOutboundMailRecorder`: split a multi-address `to` into rows; count = addresses; write `reference`.
  - [x] `MailMessageQueryService` + spec: `reference` on `MailMessageSummary`; `reference` query filter on `GET /events/{code}/mails`. Regenerate types.
- [x] **Task 5 — Partner-coordination recorder** (AC: 1, 2, 3)
  - [x] `partners/mail/HttpOutboundMailRecorder implements OutboundMailRecorder` (shared kernel picks it up: `EmailService` uses the recorder when a bean exists, 16.1a). `RestTemplate` with connect 2 s / read 5 s, base URL `event-management-service.base-url` (already in `application.yml:77-78`). No JWT: it runs on the `@Async` send thread with no `SecurityContext`, same as EMS `PartnerMeetingRsvpClientImpl`. Catch everything, log WARN, never rethrow.
  - [x] `PartnerInviteEmailService.sendCalendarInvites` / `sendCancellationNotice`: use the context-first `sendHtmlEmailWithAttachments(MailContext, to, cc, subject, html, attachments)` (`EmailService.java:322`). Context: `MailContext.of(MailTrigger.organizer(username), eventCode, PARTNERS, "partner-meeting-invite" | "partner-meeting-cancellation").withReference("partner-meeting:" + meetingId)`.
  - [x] Pass the organizer's username from `PartnerMeetingController` → `PartnerMeetingService.sendInvite` / `deleteMeeting` (today they do not receive it, `PartnerMeetingService.java:137`, `:189`).
- [x] **Task 6 — Frontend: list + RSVP + meeting panel** (AC: 1, 5)
  - [x] `MailSentList.tsx`: for a PARTNERS row with a `partner-meeting:` reference, show an RSVP summary chip, loaded through the existing partner meetings service (`getRsvps(meetingId)`, `services/api/partnerMeetingsApi.ts:101`) only for rows on screen; reuse the counting from `PartnerMeetingRsvpPanel.tsx`.
  - [x] `MeetingDetailPanel.tsx`: a "Mails" section rendering `MailSentList` with `audience=PARTNERS`, `reference=partner-meeting:{id}` (add both as props).
  - [x] Vitest for both; i18n keys in 10 locales.
- [x] **Task 7 — Verify Part A**
  - [x] EMS and partner-coordination suites to files.
  - [x] Local dev (`make dev-native-up`, all services): create a meeting on a throwaway event, send the invite, confirm `/dev/emails` has one mail with all addresses in To and EMS has the record with split recipients and the RSVP chip; stop EMS, send again, confirm the invite still goes out and the WARN line; delete the meeting → cancellation recorded.
  - [x] Docs: `docs/architecture/06-backend-architecture.md` (internal mail endpoint, partner recorder), user guide.

### Part B — plain partner mail (PR 2, `feature/16-9b-partner-adapter`)

- [x] **Task 8 — Category PARTNER** (AC: 9)
  - [x] Spec enums in `docs/api/event-newsletter-api.openapi.yml` (list filter :51-61, `EmailTemplateResponse.category` :1152-1163, `CreateEmailTemplateRequest.category` :1208-1219): add `PARTNER`. Regenerate types.
  - [x] `EmailTemplateSeedService.deriveCategory`: `partner-` prefix → `PARTNER` (before the `LAYOUT` fallback, :199; see V120 for what happens otherwise).
  - [x] Classpath templates `email-templates/partner-info-de.html` and `partner-info-en.html` (subject in the existing subject convention of the seeder; content only, layout `batbern-default`). Wording: neutral info mail to all partners, generic salutation ("Liebe Partnerinnen und Partner" / "Dear partners"), no per-person placeholder. Owner reviews the DE text.
  - [x] Admin: `EmailTemplatesTab.tsx` (:64-94) and `EmailTemplateEditModal.tsx` (:189-195) category lists + `emailTemplates.categories.PARTNER` in 10 locales.
  - [x] No migration: `email_templates.category` has no check constraint (V62).
- [x] **Task 9 — RED: adapter tests** (AC: 7, 8)
  - [x] `PartnerAudienceAdapterTest`: recipients from stubbed `UserApiClient.getPartnerUsernames()` (:129) + `getUserByUsername`; ONE recipient entry whose To: holds all addresses in order; skipped without email; only PARTNER templates allowed; no per-person variables; the record has one `mail_recipient` per address.
  - [x] Extend 16.8's byte-equal and test-send integration tests with PARTNERS.
- [x] **Task 10 — `PartnerAudienceAdapter`** (AC: 7, 8)
  - [x] Categories `PARTNER`; `personalized = false`; one recipient entry, all partner addresses on To: (as `VenueAudienceAdapter`, but To only, no Cc); variables (no per-person ones) `eventTitle`, `eventDate`, `eventNumber`, `currentYear`, `logoUrl`; Reply-To `partner@{forwarding domain}` (alias exists, `ForwardingAlias` :61-62); one `MailContext.of(trigger, eventCode, PARTNERS, templateKey)` per send.
  - [x] Add `PARTNERS` to `MailComposeRequest.audience` in the spec.
- [x] **Task 11 — Frontend** (AC: 6, 7)
  - [x] `composer/audiences/partners.tsx`: template list = pseudo-entry "Partner meeting invite with calendar" + PARTNER templates. The pseudo-entry renders the body of today's `PartnerMeetingsCompose` (meeting picker + `PartnerMeetingInviteAction`) in place of preview and footer, with the hint.
  - [x] `EventCommunicationsContainer.tsx`: Partners opens `MailComposer` (16.8 AC15 kept `PartnerMeetingsCompose` until now); `PartnerMeetingsCompose.tsx` becomes the pseudo-entry's body or is folded into it.
  - [x] Vitest (MSW): both paths; invite path keeps its confirmation and test ids.
- [ ] **Task 12 — Verify Part B**
  - [ ] Local dev: partner info mail preview (one mail, all partners on To:), test send, real send on a throwaway event; `/dev/emails` vs record; invite path still works.
  - [ ] Playwright: extend `event-mail-composer.spec.ts` (no send). Any send spec stays `@sends-real-email`, local only.

## Dev Notes

### What exists today (verified in code at 15a13959)

- Invite: `PartnerMeetingService.sendInvite` (`services/partner-coordination-service/.../service/PartnerMeetingService.java:137-175`) collects PARTNER + ORGANIZER emails from CUMS (:213-241, case-insensitive dedupe), bumps the ICS SEQUENCE, then calls `PartnerInviteEmailService.sendCalendarInvites` (`@Async`, :72-117), which joins all addresses into **one To: field** (:101) and sends with a hard-coded subject and body (:88-89) and an inline ICS. No `MailContext`, so nothing is recorded today.
- Cancellation: not a separate action. `deleteMeeting` sends a METHOD:CANCEL mail when the invite was sent (:189-210). The 16.4 picker's `PartnerMeetingInviteAction` offers the invite only.
- The pattern to copy, EMS → partner: `PartnerMeetingRsvpClientImpl` posts to `/internal/partner-meetings/rsvps` without JWT, logs and discards errors; partner-coordination permits `/internal/**` (`SecurityConfig.java:93-94`); the gateway denies `/internal/**` (`api-gateway/.../SecurityConfig.java:204-206`). **EMS has no `/internal/**` permit yet**; this story adds it.
- Partner sends use the transactional SES configuration set (`infrastructure/lib/stacks/partner-coordination-stack.ts:57`), so 16.1b's delivery events reach EMS once the rows exist.

### Why the recorder must split the To: line

`EmailService` hands the recorder `OutboundMail.to` exactly as the caller passed it; for partner invites that is `a@x,b@y,…`. `JpaOutboundMailRecorder` writes one row per `to` (:77) into `mail_recipient.address VARCHAR(320)`: a 40-address line would overflow, and 16.1b matches status by address, so nothing would ever be marked delivered. Split in EMS (`JpaOutboundMailRecorder`), not in the partner service, so any future multi-address caller is covered.

### Payload of `POST /internal/mails`

Flat JSON mirroring `MailContext` + `OutboundMail`: `sendGroupId`, `eventCode`, `audience`, `templateKey`, `contentSource`, `senderUsername`, `test`, `reference`, `to`, `cc[]`, `subject`, `htmlBody`, `sesMessageId`, `failureReason`. Already redacted by `EmailService.record` before the recorder sees it (16.1a), so no credential leaves the partner service.

### Out of scope, recorded

- The invite body stays hard-coded Java. Moving it into the admin store needs a render call from partner-coordination to EMS. Follow-up (open an issue when this story ships, if none exists).
- One To: line exposes partner addresses to each other: owner decision #1163, unchanged.
- Inbound: `partner@` is NOT an inbound address. It is an organizer-only broadcast to all partners (`ForwardingAlias.java:61`); 16.2b records it as a send (audience PARTNERS). Partner replies arrive via `replies@` / the Reply-To of the mail and thread through 16.2. iCal RSVP replies keep counting through `/internal/partner-meetings/rsvps`.

### Rules that bite

- ADR-003: the meeting id is stored as a string reference, no FK across services.
- ADR-008: `/internal/**` is the documented exception; everything public stays `hasRole('ORGANIZER')`.
- Flyway: new migration only, re-check the head.
- Mail safety: AWS is production. The invite button on staging sends a real invite to every partner; no test may press it there.
- Two PRs, each a production deploy: push, ask the owner, then open. Part A does not depend on Part B.

### References

- `docs/prd/epic-16-mail-center.md` → Story 16.9
- `_bmad-output/implementation-artifacts/16-1a-record-outbound-mail.md`, `16-8-shared-composer.md`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/service/{PartnerMeetingService,PartnerInviteEmailService}.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/controller/InternalRsvpController.java`
- `services/event-management-service/src/main/java/ch/batbern/events/client/impl/PartnerMeetingRsvpClientImpl.java`
- `services/event-management-service/src/main/java/ch/batbern/events/config/SecurityConfig.java`
- `services/event-management-service/src/main/java/ch/batbern/events/mailcenter/JpaOutboundMailRecorder.java`
- `web-frontend/src/components/organizer/{MeetingDetailPanel,PartnerMeetingInviteAction,PartnerMeetingRsvpPanel}.tsx`
- `web-frontend/src/components/organizer/EventPage/communications/PartnerMeetingsCompose.tsx`

## Dev Agent Record

### Agent Model Used

Claude Opus 5.5 (Amelia, bmad-dev-story), 2026-11-01

### Debug Log References

### Completion Notes List

**2026-11-02: rebased onto #1176 and 16.11 (#1177); shipped with 16.5 in one PR (`feature/16-5-own-text`)**

- Rebase onto #1176: invite synchronous with 502 kept, MailContext added to the hotfix's calls; partners.json `inviteFailed` + `mails` merged in all 10 locales.
- `PartnerAudienceAdapter` on the 16.11 pipeline (`storedTemplate` + catalog SYSTEM/EVENT variables, `scopes()` empty) instead of its own variable map.
- **Owner decision 2026-11-02 (supersedes #1163 and decision 3 of this story):** after a real invite to 20 recipients, invite, cancellation and the plain partner mail go to each recipient on their own; the invite ICS lists only that recipient as ATTENDEE. All sends of one action share a MailContext, so they stay ONE recorded mail with a row per address. One failed recipient is recorded FAILED and does not stop the others; the invite answers 502 only when nobody was reached, and its response counts the recipients reached. The 50-address guard of the plain partner mail is gone (no longer one SES message). Tests: PCS `PartnerInviteEmailServiceTest` (own ICS per recipient, same group, failure isolation, cancellation per recipient), PCS ITs, EMS `PartnerAudienceAdapterTest`, `MailComposerSendIntegrationTest` partners (one mail per partner, one record, two rows). PCS suite 228 / 0 failed.
- Task 12 NOT done: no local-dev send check and no Playwright extension for Partners. Unit and integration tests cover render, record and the per-recipient sends; local dev cannot exercise SES address validation anyway (no SesClient).

**Part B (branch `feature/16-9b-partner-adapter`), 2026-11-01, NOT complete**

- Done: category PARTNER (specs, seeder `partner-` prefix, `partner-info` DE/EN, admin tab + modal, 10 locales); `PartnerAudienceAdapter` (one recipient entry, To: = all partners sorted company/last/first name, NO_EMAIL + DUPLICATE skips, PARTNER templates only, 5 event variables, Reply-To `partner@{forwarding domain}`, 400 above 50 addresses because SES accepts at most 50 recipients per message; this guard is an addition to the story); composer pseudo-template mechanism (`ComposerPseudoTemplate`) with the meeting invite listed first and preselected, rendering `PartnerMeetingsCompose` in place of preview/footer with the fixed-text hint; Partners opens `MailComposer`.
- Tests: adapter unit 8, seeder 1, composer IT 2 (preview byte-equal to record, one row per address, test send to organizers only); Vitest composer 2 + container + admin; full Vitest 5542 passed / 0 failed; EMS full suite 2238 passed / 0 failed; checkstyle, tsc, eslint, prettier clean.
- OPEN, blocked on the hotfix: `PartnerAudienceAdapter.deliver` still calls `sendHtmlEmailSync` with a comma-joined To:, which fails in production exactly like the invite. After rebasing onto the hotfix switch it to `sendGroupHtmlEmailSync(MailContext, List<String> to, ...)`; the ComposeRecipient then needs the address list (key) rather than only the joined string.
- OPEN: Task 12 (local-dev preview/test/send check, Playwright extension of `event-mail-composer.spec.ts`).

**Part A (branch `feature/16-9a-partner-mail-record`), 2026-11-01**

- `MailContext.reference` + `withReference`; every copy method carries it (MailContextTest 7/7).
- V137 `mail_message.reference` (partial index on `(event_code, reference)`). Head was V136; 16.2/16.5 not yet merged, so this story took V137. Re-check if one of them lands first.
- `JpaOutboundMailRecorder.splitAddresses`: comma-separated To: → one TO row each, trimmed, empties dropped; count = addresses. Single address unchanged.
- `InternalMailController` `POST /internal/mails` (202): PARTNERS only, subject ≤ 1000, body ≤ 2 MB, ≤ 500 addresses, else 400. EMS production chain permits `/internal/**` (there is ONE `!test` chain in EMS `SecurityConfig`, not two: line 190 is the JWT decoder). Covered by `ch.batbern.securityprobe.events.InternalPathSecurityTest` against the real chain (RED shown by removing the permit). It lives outside `ch.batbern.events` because its catch-all probe controller was otherwise component-scanned and tripped `EndpointAuthorizationGuardTest`.
- List API: `reference` query filter + `reference` on `MailMessageSummary` (spec, generated types).
- PCS `HttpOutboundMailRecorder` (own RestTemplate 2 s/5 s, no JWT, never throws, one WARN with reference/group/status-or-exception, no addresses/body). `PartnerInviteEmailService` uses the context-first overload; sender username flows from `PartnerMeetingController` through `sendInvite`/`deleteMeeting`.
- Frontend: `MeetingRsvpSummary` (RSVP line on invite rows, shares `['meetingRsvps', id, 0]` with the RSVP panel; no answer = recipients − answers), `MailSentList` `audience`/`reference` props (chips hidden when scoped), "Mails of this meeting" in `MeetingDetailPanel`. i18n in 10 locales.
- Tests: shared-kernel + EMS full suite 2693 passed / 0 failed; PCS full suite 225 / 0; Vitest full 5539 passed / 0 failed; checkstyle, eslint (0 warnings), tsc, prettier clean.
- Local dev (all services, 2026-11-01): invite on BATbern63 → one captured mail, 20 addresses on To: + ICS; EMS `BATbern63-13` PARTNERS/partner-meeting-invite/sender batbern.organizer/reference, 20 recipient rows. EMS stopped → invite still sent (202, captured), one WARN `cause=ResourceAccessException`, no record. Delete → `partner-meeting-cancellation` recorded with 20 rows. Gateway: `/internal/mails` 401 anonymous, 403 with token. Screenshots: RSVP line on the mail page and "Mails of this meeting" in the panel.
- **Correction (2026-11-01, later the same day):** the local-dev check above proves capture and record, NOT delivery. Local dev has no SesClient, so `EmailService` never reaches `EmailAddresses.toDeliveryForm(to)` (`EmailService.java:369`), which rejects a comma-joined To: as one invalid address. Since #1104 every partner meeting invite and cancellation fails in production: CloudWatch (PCS, last 90 days) shows exactly one invite, BATbern60 on 2026-10-30 18:59, "Failed to send partner meeting invite: Cannot deliver to email address: it is not a valid address". A separate session fixes it on `fix/partner-invite-multi-recipient` (commit 3af00710: `sendGroupHtmlEmailWithAttachments` / `sendGroupHtmlEmailSync` with `List<String> to`, recorded ONCE with `to` = comma-joined deliverable addresses, which matches the split here). **16.9a must be rebased onto develop after that hotfix merges, keeping the List<String> call and adding the MailContext; no PR before.**
- Local-dev finding (pre-existing, not changed): the anonymized DB gives partners `@example.invalid` addresses, and `EmailService.assertSendable` refuses the whole one-To:-line invite, so locally nothing is sent and nothing recorded. For the check, 16 addresses were swapped to SES-simulator addresses in the local DB and restored afterwards (backup tables dropped).

### File List

Part A:
- shared-kernel/src/main/java/ch/batbern/shared/mail/MailContext.java
- shared-kernel/src/test/java/ch/batbern/shared/mail/MailContextTest.java
- docs/api/event-mail-api.openapi.yml
- services/event-management-service/src/main/resources/db/migration/V137__mail_message_reference.sql
- services/event-management-service/src/main/java/ch/batbern/events/domain/MailMessage.java
- services/event-management-service/src/main/java/ch/batbern/events/mailcenter/JpaOutboundMailRecorder.java
- services/event-management-service/src/main/java/ch/batbern/events/mailcenter/MailMessageQueryService.java
- services/event-management-service/src/main/java/ch/batbern/events/mailcenter/EventMailController.java
- services/event-management-service/src/main/java/ch/batbern/events/mailcenter/InternalMailController.java
- services/event-management-service/src/main/java/ch/batbern/events/config/SecurityConfig.java
- services/event-management-service/src/test/java/ch/batbern/events/mailcenter/InternalMailControllerIntegrationTest.java
- services/event-management-service/src/test/java/ch/batbern/events/mailcenter/MailRecordingIntegrationTest.java
- services/event-management-service/src/test/java/ch/batbern/events/mailcenter/EventMailControllerIntegrationTest.java
- services/event-management-service/src/test/java/ch/batbern/securityprobe/events/InternalPathSecurityTest.java
- services/partner-coordination-service/src/main/java/ch/batbern/partners/mail/HttpOutboundMailRecorder.java
- services/partner-coordination-service/src/main/java/ch/batbern/partners/service/PartnerInviteEmailService.java
- services/partner-coordination-service/src/main/java/ch/batbern/partners/service/PartnerMeetingService.java
- services/partner-coordination-service/src/main/java/ch/batbern/partners/controller/PartnerMeetingController.java
- services/partner-coordination-service/src/test/java/ch/batbern/partners/mail/HttpOutboundMailRecorderTest.java
- services/partner-coordination-service/src/test/java/ch/batbern/partners/service/PartnerInviteEmailServiceTest.java
- services/partner-coordination-service/src/test/java/ch/batbern/partners/controller/PartnerMeetingControllerIntegrationTest.java
- web-frontend/src/types/generated/event-mail-api.types.ts
- web-frontend/src/services/mailMessageService.ts
- web-frontend/src/components/organizer/EventPage/communications/MailSentList.tsx
- web-frontend/src/components/organizer/EventPage/communications/MeetingRsvpSummary.tsx
- web-frontend/src/components/organizer/EventPage/communications/partnerMeetingReference.ts
- web-frontend/src/components/organizer/EventPage/communications/__tests__/MailSentList.test.tsx
- web-frontend/src/components/organizer/MeetingDetailPanel.tsx
- web-frontend/src/components/organizer/MeetingDetailPanel.test.tsx
- web-frontend/public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/{events,partners}.json
- docs/architecture/06-backend-architecture.md
- docs/user-guide/features/event-mail.md

Part B (branch `feature/16-9b-partner-adapter`, stacked on Part A):
- docs/api/event-mail-api.openapi.yml (MailComposeRequest.audience PARTNERS)
- docs/api/event-newsletter-api.openapi.yml (category PARTNER, 3 enums)
- services/event-management-service/src/main/java/ch/batbern/events/mailcenter/compose/PartnerAudienceAdapter.java
- services/event-management-service/src/main/java/ch/batbern/events/service/EmailTemplateSeedService.java
- services/event-management-service/src/main/resources/email-templates/partner-info-de.html
- services/event-management-service/src/main/resources/email-templates/partner-info-en.html
- services/event-management-service/src/test/java/ch/batbern/events/mailcenter/compose/PartnerAudienceAdapterTest.java
- services/event-management-service/src/test/java/ch/batbern/events/mailcenter/MailComposerSendIntegrationTest.java
- services/event-management-service/src/test/java/ch/batbern/events/service/EmailTemplateSeedServiceTest.java
- web-frontend/src/types/generated/{event-mail-api,event-newsletter-api}.types.ts
- web-frontend/src/components/organizer/Admin/{EmailTemplatesTab,EmailTemplatesTab.test,EmailTemplateEditModal}.tsx
- web-frontend/src/components/organizer/EventPage/EventCommunicationsContainer.tsx
- web-frontend/src/components/organizer/EventPage/__tests__/EventCommunicationsContainer.test.tsx
- web-frontend/src/components/organizer/EventPage/communications/composer/{MailComposer,types}.tsx/.ts
- web-frontend/src/components/organizer/EventPage/communications/composer/audiences/{index,partners}.ts
- web-frontend/src/components/organizer/EventPage/communications/composer/__tests__/MailComposer.test.tsx
- web-frontend/public/locales/*/common.json (emailTemplates.categories.PARTNER), */events.json (composer.partners.*)

## Change Log

- 2026-11-01: Story drafted from Epic 16, grounded in code at 15a13959. Two parts (record, adapter).
- 2026-11-01: Part B implemented except the delivery call (waits for the multi-recipient hotfix) and Task 12 verification.
- 2026-11-02: Rebased onto #1176 + #1177; partner adapter on the pipeline; every partner mail per recipient (owner decision); status review.
- 2026-11-01: Part A implemented (record partner meeting invites and cancellations, `/internal/mails`, reference filter, RSVP line, meeting panel mails).
