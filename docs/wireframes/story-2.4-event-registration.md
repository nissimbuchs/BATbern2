# Story: Event Registration Flow - Wireframe

**Story**: Epic 2, Story 4
**Screen**: Event Registration Flow
**User Role**: Attendee
**Related FR**: FR6 (Registration)

---

## 3. Event Registration Flow

```
┌────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                  Register for Spring Conference 2025                        │
├────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                    │
│  ┌───────────────────────────────────────────────────────────────────────────────┐ │
│  │  Registration Progress:  ●━━━━━━━━━━━━━━━━━━━━━○                              │ │
│  │                         Details                Confirm                        │ │
│  └───────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                    │
│  ┌──── STEP 1: YOUR DETAILS ────────────────────────────────────────────────────┐  │
│  │                                                                              │  │
│  │  Personal Information                                                        │  │
│  │                                                                              │  │
│  │  First Name *                    Last Name *                                 │  │
│  │  ┌─────────────────────┐        ┌─────────────────────┐                      │  │
│  │  │ John                │        │ Smith               │                      │  │
│  │  └─────────────────────┘        └─────────────────────┘                      │  │
│  │                                                                              │  │
│  │  Email Address *                                                             │  │
│  │  ┌──────────────────────────────────────────────────────┐                    │  │
│  │  │ john.smith@company.ch                                │                    │  │
│  │  └──────────────────────────────────────────────────────┘                    │  │
│  │  ✓ We'll send your ticket and event updates here                             │  │
│  │                                                                              │  │
│  │  Company/Organization * (autocomplete)                                       │  │
│  │  ┌─────────────────────┐        Job Title                                    │  │
│  │  │ TechCorp AG ▼       │        ┌─────────────────────┐                      │  │
│  │  └─────────────────────┘        │ Senior Developer    │                      │  │
│  │  💡 Existing companies suggested  └─────────────────────┘                    │  │
│  │                                                                              │  │
│  │  Industry Sector                                                             │  │
│  │  ┌──────────────────────────────────────────────────────┐                    │  │
│  │  │ Financial Services ▼                                  │                   │  │
│  │  └──────────────────────────────────────────────────────┘                    │  │
│  │                                                                              │  │
│  │  Years of Experience                                                         │  │
│  │  ○ 0-2 years  ○ 3-5 years  ● 6-10 years  ○ 10+ years                         │  │
│  │                                                                              │  │
│  │  How did you hear about us?                                                  │  │
│  │  ☐ Previous attendee  ☑ Colleague  ☐ LinkedIn  ☐ Company                     │  │
│  │                                                                              │  │
│  │                                                   [Next: Confirm →]          │  │
│  └──────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                    │
└────────────────────────────────────────────────────────────────────────────────────┘

[STEP 2: CONFIRMATION]
┌───────────────────────────────────────────────────────────────────────────────-──────┐
│  ┌──── STEP 2: CONFIRM REGISTRATION ──────────────────────────────────────────────┐  │
│  │                                                                                │  │
│  │  Review Your Registration                                                      │  │
│  │  ┌────────────────────────────────────────────────────────────────────────-─┐  │  │
│  │  │ Event:     Spring Conference 2025: Cloud Native Architecture             │  │  │
│  │  │ Date:      May 15, 2025                                                  │  │  │
│  │  │ Location:  Kursaal Bern                                                  │  │  │
│  │  │ Time:      08:30 - 18:00                                                 │  │  │
│  │  │ Price:     FREE (including lunch & coffee)                               │  │  │
│  │  │                                                                          │  │  │
│  │  │ Attendee:  John Smith                                                    │  │  │
│  │  │ Email:     john.smith@company.ch                                         │  │  │
│  │  │ Company:   TechCorp AG                                                   │  │  │
│  │  └──────────────────────────────────────────────────────────────────────-───┘  │  │
│  │                                                                                │  │
│  │  Communication Preferences                                                     │  │
│  │  ☑ Send me event reminders (1 week and 1 day before)                           │  │
│  │  ☑ Subscribe to BATbern newsletter (monthly)                                   │  │
│  │                                                                                │  │
│  │  Terms & Conditions                                                            │  │
│  │  ☑ I agree to the event terms and photo/video policy                           │  │
│  │  ☑ I understand this is a free event with limited capacity                     │  │
│  │                                                                                │  │
│  │  💡 Want to manage all your registrations? [Create a free account →]          │  │
│  │                                                                                │  │
│  │  [← Back]                                   [Complete Registration]            │  │
│  └────────────────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────────────-─┘
```

---

## Architecture Notes

**ADR-005 Reference**: Anonymous Event Registration
**Story**: 4.1.5a Architecture Consolidation

**Anonymous Registration**:
- Users can register WITHOUT creating a Cognito account
- User profile created via User Management Service with `cognito_id=NULL`
- Email serves as unique identifier for anonymous users
- Optional account creation offered after registration

**Registration Scope**:
- Registration is for the WHOLE EVENT, not individual sessions
- No session selection step in this flow

**Account Linking**:
- "Create a free account" CTA shown in confirmation step
- If user later creates account with same email, automatic linking occurs on first login
- See story-2.4-registration-confirmation.md for account creation flow

---

## API Requirements

### Initial Page Load APIs

When the Event Registration screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/events/{eventId}?include=venue,sessions,speakers**
   - Query params: `include=venue,sessions,speakers` (optional fields to include)
   - Returns: Event details with embedded venue data (title, eventDate, startTime, endTime, admissionFee, maxAttendees, currentAttendees, venue.name, venue.address)
   - Used for: Display event name, date, location, time range, price in confirmation view, track capacity for "Limited capacity" messaging, calculate remaining seats
   - Consolidation: Single endpoint replaces separate event + venue calls (Story 1.17)

2. **GET /api/v1/users/me** (optional, authenticated users only)
   - Query params: `include=registrations,preferences` (if user is authenticated)
   - Returns: User profile with registrations and preferences (firstName, lastName, email, company, jobTitle, industrySector, yearsOfExperience, communicationPreferences)
   - Used for: Pre-populate all form fields for authenticated returning users
   - Note: Anonymous users skip this step (no pre-population)
   - Consolidation: Uses consolidated Users API (ADR-005)

3. **GET /api/v1/companies/search?query={query}&limit=20**
   - Query params: `query` (search string), `limit` (max results, default 20)
   - Returns: List of existing companies matching the query (id, name, industry)
   - Used for: Provide smart suggestions for company field autocomplete
   - Caching: Redis-backed with 5-minute TTL for common queries
   - Consolidation: Uses consolidated Companies API with search endpoint (Story 1.22)

4. **GET /api/v1/events/{eventId}/registrations?filter[email]={email}**
   - Query params: `filter[email]` (email from form input)
   - Returns: Registration list filtered by email (empty if not registered, or registration details if exists)
   - Used for: Check for duplicate registration, show "already registered" warning
   - Consolidation: Uses nested registrations endpoint under Events API (Story 1.17)

### User Action APIs

5. **POST /api/v1/events/{eventId}/registrations**
   - Triggered by: User clicks [Complete Registration] button in Step 2
   - Payload: Complete registration data (firstName, lastName, email, company, role, communicationPreferences, termsAccepted, photoVideoConsent)
   - Returns: Registration confirmation (registrationCode, confirmationCode, ticketUrl)
   - Used for: Creates registration record AND user profile (via User Management API), sends confirmation email, redirects to success page
   - Backend Flow (ADR-005):
     1. Event Service calls User Management API to get-or-create user profile (cognito_id=NULL)
     2. Event Service creates registration record linked to user_profile.username
     3. Sends confirmation email with registration code
   - Consolidation: Nested under Events API (Story 1.17)

6. **PATCH /api/v1/events/{eventId}/registrations/draft**
   - Triggered by: User navigates between steps (auto-save)
   - Payload: Current form state for completed step (partial registration data)
   - Returns: Draft saved confirmation
   - Used for: Stores partial registration data, allows user to return later
   - Consolidation: Uses PATCH for partial updates (Story 1.17)

7. **POST /api/v1/notifications/subscriptions**
    - Triggered by: User checks "Subscribe to BATbern newsletter" checkbox (on registration completion)
    - Payload: `{ type: "newsletter", email: string, source: "event_registration", eventId: uuid, preferences: { frequency: "monthly" } }`
    - Returns: Subscription confirmation
    - Used for: Adds email to newsletter distribution list
    - Consolidation: Uses consolidated Notifications API (Story 1.26)

---

## Navigation Map

### Multi-Step Form Navigation

1. **← Back button (Step 1)** → Navigate to `Previous Page`
   - Returns to referring page (likely Current Event Landing)
   - Context: None (abandons registration)

2. **[Next: Confirm →] button (Step 1)** → Navigate to `Step 2 - Confirm Registration`
   - Advances to step 2 (same screen)
   - Validation: Validates required fields (First Name, Last Name, Email, Company)
   - Context: Form data carried forward in state

3. **[← Back] button (Step 2)** → Navigate to `Step 1 - Your Details`
   - Returns to step 1 (same screen)
   - Context: All form data retained for editing

4. **[Complete Registration] button (Step 2)** → Navigate to `Registration Success Page`
   - Submits registration and navigates to success page
   - Validation: Validates terms acceptance checkboxes are checked
   - Target: `/events/{eventId}/registration/success?confirmationCode=BAT-2025-1234`
   - Context: registrationId, confirmationCode, ticketUrl

### Completion Navigation

5. **Registration Success (auto-navigation)** → Navigate to `Success Confirmation Screen`
   - After successful registration completion
   - Shows ticket download, calendar invite
   - Context: Registration details, ticket URL, calendar invite link

### Error State Navigation

6. **Duplicate Registration Detected** → Shows `Duplicate Registration Modal`
   - Modal with option to view existing registration or use different email
   - No navigation (modal on same screen)
   - Context: Existing registrationId

---
