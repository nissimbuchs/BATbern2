# Speaker Profile Detail View - Wireframe

**Story**: Epic 7, Story 7.1 - Speaker Community & Networking
**Screen**: Speaker Profile Detail View
**User Role**: Organizer, Speaker (self-view), Attendee, Public
**Related FR**: FR3 (Speaker Self-Service), FR10 (Speaker Portal), FR17 (Intelligent Speaker Matching)

---

## Visual Wireframe

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                Speaker Profile                    [✉ Contact] [★ Bookmark]   │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── SPEAKER PROFILE ──────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  ┌────────┐  Dr. Peter Muller                                [🔗 Share]         │ │
│  │  │        │  Principal Cloud Architect                                          │ │
│  │  │ Photo  │  TechCorp AG • Zurich, Switzerland                                  │ │
│  │  │        │  🔗 LinkedIn | 🐦 Twitter | 💻 GitHub | 🌐 Website                │ │
│  │  └────────┘                                                                      │ │
│  │             Member Since: March 2020 • 12 Talks • 4.7★ (89 ratings)            │ │
│  │                                                                                   │ │
│  │  ┌─── BIO ───────────────────────────────────────────────────────────────────┐  │ │
│  │  │ Cloud native enthusiast with 10+ years building scalable distributed     │  │ │
│  │  │ systems. Specializing in Kubernetes orchestration, DevOps automation,    │  │ │
│  │  │ and cloud architecture patterns. Active CNCF contributor and regular     │  │ │
│  │  │ conference speaker across Europe. Passionate about sharing knowledge     │  │ │
│  │  │ and mentoring the next generation of cloud engineers.                    │  │ │
│  │  └───────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  ┌─── EXPERTISE ─────────────────────────────────────────────────────────────┐  │ │
│  │  │ [Kubernetes] [Docker] [Cloud Architecture] [DevOps] [CI/CD]              │  │ │
│  │  │ [Microservices] [GitOps] [Security] [Cost Optimization]                  │  │ │
│  │  └───────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  Languages: German (Native), English (Fluent), French (Conversational)          │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── SPEAKING HISTORY ──────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  [All Events ▾] [Filter by Topic]                          Showing 12 of 12     │ │
│  │                                                                                   │ │
│  │  ┌───────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ 📅 Spring 2024 • Kubernetes at Scale: Production Best Practices          │  │ │
│  │  │    BATbern Spring Conference 2024                                         │  │ │
│  │  │    4.9★ (42 ratings) • 156 attendees • [📄 View Presentation]           │  │ │
│  │  │    "Excellent deep dive into K8s" - Top Comment                           │  │ │
│  │  └───────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  ┌───────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ 📅 Autumn 2023 • GitOps: The Future of Infrastructure as Code             │  │ │
│  │  │    BATbern Autumn Conference 2023                                         │  │ │
│  │  │    4.6★ (38 ratings) • 142 attendees • [📄 View Presentation]           │  │ │
│  │  │    "Practical and actionable" - Top Comment                               │  │ │
│  │  └───────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  ┌───────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ 📅 Spring 2023 • Cloud Cost Optimization: Strategies that Actually Work   │  │ │
│  │  │    BATbern Spring Conference 2023                                         │  │ │
│  │  │    4.8★ (45 ratings) • 168 attendees • [📄 View Presentation]           │  │ │
│  │  │    "Saved us thousands!" - Top Comment                                    │  │ │
│  │  └───────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  [Load More...] (9 more talks)                                                   │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── AVAILABILITY & PREFERENCES ───────────────────────────────────────────────────┐ │
│  │  🟢 Available for speaking engagements                                           │ │
│  │                                                                                   │ │
│  │  Preferred Topics: Kubernetes, Cloud Architecture, DevOps Automation            │ │
│  │  Preferred Formats: 45-min talk, Workshop (half-day), Panel discussion          │ │
│  │  Travel Willingness: Switzerland, Europe (occasionally)                          │ │
│  │  Availability: Evenings, Weekends (with advance notice)                          │ │
│  │                                                                                   │ │
│  │  [📧 Send Invitation] (Organizer only)                                          │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── RATINGS & FEEDBACK ────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  Overall Rating: 4.7★ from 89 ratings                                            │ │
│  │  ┌───────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ Content Quality:     ★★★★★ 4.8                                           │  │ │
│  │  │ Delivery:            ★★★★☆ 4.7                                           │  │ │
│  │  │ Engagement:          ★★★★★ 4.9                                           │  │ │
│  │  │ Practical Value:     ★★★★★ 4.6                                           │  │ │
│  │  └───────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  Recent Feedback:                                                                 │ │
│  │  ┌───────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ ★★★★★ "Best K8s talk I've attended. Peter explains complex concepts      │  │ │
│  │  │          simply and provides real-world examples." - Anna M., Spring 2024 │  │ │
│  │  └───────────────────────────────────────────────────────────────────────────┘  │ │
│  │  ┌───────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ ★★★★☆ "Great content, would love more hands-on demos next time."         │  │ │
│  │  │          - Thomas W., Autumn 2023                                          │  │ │
│  │  └───────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  [View All Feedback (89)]                                                         │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Interactive Elements

- **Contact Button**: Allows organizers to send speaking invitation or general message
- **Bookmark Button**: Attendees/organizers can bookmark speaker for future reference
- **Share Button**: Generate shareable link to speaker profile
- **Social Media Links**: Direct links to speaker's professional profiles (LinkedIn, Twitter, GitHub, Website)
- **Speaking History Cards**: Click to view event details and presentation materials
- **Expertise Tags**: Clickable to find other speakers with similar expertise
- **Filter Dropdown**: Filter speaking history by event, topic, or year
- **Load More Button**: Paginated history loading for speakers with many talks
- **Send Invitation Button**: (Organizer-only) Opens invitation workflow for this speaker
- **View All Feedback Link**: Opens detailed ratings and comments modal
- **Presentation Links**: Download or view presentation materials from past talks

---

## Functional Requirements Met

- **FR3**: Speaker self-service with complete profile display including bio, expertise, and history; speakers can view their performance feedback and ratings
- **FR10**: Speaker portal integration showing comprehensive speaker information accessible to speakers for self-management
- **FR17**: Intelligent speaker matching by displaying expertise, availability, speaking preferences, and historical performance ratings to help organizers make informed invitation decisions
- **Quality Assessment**: Ratings and feedback system provides data-driven speaker evaluation for organizer decision-making (supports FR17 speaker matching)
- **Profile Visibility**: Complete speaker profile view with all relevant information for organizers, attendees, and public users

---

## User Interactions

### For All Users (Public View)
1. **View Profile**: Browse complete speaker information including bio, expertise, and social links
2. **View Speaking History**: See list of past talks with ratings and feedback
3. **Click Expertise Tags**: Find other speakers with similar expertise areas
4. **View Presentations**: Download or view past presentation materials
5. **Read Feedback**: Browse ratings and attendee comments from previous talks
6. **Share Profile**: Generate shareable link to this profile

### For Organizers
7. **Contact Speaker**: Click [✉ Contact] to send message or speaking invitation
8. **Bookmark Speaker**: Save speaker to favorites for future events
9. **Send Invitation**: Click [📧 Send Invitation] to initiate speaker invitation workflow
10. **Review Availability**: Check speaker's current availability and preferences
11. **Assess Fit**: Review expertise tags, ratings, and past performance for event planning

### For Speakers (Self-View)
12. **View Public Profile**: See profile as others see it
13. **Edit Profile Link**: Navigate to profile management screen (if self-viewing)
14. **Track Profile Views**: See how many organizers have viewed profile
15. **Monitor Ratings**: Review feedback and ratings from past presentations

### For Attendees
16. **Bookmark Speaker**: Save to personal content library
17. **Follow Speaker**: Opt-in to notifications about future talks by this speaker
18. **Rate/Review**: (After attending talk) Provide feedback and rating
19. **Download Content**: Access presentation materials from attended talks

---

## Technical Notes

- **Component**: `SpeakerProfileDetailView.tsx` with role-based rendering
- **Permission-Based Display**: Show different actions based on user role (organizer vs. attendee vs. public)
- **Responsive Design**: Stack sections vertically on mobile, side-by-side on desktop
- **Image Optimization**: Lazy load speaker photo and presentation thumbnails
- **Social Media Integration**: Validate and display social links with appropriate icons
- **Rating System**: Use React-based star rating component with half-star support
- **Pagination**: Implement infinite scroll or "Load More" for speaking history
- **Caching Strategy**: Cache speaker profiles with React Query for performance
- **Real-time Updates**: Show live availability status if speaker updates profile
- **Accessibility**: ARIA labels for all interactive elements, keyboard navigation support
- **SEO Optimization**: Server-side rendering for public speaker profiles
- **Analytics Tracking**: Track profile views, contact attempts, and bookmark actions

---

## API Requirements

### Initial Page Load APIs

When the Speaker Profile Detail View screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/speakers/{speakerId}/profile**
   - Returns: Complete speaker profile including:
     - Personal info (name, title, company, location, photo URL)
     - Bio and description
     - Social media links (LinkedIn, Twitter, GitHub, website)
     - Member since date
     - Overall statistics (total talks, average rating, total attendees)
   - Used for: Populate speaker profile header and bio section

2. **GET /api/v1/speakers/{speakerId}/expertise**
   - Returns: List of expertise areas/topics speaker can present on
   - Response: `{ expertiseAreas: string[], languages: string[] }`
   - Used for: Display expertise tags and language proficiency

3. **GET /api/v1/speakers/{speakerId}/speaking-history**
   - Query params: `limit=10, offset=0, sortBy=date, order=desc`
   - Returns: Paginated list of past speaking engagements including:
     - Event name and date
     - Presentation title
     - Rating and review count
     - Attendee count
     - Presentation file URL
     - Top comment
   - Used for: Populate speaking history section with past talks

4. **GET /api/v1/speakers/{speakerId}/availability**
   - Returns: Current availability status and preferences:
     - `isAvailable: boolean`
     - `preferredTopics: string[]`
     - `preferredFormats: string[]`
     - `travelWillingness: string`
     - `availabilityNotes: string`
   - Used for: Display availability and preferences section (organizer-only)

5. **GET /api/v1/speakers/{speakerId}/ratings**
   - Query params: `summary=true`
   - Returns: Aggregated rating data:
     - Overall rating (average)
     - Total rating count
     - Breakdown by category (content quality, delivery, engagement, practical value)
   - Used for: Display ratings overview in ratings & feedback section

6. **GET /api/v1/speakers/{speakerId}/feedback**
   - Query params: `limit=3, offset=0, sortBy=date, order=desc`
   - Returns: Recent feedback/comments from attendees:
     - Rating (stars)
     - Comment text
     - Reviewer name
     - Event name
     - Date
   - Used for: Display recent feedback in ratings & feedback section

7. **GET /api/v1/speakers/{speakerId}/statistics**
   - Returns: Detailed speaker statistics:
     - Total talks delivered
     - Total attendees reached
     - Average attendance per talk
     - Profile view count
     - Bookmark count
   - Used for: Display speaker metrics in profile header

8. **GET /api/v1/users/me/bookmarks/speakers/{speakerId}**
   - Requires authentication
   - Returns: `{ isBookmarked: boolean }`
   - Used for: Show bookmark button state (filled vs. unfilled)

---

## Action APIs

APIs called by user interactions and actions:

### Profile Interactions

1. **POST /api/v1/users/me/bookmarks/speakers/{speakerId}**
   - Triggered by: [★ Bookmark] button click
   - Requires: User authentication
   - Response: `{ bookmarkId: string, isBookmarked: true }`
   - Used for: Add speaker to user's bookmarks
   - Side effect: Updates bookmark button state to "bookmarked"

2. **DELETE /api/v1/users/me/bookmarks/speakers/{speakerId}**
   - Triggered by: [★ Bookmark] button click (when already bookmarked)
   - Requires: User authentication
   - Response: `{ success: true, isBookmarked: false }`
   - Used for: Remove speaker from bookmarks
   - Side effect: Updates bookmark button state to "unbookmarked"

3. **POST /api/v1/speakers/{speakerId}/profile-views**
   - Triggered by: Automatically on page load
   - Payload: `{ viewerId: string, viewerRole: UserRole, timestamp: Date }`
   - Response: `{ success: true }`
   - Used for: Track profile views for analytics
   - Side effect: Increments profile view counter

4. **GET /api/v1/speakers/{speakerId}/profile/share-link**
   - Triggered by: [🔗 Share] button click
   - Response: `{ shareUrl: string, shortUrl: string }`
   - Used for: Generate shareable public link to profile
   - Opens: Share modal with generated link

### Communication Actions (Organizer-Only)

5. **POST /api/v1/organizers/messages/compose**
   - Triggered by: [✉ Contact] button click
   - Opens: Message composition modal
   - Payload: `{ recipientId: speakerId, recipientType: "speaker", subject: string, message: string }`
   - Response: `{ messageId: string, sent: true }`
   - Used for: Send direct message to speaker

6. **POST /api/v1/events/{eventId}/invitations/send**
   - Triggered by: [📧 Send Invitation] button click
   - Opens: Invitation wizard/modal
   - Payload: `{ speakerId: string, eventId: string, proposedTopics: string[], deadline: Date }`
   - Response: `{ invitationId: string, status: "sent", sentAt: Date }`
   - Used for: Send speaking invitation to speaker
   - Side effect: Creates invitation record, sends email notification

### Speaking History Interactions

7. **GET /api/v1/presentations/{presentationId}/download**
   - Triggered by: [📄 View Presentation] link click
   - Response: PDF file or presentation file
   - Used for: Download presentation materials
   - Opens: File download or viewer modal

8. **GET /api/v1/speakers/{speakerId}/speaking-history**
   - Triggered by: [Load More...] button click in speaking history
   - Query params: `limit=10, offset={currentOffset}, sortBy=date, order=desc`
   - Returns: Next page of speaking history items
   - Used for: Load additional speaking history entries (pagination)

9. **GET /api/v1/speakers/{speakerId}/speaking-history**
   - Triggered by: [Filter by Topic] dropdown selection
   - Query params: `topicFilter={selectedTopic}, limit=10, offset=0`
   - Returns: Filtered speaking history
   - Used for: Filter speaking history by topic

### Ratings & Feedback Actions

10. **GET /api/v1/speakers/{speakerId}/feedback**
    - Triggered by: [View All Feedback (89)] link click
    - Query params: `limit=50, offset=0, sortBy=date, order=desc`
    - Returns: Complete list of feedback/ratings
    - Opens: Feedback modal or separate page

11. **POST /api/v1/speakers/{speakerId}/ratings**
    - Triggered by: User submits rating (attendees who attended speaker's talk)
    - Payload: `{ eventId: string, rating: number, categories: { contentQuality: number, delivery: number, engagement: number, practicalValue: number }, comment: string }`
    - Response: `{ ratingId: string, averageRating: number }`
    - Used for: Submit attendee feedback and rating
    - Side effect: Updates speaker's overall rating

### Expertise & Discovery

12. **GET /api/v1/speakers/search**
    - Triggered by: Click on expertise tag (e.g., [Kubernetes])
    - Query params: `expertise={tagName}, limit=20`
    - Response: List of speakers with matching expertise
    - Used for: Find similar speakers with same expertise
    - Opens: Speaker search results page or modal

13. **GET /api/v1/events/upcoming**
    - Triggered by: User wants to see upcoming events with this speaker
    - Query params: `speakerId={speakerId}, status=published`
    - Returns: List of future events where speaker is presenting
    - Used for: Display "Upcoming Talks" section (if any)

---

## Navigation Map

### Primary Navigation Actions

1. **← Back button** → Navigate back to previous screen
   - Context-aware navigation:
     - From Speaker Matching Interface → Returns to [story-3.1-speaker-matching-interface.md](story-3.1-speaker-matching-interface.md)
     - From Content Discovery → Returns to [story-5.1-content-discovery.md](story-5.1-content-discovery.md)
     - From Event Management Dashboard → Returns to [story-1.16-event-management-dashboard.md](story-1.16-event-management-dashboard.md)
   - Maintains navigation context from referrer

2. **[✉ Contact] button** → Opens message composition modal
   - Type: Modal overlay
   - Role-based access: Organizer only
   - Opens: Message composer with speaker pre-selected as recipient
   - API call: `POST /api/v1/organizers/messages/compose`

3. **[★ Bookmark] button** → Toggles bookmark status
   - Type: In-place interaction (no navigation)
   - Role-based access: Authenticated users
   - Action: Toggle bookmark on/off
   - Visual feedback: Button fills when bookmarked
   - API calls: `POST /api/v1/users/me/bookmarks/speakers/{speakerId}` or `DELETE`

4. **[🔗 Share] button** → Opens share modal
   - Type: Modal overlay
   - Opens: Share dialog with generated link and social sharing options
   - Content: Shareable URL, copy button, social media share buttons
   - API call: `GET /api/v1/speakers/{speakerId}/profile/share-link`

5. **Social media link buttons ([🔗 LinkedIn], [🐦 Twitter], etc.)** → Opens external link
   - Type: External navigation
   - Opens: Speaker's social media profile in new tab
   - No API call needed (static links from profile data)

### Secondary Navigation (Data Interactions)

6. **Speaking History Card click** → Navigate to event details
   - Target: Event Detail Page [story-2.4-current-event-landing.md](story-2.4-current-event-landing.md) or Historical Event Archive [story-1.18-historical-archive.md](story-1.18-historical-archive.md)
   - Type: Full page navigation
   - Context: Event ID passed to event detail screen
   - Shows: Complete event information with speaker's session highlighted

7. **[📄 View Presentation] link click** → Opens presentation viewer
   - Type: Modal or new tab (depending on file type)
   - Opens: Presentation viewer (PDF viewer, slide viewer, or file download)
   - API call: `GET /api/v1/presentations/{presentationId}/download`

8. **Expertise tag click (e.g., [Kubernetes])** → Navigate to speaker search results
   - Target: Content Discovery screen [story-5.1-content-discovery.md](story-5.1-content-discovery.md) filtered by expertise
   - Type: Full page navigation
   - Context: Pre-filtered by selected expertise tag
   - API call: `GET /api/v1/speakers/search?expertise={tagName}`

9. **[Load More...] button click** → Loads additional speaking history
   - Type: In-place content expansion
   - No navigation, updates current view
   - API call: `GET /api/v1/speakers/{speakerId}/speaking-history` with pagination
   - Visual feedback: Loading spinner, then additional cards appear

10. **[Filter by Topic] dropdown selection** → Filters speaking history
    - Type: In-place content filtering
    - No navigation, updates speaking history list
    - API call: `GET /api/v1/speakers/{speakerId}/speaking-history` with topic filter
    - Visual feedback: History list updates to show filtered results

### Organizer-Specific Navigation

11. **[📧 Send Invitation] button** → Opens invitation workflow
    - Type: Modal or full page (depending on complexity)
    - Role-based access: Organizer only
    - Opens: Speaker invitation wizard
    - Target: Speaker Invitation Flow (similar to [story-3.2-invitation-response.md](story-3.2-invitation-response.md) but from organizer perspective)
    - Pre-fills speaker information
    - API call: `POST /api/v1/events/{eventId}/invitations/send`

12. **Availability & Preferences section** → View only for organizers
    - Type: Display only (no navigation)
    - Role-based visibility: Only shown to organizers
    - Shows: Speaker's current availability status and speaking preferences
    - Helps organizers make informed invitation decisions

### Feedback & Ratings Navigation

13. **[View All Feedback (89)] link** → Opens full feedback list
    - Type: Modal or full page
    - Opens: Complete ratings and feedback list
    - Shows: All ratings, comments, and breakdown by category
    - API call: `GET /api/v1/speakers/{speakerId}/feedback` (full dataset)
    - Allows: Filtering by rating, sorting by date/helpfulness

14. **Rating star click** → Opens rating submission (attendees only)
    - Type: Modal overlay
    - Role-based access: Attendees who attended speaker's talk
    - Opens: Rating form with star rating and comment field
    - API call: `POST /api/v1/speakers/{speakerId}/ratings`
    - Validation: Must have attended at least one talk by this speaker

### Event-Driven Navigation

15. **Profile loaded successfully** → No automatic navigation
    - Displays: Complete speaker profile
    - Feedback: None (successful state)
    - Tracks: Profile view for analytics

16. **Contact message sent successfully** → Shows confirmation
    - Type: Toast notification
    - Message: "Message sent to Peter Muller"
    - No automatic navigation, stays on profile

17. **Invitation sent successfully** → Shows confirmation
    - Type: Toast notification
    - Message: "Invitation sent successfully"
    - Optional: Navigate to Speaker Matching Interface [story-3.1-speaker-matching-interface.md](story-3.1-speaker-matching-interface.md)

18. **Bookmark added/removed** → Visual feedback only
    - Type: Button state change
    - Visual: Button fills/unfills
    - No navigation

### Error States & Redirects

19. **Speaker profile not found** → Navigate to error page
    - Target: 404 error page or Speaker Directory
    - Message: "Speaker profile not found"
    - Suggested action: "Browse all speakers" link

20. **Unauthorized access to organizer-only features** → Show permission message
    - Type: Inline message or modal
    - Message: "Organizer access required"
    - No automatic navigation, features hidden/disabled

21. **API error loading profile** → Show error state
    - Type: In-page error message
    - Message: "Unable to load speaker profile"
    - Action: [Retry] button to reload data
    - No automatic navigation

22. **Network error** → Show offline state
    - Type: Banner notification
    - Message: "You are offline. Showing cached data."
    - Shows: Last cached version of profile (if available)
    - Auto-retries: When connection restored

### Self-View Navigation (Speaker viewing own profile)

23. **"This is your profile" banner** → Shows edit option
    - Type: Info banner at top of page
    - Shows: "You are viewing your public profile"
    - Action: [Edit Profile] button
    - Target: Speaker Profile Management [story-7.1-speaker-profile-management.md](story-7.1-speaker-profile-management.md)

24. **[Edit Profile] button** → Navigate to profile editor
    - Type: Full page navigation
    - Target: [story-7.1-speaker-profile-management.md](story-7.1-speaker-profile-management.md)
    - Context: Edit mode for speaker's own profile

---

## Responsive Design Considerations

### Mobile Layout Changes

- **Profile Header**: Stack photo, name, and contact buttons vertically
- **Bio Section**: Full-width text with responsive font sizing
- **Expertise Tags**: Wrap tags into multiple rows on smaller screens
- **Speaking History Cards**: Full-width cards, stacked vertically
- **Ratings Section**: Collapse rating breakdown into expandable accordion
- **Action Buttons**: Fixed bottom bar with primary actions (Contact, Bookmark)

### Tablet Layout Changes

- **Profile Header**: Side-by-side layout with photo on left, info on right
- **Speaking History**: Two columns of cards
- **Ratings**: Show rating breakdown inline, no accordion needed
- **Navigation**: Tablet-optimized back button and breadcrumbs

### Mobile-Specific Interactions

- **Swipe Gestures**: Swipe left/right on speaking history cards to navigate between talks
- **Pull to Refresh**: Refresh speaker profile data by pulling down
- **Tap to Expand**: Tap bio section to expand/collapse if exceeds certain height
- **Bottom Sheet**: Open filters and share options in bottom sheet instead of modal
- **Sticky Header**: Speaker name and photo sticky at top when scrolling

---

## Accessibility Notes

- **Keyboard Navigation**: All interactive elements accessible via Tab key
- **ARIA Labels**:
  - `aria-label="Contact speaker Peter Muller"` on contact button
  - `aria-label="Bookmark speaker"` on bookmark button
  - `aria-label="Share profile"` on share button
  - `aria-label="Speaker rating: 4.7 out of 5 stars"` on rating display
- **Focus Indicators**: 2px solid border on focused elements with high contrast
- **Screen Reader Support**:
  - Profile sections use semantic HTML headings (h2, h3)
  - Speaking history cards have descriptive text for screen readers
  - Star ratings announced as "Rating: 4.7 out of 5 stars"
- **Color Contrast**: All text meets WCAG 2.1 AA standards (4.5:1 minimum)
- **Alt Text**: Speaker photo includes descriptive alt text
- **Skip Links**: "Skip to main content" link for keyboard users
- **ARIA Live Regions**: Dynamic updates (bookmarks, ratings) announced to screen readers

---

## State Management

### Local Component State

- `showFullBio`: boolean - Whether bio section is expanded or collapsed
- `speakingHistoryOffset`: number - Current pagination offset for speaking history
- `isContactModalOpen`: boolean - Whether contact modal is open
- `isShareModalOpen`: boolean - Whether share modal is open
- `isFeedbackModalOpen`: boolean - Whether feedback modal is open
- `selectedTopicFilter`: string | null - Current topic filter for speaking history

### Global State (Zustand Store)

- `auth.currentUser`: User - Current authenticated user
- `auth.currentRole`: UserRole - Current user role (organizer, speaker, attendee, public)
- `bookmarks.speakerBookmarks`: string[] - List of bookmarked speaker IDs
- `ui.notifications`: Notification[] - Toast notifications for feedback

### Server State (React Query)

- `speakerProfile`: Speaker profile data (cached for 5 minutes)
- `speakingHistory`: Speaking history data (cached for 10 minutes)
- `speakerRatings`: Rating summary (cached for 5 minutes)
- `speakerFeedback`: Recent feedback (cached for 5 minutes)
- `speakerAvailability`: Availability status (cached for 1 minute, organizer-only)

### Real-Time Updates

- **Profile Views**: Real-time profile view counter using WebSocket (for speaker self-view)
- **Availability Status**: Real-time availability updates when speaker changes status
- **New Ratings**: Live rating updates when new feedback is submitted
- **Invitation Status**: Real-time notification when organizer sends invitation

---

## Edge Cases & Error Handling

- **Empty Speaking History**: Show "No speaking history yet" message with encouragement text
- **No Ratings**: Show "No ratings yet" instead of rating breakdown
- **Missing Profile Photo**: Display default avatar with speaker initials
- **Broken Social Links**: Validate URLs, hide broken links, show working links only
- **Loading State**: Display skeleton screens while data loads (profile, history, ratings)
- **API Error**: Show error message with [Retry] button: "Unable to load speaker profile"
- **Permission Denied**: Hide organizer-only sections (availability, send invitation) for non-organizers
- **Offline Mode**: Show cached profile data with banner "Viewing offline version"
- **Slow Network**: Show loading indicators for >2 seconds, allow cancellation
- **Bookmark Conflict**: Handle optimistic UI updates, rollback on API error
- **Long Bio**: Truncate bio text with "Read More" expansion if exceeds 5 lines
- **No Availability Data**: Show "Availability not set" for organizers (with note to contact speaker directly)
- **Self-View**: Add banner "This is your public profile" with [Edit Profile] button for speakers viewing own profile

---

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-10-02 | 1.0 | Initial wireframe creation for Speaker Profile Detail View | Sally (UX Expert) |

---

## Review Notes

### Open Questions

1. **Profile Privacy**: Should speakers have granular privacy controls for what information is visible to public vs. organizers?
2. **Rating System**: Should we allow speakers to respond to feedback/ratings?
3. **Profile Completeness**: Should we show a completeness indicator for speakers with incomplete profiles?
4. **Related Speakers**: Should we show "Similar Speakers" recommendation section at bottom?
5. **Contact History**: Should organizers see history of past communications with this speaker?
6. **Availability Calendar**: Would a visual calendar view be more helpful than text description for availability?

### Design Iterations

- **v1.0**: Initial comprehensive design with all key sections
- Consider adding: "Upcoming Talks" section if speaker has confirmed future presentations
- Consider adding: "Connect with colleagues" feature showing other employees from same company who attended speaker's talks

### Stakeholder Feedback

- Pending review from organizers and speakers for usability validation
- Need to validate rating categories with historical feedback data
- Confirm organizer-only vs. public information visibility rules
