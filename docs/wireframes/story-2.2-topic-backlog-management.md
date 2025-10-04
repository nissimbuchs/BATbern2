# Story 2.2: Smart Topic Backlog Management - Wireframe

**Story**: Epic 2, Story 2.2 - Topic & Content Management Service
**Screen**: Smart Topic Backlog Management
**User Role**: Organizer
**Related FR**: FR18 (Intelligent Topic Backlog)

---

## Smart Topic Backlog Management (FR18)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back to Dashboard                Topic Backlog Manager                    [+ Add]  │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── FILTERS ───────────────────────┬─── VIEW OPTIONS ──────────────────────────┐  │
│  │                                   │                                            │  │
│  │ Category: [All Categories ▼]      │ Sort by: [Partner Priority ▼]             │  │
│  │ Status: [Available ▼]             │ Group by: [Category ▼]                     │  │
│  │ Last Used: [Any Time ▼]          │ View: [● Heat Map] [○ List] [○ Board]     │  │
│  │ Partner Interest: [High ▼]        │                                            │  │
│  └───────────────────────────────────┴────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── TOPIC HEAT MAP ───────────────────────────────────────────────────────────┐   │
│  │                                                                               │   │
│  │  Usage History (Darker = More Recent/Frequent)                               │   │
│  │                                                                               │   │
│  │        2020      2021      2022      2023      2024      2025                │   │
│  │  Q1 Q2 Q3 Q4 Q1 Q2 Q3 Q4 Q1 Q2 Q3 Q4 Q1 Q2 Q3 Q4 Q1 Q2 Q3 Q4 Q1 Q2          │   │
│  │                                                                               │   │
│  │  Cloud Native     ██ ░░ ░░ ░░ ██ ░░ ░░ ░░ ░░ ░░ ██ ░░ ░░ ░░ ░░ [SELECTED]  │   │
│  │  AI/ML            ░░ ░░ ░░ ░░ ░░ ░░ ██ ░░ ░░ ██ ██ ░░ ░░ ██ ░░ ░░          │   │
│  │  DevOps           ██ ██ ░░ ██ ░░ ██ ░░ ░░ ██ ░░ ░░ ██ ░░ ░░ ░░ ░░          │   │
│  │  Security         ░░ ░░ ██ ░░ ░░ ░░ ██ ░░ ░░ ██ ░░ ░░ ██ ░░ ░░ ░░          │   │
│  │  Blockchain       ██ ██ ██ ░░ ░░ ░░ ░░ ░░ ░░ ░░ ░░ ░░ ░░ ░░ ⚠️ Stale       │   │
│  │  IoT              ░░ ░░ ░░ ██ ░░ ░░ ░░ ██ ░░ ░░ ░░ ░░ ░░ ░░ ✨ Trending    │   │
│  │                                                                               │   │
│  └───────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                       │
│  ┌─── SELECTED TOPIC DETAILS ───────────────────────────────────────────────────┐   │
│  │                                                                               │   │
│  │  Topic: Cloud Native Architecture                    ID: TOP-2025-014         │   │
│  │  Category: Infrastructure                           Status: Available         │   │
│  │                                                                               │   │
│  │  ┌─── Performance ───┐  ┌─── Partner Interest ───┐  ┌─── AI Analysis ───┐   │   │
│  │  │                    │  │                        │  │                     │   │   │
│  │  │ Attendance: 187    │  │ Votes: 24 👍           │  │ Similarity Score:   │   │   │
│  │  │ Rating: 4.6/5      │  │ Priority: HIGH         │  │ 15% to "Kubernetes" │   │   │
│  │  │ Downloads: 1,247   │  │ Sponsors: UBS, Swiss Re│  │ 22% to "Microserv." │   │   │
│  │  │ Engagement: 89%    │  │                        │  │                     │   │   │
│  │  └────────────────────┘  └────────────────────────┘  └───────────────────┘   │   │
│  │                                                                               │   │
│  │  Last Used: March 2023 (Spring Conference)                                   │   │
│  │  Suggested Wait: 6+ months before reuse                                      │   │
│  │  Related Topics: Kubernetes, Microservices, Container Orchestration          │   │
│  │                                                                               │   │
│  │  Partner Comments:                                                            │   │
│  │  "Very relevant for our teams" - Credit Suisse                               │   │
│  │  "Would like deeper dive on security aspects" - Swiss Re                     │   │
│  │                                                                               │   │
│  │  [Select for Event] [View History] [Similar Topics] [Partner Feedback]       │   │
│  └───────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                       │
│  ┌─── AI SUGGESTIONS ─────────────────────────────────────────────────────────────┐  │
│  │ 🤖 Based on industry trends and partner interests:                             │  │
│  │                                                                                 │  │
│  │ • "Platform Engineering" - New trend, high search volume, no previous events   │  │
│  │ • "FinOps & Cloud Cost" - 3 partners requested, aligns with cost focus        │  │
│  │ • "Zero Trust Security" - Compliance deadline approaching for banks           │  │
│  │                                                           [See All →]          │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Heat Map View**: Visual representation of topic usage over time
- **Topic Cards**: Click to see detailed metrics and history
- **Filter Panel**: Multi-criteria filtering for topic discovery
- **AI Suggestions**: ML-powered recommendations for new topics
- **Partner Feedback**: Direct input from sponsors on topic interest
- **Similarity Analysis**: Find related topics to avoid repetition

## Functional Requirements Met

- **FR18**: Intelligent topic backlog with usage tracking
- **Historical Analysis**: Track topic usage over 20+ years
- **Partner Integration**: Incorporate sponsor topic preferences
- **AI Recommendations**: Machine learning suggests optimal topics
- **Reuse Prevention**: Alert when topics used too frequently
- **Performance Metrics**: Track attendance, ratings, downloads

## User Interactions

1. **Select Topic**: Choose topic for upcoming event from backlog
2. **View Heat Map**: Visualize usage patterns across quarters/years
3. **Filter Topics**: Apply multiple filters to find suitable topics
4. **Check Similarity**: Avoid selecting similar topics too close together
5. **View Partner Interest**: See which sponsors want specific topics
6. **Add New Topic**: Create new topic based on trends or requests

## Technical Notes

- Heat map visualization using D3.js
- ML similarity scoring based on content analysis
- Integration with partner voting system (FR8)
- Real-time trend analysis from industry sources
- Historical data retention for pattern analysis
- Automated staleness detection for unused topics

---

## API Requirements

### Consolidated APIs (Story 1.21)

**Note**: This wireframe now uses the consolidated Topics APIs from Story 1.21, which reduced 52 endpoints to 12 endpoints (77% reduction). The consolidation provides better performance through reduced HTTP requests and unified query patterns.

### Initial Page Load APIs

When the Topic Backlog Manager screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/topics?filter={}&sort={}&page={}&include=history,metrics**
   - Query params: filter (status, category, etc.), sort (-lastUsed), limit (50), include=history,metrics
   - Returns: All topics with usage history, performance metrics, status, last used date, category
   - Used for: Populate heat map and topic list
   - **Consolidation**: Replaces /organizers/{organizerId}/topics/backlog with unified filtering

2. **GET /api/v1/topics/{id}/history**
   - Query params: startYear (2020), endYear (2025), groupBy (quarter), page, limit
   - Returns: Topic usage data by quarter/year for heat map visualization, frequency, recency, gaps
   - Used for: Render heat map visualization
   - **Consolidation**: Standard pagination on history endpoint

3. **GET /api/v1/topics?filter={}&include=statistics**
   - Query params: filter (groupBy=category), include=statistics
   - Returns: Topic categories with topic counts, most used, trending indicators
   - Used for: Populate category filter dropdown
   - **Consolidation**: Uses main list endpoint with groupBy filter

4. **GET /api/v1/topics/ai-suggestions?context={}**
   - Query params: limit (3), context (organizer preferences, event planning)
   - Returns: AI-generated topic suggestions with reasoning, industry trends, partner interest alignment, search volume
   - Used for: Populate AI suggestions panel
   - **Consolidation**: Context parameter replaces multiple specialized endpoints

5. **GET /api/v1/topics?filter={}&include=votes**
   - Query params: filter (hasVotes=true), include=votes, sort=-votes
   - Returns: Aggregated partner interest data with vote counts, priority ratings, sponsor names, comments
   - Used for: Display partner interest metrics for topics
   - **Consolidation**: Uses ?include=votes parameter

6. **GET /api/v1/topics/statistics?groupBy=category**
   - Query params: groupBy (category, status), metrics (counts, trends)
   - Returns: Filter options (categories, statuses, date ranges, interest levels) with counts
   - Used for: Populate filter dropdowns
   - **Consolidation**: New statistics endpoint with flexible grouping

---

## Action APIs

### Topic Selection & Management

1. **GET /api/v1/topics/{id}?include=history,votes,metrics,insights**
   - Query params: include (history, votes, metrics, insights)
   - Returns: Detailed topic information with performance metrics (attendance, ratings, downloads, engagement), partner interest (votes, priority, sponsors, comments), last used date, suggested wait period
   - Used for: Display selected topic details panel
   - **Consolidation**: Single endpoint with ?include parameter replaces multiple specialized endpoints

2. **GET /api/v1/topics/{id}/similarity?threshold=0.15**
   - Query params: threshold (0.15), limit (10)
   - Returns: Similar topics with ML similarity scores, overlap analysis, topic names, IDs
   - Used for: Show similar topics from [Similar Topics] button
   - **Consolidation**: Dedicated similarity endpoint with configurable threshold

3. **GET /api/v1/topics/{id}/history?page={}&limit={}**
   - Query params: page, limit, startDate, endDate
   - Returns: Complete usage history with events, dates, speakers, attendance, ratings, feedback
   - Used for: View topic history from [View History] button
   - **Consolidation**: Standard pagination on history sub-resource

4. **POST /api/v1/events/{eventId}/topics**
   - Payload: `{ topicId, primaryTopic: boolean, notes }`
   - Response: Topic assigned to event, validation warnings (if recently used), assignment confirmation
   - Used for: Select topic for event from [Select for Event] button
   - **Note**: Event management API (not part of Topics API consolidation)

5. **POST /api/v1/topics**
   - Payload: `{ title, category, description, suggestedBy, source: "partner-request|trend-analysis|manual", keywords: [], relatedTopics: [] }`
   - Response: Topic created, topic ID, backlog confirmation
   - Used for: Add new topic from [+ Add] button
   - **Consolidation**: Standard RESTful POST on /topics

6. **PUT /api/v1/topics/{id}**
   - Payload: `{ title, category, description, status, keywords, relatedTopics }`
   - Response: Topic updated, revalidated, similarity scores recalculated
   - Used for: Edit topic details
   - **Consolidation**: Standard RESTful PUT on /topics/{id}

7. **POST /api/v1/topics/{id}/archive**
   - Payload: `{ archiveReason, alternativeTopicId (optional) }`
   - Response: Topic archived, usage history preserved
   - Used for: Remove topic from backlog
   - **Consolidation**: Dedicated archive action endpoint

### Filtering & Search

8. **GET /api/v1/topics?filter={}&sort={}&page={}**
   - Query params: filter (category, status, lastUsed date range, partnerInterest level), sort, groupBy, page, limit
   - Returns: Filtered topic list with matching criteria, count, pagination
   - Used for: Apply filters from filter panel
   - **Consolidation**: Unified filtering via JSON filter parameter - no new endpoints needed for different filter combinations

9. **GET /api/v1/topics?filter={"title":{"$contains":"keyword"}}**
   - Query params: filter (text search in title/description), category, minRating, maxStaleness
   - Returns: Search results with matching topics, relevance scores, highlights
   - Used for: Search topics by keyword
   - **Consolidation**: Search integrated into main list endpoint with $contains operator

10. **GET /api/v1/topics/trending?timeframe=month&type=industry**
    - Query params: timeframe (week|month|quarter|year), type (community|industry|combined)
    - Returns: Trending topics with trend scores, growth rates, sources
    - Used for: Discover trending topics
    - **Consolidation**: Single trending endpoint with type parameter consolidates community-trends and industry-trends

### AI & Analytics

11. **GET /api/v1/topics/ai-suggestions?context={}&limit=20**
    - Query params: limit (20), context (eventContext with date, audience, goals)
    - Returns: Comprehensive AI suggestions with detailed reasoning, data sources, confidence scores, implementation tips
    - Used for: View all AI suggestions from [See All →]
    - **Consolidation**: Context parameter provides event-specific suggestions without separate endpoints

12. **GET /api/v1/topics?filter={"ids":{"$in":[...]}}&include=insights**
    - Query params: filter (topicIds array), include=insights, context (eventContext)
    - Response: AI analysis of topic fit, compatibility, recommendations, optimal timing
    - Used for: Get AI analysis on multiple topic candidates
    - **Consolidation**: Use ?include=insights parameter on main list endpoint with ID filtering

13. **GET /api/v1/topics/statistics?groupBy=staleness**
    - Query params: groupBy=staleness, metrics (stalenessScores, lastUsed, recommendations)
    - Returns: Topics with staleness scores, last used dates, recommended refresh timing, overused/underused topics
    - Used for: Identify stale or overused topics
    - **Consolidation**: Statistics endpoint with staleness grouping replaces dedicated staleness-analysis endpoint

### Partner Feedback

14. **GET /api/v1/topics/{id}/votes?includeComments=true**
    - Query params: includeComments (true), page, limit
    - Returns: Partner votes, priority ratings, comments, sponsor names, voting history
    - Used for: View partner feedback from [Partner Feedback] button
    - **Consolidation**: Dedicated votes sub-resource with pagination

15. **GET /api/v1/topics?filter={"suggestedBy":"partner"}&sort=-createdAt**
    - Query params: filter (suggestedBy=partner, status), sort, priority
    - Returns: Partner-requested topics with justifications, requestor info, urgency
    - Used for: Review partner topic requests
    - **Consolidation**: Filter on main list endpoint for partner-requested topics

### Export & Reporting

16. **GET /api/v1/topics?filter={}&format=csv&include=history,metrics**
    - Query params: format (csv|pdf|xlsx), filter (criteria), include (history, metrics)
    - Returns: Download URL for topic backlog export
    - Used for: Export topic data
    - **Consolidation**: Export as format parameter on main list endpoint

17. **GET /api/v1/topics/statistics?groupBy=period&metrics=usage,performance,interest**
    - Query params: groupBy (period), metrics (usage, performance, interest), timeframe (year, all-time)
    - Returns: Topic analytics with usage patterns, performance trends, interest evolution, gaps
    - Used for: View topic analytics dashboard
    - **Consolidation**: Statistics endpoint with flexible metrics selection

### View Configuration

18. **PUT /api/v1/topics/view-preferences** (User preferences - not part of Topics API)
    - Payload: `{ viewType: "heatmap|list|board", sortBy, groupBy, defaultFilters }`
    - Response: Preferences saved
    - Used for: Save view configuration
    - **Note**: User preferences API (separate from Topics domain)

19. **GET /api/v1/topics/{id}/history?startYear=2020&endYear=2025&granularity=quarter**
    - Query params: startYear, endYear, granularity (quarter|month), format=heatmap
    - Returns: Heat map data optimized for visualization, usage intensity, color coding data
    - Used for: Refresh heat map visualization
    - **Consolidation**: History endpoint with heatmap format option

---

## Navigation Map

### Primary Navigation Actions

1. **← Back to Dashboard button** → Navigate to `Organizer Dashboard`
   - Returns to main dashboard
   - May be Event Management Dashboard (story-1.16-event-management-dashboard.md)

2. **[+ Add] button** → Opens new topic creation modal
   - Topic creation form
   - Category selection
   - Related topics linking
   - Submits via API
   - No screen navigation

3. **Topic title in header** → Refreshes topic backlog
   - Reloads current view
   - Resets to default filters
   - No screen change

### Filter Panel Actions

4. **Category dropdown** → Filters by category
   - Updates topic list
   - Refreshes heat map
   - No screen navigation

5. **Status dropdown** → Filters by status
   - Available, Used, Stale options
   - Updates display
   - No screen navigation

6. **Last Used dropdown** → Filters by date range
   - Time-based filtering
   - Updates visualization
   - No screen navigation

7. **Partner Interest dropdown** → Filters by interest level
   - High, Medium, Low options
   - Updates topic list
   - No screen navigation

8. **Sort by dropdown** → Changes sort order
   - Partner Priority, Last Used, Rating, etc.
   - Reorders topics
   - No screen navigation

9. **Group by dropdown** → Changes grouping
   - Category, Status, Year options
   - Reorganizes display
   - No screen navigation

10. **View mode buttons** → Switches visualization
    - Heat Map, List, Board views
    - Changes layout
    - Preserves filters
    - No screen navigation

### Heat Map Interactions

11. **Topic row click in heat map** → Selects topic
    - Highlights row
    - Loads topic details panel
    - Shows metrics
    - No screen navigation

12. **Quarter cell click** → Shows usage details
    - Event information tooltip
    - Speakers
    - Attendance
    - No navigation

13. **Heat map cell hover** → Shows tooltip
    - Event name
    - Date
    - Quick metrics
    - No navigation

### Topic Details Panel Actions

14. **[Select for Event] button** → Opens event selection modal
    - Event picker
    - Assignment confirmation
    - Staleness warnings if applicable
    - Creates assignment
    - No screen navigation

15. **[View History] button** → Navigate to `Topic Usage History Screen`
    - Detailed history view
    - All events using topic
    - Performance trends
    - Speaker list

16. **[Similar Topics] button** → Shows similar topics modal
    - Similarity scores
    - Topic comparisons
    - Avoid repetition warnings
    - No screen navigation (modal)

17. **[Partner Feedback] button** → Navigate to `Partner Topic Voting Interface` (story-6.4-topic-voting.md)
    - Partner voting screen
    - Comments and requests
    - Priority rankings

18. **Performance metric click** → Shows detailed breakdown
    - Attendance over time
    - Rating distribution
    - Download patterns
    - Engagement analysis
    - No navigation (tooltip/modal)

19. **Partner vote count click** → Shows voting details
    - Partner names
    - Individual votes
    - Priority levels
    - Comments
    - No navigation (tooltip/modal)

20. **Sponsor name click** → Navigate to `Partner Profile Screen`
    - Partner details
    - All topic interests
    - Sponsorship history
    - Contact information

21. **Related topic link click** → Selects related topic
    - Changes selected topic
    - Updates details panel
    - No screen navigation

### AI Suggestions Actions

22. **AI suggestion item click** → Shows suggestion details
    - Full reasoning
    - Data sources
    - Confidence score
    - Implementation suggestions
    - No navigation (expansion)

23. **[See All →] button** → Navigate to `AI Topic Suggestions Screen`
    - Comprehensive suggestions list
    - Trend analysis
    - Detailed recommendations
    - Accept/reject options

24. **Suggestion accept** → Adds topic to backlog
    - Creates topic
    - Pre-filled from suggestion
    - Opens topic form
    - No screen navigation

### Secondary Navigation (Data Interactions)

25. **Stale indicator (⚠️)** → Shows staleness warning
    - Last used date
    - Recommended action
    - Similar active topics
    - No navigation

26. **Trending indicator (✨)** → Shows trend details
    - Trend source
    - Growth metrics
    - Industry context
    - No navigation

27. **Category tag click** → Filters by category
    - Applies category filter
    - Updates display
    - No screen navigation

28. **Partner comment hover** → Shows full comment
    - Complete feedback text
    - Comment date
    - Commenter name
    - No navigation

### Event-Driven Navigation

29. **New partner vote received** → Updates interest metrics
    - Increments vote count
    - Updates priority indicator
    - May show notification
    - No automatic navigation

30. **Topic selected for event** → Updates status
    - Changes status indicator
    - Updates last used date
    - Refreshes heat map
    - No automatic navigation

31. **New AI suggestion generated** → Adds to suggestions
    - Appears in AI panel
    - Shows new indicator
    - May show notification
    - No automatic navigation

32. **Topic becomes stale** → Shows warning
    - Adds stale indicator
    - Updates status
    - May trigger notification
    - No automatic navigation

33. **Filter applied** → Updates display
    - Shows filtered results
    - Updates counts
    - Maintains selection
    - No automatic navigation

---