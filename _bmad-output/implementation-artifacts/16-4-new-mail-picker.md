# Story 16.4: "+ New mail" picker with the existing flows

Status: ready-for-dev

## Story

As an **organizer**,
I want **one "+ New mail" button on the Communications page that lets me pick the audience and opens the compose flow I already know**,
so that **sending and seeing what went out live on the same page**.

> Source: `docs/prd/epic-16-mail-center.md` → **Story 16.4**. Depends on 16.3. Can ship in the same PR as 16.3.

## Acceptance Criteria

1. **AC1** — A "+ New mail" menu offers Newsletter, Registrants, Speakers, Venue & Catering. Each opens its existing compose component in a side panel (full screen on mobile), behaviour and confirmation step unchanged.
2. **AC2** — Every send possible from the old audience tabs is possible from the picker. No compose logic is rewritten in this story.
3. **AC3** — After a successful send the panel closes and the new row appears at the top of "What went out" (React Query invalidation; a newsletter shows its live progress).
4. **AC4** — The temporary audience switch from 16.3 is removed.
5. **AC5** — i18n keys in all 10 locales; keyboard-accessible menu and panel (focus trap, Esc closes, focus returns to the button).

## Tasks / Subtasks

- [ ] **Task 1 — Picker + panel** (AC: 1, 4, 5)
  - [ ] MUI `Menu` + `Drawer` (anchor right, width ~560 px; full screen below the `isMobile` breakpoint from `useBreakpoints`).
  - [ ] Mount `EventNewsletterTab`, `EventRegistrantNoticesTab`, `SpeakerBulkComms`, `EventVenueTab` inside the drawer as they are.
- [ ] **Task 2 — Refresh after send** (AC: 3)
  - [ ] Each compose component gets an optional `onSent` callback; the container invalidates the mail-messages query and closes the drawer.
- [ ] **Task 3 — Tests** (AC: all)
  - [ ] Vitest: menu items, drawer opens the right component, `onSent` closes and invalidates.
  - [ ] Playwright: open each panel, do not send.

## Dev Notes

- The four compose components currently assume a full-width tab; check their layout at drawer width and fix only what breaks.
- Own text with placeholders is 16.5, not here.

### References

- `docs/prd/epic-16-mail-center.md`
- `web-frontend/src/components/organizer/EventPage/EventCommunicationsContainer.tsx`
- `web-frontend/src/components/organizer/EventPage/SpeakerBulkComms.tsx`
