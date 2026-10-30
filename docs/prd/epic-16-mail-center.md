# Epic 16 — Mail Center

**Author:** John (PM), with Sally (UX) and Winston (Architect) · **Date:** 2026-10-30 · **Status:** APPROVED by the owner 2026-10-30, including the five decisions below
**Source:** party-mode design session 2026-10-29/30, clickable prototype v4 (real data, kept out of the repo, served over Tailscale only), measured SES traffic of the last 6 months.

## Epic goal

The organizer team sees, per event, **what went out, what came back, and what still needs an answer**, without forwarding mail to private inboxes. The event's Communications tab becomes that page. Sending stays where it is today, extended by an own-text option with placeholders and a per-recipient preview.

## Problem

- Outbound mail is not recorded. `EmailService` returns `void`; the SES Message-ID is logged and dropped. Only newsletters (`newsletter_sends` / `newsletter_recipients`) have a history. Speaker, registrant, venue and partner mails leave no trace.
- Inbound mail is either dropped or scattered:
  - `replies@` routes by keyword in `InboundEmailRouter` and discards everything else (#1160). That is the global Reply-To of every transactional mail, so ordinary human answers are lost.
  - Calendar declines are dropped while the waitlist is full (#1161).
  - Alias mail (`info@`, `support@`, `batbern{N}-*@`) is forwarded to the organizers' private inboxes. Who answered, and what, is invisible to the rest of the team.
- `email_forwarding_log` stores outcome and counts only (no sender, subject or body). S3 raw MIME expires after 7 days.

## Measured volume (2026-10-30)

Design small. These are measured, not estimated:

| | |
|---|---|
| Alias forwards (SES `EmailsForwarded` metric) | about 51 in 6 months |
| Inbound, week of 23–30 Oct | about 101 mails, of which about 6 written by a person. The rest: 28 bounces, 25 out-of-office replies, about 40 calendar RSVPs |
| Newsletter | the bulk of outbound, about 1000–1340 recipients per send |

So this is **not a helpdesk**. It is a per-event log with a short "needs an answer" list on top.

## Decisions taken

1. **Build inside EMS** as a `mailcenter` module. No open-source helpdesk (FreeScout, Zammad, Chatwoot were considered): it would not see our automated sends or our domain entities (event, speaker, registrant), and it would duplicate the GDPR footprint.
2. **The moderator is the speakers' contact point** (owner decision 2026-10-29). All speaker mails carry Reply-To `batbern{N}-moderator@`. `batbern{N}-speaker@` is organizer-only and fans out to the speakers. In code, `-moderator@` resolves to `Event.organizerUsername` (the lead organizer), not `SessionUser.MODERATOR`. Recorded on #1160.
3. **The Communications tab becomes the mail page.** Its four sub-tabs (Newsletter, Registrants, Speakers, Venue & Catering) are replaced by a "+ New mail" picker. The existing compose flows open unchanged in a side panel. The separate newsletter history table is dropped; newsletters appear in "What went out".
4. **Unit of work is the conversation.** Statuses: Open, Waiting, Done. One assignee. Default assignee for speaker conversations: the event's moderator.
5. **Own text uses the existing Mustache placeholders** (`{{var}}`, rendered by `EmailService.replaceVariables`). Preview and send use the same render path, so they cannot diverge.

## Information architecture

**Event › Communications** (one column, three sections):

1. **Needs an answer.** Human mail linked to this event, Open or Waiting, with assignee. Usually zero to three items.
2. **What went out.** Every send, newest first: who sent it, to whom (audience + count), subject, delivered / failed counts, and "template" or "own text". A click shows the exact rendered mail, the newsletter included.
3. **Handled automatically** (collapsed). Counts only: accepts, declines, bounces, out-of-office replies, calendar RSVPs. Expandable for the rare check.

**Global top-nav "Mail"**: open items across all events, mail without an event (`info@`, `support@`) with an "assign to event" control, and spam.

## Stories

Prerequisites, already filed and independently shippable before this epic starts: **#1160** (replies@ drops human replies; speaker mails get Reply-To `-moderator@`) and **#1161** (calendar declines dropped while the waitlist is full).

---

### Story 16.1 — Record every outbound mail

**Scope:**
- `mail_message` table (EMS): direction, event code (nullable), audience kind, template key or `own_text`, rendered subject + body, sender (organizer username or `system`), recipient count, SES Message-ID(s), created-at.
- `EmailService` returns the SES Message-ID; callers that represent a user-visible send pass a `MailContext` (event, audience, template key). One row per send, not per recipient; per-recipient delivery stays where it is for the newsletter and is added as a child table for the others.
- SES message tags on every send (`event`, `audience`, `mail_id`). Delivery / bounce / complaint events from the `transactional` and `newsletter` configuration sets update per-recipient status in the DB (today they go only to CloudWatch and a log-only Lambda).

**Acceptance criteria:**
- AC1 — GIVEN any send path that an organizer triggers or that targets an event audience, WHEN it sends, THEN one `mail_message` row exists with the rendered content and the SES Message-ID.
- AC2 — GIVEN a bounce on the transactional configuration set, WHEN SES reports it, THEN the matching recipient row shows "failed" with the bounce type.
- AC3 — ADR-015 send guards still block test-marked content; a blocked send writes no row.
- AC4 — Inventory test: every caller of `EmailService` is either context-tagged or on an explicit allow-list of system mails (verification codes etc.), so a new send path cannot silently skip the record.

---

### Story 16.2 — Capture inbound human mail as conversations

**Depends on:** #1160, 16.1.

**Scope:**
- Inbound from `replies@` and all aliases is stored as `mail_message` (direction `in`) and attached to a `mail_conversation`.
- Threading, in order: per-conversation Reply-To token (`replies+{token}@`), then `In-Reply-To` / `References` against stored Message-IDs, then alias → event (`batbern{N}-*@`). Otherwise the conversation has no event.
- Classification before a conversation is opened: bounce, auto-reply (`Auto-Submitted`, `X-Autoreply`, common OOO patterns), iMIP REPLY, keyword actions already handled by `InboundEmailRouter`. These are counted for "Handled automatically", not opened.
- Forwarding to private inboxes stays on during the transition (see open decision 2).

**Acceptance criteria:**
- AC1 — GIVEN a speaker replies to a speaker mail, WHEN it arrives, THEN it lands in that event's conversation, status Open, assignee = the event's moderator.
- AC2 — GIVEN an out-of-office reply, WHEN it arrives, THEN no conversation is opened and the automatic count increments.
- AC3 — GIVEN mail to `info@` with no event reference, WHEN it arrives, THEN it appears under global Mail › "No event".
- AC4 — Raw MIME needed for display is kept for the retention period (open decision 1), not the current 7 days.

---

### Story 16.3 — Communications tab becomes the mail page

**Depends on:** 16.1 (16.2 for section 1).

**Scope:**
- Replace the sub-tabs of `EventCommunicationsContainer` with the three sections described above.
- "What went out" shows the exact rendered mail, the newsletter included, with delivered / failed counts.
- Status and assignee changes on a conversation (Open, Waiting, Done).
- i18n keys in all 10 locales.

**Acceptance criteria:**
- AC1 — GIVEN an event with a newsletter sent, WHEN the organizer opens Communications, THEN the newsletter appears in "What went out" with recipient, delivered and failed counts, and a click shows the mail as sent.
- AC2 — GIVEN no open conversations, THEN section 1 shows a one-line empty state, not an empty table.
- AC3 — The automatic section is collapsed by default and shows counts only.

---

### Story 16.4 — "+ New mail" picker with the existing flows

**Depends on:** 16.3.

**Scope:**
- A menu with the four audiences of today (Newsletter, Registrants, Speakers, Venue & Catering). Each opens its existing compose flow in a side panel, behaviour unchanged.
- After sending, the new row appears at the top of "What went out".

**Acceptance criteria:**
- AC1 — Every send that was possible from the old sub-tabs is possible from the picker, with the same confirmation step.
- AC2 — The old audience tabs are gone. They were local state, not routes, so no redirect is needed.

---

### Story 16.5 — Own text with placeholders and per-recipient preview

**Depends on:** 16.4.

**Scope:**
- A [Template | Own text] toggle in the compose panel.
- Own text: subject + body, with placeholder chips per audience that insert `{{var}}` at the cursor. Only the variables that exist for that audience are offered, taken from the template variable sets in use today:
  - Newsletter: `eventTitle`, `eventDate`, `eventTime`, `venue`, `registrationLink`, `eventDetailLink`, `eventNumber`. `unsubscribeLink` goes into the footer automatically.
  - Speakers: `speakerName`, `deadline`, `portalLink`, `organizerName`, `eventTitle`, `eventDate`, `daysRemaining`, `organizerEmail`. **New:** `sessionTitle`.
  - Registrants: `recipientName`, `deregistrationUrl`, `eventNumber`, `eventTitle`, `eventDate`, `eventType`.
  - Venue & Catering: `salutation`, `eventDate`, `topicTitle`, `agenda`, `notes`, `organizerSignature`.
- Preview renders the mail in the BATbern layout for one actual recipient at a time, stepping through the list. Substituted values are highlighted. An unknown placeholder is shown in red and blocks Send.
- "Use this template as a starting point" copies a template into own text without changing the template. "Save as template" stores own text as a new template (admin template store).
- Own text goes through the same render service, the same ADR-015 guards and the same confirmation step as templates. The backend validates placeholders again; the frontend check is convenience only.

**Acceptance criteria:**
- AC1 — GIVEN own text with `{{speakerName}}`, WHEN previewing recipient 3 of 8, THEN the preview shows that speaker's name, highlighted.
- AC2 — GIVEN own text with `{{deadlin}}`, THEN the preview marks it red, Send is disabled, and a direct API call is rejected with 400.
- AC3 — GIVEN a newsletter in own text, THEN the sent mail contains a working unsubscribe link even if the author did not insert one.
- AC4 — The rendered body stored by 16.1 equals the preview for the same recipient (byte-equal test).
- AC5 — "What went out" marks the send as "own text".

---

### Story 16.6 — Answer from the mail center

**Depends on:** 16.2, 16.3.

**Scope:**
- Reply inside a conversation. Sent from `noreply@` with display name "{Organizer} via BATbern", Reply-To `replies+{token}@`, threaded headers set.
- The answer is stored in the conversation and visible to the whole team. Status moves to Waiting on send, back to Open on the next inbound.
- Assign to another organizer, with a notification mail to the assignee.

**Acceptance criteria:**
- AC1 — GIVEN an organizer answers a speaker, WHEN the speaker replies again, THEN that reply threads into the same conversation.
- AC2 — The answer shows sender, time and text to every organizer.
- AC3 — Reassigning notifies the new assignee once.

---

### Story 16.7 — Global "Mail" navigation

**Depends on:** 16.2.

**Scope:** top-nav entry with three lists: open conversations across events, mail without an event (with "assign to event"), spam. Badge with the open count.

**Acceptance criteria:**
- AC1 — GIVEN an `info@` mail assigned to BATbern{N}, THEN it moves to that event's Communications page and leaves "No event".
- AC2 — GIVEN zero open items, the badge is hidden.

---

## Decisions taken 2026-10-30 (owner, formerly open)

1. **Retention:** conversations and rendered outbound mail are kept until event date + 12 months, then deleted. Mail without an event: created-at + 12 months. Newsletter per-recipient status unchanged. The privacy documentation gets a note.
2. **Team rule: answer from the mail center.** Alias forwarding to private inboxes stays on during the transition. Once 16.6 is live, forwarding is replaced by a short notification ("new mail for BATbern{N}, open it here") that carries no body.
3. **Sequence:** 16.1, 16.3, 16.4 first (what went out is visible, no inbound change). Then 16.2 + 16.6 for speakers only. Then 16.2 for all aliases, 16.5, 16.7.
4. **Reply-To on `-speaker@` broadcasts** sent by an organizer: `batbern{N}-moderator@`, consistent with decision 2 of the epic.
5. **Registrant replies:** stay with #1160 option 1 until 16.2 covers all aliases.

## Non-goals

- No open/click tracking.
- No general-purpose helpdesk features (SLAs, macros, canned responses beyond templates).
- No email templates beyond DE + EN.
- No change to how the newsletter is delivered.

## Constraints (carry into every story)

- The AWS account is production. No test may send real mail (ADR-015, newsletter incident 2026-07-01). Send-path tests run against local dev, where mail goes to `/dev/mails`.
- Authorization in the services (ADR-008): every mail-center endpoint is `hasRole('ORGANIZER')`.
- Mail bodies are PII. No real data in fixtures, screenshots or issues.
- TDD; integration tests on Testcontainers PostgreSQL. New migrations only (EMS head at the time of writing: V130).
