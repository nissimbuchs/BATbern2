# Story: Offline Content & Download Manager - Wireframe

**Story**: Epic 5, Story 3
**Screen**: Offline Content & Download Manager
**User Role**: Attendee
**Related FR**: FR13 (Offline)

---

## 8. Offline Content & Download Manager

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                          Offline Content Manager                    [Settings]│
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── DOWNLOAD STATUS ─────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Available Storage: 2.4 GB          Used: 847 MB (35%)                         │ │
│  │  ████████████░░░░░░░░░░░░░░░░░░░░░░░                                          │ │
│  │                                                                                  │ │
│  │  Auto-download: ● On WiFi only  ○ Always  ○ Never                             │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── OFFLINE CONTENT ─────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Downloaded for Offline (12 items)                          Sort: [Recent ▼]   │ │
│  │                                                                                  │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐ │ │
│  │  │ 📄 Kubernetes Security Best Practices                                      │ │ │
│  │  │    PDF • 12.3 MB • Downloaded 2 days ago                                  │ │ │
│  │  │    ✓ Slides  ✓ Video  ✓ Code samples                                      │ │ │
│  │  │    [Open] [Update] [Delete]                                                │ │ │
│  │  ├────────────────────────────────────────────────────────────────────────────┤ │ │
│  │  │ 📄 Container Orchestration at Scale                                        │ │ │
│  │  │    PDF • 8.7 MB • Downloaded 1 week ago                                    │ │ │
│  │  │    ✓ Slides  ✗ Video (156 MB)  ✓ Resources                                │ │ │
│  │  │    [Open] [Download Video] [Delete]                                        │ │ │
│  │  ├────────────────────────────────────────────────────────────────────────────┤ │ │
│  │  │ 📁 Spring Conference 2025 Bundle                                           │ │ │
│  │  │    8 presentations • 89 MB • Synced today                                  │ │ │
│  │  │    Event in 45 days                                                        │ │ │
│  │  │    [Browse] [Update All] [Delete]                                          │ │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘ │ │
│  │                                                                                  │ │
│  │  [Download More] [Delete All] [Export to Device]                               │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── SMART SYNC ───────────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Automatic Downloads                                                            │ │
│  │  ☑ Upcoming event materials (2 weeks before)                                   │ │
│  │  ☑ Bookmarked presentations                                                     │ │
│  │  ☑ In-progress learning paths                                                  │ │
│  │  ☐ Trending in my company                                                      │ │
│  │  ☐ New content matching interests                                              │ │
│  │                                                                                  │ │
│  │  Sync Schedule: ● Daily at 02:00  ○ Weekly  ○ Manual only                     │ │
│  │  Last sync: Today, 02:00 (Success)                                             │ │
│  │  Next sync: Tomorrow, 02:00                                                    │ │
│  │                                                                                  │ │
│  │  [Sync Now] [Configure Rules] [View Sync History]                              │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── OFFLINE VIEWER ──────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Continue Reading (Offline Mode)                                                │ │
│  │                                                                                  │ │
│  │  "Kubernetes Security Best Practices"                                          │ │
│  │  Page 12 of 45 • Last viewed: Yesterday                                        │ │
│  │  [Continue Reading →]                                                           │ │
│  │                                                                                  │ │
│  │  Reading Queue (3 items)                                                       │ │
│  │  • GitOps Workflows - 20 min read                                              │ │
│  │  • Service Mesh Patterns - 15 min read                                         │ │
│  │  • Cloud Cost Optimization - 25 min read                                       │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## API Requirements

### Initial Page Load APIs

When the Offline Content Manager screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/attendees/{userId}/offline/storage**
   - Returns: Available storage, used storage, storage breakdown by content type
   - Used for: Display storage usage bar and metrics

2. **GET /api/v1/attendees/{userId}/offline/settings**
   - Returns: Auto-download preferences (WiFi/Always/Never), sync schedule, sync rules
   - Used for: Populate download status panel and smart sync settings

3. **GET /api/v1/attendees/{userId}/offline/content**
   - Query params: sortBy (recent, size, name), limit, offset
   - Returns: List of downloaded content items with metadata, download dates, component availability
   - Used for: Populate offline content list with download status

4. **GET /api/v1/attendees/{userId}/offline/bundles**
   - Returns: Event bundles downloaded with item counts, total size, sync status
   - Used for: Display bundled content for upcoming events

5. **GET /api/v1/attendees/{userId}/offline/sync-status**
   - Returns: Last sync timestamp, next scheduled sync, sync history, success/failure status
   - Used for: Display sync schedule information in Smart Sync panel

6. **GET /api/v1/attendees/{userId}/offline/reading-queue**
   - Returns: Queue of content items for offline reading, progress tracking, estimated read times
   - Used for: Populate offline viewer reading queue and continue reading section

7. **GET /api/v1/attendees/{userId}/offline/recommendations**
   - Returns: Suggested content for offline download based on upcoming events, interests, learning paths
   - Used for: Smart sync auto-download suggestions

8. **WebSocket /ws/offline/sync-progress**
   - Real-time updates: Download progress, sync status, storage changes, completion notifications
   - Used for: Live progress bars and status updates during sync operations

---

## Action APIs

### Download & Storage Management

1. **POST /api/v1/attendees/{userId}/offline/download**
   - Payload: `{ contentId, components: ["slides", "video", "resources"], priority: "high|normal|low" }`
   - Response: Download task ID, estimated time, storage required
   - Used for: Initiate download of individual content or additional components

2. **POST /api/v1/attendees/{userId}/offline/bundles/{bundleId}/download**
   - Payload: `{ includeVideos: boolean, components: ["slides", "videos", "resources"] }`
   - Response: Bulk download task ID, item count, total size
   - Used for: Download event bundle with all presentations

3. **PUT /api/v1/attendees/{userId}/offline/content/{contentId}/update**
   - Payload: `{ components: ["slides", "video"] }`
   - Response: Updated content metadata, new version info
   - Used for: Update existing offline content with latest version

4. **DELETE /api/v1/attendees/{userId}/offline/content/{contentId}**
   - Response: Storage freed, updated storage metrics
   - Used for: Delete individual offline content item

5. **DELETE /api/v1/attendees/{userId}/offline/all**
   - Query params: confirmationToken
   - Response: Total storage freed, item count deleted
   - Used for: Delete all offline content

6. **POST /api/v1/attendees/{userId}/offline/export**
   - Payload: `{ contentIds: [], format: "zip|tar", includeMetadata: boolean }`
   - Response: Export task ID, download URL (when ready)
   - Used for: Export offline content to device storage

### Sync & Settings Management

7. **PUT /api/v1/attendees/{userId}/offline/settings**
   - Payload: `{ autoDownload: "wifi|always|never", syncSchedule: "daily|weekly|manual", syncTime: "02:00" }`
   - Response: Updated settings confirmation
   - Used for: Update download and sync preferences

8. **POST /api/v1/attendees/{userId}/offline/sync**
   - Payload: `{ force: boolean }`
   - Response: Sync task ID, estimated items to sync
   - Used for: Trigger immediate manual sync

9. **PUT /api/v1/attendees/{userId}/offline/sync-rules**
   - Payload: `{ upcomingEvents: boolean, bookmarked: boolean, learningPaths: boolean, trending: boolean, interests: boolean }`
   - Response: Updated sync rules configuration
   - Used for: Configure automatic download rules in Smart Sync

10. **GET /api/v1/attendees/{userId}/offline/sync-history**
    - Query params: limit, offset
    - Returns: Historical sync operations with timestamps, item counts, success/failure status
    - Used for: View sync history modal

### Reading & Viewing Management

11. **POST /api/v1/attendees/{userId}/offline/reading-queue/add**
    - Payload: `{ contentId, priority: number }`
    - Response: Updated queue position, estimated read time
    - Used for: Add content to offline reading queue

12. **DELETE /api/v1/attendees/{userId}/offline/reading-queue/{contentId}**
    - Response: Updated queue
    - Used for: Remove content from reading queue

13. **PUT /api/v1/attendees/{userId}/offline/content/{contentId}/progress**
    - Payload: `{ page: number, totalPages: number, lastViewed: timestamp }`
    - Response: Updated reading progress
    - Used for: Track reading progress for continue reading feature

14. **GET /api/v1/attendees/{userId}/offline/content/{contentId}/view**
    - Returns: Content file URL (IndexedDB reference), metadata, progress
    - Used for: Open content in offline viewer

### Storage & Analytics

15. **GET /api/v1/attendees/{userId}/offline/storage/analyze**
    - Returns: Storage breakdown by content type, old content suggestions, duplicate detection
    - Used for: Storage optimization recommendations

16. **POST /api/v1/attendees/{userId}/offline/storage/cleanup**
    - Payload: `{ deleteOlderThan: days, excludeBookmarked: boolean }`
    - Response: Storage freed, items deleted
    - Used for: Automated storage cleanup

---

## Navigation Map

### Primary Navigation Actions

1. **← Back button** → Navigate back to previous screen (Personal Dashboard or Content Discovery)

2. **[Settings] button** → Navigate to `Offline Settings Configuration Screen`
   - Deep settings for storage management, network preferences, content retention policies

3. **[Download More] button** → Navigate to `Content Discovery Screen`
   - Pre-filtered to show downloadable content
   - Includes download badges and size indicators

4. **Individual content [Open] button** → Navigate to `Offline Content Viewer Screen`
   - Launches offline-capable viewer
   - Loads content from IndexedDB/local storage
   - Tracks reading progress

5. **Individual content [Update] button** → Triggers in-place update
   - Shows progress overlay
   - Updates content without navigation
   - Displays success toast notification

6. **Individual content [Download Video] button** → Triggers component download
   - Shows download progress modal
   - Updates item card when complete
   - No navigation

7. **Bundle [Browse] button** → Navigate to `Bundle Content List Screen`
   - Shows all presentations in bundle
   - Allows selective viewing/deletion
   - Displays individual item statuses

8. **Bundle [Update All] button** → Triggers bulk update
   - Shows batch progress modal
   - Updates all bundle items
   - No navigation

9. **[Sync Now] button** → Triggers immediate sync
   - Shows sync progress modal with item list
   - Real-time progress via WebSocket
   - Success notification when complete
   - No navigation

10. **[Configure Rules] button** → Navigate to `Smart Sync Rules Configuration Screen`
    - Advanced rule builder for automatic downloads
    - Category selection and filters
    - Preview of content that will be downloaded

11. **[View Sync History] button** → Navigate to `Sync History Screen`
    - Timeline of sync operations
    - Success/failure details
    - Storage impact of each sync

12. **[Continue Reading →] button** → Navigate to `Offline Content Viewer Screen`
    - Opens content at saved page/position
    - Restores reading context
    - Updates last viewed timestamp

13. **Reading queue item click** → Navigate to `Offline Content Viewer Screen`
    - Opens content from beginning
    - Marks as started in queue
    - Updates reading statistics

14. **[Export to Device] button** → Triggers export flow
    - Shows export options modal
    - Generates downloadable archive
    - Provides download link when ready

15. **[Delete] button (individual content)** → Triggers confirmation modal
    - Confirms deletion with storage info
    - Removes from IndexedDB/local storage
    - Updates storage metrics
    - No screen navigation

16. **[Delete All] button** → Triggers confirmation modal
    - Warns about permanent deletion
    - Shows total storage to be freed
    - Confirms with secondary button
    - Refreshes screen content after deletion

### Secondary Navigation (Auto-Navigation)

17. **Auto-navigation after successful download** → Toast notification only
    - Content added to downloaded list
    - Updates storage metrics
    - No screen change

18. **Auto-navigation after sync completion** → Toast notification with summary
    - Shows items synced, storage used
    - Updates last sync timestamp
    - No screen change

19. **Auto-navigation when storage full** → Navigate to `Storage Management Screen`
    - Shows storage breakdown
    - Suggests content to delete
    - Offers cleanup options

20. **Auto-navigation on download failure** → Shows error modal
    - Displays failure reason (network, storage, permissions)
    - Offers retry or dismiss options
    - No screen change unless navigating to settings

### Event-Driven Navigation

21. **Connection status change (offline → online)** → Updates UI state
    - Shows sync availability
    - Enables update buttons
    - Displays sync prompt
    - No screen navigation

22. **Connection status change (online → offline)** → Updates UI state
    - Hides update/sync buttons
    - Shows offline mode indicator
    - No screen navigation

23. **Low storage warning trigger** → Shows storage warning banner
    - Offers navigation to Storage Management
    - Suggests content cleanup
    - Provides one-tap cleanup option

24. **Download progress update (WebSocket)** → Updates progress bars in real-time
    - Percentage updates
    - Speed indicators
    - Time remaining estimates
    - No screen navigation

25. **New content available notification** → Shows notification badge
    - Indicates items available for download
    - Links to Download More screen
    - Optional auto-download based on rules

26. **Scheduled sync trigger** → Background operation
    - Executes sync based on schedule
    - Shows notification when complete
    - No screen navigation unless app is active

27. **Event date approaching (auto-download trigger)** → Background download initiation
    - Downloads content based on Smart Sync rules
    - Shows notification when downloads start
    - Updates screen if active

28. **Content version update available** → Shows update badge on content cards
    - Indicates newer version available
    - Offers one-tap update
    - Shows what changed in tooltip

29. **Reading progress milestone** → Updates continue reading section
    - Saves checkpoint
    - Updates progress indicator
    - No screen navigation

30. **Export generation complete** → Shows notification with download link
    - Offers direct download
    - Option to share via system share sheet
    - No screen navigation

---
