# Story 2.4: Session Details Modal - Wireframe

**Story**: Epic 2, Story 4 - Current Event Landing Page (Session Details Modal)
**Screen**: Session Details Modal
**User Role**: Attendee (Public)
**Related FR**: FR6 (Current Event Prominence), FR15 (Mobile-Optimized Experience)

---

## Visual Wireframe

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                         [✕] │
│                                                                             │
│  ┌─── SESSION DETAILS ──────────────────────────────────────────────────┐  │
│  │                                                                        │  │
│  │  09:45 - 10:30 AM                                     🕒 45 minutes   │  │
│  │                                                                        │  │
│  │  KUBERNETES BEST PRACTICES: FROM DEV TO PRODUCTION                    │  │
│  │                                                                        │  │
│  │  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │  │
│  │                                                                        │  │
│  │  📍 Main Hall                       🎯 Technical Deep Dive            │  │
│  │  Kursaal Bern                       ⭐ Advanced Level                 │  │
│  │                                                                        │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌─── SPEAKER ──────────────────────────────────────────────────────────┐  │
│  │                                                                        │  │
│  │  ┌──────────┐                                                         │  │
│  │  │          │  Peter Müller                                           │  │
│  │  │ [Photo]  │  Senior Cloud Architect @ TechCorp AG                   │  │
│  │  │          │                                                          │  │
│  │  └──────────┘  10+ years Kubernetes experience • CNCF Ambassador      │  │
│  │                                                                        │  │
│  │                [View Full Profile →]                                  │  │
│  │                                                                        │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌─── SESSION DESCRIPTION ──────────────────────────────────────────────┐  │
│  │                                                                        │  │
│  │  Learn how to design, deploy, and manage Kubernetes clusters that    │  │
│  │  scale from development environments to production-ready systems.     │  │
│  │  This session covers:                                                 │  │
│  │                                                                        │  │
│  │  • Infrastructure as Code with GitOps workflows                       │  │
│  │  • Security hardening and RBAC best practices                         │  │
│  │  • High availability and disaster recovery strategies                 │  │
│  │  • Monitoring, logging, and observability patterns                    │  │
│  │  • Cost optimization techniques for cloud providers                   │  │
│  │                                                                        │  │
│  │  You'll leave with actionable strategies and real-world examples      │  │
│  │  from production deployments at scale.                                │  │
│  │                                                                        │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌─── KEY TAKEAWAYS ────────────────────────────────────────────────────┐  │
│  │                                                                        │  │
│  │  ✓ Production-ready Kubernetes architecture patterns                  │  │
│  │  ✓ Security best practices and compliance requirements                │  │
│  │  ✓ Monitoring and troubleshooting strategies                          │  │
│  │  ✓ GitOps workflow implementation guide                               │  │
│  │                                                                        │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌─── TARGET AUDIENCE ──────────────────────────────────────────────────┐  │
│  │                                                                        │  │
│  │  👥 DevOps Engineers • Cloud Architects • Platform Engineers          │  │
│  │                                                                        │  │
│  │  Prerequisites: Basic Kubernetes knowledge and container experience   │  │
│  │                                                                        │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌─── RELATED SESSIONS ─────────────────────────────────────────────────┐  │
│  │                                                                        │  │
│  │  → Service Mesh Architecture (11:00 AM)                               │  │
│  │  → GitOps Implementation (11:45 AM)                                   │  │
│  │  → Container Orchestration Workshop (15:00 PM)                        │  │
│  │                                                                        │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌─── ACTIONS ──────────────────────────────────────────────────────────┐  │
│  │                                                                        │  │
│  │  [📅 Add to My Schedule]  [🔗 Share Session]  [📄 Download Slides*]  │  │
│  │                                                                        │  │
│  │  * Available after the event                                          │  │
│  │                                                                        │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│              [← Back to Agenda]          [Register for Event →]            │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Interactive Elements

- **[✕] Close button**: Closes modal and returns to current event landing page

- **[View Full Profile →] link**: Navigates to speaker profile detail view (opens in new modal or page)

- **[📅 Add to My Schedule] button**:
  - For authenticated users: Adds session to personal schedule
  - For unauthenticated users: Opens login/registration modal with message "Register to build your personal schedule"

- **[🔗 Share Session] button**: Opens native share menu (mobile) or shows share options modal (desktop)
  - Options: Copy link, Twitter, LinkedIn, Email
  - Generates shareable deep link to this session

- **[📄 Download Slides] button**:
  - Disabled with tooltip before event: "Available after the event"
  - After event: Downloads presentation PDF directly
  - Tracks download analytics

- **[← Back to Agenda] link**: Closes modal and scrolls to agenda section on landing page

- **[Register for Event →] button**: Navigates to event registration page (story-2.4-event-registration.md)

- **Related session links**: Clicking any related session opens that session's detail modal

---

## Functional Requirements Met

- **FR6 (Current Event Prominence)**: Provides detailed session information for the current event with complete logistics (time, location, level)

- **FR15 (Mobile-Optimized Experience)**: Modal design adapts for mobile with scrollable content, touch-friendly buttons, and native share integration

- **FR14 (Personal Engagement)**: "Add to My Schedule" enables personal schedule management for authenticated attendees

- **NFR1 (Responsive Design)**: Modal layout optimized for mobile, tablet, and desktop with appropriate sizing and scrolling

---

## Technical Notes

- **Modal implementation**: Use React portal for overlay rendering outside DOM hierarchy
- **Scroll behavior**: Modal body scrollable while backdrop prevents page scroll (overflow: hidden on body)
- **Deep linking**: Generate shareable URLs like `/events/{eventId}/sessions/{sessionId}` that open modal on landing page
- **Keyboard navigation**: ESC key closes modal, Tab key cycles through interactive elements
- **Focus management**: Focus trap within modal, return focus to trigger element on close
- **Animation**: Smooth fade-in/scale animation for modal appearance (300ms transition)
- **Mobile optimization**: Full-screen modal on mobile (<768px), centered with max-width on desktop
- **Share API**: Use native Web Share API on mobile devices when available
- **Calendar integration**: "Add to My Schedule" creates iCal format for authenticated users
- **Analytics tracking**: Track modal views, share actions, and slide downloads for session popularity metrics
- **Lazy loading**: Speaker photo loaded only when modal opens (not on initial page load)
- **State persistence**: If user closes modal and reopens, scroll position resets to top

---

## API Requirements

### Initial Modal Load APIs

When the Session Details Modal opens, the following APIs are called:

1. **GET /api/v1/events/{eventId}/sessions/{sessionId}**
   - Query params: None
   - Returns: Complete session details
     ```json
     {
       "sessionId": "uuid",
       "title": "string",
       "description": "string",
       "startTime": "ISO-8601 datetime",
       "endTime": "ISO-8601 datetime",
       "duration": "integer (minutes)",
       "location": "string (Main Hall)",
       "venueDetails": "string (Kursaal Bern)",
       "sessionType": "enum (Keynote, Technical Deep Dive, Workshop, Panel)",
       "level": "enum (Beginner, Intermediate, Advanced)",
       "abstract": "string (max 1000 chars)",
       "keyTakeaways": ["string[]"],
       "targetAudience": "string",
       "prerequisites": "string",
       "capacity": "integer",
       "registeredCount": "integer",
       "tags": ["string[]"],
       "slidesAvailable": "boolean",
       "slidesUrl": "string | null"
     }
     ```
   - Used for: Display session title, time, duration, location, level, description, takeaways, audience info, enable/disable slides download

2. **GET /api/v1/speakers/{speakerId}**
   - Returns: Speaker details
     ```json
     {
       "speakerId": "uuid",
       "firstName": "string",
       "lastName": "string",
       "profilePhoto": "string (URL)",
       "company": "string",
       "position": "string",
       "bio": "string",
       "expertiseAreas": ["string[]"],
       "yearsExperience": "integer",
       "certifications": ["string[]"],
       "socialLinks": {
         "linkedin": "string | null",
         "twitter": "string | null",
         "github": "string | null"
       }
     }
     ```
   - Used for: Display speaker name, photo, company, position, experience, certifications in speaker section

3. **GET /api/v1/events/{eventId}/sessions?relatedTo={sessionId}**
   - Query params: `relatedTo` (sessionId to find related sessions)
   - Returns: Array of related sessions (max 3)
     ```json
     [
       {
         "sessionId": "uuid",
         "title": "string",
         "startTime": "ISO-8601 datetime",
         "sessionType": "string"
       }
     ]
     ```
   - Used for: Display related sessions list with titles and times, enable click-through to view related session modals

### User Action APIs

4. **POST /api/v1/attendees/{userId}/schedule**
   - Triggered by: User clicks [📅 Add to My Schedule] (authenticated users only)
   - Payload:
     ```json
     {
       "sessionId": "uuid",
       "eventId": "uuid",
       "source": "session_modal"
     }
     ```
   - Returns: Schedule confirmation
     ```json
     {
       "scheduleId": "uuid",
       "addedAt": "ISO-8601 datetime",
       "conflictingSessions": ["sessionId[]"] // empty if no conflicts
     }
     ```
   - Used for: Adds session to user's personal schedule, shows success toast message, handles conflicts if any

5. **GET /api/v1/events/{eventId}/sessions/{sessionId}/share-link**
   - Triggered by: User clicks [🔗 Share Session]
   - Returns: Shareable deep link
     ```json
     {
       "shareUrl": "string (https://batbern.ch/events/{eventId}?session={sessionId})",
       "title": "string (session title for share preview)",
       "description": "string (session abstract snippet)",
       "imageUrl": "string (speaker photo or event banner)"
     }
     ```
   - Used for: Generates shareable URL for social media, email, or copy to clipboard, provides Open Graph metadata for rich previews

6. **GET /api/v1/events/{eventId}/sessions/{sessionId}/slides**
   - Triggered by: User clicks [📄 Download Slides] (only if slidesAvailable = true)
   - Returns: PDF file or redirect URL to S3/CloudFront
   - Used for: Downloads session presentation slides, tracks download analytics

7. **POST /api/v1/analytics/session-view**
   - Triggered by: Modal opens (automatic, no user action)
   - Payload:
     ```json
     {
       "sessionId": "uuid",
       "eventId": "uuid",
       "userId": "uuid | null",
       "source": "landing_page",
       "timestamp": "ISO-8601 datetime"
     }
     ```
   - Returns: 204 No Content
   - Used for: Track session popularity, modal view analytics, inform recommendations

---

## Navigation Map

### Primary Navigation Actions

1. **[✕] Close button** → Closes modal
   - Target: Returns to Current Event Landing Page (same page, modal closes)
   - Context: No state change, scroll position preserved on landing page
   - Behavior: Fade-out animation, remove modal from DOM, restore body scroll

2. **[← Back to Agenda] link** → Closes modal and scrolls to agenda
   - Target: Current Event Landing Page, agenda section
   - Context: Closes modal, scrolls page to agenda section
   - Behavior: Modal closes, smooth scroll to #agenda anchor

3. **[Register for Event →] button** → Navigate to `Event Registration` page
   - Target: Event Registration Form (story-2.4-event-registration.md)
   - Context: eventId, source: "session_modal", sessionId (to pre-register interest)
   - Behavior: Full page navigation, modal closes automatically

4. **[View Full Profile →] link** → Navigate to `Speaker Profile Detail View`
   - Target: Speaker Profile Detail View (story-7.1-speaker-profile-detail-view.md)
   - Context: speakerId, source: "session_modal"
   - Behavior: Opens new modal overlaying session modal (modal stacking) OR navigates to speaker page

### Secondary Navigation (Data Interactions)

5. **Related session link click** → Opens `Session Details Modal` (recursive)
   - Target: Session Details Modal for related session
   - Context: New sessionId, replaces current modal content
   - Behavior: Modal content transitions to new session (slide animation), maintains modal open state

6. **[📅 Add to My Schedule] button** → Inline state update
   - Target: Same modal (no navigation)
   - Context: Updates user's schedule, changes button state
   - Behavior: Button changes to "✓ Added to Schedule" with green checkmark, shows success toast
   - Error handling: If conflict, shows conflict modal with options to resolve

7. **[🔗 Share Session] button** → Opens `Share Modal` or native share
   - Target: Share modal (desktop) or native share sheet (mobile)
   - Context: Session share URL, title, description
   - Behavior: On mobile, triggers Web Share API; on desktop, shows share options modal overlay

### Event-Driven Navigation

8. **User not authenticated + clicks [📅 Add to My Schedule]** → Opens `Login Modal`
   - Target: Login/Registration modal
   - Context: Return action = add session to schedule, sessionId
   - Behavior: Shows login modal overlay with message "Sign in to add sessions to your schedule"
   - Post-login: Automatically adds session to schedule and shows success message

9. **Slides not available yet (before event)** → No navigation, shows tooltip
   - Target: No navigation
   - Behavior: Disabled button shows tooltip "Slides available after the event"

### Error States & Redirects

10. **Session not found (invalid sessionId)** → Close modal, show error toast
    - Target: Returns to Current Event Landing Page
    - Behavior: Modal closes, error toast: "Session not found or no longer available"

11. **API failure (500 error)** → Show error state in modal
    - Target: Modal remains open with error state
    - Behavior: Shows error message in modal with [Retry] button to reload session data

12. **Session canceled or removed** → Show cancellation notice
    - Target: Modal shows cancellation message instead of details
    - Behavior: "This session has been canceled. See updated agenda for alternatives."
    - Action: [View Updated Agenda] button closes modal and scrolls to agenda

---

## Responsive Design Considerations

### Mobile Layout Changes (<768px)

- **Full-screen modal**: Modal takes entire viewport (100vw × 100vh) with internal scrolling
- **Close button**: Larger tap target (44×44px minimum) positioned in top-right corner
- **Speaker section**: Speaker photo smaller (60×60px), info stacks below photo instead of beside
- **Action buttons**: Stack vertically, full-width with adequate spacing (16px between)
- **Related sessions**: Display as vertical list instead of horizontal
- **Typography**: Larger font sizes for readability (16px minimum body text)
- **Share button**: Triggers native share sheet on mobile devices

### Tablet Layout Changes (768px - 1024px)

- **Centered modal**: Max-width 700px, centered with backdrop on sides
- **Two-column layout**: Speaker section and key takeaways side-by-side if space permits
- **Action buttons**: Remain horizontal but with responsive sizing
- **Scroll behavior**: Modal scrolls internally, page scroll disabled

### Desktop Layout (>1024px)

- **Centered modal**: Max-width 900px, centered with darkened backdrop
- **Hover states**: All buttons show hover effects for better affordance
- **Keyboard shortcuts**: Display keyboard shortcut hints (ESC to close)

### Mobile-Specific Interactions

- **Swipe down gesture**: Swipe down from top of modal to close (mobile only)
- **Tap outside modal**: Tap on backdrop closes modal (with visual feedback)
- **Share via native sheet**: Share button uses Web Share API on mobile for system sharing
- **Add to device calendar**: "Add to My Schedule" offers native calendar integration on mobile
- **Speaker photo tap**: Tap on speaker photo opens full profile (not just link)

---

## Accessibility Notes

- **Focus management**:
  - Focus trapped within modal when open
  - First focusable element (close button) receives focus on open
  - Focus returns to trigger element (session card) on close
  - Tab order: Close → Speaker profile → Action buttons → Related sessions → Back/Register

- **Screen reader support**:
  - Modal announced as "Dialog: Session Details" with `role="dialog"` and `aria-modal="true"`
  - Close button labeled "Close session details dialog"
  - All action buttons have descriptive `aria-label` attributes
  - Disabled slides button includes `aria-disabled="true"` and tooltip text in `aria-describedby`

- **Keyboard navigation**:
  - ESC key closes modal
  - TAB cycles through interactive elements
  - SHIFT+TAB cycles backward
  - ENTER/SPACE activates buttons and links

- **Color contrast**:
  - All text meets WCAG 2.1 AA standards (4.5:1 for body, 3:1 for large text)
  - Interactive elements have sufficient contrast in all states (default, hover, focus, disabled)

- **Visual indicators**:
  - Focus indicators clearly visible (2px solid outline, high contrast color)
  - Loading states announced with ARIA live regions
  - Success/error toasts announced to screen readers

- **Content structure**:
  - Proper heading hierarchy (H2 for modal title, H3 for sections)
  - Lists properly marked up with semantic HTML
  - Time displayed in both human-readable and ISO-8601 format for screen readers

---

## State Management

### Local Component State

- `isOpen: boolean` - Modal open/close state (managed by parent component)
- `isLoadingSession: boolean` - Loading state while fetching session details
- `isLoadingSpeaker: boolean` - Loading state while fetching speaker info
- `isAddingToSchedule: boolean` - Loading state for "Add to Schedule" action
- `isInSchedule: boolean` - Whether session is already in user's schedule
- `slideDownloadInProgress: boolean` - Loading state for slide download
- `showShareOptions: boolean` - Share modal visibility (desktop only)
- `scrollPosition: number` - Track modal scroll for analytics (time spent by section)

### Global State (Zustand Store)

- `auth.user` - Current user information (null if not authenticated)
- `auth.isAuthenticated` - Authentication status
- `attendee.mySchedule` - List of sessionIds in user's personal schedule
- `events.currentEvent` - Current event context (eventId, eventDate)

### Server State (React Query)

- **sessionDetails** - Cached session data, 5-minute stale time, invalidated on session updates
  - Query key: `['session', eventId, sessionId]`
  - Cache time: 10 minutes
  - Refetch on mount: false (modal-specific data)

- **speakerProfile** - Cached speaker data, 1-hour stale time
  - Query key: `['speaker', speakerId]`
  - Cache time: 1 hour
  - Shared across all session modals with same speaker

- **relatedSessions** - Cached related sessions, 10-minute stale time
  - Query key: `['related-sessions', sessionId]`
  - Cache time: 10 minutes

- **userSchedule** - User's personal schedule, invalidated on add/remove
  - Query key: `['user-schedule', userId]`
  - Cache time: 5 minutes
  - Refetch on window focus: true

### Real-Time Updates

- **Session changes**: WebSocket subscription to session updates (title, time, cancellation)
  - Channel: `events/${eventId}/sessions/${sessionId}`
  - Events: `session.updated`, `session.canceled`, `session.time_changed`
  - Behavior: Show banner at top of modal with update notification

- **Slide availability**: Poll every 30 seconds after event start to check for uploaded slides
  - Condition: Only poll if `slidesAvailable === false` and event has started
  - Stop polling: When slides become available or modal closes

---

## Edge Cases & Error Handling

- **Empty State (No speaker assigned)**: Show placeholder: "Speaker to be announced" with generic avatar

- **Loading State (Initial load)**: Display skeleton screens for session details, speaker info, and related sessions

- **Error State (Session not found)**: Show error message in modal: "Session not found. It may have been removed or rescheduled." with [View Updated Agenda] button

- **Error State (API failure)**: Show error message with [Retry] button: "Unable to load session details. Please try again."

- **Session canceled**: Show prominent warning banner: "⚠️ This session has been canceled. See updated agenda for alternatives." Disable registration and schedule buttons

- **Time conflict**: When adding to schedule, if another session overlaps, show conflict modal: "This session conflicts with [Session Title] at [Time]. Which would you like to attend?"

- **Not authenticated + Add to Schedule**: Show login prompt modal: "Create a free account to build your personal schedule" with [Sign Up] and [Sign In] buttons

- **Slides not available**: [Download Slides] button disabled with tooltip: "Slides will be available after the event"

- **Share API not supported**: Fallback to copy-to-clipboard with confirmation toast: "Link copied to clipboard"

- **Session full (capacity reached)**: Show "Session is full" badge, disable "Add to Schedule", offer waitlist option

- **Related sessions empty**: Hide "Related Sessions" section entirely if no related sessions found

- **Mobile landscape mode**: Ensure modal scrollable in landscape orientation with proper safe area insets

- **Slow network**: Show loading skeleton immediately, timeout after 15 seconds with retry option

- **Speaker photo failed to load**: Show fallback initials avatar with speaker's first and last name initials

- **Long session description**: Show truncated text with "Read more" link that expands to full description

---

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-10-02 | 1.0 | Initial wireframe creation for Session Details Modal | Sally (UX Expert) |

---

## Review Notes

### Stakeholder Feedback
- Awaiting product owner review for "Add to My Schedule" feature scope
- Need confirmation on whether slides should be downloadable before or after event

### Design Iterations
- Version 1.0: Initial creation based on current event landing page navigation flows

### Open Questions
1. Should we show real-time attendance count ("120/150 registered")?
2. Do we need a Q&A section for attendees to submit questions in advance?
3. Should related sessions be clickable from within the modal (modal stacking) or close current modal first?
4. Should we display speaker rating/reviews from past events?
5. Do we need to integrate with calendar apps for "Add to My Schedule" (beyond authentication)?
6. Should the modal support deep linking (direct URL access)?
7. What happens if a speaker has multiple sessions at the event - show "Other sessions by this speaker"?
