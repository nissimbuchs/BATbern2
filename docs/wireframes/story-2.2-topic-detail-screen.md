# Topic Detail Screen - Wireframe

**Story**: Epic 2, Story 2.2 - Topic & Content Management Service
**Screen**: Topic Detail Screen
**User Role**: Organizer (primary), Partner (view-only)
**Related FR**: FR18 (Intelligent Topic Backlog)

---

## Visual Wireframe

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back to Topic Backlog            Topic Details                [Edit] [🔗 Share]    │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── TOPIC OVERVIEW ────────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  Cloud Native Architecture                                   ID: TOP-2025-014    │ │
│  │  Category: Infrastructure & Cloud                            Status: Available    │ │
│  │                                                                                   │ │
│  │  Description:                                                                     │ │
│  │  Modern approaches to building and deploying applications in cloud environments  │ │
│  │  using containers, orchestration, microservices, and cloud-native patterns.      │ │
│  │  Focus on scalability, resilience, and automation.                               │ │
│  │                                                                                   │ │
│  │  Tags: [Kubernetes] [Docker] [Microservices] [Cloud Architecture] [DevOps]       │ │
│  │                                                                                   │ │
│  │  Created: Jan 15, 2020 by Peter Müller (Organizer)                               │ │
│  │  Last Modified: Sept 12, 2024                                                     │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── USAGE METRICS ─────────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  ┌─── Overall Stats ──────────────────────────────────────────────────────────┐  │ │
│  │  │                                                                             │  │ │
│  │  │  Total Events: 6          Average Attendance: 187        Usage Rate: 1.2/yr │  │ │
│  │  │  Total Speakers: 12       Average Rating: 4.6/5.0       Staleness: 🟢 42%   │  │ │
│  │  │  Total Downloads: 1,247   Engagement Rate: 89%          Wait Time: 6+ mo   │  │ │
│  │  └─────────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  Last Used: March 2023 (Spring Conference 2023)                                  │ │
│  │  Recommended: ✅ Ready to reuse (18 months since last use)                       │ │
│  │  Next Suggested: Q2 2025 or later                                                │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── USAGE HISTORY ─────────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  6 Events Found                                         [Timeline View] [List View]│ │
│  │                                                                                   │ │
│  │  ┌───────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ 📅 Spring Conference 2023 • March 15, 2023                                │  │ │
│  │  │                                                                            │  │ │
│  │  │ Session: "Cloud Native Architecture Best Practices"                       │  │ │
│  │  │ Speaker: Dr. Anna Schmidt (TechCorp AG)                                   │  │ │
│  │  │ Attendance: 187 attendees • Rating: 4.6★ (45 reviews)                    │  │ │
│  │  │ Downloads: 342 • Engagement: 92%                                          │  │ │
│  │  │                                                                            │  │ │
│  │  │ Key Feedback:                                                              │  │ │
│  │  │ "Excellent overview of modern cloud patterns" - Top rated comment         │  │ │
│  │  │ "Would love more hands-on demos" - Suggestion                             │  │ │
│  │  │                                                                            │  │ │
│  │  │ [View Event Details →] [View Presentation →]                              │  │ │
│  │  └───────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  ┌───────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ 📅 Autumn Conference 2021 • October 20, 2021                              │  │ │
│  │  │                                                                            │  │ │
│  │  │ Session: "Kubernetes in Production: Lessons Learned"                      │  │ │
│  │  │ Speaker: Michael Weber (SwissBank Ltd)                                    │  │ │
│  │  │ Attendance: 165 attendees • Rating: 4.8★ (38 reviews)                    │  │ │
│  │  │ Downloads: 289 • Engagement: 87%                                          │  │ │
│  │  │                                                                            │  │ │
│  │  │ [View Event Details →] [View Presentation →]                              │  │ │
│  │  └───────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  ┌───────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ 📅 Spring Conference 2020 • April 8, 2020                                 │  │ │
│  │  │                                                                            │  │ │
│  │  │ Session: "Introduction to Cloud Native Development"                       │  │ │
│  │  │ Speaker: Dr. Peter Müller (TechCorp AG)                                   │  │ │
│  │  │ Attendance: 201 attendees • Rating: 4.4★ (52 reviews)                    │  │ │
│  │  │ Downloads: 416 • Engagement: 85%                                          │  │ │
│  │  │                                                                            │  │ │
│  │  │ [View Event Details →] [View Presentation →]                              │  │ │
│  │  └───────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  [Load More...] (3 more events)                                                  │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── PERFORMANCE TRENDS ─────────────────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  Attendance Trend                    Rating Trend               Engagement Trend │  │
│  │  ┌─────────────────┐                ┌──────────────┐           ┌───────────────┐ │  │
│  │  │     ▲           │                │   ★          │           │      %        │ │  │
│  │  │ 220 │ ●         │                │ 5 ─          │           │ 100─          │ │  │
│  │  │     │   ●   ●   │                │   │ ●   ●    │           │    │ ● ● ●    │ │  │
│  │  │ 180 │     ●  ●● │                │ 4 │ ● ● ●    │           │ 80 │ ●   ● ●  │ │  │
│  │  │     │ ●         │                │   │          │           │    │          │ │  │
│  │  │ 140 └───────────│                │ 3 └──────────│           │ 60─          │ │  │
│  │  │      2020  2025 │                │   2020  2025 │           │     2020 2025│ │  │
│  │  └─────────────────┘                └──────────────┘           └───────────────┘ │  │
│  │                                                                                 │  │
│  │  Insight: Attendance stable, ratings improving, engagement consistently high   │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── PARTNER INTEREST ──────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  Total Votes: 24 👍          Priority: HIGH          Interest Level: 🔥 Trending  │ │
│  │                                                                                   │ │
│  │  Top Sponsors:                                                                    │ │
│  │  • UBS AG (Gold Partner) - 5 votes - "Critical for our cloud transformation"    │ │
│  │  • Swiss Re (Gold Partner) - 4 votes - "Very relevant for teams"                │ │
│  │  • Credit Suisse (Silver) - 3 votes - "Want deeper security focus"              │ │
│  │  • PostFinance (Silver) - 3 votes - "Aligns with our roadmap"                   │ │
│  │                                                                                   │ │
│  │  Recent Comments:                                                                 │ │
│  │  ┌───────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ "We're migrating 200+ services to cloud-native. This topic is essential   │  │ │
│  │  │  for our teams. Please include cost optimization aspects."                 │  │ │
│  │  │  - Maria Weber, UBS AG • Sept 5, 2024                                      │  │ │
│  │  └───────────────────────────────────────────────────────────────────────────┘  │ │
│  │  ┌───────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ "Would appreciate focus on security best practices in cloud native apps."  │  │ │
│  │  │  - Thomas Keller, Swiss Re • Aug 28, 2024                                  │  │ │
│  │  └───────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  [View All Partner Feedback (24) →]                                              │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── SIMILARITY ANALYSIS ────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  Related Topics (ML Similarity Score)                                            │ │
│  │                                                                                   │ │
│  │  ┌─────────────────────────────────────────────────────────────────────────┐    │ │
│  │  │ Kubernetes Orchestration                              Similarity: 22% 🟡 │    │ │
│  │  │ Last used: Nov 2024 • Status: Recently used                              │    │ │
│  │  │ [View Topic →]                                                           │    │ │
│  │  └─────────────────────────────────────────────────────────────────────────┘    │ │
│  │                                                                                   │ │
│  │  ┌─────────────────────────────────────────────────────────────────────────┐    │ │
│  │  │ Microservices Architecture                            Similarity: 18% 🟢 │    │ │
│  │  │ Last used: May 2022 • Status: Available for reuse                        │    │ │
│  │  │ [View Topic →]                                                           │    │ │
│  │  └─────────────────────────────────────────────────────────────────────────┘    │ │
│  │                                                                                   │ │
│  │  ┌─────────────────────────────────────────────────────────────────────────┐    │ │
│  │  │ Container Security                                    Similarity: 15% 🟢 │    │ │
│  │  │ Last used: March 2021 • Status: Available for reuse                      │    │ │
│  │  │ [View Topic →]                                                           │    │ │
│  │  └─────────────────────────────────────────────────────────────────────────┘    │ │
│  │                                                                                   │ │
│  │  ℹ️ Similarity indicators: 🔴 >70% (too similar), 🟡 40-70% (related), 🟢 <40% │ │
│  │  [View All Similar Topics (12) →]                                                │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── SPEAKER HISTORY ────────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  12 Speakers Total                                       [View All Speakers →]    │ │
│  │                                                                                   │ │
│  │  Most Frequent:                                                                   │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐ │ │
│  │  │ [Photo] Dr. Peter Müller (TechCorp AG)                                     │ │ │
│  │  │         3 presentations • 4.5★ avg rating                                  │ │ │
│  │  │         Topics: Cloud Native, Kubernetes, DevOps                           │ │ │
│  │  │         [View Speaker Profile →]                                           │ │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘ │ │
│  │                                                                                   │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐ │ │
│  │  │ [Photo] Dr. Anna Schmidt (TechCorp AG)                                     │ │ │
│  │  │         2 presentations • 4.7★ avg rating                                  │ │ │
│  │  │         Topics: Cloud Architecture, Microservices                          │ │ │
│  │  │         [View Speaker Profile →]                                           │ │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘ │ │
│  │                                                                                   │ │
│  │  Other Contributors: (7 speakers)                                                │ │
│  │  Michael Weber, Sarah König, Thomas Keller, Lisa Meier... [Show All →]          │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── AI INSIGHTS & RECOMMENDATIONS ─────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  🤖 AI Analysis:                                                                  │ │
│  │                                                                                   │ │
│  │  ✅ **Reuse Recommended**: Topic has matured well, consistent high ratings        │ │
│  │  📊 **Trend**: Cloud native adoption increasing 35% YoY in Switzerland           │ │
│  │  💡 **Suggestion**: Combine with "FinOps & Cloud Cost" for added value           │ │
│  │  ⏰ **Timing**: Q2 2025 ideal - aligns with industry cloud migration peak        │ │
│  │  🎯 **Focus Areas**: Based on partner feedback, emphasize security & cost        │ │
│  │                                                                                   │ │
│  │  Related Industry Trends:                                                         │ │
│  │  • Platform Engineering (emerging trend)                                          │ │
│  │  • FinOps & Cloud Cost Optimization (high search volume)                         │ │
│  │  • Zero Trust Security in Cloud (compliance driver)                              │ │
│  │                                                                                   │ │
│  │  [View Detailed AI Analysis →]                                                    │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── ACTIONS ────────────────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  [✅ Select for Event] [📧 Notify Partners] [📊 Export Report] [🗑️ Archive]      │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Timeline View (Alternative Visualization)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│  ┌─── USAGE TIMELINE ────────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  2020       2021       2022       2023       2024       2025                     │ │
│  │  ──┬─────────┬─────────┬─────────┬─────────┬─────────┬──                        │ │
│  │    │         │         │         │         │         │                          │ │
│  │    ●─────────●─────────●─────────●─────────●─────────●─── NOW                   │ │
│  │  Apr 20   Oct 21   May 22   Mar 23   Sept 24  (Ready)                           │ │
│  │  201👥    165👥    178👥    187👥    -         ?                                 │ │
│  │  4.4★     4.8★     4.5★     4.6★     -         -                                │ │
│  │                                                                                   │ │
│  │  Average Interval: 10.5 months                                                   │ │
│  │  Last Gap: 18 months (longest to date)                                           │ │
│  │  Recommended: Can safely reuse in Q2 2025                                        │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Interactive Elements

### Topic Overview Section
- **Edit Button**: Opens topic editor (organizer-only)
- **Share Button**: Generate shareable link to topic details
- **Tag Chips**: Clickable tags to find similar topics
- **Status Badge**: Visual indicator of topic availability

### Usage Metrics Section
- **Staleness Indicator**: Color-coded freshness score (green/yellow/red)
- **Recommended Usage**: AI-calculated optimal reuse timing
- **Stats Cards**: Click for detailed breakdowns
- **Wait Time Badge**: Shows recommended wait period

### Usage History Section
- **View Toggle**: Switch between timeline and list view
- **Event Cards**: Expandable cards showing event details
- **View Event Details**: Navigate to full event information
- **View Presentation**: Download or preview presentation materials
- **Load More**: Pagination for events beyond initial display

### Performance Trends Section
- **Trend Charts**: Interactive charts showing metrics over time
- **Data Points**: Hover for exact values and dates
- **Insight Box**: AI-generated insights from trend analysis

### Partner Interest Section
- **Vote Count**: Shows total partner votes
- **Priority Badge**: Visual priority indicator (HIGH/MEDIUM/LOW)
- **Sponsor Cards**: Company cards with vote counts and comments
- **View All Feedback**: Navigate to complete partner feedback

### Similarity Analysis Section
- **Related Topic Cards**: Similar topics with similarity scores
- **Color Indicators**: Visual similarity warnings (red/yellow/green)
- **View Topic Links**: Navigate to related topic details
- **View All Similar**: See complete similarity analysis

### Speaker History Section
- **Speaker Cards**: Most frequent speakers with stats
- **View Speaker Profile**: Navigate to full speaker profile
- **Show All**: Expand to see all speakers

### AI Insights Section
- **Recommendation Badges**: Visual AI recommendations
- **Trend Analysis**: Industry trend indicators
- **Focus Areas**: Partner-driven emphasis areas
- **Detailed Analysis**: Navigate to comprehensive AI report

### Action Buttons
- **Select for Event**: Opens event selection modal
- **Notify Partners**: Send notification about topic selection
- **Export Report**: Download topic analytics report
- **Archive**: Archive inactive topic

---

## Functional Requirements Met

- **FR18**: Intelligent topic backlog with complete usage tracking and analytics
- **Historical Analysis**: Full 20+ year topic usage history with trends
- **Partner Integration**: Partner voting, comments, and priority tracking
- **AI Recommendations**: ML-powered insights and reuse recommendations
- **Similarity Detection**: Avoid topic repetition with ML similarity scoring
- **Performance Analytics**: Track attendance, ratings, engagement over time

---

## User Interactions

### For Organizers (Full Access)
1. **View Topic Details**: See complete topic information and history
2. **Review Performance**: Analyze trends in attendance, ratings, engagement
3. **Check Staleness**: Verify if topic is ready for reuse
4. **Review Partner Interest**: See partner votes, comments, and priorities
5. **Analyze Similarity**: Check for overlapping topics to avoid repetition
6. **View Speaker History**: See all speakers who presented on this topic
7. **Get AI Insights**: Access ML-powered recommendations and timing suggestions
8. **Select for Event**: Assign topic to upcoming event
9. **Edit Topic**: Modify title, description, tags, category
10. **Export Report**: Download topic analytics for planning
11. **Archive Topic**: Remove outdated topic from active backlog
12. **Notify Partners**: Inform partners when topic is selected

### For Partners (View-Only)
13. **View Topic Details**: See topic information and usage history
14. **Review Past Events**: See where topic was used previously
15. **View Partner Feedback**: See other partner comments and votes
16. **Track Interest**: Monitor voting and priority levels
17. **View AI Insights**: Access trend analysis and recommendations

### Common Interactions
18. **Timeline View**: Switch to visual timeline of topic usage
19. **View Related Topics**: Navigate to similar topics
20. **View Speaker Profiles**: Navigate to speaker detail pages
21. **View Event Details**: Navigate to historical event pages
22. **Download Presentations**: Access presentation materials from past events
23. **Share Topic**: Generate shareable link to topic details

---

## Technical Notes

### Component Structure
- **TopicDetailScreen.tsx**: Main topic detail view component
- **TopicOverview.tsx**: Topic header with metadata
- **UsageMetrics.tsx**: Staleness and recommendation metrics
- **UsageHistory.tsx**: Historical events list with performance data
- **PerformanceTrends.tsx**: Chart visualization using Recharts
- **PartnerInterest.tsx**: Partner voting and feedback display
- **SimilarityAnalysis.tsx**: Related topics with ML scores
- **SpeakerHistory.tsx**: Speaker contribution list
- **AIInsights.tsx**: ML-powered recommendations panel
- **TimelineView.tsx**: Alternative timeline visualization
- **ActionPanel.tsx**: Topic action buttons

### State Management
- **Local State**: View mode (list/timeline), expanded sections, chart filters
- **Zustand Store**: Selected topic ID, view preferences
- **React Query**: Server state for topic data
  - `topicDetail` query: Cached for 10 minutes
  - `topicHistory` query: Cached for 15 minutes
  - `partnerFeedback` query: Cached for 5 minutes
  - `similarityAnalysis` query: Cached for 30 minutes
  - `performanceTrends` query: Cached for 15 minutes

### API Integration
- **Topic Detail**: `GET /api/v1/topics/backlog/{topicId}/details`
- **Usage History**: `GET /api/v1/topics/backlog/{topicId}/history`
- **Performance Trends**: `GET /api/v1/topics/backlog/{topicId}/trends`
- **Partner Feedback**: `GET /api/v1/topics/backlog/{topicId}/partner-feedback`
- **Similarity**: `GET /api/v1/topics/backlog/{topicId}/similarity`
- **Staleness**: `GET /api/v1/topics/backlog/{topicId}/staleness`
- **Speaker History**: `GET /api/v1/topics/backlog/{topicId}/speakers`
- **AI Insights**: `GET /api/v1/topics/backlog/{topicId}/ai-insights`
- **Select Topic**: `POST /api/v1/events/{eventId}/topics`
- **Update Topic**: `PUT /api/v1/topics/backlog/{topicId}`
- **Archive Topic**: `DELETE /api/v1/topics/backlog/{topicId}`
- **Export**: `GET /api/v1/topics/backlog/{topicId}/export?format=pdf`

### Performance Optimization
- **Data Prefetching**: Prefetch related topics and speaker profiles
- **Chart Lazy Loading**: Lazy load chart libraries (Recharts) on demand
- **Image Optimization**: Lazy load speaker photos
- **Pagination**: Load historical events in batches of 3
- **Memoization**: Memoize trend calculations and similarity scores

### Caching Strategy
- **Topic Detail**: 10-minute cache with background refresh
- **Performance Trends**: 15-minute cache (data changes infrequently)
- **Similarity Analysis**: 30-minute cache (ML scores stable)
- **Partner Feedback**: 5-minute cache (can change frequently with voting)
- **Cache Invalidation**: Invalidate on topic update, event selection

### Accessibility
- **ARIA Labels**: Descriptive labels for all interactive elements
- **Keyboard Navigation**: Full keyboard access to all features
- **Screen Reader**: Semantic HTML and proper heading hierarchy
- **Focus Management**: Proper focus handling for modals and dropdowns
- **Color Contrast**: WCAG 2.1 AA compliant (4.5:1 minimum)

---

## API Requirements

### Initial Page Load APIs

When the Topic Detail Screen loads, the following APIs are called:

1. **GET /api/v1/topics/backlog/{topicId}/details**
   - Returns: Complete topic information
   - Response: `{ id, title, description, category, tags, createdAt, createdBy, lastModifiedAt, status }`
   - Used for: Populate topic overview section

2. **GET /api/v1/topics/backlog/{topicId}/metrics**
   - Returns: Overall usage statistics
   - Response: `{ totalEvents, totalSpeakers, totalDownloads, avgAttendance, avgRating, avgEngagement, usageRate, stalenessScore }`
   - Used for: Display usage metrics cards

3. **GET /api/v1/topics/backlog/{topicId}/staleness**
   - Returns: Staleness analysis and recommendations
   - Response: `{ stalenessScore, lastUsedAt, daysSinceLastUse, recommendedWaitMonths, recommendation }`
   - Used for: Show staleness indicators and recommendations

4. **GET /api/v1/topics/backlog/{topicId}/history**
   - Query params: `limit=3, offset=0, sortBy=date, order=desc`
   - Returns: Paginated list of events using this topic
   - Response: `{ events: [{ eventId, eventName, eventDate, sessionTitle, speaker, attendance, rating, downloads, engagement, topFeedback }], pagination }`
   - Used for: Display usage history section

5. **GET /api/v1/topics/backlog/{topicId}/trends**
   - Query params: `metrics=[attendance,rating,engagement], period=all`
   - Returns: Time-series performance data
   - Response: `{ attendance: [{year, value}], rating: [{year, value}], engagement: [{year, value}], insights }`
   - Used for: Render performance trend charts

6. **GET /api/v1/topics/backlog/{topicId}/partner-feedback**
   - Query params: `limit=3, includeComments=true`
   - Returns: Partner voting and feedback summary
   - Response: `{ totalVotes, priority, interestLevel, topSponsors: [{company, partnerTier, votes, comment}], recentComments: [{text, author, company, date}] }`
   - Used for: Display partner interest section

7. **GET /api/v1/topics/backlog/{topicId}/similarity**
   - Query params: `threshold=0.15, limit=3`
   - Returns: Similar topics with ML scores
   - Response: `{ similarTopics: [{topicId, title, similarityScore, similarityType, lastUsedAt, recommendedAction}] }`
   - Used for: Display similarity analysis section

8. **GET /api/v1/topics/backlog/{topicId}/speakers**
   - Query params: `limit=2, sortBy=frequency`
   - Returns: Speakers who presented on this topic
   - Response: `{ totalSpeakers, speakers: [{speakerId, name, company, photo, presentationCount, avgRating, topics}], otherSpeakers: string[] }`
   - Used for: Display speaker history section

9. **GET /api/v1/topics/backlog/{topicId}/ai-insights**
   - Returns: AI-generated insights and recommendations
   - Response: `{ recommendation, trend, suggestion, timing, focusAreas, relatedTrends }`
   - Used for: Display AI insights section

### Action APIs

APIs called by user interactions:

#### Topic Management

10. **PUT /api/v1/topics/backlog/{topicId}**
    - Triggered by: [Edit] button save
    - Payload: `{ title, description, category, tags, status }`
    - Returns: Updated topic object
    - Side effects: Recalculates similarity scores, updates search index

11. **DELETE /api/v1/topics/backlog/{topicId}**
    - Triggered by: [Archive] button confirmation
    - Payload: `{ archiveReason, alternativeTopicId? }`
    - Returns: `{ success: true, archivedAt }`
    - Side effects: Soft delete, preserves history

12. **POST /api/v1/events/{eventId}/topics**
    - Triggered by: [Select for Event] button
    - Payload: `{ topicId, primaryTopic: true, notes }`
    - Returns: `{ success: true, warnings: [] }`
    - Side effects: Assigns topic, updates last used date, triggers notifications

13. **GET /api/v1/topics/backlog/{topicId}/share-link**
    - Triggered by: [Share] button
    - Returns: `{ shareUrl, shortUrl, expiresAt }`
    - Used for: Generate shareable link

#### History & Trends

14. **GET /api/v1/topics/backlog/{topicId}/history**
    - Triggered by: [Load More] button
    - Query params: `limit=3, offset={currentOffset}`
    - Returns: Next batch of historical events
    - Used for: Load additional usage history

15. **GET /api/v1/events/{eventId}/details**
    - Triggered by: [View Event Details] link
    - Returns: Complete event information
    - Opens: Event detail screen

16. **GET /api/v1/presentations/{presentationId}/download**
    - Triggered by: [View Presentation] link
    - Returns: Presentation file URL or download
    - Opens: Presentation viewer or download

#### Partner Feedback

17. **GET /api/v1/topics/backlog/{topicId}/partner-feedback**
    - Triggered by: [View All Partner Feedback] link
    - Query params: `limit=50, offset=0`
    - Returns: Complete partner feedback list
    - Opens: Partner feedback modal or page

18. **POST /api/v1/topics/backlog/{topicId}/notify-partners**
    - Triggered by: [Notify Partners] button
    - Payload: `{ message, notificationType: "selected|planning", recipients: [] }`
    - Returns: `{ sentCount, failureCount }`
    - Side effects: Sends email notifications to partners

#### Similarity & Related Topics

19. **GET /api/v1/topics/backlog/{relatedTopicId}/details**
    - Triggered by: [View Topic] link in similarity section
    - Returns: Related topic details
    - Opens: Topic detail screen for related topic

20. **GET /api/v1/topics/backlog/{topicId}/similarity**
    - Triggered by: [View All Similar Topics] link
    - Query params: `limit=50, threshold=0.15`
    - Returns: Complete similarity analysis
    - Opens: Similarity analysis modal

#### Speaker Interactions

21. **GET /api/v1/speakers/{speakerId}/profile**
    - Triggered by: [View Speaker Profile] link
    - Returns: Complete speaker profile
    - Opens: Speaker profile detail view

22. **GET /api/v1/topics/backlog/{topicId}/speakers**
    - Triggered by: [View All Speakers] link
    - Query params: `limit=50, offset=0`
    - Returns: Complete speaker list
    - Opens: Speaker list modal

#### AI & Analytics

23. **GET /api/v1/topics/backlog/{topicId}/ai-insights/detailed**
    - Triggered by: [View Detailed AI Analysis] link
    - Returns: Comprehensive AI analysis report
    - Response: `{ recommendation, confidence, dataPoints, industryTrends, competitorAnalysis, marketDemand, suggestedCombinations }`
    - Opens: Detailed AI insights modal or page

24. **GET /api/v1/topics/backlog/{topicId}/export**
    - Triggered by: [Export Report] button
    - Query params: `format=pdf, includeHistory=true, includeMetrics=true`
    - Returns: `{ downloadUrl, expiresAt }`
    - Opens: Download dialog

---

## Navigation Map

### Primary Navigation Actions

1. **← Back to Topic Backlog button** → Navigate to Topic Backlog Management
   - Type: Full page navigation
   - Target: [story-2.2-topic-backlog-management.md](story-2.2-topic-backlog-management.md)
   - Context: Returns to main topic backlog screen

2. **[Edit] button** → Opens topic editor
   - Type: Modal overlay
   - Opens: Topic edit form (inline modal)
   - Role-based access: Organizer only
   - Pre-fills: Current topic data

3. **[Share] button** → Opens share modal
   - Type: Modal overlay
   - Opens: Share dialog with generated link
   - Content: Shareable URL, copy button, email share
   - API call: `GET /api/v1/topics/backlog/{topicId}/share-link`

4. **Tag click** → Navigate to Topic Backlog filtered by tag
   - Type: Full page navigation
   - Target: [story-2.2-topic-backlog-management.md](story-2.2-topic-backlog-management.md)
   - Context: Pre-filtered by selected tag

### Usage History Navigation

5. **[View Event Details →] link** → Navigate to event detail
   - Type: Full page navigation
   - Target: Event Detail Page [story-2.4-current-event-landing.md](story-2.4-current-event-landing.md) or Historical Archive
   - Context: Event ID passed to event detail screen

6. **[View Presentation →] link** → Opens presentation viewer
   - Type: Modal or new tab (depending on file type)
   - Opens: Presentation viewer (PDF viewer, slide viewer, or file download)
   - API call: `GET /api/v1/presentations/{presentationId}/download`

7. **[Load More...] button** → Loads additional events
   - Type: In-place content expansion
   - API call: `GET /api/v1/topics/backlog/{topicId}/history` with pagination
   - Visual feedback: Loading spinner, then additional cards appear

8. **[Timeline View] / [List View] toggle** → Switch visualization
   - Type: In-place view change
   - No navigation, re-renders history section in different layout

### Partner Interest Navigation

9. **[View All Partner Feedback (24) →] link** → Opens complete feedback
   - Type: Modal or full page
   - Opens: Partner feedback list with filters
   - API call: `GET /api/v1/topics/backlog/{topicId}/partner-feedback` (full dataset)

10. **Sponsor name click** → Navigate to partner/company profile
    - Type: Full page navigation
    - Target: Company Management Screen [story-1.14-company-management-screen.md](story-1.14-company-management-screen.md)
    - Context: Company ID passed

### Similarity Analysis Navigation

11. **[View Topic →] link (similar topic)** → Navigate to related topic
    - Type: Full page navigation (self)
    - Target: Topic Detail Screen (this screen) with different topic ID
    - Context: Loads related topic details

12. **[View All Similar Topics (12) →] link** → Opens similarity modal
    - Type: Modal overlay
    - Opens: Complete similarity analysis with all related topics
    - API call: `GET /api/v1/topics/backlog/{topicId}/similarity` (full dataset)

### Speaker History Navigation

13. **[View Speaker Profile →] link** → Navigate to speaker profile
    - Type: Full page navigation
    - Target: Speaker Profile Detail View [story-7.1-speaker-profile-detail-view.md](story-7.1-speaker-profile-detail-view.md)
    - Context: Speaker ID passed

14. **[Show All →] link (speakers)** → Opens speaker list
    - Type: Modal overlay
    - Opens: Complete speaker list for this topic
    - API call: `GET /api/v1/topics/backlog/{topicId}/speakers` (full dataset)

### AI Insights Navigation

15. **[View Detailed AI Analysis →] link** → Opens AI report
    - Type: Modal or full page
    - Opens: Comprehensive AI analysis with detailed recommendations
    - API call: `GET /api/v1/topics/backlog/{topicId}/ai-insights/detailed`

16. **Industry trend link** → Navigate to trend analysis
    - Type: External or internal navigation
    - Target: Industry trend analysis page or external resource
    - Opens: In new tab if external

### Action Button Navigation

17. **[Select for Event] button** → Opens event selection modal
    - Type: Modal overlay
    - Opens: Event picker with validation warnings
    - API call: `POST /api/v1/events/{eventId}/topics` on confirmation
    - On success: Shows toast, updates topic status

18. **[Notify Partners] button** → Opens notification composer
    - Type: Modal overlay
    - Opens: Email composition modal with partner recipient list
    - API call: `POST /api/v1/topics/backlog/{topicId}/notify-partners`
    - On success: Shows confirmation toast

19. **[Export Report] button** → Downloads analytics report
    - Type: File download
    - API call: `GET /api/v1/topics/backlog/{topicId}/export?format=pdf`
    - Opens: Download dialog or downloads directly

20. **[Archive] button** → Opens archive confirmation
    - Type: Modal overlay
    - Opens: Archive confirmation with reason field
    - API call: `DELETE /api/v1/topics/backlog/{topicId}` on confirmation
    - On success: Navigates back to Topic Backlog

### Chart & Trend Interactions

21. **Trend chart data point click** → Shows detail tooltip
    - Type: In-place tooltip
    - Displays: Exact values, date, event link
    - No navigation

22. **Chart hover** → Shows data tooltip
    - Type: Hover tooltip
    - Displays: Metric value at point
    - No navigation

### Error States & Redirects

23. **Topic not found** → Navigate to topic backlog with error
    - Type: Full page navigation
    - Target: Topic Backlog Management
    - Message: "Topic not found or has been archived"

24. **Unauthorized access** → Show permission error
    - Type: Inline error message
    - Message: "You don't have permission to view this topic"
    - No automatic navigation, restricted features hidden

25. **API error loading data** → Show error state
    - Type: In-page error message
    - Message: "Unable to load topic details"
    - Action: [Retry] button to reload data
    - No automatic navigation

26. **Network error** → Show offline state
    - Type: Banner notification
    - Message: "You are offline. Showing cached data."
    - Shows: Last cached version of topic details
    - Auto-retries: When connection restored

---

## Responsive Design Considerations

### Mobile Layout Changes

- **Topic Overview**: Full-width with stacked layout
- **Metrics Cards**: Single column, stacked vertically
- **Charts**: Simplified charts with swipe navigation
- **History Cards**: Full-width, expandable sections
- **Partner Feedback**: Collapse into expandable accordion
- **Similarity Analysis**: Swipeable carousel for related topics
- **Action Buttons**: Fixed bottom bar with primary actions

### Tablet Layout Changes

- **Two-Column Layout**: Metrics on left, trends on right
- **Grid Charts**: 2x2 grid for performance trends
- **Speaker Cards**: Two-column grid
- **History Timeline**: Horizontal scrollable timeline

### Mobile-Specific Interactions

- **Swipe Gestures**: Swipe between history events
- **Pull to Refresh**: Refresh topic data
- **Tap to Expand**: Expand/collapse sections (partner feedback, AI insights)
- **Bottom Sheet**: Open actions in bottom sheet instead of dropdown
- **Sticky Header**: Topic name sticky at top when scrolling

---

## Accessibility Notes

- **Keyboard Navigation**: All interactive elements accessible via Tab key
- **ARIA Labels**:
  - `aria-label="Edit topic details"` on edit button
  - `aria-label="Share topic link"` on share button
  - `aria-label="Select topic for event"` on select button
  - `aria-label="Staleness score: 42%, ready for reuse"` on staleness indicator
- **Focus Indicators**: 2px solid border on focused elements
- **Screen Reader Support**:
  - Chart data announced with ARIA live regions
  - Trend insights read as supplementary information
  - Partner feedback count announced: "24 partner votes, high priority"
- **Color Contrast**: All text meets WCAG 2.1 AA standards (4.5:1 minimum)
- **Alt Text**: Charts include descriptive alt text and data tables
- **Skip Links**: "Skip to topic actions" link for keyboard users
- **Semantic HTML**: Proper heading hierarchy (h2, h3, h4)

---

## State Management

### Local Component State

- `viewMode`: 'list' | 'timeline' - History display mode
- `expandedSections`: string[] - Expanded section IDs
- `chartFilters`: object - Active chart filter settings
- `showAllSpeakers`: boolean - Speaker list expansion state
- `isEventSelectionOpen`: boolean - Event selection modal state
- `isShareModalOpen`: boolean - Share modal state

### Global State (Zustand Store)

- `topics.selectedTopicId`: string - Currently viewed topic
- `topics.viewPreferences`: object - User's saved view preferences
- `auth.currentUser`: User - Current authenticated user
- `auth.currentRole`: UserRole - Current user role (organizer, partner)

### Server State (React Query)

- `topicDetail`: Topic details (cached for 10 minutes)
- `topicMetrics`: Usage metrics (cached for 15 minutes)
- `topicHistory`: Historical events (cached for 15 minutes)
- `partnerFeedback`: Partner votes/comments (cached for 5 minutes)
- `similarityAnalysis`: Related topics (cached for 30 minutes)
- `performanceTrends`: Trend data (cached for 15 minutes)
- `speakerHistory`: Speaker list (cached for 10 minutes)
- `aiInsights`: AI recommendations (cached for 20 minutes)

### Real-Time Updates

- **Partner Votes**: Live update when new partner votes added
- **Event Selection**: Real-time notification when topic selected for event
- **Staleness Score**: Periodic recalculation (daily)
- **AI Insights**: Background refresh for trend updates

---

## Edge Cases & Error Handling

- **No Usage History**: Show "No events have used this topic yet" with suggestion to be first
- **No Partner Feedback**: Show "No partner feedback yet" with invitation to vote
- **No Similar Topics**: Show "No similar topics found" with unique topic badge
- **Archived Topic**: Show archive banner with reason and alternative topic suggestion
- **Loading State**: Display skeleton screens while data loads
- **API Error**: Show error message with [Retry] button
- **Permission Denied**: Hide organizer-only actions for partners
- **Offline Mode**: Show cached data with "Viewing offline version" banner
- **Slow Network**: Show progress indicators for >2 seconds
- **Empty Metrics**: Show "Data not available" instead of zeros
- **Chart Render Error**: Fallback to table view for trend data
- **Timeline Calculation Error**: Fall back to list view
- **Export Timeout**: Show "Export taking longer than expected" with background processing

---

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-10-04 | 1.0 | Initial wireframe creation for Topic Detail Screen | Sally (UX Expert) |

---

## Review Notes

### Open Questions

1. **Edit Permissions**: Should partners be allowed to suggest edits to topic descriptions?
2. **Speaker Notifications**: Should speakers be notified when their past presentations are referenced?
3. **Version History**: Should we track topic description changes over time?
4. **Topic Merging**: What workflow for merging duplicate topics discovered through similarity analysis?
5. **AI Confidence Score**: Should we display AI confidence levels to help organizers trust recommendations?
6. **Export Formats**: Beyond PDF, what other formats would be valuable (CSV, Excel, PowerPoint)?

### Design Iterations

- **v1.0**: Initial comprehensive design with full analytics and ML insights
- Consider adding: Topic evolution timeline showing how topic focus has shifted over years
- Consider adding: Competitive analysis showing how topic compares to industry standard events
- Consider adding: Budget impact analysis for topics (high-cost vs. low-cost to deliver)

### Stakeholder Feedback

- Pending review from organizers for AI insights usefulness validation
- Need to validate staleness calculation with historical topic reuse patterns
- Confirm partner feedback display with privacy requirements
- Validate trend chart types with organizers for decision-making value
