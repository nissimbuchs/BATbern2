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
│  │    experts                    │  🔍 Search Archive (20+ years)                  │   │
│  │  ✓ Network with 200+ IT      │  📧 Subscribe to Newsletter                     │   │
│  │    professionals              │                                                  │   │
│  │  ✓ Free admission & lunch    │                                                  │   │
│  │  ✓ Practical takeaways       │                                                  │   │
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

**CONSOLIDATED API APPROACH (Story 1.15a.1 - Events API Consolidation):**

1. **GET /api/v1/events?filter={"status":"published"}&sort=-eventDate&limit=1&include=venue,speakers,sessions,topics,agenda**
   - **Implementation**: Story 1.15a.1 consolidated Events API with resource expansion (AC2)
   - Returns: Current/upcoming published event with all sub-resources expanded in a single call
   - Response includes:
     - Event core data: title, eventNumber, eventDate, description, registrationOpen, registrationDeadline, admissionFee, maxAttendees
     - venue: Venue details with name, address, mapUrl, directions link
     - speakers: Complete speaker details (firstName, lastName, company, profilePhoto, position, sessionTitle)
     - sessions: Sessions with title, startTime, endTime, sessionType, speakers array, abstract
     - topics: Event topics (name, category, sessionCount)
     - agenda: Structured agenda (registrationTime, keynoteTime, networkingEnd, sessions, breaks, pdfUrl)
   - Used for: Populate entire landing page in a single request
   - **Performance**: Reduced from 6 API calls to 1 (83% reduction in HTTP requests)
   - **Caching**: 15-minute Caffeine in-memory cache with automatic invalidation (AC15-16)
   - **Response Time**: <50ms cached, <500ms uncached with all includes (P95)

---

**MIGRATION NOTE (Story 1.15a.1 - Events API Consolidation):**
The original implementation required 6 separate API calls on page load:
- List published events
- Event details
- Sessions
- Speakers
- Agenda
- Topics

The new consolidated API (Story 1.15a.1) uses the `?include=venue,speakers,sessions,topics,agenda` parameter to fetch all data in one call. This provides:
- Page load time: ~80% faster (from ~1.8s to <350ms for public landing page)
- Instant page render with all content available immediately
- Better SEO performance (faster initial content paint)
- Reduced server load (1 database query instead of 6)
- Atomic data consistency across all sections
- Improved user experience with no progressive loading flashes
- In-memory caching: <50ms cached response time, 15-minute TTL

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

7. **🔍 Search Archive (20+ years) link** → Navigate to `Content Discovery` (story-5.1-content-discovery.md)
   - Opens content search interface
   - Context: Opens with empty search

8. **📧 Subscribe to Newsletter link** → Opens `Newsletter Subscription Modal`
   - Shows email input modal (same page)
   - No navigation

### Top Navigation

9. **Upcoming Events menu item** → Navigate to `Future Events Listing`
    - Shows upcoming events list
    - No context passed

10. **Archive menu item** → Navigate to `Historical Archive` (story-1.18-historical-archive.md)
    - Opens archive home
    - No context passed

11. **[Login] button** → Navigate to `AWS Cognito Login`
    - Starts authentication flow
    - Context: Return URL (current page), redirects to dashboard after login

12. **[EN|DE] language switcher** → Reloads current page
    - Same page with language change
    - Context: locale parameter, reloads with translated content

---
