# Story 16.4: "+ New mail" picker with the existing flows

Status: ready-for-dev

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

- [ ] **Task 1 — Picker + panel** (AC: 1, 4, 5)
  - [ ] MUI `Menu` + `Drawer` (anchor right, width ~560 px; full screen below the `isMobile` breakpoint from `useBreakpoints`).
  - [ ] Mount `EventNewsletterTab`, `EventRegistrantNoticesTab`, `SpeakerBulkComms`, `EventVenueTab` inside the drawer as they are.
  - [ ] Partners entry (AC6): meeting list + the existing invite action extracted from `MeetingDetailPanel` into a reusable piece (no behaviour change on the partner meetings page).
- [ ] **Task 2 — Refresh after send** (AC: 3)
  - [ ] Each compose component gets an optional `onSent` callback; the container invalidates the mail-messages query and closes the drawer.
- [ ] **Task 3 — Tests** (AC: all)
  - [ ] Vitest: menu items, drawer opens the right component, `onSent` closes and invalidates.
  - [ ] Playwright: open each panel, do not send.

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

### Debug Log References

### Completion Notes List

### File List

## Change Log

- 2026-10-30: Story drafted from Epic 16 (John, PM).
- 2026-10-30: Partners entry added (owner request); pointer to 16.8.
