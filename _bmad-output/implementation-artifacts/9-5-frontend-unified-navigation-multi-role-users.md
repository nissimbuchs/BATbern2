# Story 9.5: Frontend Unified Navigation for Multi-Role Users

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **speaker who is also an attendee** (or any multi-role user),
I want to see navigation options for all my portals when logged in via Cognito,
so that I can easily switch between my roles without re-authentication.

## Acceptance Criteria

1. **AC1 — Multi-Role Navigation:** When a Cognito-authenticated user has multiple roles (e.g., `custom:role=ORGANIZER,SPEAKER`), the main navigation bar shows combined menu items from ALL roles — not just the primary role. Items are grouped by portal section with visual separators between role groups.
2. **AC2 — Speaker Portal Link:** Users with SPEAKER role see a "Speaker Portal" navigation item that links to `/speaker-portal/login` (where they can quickly authenticate with their password to obtain a speaker session token). This item appears in the speaker section of the navigation.
3. **AC3 — Active Portal Highlighting:** The currently active portal section (based on the URL path prefix like `/organizer/`, `/speaker/`, `/attendee/`) is visually highlighted in the navigation — the active nav item has the existing blue selected state, and if role groups have section headers, the active group is emphasized.
4. **AC4 — ProtectedRoute Multi-Role Support:** `ProtectedRoute` checks the user's full `roles[]` array (not just primary `role`) against `allowedRoles`. A user with `roles: ['organizer', 'speaker']` can access both `OrganizerRoute` and `SpeakerRoute` protected pages.
5. **AC5 — Mobile Responsive Multi-Role Nav:** The `MobileDrawer` shows navigation items for all user roles in a vertical list, with role section headers/dividers for clarity. Works correctly on mobile breakpoint (< 768px).
6. **AC6 — User Profile Dropdown Shows All Roles:** The `UserMenuDropdown` displays all assigned roles (not just primary role). Example: "Organizer, Speaker" instead of just "Organizer".
7. **AC7 — AuthContext Multi-Role Fixes:** `hasRole()` checks `user.roles.includes(role)` instead of `user.role === role`. `canAccess()` aggregates allowed paths from ALL user roles. `hasPermission()` merges permissions from ALL user roles.

## Tasks / Subtasks

- [x] Task 1: Fix `AuthContext` multi-role methods (AC: 7)
  - [x] 1.1 Update `hasRole()` in `AuthContext.tsx` (line ~228): change `state.user?.role === role` to `state.user?.roles?.includes(role) ?? false`
  - [x] 1.2 Update `canAccess()` in `AuthContext.tsx` (line ~285): aggregate `pathAccess` entries for ALL `user.roles` instead of just `user.role`. Collect all allowed path prefixes from every role the user has, then check if current path matches any.
  - [x] 1.2a Add `/speaker-portal` to the `publicPaths` array in `canAccess()` — speaker-portal routes are public in App.tsx (no ProtectedRoute wrapper), but currently not listed in publicPaths. Without this, any code calling `canAccess('/speaker-portal/login')` returns false.
  - [x] 1.3 Update `hasPermission()` in `AuthContext.tsx` (line ~237): merge permission sets from ALL `user.roles`. If user is ORGANIZER+SPEAKER, they get union of both permission matrices.
  - [x] 1.4 Write unit tests for multi-role `hasRole()`, `canAccess()`, `hasPermission()` scenarios in `AuthContext.test.tsx`

- [x] Task 2: Update `ProtectedRoute` multi-role check (AC: 4)
  - [x] 2.1 Change `ProtectedRoute.tsx` line ~54: replace `allowedRoles.includes(user.role)` with `user.roles.some(r => allowedRoles.includes(r))` — checks if ANY of user's roles is in the allowed list
  - [x] 2.2 Write unit test: user with `roles: ['organizer', 'speaker']` can access `SpeakerRoute` (which allows `['organizer', 'speaker']`) AND `OrganizerRoute` (which allows `['organizer']`)
  - [x] 2.3 Write unit test: user with `roles: ['speaker']` CANNOT access route with `allowedRoles: ['organizer']`

- [x] Task 3: Update `navigationConfig.ts` for multi-role support (AC: 1, 2)
  - [x] 3.1 Add `getNavigationForRoles(roles: UserRole[]): NavigationItem[]` function that returns DEDUPLICATED nav items across all roles. Items that appear in multiple roles (e.g., "Public Site" in organizer and speaker) should appear only once.
  - [x] 3.2 Add "Speaker Portal" nav item to the speaker section: `{ labelKey: 'navigation.speakerPortal', path: '/speaker-portal/login', icon: RecordVoiceOver, roles: ['speaker'], description: 'Access speaker self-service portal' }`
  - [x] 3.3 Add `getRoleGroupLabel(role: UserRole): string` helper that returns a display-friendly section header label key (e.g., `'navigation.section.organizer'` → "Organizer", `'navigation.section.speaker'` → "Speaker")
  - [x] 3.4 Add `getGroupedNavigationForRoles(roles: UserRole[]): { role: UserRole, items: NavigationItem[] }[]` that returns navigation items grouped by role section (for rendering with section headers/dividers between role groups)
  - [x] 3.5 Unit test: `getNavigationForRoles(['organizer', 'speaker'])` returns combined items, deduplicates "Public Site"
  - [x] 3.6 Unit test: `getGroupedNavigationForRoles(['organizer', 'speaker'])` returns two groups with correct items

- [x] Task 4: Update `NavigationMenu` component for multi-role (AC: 1, 3)
  - [x] 4.1 Add `userRoles?: UserRole[]` prop to `NavigationMenu` (keep `userRole` for backward compat). When `userRoles` is provided, use `getGroupedNavigationForRoles(userRoles)` instead of `getNavigationForRole(userRole)`.
  - [x] 4.2 Render role groups with `Divider` + optional `Typography` section header between groups when user has 2+ roles. For single-role users, render flat list (no change from current behavior).
  - [x] 4.3 Active highlighting: existing `isPathActive()` already works per-item; no extra logic needed for multi-role.
  - [x] 4.4 Unit test: renders grouped items with section headers for multi-role user

- [x] Task 5: Update `AppHeader` to pass multi-role (AC: 1, 3)
  - [x] 5.1 In `AppHeader.tsx`, extract `userRoles` from user: `const userRoles = user && ('roles' in user ? (user as UserContext).roles : undefined)` — NOTE: `user` can be either `UserProfile` (from prop) or `UserContext` (from useAuth hook). Only `UserContext` has `roles[]`, so the `'roles' in user` check is essential. Do NOT simplify to `user.roles` without the type guard.
  - [x] 5.2 Pass `userRoles={userRoles}` to `NavigationMenu` alongside existing `userRole={currentRole}` for backward compatibility
  - [x] 5.3 Ensure single-role users see no difference (backward compatible)
  - [x] 5.4 Fix Tasks button (line ~177): change `currentRole === 'organizer'` to `userRoles?.includes('organizer') ?? currentRole === 'organizer'` — ensures the Tasks button shows for multi-role users who have organizer role regardless of primary role order

- [x] Task 6: Update `MobileDrawer` for multi-role (AC: 5)
  - [x] 6.1 Add optional `userRoles?: UserRole[]` prop to `MobileDrawer`
  - [x] 6.2 Pass `userRoles` through to `NavigationMenu` when provided
  - [x] 6.3 In `AppHeader.tsx`, pass `userRoles` to `MobileDrawer`
  - [x] 6.4 Unit test: MobileDrawer renders items for both roles when multi-role

- [x] Task 7: Update `UserMenuDropdown` to show all roles (AC: 6)
  - [x] 7.1 In `UserMenuDropdown.tsx` line ~123: replace `{t('role.${user.role}')}` with a multi-role display — map `user.roles` to translated labels, join with ", " (e.g., "Organizer, Speaker")
  - [x] 7.2 If user has only one role, display remains the same (backward compat)
  - [x] 7.3 Unit test: multi-role user sees "Organizer, Speaker" in dropdown

- [x] Task 8: Add i18n translation keys (AC: 1, 2, 6)
  - [x] 8.1 Add translation keys to `web-frontend/public/locales/en/common.json` and `web-frontend/public/locales/de/common.json` (NOT `src/locales/` — i18n files are in `public/locales/`). Add inside the existing `"navigation"` object: `"section": { "organizer": "Organizer", "speaker": "Speaker", "partner": "Partner", "attendee": "Attendee" }` and `"speakerPortal": "Speaker Portal"`
  - [x] 8.2 German translations in `de/common.json`: `"section": { "organizer": "Organisator", "speaker": "Referent", "partner": "Partner", "attendee": "Teilnehmer" }` and `"speakerPortal": "Speaker Portal"`
  - [x] 8.3 Verify existing `role.*` keys — actual values are: `role.organizer: "Event Organizer"`, `role.speaker: "Speaker"`, `role.partner: "Partner"`, `role.attendee: "Attendee"` (EN). The multi-role display in UserMenuDropdown will show e.g. "Event Organizer, Speaker" — this is correct and intentional.

- [x] Task 9: Comprehensive test suite (AC: 1-7)
  - [x] 9.1 Create `web-frontend/src/config/__tests__/navigationConfig.test.ts` — test `getNavigationForRoles()`, `getGroupedNavigationForRoles()`, deduplication
  - [x] 9.2 Update `web-frontend/src/components/shared/Navigation/NavigationMenu.test.tsx` — add multi-role rendering test (NOTE: navigation test files are ADJACENT to components, NOT in `__tests__/` subdirectory)
  - [x] 9.3 Update or create `web-frontend/src/components/auth/ProtectedRoute/ProtectedRoute.test.tsx` — add multi-role access tests (check if file exists first; if not, create adjacent to component)
  - [x] 9.4 Update `web-frontend/src/components/shared/Navigation/UserMenuDropdown.test.tsx` — add multi-role display test
  - [x] 9.5 Update `web-frontend/src/components/shared/Navigation/AppHeader.test.tsx` — add test for Tasks button visibility with multi-role user where primary role is NOT organizer
  - [x] 9.6 Run full frontend test suite: `cd web-frontend && npm test` — all tests must pass

## Dev Notes

### CRITICAL: Two Separate Auth Systems

BATbern has TWO independent authentication systems. Story 9.5 only modifies the **Cognito-authenticated** app navigation:

| Aspect | Cognito Auth (Main App) | Speaker Portal (Token Auth) |
|--------|-------------------------|----------------------------|
| **Auth method** | Email + password via AWS Cognito | Magic link JWT + session token |
| **Layout** | `BaseLayout` + `AppHeader` | `PublicLayout` (no header) |
| **Token storage** | AWS Amplify managed | `speaker_jwt` HTTP-only cookie + `?token=xxx` query params |
| **Routes** | `/dashboard`, `/organizer/*`, `/speaker/*` | `/speaker-portal/*` |
| **Navigation** | Role-adaptive `NavigationMenu` | None (standalone pages) |

**Story 9.5 changes ONLY the Cognito-authenticated navigation.** Speaker portal pages (`/speaker-portal/*`) remain unchanged — they continue using `PublicLayout` and token-based auth.

### CRITICAL: Speaker Portal Link Strategy

The "Speaker Portal" nav item links to `/speaker-portal/login` (NOT `/speaker-portal/dashboard`). Reason: the speaker dashboard requires a `?token=xxx` session token in the query param. A Cognito-authenticated user doesn't automatically have a speaker session token. By linking to the login page, the user can quickly authenticate with their password (they already have a Cognito account from Story 9.2) to obtain the session token and be redirected to the dashboard.

Future optimization (post-Epic 9): Add a backend endpoint that converts a Cognito JWT into a speaker session token, allowing direct navigation from the main app to the speaker dashboard without re-authentication.

### CRITICAL: Backward Compatibility

All changes MUST be backward compatible. Single-role users should see zero visual or functional differences:
- `NavigationMenu` accepts `userRoles?: UserRole[]` as optional — falls back to `userRole: UserRole` if not provided
- `MobileDrawer` accepts `userRoles?: UserRole[]` as optional
- `getNavigationForRole(role)` remains unchanged — `getNavigationForRoles(roles)` is a new function
- `ProtectedRoute` still works with single-role users (`.some()` works with 1-element arrays)

### Existing Role Extraction (Already Correct)

`authService.ts:344-373` already correctly parses multi-role tokens:

```typescript
// authService.ts — extractUserContextFromToken()
const rolesString = tokenPayload['custom:role']; // e.g., "ORGANIZER,SPEAKER"
const roles: UserRole[] = rolesString
  ? rolesString.split(',').map((r) => r.trim().toLowerCase() as UserRole)
  : [];
const primaryRole: UserRole = roles[0] || 'attendee';

return {
  role: primaryRole,      // 'organizer' — backward compat
  roles: roles,           // ['organizer', 'speaker'] — full set
  ...
};
```

**No changes to `authService.ts` or `UserContext` types needed.** The `roles[]` array is already populated correctly.

### Key Files to Modify

| File | Change | AC |
|------|--------|-----|
| `web-frontend/src/contexts/AuthContext.tsx` | Fix `hasRole()`, `canAccess()`, `hasPermission()` for multi-role | AC7 |
| `web-frontend/src/components/auth/ProtectedRoute/ProtectedRoute.tsx` | Check `user.roles[]` instead of `user.role` | AC4 |
| `web-frontend/src/config/navigationConfig.ts` | Add `getNavigationForRoles()`, `getGroupedNavigationForRoles()`, speaker portal item | AC1, AC2 |
| `web-frontend/src/components/shared/Navigation/NavigationMenu.tsx` | Accept `userRoles[]`, render grouped nav | AC1, AC3 |
| `web-frontend/src/components/shared/Navigation/AppHeader.tsx` | Pass `userRoles` to NavigationMenu and MobileDrawer | AC1 |
| `web-frontend/src/components/shared/Navigation/MobileDrawer.tsx` | Accept `userRoles[]`, pass to NavigationMenu | AC5 |
| `web-frontend/src/components/shared/Navigation/UserMenuDropdown.tsx` | Show all roles in dropdown | AC6 |
| `web-frontend/src/locales/` (i18n files) | Add section header + speaker portal translation keys | AC1, AC2 |

### File-by-File Implementation Guide

**1. `AuthContext.tsx` — Fix multi-role methods**

```typescript
// hasRole — BEFORE (broken for multi-role):
const hasRole = useCallback(
  (role: UserRole): boolean => {
    return state.user?.role === role;
  }, [state.user]
);

// hasRole — AFTER (checks all roles):
const hasRole = useCallback(
  (role: UserRole): boolean => {
    return state.user?.roles?.includes(role) ?? false;
  }, [state.user]
);
```

```typescript
// canAccess — AFTER (aggregate all role paths):
const canAccess = useCallback(
  (path: string): boolean => {
    // ... public paths check unchanged ...
    if (!state.user) return false;
    const { roles } = state.user;
    const pathAccess: Record<UserRole, string[]> = { /* same as current */ };
    // Aggregate allowed paths from ALL roles
    const allowedPaths = roles.flatMap(role => pathAccess[role] || []);
    return allowedPaths.some((allowedPath) => path.startsWith(allowedPath));
  }, [state.isAuthenticated, state.user]
);
```

```typescript
// hasPermission — AFTER (merge permissions from all roles):
const hasPermission = useCallback(
  (resource: string, action: string): boolean => {
    if (!state.user) return false;
    const { roles } = state.user;
    const permissions: Record<UserRole, Record<string, string[]>> = { /* same as current */ };
    // Merge permissions from all roles
    return roles.some(role => {
      const rolePerms = permissions[role];
      return rolePerms?.[resource]?.includes(action) ?? false;
    });
  }, [state.user]
);
```

**2. `ProtectedRoute.tsx` — Multi-role check**

```typescript
// BEFORE (line ~54):
if (!allowedRoles.includes(user.role)) {

// AFTER:
if (!user.roles.some(r => allowedRoles.includes(r))) {
```

**3. `navigationConfig.ts` — New functions**

```typescript
import { RecordVoiceOver } from '@mui/icons-material'; // For speaker portal icon

// Add Speaker Portal item to speaker section:
{
  labelKey: 'navigation.speakerPortal',
  path: '/speaker-portal/login',
  icon: RecordVoiceOver,
  roles: ['speaker'],
  description: 'Access speaker self-service portal',
},

// New function — deduplicated items for all roles:
export function getNavigationForRoles(roles: UserRole[]): NavigationItem[] {
  const seen = new Set<string>();
  return navigationConfig
    .filter(item => item.roles.some(r => roles.includes(r)))
    .filter(item => {
      if (seen.has(item.path)) return false;
      seen.add(item.path);
      return true;
    });
}

// New function — grouped by role:
export function getGroupedNavigationForRoles(
  roles: UserRole[]
): { role: UserRole; labelKey: string; items: NavigationItem[] }[] {
  return roles.map(role => ({
    role,
    labelKey: `navigation.section.${role}`,
    items: getNavigationForRole(role),
  }));
}
```

**4. `NavigationMenu.tsx` — Grouped rendering**

```typescript
interface NavigationMenuProps {
  userRole: UserRole;
  userRoles?: UserRole[];  // NEW: optional multi-role
  onItemClick?: () => void;
  showText?: boolean;
  variant?: 'horizontal' | 'vertical';
}

// When userRoles has 2+ entries, render grouped navigation:
// - For each role group, render an optional section header (Typography)
// - Render nav items for that group
// - Render a Divider between groups
// For single-role users or when userRoles not provided, render flat (current behavior)
```

**5. `UserMenuDropdown.tsx` — All roles display**

```typescript
// BEFORE (line ~123):
<Typography variant="caption" color="text.secondary">
  {t(`role.${user.role}`)}
</Typography>

// AFTER:
<Typography variant="caption" color="text.secondary">
  {user.roles.map(r => t(`role.${r}`)).join(', ')}
</Typography>
```

### MUI Icon for Speaker Portal

Use `RecordVoiceOver` from `@mui/icons-material` — confirmed available in this project's MUI version (already imported as `SpeakerIcon` in `web-frontend/src/components/shared/Company/CompanyStatistics.tsx`). It's a microphone icon with a person, fitting for "Speaker Portal".

### IMPORTANT: Dead Speaker Nav Links (Pre-existing)

The speaker section of `navigationConfig.ts` contains nav items for `/speaker/dashboard`, `/speaker/events`, `/speaker/content`, `/speaker/profile` — but these routes have NO corresponding `<Route>` entries in `App.tsx`. Only `/speakers` and `/speaker/company/*` are actually routed. These are dead links from the original Story 1.17 navigation scaffolding (placeholder for future Cognito-authenticated speaker features). Do NOT try to fix these dead links in this story — they are a pre-existing issue. The new "Speaker Portal" nav item (`/speaker-portal/login`) correctly links to the actual working speaker login page.

### Translation Keys Needed

i18n files are at `web-frontend/public/locales/{lang}/common.json` (NOT `src/locales/`).

**Add to EN** (`public/locales/en/common.json`) inside existing `"navigation"` object:
```json
"speakerPortal": "Speaker Portal",
"section": {
  "organizer": "Organizer",
  "speaker": "Speaker",
  "partner": "Partner",
  "attendee": "Attendee"
}
```

**Add to DE** (`public/locales/de/common.json`) inside existing `"navigation"` object:
```json
"speakerPortal": "Speaker Portal",
"section": {
  "organizer": "Organisator",
  "speaker": "Referent",
  "partner": "Partner",
  "attendee": "Teilnehmer"
}
```

**Existing role keys** (already correct, no changes needed):
```json
// EN: "role": { "organizer": "Event Organizer", "speaker": "Speaker", "partner": "Partner", "attendee": "Attendee" }
// DE: "role": { "organizer": "Veranstalter", "speaker": "Referent", "partner": "Partner", "attendee": "Teilnehmer" }
```
Multi-role UserMenuDropdown will display e.g. "Event Organizer, Speaker" — correct and intentional.

### Testing Standards

**Frontend tests use Vitest + React Testing Library** (never Enzyme):

```typescript
// Pattern: test('should render items for all roles when user has multiple roles')
// Use renderWithProviders() helper that wraps in AuthContext + Router

// Test multi-role AuthContext:
const mockUser: UserContext = {
  userId: 'test-123',
  username: 'john.doe',
  email: 'john@example.com',
  emailVerified: true,
  role: 'organizer',
  roles: ['organizer', 'speaker'],
  preferences: { language: 'en', theme: 'light', notifications: { email: true, sms: false, push: true }, privacy: { showProfile: true, allowMessages: true } },
  issuedAt: Math.floor(Date.now() / 1000),
  expiresAt: Math.floor(Date.now() / 1000) + 3600,
  tokenId: 'test-token',
};
```

### Do NOT Change in This Story

- `authService.ts` — Token extraction already correct
- `UserContext` / `auth.ts` types — Already has `roles: UserRole[]`
- Speaker portal pages (`/speaker-portal/*`) — Separate auth system, unchanged
- `PublicNavigation.tsx` — Public layout, unrelated to Cognito auth
- `PublicLayout.tsx` — Speaker portal layout, unchanged
- Backend services — No backend changes needed
- `speakerAuthService.ts` — Speaker portal API client, unchanged
- `SpeakerMagicLoginPage.tsx`, `SpeakerLoginPage.tsx`, `SpeakerDashboardPage.tsx` — Unchanged

### Previous Story Intelligence (Stories 9.1-9.4)

**From Story 9.1 (done):**
- JWT magic link flow works end-to-end
- `speaker_jwt` HTTP-only cookie set by backend
- Session bridge pattern: JWT → opaque VIEW token → dashboard
- Frontend stores are NOT touched by speaker portal auth

**From Story 9.2 (done):**
- Cognito accounts auto-created on invitation acceptance
- `custom:role=SPEAKER` set in Cognito (singular `custom:role`, NOT `custom:roles`)
- Existing attendee accounts get SPEAKER role added (role extension)
- `UserContext.roles[]` already populated correctly by `authService.ts`

**From Story 9.3 (done):**
- `SpeakerLoginPage.tsx` exists at `/speaker-portal/login` with dual auth
- Password login produces same session as magic link
- SecurityConfig dual-update pattern applied for all speaker auth endpoints
- German language for speaker portal pages ("Mit Passwort anmelden", "Magischen Link senden")

**From Story 9.4 (ready-for-dev):**
- Migration script design — not yet implemented
- Will migrate Epic 6 staging users to Cognito accounts

### Git Intelligence

Recent commits show Story 9.1-9.2 are committed (`feat(auth): implement Story 9.1`, `feat(epic-9): create Story 9.2`). Story 9.3 changes are in working tree (uncommitted). The branch is `feature/speaker-account-creation`.

### Project Structure Notes

- All changes are in `web-frontend/src/` only (pure frontend story)
- No new pages created — only modifications to existing navigation/auth components
- No backend changes, no Flyway migrations, no SecurityConfig changes
- Follows existing patterns: MUI components, React.memo, React Testing Library, i18n
- Component file naming: PascalCase (`NavigationMenu.tsx`)
- Config file naming: camelCase (`navigationConfig.ts`)
- Test file location: `__tests__/` directory adjacent to component

### References

- [Source: docs/prd/epic-9-speaker-authentication.md#Story-9.5] — Acceptance criteria source
- [Source: web-frontend/src/contexts/AuthContext.tsx] — hasRole(), canAccess(), hasPermission() (lines 228-330)
- [Source: web-frontend/src/components/auth/ProtectedRoute/ProtectedRoute.tsx] — allowedRoles check (line 54)
- [Source: web-frontend/src/config/navigationConfig.ts] — getNavigationForRole(), NavigationItem type
- [Source: web-frontend/src/components/shared/Navigation/NavigationMenu.tsx] — Current single-role rendering
- [Source: web-frontend/src/components/shared/Navigation/AppHeader.tsx] — currentRole extraction (line 53)
- [Source: web-frontend/src/components/shared/Navigation/MobileDrawer.tsx] — userRole prop
- [Source: web-frontend/src/components/shared/Navigation/UserMenuDropdown.tsx] — Single role display (line 123)
- [Source: web-frontend/src/services/auth/authService.ts] — extractUserContextFromToken() multi-role parsing (lines 344-373)
- [Source: web-frontend/src/types/auth.ts] — UserContext.roles: UserRole[] (line 29)
- [Source: _bmad-output/implementation-artifacts/9-1-jwt-magic-link-authentication.md] — Session bridge pattern
- [Source: _bmad-output/implementation-artifacts/9-3-dual-authentication-support.md] — SpeakerLoginPage exists, dual auth pattern
- [Source: CLAUDE.md] — TDD mandatory, Vitest + React Testing Library, frontend test patterns

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (create-story workflow + dev-story workflow)

### Debug Log References

- Test run 1: 3 files failed (AuthContext.test.tsx module resolution, useAuth.test.tsx missing roles[], PublishingTimeline pre-existing)
- Fix: Changed AuthContext.test.tsx from `require()` to `import` pattern; added `roles[]` to all useAuth.test.tsx mock users
- Test run 2: 1 pre-existing failure only (PublishingTimeline date format) — all Story 9.5 tests pass

### Completion Notes List

- Story created via create-story workflow on 2026-02-17
- Story 9.5 is the LAST implementation story in Epic 9 (Story 9.4 is migration, run after all features)
- Pure frontend story — no backend changes, no DB migrations, no SecurityConfig changes
- Key insight: two separate auth systems (Cognito main app vs token-based speaker portal) — this story only touches Cognito navigation
- Speaker portal link goes to /speaker-portal/login (not /dashboard) because dashboard requires ?token= query param
- All multi-role functions must be backward compatible with single-role users
- Implementation completed: 2026-02-17, all 9 tasks done, 3609 tests passing (1 pre-existing failure unrelated)
- AuthContext.test.tsx uses `renderHook` with `useAuth` + `AuthProvider` wrapper (same pattern as useAuth.test.tsx) — avoids `require()` path resolution issues with `@services` alias

### Change Log

- 2026-02-17: All 9 tasks implemented and tested (dev-story workflow, Claude Opus 4.6)

### File List

**Modified:**
- `web-frontend/src/contexts/AuthContext.tsx` — Fixed `hasRole()`, `canAccess()`, `hasPermission()` for multi-role (AC7)
- `web-frontend/src/components/auth/ProtectedRoute/ProtectedRoute.tsx` — `user.roles.some()` instead of `user.role` (AC4)
- `web-frontend/src/config/navigationConfig.ts` — Added `getNavigationForRoles()`, `getGroupedNavigationForRoles()`, Speaker Portal item (AC1, AC2)
- `web-frontend/src/components/shared/Navigation/NavigationMenu.tsx` — `userRoles` prop, grouped rendering with section headers (AC1, AC3)
- `web-frontend/src/components/shared/Navigation/AppHeader.tsx` — Extract `userRoles`, pass to NavigationMenu/MobileDrawer, fix Tasks button (AC1)
- `web-frontend/src/components/shared/Navigation/MobileDrawer.tsx` — `userRoles` prop passthrough (AC5)
- `web-frontend/src/components/shared/Navigation/UserMenuDropdown.tsx` — Show all roles comma-separated (AC6)
- `web-frontend/public/locales/en/common.json` — Added `navigation.speakerPortal`, `navigation.section.*` keys
- `web-frontend/public/locales/de/common.json` — German translations for above keys

**Modified (test files):**
- `web-frontend/src/hooks/useAuth/useAuth.test.tsx` — Added `roles[]` to all mock user objects (9 mocks updated)
- `web-frontend/src/components/auth/ProtectedRoute/ProtectedRoute.test.tsx` — Added 3 multi-role tests + `roles[]` on 15 existing mocks
- `web-frontend/src/components/shared/Navigation/NavigationMenu.test.tsx` — Added 5 multi-role tests + extended i18n mock
- `web-frontend/src/components/shared/Navigation/UserMenuDropdown.test.tsx` — Added 2 multi-role display tests
- `web-frontend/src/components/shared/Navigation/AppHeader.test.tsx` — Added 2 Tasks button multi-role tests

**Created:**
- `web-frontend/src/contexts/AuthContext.test.tsx` — 13 tests: hasRole, canAccess, hasPermission with multi-role users
- `web-frontend/src/config/__tests__/navigationConfig.test.ts` — 10 tests: getNavigationForRoles, getGroupedNavigationForRoles, Speaker Portal item
