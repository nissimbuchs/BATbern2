# Story: Community Features & Engagement - Wireframe

**Story**: Epic 7, Story 4
**Screen**: Community Features & Engagement
**User Role**: Attendee
**Related FR**: FR13 (Community)

---

## 6. Community Features & Engagement

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                         BATbern Community Hub                                 │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── CONTENT RATINGS & REVIEWS ──────────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  Recent Community Activity                                                     │  │
│  │                                                                                 │  │
│  │  ┌─────────────────────────────────────────────────────────────────────────┐  │  │
│  │  │ ⭐⭐⭐⭐⭐ "Excellent deep dive into K8s security"                         │  │  │
│  │  │ John S. reviewed "Kubernetes Security Best Practices"                   │  │  │
│  │  │ "The real-world examples and failure stories made this incredibly       │  │  │
│  │  │  valuable. Implementing these saved us from a major incident."          │  │  │
│  │  │ 👍 Helpful (42)  💬 Comments (8)                         2 hours ago    │  │  │
│  │  └─────────────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                                 │  │
│  │  ┌─────────────────────────────────────────────────────────────────────────┐  │  │
│  │  │ ⭐⭐⭐⭐ "Great intro but wanted more advanced content"                     │  │  │
│  │  │ Maria T. reviewed "Docker Fundamentals"                                  │  │  │
│  │  │ "Perfect for beginners. Would love a follow-up on multi-stage builds"   │  │  │
│  │  │ 👍 Helpful (18)  💬 Comments (3)                         Yesterday       │  │  │
│  │  └─────────────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                                 │  │
│  │  [Write Review] [View All Reviews] [Top Rated Content]                        │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── DISCUSSION FORUMS ────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Active Discussions                                     [Start New Discussion]  │ │
│  │                                                                                  │ │
│  │  🔥 "Who's implementing Platform Engineering?"          42 replies • 2h ago     │ │
│  │     Latest: "We're building an IDP with Backstage..."                          │ │
│  │     [Join Discussion]                                                          │ │
│  │                                                                                  │ │
│  │  💬 "Best practices for FinOps in Switzerland?"        28 replies • 5h ago     │ │
│  │     Latest: "Swiss data residency requirements..."                             │ │
│  │     [Join Discussion]                                                          │ │
│  │                                                                                  │ │
│  │  📚 "Study group for CKA certification"                15 members • Active      │ │
│  │     Next meetup: May 1, online                                                 │ │
│  │     [Join Group]                                                               │ │
│  │                                                                                  │ │
│  │  [Browse All Topics] [My Discussions] [Trending]                               │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── SOCIAL SHARING ──────────────┬─── LEARNING PATHS ──────────────────────────┐  │
│  │                                  │                                             │  │
│  │  Share Your Learning             │  Community-Curated Paths                   │  │
│  │                                  │                                             │  │
│  │  "Just completed the Kubernetes  │  🎯 Zero to K8s Hero (6 months)            │  │
│  │   security track! 🎉"            │     Created by: Thomas W.                  │  │
│  │   - Posted by John S.            │     Followers: 234                         │  │
│  │                                  │     [Start Path]                            │  │
│  │  [🔗 LinkedIn] [🐦 Twitter]      │                                             │  │
│  │                                  │  🔒 Security First Developer                │  │
│  │  Your Share Stats:               │     Created by: Sara K.                    │  │
│  │  • Content shared: 12 times      │     Followers: 189                         │  │
│  │  • Reached: ~1,200 people        │     [Start Path]                            │  │
│  │  • Engagement rate: 8.5%         │                                             │  │
│  └──────────────────────────────────┴─────────────────────────────────────────────┘  │
│                                                                                       │
│  ┌─── ACHIEVEMENTS & GAMIFICATION ─────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Your Achievements                           Community Leaderboard              │ │
│  │                                                                                  │ │
│  │  🏆 Early Bird (Registered 1 month early)    1. Thomas W. - 2,450 pts         │ │
│  │  📚 Knowledge Seeker (10+ presentations)     2. Sara K. - 2,230 pts           │ │
│  │  🤝 Community Helper (5+ helpful reviews)    3. Anna L. - 1,890 pts           │ │
│  │  🎯 Learning Path Complete (1 path)          ...                               │ │
│  │  🔓 Next: Super Learner (25+ presentations) 47. You - 230 pts                  │ │
│  │                                                                                  │ │
│  │  [View All Badges] [Share Achievements]      [View Full Leaderboard]           │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## API Requirements

### Initial Page Load APIs

When the Community Hub screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/community/reviews/recent**
   - Query params: limit (10), sortBy (recent)
   - Returns: Recent content reviews with ratings, reviewer info, review text, helpful counts, comment counts, timestamps
   - Used for: Populate recent community activity section with reviews

2. **GET /api/v1/community/discussions/active**
   - Query params: limit (5), sortBy (activity)
   - Returns: Active discussion threads with titles, reply counts, latest reply preview, timestamps, trending indicators
   - Used for: Display active discussions section

3. **GET /api/v1/community/study-groups**
   - Query params: status (active), limit (5)
   - Returns: Active study groups with names, member counts, next meetup info, topics
   - Used for: Show study groups in discussions section

4. **GET /api/v1/attendees/{userId}/sharing/stats**
   - Returns: User's social sharing statistics (shares count, reach, engagement rate)
   - Used for: Display share stats in social sharing section

5. **GET /api/v1/community/learning-paths/featured**
   - Query params: limit (5)
   - Returns: Community-curated learning paths with creators, follower counts, content counts, descriptions
   - Used for: Populate learning paths section

6. **GET /api/v1/attendees/{userId}/achievements**
   - Returns: User's earned achievements/badges with unlock dates, progress to next badges
   - Used for: Display achievements section

7. **GET /api/v1/community/leaderboard**
   - Query params: limit (10), period (all-time)
   - Returns: Top community members with points, rankings, achievement highlights
   - Used for: Populate community leaderboard

8. **GET /api/v1/attendees/{userId}/community-stats**
   - Returns: User's community ranking, points, level, next milestone
   - Used for: Display user's position in leaderboard

---

## Action APIs

### Reviews & Ratings

1. **POST /api/v1/content/{contentId}/reviews**
   - Payload: `{ rating: 1-5, reviewText, anonymous: boolean, tags: [] }`
   - Response: Review ID, post confirmation
   - Used for: Submit review for content

2. **PUT /api/v1/reviews/{reviewId}**
   - Payload: `{ rating, reviewText, tags }`
   - Response: Updated review
   - Used for: Edit existing review

3. **DELETE /api/v1/reviews/{reviewId}**
   - Response: Deletion confirmation
   - Used for: Delete review

4. **POST /api/v1/reviews/{reviewId}/helpful**
   - Response: Updated helpful count
   - Used for: Mark review as helpful

5. **GET /api/v1/reviews/{reviewId}/comments**
   - Returns: Comments on review with authors, timestamps
   - Used for: View comments on review

6. **POST /api/v1/reviews/{reviewId}/comments**
   - Payload: `{ commentText }`
   - Response: Comment ID, confirmation
   - Used for: Comment on review

7. **GET /api/v1/content/reviews**
   - Query params: sortBy (helpful, recent, rating), filters (rating, topic), limit, offset
   - Returns: All content reviews with filtering and sorting
   - Used for: Navigate to all reviews view

8. **GET /api/v1/content/top-rated**
   - Query params: period (week, month, all-time), category
   - Returns: Top-rated content based on reviews
   - Used for: View top rated content

### Discussions & Forums

9. **GET /api/v1/community/discussions**
   - Query params: category, sortBy (recent, trending, popular), limit, offset
   - Returns: All discussion threads
   - Used for: Browse all discussions

10. **GET /api/v1/community/discussions/{discussionId}**
    - Returns: Full discussion thread with all replies, authors, timestamps, reactions
    - Used for: View discussion details

11. **POST /api/v1/community/discussions/create**
    - Payload: `{ title, content, category, tags: [] }`
    - Response: Discussion ID, creation confirmation
    - Used for: Start new discussion

12. **POST /api/v1/community/discussions/{discussionId}/reply**
    - Payload: `{ content, replyToId (optional) }`
    - Response: Reply ID, confirmation
    - Used for: Reply to discussion

13. **POST /api/v1/community/discussions/{discussionId}/follow**
    - Response: Follow confirmation, notification settings
    - Used for: Follow discussion for updates

14. **GET /api/v1/community/discussions/trending**
    - Query params: period (today, week), limit
    - Returns: Trending discussions based on activity
    - Used for: View trending discussions

15. **GET /api/v1/attendees/{userId}/discussions**
    - Query params: filter (created, participated), limit, offset
    - Returns: User's discussions
    - Used for: View user's discussion activity

### Study Groups

16. **POST /api/v1/community/study-groups/{groupId}/join**
    - Response: Membership confirmation, group access details
    - Used for: Join study group

17. **GET /api/v1/community/study-groups/{groupId}/details**
    - Returns: Full group details, members, schedule, resources, discussion board
    - Used for: View study group details

18. **POST /api/v1/community/study-groups/create**
    - Payload: `{ name, description, topic, schedule, maxMembers }`
    - Response: Group ID, creation confirmation
    - Used for: Create new study group

19. **GET /api/v1/attendees/{userId}/study-groups**
    - Returns: User's study groups with membership status, upcoming meetings
    - Used for: View user's study groups

### Social Sharing

20. **POST /api/v1/attendees/{userId}/achievements/share**
    - Payload: `{ achievementId, platforms: ["linkedin", "twitter"], message }`
    - Response: Share links, tracking URLs
    - Used for: Share achievement on social media

21. **POST /api/v1/content/{contentId}/share**
    - Payload: `{ platform: "linkedin|twitter|facebook", customMessage }`
    - Response: Share link, tracking URL
    - Used for: Share content on social media

22. **GET /api/v1/attendees/{userId}/sharing/history**
    - Query params: limit, offset
    - Returns: Share history with platforms, reach metrics, engagement
    - Used for: View sharing history and analytics

### Learning Paths

23. **POST /api/v1/learning-paths/{pathId}/start**
    - Response: Path enrollment confirmation, first content item
    - Used for: Start following learning path

24. **GET /api/v1/learning-paths/{pathId}/details**
    - Returns: Full path details, content items, creator info, progress tracking, follower count
    - Used for: View learning path details

25. **POST /api/v1/learning-paths/create**
    - Payload: `{ title, description, contentItems: [], isPublic: boolean, tags: [] }`
    - Response: Path ID, creation confirmation
    - Used for: Create custom learning path

26. **PUT /api/v1/learning-paths/{pathId}/follow**
    - Response: Follow confirmation
    - Used for: Follow learning path

27. **GET /api/v1/attendees/{userId}/learning-paths**
    - Query params: filter (enrolled, created, completed)
    - Returns: User's learning paths with progress
    - Used for: View user's learning paths

### Achievements & Gamification

28. **GET /api/v1/attendees/{userId}/achievements/all**
    - Returns: All available achievements with earned status, requirements, progress
    - Used for: View all badges and achievements

29. **GET /api/v1/community/leaderboard/full**
    - Query params: period (week, month, all-time), category, limit, offset
    - Returns: Full leaderboard with rankings
    - Used for: View complete leaderboard

30. **GET /api/v1/attendees/{userId}/points/history**
    - Query params: limit, offset
    - Returns: Points earning history with activities, dates, point values
    - Used for: View points history and earning activities

31. **POST /api/v1/attendees/{userId}/achievements/{achievementId}/claim**
    - Response: Achievement claim confirmation (if auto-award failed)
    - Used for: Manually claim earned achievement

---

## Navigation Map

### Primary Navigation Actions

1. **← Back button** → Navigate back to previous screen (Personal Dashboard or Content Discovery)
   - Returns to last visited screen

2. **Review card click** → Navigate to `Content Details Screen` with review highlighted
   - Shows content with reviews section
   - Scrolls to selected review
   - Allows viewing full content

3. **Review author name click** → Navigate to `User Profile Screen`
   - View reviewer's profile
   - See their reviews and contributions
   - Community activity

4. **[👍 Helpful] button** → Marks review as helpful
   - Updates helpful count
   - Changes button state
   - No screen navigation

5. **[💬 Comments] link** → Expands comments section
   - Shows all comments on review
   - Reply interface
   - No screen navigation

6. **[Write Review] button** → Opens review form modal
   - Rating selection
   - Review text editor
   - Tag selection
   - No screen navigation after submit

7. **[View All Reviews] button** → Navigate to `All Reviews Screen`
   - Filterable review list
   - Sort options
   - Search functionality

8. **[Top Rated Content] button** → Navigate to `Top Rated Content Screen`
   - Content sorted by ratings
   - Filter by category/period
   - Trending indicators

9. **Discussion thread click** → Navigate to `Discussion Thread Screen`
   - Full thread with all replies
   - Reply interface
   - Follow/unfollow option

10. **[Join Discussion] button** → Navigate to `Discussion Thread Screen`
    - Opens at reply section
    - Ready to add reply

11. **[Start New Discussion] button** → Navigate to `New Discussion Screen`
    - Discussion creation form
    - Category selection
    - Rich text editor

12. **Study group item click** → Navigate to `Study Group Details Screen`
    - Group information
    - Member list
    - Schedule and resources
    - Join option

13. **[Join Group] button** → Joins study group
    - Confirmation modal
    - Access to group resources
    - Can navigate to group details

14. **[Browse All Topics] button** → Navigate to `Discussion Topics Browser`
    - Category organization
    - Topic filtering
    - Active discussions per topic

15. **[My Discussions] button** → Navigate to `My Discussions Screen`
    - User's created discussions
    - Participated discussions
    - Followed discussions

16. **[Trending] button** → Navigate to `Trending Discussions Screen`
    - Hot topics
    - Active today
    - Rising discussions

17. **Social share buttons ([🔗 LinkedIn] [🐦 Twitter])** → Opens share dialog
    - Pre-filled share text
    - Opens social platform
    - Tracks share for stats
    - No app navigation

18. **Learning path card click** → Navigate to `Learning Path Details Screen`
    - Full path curriculum
    - Content items list
    - Progress tracking
    - Creator information

19. **[Start Path] button** → Enrolls in learning path
    - Confirmation modal
    - First content item recommended
    - Progress tracking enabled
    - Can navigate to first content

20. **Achievement badge click** → Opens achievement details modal
    - Achievement description
    - Unlock date
    - Rarity statistics
    - Share option
    - No screen navigation

21. **[View All Badges] button** → Navigate to `All Achievements Screen`
    - All available achievements
    - Earned and locked badges
    - Progress toward locked badges
    - Achievement categories

22. **[Share Achievements] button** → Opens achievement share dialog
    - Select achievements to share
    - Choose platforms
    - Custom message
    - No screen navigation after share

23. **Leaderboard user click** → Navigate to `User Profile Screen`
    - View user's profile
    - Community contributions
    - Achievements

24. **[View Full Leaderboard] button** → Navigate to `Full Leaderboard Screen`
    - Complete rankings
    - Filter by period/category
    - Search for users

### Secondary Navigation (Data Interactions)

25. **Review rating stars click** → Filters reviews by rating
    - Shows reviews with that rating
    - Updates review list
    - No screen navigation

26. **Discussion reply count click** → Navigates to discussion thread
    - Scrolls to replies section
    - Ready to view replies

27. **Content shared count hover** → Shows share breakdown tooltip
    - Platforms used
    - Reach per platform
    - No navigation

28. **Next achievement progress bar click** → Opens achievement details
    - Shows requirements
    - Current progress
    - Tips for earning
    - No screen navigation

### Event-Driven Navigation

29. **New review on followed content** → Shows notification
    - Review preview
    - Links to review
    - No automatic navigation

30. **Review marked as helpful milestone** → Shows notification
    - Threshold reached (e.g., 10 helpful votes)
    - Points awarded
    - No automatic navigation

31. **Reply to discussion** → Shows notification
    - Reply preview
    - Links to discussion
    - No automatic navigation

32. **Study group meeting reminder** → Shows notification
    - Meeting details
    - Join link
    - Links to group details

33. **Achievement unlocked** → Shows celebration modal
    - Achievement animation
    - Badge display
    - Share option
    - Points awarded notification

34. **Leaderboard rank up** → Shows notification
    - New ranking
    - Points milestone
    - Celebration message
    - No automatic navigation

35. **Learning path milestone** → Shows progress notification
    - Percentage complete
    - Next content recommendation
    - No automatic navigation

36. **Popular discussion in your interests** → Shows recommendation notification
    - Discussion preview
    - Relevance indicator
    - Links to discussion

---
