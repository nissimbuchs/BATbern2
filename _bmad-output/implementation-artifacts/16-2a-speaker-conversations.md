# Story 16.2a: Speaker replies become conversations

Status: ready-for-dev

## Story

As an **organizer**,
I want **every human mail a speaker writes to the event's moderator alias, or in reply to a speaker mail, to land in the event's "Needs an answer" list with a status and an assignee**,
so that **the whole team sees what speakers asked and who is answering, instead of the question sitting in one private inbox**.

> Source: `docs/prd/epic-16-mail-center.md` → **Story 16.2**, split by the owner's sequence ("Decisions taken 2026-10-30" #3: "16.2 + 16.6 for speakers only", then "16.2 for all aliases"). **16.2a** = speakers, **16.2b** = every other alias, no-event mail and registrant replies (`16-2b-all-alias-conversations.md`).
> Depends on 16.0 Part A (moderator Reply-To), 16.1a (`mail_message`), 16.1b (delivery listener), 16.3 (mail page). Followed by **16.6** (answer from the mail center), which builds on the conversation model introduced here.
> Alias forwarding to private inboxes stays ON (owner decision 2). Nothing in this story changes who receives a forwarded copy.
> **UX reference:** `docs/ux/mail-center-spec.md` (section 1 "Braucht eine Antwort"), `docs/ux/mail-center-prototype.html` (rows with "Ich übernehme", done items greyed), `docs/ux/mail-center-layouts.html` variant A (inline expand), `docs/ux/mail-center-mobile.html` (full-screen dialog).

## Acceptance Criteria

**Capture and threading**

1. **AC1 (speaker reply → conversation)** — GIVEN event BATbern{N} with lead organizer U (`Event.organizerUsername`), WHEN a speaker's human mail arrives at `batbern{N}-moderator@`, THEN one `mail_message` (direction `IN`) is stored and attached to a `mail_conversation` with event `BATbern{N}`, audience `SPEAKERS`, status `OPEN`, assignee U. If the event has no `organizerUsername`, the conversation is unassigned.
2. **AC2 (reply to a speaker mail at `replies@`)** — GIVEN an outbound mail recorded by 16.1a with audience `SPEAKERS` (e.g. the Q&A digest, which still uses `replies@`), WHEN a human answer arrives at `replies@` whose `In-Reply-To` or `References` names that mail, THEN it opens (or joins) a conversation on that mail's event, as in AC1. The keyword router runs first, unchanged: a keyword that acts (16.0 AC12) is counted, not opened.
3. **AC3 (threading order)** — The conversation is found in this order, first hit wins:
   1. the per-conversation token in `replies+{token}@` (the column is created here; tokens are handed out by 16.6);
   2. `In-Reply-To`, then `References` (newest first), matched against stored outbound and inbound Message-IDs (see Dev Notes, "Message-ID trap");
   3. the alias's event (`batbern{N}-*@`);
   4. otherwise no event (16.2b; in this story such mail is not opened).
   Within the event found by steps 2–3, a mail joins the conversation of the same counterpart address that is `OPEN` or `WAITING`; otherwise a new conversation is opened. A new inbound mail on a `WAITING` or `DONE` conversation sets it to `OPEN`.
4. **AC4 (idempotent)** — The same mail delivered twice (SQS redelivery, or one mail addressed to two aliases and therefore stored by SES twice) is stored once. Key: the mail's own `Message-ID` header, else the S3 key.
5. **AC5 (`-speaker@` broadcast is recorded as sent)** — GIVEN an organizer mails `batbern{N}-speaker@`, WHEN the forwarder sends the broadcast, THEN one `mail_message` (direction `OUT`, audience `SPEAKERS`, content source `OWN_TEXT`, sender = the organizer's username) appears in that event's "What went out", with the speakers as recipients and the SES Message-ID per recipient. It is not a conversation. Speaker replies to it thread per AC3.
6. **AC6 (organizers are not counterparts)** — Mail from an organizer address (primary or additional email) to `-moderator@` is forwarded as today and opens no conversation.

**Classification ("Handled automatically")**

7. **AC7 (counted, not opened)** — Every inbound mail on both paths (`replies@` and aliases) is classified before anything is opened. Out-of-office replies (`Auto-Submitted` not `no`, `Precedence: auto_reply`, `X-Autoreply`, `X-Autorespond`, and subject patterns listed in Dev Notes), bounces (`MailClassifier.isBounce`), iMIP calendar replies, keyword actions of `InboundEmailRouter` (unsubscribe, deregistration link, confirmation) and loops are counted per event in a new table without personal data, never opened and never stored with a body.
8. **AC8 (counts on the page)** — `GET /events/{eventCode}/mails/counts` returns, besides `failedDeliveries`: `outOfOffice`, `bounces`, `calendarAccepts`, `calendarDeclines`, `keywordAccepts`, `keywordCancels`, `unsubscribes`. "Handled automatically" shows each non-zero count on its own line and stays collapsed by default. An event is attributed by In-Reply-To (outbound mail → its event), by the registration calendar UID (`reg-{calendarUid}`) for calendar replies, or by the subject's event code for keyword actions; otherwise the count has no event and is not shown on an event page.

**UI: "Needs an answer"**

9. **AC9 (section)** — The event's Communications page shows "Needs an answer" above "What went out": one row per conversation with a status dot, counterpart name, via (`batbern{N}-moderator@` / reply to a mail), date, subject and the assignee's avatar. `OPEN` and `WAITING` first, newest activity first; `DONE` below them, greyed. The section's count badge counts `OPEN` only. The "+ New mail" button moves into this section's header (`mail-center-spec.md`).
10. **AC10 (empty state)** — GIVEN no conversations for the event, THEN the section shows one line ("Nothing open.") and no table.
11. **AC11 (read)** — A click opens the conversation inline below its row (desktop) or as a full-screen dialog (mobile, `useBreakpoints().isMobile`): every message, oldest first, with sender, time, text and attachment names. Bodies are shown sanitized in the same sandboxed frame as "What went out". One conversation open at a time; a second click closes it. `?conversation={key}` in the URL opens it directly (used by 16.6's notification links).
12. **AC12 (status and assignee)** — An organizer can set status (Open, Waiting, Done) and assignee (any organizer, or none), and "I'll take it" assigns themselves in one click on an unassigned row. The change is stored server side and visible to every organizer on the next load. No mail is sent in this story (the assignment notification is 16.6 AC3).

**API, retention, safety**

13. **AC13 (API)** — New operations in `docs/api/event-mail-api.openapi.yml` (spec first, ADR-013), all `@PreAuthorize("hasRole('ORGANIZER')")`, `Cache-Control: no-store`:
    - `GET /events/{eventCode}/conversations` (paged, filter `status`);
    - `GET /conversations/{conversationKey}` (the conversation and its messages as `MailMessageDetail`);
    - `PATCH /conversations/{conversationKey}` (`status`, `assigneeUsername`; unknown assignee → 400; unknown key → 404).
    Messages reuse the Mail schema (`MailMessageSummary` gains optional `direction`, `fromAddress`, `fromName`, `eventCode`, `conversationKey`, `attachments`). `GET /events/{eventCode}/mails` stays outbound-only (it filters `direction = 'OUT'` today). Frontend types regenerated.
14. **AC14 (retention, owner decision 1)** — The text needed for display is stored in the database at receipt, so it outlives the 7-day S3 lifecycle. Conversations and their messages are deleted at event date + 12 months, conversations without an event at last activity + 12 months, by the existing `MailMessageRetentionScheduler` (extended, not a second job). The new count table follows the same rule.
15. **AC15 (PII, mail safety)** — Fixtures synthetic only (`@example.com`, SES simulator). No test sends real mail. Inbound processing is verified on local dev through the dev simulator (Task 7).
16. **AC16 (i18n)** — New UI keys in all 10 locales, EN and DE first-class.

## Tasks / Subtasks

- [ ] **Task 0 — Settle the Message-ID format before coding** (AC: 3) — no code
  - [ ] In an organizer's **own** mailbox (no production data access), open the raw source of one speaker mail sent by the platform (sent through `SendEmail`) and one registration confirmation (sent through `SendRawEmail`, it carries an ICS). Note the `Message-ID` header of each and compare with the SES MessageId format we store (`mail_recipient.ses_message_id`). Write both formats into Completion Notes. The matcher (Task 4) handles every outcome; this tells you which branch carries production traffic.

- [ ] **Task 1 — Schema** (AC: 1, 4, 5, 7, 14)
  - [ ] Re-check the EMS migration head first: V136 on 2026-11-01, and 16.8/16.9 may add migrations in parallel. New `V{next}__mail_conversations.sql`:
    - `mail_conversation_seq`; `mail_conversation`: `id` UUID PK, `conversation_key` (`conv-{n}`, unique, ADR-003 public id), `event_code` (nullable), `audience` (same CHECK values as `mail_message`), `subject`, `counterpart_address` (lowercased), `counterpart_name`, `status` (`OPEN|WAITING|DONE`, default `OPEN`), `assignee_username` (nullable), `reply_token` (unique, 16 chars, lowercase base32), `created_at`, `updated_at`, `updated_by`, `last_message_at`, `last_inbound_at`. Indexes: `(event_code, status, last_message_at DESC)`, `(counterpart_address)`.
    - `mail_message` additive columns: `conversation_id` (FK → `mail_conversation` ON DELETE CASCADE), `from_address`, `from_name`, `to_addresses` (text, comma-separated), `message_id_header`, `in_reply_to`, `references_header`, `body_text`, `inbound_path` (`REPLIES|ALIAS`), `alias_local_part`, `attachments` (JSONB: name, contentType, size). Indexes on `conversation_id` and `message_id_header`.
    - `mail_recipient.message_id_header` (nullable, indexed): the header as SES sent it, filled by the 16.1b listener (Task 4).
    - `mail_automatic_event`: `id`, `dedupe_key` (unique), `event_code` (nullable), `kind` (CHECK: `OUT_OF_OFFICE, BOUNCE, CALENDAR_ACCEPTED, CALENDAR_DECLINED, CALENDAR_TENTATIVE, KEYWORD_ACCEPT, KEYWORD_CANCEL, UNSUBSCRIBE, LOOP`; 16.7 adds `SPAM, VIRUS`), `received_at`. No address, no subject, no body.
  - [ ] Entities `MailConversation`, `MailAutomaticEvent`; `MailMessage` gets the new fields. Enum values stored UPPER_CASE, following V135 (not the project-wide lowercase rule; keep the table consistent).
  - [ ] Inbound rows: `direction = 'IN'`, `content_source = 'OWN_TEXT'` (written by a person; keeps the shared-kernel `MailContext.ContentSource` enum and the V135 CHECK unchanged; the UI must not show the "own text" marker for `IN`), `template_key` null, `recipient_count` 0, no `mail_recipient` rows, `send_group_id = 'in_' + first 40 hex of sha256(Message-ID header, else S3 key)` (gives AC4 through the existing unique constraint), `body_html` = sanitized HTML or escaped text, never empty.

- [ ] **Task 2 — Parser and classifier** (AC: 7, TDD first)
  - [ ] RED: `InboundMailParserTest` with synthetic `.eml` fixtures under `src/test/resources/mail/` (reuse `human-reply.eml`, `ooo-reply.eml`; add multipart/alternative, HTML-only, attachment, quoted-printable German umlauts, RFC 2047 subject, `In-Reply-To` + folded `References`).
  - [ ] `mailcenter/inbound/InboundMailParser` → `InboundMail` record: Message-ID, In-Reply-To, References (list, newest last as in the header), From (address lowercased + display name), Reply-To, To/Cc, subject, text (prefer `text/plain`), sanitized HTML (only when there is no text part), attachment metadata, and the classification headers.
  - [ ] HTML sanitizing: add `org.jsoup:jsoup` to EMS and clean with a safelist without `img`, `style`, `script`, links kept as text + href. No remote content may load in the organizer's browser (tracking pixels). If adding the dependency is rejected in review, show the text part only and render HTML-only mails as jsoup-free plain text extraction.
  - [ ] `mailcenter/inbound/InboundClassifier` → `HUMAN | OUT_OF_OFFICE | BOUNCE | CALENDAR_REPLY | LOOP`. Reuse `forwarding/MailClassifier` (make it `public`; today `final class` package-private, `MailClassifier.java:9`); add the subject patterns (Dev Notes). Table-driven tests, DE + EN + FR.

- [ ] **Task 3 — One processing entry per path, callable by the dev simulator** (AC: 2, 7, 15)
  - [ ] Extract the per-mail body of `InboundEmailListenerService.handleS3Notification` (lines 107–147) into a non-conditional bean `InboundReplyProcessor.process(String s3Key, byte[] raw)`; the SQS listener keeps fetching from S3 and delegates. Same for `EmailForwardingListener` (line 63) → `AliasMailProcessor.process(s3Key, raw)` calling `EmailForwardingService.forward` and then the mail center. Behaviour of the listeners is unchanged; their existing tests stay green.
  - [ ] Make the router's result say which keyword acted: replace `RouteResult.HANDLED` by `UNSUBSCRIBED`, `DEREGISTRATION_LINK`, `CONFIRMED` (callers compare `!= UNRECOGNIZED`: `InboundEmailListenerService.java:134`, `DevEmailController.java:115`; update `InboundEmailRouterTest`).

- [ ] **Task 4 — Threading and ingestion** (AC: 1–6, TDD first)
  - [ ] RED: `ConversationThreadingIntegrationTest` (Testcontainers): moderator alias → new conversation with assignee = `organizerUsername`; second mail same speaker → same conversation; `DONE` + new mail → `OPEN`; In-Reply-To matching a `SPEAKERS` outbound by SES id; by stored header; by the 16.1b-captured header; References only; unknown → not opened (16.2a scope); organizer sender → not opened; duplicate delivery → one row; token match (seed a conversation with a token).
  - [ ] `mailcenter/inbound/ConversationThreader`: the AC3 order. Message-ID matching: normalise every id to the text between `<` and `>`; match the full value against `mail_message.message_id_header` and `mail_recipient.message_id_header`, and its local part (before `@`) against `mail_recipient.ses_message_id`. Count which rule matched (`MailCenterMetrics`, e.g. `ThreadMatchedBy` with dimension `token|header|sesId|alias`), so production shows which branch is real.
  - [ ] `mailcenter/inbound/InboundMailCenter.ingestReply(s3Key, raw, routeResult)` and `ingestAlias(s3Key, raw, forwardResult, addressedAliases)`: classify → count (Task 5) or thread + store. 16.2a gate: open a conversation only for AC1/AC2 (moderator alias, or a match to a `SPEAKERS` outbound mail or an existing conversation). Everything else: counts only, nothing stored. Organizer check via `ForwardingDirectory.organizerEmails()` (cached per user in `userApiCache`).
  - [ ] Ingestion never breaks forwarding: called after the existing processing, wrapped in try/catch, failure logged and counted (`MailCenterIngestFailed`), never rethrown. Forwarding is the safety net until 16.6 AC4.
  - [ ] AC5: `EmailForwardingService.sendBroadcast` (line 385) records the broadcast through a new `EmailService.sendRawMessage(MailContext, byte[] mime, destinations, configSet, OutboundMail summary)` overload (shared kernel) that calls the 16.1a recorder with the SES MessageId; recipients = To + Cc of the broadcast; body = the parsed broadcast (Task 2); sender username via a new `ForwardingDirectory.organizerUsernameFor(email)`. Remove `EmailForwardingService` from the AC5 allow-list of `OutboundMailInventoryGuardTest` only for this call if the guard can express it; otherwise keep the entry and add a one-line reason.
  - [ ] 16.1b listener (`MailDeliveryStatusService.process`, line 67): when the SES event carries `mail.commonHeaders.messageId`, store it in `mail_recipient.message_id_header` for the matched rows. Unit test with a synthetic SES event.

- [ ] **Task 5 — Automatic counts** (AC: 7, 8)
  - [ ] `AutomaticMailCounter.count(kind, eventCode, dedupeKey)`: insert into `mail_automatic_event` with `ON CONFLICT (dedupe_key) DO NOTHING`.
  - [ ] Event attribution: outbound mail found by Message-ID → its `event_code`; calendar reply UID → `CalendarInviteUid.parse` + `RegistrationRepository.findByCalendarUid` (as in `CalendarDeclineService.java:52`) → the registration's event; keyword actions → the subject's event code (same pattern as `InboundEmailRouter.extractEventCode`).
  - [ ] Spec + `EventMailController.getEventMailCounts` (line 51) + `MailMessageQueryService`: the new count fields.

- [ ] **Task 6 — Conversation API** (AC: 12, 13)
  - [ ] Spec first: in `docs/api/event-mail-api.openapi.yml` add tag `MailConversation`, the three operations of AC13, schemas `MailConversationSummary` (`conversationKey`, `eventCode`, `audience`, `subject`, `counterpartAddress`, `counterpartName`, `status`, `assigneeUsername`, `via`, `lastMessageAt`, `messageCount`), `MailConversationPage`, `MailConversationDetail` (`conversation` + `messages: MailMessageDetail[]`), `MailConversationPatch`; the new optional fields on `MailMessageSummary`; the new counts on `MailCounts`.
  - [ ] `mailcenter/MailConversationController` implements the generated interface; `ConversationQueryService` (native SQL like `MailMessageQueryService`), `ConversationService.patch` (validates the assignee holds ORGANIZER via `UserApiClient.getOrganizerUsernames()`; `updated_by` = caller). `EndpointAuthorizationGuardTest` green.
  - [ ] Integration test: list order (OPEN, WAITING, then DONE), 403 for a non-organizer, 404 for an unknown key, 400 for an unknown assignee, PATCH visible to a second read.
  - [ ] Bruno (`bruno-tests/events/`): list and 404 only, read-only, prose in `docs { }`. No PATCH: Bruno runs against production after every deploy (memory: "Bruno PUT-replay nulls prod data").

- [ ] **Task 7 — Local dev simulator** (AC: 15)
  - [ ] `DevEmailController` (profile `local`): `POST /dev/emails/inbound` with `{ from, fromName, to, cc, subject, body, inReplyToCaptureId?, headers? }`. It builds RFC 5322 MIME (RFC 2047 subject, as `replyMime` does today, line 133), sets `In-Reply-To: <local-{captureId}@localhost>` when a capture id is given (local sends are recorded with `ses_message_id = local-{captureId}`, `EmailService.java:228`), and calls `InboundReplyProcessor` when `to` is `replies@…`, else `AliasMailProcessor`. Unit test in `DevEmailControllerReplyTest`.
  - [ ] Verify on local dev (`make dev-native-up`): speaker mail captured → simulate a speaker reply to `batbern{N}-moderator@` → conversation visible on the page with assignee; simulate an OOO → count increments; simulate the same Message-ID twice → one row. Delete the test rows afterwards. Write the result into Completion Notes.

- [ ] **Task 8 — Retention** (AC: 14)
  - [ ] Extend `MailMessageRetentionScheduler.purgeExpired` (line 38): delete conversations whose event date and last activity are older than 12 months (no event: last activity), messages follow by cascade; then the existing `deleteExpired`; then `mail_automatic_event` by the same rule on `received_at`. One transaction, one ShedLock, as today. Integration test for each branch.
  - [ ] `docs/architecture/08-operations-security.md` section "Recorded mail is personal data": inbound mail and conversations, same retention, bodies stored in DB, S3 still 7 days.

- [ ] **Task 9 — Frontend "Needs an answer"** (AC: 9–12, 16; Vitest first)
  - [ ] Service `mailConversationService.ts` (no direct fetch) + hooks `useMailConversations.ts` (`useEventConversations`, `useConversation`, `usePatchConversation` invalidating `['mails', eventCode]` and the conversation keys).
  - [ ] Extract from `communications/MailDetail.tsx`: `MailBodyFrame` (the `sandbox=""` iframe, lines 70–85) and `FullScreenDetailDialog` (generalised `MailDetailDialog`, lines 152–188). `MailDetail` uses both; no visual change (its tests stay green).
  - [ ] New `communications/NeedsAnswerSection.tsx`, `ConversationRow.tsx`, `ConversationDetail.tsx` (messages oldest first, `MailBodyFrame`, attachment names, status select, assignee via the existing `components/shared/OrganizerSelect`, "I'll take it"). Same open/close rules as `MailSentList` (one open, second click closes, dialog on mobile).
  - [ ] `EventCommunicationsContainer.tsx`: section 1 above "What went out" (line 207), "+ New mail" button and menu (lines 138–176) move into its header; "Handled automatically" (lines 211–220) lists the new counts; read `?conversation=` and open it.
  - [ ] i18n: `eventPage.mail.conversations.*` and `eventPage.mail.automatic.*` in `public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/events.json`.
  - [ ] Vitest: empty state is one line, order and greyed DONE, badge counts OPEN only, inline vs dialog by breakpoint, PATCH on status/assignee/"I'll take it", deep link. Playwright (organizer project, local dev): read-only spec, no send, no horizontal scroll at 375 px.

- [ ] **Task 10 — Docs** — `docs/user-guide/features/event-mail.md` (new section "Needs an answer", what is counted automatically), `docs/architecture/06-backend-architecture.md` (inbound flow: two paths, classifier, threader, the dual Message-ID match). Shared-kernel change → `06-backend-architecture.md` is also its mapped doc (`.github/doc-drift-mappings.yml`).

### Suggested PR split

1. **Backend** (Tasks 0–8, 10 backend part): migration, ingestion, counts, API, simulator, retention. Deploys silently: conversations start filling, forwarding unchanged. Shared kernel + EMS.
2. **Frontend** (Task 9, user guide). Only after PR 1 is live, so the page never calls a missing endpoint.

## Dev Notes

### Where inbound mail arrives today (verified in code)

- **Two SES paths** (`infrastructure/lib/stacks/inbound-email-stack.ts`): rule `RouteReplies` (`replies@{domain}`, lines 190–200) → S3 `emails/` → replies queue → `InboundEmailListenerService` (conditional on `aws.inbound-email.enabled`, line 50); rules `RouteForwardingNamed` (lines 203–220) and the domain catch-all (lines 224–233) → S3 `forwarding/` → forwarding queue → `EmailForwardingListener` (conditional on `aws.inbound-email.forwarding.enabled`, line 26) → `EmailForwardingService.forward` (line 87).
- **Speaker replies arrive on the alias path, not at `replies@`.** Since 16.0 Part A every speaker mail carries Reply-To `batbern{N}-moderator@` (`ModeratorReplyAddress`, `ForwardingAlias.moderatorAddress`). The epic's "inbound from `replies@`" covers only older mails and the Q&A digest (`QnaNotificationService` keeps `replies@`, 16.0 Completion Notes).
- `ForwardingAlias.classify` (`ForwardingAlias.java:57–87`): `-moderator@` = `EVENT_MODERATOR`, open to anyone (line 108); `-speaker@` = `EVENT_SPEAKERS`, organizer-only. `forward()` drops calendar replies first (line 88), then malformed, then loops (own domain or `X-BATbern-Forwarded`, line 116), then unknown/no-reply aliases (125–133), then auto-replies (136–141). **It never checks for bounces**; only `forwardUnrecognizedReply` does (line 258). The classifier in Task 2 covers both paths.
- Raw MIME: S3 bucket `batbern-inbound-emails-{env}` expires everything after 7 days (`inbound-email-stack.ts:155–160`). EMS has read access only (line 440). **Decision taken in this story: store the parsed text and sanitized HTML in the database at receipt**, so AC14 needs no CDK change and works on local dev, where there is no S3. Attachments: metadata only. See the open question below.

### Message-ID trap (read before Task 4)

- We store the **SES API MessageId** (`response.messageId()`, e.g. `0107019a…-000000`) in `mail_recipient.ses_message_id` (`EmailService.java:273`, `JpaOutboundMailRecorder.java:92`). A reply carries the **header** value in `In-Reply-To`.
- For `SendEmail` (speaker mails, most transactional mail) SES generates the header, in the form `<{MessageId}@{region}.amazonses.com>` (inferred, region `eu-central-1` per `application.yml:68`; Task 0 confirms). So match on the local part, not the full string.
- For `SendRawEmail` (registration mails with ICS, newsletter, forwarding) the MIME is built by JavaMail, which writes its own `Message-ID` on save, and the forwarder deliberately keeps the original sender's id (`ForwardedMessageRewriter.java:103–112`). Whether SES keeps or replaces a caller-supplied `Message-ID` is not verified here. Hence the triple match in Task 4 (stored header, header captured from the SES event, SES id local part) and the metric that shows which one matches in production.
- Local dev: sends are recorded with `ses_message_id = local-{captureId}` (`EmailService.java:228, 347, 475`); the simulator builds `In-Reply-To: <local-{captureId}@localhost>`, whose local part matches.

### Classifier details

- Existing header checks: `MailClassifier.isAutoReply` (line 48), `isBounce` (line 63), `isCalendarReply` (line 30). Story 16.0 AC4 speaks of "out-of-office patterns already known to `MailClassifier`"; the code knows headers only, no subject patterns. Add, case-insensitive, at the start of the subject: `Out of Office`, `Automatic reply`, `Auto-Reply`, `Autoreply`, `Abwesenheitsnotiz`, `Automatische Antwort`, `Abwesend`, `Réponse automatique`, `Absence`, `Risposta automatica`. Precedence `bulk/list` stays NOT a signal (comment at line 44–46 explains why).
- Calendar replies on the alias path go to `calendar@` (registration ICS organizer, `application.yml:112`) and are handled by `CalendarDeclineService`; on the replies path partner-meeting RSVPs go to `InboundEmailRouter.routeIcsReply`. Count both; partner RSVPs have no EMS event (meeting UUID only) and stay without event until 16.9/16.2b.

### Conversation model (owner decision 2026-11-01: accepted)

- Messages stay `mail_message` rows (`IN` and `OUT`), one mail resource: `/events/{code}/mails/{mailKey}` returns any of them. A conversation is a separate concept with its own state (status, assignee, token), so it gets its own small resource: `/events/{code}/conversations` (list) and `/conversations/{key}` (detail, PATCH; 16.6 adds `POST …/replies`; 16.7 adds the global `GET /conversations`). Keys are global (`conv-{n}`) because 16.7 can move a conversation to another event; that is also why the per-conversation operations are not nested under an event.
- Alternative considered: conversation fields on `MailMessageSummary` and `PATCH /events/{code}/mails/{mailKey}`. Rejected: status and assignee belong to the thread, not to one message, and no-event conversations (16.2b) have no event path to live under.
- `audience` of a speaker conversation is `SPEAKERS`. Default assignee is `Event.organizerUsername` (`domain/Event.java:95`), not the per-session `MODERATOR` role (owner decision 2).

### Frontend facts

- `EventCommunicationsContainer.tsx` (268 lines): header with "+ New mail" (138–176), inline composer (178–205), `MailSentList` (207–209), "Handled automatically" accordion (211–220), mobile composer dialog (222–255).
- `MailDetail.tsx`: body iframe `sandbox=""` (70–85), `MailDetailDialog` (152–188), data via `useMailMessage(eventCode, mailKey)` (line 48).
- `MailSentList.tsx`: one open row, inline below on desktop, `MailDetailDialog` on mobile. Copy that behaviour.
- `OrganizerSelect` (`components/shared/OrganizerSelect/OrganizerSelect.tsx`) is already used by `EventSettingsTab.tsx:297` for the event moderator; reuse it for the assignee.
- Event page deep links use `?tab=communications` (`EventPage.tsx:13`).

### Mail safety and tests

- ADR-015: no test sends real mail. This story sends nothing new except the recorded `-speaker@` broadcast, which is existing behaviour.
- Bruno runs against production after each deploy: read-only requests only.
- Mail bodies are PII: organizer-only endpoints, `no-store`, synthetic fixtures.

### Owner decisions (2026-11-01: recommendations below accepted, see epic "Decisions taken 2026-11-01")

- Attachments: kept as names only. Once 16.6 AC4 stops forwarding, a speaker's attachment (e.g. slides) is readable for 7 days only (S3) and not from the UI. Options: (a) accept, speakers upload through the portal; (b) copy attachments up to a size cap to a new S3 prefix at receipt and delete them in the retention job (CDK: write grant + prefix lifecycle backstop). Recommendation: (b), decided before 16.6 AC4 is switched on.

### References

- `docs/prd/epic-16-mail-center.md` (Story 16.2, decisions 1–5, constraints)
- `docs/ux/mail-center-spec.md`, `mail-center-prototype.html`, `mail-center-layouts.html`, `mail-center-mobile.html`
- `services/event-management-service/src/main/java/ch/batbern/events/forwarding/{EmailForwardingService,EmailForwardingListener,ForwardingAlias,MailClassifier,ForwardingDirectory,ForwardedMessageRewriter}.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/{InboundEmailListenerService,InboundEmailRouter,CalendarDeclineService,CalendarInviteUid}.java`
- `services/event-management-service/src/main/java/ch/batbern/events/mailcenter/{EventMailController,MailMessageQueryService,JpaOutboundMailRecorder,MailDeliveryStatusService,MailCenterMetrics}.java`
- `services/event-management-service/src/main/java/ch/batbern/events/scheduler/MailMessageRetentionScheduler.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/DevEmailController.java`
- `services/event-management-service/src/main/resources/db/migration/V135__create_mail_message.sql`
- `shared-kernel/src/main/java/ch/batbern/shared/service/EmailService.java`, `shared-kernel/src/main/java/ch/batbern/shared/mail/*`
- `docs/api/event-mail-api.openapi.yml`, `docs/architecture/ADR-013-rest-api-crud-conventions.md`, `docs/architecture/ADR-015-test-data-markers-and-email-send-guards.md`
- `web-frontend/src/components/organizer/EventPage/EventCommunicationsContainer.tsx`, `communications/{MailSentList,MailDetail}.tsx`

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List

## Change Log

- 2026-11-01: Story drafted from Epic 16 Story 16.2, speaker part (split 16.2a/16.2b per owner sequence #3).
