# Story: Topic Voting & Strategic Input - Wireframe

**Story**: Epic 6, Story 4
**Screen**: Topic Voting & Strategic Input
**User Role**: Partner
**Related FR**: FR8 (Topic Influence)

---

## 4. Topic Voting & Strategic Input (FR8)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                        Strategic Topic Planning                               │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  Next Planning Period: Q3 2025            Voting Closes: April 30, 2025              │
│                                                                                       │
│  ┌──── YOUR TOPIC PRIORITIES ──────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Rank topics by importance to your organization (drag to reorder)               │ │
│  │                                                                                  │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐ │ │
│  │  │ 1 ☰  AI/ML in Financial Services                             🔺 High Impact│ │ │
│  │  │      Your teams need practical AI implementation guidance                  │ │ │
│  │  │      Votes from other partners: ████████ 8                                │ │ │
│  │  ├────────────────────────────────────────────────────────────────────────────┤ │ │
│  │  │ 2 ☰  Cloud Cost Optimization (FinOps)                       🔺 High Impact│ │ │
│  │  │      Critical for 2025 budget planning                                     │ │ │
│  │  │      Votes from other partners: ██████ 6                                  │ │ │
│  │  ├────────────────────────────────────────────────────────────────────────────┤ │ │
│  │  │ 3 ☰  Zero Trust Security Architecture                       🔸 Med Impact │ │ │
│  │  │      Compliance requirement for 2026                                       │ │ │
│  │  │      Votes from other partners: █████████ 9                               │ │ │
│  │  ├────────────────────────────────────────────────────────────────────────────┤ │ │
│  │  │ 4 ☰  Platform Engineering                                   🔸 Med Impact │ │ │
│  │  │      Building internal developer platforms                                 │ │ │
│  │  │      Votes from other partners: ████ 4                                    │ │ │
│  │  ├────────────────────────────────────────────────────────────────────────────┤ │ │
│  │  │ 5 ☰  Quantum Computing Readiness                            🔻 Low Impact │ │ │
│  │  │      Future planning (3-5 years)                                           │ │ │
│  │  │      Votes from other partners: ██ 2                                      │ │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘ │ │
│  │                                                                                  │ │
│  │  [Submit Votes] [Add Custom Topic] [View All Topics]                            │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── SUGGEST NEW TOPICS ──────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Topic Title *                                                                  │ │
│  │  ┌──────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ Sustainable IT & Green Computing                                          │  │ │
│  │  └──────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                  │ │
│  │  Business Justification *                                                       │ │
│  │  ┌──────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ ESG requirements are becoming critical for Swiss financial institutions.  │  │ │
│  │  │ We need guidance on reducing datacenter carbon footprint and meeting      │  │ │
│  │  │ 2030 sustainability goals while maintaining performance.                  │  │ │
│  │  └──────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                  │ │
│  │  Target Audience                                                                │ │
│  │  ☑ C-Level/Management                                                           │ │
│  │  ☑ IT Infrastructure                                                            │ │
│  │  ☑ Development Teams                                                            │ │
│  │  ☐ Security Teams                                                               │ │
│  │                                                                                  │ │
│  │  [Submit Suggestion] [Save Draft]                                               │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── COMMUNITY TOPIC TRENDS ─────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  What Other Partners Are Requesting              Industry Trends                │ │
│  │                                                                                  │ │
│  │  1. Zero Trust Security (9 votes)                🔥 Platform Engineering       │ │
│  │  2. AI/ML Applications (8 votes)                 📈 FinOps & Cost Control      │ │
│  │  3. Cloud Cost Control (6 votes)                 🆕 Sustainable IT             │ │
│  │  4. Platform Engineering (4 votes)               📊 Observability at Scale     │ │
│  │  5. Edge Computing (3 votes)                     🔮 Quantum Readiness          │ │
│  │                                                                                  │ │
│  │  Your Influence Score: 85/100                                                   │ │
│  │  (Based on attendance, engagement, and sponsorship level)                       │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## API Requirements

### Consolidated APIs (Story 1.21)

**Note**: This wireframe now uses the consolidated Topics APIs from Story 1.21. The voting functionality integrates with the voting-session resource consolidation, reducing specialized endpoints.

### Initial Page Load APIs

When the Strategic Topic Planning screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/topics/voting-sessions/{sessionId}?include=topics,results** (voting-session resource)
   - Query params: `include=topics,results`
   - Returns: Current voting session details (planning period, voting deadline, status), available topics, current results
   - Used for: Display voting period, deadline, and available topics
   - **Consolidation**: Voting session resource includes session metadata and topics in one call

2. **GET /api/v1/topics?filter={"votingSessionId":"{sessionId}"}&include=votes**
   - Query params: filter (votingSessionId), include=votes, sort=-votes
   - Returns: Partner's current topic rankings with priorities, impact ratings, justifications, vote counts from other partners
   - Used for: Populate "Your Topic Priorities" section with ranked topics
   - **Consolidation**: Uses main topics list with voting session filter

3. **GET /api/v1/topics?filter={"votingSessionId":"{sessionId}"}&sort=votes**
   - Query params: filter (votingSessionId), sort (votes, alphabetical), limit, page
   - Returns: All available topics for voting with descriptions, vote counts, impact ratings
   - Used for: Enable "View All Topics" functionality
   - **Consolidation**: Standard topic list with voting session filter

4. **GET /api/v1/topics?filter={"suggestedBy":"partner_{partnerId}","status":"draft"}**
   - Query params: filter (suggestedBy partner, status=draft)
   - Returns: Saved draft topic suggestions for the partner
   - Used for: Pre-fill suggestion form if draft exists
   - **Consolidation**: Filter on main topics list for drafts

5. **GET /api/v1/topics/trending?type=community&timeframe=month**
   - Query params: type=community, timeframe=month, limit=10, include=votes
   - Returns: Top voted topics across all partners, vote counts, trending topics
   - Used for: Populate "What Other Partners Are Requesting" section
   - **Consolidation**: Single trending endpoint with type=community parameter

6. **GET /api/v1/topics/trending?type=industry&timeframe=quarter**
   - Query params: type=industry, timeframe=quarter, limit=5
   - Returns: Industry trending topics from external sources, relevance scores
   - Used for: Display "Industry Trends" section
   - **Consolidation**: Single trending endpoint with type=industry parameter

7. **GET /api/v1/partners/{partnerId}/influence-score** (Partner API - not Topics)
   - Returns: Partner's influence score (0-100), score breakdown (attendance, engagement, sponsorship), historical trend
   - Used for: Display influence score with explanation
   - **Note**: Partner analytics API (not part of Topics API consolidation)

---

## Action APIs

### Voting Actions

1. **PUT /api/v1/topics/voting-sessions/{sessionId}/votes** (voting-session resource)
   - Payload: `{ partnerId, rankings: [{ topicId, rank, impactRating: "high|medium|low", justification }] }`
   - Response: Updated vote confirmation, new influence score
   - Used for: Submit or update topic rankings
   - **Consolidation**: Votes managed through voting-session resource

2. **PATCH /api/v1/topics/voting-sessions/{sessionId}/votes** (voting-session resource)
   - Payload: `{ partnerId, rankings: [] }`
   - Response: Auto-save confirmation
   - Used for: Automatically save vote rankings as user drags/reorders (periodic auto-save)
   - **Consolidation**: PATCH for partial updates/auto-save

3. **GET /api/v1/topics/{topicId}?include=votes,history,insights**
   - Query params: `include=votes,history,insights`
   - Returns: Detailed topic information, vote history, related content, AI insights
   - Used for: View detailed information about a specific topic
   - **Consolidation**: Single endpoint with ?include parameter

### Topic Suggestion

4. **POST /api/v1/topics**
   - Payload: `{ title, description, businessJustification, targetAudience: [], supportingLinks: [], suggestedBy: "partner_{partnerId}", status: "pending" }`
   - Response: Topic ID, review status, expected review timeline
   - Used for: Submit new topic suggestion
   - **Consolidation**: Standard POST on /topics endpoint

5. **POST /api/v1/topics**
   - Payload: `{ title, description, businessJustification, targetAudience: [], status: "draft", suggestedBy: "partner_{partnerId}" }`
   - Response: Draft topic ID, save confirmation
   - Used for: Save topic suggestion as draft
   - **Consolidation**: Same POST endpoint with status=draft

6. **PUT /api/v1/topics/{suggestionId}**
   - Payload: Updated draft data `{ title, description, businessJustification, targetAudience: [] }`
   - Response: Update confirmation
   - Used for: Update existing draft
   - **Consolidation**: Standard PUT on /topics/{id}

7. **DELETE /api/v1/topics/{suggestionId}** or **POST /api/v1/topics/{suggestionId}/archive**
   - Payload: `{ reason: "draft abandoned" }`
   - Response: Deletion/archive confirmation
   - Used for: Delete draft suggestion
   - **Consolidation**: Standard DELETE or archive action

8. **GET /api/v1/topics?filter={"suggestedBy":"partner_{partnerId}"}&sort=-createdAt**
   - Query params: filter (suggestedBy, status), sort, limit, page
   - Returns: List of previously submitted suggestions with review status and feedback
   - Used for: View history of topic suggestions
   - **Consolidation**: Filter on main topics list

### Topic Discovery & Research

9. **GET /api/v1/topics?filter={"votingSessionId":"{sessionId}"}&sort={}&page={}**
   - Query params: filter (votingSessionId, category, popularity), sort (votes, alphabetical), limit, page
   - Returns: Comprehensive list of all votable topics with details
   - Used for: Navigate to "All Topics" browsing screen
   - **Consolidation**: Standard topics list with voting session filter

10. **PUT /api/v1/topics/voting-sessions/{sessionId}/votes**
    - Payload: `{ partnerId, addTopics: [{ topicId, priority }] }`
    - Response: Updated rankings with newly added topic
    - Used for: Add existing topic to partner's voting list from "All Topics" view
    - **Consolidation**: Update votes through voting-session resource

11. **PUT /api/v1/topics/voting-sessions/{sessionId}/votes**
    - Payload: `{ partnerId, removeTopics: [topicId] }`
    - Response: Updated rankings with topic removed
    - Used for: Remove topic from partner's voting list
    - **Consolidation**: Update votes through voting-session resource

### Community & Trends

12. **GET /api/v1/topics/trending?type=community&detailed=true**
    - Query params: type=community, detailed=true, dateRange, partnerSegment (industry, size)
    - Returns: Detailed trending analysis, vote patterns, emerging topics, correlation with partner success
    - Used for: Navigate to detailed community trends analysis
    - **Consolidation**: Trending endpoint with detailed flag and type=community

13. **GET /api/v1/topics/trending?type=industry&detailed=true**
    - Query params: type=industry, detailed=true, sources (conferences, publications, surveys)
    - Returns: Comprehensive industry trend report, relevance analysis, recommended topics
    - Used for: Navigate to detailed industry trends report
    - **Consolidation**: Trending endpoint with detailed flag and type=industry

14. **GET /api/v1/topics/ai-suggestions?context={"keywords":["..."],"purpose":"research"}**
    - Query params: context (keywords, purpose=research)
    - Returns: Research data supporting topic relevance, market demand, skill gaps, AI-powered analysis
    - Used for: Research support for topic suggestions
    - **Consolidation**: AI suggestions endpoint with research context

### Influence & Analytics

15. **GET /api/v1/partners/{partnerId}/influence-score?breakdown=true**
    - Query params: breakdown=true, includeHistory=true
    - Returns: Detailed influence score calculation, component breakdowns, improvement recommendations, historical trends
    - Used for: View detailed influence score analysis
    - **Note**: Partner analytics API (not part of Topics API consolidation)

16. **GET /api/v1/topics/voting-sessions?filter={"partnerId":"{partnerId}"}&include=votes,outcomes**
    - Query params: filter (partnerId), include (votes, outcomes), limit, page
    - Returns: Historical voting sessions, topics voted for, outcomes (selected/not selected), impact on events
    - Used for: View voting history and track record
    - **Consolidation**: List voting sessions with partner filter

17. **GET /api/v1/topics/voting-sessions/{sessionId}?include=results,topics**
    - Query params: include=results,topics
    - Returns: Final voting results for completed session, selected topics, partner contribution impact
    - Used for: View results of past voting sessions
    - **Consolidation**: Voting session resource with results inclusion

---

## Navigation Map

### Primary Navigation Actions

1. **← Back button** → Navigate back to `Partner Analytics Dashboard`
   - Auto-saves current votes if changes made
   - Returns to main dashboard

2. **Planning Period info click** → Opens planning period details modal
   - Shows full planning calendar
   - Event schedule for the period
   - Key deadlines
   - No screen navigation

3. **Voting deadline info click** → Opens voting timeline modal
   - Shows voting process timeline
   - Review and selection process
   - Notification schedule
   - No screen navigation

4. **Topic drag handle (☰)** → Enables drag to reorder
   - Drag topic to new position in rankings
   - Auto-saves rankings
   - Updates influence on other votes
   - No screen navigation

5. **Impact rating indicator click** → Opens impact rating guide modal
   - Explains high/medium/low impact
   - Provides rating guidance
   - Shows examples
   - No screen navigation

6. **Topic card click** → Navigate to `Topic Details Screen`
   - Full topic description
   - Historical vote data
   - Related content and events
   - Potential speakers
   - Community discussion

7. **Votes from other partners bar** → Shows tooltip with anonymized details
   - Number of partners voting
   - Vote distribution
   - Trending status
   - No screen navigation

8. **[Submit Votes] button** → Triggers vote submission
   - Validates all rankings
   - Shows confirmation modal
   - Submits votes to system
   - Shows success notification
   - No screen navigation

9. **[Add Custom Topic] button** → Scrolls to suggest new topics section
   - Smooth scroll to suggestion form
   - Highlights form
   - No screen navigation

10. **[View All Topics] button** → Navigate to `All Topics Browser Screen`
    - Searchable/filterable topic list
    - Category organization
    - Add to voting list functionality
    - Vote distribution visualization

11. **[Submit Suggestion] button** → Triggers topic suggestion submission
    - Validates form fields
    - Submits suggestion
    - Shows confirmation modal with review timeline
    - Clears form
    - No screen navigation

12. **[Save Draft] button** → Saves draft suggestion
    - Saves current form state
    - Shows save confirmation
    - Draft available for later editing
    - No screen navigation

13. **Community trend item click** → Navigate to `Topic Details Screen`
    - Shows topic details
    - Allows adding to voting list
    - Community voting insights

14. **Industry trend item click** → Navigate to `Industry Trend Report Screen`
    - Detailed trend analysis
    - Supporting research
    - Relevance to partner community
    - Related topics for voting

15. **Influence Score click** → Navigate to `Influence Score Details Screen`
    - Score breakdown and components
    - Historical trend
    - Improvement recommendations
    - Comparison with other partners

### Secondary Navigation (Form & Data Interactions)

16. **Target Audience checkboxes** → Updates submission
    - Selects target audience
    - May affect review priority
    - No screen navigation

17. **Topic title field (suggestion form)** → Triggers similar topic suggestions
    - Shows existing similar topics
    - Prevents duplicate suggestions
    - Offers to add existing topic instead
    - No screen navigation

18. **Business Justification field** → Character count and quality feedback
    - Shows character count
    - Provides writing quality tips
    - No screen navigation

### Event-Driven Navigation

19. **Vote rankings changed** → Triggers auto-save
    - Saves draft rankings periodically
    - Shows subtle save indicator
    - No screen navigation

20. **Votes submitted successfully** → Shows success notification
    - Confirmation message
    - Updated influence score
    - Voting session status update
    - No automatic navigation

21. **Topic suggestion submitted** → Shows confirmation notification
    - Suggestion ID provided
    - Expected review timeline
    - Links to suggestion tracking
    - No automatic navigation

22. **Draft saved** → Shows save notification
    - Save timestamp
    - Draft available in form
    - No automatic navigation

23. **Voting deadline approaching (7 days)** → Shows reminder banner
    - Countdown to deadline
    - Current vote status
    - Submit votes prompt
    - No automatic navigation

24. **New topic added to voting pool** → Shows notification
    - New topic alert
    - Link to topic details
    - Option to add to rankings
    - No automatic navigation

25. **Suggestion approved** → Shows success notification
    - Approval confirmation
    - Topic added to voting pool
    - Credit to suggesting partner
    - Links to vote for new topic

26. **Suggestion rejected** → Shows notification with feedback
    - Rejection reason
    - Improvement suggestions
    - Option to revise and resubmit
    - No automatic navigation

27. **Voting session closed** → Shows closure notification
    - Session ended message
    - Results available date
    - Thank you message
    - No automatic navigation

28. **Voting results published** → Shows notification with results link
    - Results available
    - Partner's impact on selections
    - Links to detailed results
    - No automatic navigation

29. **Similar topic detected (during suggestion)** → Shows suggestion modal
    - Lists similar existing topics
    - Suggests voting for existing instead
    - Option to proceed with new suggestion
    - No screen navigation

30. **Influence score updated** → Updates score display
    - Animated score change
    - Highlights contributing factors
    - No screen navigation

---
