# Story 2.4: Current Event Landing Page - Wireframe

**Story**: Epic 2, Story 4 - Current Event Landing Page
**Screen**: Current Event Landing Page (Homepage - FR6 Compliant)
**User Role**: Attendee (Public)
**Related FR**: FR6 (Current Event Prominence)

---

## 1. Current Event Landing Page (Homepage - FR6 Compliant)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ BATbern                                    Upcoming Events  Archive  [EN|DE] [Login] │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │            🚀 SPRING CONFERENCE 2025: CLOUD NATIVE ARCHITECTURE                  │ │
│  │                                                                                   │ │
│  │                          MAY 15, 2025 • KURSAAL BERN                             │ │
│  │                                                                                   │ │
│  │                    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━                          │ │
│  │                                                                                   │ │
│  │                              🎟️ FREE ADMISSION                                   │ │
│  │                         Limited Seats - Register Now!                            │ │
│  │                                                                                   │ │
│  │                         [ REGISTER FOR FREE → ]                                  │ │
│  │                                                                                   │ │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── EVENT HIGHLIGHTS ───────────────────────────────────────────────────────────┐  │
│  │                                                                                  │  │
│  │  📍 Location           ⏰ Schedule              🎯 Topics                        │  │
│  │  Kursaal Bern         08:30 Registration      • Kubernetes at Scale             │  │
│  │  Kornhausstrasse 3    09:00 Keynote           • Service Mesh                    │  │
│  │  3013 Bern           17:30 Networking         • Cloud Security                  │  │
│  │  [Get Directions]     [Full Schedule]         • GitOps & CI/CD                  │  │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── FEATURED SPEAKERS ──────────────────────────────────────────────────────────┐  │
│  │                                                                                  │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │  │
│  │  │              │  │              │  │              │  │              │      │  │
│  │  │   [Photo]    │  │   [Photo]    │  │   [Photo]    │  │   [Photo]    │      │  │
│  │  │              │  │              │  │              │  │              │      │  │
│  │  │  Sara Kim    │  │ Peter Muller │  │ Thomas Weber │  │  Anna Lopez  │      │  │
│  │  │              │  │              │  │              │  │              │      │  │
│  │  │ Docker Inc.  │  │  TechCorp    │  │  Swiss Re    │  │   Google     │      │  │
│  │  │              │  │              │  │              │  │              │      │  │
│  │  │ "Container   │  │ "K8s Best    │  │ "Zero Trust  │  │ "AI/ML on    │      │  │
│  │  │  Security"   │  │  Practices"  │  │  Security"   │  │  Kubernetes" │      │  │
│  │  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘      │  │
│  │                                                                                  │  │
│  │                            [View All 8 Speakers →]                              │  │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── AGENDA AT A GLANCE ─────────────────────────────────────────────────────────┐  │
│  │                                                                                  │  │
│  │  Morning Sessions (Technical Deep Dives)     Afternoon (Practical Applications) │  │
│  │                                                                                  │  │
│  │  09:00  Keynote: Future of Cloud Native      13:30  Workshop: Hands-on K8s     │  │
│  │  09:45  Kubernetes Best Practices            14:15  Panel: Security Challenges  │  │
│  │  10:30  Coffee Break & Networking            15:00  Container Orchestration     │  │
│  │  11:00  Service Mesh Architecture            15:45  Coffee & Demos              │  │
│  │  11:45  GitOps Implementation                16:15  Closing Keynote             │  │
│  │  12:30  Lunch Break                          17:00  Networking Apéro            │  │
│  │                                                                                  │  │
│  │                         [Download Full Agenda (PDF)]                            │  │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── WHY ATTEND ─────────────┬──── QUICK LINKS ────────────────────────────────┐   │
│  │                              │                                                  │   │
│  │  ✓ Learn from industry       │  📚 Browse Past Events                          │   │
│  │    experts                    │  📊 Download Presentations                      │   │
│  │  ✓ Network with 200+ IT      │  🔍 Search Archive (20+ years)                  │   │
│  │    professionals              │  📧 Subscribe to Newsletter                     │   │
│  │  ✓ Free admission & lunch    │  👥 Join Community                              │   │
│  │  ✓ Practical takeaways       │  🏢 Become a Partner                            │   │
│  │  ✓ CPE credits available     │                                                  │   │
│  └──────────────────────────────┴──────────────────────────────────────────────────┘  │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Key Features:
- **Prominent current event** display above the fold
- **Free admission** clearly highlighted
- **Complete logistics** (date, location, time) immediately visible
- **One-click registration** with clear CTA
- **Secondary navigation** to archives below primary content

---

## API Requirements

### Initial Page Load APIs

When the Current Event Landing Page loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/events?status=published&limit=1**
   - Returns: Current/upcoming published event (title, eventNumber, eventDate, venue name and address, description, registrationOpen)
   - Used for: Display in hero banner, show event date and location, control registration button visibility, SEO meta tags

2. **GET /api/v1/events/{eventId}**
   - Returns: Complete event details (venue address and mapUrl, eventDate, registrationDeadline, admissionFee, maxAttendees)
   - Used for: Display full address in location section, link to directions, format schedule times, show registration urgency, display admission fee, calculate "Limited Seats" messaging

3. **GET /api/v1/events/{eventId}/sessions**
   - Returns: Sessions with title, startTime, endTime, sessionType, speakers array, abstract
   - Used for: Display session titles in agenda, build schedule timeline, organize by session type, list speaker names and companies, show descriptions on hover, count for "View All X Speakers" link

4. **GET /api/v1/events/{eventId}/speakers**
   - Returns: Speaker details (firstName, lastName, company name, profilePhoto, position, sessionTitle)
   - Used for: Display speaker names, show company affiliation, render speaker photos, show role/title, display session topics, populate featured speakers section (first 4), total count

5. **GET /api/v1/events/{eventId}/agenda**
   - Returns: Structured agenda (registrationTime, keynoteTime, networkingEnd, sessions array, breaks array, pdfUrl)
   - Used for: Display registration and networking times, show keynote timing, build timeline, show coffee breaks and lunch, link to PDF download

6. **GET /api/v1/events/{eventId}/topics**
   - Returns: Event topics (name, category, sessionCount)
   - Used for: Display topic list in "Topics" section, group topics if needed, show session coverage count

### User Action APIs

7. **POST /api/v1/newsletter/subscribe**
   - Triggered by: User clicks "📧 Subscribe to Newsletter" link
   - Payload: `{ email: string, source: "event_landing" }`
   - Returns: Subscription confirmation
   - Used for: Opens modal for email input, subscribes user to newsletter

8. **GET /api/v1/events/{eventId}/agenda.pdf**
   - Triggered by: User clicks [Download Full Agenda (PDF)]
   - Returns: PDF file download
   - Used for: Downloads formatted agenda document

---

## Navigation Map

### Primary Navigation Actions

1. **[REGISTER FOR FREE →] button** → Navigate to `Event Registration` (story-2.4-event-registration.md)
   - Opens registration flow
   - Context: eventId, source page ("landing")

2. **[Get Directions] link** → Opens external map service
   - Opens Google Maps/Apple Maps with venue address
   - Context: Venue address, coordinates

3. **[Full Schedule] link** → Navigate to `Detailed Agenda Page`
   - Shows complete event schedule
   - Context: eventId

4. **[View All 8 Speakers →] link** → Navigate to `Speaker Listing Page`
   - Shows all speakers for event
   - Context: eventId

5. **[Download Full Agenda (PDF)] link** → Downloads PDF
   - Triggers PDF download
   - Context: eventId

### Quick Links Navigation

6. **📚 Browse Past Events link** → Navigate to `Historical Archive` (story-1.18-historical-archive.md)
   - Opens archive home page
   - No context passed

7. **📊 Download Presentations link** → Navigate to `Content Discovery` (story-5.1-content-discovery.md)
   - Opens content archive with downloadable filter
   - Context: Filter preset to show downloadable content

8. **🔍 Search Archive (20+ years) link** → Navigate to `Content Discovery` (story-5.1-content-discovery.md)
   - Opens content search interface
   - Context: Opens with empty search

9. **📧 Subscribe to Newsletter link** → Opens `Newsletter Subscription Modal`
   - Shows email input modal (same page)
   - No navigation

10. **👥 Join Community link** → Navigate to `Speaker Community` (story-7.1-speaker-community.md)
    - Opens community portal
    - No context passed

11. **🏢 Become a Partner link** → Navigate to `Partner Onboarding Page`
    - Opens partner registration flow
    - No context passed

### Top Navigation

12. **Upcoming Events menu item** → Navigate to `Future Events Listing`
    - Shows upcoming events list
    - No context passed

13. **Archive menu item** → Navigate to `Historical Archive` (story-1.18-historical-archive.md)
    - Opens archive home
    - No context passed

14. **[Login] button** → Navigate to `AWS Cognito Login`
    - Starts authentication flow
    - Context: Return URL (current page), redirects to dashboard after login

15. **[EN|DE] language switcher** → Reloads current page
    - Same page with language change
    - Context: locale parameter, reloads with translated content

---
