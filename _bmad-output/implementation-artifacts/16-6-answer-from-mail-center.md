# Story 16.6: Answer from the mail center

Status: ready-for-dev

## Story

As an **organizer**,
I want **to answer a conversation from the event's mail page, with the answer and the next reply staying in that conversation**,
so that **the team sees who answered what, and nobody has to forward mail to a private inbox any more**.

> Source: `docs/prd/epic-16-mail-center.md` → **Story 16.6**. Depends on **16.2a** (conversation model, `reply_token`, threader, "Needs an answer" UI) and 16.3. Built for speaker conversations first (owner sequence #3); it works unchanged for the conversations 16.2b adds.
> Owner decision 2: alias forwarding stays ON until this story is live. The final part (AC7–AC9) replaces forwarding with a body-less notification behind a runtime switch, and is switched on **only after the owner's go**.

## Acceptance Criteria

**Reply**

1. **AC1 (send)** — GIVEN an open conversation, WHEN an organizer writes an answer and sends it, THEN one mail goes to the counterpart (the last inbound mail's `Reply-To` if present, else its `From`):
   - From `"{organizer first + last name} via BATbern" <noreply@{domain}>`;
   - Reply-To `replies+{token}@{domain}` (the conversation's `reply_token`);
   - Subject `Re: {conversation subject}` (no second prefix when it already starts with `Re:`, `AW:`, `RE:`, `Aw:`);
   - `In-Reply-To` = the last inbound mail's `Message-ID`, `References` = its `References` plus that id;
   - our own `Message-ID: <{uuid}@{domain}>`, stored with the record.
2. **AC2 (recorded, visible)** — The answer is recorded through 16.1a (`MailContext`, `OutboundMailRecorder`): one `mail_message` direction `OUT`, content source `OWN_TEXT`, sender = the organizer, linked to the conversation. Every organizer sees it in the conversation with sender, time and text. It does not appear in "What went out" a second time as a separate send (it is part of the conversation).
3. **AC3 (status)** — Sending sets the conversation to `WAITING`; the next inbound mail sets it back to `OPEN` (16.2a AC3). An organizer may tick "Mark as done" before sending; then it becomes `DONE` instead.
4. **AC4 (threading back)** — GIVEN an organizer answered a speaker, WHEN the speaker replies again, THEN the reply lands in the same conversation, whichever SES rule received it (see Dev Notes, "Where `replies+` mail lands"). A token reply never triggers a keyword action of `InboundEmailRouter`, even if its first line is "accept" or "cancel".
5. **AC5 (reassign notifies once)** — GIVEN an organizer sets the assignee to another organizer, THEN the new assignee receives one notification mail (template `mailcenter-assigned`, DE + EN, the assignee's preferred language) with event, counterpart name, subject and a link to `/organizer/events/{eventCode}?tab=communications&conversation={key}` (no-event conversation: the global Mail page of 16.7). Assigning yourself ("I'll take it"), clearing the assignee, or saving the same assignee again sends nothing.
6. **AC6 (guards)** — The answer goes through the same send walls as every other mail (ADR-015: reserved domains refused, test-marked never sent). Empty text → 400. A conversation without a counterpart address that can be mailed → 409. Only `hasRole('ORGANIZER')`.

**Forwarding replaced by a notification (final part, owner go required)**

7. **AC7 (switch)** — A runtime setting `mailcenter.notify-instead-of-forward` (admin settings store, read per mail) holds a set of alias groups: `moderator` (`batbern{N}-moderator@`), `replies` (unrecognized mail and token replies at `replies@`), `contact` (`info@`, `events@`, `support@`; usable once 16.2b and 16.7 are live). Default empty = today's forwarding. Removing a group switches it back at once, no deploy.
8. **AC8 (notification instead of copy)** — For a mail of a switched group that was stored as a conversation, the recipients who would have received the forwarded copy receive instead one short mail (template `mailcenter-new-mail`, DE + EN): "New mail for BATbern{N} from {name}: {subject}. Open it here: {link}". No body, no attachment, no quoted text. Mail that was **not** stored (ingestion failed, or out of the stored scope) is forwarded as today, so the switch can never lose mail.
9. **AC9 (order)** — With a group switched on, the mail center stores the mail **before** anything is sent, and a storage failure is rethrown so SQS retries (and dead-letters with the existing alarms). With the group off, the 16.2a order stays (forward first, store best-effort).

**All**

10. **AC10** — No test sends real mail. Send paths are verified on local dev (`/dev/emails`). No Bruno request calls the reply operation or changes an assignee (Bruno runs against production after each deploy).
11. **AC11** — New UI keys in all 10 locales; the two templates in DE and EN only.

## Tasks / Subtasks

### PR 1 — Reply and reassign

- [ ] **Task 1 — Compose and send through the shared kernel** (AC: 1, 2, 6; TDD first)
  - [ ] RED: shared-kernel `EmailServiceComposedMailTest`: From display name, Reply-To, `In-Reply-To`, `References`, the given `Message-ID` survives serialisation (JavaMail regenerates it on `saveChanges()`; use a `MimeMessage` subclass with a no-op `updateMessageID()`, like `ForwardedMessageRewriter.PreservingMimeMessage`), test-marked subject refused, recorder called once with the SES id; local mode captures to the dev inbox and records `local-{captureId}`.
  - [ ] `shared-kernel/.../mail/ComposedMail` record (fromName, to, subject, html, text, replyTo, messageId, inReplyTo, references) and `EmailService.sendComposed(MailContext, ComposedMail)`. Build the MIME as `sendHtmlEmailSyncWithAttachments` does (`EmailService.java:442–531`: multipart/alternative, From with `fromEmail`), send with `SendRawEmail`, record. Do not reuse `sendHtmlEmail`: `SendEmail` cannot carry `In-Reply-To`/`References`, and the display name is the global `app.email.from-name` (`EmailService.java:95–96`).
- [ ] **Task 2 — Reply service and endpoint** (AC: 1–3, 6)
  - [ ] Spec first (`docs/api/event-mail-api.openapi.yml`): `POST /conversations/{conversationKey}/replies`, body `{ text: string (1..20000), markDone?: boolean }`, 201 → `MailMessageDetail`; 400, 403, 404, 409.
  - [ ] `mailcenter/ConversationReplyService`: load conversation + last inbound message; organizer name via `UserApiClient.getUserByUsername`; body = the organizer's text as plain text and as minimal HTML (escaped, line breaks, no BATbern layout), followed by a quote of the last inbound text ("On {date}, {name} wrote:", `>`-prefixed in the text part, `<blockquote>` in HTML); `MailContext.individual(eventCode, audience, null)` with `contentSource OWN_TEXT` and the sender; after the send set `mail_message.conversation_id` and `message_id_header` for the recorded group (by `send_group_id`), then status `WAITING` or `DONE`, `last_message_at`.
  - [ ] Integration test (Testcontainers, SES mocked as in `MailRecordingIntegrationTest`): headers, record linked, status, 409, 403.
- [ ] **Task 3 — Token replies on both paths** (AC: 4)
  - [ ] RED: `TokenReplyRoutingTest` for both processors (`InboundReplyProcessor`, `AliasMailProcessor`, both from 16.2a Task 3): a mail addressed to `replies+{token}@` with first line "accept" runs no router action and joins the conversation; unknown token → treated like a plain `replies@` mail (router, then 16.2a rules).
  - [ ] `ForwardingAlias.classify` (`ForwardingAlias.java:57`): local part `replies` or `replies+…` → new kind `CONVERSATION_REPLY`, `forwardable() == false`, so `forward()` returns early instead of `UNRESOLVED` (today it would be dropped at `EmailForwardingService.java:125–133`). `AliasMailProcessor` hands such mail to the mail center and, while the `replies` group is off, forwards it with `forwardUnrecognizedReply` (same recipients as today's `replies@`).
  - [ ] `InboundReplyProcessor`: detect the token in To/Cc before calling the router.
  - [ ] Metric `ConversationReplies` with dimension `path=replies|alias`, so the first production reply shows which SES rule took it.
- [ ] **Task 4 — Reassign notification** (AC: 5)
  - [ ] Templates `email-templates/mailcenter-assigned-{de,en}.html` with `<!-- subject: … -->` header (`EmailTemplateSeedService` parses it). Add a `mailcenter-` branch in `EmailTemplateSeedService.deriveCategory` (line 151): otherwise the key falls through to `"LAYOUT"` (line 200) and is seeded as a layout. Category value `MAIL_CENTER`: add it to the category enum in `docs/api/event-newsletter-api.openapi.yml` (lines 51–60 and 1161), `EmailTemplatesTab.tsx` (lines 68, 84, 93) and `EmailTemplateEditModal.tsx` (line 192). 16.9 adds `PARTNER` to the same enum in parallel: rebase carefully.
  - [ ] `ConversationAssignmentNotifier`, shaped like `TaskReminderEmailService` (DB-first template, classpath fallback, `MailContext.of(..., Audience.ORGANIZERS, "mailcenter-assigned")`). Called by `ConversationService.patch` only when the assignee changes to someone other than the caller. Test: self, same, cleared, changed (one mail).
- [ ] **Task 5 — Frontend** (AC: 1–3, 5, 11; Vitest first)
  - [ ] `communications/ConversationReplyBox.tsx` under the messages in `ConversationDetail` (16.2a): text field, "Mark as done" checkbox, the hint "Sent as '{name} via BATbern'. The reply lands here." (`mail-center-prototype.html`), inline confirm like the composer, Send disabled while empty or pending, toast after send, list refresh. Full width in the mobile dialog; the send button stays visible above the keyboard.
  - [ ] Service + hook `useReplyToConversation` (invalidates the conversation and `['mails', eventCode]`).
  - [ ] Vitest: disabled states, markDone, error 409 shown, after send status chip shows Waiting. Playwright: local dev only, tagged `@sends-real-email` so the staging guard skips it.
- [ ] **Task 6 — Docs** — user guide `event-mail.md` (answering, reassigning), `06-backend-architecture.md` (reply headers, token routing), shared kernel change noted.

### PR 2 — Notification instead of forwarding (code ships switched off; switching on needs the owner's go)

- [ ] **Prerequisite (owner decision 2026-11-01, from 16.2a):** inbound attachments are kept, not only their names. At receipt, copy each attachment up to a size cap to a new S3 prefix, show it in the conversation view, delete it in the retention job (CDK: EMS write grant on the prefix + a lifecycle backstop; needs the Doc Drift doc change). No forwarding group is switched to notification before this is live.

- [ ] **Task 7 — Switch and notification** (AC: 7–9)
  - [ ] Setting key `mailcenter.notify-instead-of-forward` read via `AdminSettingsService.getSetting` (line 18), parsed into a set; unknown values ignored and logged.
  - [ ] Templates `mailcenter-new-mail-{de,en}.html`, category `MAIL_CENTER`.
  - [ ] `AliasMailProcessor` / `InboundReplyProcessor`: when the mail's group is on, ingest first (rethrow on failure), and if a conversation was stored, send the notification to the resolved recipients instead of the forwarded copy; otherwise forward as today. Speaker broadcasts (`-speaker@`), `-participants@`, `partner@` and `ok@` are organizer sends to other people and are always forwarded, whatever the setting.
  - [ ] Keep the forwarding log honest: new outcome `NOTIFIED` in `EmailForwardingOutcome` + migration extending the CHECK constraint (same pattern as V131); metric `NotificationsSent`.
  - [ ] Tests per group on and off; storage failure with the group on → rethrown, nothing sent.
- [ ] **Task 8 — Runbook and go** — `docs/operations/runbooks.md`: how to switch a group on and back (admin settings), what to check first (`NotificationsSent`, `MailCenterIngestFailed`, replies DLQ alarm). Do not set the value in production; the owner does, per group.

## Dev Notes

### Where `replies+` mail lands (verified in code, SES behaviour inferred)

- SES rule `RouteReplies` lists exactly `replies@{replyDomain}` (`infrastructure/lib/stacks/inbound-email-stack.ts:190–200`). In production `replyDomain` and `forwardingDomain` are both `batbern.ch` (lines 84–85). A plus-addressed `replies+{token}@batbern.ch` most likely does not match an exact-address recipient condition and falls through to the domain catch-all (`RouteForwardingCatchAll`, lines 224–233) → `forwarding/` → `EmailForwardingListener`. Not verified against SES; Task 3 handles both paths, so the outcome does not matter, and the metric shows it after the first real reply.
- On the alias path today, `ForwardingAlias.classify("replies+…")` returns `UNKNOWN` (line 72), `forward()` logs "addresses no known alias" and returns `UNRESOLVED` (`EmailForwardingService.java:130–133`): the reply would be dropped. Task 3 is therefore not optional.
- On the replies path, `InboundEmailRouter.route` acts on a short first line (`MAX_KEYWORD_WORDS = 3`, line 38) when the subject carries an event code. A speaker answering "Accept" to a conversation about BATbern60 would confirm a registration. Hence the token check before the router.

### Message-ID

- We set our own `Message-ID` and store it in `mail_message.message_id_header` (16.2a column). If SES keeps it, the speaker's `In-Reply-To` matches the stored header; if SES replaces it, the SES id or the header captured by the 16.1b listener matches (16.2a Task 4). Either way the token in the Reply-To is the primary key; the headers are the fallback when a client drops the token (e.g. "reply" from a forwarded copy).

### Recording

- `EmailService.record(...)` (`EmailService.java:603`) is private and called only after a successful SES call; `sendRawMessage` (line 555) records nothing today. `sendComposed` must call `record`.
- `MailContext` has no conversation field. Link after the send by `send_group_id` (EMS-local update); no shared-kernel change needed for the link, so 16.9's HTTP recorder payload stays as it is.
- `MailMessageQueryService` lists `direction = 'OUT'` (line 62). Exclude `conversation_id IS NOT NULL` from "What went out" in the same query, so an answer is shown once, inside its conversation.

### Templates

- Pattern: `TaskReminderEmailService.java:100–160` (DB-first via `loadHtmlContent`, classpath fallback, `emailTemplateService.resolveSubject`, `MailContext.of(run, eventCode, Audience.ORGANIZERS, key)`).
- Seeding: `EmailTemplateSeedService.seedTemplatesFromClasspath` (line 43) seeds new classpath files on startup; `deriveCategory` (line 151) decides the admin tab. Without the new branch the templates land as `LAYOUT` (line 200).
- DE + EN only (CLAUDE.md, email templates). No real names in template examples.

### Notification recipients (AC8)

- The recipients are the ones forwarding resolves today: `ForwardingRecipientResolver.resolve(alias)` per alias, organizers for `replies@` (`EmailForwardingService.java:270–271`). Reuse them, so switching on changes the content of the mail, not who receives it.
- Group `contact` must stay off until 16.2b stores `info@`/`support@` mail and 16.7 can show conversations without an event; otherwise the link has nowhere to go.

### Owner decisions (2026-11-01: recommendations below accepted, see epic "Decisions taken 2026-11-01")

- Body of an answer: plain text with a minimal HTML version and the quoted inbound mail, no BATbern newsletter layout (recommended: it is a personal answer). Alternative: the default layout `layout-batbern-default`.
- Attachments in answers: not in scope. Inbound attachments: see 16.2a open question; decide before switching any group on.

### References

- `docs/prd/epic-16-mail-center.md` (Story 16.6, decision 2)
- `_bmad-output/implementation-artifacts/16-2a-speaker-conversations.md`
- `shared-kernel/src/main/java/ch/batbern/shared/service/EmailService.java`, `shared-kernel/src/main/java/ch/batbern/shared/mail/MailContext.java`
- `services/event-management-service/src/main/java/ch/batbern/events/forwarding/{EmailForwardingService,ForwardingAlias,ForwardedMessageRewriter,ForwardingRecipientResolver}.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/{InboundEmailRouter,TaskReminderEmailService,EmailTemplateSeedService,AdminSettingsService}.java`
- `services/event-management-service/src/main/resources/db/migration/V131__email_forwarding_log_dropped_outcomes.sql` (CHECK extension pattern)
- `infrastructure/lib/stacks/inbound-email-stack.ts`
- `docs/architecture/ADR-015-test-data-markers-and-email-send-guards.md`

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List

## Change Log

- 2026-11-01: Story drafted from Epic 16 Story 16.6.
