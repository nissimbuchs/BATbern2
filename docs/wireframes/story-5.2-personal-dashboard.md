# Story: Personal Attendee Dashboard - Wireframe

**Story**: Epic 5, Story 2
**Screen**: Personal Attendee Dashboard
**User Role**: Attendee
**Related FR**: FR13 (Personalization)

---

## 4. Personal Attendee Dashboard (Logged In)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ BATbern                                                    John Smith ▼  [Logout]    │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  Welcome back, John! Here's your personalized BATbern experience.                    │
│                                                                                       │
│  ┌──── YOUR UPCOMING EVENTS ──────────────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  ┌──────────────────────────────────────────────────────────────────────────┐  │  │
│  │  │ 📅 Spring Conference 2025                           In 45 days            │  │  │
│  │  │ May 15, 2025 • Kursaal Bern                                              │  │  │
│  │  │                                                                           │  │  │
│  │  │ Your Schedule:                              Actions:                     │  │  │
│  │  │ • 09:45 Kubernetes Best Practices            [📅 Add to Calendar]        │  │  │
│  │  │ • 13:30 Hands-on Workshop                    [📧 Email Ticket]           │  │  │
│  │  │ • 15:00 Container Orchestration               [📍 Get Directions]        │  │  │
│  │  │                                                [👥 Who's Attending]       │  │  │
│  │  └──────────────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                                 │  │
│  │  No other events registered. [Browse Upcoming Events →]                        │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── RECOMMENDED FOR YOU ────────┬──── YOUR CONTENT LIBRARY ──────────────────┐   │
│  │                                  │                                             │   │
│  │  Based on your interests:        │  Saved Presentations (12)                  │   │
│  │                                  │                                             │   │
│  │  🎯 "Zero Trust in K8s"          │  📄 K8s Security Best Practices            │   │
│  │     Thomas Weber • 2024          │     Thomas Weber • 2024 • Downloaded       │   │
│  │     95% match to your interests  │                                             │   │
│  │     [View] [Save]                │  📄 Container Orchestration Guide          │   │
│  │                                  │     Sara Kim • 2023 • Downloaded           │   │
│  │  🔥 "GitOps Workflows"           │                                             │   │
│  │     Marc Baum • 2024             │  📄 DevOps Transformation                   │   │
│  │     Trending in your company     │     Peter Muller • 2023 • Bookmarked       │   │
│  │     [View] [Save]                │                                             │   │
│  │                                  │  [View All] [Manage Downloads]             │   │
│  │  [More Recommendations →]         │                                             │   │
│  └──────────────────────────────────┴─────────────────────────────────────────────┘  │
│                                                                                       │
│  ┌──── YOUR LEARNING PATH ─────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Kubernetes Mastery Track                                    Level: Intermediate │ │
│  │  ━━━━━━━━━━━━━━━━━━━━●━━━━━━━━━━━━━━  60% Complete                             │ │
│  │                                                                                  │ │
│  │  ✓ Introduction to Containers        ✓ K8s Fundamentals      ● K8s Security    │ │
│  │  ✓ Docker Deep Dive                  ✓ Advanced Deployments  ○ Service Mesh    │ │
│  │                                                                                  │ │
│  │  Next: "Kubernetes Security" - 45 min estimated                                 │ │
│  │  [Continue Learning] [Change Path] [View Achievements]                          │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── COMMUNITY ACTIVITY ──────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  🏆 Your Stats                        📊 Community Pulse                       │ │
│  │  Events Attended: 8                   Hot Topics This Month:                   │ │
│  │  Presentations Viewed: 47             • AI/ML on Kubernetes (⬆ 45%)            │ │
│  │  Community Points: 230                • FinOps & Cost Control (⬆ 32%)          │ │
│  │  Rank: Active Member                  • Platform Engineering (New!)            │ │
│  │                                                                                  │ │
│  │  [View Profile] [Leaderboard]         [Join Discussion →]                       │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Event Registration**: View upcoming registered events with personalized schedules
- **Personalized Recommendations**: AI-curated content based on interests and activity
- **Content Library**: Saved presentations with download management
- **Learning Path Tracking**: Visual progress through curated learning sequences
- **Community Stats**: Gamification with points, ranks, and achievements
- **Quick Actions**: Calendar export, directions, email tickets, social discovery

## Functional Requirements Met

- **FR13**: Complete personalization with user-specific content
- **Event Management**: Registration tracking and schedule management
- **Content Library**: Personal collection of saved materials
- **Learning Paths**: Progress tracking and recommendations
- **Gamification**: Community points, ranks, and engagement tracking
- **Social Features**: Who's attending, community pulse, discussions

## User Interactions

1. **View Upcoming Events**: See registered events with personal schedule
2. **Manage Content Library**: Access saved presentations and downloads
3. **Continue Learning**: Progress through learning paths with recommendations
4. **Explore Recommendations**: Discover new content based on interests
5. **Track Progress**: View stats, achievements, and community rank
6. **Quick Actions**: Calendar sync, directions, tickets, social features

## Technical Notes

- Real-time dashboard updates via WebSocket
- ML-based personalized recommendations
- Progress tracking with gamification logic
- Calendar integration (iCal, Google Calendar)
- CDN delivery for saved content
- Community statistics aggregation
- Achievement badge system

---

## API Requirements

APIs needed to load and display data for this screen:

### Initial Page Load

1. **GET /api/v1/attendees/{attendeeId}/dashboard**
   - Retrieve complete dashboard data
   - Response includes: events, recommendations, library, learning paths, stats
   - Used for: Full dashboard initialization
   - Aggregated: Single call for complete dashboard state
   - Requires: User authentication

2. **GET /api/v1/attendees/{attendeeId}/events/registered**
   - Retrieve registered upcoming events
   - Query params: `upcoming=true`, `limit=5`
   - Response includes: event details, personal schedule, registration info
   - Used for: "Your Upcoming Events" section

3. **GET /api/v1/attendees/{attendeeId}/events/{eventId}/schedule**
   - Retrieve personal event schedule
   - Response includes: sessions attendee is registered for, times, locations
   - Used for: Personal schedule within event card

4. **GET /api/v1/attendees/{attendeeId}/recommendations**
   - Retrieve personalized content recommendations
   - Query params: `limit=5`, `includeMatchScore=true`
   - Response includes: recommended content with match scores, reasons
   - Used for: "Recommended for You" section
   - ML: Collaborative filtering + interest matching

5. **GET /api/v1/attendees/{attendeeId}/library**
   - Retrieve saved content library
   - Query params: `limit=10`, `sortBy=recent`
   - Response includes: saved presentations, download status, metadata
   - Used for: "Your Content Library" section

6. **GET /api/v1/attendees/{attendeeId}/learning-paths/active**
   - Retrieve active learning paths with progress
   - Response includes: path details, completion percentage, next content, achievements
   - Used for: "Your Learning Path" section

7. **GET /api/v1/attendees/{attendeeId}/stats**
   - Retrieve community statistics and gamification data
   - Response includes: events attended, presentations viewed, points, rank, badges
   - Used for: "Community Activity" section

8. **GET /api/v1/community/trending**
   - Retrieve trending topics and community pulse
   - Query params: `period=month`
   - Response includes: trending topics with growth percentages
   - Used for: "Community Pulse" in community activity

---

## Action APIs

APIs called by user interactions and actions:

### Event Management

1. **GET /api/v1/events/{eventId}/details**
   - Triggered by: Clicking event card
   - Response: Full event details, agenda, venue info
   - Opens: Event details page

2. **GET /api/v1/events/{eventId}/calendar/export**
   - Triggered by: [📅 Add to Calendar] button
   - Query params: `format=ical|google|outlook`, `attendeeId`
   - Response: iCal file or calendar service redirect
   - Downloads: .ics file with personal schedule

3. **POST /api/v1/attendees/{attendeeId}/events/{eventId}/ticket-email**
   - Triggered by: [📧 Email Ticket] button
   - Response: Ticket email sent confirmation
   - Side effect: Sends ticket to attendee's email

4. **GET /api/v1/events/{eventId}/venue/directions**
   - Triggered by: [📍 Get Directions] button
   - Response: Venue address, map link, directions URL
   - Opens: Google Maps or directions modal

5. **GET /api/v1/events/{eventId}/attendees**
   - Triggered by: [👥 Who's Attending] button
   - Query params: `publicProfiles=true`, `limit=50`
   - Response: List of attendees (public profiles only)
   - Opens: Attendee list modal with networking features

6. **GET /api/v1/events/upcoming**
   - Triggered by: [Browse Upcoming Events →] link
   - Response: List of upcoming public events
   - Navigation: Browse events page

### Recommendations & Discovery

7. **GET /api/v1/content/{contentId}**
   - Triggered by: [View] button on recommendation
   - Response: Full content details, viewer URL
   - Opens: Content viewer page

8. **POST /api/v1/attendees/{attendeeId}/library/save**
   - Triggered by: [Save] button on recommendation
   - Payload: `{ contentId }`
   - Response: Content saved to library
   - Feedback: "Saved to library" toast
   - Updates: Library count increments

9. **GET /api/v1/attendees/{attendeeId}/recommendations/more**
   - Triggered by: [More Recommendations →] link
   - Query params: `offset=5`, `limit=20`
   - Response: Additional personalized recommendations
   - Opens: Full recommendations page

### Content Library Management

10. **GET /api/v1/attendees/{attendeeId}/library/all**
    - Triggered by: [View All] button in library section
    - Response: Complete content library with filters
    - Opens: Full library management page

11. **GET /api/v1/content/{contentId}/download**
    - Triggered by: Clicking downloaded content item
    - Response: Presigned S3 URL or file stream
    - Downloads: PDF file

12. **DELETE /api/v1/attendees/{attendeeId}/library/{contentId}**
    - Triggered by: Remove from library action
    - Response: Content removed from library
    - Feedback: "Removed from library" toast
    - Updates: Library list refreshes

13. **GET /api/v1/attendees/{attendeeId}/downloads**
    - Triggered by: [Manage Downloads] button
    - Response: List of all downloaded files with metadata
    - Opens: Download management modal

### Learning Paths

14. **GET /api/v1/learning-paths/{pathId}/next**
    - Triggered by: [Continue Learning] button
    - Response: Next content in learning path
    - Opens: Content viewer with learning path context

15. **POST /api/v1/attendees/{attendeeId}/learning-paths/{pathId}/progress**
    - Triggered by: Completing content in learning path
    - Payload: `{ contentId, completed: true, timeSpent }`
    - Response: Progress updated, achievements unlocked
    - Updates: Progress bar, completion percentage

16. **GET /api/v1/learning-paths/available**
    - Triggered by: [Change Path] button
    - Response: Available learning paths with recommendations
    - Opens: Learning path selection modal

17. **GET /api/v1/attendees/{attendeeId}/achievements**
    - Triggered by: [View Achievements] button
    - Response: Earned badges, certificates, milestones
    - Opens: Achievements gallery modal

### Community & Social

18. **GET /api/v1/attendees/{attendeeId}/profile**
    - Triggered by: [View Profile] button
    - Response: Full attendee profile with activity history
    - Opens: Attendee profile page

19. **GET /api/v1/community/leaderboard**
    - Triggered by: [Leaderboard] button
    - Query params: `period=month|all-time`, `limit=100`
    - Response: Community leaderboard with ranks and points
    - Opens: Leaderboard page

20. **GET /api/v1/community/discussions**
    - Triggered by: [Join Discussion →] link
    - Query params: `topic=trending`
    - Response: Community discussions list
    - Opens: Community forum or discussion page

21. **POST /api/v1/community/activity**
    - Triggered by: Various user actions (auto-tracked)
    - Payload: `{ action: "view|download|attend|complete", points }`
    - Response: Activity logged, points awarded
    - Background: Async, doesn't block UI

### Profile & Settings

22. **GET /api/v1/attendees/{attendeeId}/settings**
    - Triggered by: User dropdown menu → Settings
    - Response: User preferences and settings
    - Opens: Settings page

23. **POST /api/v1/auth/logout**
    - Triggered by: [Logout] button
    - Response: Session terminated
    - Navigation: Redirect to homepage

---

## Navigation Map

Screen transitions triggered by actions and events:

### Primary Navigation

1. **BATbern Logo** (top-left)
   - **Target**: Main homepage
   - **Type**: Full page navigation
   - **Context**: Return to public homepage

2. **John Smith ▼** (user dropdown)
   - **Target**: Dropdown menu
   - **Type**: Dropdown overlay
   - **Options**:
     - My Profile → Attendee profile page
     - Settings → User settings
     - Help → Help center
     - Logout → Logout action

3. **[Logout]**
   - **Action**: POST logout
   - **Target**: Homepage (logged out)
   - **Type**: Full page redirect
   - **Feedback**: "You've been logged out" message

### Event Management Navigation

4. **Event Card Click**
   - **Target**: Event details page
   - **Type**: Full page navigation
   - **Content**: Complete event info, agenda, venue, speakers

5. **[📅 Add to Calendar]**
   - **Action**: Downloads calendar file
   - **No Navigation**: Remains on dashboard
   - **Options**: iCal, Google Calendar, Outlook
   - **Feedback**: "Added to calendar" toast

6. **[📧 Email Ticket]**
   - **Action**: Sends ticket email
   - **No Navigation**: Remains on dashboard
   - **Feedback**: "Ticket sent to john@example.com" toast

7. **[📍 Get Directions]**
   - **Target**: Google Maps or directions modal
   - **Type**: External link (new tab) or modal
   - **Content**: Venue location, directions, parking info

8. **[👥 Who's Attending]**
   - **Target**: Attendee list modal
   - **Type**: Modal overlay
   - **Content**: Public attendee profiles, networking features
   - **Actions**: [Connect], [Send Message], [View Profile]

9. **[Browse Upcoming Events →]**
   - **Target**: Event listing page
   - **Type**: Full page navigation
   - **Content**: All upcoming public events with registration

### Recommendations Navigation

10. **Recommendation Card [View]**
    - **Target**: Content viewer page
    - **Type**: Full page navigation
    - **Content**: PDF viewer, video player, or article reader

11. **Recommendation Card [Save]**
    - **Action**: Saves to library
    - **No Navigation**: Remains on dashboard
    - **Feedback**: "Saved to library" toast
    - **Updates**: Library count increments

12. **[More Recommendations →]**
    - **Target**: Full recommendations page
    - **Type**: Full page navigation
    - **Content**: Extended personalized recommendations with filtering

### Content Library Navigation

13. **Library Item Click**
    - **Target**: Content viewer
    - **Type**: Full page or modal
    - **Content**: Saved presentation with annotations

14. **[View All]** (library)
    - **Target**: Full library management page
    - **Type**: Full page navigation
    - **Content**: Complete library with search, filters, collections

15. **[Manage Downloads]**
    - **Target**: Download management modal
    - **Type**: Modal overlay
    - **Content**: All downloaded files with delete options
    - **Actions**: [Delete], [Re-download], [View]

### Learning Path Navigation

16. **[Continue Learning]**
    - **Target**: Next content in learning path
    - **Type**: Full page navigation
    - **Content**: Content viewer with learning path controls
    - **Context**: Progress tracking enabled

17. **Learning Path Progress Bar Click**
    - **Target**: Learning path details modal
    - **Type**: Modal overlay
    - **Content**: Full path outline, progress visualization
    - **Actions**: [Resume], [View Certificate], [Share Progress]

18. **[Change Path]**
    - **Target**: Learning path selection modal
    - **Type**: Modal overlay
    - **Content**: Available paths with recommendations
    - **Submit**: Switches active learning path

19. **[View Achievements]**
    - **Target**: Achievements gallery modal
    - **Type**: Modal overlay
    - **Content**: Earned badges, certificates, milestones
    - **Social**: Share achievements on LinkedIn, Twitter

### Community Navigation

20. **[View Profile]**
    - **Target**: Attendee profile page
    - **Type**: Full page navigation
    - **Content**: Public profile with activity, badges, stats

21. **[Leaderboard]**
    - **Target**: Community leaderboard page
    - **Type**: Full page navigation
    - **Content**: Rankings, top contributors, filtering by period

22. **[Join Discussion →]**
    - **Target**: Community forum/discussion page
    - **Type**: Full page navigation
    - **Content**: Trending discussions, Q&A, forums

23. **Trending Topic Click** (e.g., "AI/ML on Kubernetes")
    - **Target**: Content discovery page with topic filter
    - **Type**: Full page navigation
    - **Content**: Search results filtered to trending topic

### Event-Driven Navigation

24. **On New Recommendation Available**
    - **Notification**: Dashboard notification or email
    - **No Navigation**: Stays on dashboard
    - **Updates**: Recommendations section refreshes
    - **Badge**: "NEW" badge on recommendation card

25. **On Learning Path Milestone Reached**
    - **Notification**: Achievement unlock modal
    - **Type**: Celebration modal overlay
    - **Content**: Badge earned, progress visualization
    - **Actions**: [Share], [Continue], [View Certificate]

26. **On Event Approaching** (< 7 days)
    - **Highlight**: Event card highlighted with countdown
    - **Notification**: Email or push notification
    - **Action**: [Prepare] button for checklist

27. **On Content Added to Library**
    - **No Navigation**: Stays on dashboard
    - **Updates**: Library section refreshes
    - **Feedback**: "Added to library" toast with undo option

28. **On Points/Rank Update**
    - **No Navigation**: Stays on dashboard
    - **Updates**: Stats section refreshes
    - **Feedback**: Subtle animation on points/rank change

### Quick Actions

29. **Personal Schedule Session Click**
    - **Target**: Session details modal
    - **Type**: Modal overlay
    - **Content**: Session info, speaker bio, location
    - **Actions**: [Add to Calendar], [Set Reminder], [View Materials]

30. **Downloaded Content Click**
    - **Action**: Opens downloaded file
    - **No Navigation**: Opens in PDF viewer or downloads
    - **Context**: File already available locally or in cache

### Error States

31. **On Event Registration Expired**
    - **Feedback**: "Registration closed" message on event card
    - **Action**: Removed from upcoming events or marked as closed

32. **On Content Unavailable**
    - **Feedback**: "Content no longer available" in library
    - **Action**: [Remove from Library] button

33. **On Network Error**
    - **No Navigation**: Stays on dashboard
    - **Feedback**: "Unable to load updates" banner
    - **Actions**: [Retry], [Refresh Page]

### Mobile-Specific

34. **Mobile Dashboard Layout**
    - **Layout**: Single column, stacked sections
    - **Priority**: Events first, then recommendations
    - **Collapsed**: Community stats collapsed by default

35. **Mobile Quick Actions**
    - **Swipe**: Swipe left on event card for quick actions
    - **Long Press**: Long press for context menu

---
