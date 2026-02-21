# Story 8.0: Partner Portal Shell

Status: ready-for-dev

## Story

As a **partner**,
I want a dedicated portal that shows my company's profile and provides navigation to all partner features,
so that I can access my analytics, topic voting, and meeting information without seeing other partners' data.

## Context

The organizer partner management screens (`/organizer/partners`) are fully built and real-API-backed. This story **reuses those components** for the partner-facing portal rather than building new ones. The organizer screens just need:
1. Their hardcoded mock user replaced with real auth context
2. Role-aware rendering (hide organizer-only controls when viewed by a partner)
3. A partner-facing route shell with navigation slots for Epic 8 features

Stories 8.1, 8.2, and 8.3 each replace a placeholder route created here with their real feature component.

## Acceptance Criteria

1. **AC1 - Partner sees their own company**: When a PARTNER user navigates to `/partners`, they see the detail view for their own company (read-only). Company is resolved from auth context — not a URL param the user can manipulate.

2. **AC2 - Role-aware PartnerDetailScreen**: `PartnerDetailScreen` reads the current user from `useAuth()` (replacing the hardcoded mock). When role is PARTNER:
   - Settings tab is hidden
   - Edit button in the detail header is hidden
   - Notes tab is read-only (no Add / Edit / Delete)
   - "Add Meeting" button is hidden

3. **AC3 - Partner portal layout**: A `PartnerPortalLayout` component provides top-level navigation:
   - **My Company** → `/partners/company`
   - **Analytics** → `/partners/analytics` (placeholder, Story 8.1 will replace)
   - **Topics** → `/partners/topics` (placeholder, Story 8.2 will replace)

4. **AC4 - Routes wired in App.tsx**:
   - `/partners` → redirects to `/partners/company`
   - `/partners/company` → `PartnerPortalLayout` + `PartnerCompanyPage`
   - `/partners/analytics` → `PartnerPortalLayout` + `PartnerAnalyticsPlaceholder` (Story 8.1 replaces)
   - `/partners/topics` → `PartnerPortalLayout` + `PartnerTopicsPlaceholder` (Story 8.2 replaces)
   - `/analytics` (old stub) → redirects to `/partners/analytics`
   - All routes protected by `PartnerRoute` (`['partner', 'organizer']`)

5. **AC5 - companyName from auth**: `UserContext` is extended with `companyName?: string`. If absent at runtime, `/partners/company` shows an error: *"Your account is not linked to a company — contact your organizer."*

6. **AC6 - Organizer unaffected**: All `/organizer/partners/*` routes and behaviour are identical to today. No regression.

7. **AC7 - i18n**: All new UI strings in `de/partner.json` and `en/partner.json`.

## What was deliberately cut

| Removed | Reason |
|---------|--------|
| New partner-specific detail components | Reuse organizer components with role guards — same data, different permissions |
| Separate meetings screen for partners | Story 8.3 is organizer-only; partners receive the ICS invite but don't manage meetings in the portal |
| Engagement metrics (bar, cards in overview tab) | Filled by Story 8.1 — hidden/greyed out until 8.1 ships |

## Tasks / Subtasks

### Task 1: Extend UserContext with companyName (AC: 1, 5)

- [ ] Inspect `src/contexts/AuthContext.tsx` and JWT token parser — check whether Cognito JWT carries `custom:companyName` or `custom:company` claim
- [ ] If claim exists: add `companyName?: string` to `UserContext` in `src/types/auth.ts` and populate it in `AuthContext` from the decoded token
- [ ] If claim absent: on auth context load, call `GET /api/v1/partners?companyId={user.companyId}` (single call, result stored in auth state) to resolve `companyName` from `companyId`
- [ ] Either way, `UserContext` ends up with:
  ```typescript
  companyName?: string;  // resolved from JWT or API on login
  ```

### Task 2: Fix PartnerDetailScreen — replace hardcoded currentUser (AC: 2, 6)

- [ ] In `PartnerDetailScreen.tsx` lines 65-69, replace:
  ```typescript
  // BEFORE (hardcoded mock)
  const currentUser = {
    username: 'organizer1',
    role: 'ORGANIZER' as const,
  };
  ```
  with:
  ```typescript
  const { user } = useAuth();
  const currentUser = {
    username: user?.username ?? '',
    role: user?.role ?? 'ORGANIZER',
  };
  ```
- [ ] Verify `PartnerSettingsTab` role check still works correctly for ORGANIZER role

### Task 3: Role-aware rendering in sub-components (AC: 2)

Propagate `role` from `PartnerDetailScreen` down to child components as a prop. Each component already receives `currentUser` or can receive a `role` prop.

- [ ] **`PartnerTabNavigation.tsx`**: add `role` prop, filter out the **Settings** tab when `role === 'PARTNER'`
- [ ] **`PartnerDetailHeader.tsx`**: add `role` prop, hide the **Edit** button and the **Schedule Meeting** / **Export Data** / **Send Email** action buttons when `role === 'PARTNER'` (these are already disabled but visible)
- [ ] **`PartnerNotesTab.tsx`**: add `role` prop; when `role === 'PARTNER'`, hide "Add Note" button and the edit/delete icons on each note card (read-only display)
- [ ] **`PartnerMeetingsTab.tsx`**: add `role` prop; when `role === 'PARTNER'`, hide the "Add Meeting" button (already disabled but visible)
- [ ] In `PartnerDetailScreen.tsx`: pass `role={currentUser.role}` to all tab and header components that receive the new prop

### Task 4: PartnerDetailScreen — dual-mode companyName (AC: 1, 6)

`PartnerDetailScreen` currently reads `companyName` from `useParams()` for the organizer view. For the partner view, the company name comes from auth context — not from the URL.

- [ ] Add optional prop to `PartnerDetailScreen`:
  ```typescript
  interface PartnerDetailScreenProps {
    companyName?: string;  // provided by partner portal; organizer falls back to useParams()
  }
  ```
- [ ] Internal logic:
  ```typescript
  const { companyName: urlCompanyName } = useParams<{ companyName: string }>();
  const resolvedCompanyName = props.companyName ?? urlCompanyName ?? '';
  ```
- [ ] Organizer pages (`OrganizerPartnerDetail.tsx`) pass no prop → unchanged behaviour
- [ ] Partner portal page passes `companyName={user.companyName}` from auth context

### Task 5: PartnerPortalLayout (AC: 3, 7)

- [ ] Create `src/components/partner/PartnerPortalLayout.tsx`
  - MUI `Tabs` or top nav bar with three entries: **My Company** | **Analytics** | **Topics**
  - Each tab uses `<NavLink>` (or React Router `useNavigate`) to `/partners/company`, `/partners/analytics`, `/partners/topics`
  - Active tab highlighted based on current path (`useLocation`)
  - `<Outlet />` renders the active child route
  - i18n keys: `partner.portal.nav.myCompany`, `partner.portal.nav.analytics`, `partner.portal.nav.topics`

### Task 6: PartnerCompanyPage (AC: 1, 4, 5)

- [ ] Create `src/pages/PartnerCompanyPage.tsx`
  - Reads `user?.companyName` from `useAuth()`
  - If no `companyName`: renders `<Alert severity="error">` with translated error message (`partner.portal.noCompanyLinked`)
  - Otherwise: renders `<PartnerDetailScreen companyName={user.companyName} />`

### Task 7: Placeholder pages for 8.1 and 8.2 (AC: 4)

- [ ] Create `src/pages/PartnerAnalyticsPlaceholder.tsx` — MUI `Typography` with `t('partner.analytics.comingSoon')`, to be replaced entirely by Story 8.1
- [ ] Create `src/pages/PartnerTopicsPlaceholder.tsx` — MUI `Typography` with `t('partner.topics.comingSoon')`, to be replaced entirely by Story 8.2
- [ ] Both are intentionally minimal — Story 8.1 and 8.2 delete these files and wire in their own components

### Task 8: Update App.tsx routes (AC: 4, 6)

- [ ] Replace the existing `/partners` stub route and `/analytics` stub route with:
  ```tsx
  {/* Partner Portal */}
  <Route path="/partners" element={<PartnerRoute><PartnerPortalLayout /></PartnerRoute>}>
    <Route index element={<Navigate to="company" replace />} />
    <Route path="company" element={<PartnerCompanyPage />} />
    <Route path="analytics" element={<PartnerAnalyticsPlaceholder />} />  {/* Story 8.1 replaces */}
    <Route path="topics" element={<PartnerTopicsPlaceholder />} />          {/* Story 8.2 replaces */}
  </Route>

  {/* Redirect old /analytics stub */}
  <Route path="/analytics" element={<Navigate to="/partners/analytics" replace />} />
  ```
- [ ] Delete `src/pages/Partners.tsx` (replaced by `PartnerPortalLayout` + `PartnerCompanyPage`)
- [ ] Confirm `Analytics.tsx` stub is removed or redirected (no longer needed as a page)

### Task 9: i18n keys (AC: 7)

- [ ] Add to `public/locales/de/partner.json` and `en/partner.json`:
  - `partner.portal.nav.myCompany` — "Mein Unternehmen" / "My Company"
  - `partner.portal.nav.analytics` — "Analysen" / "Analytics"
  - `partner.portal.nav.topics` — "Themen" / "Topics"
  - `partner.portal.noCompanyLinked` — "Ihr Konto ist keinem Unternehmen zugeordnet" / "Your account is not linked to a company"
  - `partner.portal.noCompanyLinked.detail` — "Bitte wenden Sie sich an Ihren Organisator" / "Please contact your organizer"
  - `partner.analytics.comingSoon` — "Analysen folgen bald" / "Analytics coming soon"
  - `partner.topics.comingSoon` — "Themenabstimmung folgt bald" / "Topic voting coming soon"

### Task 10: Tests (AC: 1, 2, 4, 6)

- [ ] `PartnerDetailScreen.test.tsx`:
  - ORGANIZER role → Settings tab visible, Edit button visible, Notes CRUD visible
  - PARTNER role → Settings tab hidden, Edit button hidden, Notes read-only, Add Meeting hidden
  - `companyName` prop overrides `useParams()` when provided
- [ ] `PartnerPortalLayout.test.tsx`:
  - Three nav tabs render
  - Active tab matches current route
- [ ] `PartnerCompanyPage.test.tsx`:
  - No `companyName` in auth → renders error alert
  - With `companyName` → renders PartnerDetailScreen
- [ ] E2E `e2e/partner/portal-navigation.spec.ts`:
  - Partner logs in → lands on `/partners/company`
  - Company detail shows their own company (not other companies)
  - Analytics tab navigates to `/partners/analytics`
  - Topics tab navigates to `/partners/topics`
  - Organizer visiting `/organizer/partners/CompanyA` works unchanged (regression check)

## Dev Notes

### New file locations

```
src/
  components/
    partner/
      PartnerPortalLayout.tsx      ← new
  pages/
    PartnerCompanyPage.tsx         ← new (replaces Partners.tsx stub)
    PartnerAnalyticsPlaceholder.tsx ← new temporary (deleted by Story 8.1)
    PartnerTopicsPlaceholder.tsx    ← new temporary (deleted by Story 8.2)
```

Existing organizer components in `src/components/organizer/PartnerManagement/` are **modified in place** (Tasks 2–4) — not moved or copied.

### PartnerRoute behaviour

`PartnerRoute` allows `['organizer', 'partner']`. Organizers can navigate to `/partners/company` to preview the partner view — intentional and useful for testing without a partner account.

### companyName resolution priority

1. Check `custom:companyName` Cognito claim in decoded JWT — zero extra API calls
2. If absent: single `GET /api/v1/partners?companyId={companyId}` call on login, result stored in auth state
3. If neither: show error alert (AC5)

### Notes tab read-only for partners

Partners can see organizer notes (transparency about the partnership) but cannot add, edit, or delete them. This is the expected behaviour — notes are organizer-managed records.

### ADR Compliance

- **ADR-003**: `companyName` string used throughout — consistent with all existing partner hooks
- **ADR-008**: `PartnerRoute` (frontend) + backend `@PreAuthorize` (per-story) together enforce access

### References

- [Source: web-frontend/src/components/organizer/PartnerManagement/PartnerDetailScreen.tsx — hardcoded mock lines 65-69]
- [Source: web-frontend/src/hooks/useAuth/useAuth.ts — auth context structure]
- [Source: web-frontend/src/App.tsx — existing route definitions]
- [Source: web-frontend/src/types/auth.ts — UserContext type]
- Downstream: Story 8.1 replaces `PartnerAnalyticsPlaceholder` at `/partners/analytics`
- Downstream: Story 8.2 replaces `PartnerTopicsPlaceholder` at `/partners/topics`

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List
