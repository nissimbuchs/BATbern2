# Story 16.2b: Conversations for every alias, registrant replies and mail without an event

Status: ready-for-dev

## Story

As an **organizer**,
I want **human mail to `info@`, `events@`, `support@` and every `batbern{N}-*@` alias, and human replies to registrant and newsletter mails, to become conversations like the speakers' do**,
so that **every question that needs a person is in one place, with or without an event, before forwarding to private inboxes is switched off**.

> Source: `docs/prd/epic-16-mail-center.md` → **Story 16.2**, second half of the owner's sequence (#3 step 5: "16.2 for all aliases"), and decision 5 ("Registrant replies stay with #1160 option 1 until 16.2 covers all aliases").
> Depends on **16.2a** (model, parser, classifier, threader, counts, UI section) and **16.6** (answers work for any conversation). The global list that shows conversations **without an event** is **16.7**; until 16.7 ships, such conversations are stored and forwarded as today, but not listed in the UI.
> Forwarding stays ON. 16.6's switch group `contact` may only be turned on after this story and 16.7.

## Acceptance Criteria

1. **AC1 (contact aliases)** — GIVEN a human mail to `info@`, `events@` or `support@` (open to anyone, `ForwardingAlias.openToAnyone`), WHEN it arrives, THEN it is stored and threaded per 16.2a AC3. Without a token or Message-ID match it opens a conversation **without an event** (`event_code` null), audience `PUBLIC`, unassigned, status `OPEN`.
2. **AC2 (registrant and newsletter replies)** — GIVEN a human reply at `replies@` that the keyword router does not act on (`RouteResult.UNRECOGNIZED`), WHEN its `In-Reply-To`/`References` names a recorded registrant or newsletter mail, THEN it opens or joins a conversation on that mail's event, audience `REGISTRANTS` or `NEWSLETTER`, unassigned. Without a match it becomes a conversation without an event.
3. **AC3 (subject fallback, owner decision 2026-11-01)** — When steps 1–3 of the threading order find nothing and the subject contains exactly one event code of an existing event (`BATbern\d+`, same pattern as `InboundEmailRouter.EVENT_CODE_PATTERN`), the conversation is put on that event. Counted separately (`ThreadMatchedBy=subject`) so the owner can judge it. If the owner declines, drop this AC and the step.
4. **AC4 (organizer broadcasts are sends, not conversations)** — Mail an organizer sends to `batbern{N}-participants@`, the deprecated `batbern{N}@` or `partner@` is recorded like 16.2a AC5: one `mail_message` direction `OUT`, audience `REGISTRANTS` (event aliases, on that event) or `PARTNERS` (no event), content source `OWN_TEXT`, sender = the organizer. Mail to `ok@` (organizers to organizers) is neither recorded nor opened.
5. **AC5 (no conversation for automatic mail)** — The 16.2a classifier applies unchanged: out-of-office, bounces, calendar replies and keyword actions are counted, never opened. Partner-meeting RSVPs routed by `InboundEmailRouter.routeIcsReply` are counted as calendar kinds; their event is set when 16.9 makes the meeting's event known to EMS, otherwise none.
6. **AC6 (organizer senders)** — Mail from an organizer address to a contact alias (e.g. an organizer forwarding something to `info@`) opens a conversation like any other mail; the organizer is the counterpart. Exception, as in 16.2a AC6: organizer mail to `-moderator@` opens nothing.
7. **AC7 (Message-ID on raw sends)** — Every mail EMS sends through `SendRawEmail` with a `MailContext` (registration mails with ICS, newsletter) carries an explicit `Message-ID: <{uuid}@{domain}>` that is stored per recipient, so replies to them can be matched whatever SES does with caller-supplied ids.
8. **AC8 (audience `PUBLIC`)** — New audience value `PUBLIC` (anyone who is not addressed as a group: contact-alias writers) in the shared-kernel `MailContext.Audience`, the `mail_message` and `mail_conversation` CHECK constraints, and `MailAudience` in the spec. The "What went out" filter chips do not offer it (no outbound mail has it).
9. **AC9 (event page)** — Conversations from AC2/AC3 appear in that event's "Needs an answer" exactly like speaker conversations; the row's "via" names the path (`Reply to a registrant mail`, `Reply to the newsletter`, `info@`).
10. **AC10 (retention)** — No-event conversations: last activity + 12 months (16.2a AC14 rule, already implemented); verify with an integration test for an `info@` conversation.
11. **AC11** — Synthetic fixtures only; no real mail in tests; verified on local dev with the 16.2a simulator. New UI keys in all 10 locales.

## Tasks / Subtasks

- [ ] **Task 1 — Widen the ingestion gate** (AC: 1, 2, 5, 6; TDD first)
  - [ ] RED: extend `ConversationThreadingIntegrationTest` (16.2a): `info@` without match → no-event conversation; `support@` with In-Reply-To of a registrant mail → that event; `replies@` UNRECOGNIZED reply to a newsletter → event, audience `NEWSLETTER`; organizer to `info@` → conversation; organizer to `-moderator@` → none; OOO to `info@` → counted only.
  - [ ] `InboundMailCenter`: replace the 16.2a speaker-only gate by: open for every `HUMAN` mail on the alias path whose addressed aliases include `ORGANIZERS` (except `ok@`), `SUPPORT` or `EVENT_MODERATOR`, and for every `UNRECOGNIZED` human mail on the replies path. Audience from the matched outbound mail, else `SPEAKERS` for `-moderator@`, else `PUBLIC`.
- [ ] **Task 2 — Subject fallback** (AC: 3) — step 4 in `ConversationThreader`, after the alias step; event must exist (`EventRepository.existsByEventCode`); several distinct codes → no event. Unit tests incl. `Re: AW: BATbern60 …`, two codes, unknown code.
- [ ] **Task 3 — Broadcasts recorded** (AC: 4)
  - [ ] `EmailForwardingService`: the private-copy path (`sendIndividually`, line 399) for `EVENT_PARTICIPANTS`, `EVENT_PARTICIPANTS_DEPRECATED` and `PARTNERS` aliases records one `mail_message` per inbound mail with one `mail_recipient` per copy (SES id per copy), through the `sendRawMessage(MailContext, …)` overload from 16.2a. The other aliases' copies (to organizers) stay unrecorded: they are the forwarded inbound mail, already stored as `IN`.
  - [ ] Test: participants broadcast → one OUT row with N recipients on the event; partner@ → PARTNERS, no event; ok@ → nothing.
- [ ] **Task 4 — Explicit Message-ID on raw sends** (AC: 7)
  - [ ] Shared kernel `EmailService.sendHtmlEmailWithAttachments` / `sendHtmlEmailSyncWithAttachments` (lines 295–531): when a `MailContext` is given, set `Message-ID: <{uuid}@{domain of fromEmail}>` with a `MimeMessage` subclass whose `updateMessageID()` keeps it, and pass it to the recorder. `OutboundMail` gets an optional `messageIdHeader`; `JpaOutboundMailRecorder` writes it to `mail_recipient.message_id_header` (column from 16.2a). The 16.9 HTTP recorder ignores the new field if it does not need it; coordinate the record shape with the 16.9 author (`OutboundMail` is the SPI payload).
  - [ ] Test: header present in the raw bytes, equal to the recorded value; no context → unchanged behaviour.
- [ ] **Task 5 — Audience `PUBLIC`** (AC: 8) — migration `V{next}__mail_audience_public.sql` (re-check the head; 16.8/16.9 may have added migrations): drop and re-add `chk_mail_message_audience` and the conversation's audience CHECK with `PUBLIC`. Shared-kernel enum, spec enum, regenerate frontend types; `MailSentList` `FILTERS` (line 30) unchanged.
- [ ] **Task 6 — UI wording** (AC: 9, 11) — "via" labels for the new paths in `ConversationRow`; i18n keys in all 10 locales. No new component.
- [ ] **Task 7 — Local dev check** (AC: 11) — with the 16.2a simulator: `info@` mail without match, registrant reply (capture a local registration confirmation, simulate the reply with its capture id), participants broadcast. Rows deleted afterwards; results in Completion Notes.
- [ ] **Task 8 — Docs** — user guide `event-mail.md` (which mail becomes a conversation), `06-backend-architecture.md` (gate, subject fallback, Message-ID on raw sends), `08-operations-security.md` (no change to retention; confirm the text still matches).

### Suggested PR split

One PR (backend + small UI wording). If Task 4 (shared kernel) grows, ship it first on its own: it changes headers of registration and newsletter mail in production, which is worth a separate deploy and a look at the first replies.

## Dev Notes

### Aliases in code (verified, `ForwardingAlias.java`)

| Local part | Kind | Who may send (line 106–112) | 16.2b treatment |
|---|---|---|---|
| `info`, `events` | ORGANIZERS | anyone | conversation (no event unless matched) |
| `ok` | ORGANIZERS | organizers only | nothing (internal) |
| `support` | SUPPORT | anyone | conversation |
| `partner` | PARTNERS | organizers only | outbound record, audience PARTNERS |
| `batbern{N}-moderator` | EVENT_MODERATOR | anyone | conversation (16.2a) |
| `batbern{N}-speaker` | EVENT_SPEAKERS | organizers only | outbound record (16.2a) |
| `batbern{N}-participants`, `batbern{N}` | EVENT_PARTICIPANTS(_DEPRECATED) | organizers only | outbound record, audience REGISTRANTS |
| `noreply`, `no-reply`, `calendar` | NO_REPLY | (dropped) | counted only (calendar kinds) |

**Contradiction with the epic, reported:** 16.9 says "mail to `partner@` … belongs to the meeting's event" and lists it under inbound. In code `partner@` is an organizer-only broadcast to all partner users (`ForwardingAlias.java:61–62`, `openToAnyone` false, `ForwardingDirectory.partnerEmails`). A partner writing to `partner@` is `REJECTED`. Partners' answers to such a broadcast go to the organizer who wrote it (Reply-To = original sender, `ForwardedMessageRewriter`), outside BATbern. So `partner@` is a send in this story, not an inbox.

### Registrant replies today (decision 5)

- Registrant mails carry the global Reply-To `replies@batbern.ch` (`application.yml:113`). The router acts on short keywords (`InboundEmailRouter.java:81–146`); everything else is `UNRECOGNIZED` and forwarded to all organizers by `forwardUnrecognizedReply` (`EmailForwardingService.java:233`). This story adds storage next to that forward; the forward itself stays until 16.6's `replies` group is switched on.
- Registration confirmations go through `SendRawEmail` (ICS attachment; `RegistrationEmailService` uses the `WithAttachments` variants), and so does the newsletter (`NewsletterEmailService`). Their `Message-ID` is written by JavaMail; whether SES keeps it is unverified (16.2a Dev Notes, "Message-ID trap"). AC7 makes the header ours and known, so matching no longer depends on SES.

### Volume (epic, measured 2026-10-30)

About 6 human mails a week across all inbound, about 51 alias forwards in 6 months. The no-event list stays short; no paging tricks needed beyond the standard `page`/`limit`.

### References

- `docs/prd/epic-16-mail-center.md` (Story 16.2, decision 5, Story 16.9 inbound note)
- `_bmad-output/implementation-artifacts/16-2a-speaker-conversations.md`, `16-6-answer-from-mail-center.md`, `16-7-global-mail-navigation.md`
- `services/event-management-service/src/main/java/ch/batbern/events/forwarding/{ForwardingAlias,EmailForwardingService,ForwardingDirectory,ForwardedMessageRewriter}.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/{InboundEmailRouter,RegistrationEmailService,NewsletterEmailService}.java`
- `shared-kernel/src/main/java/ch/batbern/shared/service/EmailService.java`, `shared-kernel/src/main/java/ch/batbern/shared/mail/{MailContext,OutboundMail}.java`
- `services/event-management-service/src/main/resources/db/migration/V135__create_mail_message.sql`

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List

## Change Log

- 2026-11-01: Story drafted from Epic 16 Story 16.2, all-alias part (split 16.2a/16.2b per owner sequence #3).
