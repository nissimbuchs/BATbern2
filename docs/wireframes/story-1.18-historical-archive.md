# Story: Historical Archive Browser - Wireframe

**Story**: Epic 1, Story 18
**Screen**: Historical Archive Browser
**User Role**: Attendee
**Related FR**: FR2 (Archive)

---

## 5. Historical Archive Browser

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                    BATbern Archive: 20+ Years of Knowledge                    │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── BROWSE BY YEAR ──────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  2025  2024  2023  2022  2021  2020  2019  2018  2017  2016  [Show All Years] │ │
│  │   ●                                                                              │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── 2025 EVENTS ─────────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐ │ │
│  │  │ 🗓️ Spring Conference 2025                          May 15, 2025           │ │ │
│  │  │ Cloud Native Architecture                          8 Speakers • 247 Attendees│ │ │
│  │  │                                                                             │ │ │
│  │  │ Featured Presentations:                                                     │ │ │
│  │  │ • Kubernetes Best Practices - P. Muller            ⭐ 4.8 • ⬇ 523          │ │ │
│  │  │ • Container Security - S. Kim                      ⭐ 4.9 • ⬇ 891          │ │ │
│  │  │ • Zero Trust Architecture - T. Weber               ⭐ 4.7 • ⬇ 445          │ │ │
│  │  │                                                                             │ │ │
│  │  │ [View Event] [Browse Presentations] [Photo Gallery]                        │ │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘ │ │
│  │                                                                                  │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐ │ │
│  │  │ 🗓️ Summer Workshop 2025                           July 20, 2025 (Upcoming)│ │ │
│  │  │ AI/ML in Production                                Registration Open       │ │ │
│  │  │                                                                             │ │ │
│  │  │ Confirmed Speakers:                                                        │ │ │
│  │  │ • Deep Learning at Scale - Dr. A. Mueller                                  │ │ │
│  │  │ • MLOps Best Practices - L. Chen                                           │ │ │
│  │  │ • More speakers being confirmed...                                         │ │ │
│  │  │                                                                             │ │ │
│  │  │ [Register Now] [View Details] [Set Reminder]                               │ │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘ │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── EXPLORE BY TOPIC ────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  📊 Topic Evolution Over Time                                                   │ │
│  │                                                                                  │ │
│  │  Kubernetes     ████████████████████ 142 presentations                         │ │
│  │  DevOps         ███████████████████  134 presentations                         │ │
│  │  Security       ████████████         89 presentations                          │ │
│  │  Cloud Native   ████████████         78 presentations                          │ │
│  │  AI/ML          █████████            67 presentations                          │ │
│  │  Microservices  ████████             56 presentations                          │ │
│  │  Data Eng.      ██████               45 presentations                          │ │
│  │                                                                                  │ │
│  │  [View Topic Timeline] [Compare Topics] [Download Stats]                        │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── SPEAKERS HALL OF FAME ───────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Most Presentations          Highest Rated           Most Downloaded            │ │
│  │  1. Thomas Weber (15)        1. Sara Kim (4.9)      1. Peter Muller (3,421)    │ │
│  │  2. Sara Kim (12)            2. Anna Lopez (4.8)    2. Sara Kim (2,987)        │ │
│  │  3. Peter Muller (12)        3. Marc Baum (4.8)     3. Thomas Weber (2,654)    │ │
│  │                                                                                  │ │
│  │  [View All Speakers] [Speaker Directory] [Become a Speaker]                     │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## API Requirements

**ARCHITECTURAL PRINCIPLE:**
Historical events are accessed through the **Event Management Service** (`/api/v1/events`) with `status` filters (`archived`, `completed`), not through a separate archive API. This ensures:
- Single source of truth for all events
- Consistent API patterns across the application
- Simplified architecture (no separate archive service)
- Public read access controlled via API Gateway authorization

### Initial Page Load APIs

When the Historical Archive Browser screen loads, the following APIs are called to provide the necessary data:

1. **TODO: Statistics API - Year selector**
   - **Future endpoint**: `GET /api/v1/events/statistics/by-year` or similar
   - Returns: List of years with event counts, earliest year, most recent year
   - Used for: Populate year selector with available years
   - **Current workaround**: Query events grouped by year client-side or hardcode known years

2. **GET /api/v1/events?filter={"year":2025,"status":["archived","upcoming"]}&include=speakers,sessions,analytics&sort=-eventDate&limit=20**
   - **Implementation**: Story 1.15a.1 consolidated Events API with resource expansion (AC2, AC4)
   - Returns: Events for selected year with all event card data in a single consolidated call
   - Response includes per event:
     - Event core data: title, date, topic, status
     - speakers: Speaker count and list
     - sessions: Session data with ratings
     - analytics: Attendee count, download counts, rating statistics, photos count
   - Used for: Populate events list for selected year with complete card details
   - **Performance**: Reduced from 21 API calls to 1 (95% reduction for 20 events - was 1 archive call + 20 summary calls)
   - **Caching**: 15-minute Caffeine in-memory cache with automatic invalidation (AC15-16)
   - **Response Time**: <50ms cached, <500ms uncached with all includes (P95)

---

**ARCHITECTURE NOTE - No Separate Archive API:**
Historical events are accessed through the Event Management Service using `status` filters, not a separate archive API.

**Previous approach:**
- Separate `/api/v1/archive/events` endpoint
- 1 call per event to GET /api/v1/events/{eventId}/summary (20 additional calls for 20 events)
- Total: 21 API calls for initial page load

**Current consolidated approach (Story 1.15a.1):**
Uses Event Management API with `?include=speakers,sessions,analytics` to fetch all event card data in one call. This provides:
- Page load time: ~90% faster (from ~5s to <500ms for 20 events)
- Single loading state for entire year's events
- Instant event card rendering with all details
- Reduced database load (1 optimized query vs 21 separate queries)
- Better caching efficiency (single cache entry vs 21 entries, 15-minute TTL)
- Atomic data consistency (all events at same point in time)
- In-memory caching: <50ms cached response time with Caffeine

3. **TODO: Statistics API - Topic usage**
   - **Future endpoint**: `GET /api/v1/topics/statistics` (enhance existing topic backlog endpoint)
   - Query params: minPresentations (5), sortBy (count)
   - Returns: Topic statistics with presentation counts, year ranges, growth trends, popularity over time
   - Used for: Populate "Explore by Topic" section with topic bars
   - **Current workaround**: Aggregate content data client-side or defer this feature

5. **TODO: Statistics API - Speakers hall of fame**
   - **Future endpoint**: `GET /api/v1/speakers/statistics/hall-of-fame`
   - Query params: categories (most-presentations, highest-rated, most-downloaded)
   - Returns: Top speakers in each category with counts, ratings, download stats, profile links
   - Used for: Populate "Speakers Hall of Fame" section
   - **Current workaround**: Aggregate speaker data client-side or defer this feature

6. **GET /api/v1/content?filter={"eventId":"{eventId}","featured":true}&sort=-rating&limit=3**
   - Query params: Filter by eventId and featured flag, sort by rating, limit to 3
   - Returns: Featured presentations with title, speaker, rating, download count
   - Used for: Display top presentations for each event card
   - **Consolidated**: Uses Story 1.20 unified search (was /api/v1/archive/featured-presentations)
   - **Benefit**: Reuses content search infrastructure with filtering

---

## Action APIs

### Event Interactions

1. **GET /api/v1/events/{eventId}/details**
   - Returns: Full event details with agenda, all presentations, speakers, venue, statistics, photos, documents
   - Used for: Load event detail view when clicking [View Event]

2. **GET /api/v1/content?filter={"eventId":"{eventId}"}&sort={sortBy}&limit=50**
   - Query params: Filter by eventId, sort by rating/downloads/date, limit to 50
   - Returns: All presentations for event with metadata, files, ratings
   - Used for: Navigate to presentations list from [Browse Presentations]
   - **Consolidated**: Uses Story 1.20 unified search (was /api/v1/events/{eventId}/presentations)
   - **Benefit**: Consistent search/filter/sort across all content queries

3. **GET /api/v1/events/{eventId}/photos**
   - Query params: limit (100), quality (thumbnail|full)
   - Returns: Event photos with URLs, captions, timestamps, photographer credits
   - Used for: Load photo gallery from [Photo Gallery]

4. **GET /api/v1/events/{eventId}/attendees**
   - Query params: includeProfile (false), limit (500)
   - Returns: Attendee list with names, organizations, attendance status (privacy-aware)
   - Used for: Display attendee list from [Attendee List]

### Upcoming Event Actions

5. **POST /api/v1/attendees/{userId}/reminders**
   - Payload: `{ eventId, reminderDate, reminderType: "email|push", advanceNotice: "1-day|1-week|custom" }`
   - Response: Reminder created, confirmation, scheduled time
   - Used for: Create event reminder from [Set Reminder] button

6. **GET /api/v1/events/{eventId}/registration-status**
   - Returns: Registration availability, pricing, capacity, early-bird status, prerequisites
   - Used for: Check registration status before navigating to registration

### Topic Analysis

7. **TODO: Statistics API - Topic timeline**
   - **Future endpoint**: `GET /api/v1/topics/{topicName}/timeline`
   - Query params: includeEvents (true)
   - Returns: Topic evolution over time with yearly counts, associated events, trend analysis, related topics
   - Used for: Display topic timeline from [View Topic Timeline]

8. **TODO: Statistics API - Topic comparison**
   - **Future endpoint**: `GET /api/v1/topics/compare`
   - Query params: topics: [], startYear, endYear
   - Returns: Comparison data with trend lines, correlation analysis, speaker overlap, presentation counts
   - Used for: Compare topics from [Compare Topics]

9. **TODO: Statistics API - Topic export**
   - **Future endpoint**: `GET /api/v1/topics/export`
   - Query params: format (csv|pdf|json), includeTimeline (true)
   - Returns: Download URL for topic statistics, expiration timestamp
   - Used for: Download statistics from [Download Stats]

### Speaker Discovery

10. **GET /api/v1/speakers/directory**
    - Query params: sortBy (presentations|rating|downloads), filter, limit (50)
    - Returns: Complete speaker directory with profiles, statistics, expertise, availability
    - Used for: Navigate to speaker directory from [View All Speakers]

11. **GET /api/v1/speakers/{speakerId}/profile**
    - Returns: Speaker profile with bio, presentations, ratings, topics, contact info
    - Used for: View speaker profile from speaker name click

12. **GET /api/v1/speakers/{speakerId}/presentations/history**
    - Query params: limit (20)
    - Returns: All presentations by speaker with events, dates, ratings, downloads
    - Used for: View speaker's presentation history

### Year Navigation

13. **GET /api/v1/events?filter={"year":{year},"status":["archived","completed"]}&include=speakers,sessions,analytics**
    - **Implementation**: Story 1.15a.1 consolidated Events API (AC2, AC4)
    - Query params: year, status filter
    - Returns: Events for newly selected year with all event card data
    - Used for: Load events when year is changed in selector
    - **Consolidated**: Uses Event Management API (was /api/v1/archive/events)
    - **Caching**: 15-minute Caffeine cache, <50ms cached response

14. **TODO: Statistics API - Year range statistics**
    - **Future endpoint**: `GET /api/v1/events/statistics/year-range`
    - Query params: startYear, endYear
    - Returns: Event summary statistics across year range
    - Used for: Show multi-year statistics when expanding year selector

### Downloads & Bookmarks

15. **GET /api/v1/content/{contentId}/download**
    - Returns: Download URL, file metadata, expiration timestamp
    - Used for: Download presentation files
    - **Consolidated**: Story 1.20 standard download endpoint (was /api/v1/presentations/{presentationId}/download)
    - **Benefit**: Consistent download pattern across all content types

16. **POST /api/v1/content/{contentId}/reviews** (with bookmark metadata)
    - Payload: `{ type: "bookmark", userId, metadata: { eventId, notes } }`
    - Response: Bookmark created, confirmation
    - Used for: Bookmark event or presentation for later
    - **Consolidated**: Reviews endpoint can handle bookmarks (was POST /api/v1/attendees/{userId}/bookmarks)
    - **Benefit**: Unified engagement tracking

17. **DELETE /api/v1/content/{contentId}/reviews/{reviewId}**
    - Response: Bookmark removed
    - Used for: Remove bookmark
    - **Consolidated**: Standard review deletion (was DELETE /api/v1/attendees/{userId}/bookmarks/{bookmarkId})
    - **Benefit**: Consistent deletion pattern

### Search & Filter

18. **GET /api/v1/content?filter={}&facets=year,speaker,topic&include=event**
    - Query params: `filter={"$or":[{"title":{"$contains":"query"}},{"tags":{"$in":["topic"]}}]}`, year, speaker, minRating, sortBy, facets
    - Returns: Search results with events, presentations, speakers matching criteria, facet aggregations
    - Used for: Search archive content
    - **Consolidated**: Story 1.20 unified search (was /api/v1/archive/search)
    - **Benefit**: Single search API for archive, discovery, and all content

19. **GET /api/v1/events?filter={"year":{year},"topic":{topic}}&page=1&limit=50**
    - Query params: Filter by year, topic, speaker count, attendee count, min rating
    - Returns: Filtered event list
    - Used for: Filter events by criteria
    - **Consolidated**: Standard events API with filtering (was /api/v1/archive/events/filter)
    - **Benefit**: Reuses events infrastructure with rich filters

---

## Navigation Map

### Primary Navigation Actions

1. **← Back button** → Navigate back to previous screen
   - Usually `Current Event Landing` (story-2.4-current-event-landing.md)
   - Or homepage
   - Or referrer page

2. **Page title (BATbern Archive)** → Refreshes archive browser
   - Reloads current year
   - Resets filters
   - Scrolls to top

3. **Year selector button (2025, 2024, etc.)** → Changes selected year
   - Loads events for selected year
   - Updates URL parameter
   - Updates event list
   - No screen navigation

4. **[Show All Years] button** → Expands year selector
   - Shows all available years
   - Scrollable year list
   - Multi-select option
   - No screen navigation

### Event Card Actions

5. **Event card click** → Navigate to `Event Detail Screen`
   - Full event information
   - Complete agenda
   - All presentations
   - Photos and resources

6. **[View Event] button** → Navigate to `Event Detail Screen`
   - Same as event card click
   - Full event view
   - Detailed information

7. **[Browse Presentations] button** → Navigate to `Event Presentations List Screen`
   - Filtered presentation list
   - Sortable by rating/downloads
   - Search within event
   - Download options

8. **[Photo Gallery] button** → Navigate to `Event Photo Gallery Screen`
   - Grid view of photos
   - Lightbox display
   - Download options
   - Social sharing

9. **[Attendee List] button** → Navigate to `Event Attendee List Screen`
   - Attendee directory
   - Organization breakdown
   - Privacy-aware display
   - Networking connections

10. **Featured presentation click** → Navigate to `Presentation Detail Screen`
    - Presentation view/download
    - Speaker information
    - Rating and comments
    - Related presentations

11. **Speaker name click (P. Muller)** → Navigate to `Speaker Profile Screen`
    - Speaker bio
    - All presentations
    - Expertise areas
    - Contact options

12. **Rating display (⭐ 4.8)** → Shows rating details tooltip
    - Rating breakdown
    - Comment count
    - Rating distribution
    - No navigation

13. **Download count (⬇ 523)** → Shows download statistics
    - Download trend
    - Popular formats
    - Recent downloads
    - No navigation

### Upcoming Event Actions

14. **[Register Now] button** → Navigate to `Event Registration Screen` (story-2.4-event-registration.md)
    - Registration form
    - Ticket selection
    - Payment processing
    - Confirmation

15. **[View Details] button** → Navigate to `Upcoming Event Detail Screen`
    - Event information
    - Confirmed speakers
    - Registration status
    - Schedule preview

16. **[Set Reminder] button** → Opens reminder configuration modal
    - Date/time selector
    - Notification type
    - Advance notice options
    - Creates reminder
    - No screen navigation

### Topic Exploration Actions

17. **Topic bar click (Kubernetes)** → Navigate to `Topic Timeline Screen`
    - Topic evolution visualization
    - Year-by-year breakdown
    - Associated events
    - Top presentations

18. **[View Topic Timeline] button** → Navigate to `Topic Timeline Visualization`
    - Interactive timeline
    - Trend analysis
    - Event correlation
    - Speaker contributions

19. **[Compare Topics] button** → Navigate to `Topic Comparison Screen`
    - Multi-topic comparison
    - Trend lines
    - Speaker overlap
    - Popularity metrics

20. **[Download Stats] button** → Triggers statistics download
    - Format selection
    - Generates export
    - Downloads file
    - No screen navigation

### Speaker Hall of Fame Actions

21. **Speaker name click in hall of fame** → Navigate to `Speaker Profile Screen`
    - Speaker details
    - Presentation history
    - Statistics
    - Contact information

22. **[View All Speakers] button** → Navigate to `Speaker Directory Screen`
    - Complete speaker list
    - Search and filter
    - Sort options
    - Profile previews

23. **[Speaker Directory] button** → Navigate to `Speaker Directory Screen`
    - Same as View All Speakers
    - Searchable directory
    - Advanced filters

24. **[Become a Speaker] button** → Navigate to `Speaker Application Form`
    - Application wizard
    - Profile creation
    - Topic selection
    - Submission

### Secondary Navigation (Data Interactions)

25. **Event date hover** → Shows event timeline tooltip
    - Event duration
    - Days until/since
    - Related events
    - No navigation

26. **Topic presentation count hover** → Shows topic details
    - Yearly breakdown
    - Top speakers
    - Recent trends
    - No navigation

27. **Hall of fame metric hover** → Shows detailed statistics
    - Breakdown by year
    - Rating distribution
    - Download sources
    - No navigation

### Event-Driven Navigation

28. **New archive year added** → Updates year selector
    - Adds new year option
    - Updates event counts
    - May show notification
    - No automatic navigation

29. **Bookmark added** → Shows confirmation
    - Success notification
    - Bookmark count updated
    - No automatic navigation

30. **Download started** → Shows download progress
    - Progress indicator
    - Cancel option
    - Completion notification
    - No automatic navigation

31. **Search results returned** → Updates display
    - Filtered results shown
    - Result count updated
    - Highlights matches
    - No automatic navigation

32. **Reminder created** → Shows confirmation
    - Success notification
    - Reminder details
    - Calendar sync option
    - No automatic navigation

---
