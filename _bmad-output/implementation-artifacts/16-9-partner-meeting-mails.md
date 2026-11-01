# Story 16.9: Partner meeting mails in the mail center

Status: ready-for-dev

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

- [ ] **Task 1 — Shared kernel** (AC: 1, 5)
  - [ ] `MailContext` gains a nullable `reference` (what the mail is about, e.g. `partner-meeting:{meetingId}`) and `withReference(String)`. Only `MailContext.java` calls the canonical constructor (3 places, verified); update them and `MailContextTest`.
- [ ] **Task 2 — Migration** (AC: 1, 5)
  - [ ] EMS `V{next}__mail_message_reference.sql`: `ALTER TABLE mail_message ADD COLUMN reference VARCHAR(100)`; index `(event_code, reference)`. **Re-check the head before numbering** (V136 at drafting; 16.2 and 16.5 also add migrations; whichever ships first takes the number).
  - [ ] Cross-service reference by value, no FK (ADR-003). The meeting id is the partner meeting's public id (`/partner-meetings/{meetingId}`), so it may appear in the API.
- [ ] **Task 3 — RED: tests first** (AC: 1–5, 10)
  - [ ] EMS `InternalMailControllerIntegrationTest` (`AbstractIntegrationTest`): records a PARTNERS mail with 5 To addresses → 1 `mail_message` (with `reference`), 5 `mail_recipient` rows, `recipient_count = 5`; non-PARTNERS audience → 400; oversized body → 400; reachable without a token (assert the filter chain lets it through).
  - [ ] EMS `JpaOutboundMailRecorder` test: a comma-separated To is split, trimmed, empty parts dropped; a single address unchanged (no regression for the other audiences).
  - [ ] EMS `MailMessageQueryService` test: `reference` filter; summary exposes `reference`.
  - [ ] Partner `HttpOutboundMailRecorderTest` (`MockRestServiceServer`): posts the payload without `Authorization`; 503, timeout and connection refused → no exception, one WARN (AC2).
  - [ ] Partner `PartnerInviteEmailServiceTest`: invite and cancellation pass a `MailContext` with audience PARTNERS, the template keys, the event code, the sender and `partner-meeting:{id}`; the To: line is unchanged (AC3).
- [ ] **Task 4 — EMS internal endpoint** (AC: 3, 4)
  - [ ] `mailcenter/InternalMailController`: `@PostMapping("/internal/mails")`, plain request record (`InternalMailRequest`), `@Valid`, limits: subject ≤ 1000, body ≤ 2 MB, ≤ 500 addresses. Maps to `MailContext` + `OutboundMail`, calls `JpaOutboundMailRecorder.record`. Returns 202.
  - [ ] `SecurityConfig` (EMS): `.requestMatchers("/internal/**").permitAll()` in **every** filter chain (two `@Profile("!test")` chains at `SecurityConfig.java:65` and `:190`; check the test-profile config too). Comment as in CUMS `SecurityConfig.java:79-81`. `EndpointAuthorizationGuard` excludes `/internal/` (`shared-kernel/src/testFixtures/.../EndpointAuthorizationGuard.java:36`); no `@PreAuthorize` needed.
  - [ ] `JpaOutboundMailRecorder`: split a multi-address `to` into rows; count = addresses; write `reference`.
  - [ ] `MailMessageQueryService` + spec: `reference` on `MailMessageSummary`; `reference` query filter on `GET /events/{code}/mails`. Regenerate types.
- [ ] **Task 5 — Partner-coordination recorder** (AC: 1, 2, 3)
  - [ ] `partners/mail/HttpOutboundMailRecorder implements OutboundMailRecorder` (shared kernel picks it up: `EmailService` uses the recorder when a bean exists, 16.1a). `RestTemplate` with connect 2 s / read 5 s, base URL `event-management-service.base-url` (already in `application.yml:77-78`). No JWT: it runs on the `@Async` send thread with no `SecurityContext`, same as EMS `PartnerMeetingRsvpClientImpl`. Catch everything, log WARN, never rethrow.
  - [ ] `PartnerInviteEmailService.sendCalendarInvites` / `sendCancellationNotice`: use the context-first `sendHtmlEmailWithAttachments(MailContext, to, cc, subject, html, attachments)` (`EmailService.java:322`). Context: `MailContext.of(MailTrigger.organizer(username), eventCode, PARTNERS, "partner-meeting-invite" | "partner-meeting-cancellation").withReference("partner-meeting:" + meetingId)`.
  - [ ] Pass the organizer's username from `PartnerMeetingController` → `PartnerMeetingService.sendInvite` / `deleteMeeting` (today they do not receive it, `PartnerMeetingService.java:137`, `:189`).
- [ ] **Task 6 — Frontend: list + RSVP + meeting panel** (AC: 1, 5)
  - [ ] `MailSentList.tsx`: for a PARTNERS row with a `partner-meeting:` reference, show an RSVP summary chip, loaded through the existing partner meetings service (`getRsvps(meetingId)`, `services/api/partnerMeetingsApi.ts:101`) only for rows on screen; reuse the counting from `PartnerMeetingRsvpPanel.tsx`.
  - [ ] `MeetingDetailPanel.tsx`: a "Mails" section rendering `MailSentList` with `audience=PARTNERS`, `reference=partner-meeting:{id}` (add both as props).
  - [ ] Vitest for both; i18n keys in 10 locales.
- [ ] **Task 7 — Verify Part A**
  - [ ] EMS and partner-coordination suites to files.
  - [ ] Local dev (`make dev-native-up`, all services): create a meeting on a throwaway event, send the invite, confirm `/dev/emails` has one mail with all addresses in To and EMS has the record with split recipients and the RSVP chip; stop EMS, send again, confirm the invite still goes out and the WARN line; delete the meeting → cancellation recorded.
  - [ ] Docs: `docs/architecture/06-backend-architecture.md` (internal mail endpoint, partner recorder), user guide.

### Part B — plain partner mail (PR 2, `feature/16-9b-partner-adapter`)

- [ ] **Task 8 — Category PARTNER** (AC: 9)
  - [ ] Spec enums in `docs/api/event-newsletter-api.openapi.yml` (list filter :51-61, `EmailTemplateResponse.category` :1152-1163, `CreateEmailTemplateRequest.category` :1208-1219): add `PARTNER`. Regenerate types.
  - [ ] `EmailTemplateSeedService.deriveCategory`: `partner-` prefix → `PARTNER` (before the `LAYOUT` fallback, :199; see V120 for what happens otherwise).
  - [ ] Classpath templates `email-templates/partner-info-de.html` and `partner-info-en.html` (subject in the existing subject convention of the seeder; content only, layout `batbern-default`). Wording: neutral info mail to all partners, generic salutation ("Liebe Partnerinnen und Partner" / "Dear partners"), no per-person placeholder. Owner reviews the DE text.
  - [ ] Admin: `EmailTemplatesTab.tsx` (:64-94) and `EmailTemplateEditModal.tsx` (:189-195) category lists + `emailTemplates.categories.PARTNER` in 10 locales.
  - [ ] No migration: `email_templates.category` has no check constraint (V62).
- [ ] **Task 9 — RED: adapter tests** (AC: 7, 8)
  - [ ] `PartnerAudienceAdapterTest`: recipients from stubbed `UserApiClient.getPartnerUsernames()` (:129) + `getUserByUsername`; ONE recipient entry whose To: holds all addresses in order; skipped without email; only PARTNER templates allowed; no per-person variables; the record has one `mail_recipient` per address.
  - [ ] Extend 16.8's byte-equal and test-send integration tests with PARTNERS.
- [ ] **Task 10 — `PartnerAudienceAdapter`** (AC: 7, 8)
  - [ ] Categories `PARTNER`; `personalized = false`; one recipient entry, all partner addresses on To: (as `VenueAudienceAdapter`, but To only, no Cc); variables (no per-person ones) `eventTitle`, `eventDate`, `eventNumber`, `currentYear`, `logoUrl`; Reply-To `partner@{forwarding domain}` (alias exists, `ForwardingAlias` :61-62); one `MailContext.of(trigger, eventCode, PARTNERS, templateKey)` per send.
  - [ ] Add `PARTNERS` to `MailComposeRequest.audience` in the spec.
- [ ] **Task 11 — Frontend** (AC: 6, 7)
  - [ ] `composer/audiences/partners.tsx`: template list = pseudo-entry "Partner meeting invite with calendar" + PARTNER templates. The pseudo-entry renders the body of today's `PartnerMeetingsCompose` (meeting picker + `PartnerMeetingInviteAction`) in place of preview and footer, with the hint.
  - [ ] `EventCommunicationsContainer.tsx`: Partners opens `MailComposer` (16.8 AC15 kept `PartnerMeetingsCompose` until now); `PartnerMeetingsCompose.tsx` becomes the pseudo-entry's body or is folded into it.
  - [ ] Vitest (MSW): both paths; invite path keeps its confirmation and test ids.
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

### Debug Log References

### Completion Notes List

### File List

## Change Log

- 2026-11-01: Story drafted from Epic 16, grounded in code at 15a13959. Two parts (record, adapter).
