# Story 16.7: Global "Mail" navigation, mail without an event, and spam

Status: ready-for-dev

## Story

As an **organizer**,
I want **one "Mail" entry in the top navigation with everything that needs an answer across all events, the mail that belongs to no event, and the spam**,
so that **I can see at a glance whether anything is waiting, put an `info@` mail on the right event, and trust that spam and viruses never reach "Needs an answer"**.

> Source: `docs/prd/epic-16-mail-center.md` → **Story 16.7** (with the API note, owner decision 2026-10-31: no second mail resource; sent mail across events is `GET /mails` with the same schema and filters as `GET /events/{code}/mails`, plus `eventCode` and "no event").
> Depends on **16.2a** (conversations, threader, classifier, counts) and in practice **16.2b** (no-event conversations exist only from there). Contains a **CDK change** (SES spam and virus scanning).
> **`GET /mails` is introduced by whichever of 16.7 and 16.10 is built first.** The contract is defined here (Task 3); if 16.10 is built first, it builds Task 3 and this story reuses it.
> **UX reference:** `docs/ux/mail-center-spec.md` ("Global 'Mail'"), `docs/ux/mail-center-prototype.html` (badge, "Offen", "Ohne Event · info@, support@" with "Event zuordnen", "Spam"), `docs/ux/mail-center-mobile.html`.

## Acceptance Criteria

1. **AC1 (assign to event)** — GIVEN a conversation without an event (e.g. an `info@` mail), WHEN an organizer assigns it to BATbern{N}, THEN it and all its messages carry event BATbern{N}: it leaves "No event" and appears in that event's "Needs an answer". Assigning back to "no event" reverses it. Keys do not change (`mail_key` `general-{n}` stays; it is an id, not a label).
2. **AC2 (badge)** — The "Mail" entry shows a badge with the number of `OPEN` conversations across all events and without event. GIVEN zero, the badge is hidden. It refreshes at least every 60 s and after any change made in this browser.
3. **AC3 (spam)** — GIVEN an inbound mail with `X-SES-Spam-Verdict: FAIL`, WHEN it arrives, THEN it is stored as an inbound message flagged spam, appears only in the Spam list, opens no conversation and is not counted as open. "Not spam" clears the flag and runs the 16.2a/16.2b threading, so it lands where it belongs (an event's "Needs an answer" or "No event").
4. **AC4 (virus)** — GIVEN `X-SES-Virus-Verdict: FAIL`, THEN the mail is neither stored nor forwarded nor opened; it is counted (`VIRUS`) and logged with the S3 key only.
5. **AC5 (forwarding of spam and virus)** — Neither a virus-FAIL nor a spam-FAIL mail is forwarded to private inboxes any more (recommendation, see open questions; virus is not negotiable).
6. **AC6 (scanning on)** — The three SES receipt rules (`RouteReplies`, `RouteForwardingNamed`, `RouteForwardingCatchAll`) have `scanEnabled: true`. CDK unit test asserts it. Only `FAIL` is acted on; `PASS`, `GRAY`, `PROCESSING_FAILED` and a missing header are treated as clean.
7. **AC7 (spam retention)** — Spam is deleted 30 days after receipt by the existing `MailMessageRetentionScheduler` (extended, not a second job). The 12-month rules stay for everything else.
8. **AC8 (three lists, one page)** — Route `/organizer/mail`, organizer-only, nav entry "Mail" after "Events". Sections in this order: **Open** (OPEN and WAITING across events, each row shows its event code), **No event** (`info@`, `support@`; each row has an "Assign to event" control), **Spam** (collapsed, count in the header, "Not spam" per row). Rows and inline details are the 16.2a components; mobile opens the full-screen dialog. Empty sections show one line, never an empty table.
9. **AC9 (sent across events)** — `GET /mails` returns "what went out" across all events with the same schema and filters as `GET /events/{eventCode}/mails`, plus `eventCode` and `noEvent=true`; `GET /mails/{mailKey}` returns one mail. On the page, a fourth, collapsed section "What went out (all events)" lists it with an event filter (see open question; drop the section, keep the API, if the owner prefers the prototype's three lists).
10. **AC10 (API)** — Spec first in `docs/api/event-mail-api.openapi.yml`, every operation `hasRole('ORGANIZER')` and `no-store`:
    - `GET /mails`, `GET /mails/{mailKey}` (AC9; parameter `direction` `OUT|IN|ANY`, default `OUT`, also added to `GET /events/{eventCode}/mails` with the same default so 16.3's contract is unchanged; parameter `spam` (boolean, default false));
    - `PATCH /mails/{mailKey}` with `{ spam: false }` only (AC3; anything else 400);
    - `GET /conversations` (filters `status`, `eventCode`, `noEvent`);
    - `GET /conversations/counts` → `{ open, waiting, noEvent, spam }` for the badge and the section headers;
    - `PATCH /conversations/{conversationKey}` accepts `eventCode` (existing event, or `null` for no event).
11. **AC11** — New UI keys in all 10 locales. Synthetic fixtures; no real mail in tests; spam/virus paths verified on local dev with the simulator's `headers` field.

## Tasks / Subtasks

### PR 1 — Infrastructure (deploy first, so the headers exist when PR 2 lands)

- [ ] **Task 1 — SES scanning** (AC: 6)
  - [ ] RED: `infrastructure/test/unit/inbound-email-stack.test.ts`: every `AWS::SES::ReceiptRule` has `Rule.ScanEnabled: true`.
  - [ ] `infrastructure/lib/stacks/inbound-email-stack.ts`: `scanEnabled: true` on the rules at lines 190, 203 and 224 (`ReceiptRuleOptions.scanEnabled`, aws-cdk-lib `aws-ses/lib/receipt-rule.d.ts:70`). In-place property update of existing rules, no replacement; check the synth diff shows `Modify` only.
  - [ ] Doc Drift Check: `infrastructure/` maps to `docs/architecture/08-operations-security.md` and `06-backend-architecture.md` (`.github/doc-drift-mappings.yml:71–74`). Add the scanning note to `08-operations-security.md` in the same PR.
  - [ ] No Lambda is touched, so no handler test is needed. Run the CDK tests with `./gradlew --stop`, `/tmp/cdk.out*` cleared and `--maxWorkers=2` (rack OOM, 16.0 Debug Log).
  - [ ] Deploy-clean: a PR to `develop` deploys to production. Ask the owner before opening it.

### PR 2 — Backend

- [ ] **Task 2 — Spam and virus in the classifier** (AC: 3–5; TDD first)
  - [ ] Migration `V{next}__mail_spam.sql` (re-check the head; 16.8/16.9 may have added migrations): `mail_message.is_spam BOOLEAN NOT NULL DEFAULT FALSE`, partial index `WHERE is_spam`; extend the `mail_automatic_event.kind` CHECK with `SPAM`, `VIRUS`.
  - [ ] `InboundClassifier` (16.2a): `VIRUS` before everything; `SPAM` before `HUMAN`. Fixtures with the two headers (synthetic). `InboundMailCenter`: virus → count only; spam → store `IN` with `is_spam = true`, no conversation, count `SPAM`.
  - [ ] Forwarding: `EmailForwardingService.forward` (line 87) and `forwardUnrecognizedReply` (line 233) drop `VIRUS`/`SPAM` FAIL with new results/outcomes (`DROPPED_VIRUS`, `DROPPED_SPAM`; `EmailForwardingOutcome` + migration extending the CHECK, pattern V131; metrics). Calendar replies carry the verdict headers too: check verdicts before the calendar branch (line 88).
- [ ] **Task 3 — `GET /mails` and `GET /mails/{mailKey}`** (AC: 9, 10) — skip if 16.10 already built it; then only add `spam`.
  - [ ] `MailMessageQueryService`: make the event optional. Today every query requires it (`requireEvent`, line 173) and filters `m.event_code = :eventCode AND m.direction = 'OUT'` (line 62), and the newsletter backfill joins on the event code (line 80). Change to `(CAST(:eventCode AS TEXT) IS NULL OR …)`, `:noEvent`, `:direction`, `:spam`, and exclude conversation answers from the OUT list (`conversation_id IS NULL`, 16.6). The event endpoint keeps its 404 for an unknown event.
  - [ ] New controller methods in `EventMailController` or a sibling `MailController` (same generated interface if the tag is shared). `GET /mails/{mailKey}` works for any key (`mail_key` is globally unique, V135; `newsletter-{sendId}` too).
  - [ ] Integration tests: across events, `eventCode` filter, `noEvent`, `direction` default OUT, `ANY`, spam excluded by default, 403.
- [ ] **Task 4 — Conversations across events** (AC: 1, 2, 10)
  - [ ] `GET /conversations` and `/conversations/counts` in `ConversationQueryService`; `PATCH` with `eventCode`: validate the event exists, update the conversation and its messages' `event_code` in one transaction, `updated_by`.
  - [ ] `PATCH /mails/{mailKey}` `{ spam: false }`: clear the flag, run `ConversationThreader` on the stored message (it has every header the threader needs, 16.2a columns), count nothing new.
  - [ ] Integration tests for AC1 (both directions), AC3 (not spam → event conversation and → no-event conversation).
- [ ] **Task 5 — Retention** (AC: 7) — `MailMessageRetentionScheduler.purgeExpired`: `DELETE FROM mail_message WHERE is_spam AND created_at < now() - 30 days` before the 12-month rules. Integration test.
- [ ] **Task 6 — Local dev check** (AC: 11) — simulator (16.2a Task 7) with `headers: { "X-SES-Spam-Verdict": "FAIL" }` and the virus header; not spam → lands on the event. Rows deleted afterwards.
- [ ] Bruno: `GET /mails`, `GET /conversations`, `GET /conversations/counts`, 403/404 only. No PATCH (Bruno runs against production).

### PR 3 — Frontend

- [ ] **Task 7 — Navigation entry with badge** (AC: 2, 8)
  - [ ] `config/navigationConfig.ts`: `NavigationItem` (lines 20–27) has no badge; add an optional `badge?: 'mailOpen'` and the item `{ labelKey: 'navigation.mail', path: '/organizer/mail', roles: ['organizer'], badge: 'mailOpen' }` after Events (line 41). `NavigationMenu.tsx` and `AppHeader.tsx` render a MUI `Badge` (hidden at 0; `AppHeader.tsx:191` shows the pattern used for notifications) from a hook `useOpenConversationCount` (`refetchInterval` 60 s, invalidated by every conversation mutation).
  - [ ] Route in `App.tsx` next to the other organizer routes (`/organizer/users/*` at line 691 is the pattern: `ProtectedRoute` + `AuthLayout`).
- [ ] **Task 8 — Mail page** (AC: 1, 3, 8, 9)
  - [ ] `components/organizer/MailCenter/MailCenterPage.tsx`: the sections of AC8/AC9, max width about 1100 px like the event page. Reuse `NeedsAnswerSection`/`ConversationRow`/`ConversationDetail` (16.2a) with an `eventCode?` prop (row shows the event when absent) and `MailSentList` with `eventCode?` (global list via `GET /mails`). `MailDetail` gets `eventCode?`: without it, it loads `GET /mails/{mailKey}`.
  - [ ] "Assign to event": a select of events (existing `hooks/useEvents.ts`), "— no event —" first; PATCH; the row moves on success.
  - [ ] Spam: collapsed accordion with count; row shows from, subject, date; inline body; "Not spam" button.
  - [ ] Vitest: badge hidden at 0, sections and empty states, assign moves the row, not spam, mobile dialog. Playwright (organizer project, local dev): read-only navigation spec, no horizontal scroll at 375 px.
  - [ ] i18n: `navigation.mail` (common namespace, where the other `navigation.*` keys live) and `mailCenter.*` in all 10 locales.
- [ ] **Task 9 — Docs** — user guide `docs/user-guide/features/event-mail.md` (global page, assigning, spam), `06-backend-architecture.md` (spam/virus handling, `GET /mails`).

## Dev Notes

### SES scanning (verified in CDK code, SES behaviour from AWS documentation)

- The rules are defined in `inbound-email-stack.ts`: replies (190–200), named aliases (203–220), domain catch-all (224–233). None sets `scanEnabled` today, so SES adds no verdict headers and every phishing mail to `info@` is forwarded to the organizers (the prototype shows one: "1 Mail an info@ (FedEx-Phishing)").
- With scanning on, SES writes `X-SES-Spam-Verdict` and `X-SES-Virus-Verdict` into the stored message (values `PASS`, `FAIL`, `GRAY`, `PROCESSING_FAILED`). The mail is still delivered to S3; acting on the verdict is our code's job (Task 2).

### `GET /mails` is not a second resource

- Same schema (`MailMessagePage`, `MailMessageSummary`, `MailMessageDetail`), same query service, same filters; only the event moves from the path to an optional filter. 16.10 adds participant filters to the same operation. One query builder for all three entry points, so the lists cannot drift.
- `MailMessageSummary.eventCode` (added by 16.2a) is required for rows of the global list; fill it for outbound rows too.

### Conversations moving between events

- `mail_conversation.event_code` and `mail_message.event_code` are plain columns (no FK to `events`, V135). Update both. `mail_key` keeps its prefix; the UI must never parse the event out of a key.
- Retention follows the new event (event date + 12 months) automatically, since the rule reads `event_code` at run time.

### Open questions for the owner

1. Forward spam during the transition? Recommendation: no (AC5). Spam is visible in the Spam list for 30 days, so nothing is lost; forwarding it only trains organizers to click phishing.
2. "What went out (all events)" on the global page (AC9): the prototype (2026-10-30) shows three lists and says sent mail stays per event; the API decision (2026-10-31) introduces `GET /mails`. Recommendation: keep the collapsed fourth section; it costs one component reuse and answers "what did we send this week".

### References

- `docs/prd/epic-16-mail-center.md` (Story 16.7, API note)
- `docs/ux/mail-center-spec.md`, `mail-center-prototype.html`, `mail-center-mobile.html`
- `_bmad-output/implementation-artifacts/16-2a-speaker-conversations.md`, `16-2b-all-alias-conversations.md`, `16-10-mail-tab-user-partner-detail.md`
- `infrastructure/lib/stacks/inbound-email-stack.ts`, `infrastructure/test/unit/inbound-email-stack.test.ts`
- `services/event-management-service/src/main/java/ch/batbern/events/mailcenter/{EventMailController,MailMessageQueryService}.java`
- `services/event-management-service/src/main/java/ch/batbern/events/forwarding/EmailForwardingService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/scheduler/MailMessageRetentionScheduler.java`
- `web-frontend/src/config/navigationConfig.ts`, `web-frontend/src/components/shared/Navigation/{NavigationMenu,AppHeader}.tsx`, `web-frontend/src/App.tsx`

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List

## Change Log

- 2026-11-01: Story drafted from Epic 16 Story 16.7 (incl. the 2026-10-31 API note and SES scanning).
