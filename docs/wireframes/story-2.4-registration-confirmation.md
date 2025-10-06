# Story 2.4: Registration Confirmation Page - Wireframe

**Story**: Epic 2, Story 4 - Event Registration Flow (Confirmation)
**Screen**: Registration Confirmation Page
**User Role**: Attendee
**Related FR**: FR6 (Event Registration), FR7 (Email Notifications), FR14 (Personal Engagement)

---

## Visual Wireframe

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ BATbern                                         Upcoming Events  [EN|DE] │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│                                                                             │
│                        ✅ REGISTRATION CONFIRMED!                           │
│                                                                             │
│                 You're all set for the Spring Conference 2025              │
│                                                                             │
│              ⏱️ Event starts in 45 days, 3 hours, 12 minutes               │
│                                                                             │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                                                             │
│  ┌─── YOUR REGISTRATION DETAILS ────────────────────────────────────────┐  │
│  │                                                                        │  │
│  │  Event:          Spring Conference 2025: Cloud Native Architecture   │  │
│  │  Date:           May 15, 2025                                         │  │
│  │  Time:           08:30 - 18:00                                        │  │
│  │  Location:       Kursaal Bern, Kornhausstrasse 3, 3013 Bern          │  │
│  │                                                                        │  │
│  │  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │  │
│  │                                                                        │  │
│  │  Attendee:       John Smith                                           │  │
│  │  Email:          john.smith@company.ch                                │  │
│  │  Company:        TechCorp AG                                           │  │
│  │                                                                        │  │
│  │  Confirmation:   BAT-2025-1234                                        │  │
│  │                  (Reference number for your records)                  │  │
│  │                                                                        │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌─── WHAT'S NEXT? ──────────────────────────────────────────────────────┐  │
│  │                                                                        │  │
│  │  1. 📧 Check Your Email                                               │  │
│  │     A confirmation email has been sent to john.smith@company.ch       │  │
│  │     with your ticket and event details.                               │  │
│  │                                                                        │  │
│  │  2. 📅 Add to Calendar                                                │  │
│  │     [📅 Add to Google Calendar]  [📅 Download .ics file]            │  │
│  │                                                                        │  │
│  │  3. 🎟️ Download Your Ticket                                          │  │
│  │     [📄 Download PDF Ticket]                                          │  │
│  │     (Bring this or show the QR code on your phone)                   │  │
│  │                                                                        │  │
│  │  4. 🔔 Event Reminders                                                │  │
│  │     You'll receive reminders 1 week and 1 day before the event       │  │
│  │                                                                        │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌─── EVENT INFORMATION ─────────────────────────────────────────────────┐  │
│  │                                                                        │  │
│  │  📍 Getting There                                                      │  │
│  │     Kursaal Bern is in the city center, 5 min walk from main station │  │
│  │     [Get Directions]                                                   │  │
│  │                                                                        │  │
│  │  🅿️ Parking                                                           │  │
│  │     Public parking available at nearby Casino Parking                 │  │
│  │                                                                        │  │
│  │  🍽️ Catering                                                          │  │
│  │     Lunch and refreshments included (free of charge)                  │  │
│  │     Please inform us of dietary requirements: [Update Preferences]    │  │
│  │                                                                        │  │
│  │  📱 Wi-Fi                                                              │  │
│  │     Free Wi-Fi will be available throughout the venue                 │  │
│  │                                                                        │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌─── QUICK LINKS ───────────────────────────────────────────────────────┐  │
│  │                                                                        │  │
│  │  🎤 [View Speaker Lineup]        📋 [View Full Agenda]               │  │
│  │  🗺️ [Explore Past Events]        📧 [Update My Preferences]         │  │
│  │                                                                        │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌─── SHARE WITH COLLEAGUES ─────────────────────────────────────────────┐  │
│  │                                                                        │  │
│  │  [🔗 LinkedIn]  [🐦 Twitter/X]  [📧 Email]                           │  │
│  │  Share: "I'm attending Spring Conference 2025 on May 15 - Join me!"  │  │
│  │                                                                        │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌─── NEED TO MAKE CHANGES? ────────────────────────────────────────────┐  │
│  │                                                                        │  │
│  │  Need to update your registration or cancel?                          │  │
│  │  [Edit Registration]  [Cancel Registration]                           │  │
│  │                                                                        │  │
│  │  Questions? Contact us at events@batbern.ch                           │  │
│  │                                                                        │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│                      [← Back to Event Details]                             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Interactive Elements

- **⏱️ Countdown Timer**: Live countdown to event start time
  - Updates every minute showing days, hours, minutes remaining
  - Changes to "Event in progress" when started
  - Changes to "Event has ended" after event completion

- **📅 Add to Calendar buttons**: Two options for calendar integration (works for unauthenticated users)
  - "Add to Google Calendar" - Direct Google Calendar integration
  - "Download .ics file" - Universal calendar file for Outlook, Apple Calendar, etc.
  - No authentication required - available to all users

- **📄 Download PDF Ticket button**: Generates and downloads personalized ticket with QR code

- **🔗 Social Sharing Buttons**: Share registration with colleagues
  - **LinkedIn**: Opens LinkedIn share dialog with pre-filled message
  - **Twitter/X**: Opens Twitter/X share dialog with event details
  - **Email**: Opens email client with shareable event information
  - Share message: "I'm attending {Event Name} on {Date} - Join me!"

- **[Get Directions] link**: Opens map service (Google Maps/Apple Maps) with venue location

- **[Update Preferences] link**: Opens modal to specify dietary requirements or accessibility needs

- **[View Speaker Lineup]**: Navigates to current event landing page, speaker section

- **[View Full Agenda]**: Downloads or displays full agenda PDF

- **[Explore Past Events]**: Navigates to historical archive

- **[Update My Preferences]**: Opens user settings for newsletter and notification preferences

- **[Edit Registration] button**: Navigates back to registration form with pre-filled data for modifications

- **[Cancel Registration] button**: Opens confirmation modal before canceling registration (free events only - no refunds)

- **Email link (events@batbern.ch)**: Opens default email client with support address

---

## Functional Requirements Met

- **FR6 (Event Registration)**: Confirms successful registration completion with clear confirmation message and reference number

- **FR7 (Email Notifications)**:
  - Confirms confirmation email has been sent
  - Shows event reminder schedule (1 week and 1 day before)
  - Provides contact email for support

- **FR14 (Personal Engagement)**:
  - Ticket download for personal records
  - Calendar integration for personal scheduling
  - Preference management link for future engagement
  - Newsletter subscription confirmation (if opted in during registration)

- **NFR1 (Responsive Design)**: Layout optimized for mobile, tablet, and desktop viewing

---

## Technical Notes

- **Confirmation page must be accessible only via successful registration** - Direct URL access should validate confirmation code
- **QR Code generation** required for PDF ticket (includes registration ID)
- **Calendar file (.ics) generation** with event details, location, and reminders (no authentication required)
- **Google Calendar API integration** for direct calendar add (no authentication required)
- **Countdown Timer** - JavaScript-based live countdown updated every minute, shows days/hours/minutes until event
- **Social Sharing** - Pre-filled share messages for LinkedIn, Twitter/X, and Email with event details and registration context
- **PDF generation service** for ticket with QR code and event branding
- **Email verification** that confirmation email was sent (background job status check)
- **Analytics tracking** for conversion funnel completion
- **Session storage** to prevent duplicate registrations via back button
- **Deep linking** support for mobile calendar apps
- **Free events only** - No payment processing, no refund logic, cancellation is simple unregistration

---

## API Requirements

### Initial Page Load APIs

When the Registration Confirmation Page loads, the following APIs are called:

1. **GET /api/v1/events/{eventId}/registrations/{confirmationCode}**
   - Query params: None (confirmation code in URL path)
   - Returns: Complete registration details (registrationId, attendeeInfo, eventInfo with embedded venue, ticketUrl, emailStatus)
   - Used for: Validate confirmation code exists, display registration details, show attendee info, verify email sent, enable ticket download button
   - Consolidation: Uses nested registrations endpoint under Events API (Story 1.17)

2. **GET /api/v1/events/{eventId}?include=venue,sessions,speakers**
   - Query params: `include=venue,sessions,speakers` (to embed venue and session data)
   - Returns: Event details with embedded venue (title, eventDate, startTime, endTime, description, venue.name, venue.fullAddress, venue.coordinates, venue.parkingInfo, venue.wifiInfo, venue.accessibilityInfo)
   - Used for: Display event information section, populate calendar invite data, show location details, generate directions link, display "Getting There" information
   - Consolidation: Single endpoint replaces separate event + venue calls (Story 1.17)

3. **GET /api/v1/notifications/subscriptions?email={email}&type=newsletter**
   - Query params: `email` (from registration), `type=newsletter`
   - Returns: Newsletter subscription status (isSubscribed, subscriptionDate, frequency, preferences)
   - Used for: Show newsletter subscription confirmation if user opted in during registration
   - Consolidation: Uses consolidated Notifications API (Story 1.26)

### User Action APIs

4. **GET /api/v1/events/{eventId}/registrations/{registrationId}/ticket.pdf**
   - Triggered by: User clicks [📄 Download PDF Ticket]
   - Returns: PDF file with ticket, QR code, event details
   - Used for: Downloads personalized ticket PDF with QR code containing registrationId
   - Consolidation: Nested under Events/Registrations API (Story 1.17)

5. **GET /api/v1/events/{eventId}/calendar.ics?registrationId={registrationId}**
   - Triggered by: User clicks [📅 Download .ics file]
   - Query params: `registrationId` (to personalize)
   - Returns: .ics calendar file with event details and venue information
   - Used for: Downloads calendar file for importing to calendar applications
   - Consolidation: Nested under Events API, includes venue data (Story 1.17)

6. **POST /api/v1/integrations/calendar/google**
   - Triggered by: User clicks [📅 Add to Google Calendar]
   - Payload: `{ eventId: uuid, registrationId: uuid, attendeeEmail: string }`
   - Returns: Google Calendar OAuth redirect URL or success confirmation
   - Used for: Initiates Google Calendar API flow to add event directly
   - Consolidation: Uses consolidated Integrations API (Story 1.27)

7. **PATCH /api/v1/events/{eventId}/registrations/{registrationId}**
   - Triggered by: User clicks [Update Preferences] in catering section
   - Payload: `{ dietaryRequirements: string[], accessibilityNeeds: string }`
   - Returns: Updated registration confirmation
   - Used for: Opens modal to update dietary requirements and accessibility needs
   - Consolidation: Uses PATCH for partial updates (Story 1.17)

8. **GET /api/v1/events/{eventId}/registrations/{registrationId}**
   - Triggered by: User clicks [Edit Registration]
   - Returns: Current registration data for editing
   - Used for: Fetches registration data to pre-fill edit form
   - Consolidation: Nested under Events API (Story 1.17)

9. **DELETE /api/v1/events/{eventId}/registrations/{registrationId}**
    - Triggered by: User confirms cancellation via [Cancel Registration]
    - Returns: Cancellation confirmation (canceledAt, cancellationMessage)
    - Response example: `{ "success": true, "canceledAt": "2025-03-15T10:30:00Z", "message": "Sorry to see you go! We've sent you an email confirmation." }`
    - Used for: Cancels registration (simple unregistration), sends cancellation email with "sorry" message and event details, shows cancellation confirmation
    - Note: Free events only - no refunds, no payment processing
    - Consolidation: Nested under Events API (Story 1.17)

10. **GET /api/v1/events/{eventId}/agenda.pdf**
    - Triggered by: User clicks [View Full Agenda]
    - Returns: PDF file with complete event agenda
    - Used for: Downloads full agenda document
    - Consolidation: Nested under Events API (Story 1.17)

11. **PUT /api/v1/attendees/me**
    - Triggered by: User clicks [Update My Preferences]
    - Payload: `{ email: string, preferences: { notifications: object, newsletter: { frequency: string } } }`
    - Returns: Updated attendee profile with preferences
    - Used for: Opens modal or navigates to preferences page for managing communication settings
    - Consolidation: Uses consolidated Attendees API with embedded preferences (Story 1.25)

12. **POST /api/v1/content/share**
    - Triggered by: User clicks social sharing button (LinkedIn, Twitter/X, or Email)
    - Payload: `{ platform: "linkedin" | "twitter" | "email", contentType: "event_registration", eventId: uuid, registrationId: uuid, shareMessage: string }`
    - Returns: Share URL or success confirmation
    - Used for: Generates shareable link and pre-filled message for social platforms
    - LinkedIn: Opens LinkedIn share dialog with event URL and message
    - Twitter/X: Opens Twitter/X share dialog with event hashtag and message
    - Email: Opens mailto: link with pre-filled subject and body
    - Consolidation: Uses consolidated Content API for sharing features (Story 1.20)

---

## Navigation Map

### Primary Navigation Actions

1. **[← Back to Event Details] link** → Navigate to `Current Event Landing Page` (story-2.4-current-event-landing.md)
   - Target: Current event landing page
   - Context: eventId
   - Behavior: Full page navigation

2. **[📅 Add to Google Calendar] button** → External service integration
   - Initiates Google Calendar OAuth flow
   - Context: Event details, registration ID
   - Behavior: Opens OAuth popup or redirects

3. **[📅 Download .ics file] button** → File download
   - Downloads .ics calendar file
   - Context: Event details, registration ID
   - Behavior: Browser download

4. **[📄 Download PDF Ticket] button** → File download
   - Downloads personalized PDF ticket
   - Context: Registration ID, confirmation code, QR code
   - Behavior: Browser download

5. **[Get Directions] link** → External map service
   - Opens Google Maps/Apple Maps with venue address
   - Context: Venue coordinates and address
   - Behavior: Opens in new tab or native map app (mobile)

### Secondary Navigation (Data Interactions)

6. **[View Speaker Lineup] link** → Navigate to `Current Event Landing Page` (speaker section)
   - Target: Current event landing page, scrolled to speakers section
   - Context: eventId, anchor: #speakers
   - Behavior: Full page navigation with scroll

7. **[View Full Agenda] link** → Download agenda PDF
   - Downloads complete event agenda
   - Context: eventId
   - Behavior: Browser download

8. **[Explore Past Events] link** → Navigate to `Historical Archive` (story-1.18-historical-archive.md)
   - Target: Historical archive browse page
   - Context: None (general archive entry)
   - Behavior: Full page navigation

9. **[Update My Preferences] link** → Navigate to `User Settings` (TBD: User settings wireframe)
   - Target: User settings page or modal
   - Context: User email, current preferences
   - Behavior: Modal or full page navigation

10. **[Edit Registration] button** → Navigate to `Event Registration Form` (story-2.4-event-registration.md)
    - Target: Registration form with edit mode
    - Context: registrationId, pre-filled registration data
    - Behavior: Full page navigation to registration form
    - Validation: Registration must not be canceled

11. **Email link (events@batbern.ch)** → External email client
    - Opens default email client with support address
    - Context: Pre-filled subject with confirmation code
    - Behavior: mailto: link

### Modal Interactions

12. **[Update Preferences] (Catering section)** → Opens `Dietary Requirements Modal`
    - Modal with form for dietary requirements and accessibility needs
    - Context: Current dietary requirements (if any)
    - Behavior: Modal overlay on same screen
    - Save action: Updates registration, closes modal, shows success message

13. **[Social Sharing buttons] (LinkedIn, Twitter/X, Email)** → Opens social sharing dialog
    - LinkedIn: Opens LinkedIn share dialog with pre-filled message and event link
    - Twitter/X: Opens Twitter/X share dialog with pre-filled message and event hashtag
    - Email: Opens mailto: link with event details and registration info
    - Context: Event details, share message
    - Behavior: Opens external platform dialog or email client

14. **[Cancel Registration] button** → Opens `Cancel Confirmation Modal`
    - Modal with cancellation warning and "Sorry to see you go" message
    - Context: Registration details, free event notice (no refunds)
    - Behavior: Modal overlay on same screen
    - Confirm action: Cancels registration (unregisters), sends cancellation email, shows "Sorry to see you go" confirmation
    - Cancel action: Closes modal, stays on confirmation page

### Event-Driven Navigation

15. **Email not sent (failed)** → Shows `Email Retry Banner`
    - Inline banner at top of page with [Resend Email] button
    - Context: registrationId, email address
    - Behavior: Banner shown only if email delivery failed
    - Action: Triggers email resend API

16. **Registration already canceled** → Redirect to `Cancellation Page`
    - If user tries to access confirmation page for canceled registration
    - Target: Cancellation confirmation page
    - Behavior: Automatic redirect with cancellation details

### Error States & Redirects

17. **Invalid confirmation code** → Navigate to `404 or Error Page`
    - If confirmation code doesn't exist or is invalid
    - Target: Error page with message and link back to events
    - Behavior: HTTP 404 response

18. **Registration expired** → Shows `Expired Registration Page`
    - If registration is too old or event has passed
    - Target: Expired registration page with archive link
    - Behavior: Shows event details in archived state

---

## Responsive Design Considerations

### Mobile Layout Changes
- **Hero message** remains prominent at top
- **Registration details card** becomes full-width
- **"What's Next?" steps** stack vertically with larger tap targets
- **Calendar buttons** stack vertically for easier mobile interaction
- **Quick Links** displayed as full-width buttons instead of side-by-side
- **Edit/Cancel buttons** placed in sticky footer for easy access

### Tablet Layout Changes
- **Two-column layout** for "What's Next?" and "Event Information" sections
- **Quick Links** remain side-by-side with adequate spacing
- **Calendar buttons** side-by-side with sufficient touch target size

### Mobile-Specific Interactions
- **Tap on venue address** → Opens native map app directly (not browser)
- **Tap on email address** → Opens native email app with pre-filled subject
- **QR code** in downloaded ticket optimized for mobile screen display
- **Share button** appears on mobile for native sharing (event details, ticket)
- **Add to Calendar** detects mobile OS and suggests native calendar app

---

## Accessibility Notes

- **Success confirmation** announced via screen reader with ARIA live region
- **All interactive buttons** have clear focus indicators and keyboard navigation
- **Color contrast** for all text meets WCAG 2.1 AA standards (green checkmark has sufficient contrast)
- **Confirmation number** readable and selectable for copy-paste
- **Links vs buttons** semantically correct (navigation = links, actions = buttons)
- **Modal dialogs** trap focus and can be closed via ESC key
- **PDF ticket** includes alt text for QR code and proper heading structure
- **Calendar files** include location and description for screen reader users
- **Error messages** clearly announced and associated with form fields via aria-describedby

---

## State Management

### Local Component State
- `isDownloadingTicket: boolean` - Loading state for ticket download
- `isAddingToCalendar: boolean` - Loading state for calendar integration
- `showDietaryModal: boolean` - Modal visibility for dietary preferences
- `showCancelModal: boolean` - Modal visibility for cancellation confirmation
- `emailResendSuccess: boolean` - Success state for email resend action
- `countdownText: string` - Live countdown timer display (e.g., "45 days, 3 hours, 12 minutes")
- `shareInProgress: boolean` - Loading state for social sharing action

### Global State (Zustand Store)
- `auth.user` - Current user information (if authenticated)
- `registration.confirmationCode` - Current registration confirmation code
- `registration.details` - Full registration details for this confirmation

### Server State (React Query)
- **registrationDetails** - Cached registration data, invalidated on edit/cancel
- **eventDetails** - Cached event information, 5-minute stale time
- **venueInfo** - Cached venue details, 10-minute stale time
- **newsletterStatus** - Cached subscription status, manual invalidation

### Real-Time Updates
- **Countdown Timer** - JavaScript interval updates every 60 seconds to refresh countdown display
- **Email delivery status** - Polling every 5 seconds for 30 seconds to check email delivery
- **Registration status** - WebSocket connection to detect cancellation or edits from other devices (optional in MVP)
- **Event updates** - Subscribe to event changes (cancellation, date change) via WebSocket (optional in MVP)

---

## Form Validation Rules

### Modal: Dietary Requirements Form

#### Field-Level Validations
- **Dietary Requirements** (multi-select checkboxes): Optional, no validation required
- **Additional Notes** (textarea): Optional, max 500 characters
- **Accessibility Needs** (textarea): Optional, max 500 characters

#### Form-Level Validations
- None - all fields optional, submission always allowed

---

## Edge Cases & Error Handling

- **Empty State (Email Failed)**: Show warning banner at top: "⚠️ Email delivery failed. [Resend Confirmation Email]"

- **Loading State (Page Load)**: Display skeleton screens for registration details and event information while fetching data

- **Error State (Invalid Confirmation Code)**: Show error page: "This confirmation code is invalid or has expired. [Back to Events]"

- **Error State (Network Failure)**: Show error message with [Retry] button for failed API calls

- **Ticket Download Failure**: Show error toast: "Unable to download ticket. Please try again or contact support."

- **Calendar Integration Failure**: Show error toast: "Unable to add to calendar. Please download the .ics file instead."

- **Already Canceled**: Redirect to cancellation page with message: "This registration has been canceled on {date}."

- **Event Canceled**: Show warning banner: "⚠️ This event has been canceled. You will receive a notification email with details."

- **Countdown Timer Complete**: When event starts, countdown changes to: "🎉 Event in progress!" and after event ends: "Event has ended. Thank you for attending!"

- **Multiple Registrations Detected**: If user tries to register again with different email, show warning: "You're already registered with {email}."

- **Session Expired**: If confirmation code is in URL but session expired, re-fetch registration details via API

- **Offline Mode (PWA)**: Show offline banner: "You're offline. Some features may not be available until you reconnect."

---

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-10-02 | 1.0 | Initial wireframe creation for Registration Confirmation Page | Sally (UX Expert) |

---

## Review Notes

### Stakeholder Feedback
- ✅ Dietary preferences workflow approved
- ✅ Cancellation policy confirmed: Free events only, simple unregistration with "Sorry to see you go" message
- ✅ Social sharing buttons approved: LinkedIn, Twitter/X, Email
- ✅ Countdown timer approved: Live countdown until event starts

### Design Iterations
- Version 1.0: Initial creation based on registration flow (story-2.4-event-registration.md)

### Open Questions

All open questions have been resolved:

1. ✅ **Calendar Integration Authentication**: Should calendar integration require authentication or work for unauthenticated users?
   - **DECISION: Work for unauthenticated users**
   - Add to Calendar (.ics download) available to all users
   - No login required to download calendar file
   - Confirmation email includes calendar attachment regardless of auth status

2. ✅ **Cancellation Policy**: What is the cancellation policy and refund process (if free event, what to communicate)?
   - **DECISION: Free events only - simple unregistration**
   - All BATbern events are free (no payment, no refunds)
   - Cancellation simply unregisters user from event
   - Show "Sorry to see you go" message on cancellation
   - Send cancellation confirmation email with event details (in case they change their mind)
   - No waitlist promotion on cancellation (deferred to future enhancement)

3. ✅ **Social Sharing Buttons**: Should we add social sharing buttons for the event?
   - **DECISION: Yes** - Add social sharing buttons
   - Share on: LinkedIn, Twitter/X, Email
   - Share message: "I'm attending {Event Name} on {Date} - Join me!"
   - Link to public event page (if published)

4. ✅ **Waitlist Position Display**: Do we need to display waitlist position if applicable?
   - **DECISION: No** - Not in MVP scope
   - Waitlist functionality deferred to future enhancement
   - All registrations are confirmed (no capacity limits in MVP)

5. ✅ **Event Countdown Timer**: Should there be a countdown timer until the event date?
   - **DECISION: Yes** - Add countdown timer
   - Display: "Event starts in X days, Y hours, Z minutes"
   - Update dynamically (live countdown)
   - Show until event start time
   - After event starts: Show "Event in progress" or "Event has ended"
