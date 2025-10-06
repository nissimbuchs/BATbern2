# All Topics Browser Screen - Wireframe

**Story**: Epic 6, Story 6.1 - Partner Topic Voting Integration
**Screen**: All Topics Browser Screen
**User Role**: Partner (primary), Organizer (view-only)
**Related FR**: FR8 (Topic Influence)

---

## Visual Wireframe

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back to Topic Voting            Browse All Topics                 My Votes: 5/10   │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── SEARCH & FILTERS ──────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  [🔍 Search topics...]                                          [Clear Filters]  │ │
│  │                                                                                   │ │
│  │  Category: [All ▾]  Popularity: [All ▾]  My Votes: [All ▾]  Sort: [Votes ▾]    │ │
│  │                                                                                   │ │
│  │  Quick Filters: [Most Popular] [Trending 📈] [New Topics ✨] [Your Interests]   │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── TOPIC STATISTICS ──────────────────────────────────────────────────────────────┐ │
│  │  Total Topics: 47  |  Trending: 8  |  Your Selections: 5  |  Most Voted: Zero   │ │
│  │  Trust Security (12 votes)                                                        │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── TOPIC GRID ────────────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  Showing 47 topics                                         [Grid ▣] [List ☰]     │ │
│  │                                                                                   │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ 🔥 AI/ML in Financial Services                               ✅ IN YOUR LIST│  │ │
│  │  │                                                                              │  │ │
│  │  │ Practical AI implementation guidance for financial institutions              │  │ │
│  │  │ Category: Technology & Innovation                                           │  │ │
│  │  │                                                                              │  │ │
│  │  │ 📊 Votes: ████████ 12 partners (25%)                                        │  │ │
│  │  │ 📈 Trending: +4 votes this week                                             │  │ │
│  │  │ 🎯 Impact: High (regulatory compliance, competitive advantage)              │  │ │
│  │  │                                                                              │  │ │
│  │  │ Top Requesting Partners: UBS, Credit Suisse, Swiss Re                       │  │ │
│  │  │ Last Used: Never (new topic)                                                │  │ │
│  │  │                                                                              │  │ │
│  │  │ [View Details] [✓ Added]                                                    │  │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ Zero Trust Security Architecture                                           │  │ │
│  │  │                                                                              │  │ │
│  │  │ Modern security model eliminating implicit trust in network segments         │  │ │
│  │  │ Category: Security & Compliance                                             │  │ │
│  │  │                                                                              │  │ │
│  │  │ 📊 Votes: █████████ 15 partners (31%)                                       │  │ │
│  │  │ 🏆 Most Popular Topic                                                       │  │ │
│  │  │ 🎯 Impact: High (2026 compliance requirement)                               │  │ │
│  │  │                                                                              │  │ │
│  │  │ Top Requesting Partners: PostFinance, Raiffeisen, Swisscom                  │  │ │
│  │  │ Last Used: Spring 2023 (18 months ago)                                      │  │ │
│  │  │                                                                              │  │ │
│  │  │ [View Details] [+ Add to My List]                                           │  │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ ✨ Platform Engineering                                                     │  │ │
│  │  │                                                                              │  │ │
│  │  │ Building internal developer platforms for productivity and standardization   │  │ │
│  │  │ Category: DevOps & Infrastructure                                           │  │ │
│  │  │                                                                              │  │ │
│  │  │ 📊 Votes: ████ 6 partners (13%)                                             │  │ │
│  │  │ 📈 Trending: New topic, growing interest                                    │  │ │
│  │  │ 🎯 Impact: Medium (operational efficiency)                                  │  │ │
│  │  │                                                                              │  │ │
│  │  │ Top Requesting Partners: Migros, Coop, Die Post                             │  │ │
│  │  │ Last Used: Never (new topic)                                                │  │ │
│  │  │                                                                              │  │ │
│  │  │ [View Details] [+ Add to My List]                                           │  │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ Cloud Cost Optimization (FinOps)                                           │  │ │
│  │  │                                                                              │  │ │
│  │  │ Financial operations for cloud - optimize spending while maintaining perf    │  │ │
│  │  │ Category: Cloud & Infrastructure                                            │  │ │
│  │  │                                                                              │  │ │
│  │  │ 📊 Votes: ██████ 10 partners (21%)                                          │  │ │
│  │  │ 💰 High ROI potential                                                        │  │ │
│  │  │ 🎯 Impact: High (budget planning, cost savings)                             │  │ │
│  │  │                                                                              │  │ │
│  │  │ Top Requesting Partners: SBB, Swisscom, UBS                                 │  │ │
│  │  │ Last Used: Autumn 2022 (2 years ago)                                        │  │ │
│  │  │                                                                              │  │ │
│  │  │ [View Details] [+ Add to My List]                                           │  │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ Sustainable IT & Green Computing                                           │  │ │
│  │  │                                                                              │  │ │
│  │  │ ESG-compliant IT infrastructure and datacenter carbon footprint reduction    │  │ │
│  │  │ Category: Sustainability                                                    │  │ │
│  │  │                                                                              │  │ │
│  │  │ 📊 Votes: ██ 3 partners (6%)                                                │  │ │
│  │  │ 🆕 New suggested topic - under review                                       │  │ │
│  │  │ 🎯 Impact: Medium (ESG requirements)                                        │  │ │
│  │  │                                                                              │  │ │
│  │  │ Suggested By: Credit Suisse                                                 │  │ │
│  │  │ Status: Under organizer review                                              │  │ │
│  │  │                                                                              │  │ │
│  │  │ [View Details] [+ Add to My List]                                           │  │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ Kubernetes at Scale                                                        │  │ │
│  │  │                                                                              │  │ │
│  │  │ Operating Kubernetes clusters at enterprise scale with best practices        │  │ │
│  │  │ Category: Cloud & Infrastructure                                            │  │ │
│  │  │                                                                              │  │ │
│  │  │ 📊 Votes: ██ 4 partners (8%)                                                │  │ │
│  │  │ ⚠️ Similar to "Cloud Native Architecture" (last used Mar 2023)              │  │ │
│  │  │ 🎯 Impact: Medium (operational maturity)                                    │  │ │
│  │  │                                                                              │  │ │
│  │  │ Top Requesting Partners: Swisscom, Die Post                                 │  │ │
│  │  │ Last Used: May 2021 (3+ years ago)                                          │  │ │
│  │  │                                                                              │  │ │
│  │  │ [View Details] [+ Add to My List]                                           │  │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  [Load More...] (41 more topics)                                                 │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── COMPARISON BASKET ─────────────────────────────────────────────────────────────┐ │
│  │  📋 Compare Topics (3 selected)                                    [Clear] [Compare]│ │
│  │  AI/ML in Financial | Zero Trust Security | Platform Engineering                │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### List View (Alternative Layout)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│  ┌─── TOPIC LIST (Compact View) ─────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  # | Topic Title                        | Category      | Votes | Status | Action│ │
│  │  ──┼────────────────────────────────────┼───────────────┼───────┼────────┼──────│ │
│  │  🔥│ Zero Trust Security Architecture   │ Security      │ █████ │ ✅     │ Added│ │
│  │  1 │ Last: Spring 2023 • 15 votes (31%) │ & Compliance  │   15  │        │      │ │
│  │  ──┼────────────────────────────────────┼───────────────┼───────┼────────┼──────│ │
│  │  🔥│ AI/ML in Financial Services        │ Technology    │ █████ │ ✅     │ Added│ │
│  │  2 │ Last: Never • 12 votes (25%) 📈    │ Innovation    │   12  │        │      │ │
│  │  ──┼────────────────────────────────────┼───────────────┼───────┼────────┼──────│ │
│  │  💰│ Cloud Cost Optimization (FinOps)   │ Cloud &       │ ████  │        │[+ Add]│ │
│  │  3 │ Last: Autumn 2022 • 10 votes (21%) │ Infrastructure│   10  │        │      │ │
│  │  ──┼────────────────────────────────────┼───────────────┼───────┼────────┼──────│ │
│  │  ✨│ Platform Engineering               │ DevOps &      │ ███   │        │[+ Add]│ │
│  │  4 │ Last: Never • 6 votes (13%) 📈     │ Infrastructure│   6   │        │      │ │
│  │  ──┼────────────────────────────────────┼───────────────┼───────┼────────┼──────│ │
│  │    │ Kubernetes at Scale                │ Cloud &       │ █     │        │[+ Add]│ │
│  │  5 │ Last: May 2021 • 4 votes (8%)      │ Infrastructure│   4   │        │      │ │
│  │  ──┼────────────────────────────────────┼───────────────┼───────┼────────┼──────│ │
│  │                                                                                   │ │
│  │  [Load More...] (42 more topics)                                                 │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Topic Comparison Modal

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ [X] Compare Topics                                                                    │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌────────────────────┬────────────────────┬────────────────────┐                    │
│  │ AI/ML in Financial │ Zero Trust         │ Platform           │                    │
│  │ Services           │ Security           │ Engineering        │                    │
│  ├────────────────────┼────────────────────┼────────────────────┤                    │
│  │ Votes: 12 (25%)    │ Votes: 15 (31%)    │ Votes: 6 (13%)     │                    │
│  │ Trending: +4       │ Steady growth      │ New topic          │                    │
│  ├────────────────────┼────────────────────┼────────────────────┤                    │
│  │ Impact: HIGH       │ Impact: HIGH       │ Impact: MEDIUM     │                    │
│  │ • Competitive edge │ • Compliance 2026  │ • Operational      │                    │
│  │ • Regulatory       │ • Security posture │ • Efficiency       │                    │
│  ├────────────────────┼────────────────────┼────────────────────┤                    │
│  │ Last Used: Never   │ Last: Spring 2023  │ Last: Never        │                    │
│  │ Ready: ✅          │ Ready: ✅          │ Ready: ✅          │                    │
│  ├────────────────────┼────────────────────┼────────────────────┤                    │
│  │ Top Partners:      │ Top Partners:      │ Top Partners:      │                    │
│  │ • UBS              │ • PostFinance      │ • Migros           │                    │
│  │ • Credit Suisse    │ • Raiffeisen       │ • Coop             │                    │
│  │ • Swiss Re         │ • Swisscom         │ • Die Post         │                    │
│  ├────────────────────┼────────────────────┼────────────────────┤                    │
│  │ Industry Trend:    │ Industry Trend:    │ Industry Trend:    │                    │
│  │ 🔥 Hot (35% YoY)   │ 📈 Rising (28%)    │ ✨ Emerging (42%)  │                    │
│  ├────────────────────┼────────────────────┼────────────────────┤                    │
│  │ [✅ In Your List]  │ [+ Add to List]    │ [+ Add to List]    │                    │
│  └────────────────────┴────────────────────┴────────────────────┘                    │
│                                                                                       │
│  Recommendation: All three topics are complementary and could be combined into a     │
│  "Modern Enterprise Technology Stack" theme for a full-day event.                    │
│                                                                                       │
│                                                         [Close] [Add All to My List]  │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Interactive Elements

### Search & Filter Section
- **Search Bar**: Real-time search across topic titles and descriptions
- **Category Filter**: Filter by technology category (Cloud, Security, AI/ML, etc.)
- **Popularity Filter**: Filter by vote count ranges (high/medium/low popularity)
- **My Votes Filter**: Show only topics in my voting list, or not in my list
- **Sort Dropdown**: Sort by votes, trending, alphabetical, newest, impact level
- **Quick Filter Chips**: One-click filters for common views
- **Clear Filters**: Reset all filters to default state

### Topic Statistics Bar
- **Total Topics**: Count of all available topics
- **Trending Count**: Topics with increasing votes
- **Your Selections**: Count of topics added to voting list
- **Most Voted**: Highest voted topic name

### Topic Cards (Grid View)
- **Topic Badge**: Visual indicators (🔥 trending, ✨ new, 🏆 popular, 🆕 suggested)
- **Status Badge**: "IN YOUR LIST" for added topics
- **Vote Bar**: Visual representation of vote distribution
- **Trending Indicator**: Shows vote change this week
- **Impact Level**: High/Medium/Low with rationale
- **Top Requesting Partners**: Company names voting for this topic
- **Last Used**: When topic was last used in an event
- **View Details**: Navigate to Topic Detail Screen
- **Add/Remove Button**: Toggle topic in voting list

### Topic List (List View)
- **Compact Row**: Single-line topic summary
- **Sort Indicators**: Visual sort direction
- **Quick Actions**: Inline add/remove buttons
- **Hover Details**: Expanded info on hover

### Comparison Basket
- **Topic Chips**: Selected topics for comparison
- **Clear Button**: Remove all from comparison
- **Compare Button**: Open comparison modal

### Comparison Modal
- **Side-by-Side View**: Compare up to 3 topics
- **Metrics Comparison**: Votes, impact, trends, partners
- **Recommendation**: AI-generated insights on topic combinations
- **Bulk Actions**: Add all compared topics to voting list

---

## Functional Requirements Met

- **FR8**: Partner topic influence through comprehensive voting interface
- **Topic Discovery**: Browse all available topics with search and filtering
- **Vote Distribution**: Visual representation of partner voting patterns
- **Community Insights**: See what other partners are requesting
- **Informed Decisions**: Comparison tools and detailed metrics
- **Voting Integration**: Seamless add/remove from voting list

---

## User Interactions

### For Partners (Primary Users)
1. **Browse All Topics**: View complete catalog of available topics
2. **Search Topics**: Find topics by keyword across titles and descriptions
3. **Filter by Category**: Narrow down by technology category
4. **Filter by Popularity**: See most/least voted topics
5. **View My Selections**: Filter to show only topics in voting list
6. **Sort Topics**: Order by votes, trending, impact, or alphabetically
7. **Quick Filter**: Apply predefined filters (Popular, Trending, New, Your Interests)
8. **View Topic Details**: Navigate to full topic detail screen
9. **Add to Voting List**: Add topic to personal voting rankings
10. **Remove from List**: Remove topic from voting list
11. **Compare Topics**: Select multiple topics for side-by-side comparison
12. **View Comparison**: Open comparison modal with detailed metrics
13. **Add All Compared**: Bulk add compared topics to voting list
14. **Toggle View Mode**: Switch between grid and list view
15. **Load More**: Paginate through large topic catalog
16. **See Vote Distribution**: Understand community voting patterns
17. **View Trending**: See topics gaining votes this week
18. **Check Similar Topics**: Warning for similar/duplicate topics
19. **Monitor Voting Progress**: Track "My Votes: 5/10" limit

### For Organizers (View-Only)
20. **View Partner Interests**: See which topics partners are voting for
21. **Analyze Vote Distribution**: Understand partner priorities
22. **Monitor Trending Topics**: Track growing interest areas
23. **Review New Suggestions**: See partner-suggested topics under review

### Common Interactions
24. **View Topic History**: See when topic was last used
25. **See Top Requesting Partners**: Companies interested in each topic
26. **View Industry Trends**: Understand broader market interest
27. **Check Topic Impact**: Understand business value proposition

---

## Technical Notes

### Component Structure
- **AllTopicsBrowserScreen.tsx**: Main browser screen component
- **TopicSearchBar.tsx**: Search with autocomplete
- **TopicFilters.tsx**: Filter panel with multi-criteria filtering
- **TopicStatsBar.tsx**: Summary statistics display
- **TopicGrid.tsx**: Grid layout for topic cards
- **TopicList.tsx**: List layout for topic rows
- **TopicCard.tsx**: Individual topic card component (grid view)
- **TopicRow.tsx**: Individual topic row component (list view)
- **ComparisonBasket.tsx**: Topic comparison selection bar
- **ComparisonModal.tsx**: Side-by-side topic comparison
- **VoteDistributionBar.tsx**: Visual vote percentage bar
- **QuickFilters.tsx**: Predefined filter chips

### State Management
- **Local State**:
  - View mode (grid/list)
  - Search query
  - Active filters
  - Comparison basket (selected topics for comparison)
  - Sort order
- **Zustand Store**:
  - Partner's current voting list
  - View preferences (grid/list default)
  - Filter preferences
- **React Query**: Server state for topics
  - `allTopics` query: Cached for 5 minutes
  - `votingSession` query: Cached for 10 minutes
  - `partnerVotes` query: Cached for 2 minutes (frequently updated)
  - `topicStats` query: Cached for 5 minutes

### API Integration
- **All Topics**: `GET /api/v1/topics/voting-session/{sessionId}/all-topics`
- **Search Topics**: `GET /api/v1/topics/search?query={text}&sessionId={id}`
- **Filter Topics**: `GET /api/v1/topics/voting-session/{sessionId}/all?category={cat}&popularity={range}`
- **Add to Votes**: `POST /api/v1/partners/{partnerId}/topics/votes/add`
- **Remove from Votes**: `DELETE /api/v1/partners/{partnerId}/topics/votes/{topicId}`
- **Compare Topics**: `POST /api/v1/topics/compare` (with topicIds array)
- **Topic Details**: `GET /api/v1/topics/{topicId}/details`
- **Partner Votes**: `GET /api/v1/partners/{partnerId}/topics/votes/current`

### Performance Optimization
- **Virtual Scrolling**: For large topic lists (>50 topics)
- **Debounced Search**: 300ms debounce on search input
- **Lazy Loading**: Load more topics on scroll
- **Image Optimization**: Lazy load partner company logos
- **Memoization**: Memoize filter and sort calculations
- **Optimistic Updates**: Immediate UI update when adding/removing topics

### Caching Strategy
- **Topic List**: 5-minute cache with background refresh
- **Partner Votes**: 2-minute cache (can change frequently)
- **Vote Statistics**: 5-minute cache
- **Search Results**: 10-minute cache per query
- **Cache Invalidation**: Invalidate on vote submission, topic addition/removal

### Accessibility
- **Keyboard Navigation**: Full keyboard access to all features
- **ARIA Labels**:
  - `aria-label="Search topics"` on search input
  - `aria-label="Add {topicName} to voting list"` on add buttons
  - `aria-label="Filter by {category}"` on filter dropdowns
  - `aria-label="Compare selected topics"` on compare button
- **Focus Management**: Proper focus handling for modals and dropdowns
- **Screen Reader Support**:
  - Topic card data announced completely
  - Vote counts announced as "15 partners, 31 percent"
  - Filter changes announced
- **Color Contrast**: WCAG 2.1 AA compliant (4.5:1 minimum)
- **Alt Text**: Partner logos include alt text

---

## API Requirements

### Consolidated APIs (Story 1.21)

**Note**: This wireframe now uses the consolidated Topics APIs from Story 1.21. The browsing functionality benefits from unified filtering and the voting-session resource consolidation.

### Initial Page Load APIs

1. **GET /api/v1/topics?filter={"votingSessionId":"{sessionId}"}&sort=-votes&page=1&limit=20**
   - Query params: `filter={"votingSessionId":"{sessionId}"}, sort=-votes, page=1, limit=20, include=votes`
   - Returns: Paginated list of all votable topics with comprehensive data
   - Response: `{ topics: [{ id, title, description, category, voteCount, votePercentage, trending, isNew, lastUsedAt, impact, topRequestingPartners }], pagination, totalCount }`
   - Used for: Initial topic list display
   - **Consolidation**: Main topics list with voting session filter replaces specialized voting-session/all-topics endpoint

2. **GET /api/v1/topics/voting-sessions/{sessionId}/votes?partnerId={partnerId}**
   - Query params: `partnerId={partnerId}`
   - Returns: Partner's current topic voting list for this session
   - Response: `{ topicIds: [], votingLimit: 10, currentCount: 5 }`
   - Used for: Mark topics as "IN YOUR LIST" and show vote count
   - **Consolidation**: Votes managed through voting-session resource

3. **GET /api/v1/topics/statistics?groupBy=votingSession&sessionId={sessionId}**
   - Query params: `groupBy=votingSession, sessionId={sessionId}`
   - Returns: Overall voting statistics for the session
   - Response: `{ totalTopics: 47, trendingCount: 8, mostVotedTopic: { id, title, voteCount } }`
   - Used for: Topic statistics bar
   - **Consolidation**: Statistics endpoint with voting session grouping

4. **GET /api/v1/topics/statistics?groupBy=category**
   - Query params: `groupBy=category`
   - Returns: Available topic categories with counts
   - Response: `{ categories: [{ name, count, description }] }`
   - Used for: Populate category filter dropdown
   - **Consolidation**: Statistics endpoint with category grouping

### Action APIs

#### Topic Management

5. **GET /api/v1/topics?filter={"votingSessionId":"{sessionId}","title":{"$contains":"{text}"}}&limit=20**
   - Triggered by: Search input (debounced)
   - Query params: `filter (votingSessionId, title/description contains text), limit=20`
   - Returns: Matching topics with search relevance
   - Used for: Real-time topic search
   - **Consolidation**: Search integrated into main list endpoint with $contains filter operator

6. **GET /api/v1/topics?filter={"votingSessionId":"{sessionId}","category":"{cat}"}&sort={field}&page={n}**
   - Triggered by: Filter changes, sort changes, load more
   - Query params: `filter (votingSessionId, category, popularity range), sort, page, limit`
   - Returns: Filtered and sorted topic list with pagination
   - Used for: Apply filters and sorting
   - **Consolidation**: Unified filtering via JSON filter parameter - no new endpoints needed

7. **PUT /api/v1/topics/voting-sessions/{sessionId}/votes**
   - Triggered by: [+ Add to My List] button
   - Payload: `{ partnerId, addTopics: [topicId] }`
   - Returns: `{ success: true, currentVoteCount: 6, limit: 10 }`
   - Side effects: Adds topic to partner's voting list
   - Used for: Add topic to voting list
   - **Consolidation**: Votes managed through voting-session resource

8. **PUT /api/v1/topics/voting-sessions/{sessionId}/votes**
   - Triggered by: [✓ Added] button (toggle off)
   - Payload: `{ partnerId, removeTopics: [topicId] }`
   - Returns: `{ success: true, currentVoteCount: 4 }`
   - Side effects: Removes topic from voting list
   - Used for: Remove topic from voting list
   - **Consolidation**: Same endpoint as add, different payload

9. **GET /api/v1/topics/{topicId}?include=votes,history,insights**
   - Triggered by: [View Details] button
   - Query params: `include=votes,history,insights`
   - Returns: Complete topic information with comprehensive data
   - Opens: Topic Detail Screen
   - Used for: Navigate to topic details
   - **Consolidation**: Single endpoint with ?include parameter

#### Topic Comparison

10. **GET /api/v1/topics?filter={"ids":{"$in":[id1,id2,id3]}}&include=votes,insights**
    - Triggered by: [Compare] button
    - Query params: `filter (ids in array), include=votes,insights, context (sessionId)`
    - Returns: Side-by-side comparison data with AI recommendation
    - Response: `{ topics: [{ id, title, votes, impact, trend, topPartners, lastUsed, industryTrend }], aiRecommendation: "text" }`
    - Used for: Open comparison modal
    - **Consolidation**: Use main list endpoint with ID filter for comparison data

11. **PUT /api/v1/topics/voting-sessions/{sessionId}/votes**
    - Triggered by: [Add All to My List] in comparison modal
    - Payload: `{ partnerId, addTopics: [id1, id2, id3] }`
    - Returns: `{ success: true, addedCount: 3, currentVoteCount: 8 }`
    - Used for: Bulk add compared topics to voting list
    - **Consolidation**: Same voting-session votes endpoint handles bulk operations

#### Filtering & Sorting

12. **GET /api/v1/topics/trending?type=community&sessionId={sessionId}&timeframe=week**
    - Triggered by: [Trending 📈] quick filter
    - Query params: `type=community, sessionId={sessionId}, timeframe=week`
    - Returns: Topics with increasing votes in this session
    - Response: `{ topics: [], voteChanges: [{ topicId, changeThisWeek }] }`
    - Used for: Show trending topics
    - **Consolidation**: Trending endpoint with session filter

13. **GET /api/v1/topics?filter={"votingSessionId":"{sessionId}","isNew":true}&sort=-createdAt**
    - Triggered by: [New Topics ✨] quick filter
    - Query params: `filter (votingSessionId, isNew=true), sort=-createdAt`
    - Returns: Recently added topics for this session
    - Response: `{ topics: [], addedDates: [] }`
    - Used for: Show new topics
    - **Consolidation**: Filter on main list for new topics

14. **GET /api/v1/topics/ai-suggestions?context={"partnerId":"{partnerId}","purpose":"recommendations"}**
    - Triggered by: [Your Interests] quick filter
    - Query params: `context (partnerId, partner industry/preferences, purpose=recommendations)`
    - Returns: Topics matching partner's profile with AI-powered matching
    - Response: `{ topics: [], matchReasons: [], confidence: [] }`
    - Used for: Personalized topic recommendations
    - **Consolidation**: AI suggestions endpoint with partner context for personalization

#### Pagination

15. **GET /api/v1/topics?filter={"votingSessionId":"{sessionId}"}&page={page}&limit=20**
    - Triggered by: [Load More] button or infinite scroll
    - Query params: `filter (votingSessionId), page={page}, limit=20`
    - Returns: Next batch of topics with pagination metadata
    - Used for: Load additional topics
    - **Consolidation**: Standard pagination on main list endpoint

---

## Navigation Map

### Primary Navigation Actions

1. **← Back to Topic Voting button** → Navigate to Topic Voting Screen
   - Type: Full page navigation
   - Target: [story-6.4-topic-voting.md](story-6.4-topic-voting.md)
   - Context: Returns to main voting interface

2. **My Votes: 5/10 indicator** → Shows voting progress
   - Type: Informational (no navigation)
   - Tooltip: "You can vote for up to 10 topics"
   - Visual: Progress bar overlay on hover

3. **Search input** → Filters topic list
   - Type: In-place filtering
   - Debounced: 300ms delay
   - No navigation, updates topic grid

4. **[Clear Filters] button** → Resets all filters
   - Type: In-place action
   - Clears: Search, category, popularity, my votes, sort
   - No navigation

5. **Category dropdown** → Filters by category
   - Type: In-place filtering
   - Updates topic grid
   - No navigation

6. **Popularity dropdown** → Filters by vote count
   - Type: In-place filtering
   - Options: All, High (>20 votes), Medium (10-20), Low (<10)
   - No navigation

7. **My Votes dropdown** → Filters by voting status
   - Type: In-place filtering
   - Options: All, In My List, Not in My List
   - No navigation

8. **Sort dropdown** → Changes sort order
   - Type: In-place sorting
   - Options: Votes (desc), Trending, Alphabetical, Newest, Impact
   - No navigation

9. **Quick filter chips** → Applies predefined filters
   - Type: In-place filtering
   - Filters: Most Popular, Trending, New Topics, Your Interests
   - No navigation

10. **[Grid/List] toggle** → Switches view mode
    - Type: In-place view change
    - Toggles between grid cards and compact list
    - No navigation

### Topic Card Actions

11. **Topic card click (title)** → Navigate to Topic Detail Screen
    - Type: Full page navigation
    - Target: [story-2.2-topic-detail-screen.md](story-2.2-topic-detail-screen.md)
    - Context: Topic ID passed for detailed view

12. **[View Details] button** → Navigate to Topic Detail Screen
    - Type: Full page navigation
    - Same as topic card click

13. **[+ Add to My List] button** → Adds topic to voting list
    - Type: In-place action
    - API call: `POST /api/v1/partners/{partnerId}/topics/votes/add`
    - Visual feedback: Button changes to [✓ Added]
    - Updates: My Votes count
    - No navigation

14. **[✓ Added] button** → Removes topic from voting list
    - Type: In-place action (toggle off)
    - API call: `DELETE /api/v1/partners/{partnerId}/topics/votes/{topicId}`
    - Visual feedback: Button changes to [+ Add to My List]
    - Updates: My Votes count
    - No navigation

15. **Vote bar hover** → Shows detailed vote breakdown
    - Type: Tooltip
    - Displays: Exact vote count, percentage, partner names
    - No navigation

16. **Trending indicator click** → Shows trend details
    - Type: Tooltip
    - Displays: Vote change this week, trend direction
    - No navigation

17. **Impact level hover** → Shows impact explanation
    - Type: Tooltip
    - Displays: Impact rationale and business value
    - No navigation

18. **Top Requesting Partners hover** → Shows partner details
    - Type: Tooltip
    - Displays: Full partner list with vote details
    - No navigation

19. **Last Used info hover** → Shows usage history summary
    - Type: Tooltip
    - Displays: Event name, date, speaker, attendance
    - No navigation

### Comparison Actions

20. **Topic card checkbox (compare)** → Adds to comparison basket
    - Type: In-place selection
    - Max: 3 topics
    - Updates: Comparison basket bar
    - No navigation

21. **Comparison basket chip click** → Removes from comparison
    - Type: In-place action
    - Updates: Comparison basket
    - No navigation

22. **[Clear] button (basket)** → Clears all comparisons
    - Type: In-place action
    - Empties comparison basket
    - No navigation

23. **[Compare] button** → Opens comparison modal
    - Type: Modal overlay
    - API call: `POST /api/v1/topics/compare`
    - Opens: Comparison modal with side-by-side view
    - No screen navigation

24. **[Add All to My List] button (modal)** → Bulk adds topics
    - Type: Modal action
    - API call: `POST /api/v1/partners/{partnerId}/topics/votes/bulk-add`
    - Updates: My Votes count, topic card buttons
    - Closes: Modal after action
    - No screen navigation

25. **[Close] button (modal)** → Closes comparison modal
    - Type: Modal dismiss
    - Returns to browser screen
    - No screen navigation

### Pagination Actions

26. **[Load More] button** → Loads additional topics
    - Type: In-place content expansion
    - API call: Increments offset
    - Appends: Additional topic cards
    - No navigation

27. **Scroll to bottom** → Infinite scroll trigger
    - Type: Auto-load
    - Triggers: Load more topics
    - No manual navigation

### Error States & Redirects

28. **No topics found** → Shows empty state
    - Type: In-page message
    - Message: "No topics match your filters"
    - Action: [Clear Filters] button
    - No navigation

29. **Voting limit reached** → Shows warning
    - Type: Toast notification
    - Message: "You've reached the maximum of 10 votes"
    - Action: Must remove a topic to add another
    - No navigation

30. **API error loading topics** → Shows error state
    - Type: In-page error
    - Message: "Unable to load topics"
    - Action: [Retry] button
    - No navigation

31. **Network error** → Shows offline state
    - Type: Banner notification
    - Message: "You are offline. Showing cached topics."
    - Shows: Last cached data
    - No navigation

---

## Responsive Design Considerations

### Mobile Layout Changes

- **Search Bar**: Full-width with filters in expandable drawer
- **Topic Cards**: Single column, stacked vertically
- **Comparison Basket**: Fixed bottom bar with compact view
- **Filters**: Bottom sheet instead of inline dropdowns
- **Sort**: Accessible via top-right menu button
- **View Toggle**: Hidden (defaults to list view on mobile)

### Tablet Layout Changes

- **Topic Cards**: Two-column grid
- **Filters**: Collapsible sidebar
- **Comparison**: Side-by-side in modal (2 topics max)

### Mobile-Specific Interactions

- **Swipe to Add**: Swipe right on topic card to add to voting list
- **Pull to Refresh**: Refresh topic list
- **Bottom Sheet Filters**: Open filters in bottom sheet
- **Sticky Search**: Search bar sticky at top when scrolling
- **FAB Button**: Floating action button for compare (when topics selected)

---

## Accessibility Notes

- **Keyboard Navigation**: All interactive elements accessible via Tab key
- **ARIA Labels**:
  - `aria-label="Search topics by title or description"` on search input
  - `aria-label="Add {topicName} to your voting list"` on add buttons
  - `aria-label="Filter topics by category"` on category filter
  - `aria-label="Compare {count} selected topics"` on compare button
- **Focus Indicators**: 2px solid border on focused elements
- **Screen Reader Support**:
  - Topic card data fully described
  - Vote counts announced: "15 partners, 31 percent"
  - Filter changes announced with aria-live regions
  - "Added to voting list" announcements
- **Color Contrast**: All text meets WCAG 2.1 AA standards (4.5:1 minimum)
- **Alt Text**: Icons include descriptive alt text
- **Skip Links**: "Skip to topic list" for keyboard users
- **ARIA Live Regions**: Vote count updates announced automatically

---

## State Management

### Local Component State

- `viewMode`: 'grid' | 'list' - Current view mode
- `searchQuery`: string - Current search text
- `filters`: object - Active filters (category, popularity, myVotes)
- `sortBy`: string - Current sort field
- `sortOrder`: 'asc' | 'desc' - Sort direction
- `comparisonBasket`: TopicId[] - Topics selected for comparison
- `isComparisonModalOpen`: boolean - Comparison modal state

### Global State (Zustand Store)

- `voting.currentVotes`: TopicId[] - Partner's current voting list
- `voting.votingLimit`: number - Maximum votes allowed (10)
- `topics.viewPreferences`: object - User's saved view mode
- `topics.filterPreferences`: object - Last used filters
- `auth.currentUser`: User - Current authenticated user
- `auth.partnerId`: string - Current partner ID

### Server State (React Query)

- `allTopics`: All votable topics (cached for 5 minutes)
- `partnerVotes`: Partner's current votes (cached for 2 minutes)
- `topicStats`: Voting statistics (cached for 5 minutes)
- `votingSession`: Current voting session info (cached for 10 minutes)
- `categories`: Topic categories (cached for 1 hour)

### Real-Time Updates

- **Vote Count Updates**: Live update when other partners vote
- **New Topic Added**: Real-time notification when organizers add topics
- **Voting Limit Warning**: Live check when approaching limit (8/10, 9/10)
- **Trending Topics**: Periodic refresh (every 5 minutes) for trending indicators

---

## Edge Cases & Error Handling

- **Empty Topic List**: Show "No topics available for voting yet"
- **No Search Results**: Show "No topics match '{query}'" with suggestion to clear search
- **Voting Limit Reached**: Disable [+ Add] buttons, show tooltip "Remove a topic to add another"
- **All Topics Already Added**: Show "You've added all available topics to your voting list"
- **Comparison Limit**: Disable checkbox after 3 topics selected
- **Duplicate Add Attempt**: Show toast "Topic already in your voting list"
- **Loading State**: Display skeleton cards while data loads
- **API Error**: Show error banner with [Retry] button
- **Network Timeout**: Show cached data with "Viewing offline version" banner
- **Slow Network**: Show progress indicators for >2 seconds
- **Invalid Session**: Redirect to voting session selection or show "Voting session has ended"
- **Permission Denied**: Show "You must be a partner to vote on topics"

---

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-10-04 | 1.0 | Initial wireframe creation for All Topics Browser Screen | Sally (UX Expert) |

---

## Review Notes

### Open Questions

1. **Vote Limit**: Should the 10-vote limit be configurable per partner tier (Gold=10, Silver=5)?
2. **Topic Suggestions**: Should partners be able to suggest new topics directly from this screen?
3. **Historical Data**: Should we show how partner's past votes influenced event planning?
4. **Collaboration**: Should partners see which specific partner companies voted for each topic?
5. **Categories**: Should we support multi-select category filtering?
6. **Save Searches**: Should partners be able to save favorite filter combinations?

### Design Iterations

- **v1.0**: Initial comprehensive design with grid/list views and comparison feature
- Consider adding: Topic relationship graph showing connections between related topics
- Consider adding: Vote history timeline showing when votes were cast
- Consider adding: Partner influence score impact per topic (how much your vote matters)

### Stakeholder Feedback

- Pending review from partners for comparison feature usefulness
- Need to validate voting limit (10 topics) with historical voting patterns
- Confirm anonymity level for "Top Requesting Partners" (company names vs. anonymous)
- Validate filter options with partner user research
