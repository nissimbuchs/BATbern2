# Attendee List Modal - Wireframe

**Story**: Epic 2, Story 2.4 - Current Event Landing Page (Extension)
**Screen**: Attendee List Modal
**User Role**: Attendee (Public/Authenticated)
**Related FR**: FR6 (Current Event Prominence), FR14 (Personal Engagement), FR15 (Mobile Experience)

---

## Visual Wireframe

```
┌────────────────────────────────────────────────────────────────────┐
│  Attendees (47)                                          [✕ Close] │
├────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌────────── FILTERS & SEARCH ────────────────────────────────────┐│
│  │                                                                 ││
│  │  🔍 Search attendees...                                        ││
│  │                                                                 ││
│  │  Filter: [All Companies ▾] [All Roles ▾] [✓] My Network       ││
│  │                                                                 ││
│  └─────────────────────────────────────────────────────────────────┘│
│                                                                     │
│  ┌────────── ATTENDEE LIST ────────────────────────────────────────┐│
│  │                                                                 ││
│  │  ┌─────────────────────────────────────────────────────────────┐││
│  │  │  [Photo]  Sara Kim                    🔗 Connect   ⋮       │││
│  │  │           Senior Architect                                  │││
│  │  │           Docker Inc.                                       │││
│  │  │           📍 Zürich • 🎯 Speaker                            │││
│  │  └─────────────────────────────────────────────────────────────┘││
│  │                                                                 ││
│  │  ┌─────────────────────────────────────────────────────────────┐││
│  │  │  [Photo]  Peter Müller               ✓ Connected  ⋮        │││
│  │  │           DevOps Lead                                       │││
│  │  │           TechCorp AG                                       │││
│  │  │           📍 Bern • 👤 Attendee                             │││
│  │  └─────────────────────────────────────────────────────────────┘││
│  │                                                                 ││
│  │  ┌─────────────────────────────────────────────────────────────┐││
│  │  │  [Photo]  Anna Lopez                  🔗 Connect   ⋮       │││
│  │  │           Cloud Engineer                                    │││
│  │  │           Swiss Re                                          │││
│  │  │           📍 Basel • 👤 Attendee                            │││
│  │  └─────────────────────────────────────────────────────────────┘││
│  │                                                                 ││
│  │  ... (47 attendees total)                                      ││
│  │                                                                 ││
│  │  [Load More]                                                   ││
│  └─────────────────────────────────────────────────────────────────┘│
│                                                                     │
│  ┌────────── PRIVACY NOTICE ──────────────────────────────────────┐│
│  │  ℹ️ Only attendees who opted into networking are shown.        ││
│  │     Update your privacy settings [here].                       ││
│  └─────────────────────────────────────────────────────────────────┘│
│                                                                     │
│                                              [Close]  [My Profile] │
└────────────────────────────────────────────────────────────────────┘
```

---

## Key Interactive Elements

- **Search Box** 🔍: Real-time search filtering attendees by name, company, or location
- **Company Filter Dropdown**: Multi-select dropdown to filter attendees by company affiliation
- **Role Filter Dropdown**: Filter by attendee type (Speaker, Organizer, Partner, Attendee)
- **My Network Toggle**: Checkbox to show only connected/known attendees
- **Connect Button** 🔗: Send connection request to attendee (authenticated users only)
- **Connected Indicator** ✓: Shows existing connections with green checkmark
- **More Options Menu** ⋮: Access additional actions (View Profile, Message, Block)
- **Attendee Card**: Click to view full profile detail modal
- **Load More Button**: Pagination control to load next batch of attendees
- **Privacy Settings Link**: Navigate to user settings to manage networking visibility
- **Close Button** ✕: Dismiss modal and return to Current Event Landing Page
- **My Profile Button**: Quick access to edit own attendee visibility

---

## Functional Requirements Met

- **FR6 (Current Event Prominence)**: Enhances the current event landing page by showing who's attending, increasing engagement and transparency
- **FR14 (Personal Engagement Management)**: Enables attendees to manage networking connections and discover peers attending the event
- **FR15 (Mobile-Optimized Experience)**: Modal design is fully responsive, works on mobile devices with touch-optimized cards and filters
- **Privacy & GDPR Compliance**: Only shows attendees who explicitly opted into networking visibility, with clear privacy controls

---

## Technical Notes

### UI Framework & Components
- **Modal Component**: Material-UI `Dialog` with full-screen mobile breakpoint
- **Search**: Debounced input (300ms) with real-time filtering via API or local state
- **Virtualization**: React-window or react-virtuoso for rendering 100+ attendee cards efficiently
- **Infinite Scroll**: Intersection Observer API for "Load More" pagination trigger
- **Skeleton Loading**: Display skeleton cards during initial load and filtering

### State Management
- **Local State**: Search query, selected filters, pagination cursor
- **Server State (React Query)**: Attendee list cached with 5-minute stale time
- **Optimistic Updates**: Connection requests show immediate UI feedback before API confirmation

### Responsive Design
- **Mobile (<768px)**: Full-screen modal, stacked filters, card view
- **Tablet (768-1024px)**: 80% viewport width modal, 2-column grid
- **Desktop (>1024px)**: Fixed 700px width modal, single column list

### Privacy & Security
- **Opt-in Visibility**: Default privacy setting is "hidden" - users must explicitly enable networking visibility
- **Connection Requests**: Stored separately from public attendee list; require mutual acceptance
- **Block Feature**: Users can hide specific attendees from their view

---

## API Requirements

### Initial Page Load APIs

1. **GET /api/v1/events/{eventId}/attendees?include=company,connections**
   - Query params:
     - `include=company,connections` (consolidated resource expansion from Story 1.17)
     - `filter={"networkingOptIn":true}` (only show users who enabled networking)
     - `limit=20` (pagination)
     - `offset=0` (pagination cursor)
   - Returns: Array of attendee objects with expanded company and connection data
     ```json
     {
       "attendees": [
         {
           "userId": "uuid",
           "firstName": "Sara",
           "lastName": "Kim",
           "profilePhotoUrl": "https://cdn.batbern.ch/...",
           "jobTitle": "Senior Architect",
           "location": "Zürich",
           "role": "SPEAKER",
           "networkingOptIn": true,
           "company": {
             "id": "uuid",
             "name": "Docker Inc.",
             "logo": "https://cdn.batbern.ch/..."
           },
           "connection": {
             "isConnected": false,
             "requestPending": false,
             "requestId": null
           }
         }
       ],
       "totalCount": 47,
       "hasMore": true,
       "nextOffset": 20
     }
     ```
   - Used for: Populate attendee list with company and connection data in single API call, show total count in header, enable pagination
   - Performance: Single API call replaces 3 separate calls (attendees + connections + settings)

2. **GET /api/v1/attendees/me?include=settings**
   - Query params: `include=settings` (consolidated Attendees API from Story 1.25)
   - Returns: Current user's profile with privacy settings
     ```json
     {
       "userId": "uuid",
       "firstName": "string",
       "lastName": "string",
       "settings": {
         "networkingOptIn": true,
         "privacyLevel": "public"
       }
     }
     ```
   - Used for: Check if current user is visible to others, show privacy notice if opted out

### User Action APIs

3. **GET /api/v1/events/{eventId}/attendees?include=company,connections&filter={...}**
   - Triggered by: User types in search box (debounced 300ms) or changes filters
   - Query params (using consolidated filter syntax from Story 1.16):
     - `include=company,connections`
     - `filter={"query":"sara","companies":["Docker Inc."],"roles":["SPEAKER"],"networkingOptIn":true}`
     - `limit=20`
     - `offset=0`
   - Response: Filtered attendee list (same schema as initial load)
   - Used for: Real-time search filtering without separate search endpoint
   - Performance: Uses same endpoint as initial load with different filter parameters

4. **POST /api/v1/users/{userId}/connection-requests**
   - Triggered by: User clicks [🔗 Connect] button
   - Payload:
     ```json
     {
       "targetUserId": "uuid",
       "message": "Hi, looking forward to connecting at BATbern!",
       "eventId": "event-uuid"
     }
     ```
   - Response: Connection request confirmation
     ```json
     {
       "requestId": "uuid",
       "status": "PENDING",
       "createdAt": "2025-05-01T10:00:00Z"
     }
     ```
   - Used for: Send connection request, show "Request Sent" button state

5. **DELETE /api/v1/users/me/connection-requests/{requestId}**
   - Triggered by: User clicks "Cancel Request" in more options menu (⋮)
   - Returns: 204 No Content
   - Used for: Cancel pending connection request

6. **GET /api/v1/events/{eventId}/attendees?include=company,connections&offset={cursor}**
   - Triggered by: User clicks [Load More] button or scrolls to bottom
   - Query params: `offset={nextOffset}`, `limit=20`, same filters as current view
   - Returns: Next batch of attendees (append to existing list)
   - Used for: Infinite scroll pagination

7. **PATCH /api/v1/attendees/me**
   - Triggered by: User toggles networking opt-in via privacy settings link
   - Payload:
     ```json
     {
       "settings": {
         "networkingOptIn": true
       }
     }
     ```
   - Response: Updated attendee profile
   - Used for: Update privacy visibility using consolidated Attendees API (Story 1.25), refresh modal content

---

## Navigation Map

### Primary Navigation Actions

1. **[✕ Close] button (top-right)** → Dismiss modal
   - Target: Current Event Landing Page (story-2.4-current-event-landing.md)
   - Action: Close modal overlay, return focus to landing page
   - Context: No state preserved

2. **[Close] button (bottom-left)** → Dismiss modal
   - Same as #1 (duplicate for accessibility and mobile UX)

3. **[My Profile] button (bottom-right)** → Navigate to User Profile Settings
   - Target: User Profile Screen (story-1.20-user-profile.md)
   - Action: Open user profile in new modal or navigate to settings page
   - Context: Opens to "Privacy & Networking" tab if in settings

### Secondary Navigation (Data Interactions)

4. **Click Attendee Card** → Navigate to User Profile Preview Modal
   - Target: User Profile Detail View (inline modal or full profile screen)
   - Action: Show read-only profile with networking options
   - Context: Pass `userId`, `eventId` for contextualized view

5. **[🔗 Connect] button** → Send connection request (inline action, no navigation)
   - No navigation - UI state change only
   - Action: Button changes to "Request Sent" with disabled state
   - Context: Show success toast notification

6. **[⋮ More Options] menu** → Dropdown menu (inline, no navigation)
   - Options:
     - **View Profile** → Navigate to full User Profile Detail View
     - **Send Message** → Opens messaging modal (future feature)
     - **Block User** → API call to block user (confirmation modal)
   - Context: User-specific actions

7. **Privacy Settings [here] link** → Navigate to User Settings
   - Target: User Settings Screen (story-5.2-user-settings.md)
   - Action: Opens settings page with "Privacy & Networking" tab active
   - Context: Highlight `networkingOptIn` toggle

### Event-Driven Navigation

8. **Connection Request Accepted (WebSocket/Polling)** → Update UI
   - No navigation - real-time UI update
   - Action: Change "Request Sent" button to "✓ Connected"
   - Context: Show notification toast "Sara Kim accepted your connection request"

9. **Search with No Results** → Show Empty State
   - No navigation - inline empty state
   - Action: Display "No attendees found. Try different filters."
   - Context: Preserve search query and filters for user to adjust

### Error States & Redirects

10. **Unauthenticated User** → Show Limited View or Login Prompt
    - Target: Login Screen (story-1.2-login-screen.md)
    - Condition: User not logged in, clicks [Connect] or views full list
    - Action: Show modal overlay "Sign in to connect with attendees" with [Login] CTA
    - Context: Return to this modal after successful login

11. **Privacy Disabled (User Opted Out)** → Show Privacy Banner
    - No navigation - show inline banner at top of modal
    - Message: "You're invisible to others. [Enable Networking] to appear in this list."
    - Action: Click banner navigates to User Settings privacy tab

12. **API Error (500, Network Failure)** → Show Error State
    - No navigation - inline error message
    - Action: Display "Unable to load attendees. [Retry]" with retry button
    - Context: Preserve user's search and filter state for retry

---

## Responsive Design Considerations

### Mobile Layout Changes (<768px)

- **Full-Screen Modal**: Modal expands to 100% viewport height and width
- **Filters Stack Vertically**: Search box, company filter, role filter, and "My Network" toggle stack in single column
- **Attendee Cards**: Full-width cards with left-aligned photo, single column layout
- **Sticky Search**: Search and filters stick to top of modal during scroll
- **Bottom Sheet Actions**: [Close] and [My Profile] buttons fixed to bottom as floating action buttons
- **Touch Targets**: All buttons and cards have minimum 44px tap target height

### Tablet Layout Changes (768-1024px)

- **80% Viewport Width**: Modal is 80% of screen width, max 600px
- **2-Column Grid (Optional)**: For landscape tablets, show 2-column card grid
- **Filters Inline**: Filters display in horizontal row instead of stacked
- **Sidebar Possible**: On landscape tablets, show filters in left sidebar panel

### Mobile-Specific Interactions

- **Swipe to Close**: Swipe down gesture from top of modal closes it (native mobile pattern)
- **Pull to Refresh**: Swipe down on attendee list refreshes data
- **Touch Feedback**: Haptic feedback on connection request sent (if device supports)
- **Card Swipe Actions**: Swipe left on attendee card reveals quick actions (Connect, Block)
- **Long Press Menu**: Long press on attendee card shows context menu (alternative to ⋮ button)

---

## Accessibility Notes

- **ARIA Labels**:
  - Modal: `role="dialog"` `aria-labelledby="modal-title"` `aria-describedby="modal-description"`
  - Close buttons: `aria-label="Close attendee list"`
  - Connect buttons: `aria-label="Send connection request to Sara Kim"`
  - More options: `aria-label="More options for Sara Kim"`

- **Keyboard Navigation**:
  - `Tab`: Navigate through search, filters, and attendee cards
  - `Enter/Space`: Activate buttons and open dropdowns
  - `Esc`: Close modal
  - `Arrow Keys`: Navigate within dropdowns and card lists
  - **Focus Trap**: Focus remains within modal while open, returns to trigger element on close

- **Screen Reader Support**:
  - Total count announced: "47 attendees"
  - Filter changes announced: "Filtered to 12 attendees from Docker Inc."
  - Connection status announced: "Connected with Peter Müller"
  - Loading state announced: "Loading more attendees"

- **Color Contrast**:
  - All text meets WCAG 2.1 AA standards (4.5:1 ratio minimum)
  - Connected checkmark uses green with sufficient contrast on white background
  - Focus indicators are clearly visible (2px blue outline)

- **Reduced Motion**: Respect `prefers-reduced-motion` media query
  - Disable modal slide-in animation
  - Disable smooth scrolling for pagination
  - Reduce or remove transition effects

---

## State Management

### Local Component State

- `searchQuery: string` - Current search input value
- `selectedCompanies: string[]` - Selected companies for filtering
- `selectedRoles: UserRole[]` - Selected roles for filtering
- `showMyNetworkOnly: boolean` - Toggle for "My Network" filter
- `attendees: Attendee[]` - Current loaded attendee list
- `isLoading: boolean` - Loading state for initial load and pagination
- `hasMore: boolean` - Whether more attendees exist for pagination
- `offset: number` - Current pagination offset

### Global State (Zustand Store)

- `userConnections: string[]` - List of user IDs the current user is connected with
- `pendingConnectionRequests: string[]` - List of user IDs with pending requests
- `currentUser.networkingOptIn: boolean` - Current user's networking visibility setting

### Server State (React Query)

- **eventAttendees** - Cached attendee list with expanded company and connection data (consolidated API)
  - Query Key: `['event-attendees', eventId, 'company,connections', filterParams]`
  - Stale Time: 5 minutes (data is relatively static during event)
  - Cache Time: 30 minutes
  - Refetch on Focus: true (refresh when user returns to tab)
  - Retry: 2 attempts with exponential backoff
  - Single API call with `?include=company,connections` replaces 3 separate queries

- **currentAttendeeSettings** - Current user's profile and settings
  - Query Key: `['attendee', 'me', 'settings']`
  - Stale Time: 10 minutes
  - Managed via consolidated Attendees API (Story 1.25)

### Real-Time Updates (Optional Enhancement)

- **WebSocket Connection**: Subscribe to connection request updates
  - Event: `CONNECTION_REQUEST_ACCEPTED` → Update UI to show "✓ Connected"
  - Event: `CONNECTION_REQUEST_REJECTED` → Revert button to "🔗 Connect"
- **Polling Fallback**: Poll `/api/v1/users/me/connections` every 30 seconds if WebSocket unavailable

---

## Form Validation Rules

### Field-Level Validations

This screen contains search and filter inputs but no traditional form submission. Validation is minimal:

- **Search Query**:
  - Min length: 0 (empty allowed for "show all")
  - Max length: 100 characters
  - Sanitization: Strip HTML tags, trim whitespace
  - Debounced: 300ms delay before triggering API call

- **Company Filter**:
  - Multi-select allowed
  - No validation required (dropdown from predefined list)

- **Role Filter**:
  - Multi-select allowed
  - No validation required (dropdown from enum: ORGANIZER, SPEAKER, PARTNER, ATTENDEE)

### Form-Level Validations

No form-level validations required. This is a read-only list with filtering capabilities, not a data submission form.

---

## Edge Cases & Error Handling

### Empty States

- **No Attendees Registered**:
  - Show message: "No attendees yet. Be the first to register!"
  - Display [Register Now] CTA button

- **All Attendees Opted Out of Networking**:
  - Show message: "No attendees have enabled networking visibility yet."
  - Display privacy explanation and [Learn More] link

- **Search Returns No Results**:
  - Show message: "No attendees found for 'query'. Try different filters."
  - Display [Clear Search] button to reset

- **User Not Registered for Event**:
  - Show banner: "Register for the event to connect with attendees. [Register]"
  - Display limited view (first 5 attendees only)

### Loading States

- **Initial Load**: Display 5 skeleton attendee cards with pulsing animation
- **Pagination Load**: Show spinner below last attendee card with "Loading more..."
- **Search Filtering**: Show loading overlay on attendee list with semi-transparent background

### Error States

- **API Failure (500 Error)**:
  - Show error message: "Unable to load attendees. Please try again."
  - Display [Retry] button that re-triggers API call
  - Log error to monitoring service (Sentry)

- **Network Timeout**:
  - Show message: "Request timed out. Check your connection and [Retry]."
  - Retry automatically after 5 seconds (show countdown)

- **Unauthorized (401)**:
  - Redirect to login screen with return URL
  - Show toast: "Please sign in to view attendees."

- **Forbidden (403 - User Banned)**:
  - Show message: "You don't have permission to view attendees."
  - Disable modal interaction

### Permission & Privacy Cases

- **Current User Opted Out**:
  - Show prominent banner at top: "You're invisible to other attendees. [Enable Networking]"
  - Still allow user to browse attendee list

- **Blocked Users**:
  - Don't display blocked users in attendee list
  - No indication that users have been filtered out (privacy protection)

- **Connection Request Limit Exceeded**:
  - Disable [Connect] button when user reaches rate limit (e.g., 20 requests/hour)
  - Show tooltip: "You've reached the connection request limit. Try again in 1 hour."

### Edge Case: Event Date Passed

- **Post-Event Access**:
  - Show historical attendee list (read-only)
  - Disable [Connect] button: "Event has ended. View profiles only."
  - Remove "My Network" filter (less relevant post-event)

---

## Change Log

| Date       | Version | Description                              | Author     |
|------------|---------|------------------------------------------|------------|
| 2025-10-04 | 1.0     | Initial wireframe creation               | Sally (UX) |
| 2025-10-04 | 1.1     | Updated to use consolidated APIs from Stories 1.16, 1.17, 1.25: single attendees endpoint with ?include=company,connections (reduced 3 API calls to 1), filter syntax for search, settings management via PATCH /api/v1/attendees/me | Winston (Architect) |

---

## Review Notes

### Stakeholder Feedback

*No feedback yet - awaiting review*

### Design Iterations

*Initial design based on Current Event Landing Page extension and networking requirements*

### Open Questions

1. **Connection Request Workflow**: Should connection requests be event-specific or platform-wide (follow across all events)?
   - **Recommendation**: Platform-wide connections (like LinkedIn) to build lasting community network

2. **Privacy Defaults**: Should networking opt-in be enabled by default for new users?
   - **Recommendation**: Default to `false` (GDPR conservative approach), prompt users during first event registration

3. **Messaging Feature**: Should connected users be able to message each other directly?
   - **Recommendation**: Phase 2 feature - start with connection requests only, add messaging later

4. **Speaker Highlighting**: Should speakers/organizers have special badges or highlighted cards?
   - **Recommendation**: Yes - use role badge (🎯 Speaker, 👥 Organizer) to help attendees identify key people

5. **Company Logo Display**: Should attendee cards show company logos instead of/in addition to company names?
   - **Recommendation**: Show company logo if available (20px thumbnail), fallback to company name

---

**Implementation Priority**: 🟢 **LOW** - Nice-to-have networking feature for enhanced attendee engagement. Not blocking MVP launch.

**Dependencies**:
- User authentication (Story 1.2)
- Event registration system (Story 2.4)
- User profile management (Story 1.20)
- Privacy settings (Story 5.2)

**Related Screens**:
- Current Event Landing Page (story-2.4-current-event-landing.md)
- User Profile (story-1.20-user-profile.md)
- User Settings (story-5.2-user-settings.md)
