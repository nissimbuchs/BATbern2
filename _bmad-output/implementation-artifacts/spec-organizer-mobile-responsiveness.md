---
title: 'Organizer frontend mobile responsiveness sweep'
type: 'bugfix'
created: '2026-06-05'
status: 'done'
context: []
baseline_commit: '259148b19a669ba8b8f03f9baef96e064a035253'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Many logged-in organizer surfaces (pages, tab strips, drawers, dialogs, filters, tables, headers) render incorrectly on mobile (<600px): horizontal overflow, fixed px widths, non-scrollable tab strips, button rows that blow the viewport.

**Approach:** Systematic per-file fix using the codebase's existing responsive patterns (`useMediaQuery(theme.breakpoints.down(...))`, responsive `sx` values, `variant="scrollable"` Tabs, card-fallback tables per `SpeakersSessionsTable`). Branch `fix/organizer-mobile-responsiveness`; after tests pass, deploy via `scripts/deploy/publish-beta-frontend.sh`. Do NOT push the branch.

## Boundaries & Constraints

**Always:** Use MUI breakpoint APIs (`useMediaQuery`/responsive `sx`) — never `window.innerWidth`. Preserve desktop rendering pixel-identical at ≥960px. i18n via `useTranslation()` for any new visible string (all 10 locales). TDD: failing test → fix → green.

**Ask First:** Removing/hiding table columns on mobile beyond the proposed sets; any layout redesign beyond responsive re-flow; touching non-organizer (speaker/partner/public) shared components in ways that change their mobile behavior.

**Never:** Push the branch to origin. Change backend/API. Add new dependencies. Modify `BlobTopicSelectorPage` canvas or `LiveControlPage` (already mobile-first).

</frozen-after-approval>

## Code Map

- `web-frontend/src/theme/theme.ts` -- breakpoints xs0/sm600/md960/lg1280
- `web-frontend/src/hooks/useBreakpoints/useBreakpoints.ts` -- existing `{isMobile,isTablet,isDesktop}` hook (underused)
- `web-frontend/src/components/organizer/EventManagement/SpeakersSessionsTable.tsx` -- reference card-fallback table pattern
- `web-frontend/src/components/organizer/EventPage/EventPage.tsx` -- reference mobile BottomNavigation tabs pattern

## Tasks & Acceptance

**Execution:**

- [x] `web-frontend/src/components/organizer/EventPage/AiAssistDrawer.tsx` -- drawer `width: 480` → `{ xs: '100%', sm: 480 }` -- overflows phones
- [x] `web-frontend/src/components/organizer/PartnerManagement/PartnerCreateEditModal.tsx` -- replace `window.innerWidth < 640` with `useMediaQuery(theme.breakpoints.down('sm'))` -- not resize-reactive
- [x] `web-frontend/src/pages/organizer/EventManagementAdminPage.tsx` -- 9-tab strip: add `variant="scrollable" scrollButtons="auto" allowScrollButtonsMobile` -- tabs overflow
- [x] `web-frontend/src/pages/organizer/OrganizerAnalyticsPage.tsx` -- 4-tab strip scrollable; `KpiCard` `minWidth` responsive -- minor overflow
- [x] `web-frontend/src/components/organizer/TopicStatusPanel.tsx` -- 7-col table with fixed % widths + `minWidth:120` Select: ensure `TableContainer` horizontal scroll with sensible `minWidth` on table; hide low-value columns on xs -- worst table offender
- [x] `web-frontend/src/components/organizer/PartnerMeetingsPage.tsx` -- 8-col table: guarantee `overflowX:'auto'` + table `minWidth`; responsive header row -- overflow
- [x] `web-frontend/src/components/organizer/EventPage/EventParticipantTable.tsx` -- guarantee horizontal scroll; hide Company/RegDate columns on xs -- 6 cols crowd phones
- [x] `web-frontend/src/components/organizer/UserManagement/UserTable.tsx` -- same treatment (hide Company/Status on xs) -- 5+ cols
- [x] `web-frontend/src/components/organizer/NewsletterSubscribers/NewsletterSubscriberTable.tsx` -- same treatment (hide Language/Source/SubscribedAt on xs) -- 6+ cols
- [x] `web-frontend/src/components/organizer/Analytics/DataTable.tsx` -- ensure container `overflowX:'auto'` -- analytics tables clip
- [x] `web-frontend/src/components/organizer/EventManagement/EventSearch.tsx` -- `minWidth: 200/150` → `{ sm: 200 }`/`{ sm: 150 }` (full-width xs, per `PartnerFilters` pattern) -- filter overflow
- [x] `web-frontend/src/components/organizer/NewsletterSubscribers/NewsletterSubscriberFilters.tsx` -- `minWidth: 250` → `{ xs: '100%', sm: 250 }` -- breaks <400px
- [x] `web-frontend/src/components/organizer/EventPage/EventPage.tsx` -- header action-button Stack → `direction={{ xs: 'column', sm: 'row' }}` -- buttons overflow
- [x] `web-frontend/src/components/shared/Company/CompanyManagementScreen.tsx` -- header Stack (title + toggle + create) responsive direction -- verified OK (outer Stack already `direction={isMobile ? 'column' : 'row'}`; view-toggle hidden + create-label hidden on xs)
- [x] `web-frontend/src/pages/EventManagementDashboard.tsx` -- header row responsive direction -- fixed in the real component `components/organizer/EventManagement/EventManagementDashboard.tsx` (wrapper page only re-exports); Active-Events title+count row now `direction={{ xs: 'column', sm: 'row' }}`
- [x] `web-frontend/src/pages/organizer/SlotAssignmentPage.tsx` -- header row responsive direction -- awkward wrap
- [x] `web-frontend/src/pages/organizer/NotificationsPage.tsx` -- actions row responsive wrap -- awkward wrap
- [x] `web-frontend/src/pages/organizer/TaskBoardPage.tsx` -- kanban Paper `height: { xs: 280, ... }` → `minHeight`/flex so columns grow -- content cut off
- [x] `web-frontend/src/components/organizer/NewsletterSubscribers/UnsubscribeDialog.tsx` -- verify `maxWidth`/`fullWidth`; fix if hardcoded -- verified OK (already `maxWidth="sm" fullWidth`)
- [x] Unit tests -- for each changed component add/extend Vitest cases asserting mobile rendering (mock `matchMedia` xs) per AC below -- TDD

**Acceptance Criteria:**

- Given a 375px viewport, when any organizer route renders, then no element forces `document` width beyond the viewport (tables scroll inside their own container).
- Given a 375px viewport, when EventManagementAdminPage or OrganizerAnalyticsPage renders, then the tab strip is swipe/scrollable and all tabs reachable.
- Given a 375px viewport, when AiAssistDrawer opens, then it spans 100% width; at ≥600px it stays 480px.
- Given a 375px viewport, when PartnerCreateEditModal opens, then it is fullScreen via media query (reacts to resize).
- Given a ≥960px viewport, when any touched surface renders, then layout is unchanged from develop.
- Given the test suite, when `npm run test` and `npm run build` run, then both pass with no new lint warnings.

## Spec Change Log

## Verification

**Commands:**
- `cd web-frontend && set -o pipefail && npm run test 2>&1 | tee /tmp/mobile-fix-test.log` -- expected: all pass
- `cd web-frontend && set -o pipefail && npm run build 2>&1 | tee /tmp/mobile-fix-build.log` -- expected: build success
- `cd web-frontend && npm run lint` -- expected: ≤50 warnings, no new errors

**Manual checks (if no CLI):**
- After beta deploy: spot-check `/organizer/events`, EventPage tabs, `/organizer/admin`, `/organizer/partner-topics`, `/organizer/newsletter-subscribers` at 375px on beta.batbern.ch.

## Suggested Review Order

**Hard bugs — resize-reactive breakpoints**

- Entry point: `window.innerWidth` one-time read replaced by reactive `useMediaQuery(down('sm'))` fullScreen
  [`PartnerCreateEditModal.tsx:163`](../../web-frontend/src/components/organizer/PartnerManagement/PartnerCreateEditModal.tsx#L163)

- Fixed 480px drawer paper → `{ xs: '100%', sm: 480 }`
  [`AiAssistDrawer.tsx:111`](../../web-frontend/src/components/organizer/EventPage/AiAssistDrawer.tsx#L111)

**Tables — scroll containers + xs column hiding**

- Worst offender: fixed % column widths now scroll inside container, responsive table minWidth, Date/Planned-Event hidden on xs
  [`TopicStatusPanel.tsx:220`](../../web-frontend/src/components/organizer/TopicStatusPanel.tsx#L220)

- 8-col table newly wrapped in `TableContainer` with `minWidth: 800`
  [`PartnerMeetingsPage.tsx:137`](../../web-frontend/src/components/organizer/PartnerMeetingsPage.tsx#L137)

- Company/RegDate cells hidden on xs (header+body pairs)
  [`EventParticipantTable.tsx:220`](../../web-frontend/src/components/organizer/EventPage/EventParticipantTable.tsx#L220)

- Company/Status hidden on xs
  [`UserTable.tsx:149`](../../web-frontend/src/components/organizer/UserManagement/UserTable.tsx#L149)

- Language/Source/SubscribedAt hidden on xs via `HIDE_ON_XS` helper
  [`NewsletterSubscriberTable.tsx:46`](../../web-frontend/src/components/organizer/NewsletterSubscribers/NewsletterSubscriberTable.tsx#L46)

- Explicit `overflowX: 'auto'` on analytics table container
  [`DataTable.tsx:63`](../../web-frontend/src/components/organizer/Analytics/DataTable.tsx#L63)

**Tab strips — scrollable on mobile**

- 9-tab strip gains `variant="scrollable" scrollButtons="auto" allowScrollButtonsMobile`
  [`EventManagementAdminPage.tsx:95`](../../web-frontend/src/pages/organizer/EventManagementAdminPage.tsx#L95)

- Same treatment for the 4-tab analytics strip
  [`OrganizerAnalyticsPage.tsx:73`](../../web-frontend/src/pages/organizer/OrganizerAnalyticsPage.tsx#L73)

**Filters & headers — responsive re-flow**

- `minWidth: 200/150` → `{ sm: ... }` so filters go full-width on xs
  [`EventSearch.tsx:197`](../../web-frontend/src/components/organizer/EventManagement/EventSearch.tsx#L197)

- `minWidth: 250` → `{ xs: '100%', sm: 250 }`
  [`NewsletterSubscriberFilters.tsx:57`](../../web-frontend/src/components/organizer/NewsletterSubscribers/NewsletterSubscriberFilters.tsx#L57)

- Header action buttons stack vertically on xs
  [`EventPage.tsx:222`](../../web-frontend/src/components/organizer/EventPage/EventPage.tsx#L222)

- Active-events title row re-flows on xs
  [`EventManagementDashboard.tsx:152`](../../web-frontend/src/components/organizer/EventManagement/EventManagementDashboard.tsx#L152)

- Header/actions rows re-flow on xs
  [`SlotAssignmentPage.tsx:96`](../../web-frontend/src/pages/organizer/SlotAssignmentPage.tsx#L96)
  [`NotificationsPage.tsx:72`](../../web-frontend/src/pages/organizer/NotificationsPage.tsx#L72)

- KPI cards get responsive minWidth
  [`KpiCard.tsx:19`](../../web-frontend/src/components/organizer/Analytics/KpiCard.tsx#L19)

- Kanban columns: fixed 280px mobile height → `minHeight` (content grows); md keeps viewport-height scroll
  [`TaskBoardPage.tsx:302`](../../web-frontend/src/pages/organizer/TaskBoardPage.tsx#L302)

**Tests (peripherals)**

- Stylesheet-inspection technique proving xs base rules (fail-on-revert verified)
  [`AiAssistDrawer.test.tsx:1`](../../web-frontend/src/components/organizer/EventPage/AiAssistDrawer.test.tsx#L1)

- matchMedia mock now parses max-width vs simulated viewport (375/1024)
  [`PartnerCreateEditModal.test.tsx:1`](../../web-frontend/src/components/organizer/PartnerManagement/PartnerCreateEditModal.test.tsx#L1)

- New coverage for the previously untested worst offender
  [`TopicStatusPanel.test.tsx:1`](../../web-frontend/src/components/organizer/TopicStatusPanel.test.tsx#L1)

- Scrollable-tabs + column-hiding + scroll-container assertions
  [`EventManagementAdminPage.test.tsx:1`](../../web-frontend/src/pages/organizer/EventManagementAdminPage.test.tsx#L1)
  [`OrganizerAnalyticsPage.test.tsx:1`](../../web-frontend/src/pages/organizer/OrganizerAnalyticsPage.test.tsx#L1)
  [`EventParticipantTable.test.tsx:1`](../../web-frontend/src/components/organizer/EventPage/EventParticipantTable.test.tsx#L1)
  [`UserTable.test.tsx:1`](../../web-frontend/src/components/organizer/UserManagement/UserTable.test.tsx#L1)
  [`NewsletterSubscriberTable.test.tsx:1`](../../web-frontend/src/components/organizer/NewsletterSubscribers/__tests__/NewsletterSubscriberTable.test.tsx#L1)
  [`PartnerMeetingsPage.test.tsx:1`](../../web-frontend/src/components/organizer/PartnerMeetingsPage.test.tsx#L1)
  [`DataTable.test.tsx:1`](../../web-frontend/src/components/organizer/Analytics/DataTable.test.tsx#L1)
