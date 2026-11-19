---
title: 'Every visitor sees the speaking call on the upcoming-event cards'
type: 'bugfix'
ticket: '3'
created: '2026-11-19'
status: 'built'
baseline_revision: 'ab1bcba3d397c078fd4bfcd8350a0885e661df1b'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context: ['{project-root}/docs/architecture/coding-standards.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Since 2026-10-07 nobody sees the self-nomination button on the homepage's upcoming-event cards (#1263). The list items never said whether self-nomination is open; story 3.2 adds `selfNominationOpen`. Also, the panel hides itself from anonymous visitors, who are most visitors. The button also sits outside the card, below the link.

**Approach:** The frontend gate reads the server flag. While it is open, the upcoming-event card is split (owner, 2026-11-08). The upper part is one link to the event page. The lower part is a real button with the existing wording, `attendee.selfNominate.button`. It replaces the "View details" footer and the panel below the card. A logged-in click opens the existing form. An anonymous click goes to `/login` with router state `{ from: { pathname: '/', search: '?nominate=<eventCode>' } }`. The existing `loginTarget` accepts internal paths only and brings the visitor back. The card whose code matches `nominate` then opens its form once, and the parameter is removed from the URL.

## Boundaries & Constraints

**Always:** Tailwind only, no MUI on the homepage. Exactly one call to action per card. Two tab stops on a card with the button, one without. No interactive element nested in another. The backend stays authoritative: a 409 `SELF_NOMINATION_NOT_ALLOWED` still shows the generic error. Every new UI key in all 10 locales. Tests: MSW for the network with the real `/public/events` response shape, never `vi.mock` of own hooks, services or components.

**Never:** Change the archive cards (`EventCard` without `enableSelfNomination`). Change the unpublished `PlannedEventCard`. Change the hero button on an event's own page. Change the backend. Build a return target from anything but a fixed internal path plus the event code.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Open, anonymous | `selfNominationOpen: true`, not logged in | Card shows link part and button; the click navigates to `/login` with `from` = `/?nominate=CODE` | none |
| Return after login | logged in, URL `/?nominate=CODE` | That card's dialog opens once, the param is removed (replace), other cards unaffected | Unknown or closed code: nothing opens, the param is still removed |
| Open, logged in | flag true | Button opens dialog, submit POSTs as in Story 7.2 | 409 duplicate shows done, 409 not-allowed shows generic error |
| Closed | flag false or absent | Card is one link with the "View details" footer, no button | none |
| Hostile param | `?nominate=//evil.com` | Treated as an event code that matches no card, so nothing opens | no navigation away |

</frozen-after-approval>

## Code Map

- `web-frontend/src/utils/eventPublication.ts:30` -- `canOfferSelfNomination`: switch to `event.selfNominationOpen === true` (the 3.2 flag, set on public list, detail and current). Its input type gains `selfNominationOpen?: boolean`. Update `utils/__tests__/eventPublication.test.ts`.
- `web-frontend/src/components/public/EventCard.tsx` -- today: the whole card is one `<Link>`, and with `showSelfNominate` the `SpeakerSelfNominatePanel` renders below it (line 245ff). New: when `showSelfNominate`, render the card as a container. The link wraps image, header and content without the footer. A footer section holds the panel's button. Without `showSelfNominate`, the output is unchanged (archive). Keep the `data-testid`s used by existing tests and e2e (`event-card`, `event-card-<code>`).
- `web-frontend/src/components/attendee/SpeakerSelfNominatePanel.tsx` -- returns `null` for anonymous visitors (line 96). New: anonymous sees the same button, and the click navigates to `/login` with the state above. New optional prop `autoOpen?: boolean` opens the dialog on mount once. Drop the `preventDefault`/`stopPropagation` hack once the button is no longer inside a link. The hero on `HomePage.tsx:270` also uses this panel: the anonymous button appears there too, which is consistent and in scope only if the hero is the homepage's (it is). Note it in the PR.
- `web-frontend/src/components/public/UpcomingEventCards.tsx:23` (`PublishedEventCard`) and `UpcomingEventsSection.tsx` -- read `nominate` from `useSearchParams`. Pass `autoOpen` to the matching card. Clear the param with `setSearchParams(..., { replace: true })`.
- `web-frontend/src/utils/auth/loginTarget.ts` -- reuse as is (internal paths only, auth pages refused).
- `App.tsx:199` `LoginPage` -- already navigates to `loginTarget(location.state)`. The sign-up link (`handleSignUp`) drops the state. Pass `location.state` through to `/auth/register` only if the registration wizard's way back to login can carry it. Otherwise leave it and note it.
- Tests: `components/public/__tests__/EventCard.test.tsx`, `UpcomingEventsSection.test.tsx`, `components/attendee/__tests__/SpeakerSelfNominatePanel.test.tsx` -- move them to the real response shape via the MSW harness (`@/test/msw/api`, `api.on(...)`; see `ClaimProfilePage.test.tsx` for auth plus routes with `renderWithAuth`/`signedInAs`).

## Tasks & Acceptance

**Execution:**
- [x] `eventPublication.ts` plus test -- gate on the flag -- one rule, server-side
- [x] `SpeakerSelfNominatePanel.tsx` plus test -- anonymous button to login with return state; `autoOpen` -- AC2, AC3
- [x] `EventCard.tsx` plus test -- split card when open; unchanged otherwise -- AC4, AC5
- [x] `UpcomingEventCards.tsx` / `UpcomingEventsSection.tsx` plus test -- `nominate` param opens the matching card once and is cleared -- AC2
- [x] Locales -- only if a new key is needed, then all 10 -- AC6

**Acceptance Criteria:**
- Given the `/public/events?scope=upcoming` response (MSW, spec shape) with one open and one closed event, when an anonymous visitor views the homepage section, then only the open card shows the button and the closed card shows "View details".
- Given an anonymous visitor, when they click the button and log in, then they are back on `/` with that event's form open.
- Given a logged-in visitor, when they click the button and submit, then the POST matches the spec and the success message shows.
- Given an open card, when tabbing, then the link and the button are two separate stops and the DOM has no interactive element inside another.

## Verification

**Commands:**
- `cd web-frontend && npx vitest run src/components/public src/components/attendee src/utils src/pages/public --maxWorkers=4` -- expected: green
- `cd web-frontend && npm run type-check && npm run lint && npm run check:test-ratchet && npm run check:i18n-keys` -- expected: green, no RAISED ratchet line

## Review Triage Log

Quick review, 5 findings: 4 real (3 medium, 1 low), 1 false.
- medium, patch: the hero behaviour (`HomePage.tsx:270-276`) had no test, so the `hideWhenSignedOut` guard protecting the event page hero was unpinned. A HomePage test is added.
- low, patch: the hero relied on `UpcomingEventsSection` to remove `nominate`. The hero now clears it itself.
- medium, fixed in its own commit (C9, pre-existing): the Google login returns through `AuthCallbackPage`, which always goes to `/dashboard`, so the return target is lost. That holds for every return-to-page login, the #1183 D claim link included.
- medium, fixed in the same separate commit: the sign-up path (`App.tsx` `handleSignUp`) drops the return target.
- false: the plan's status in the diff was stale only because the diff was staged before the status update. The file on disk is correct.
