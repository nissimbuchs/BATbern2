# Story: AI-Powered Content Discovery Engine - Wireframe

**Story**: Epic 5, Story 1
**Screen**: AI-Powered Content Discovery Engine
**User Role**: Attendee
**Related FR**: FR13 (Content Discovery)

---

## 2. AI-Powered Content Discovery Engine (FR13)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back to Home              BATbern Knowledge Hub                    [Login/Register]│
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐ │
│  │  🔍 What are you looking for?                                                    │ │
│  │  ┌──────────────────────────────────────────────────────────────────────┐       │ │
│  │  │ Search presentations, speakers, topics...                             │ 🎤    │ │
│  │  └──────────────────────────────────────────────────────────────────────┘       │ │
│  │                                                                                   │ │
│  │  Suggestions: "Kubernetes security" "Thomas Weber" "DevOps 2023" "AI/ML"         │ │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── SMART FILTERS ──────────────┬─── YOUR INTERESTS ─────────────────────────────┐ │
│  │                                 │                                                │ │
│  │  Topic Categories               │  Based on your activity:                      │ │
│  │  ☑ Cloud Native (142)          │  • Kubernetes & Containers                     │ │
│  │  ☐ Security (89)               │  • DevOps Practices                            │ │
│  │  ☐ AI/ML (67)                  │  • Cloud Architecture                          │ │
│  │  ☐ DevOps (134)                │                                                │ │
│  │  ☐ Data Engineering (45)       │  [Customize Interests]                         │ │
│  │                                 │                                                │ │
│  │  Time Period                    │  🤖 AI Recommendations                         │ │
│  │  ○ Last Month                  │  "Since you enjoyed 'K8s Security', you       │ │
│  │  ○ Last Year                   │   might like these presentations..."          │ │
│  │  ● Last 5 Years                │                                                │ │
│  │  ○ All Time (20+ years)        │  [View Recommendations →]                      │ │
│  │                                 │                                                │ │
│  │  Content Type                   │                                                │ │
│  │  ☑ Presentations               │                                                │ │
│  │  ☑ Videos                      │                                                │ │
│  │  ☐ Code Examples               │                                                │ │
│  │  ☐ Workshop Materials          │                                                │ │
│  │                                 │                                                │ │
│  │  [Apply Filters] [Clear All]    │                                                │ │
│  └─────────────────────────────────┴────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── SEARCH RESULTS (247 items) ──────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Sort by: [Relevance ▼] [Date ▼] [Rating ▼] [Downloads ▼]     View: [▦▦] [☰]  │ │
│  │                                                                                  │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐ │ │
│  │  │ 95% Match                                                                   │ │ │
│  │  │                                                                              │ │ │
│  │  │  🎯 Kubernetes Security Best Practices                                      │ │ │
│  │  │  Thomas Weber • Spring 2024 • ⭐ 4.8/5 • 👁️ 1,247 views • ⬇ 523            │ │ │
│  │  │                                                                              │ │ │
│  │  │  "...implementing zero-trust security in Kubernetes environments with       │ │ │
│  │  │  practical examples from Swiss financial sector compliance..."              │ │ │
│  │  │                                                                              │ │ │
│  │  │  Tags: #kubernetes #security #compliance #fintech                           │ │ │
│  │  │                                                                              │ │ │
│  │  │  [View] [Download PDF] [Watch Video] [💾 Save] [Share]                     │ │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘ │ │
│  │                                                                                  │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐ │ │
│  │  │ 87% Match                                                                   │ │ │
│  │  │                                                                              │ │ │
│  │  │  🔒 Container Security Deep Dive                                            │ │ │
│  │  │  Sara Kim • Autumn 2023 • ⭐ 4.9/5 • 👁️ 2,103 views • ⬇ 891               │ │ │
│  │  │                                                                              │ │ │
│  │  │  "...comprehensive security scanning in CI/CD pipelines with                │ │ │
│  │  │  lessons learned from production incidents..."                              │ │ │
│  │  │                                                                              │ │ │
│  │  │  Tags: #docker #security #cicd #devops                                      │ │ │
│  │  │                                                                              │ │ │
│  │  │  [View] [Download PDF] [Watch Video] [💾 Save] [Share]                     │ │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘ │ │
│  │                                                                                  │ │
│  │  [Load More Results...]                                                         │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── LEARNING PATHS ────────────────────────────────────────────────────────────┐   │
│  │                                                                                │   │
│  │  📚 Curated Learning Paths Based on Your Search                               │   │
│  │                                                                                │   │
│  │  Kubernetes Journey: Beginner → Expert                                        │   │
│  │  ━━━━━━━━━━━━━━━━●━━━━━━━━━━  65% Complete                                   │   │
│  │  Next: "Advanced Networking in K8s" by Marc Baum                             │   │
│  │  [Continue Learning →]                                                        │   │
│  └────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **AI-Powered Search**: Natural language search with semantic understanding
- **Smart Filters**: Multi-dimensional filtering (topic, time, content type)
- **Personalized Recommendations**: ML-based suggestions based on user activity
- **Match Score**: AI confidence score showing relevance (95%, 87%, etc.)
- **Learning Paths**: Curated progression tracking across related content
- **Rich Previews**: View, download, watch, save, and share options
- **Voice Search**: Microphone icon for voice-based queries

## Functional Requirements Met

- **FR13**: Complete AI-powered content discovery with semantic search
- **Personalization**: User interest tracking and recommendations
- **20+ Years Archive**: Access to complete historical content
- **Multi-format Content**: PDFs, videos, code examples, workshop materials
- **Learning Path Integration**: Progression tracking and recommendations
- **Social Features**: Save, share, and collaborative discovery

## User Interactions

1. **Search**: Enter natural language queries, get semantically relevant results
2. **Filter**: Apply multi-dimensional filters to narrow results
3. **Customize Interests**: Set and manage content preferences
4. **View Content**: Access presentations, videos, and materials
5. **Save & Share**: Bookmark content and share with colleagues
6. **Follow Learning Paths**: Track progress through curated content sequences
7. **Get Recommendations**: Discover related content via AI suggestions

## Technical Notes

- Elasticsearch/OpenSearch for full-text search
- ML-based semantic search using embeddings
- Collaborative filtering for recommendations
- User activity tracking for personalization
- Learning path progress stored per user
- Real-time search suggestions
- CDN delivery for media content
- Analytics tracking for content performance

---

## API Requirements

APIs needed to load and display data for this screen:

### Initial Page Load

1. **GET /api/v1/content/discovery/homepage**
   - Retrieve discovery homepage data
   - Response includes: featured content, user interests, recent searches, trending topics
   - Used for: Initial screen rendering with personalized content
   - Auth: Optional (personalized if logged in)

2. **GET /api/v1/users/{userId}/interests**
   - Retrieve user's content interests and preferences
   - Response includes: selected topics, favorite speakers, learning paths
   - Used for: "Your Interests" panel
   - Requires: User authentication

3. **GET /api/v1/content/discovery/suggestions**
   - Retrieve search suggestions and trending queries
   - Response includes: popular searches, related topics
   - Used for: Search suggestions below search box

4. **GET /api/v1/content/learning-paths**
   - Retrieve user's learning paths with progress
   - Query params: `userId`
   - Response includes: path details, completion percentage, next content
   - Used for: "Learning Paths" panel

### Search & Filtering

5. **GET /api/v1/content/search**
   - Retrieve search results
   - Query params: `query`, `topics[]`, `period`, `contentTypes[]`, `sort`, `page`, `limit`
   - Response includes: results with match scores, metadata, statistics
   - Used for: Main search results display
   - Processing: Semantic search with ML embeddings

6. **GET /api/v1/content/filters/metadata**
   - Retrieve available filter options and counts
   - Response includes: topic categories with counts, time periods, content types
   - Used for: Populating filter options with result counts

7. **GET /api/v1/content/search/autocomplete**
   - Retrieve search autocomplete suggestions
   - Query params: `query`, `limit=10`
   - Response includes: suggested queries, speakers, topics
   - Used for: Real-time search suggestions as user types
   - Debounced: 300ms delay

---

## Action APIs

APIs called by user interactions and actions:

### Search & Discovery

1. **POST /api/v1/content/search**
   - Triggered by: Search query submission or filter application
   - Payload: `{ query, filters: { topics, period, contentTypes }, sort, page }`
   - Response: Search results with match scores, facets, total count
   - Processing:
     - Semantic search using embeddings
     - Collaborative filtering
     - User context for personalization
   - Analytics: Logs search query for recommendations

2. **GET /api/v1/content/search/voice**
   - Triggered by: 🎤 Voice search button
   - Opens: Voice input interface
   - Processing: Speech-to-text conversion
   - Then: Executes standard search with transcribed query

3. **POST /api/v1/content/search/feedback**
   - Triggered by: User interaction with search results
   - Payload: `{ query, resultId, action: "click|save|ignore", position }`
   - Response: Feedback recorded
   - Used for: Improving ML ranking models

### Filtering & Sorting

4. **PUT /api/v1/content/search/filters**
   - Triggered by: [Apply Filters] button or real-time filter changes
   - Payload: `{ topics[], period, contentTypes[] }`
   - Response: Updated search results matching filters
   - Updates: Search results panel refreshes

5. **DELETE /api/v1/content/search/filters**
   - Triggered by: [Clear All] button
   - Response: Search results without filters
   - Updates: All filter checkboxes cleared, results refresh

6. **PUT /api/v1/content/search/sort**
   - Triggered by: Sort dropdown selection
   - Payload: `{ sortBy: "relevance|date|rating|downloads" }`
   - Response: Re-sorted search results
   - Updates: Results reorder

### Content Actions

7. **GET /api/v1/content/{contentId}**
   - Triggered by: [View] button on result card
   - Response: Full content details, viewer URL
   - Opens: Content viewer (PDF viewer, video player, etc.)
   - Analytics: Increments view count

8. **GET /api/v1/content/{contentId}/download**
   - Triggered by: [Download PDF] button
   - Response: Presigned S3 URL or file stream
   - Downloads: PDF file
   - Analytics: Increments download count
   - Auth: May require login for some content

9. **GET /api/v1/content/{contentId}/video**
   - Triggered by: [Watch Video] button
   - Response: Video streaming URL, player configuration
   - Opens: Video player (modal or full screen)
   - CDN: Streams from CloudFront

10. **POST /api/v1/users/{userId}/saved-content**
    - Triggered by: [💾 Save] button
    - Payload: `{ contentId, collection: "default" }`
    - Response: Content saved to user's library
    - Feedback: Button changes to "✓ Saved"
    - Side effect: Added to recommendations algorithm

11. **DELETE /api/v1/users/{userId}/saved-content/{contentId}**
    - Triggered by: Clicking "✓ Saved" again (unsave)
    - Response: Content removed from saved items
    - Feedback: Button reverts to "💾 Save"

12. **POST /api/v1/content/{contentId}/share**
    - Triggered by: [Share] button
    - Opens: Share modal with options
    - Payload: `{ method: "email|link|social", recipients: [] }`
    - Response: Share link generated, emails sent
    - Options: Email, copy link, LinkedIn, Twitter

### Personalization

13. **GET /api/v1/content/recommendations**
    - Triggered by: [View Recommendations →] button
    - Query params: `userId`, `limit=10`
    - Response: AI-recommended content based on activity
    - Opens: Recommendations modal or dedicated page
    - ML: Collaborative filtering + content-based

14. **PUT /api/v1/users/{userId}/interests**
    - Triggered by: [Customize Interests] button
    - Opens: Interest customization modal
    - Payload: `{ topics: [], speakers: [], excludeTopics: [] }`
    - Response: Updated interest preferences
    - Updates: "Your Interests" panel, search results personalization

15. **POST /api/v1/users/{userId}/activity**
    - Triggered by: Various user interactions (auto-tracked)
    - Payload: `{ action: "view|download|search|save", contentId, metadata }`
    - Response: Activity logged
    - Used for: Personalization and recommendations
    - Background: Async, doesn't block UI

### Learning Paths

16. **GET /api/v1/learning-paths/{pathId}**
    - Triggered by: [Continue Learning →] button
    - Response: Full learning path details, content list, progress
    - Opens: Learning path page with content sequence

17. **POST /api/v1/users/{userId}/learning-paths/{pathId}/progress**
    - Triggered by: Viewing content within a learning path
    - Payload: `{ contentId, completed: true }`
    - Response: Progress updated, next content unlocked
    - Updates: Progress bar, completion percentage

18. **GET /api/v1/learning-paths/recommendations**
    - Triggered by: Viewing learning paths section
    - Query params: `userId`, `basedOnInterests=true`
    - Response: Recommended learning paths
    - Used for: Suggesting new learning paths to user

### Voice Search

19. **POST /api/v1/content/search/voice-to-text**
    - Triggered by: Recording voice input
    - Payload: Audio stream or file
    - Response: Transcribed text query
    - Processing: Speech-to-text API (AWS Transcribe or similar)
    - Then: Executes regular search with transcribed query

---

## Navigation Map

Screen transitions triggered by actions and events:

### Primary Navigation

1. **← Back to Home**
   - **Target**: Main BATbern homepage
   - **Type**: Full page navigation
   - **Context**: Return to site homepage

2. **[Login/Register]**
   - **Target**: Login/registration page
   - **Type**: Modal or full page
   - **Context**: Required for saving, learning paths, personalization

### Search Interaction

3. **Search Box Interaction**
   - **Type**: Inline autocomplete dropdown
   - **Trigger**: Typing (debounced 300ms)
   - **Content**: Suggestions, recent searches, trending topics
   - **Action**: Click suggestion → Execute search

4. **🎤 Voice Search**
   - **Target**: Voice input modal
   - **Type**: Modal overlay with audio visualization
   - **Flow**: Record → Transcribe → Execute search
   - **Feedback**: Visual waveform, "Listening..." indicator

5. **Search Submission**
   - **Action**: Enter key or search button
   - **No Navigation**: Remains on discovery page
   - **Updates**: Search results panel refreshes with new results

### Filter & Sort Navigation

6. **Filter Checkboxes**
   - **Action**: Toggle filters
   - **No Navigation**: Remains on page
   - **Updates**: Real-time or on [Apply Filters]
   - **URL**: Updates query params for shareability

7. **[Apply Filters]**
   - **No Navigation**: Remains on page
   - **Updates**: Search results refresh with filters applied
   - **Feedback**: Result count updates

8. **[Clear All]**
   - **No Navigation**: Remains on page
   - **Updates**: All filters cleared, original results restored

9. **Sort Dropdown**
   - **No Navigation**: Remains on page
   - **Updates**: Results reorder by selected criterion

10. **View Toggle** ([▦▦] grid / [☰] list)
    - **No Navigation**: Remains on page
    - **Updates**: Result cards change layout
    - **Preference**: Saved for future visits

### Result Card Actions

11. **[View]**
    - **Target**: Content viewer page
    - **Type**: Full page or modal
    - **Content**: PDF viewer, video player, or content reader
    - **Features**: Fullscreen, annotations, related content

12. **[Download PDF]**
    - **Action**: Downloads file
    - **No Navigation**: Remains on page
    - **Feedback**: Download initiated toast
    - **Auth Check**: May require login

13. **[Watch Video]**
    - **Target**: Video player modal or page
    - **Type**: Modal overlay with video player
    - **Features**: Play, pause, captions, quality selection
    - **Close**: Returns to discovery page

14. **[💾 Save]**
    - **Auth Check**: Requires login
    - **If Logged In**:
      - Action: Saves content
      - Feedback: Button changes to "✓ Saved"
      - No Navigation: Remains on page
    - **If Not Logged In**:
      - Target: Login modal
      - Context: "Login to save content"

15. **[Share]**
    - **Target**: Share options modal
    - **Type**: Modal overlay
    - **Options**: Email, copy link, social media
    - **Submit**: Generates shareable link
    - **Close**: Returns to discovery page

16. **Result Card Click** (not on button)
    - **Target**: Content detail page
    - **Type**: Full page navigation
    - **Content**: Full content view with metadata, related content, comments

### Personalization Navigation

17. **[Customize Interests]**
    - **Target**: Interest customization modal
    - **Type**: Modal overlay with multi-select
    - **Content**: Topic selector, speaker preferences
    - **Submit**: Saves preferences, updates recommendations
    - **Close**: Returns to discovery page

18. **[View Recommendations →]**
    - **Target**: Recommendations page or modal
    - **Type**: Full page or modal
    - **Content**: AI-curated content based on activity
    - **Features**: Explanation of why recommended

19. **Interest Tag Click** (in "Your Interests")
    - **Action**: Filters search to that topic
    - **No Navigation**: Remains on discovery page
    - **Updates**: Search results filtered to selected interest

### Learning Paths Navigation

20. **[Continue Learning →]**
    - **Target**: Learning path page
    - **Type**: Full page navigation
    - **Content**: Sequential content with progress tracking
    - **Features**: Next/previous, completion badges

21. **Learning Path Progress Bar Click**
    - **Target**: Learning path details modal
    - **Type**: Modal overlay
    - **Content**: Full path outline, completed items, next steps
    - **Actions**: [Resume], [View Details], [Share Progress]

22. **"Next: [Content Title]" Link**
    - **Target**: Next content in learning path
    - **Type**: Content viewer page
    - **Context**: Learning path context maintained

### Load More & Pagination

23. **[Load More Results...]**
    - **Action**: Loads next page of results
    - **No Navigation**: Remains on page
    - **Updates**: Appends results to current list (infinite scroll)
    - **Alternative**: Pagination buttons (1, 2, 3...)

### Event-Driven Navigation

24. **On Search Complete**
    - **No Navigation**: Remains on page
    - **Updates**: Results panel, result count, facets
    - **Feedback**: Loading spinner → results appear

25. **On No Results Found**
    - **No Navigation**: Remains on page
    - **Feedback**: "No results found" message
    - **Suggestions**: Related searches, clear filters, browse trending

26. **On Content Saved**
    - **No Navigation**: Remains on page
    - **Feedback**: "Saved to your library" toast
    - **Action Link**: [View Library] in toast

27. **On Share Success**
    - **No Navigation**: Remains on page
    - **Feedback**: "Link copied" or "Shared successfully" toast

28. **On Login Required**
    - **Target**: Login modal
    - **Type**: Modal overlay
    - **Context**: Saves intended action (save, learning path)
    - **After Login**: Completes intended action, returns to discovery

### Error States

29. **On Search Error**
    - **No Navigation**: Remains on page
    - **Feedback**: Error message with retry option
    - **Actions**: [Retry Search], [Clear Filters]

30. **On Content Unavailable**
    - **Feedback**: "Content no longer available" message
    - **Suggestions**: Related content, similar topics

31. **On Download Failure**
    - **No Navigation**: Remains on page
    - **Feedback**: "Download failed" toast
    - **Actions**: [Retry], [Contact Support]

### Mobile-Specific

32. **Mobile Filter Panel**
    - **Target**: Full-screen filter modal
    - **Type**: Slides up from bottom
    - **Actions**: Apply, clear, close

33. **Mobile Voice Search**
    - **Native**: Uses device speech recognition
    - **UI**: Full-screen with large microphone icon
    - **Feedback**: Visual audio levels

34. **Mobile Result Cards**
    - **Layout**: Single column, compact
    - **Actions**: Swipe left for more options
    - **Save**: Double-tap to save

---
