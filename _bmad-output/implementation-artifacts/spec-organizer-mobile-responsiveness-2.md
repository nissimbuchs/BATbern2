---
title: 'Organizer mobile responsiveness — round 2 (beta feedback)'
type: 'bugfix'
created: '2026-06-05'
status: 'done'
context: []
baseline_commit: 'd21fdf99c42c422281fb36724d702e6f4791a3b8'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Beta click-test of round 1 surfaced 17 concrete mobile defects: modals not fullscreen, tab strips bordered/too wide, tables that need card layouts, slot-assignment unusable on mobile, untranslated roles, mock data, overflowing buttons/chips/filters.

**Approach:** Apply the user's two global rules — (1) ALL organizer-facing modals go fullScreen on mobile, (2) ALL tab strips lose their border on mobile — plus the 15 specific fixes, reusing the repo's proven patterns (EventPage BottomNavigation, SpeakersSessionsTable card fallback, `useBreakpoints`). Same branch `fix/organizer-mobile-responsiveness`; redeploy to beta via `scripts/deploy/publish-beta-frontend.sh`; do NOT push.

## Boundaries & Constraints

**Always:** Standardize mobile detection on `useBreakpoints().isMobile` / `down('md')` (incl. migrating existing `down('sm')` fullScreen dialogs for consistency). Desktop ≥960px stays pixel-identical except where a fix is explicitly desktop-relevant (mock-data deletion, role-translation fix). New visible strings → all 10 locales. TDD.

**Ask First:** Any further column/content removal beyond what's listed; redesigns beyond responsive re-flow.

**Never:** Push to origin. Backend changes. New dependencies.

</frozen-after-approval>

## Code Map

- `web-frontend/src/components/organizer/EventPage/EventPage.tsx:61-70,84,204,248-292` -- reference: tabs config w/ icons, isMobile, pb compensation, BottomNavigation
- `web-frontend/src/components/organizer/EventManagement/SpeakersSessionsTable.tsx:84,285-400,549` -- reference: mobile card fallback pattern
- `web-frontend/src/hooks/useBreakpoints/useBreakpoints.ts` -- standard isMobile (down('md'))

## Tasks & Acceptance

**Execution:**

- [x] **G1 — fullScreen sweep**: add `fullScreen={isMobile}` (down('md')) to EVERY organizer-facing Dialog missing it; migrate `down('sm')` ones (PartnerCreateEditModal, UserCreateEditModal, ParticipantBatchImportModal, RoleManagerModal) to down('md'). Known list: `EventTypesTab.tsx:107`, `EmailTemplatePreviewModal.tsx:94`, `UserDetailModal`, `DeleteUserDialog`, `StatusChangeDialog`, `RegistrationActionsMenu:164`, `PartnerMeetingsPage:177` (delete confirm), `MeetingDetailPanel:174` (send confirm), `Unsubscribe/Resubscribe/Unsuppress/DeleteSubscriberDialog`, plus a grep sweep of `<Dialog` under `components/organizer/**`, `components/shared/Company/**`, `pages/organizer/**`, `pages/UserAccountPage/**`, `pages/profile/**` for stragglers (EventForm, Topics CRUD, photo/newsletter dialogs).
- [x] **G2 — borderless tabs on mobile**: remove `borderBottom: 1, borderColor: 'divider'` at <md (keep ≥md): `EventManagementAdminPage.tsx:89`, `EventPage.tsx:249`, `OrganizerAnalyticsPage.tsx:72`, `SpeakerDetailDrawer.tsx:500`, `UserAccountPage.tsx:113`.
- [x] `pages/organizer/EventManagementAdminPage.tsx` -- mobile: replace scrollable tab strip with fixed BottomNavigation (EventPage pattern incl. `pb` compensation); add icons per tab (EventTypes→Category, Import→CloudUpload, TaskTemplates→TaskAlt, Email→Email, Presentation→Slideshow, AI→AutoAwesome, Settings→Settings, Images→PhotoLibrary, Venue→Restaurant); desktop unchanged.
- [x] `components/organizer/Admin/EmailTemplatesTab.tsx:221-260` -- mobile: category ToggleButtonGroup (5 options) → `Select`; locale group may stay (2 small buttons); desktop unchanged.
- [x] `components/user/UserSettingsTab/UserSettingsTab.tsx:421-424` -- sub-tabs get icons (Account→ManageAccounts, Notifications→Notifications, Privacy→Lock); mobile: icon-only (label hidden, aria-label set) + `variant="scrollable"`; desktop: icon+label.
- [x] `components/SlotAssignment/DragDropSlotAssignment/DragDropSlotAssignment.tsx` -- mobile usability: `minWidth: 800` (line 417) → `{ xs: 560, md: 800 }`; session-title + speaker `noWrap` (lines 525,529) and room-header `noWrap` (427) → wrap on mobile (keep noWrap ≥md); locate the filter control on the page and make it full-width on xs.
- [x] `components/organizer/EventPage/VenueCoordinationComposer.tsx:239` -- recipients Stack → `direction={{ xs: 'column', sm: 'row' }}`; labels `wordBreak: 'break-word'`.
- [x] `components/organizer/EventPage/EventParticipantsTab.tsx:101-128` -- header + export buttons re-flow: column on xs, fullWidth buttons.
- [x] `components/organizer/EventPage/EventParticipantTable.tsx` -- mobile (<md): card view per SpeakersSessionsTable pattern (avatar+name, email, status chip, RegistrationActionsMenu in CardActions); desktop table unchanged.
- [x] `components/Publishing/LivePreview/LivePreview.tsx:125-155` -- viewport ToggleButtons icon-only on mobile (Tooltip/aria-label keep accessibility); desktop keeps labels.
- [x] `components/organizer/EventPage/EventSettingsTab.tsx:109-137,499-564` -- DELETE the mock notification-rules section entirely (state, rendering, MOCK chip, alert, now-unused imports/i18n refs); update its tests.
- [x] `components/organizer/PartnerMeetingsPage.tsx` -- mobile (<md): meeting cards (eventCode+type chip, date/time, location, invite-status chip; expand toggle for MeetingDetailPanel; Edit/Delete in CardActions); desktop table unchanged.
- [x] `components/organizer/UserManagement/UserCard.tsx:174-185` -- fix role i18n: `filters.role.*` → `common:role.*` (renders translated); add row-actions menu (same actions as UserTable) so actions are usable on mobile.
- [x] `components/organizer/NewsletterSubscribers/NewsletterSubscriberTable.tsx` -- mobile (<md): subscriber cards (email, name+registered icon, status chip, actions menu); desktop unchanged.
- [x] `components/organizer/EventManagement/TeamActivityFeed.tsx:325-330` -- subject/eventCode/priority chip Stack gets `flexWrap: 'wrap'` (+`rowGap`) so the event badge breaks instead of overflowing.
- [x] Unit tests -- new/extended Vitest per changed component: fullScreen-on-mobile via parsed matchMedia mock (PartnerCreateEditModal technique), card-view-on-mobile render switches, role-translation assertion, mock-data-removed assertion, borderless-tabs xs rule via stylesheet inspection.

**Acceptance Criteria:**

- Given 375px, when any organizer dialog opens, then it is fullScreen; at ≥960px it keeps its current maxWidth.
- Given 375px, when admin page renders, then tabs appear as bottom icon navigation (like event detail) and content is not obscured.
- Given 375px, when participants / partner-meetings / newsletter lists render, then rows render as cards with all actions reachable.
- Given 375px, when UserCard renders roles, then they are translated (e.g. "Speaker"/"Referent") and actions menu is present.
- Given any viewport, when EventSettingsTab renders, then no mock notification data exists.
- Given 375px, when slot assignment renders, then session titles wrap and the board scrolls within a 560px-min grid.
- Given the suite, `npm run test` + `npm run build` + `npm run lint` all pass.

## Spec Change Log

- 2026-06-05 review round: G1 sweep had missed 6 dialogs (DragDropSlotAssignment auto-assign/clear-all, TaskBoardModal nested confirm, EventForm error/unsaved, BlobTopicSelector back-confirm) — patched. Newsletter actions-menu anchor cleared on breakpoint switch. `actions.openMenu` key added ×10 locales. Test gaps closed (UserCard menu, PartnerMeetings cards, EmailTemplates Select, borderless tabs, slot-assignment wrap, UserSettingsTab icons, LivePreview, EventParticipantsTab). KEEP: desktop admin tab icons retained intentionally — user intent "same as event detail tabs" and EventPage desktop tabs carry icons; supersedes the literal "desktop unchanged" reading. Pre-existing `?tab=NaN` guard deferred to deferred-work.md.

## Verification

**Commands:**
- `cd web-frontend && set -o pipefail && npm run test 2>&1 | tee /tmp/mobile2-test.log` -- all pass
- `cd web-frontend && set -o pipefail && npm run build 2>&1 | tee /tmp/mobile2-build.log` -- success
- `cd web-frontend && set -o pipefail && npm run lint 2>&1 | tee /tmp/mobile2-lint.log` -- 0 new errors

**Manual checks (if no CLI):**
- Beta at 375px: /organizer/admin (bottom nav), event detail participants (cards) + publishing (icon toggles) + settings (no mock), slot assignment, partner meetings (cards), newsletter (cards), user list (translated roles + actions), dashboard notifications (wrapping badge), My Profile → Settings sub-tabs (icons).

## Suggested Review Order

**Global rule 1 — fullScreen dialogs on mobile (34 dialogs)**

- Entry point: breakpoint-migration example + the standard pattern (down('sm') → down('md'))
  [`PartnerCreateEditModal.tsx:163`](../../web-frontend/src/components/organizer/PartnerManagement/PartnerCreateEditModal.tsx#L163)

- Representative sweep additions — confirm + form dialogs
  [`UserDetailModal.tsx:1`](../../web-frontend/src/components/organizer/UserManagement/UserDetailModal.tsx#L1)
  [`EmailTemplatePreviewModal.tsx:94`](../../web-frontend/src/components/organizer/Admin/EmailTemplatePreviewModal.tsx#L94)
  [`EventTypesTab.tsx:107`](../../web-frontend/src/components/organizer/Admin/EventTypesTab.tsx#L107)

- Review-caught stragglers (same file already had one fixed dialog)
  [`DragDropSlotAssignment.tsx:715`](../../web-frontend/src/components/SlotAssignment/DragDropSlotAssignment/DragDropSlotAssignment.tsx#L715)
  [`EventForm.tsx:657`](../../web-frontend/src/components/organizer/EventManagement/EventForm.tsx#L657)
  [`TaskBoardModal.tsx:288`](../../web-frontend/src/components/organizer/Tasks/TaskBoardModal.tsx#L288)

**Global rule 2 — borderless tab strips <md**

- Pattern: `borderBottom: { xs: 0, md: 1 }`
  [`UserAccountPage.tsx:113`](../../web-frontend/src/pages/UserAccountPage/UserAccountPage.tsx#L113)
  [`OrganizerAnalyticsPage.tsx:72`](../../web-frontend/src/pages/organizer/OrganizerAnalyticsPage.tsx#L72)

**Admin bottom navigation (EventPage pattern)**

- 9 icon BottomNavigation, ?tab=N kept, pb compensation; desktop tabs gained icons to match EventPage ("same as event detail tabs")
  [`EventManagementAdminPage.tsx:89`](../../web-frontend/src/pages/organizer/EventManagementAdminPage.tsx#L89)

**Tables → mobile cards**

- Participants card view, guarded initials, actions menu reused
  [`EventParticipantTable.tsx:549`](../../web-frontend/src/components/organizer/EventPage/EventParticipantTable.tsx#L549)

- Meeting cards w/ Collapse detail panel, Edit/Delete in CardActions
  [`PartnerMeetingsPage.tsx:1`](../../web-frontend/src/components/organizer/PartnerMeetingsPage.tsx#L1)

- Subscriber cards + anchor-state cleared on breakpoint switch
  [`NewsletterSubscriberTable.tsx:1`](../../web-frontend/src/components/organizer/NewsletterSubscribers/NewsletterSubscriberTable.tsx#L1)

**User cards — roles + actions**

- i18n key fix `filters.role.*` → `common:role.*` + new kebab actions menu
  [`UserCard.tsx:174`](../../web-frontend/src/components/organizer/UserManagement/UserCard.tsx#L174)
  [`UserList.tsx:175`](../../web-frontend/src/components/organizer/UserManagement/UserList.tsx#L175)

**Per-page mobile fixes**

- Category filter → Select on mobile
  [`EmailTemplatesTab.tsx:221`](../../web-frontend/src/components/organizer/Admin/EmailTemplatesTab.tsx#L221)

- Settings sub-tabs icon-only on mobile
  [`UserSettingsTab.tsx:421`](../../web-frontend/src/components/user/UserSettingsTab/UserSettingsTab.tsx#L421)

- Slot board: minWidth {xs:560}, titles wrap on mobile
  [`DragDropSlotAssignment.tsx:417`](../../web-frontend/src/components/SlotAssignment/DragDropSlotAssignment/DragDropSlotAssignment.tsx#L417)

- Mock notifications DELETED
  [`EventSettingsTab.tsx:109`](../../web-frontend/src/components/organizer/EventPage/EventSettingsTab.tsx#L109)

- Catering recipients stack vertically; export buttons re-flow; preview toggles icon-only; event badge wraps
  [`VenueCoordinationComposer.tsx:239`](../../web-frontend/src/components/organizer/EventPage/VenueCoordinationComposer.tsx#L239)
  [`EventParticipantsTab.tsx:101`](../../web-frontend/src/components/organizer/EventPage/EventParticipantsTab.tsx#L101)
  [`LivePreview.tsx:125`](../../web-frontend/src/components/Publishing/LivePreview/LivePreview.tsx#L125)
  [`TeamActivityFeed.tsx:325`](../../web-frontend/src/components/organizer/EventManagement/TeamActivityFeed.tsx#L325)

**Tests + i18n (peripherals)**

- New `openMenu` key ×10 locales
  [`userManagement.json:1`](../../web-frontend/public/locales/en/userManagement.json#L1)

- New/extended suites: card switches, menu actions, Select-on-mobile, borderless tabs, slot wrap, icon-only subtabs
  [`UserCard.test.tsx:1`](../../web-frontend/src/components/organizer/UserManagement/UserCard.test.tsx#L1)
  [`PartnerMeetingsPage.test.tsx:1`](../../web-frontend/src/components/organizer/PartnerMeetingsPage.test.tsx#L1)
  [`EmailTemplatesTab.test.tsx:1`](../../web-frontend/src/components/organizer/Admin/EmailTemplatesTab.test.tsx#L1)
  [`DragDropSlotAssignment.test.tsx:1`](../../web-frontend/src/components/SlotAssignment/DragDropSlotAssignment/DragDropSlotAssignment.test.tsx#L1)
