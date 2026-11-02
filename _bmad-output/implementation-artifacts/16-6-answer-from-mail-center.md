# Story 16.6: Answer from the mail center

Status: done

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

- [x] **Task 1 — Compose and send through the shared kernel** (AC: 1, 2, 6; TDD first)
  - [x] RED: shared-kernel `EmailServiceComposedMailTest`: From display name, Reply-To, `In-Reply-To`, `References`, the given `Message-ID` survives serialisation (JavaMail regenerates it on `saveChanges()`; use a `MimeMessage` subclass with a no-op `updateMessageID()`, like `ForwardedMessageRewriter.PreservingMimeMessage`), test-marked subject refused, recorder called once with the SES id; local mode captures to the dev inbox and records `local-{captureId}`.
  - [x] `shared-kernel/.../mail/ComposedMail` record (fromName, to, subject, html, text, replyTo, messageId, inReplyTo, references) and `EmailService.sendComposed(MailContext, ComposedMail)`. Build the MIME as `sendHtmlEmailSyncWithAttachments` does (`EmailService.java:442–531`: multipart/alternative, From with `fromEmail`), send with `SendRawEmail`, record. Do not reuse `sendHtmlEmail`: `SendEmail` cannot carry `In-Reply-To`/`References`, and the display name is the global `app.email.from-name` (`EmailService.java:95–96`).
- [x] **Task 2 — Reply service and endpoint** (AC: 1–3, 6)
  - [x] Spec first (`docs/api/event-mail-api.openapi.yml`): `POST /conversations/{conversationKey}/replies`, body `{ text: string (1..20000), markDone?: boolean }`, 201 → `MailMessageDetail`; 400, 403, 404, 409.
  - [x] `mailcenter/ConversationReplyService`: load conversation + last inbound message; organizer name via `UserApiClient.getUserByUsername`; body = the organizer's text as plain text and as minimal HTML (escaped, line breaks, no BATbern layout), followed by a quote of the last inbound text ("On {date}, {name} wrote:", `>`-prefixed in the text part, `<blockquote>` in HTML); `MailContext.individual(eventCode, audience, null)` with `contentSource OWN_TEXT` and the sender; after the send set `mail_message.conversation_id` and `message_id_header` for the recorded group (by `send_group_id`), then status `WAITING` or `DONE`, `last_message_at`.
  - [x] Integration test (Testcontainers, SES mocked as in `MailRecordingIntegrationTest`): headers, record linked, status, 409, 403.
- [x] **Task 3 — Token replies on both paths** (AC: 4)
  - [x] RED: `TokenReplyRoutingTest` for both processors (`InboundReplyProcessor`, `AliasMailProcessor`, both from 16.2a Task 3): a mail addressed to `replies+{token}@` with first line "accept" runs no router action and joins the conversation; unknown token → treated like a plain `replies@` mail (router, then 16.2a rules).
  - [x] `ForwardingAlias.classify` (`ForwardingAlias.java:57`): local part `replies` or `replies+…` → new kind `CONVERSATION_REPLY`, `forwardable() == false`, so `forward()` returns early instead of `UNRESOLVED` (today it would be dropped at `EmailForwardingService.java:125–133`). `AliasMailProcessor` hands such mail to the mail center and, while the `replies` group is off, forwards it with `forwardUnrecognizedReply` (same recipients as today's `replies@`).
  - [x] `InboundReplyProcessor`: detect the token in To/Cc before calling the router.
  - [x] Metric `ConversationReplies` with dimension `path=replies|alias`, so the first production reply shows which SES rule took it.
- [x] **Task 4 — Reassign notification** (AC: 5)
  - [x] Templates `email-templates/mailcenter-assigned-{de,en}.html` with `<!-- subject: … -->` header (`EmailTemplateSeedService` parses it). Add a `mailcenter-` branch in `EmailTemplateSeedService.deriveCategory` (line 151): otherwise the key falls through to `"LAYOUT"` (line 200) and is seeded as a layout. Category value `MAIL_CENTER`: add it to the category enum in `docs/api/event-newsletter-api.openapi.yml` (lines 51–60 and 1161), `EmailTemplatesTab.tsx` (lines 68, 84, 93) and `EmailTemplateEditModal.tsx` (line 192). 16.9 adds `PARTNER` to the same enum in parallel: rebase carefully.
  - [x] `ConversationAssignmentNotifier`, shaped like `TaskReminderEmailService` (DB-first template, classpath fallback, `MailContext.of(..., Audience.ORGANIZERS, "mailcenter-assigned")`). Called by `ConversationService.patch` only when the assignee changes to someone other than the caller. Test: self, same, cleared, changed (one mail).
- [x] **Task 5 — Frontend** (AC: 1–3, 5, 11; Vitest first)
  - [x] `communications/ConversationReplyBox.tsx` under the messages in `ConversationDetail` (16.2a): text field, "Mark as done" checkbox, the hint "Sent as '{name} via BATbern'. The reply lands here." (`mail-center-prototype.html`), inline confirm like the composer, Send disabled while empty or pending, toast after send, list refresh. Full width in the mobile dialog; the send button stays visible above the keyboard.
  - [x] Service + hook `useReplyToConversation` (invalidates the conversation and `['mails', eventCode]`).
  - [x] Vitest: disabled states, markDone, error 409 shown, after send status chip shows Waiting. Playwright: local dev only, tagged `@sends-real-email` so the staging guard skips it.
- [x] **Task 6 — Docs** — user guide `event-mail.md` (answering, reassigning), `06-backend-architecture.md` (reply headers, token routing), shared kernel change noted.

### PR 2 — Notification instead of forwarding (code ships switched off; switching on needs the owner's go)

- [x] **Prerequisite (owner decision 2026-11-01, from 16.2a):** inbound attachments are kept, not only their names. At receipt, copy each attachment up to a size cap (owner 2026-11-02: **25 MB per file**; larger ones keep only the name) to a new S3 prefix, show it in the conversation view, delete it in the retention job (CDK: EMS write grant on the prefix + a lifecycle backstop; needs the Doc Drift doc change). No forwarding group is switched to notification before this is live.

- [x] **Task 7 — Switch and notification** (AC: 7–9)
  - [x] Setting key `mailcenter.notify-instead-of-forward` read via `AdminSettingsService.getSetting` (line 18), parsed into a set; unknown values ignored and logged.
  - [x] Templates `mailcenter-new-mail-{de,en}.html`, category `MAIL_CENTER`.
  - [x] `AliasMailProcessor` / `InboundReplyProcessor`: when the mail's group is on, ingest first (rethrow on failure), and if a conversation was stored, send the notification to the resolved recipients instead of the forwarded copy; otherwise forward as today. Speaker broadcasts (`-speaker@`), `-participants@`, `partner@` and `ok@` are organizer sends to other people and are always forwarded, whatever the setting.
  - [x] Keep the forwarding log honest: new outcome `NOTIFIED` in `EmailForwardingOutcome` + migration extending the CHECK constraint (same pattern as V131); metric `NotificationsSent`.
  - [x] Tests per group on and off; storage failure with the group on → rethrown, nothing sent.
- [x] **Task 8 — Runbook and go** — `docs/operations/runbooks.md`: how to switch a group on and back (admin settings), what to check first (`NotificationsSent`, `MailCenterIngestFailed`, replies DLQ alarm). Do not set the value in production; the owner does, per group.

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

Claude Opus 5.5 (Amelia, bmad-dev-story), 2026-11-02

### Debug Log References

### Completion Notes List

**PR 1 (reply and reassign), 2026-11-02, on `feature/16-2a-speaker-conversations`, rebased on develop after #1185:**
- Shared kernel: `ComposedMail` + `EmailService.sendComposed` (SendRawEmail, multipart/alternative,
  caller's Message-ID kept by a `MimeMessage` subclass, same walls, local capture → `local-{id}`).
  `OutboundMail` gained `messageIdHeader` (6-arg constructor kept; the partner HTTP recorder maps
  fields explicitly and ignores it). `JpaOutboundMailRecorder` writes it, without brackets, to
  `mail_recipient.message_id_header`.
- V140 `mail_message.reply_to_address`: 16.2a parsed Reply-To but did not store it; AC1 needs it.
  `InboundMailStore` writes it.
- `POST /conversations/{key}/replies` (`ConversationReplyService`): Reply-To else From, 409 when the
  address cannot be mailed (unacceptable or reserved domain, checked before the send), 400 on empty
  or >20000 text. "What went out" now excludes rows with a `conversation_id`.
- Token replies: `ForwardingAlias.Kind.CONVERSATION_REPLY`, `Result.CONVERSATION_REPLY`;
  `AliasMailProcessor` stores + forwards (organizers, as `forwardUnrecognizedReply`) a known token and
  hands an unknown one to `InboundReplyProcessor`; `InboundReplyProcessor` skips the router for a
  known token. Metric `ConversationReplies{path}` (MailCenterMetrics got a one-dimension overload).
- Reassign: `ConversationAssignmentNotifier` (template `mailcenter-assigned` DE/EN, category
  `MAIL_CENTER`, new variable scope MAIL_CENTER: conversationSubject, counterpartName,
  conversationLink); called from `ConversationService.patch` only when the assignee changes to
  someone other than the caller. No-event link: `/organizer/mail?conversation=` (16.7 route).
- Frontend: `ConversationReplyBox` (sticky, inline confirm, Mark as done, 409 message, toast),
  `useReplyToConversation`, MAIL_CENTER in the admin template tabs; i18n in all 10 locales.
  The sender hint takes the name from `/users/me` (`useUserProfile`); `useAuth` has no names.
- Tests: `EmailServiceComposedMailTest` (5), `ConversationReplyIntegrationTest` (4),
  `TokenReplyRoutingIntegrationTest` (3), `ConversationServiceAssignmentTest` (4), golden files
  `mailcenter-assigned-{de,en}`, `ConversationReplyBox.test.tsx` (5), Playwright
  `conversation-reply.spec.ts` (@sends-real-email, local dev only). No Bruno request added (AC10).
- Live on local dev (2026-11-02, event BATbern63, synthetic simulator address): moderator mail →
  conv; answer via gateway 201, captured "Re: …" to the speaker; status WAITING; speaker reply
  "Accept" to `replies+{token}@` → same conversation, OPEN, 3 messages; answer not in "What went out".
  Reassign: 4 PATCHes (other, same, self, clear) → exactly 1 captured mail with the deep link. The
  local test organizer has no first/last name, so the display name fell back to the username (by
  design). The test rows of BATbern63 stay in the local DB (local only).
- Not verified: how SES routes `replies+{token}@` in production (rule inference only; the metric will
  show it), and whether SES keeps our Message-ID (threading does not depend on it: token first).

**PR 2 (attachments + notify switch, shipped OFF), 2026-11-02, after 16.2b, same branch:**
- Attachments (owner cap 25 MB): `InboundAttachmentStore` puts each file within the cap to the
  private inbound bucket `mail-attachments/{send_group_id}/{index}`; key in the attachments JSON
  (`storageKey`, API shows `kept`). Not the content bucket: it is served whole by CloudFront.
  Reuse per owner remark (2026-11-02): injected `S3Client`/`S3Presigner` like the other EMS S3
  services (there is no shared S3 helper), download as the session materials do
  (`GET …/attachments/{index}/download` → `{downloadUrl}`, now `_shared` `DownloadUrlResponse`;
  frontend opens it with `window.open(url, '_blank', 'noopener,noreferrer')` like
  `SessionEditModal`). Retention deletes the files of expiring conversations; CDK: lifecycle by
  prefix (`emails/`, `forwarding/` 7 days, `mail-attachments/` 400 days backstop) and EMS
  put/delete on `mail-attachments/*` only. The grant sits on the EMS task role (EMS stack), so it
  deploys with the EMS image. Server-side put (no presigned PUT): the file comes from SES, not a
  browser.
- Switch: `MailCenterSwitch` (setting `mailcenter.notify-instead-of-forward`, read per mail),
  `ConversationNotices` (template `mailcenter-new-mail` DE/EN), `EmailForwardingService.forward(…,
  notice)` / `forwardUnrecognizedReply(…, notice)` (same checks/claim/recipients), outcome
  `NOTIFIED` (V142), metric `NotificationsSent`, `InboundMailCenter.ingest*Strict` (throws; a
  redelivery of a stored mail returns its conversation). `AliasMailProcessor` switches only when
  every addressed alias maps to a switched group; `InboundReplyProcessor` for `replies`.
  `ConversationLinks` shared by both notifications. **Not switched on anywhere**; the owner does
  it per group (runbook).
- Tests: `InboundAttachmentStoreTest` (4), parser bytes (1), conversation API link/kept/404/403,
  retention deletes the files (folded into the one purge test, ShedLock), `MailCenterSwitchTest`
  (3), `AliasMailProcessorSwitchTest` (5), `InboundReplyProcessorSwitchTest` (4),
  `EmailForwardingServiceTest` notice mode (+2), golden `mailcenter-new-mail-{de,en}`, CDK (+2),
  Vitest attachment link/error (+1). Full: shared kernel 484/0, EMS 2389/0, PCS 231/0, CDK inbound
  stack 32/32, frontend Vitest 2416 passed, Playwright mail specs 4/4 (local dev).
- Live on local dev (BATbern63, synthetic address): setting `moderator` → the 3 resolved recipients
  got the notice (link, no text), log `notified|3`; setting cleared → forwarded copies with the text.
  Rows removed. Local dev has no inbound bucket, so attachment upload was verified by tests only.
- Not verified: attachment upload against the real bucket (needs the deploy); first production
  notification (owner's go).


### File List

PR 1:
- _bmad-output/implementation-artifacts/16-6-answer-from-mail-center.md
- _bmad-output/implementation-artifacts/sprint-status.yaml
- docs/api/event-mail-api.openapi.yml
- docs/api/event-newsletter-api.openapi.yml
- docs/architecture/06-backend-architecture.md
- docs/user-guide/appendix/changelog.md
- docs/user-guide/features/event-mail.md
- services/event-management-service/src/main/java/ch/batbern/events/forwarding/AliasMailProcessor.java
- services/event-management-service/src/main/java/ch/batbern/events/forwarding/EmailForwardingService.java
- services/event-management-service/src/main/java/ch/batbern/events/forwarding/ForwardingAlias.java
- services/event-management-service/src/main/java/ch/batbern/events/forwarding/ForwardingRecipientResolver.java
- (new) services/event-management-service/src/main/java/ch/batbern/events/mailcenter/ConversationAssignmentNotifier.java
- (new) services/event-management-service/src/main/java/ch/batbern/events/mailcenter/ConversationReplyService.java
- services/event-management-service/src/main/java/ch/batbern/events/mailcenter/ConversationService.java
- services/event-management-service/src/main/java/ch/batbern/events/mailcenter/JpaOutboundMailRecorder.java
- services/event-management-service/src/main/java/ch/batbern/events/mailcenter/MailCenterMetrics.java
- services/event-management-service/src/main/java/ch/batbern/events/mailcenter/MailConversationController.java
- services/event-management-service/src/main/java/ch/batbern/events/mailcenter/MailMessageQueryService.java
- services/event-management-service/src/main/java/ch/batbern/events/mailcenter/inbound/InboundMailCenter.java
- services/event-management-service/src/main/java/ch/batbern/events/mailcenter/inbound/InboundMailStore.java
- services/event-management-service/src/main/java/ch/batbern/events/mailcenter/variables/MailVariable.java
- services/event-management-service/src/main/java/ch/batbern/events/service/EmailTemplateSeedService.java
- services/event-management-service/src/main/java/ch/batbern/events/service/InboundReplyProcessor.java
- (new) services/event-management-service/src/main/resources/db/migration/V140__mail_message_reply_to.sql
- (new) services/event-management-service/src/main/resources/email-templates/mailcenter-assigned-de.html
- (new) services/event-management-service/src/main/resources/email-templates/mailcenter-assigned-en.html
- services/event-management-service/src/test/java/ch/batbern/events/forwarding/EmailForwardingListenerTest.java
- (new) services/event-management-service/src/test/java/ch/batbern/events/mailcenter/ConversationReplyIntegrationTest.java
- (new) services/event-management-service/src/test/java/ch/batbern/events/mailcenter/ConversationServiceAssignmentTest.java
- services/event-management-service/src/test/java/ch/batbern/events/mailcenter/MailGoldenTest.java
- (new) services/event-management-service/src/test/java/ch/batbern/events/mailcenter/inbound/TokenReplyRoutingIntegrationTest.java
- (new) services/event-management-service/src/test/resources/mail-golden/mailcenter-assigned-de.html
- (new) services/event-management-service/src/test/resources/mail-golden/mailcenter-assigned-en.html
- (new) shared-kernel/src/main/java/ch/batbern/shared/mail/ComposedMail.java
- shared-kernel/src/main/java/ch/batbern/shared/mail/OutboundMail.java
- shared-kernel/src/main/java/ch/batbern/shared/service/EmailService.java
- (new) shared-kernel/src/test/java/ch/batbern/shared/service/EmailServiceComposedMailTest.java
- (new) web-frontend/e2e/organizer/conversation-reply.spec.ts
- web-frontend/public/locales/de/common.json
- web-frontend/public/locales/de/events.json
- web-frontend/public/locales/en/common.json
- web-frontend/public/locales/en/events.json
- web-frontend/public/locales/es/common.json
- web-frontend/public/locales/es/events.json
- web-frontend/public/locales/fi/common.json
- web-frontend/public/locales/fi/events.json
- web-frontend/public/locales/fr/common.json
- web-frontend/public/locales/fr/events.json
- web-frontend/public/locales/gsw-BE/common.json
- web-frontend/public/locales/gsw-BE/events.json
- web-frontend/public/locales/it/common.json
- web-frontend/public/locales/it/events.json
- web-frontend/public/locales/ja/common.json
- web-frontend/public/locales/ja/events.json
- web-frontend/public/locales/nl/common.json
- web-frontend/public/locales/nl/events.json
- web-frontend/public/locales/rm/common.json
- web-frontend/public/locales/rm/events.json
- web-frontend/src/components/organizer/Admin/EmailTemplateEditModal.tsx
- web-frontend/src/components/organizer/Admin/EmailTemplatesTab.test.tsx
- web-frontend/src/components/organizer/Admin/EmailTemplatesTab.tsx
- web-frontend/src/components/organizer/EventPage/communications/ConversationDetail.tsx
- (new) web-frontend/src/components/organizer/EventPage/communications/ConversationReplyBox.tsx
- (new) web-frontend/src/components/organizer/EventPage/communications/__tests__/ConversationReplyBox.test.tsx
- web-frontend/src/components/organizer/EventPage/communications/__tests__/NeedsAnswerSection.test.tsx
- web-frontend/src/hooks/useMailConversations/useMailConversations.ts
- web-frontend/src/services/mailConversationService.ts
- web-frontend/src/types/generated/event-mail-api.types.ts
- web-frontend/src/types/generated/event-newsletter-api.types.ts

PR 2:
- _bmad-output/implementation-artifacts/16-6-answer-from-mail-center.md
- _bmad-output/implementation-artifacts/sprint-status.yaml
- docs/api/_shared.openapi.yml
- docs/api/event-mail-api.openapi.yml
- docs/architecture/06-backend-architecture.md
- docs/architecture/08-operations-security.md
- docs/operations/runbooks.md
- docs/user-guide/appendix/changelog.md
- docs/user-guide/features/event-mail.md
- infrastructure/lib/stacks/inbound-email-stack.ts
- infrastructure/test/unit/inbound-email-stack.test.ts
- services/event-management-service/src/main/java/ch/batbern/events/domain/EmailForwardingOutcome.java
- services/event-management-service/src/main/java/ch/batbern/events/domain/MailMessage.java
- services/event-management-service/src/main/java/ch/batbern/events/forwarding/AliasMailProcessor.java
- services/event-management-service/src/main/java/ch/batbern/events/forwarding/EmailForwardingService.java
- services/event-management-service/src/main/java/ch/batbern/events/forwarding/ForwardingMetrics.java
- services/event-management-service/src/main/java/ch/batbern/events/mailcenter/ConversationAssignmentNotifier.java
- (new) services/event-management-service/src/main/java/ch/batbern/events/mailcenter/ConversationAttachmentService.java
- (new) services/event-management-service/src/main/java/ch/batbern/events/mailcenter/ConversationLinks.java
- (new) services/event-management-service/src/main/java/ch/batbern/events/mailcenter/ConversationNotices.java
- (new) services/event-management-service/src/main/java/ch/batbern/events/mailcenter/MailCenterSwitch.java
- services/event-management-service/src/main/java/ch/batbern/events/mailcenter/MailConversationController.java
- (new) services/event-management-service/src/main/java/ch/batbern/events/mailcenter/inbound/InboundAttachmentStore.java
- services/event-management-service/src/main/java/ch/batbern/events/mailcenter/inbound/InboundMail.java
- services/event-management-service/src/main/java/ch/batbern/events/mailcenter/inbound/InboundMailCenter.java
- services/event-management-service/src/main/java/ch/batbern/events/mailcenter/inbound/InboundMailParser.java
- services/event-management-service/src/main/java/ch/batbern/events/repository/MailConversationRepository.java
- services/event-management-service/src/main/java/ch/batbern/events/scheduler/MailMessageRetentionScheduler.java
- services/event-management-service/src/main/java/ch/batbern/events/service/InboundReplyProcessor.java
- (new) services/event-management-service/src/main/resources/db/migration/V142__email_forwarding_log_notified.sql
- (new) services/event-management-service/src/main/resources/email-templates/mailcenter-new-mail-de.html
- (new) services/event-management-service/src/main/resources/email-templates/mailcenter-new-mail-en.html
- (new) services/event-management-service/src/test/java/ch/batbern/events/forwarding/AliasMailProcessorSwitchTest.java
- services/event-management-service/src/test/java/ch/batbern/events/forwarding/EmailForwardingListenerTest.java
- services/event-management-service/src/test/java/ch/batbern/events/forwarding/EmailForwardingServiceTest.java
- (new) services/event-management-service/src/test/java/ch/batbern/events/mailcenter/MailCenterSwitchTest.java
- services/event-management-service/src/test/java/ch/batbern/events/mailcenter/MailConversationControllerIntegrationTest.java
- services/event-management-service/src/test/java/ch/batbern/events/mailcenter/MailGoldenTest.java
- (new) services/event-management-service/src/test/java/ch/batbern/events/mailcenter/inbound/InboundAttachmentStoreTest.java
- services/event-management-service/src/test/java/ch/batbern/events/mailcenter/inbound/InboundMailParserTest.java
- services/event-management-service/src/test/java/ch/batbern/events/mailcenter/inbound/MailConversationRetentionIntegrationTest.java
- services/event-management-service/src/test/java/ch/batbern/events/service/InboundEmailListenerServiceTest.java
- (new) services/event-management-service/src/test/java/ch/batbern/events/service/InboundReplyProcessorSwitchTest.java
- (new) services/event-management-service/src/test/resources/mail-golden/mailcenter-new-mail-de.html
- (new) services/event-management-service/src/test/resources/mail-golden/mailcenter-new-mail-en.html
- web-frontend/public/locales/de/events.json
- web-frontend/public/locales/en/events.json
- web-frontend/public/locales/es/events.json
- web-frontend/public/locales/fi/events.json
- web-frontend/public/locales/fr/events.json
- web-frontend/public/locales/gsw-BE/events.json
- web-frontend/public/locales/it/events.json
- web-frontend/public/locales/ja/events.json
- web-frontend/public/locales/nl/events.json
- web-frontend/public/locales/rm/events.json
- web-frontend/src/components/organizer/EventPage/communications/ConversationDetail.tsx
- web-frontend/src/components/organizer/EventPage/communications/__tests__/NeedsAnswerSection.test.tsx
- web-frontend/src/services/mailConversationService.ts
- web-frontend/src/types/generated/event-mail-api.types.ts

## Change Log

- 2026-11-01: Story drafted from Epic 16 Story 16.6.
- 2026-11-02: PR 1 (Tasks 1–6) implemented: answer, token replies on both paths, reassign notification.
- 2026-11-02: PR 2 implemented: attachments kept (25 MB), notify-instead-of-forward switch (off), runbook; status review.
- 2026-11-02: Live via #1189 (merge 4c83dbcb, 22:40Z); production EMS image c62cc3f migrated to V142, no checksum mismatch. Status done. No inbound mail yet since the deploy, so the threading metrics are not observed yet.
