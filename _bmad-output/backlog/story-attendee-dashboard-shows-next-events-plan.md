---
title: 'The attendee dashboard shows the next three events, registered or not'
type: 'feature'
ticket: '3'
created: '2026-11-13'
status: 'built'
baseline_revision: '33b706b3d'
route: 'oneshot'
route_source: 'auto'
risk: 'low'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
context: ['{project-root}/_bmad-output/backlog/story-attendee-dashboard-shows-next-events.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The "Upcoming events" list on the attendee dashboard shows only events the user registered for, so it is usually empty.

**Approach:** Show three cards: every upcoming event the user is registered for first, then the next upcoming events they are not registered for, until there are three. Use the homepage's upcoming-event cards. Published events link to their event page, where the user registers. Unpublished ones show code, title and date only, without a link. No register button on the card (owner 2026-11-13).

</frozen-after-approval>

## Code Map

- `web-frontend/src/components/public/UpcomingEventsSection.tsx` -- homepage section. `PublishedEventCard` (EventCard + `useMyRegistration` chip + self-nomination) and `PlannedEventCard` (code/title/date, no link). Calendar from `presentationService.getUpcomingEvents(excludeCode)`; published from `eventApiClient.getPublicEvents({scope:'upcoming'})`; query keys `['events','public','upcoming-dates',code]` and `['events','public','upcoming']`. Extract the two cards and the "calendar + published map" logic into a shared hook (for example `useUpcomingEventCards`) so the homepage and the dashboard use one implementation. The homepage behaviour must not change (its tests stay green).
- `web-frontend/src/services/presentationService.ts` L71-84 `getUpcomingEvents(excludeEventCode)` -- slices to 3. The dashboard needs more candidates when the user is registered for some of them: add an optional `max` (default 3) or return the sorted list and slice in the caller. Keep the presenter slide's result the same.
- `web-frontend/src/pages/attendee/AttendeeDashboardPage.tsx` -- the `attendee-upcoming` section (L116-131) renders `dashboard.upcomingEvents` with the local `EventCard`. Replace it with: registered upcoming events (all of them, first; homepage `PublishedEventCard` when the event is in the published list, else the local `EventCard` as a fallback), then calendar events whose code is not registered, until three cards in total. Grid like the homepage. Keep the empty text only when there is nothing at all. The "past" list stays as it is (local `EventCard`, `/archive/` link).
- `web-frontend/src/pages/attendee/__tests__/AttendeeDashboardPage.test.tsx` -- `vi.mock`s our own service, `useAuth`, `CommunityTopicSuggestPanel`, `PublicLayout`. Rewrite it on the MSW harness (`web-frontend/src/test/msw/`, example `src/pages/profile/__tests__/ProfilePage.test.tsx`); mock only at the network. Test fidelity rules: docs/architecture/coding-standards.md.

## Tasks & Acceptance

- Tests first (MSW): no registration → the next three calendar events, published ones linking to `/events/{code}`, an unpublished one without a link; registered for one → that one first (with its registration status) then two more; registered for four → four, no others; public list fails → registered events still show (fallback card); homepage section unchanged.
- Implement as in the Code Map. No new i18n keys unless needed; new keys go into all 10 locales.

## Verification

- `cd web-frontend && npx vitest run src/pages/attendee src/components/public src/services --maxWorkers=4` -- 0 failures
- `cd web-frontend && npm run type-check && npm run lint` -- pass
- `make check-test-ratchet` -- no RAISED line (the dashboard test leaving `vi.mock` lowers it; on STALE run `cd web-frontend && npm run check:test-ratchet -- --update`)

## Implementation Notes

Oneshot: a frontend-only change on one page, reusing the homepage component; no backend or API change.

Built 2026-11-13 on `feature/whats-new-card`.

- Shared: `PublishedEventCard` and `PlannedEventCard` moved unchanged to `web-frontend/src/components/public/UpcomingEventCards.tsx`; the calendar + published-map logic is `web-frontend/src/hooks/useUpcomingEventCards.ts` (`useUpcomingEventCards(excludeEventCode, max?)`). `UpcomingEventsSection` uses both; without `max` it makes the same calls with the same query keys as before, and its four tests pass unmodified.
- `presentationService.getUpcomingEvents(excludeEventCode, max = 3)`: the slide and the homepage keep 3. The dashboard asks for 10 candidates (the read-model call already fetches 10), with no hero to exclude.
- Dashboard: registered upcoming events (dashboard answer, sorted by date) first, all of them; a registered event in the published list gets `PublishedEventCard` (status chip through `useMyRegistration`), otherwise the local `EventCard` (link to `/events/`, status from the dashboard). Then calendar events not registered, up to three cards in total. Homepage grid classes. Empty text only when both are empty. Past list unchanged. No new i18n keys.
- Test: `AttendeeDashboardPage.test.tsx` rewritten on the MSW harness (no `vi.mock` left): no registration (three cards, published linked, unpublished without link or venue), registered for one (first, chip "Confirmed"), registered for four (four, no others), public list fails (registered fallback card stays), nothing at all (empty text), past list. `presentationService.test.ts` gained a `max` case. Ratchet baseline updated (4 counts fell).
- Verified: `npx vitest run src/pages/attendee src/components/public src/services --maxWorkers=4` 90 files, 1124 tests passed; `npm run type-check` and `npm run lint` pass; `make check-test-ratchet` clean after `--update`. Not verified on local dev in a browser.
- Known limit: the published list is fetched with `limit: 5` (shared query with the homepage). A registered or candidate event beyond the first five published ones would show as the fallback/planned card. Today's calendar has fewer events than that.
- User-guide changelog entry added (comparable UI changes are listed there), so the commit carries no `[no-doc]`.
