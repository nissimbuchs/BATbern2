# Epic 4: Public Website & Content Discovery

## Status
✅ **COMPLETE** - All Stories Complete (100%)

**Last Updated:** 2026-01-16

**Progress Summary:**
- ✅ Story 4.1 Foundation (Setup, Layout, Hero, Content, Registration) - COMPLETE (9 sub-stories)
- ✅ Story 4.1 Backend Integration - COMPLETE (2 sub-stories)
- ✅ Story 4.1 Finalization - COMPLETE (Testing, SEO, Performance)
- ✅ Story 4.2 Historical Event Archive Browsing - COMPLETE (BAT-109)
- ✅ Story 4.3 Historical Content Search & Discovery - COMPLETE

**Completed Stories (Story 4.1 sub-stories):**
- ✅ 4.1.1 - Setup shadcn/ui & Dark Theme
- ✅ 4.1.2 - Public Layout & Navigation
- ✅ 4.1.3 - Event Landing Hero
- ✅ 4.1.4 - Event Content Sections
- ✅ 4.1.5 - Registration Wizard
- ✅ 4.1.5a - Registration Architecture
- ✅ 4.1.5c - Secure Email Confirmation
- ✅ 4.1.5d - Anonymous Registration Cancellation
- ✅ 4.1.6 - Registration Confirmation
- ✅ 4.1.7 - API Optimization & Performance
- ✅ 4.1.8 - Testing, SEO & Deploy
- ✅ 4.1.8a - Performance Optimization & Critical Fixes
- ✅ 2.2a - Backend Anonymous Registration

**Completed Stories (Archive & Search):**
- ✅ Story 4.2 - Historical Event Archive Browsing (BAT-109)
  - Archive browse page with infinite scroll
  - Grid/list view toggle
  - Time period and topic filters
  - Archive event detail pages
  - Backend: 12/12 tests passing, Frontend: 65/65 tests passing, E2E: 27 tests created
- ✅ Story 4.3 - Historical Content Search & Discovery
  - Full-text search across events, sessions, speakers
  - PostgreSQL GIN indexes for search performance
  - Faceted filtering and sorting

## Epic Overview

**Epic Goal**: Launch public-facing BATbern website with current event landing pages, event registration, historical archive browsing, and full-text content search, making 20+ years of BATbern content accessible to the public.

**Deliverable**: Complete public website featuring upcoming events with registration flow, searchable historical archive, and content discovery across presentations, speakers, and topics.

**Architecture Context**:
- **Frontend**: React public pages with server-side rendering for SEO
- **Backend**: Attendee Experience Service + Event Management Service APIs
- **Search**: PostgreSQL full-text search for content discovery
- **CDN**: CloudFront for global content delivery
- **Cache**: Caffeine in-memory cache for event data and search results

**Duration**: 5 weeks (Weeks 22-26)

**Prerequisites**: Epic 3 complete (historical data migrated and searchable)

---

## Story Overview

This epic consolidates three stories focused on public-facing features:

- **Story 4.1 (formerly 2.4)**: Current Event Landing Page with Registration
- **Story 4.2 (formerly 1.18)**: Historical Event Archive Browsing
- **Story 4.3 (formerly 5.1)**: Historical Content Search & Discovery

**Goal**: Public website operational by Week 25, enabling event discovery, registration, and content exploration.

---

## Story 4.1: Current Event Landing Page with Registration
**(Formerly Story 2.4)**

**User Story:**
As an **attendee**, I want to see the current/upcoming BATbern event prominently displayed with all key information and register easily, so that I can quickly understand event details and decide to attend.

**Architecture Integration:**
- **Frontend**: React landing page with server-side rendering
- **Backend**: Attendee Experience Service, Event Management Service
- **CDN**: CloudFront for global content delivery
- **Cache**: Caffeine in-memory cache for event data (1-minute TTL)
- **Email**: AWS SES for registration confirmations

**Key Functionality:**
1. **Hero Section**: Prominent event display with title, date, countdown timer
2. **Event Details**: Date, time, location with map, free attendance badge, topic description
3. **Speaker Lineup**: Speaker photos, names, companies, session titles
4. **Session Schedule**: Session cards with time, speaker, room, capacity
5. **Event Registration**: 3-step wizard (personal info → session selection → confirmation)
6. **Registration Confirmation**: QR code for check-in, calendar export (iCal), email confirmation
7. **Session Details Modal**: Deep dive into individual sessions with speaker bios

**Post-Event 14-Day Display Rule:**
After an event finishes, the homepage continues to show it for **14 days** using an archive-style layout: timetable and speaker list are visible, but registration, logistics, and venue blocks are hidden. After 14 days a nightly scheduler auto-archives the event and `/api/v1/events/current` returns 404 until the next event is active.

**Phase 1 eligibility rule**: Only events that have `currentPublishedPhase` set qualify as an upcoming event (Phase 1). Unpublished future events (e.g., state `SPEAKER_IDENTIFICATION` with no published phase) are excluded and do not block the Phase 2 (recently-completed) fallback.

---

### Wireframe References
**From docs/wireframes/sitemap.md:**

1. **Current Event Landing:** `docs/wireframes/story-2.4-current-event-landing.md` ✅
   - Hero section with event title, date, location
   - Countdown timer (if within 30 days)
   - Event highlights and topic description
   - Session schedule overview
   - Speaker lineup with photos
   - Venue information with map
   - [Register Now] call-to-action button
   - Footer with historical events link

2. **Event Registration Flow:** `docs/wireframes/story-2.4-event-registration.md` ✅
   - Multi-step registration wizard (3 steps)
   - **Step 1/3**: Personal information (name, email, company, role)
   - **Step 2/3**: Session selection (choose sessions to attend)
   - **Step 3/3**: Review & confirm (summary with edit options)
   - Progress indicator showing current step
   - [Back] and [Next]/[Submit] navigation buttons

3. **Registration Confirmation:** `docs/wireframes/story-2.4-registration-confirmation.md` ✅
   - Success message with registration number
   - Registration summary (event, sessions, personal details)
   - QR code for event check-in
   - [Download Calendar] button (iCal format)
   - Email confirmation notice
   - [View My Registrations] link to dashboard

4. **Session Details Modal:** `docs/wireframes/story-2.4-session-details-modal.md` ✅
   - Modal overlay triggered from landing page session list
   - Session title, time, duration, room/location
   - Speaker information with photo and bio
   - Session description and learning objectives
   - Session capacity and availability indicator
   - [Add to My Schedule] button
   - [Share Session] social media buttons
   - [Close] modal control

### UI Components
**Key interface elements:**
- **Hero Banner**: Full-width banner with event visual, title, date, countdown
- **Event Stats**: Quick facts cards (date, location, free attendance badge, topic)
- **Session Cards**: Session grid/list with time, speaker, room, capacity
- **Speaker Lineup**: Photo grid with names, companies, session titles
- **Venue Map**: Embedded Google Maps with directions link
- **Registration Form**: Multi-step form with validation and progress tracking
- **QR Code Display**: Scannable QR code for check-in access
- **Social Sharing**: Pre-populated share buttons (LinkedIn, Twitter, Email)
- **Filter Controls**: Session filtering by time, topic, speaker
- **Calendar Export**: Generate iCal file for calendar apps

### Wireframe Status
- ✅ **EXISTS**: All four wireframes fully documented and ready for implementation
  - Current Event Landing Page (public-facing hero page)
  - Event Registration Flow (3-step registration wizard)
  - Registration Confirmation (success page with QR code)
  - Session Details Modal (detailed session info overlay)

### Navigation
**Key navigation paths from these screens:**
- **Current Event Landing →**
  - → Event Registration Flow (click [Register Now])
  - → Session Details Modal (click session card)
  - → Speaker Profile Detail View (click speaker photo)
  - → Venue Details (click map/venue info)
  - → Filter Modal (apply session filters)
- **Event Registration (Step 1/3) →**
  - → Event Registration (Step 2/3) (click [Next])
  - ⤴ Current Event Landing (click [Cancel])
- **Event Registration (Step 2/3) →**
  - → Event Registration (Step 3/3) (click [Next])
  - ⤴ Event Registration (Step 1/3) (click [Back])
- **Event Registration (Step 3/3) →**
  - → Registration Confirmation (click [Submit])
  - ⤴ Event Registration (Step 2/3) (click [Back])
- **Registration Confirmation →**
  - → Ticket/QR Code Page (included in confirmation)
  - → Personal Attendee Dashboard (click [View My Registrations])
  - → Current Event Landing (click [Back to Event])
- **Session Details Modal →**
  - Close (returns to Current Event Landing)
  - → Speaker Profile (click speaker name/photo)

---

### Acceptance Criteria

**Landing Page Components:**
1. **Hero Banner**: Full-width banner with event visual, title, date, countdown timer (<30 days)
2. **Event Quick Facts**: Date, location, free attendance badge, topic as card layout
3. **Speaker Grid**: Photo grid with names, companies, session titles (hover preview with abstract)
4. **Session Cards**: Grid/list toggle, session time/speaker/room/capacity, filter by time/topic
5. **Venue Map**: Embedded Google Maps with directions link
6. **Social Sharing**: Pre-populated share buttons (LinkedIn, Twitter, Email) with Open Graph tags

**Registration Flow:**
7. **Step 1 - Personal Information**: Name, email, company, role with validation
8. **Step 2 - Session Selection**: Multi-select sessions with capacity warnings
9. **Step 3 - Review & Submit**: Summary with edit options, terms acceptance
10. **Progress Indicator**: Visual progress bar showing current step (1/3, 2/3, 3/3)
11. **Navigation**: [Back], [Next], [Cancel] buttons with form state preservation

**Registration Confirmation:**
12. **Success Message**: Registration number and success confirmation
13. **QR Code**: Scannable QR code for event check-in (embedded in confirmation)
14. **Calendar Export**: [Download Calendar] button generating iCal file
15. **Email Confirmation**: Automatic confirmation email with event details and QR code
16. **My Registrations Link**: Navigate to personal dashboard for registration management

**Technical Requirements:**
17. **Mobile-Responsive**: Mobile-first responsive layout (breakpoints: 320px, 768px, 1024px)
18. **Performance**: Page loads <1.5 seconds globally from CloudFront
19. **SEO Optimization**: Proper meta tags, Open Graph, structured data (JSON-LD)
20. **Analytics**: Google Analytics event tracking for registrations and navigation
21. **Unconfirmed Registration Cleanup**: Unconfirmed registrations (status = `registered` / unconfirmed) are permanently deleted after 48 hours by a scheduled cleanup job.

**Deliverables:**
- [x] Public landing page live and accessible
- [x] Registration flow functional end-to-end
- [x] QR code generation working
- [x] Calendar export generating valid iCal files
- [x] Email confirmations sent within 1 minute
- [x] Mobile-responsive design verified on all devices
- [x] SEO meta tags producing rich social media previews
- [x] Performance benchmarks met (optimizations deployed, pending final validation)

**Estimated Duration:** 1.5 weeks

---

### Story 4.1.5d: Anonymous Registration Cancellation
**Status:** ✅ **COMPLETE**

**User Story:**
As an **anonymous attendee**, I want to cancel my event registration without logging in using a secure link from my confirmation email, so that I can withdraw my registration if my plans change.

**Problem Statement:**
Per Story 4.1.5c security requirements, registration codes were removed from API responses to prevent unauthorized access. However, the original cancellation endpoint required users to provide their registration code, creating an impossible situation for anonymous users who never received their code. This story implements a secure JWT-based cancellation flow that maintains security while enabling anonymous cancellation.

**Architecture Integration:**
- **Backend**: Event Management Service — UUID-based deregistration token validation (superseded by Story 10.12)
- **Security**: UUID `deregistrationToken` stored on the registration record (not a JWT)
- **Email**: Cancellation link embedded in confirmation emails
- **Frontend**: Public cancellation page with loading/success/error states
- **i18n**: Full German/English support for emails and UI

> **Note**: The original design called for a JWT cancellation token with `type: "registration-cancellation"` and 48-hour expiry. Story 10.12 replaced this with a UUID-based `deregistrationToken` column on the registration record. The JWT mechanism described below reflects the original design; see the **UUID-Based Deregistration (Story 10.12)** section for the current implementation.

**Key Functionality:**
1. **UUID Token Generation**: Generate separate UUID `deregistrationToken` during registration (Story 10.12 implementation)
2. **Email Integration**: Cancellation link embedded in email footer (German/English templates)
3. **Public Cancellation Endpoint**: POST `/api/v1/events/{eventCode}/registrations/cancel?token={token}` (no authentication required)
4. **Token Validation**: UUID token looked up against `deregistrationToken` column on the registration record
5. **Status Update**: Registration status set to `cancelled` (record is retained in the database)
6. **Cancellation Page**: User-friendly confirmation page with i18n support
7. **Security**: Token cleared from URL after successful cancellation

**Implementation Details:**

**Backend (Event Management Service) — Story 10.12 implementation:**
- **Registration entity**: `deregistrationToken` UUID column stored at registration time
- **DeregistrationController.java** (Story 10.12 endpoints — see UUID-Based Deregistration section):
  - GET `/api/v1/registrations/deregister/verify?token={uuid}` — verifies token and returns registration info
  - POST `/api/v1/registrations/deregister` with UUID token — sets status to `cancelled`
  - POST `/api/v1/registrations/deregister/by-email` — always returns 200 (anti-enumeration)
- **EventController.java** (original Story 4.1.5d endpoint, superseded by Story 10.12):
  - POST `/api/v1/events/{eventCode}/registrations/cancel?token={token}` endpoint (public access)
  - Validates UUID token, sets registration status to `cancelled`
  - Returns `{message: "...", status: "CANCELLED"}`
- **SecurityConfig.java**: Added `.requestMatchers("/api/v1/registrations/cancel").permitAll()`
- **RegistrationEmailService.java**: Updated to accept `cancellationToken` parameter
- **Email Templates**: Added cancellation link in footer

**Frontend (React):**
- **eventApiClient.ts**: `cancelRegistration(eventCode, token)` method — calls `POST /api/v1/events/{eventCode}/registrations/cancel?token={token}` with Skip-Auth header
- **CancelRegistrationPage.tsx**: Three-state UI (loading → success/error)
- **App.tsx**: Route `/cancel-registration` with lazy loading
- **i18n**: Full translation support in registration namespace (de/en)

**Security Model (Story 10.12 implementation):**
- **UUID Token Lookup**: Cancellation token is a UUID stored as `deregistrationToken` on the registration record (not a JWT; no type-field or expiry validation)
- **Single-Use**: Registration status is set to `cancelled` after successful deregistration; repeated calls return 409 Conflict
- **Anti-Enumeration**: `POST /api/v1/registrations/deregister/by-email` always returns HTTP 200 regardless of whether the email is registered
- **URL Clearing**: Token removed from browser URL after processing (security best practice)
- **No Authentication**: Public endpoint — UUID token provides authorization

**Acceptance Criteria:**
1. ✅ Cancellation token generated during registration alongside confirmation token
2. ✅ Email templates include "Registrierung stornieren" / "Cancel Registration" link
3. ✅ POST `/api/v1/events/{eventCode}/registrations/cancel?token={token}` endpoint validates UUID token and sets registration status to `cancelled`
4. ✅ Cancellation page shows loading spinner during API call
5. ✅ Success state displays confirmation message with "Back to Home" button
6. ✅ Error state shows user-friendly messages for expired/invalid/not-found tokens
7. ✅ Full i18n support for German and English users
8. ✅ Token cleared from URL after successful cancellation
9. ✅ Security config permits public access to cancellation endpoint
10. ✅ All tests updated to reflect new method signature with cancellation token
11. ✅ On successful deregistration, the first waitlisted registration (if any) is automatically promoted to confirmed status and their `waitlistPosition` cleared
12. ✅ The by-email deregistration endpoint (`POST /api/v1/registrations/deregister/by-email`) returns HTTP 200 unconditionally to prevent user-enumeration attacks

**Technical Files Modified:**
- Backend: 6 files (ConfirmationTokenService, EventController, SecurityConfig × 2, RegistrationEmailService, email templates × 2)
- Frontend: 5 files (eventApiClient, CancelRegistrationPage, App, i18n config, translation files × 2)
- Documentation: 1 file (events-api.openapi.yml)
- Tests: 1 file (RegistrationEmailServiceTest)

**Branch:** `feature/anonymous-registration-cancellation`
**Commit:** `8ba3fe1` (15 files changed, 587 insertions, 14 deletions)
**Implementation Date:** 2026-01-11

**Testing Status:**
- ✅ Unit tests passing (all test method signatures updated)
- ✅ TypeScript compilation successful
- ✅ Pre-commit hooks passing (Prettier, ESLint, Checkstyle)
- ⏳ Manual E2E testing pending

---

### UUID-Based Deregistration (Story 10.12)
**Status:** ✅ **COMPLETE** — supersedes the JWT mechanism from Story 4.1.5d

Story 10.12 replaced the JWT cancellation token mechanism with a UUID `deregistrationToken` stored directly on the registration record. The following endpoints are now the canonical deregistration API:

**Endpoints (all public — no authentication required):**

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/registrations/deregister/verify?token={uuid}` | Verify token validity and return registration info; 404 if unknown or already cancelled |
| `POST` | `/api/v1/registrations/deregister` | Cancel registration by UUID token; sets status to `cancelled`; 409 on second call; 404 for unknown token |
| `POST` | `/api/v1/registrations/deregister/by-email` | Initiate deregistration by email; **always returns 200** (anti-enumeration) |

**Behaviour:**
- Cancellation sets registration status to `cancelled` (record is retained in the database)
- On successful cancellation, the first waitlisted registration (if any) is automatically promoted to `registered` status and their `waitlistPosition` is cleared
- The by-email endpoint returns HTTP 200 unconditionally to prevent user-enumeration attacks

---

## Story 4.2: Historical Event Archive Browsing
**(Formerly Story 1.18)**

**User Story:**
As an **visitor or attendee**, I want to browse and view historical BATbern events with rich content and filtering capabilities, so that I can explore 20+ years of conference knowledge and expertise.

**Architecture Integration:**
- **Frontend**: React event browsing components with search and filtering
- **Backend**: Attendee Experience Service for content discovery
- **Database**: PostgreSQL with full-text search indexes
- **CDN**: CloudFront for optimized content delivery
- **Cache**: Caffeine in-memory cache for archive data (15-minute TTL)

**Key Functionality:**
1. **Event Archive Grid**: Card-based layout showing events with session titles, speakers, and companies
2. **Simple Filtering**: Time period and topic filters optimized for 3 events/year
3. **Infinite Scroll**: Automatic loading of more events as user scrolls
4. **Sort Controls**: Sort by date, attendance, session count
5. **Archive Event Detail Pages**: Content-focused pages with session descriptions and presentation downloads
6. **Grid/List Toggle**: Switch between grid and list views with user preference persistence
7. **Session Materials**: Direct PDF download links for all presentations

---

### Wireframe Reference
**Updated Modern Wireframe:**
- **Main Screen**: `docs/wireframes/story-4.2-archive-browsing-modern.md` ✅
  - Event archive grid with session previews
  - Simple time period + topic filters (no year-level nav for 3 events/year)
  - Grid/list view toggle
  - Automatic infinite scroll
  - Sort controls (newest, oldest, most attended, most sessions)
  - Archive event detail page (content-focused, no logistics)
  - Session cards with descriptions and material downloads
  - Simple speaker grid

---

### Acceptance Criteria

**Archive Browse Interface:**
1. **Event Cards**: Cards show event image, title, date, topic, and first 3 sessions with speaker/company
2. **View Options**: Toggle between grid view and list view (persists to localStorage)
3. **Infinite Scroll**: Automatic loading when scrolling within 400px of bottom (20 events per page)
4. **Session Preview**: Each card displays 3 session titles with speaker names and companies
5. **Load Indicator**: Shows "Loading more events..." with progress (X of Y events shown)

**Filtering & Search:**
6. **Filter Panel**: Sidebar (desktop) or bottom sheet (mobile) with time period + topic filters
7. **Time Period Filter**: Radio group (All, Last 5 Years, 2020-2024, 2015-2019, 2010-2014, Before 2010)
8. **Topic Filter**: Checkboxes with event counts (e.g., "Cloud (23)", "Security (18)")
9. **Search Bar**: Full-text search across event titles, topics, speakers (debounced 300ms)
10. **Filter Persistence**: Filters preserved in URL query parameters for shareable links
11. **Clear Filters**: [Clear All Filters] button to reset to default view
12. **Sort Controls**: Dropdown with Newest, Oldest, Most Attended, Most Sessions

**Archive Event Detail Pages:**
13. **Event Header**: Event title, date, topic, description (no venue/logistics for archive)
14. **All Sessions Shown**: Display all 4-8 sessions at once (no pagination)
15. **Session Details**: Each session shows title, speaker(s), full description, materials
16. **Presentation Downloads**: Download PDF buttons with file size (e.g., "Download PDF (2.4 MB)")
17. **Speaker Grid**: Simple grid showing all speakers with photos and companies
18. **Back Navigation**: "← Back to Archive" returns to browse page with filters preserved

**Technical Requirements:**
19. **Performance**: Archive page <2.5s LCP, infinite scroll <300ms, images lazy loaded
20. **Responsive Design**: Mobile-first design (320px+), tablet (768px+), desktop (1024px+)
21. **Accessibility**: WCAG 2.1 Level AA, keyboard navigation, screen reader labels
22. **SEO**: Meta tags, Open Graph, structured data (JSON-LD), canonical URLs
23. **API Efficiency**: Single call with `?include=topics,sessions,speakers` for event cards

**Deliverables:**
- [ ] Archive browse page live with 54+ historical events
- [ ] Filtering and sorting functional (time period + topics)
- [ ] Infinite scroll working smoothly
- [ ] Archive event detail pages showing all sessions with materials
- [ ] Presentation downloads working with direct CDN links
- [ ] Mobile-responsive design verified on all breakpoints
- [ ] Performance <2.5s LCP verified

**Estimated Duration:** 2 weeks

---

## Story 4.3: Historical Content Search & Discovery
**(Formerly Story 5.1)**

**User Story:**
As an **attendee**, I want to search 20+ years of BATbern content with filtering capabilities, so that I can find relevant presentations and expertise quickly.

**Architecture Integration:**
- **Search Engine**: PostgreSQL full-text search with ts_rank scoring
- **Database**: PostgreSQL content metadata with GIN indexes
- **Frontend**: React search interface with faceted search
- **Cache**: Caffeine in-memory cache for search results (5-minute TTL)
- **Backend**: Attendee Experience Service search APIs

**Key Functionality:**
1. **Full-Text Search**: Search across presentation titles, abstracts, speakers, topics, companies
2. **Autocomplete Suggestions**: Real-time search suggestions as user types
3. **Faceted Search**: Multi-dimensional filtering (year, topic, speaker, company, rating)
4. **Content Preview**: Hover preview with abstract excerpts and speaker info
5. **Relevance Ranking**: Intelligent ranking using PostgreSQL ts_rank
6. **Content Actions**: Save to library, rate, review, share, download from search results

---

### Wireframe References
**From docs/wireframes/sitemap.md:**

1. **Content Discovery**: `docs/wireframes/story-5.1-content-discovery.md` ✅
   - Search bar with autocomplete
   - Faceted filter panel (year, topic, speaker, company, rating)
   - Content results grid with thumbnails
   - Sort controls (relevance, date, popularity, rating)
   - Quick actions (save, share, download)
   - Basic recommendations panel (non-AI)

2. **Content Viewer**: `docs/wireframes/story-5.1-content-viewer.md` ✅
   - PDF/presentation viewer
   - Navigation controls
   - Bookmarking interface
   - Rating and review system
   - Related content suggestions

3. **Filter Modal**: `docs/wireframes/story-5.1-filter-modal.md` ✅
   - Mobile-responsive filter interface
   - Multi-select filters with counts
   - Active filters display
   - Reset and apply buttons

---

### Acceptance Criteria

**Search Interface:**
1. **Search Bar**: Prominent search input with icon, placeholder text, keyboard shortcut (/)
2. **Autocomplete**: Real-time suggestions appearing as user types (debounced 300ms)
3. **Search Scope**: Dropdown to limit search (All Content / Presentations / Speakers / Events)
4. **Voice Search**: Voice input option for mobile devices
5. **Search History**: Recent searches displayed for quick re-search (last 10 searches)

**Search Results:**
6. **Content Grid**: Search results displayed as cards with thumbnail, title, speaker, date, relevance score
7. **Sort Options**: Sort by relevance (default), date, popularity, rating
8. **Result Count**: Display total results found ("Showing 1-20 of 453 results")
9. **Highlighted Matches**: Search terms highlighted in titles and abstracts
10. **Quick Actions**: Inline actions on each result (Save, Share, Download, View Details)

**Faceted Filtering:**
11. **Filter Panel**: Sidebar with year range slider, topic checkboxes, speaker multi-select, company filter, rating filter (4+ stars, 3+ stars)
12. **Filter Counts**: Show result counts per filter option (e.g., "Cloud (23)", "Security (15)")
13. **Filter Combinations**: Support multiple filters with AND/OR logic
14. **Dynamic Filters**: Filter options update based on current search results

**Content Discovery:**
15. **Basic Recommendations**: Non-AI recommendations panel with "Popular this month", "Recently viewed", "Related to your searches"
16. **Topic Exploration**: Browse by topic category with visual topic cards
17. **Speaker Discovery**: Featured speakers section with top-rated speakers
18. **Content Types**: Filter by content type (presentations, videos, photos, documents)

**Search Performance:**
19. **Response Time**: Search results return in <500ms for typical queries
20. **Pagination**: Efficient pagination for large result sets (20 results per page)
21. **Index Optimization**: PostgreSQL GIN indexes on searchable fields
22. **Cache Strategy**: Caffeine in-memory caching for common searches (5-minute TTL)

**Deliverables:**
- [ ] Full-text search functional across all content
- [ ] Search returns relevant results in <500ms
- [ ] Autocomplete suggestions working
- [ ] Faceted filtering operational
- [ ] Content preview and quick actions working
- [ ] Mobile-responsive search interface
- [ ] Search accuracy validated with test queries (>85% relevance)

**Estimated Duration:** 1.5 weeks

**Note:** AI-powered recommendations (FR13) have been removed from MVP scope. Basic non-AI recommendations are included.

---

## Epic 4 Success Metrics

**Public Website Success (End of Week 25):**
- ✅ Current event landing page live with next event displayed
- ✅ Event registration flow functional (3-step wizard with QR code)
- ✅ Historical archive browsing operational (54+ events accessible)
- ✅ Full-text search working across 20+ years of content
- ✅ Mobile-responsive design on all pages
- ✅ >99.5% uptime for public-facing pages

**Technical Performance:**
- **Page Load Time**: Landing page <1.5s LCP globally
- **Archive Performance**: Event listings <2.5s LCP
- **Search Speed**: Results return <500ms P95
- **Registration Flow**: Complete flow <30 seconds average
- **CDN Effectiveness**: >90% cache hit rate
- **Mobile Performance**: Lighthouse score >90

**Business Value:**
- **Public Visibility**: BATbern events discoverable by general public
- **Registration Enabled**: Attendees can register for upcoming events
- **Content Accessible**: 20+ years of content searchable and downloadable
- **SEO Success**: Event pages indexed and ranking in search engines
- **User Engagement**: >60% of visitors explore archive or search content
- **Conversion Rate**: >40% of landing page visitors register for events

**User Experience:**
- **Mobile Traffic**: >40% of traffic from mobile devices supported
- **Navigation**: Intuitive navigation across landing page, archive, search
- **Accessibility**: WCAG 2.1 Level AA compliance verified
- **Social Sharing**: Rich previews displayed correctly on social media platforms
- **Performance**: No page load times exceed 3 seconds

---

## Dependencies & Prerequisites

**Required Before Starting Epic 4:**
- ✅ Epic 3 complete (historical data migrated and indexed)
- ✅ Event Management Service APIs operational
- ✅ Attendee Experience Service APIs operational
- ✅ PostgreSQL full-text search indexes built
- ✅ S3/CDN configured for content delivery
- ✅ AWS SES configured for registration emails

**Enables Following Phases:**
- **Phase 2 Epics**: Enhanced workflows can build on public website foundation
- **Epic 5**: Enhanced Organizer Workflows (event publishing integrates with public site)
- **Epic 7**: Attendee Experience Enhancements (personal dashboards extend public features)

---

## Implementation Sequence

**Week 21-22: Current Event Landing Page (Story 4.1)**
- Week 21: Hero section, event details, speaker lineup components
- Week 22: Registration flow (3 steps), confirmation, QR code generation

**Week 23-24: Historical Archive Browsing (Story 4.2)**
- Week 23: Archive grid, timeline view, filtering interface
- Week 24: Event detail pages, presentation downloads, photo galleries

**Week 25: Content Search & Polish (Story 4.3)**
- Week 25 Days 1-3: Search interface, autocomplete, faceted filtering
- Week 25 Days 4-5: Bug fixes, performance optimization, launch preparation

---

## Risk Management

**Technical Risks:**
- **Risk**: PostgreSQL full-text search performance degrades with large result sets
  - **Mitigation**: GIN indexes, Caffeine in-memory caching, pagination limits
- **Risk**: QR code generation library issues or format incompatibility
  - **Mitigation**: Test with multiple QR code scanner apps, use standard format
- **Risk**: Email deliverability issues with registration confirmations
  - **Mitigation**: AWS SES with proper domain verification, SPF/DKIM/DMARC setup

**Schedule Risks:**
- **Risk**: Registration flow taking longer than 1.5 weeks
  - **Mitigation**: Simplify to essential fields, defer advanced features
  - **Mitigation**: Use existing UI component libraries (Material-UI forms)
- **Risk**: Performance optimization requiring more time
  - **Mitigation**: Aggressive caching strategy, CDN for all static assets
  - **Mitigation**: Progressive enhancement approach (basic functionality first)

---

## Transition to Phase 2

**Epic 4 Exit Criteria:**
- [ ] Public website launched and accessible
- [ ] All 3 stories (4.1-4.3) completed
- [ ] Performance benchmarks met for all pages
- [ ] Mobile-responsive design verified
- [ ] SEO optimization confirmed (search engine indexing)
- [ ] Registration flow tested end-to-end
- [ ] Historical content fully accessible

**Phase 1 Complete - Ready for Phase 2 Enhanced Features:**
- Foundation complete: Infrastructure, CRUD, Data, Public Website (Weeks 1-25)
- Public website operational with registrations and content discovery
- Platform ready for workflow automation (Epic 5+)
- Stakeholder demos successful, user feedback positive

---

## Notes & References

**Original Story Locations:**
- Story 4.1 (Current Event Landing Page): `docs/prd/epic-2-event-creation-publishing-stories.md` Story 2.4
- Story 4.2 (Historical Archive): `docs/prd/epic-1-foundation-stories.md` Story 1.18
- Story 4.3 (Content Search): `docs/prd/epic-5-attendee-experience-stories.md` Story 5.1

**Wireframe References:**
- Current Event Landing: `docs/wireframes/story-2.4-*.md`
- Historical Archive: `docs/wireframes/story-1.18-*.md`
- Content Discovery: `docs/wireframes/story-5.1-*.md`

**Epic Reorganization Context:**
- Date: 2025-10-12
- Reason: Consolidate public-facing features into cohesive website epic
- Impact: Public website delivery by Week 25 (vs. Week 38+ in original structure)
- Phase 1 Completion: Epic 4 marks end of Phase 1 (Foundation & Core Functionality)

---

*This epic was created as part of the epic reorganization on 2025-10-12 to consolidate public website features and accelerate public launch to Week 25.*
