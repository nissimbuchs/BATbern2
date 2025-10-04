# Main Navigation Bar - Role-Adaptive Global Navigation

<!-- Powered by BMAD™ Core -->

## Header Information

**Story:** Epic 1, Story 1.17 - React Frontend Foundation with Role-Adaptive Architecture

**Screen:** Main Navigation Bar / Global Navigation Menu

**User Role:** All Authenticated Users (Organizer, Speaker, Partner, Attendee)

**Related FR:** FR1 (Multi-Role User Management), Story 1.17 (React Frontend Foundation), Story 1.20 (User Role Management)

## Visual Wireframe

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ [🎪 BATbern Logo]                                          [🔔3] [@▼] [?]  │
├──────┬──────────┬──────────┬──────────┬──────────┬──────────────────────────┤
│ Item1│  Item2   │  Item3   │  Item4   │  Item5   │                          │
└──────┴──────────┴──────────┴──────────┴──────────┴──────────────────────────┘

┌─── ORGANIZER ROLE (Sample Menu Items) ──────────────────────────────────────┐
│ [BATbern] Events │ Topics │ Speakers │ Partners │ Analytics         [@▼][?]│
└──────────────────────────────────────────────────────────────────────────────┘

┌─── SPEAKER ROLE (Sample Menu Items) ────────────────────────────────────────┐
│ [BATbern] Dashboard │ Materials │ Profile │ Community          [@▼][?]     │
└──────────────────────────────────────────────────────────────────────────────┘

┌─── PARTNER ROLE (Sample Menu Items) ────────────────────────────────────────┐
│ [BATbern] Analytics │ Voting │ Meetings │ Settings              [@▼][?]   │
└──────────────────────────────────────────────────────────────────────────────┘

┌─── ATTENDEE ROLE (Sample Menu Items) ───────────────────────────────────────┐
│ [BATbern] Events │ Content │ Learning │ Profile                  [@▼][?]  │
└──────────────────────────────────────────────────────────────────────────────┘

┌─── USER MENU DROPDOWN (@▼) ─────────────────────────────────────────────────┐
│  👤 John Doe (john.doe@example.com)                                         │
│  🎯 Role: Organizer                                                         │
│  ───────────────────────────────────────────────────────────────────────────│
│  ⚙️  Profile & Settings                                                     │
│  🔔  Notifications                                                          │
│  ❓  Help Center                                                            │
│  🌐  Language: Deutsch ▼                                                    │
│  ───────────────────────────────────────────────────────────────────────────│
│  🚪  Logout                                                                 │
└──────────────────────────────────────────────────────────────────────────────┘

┌─── NOTIFICATION BELL DROPDOWN (🔔) ──────────────────────────────────────────┐
│  Notifications (3 unread)                              [Mark all as read]   │
│  ───────────────────────────────────────────────────────────────────────────│
│  ● Event "BATbern 2025" status changed to "Published"                      │
│    2 hours ago                                                  [View][×]   │
│  ───────────────────────────────────────────────────────────────────────────│
│  ● New speaker invitation response from Jane Smith                         │
│    Yesterday                                                    [View][×]   │
│  ───────────────────────────────────────────────────────────────────────────│
│  ○ Topic "AI & Machine Learning" assigned to Event                         │
│    2 days ago                                                   [View][×]   │
│  ───────────────────────────────────────────────────────────────────────────│
│  [↻ Reload]                                          [View All Notifications]│
└──────────────────────────────────────────────────────────────────────────────┘

┌─── MOBILE NAVIGATION (Hamburger Menu ☰) ────────────────────────────────────┐
│  ☰                  BATbern                         [🔔3] [@]              │
│  ───────────────────────────────────────────────────────────────────────────│
│  When hamburger clicked, full-screen overlay menu:                          │
│                                                                              │
│  🏠  Dashboard                                                              │
│  📅  Events                                                                 │
│  🎤  Speakers                                                               │
│  🤝  Partners                                                               │
│  📊  Analytics                                                              │
│  ───────────────────────────────────────────────────────────────────────────│
│  ⚙️  Settings                                                               │
│  ❓  Help                                                                   │
│  🚪  Logout                                                                 │
└──────────────────────────────────────────────────────────────────────────────┘

┌─── BREADCRUMBS (Below Navigation Bar) ──────────────────────────────────────┐
│  Home > Events > Event Detail > Edit                                        │
└──────────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Logo Button**: Click to navigate to role-specific dashboard (home screen for current role)
- **Main Menu Items**: Horizontal tabs that navigate to role-specific portal sections (highlighted on current section)
- **Notification Bell (🔔)**: Click to open notification dropdown with unread count badge (manual reload to update)
- **Reload Button (↻)**: Manual reload button in notification dropdown to fetch latest notifications
- **User Avatar Dropdown (@▼)**: Click to open user menu with profile, settings, help, language switcher, logout
- **Help Icon (?)**: Quick access to context-sensitive help and documentation
- **Language Switcher**: Dropdown in user menu to toggle between German (Deutsch) and English
- **Hamburger Menu (☰)**: Mobile-only responsive navigation toggle (appears on screens < 768px)
- **Breadcrumb Trail**: Contextual navigation showing current location in app hierarchy (clickable links)
- **Role Badge**: Visual indicator showing current user role (changes color per role: Organizer=blue, Speaker=green, Partner=purple, Attendee=orange)

## Functional Requirements Met

- **FR1 (Multi-Role User Management)**: Navigation adapts dynamically based on authenticated user's role from JWT token
- **Story 1.17 (React Frontend Foundation)**: Implements role-adaptive component architecture with shared navigation component
- **Story 1.20 (User Role Management)**: Displays current role and provides access to profile/settings for role management
- **Story 1.2 (Authentication)**: Integrates AWS Cognito authentication state and provides logout functionality
- **Frontend Architecture (05-frontend-architecture.md)**: Implements `BaseLayout` and `NavigationItem` component patterns with role-based menu configuration

## Technical Notes

### Component Architecture
- **Component**: `<AppHeader>` (React functional component with TypeScript)
- **State Management**:
  - Zustand store for UI state (sidebar open, notifications)
  - React Query for user profile and notification data
  - Auth context from Cognito (user, role, permissions)
- **Navigation Config**: Static configuration in frontend code: `navigationConfig: Record<UserRole, NavigationItem[]>` (see frontend architecture)
- **Responsive Design**: Mobile-first with breakpoints at 768px (tablet) and 1024px (desktop)
- **Notification Updates**: Manual reload only (no WebSocket or polling)

### Material-UI Components
- `AppBar` with `position="fixed"` and `elevation={1}`
- `Toolbar` for horizontal layout
- `IconButton` for notification bell and user avatar
- `Menu` and `MenuItem` for dropdowns
- `Badge` for notification count
- `Drawer` for mobile hamburger menu
- `Breadcrumbs` component for navigation trail

### Navigation Item Structure
```typescript
interface NavigationItem {
  id: string;
  label: string;
  icon: React.ComponentType;
  path: string;
  roles: UserRole[];
  children?: NavigationItem[]; // For nested menus
}
```

### Search Functionality
- **Not included in MVP** - Deferred to Epic 5 (Attendee Experience - Content Discovery)
- Search functionality will be implemented per-screen where needed (e.g., speaker search in Speaker Matching Interface)

### Accessibility
- Semantic HTML5 `<nav>` element with proper ARIA labels
- Keyboard navigation with Tab/Shift+Tab (focus trap in dropdown menus)
- Screen reader announcements for notification updates
- Focus indicators meet WCAG 2.1 AA contrast requirements (3:1 minimum)
- Skip to main content link for screen readers

### Internationalization (i18n)

Per frontend architecture requirements (docs/architecture/05-frontend-architecture.md):

**Supported Languages:**
- **Primary**: German (de-CH) - Swiss German locale (default/fallback)
- **Secondary**: English (en-US)

**Implementation:**
- **Library**: react-i18next with i18next-browser-languagedetector
- **Translation Namespaces**: Navigation uses `common` namespace for menu items, buttons, labels
- **Language Detection Priority**:
  1. User Profile (from `/api/v1/users/me` → `preferredLanguage` field)
  2. LocalStorage (`batbern-language` key)
  3. Browser Language (`navigator.language`)
  4. Default Fallback: German (`de`)

**Translated Elements:**
- Menu item labels (Events, Topics, Speakers, etc.)
- User menu items (Profile & Settings, Notifications, Help Center, Logout)
- Notification dropdown text ("Notifications", "Mark all as read", "View All Notifications")
- Button labels (Reload, View, etc.)
- Role labels ("Role: Organizer" → "Rolle: Organisator")
- Error messages and loading states

**Language Switcher Behavior:**
1. User clicks language dropdown in user menu
2. Selects new language (Deutsch/English)
3. API call to `PUT /api/v1/users/me/language` persists preference
4. `i18next.changeLanguage(newLang)` updates UI immediately
5. `document.documentElement.lang` attribute updated for accessibility
6. LocalStorage updated as backup
7. All navigation text re-renders with new translations
8. ARIA announcement: "Language changed to {language}" (screen reader)

**Date/Time Formatting:**
- Notification timestamps use `date-fns` with locale support
- German: "vor 2 Stunden" (2 hours ago)
- English: "2 hours ago"

**Accessibility:**
- `<html lang="de">` or `<html lang="en">` attribute updated on change
- Screen reader announcement via `aria-live="polite"` region
- Language switcher has `aria-label="Change language"` in current language

### Performance Optimization
- Lazy loading for dropdown menu components
- React.memo for navigation items to prevent unnecessary re-renders
- Simple pagination for notifications (load 10 at a time with "Load More" button)
- i18n namespaces: Load `common` namespace immediately, lazy-load role-specific namespaces on first use

## API Requirements

### Initial Page Load APIs

1. **GET /api/v1/users/me**
   - Triggered on: App initialization after authentication
   - Query params: None (user ID from JWT token)
   - Returns:
     ```json
     {
       "userId": "uuid",
       "email": "john.doe@example.com",
       "firstName": "John",
       "lastName": "Doe",
       "currentRole": "ORGANIZER",
       "availableRoles": ["ORGANIZER", "SPEAKER"],
       "companyId": "uuid",
       "preferredLanguage": "de",
       "profilePhotoUrl": "https://cdn.example.com/...",
       "notificationPreferences": { ... }
     }
     ```
   - Used for: Populating user profile in navigation bar, determining role-based menu items
   - Cache: React Query cache for 5 minutes, invalidate on user profile update

2. **GET /api/v1/notifications?limit=10&offset=0**
   - Triggered on: App initialization, manual reload in notification dropdown
   - Query params:
     - `limit=10` (show 10 notifications per page in dropdown)
     - `offset=0` (pagination offset, increments by 10 for "Load More")
   - Returns:
     ```json
     {
       "notifications": [
         {
           "id": "uuid",
           "type": "EVENT_STATUS_CHANGED",
           "title": "Event status changed",
           "message": "Event 'BATbern 2025' is now Published",
           "timestamp": "2024-03-15T14:30:00Z",
           "isRead": false,
           "actionUrl": "/events/uuid",
           "priority": "NORMAL"
         }
       ],
       "unreadCount": 3,
       "totalCount": 47,
       "hasMore": true
     }
     ```
   - Used for: Notification bell badge count, notification dropdown list
   - Cache: React Query with 5-minute stale time, manual refresh only

3. **Navigation Menu Items**
   - **Static configuration in frontend** (no API call)
   - Menu items defined in TypeScript config file: `src/config/navigationConfig.ts`
   - Role-based rendering using current user's role from JWT token
   - Example structure:
     ```typescript
     const navigationConfig: Record<UserRole, NavigationItem[]> = {
       ORGANIZER: [
         { id: 'events', label: 'Events', path: '/organizer/events', icon: EventIcon },
         { id: 'topics', label: 'Topics', path: '/organizer/topics', icon: TopicIcon },
         { id: 'speakers', label: 'Speakers', path: '/organizer/speakers', icon: SpeakerIcon }
       ],
       // ... other roles
     };
     ```
   - No API call needed, instant rendering based on auth state

### User Action APIs

1. **POST /api/v1/notifications/{id}/mark-read**
   - Triggered by: User clicks notification item or "Mark as read" button
   - Payload: None (notification ID in URL)
   - Response:
     ```json
     { "success": true, "updatedAt": "2024-03-15T14:35:00Z" }
     ```
   - Used for: Updating notification read status, decrementing badge count
   - Optimistic update: Immediately update UI, rollback on error
   - Cache invalidation: Invalidate notifications query cache

2. **POST /api/v1/notifications/mark-all-read**
   - Triggered by: User clicks "Mark all as read" button in notification dropdown
   - Payload: None
   - Response:
     ```json
     { "success": true, "markedCount": 3 }
     ```
   - Used for: Bulk marking notifications as read, clearing badge count
   - Optimistic update: Set all notifications to read, badge to 0
   - Cache invalidation: Invalidate notifications query cache

3. **DELETE /api/v1/notifications/{id}**
   - Triggered by: User clicks [×] button on notification item
   - Payload: None (notification ID in URL)
   - Response:
     ```json
     { "success": true }
     ```
   - Used for: Removing notification from list
   - Optimistic update: Remove from UI immediately
   - Cache invalidation: Invalidate notifications query cache

4. **POST /api/v1/auth/logout**
   - Triggered by: User clicks "Logout" in user dropdown menu
   - Payload:
     ```json
     { "refreshToken": "..." }
     ```
   - Response:
     ```json
     { "success": true }
     ```
   - Used for: Invalidating refresh token, clearing session
   - Side effects:
     - Clear all React Query cache
     - Clear Zustand state stores
     - Revoke Cognito tokens
     - Redirect to `/auth/login`

5. **PUT /api/v1/users/me/language**
   - Triggered by: User selects language from dropdown in user menu
   - Payload:
     ```json
     { "language": "de" }
     ```
   - Response:
     ```json
     { "success": true, "language": "de" }
     ```
   - Used for: Updating user's preferred language setting
   - Side effects:
     - Update i18n language in browser (i18next.changeLanguage)
     - Update document.documentElement.lang attribute
     - Persist to localStorage
     - Invalidate user profile cache

6. **GET /api/v1/notifications (Manual Reload)**
   - Triggered by: User clicks reload button (↻) in notification dropdown
   - Query params: Same as initial load (`limit=10`, `offset=0`)
   - Returns: Same notification structure as initial load API
   - Used for: Manually refreshing notification list and badge count
   - Cache: Invalidates existing React Query cache, fetches fresh data

## Navigation Map

### Primary Navigation Actions

1. **Logo Click** → Navigate to role-specific dashboard
   - Target:
     - Organizer → `/organizer/dashboard` (Event Management Dashboard)
     - Speaker → `/speaker/dashboard` (Speaker Dashboard)
     - Partner → `/partner/dashboard` (Partner Analytics Dashboard)
     - Attendee → `/attendee/dashboard` (Personal Attendee Dashboard)
   - Navigation type: Full page navigation
   - Context: None (always goes to home)

2. **Menu Item Click** → Navigate to role-specific portal section
   - Target: Varies by role and menu item (e.g., `/organizer/events`, `/speaker/materials`)
   - Navigation type: Full page navigation with active menu highlighting
   - Context: None
   - Validation: Role-based routing guard ensures user has permission

3. **Notification Bell Click** → Open notification dropdown
   - Target: Inline dropdown menu (not navigation)
   - Navigation type: Modal overlay
   - Context: Fetches latest notifications
   - Side effect: Mark dropdown as opened (for tracking)

4. **User Avatar Click** → Open user menu dropdown
   - Target: Inline dropdown menu (not navigation)
   - Navigation type: Modal overlay
   - Context: Current user profile data

5. **Logout Click** → Logout and redirect to login
   - Target: `/auth/login`
   - Navigation type: Full page redirect (clear auth state)
   - Context: None
   - Validation: Revoke tokens, clear cache, clear local storage
   - Side effect: API call to invalidate refresh token

6. **Help Icon Click** → Navigate to Help Center
   - Target: `/help` (Help Center Screen)
   - Navigation type: Full page navigation or sidebar overlay (TBD)
   - Context: Current page context for context-sensitive help

### Secondary Navigation (Data Interactions)

1. **Notification Item Click** → Navigate to related content
   - Target: `notification.actionUrl` (e.g., `/events/{id}`, `/speakers/{id}`)
   - Navigation type: Full page navigation
   - Context: Pass notification context if available
   - Side effect: Mark notification as read (API call)

2. **Breadcrumb Link Click** → Navigate to parent page
   - Target: URL from breadcrumb path (e.g., `/events`, `/events/{id}`)
   - Navigation type: Full page navigation
   - Context: Preserve navigation history

3. **Language Switcher Change** → Update language and reload
   - Target: Current page (no navigation)
   - Navigation type: None (in-place update)
   - Context: New language code
   - Side effect: API call to update user preference, i18n language change

### Event-Driven Navigation

1. **Session Expired (401 Unauthorized)** → Redirect to login
   - Target: `/auth/login?returnUrl={currentPath}`
   - Trigger: API response 401 status
   - Navigation type: Full page redirect
   - Context: Save current path for post-login redirect
   - Side effect: Clear auth state, show "Session expired" message

2. **Page Refresh** → Reload all data
   - Target: Current page (refresh)
   - Trigger: Browser refresh or F5 key
   - Side effect:
     - Clear all React Query cache
     - Re-fetch user profile
     - Re-fetch notifications
     - Re-initialize navigation menu based on current role
   - Validation: Authentication token still valid

### Error States & Redirects

1. **Unauthorized Route Access (403 Forbidden)** → Redirect to error page
   - Target: `/error/403` or redirect to appropriate role dashboard
   - Trigger: API response 403 status or route guard
   - Navigation type: Full page redirect
   - Context: Error message and return URL
   - Side effect: Show "Access denied" toast notification

2. **Network Error** → Show error banner (no navigation)
   - Target: None (stay on current page)
   - Trigger: API timeout or network failure
   - Side effect: Display persistent error banner at top of page with retry button
   - Validation: Retry API calls on user action

3. **User Not Found (404 in /users/me)** → Force logout
   - Target: `/auth/login`
   - Trigger: User profile API returns 404 (user deleted)
   - Navigation type: Full page redirect
   - Side effect: Clear auth state, show "Account not found" message

## Responsive Design Considerations

### Mobile Layout Changes (< 768px)

- **Hamburger Menu**: Replace horizontal menu items with hamburger icon (☰)
  - Opens full-screen overlay menu with vertical navigation list
  - Slide-in animation from left (300ms transition)
  - Close with hamburger icon, overlay click, or swipe-right gesture
- **Notification/User Icons**: Remain visible in top-right
- **Logo**: Shrink to icon-only version (square logo) on very small screens (< 480px)
- **Breadcrumbs**: Hide on mobile (< 768px), rely on hamburger menu and page title

### Tablet Layout Changes (768px - 1024px)

- **Menu Items**: Full horizontal menu with abbreviated labels if needed
- **Notification Dropdown**: Full-width dropdown instead of fixed width
- **User Menu**: Shift to left edge of dropdown to avoid viewport overflow

### Mobile-Specific Interactions

- **Swipe Right on Hamburger Menu**: Close mobile navigation menu
- **Tap Outside Dropdown**: Close dropdown menu (notification or user menu)
- **Long Press on Menu Item**: Show tooltip or submenu (if nested navigation)
- **Pull-to-Refresh**: Refresh current page data (optional, native browser behavior)

## Accessibility Notes

- **Semantic HTML**: `<nav>` element with `aria-label="Main navigation"` for primary navigation
- **Keyboard Navigation**:
  - Tab key moves focus through: Logo → Search → Menu Items → Notification → User Avatar → Help
  - Arrow keys navigate within dropdown menus
  - Enter/Space activates focused item
  - Escape closes open dropdowns
  - Shift+Tab reverses focus direction
- **Screen Reader Support**:
  - `aria-label` on all icon-only buttons (notification bell, user avatar, help)
  - `aria-live="polite"` region for notification count updates
  - `aria-expanded` attribute on dropdowns (true/false)
  - `aria-current="page"` on active menu item
  - `role="menu"` and `role="menuitem"` for dropdown menus
- **Focus Indicators**:
  - Visible focus outline on all interactive elements (2px solid blue)
  - Minimum 3:1 contrast ratio against background (WCAG 2.1 AA)
  - Focus trap in open dropdown menus (Tab cycles within menu)
- **Color Contrast**:
  - Text meets WCAG 2.1 AA standard (4.5:1 for normal text, 3:1 for large text)
  - Role badges use distinct colors with sufficient contrast
  - Notification badge uses red with white text (high contrast)
- **ARIA Announcements**:
  - New notifications announced via `aria-live="assertive"` (if critical) or `"polite"` (if informational)
  - Role change announced: "Your role has been updated to Speaker"
  - Logout announced: "You have been logged out"

## State Management

### Local Component State

- `mobileMenuOpen: boolean` - Controls hamburger menu overlay visibility
- `notificationDropdownOpen: boolean` - Controls notification dropdown visibility
- `userMenuDropdownOpen: boolean` - Controls user menu dropdown visibility
- `searchQuery: string` - Current search input value
- `searchFocused: boolean` - Search bar focus state (for animation)
- `breadcrumbPath: string[]` - Computed from current route

### Global State (Zustand)

- **Auth Store** (`useAuthStore`):
  - `user: User | null` - Current authenticated user profile
  - `currentRole: UserRole` - Active role (ORGANIZER, SPEAKER, PARTNER, ATTENDEE)
  - `availableRoles: UserRole[]` - Roles user can switch between
  - `isAuthenticated: boolean` - Authentication status
  - `logout: () => Promise<void>` - Logout action

- **UI Store** (`useUIStore`):
  - `sidebarOpen: boolean` - Global sidebar visibility (for desktop)
  - `locale: 'de' | 'en'` - Current i18n language
  - `setLocale: (locale) => void` - Language switcher action

### Server State (React Query)

- **User Profile Query** (`useUserProfile`):
  - Key: `['user', 'me']`
  - Stale time: 5 minutes
  - Cache time: 10 minutes
  - Refetch on window focus: true

- **Notifications Query** (`useNotifications`):
  - Key: `['notifications', { limit: 10, offset: 0 }]`
  - Stale time: 5 minutes
  - Refetch interval: Manual only (no automatic polling)
  - Cache time: 10 minutes

### Manual Updates Only

- **No real-time updates** - All data refreshes are manual
- **Notification Updates**:
  - Manual: User clicks reload button (↻) in notification dropdown
  - Automatic: Page refresh/reload (browser F5)
- **User Profile Updates**:
  - Automatic: Page refresh/reload
  - After user edits: Invalidate cache when returning from settings page
- **Navigation Menu Updates**:
  - Static configuration - no dynamic updates
  - Changes only apply on next login/page refresh

## Edge Cases & Error Handling

- **Empty State - No Notifications**: Show "No new notifications" message in dropdown with icon
- **Loading State - User Profile**: Show skeleton loader for user avatar and name during initial load
- **Loading State - Notifications**: Show skeleton loader for notification items in dropdown
- **Error State - User Profile Failed**: Show generic avatar icon, display error toast, retry after 30 seconds
- **Error State - Notifications Failed**: Show error icon on notification bell, display error toast when clicked
- **Permission Denied (403)**: If user tries to access unauthorized menu item, redirect to dashboard with error toast
- **Session Expired (401)**: Show modal "Your session has expired. Please log in again." with [Login] button
- **Network Offline**: Show offline banner at top of navigation, disable API-dependent features
- **Notification Reload Failed**: Show error toast "Could not load notifications. Please try again.", retry button in dropdown
- **Notification Deletion Failed**: Rollback optimistic update, show error toast "Could not delete notification. Please try again."
- **Logout Failed**: Show error toast "Logout failed. Please try again.", retry API call
- **Language Change Failed**: Rollback to previous language, show error toast "Could not update language preference"

## Change Log

| Date       | Version | Description                                      | Author     |
|------------|---------|--------------------------------------------------|------------|
| 2025-10-04 | 1.0     | Initial wireframe creation for Story 1.17        | UX Expert  |
| 2025-10-04 | 1.1     | Simplified based on stakeholder feedback: removed global search, WebSocket/polling, role switching | UX Expert  |

## Review Notes

### Stakeholder Feedback

- *Pending initial review*

### Design Iterations

- **v1.0**: Initial comprehensive wireframe based on frontend architecture document (docs/architecture/05-frontend-architecture.md) and Story 1.17 requirements
- **v1.1**: Simplified MVP scope based on stakeholder feedback:
  - **Removed**: Global search bar (deferred to Epic 5)
  - **Removed**: WebSocket/polling for notifications (manual reload only)
  - **Removed**: Role switching capability (single role per session)
  - **Changed**: Navigation menu items to static frontend config (no API)
  - **Added**: Manual reload button for notifications
  - **Simplified**: Breadcrumbs mandatory for organizer, optional for other roles

### Open Questions

All open questions have been resolved by stakeholder feedback:

1. ✅ **Search Implementation**: ~~Deferred to Epic 5~~ → **DECISION: Defer to Epic 5 (Attendee Experience - Content Discovery)**

2. ✅ **WebSocket vs. Polling**: ~~60-second polling~~ → **DECISION: No WebSocket or polling. Manual reload only.**

3. ✅ **Breadcrumb Visibility**: **DECISION: Recommendation accepted** - Required on organizer portal, optional on speaker/partner/attendee portals

4. ✅ **Mobile Menu Animation**: **DECISION: Recommendation accepted** - Left slide-in animation

5. ✅ **Navigation API**: **DECISION: Static frontend config** - No API call for menu items

6. ✅ **Role Switcher**: ~~Future enhancement~~ → **DECISION: No role switching. Single role per user session.**
