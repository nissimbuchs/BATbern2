# Epic 4: Public Website & Content Discovery

## Epic Overview

**Epic Goal**: Launch public-facing BATbern website with current event landing pages, event registration, historical archive browsing, and full-text content search, making 20+ years of BATbern content accessible to the public.

**Deliverable**: Complete public website featuring upcoming events with registration flow, searchable historical archive, and content discovery across presentations, speakers, and topics.

**Architecture Context**:
- **Frontend**: React public pages with server-side rendering for SEO
- **Backend**: Attendee Experience Service + Event Management Service APIs
- **Search**: PostgreSQL full-text search for content discovery
- **CDN**: CloudFront for global content delivery
- **Cache**: Redis for event data and search results

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
- **Cache**: Redis for event data caching (1-minute TTL)
- **Email**: AWS SES for registration confirmations

**Key Functionality:**
1. **Hero Section**: Prominent event display with title, date, countdown timer
2. **Event Details**: Date, time, location with map, free attendance badge, topic description
3. **Speaker Lineup**: Speaker photos, names, companies, session titles
4. **Session Schedule**: Session cards with time, speaker, room, capacity
5. **Event Registration**: 3-step wizard (personal info → session selection → confirmation)
6. **Registration Confirmation**: QR code for check-in, calendar export (iCal), email confirmation
7. **Session Details Modal**: Deep dive into individual sessions with speaker bios

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

**Deliverables:**
- [ ] Public landing page live and accessible
- [ ] Registration flow functional end-to-end
- [ ] QR code generation working
- [ ] Calendar export generating valid iCal files
- [ ] Email confirmations sent within 1 minute
- [ ] Mobile-responsive design verified on all devices
- [ ] SEO meta tags producing rich social media previews
- [ ] Performance benchmarks met (<1.5s LCP)

**Estimated Duration:** 1.5 weeks

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
- **Cache**: Redis for archive data (15-minute TTL)

**Key Functionality:**
1. **Event Archive Grid**: Card-based layout showing events with thumbnails, dates, titles, topics
2. **Timeline View**: Interactive timeline visualization showing 20+ years of events (2000-2025)
3. **Advanced Filtering**: Multi-criteria filters (year, topic, speaker, company, content type)
4. **Sort Controls**: Sort by date, relevance, popularity, topic
5. **Event Detail Pages**: Rich event pages with sessions, speakers, presentations, photo galleries
6. **Grid/List Toggle**: Switch between grid and list views with user preference persistence
7. **Content Preview**: Inline preview modals for presentations without full download

---

### Acceptance Criteria

**Archive Interface:**
1. **Event Grid**: Event cards with thumbnail, title, date, topic, speaker count, view count
2. **Timeline Visualization**: Gantt-style timeline showing events by year/quarter (clickable to filter)
3. **View Options**: Toggle between grid view, list view, and timeline view
4. **Pagination**: Load more functionality with infinite scroll option (20 events per page)
5. **Quick Filters**: Prominent filter chips for common queries (Last Year, Top Rated, Most Popular)

**Filtering & Search:**
6. **Filter Panel**: Sidebar with year slider, topic checkboxes, speaker dropdown, company filter
7. **Search Integration**: Search bar at top filtering events by title, topic, speaker
8. **Active Filters Display**: Visual chips showing applied filters with [x] to remove
9. **Filter Persistence**: Filters preserved in URL query parameters for sharing
10. **Reset Filters**: [Clear All Filters] button to reset to default view

**Event Detail Pages:**
11. **Event Header**: Event title, date, location, topic, attendance count
12. **Session List**: All sessions with time, speaker, title, abstract, presentation link
13. **Speaker Profiles**: Linked speaker profiles with bio and speaking history
14. **Presentation Downloads**: Secure download links for all presentations (track download count)
15. **Photo Gallery**: Event photos in responsive grid with lightbox view
16. **Related Events**: "Similar events you might like" based on topic/speakers

**Technical Requirements:**
17. **Performance Optimization**: Lazy loading images, code splitting, progressive content loading
18. **Responsive Design**: Optimized browsing on mobile, tablet, desktop
19. **Accessibility**: Full keyboard navigation, screen reader compatibility (WCAG 2.1 Level AA)
20. **SEO**: Individual event pages indexed by search engines with rich snippets

**Deliverables:**
- [ ] Event archive interface live with 54+ historical events
- [ ] Filtering and sorting functional across all dimensions
- [ ] Timeline visualization interactive and performant
- [ ] Event detail pages rich with content
- [ ] Presentation downloads working with access controls
- [ ] Mobile-responsive design verified
- [ ] Performance <2.5s Largest Contentful Paint

**Estimated Duration:** 2 weeks

**Reference:** See `docs/prd/epic-1-foundation-stories.md` Story 1.18 for additional wireframe details

---

## Story 4.3: Historical Content Search & Discovery
**(Formerly Story 5.1)**

**User Story:**
As an **attendee**, I want to search 20+ years of BATbern content with filtering capabilities, so that I can find relevant presentations and expertise quickly.

**Architecture Integration:**
- **Search Engine**: PostgreSQL full-text search with ts_rank scoring
- **Database**: PostgreSQL content metadata with GIN indexes
- **Frontend**: React search interface with faceted search
- **Cache**: Redis for search results (5-minute TTL)
- **Backend**: Attendee Experience Service search APIs

**Key Functionality:**
1. **Full-Text Search**: Search across presentation titles, abstracts, speakers, topics, companies
2. **Autocomplete Suggestions**: Real-time search suggestions as user types
3. **Faceted Search**: Multi-dimensional filtering (year, topic, speaker, company, rating)
4. **Content Preview**: Hover preview with abstract excerpts and speaker info
5. **Relevance Ranking**: Intelligent ranking using PostgreSQL ts_rank
6. **Content Actions**: Save to library, rate, review, share, download from search results

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
22. **Cache Strategy**: Redis caching for common searches (5-minute TTL)

**Deliverables:**
- [ ] Full-text search functional across all content
- [ ] Search returns relevant results in <500ms
- [ ] Autocomplete suggestions working
- [ ] Faceted filtering operational
- [ ] Content preview and quick actions working
- [ ] Mobile-responsive search interface
- [ ] Search accuracy validated with test queries (>85% relevance)

**Estimated Duration:** 1.5 weeks

**Reference:** See `docs/prd/epic-5-attendee-experience-stories.md` Story 5.1 for additional wireframe details

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
  - **Mitigation**: GIN indexes, Redis caching, pagination limits
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
