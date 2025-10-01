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
│  │  │ Actions:                                                                  │  │  │
│  │  │ [📅 Add to Calendar]  [📍 Get Directions]  [👥 Who's Attending]         │  │  │
│  │  └──────────────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                                 │  │
│  │  No other events registered. [Browse Upcoming Events →]                        │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── RECOMMENDED FOR YOU ──────────────────────────────────────────────────────┐   │
│  │                                                                                 │   │
│  │  Based on your interests:                                                      │   │
│  │                                                                                 │   │
│  │  🎯 "Zero Trust in K8s"                                                        │   │
│  │     Thomas Weber • 2024                                                        │   │
│  │     95% match to your interests                                                │   │
│  │     [View]                                                                     │   │
│  │                                                                                 │   │
│  │  🔥 "GitOps Workflows"                                                         │   │
│  │     Marc Baum • 2024                                                           │   │
│  │     Trending in your company                                                   │   │
│  │     [View]                                                                     │   │
│  │                                                                                 │   │
│  │  [More Recommendations →]                                                      │   │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── COMMUNITY ACTIVITY ──────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  🏆 Your Stats                                                                  │ │
│  │  Events Attended: 8                                                             │ │
│  │  Presentations Viewed: 47                                                       │ │
│  │  Rank: Active Member                                                            │ │
│  │                                                                                  │ │
│  │  [View Profile] [Leaderboard]                                                   │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Event Registration**: View upcoming registered events
- **Personalized Recommendations**: AI-curated content based on interests and activity
- **Community Stats**: Gamification with ranks and achievements
- **Quick Actions**: Calendar export, directions, social discovery

## Functional Requirements Met

- **FR13**: Complete personalization with user-specific content
- **Event Management**: Registration tracking and schedule management
- **Gamification**: Community ranks and engagement tracking
- **Social Features**: Who's attending, discussions

## User Interactions

1. **View Upcoming Events**: See registered events
2. **View Recommendations**: Browse personalized content recommendations
3. **Track Progress**: View stats, achievements, and community rank
4. **Quick Actions**: Calendar sync, directions, social features

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

8. **GET /api/v1/attendees/{attendeeId}/recommendations/more**
   - Triggered by: [More Recommendations →] link
   - Query params: `offset=5`, `limit=20`
   - Response: Additional personalized recommendations
   - Opens: Full recommendations page

### Content Library Management

9. **GET /api/v1/attendees/{attendeeId}/library/all**
    - Triggered by: [View All] button in library section
    - Response: Complete content library with filters
    - Opens: Full library management page

10. **GET /api/v1/content/{contentId}/download**
    - Triggered by: Clicking downloaded content item
    - Response: Presigned S3 URL or file stream
    - Downloads: PDF file

11. **DELETE /api/v1/attendees/{attendeeId}/library/{contentId}**
    - Triggered by: Remove from library action
    - Response: Content removed from library
    - Feedback: "Removed from library" toast
    - Updates: Library list refreshes

12. **GET /api/v1/attendees/{attendeeId}/downloads**
    - Triggered by: [Manage Downloads] button
    - Response: List of all downloaded files with metadata
    - Opens: Download management modal

### Community & Social

13. **GET /api/v1/attendees/{attendeeId}/profile**
    - Triggered by: [View Profile] button
    - Response: Full attendee profile with activity history
    - Opens: Attendee profile page

14. **GET /api/v1/community/leaderboard**
    - Triggered by: [Leaderboard] button
    - Query params: `period=month|all-time`, `limit=100`
    - Response: Community leaderboard with ranks
    - Opens: Leaderboard page

15. **GET /api/v1/community/discussions**
    - Triggered by: [Join Discussion →] link
    - Query params: `topic=trending`
    - Response: Community discussions list
    - Opens: Community forum or discussion page

16. **POST /api/v1/community/activity**
    - Triggered by: Various user actions (auto-tracked)
    - Payload: `{ action: "view|download|attend|complete" }`
    - Response: Activity logged
    - Background: Async, doesn't block UI

### Profile & Settings

17. **GET /api/v1/attendees/{attendeeId}/settings**
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

11. **[More Recommendations →]**
    - **Target**: Full recommendations page
    - **Type**: Full page navigation
    - **Content**: Extended personalized recommendations with filtering

### Content Library Navigation

12. **Library Item Click**
    - **Target**: Content viewer
    - **Type**: Full page or modal
    - **Content**: Saved presentation with annotations

### Community Navigation

15. **[View Profile]**
    - **Target**: Attendee profile page
    - **Type**: Full page navigation
    - **Content**: Public profile with activity, badges, stats

### Event-Driven Navigation

17. **On New Recommendation Available**
    - **Notification**: Dashboard notification or email
    - **No Navigation**: Stays on dashboard
    - **Updates**: Recommendations section refreshes
    - **Badge**: "NEW" badge on recommendation card

18. **On Event Approaching** (< 7 days)
    - **Highlight**: Event card highlighted with countdown
    - **Notification**: Email or push notification
    - **Action**: [Prepare] button for checklist

19. **On Content Added to Library**
    - **No Navigation**: Stays on dashboard
    - **Updates**: Library section refreshes
    - **Feedback**: "Added to library" toast with undo option

20. **On Rank Update**
    - **No Navigation**: Stays on dashboard
    - **Updates**: Stats section refreshes
    - **Feedback**: Subtle animation on rank change

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
