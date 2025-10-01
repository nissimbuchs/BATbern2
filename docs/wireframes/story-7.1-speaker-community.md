# Story 7.1: Speaker Community & Networking - Wireframe

**Story**: Epic 7, Story 1 - Speaker Community & Networking
**Screen**: BATbern Speaker Community
**User Role**: Speaker
**Related FR**: FR5 (Speaker Management)

---

## 8. Speaker Community & Networking

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                        BATbern Speaker Community                    [Search]  │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── SPEAKER NETWORK ─────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Fellow Speakers at Spring Conference 2025                      [View All 8]    │ │
│  │                                                                                  │ │
│  │  ┌────────────────┐ ┌────────────────┐ ┌────────────────┐ ┌────────────────┐  │ │
│  │  │     Sara K.    │ │   Thomas W.    │ │    Anna L.     │ │    Marc B.     │  │ │
│  │  │   [Photo]      │ │   [Photo]      │ │   [Photo]      │ │   [Photo]      │  │ │
│  │  │                │ │                │ │                │ │                │  │ │
│  │  │ Docker Expert  │ │ Cloud Security │ │ AI/ML Engineer │ │ DevOps Lead    │  │ │
│  │  │ ⭐ 4.9 (15)    │ │ ⭐ 4.7 (8)     │ │ ⭐ 4.8 (6)     │ │ ⭐ New Speaker │  │ │
│  │  │                │ │                │ │                │ │                │  │ │
│  │  │ [Connect]      │ │ [Connect]      │ │ [Connected ✓]  │ │ [Connect]      │  │ │
│  │  └────────────────┘ └────────────────┘ └────────────────┘ └────────────────┘  │ │
│  │                                                                                  │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── KNOWLEDGE SHARING ──────────────┬─── MENTORSHIP ──────────────────────────────┐│
│  │                                    │                                              ││
│  │  Recent Discussions                │  Mentor Matching                             ││
│  │                                    │                                              ││
│  │  "Tips for first-time speakers?"   │  Find a Mentor:                             ││
│  │  By Marc B. • 12 replies • 2h ago  │  ○ Presentation skills                      ││
│  │  [View Thread]                     │  ● Technical topics (K8s, Cloud)            ││
│  │                                    │  ○ Career development                       ││
│  │  "Demo disaster recovery plans"    │                                              ││
│  │  By Sara K. • 8 replies • 1d ago   │  Available Mentors:                         ││
│  │  [View Thread]                     │  • Peter M. (You) - 2 mentees               ││
│  │                                    │  • Dr. Weber - 1 mentee                     ││
│  │  "Handling difficult questions"    │  • Lisa Chen - 3 mentees                    ││
│  │  By You • 15 replies • 3d ago      │                                              ││
│  │  [View Thread]                     │  [Become a Mentor] [Find Mentor]            ││
│  │                                    │                                              ││
│  │  [Start Discussion]                │                                              ││
│  └────────────────────────────────────┴──────────────────────────────────────────────┘│
│                                                                                       │
│  ┌─── RESOURCES & TOOLS ────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Speaker Toolkit                          Shared by Community                   │ │
│  │                                                                                  │ │
│  │  📚 Presentation Templates                📊 "K8s Security Checklist"          │ │
│  │     BATbern branded, ready to use            By Thomas W., last week           │ │
│  │     [Download Pack]                          [View] [Download]                 │ │
│  │                                                                                  │ │
│  │  🎯 Demo Best Practices Guide             💡 "Live Coding Tips & Tricks"        │ │
│  │     Avoid common pitfalls                    By Sara K., 2 weeks ago          │ │
│  │     [Read Guide]                             [Read] [Bookmark]                 │ │
│  │                                                                                  │ │
│  │  🎤 Public Speaking Course                📝 "Abstract Writing Workshop"        │ │
│  │     Free for BATbern speakers                Recording from last month         │ │
│  │     [Enroll Now]                             [Watch] [Materials]              │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── YOUR SPEAKING STATS ──────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Performance Dashboard                    Growth Tracking                       │ │
│  │                                                                                  │ │
│  │  Latest Talk Rating: 4.8/5 ⭐            Topics Covered: 8                     │ │
│  │  Total Attendees: 1,247                  Events Spoken: 12                     │ │
│  │  Slide Downloads: 892                    Years Active: 4                        │ │
│  │  Questions Answered: 67                  Ranking: Top 15%                       │ │
│  │                                                                                  │ │
│  │  Feedback Highlights:                    Next Goals:                            │ │
│  │  "Clear explanations" (18x)              • Reach 1500 attendees ☐              │ │
│  │  "Great demos" (12x)                     • Speak at 15 events ☐                │ │
│  │  "Engaging presenter" (10x)              • Mentor 3 speakers ☐                 │ │
│  │                                                                                  │ │
│  │  [View Detailed Analytics] [Download Speaker Report]                            │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## API Requirements

### Initial Page Load APIs

When the Speaker Community screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/speakers/{speakerId}/network/co-speakers**
   - Query params: eventId (optional), limit (4)
   - Returns: List of fellow speakers from recent events with profiles, ratings, connection status
   - Used for: Populate speaker network section with co-speakers

2. **GET /api/v1/community/discussions/recent**
   - Query params: limit (5), sortBy (recent)
   - Returns: Recent discussion threads with titles, authors, reply counts, timestamps
   - Used for: Display recent discussions in knowledge sharing section

3. **GET /api/v1/speakers/{speakerId}/mentorship/status**
   - Returns: Speaker's mentorship status (mentor/mentee/neither), current mentees/mentor, mentorship preferences
   - Used for: Display mentorship information and available mentors

4. **GET /api/v1/mentors/available**
   - Query params: expertiseArea (optional), limit (5)
   - Returns: List of available mentors with expertise, current mentee counts, availability
   - Used for: Show available mentors list

5. **GET /api/v1/resources/toolkit**
   - Returns: Official BATbern speaker resources (templates, guides, courses)
   - Used for: Populate speaker toolkit section

6. **GET /api/v1/resources/community-shared**
   - Query params: limit (5), sortBy (recent)
   - Returns: Community-shared resources with authors, timestamps, download counts
   - Used for: Display shared resources from community

7. **GET /api/v1/speakers/{speakerId}/statistics**
   - Returns: Comprehensive speaker statistics (ratings, attendees, downloads, questions, topics covered, events, ranking, feedback highlights)
   - Used for: Populate performance dashboard

8. **GET /api/v1/speakers/{speakerId}/goals**
   - Returns: Speaker's goals with progress tracking, completion status
   - Used for: Display next goals section

---

## Action APIs

### Networking & Connections

1. **POST /api/v1/speakers/{speakerId}/connections/send**
   - Payload: `{ targetSpeakerId, message }`
   - Response: Connection request ID, pending status
   - Used for: Send connection request to another speaker

2. **GET /api/v1/speakers/{speakerId}/connections**
   - Query params: status (connected, pending, all), limit, offset
   - Returns: List of connections with status, connection dates, shared events
   - Used for: View all speaker connections

3. **PUT /api/v1/speakers/{speakerId}/connections/{connectionId}/respond**
   - Payload: `{ response: "accept|decline" }`
   - Response: Updated connection status
   - Used for: Accept or decline connection request

4. **GET /api/v1/speakers/{speakerId}/network/suggestions**
   - Query params: limit (10)
   - Returns: Suggested speakers to connect with based on shared topics, events, interests
   - Used for: Get connection suggestions

5. **GET /api/v1/speakers/search**
   - Query params: query, expertise, location, limit, offset
   - Returns: Search results of speakers matching criteria
   - Used for: Search for speakers in community

### Knowledge Sharing & Discussions

6. **GET /api/v1/community/discussions**
   - Query params: category, sortBy (recent, popular), limit, offset
   - Returns: List of discussion threads with metadata
   - Used for: Browse all discussions

7. **GET /api/v1/community/discussions/{discussionId}**
   - Returns: Full discussion thread with all replies, timestamps, authors
   - Used for: View discussion thread details

8. **POST /api/v1/community/discussions/create**
   - Payload: `{ title, content, category, tags: [] }`
   - Response: Discussion ID, creation confirmation
   - Used for: Start new discussion thread

9. **POST /api/v1/community/discussions/{discussionId}/reply**
   - Payload: `{ content, replyToId (optional) }`
   - Response: Reply ID, confirmation
   - Used for: Reply to discussion thread

10. **POST /api/v1/community/discussions/{discussionId}/react**
    - Payload: `{ reactionType: "like|helpful|insightful" }`
    - Response: Updated reaction count
    - Used for: React to discussion or reply

### Mentorship

11. **POST /api/v1/speakers/{speakerId}/mentorship/become-mentor**
    - Payload: `{ expertiseAreas: [], maxMentees: number, availability, bio }`
    - Response: Mentor profile ID, approval status
    - Used for: Apply to become a mentor

12. **POST /api/v1/speakers/{speakerId}/mentorship/request-mentor**
    - Payload: `{ mentorId, goalDescription, preferredSchedule }`
    - Response: Mentorship request ID, pending status
    - Used for: Request mentorship from specific mentor

13. **GET /api/v1/speakers/{speakerId}/mentorship/matches**
    - Query params: expertiseArea, preferences
    - Returns: Matched mentors based on speaker's needs and preferences
    - Used for: Find mentor matches

14. **PUT /api/v1/speakers/{speakerId}/mentorship/requests/{requestId}/respond**
    - Payload: `{ response: "accept|decline", message }`
    - Response: Updated mentorship status
    - Used for: Accept or decline mentorship request (for mentors)

15. **GET /api/v1/speakers/{speakerId}/mentorship/sessions**
    - Returns: Scheduled mentorship sessions, past sessions, notes
    - Used for: View mentorship sessions and history

### Resources & Tools

16. **GET /api/v1/resources/toolkit/{resourceId}/download**
    - Returns: Download URL, expiration timestamp
    - Used for: Download official toolkit resource

17. **POST /api/v1/resources/community/upload**
    - Payload: File upload (multipart/form-data) + metadata `{ title, description, category, tags: [] }`
    - Response: Resource ID, upload confirmation
    - Used for: Share resource with community

18. **GET /api/v1/resources/community/{resourceId}**
    - Returns: Resource details, download URL, author info, statistics
    - Used for: View and download community resource

19. **POST /api/v1/resources/community/{resourceId}/bookmark**
    - Response: Bookmark confirmation
    - Used for: Bookmark community resource

20. **DELETE /api/v1/resources/community/{resourceId}/bookmark**
    - Response: Unbookmark confirmation
    - Used for: Remove bookmark from resource

21. **GET /api/v1/speakers/{speakerId}/resources/bookmarked**
    - Returns: List of bookmarked resources
    - Used for: View bookmarked resources

### Speaker Statistics & Analytics

22. **GET /api/v1/speakers/{speakerId}/analytics/detailed**
    - Query params: dateRange, metrics
    - Returns: Detailed analytics data with trends, comparisons, insights
    - Used for: Navigate to detailed analytics view

23. **POST /api/v1/speakers/{speakerId}/analytics/export**
    - Payload: `{ format: "pdf|excel", dateRange, includeGraphs: boolean }`
    - Response: Export task ID, estimated completion time
    - Used for: Generate speaker report

24. **GET /api/v1/speakers/{speakerId}/analytics/export/{taskId}/download**
    - Returns: Download URL, expiration timestamp
    - Used for: Download generated speaker report

25. **GET /api/v1/speakers/{speakerId}/feedback/analysis**
    - Returns: Feedback analysis with common themes, sentiment analysis, word clouds
    - Used for: View feedback highlights and analysis

### Goals Management

26. **POST /api/v1/speakers/{speakerId}/goals**
    - Payload: `{ title, targetValue, currentValue, deadline }`
    - Response: Goal ID, creation confirmation
    - Used for: Create new speaker goal

27. **PUT /api/v1/speakers/{speakerId}/goals/{goalId}**
    - Payload: `{ currentValue, completed: boolean }`
    - Response: Updated goal, progress percentage
    - Used for: Update goal progress

28. **DELETE /api/v1/speakers/{speakerId}/goals/{goalId}**
    - Response: Deletion confirmation
    - Used for: Delete goal

---

## Navigation Map

### Primary Navigation Actions

1. **← Back button** → Navigate back to `Speaker Dashboard`
   - Returns to main speaker dashboard

2. **[Search] button** → Opens speaker search interface
   - Search by name, expertise, location
   - Filter options
   - Can navigate to search results screen

3. **[View All 8] button (Speaker Network)** → Navigate to `Full Speaker Network Screen`
   - All co-speakers from recent events
   - Connection management
   - Filter and sort options

4. **Speaker card click** → Navigate to `Speaker Profile Screen`
   - View full speaker profile
   - Past presentations
   - Expertise and ratings
   - Connection status

5. **[Connect] button** → Triggers connection request
   - Opens message modal (optional)
   - Sends connection request
   - Updates button to "Pending"
   - No screen navigation

6. **[Connected ✓] button** → Opens connection options menu
   - View shared events
   - Send message
   - Disconnect option
   - No screen navigation

7. **Discussion thread item click** → Navigate to `Discussion Thread Screen`
   - Full thread with all replies
   - Reply interface
   - Thread participants

8. **[View Thread] button** → Navigate to `Discussion Thread Screen`
   - Same as thread item click

9. **[Start Discussion] button** → Navigate to `New Discussion Screen`
   - Discussion creation form
   - Category selection
   - Tag input
   - Rich text editor

10. **Mentorship radio button** → Updates mentor search filter
    - Filters available mentors
    - Updates mentor list
    - No screen navigation

11. **Available Mentor click** → Navigate to `Mentor Profile Screen`
    - Mentor bio and expertise
    - Mentee testimonials
    - Request mentorship option

12. **[Become a Mentor] button** → Navigate to `Mentor Application Screen`
    - Mentor profile setup
    - Expertise specification
    - Availability settings

13. **[Find Mentor] button** → Navigate to `Mentor Matching Screen`
    - Mentor discovery interface
    - Expertise-based matching
    - Filter and search options

14. **Toolkit resource [Download Pack] button** → Triggers download
    - Downloads resource pack
    - No screen navigation

15. **Toolkit resource [Read Guide] button** → Navigate to `Resource Viewer Screen`
    - In-app document viewer
    - Download option

16. **Toolkit resource [Enroll Now] button** → Navigate to `Course Enrollment Screen`
    - Course details
    - Enrollment form
    - Course materials

17. **Community resource card click** → Navigate to `Resource Details Screen`
    - Full resource information
    - Author profile
    - Download/view options
    - Comments and ratings

18. **Community resource [View] button** → Opens resource viewer
    - In-app viewer or new tab
    - Can navigate to full screen viewer

19. **Community resource [Download] button** → Triggers download
    - Downloads resource file
    - No screen navigation

20. **Community resource [Bookmark] button** → Bookmarks resource
    - Updates bookmark status
    - Adds to bookmarks list
    - No screen navigation

21. **Statistics metric click** → Opens detailed breakdown modal
    - Metric trends over time
    - Comparison with benchmarks
    - No screen navigation

22. **Feedback highlight click** → Opens feedback details modal
    - Shows all feedback with that theme
    - Sentiment analysis
    - No screen navigation

23. **Goal checkbox** → Marks goal as complete
    - Completion celebration
    - Updates progress
    - No screen navigation

24. **[View Detailed Analytics] button** → Navigate to `Detailed Analytics Screen`
    - Comprehensive performance dashboard
    - Historical trends
    - Peer comparisons

25. **[Download Speaker Report] button** → Triggers report generation
    - Select report options
    - Generates PDF/Excel
    - Downloads when ready
    - No screen navigation

### Secondary Navigation (Data Interactions)

26. **Speaker rating click** → Opens rating details modal
    - Rating distribution
    - Recent feedback
    - Event-by-event breakdown
    - No screen navigation

27. **Discussion reply count click** → Jumps to replies section
    - Scrolls to replies in thread view
    - Highlights reply section
    - No screen navigation

28. **Resource download count hover** → Shows download statistics tooltip
    - Download trend
    - Popular with roles/topics
    - No navigation

### Event-Driven Navigation

29. **Connection request received** → Shows notification
    - Request preview
    - Accept/decline quick actions
    - Links to requester profile
    - No automatic navigation

30. **Connection request accepted** → Shows success notification
    - New connection confirmed
    - Suggests starting conversation
    - No automatic navigation

31. **New discussion reply** → Shows notification
    - Reply preview
    - Links to discussion thread
    - No automatic navigation

32. **Mentorship request received** → Shows notification (for mentors)
    - Request details
    - Accept/decline options
    - Links to mentee profile
    - No automatic navigation

33. **Mentorship accepted** → Shows confirmation notification
    - Next steps outlined
    - Session scheduling link
    - No automatic navigation

34. **New community resource** → Shows notification
    - Resource preview
    - Relevant to speaker's interests
    - Links to resource
    - No automatic navigation

35. **Goal milestone achieved** → Shows celebration notification
    - Milestone highlight
    - Achievement badge (if applicable)
    - Share option
    - No automatic navigation

36. **Speaker report ready** → Shows notification with download link
    - Download available
    - Preview option
    - No automatic navigation

37. **Mentioned in discussion** → Shows notification
    - Discussion context
    - Links to mention
    - Reply option
    - No automatic navigation

---
