# Epic 16 — Mail Center

**Author:** John (PM), with Sally (UX) and Winston (Architect) · **Date:** 2026-10-30 · **Status:** APPROVED by the owner 2026-10-30, including the five decisions below
**Source:** party-mode design session 2026-10-29/30, measured SES traffic of the last 6 months, Winston's compose review 2026-10-30.
**UX:** [`docs/ux/mail-center-prototype.html`](../ux/mail-center-prototype.html) (anonymized, open in a browser) and [`docs/ux/mail-center-spec.md`](../ux/mail-center-spec.md). Readiness: [`docs/implementation-readiness-report-epic-16-2026-10-30.md`](../implementation-readiness-report-epic-16-2026-10-30.md).

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

## How to start (read this first, dev agent)

**Read order:** this epic → `_bmad-output/project-context.md` → the story file → `docs/ux/mail-center-spec.md` (UI stories) → the files listed in its Dev Notes. `CLAUDE.md` rules apply (TDD, Testcontainers, PII, Flyway).

**Story order:**
1. 16.0 Part A
2. 16.0 Part B
3. 16.0 Part C (#1162)
4. 16.1a, then 16.1b
5. 16.3 + 16.4
6. 16.8, then 16.9 (story files are written when they come up)

`sprint-status.yaml` lists older `ready-for-dev` stories (11-e-5, 11-g-1, 13-5) ahead of Epic 16. Always start with the explicit story path, never with "next story".

**Branching:**
- The epic and the story files live on `docs/epic-16-mail-center`, which is pushed but not merged to `develop`.
- Until it is merged, cut each story branch from it: `git fetch origin && git switch -c feature/16-0a-replies-reach-a-person origin/docs/epic-16-mail-center`. After it is merged, cut from `origin/develop`.
- One branch and one PR per deployable part:
  - `feature/16-0a-...` (`Fixes #1160`)
  - `feature/16-0b-...` (`Fixes #1161`)
  - `feature/16-0c-...` (`Fixes #1162`)
  - `feature/16-1a-...`, `feature/16-1b-...`
  - `feature/16-3-4-...`
- Conventional commits, scope `mailcenter`, `email`, `forwarding` or `registration`.
- Update the story file (tasks, Dev Agent Record, File List, Status) and `sprint-status.yaml` on the story branch, in the same commits as the code.

**Pushing and PRs:**
- **A PR to `develop` deploys to production (www.batbern.ch) at once, unreviewed.** Opening a PR is a production action.
- Push the branch, then stop and ask the owner before opening the PR. Once he agrees, open it immediately; a branch push alone starts no CI.
- Only `Fixes #N` in the PR body closes an issue. `Refs #N` closes nothing.
- Before pushing, run the affected module's tests: `set -o pipefail; ./gradlew :services:event-management-service:test 2>&1 | tee /tmp/ems-test.log`. The pre-push hook runs the integration tests and needs Docker.

**Mail safety:**
- The AWS account is production. No test, Bruno run or Playwright run may send real mail (ADR-015, incident 2026-07-01).
- Verify send paths on local dev (`make dev-native-up`). Mail lands in `/dev/mails` there.
- Never download production data.

**Migrations:** EMS head was V130 on 2026-10-30. Check again before numbering. Never edit an applied migration.

## Information architecture

**Event › Communications** (one column, three sections):

1. **Needs an answer.** Human mail linked to this event, Open or Waiting, with assignee. Usually zero to three items.
2. **What went out.** Every send, newest first, filterable by audience (Newsletter, Registrants, Speakers, Venue & Catering, Partners): who sent it, to whom (audience + count), subject, delivered / failed counts, and "template" or "own text". A click shows the exact rendered mail, the newsletter included.
3. **Handled automatically** (collapsed). Counts only: accepts, declines, bounces, out-of-office replies, calendar RSVPs. Expandable for the rare check.

**Global top-nav "Mail"**: open items across all events, mail without an event (`info@`, `support@`) with an "assign to event" control, and spam.

## Stories

### Story 16.0 — Stop losing replies and calendar declines (#1160, #1161, #1162)

Story file: `_bmad-output/implementation-artifacts/16-0-stop-losing-replies-and-calendar-declines.md`. Three parts, three PRs.

**Part A (#1160):**
- Speaker mails carry Reply-To `batbern{N}-moderator@`.
- `-speaker@` broadcasts get the same Reply-To.
- Unrecognized mail at `replies@` is forwarded to the organizers instead of discarded. Auto-replies and bounces are filtered.

**Part B (#1161):**
- A calendar "Declined" from a registrant triggers the existing deregistration-link mail. The registration is cancelled only when they click it.
- New registration invites carry a stable UID per registration, so declines can be matched.
- A second decline within 7 days sends no second mail (`registrations.decline_link_sent_at`).

**Part C (#1162):**
- A reply whose first line contains "cancel" or "absagen" no longer cancels immediately. It sends the deregistration-link mail. Keywords act only on short lines (at most 3 words); longer sentences are forwarded as human mail.

---

### Story 16.1a — Record every outbound mail

Story file: `_bmad-output/implementation-artifacts/16-1a-record-outbound-mail.md`.

**Scope:**
- `mail_message` + `mail_recipient` tables in EMS.
- A `MailContext` passed by callers and an `OutboundMailRecorder` SPI in the shared kernel, called after a successful SES send with the Message-ID.
- Send-group rules: one group per organizer request, per scheduler run × event × template, or per individual transactional mail.
- Readable `mail_key` (`{eventCode}-{n}`), source-scan guard test, retention job, privacy note.
- Newsletter: one `mail_message` linked to `newsletter_sends`; per-recipient status stays in `newsletter_recipients`.

**Acceptance criteria (summary):** every EMS event/organizer send is recorded with Message-IDs; test-marked sends write nothing; a new send path without context fails the build; retention deletes at event date + 12 months.

---

### Story 16.1b — Delivery status from SES events

Story file: `_bmad-output/implementation-artifacts/16-1b-delivery-status.md`. Depends on 16.1a. Contains a CDK change.

**Scope:** an SQS queue on the existing transactional SES events topic; an EMS listener sets `DELIVERED` / `FAILED` (with reason) per recipient; statuses never move backwards; newsletter suppression untouched.

---

### Story 16.2 — Capture inbound human mail as conversations

**Depends on:** 16.0 Part A, 16.1a.

**Scope:**
- Inbound from `replies@` and all aliases is stored as `mail_message` (direction `in`) and attached to a `mail_conversation`.
- Threading, in order: per-conversation Reply-To token (`replies+{token}@`), then `In-Reply-To` / `References` against stored Message-IDs, then alias → event (`batbern{N}-*@`). Otherwise the conversation has no event.
- Classification before a conversation is opened: bounce, auto-reply (`Auto-Submitted`, `X-Autoreply`, common OOO patterns), iMIP REPLY, keyword actions already handled by `InboundEmailRouter`. These are counted for "Handled automatically", not opened.
- Forwarding to private inboxes stays on during the transition (owner decision 2).
- **UI: the "Needs an answer" section** on the event's Communications page (moved here from 16.3 by the readiness check): list of Open/Waiting conversations with assignee, conversation view (read only), status and assignee changes. One-line empty state.

**Acceptance criteria:**
- AC1 — GIVEN a speaker replies to a speaker mail, WHEN it arrives, THEN it lands in that event's conversation, status Open, assignee = the event's moderator.
- AC2 — GIVEN an out-of-office reply, WHEN it arrives, THEN no conversation is opened and the automatic count increments.
- AC3 — GIVEN mail to `info@` with no event reference, WHEN it arrives, THEN it appears under global Mail › "No event".
- AC4 — Raw MIME needed for display is kept for the retention period (owner decision 1), not the current 7 days.
- AC5 — GIVEN no open conversations, THEN "Needs an answer" shows a one-line empty state, not an empty table.
- AC6 — An organizer can set status (Open, Waiting, Done) and assignee; the change is visible to every organizer.

---

### Story 16.3 — Communications tab becomes the mail page

**Depends on:** 16.1a (16.1b for delivered/failed counts of non-newsletter mail; without it they show as sent).

**Scope:**
- Replace the sub-tabs of `EventCommunicationsContainer` with "What went out" and "Handled automatically", leaving room for "Needs an answer" (built in 16.2).
- "What went out" shows the exact rendered mail, the newsletter included, with delivered / failed counts.
- i18n keys in all 10 locales.

**Acceptance criteria:**
- AC1 — GIVEN an event with a newsletter sent, WHEN the organizer opens Communications, THEN the newsletter appears in "What went out" with recipient, delivered and failed counts, and a click shows the mail as sent.
- AC2 — The automatic section is collapsed by default and shows counts only (failed deliveries in this story; accepts, declines and out-of-office counts come with 16.2).

---

### Story 16.4 — "+ New mail" picker with the existing flows

**Depends on:** 16.3.

**Scope:**
- A menu with the four audiences of today (Newsletter, Registrants, Speakers, Venue & Catering) plus **Partners** (owner request 2026-10-30). Each opens its existing compose flow in a side panel, behaviour unchanged. Partners opens a picker of this event's partner meetings and the existing invite / cancellation action (`MeetingDetailPanel`).
- After sending, the new row appears at the top of "What went out".

**Acceptance criteria:**
- AC1 — Every send that was possible from the old sub-tabs is possible from the picker, with the same confirmation step.
- AC2 — The old audience tabs are gone. They were local state, not routes, so no redirect is needed.

---

### Story 16.5 — Own text with placeholders and per-recipient preview

**Depends on:** 16.8 (built once, inside the shared composer, not four times).

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
- AC4 — The rendered body stored by 16.1a equals the preview for the same recipient (byte-equal test).
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
- AC4 (owner decision 2, final task) — Once 16.6 is live and the owner gives the go, alias forwarding to private inboxes is replaced by a body-less notification ("new mail for BATbern{N}, open it here", link to the conversation). Behind a runtime flag, so it can be switched back.

---

### Story 16.7 — Global "Mail" navigation

**Depends on:** 16.2.

**Scope:** top-nav entry with three lists: open conversations across events, mail without an event (with "assign to event"), spam. Badge with the open count.

**Spam source:** enable SES spam and virus scanning on the inbound receipt rules (`ScanEnabled: true`, CDK) and classify by the `X-SES-Spam-Verdict` / `X-SES-Virus-Verdict` headers in 16.2's classifier. Virus verdict FAIL is dropped and counted, never shown. Spam FAIL goes to the spam list, not to "Needs an answer". Spam is kept 30 days, then deleted.

**Acceptance criteria:**
- AC1 — GIVEN an `info@` mail assigned to BATbern{N}, THEN it moves to that event's Communications page and leaves "No event".
- AC2 — GIVEN zero open items, the badge is hidden.
- AC3 — GIVEN an inbound mail with `X-SES-Spam-Verdict: FAIL`, THEN it appears only under spam, and "not spam" moves it to the right list.

---

### Story 16.8 — One shared composer for all audiences

**Depends on:** 16.1a, 16.4. Architecture review: Winston, 2026-10-30.

**Why:** the five compose flows are one flow written five times, and they have drifted apart (verified in code):

| Audience | Preview | Test send to organizers | Recipients |
|---|---|---|---|
| Newsletter | server-rendered | yes (`testMode`), but only to organizers who are also subscribers | all subscribers |
| Registrants | server-rendered with **sample** data, not the real recipient | no | all active registrants |
| Speakers | **none** | no | by status; the frontend loops one request per speaker |
| Venue & Catering | server-rendered per recipient | no | role checkboxes, first role To, rest Cc |
| Partners | none; subject and body are hard-coded Java | no | all partner and organizer users in **one To: line** |

The speaker reminder templates **are** editable in the admin template editor. The speaker flow lacks template choice and preview: the key (`speaker-reminder-{response,content}-tier{1,2,3}`) is derived in code from reminder type and an auto-detected tier, so the organizer never sees which of the six goes out. Partners are the one audience with no admin template at all.

**Scope:**
- **Frontend:** one `MailComposer` drawer, the same steps for every audience:
  1. template or own text (own text arrives with 16.5);
  2. language;
  3. recipients, shown by the server;
  4. preview stepping through the real recipients;
  5. **[Send to organizers]** (test) or **[Send to N recipients]**, behind one confirm dialog.
- **Audience adapters:** each audience contributes only a small adapter: template category, extra fields (reminder type, venue roles, meeting picker), async or sync.
- **Backend:** one API in EMS `mailcenter`:
  - `POST /events/{code}/mails/preview` returns HTML, subject, recipient i/n and unknown placeholders;
  - `POST /events/{code}/mails` with `target: RECIPIENTS | ORGANIZERS`;
  - `GET /events/{code}/mails/{mailKey}`, which also serves the send progress.
- **Strategy:** audience logic sits behind `AudienceAdapter { resolveRecipients, variables, allowedVariables, allowedCategories, deliver }`. One render path makes preview equal to sent. One placeholder check, one `MailContext`, one `hasRole('ORGANIZER')`.
- Adapters reuse what exists:
  - the newsletter async job and `newsletter_sends` progress/retry;
  - the registrant one-shot guard;
  - the speaker per-recipient send, with the loop moved server-side;
  - the venue To plus Cc.
- **Test send:** `target=ORGANIZERS` renders the mail for the recipient currently previewed and sends it to all ORGANIZER-role users with a `[TEST]` subject prefix. It is recorded with `is_test=true` and hidden from "What went out" by default. The unsubscribe link is a dummy.
- **Speakers:** the template list shows all six reminder templates. The auto-detected tier is preselected and can be overridden.
- **Clean-up:** after one release, delete `SpeakerBulkComms`, `EventRegistrantNoticesTab`, the body of `VenueCoordinationComposer`, the newsletter compose section, and the old preview/send endpoints and specs.

**Acceptance criteria:**
- AC1 — Every audience offers template choice, preview of the real recipient (stepping ‹ ›), test send to organizers and send to recipients, with the same layout.
- AC2 — The preview for recipient i is byte-equal to the mail recipient i receives (one render path, tested).
- AC3 — "Send to organizers" reaches every organizer, whether or not they subscribe, and never a real recipient.
- AC4 — The newsletter keeps its progress display and retry; registrants keep the one-shot guard.
- AC5 — No Playwright or Bruno test calls the send endpoint against staging (`@sends-real-email` guard); send tests run against local dev only.

**Decisions taken (owner can overrule):**
- Speaker tier: the auto-detected tier is preselected and can be overridden.
- Registrant one-shot guard for own text: one own-text send per event and subject. A second send needs an explicit "send again" confirmation.

---

### Story 16.9 — Partner meeting mails in the mail center

**Depends on:** 16.1a, 16.8. Owner request 2026-10-30.

**Scope:**
- **Sent:** partner-coordination implements the 16.1a `OutboundMailRecorder` SPI with an HTTP recorder. It posts to EMS `POST /internal/mail-messages` (no JWT, not routed by the gateway; same pattern as EMS → partner `/internal/partner-meetings/rsvps`). It is best-effort: failures are logged and not retried.
  - Partner invites and cancellations appear in the event's "What went out", audience Partners, with the RSVP counts from `PartnerMeetingRsvpPanel` alongside.
  - The partner meeting page shows the same list, filtered to that meeting.
- **New mail:** "+ New mail → Partners" offers two paths:
  - (a) meeting invite or cancellation through the existing partner-service endpoint, because it owns the ICS UID, SEQUENCE and RSVPs;
  - (b) a plain partner mail through a 16.8 Partners adapter, with recipients = PARTNER-role users via CUMS and a new template category PARTNER in the admin store.
- **Inbound:** mail to `partner@` and meeting RSVPs belong to the meeting's event. With 16.2 they show under that event; RSVPs are counted under "Handled automatically".

**Acceptance criteria:**
- AC1 — GIVEN a partner meeting invite is sent, THEN it appears in the event's "What went out" within one minute, with recipient count and RSVP summary.
- AC2 — GIVEN EMS is unreachable, THEN the invite is still sent, and the missing record is logged as a warning.
- AC3 — A plain partner mail supports template, preview, test send to organizers and send, like every other audience.

**Out of scope, recorded:**
- The invite body stays hard-coded in Java. Moving it into the admin store needs a render call from the partner service to EMS. Follow-up.
- All partners in one To: line expose their addresses to each other. Owner decision (#1163, 2026-10-31): keep it as it is; 16.9 does not change the recipient line.

---

## Decisions taken 2026-10-30 (owner, formerly open)

1. **Retention:** conversations and rendered outbound mail are kept until event date + 12 months, then deleted. Mail without an event: created-at + 12 months. Newsletter per-recipient status unchanged. The privacy documentation gets a note.
2. **Team rule: answer from the mail center.** Alias forwarding to private inboxes stays on during the transition. Once 16.6 is live, forwarding is replaced by a short notification ("new mail for BATbern{N}, open it here") that carries no body.
3. **Sequence:**
   1. 16.0 (#1160, #1161, #1162).
   2. 16.1a, 16.1b, 16.3, 16.4: what went out becomes visible, no inbound change.
   3. 16.8 shared composer, then 16.9 partners (added 2026-10-30).
   4. 16.2 + 16.6 for speakers only.
   5. 16.2 for all aliases, 16.5, 16.7.
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
