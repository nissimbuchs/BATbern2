# Story 16.3: Communications tab becomes the mail page

Status: done

## Story

As an **organizer**,
I want **the event's Communications tab to show everything that went out for this event, newest first, with the exact mail and its delivery result**,
so that **I can answer "did the speakers get the slides mail?" in one click instead of asking the team**.

> Source: `docs/prd/epic-16-mail-center.md` → **Story 16.3**. Depends on 16.1a (16.1b for delivered/failed of non-newsletter mail).
> **Scope:** "Needs an answer" is built by 16.2 (epic updated by the readiness check). This story builds "What went out" and "Handled automatically" and leaves room for it at the top.
> **UX reference:** `docs/ux/mail-center-prototype.html` (anonymized, Tailwind) and `docs/ux/mail-center-spec.md`. The prototype is a layout reference only: build with the organizer area's MUI components and theme.

## Acceptance Criteria

1. **AC1** — GIVEN an event with a newsletter sent, WHEN the organizer opens Communications, THEN "What went out" lists it with sender, audience, recipient count, delivered and failed counts, and a click shows the mail as sent.
2. **AC2** — GIVEN a speaker or registrant send recorded by 16.1a, THEN it appears in the same list with the same fields and is marked "Template" or "Own text".
3. **AC3** — "Handled automatically" is collapsed by default and shows counts only. In this story it shows failed deliveries (bounces, rejects) for this event. Accept/decline/out-of-office counts are added by 16.2.
4. **AC4** — GIVEN failed recipients on a send, WHEN the organizer expands that send, THEN the failed addresses and the reason are listed.
5. **AC5** — New read endpoint `GET /api/v1/events/{eventCode}/mail-messages` (paged, newest first) and `GET /api/v1/events/{eventCode}/mail-messages/{mailKey}` (with body and recipients), both `hasRole('ORGANIZER')`, ADR-013 conventions, in a new spec `docs/api/event-mail-api.openapi.yml`; frontend types regenerated.
6. **AC6** — Sends made before 16.1a shipped are not shown, except newsletters, which are backfilled from `newsletter_sends` by the endpoint (read-time union, no data migration).
7. **AC7** — New i18n keys in all 10 locales; EN + DE first-class.
8. **AC8** — Works on mobile width (single column, no horizontal scroll).

## Tasks / Subtasks

- [x] **Task 1 — API** (AC: 5, 6)
  - [x] Spec first, then controller + service in EMS `mailcenter` package; `EndpointAuthorizationGuardTest` stays green.
  - [x] Newsletter rows: union `mail_message` with `newsletter_sends` that have no `mail_message` link, mapped to the same DTO; delivered/failed counts from `newsletter_recipients`.
  - [x] Bruno contract tests (read-only, `docs { }` comments only), run against local dev.
- [x] **Task 2 — Frontend** (AC: 1–4, 7, 8)
  - [x] Replace the body of `EventCommunicationsContainer.tsx`: sections "What went out" and "Handled automatically". Keep the four existing compose components mounted behind a temporary audience switch until 16.4 lands, so sending stays possible in between (16.3 and 16.4 may ship in one PR; then drop the switch).
  - [x] Detail view: rendered HTML in a sandboxed iframe (`sandbox` without scripts), as in the `/dev/emails` preview.
  - [x] Remove the newsletter send-history table from `EventNewsletterTab.tsx` (now in "What went out"); keep its active-send progress polling.
  - [x] Service layer `mailMessageService.ts` (no direct fetch), React Query hooks.
- [x] **Task 3 — Tests** (AC: all)
  - [x] Vitest for the list, empty state, expand-failed, mobile layout.
  - [x] Integration test for the newsletter union and the 403 for non-organizers.
  - [x] Playwright (organizer project) read-only spec; must not send anything (`@sends-real-email` guard rules).

## Dev Notes

- `EventCommunicationsContainer.tsx` (109 lines) holds the four audiences in local `useState`; there are no sub-tab routes, so nothing needs a redirect.
- Newsletter history today comes from `useNewsletterHistory(eventCode)` in `EventNewsletterTab.tsx`. That hook also drives "resume polling an active send"; keep that behaviour.
- Grouping in the list: individual transactional mails (one group per mail, see 16.1a AC2) are collapsed per template and day, e.g. "Registration confirmation · 14 mails", so they don't flood the list.
- Rendered mail bodies are PII; the endpoint is organizer-only and never cached publicly.

### References

- `docs/prd/epic-16-mail-center.md` (Information architecture)
- `web-frontend/src/components/organizer/EventPage/EventCommunicationsContainer.tsx`
- `web-frontend/src/components/organizer/EventPage/EventNewsletterTab.tsx`
- `docs/api/event-newsletter-api.openapi.yml`
- `docs/architecture/ADR-013-rest-api-crud-conventions.md`

## Dev Agent Record

### Agent Model Used

Claude Opus 5.5 (Amelia, dev-story), 2026-10-30. Built together with 16.4 on one branch
(`feature/16-3-4-mail-page`, stacked on 16.1b), as the epic allows; the temporary audience switch
was therefore never built (16.4 AC4).

### Debug Log References

- EMS: `EventMailControllerIntegrationTest` 8/8 (PostgreSQL). Full EMS suite with checkstyle on
  clean results: 2209 tests, 0 failures, 18 skipped.
- Frontend: lint, type-check, `check:api-types` clean; full Vitest 5504 passed, 0 failed.
- Local dev (live): Bruno `events` collection 12 requests, 24/24, no skipped files. Playwright
  `event-mail-page.spec.ts` 2/2 (every panel opens and closes with Esc, focus back on "+ New mail",
  no horizontal scroll at 375 px). `event-newsletter-send.spec.ts` passes; it showed live progress
  1 → 554 / 1010 and the row switching from the backfill key to the recorded key.

### Completion Notes List

- **API (AC5, AC6):** `event-mail-api.openapi.yml` (spec first), `EventMailController` →
  `MailMessageQueryService` (native SQL, read-only). Three operations, all
  `hasRole('ORGANIZER')`, `Cache-Control: no-store`; `EndpointAuthorizationGuardTest` green.
  Besides the two in AC5, `GET .../mail-messages/counts` serves "Handled automatically" (AC3), so
  the count does not depend on the page of the list.
- **Newsletter union (AC6):** `newsletter_sends` without a `mail_message` are listed as
  `newsletter-{sendId}` without body; audience REGISTRANTS for `REGISTRANT_NOTICE` templates.
  Registrant notices (slides-online etc.) live in the same table, so they are backfilled too.
- **Counts:** recorded mail from `mail_recipient` statuses (16.1b). Newsletter from
  `newsletter_recipients`: failed = refused by SES or bounced, delivered = the rest; the newsletter
  set reports no deliveries. Documented in the spec, the architecture doc and the user guide.
- **Grouping (Dev Notes):** done on the server, so paging stays correct: automatic mails (no
  sender, template, not a newsletter) collapse per template and Zurich day; `grouped=false` plus
  `templateKey`/`date` expands one group. Group titles show the template key, not a friendly name.
- **Frontend (AC1-AC4, AC8):** `EventCommunicationsContainer` is the mail page; "What went out"
  (`MailSentList`: filter chips, rows, paging), "Handled automatically" (collapsed accordion,
  counts), detail drawer with failed recipients first and the body in an iframe with
  `sandbox=""` (no scripts, no same-origin). The list polls every 3 s while a newsletter is
  PENDING/IN_PROGRESS.
- **Newsletter tab:** history table removed; the newest PARTIAL/FAILED send keeps a Retry button
  in an alert, and the active-send polling stays.
- **i18n (AC7):** `eventPage.mail.*` and `eventPage.newsletter.retryHint` in all 10 locales; the
  unused `eventPage.communications.audiences` keys removed.
- **API review 2026-10-31 (owner):** the resource is `/events/{eventCode}/mails` (was
  `/mail-messages`, the name in AC5), so 16.8 extends it with POST send and preview instead of
  creating a second resource, 16.9 posts to `/internal/mails` and 16.7 reads `GET /mails`.
  Operation ids `listEventMails`, `getEventMail`, `getEventMailCounts`. Verified: EMS mailcenter
  tests + guard green, Bruno `events` 12 requests 24/24 against local dev.
- **Side finding, not fixed here:** `DELETE /api/v1/events/{code}` answers 500 for an event with
  `newsletter_sends` (FK without ON DELETE). Added to #1158 with options.

### File List

- `docs/api/event-mail-api.openapi.yml` (new)
- `services/event-management-service/build.gradle`
- `services/event-management-service/src/main/java/ch/batbern/events/mailcenter/EventMailController.java` (new)
- `services/event-management-service/src/main/java/ch/batbern/events/mailcenter/MailMessageQueryService.java` (new)
- `services/event-management-service/src/test/java/ch/batbern/events/mailcenter/EventMailControllerIntegrationTest.java` (new)
- `bruno-tests/events/mails-{list,detail,counts,forbidden,unknown-event}.bru` (new)
- `.github/workflows/security-scan.yml` (ZAP scan of the new spec)
- `web-frontend/package.json` (type generation)
- `web-frontend/src/types/generated/event-mail-api.types.ts` (new, generated)
- `web-frontend/src/services/mailMessageService.ts` (new)
- `web-frontend/src/hooks/useMailMessages/useMailMessages.ts` (new)
- `web-frontend/src/components/organizer/EventPage/EventCommunicationsContainer.tsx`
- `web-frontend/src/components/organizer/EventPage/communications/MailSentList.tsx` (new)
- `web-frontend/src/components/organizer/EventPage/communications/MailDetail.tsx` (new; was `MailDetailDrawer.tsx`)
- `web-frontend/src/components/organizer/EventPage/communications/PartnerMeetingsCompose.tsx` (new)
- `web-frontend/src/components/organizer/EventPage/communications/__tests__/{MailSentList,MailDetail,PartnerMeetingsCompose}.test.tsx` (new)
- `web-frontend/src/components/organizer/EventPage/EventNewsletterTab.tsx`
- `web-frontend/src/components/organizer/EventPage/EventRegistrantNoticesTab.tsx`
- `web-frontend/src/components/organizer/EventPage/SpeakerBulkComms.tsx`
- `web-frontend/src/components/organizer/EventPage/EventVenueTab.tsx`
- `web-frontend/src/components/organizer/EventPage/VenueCoordinationComposer.tsx`
- `web-frontend/src/components/organizer/EventPage/__tests__/{EventCommunicationsContainer,EventNewsletterTab,SpeakerBulkComms}.test.tsx`
- `web-frontend/src/components/organizer/PartnerMeetingInviteAction.tsx` (new, extracted)
- `web-frontend/src/components/organizer/PartnerMeetingInviteAction.test.tsx` (new)
- `web-frontend/src/components/organizer/MeetingDetailPanel.tsx`
- `web-frontend/public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/events.json`
- `web-frontend/e2e/organizer/event-mail-page.spec.ts` (new)
- `web-frontend/e2e/organizer/event-newsletter-send.spec.ts`
- `docs/architecture/06-backend-architecture.md`
- `docs/user-guide/features/event-mail.md` (new), `docs/user-guide/features/README.md`
- `_bmad-output/implementation-artifacts/16-3-communications-tab-becomes-mail-page.md`
- `_bmad-output/implementation-artifacts/16-4-new-mail-picker.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`

## Change Log

- 2026-10-30: Story drafted from Epic 16 (John, PM).
- 2026-10-30: Readiness fixes: section 1 owned by 16.2, UX reference in docs/ux, transactional grouping.
- 2026-10-30: Implemented with 16.4 (API, mail page, tests, docs). Status review. Amelia (dev).
- 2026-10-31: Mail detail opens inline below its row (desktop) or full screen (mobile) instead of a drawer, owner decision; see 16.4 notes.
- 2026-10-31: Read API renamed `/mail-messages` → `/mails` after the Epic 16 API review.
