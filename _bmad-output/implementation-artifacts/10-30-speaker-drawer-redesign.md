# Story 10.30: Speaker Drawer Redesign — Tabbed Layout, Organizer Assignment, Mobile UX

Status: ready-for-dev

## Story

As an **organizer**,
I want the speaker detail drawer organized into tabs with inline organizer editing,
so that I can manage speaker outreach efficiently on any device without excessive scrolling.

## Acceptance Criteria

1. **AC1**: Tabbed drawer with Overview, Details, Activity tabs replaces the monolithic drawer
2. **AC2**: Default tab selection matches speaker workflow state (IDENTIFIED/CONTACTED → Activity, DECLINED/CONTENT_SUBMITTED/QUALITY_REVIEWED → Details, others → Overview)
3. **AC3**: Assigned organizer editable via inline autocomplete on Overview tab, persisted via `PATCH /api/v1/events/{eventCode}/speakers/pool/{speakerId}`
4. **AC4**: "Submit Content" and "Review Content" render as in-drawer sub-views with back button (no second drawer)
5. **AC5**: Contact history and mark-contacted form in Activity tab with full-height scroll (no nested `maxHeight: 150px`)
6. **AC6**: Mobile: full-width drawer, scrollable tabs, sticky action footer
7. **AC7**: All existing `data-testid` attributes preserved for Playwright E2E tests
8. **AC8**: `npm run build` passes; existing tests pass

## Tasks / Subtasks

- [ ] Task 1: Create SpeakerDrawer directory and component structure (AC: 1)
  - [ ] 1.1 Create `web-frontend/src/components/organizer/SpeakerDrawer/` directory
  - [ ] 1.2 Create `index.ts` barrel export
  - [ ] 1.3 Create `SpeakerDetailDrawer.tsx` — MUI Drawer container with Tabs + `drawerView` state machine (`null | 'content-submission' | 'quality-review'`); `null` = tabs visible, non-null = sub-view replaces tabs
  - [ ] 1.4 Drawer width: `{ xs: '100%', sm: 520 }`, tabs `variant={isMobile ? 'scrollable' : 'standard'}`

- [ ] Task 2: Create SpeakerDrawerHeader component (AC: 1)
  - [ ] 2.1 Create `SpeakerDrawerHeader.tsx` — speaker name, company, status chip, close button
  - [ ] 2.2 Reuse status chip color logic from existing `SpeakerOutreachDetailsDrawer.tsx` lines 358-375
  - [ ] 2.3 Include tentative badge when `speaker.isTentative`

- [ ] Task 3: Create OverviewTabPanel component (AC: 1, 3)
  - [ ] 3.1 Create `OverviewTabPanel.tsx`
  - [ ] 3.2 Include `AssignedOrganizerField` (inline-editable autocomplete)
  - [ ] 3.3 Quick info section: email, expertise, key dates
  - [ ] 3.4 Context-dependent action buttons:
    - IDENTIFIED: email input + "Send Invitation" button (extract from lines 527-584)
    - INVITED: "Send Reminder" button (extract from lines 586-608)
    - ACCEPTED: "Submit Content" button → sets `drawerView='content-submission'` (extract from lines 610-622)
    - CONTENT_SUBMITTED: "Review Content" button → sets `drawerView='quality-review'` (extract from lines 624-636)
    - CANCELLED/NO_SHOW: no action buttons (read-only overview)
  - [ ] 3.5 Mobile: sticky action footer (`position: sticky, bottom: 0, bgcolor: 'background.paper'`)

- [ ] Task 4: Create AssignedOrganizerField component (AC: 3)
  - [ ] 4.1 Create `AssignedOrganizerField.tsx`
  - [ ] 4.2 Display current organizer name (from `speaker.assignedOrganizerId` resolved via organizer list)
  - [ ] 4.3 Use `<OrganizerSelect>` from `@/components/shared/OrganizerSelect` — the established shared component that self-fetches organizers via `useUserList({ role: 'ORGANIZER' })`. Same pattern used in `SpeakerBrainstormingPanel`, `EventTasksTab`, `CustomTaskModal`, `EventSettingsTab`. Pass `includeUnassigned={true}` to allow clearing assignment.
  - [ ] 4.4 On select: call `usePatchSpeakerPool` hook with `{ assignedOrganizerId: selectedUsername }`
  - [ ] 4.5 Show loading spinner during save, success/error snackbar feedback
  - [ ] 4.6 After successful PATCH, the `usePatchSpeakerPool` hook invalidates `speakerPoolKeys.list(eventCode)` which re-fetches the pool — the `OrganizerSelect` resolves names internally so the displayed name updates automatically on re-render

- [ ] Task 5: Create DetailsTabPanel component (AC: 1, 5)
  - [ ] 5.1 Create `DetailsTabPanel.tsx`
  - [ ] 5.2 Response details section (green bg) — extract from lines 378-414: acceptedAt, preferredTimeSlot, travelRequirements, technicalRequirements, initialPresentationTitle, preferenceComments
  - [ ] 5.3 Decline details section (red bg) — extract from lines 416-430: declinedAt, declineReason
  - [ ] 5.4 Tentative details section (amber bg) — extract from lines 432-441: tentativeReason
  - [ ] 5.5 Revision feedback section (red bg) — extract from lines 443-453: notes when contentStatus=REVISION_NEEDED
  - [ ] 5.6 Submitted content section (green bg) — extract from lines 455-525: submittedTitle, submittedAbstract (full-height scroll, remove `maxHeight: 150px`), materialFileName + download link, contentStatus badge, contentSubmittedAt
  - [ ] 5.7 Email input for IDENTIFIED speakers without email — extract from lines 527-560

- [ ] Task 6: Create ActivityTabPanel component (AC: 1, 5)
  - [ ] 6.1 Create `ActivityTabPanel.tsx`
  - [ ] 6.2 Mark-contacted form at top — extract from lines 642-726: contactMethod dropdown, contactDate picker, notes textarea, submit/cancel buttons
  - [ ] 6.3 Contact history timeline below — extract from lines 730-805: chronological list with method icons, date, notes, organizer username
  - [ ] 6.4 Loading/error/empty states for outreach history
  - [ ] 6.5 Full-height scroll on the tab panel itself (no nested maxHeight)

- [ ] Task 7: Implement sub-views for content submission and quality review (AC: 4)
  - [ ] 7.1 Extract content submission form logic from `ContentSubmissionDrawer.tsx` (431 lines) into a sub-view rendered within `SpeakerDetailDrawer` when `drawerView='content-submission'`. **Note:** This is not a simple form lift — it includes `UserAutocomplete` with `searchUsers()`, `UserAvatar`, `UserCreateEditModal` (modal-within-drawer), speaker-user linking with role assignment, and a `useRef` tracking prefilled speaker ID. All must be preserved and tested.
  - [ ] 7.2 Extract quality review form logic from `QualityReviewDrawer.tsx` (461 lines) into a sub-view rendered within `SpeakerDetailDrawer` when `drawerView='quality-review'`. **Note:** Uses `useFeatureFlags()` to conditionally show AI analysis button and `useAiAnalyzeAbstract()` hook — preserve the feature-flag gating so AI analysis only renders when enabled.
  - [ ] 7.3 Back button at top of sub-views → resets `drawerView=null` (returns to tabbed view)
  - [ ] 7.4 Preserve all existing functionality: user autocomplete + create modal (content), AI analysis with feature flag gating (quality review), reject feedback form with notes

- [ ] Task 8: Implement default tab selection logic (AC: 2)
  - [ ] 8.1 `getDefaultTab(speaker)` function — explicit mapping for all statuses:
    - IDENTIFIED, CONTACTED → 2 (Activity) — outreach workflow, contact history most relevant
    - DECLINED, CONTENT_SUBMITTED, QUALITY_REVIEWED → 1 (Details) — response/content info most relevant
    - INVITED, ACCEPTED, CONFIRMED, READY, CANCELLED, NO_SHOW → 0 (Overview) — action buttons most relevant
    - Fallback for any unknown/future status → 0 (Overview)
  - [ ] 8.2 Reset tab on speaker change (`useEffect` on `speaker?.id`)
  - [ ] 8.3 Reset `drawerView` to `null` (tabbed view) when drawer opens or speaker changes

- [ ] Task 9: Wire up in EventSpeakersTab (AC: 1, 7)
  - [ ] 9.1 Replace `SpeakerOutreachDetailsDrawer`, `ContentSubmissionDrawer`, `QualityReviewDrawer` imports with single `SpeakerDetailDrawer`
  - [ ] 9.2 Remove `contentDrawerOpen`, `contentSpeaker`, `reviewDrawerOpen`, `reviewSpeaker` state variables
  - [ ] 9.3 Remove `onOpenContentSubmission`, `onOpenQualityReview` callbacks — sub-views handled internally
  - [ ] 9.4 Keep `selectedSpeaker` + `detailsDrawerOpen` as the only drawer state

- [ ] Task 10: Add i18n translations (AC: 1)
  - [ ] 10.1 Add keys to organizer namespace: `speakers.tabs.overview`, `speakers.tabs.details`, `speakers.tabs.activity`
  - [ ] 10.2 Add back button label: `speakers.drawer.back`

- [ ] Task 11: Cleanup and delete old files (AC: 7, 8)
  - [ ] 11.1 Delete `web-frontend/src/components/organizer/SpeakerOutreach/SpeakerOutreachDetailsDrawer.tsx` (823 lines)
  - [ ] 11.2 Delete `web-frontend/src/components/organizer/SpeakerStatus/ContentSubmissionDrawer.tsx` (430 lines)
  - [ ] 11.3 Delete `web-frontend/src/components/organizer/SpeakerStatus/QualityReviewDrawer.tsx` (460 lines)
  - [ ] 11.4 Remove old imports from `EventSpeakersTab.tsx`
  - [ ] 11.5 Verify `npm run build` passes with zero TypeScript errors

- [ ] Task 12: Verify and test (AC: 7, 8)
  - [ ] 12.1 Run `npm run build` — zero errors
  - [ ] 12.2 Verify ALL existing `data-testid` attributes are preserved in the new components. Complete checklist:
    - From SpeakerOutreachDetailsDrawer: `contact-method-select`, `contact-method-email`, `contact-method-phone`, `contact-method-in_person`, `contact-notes-field`, `mark-contacted-button`
    - From ContentSubmissionDrawer: `speaker-search-field`, `presentation-title-field`, `presentation-abstract-field`, `submit-speaker-content-button`
    - From QualityReviewDrawer: `approve-content-button`
    - Run `grep -r 'data-testid' web-frontend/src/components/organizer/SpeakerDrawer/` and compare against this list
  - [ ] 12.3 Run existing frontend tests — no regressions

## Dev Notes

### Architecture Patterns

- **MUI Drawer** — existing pattern: slide from right, `anchor="right"`, `PaperProps={{ sx: { width: ... } }}`
- **MUI Tabs** — use `<Tabs value={tab} onChange={...}>` + `<TabPanel>` pattern; `variant="scrollable"` on mobile
- **Snackbar feedback** — existing pattern used in all drawers for invite/remind/save operations
- **`useMediaQuery`** — `const isMobile = useMediaQuery(theme.breakpoints.down('sm'))` for responsive behavior
- **`drawerView` state machine** — `null | 'content-submission' | 'quality-review'`. When `null`, the tabbed UI (Overview/Details/Activity) is visible. When non-null, the corresponding sub-view replaces the entire tab area with a back button at the top. Reset to `null` on drawer open, speaker change, or back button click.

### Existing Hooks and Components to Reuse

- `useSpeakerOutreachHistory(eventCode, speakerId)` — `web-frontend/src/hooks/useSpeakerOutreach.ts`
- `useRecordOutreach()` — same file
- `useSendInvitation(eventCode)` — `web-frontend/src/hooks/useSpeakerPool.ts`
- `useSendReminder(eventCode)` — same file
- `usePatchSpeakerPool()` — same file (Phase 2, already committed on this branch)
- **`<OrganizerSelect>`** — `web-frontend/src/components/shared/OrganizerSelect/OrganizerSelect.tsx` — **the established pattern** for organizer dropdowns. Self-fetches organizers via `useUserList({ role: 'ORGANIZER' })`. Used in `SpeakerBrainstormingPanel`, `EventTasksTab`, `CustomTaskModal`, `EventSettingsTab`. Accepts `value`, `onChange`, `includeUnassigned`, `includeAllOption` props.
- **`useOrganizers()`** — exported from same file, returns `{ organizers, isLoading }` for non-dropdown usage (e.g., resolving organizer name from ID for display)
- `useFeatureFlags()` — needed in quality review sub-view to gate AI analysis
- `useAiAnalyzeAbstract()` — needed in quality review sub-view for AI abstract analysis

### Existing Components to Extract From

| Source File | Lines | What to Extract |
|---|---|---|
| `SpeakerOutreachDetailsDrawer.tsx` | 100-128 | Send invitation logic |
| Same | 141-159 | Send reminder logic |
| Same | 162-201 | Email input state + validation |
| Same | 203-303 | Mark-contacted form state + handlers + getContactMethodIcon + formatDate |
| Same | 336-637 | Speaker info card + all status-dependent sections |
| Same | 642-726 | Mark-contacted form JSX |
| Same | 730-805 | Contact history timeline JSX |
| `ContentSubmissionDrawer.tsx` | 285-416 | Content submission form (user autocomplete, title, abstract). **Includes:** `UserAutocomplete` + `searchUsers()`, `UserAvatar`, `UserCreateEditModal` (modal-within-drawer), speaker-user linking, `useRef` for prefilled speaker ID |
| `QualityReviewDrawer.tsx` | 255-440 | Quality review form (AI analysis, approve/reject). **Includes:** `useFeatureFlags()` gating on AI button, `useAiAnalyzeAbstract()` hook, reject feedback form with notes textarea |

### Project Structure Notes

- New components go in `web-frontend/src/components/organizer/SpeakerDrawer/` (new directory, parallel to existing `SpeakerOutreach/` and `SpeakerStatus/`)
- Follow existing naming: PascalCase component files, barrel `index.ts`
- Keep `data-testid` attributes identical to prevent Playwright E2E breakage

### References

- [Plan: Speaker Drawer Redesign](/Users/nissim/.claude/plans/recursive-rolling-abelson.md)
- [Source: SpeakerOutreachDetailsDrawer.tsx](web-frontend/src/components/organizer/SpeakerOutreach/SpeakerOutreachDetailsDrawer.tsx) — 823-line monolith being decomposed
- [Source: ContentSubmissionDrawer.tsx](web-frontend/src/components/organizer/SpeakerStatus/ContentSubmissionDrawer.tsx) — 430 lines
- [Source: QualityReviewDrawer.tsx](web-frontend/src/components/organizer/SpeakerStatus/QualityReviewDrawer.tsx) — 460 lines
- [Source: EventSpeakersTab.tsx](web-frontend/src/components/organizer/EventPage/EventSpeakersTab.tsx) — orchestrator
- [Source: useSpeakerPool.ts](web-frontend/src/hooks/useSpeakerPool.ts) — hooks including usePatchSpeakerPool
- [Source: speakerPool.types.ts](web-frontend/src/types/speakerPool.types.ts) — types including PatchSpeakerPoolRequest
- [Source: OrganizerSelect.tsx](web-frontend/src/components/shared/OrganizerSelect/OrganizerSelect.tsx) — shared organizer dropdown + useOrganizers hook
- [Source: SpeakerBrainstormingPanel.tsx](web-frontend/src/components/SpeakerBrainstormingPanel/SpeakerBrainstormingPanel.tsx) — reference implementation of OrganizerSelect usage

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List
