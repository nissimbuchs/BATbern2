# Story: Event Registration Flow - Wireframe

**Story**: Epic 2, Story 4
**Screen**: Event Registration Flow
**User Role**: Attendee
**Related FR**: FR6 (Registration)

---

## 3. Event Registration Flow

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                  Register for Spring Conference 2025                          │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐ │
│  │  Registration Progress:  ●━━━━━━━━━○━━━━━━━━━○━━━━━━━━━○                        │ │
│  │                         Details    Sessions   Confirm                            │ │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── STEP 1: YOUR DETAILS ──────────────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  Personal Information                                                          │  │
│  │                                                                                 │  │
│  │  First Name *                    Last Name *                                   │  │
│  │  ┌─────────────────────┐        ┌─────────────────────┐                      │  │
│  │  │ John                │        │ Smith               │                      │  │
│  │  └─────────────────────┘        └─────────────────────┘                      │  │
│  │                                                                                 │  │
│  │  Email Address *                                                               │  │
│  │  ┌──────────────────────────────────────────────────────┐                     │  │
│  │  │ john.smith@company.ch                                │                     │  │
│  │  └──────────────────────────────────────────────────────┘                     │  │
│  │  ✓ We'll send your ticket and event updates here                             │  │
│  │                                                                                 │  │
│  │  Company/Organization *          Job Title                                     │  │
│  │  ┌─────────────────────┐        ┌─────────────────────┐                      │  │
│  │  │ TechCorp AG         │        │ Senior Developer    │                      │  │
│  │  └─────────────────────┘        └─────────────────────┘                      │  │
│  │                                                                                 │  │
│  │  Industry Sector                                                               │  │
│  │  ┌──────────────────────────────────────────────────────┐                     │  │
│  │  │ Financial Services ▼                                  │                     │  │
│  │  └──────────────────────────────────────────────────────┘                     │  │
│  │                                                                                 │  │
│  │  Years of Experience                                                           │  │
│  │  ○ 0-2 years  ○ 3-5 years  ● 6-10 years  ○ 10+ years                        │  │
│  │                                                                                 │  │
│  │  Dietary Requirements (optional)                                               │  │
│  │  ☐ Vegetarian  ☐ Vegan  ☐ Gluten-free  ☐ Other: _________                   │  │
│  │                                                                                 │  │
│  │  How did you hear about us?                                                    │  │
│  │  ☐ Previous attendee  ☑ Colleague  ☐ LinkedIn  ☐ Company                     │  │
│  │                                                                                 │  │
│  │                                            [Next: Choose Sessions →]           │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘

[STEP 2: SESSION PREFERENCES]
┌─────────────────────────────────────────────────────────────────────────────────────┐
│  ┌──── STEP 2: SESSION PREFERENCES ───────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  Select sessions you're most interested in (helps with capacity planning)      │  │
│  │                                                                                 │  │
│  │  Morning Sessions (09:00 - 12:30)                                             │  │
│  │  ┌─────────────────────────────────────────────────────────────────────────┐  │  │
│  │  │ ☑ Keynote: Future of Cloud Native (Required)                            │  │  │
│  │  │ ☑ Kubernetes Best Practices - Peter Muller                              │  │  │
│  │  │ ☐ Service Mesh Architecture - Anna Lopez                                │  │  │
│  │  │ ☐ GitOps Implementation - Marc Baum                                     │  │  │
│  │  └─────────────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                                 │  │
│  │  Afternoon Sessions (13:30 - 17:00)                                           │  │
│  │  ┌─────────────────────────────────────────────────────────────────────────┐  │  │
│  │  │ ☑ Workshop: Hands-on Kubernetes (Limited: 27/30 seats)                  │  │  │
│  │  │ ☐ Panel: Security Challenges in Cloud Native                            │  │  │
│  │  │ ☑ Container Orchestration at Scale                                      │  │  │
│  │  │ ☐ Closing Keynote: AI/ML on Kubernetes                                  │  │  │
│  │  └─────────────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                                 │  │
│  │  Topics of Interest (for future events)                                       │  │
│  │  ☑ Kubernetes  ☑ Security  ☐ AI/ML  ☑ DevOps  ☐ Data Engineering            │  │
│  │                                                                                 │  │
│  │  [← Back]                                          [Next: Confirm →]          │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────┘

[STEP 3: CONFIRMATION]
┌─────────────────────────────────────────────────────────────────────────────────────┐
│  ┌──── STEP 3: CONFIRM REGISTRATION ──────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  Review Your Registration                                                      │  │
│  │  ┌─────────────────────────────────────────────────────────────────────────┐  │  │
│  │  │ Event:     Spring Conference 2025: Cloud Native Architecture             │  │  │
│  │  │ Date:      May 15, 2025                                                  │  │  │
│  │  │ Location:  Kursaal Bern                                                  │  │  │
│  │  │ Time:      08:30 - 18:00                                                 │  │  │
│  │  │ Price:     FREE (including lunch & coffee)                               │  │  │
│  │  │                                                                           │  │  │
│  │  │ Attendee:  John Smith                                                    │  │  │
│  │  │ Email:     john.smith@company.ch                                         │  │  │
│  │  │ Company:   TechCorp AG                                                   │  │  │
│  │  │                                                                           │  │  │
│  │  │ Selected Sessions:                                                       │  │  │
│  │  │ • Morning: Keynote, Kubernetes Best Practices                            │  │  │
│  │  │ • Afternoon: Workshop, Container Orchestration                           │  │  │
│  │  └─────────────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                                 │  │
│  │  Communication Preferences                                                     │  │
│  │  ☑ Send me event reminders (1 week and 1 day before)                         │  │
│  │  ☑ Subscribe to BATbern newsletter (monthly)                                  │  │
│  │  ☐ Notify me about similar future events                                      │  │
│  │                                                                                 │  │
│  │  Terms & Conditions                                                            │  │
│  │  ☑ I agree to the event terms and photo/video policy                         │  │
│  │  ☑ I understand this is a free event with limited capacity                   │  │
│  │                                                                                 │  │
│  │  [← Back]                                   [Complete Registration]           │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## API Requirements

### Initial Page Load APIs

When the Event Registration screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/events/{eventId}**
   - Returns: Event details (title, eventDate, venue name, startTime, endTime, admissionFee, maxAttendees, currentAttendees)
   - Used for: Display event name, date, location, time range, price in confirmation view, track capacity for "Limited capacity" messaging, calculate remaining seats

2. **GET /api/v1/events/{eventId}/sessions**
   - Returns: Sessions (id, title, startTime, speakers array, capacity, currentAttendees, isRequired)
   - Used for: Display session names, group into morning/afternoon, show speaker names with sessions, show capacity warnings ("27/30 seats"), calculate availability, mark keynote as required

3. **GET /api/v1/events/{eventId}/topics**
   - Returns: Topics (id, name, category)
   - Used for: Display topic names in "Topics of Interest" checkboxes, group topics if needed

4. **GET /api/v1/attendee/profile** (if authenticated)
   - Returns: Attendee profile (firstName, lastName, email, company, jobTitle, industrySector, yearsOfExperience, dietaryRequirements, topicPreferences)
   - Used for: Pre-populate all form fields for returning users

5. **GET /api/v1/events/{eventId}/registration/status?email={email}**
   - Query params: email (from form input)
   - Returns: Registration status (isRegistered, registrationId, registrationDate)
   - Used for: Check for duplicate registration, show "already registered" warning

### User Action APIs

6. **POST /api/v1/events/{eventId}/registrations**
   - Triggered by: User clicks [Complete Registration] button in Step 3
   - Payload: Complete registration data (attendeeDetails, sessionPreferences, topicInterests, communicationPreferences, termsAccepted, photoVideoConsent)
   - Returns: Registration confirmation (registrationId, confirmationCode, ticketUrl)
   - Used for: Creates registration record, sends confirmation email, redirects to success page

7. **PUT /api/v1/events/{eventId}/registrations/draft**
   - Triggered by: User navigates between steps (auto-save)
   - Payload: Current form state for completed step
   - Returns: Draft saved confirmation
   - Used for: Stores partial registration data, allows user to return later

8. **GET /api/v1/events/{eventId}/sessions/{sessionId}/capacity**
   - Triggered by: Real-time capacity checking when user checks session checkbox
   - Returns: Capacity info (currentAttendees, maxCapacity, isAvailable, waitlistAvailable)
   - Used for: Updates UI with capacity warnings, disables checkbox if full

9. **POST /api/v1/attendee/profile**
   - Triggered by: User completes registration (first-time attendee)
   - Payload: Attendee profile data from form
   - Returns: Created attendee profile
   - Used for: Creates reusable attendee profile for future registrations

10. **PUT /api/v1/attendee/profile**
    - Triggered by: User completes registration (returning attendee with changes)
    - Payload: Updated attendee profile data
    - Returns: Updated profile confirmation
    - Used for: Updates existing attendee profile with new information

11. **POST /api/v1/newsletter/subscribe**
    - Triggered by: User checks "Subscribe to BATbern newsletter" checkbox (on registration completion)
    - Payload: `{ email: string, source: "event_registration", eventId: uuid }`
    - Returns: Subscription confirmation
    - Used for: Adds email to newsletter distribution list

---

## Navigation Map

### Multi-Step Form Navigation

1. **← Back button (Step 1)** → Navigate to `Previous Page`
   - Returns to referring page (likely Current Event Landing)
   - Context: None (abandons registration)

2. **[Next: Choose Sessions →] button (Step 1)** → Navigate to `Step 2 - Session Preferences`
   - Advances to step 2 (same screen)
   - Validation: Validates required fields (First Name, Last Name, Email, Company)
   - Context: Form data carried forward in state

3. **[← Back] button (Step 2)** → Navigate to `Step 1 - Your Details`
   - Returns to step 1 (same screen)
   - Context: Session preferences retained in state

4. **[Next: Confirm →] button (Step 2)** → Navigate to `Step 3 - Confirm Registration`
   - Advances to step 3 (same screen)
   - Context: All form data carried forward to confirmation view

5. **[← Back] button (Step 3)** → Navigate to `Step 2 - Session Preferences`
   - Returns to step 2 (same screen)
   - Context: All form data retained for editing

6. **[Complete Registration] button (Step 3)** → Navigate to `Registration Success Page`
   - Submits registration and navigates to success page
   - Validation: Validates terms acceptance checkboxes are checked
   - Target: `/events/{eventId}/registration/success?confirmationCode=BAT-2025-1234`
   - Context: registrationId, confirmationCode, ticketUrl

### Completion Navigation

7. **Registration Success (auto-navigation)** → Navigate to `Success Confirmation Screen`
   - After successful registration completion
   - Shows ticket download, calendar invite
   - Context: Registration details, ticket URL, calendar invite link

### Error State Navigation

8. **Duplicate Registration Detected** → Shows `Duplicate Registration Modal`
   - Modal with option to view existing registration or use different email
   - No navigation (modal on same screen)
   - Context: Existing registrationId

---
