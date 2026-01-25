# Story 5.2: Event Details Page (Attendee View) - Wireframe

**Story**: Epic 5, Story 5.2 - Personal Engagement Management
**Screen**: Event Details Page (Attendee View)
**User Role**: Attendee (Authenticated)
**Related FR**: FR6 (Current Event Prominence), FR14 (Personal Engagement Management)

---

## Visual Wireframe

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│ ← Back to Dashboard    BATbern Event Details               [Share ⤴] [•••]      │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                   │
│  ┌──── EVENT HEADER ──────────────────────────────────────────────────────────┐  │
│  │                                                                             │  │
│  │  🚀 SPRING CONFERENCE 2025: CLOUD NATIVE ARCHITECTURE                      │  │
│  │                                                                             │  │
│  │  📅 May 15, 2025 • 08:30 - 17:30      📍 Kursaal Bern                     │  │
│  │                                                                             │  │
│  │  ✅ Registered    [ View My Schedule ] [ Add to Calendar ⬇ ]              │  │
│  │                                                                             │  │
│  └─────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                   │
│  ┌─ EVENT OVERVIEW ──────────────┬─ MY PARTICIPATION ────────────────────────┐   │
│  │                                │                                            │   │
│  │  📋 Description                │  ✅ Registration Status: Confirmed         │   │
│  │  Comprehensive deep-dive into  │     Registration #: BAT-2025-045          │   │
│  │  cloud-native architecture,    │                                            │   │
│  │  featuring 8 expert speakers.  │  📊 My Selected Sessions: 4/6             │   │
│  │                                 │     Morning: 2 sessions                   │   │
│  │  🎯 Topics Covered:            │     Afternoon: 2 sessions                 │   │
│  │  • Kubernetes at Scale          │                                            │   │
│  │  • Service Mesh                 │  🔖 Bookmarked Content: 3 items           │   │
│  │  • Cloud Security               │                                            │   │
│  │  • GitOps & CI/CD              │  [ Manage My Schedule → ]                 │   │
│  │                                 │  [ View QR Code → ]                       │   │
│  │  🎟️ Free Admission              │                                            │   │
│  │  👥 Expected: ~200 attendees   │                                            │   │
│  │                                 │                                            │   │
│  └─────────────────────────────────┴────────────────────────────────────────────┘  │
│                                                                                   │
│  ┌──── SESSION SCHEDULE ────────────────────────────────────────────────────────┐  │
│  │                                                                               │  │
│  │  Filter: [All Sessions ⏷]  [My Schedule Only ☐]  [Morning ☐]  [Afternoon ☐] │  │
│  │                                                                               │  │
│  │  ┌─ 09:00-09:45 ──────────────────────────────────────────────────────────┐ │  │
│  │  │  ✓ Keynote: Future of Cloud Native                     [In My Schedule] │ │  │
│  │  │     🎤 Sara Kim (Docker Inc.)                          Room: Main Hall  │ │  │
│  │  │     "Exploring the future of cloud-native platforms..."                 │ │  │
│  │  │     👥 195/200 attending    [View Details →]                            │ │  │
│  │  └───────────────────────────────────────────────────────────────────────────┘ │  │
│  │                                                                               │  │
│  │  ┌─ 09:45-10:30 ─────────────────────────────────────────────────────────┐ │  │
│  │  │  ✓ Kubernetes Best Practices                          [In My Schedule] │ │  │
│  │  │     🎤 Peter Muller (TechCorp AG)                      Room: Hall A     │ │  │
│  │  │     "Production-ready K8s deployments at scale..."                      │ │  │
│  │  │     👥 87/120 attending    [View Details →]                             │ │  │
│  │  └───────────────────────────────────────────────────────────────────────────┘ │  │
│  │                                                                               │  │
│  │  ┌─ 10:30-11:00 ─────────────────────────────────────────────────────────┐ │  │
│  │  │  ☕ Coffee Break & Networking                                            │ │  │
│  │  │     Location: Foyer                                                     │ │  │
│  │  └───────────────────────────────────────────────────────────────────────────┘ │  │
│  │                                                                               │  │
│  │  ┌─ 11:00-11:45 ─────────────────────────────────────────────────────────┐ │  │
│  │  │    Service Mesh Architecture                           [+ Add to Schedule] │ │  │
│  │  │     🎤 Thomas Weber (Swiss Re)                         Room: Hall B     │ │  │
│  │  │     "Implementing Istio in enterprise environments..." │ │  │
│  │  │     👥 92/120 attending    [View Details →]                             │ │  │
│  │  └───────────────────────────────────────────────────────────────────────────┘ │  │
│  │                                                                               │  │
│  │                           [Show All Sessions (6 more) ↓]                     │  │
│  │                                                                               │  │
│  └───────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                   │
│  ┌──── SPEAKER LINEUP ───────────────────────────────────────────────────────────┐  │
│  │                                                                               │  │
│  │  8 Expert Speakers:                                    [View All Speakers →] │  │
│  │                                                                               │  │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐            │  │
│  │  │  [Photo]   │  │  [Photo]   │  │  [Photo]   │  │  [Photo]   │            │  │
│  │  │            │  │            │  │            │  │            │            │  │
│  │  │ Sara Kim   │  │ Peter      │  │ Thomas     │  │ Anna       │            │  │
│  │  │            │  │ Muller     │  │ Weber      │  │ Lopez      │            │  │
│  │  │ Docker Inc.│  │ TechCorp   │  │ Swiss Re   │  │ Google     │            │  │
│  │  │            │  │            │  │            │  │            │            │  │
│  │  │ 2 Sessions │  │ 1 Session  │  │ 1 Session  │  │ 1 Session  │            │  │
│  │  └────────────┘  └────────────┘  └────────────┘  └────────────┘            │  │
│  │                                                                               │  │
│  └───────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                   │
│  ┌──── VENUE & LOGISTICS ────────────────────────────────────────────────────────┐  │
│  │                                                                               │  │
│  │  📍 Kursaal Bern                           🗺️ [Interactive Map]             │  │
│  │     Kornhausstrasse 3, 3013 Bern                                             │  │
│  │                                                                               │  │
│  │  🚇 Public Transport:                      🚗 Parking:                       │  │
│  │     • Tram 3, 6: "Kursaal"                    • Kursaal Parking (CHF 3/h)   │  │
│  │     • Bus 12: "Kornhausplatz"                 • Park & Ride available        │  │
│  │                                                                               │  │
│  │  [ Get Directions → ]  [ Download Venue Map (PDF) ]                          │  │
│  │                                                                               │  │
│  └───────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                   │
│  ┌──── RELATED CONTENT ──────────────────────────────────────────────────────────┐  │
│  │                                                                               │  │
│  │  📚 Related Past Events:                                                     │  │
│  │                                                                               │  │
│  │  ┌─────────────────────────────────────────────────────────────────────┐    │  │
│  │  │  Fall 2024: Cloud Security                           🔖 3 presentations  │    │  │
│  │  │  Spring 2024: Kubernetes Deep Dive                   🔖 5 presentations  │    │  │
│  │  │  Fall 2023: Microservices Architecture               🔖 4 presentations  │    │  │
│  │  └─────────────────────────────────────────────────────────────────────┘    │  │
│  │                                                                               │  │
│  │  [ Browse All Events → ]  [ Search Archive → ]                               │  │
│  │                                                                               │  │
│  └───────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                   │
│  ┌──── QUICK ACTIONS ────────────────────────────────────────────────────────────┐  │
│  │                                                                               │  │
│  │  [ 📧 Email Event Details ]  [ 🔗 Copy Event Link ]  [ 📱 Add to Wallet ]   │  │
│  │                                                                               │  │
│  └───────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Interactive Elements

- **[← Back to Dashboard] button**: Return to Personal Attendee Dashboard (story-5.2-personal-dashboard.md)
- **[Share ⤴] button**: Opens share modal with options (Email, LinkedIn, Twitter, Copy Link)
- **[•••] menu**: Additional options dropdown (Report Issue, Download All Materials, Remove from My Events)
- **[View My Schedule] button**: Opens personalized schedule view with only selected sessions
- **[Add to Calendar ⬇] dropdown**: Download options (iCal, Google Calendar, Outlook)
- **[Manage My Schedule →] button**: Navigate to Session Selection/Modification screen
- **[View QR Code →] button**: Display event check-in QR code
- **Session checkboxes (✓)**: Indicates session is in personal schedule
- **[In My Schedule] / [+ Add to Schedule] buttons**: Toggle session in personal schedule
- **[View Details →] links**: Open Session Details Modal (story-2.4-session-details-modal.md)
- **[View All Speakers →] link**: Navigate to Speaker Lineup page
- **Speaker photo cards**: Click to view Speaker Profile Detail (story-7.1-speaker-profile-detail-view.md)
- **[Interactive Map]**: Embedded Google Maps with venue location
- **[Get Directions →] button**: Open directions in default map app
- **[Download Venue Map (PDF)] button**: Download printable venue map
- **Related event cards**: Click to navigate to past event details
- **[Browse All Events →] button**: Navigate to Historical Archive (story-1.18-historical-archive.md)
- **[Search Archive →] button**: Navigate to Content Discovery (story-5.1-content-discovery.md)
- **Quick action buttons**: Trigger respective actions (email, copy link, add to wallet)

---

## Functional Requirements Met

- **FR6**: Event details prominently displayed with complete logistics and registration information
- **FR14**: Personal engagement management through session selection, bookmarking, and calendar integration
- **FR6**: Clear event information display with date, location, and topic details
- **FR11**: Access to related historical events and content archive
- **FR15**: Mobile-optimized layout with responsive design (mobile wireframe adaptations below)

---

## Technical Notes

- **Real-time Attendance Updates**: WebSocket connection for live session capacity updates
- **Responsive Design**: Mobile-first approach with collapsible sections for small screens
- **Caching Strategy**: Event details cached with 5-minute TTL, session capacity refreshed every 30 seconds
- **State Management**: Zustand store for personal schedule (selectedSessions array)
- **Calendar Integration**: iCal file generation using ics library
- **QR Code Generation**: Dynamic QR code with registration details (qrcode.react library)
- **Map Integration**: Google Maps Embed API with venue coordinates
- **Share Functionality**: Web Share API with fallback to modal for unsupported browsers
- **Wallet Integration**: Apple Wallet / Google Pay pass generation via Passkit

---

## API Requirements

### Initial Page Load APIs

1. **GET /api/v1/events/{eventId}**
   - Returns: Complete event details (id, title, description, eventDate, startTime, endTime, venue, topics, maxAttendees, registrationOpen, admissionFee)
   - Used for: Display in header, event overview section, venue details, SEO meta tags

2. **GET /api/v1/events/{eventId}/sessions**
   - Query params: includeCapacity=true, includeSpeakers=true
   - Returns: Array of sessions (id, title, startTime, endTime, sessionType, room, description, speakerId, speakerName, speakerCompany, currentAttendees, maxCapacity)
   - Used for: Display session schedule, speaker lineup, capacity indicators

3. **GET /api/v1/events/{eventId}/speakers**
   - Returns: Array of speakers (id, firstName, lastName, company, profilePhoto, position, sessionCount, sessionTitles)
   - Used for: Display speaker lineup section, speaker photos and details

4. **GET /api/v1/registrations/me?eventId={eventId}**
   - Returns: User's registration details (registrationNumber, status, selectedSessions[], qrCodeData, registeredAt)
   - Used for: Display registration status, populate "My Participation" section, show QR code data

5. **GET /api/v1/events/{eventId}/venue**
   - Returns: Venue details (name, address, mapUrl, coordinates, publicTransport[], parkingInfo, venueMapPdfUrl)
   - Used for: Display venue section, embed map, show transportation options

6. **GET /api/v1/events/related?eventId={eventId}&limit=3**
   - Returns: Related events based on topics (id, title, eventDate, sessionCount, presentationCount)
   - Used for: Display related content section

### User Action APIs

7. **PUT /api/v1/registrations/{registrationId}/sessions**
   - Triggered by: User clicks [In My Schedule] or [+ Add to Schedule]
   - Payload: `{ sessionId: string, action: "add" | "remove" }`
   - Returns: Updated registration with selectedSessions[]
   - Used for: Update user's selected sessions, refresh schedule display

8. **GET /api/v1/registrations/{registrationId}/calendar**
   - Triggered by: User clicks [Add to Calendar] dropdown option
   - Query params: format=ical|google|outlook
   - Returns: Calendar file or redirect URL
   - Used for: Download calendar file or open calendar web service

9. **GET /api/v1/registrations/{registrationId}/qr-code**
   - Triggered by: User clicks [View QR Code]
   - Returns: QR code data (base64 image, registrationNumber, checkInUrl)
   - Used for: Display QR code modal for event check-in

10. **POST /api/v1/events/{eventId}/share**
    - Triggered by: User clicks [Share] or quick action buttons
    - Payload: `{ method: "email" | "link" | "social", recipient?: string }`
    - Returns: Shareable link or confirmation
    - Used for: Generate shareable event link, send email with event details

11. **GET /api/v1/events/{eventId}/venue-map**
    - Triggered by: User clicks [Download Venue Map (PDF)]
    - Returns: PDF file download
    - Used for: Download printable venue map

12. **GET /api/v1/registrations/{registrationId}/wallet-pass**
    - Triggered by: User clicks [Add to Wallet]
    - Query params: platform=apple|google
    - Returns: Wallet pass file (.pkpass or .pkpass URL)
    - Used for: Download Apple Wallet or Google Pay pass

---

## Navigation Map

### Primary Navigation Actions

1. **← Back button** → Navigate to `Personal Attendee Dashboard` (story-5.2-personal-dashboard.md)
   - Target: Personal dashboard with event overview
   - Context: Return to dashboard with updated schedule

2. **[View My Schedule] button** → Opens filtered session view
   - Target: Same screen, filtered to show only selected sessions
   - Context: Filter state applied to session list

3. **[Add to Calendar ⬇] dropdown** → Calendar integration
   - Options: iCal download, Google Calendar, Outlook
   - Target: Calendar app or file download
   - Context: Event details and selected sessions

4. **[Manage My Schedule →] button** → Navigate to `Session Selection Screen`
   - Target: Dedicated session management interface
   - Context: eventId, current selectedSessions[]

5. **[View QR Code →] button** → Opens QR Code Modal
   - Target: Modal overlay (same page)
   - Context: registrationNumber, qrCodeData

### Secondary Navigation (Data Interactions)

6. **Session [View Details →] link** → Opens `Session Details Modal` (story-2.4-session-details-modal.md)
   - Target: Modal overlay with full session information
   - Context: sessionId, speakerInfo, session details

7. **Speaker photo card** → Navigate to `Speaker Profile Detail View` (story-7.1-speaker-profile-detail-view.md)
   - Target: Full speaker profile page
   - Context: speakerId, eventContext

8. **[View All Speakers →] link** → Navigate to `Full Speaker List`
   - Target: Complete speaker lineup page
   - Context: eventId, all speakers array

9. **Related event card** → Navigate to `Event Details` (past event)
   - Target: Same screen structure for historical event
   - Context: eventId (past event)

10. **[Browse All Events →] button** → Navigate to `Historical Archive` (story-1.18-historical-archive.md)
    - Target: Event archive home page
    - Context: No filters applied

11. **[Search Archive →] button** → Navigate to `Content Discovery` (story-5.1-content-discovery.md)
    - Target: Content search interface
    - Context: Pre-filled with related topics from current event

### Event-Driven Navigation

12. **[Share ⤴] modal interactions** → Various share actions
    - Email: Opens email client or share modal
    - Social: Opens social platform share dialog
    - Copy Link: Copies URL to clipboard (stays on page)

13. **Map interactions** → External navigation
    - [Get Directions]: Opens Google Maps/Apple Maps app
    - [Interactive Map]: Shows map modal or inline navigation

### Error States & Redirects

14. **Registration not found** → Redirect to `Event Registration Flow`
    - Condition: User not registered for event
    - Target: story-2.4-event-registration.md
    - Context: eventId, redirect back to details after registration

15. **Event not found / 404** → Redirect to `Event Listing`
    - Condition: Invalid eventId
    - Target: Event catalog page
    - Context: Error message displayed

16. **Unauthorized access** → Redirect to `Login`
    - Condition: User not authenticated
    - Target: Login screen
    - Context: Return URL to event details after login

---

## Responsive Design Considerations

### Mobile Layout Changes

**Stacked Layout (320px - 768px):**
- Header: Event title, date, location stacked vertically
- Action buttons: Full-width CTAs ([View My Schedule], [Add to Calendar])
- Event Overview & My Participation: Combined into single collapsible section
- Session Schedule: Card-based layout (one session per row)
  - Session time, title, speaker stacked
  - Capacity indicator below speaker name
  - [+ Add] button moves to bottom of card
- Speaker Lineup: 2-column grid on mobile (down from 4)
- Venue & Logistics: Stacked sections with collapsible transport/parking details
- Related Content: Single column list
- Quick Actions: Bottom sheet modal (fixed at bottom)

**Tablet Layout (768px - 1024px):**
- 2-column layout for Event Overview / My Participation
- Session Schedule: Full width with compact card design
- Speaker Lineup: 3-column grid
- Venue map: Full width with side-by-side transport/parking info

### Mobile-Specific Interactions

- **Swipe gestures**: Swipe between sessions in schedule
- **Pull-to-refresh**: Update session capacity and registration status
- **Bottom sheet modals**: Share options, QR code, session details
- **Sticky header**: Event title and [Add to Calendar] button sticky on scroll
- **Collapsible sections**: Tap to expand/collapse venue details, related events
- **Touch-optimized**: 44px minimum touch target for all interactive elements
- **Native share**: Use Web Share API on mobile for share functionality
- **Add to Wallet**: Native wallet integration for iOS/Android

---

## Accessibility Notes

- **Keyboard Navigation**: All interactive elements accessible via Tab key with logical focus order
- **ARIA Labels**:
  - `aria-label="Add session to personal schedule"` on [+ Add] buttons
  - `aria-label="View session details for Kubernetes Best Practices"` on [View Details] links
  - `aria-label="Share event via email, social media, or link"` on [Share] button
  - `role="region"` on main content sections with `aria-labelledby` pointing to section headers
- **Screen Reader Announcements**:
  - Live region (`aria-live="polite"`) for session capacity updates
  - Status announcements when adding/removing sessions from schedule
- **Color Contrast**: WCAG 2.1 AA compliance (4.5:1 for text, 3:1 for UI components)
- **Focus Indicators**: 2px solid outline on all focused elements
- **Alt Text**: All speaker photos have descriptive alt text (`alt="Sara Kim, Docker Inc."`)
- **Semantic HTML**: Proper heading hierarchy (h1 for event title, h2 for sections, h3 for subsections)
- **Skip Links**: "Skip to session schedule" and "Skip to speaker lineup" links at top

---

## State Management

### Local Component State

- `selectedSessionsLocal`: Array of session IDs currently in user's schedule (synced with API)
- `filterState`: Current filter settings (All Sessions, My Schedule, Morning, Afternoon)
- `expandedSections`: Boolean map of which sections are expanded (mobile only)
- `shareModalOpen`: Boolean for share modal visibility
- `qrCodeModalOpen`: Boolean for QR code modal visibility

### Global State (Zustand Store)

- `currentEvent`: Complete event object from API
- `userRegistration`: User's registration details including selectedSessions[]
- `sessionCapacities`: Real-time session capacity data from WebSocket
- `userBookmarks`: Array of bookmarked content IDs

### Server State (React Query)

- `useEvent(eventId)`: Event details with 5-minute cache
- `useEventSessions(eventId)`: Sessions with 30-second refetch interval
- `useUserRegistration(registrationId)`: User registration with optimistic updates
- `useRelatedEvents(eventId)`: Related events with 15-minute cache

### Real-Time Updates

- **WebSocket Connection**: `/ws/events/{eventId}/capacity`
  - Updates session capacity every 30 seconds
  - Triggers UI update for capacity indicators
  - No page refresh required

---

## Form Validation Rules

_No forms on this screen - all actions are single-click or selection-based_

---

## Edge Cases & Error Handling

- **Empty State (Not Registered)**:
  - Show "You're not registered for this event" banner
  - Replace "My Participation" section with [Register Now] CTA
  - Hide [View My Schedule] and [View QR Code] buttons

- **Loading State**:
  - Display skeleton screens for session list, speaker lineup
  - Show loading spinner for event header
  - Disable interactive elements until data loaded

- **Error State (API Failure)**:
  - Show error message: "Unable to load event details. Please try again."
  - Provide [Retry] button to refetch data
  - Cache last successful load if available

- **Session Capacity Full**:
  - Disable [+ Add to Schedule] button
  - Show "Session Full" badge
  - Offer "Join Waitlist" option if available

- **Registration Deadline Passed**:
  - Show "Registration Closed" banner
  - Disable session selection if not already registered
  - Show alternative: "Browse upcoming events"

- **Event Date Passed**:
  - Show "Event Completed" badge
  - Replace [Add to Calendar] with [View Recordings] button
  - Link to event archive and content library

- **Network Offline (PWA)**:
  - Show "You're offline" banner
  - Display cached event data
  - Disable actions requiring network (share, calendar sync)
  - Queue session selections to sync when online

- **Permission Denied (Calendar, Wallet)**:
  - Show fallback: "Download calendar file manually"
  - Provide alternative wallet pass download link
  - Guide user to enable permissions

- **No Related Events Found**:
  - Hide "Related Content" section if no matches
  - Show alternative: "Explore all past events" link

---

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-10-04 | 1.0 | Initial wireframe creation for Event Details Page (Attendee View) | ux-expert |

---

## Review Notes

### Stakeholder Feedback

_To be added during review_

### Design Iterations

_To be documented as design evolves_

### Open Questions

1. **Session Waitlist**: Should we implement waitlist functionality for full sessions in MVP?
   - Decision needed from product team

2. **Calendar Sync**: Support two-way calendar sync or one-way export only?
   - Technical feasibility assessment needed

3. **Wallet Pass Expiry**: How long should event passes remain valid post-event?
   - Business rule clarification required

4. **Related Events Algorithm**: What criteria determine "related" events (topics, speakers, both)?
   - ML/algorithm specification needed
