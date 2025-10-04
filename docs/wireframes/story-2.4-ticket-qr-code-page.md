# Ticket/QR Code Page - Wireframe

**Story**: Epic 2, Story 2.4 - Event Registration Flow (Ticket Display)
**Screen**: Ticket/QR Code Page (Mobile-First Event Access)
**User Role**: Attendee (Authenticated)
**Related FR**: FR6 (Event Access & Registration), FR15 (Mobile-Optimized Experience), FR14 (Personal Engagement)

---

## Visual Wireframe

### Mobile View (Primary Interface)

```
┌─────────────────────────────────────────┐
│ ✕               MY TICKET               │
├─────────────────────────────────────────┤
│                                         │
│     🎟️ SPRING CONFERENCE 2025          │
│     Cloud Native Architecture           │
│                                         │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                         │
│         📅 MAY 15, 2025                 │
│         ⏰ 08:30 - 18:00                │
│         📍 KURSAAL BERN                 │
│                                         │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                         │
│    ┌───────────────────────────────┐   │
│    │                               │   │
│    │      ████████████████         │   │
│    │      ██ QR CODE ████          │   │
│    │      ████████████████         │   │
│    │      ████████████████         │   │
│    │      ████████████████         │   │
│    │                               │   │
│    │   Scan at event entrance      │   │
│    │                               │   │
│    └───────────────────────────────┘   │
│                                         │
│         ✅ REGISTRATION CONFIRMED       │
│                                         │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                         │
│  👤 ATTENDEE INFORMATION                │
│                                         │
│  Name:     John Smith                   │
│  Email:    john.smith@company.ch        │
│  Company:  TechCorp AG                  │
│                                         │
│  Ticket ID: BAT-2025-1234               │
│  Issued:    March 1, 2025 14:30         │
│                                         │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                         │
│  📱 QUICK ACTIONS                       │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  🍎 Add to Apple Wallet         │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  🤖 Add to Google Wallet        │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  🖨️ Print Ticket                 │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  💾 Download PDF                │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  📤 Share Ticket                │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                         │
│  ℹ️ CHECK-IN INSTRUCTIONS               │
│                                         │
│  • Arrive 15 minutes early              │
│  • Have this QR code ready to scan      │
│  • Check-in desk opens at 08:00         │
│  • Name tag will be provided            │
│                                         │
│  📍 [Get Directions to Venue]           │
│  📋 [View Event Details]                │
│  🎤 [View Speaker Lineup]               │
│                                         │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                         │
│  ⚙️ MANAGE TICKET                       │
│                                         │
│  [✏️ Update Registration]               │
│  [🔄 Transfer Ticket]                   │
│  [❌ Cancel Registration]               │
│                                         │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                         │
│  🔒 This is your personal ticket.       │
│      Do not share publicly.             │
│                                         │
│  Need help? events@batbern.ch           │
│                                         │
└─────────────────────────────────────────┘
```

### Desktop View

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ BATbern                              Upcoming Events  My Tickets  [👤 John] │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │                                                                         │ │
│  │                            🎟️ YOUR EVENT TICKET                        │ │
│  │                                                                         │ │
│  │           Spring Conference 2025: Cloud Native Architecture            │ │
│  │                                                                         │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
│  ┌────── TICKET ──────────┬────── ATTENDEE INFO ─────────────────────────┐ │
│  │                         │                                              │ │
│  │  📅 MAY 15, 2025        │  👤 Name:     John Smith                     │ │
│  │  ⏰ 08:30 - 18:00       │  📧 Email:    john.smith@company.ch          │ │
│  │  📍 Kursaal Bern        │  🏢 Company:  TechCorp AG                    │ │
│  │  Kornhausstrasse 3      │  🎫 Ticket:   BAT-2025-1234                  │ │
│  │  3013 Bern              │  📅 Issued:   March 1, 2025 14:30            │ │
│  │                         │                                              │ │
│  │                         │  ✅ REGISTRATION CONFIRMED                   │ │
│  │  ┌─────────────────┐   │                                              │ │
│  │  │                 │   │  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │ │
│  │  │   ████████      │   │                                              │ │
│  │  │   ████████      │   │  📱 SAVE TO MOBILE WALLET                    │ │
│  │  │   ████████      │   │                                              │ │
│  │  │   ████████      │   │  [🍎 Apple Wallet]  [🤖 Google Wallet]       │ │
│  │  │                 │   │                                              │ │
│  │  │ Scan at entrance│   │  💾 DOWNLOAD & PRINT                         │ │
│  │  │                 │   │                                              │ │
│  │  └─────────────────┘   │  [💾 Download PDF]  [🖨️ Print Ticket]        │ │
│  │                         │                                              │ │
│  │  BAT-2025-1234          │  📤 SHARE                                    │ │
│  │                         │                                              │ │
│  │                         │  [📧 Email]  [📋 Copy Link]                  │ │
│  │                         │                                              │ │
│  └─────────────────────────┴──────────────────────────────────────────────┘ │
│                                                                             │
│  ┌────── CHECK-IN INSTRUCTIONS ──────┬────── QUICK LINKS ────────────────┐ │
│  │                                    │                                   │ │
│  │  ✓ Arrive 15 minutes early         │  📍 Get Directions to Venue       │ │
│  │  ✓ Have QR code ready to scan      │  📋 View Full Event Details       │ │
│  │  ✓ Check-in desk opens at 08:00    │  🎤 View Speaker Lineup           │ │
│  │  ✓ Name tag will be provided       │  📅 Add to Calendar               │ │
│  │  ✓ Lunch & refreshments included   │  👥 See Who's Attending           │ │
│  │                                    │                                   │ │
│  └────────────────────────────────────┴───────────────────────────────────┘ │
│                                                                             │
│  ┌────── MANAGE TICKET ──────────────────────────────────────────────────┐ │
│  │                                                                         │ │
│  │  [✏️ Update Registration]  [🔄 Transfer Ticket]  [❌ Cancel Registration]│ │
│  │                                                                         │ │
│  │  🔒 This is your personal ticket. Keep it secure and do not share     │ │
│  │     the QR code publicly to prevent unauthorized access.               │ │
│  │                                                                         │ │
│  │  Need help? Contact events@batbern.ch                                  │ │
│  │                                                                         │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Interactive Elements

### QR Code Display
- **Large QR Code**: Center-aligned, high-contrast QR code (300x300px mobile, 400x400px desktop)
  - Encodes: `{"registrationId": "uuid", "eventId": "uuid", "attendeeId": "uuid", "checksum": "hash"}`
  - High error correction level (30% redundancy)
  - Scannable from 1 meter distance
  - Auto-adjusts brightness for optimal scanning
- **Tap to Enlarge** (Mobile): Taps QR code to view full-screen for easier scanning
- **Auto-Refresh**: QR code regenerates on page load with timestamp to prevent screenshot reuse

### Mobile Wallet Integration
- **🍎 Add to Apple Wallet**: Generates Apple Wallet pass (.pkpass file)
  - iOS devices only (button hidden on Android)
  - Pass includes QR code, event details, location with map
  - Lock screen notification 1 hour before event
  - Auto-updates if event details change
- **🤖 Add to Google Wallet**: Generates Google Wallet pass
  - Android devices only (button hidden on iOS)
  - Pass includes QR code, event details, location
  - Notification before event

### Download & Print Actions
- **💾 Download PDF**: Downloads printable ticket PDF
  - Includes QR code, event details, check-in instructions
  - Optimized for A4 and Letter paper sizes
  - Grayscale-friendly for B&W printers
- **🖨️ Print Ticket**: Opens browser print dialog
  - Pre-formatted print-friendly view
  - No headers/footers, optimized layout

### Share Ticket
- **📤 Share Ticket**: Opens sharing modal with options:
  - **Email**: Send ticket to another email address (with warning about security)
  - **Copy Link**: Copy secure ticket link to clipboard
  - **Transfer**: Transfer ownership to another person (see Transfer flow below)
- **Security Warning**: "Share carefully - anyone with this link can check in"

### Navigation Links
- **[Get Directions]**: Opens map app (Google Maps/Apple Maps) with venue location
- **[View Event Details]**: Navigate to Event Details page
- **[View Speaker Lineup]**: Navigate to Current Event Landing Page, speaker section
- **[Add to Calendar]**: Downloads .ics calendar file or opens calendar integration

### Ticket Management
- **[Update Registration]**: Navigate to edit registration form
- **[Transfer Ticket]**: Opens transfer modal (see Transfer flow below)
- **[Cancel Registration]**: Opens cancellation confirmation modal

---

## Functional Requirements Met

- **FR6 (Event Access & Registration)**: Provides QR code ticket for event check-in and access verification
- **FR15 (Mobile-Optimized Experience)**: Mobile-first design with offline-capable PWA, wallet integration
- **FR14 (Personal Engagement)**: Personal ticket with attendee details, save to wallet, calendar integration
- **NFR1 (Responsive Design)**: Optimized for mobile devices (primary use case) with desktop fallback

---

## Technical Notes

### QR Code Generation & Security
- **Format**: QR Code encoding JSON with registration ID, event ID, attendee ID, timestamp, HMAC signature
  ```json
  {
    "registrationId": "reg-uuid-1234",
    "eventId": "event-uuid-5678",
    "attendeeId": "user-uuid-9012",
    "timestamp": "2025-05-15T08:30:00Z",
    "checksum": "sha256-hmac-signature"
  }
  ```
- **Security**: HMAC signature prevents QR code forgery, timestamp prevents replay attacks
- **Expiry**: QR code valid for 24 hours before event, refreshes on page load
- **Validation**: Backend validates signature, checks registration status, prevents duplicate check-ins

### Mobile Wallet Passes
- **Apple Wallet (.pkpass)**:
  - Uses PassKit format with Apple Developer certificate
  - Includes QR code as barcode, event details, logo, colors
  - Location-based notification when near venue
  - Push notifications for event updates
  - Expiry: Auto-removes 7 days after event
- **Google Wallet**:
  - Uses Google Pay API with signed JWT
  - Includes QR code, event details, logo
  - Location-based notification when near venue
  - Auto-updates with push capability
  - Expiry: Auto-removes after event

### Offline Capability (PWA)
- **Service Worker**: Caches ticket page and QR code for offline access
- **Offline Banner**: Shows "Offline mode - ticket cached" when no network
- **Auto-Sync**: Syncs ticket updates when network restored
- **Critical Data Cached**: QR code, event details, attendee info stored in IndexedDB

### PDF Ticket Generation
- **Server-Side Rendering**: Backend generates PDF with QR code, event branding
- **Format**: A4/Letter optimized, grayscale-friendly
- **Content**:
  - Event name, date, time, location with map
  - QR code (center, large size)
  - Attendee details
  - Check-in instructions
  - Footer: BATbern branding, contact info

### Print Optimization
- **CSS Print Styles**: Hide navigation, optimize layout for print
- **Print-Friendly View**: Single-page layout with QR code centered
- **Grayscale Mode**: Ensures QR code readable in B&W

### Ticket Transfer Workflow
- **Transfer Modal**:
  1. Enter recipient email address
  2. Optional message to recipient
  3. Confirm transfer (warning: "You will lose access to this ticket")
- **Backend Process**:
  - Creates new registration for recipient
  - Invalidates current ticket QR code
  - Sends email to recipient with new ticket link
  - Sends confirmation to original attendee
- **Restrictions**: Transfer allowed up to 24 hours before event

---

## API Requirements

### Initial Page Load APIs

1. **GET /api/v1/events/{eventId}/registrations/{registrationId}/ticket**
   - Authorization: Required (authenticated user, must own registration)
   - Path params: `eventId`, `registrationId`
   - Returns: Complete ticket data
     ```json
     {
       "registrationId": "reg-uuid",
       "eventId": "event-uuid",
       "attendeeId": "user-uuid",
       "ticketId": "BAT-2025-1234",
       "qrCodeData": {
         "registrationId": "reg-uuid",
         "eventId": "event-uuid",
         "attendeeId": "user-uuid",
         "timestamp": "2025-05-15T08:30:00Z",
         "checksum": "sha256-hmac"
       },
       "qrCodeImageUrl": "https://cdn.batbern.ch/qr/...",
       "attendeeInfo": {
         "firstName": "John",
         "lastName": "Smith",
         "email": "john.smith@company.ch",
         "company": "TechCorp AG"
       },
       "eventInfo": {
         "title": "Spring Conference 2025: Cloud Native Architecture",
         "eventDate": "2025-05-15T08:30:00Z",
         "endTime": "2025-05-15T18:00:00Z",
         "venue": {
           "name": "Kursaal Bern",
           "address": "Kornhausstrasse 3, 3013 Bern",
           "coordinates": {"lat": 46.9479, "lng": 7.4474}
         }
       },
       "issuedAt": "2025-03-01T14:30:00Z",
       "status": "CONFIRMED",
       "canTransfer": true,
       "canCancel": true,
       "walletPassUrls": {
         "appleWallet": "https://api.batbern.ch/wallet/apple/...",
         "googleWallet": "https://api.batbern.ch/wallet/google/..."
       }
     }
     ```
   - Used for: Display all ticket information, generate QR code, enable wallet integration

### User Action APIs

2. **GET /api/v1/events/{eventId}/registrations/{registrationId}/ticket.pdf**
   - Triggered by: [Download PDF] button
   - Authorization: Required (authenticated user, must own registration)
   - Returns: PDF file download
   - Used for: Downloads printable ticket PDF with QR code

3. **GET /api/v1/events/{eventId}/registrations/{registrationId}/wallet/apple**
   - Triggered by: [Add to Apple Wallet] button
   - Authorization: Required (authenticated user, must own registration)
   - Returns: Apple Wallet pass file (.pkpass)
   - Used for: Downloads Apple Wallet pass with QR code

4. **GET /api/v1/events/{eventId}/registrations/{registrationId}/wallet/google**
   - Triggered by: [Add to Google Wallet] button
   - Authorization: Required (authenticated user, must own registration)
   - Returns: Google Wallet save URL (redirect)
   - Used for: Redirects to Google Wallet save flow

5. **POST /api/v1/events/{eventId}/registrations/{registrationId}/transfer**
   - Triggered by: [Transfer Ticket] → Confirm transfer in modal
   - Authorization: Required (authenticated user, must own registration)
   - Payload:
     ```json
     {
       "recipientEmail": "recipient@example.com",
       "message": "Looking forward to seeing you there!",
       "confirmTransfer": true
     }
     ```
   - Validation:
     - Recipient email: Required, valid email format, not same as current owner
     - Transfer deadline: Must be >24 hours before event
     - Can't transfer already transferred ticket
   - Response:
     ```json
     {
       "transferId": "transfer-uuid",
       "originalRegistrationId": "reg-uuid-old",
       "newRegistrationId": "reg-uuid-new",
       "recipientEmail": "recipient@example.com",
       "transferredAt": "2025-04-01T10:00:00Z",
       "expiresAt": "2025-04-02T10:00:00Z"
     }
     ```
   - Used for: Transfer ticket ownership to another person, invalidate current ticket

6. **POST /api/v1/events/{eventId}/registrations/{registrationId}/share**
   - Triggered by: [Share Ticket] → Email or Copy Link
   - Authorization: Required (authenticated user, must own registration)
   - Payload:
     ```json
     {
       "method": "email" | "link",
       "recipientEmail": "friend@example.com",
       "message": "Check out my ticket!"
     }
     ```
   - Response:
     ```json
     {
       "shareUrl": "https://batbern.ch/tickets/secure-token-abc123",
       "expiresAt": "2025-05-16T00:00:00Z"
     }
     ```
   - Used for: Generate shareable ticket link (read-only, expires after event)

7. **PUT /api/v1/events/{eventId}/registrations/{registrationId}**
   - Triggered by: [Update Registration] button
   - Authorization: Required (authenticated user, must own registration)
   - Navigates to: Registration edit form (not API call on this screen)
   - Used for: Edit registration details (dietary requirements, company, etc.)

8. **DELETE /api/v1/events/{eventId}/registrations/{registrationId}**
   - Triggered by: [Cancel Registration] → Confirm cancellation in modal
   - Authorization: Required (authenticated user, must own registration)
   - Response:
     ```json
     {
       "registrationId": "reg-uuid",
       "status": "CANCELED",
       "canceledAt": "2025-04-01T15:00:00Z",
       "refundAmount": 0,
       "message": "Sorry to see you go! Registration canceled successfully."
     }
     ```
   - Used for: Cancel registration, invalidate ticket, send cancellation email

9. **POST /api/v1/events/{eventId}/registrations/{registrationId}/refresh-qr**
   - Triggered by: Page load, or manual refresh (every 5 minutes in background)
   - Authorization: Required (authenticated user, must own registration)
   - Returns: New QR code data with updated timestamp and signature
   - Used for: Refresh QR code to prevent screenshot reuse, update timestamp

---

## Navigation Map

### Primary Navigation Actions

1. **✕ Close button** (Mobile) → Navigate back
   - Target: Previous page (Registration Confirmation or Event Details)
   - Action: Browser back navigation
   - Context: No state change

2. **[My Tickets]** (Desktop top nav) → Navigate to Tickets List
   - Target: User dashboard with all tickets
   - Action: Shows all user's event tickets
   - Context: User ID

3. **[Get Directions to Venue]** → External map service
   - Opens: Google Maps (Android/Web) or Apple Maps (iOS)
   - Context: Venue coordinates and address
   - Behavior: Opens in new tab or native map app

4. **[View Event Details]** → Navigate to Event Details page
   - Target: Event Details - Attendee View (story-5.2-event-details-attendee-view.md)
   - Context: eventId
   - Behavior: Full page navigation

5. **[View Speaker Lineup]** → Navigate to Current Event Landing Page
   - Target: Current Event Landing Page, speaker section
   - Context: eventId, anchor #speakers
   - Behavior: Full page navigation with scroll

### Download & Wallet Actions

6. **[Download PDF]** → File download
   - Action: Downloads PDF ticket file
   - Filename: `BATbern-Ticket-{EventName}-{Date}.pdf`
   - Behavior: Browser download

7. **[Print Ticket]** → Browser print dialog
   - Action: Opens print dialog with optimized print view
   - Behavior: Native browser print

8. **[Add to Apple Wallet]** → Wallet pass download
   - Action: Downloads .pkpass file
   - Behavior: iOS prompts to add to Wallet app
   - Error: Show "Only available on iOS devices" if not iOS

9. **[Add to Google Wallet]** → Redirect to Google Wallet
   - Action: Redirects to Google Wallet save URL
   - Behavior: Opens Google Wallet app or web interface
   - Error: Show "Only available on Android devices" if not Android

### Share Actions

10. **[Share Ticket]** → Opens Share Modal
    - Modal with options:
      - Email ticket to someone
      - Copy secure link
      - Transfer ownership
    - Behavior: Modal overlay on same page
    - No navigation

11. **[Email]** (in Share Modal) → Opens Email Form
    - Modal with email input field
    - Sends read-only ticket link to recipient
    - Warning: "This doesn't transfer ownership"
    - Behavior: Sends email, closes modal, shows success toast

12. **[Copy Link]** (in Share Modal) → Clipboard action
    - Action: Copies secure ticket URL to clipboard
    - Toast: "Link copied! Valid until {event end date}"
    - Behavior: No navigation, closes modal

### Ticket Management

13. **[Update Registration]** → Navigate to Edit Registration
    - Target: Event Registration Form (edit mode)
    - Context: registrationId, pre-filled data
    - Behavior: Full page navigation

14. **[Transfer Ticket]** → Opens Transfer Modal
    - Modal with transfer form:
      - Recipient email input
      - Message field (optional)
      - Warnings about losing access
      - Confirm button
    - Behavior: Modal overlay on same page

15. **Transfer Confirmation** → Processes transfer
    - API call to transfer ticket
    - On success:
      - Shows success message: "Ticket transferred to {email}"
      - Invalidates current ticket (QR code grayed out)
      - Shows "This ticket has been transferred" status
      - Link: "Transfer canceled? Contact support"
    - Behavior: Modal closes, page updates to show transferred state

16. **[Cancel Registration]** → Opens Cancellation Modal
    - Confirmation modal with warnings:
      - "Are you sure you want to cancel?"
      - "You will lose access to this event"
      - "This action cannot be undone"
      - Free event notice: "No refund (free event)"
    - Options: [Keep Ticket] [Cancel Registration]
    - Behavior: Modal overlay

17. **Cancellation Confirmed** → Processes cancellation
    - API call to cancel registration
    - On success:
      - Shows cancellation confirmation
      - Invalidates ticket (QR code removed)
      - Navigate to cancellation confirmation page
    - Behavior: Redirects to confirmation page

### Event-Driven Navigation

18. **Ticket Transferred (by user)** → Show Transferred State
    - Ticket marked as transferred
    - QR code grayed out or removed
    - Message: "This ticket was transferred to {email} on {date}"
    - CTA: [View My Other Tickets]
    - No navigation: State update on same page

19. **Ticket Canceled (by user)** → Show Canceled State
    - Ticket marked as canceled
    - QR code removed
    - Message: "This registration was canceled on {date}"
    - CTA: [Register Again] [View Other Events]
    - No navigation: State update on same page

20. **Event Canceled (by organizer)** → Show Event Canceled State
    - Banner: "⚠️ This event has been canceled"
    - Ticket status: CANCELED
    - Message: "You'll receive a full refund within 5-7 days"
    - CTA: [View Other Events]
    - No navigation: State update on same page

21. **Event Date Changed** → Show Update Notification
    - Banner: "📅 Event date has been updated"
    - Shows old date → new date
    - Message: "Your ticket is still valid. Re-download to update calendar."
    - CTA: [Download Updated Ticket] [Update Calendar]
    - No navigation: State update with notification

### Error States & Redirects

22. **Invalid Ticket Access (401)** → Redirect to Login
    - Condition: Unauthenticated user tries to access ticket
    - Target: Login Screen (story-1.2-login-screen.md)
    - Context: Return URL to this ticket page
    - Behavior: Redirect with "Please sign in to view your ticket"

23. **Unauthorized Ticket Access (403)** → Show Error Page
    - Condition: User tries to access someone else's ticket
    - Display: "You don't have permission to view this ticket"
    - CTA: [View My Tickets]
    - Behavior: Error page, no access granted

24. **Ticket Not Found (404)** → Show Error Page
    - Condition: Invalid registration ID or ticket deleted
    - Display: "Ticket not found or no longer valid"
    - CTA: [View My Tickets] [Browse Events]
    - Behavior: Error page

25. **QR Code Generation Failed** → Show Error State
    - Display: "Unable to generate QR code. Please try again."
    - CTA: [Retry] [Download PDF Instead]
    - Fallback: Show ticket ID as text barcode
    - Behavior: Inline error with retry option

---

## Responsive Design Considerations

### Mobile Layout (<768px) - Primary Interface

- **Full-Screen QR Code**: Tap QR code to view full-screen for easier scanning
- **Stacked Layout**: All elements stack vertically with priority order:
  1. Event title & date
  2. QR code (large, center)
  3. Confirmation status
  4. Attendee info
  5. Quick actions (wallet, download, print)
  6. Check-in instructions
  7. Management options
- **Sticky Header**: Close button (✕) and "MY TICKET" title sticky at top
- **Large Tap Targets**: All buttons 44px minimum height
- **Auto-Brightness**: Screen brightness auto-increases when QR code visible
- **Lock Screen**: PWA shows QR code on lock screen 1 hour before event

### Tablet Layout (768-1024px)

- **2-Column Layout**: QR code on left, attendee info & actions on right
- **Larger QR Code**: 350x350px for easier scanning from distance
- **Side-by-side Actions**: Wallet buttons and download options in rows

### Desktop Layout (>1024px)

- **3-Column Layout**:
  - Left: QR code and ticket ID
  - Center: Attendee info and actions
  - Right: Check-in instructions and quick links
- **Maximum Width**: 1200px container, centered
- **Print Button Prominent**: Desktop users more likely to print

### Mobile-Specific Interactions

- **Tap QR Code**: Opens full-screen QR code view (landscape supported)
- **Brightness Boost**: Auto-increase screen brightness when QR displayed
- **Wallet Integration**: Native iOS/Android wallet integration
- **Haptic Feedback**: Vibration on successful download/save to wallet
- **Lock Screen Widget**: PWA shows QR code on lock screen 1 hour before event
- **Offline Mode**: Full ticket cached for offline access (service worker)
- **Screenshot Detection**: Warning if user takes screenshot (security reminder)

---

## Accessibility Notes

### ARIA Labels & Roles
- **QR Code Image**: `<img alt="QR code for event check-in" role="img">`
- **Ticket Status**: `<div role="status" aria-live="polite">Registration Confirmed</div>`
- **Action Buttons**: Each button has descriptive aria-label
  - `aria-label="Add ticket to Apple Wallet"`
  - `aria-label="Download printable PDF ticket"`
- **Security Notice**: `<aside role="note" aria-label="Security information">`

### Keyboard Navigation
- **Tab Order**: Header → QR code → Action buttons → Links → Management options
- **Enter/Space**: Activate all buttons
- **Escape**: Close modals (share, transfer, cancel)
- **Focus Indicators**: High-contrast 3px blue outline on all interactive elements

### Screen Reader Support
- **QR Code Alternative**: Text-based ticket ID announced for screen reader users
- **Status Announcements**: Registration status, transfer status, cancellation status announced
- **Button Context**: "Download PDF ticket for Spring Conference 2025"
- **Warning Messages**: Security warnings and transfer confirmations announced clearly

### Visual Indicators
- **High Contrast QR Code**: Black on white with 21:1 contrast ratio
- **Status Colors**:
  - Green checkmark for confirmed (with text "Confirmed")
  - Red for canceled (with text "Canceled")
  - Gray for transferred (with text "Transferred")
- **Color + Icon**: All status indicators use both color and icon
- **Large Text**: Event title 24px, ticket details 16px minimum

### Reduced Motion
- **Respect Preferences**: No animations if user prefers reduced motion
- **Static Display**: QR code doesn't animate or pulse
- **Instant Transitions**: Modals appear instantly without slide-in

---

## State Management

### Local Component State
- `qrCodeSize: number` - Dynamically calculated QR code size based on viewport
- `isFullScreenQr: boolean` - Full-screen QR code modal state
- `brightnessBoostActive: boolean` - Screen brightness boost state
- `showShareModal: boolean` - Share modal visibility
- `showTransferModal: boolean` - Transfer modal visibility
- `showCancelModal: boolean` - Cancellation modal visibility
- `transferEmail: string` - Transfer recipient email input
- `transferMessage: string` - Transfer message input
- `isDownloading: boolean` - Download in progress state
- `offlineMode: boolean` - Offline PWA state

### Global State (Zustand Store)
- `auth.user` - Current authenticated user
- `tickets.current` - Current ticket being displayed
- `tickets.list` - List of all user's tickets
- `ui.brightness` - Screen brightness setting

### Server State (React Query)
- **Query Keys**:
  - `['ticket', registrationId]` - Ticket data (5 min stale time)
  - `['event', eventId]` - Event details (5 min stale time)
- **Mutations**:
  - `transferTicket` - Transfer ticket to another person
  - `cancelTicket` - Cancel registration
  - `refreshQrCode` - Refresh QR code with new timestamp

### Real-Time Updates
- **WebSocket Connection**: Subscribe to ticket status changes
  - Event: `TICKET_TRANSFERRED` → Update UI to show transferred state
  - Event: `EVENT_CANCELED` → Update UI to show event canceled
  - Event: `EVENT_UPDATED` → Show notification banner with changes
- **Background Sync**: Refresh QR code every 5 minutes (security)
- **Offline Sync**: Queue transfer/cancel actions when offline, sync when online

### PWA Caching
- **Service Worker**: Caches ticket page HTML, CSS, JS
- **IndexedDB**: Stores ticket data, QR code, event details
- **Cache Strategy**: Network-first with cache fallback
- **Cache Expiry**: Clear cache 24 hours after event

---

## Form Validation Rules

### Transfer Ticket Modal

#### Field-Level Validations
- **Recipient Email**:
  - Required: Cannot be empty
  - Format: Must be valid email address
  - Not self: Cannot transfer to own email
  - Pattern: RFC 5322 email validation
- **Message** (optional):
  - Max Length: 500 characters
  - Sanitization: Strip HTML tags

#### Form-Level Validations
- **Transfer Deadline**: Must be >24 hours before event
  - Error: "Ticket cannot be transferred within 24 hours of event"
- **Already Transferred**: Ticket can only be transferred once
  - Error: "This ticket has already been transferred"
- **Event Ended**: Cannot transfer after event
  - Error: "Cannot transfer ticket for past event"

### Share Ticket Modal

#### Field-Level Validations
- **Recipient Email** (for email share):
  - Required if method is "email"
  - Format: Valid email address
  - Max Length: 254 characters

#### Form-Level Validations
- None - sharing is informational, not ownership transfer

---

## Edge Cases & Error Handling

### Empty States

- **No Ticket Found**:
  - Display: "Ticket not found or you don't have permission to view it"
  - CTA: [View My Tickets] [Browse Events]

- **Ticket List Empty** (for user):
  - Display: "You don't have any tickets yet"
  - CTA: [Browse Upcoming Events]

### Loading States

- **Initial Page Load**: Skeleton screen with placeholder QR code
- **QR Code Generating**: Spinner overlay on QR code area
- **PDF Downloading**: Progress bar or spinner on download button
- **Wallet Pass Generating**: Loading spinner on wallet button
- **Transfer Processing**: Spinner on confirm button, disable form

### Error States

- **QR Code Generation Failed**:
  - Display: "⚠️ Unable to generate QR code. Show ticket ID at check-in."
  - Fallback: Display ticket ID as large text
  - CTA: [Retry] [Contact Support]

- **PDF Download Failed**:
  - Toast: "Unable to download PDF. Please try again."
  - CTA: [Retry Download]
  - Fallback: Screenshot ticket page

- **Wallet Integration Failed**:
  - Toast: "Unable to add to wallet. Try downloading PDF instead."
  - CTA: [Download PDF]

- **Transfer Failed (API Error)**:
  - Display: "Transfer failed. Please try again or contact support."
  - Keep modal open with form data
  - CTA: [Retry] [Cancel]

- **Network Error (Offline)**:
  - Banner: "You're offline. Showing cached ticket."
  - QR code still visible (cached)
  - Disable: Transfer, Cancel, Update buttons
  - Enable: Download PDF (if cached), Print

### Ticket Status Edge Cases

- **Ticket Already Used (Checked In)**:
  - Status badge: "✅ Checked In"
  - Message: "You checked in at {time}"
  - QR code: Still visible (for exit/re-entry if allowed)

- **Duplicate Check-In Attempt**:
  - Handled by backend: Reject duplicate scan
  - User sees: "Already checked in" message at venue
  - No client-side change

- **Ticket Transferred by Someone Else**:
  - Status: "TRANSFERRED TO YOU"
  - Message: "This ticket was transferred to you by {original owner}"
  - QR code: New code for new owner
  - Action: [Accept Transfer] (if requires confirmation)

- **Event Postponed**:
  - Banner: "📅 Event postponed to {new date}"
  - Message: "Your ticket is still valid"
  - CTA: [Update Calendar] [Download Updated PDF]

- **Venue Changed**:
  - Banner: "📍 Venue changed to {new venue}"
  - Message: "Your ticket is still valid"
  - CTA: [Get Directions] [Update Calendar]

### Security Edge Cases

- **Screenshot Detected** (Mobile):
  - Toast: "⚠️ Don't share your QR code publicly. Keep it secure."
  - Analytics: Log screenshot event

- **QR Code Expired**:
  - Auto-refresh QR code every 5 minutes
  - If refresh fails: Show expired warning
  - CTA: [Refresh QR Code]

- **Suspicious Activity** (multiple devices accessing same ticket):
  - Email alert to owner: "Your ticket was accessed from {location}"
  - Banner on page: "Your ticket was recently viewed on another device"
  - CTA: [Secure My Account]

### Event Capacity Edge Cases

- **Event Oversold** (unlikely but possible):
  - All tickets remain valid
  - Organizers handle at venue
  - No client-side impact

### Time-Based Edge Cases

- **Viewing Ticket After Event**:
  - Status: "EVENT ENDED"
  - Message: "Thank you for attending!"
  - QR code: Still visible (for records)
  - CTA: [View Event Photos] [View Recordings] [View Other Events]

- **Viewing Ticket 1 Hour Before Event**:
  - Reminder banner: "🔔 Event starts in 1 hour!"
  - CTA: [Get Directions] [Check-in Now]
  - Lock screen notification (PWA)

---

## Change Log

| Date       | Version | Description                                  | Author     |
|------------|---------|----------------------------------------------|------------|
| 2025-10-04 | 1.0     | Initial wireframe creation for Ticket/QR page | Sally (UX) |

---

## Review Notes

### Stakeholder Feedback

*Awaiting stakeholder review*

### Design Iterations

*Initial design based on event registration flow and mobile-first ticket access requirements*

### Open Questions

1. **QR Code Refresh Frequency**: How often should QR code refresh for security?
   - **Current**: Every 5 minutes in background
   - **Alternative**: Only on page load, or on manual refresh
   - **Recommendation**: Every 5 minutes to prevent screenshot reuse, with 24-hour validity

2. **Ticket Transfer Restrictions**: Should ticket transfer be allowed at all?
   - **Current**: Allowed up to 24 hours before event
   - **Concerns**: Abuse, scalping (though free event), name mismatch at check-in
   - **Recommendation**: Allow transfer with email verification, log all transfers

3. **Screenshot Security**: Should we prevent screenshots or just warn?
   - **Current**: Warn with toast notification
   - **Alternative**: Technical prevention (DRM, screenshot blocking)
   - **Recommendation**: Warning only - UX friction vs. security trade-off

4. **Offline Access Duration**: How long should ticket stay cached offline?
   - **Current**: Until 24 hours after event
   - **Alternative**: 7 days after event, or until manually cleared
   - **Recommendation**: 24 hours after event, then prompt to clear cache

5. **Multiple Device Access**: Should users be warned if ticket accessed from multiple devices?
   - **Current**: Yes - email alert sent
   - **Concerns**: False positives (user switches devices)
   - **Recommendation**: Alert only if >3 devices within 1 hour (suspicious pattern)

6. **Check-In History**: Should ticket show check-in timestamp after user checks in?
   - **Current**: Yes - "✅ Checked in at {time}"
   - **Use case**: Re-entry, exit tracking, user confirmation
   - **Recommendation**: Yes - helpful for attendee and organizer

---

**Implementation Priority**: 🔴 **HIGH** - Critical for event access and attendee check-in. Required for MVP.

**Dependencies**:
- Event Registration Flow (Story 2.4) ✅
- Registration Confirmation Page (Story 2.4) ✅
- QR Code generation service (backend)
- Apple Wallet / Google Wallet integration APIs
- PDF generation service
- PWA service worker for offline caching

**Related Screens**:
- Registration Confirmation Page (story-2.4-registration-confirmation.md) ✅
- Event Details - Attendee View (story-5.2-event-details-attendee-view.md)
- Event Registration Flow (story-2.4-event-registration.md)
- User Dashboard with ticket list (future)
