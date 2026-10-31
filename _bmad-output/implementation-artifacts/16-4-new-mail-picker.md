# Story 16.4: "+ New mail" picker with the existing flows

Status: review

## Story

As an **organizer**,
I want **one "+ New mail" button on the Communications page that lets me pick the audience and opens the compose flow I already know**,
so that **sending and seeing what went out live on the same page**.

> Source: `docs/prd/epic-16-mail-center.md` → **Story 16.4**. Depends on 16.3. Can ship in the same PR as 16.3.

## Acceptance Criteria

1. **AC1** — A "+ New mail" menu offers Newsletter, Registrants, Speakers, Venue & Catering, Partners. Each opens its existing compose component in a side panel (full screen on mobile), behaviour and confirmation step unchanged.
2. **AC2** — Every send possible from the old audience tabs is possible from the picker. No compose logic is rewritten in this story.
3. **AC3** — After a successful send the panel closes and the new row appears at the top of "What went out" (React Query invalidation; a newsletter shows its live progress).
4. **AC4** — The temporary audience switch from 16.3 is removed.
5. **AC5** — i18n keys in all 10 locales; keyboard-accessible menu and panel (focus trap, Esc closes, focus returns to the button).
6. **AC6 (Partners)** — Partners lists this event's partner meetings (`GET /partner-meetings` has no event filter; filter by `eventCode` client-side, the list is a handful of meetings). Choosing one shows the existing invite / cancellation action from `MeetingDetailPanel` with its confirmation. GIVEN the event has no partner meeting, THEN the entry says so and links to the partner meetings page.

## Tasks / Subtasks

- [x] **Task 1 — Picker + panel** (AC: 1, 4, 5)
  - [x] MUI `Menu` + `Drawer` (anchor right, width ~560 px; full screen below the `isMobile` breakpoint from `useBreakpoints`).
  - [x] Mount `EventNewsletterTab`, `EventRegistrantNoticesTab`, `SpeakerBulkComms`, `EventVenueTab` inside the drawer as they are.
  - [x] Partners entry (AC6): meeting list + the existing invite action extracted from `MeetingDetailPanel` into a reusable piece (no behaviour change on the partner meetings page).
- [x] **Task 2 — Refresh after send** (AC: 3)
  - [x] Each compose component gets an optional `onSent` callback; the container invalidates the mail-messages query and closes the drawer.
- [x] **Task 3 — Tests** (AC: all)
  - [x] Vitest: menu items, drawer opens the right component, `onSent` closes and invalidates.
  - [x] Playwright: open each panel, do not send.

## Dev Notes

- The four compose components currently assume a full-width tab; check their layout at drawer width and fix only what breaks.
- Own text with placeholders is 16.5, not here.
- **This story deliberately mounts the existing components.** 16.8 replaces them with one shared composer (Winston's review 2026-10-30); do not refactor them here.
- UX reference: `docs/ux/mail-center-prototype.html` and `docs/ux/mail-center-spec.md` (layout only; build with MUI).

### References

- `docs/prd/epic-16-mail-center.md`
- `web-frontend/src/components/organizer/EventPage/EventCommunicationsContainer.tsx`
- `web-frontend/src/components/organizer/EventPage/SpeakerBulkComms.tsx`

## Dev Agent Record

### Agent Model Used

Claude Opus 5.5 (Amelia, dev-story), 2026-10-30. Same branch and PR as 16.3.

### Debug Log References

- EMS: `EventMailControllerIntegrationTest` 8/8 (PostgreSQL). Full EMS suite with checkstyle on
  clean results: 2209 tests, 0 failures, 18 skipped.
- Frontend: lint, type-check, `check:api-types` clean; full Vitest 5504 passed, 0 failed.
- Local dev (live): Bruno `events` collection 12 requests, 24/24, no skipped files. Playwright
  `event-mail-page.spec.ts` 2/2 (every panel opens and closes with Esc, focus back on "+ New mail",
  no horizontal scroll at 375 px). `event-newsletter-send.spec.ts` passes; it showed live progress
  1 → 554 / 1010 and the row switching from the backfill key to the recorded key.

### Completion Notes List

- **Picker (AC1, AC5):** MUI `Menu` + `Drawer`, five entries. The drawer is a modal (focus trap, Esc
  closes); focus returns to "+ New mail" after the slide-out (`disableRestoreFocus` + transition
  `onExited`, because MUI would restore it to the vanished menu item; caught by a test).
- **Deviation, panel width:** `min(1100px, 92vw)` instead of ~560 px, full screen on mobile. The
  four composers switch to two columns on the viewport `md` breakpoint, so a 560 px panel squeezed
  them; widening the panel avoided changing their layouts (Dev Notes: fix only what breaks).
- **Refresh after send (AC3):** optional `onSent` on each composer; the container invalidates
  `['mail-messages', eventCode]`, closes the panel and shows a toast. Deliberate exceptions: a
  **test** newsletter keeps the panel open (the real send follows), and speaker reminders with
  failures keep it open so the failed speakers and the retry stay visible.
- **Partners (AC6):** `PartnerMeetingInviteAction` extracted from `MeetingDetailPanel` (same
  chip, button, confirm dialog and test ids). One visible change on the partner meetings page: the
  "invite sent" alert now shows next to the invite button instead of at the top of the panel.
  `PartnerMeetingsCompose` filters `GET /partner-meetings` by `eventCode` client-side and links to
  the partner meetings page when there is none.
- **AC4:** no temporary switch was built; 16.3 and 16.4 ship together.
- **Layout change after review (2026-10-31, owner):** side panels replaced by inline expand
  (prototype variant A, `docs/ux/mail-center-layouts.html`). Desktop: the composer opens at the top
  of the page, focus moves to its heading, Esc or ✕ closes it and focus returns to "+ New mail"; a
  mail opens below its row, mail beside facts. Mobile (< 900 px): both are full-screen dialogs
  (`docs/ux/mail-center-mobile.html`). `MailDetailDrawer` became `MailDetail` + `MailDetailDialog`;
  the open mail is state of `MailSentList`. Page width 900 → 1100 px so the composers keep their
  two columns. Vitest 23/23 in the touched files, `event-mail-page.spec.ts` 2/2 on local dev (now also
  checks: no modal on desktop, a 375 px full-screen dialog on mobile).
- **E2E:** `event-newsletter-send.spec.ts` now opens the panel from "+ New mail" and checks the
  row on the mail page. On the anonymized local DB the send ends PARTIAL and the spec passes on
  that (#1158 option 3, which the issue warns about); noted in the spec and on #1158.

### File List

See Story 16.3 (one change set):

- `docs/api/event-mail-api.openapi.yml` (new)
- `services/event-management-service/build.gradle`
- `services/event-management-service/src/main/java/ch/batbern/events/mailcenter/EventMailController.java` (new)
- `services/event-management-service/src/main/java/ch/batbern/events/mailcenter/MailMessageQueryService.java` (new)
- `services/event-management-service/src/test/java/ch/batbern/events/mailcenter/EventMailControllerIntegrationTest.java` (new)
- `bruno-tests/events/mail-messages-{list,detail,counts,forbidden,unknown-event}.bru` (new)
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
- 2026-10-30: Partners entry added (owner request); pointer to 16.8.
- 2026-10-30: Implemented with 16.3. Status review. Amelia (dev).
- 2026-10-31: Side panels replaced by inline expand (desktop) and full-screen dialogs (mobile), owner decision.
