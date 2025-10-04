# Content Viewer Page

## Header Information

**Story:** Epic 5, Story 5.1 - Historical Content Search / Story 1.18 - Basic Event Display & Archive
**Screen:** Content Viewer Page (Universal Content Viewer)
**User Role:** Attendee, Speaker, Organizer, Partner, Public (role-dependent features)
**Related FR:** FR11 (Complete event archive with presentation downloads), FR13 (Content discovery - basic version), FR14 (Personal engagement with content bookmarking and downloads)

---

## Visual Wireframe

```
┌───────────────────────────────────────────────────────────────────────────┐
│ ✕ Close                                                    [🔍] [⬇] [🔖]  │
├───────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  ┌─── CONTENT HEADER ────────────────────────────────────────────────────┐│
│  │                                                                        ││
│  │  Kubernetes Security Best Practices                                   ││
│  │  Thomas Weber • UBS • Spring 2024 Event • March 15, 2024              ││
│  │                                                                        ││
│  │  ⭐ 4.8/5 (234 ratings)  •  👁️ 1,247 views  •  ⬇ 523 downloads        ││
│  │                                                                        ││
│  │  Tags: #kubernetes #security #compliance #fintech #devops             ││
│  │                                                                        ││
│  └────────────────────────────────────────────────────────────────────────┘│
│                                                                            │
│  ┌─── CONTENT VIEWER ────────────────────────────────────────────────────┐│
│  │                                                                        ││
│  │  ┌──────────────────────────────────────────────────────────────┐   ││
│  │  │                                                               │   ││
│  │  │                                                               │   ││
│  │  │              Kubernetes Security Best Practices              │   ││
│  │  │                                                               │   ││
│  │  │                      By Thomas Weber                         │   ││
│  │  │                           UBS                                │   ││
│  │  │                                                               │   ││
│  │  │                    BATbern Spring 2024                       │   ││
│  │  │                                                               │   ││
│  │  │                                                               │   ││
│  │  │                    [PDF/SLIDE CONTENT]                       │   ││
│  │  │                                                               │   ││
│  │  │          - Implementing Zero-Trust Security               │   ││
│  │  │          - Network Policies & Pod Security                │   ││
│  │  │          - Secret Management Best Practices              │   ││
│  │  │          - Compliance in Financial Services              │   ││
│  │  │                                                               │   ││
│  │  │                                                               │   ││
│  │  │                                                               │   ││
│  │  │                                                               │   ││
│  │  └──────────────────────────────────────────────────────────────┘   ││
│  │                                                                        ││
│  │  ┌─── VIEWER CONTROLS ──────────────────────────────────────────┐   ││
│  │  │                                                               │   ││
│  │  │  [◀ Prev]    Slide 1 of 42    [Next ▶]         [⛶ Fullscreen] │   ││
│  │  │                                                               │   ││
│  │  │  [🔍-] [🔍+] [↻ Rotate] [🖨 Print] [💬 Comments (12)]          │   ││
│  │  │                                                               │   ││
│  │  └───────────────────────────────────────────────────────────────┘   ││
│  │                                                                        ││
│  └────────────────────────────────────────────────────────────────────────┘│
│                                                                            │
│  ┌─── CONTENT DETAILS ───────────────────────────────────────────────────┐│
│  │                                                                        ││
│  │  📝 Abstract                                                           ││
│  │  ┌──────────────────────────────────────────────────────────────┐   ││
│  │  │ Implementing zero-trust security in Kubernetes environments   │   ││
│  │  │ with practical examples from Swiss financial sector           │   ││
│  │  │ compliance requirements. Learn how to secure your clusters    │   ││
│  │  │ with network policies, pod security standards, secret         │   ││
│  │  │ management, and audit logging. Includes real-world case       │   ││
│  │  │ studies from banking infrastructure deployments.              │   ││
│  │  └──────────────────────────────────────────────────────────────┘   ││
│  │                                                                        ││
│  │  👤 Speaker                                                            ││
│  │  ┌──────────────────────────────────────────────────────────────┐   ││
│  │  │ 📷 [Photo]  Thomas Weber                                      │   ││
│  │  │             Senior Cloud Architect, UBS                       │   ││
│  │  │             Expertise: Kubernetes, Security, FinTech          │   ││
│  │  │             [View Full Profile →]                             │   ││
│  │  └──────────────────────────────────────────────────────────────┘   ││
│  │                                                                        ││
│  │  🎯 Event                                                              ││
│  │  ┌──────────────────────────────────────────────────────────────┐   ││
│  │  │ BATbern Spring 2024 • March 15, 2024                          │   ││
│  │  │ Venue: Technopark Zurich                                      │   ││
│  │  │ Session: Cloud Native Security Track                          │   ││
│  │  │ [View Event Details →]                                        │   ││
│  │  └──────────────────────────────────────────────────────────────┘   ││
│  │                                                                        ││
│  │  📎 Additional Materials                                               ││
│  │  ┌──────────────────────────────────────────────────────────────┐   ││
│  │  │ 📄 Presentation Slides (PDF) • 2.3 MB        [Download]       │   ││
│  │  │ 📹 Video Recording (MP4) • 145 MB            [Watch] [⬇]      │   ││
│  │  │ 📝 Speaker Notes (PDF) • 486 KB              [Download]       │   ││
│  │  │ 💻 Code Examples (ZIP) • 1.2 MB              [Download]       │   ││
│  │  └──────────────────────────────────────────────────────────────┘   ││
│  │                                                                        ││
│  └────────────────────────────────────────────────────────────────────────┘│
│                                                                            │
│  ┌─── ENGAGEMENT ────────────────────────────────────────────────────────┐│
│  │                                                                        ││
│  │  ⭐ Rate This Content                                                  ││
│  │  ┌──────────────────────────────────────────────────────────────┐   ││
│  │  │ ☆ ☆ ☆ ☆ ☆  Rate your experience (234 ratings, avg 4.8/5)     │   ││
│  │  └──────────────────────────────────────────────────────────────┘   ││
│  │                                                                        ││
│  │  💬 Comments & Reviews (12)                                            ││
│  │  ┌──────────────────────────────────────────────────────────────┐   ││
│  │  │ 👤 Maria Schmidt • 2 weeks ago                    ⭐⭐⭐⭐⭐    │   ││
│  │  │    Excellent practical examples from real banking use cases.  │   ││
│  │  │    The secret management section was particularly valuable.   │   ││
│  │  │    [👍 12] [Reply]                                            │   ││
│  │  │                                                               │   ││
│  │  │ 👤 John Chen • 1 month ago                       ⭐⭐⭐⭐⭐    │   ││
│  │  │    Best K8s security presentation I've seen. Clear and        │   ││
│  │  │    actionable. Already implementing in our environment.       │   ││
│  │  │    [👍 8] [Reply]                                             │   ││
│  │  │                                                               │   ││
│  │  │ [Load More Comments...]      [Add Your Review]               │   ││
│  │  └──────────────────────────────────────────────────────────────┘   ││
│  │                                                                        ││
│  └────────────────────────────────────────────────────────────────────────┘│
│                                                                            │
│  ┌─── RELATED CONTENT ───────────────────────────────────────────────────┐│
│  │                                                                        ││
│  │  You might also like:                                                  ││
│  │                                                                        ││
│  │  ┌────────────┬────────────┬────────────┬────────────┐              ││
│  │  │ 📊 [Card]  │ 📊 [Card]  │ 📊 [Card]  │ 📊 [Card]  │              ││
│  │  │            │            │            │            │              ││
│  │  │ Container  │ K8s RBAC   │ Security   │ DevSecOps  │              ││
│  │  │ Security   │ Deep Dive  │ Audit Log  │ Pipeline   │              ││
│  │  │ Deep Dive  │            │ Analysis   │ Tools      │              ││
│  │  │ Sara Kim   │ Alex Müller│ Lisa Wang  │ David Ross │              ││
│  │  │ 2023       │ 2024       │ 2023       │ 2024       │              ││
│  │  │ ⭐ 4.9/5   │ ⭐ 4.7/5   │ ⭐ 4.6/5   │ ⭐ 4.8/5   │              ││
│  │  │            │            │            │            │              ││
│  │  │ [View]     │ [View]     │ [View]     │ [View]     │              ││
│  │  └────────────┴────────────┴────────────┴────────────┘              ││
│  │                                                                        ││
│  │  [View All Related Content →]                                         ││
│  │                                                                        ││
│  └────────────────────────────────────────────────────────────────────────┘│
│                                                                            │
└───────────────────────────────────────────────────────────────────────────┘
```

---

## Key Interactive Elements

### Header Actions
- **[✕ Close]**: Close viewer, return to previous screen (Content Discovery, Personal Dashboard, Event Details)
- **[🔍 Fullscreen]**: Toggle fullscreen mode for immersive viewing
- **[⬇ Download]**: Download all materials as ZIP or individual file
- **[🔖 Save/Bookmark]**: Add to personal library (heart icon, toggles between filled/outline)

### Content Viewer
- **PDF Viewer**: Embedded PDF.js viewer for presentations and documents
  - Zoom controls (zoom in/out, fit to width, fit to page)
  - Page navigation (prev/next, jump to page, thumbnail sidebar)
  - Rotation controls for landscape/portrait
  - Text selection and copy (if allowed)
- **Video Player**: Embedded HTML5 video player for recordings
  - Play/pause, volume, scrubber timeline
  - Playback speed controls (0.5x, 1x, 1.25x, 1.5x, 2x)
  - Fullscreen mode
  - Picture-in-picture
  - Subtitles/captions (if available)
- **Slide Viewer**: Custom slide viewer for PPTX (converted to images)
  - Auto-advance option
  - Keyboard navigation (arrow keys)
  - Touch swipe gestures (mobile)

### Viewer Controls
- **[◀ Prev] / [Next ▶]**: Navigate between slides/pages
- **Slide Counter**: "Slide 1 of 42" with jump-to functionality
- **[⛶ Fullscreen]**: Enter/exit fullscreen mode
- **[🔍-] [🔍+]**: Zoom out/in controls
- **[↻ Rotate]**: Rotate content 90 degrees clockwise
- **[🖨 Print]**: Print current page or entire document
- **[💬 Comments]**: Toggle comments sidebar, show comment count

### Rating & Reviews
- **Star Rating**: Click stars to rate (1-5 stars), updates in real-time
- **Comment Input**: "Add Your Review" opens text area with submit button
- **Comment Actions**: [👍 Like], [Reply] for threaded discussions
- **Load More**: Paginated comment loading

### Related Content Cards
- **Content Cards**: Thumbnail, title, speaker, event date, rating
- **[View] Button**: Opens content in viewer (replaces current content)
- **Horizontal Scroll**: Swipe or arrow navigation for more suggestions

---

## Functional Requirements Met

- **FR11 (Event Archive)**: Complete archive access with presentation downloads, speaker profiles, and photo galleries via CDN
- **FR13 (Content Discovery - Basic)**: Related content suggestions based on tags and topic similarity (non-AI)
- **FR14 (Personal Engagement)**: Content bookmarking, presentation downloads, rating/review system for community engagement
- **FR15 (Mobile-Optimized)**: Responsive content viewer with offline capability (PWA), touch gestures, mobile-friendly controls
- **FR16 (Community Features - Basic)**: Content ratings, social sharing (links), curated related content

---

## User Interactions

### Opening Content
1. User clicks **[View]** on content card from Content Discovery or Personal Dashboard
2. System checks content type (PDF, video, PPTX, document)
3. Loading screen displays with progress indicator
4. APIs called in parallel:
   - `GET /api/v1/content/{contentId}` - Content metadata
   - `GET /api/v1/content/{contentId}/download-url` - CDN URL
   - `GET /api/v1/content/{contentId}/related` - Related content
   - `GET /api/v1/content/{contentId}/reviews` - Comments/ratings
   - `POST /api/v1/content/{contentId}/view` - Track view (async)
5. Content Viewer Page opens with content loaded
6. If PDF/PPTX: PDF.js renders first page
7. If video: Video player loads with poster frame
8. Viewer controls appear, metadata displayed below

### Viewing Content
1. **Navigation**:
   - User clicks **[Next ▶]** or uses keyboard arrow keys → Next slide/page loads
   - User clicks **[◀ Prev]** → Previous slide/page loads
   - User clicks page number → Jump-to-page modal opens, enter page number
   - Auto-advance mode: Slides advance every N seconds (configurable)
2. **Zooming**:
   - User clicks **[🔍+]** → Content zooms in (25% increments, max 400%)
   - User clicks **[🔍-]** → Content zooms out (25% increments, min 25%)
   - User double-clicks content → Toggle fit-to-width / fit-to-page
   - User uses mouse wheel → Zoom in/out (with Ctrl/Cmd modifier)
3. **Fullscreen**:
   - User clicks **[⛶ Fullscreen]** or presses F11 → Enter fullscreen
   - Viewer controls auto-hide after 3 seconds of inactivity
   - Mouse movement or touch → Controls reappear
   - User presses Esc or clicks exit fullscreen → Return to normal view

### Downloading Content
1. User clicks **[⬇ Download]** button in header
2. If single file:
   - Direct download initiated (presigned S3 URL)
   - Download progress shows in browser status bar
   - Success toast: "Download started: Kubernetes_Security.pdf"
3. If multiple materials:
   - Modal opens: "Download Options"
     - ☑ Presentation Slides (2.3 MB)
     - ☑ Video Recording (145 MB)
     - ☑ Speaker Notes (486 KB)
     - ☐ Code Examples (1.2 MB)
   - Total size displayed (e.g., "148.6 MB selected")
   - **[Download Selected]** → Generates ZIP archive, download link
   - **[Download All]** → All materials included
4. Download tracking API called: `POST /api/v1/content/{contentId}/download`
5. Download counter increments in real-time

### Bookmarking Content
1. User clicks **[🔖 Save]** button in header
2. If not authenticated:
   - Login modal appears: "Sign in to save content to your library"
   - After login: Bookmark action completes automatically
3. If authenticated:
   - Bookmark API called: `POST /api/v1/users/{userId}/bookmarks`
   - Success: Icon changes to filled heart, toast "Saved to your library"
   - If already bookmarked: Remove bookmark, icon outline, toast "Removed from library"
4. Optional: "Add to Collection" modal
   - Select existing collection dropdown
   - Or create new collection: [+ New Collection]
   - Input collection name, description
   - **[Save]** → Content added to collection

### Rating Content
1. User hovers over star rating → Stars highlight up to hover position
2. User clicks 4th star → 4-star rating submitted
3. API called: `POST /api/v1/content/{contentId}/ratings`
4. Rating saved, average rating updates immediately
5. Success toast: "Thank you for rating! Your rating: ⭐⭐⭐⭐"
6. User's rating persists (shows filled stars on reload)
7. User can change rating by clicking different star count

### Adding Comments/Reviews
1. User clicks **[Add Your Review]** button
2. Comment editor expands:
   - Text area with formatting toolbar (bold, italic, bullets, links)
   - Character counter (max 2000 chars)
   - Optional: Attach screenshot or image
   - **[Cancel]** / **[Post Review]** buttons
3. User writes review, clicks **[Post Review]**
4. If not authenticated: Login prompt
5. If authenticated:
   - Validation: Minimum 20 characters
   - API called: `POST /api/v1/content/{contentId}/reviews`
   - Success: Comment appears at top of list
   - Comment marked "Just now" with user avatar
   - Success toast: "Review posted successfully"
6. New comment count updates (12 → 13 comments)

### Interacting with Comments
1. User clicks **[👍 Like]** on comment → Like count increments, icon fills
2. User clicks **[Reply]** → Nested comment editor appears
   - Indented reply text area
   - @mentions original commenter
   - **[Cancel]** / **[Post Reply]** buttons
3. User posts reply → Appears as threaded comment below original
4. User clicks **[Load More Comments...]** → Next 10 comments load (pagination)

### Viewing Related Content
1. Related content cards displayed based on:
   - Same tags (e.g., #kubernetes, #security)
   - Same topic category
   - Same speaker (other presentations)
   - Same event (other sessions)
2. User clicks **[View]** on related content card
3. Current content replaced with selected content (smooth transition)
4. URL updates, browser history entry added
5. Previous content accessible via browser back button
6. Analytics tracked: "Related content click"

### Printing Content
1. User clicks **[🖨 Print]** button
2. Print modal opens:
   - Options: Current page only / All pages / Page range
   - Include comments: Yes / No
   - Page orientation: Portrait / Landscape
   - **[Cancel]** / **[Print]** buttons
3. User configures options, clicks **[Print]**
4. Browser print dialog opens with configured options
5. User prints or saves as PDF

### Closing Viewer
1. User clicks **[✕ Close]** button or presses Esc
2. If on slide 5+ and spent >2 minutes viewing:
   - "Save progress" prompt: "You were on slide 5. Save bookmark?"
     - **[Don't Save]** → Close immediately
     - **[Save Bookmark]** → Save position, then close
3. Return to previous screen (referrer):
   - From Content Discovery → Content Discovery (search results preserved)
   - From Personal Dashboard → Personal Dashboard
   - From Event Details → Event Details
4. Analytics tracked: `POST /api/v1/content/{contentId}/close` with viewing duration, slides viewed

---

## Technical Notes

### Frontend Implementation
- **Framework**: React 18.2 with TypeScript, Material-UI Modal component
- **PDF Rendering**: PDF.js library for client-side PDF rendering
- **Video Player**: HTML5 video element with custom controls, react-player for advanced features
- **Slide Viewer**: Custom React component with image preloading for smooth transitions
- **State Management**: React Query for content data, Zustand for viewer state (page number, zoom level, fullscreen)
- **Keyboard Shortcuts**:
  - Arrow Left/Right: Previous/Next slide
  - Arrow Up/Down: Scroll content
  - Ctrl/Cmd + Plus/Minus: Zoom in/out
  - Ctrl/Cmd + 0: Reset zoom to 100%
  - F11: Toggle fullscreen
  - Esc: Exit fullscreen or close viewer
  - Home/End: First/Last slide

### Content Delivery
- **CDN**: CloudFront CDN for global content delivery, low-latency access
- **Presigned URLs**: S3 presigned URLs for secure downloads (1-hour expiration)
- **Streaming**: HLS streaming for videos (adaptive bitrate)
- **Caching Strategy**:
  - Content metadata: 15 min cache (React Query)
  - CDN URLs: 5 min cache (regenerated frequently for security)
  - Related content: 30 min cache
  - Comments: 2 min cache, invalidated on new comment
- **Image Optimization**: Thumbnails served via CloudFront with image resizing (multiple sizes for responsive images)

### PDF Viewer Features
- **Text Layer**: Searchable text overlay (Ctrl+F to search within PDF)
- **Annotations**: Read-only support for PDF annotations (highlights, notes)
- **Lazy Loading**: Pages loaded on-demand (viewport + 2 pages buffer)
- **Memory Management**: Unload off-screen pages to prevent memory leaks
- **Print Optimization**: High-quality rendering for print

### Video Player Features
- **Adaptive Streaming**: HLS.js for adaptive bitrate streaming
- **Quality Selector**: Manual quality selection (1080p, 720p, 480p, 360p)
- **Playback Resume**: Resume from last viewed position (saved in localStorage)
- **Keyboard Shortcuts**:
  - Space: Play/pause
  - F: Fullscreen
  - M: Mute/unmute
  - Arrow Left/Right: Seek backward/forward 10s
  - Arrow Up/Down: Volume up/down
  - 0-9: Jump to 0%, 10%, ..., 90% of video
- **Accessibility**: Full keyboard control, ARIA labels, captions support

### Performance Optimization
- **Code Splitting**: PDF.js and video player loaded lazily (only when needed)
- **Preloading**: Next/previous slides preloaded in background
- **Thumbnail Generation**: Server-side thumbnail generation for PPTX slides
- **Progressive Loading**: Show low-res preview while high-res loads
- **Intersection Observer**: Lazy load related content cards when scrolled into view

### Analytics Tracking
- **View Tracking**: Duration, slides viewed, completion rate
- **Engagement Metrics**: Zoom usage, fullscreen usage, print actions
- **Download Tracking**: Individual file downloads, ZIP downloads
- **Interaction Events**: Comments posted, ratings given, related content clicks
- **Heatmap Data**: Which slides get the most time/attention (for organizers)

---

## API Requirements

### Initial Page Load APIs

1. **GET /api/v1/content/{contentId}**
   - **Authorization**: Optional (public content accessible without auth, private requires auth)
   - **Path Params**: `contentId` (UUID)
   - **Query Params**: `includeRelated=true` (optional, default: false)
   - **Returns**: Complete content metadata
     ```json
     {
       "id": "content-123",
       "title": "Kubernetes Security Best Practices",
       "description": "Implementing zero-trust security in Kubernetes environments...",
       "contentType": "presentation",
       "fileId": "file-456",
       "fileType": "application/pdf",
       "fileSizeBytes": 2400000,
       "pageCount": 42,
       "duration": null,
       "eventId": "event-789",
       "eventTitle": "BATbern Spring 2024",
       "eventDate": "2024-03-15T14:00:00Z",
       "sessionTitle": "Cloud Native Security Track",
       "speakerId": "speaker-321",
       "speakerName": "Thomas Weber",
       "speakerTitle": "Senior Cloud Architect",
       "companyId": "company-654",
       "companyName": "UBS",
       "thumbnailUrl": "https://d3tsrt2aaweqwh.cloudfront.net/thumbnails/content-123.jpg",
       "previewUrl": "https://d3tsrt2aaweqwh.cloudfront.net/previews/content-123.pdf",
       "tags": ["kubernetes", "security", "compliance", "fintech", "devops"],
       "viewCount": 1247,
       "downloadCount": 523,
       "averageRating": 4.8,
       "ratingCount": 234,
       "commentCount": 12,
       "createdAt": "2024-03-15T18:00:00Z",
       "updatedAt": "2024-03-16T09:30:00Z",
       "isPublic": true,
       "allowDownload": true,
       "allowComments": true
     }
     ```
   - **Used for**: Populate content header, metadata sections, viewer configuration

2. **GET /api/v1/content/{contentId}/download-url**
   - **Authorization**: Optional (public content accessible, private requires auth)
   - **Path Params**: `contentId` (UUID)
   - **Query Params**:
     - `materialType` (optional): "presentation" | "video" | "notes" | "code"
     - `expiresIn` (optional): Expiration in seconds (default: 3600)
   - **Returns**: Presigned download URL
     ```json
     {
       "contentId": "content-123",
       "downloadUrl": "https://batbern-content.s3.eu-central-1.amazonaws.com/...",
       "cdnUrl": "https://d3tsrt2aaweqwh.cloudfront.net/content/...",
       "expiresAt": "2024-04-01T15:30:00Z",
       "fileType": "application/pdf",
       "fileSizeBytes": 2400000,
       "fileName": "Kubernetes_Security_Best_Practices.pdf"
     }
     ```
   - **Used for**: Load content in viewer, enable download button

3. **GET /api/v1/content/{contentId}/materials**
   - **Authorization**: Optional (public content accessible, private requires auth)
   - **Path Params**: `contentId` (UUID)
   - **Returns**: Additional materials associated with content
     ```json
     {
       "materials": [
         {
           "id": "material-1",
           "type": "presentation",
           "name": "Presentation Slides",
           "fileId": "file-456",
           "fileName": "K8s_Security.pdf",
           "fileSizeBytes": 2400000,
           "downloadUrl": "https://...",
           "cdnUrl": "https://..."
         },
         {
           "id": "material-2",
           "type": "video",
           "name": "Video Recording",
           "fileId": "file-457",
           "fileName": "K8s_Security_Recording.mp4",
           "fileSizeBytes": 152000000,
           "durationSeconds": 2700,
           "streamUrl": "https://.../playlist.m3u8",
           "cdnUrl": "https://..."
         },
         {
           "id": "material-3",
           "type": "notes",
           "name": "Speaker Notes",
           "fileId": "file-458",
           "fileName": "K8s_Security_Notes.pdf",
           "fileSizeBytes": 486000,
           "downloadUrl": "https://...",
           "cdnUrl": "https://..."
         },
         {
           "id": "material-4",
           "type": "code",
           "name": "Code Examples",
           "fileId": "file-459",
           "fileName": "K8s_Security_Examples.zip",
           "fileSizeBytes": 1200000,
           "downloadUrl": "https://...",
           "cdnUrl": "https://..."
         }
       ],
       "totalSizeBytes": 156086000
     }
     ```
   - **Used for**: Populate "Additional Materials" section

4. **GET /api/v1/content/{contentId}/related**
   - **Authorization**: Optional
   - **Path Params**: `contentId` (UUID)
   - **Query Params**:
     - `limit` (optional): Max results (default: 8, max: 20)
     - `algorithm` (optional): "tags" | "speaker" | "event" | "topic" (default: "tags")
   - **Returns**: Related content recommendations
     ```json
     {
       "relatedContent": [
         {
           "id": "content-456",
           "title": "Container Security Deep Dive",
           "speakerName": "Sara Kim",
           "eventDate": "2023-09-20T14:00:00Z",
           "thumbnailUrl": "https://...",
           "averageRating": 4.9,
           "ratingCount": 342,
           "viewCount": 2103,
           "tags": ["docker", "security", "cicd", "devops"],
           "matchScore": 0.87
         },
         // ... more related content
       ],
       "totalCount": 47,
       "algorithm": "tags",
       "matchCriteria": ["kubernetes", "security"]
     }
     ```
   - **Used for**: Populate "Related Content" carousel

5. **GET /api/v1/content/{contentId}/reviews**
   - **Authorization**: Optional (public reviews visible to all)
   - **Path Params**: `contentId` (UUID)
   - **Query Params**:
     - `limit` (optional): Reviews per page (default: 10, max: 50)
     - `offset` (optional): Pagination offset (default: 0)
     - `sort` (optional): "recent" | "helpful" | "rating" (default: "recent")
   - **Returns**: Comments and reviews
     ```json
     {
       "reviews": [
         {
           "id": "review-789",
           "userId": "user-123",
           "userName": "Maria Schmidt",
           "userAvatar": "https://.../avatars/user-123.jpg",
           "rating": 5,
           "comment": "Excellent practical examples from real banking use cases...",
           "createdAt": "2024-03-20T10:30:00Z",
           "updatedAt": "2024-03-20T10:30:00Z",
           "helpfulCount": 12,
           "replies": [
             {
               "id": "reply-101",
               "userId": "user-456",
               "userName": "Thomas Weber",
               "comment": "Thank you Maria! Glad it was helpful.",
               "createdAt": "2024-03-21T08:15:00Z",
               "isSpeaker": true
             }
           ]
         },
         // ... more reviews
       ],
       "pagination": {
         "total": 234,
         "limit": 10,
         "offset": 0,
         "hasMore": true
       },
       "summary": {
         "averageRating": 4.8,
         "totalReviews": 234,
         "ratingDistribution": {
           "5": 180,
           "4": 40,
           "3": 10,
           "2": 3,
           "1": 1
         }
       }
     }
     ```
   - **Used for**: Populate comments section, display rating distribution

---

### User Action APIs

1. **POST /api/v1/content/{contentId}/view**
   - **Triggered by**: Content viewer opens (async, doesn't block UI)
   - **Authorization**: Optional (tracks views for authenticated and anonymous users)
   - **Path Params**: `contentId` (UUID)
   - **Payload**:
     ```json
     {
       "referrer": "content_discovery",
       "userAgent": "Mozilla/5.0...",
       "viewDurationSeconds": 0,
       "slidesViewed": [1],
       "sessionId": "session-abc123"
     }
     ```
   - **Response**:
     ```json
     {
       "viewId": "view-987",
       "viewCount": 1248,
       "tracked": true
     }
     ```
   - **Used for**: Track view count, analytics (which content is popular)

2. **PUT /api/v1/content/{contentId}/view/{viewId}**
   - **Triggered by**: Periodic updates while viewing (every 30s), on viewer close
   - **Authorization**: Optional
   - **Path Params**: `contentId` (UUID), `viewId` (UUID from initial view tracking)
   - **Payload**:
     ```json
     {
       "viewDurationSeconds": 420,
       "slidesViewed": [1, 2, 3, 5, 7, 8, 10, 12, 15],
       "completionPercentage": 0.36,
       "interactions": {
         "zoomed": 3,
         "fullscreen": 1,
         "printed": 0
       }
     }
     ```
   - **Response**:
     ```json
     {
       "viewId": "view-987",
       "updated": true
     }
     ```
   - **Used for**: Detailed analytics, resume playback position, engagement metrics

3. **POST /api/v1/users/{userId}/bookmarks**
   - **Triggered by**: **[🔖 Save]** button click
   - **Authorization**: Requires authenticated user
   - **Path Params**: `userId` (UUID)
   - **Payload**:
     ```json
     {
       "contentId": "content-123",
       "collectionId": "collection-456",
       "notes": "Great resource for K8s security implementation",
       "currentSlide": 5,
       "tags": ["reference", "security", "to-review"]
     }
     ```
   - **Response**:
     ```json
     {
       "bookmarkId": "bookmark-789",
       "contentId": "content-123",
       "userId": "user-123",
       "createdAt": "2024-04-01T14:35:00Z",
       "collection": {
         "id": "collection-456",
         "name": "Security Resources"
       }
     }
     ```
   - **Used for**: Save content to personal library, create collections

4. **DELETE /api/v1/users/{userId}/bookmarks/{bookmarkId}**
   - **Triggered by**: **[🔖 Remove]** button click (if already bookmarked)
   - **Authorization**: Requires authenticated user, bookmark owner
   - **Path Params**: `userId` (UUID), `bookmarkId` (UUID)
   - **Response**:
     ```json
     {
       "bookmarkId": "bookmark-789",
       "deleted": true,
       "deletedAt": "2024-04-01T14:40:00Z"
     }
     ```
   - **Used for**: Remove bookmark from library

5. **POST /api/v1/content/{contentId}/ratings**
   - **Triggered by**: User clicks star rating (1-5 stars)
   - **Authorization**: Requires authenticated user
   - **Path Params**: `contentId` (UUID)
   - **Payload**:
     ```json
     {
       "rating": 4,
       "userId": "user-123"
     }
     ```
   - **Response**:
     ```json
     {
       "ratingId": "rating-654",
       "contentId": "content-123",
       "userId": "user-123",
       "rating": 4,
       "createdAt": "2024-04-01T14:38:00Z",
       "updatedAverageRating": 4.78,
       "updatedRatingCount": 235
     }
     ```
   - **Used for**: Submit user rating, update average rating in real-time

6. **PUT /api/v1/content/{contentId}/ratings/{ratingId}**
   - **Triggered by**: User changes existing rating (clicks different star count)
   - **Authorization**: Requires authenticated user, rating owner
   - **Path Params**: `contentId` (UUID), `ratingId` (UUID)
   - **Payload**:
     ```json
     {
       "rating": 5
     }
     ```
   - **Response**:
     ```json
     {
       "ratingId": "rating-654",
       "previousRating": 4,
       "newRating": 5,
       "updatedAt": "2024-04-01T14:42:00Z",
       "updatedAverageRating": 4.79
     }
     ```
   - **Used for**: Update existing rating

7. **POST /api/v1/content/{contentId}/reviews**
   - **Triggered by**: **[Post Review]** button in comment editor
   - **Authorization**: Requires authenticated user
   - **Path Params**: `contentId` (UUID)
   - **Payload**:
     ```json
     {
       "userId": "user-123",
       "rating": 5,
       "comment": "Excellent practical examples from real banking use cases. The secret management section was particularly valuable.",
       "parentReviewId": null
     }
     ```
   - **Validation**:
     - Comment: Required if rating not provided, 20-2000 characters
     - Rating: Optional (1-5), if provided must submit rating separately via ratings API
   - **Response**:
     ```json
     {
       "reviewId": "review-890",
       "contentId": "content-123",
       "userId": "user-123",
       "userName": "Maria Schmidt",
       "userAvatar": "https://.../avatars/user-123.jpg",
       "rating": 5,
       "comment": "Excellent practical examples...",
       "createdAt": "2024-04-01T14:45:00Z",
       "helpfulCount": 0,
       "replies": []
     }
     ```
   - **Used for**: Post new review/comment, appears immediately in comments section

8. **POST /api/v1/content/{contentId}/reviews/{reviewId}/replies**
   - **Triggered by**: **[Reply]** button in comment, then **[Post Reply]**
   - **Authorization**: Requires authenticated user
   - **Path Params**: `contentId` (UUID), `reviewId` (UUID of parent review)
   - **Payload**:
     ```json
     {
       "userId": "user-456",
       "comment": "Thank you Maria! Glad it was helpful.",
       "mentionUserId": "user-123"
     }
     ```
   - **Response**:
     ```json
     {
       "replyId": "reply-102",
       "parentReviewId": "review-890",
       "userId": "user-456",
       "userName": "Thomas Weber",
       "comment": "Thank you Maria! Glad it was helpful.",
       "createdAt": "2024-04-01T15:00:00Z",
       "isSpeaker": true
     }
     ```
   - **Used for**: Post reply to existing review, creates threaded conversation

9. **POST /api/v1/content/{contentId}/reviews/{reviewId}/helpful**
   - **Triggered by**: **[👍 Like]** button on review
   - **Authorization**: Requires authenticated user
   - **Path Params**: `contentId` (UUID), `reviewId` (UUID)
   - **Payload**:
     ```json
     {
       "userId": "user-789",
       "helpful": true
     }
     ```
   - **Response**:
     ```json
     {
       "reviewId": "review-890",
       "userId": "user-789",
       "helpful": true,
       "updatedHelpfulCount": 13
     }
     ```
   - **Used for**: Mark review as helpful, increases helpful count

10. **POST /api/v1/content/{contentId}/download**
    - **Triggered by**: **[⬇ Download]** button click or download link
    - **Authorization**: Optional (public content accessible, private requires auth)
    - **Path Params**: `contentId` (UUID)
    - **Payload**:
      ```json
      {
        "materialIds": ["material-1", "material-3"],
        "downloadType": "zip",
        "referrer": "content_viewer"
      }
      ```
    - **Response** (for ZIP):
      ```json
      {
        "downloadId": "download-345",
        "downloadUrl": "https://batbern-content.s3.../downloads/bundle-abc123.zip",
        "expiresAt": "2024-04-01T16:00:00Z",
        "fileSizeBytes": 3886000,
        "materials": [
          {
            "materialId": "material-1",
            "fileName": "K8s_Security.pdf"
          },
          {
            "materialId": "material-3",
            "fileName": "K8s_Security_Notes.pdf"
          }
        ]
      }
      ```
    - **Response** (for single file):
      ```json
      {
        "downloadId": "download-346",
        "downloadUrl": "https://batbern-content.s3.../K8s_Security.pdf",
        "expiresAt": "2024-04-01T16:00:00Z",
        "fileSizeBytes": 2400000,
        "fileName": "K8s_Security.pdf"
      }
      ```
    - **Used for**: Track downloads, generate download URLs, analytics

11. **POST /api/v1/content/{contentId}/share**
    - **Triggered by**: Share action (copy link, email, social)
    - **Authorization**: Optional
    - **Path Params**: `contentId` (UUID)
    - **Payload**:
      ```json
      {
        "method": "link",
        "recipients": [],
        "message": "Check out this great K8s security presentation!"
      }
      ```
    - **Response**:
      ```json
      {
        "shareId": "share-567",
        "shareUrl": "https://batbern.ch/content/content-123?ref=share-567",
        "shortUrl": "https://bat.link/k8s-sec",
        "sharedAt": "2024-04-01T14:50:00Z",
        "method": "link"
      }
      ```
    - **Used for**: Generate shareable links, track shares, social media integration

---

## Navigation Map

### Primary Navigation Actions

1. **✕ Close button / Esc key** → Return to previous screen
   - **Target**: Referrer screen (Content Discovery, Personal Dashboard, Event Details)
   - **Context**: None (returns to exact state before opening viewer)
   - **Behavior**:
     - If viewing duration >2 min and on slide 5+: "Save progress?" prompt
     - On confirm: Save bookmark with current position
     - On cancel: Close without saving
   - **Analytics**: Track close action with viewing stats

2. **Content title or event link** → Navigate to Event Details
   - **Target**: Event Details Page (if wireframe exists)
   - **Context**: `eventId` from content metadata
   - **Behavior**: Opens event in new context, viewer can remain open (if modal) or navigate (if full page)

3. **Speaker name link or [View Full Profile →]** → Navigate to Speaker Profile
   - **Target**: Speaker Profile Detail View (`story-7.1-speaker-profile-detail-view.md`)
   - **Context**: `speakerId` from content metadata
   - **Behavior**: Opens speaker profile (new page or modal depending on implementation)

---

### Secondary Navigation (Data Interactions)

1. **[View] button on related content card** → Load different content
   - **Action**: Replace current content with selected related content
   - **Behavior**:
     - Smooth transition (fade out → load → fade in)
     - URL updates to new contentId
     - Browser history entry added (back button works)
     - Viewer state resets (page 1, zoom 100%, fullscreen off)
   - **Analytics**: Track "related content click"

2. **[View All Related Content →] link** → Navigate to filtered Content Discovery
   - **Target**: Content Discovery page with filters pre-applied
   - **Context**: Same tags/speaker/topic as current content
   - **Behavior**: Opens Content Discovery with search results matching current content's attributes

3. **Tag click (e.g., #kubernetes)** → Navigate to tag search
   - **Target**: Content Discovery page filtered by tag
   - **Context**: Selected tag
   - **Behavior**: Opens search results for that tag, viewer closes (or remains if modal)

4. **Slide thumbnail click** (in sidebar, if implemented) → Jump to slide
   - **Action**: Navigate to selected slide
   - **Behavior**: Smooth scroll/transition to target slide
   - **No Navigation**: Remains in viewer

5. **Page number click** → Open jump-to-page modal
   - **Action**: Modal overlay with numeric input
   - **Input**: Enter page number (1-42)
   - **Behavior**: Navigate to entered page, modal closes
   - **No Navigation**: Remains in viewer

---

### Event-Driven Navigation

1. **Content load complete** → Display viewer
   - **Trigger**: All load APIs return successfully
   - **Behavior**: Loading spinner disappears, content appears
   - **Error Handling**: If load fails, show error state with retry option

2. **Rating submitted** → Update UI
   - **Trigger**: Rating API success response
   - **Behavior**:
     - User's stars fill to selected rating
     - Average rating updates
     - Rating count increments
     - Success toast appears
   - **No Navigation**: Remains in viewer

3. **Comment posted** → Add to comments list
   - **Trigger**: Review API success response
   - **Behavior**:
     - New comment appears at top of list
     - Comment count increments
     - Comment editor clears
     - Success toast appears
     - Scroll to new comment
   - **No Navigation**: Remains in viewer

4. **Bookmark saved** → Update bookmark icon
   - **Trigger**: Bookmark API success response
   - **Behavior**:
     - Bookmark icon changes from outline to filled
     - Success toast: "Saved to your library"
     - If collection selected, toast includes collection name
   - **No Navigation**: Remains in viewer

5. **Download started** → Browser download
   - **Trigger**: Download API returns presigned URL
   - **Behavior**:
     - Browser download initiated
     - Download counter increments
     - Success toast appears
     - File downloads in background
   - **No Navigation**: Remains in viewer

6. **Fullscreen toggled** → Enter/exit fullscreen
   - **Trigger**: Fullscreen button or F11 key
   - **Behavior**:
     - Viewer expands to full screen
     - Controls auto-hide after 3s inactivity
     - Mouse movement shows controls
     - Esc exits fullscreen
   - **No Navigation**: Remains in viewer

---

### Error States & Redirects

1. **Content not found (404)** → Show error page
   - **Condition**: Content ID invalid or content deleted
   - **Display**: "Content not found" error page
   - **Message**: "This content is no longer available or the link is incorrect."
   - **Actions**:
     - [Browse Content] → Navigate to Content Discovery
     - [Go Back] → Return to previous screen

2. **Unauthorized access (403)** → Login prompt or error
   - **Condition**: Private content requires authentication
   - **Display**: Login modal or error page
   - **Message**: "This content is private. Please log in to access."
   - **Actions**:
     - [Login] → Open login modal, return to content after auth
     - [Cancel] → Return to previous screen

3. **Content load failed (500)** → Show error state with retry
   - **Condition**: CDN unreachable, S3 error, network issue
   - **Display**: Error state in viewer area
   - **Message**: "Unable to load content. Please try again."
   - **Actions**:
     - [Retry] → Reload content
     - [Download] → Direct download as fallback
     - [Close] → Return to previous screen

4. **Rating/Comment failed** → Show error toast
   - **Condition**: API error, network issue, validation failure
   - **Display**: Error toast notification
   - **Message**: "Unable to submit rating. Please try again."
   - **Actions**:
     - Toast auto-dismisses after 5s
     - User can retry action
   - **No Navigation**: Remains in viewer

5. **Download failed** → Show error toast with retry
   - **Condition**: S3 presigned URL expired, network error
   - **Display**: Error toast notification
   - **Message**: "Download failed. Please try again."
   - **Actions**:
     - [Retry] → Regenerate presigned URL, retry download
     - Toast auto-dismisses after 10s
   - **No Navigation**: Remains in viewer

6. **Session timeout during view** → Save progress prompt
   - **Condition**: JWT token expired while viewing
   - **Display**: Session timeout modal
   - **Message**: "Your session has expired. Your viewing progress has been saved."
   - **Actions**:
     - [Login Again] → Open login, resume viewing after auth
     - [Close] → Return to previous screen
   - **Behavior**: Viewing progress saved to localStorage, restored after login

---

## Responsive Design Considerations

### Mobile Layout Changes (< 768px)

- **Full-Screen Viewer**: Content viewer takes entire viewport, header minimized
- **Collapsible Sections**: Abstract, speaker, event details collapsed by default
  - Expand on tap with smooth animation
  - Icons indicate expand/collapse state
- **Touch-Optimized Controls**:
  - Larger hit targets (min 44x44px)
  - Swipe left/right to navigate slides
  - Pinch to zoom gesture
  - Double-tap to toggle fit-to-width/fit-to-page
- **Bottom Navigation Bar**: Primary controls in fixed bottom bar
  - [◀ Prev] [Page Count] [Next ▶]
  - [⬇ Download] [🔖 Save] [⋮ More]
- **Floating Action Button**: Primary actions accessible via FAB
  - Tap FAB → Opens action menu (Download, Save, Share, Fullscreen)
- **Related Content**: Horizontal scroll carousel (one card visible at a time)
- **Comments**: Load on-demand via "View Comments" button to save initial load time

### Tablet Layout Changes (768px - 1024px)

- **Two-Column Layout**: Content viewer on left (70%), metadata on right (30%)
- **Sticky Header**: Content title and actions remain visible while scrolling metadata
- **Hybrid Controls**: Both touch and mouse/keyboard navigation supported
- **Related Content**: 2 cards per row in carousel
- **Comments**: Show top 3 initially, "Load More" for rest

### Mobile-Specific Interactions

- **Swipe Gestures**:
  - Swipe left → Next slide
  - Swipe right → Previous slide
  - Swipe down from top → Close viewer (with confirm if >2 min viewing)
  - Swipe up from bottom → Expand details panel
- **Pinch Zoom**: Native pinch-to-zoom on content
- **Share Sheet**: Native mobile share sheet for sharing content
  - Integrates with installed apps (WhatsApp, Email, etc.)
- **Offline Viewing**:
  - "Save for Offline" option downloads content to device
  - Offline indicator shows cached content
  - Works in PWA mode

### Tablet-Specific Features
- **Split View**: View related content side-by-side with main content (iPad)
- **Pencil Support**: Apple Pencil annotations on iPad (future enhancement)
- **Keyboard Shortcuts**: Full keyboard shortcut support for iPad with keyboard

---

## Accessibility Notes

- **Keyboard Navigation**:
  - Tab: Navigate through interactive elements (controls, links, buttons)
  - Arrow keys: Navigate slides, scroll content
  - Enter/Space: Activate buttons, play/pause video
  - Esc: Exit fullscreen, close viewer
  - Ctrl/Cmd shortcuts: Zoom, print, fullscreen
  - All functionality accessible via keyboard only

- **Screen Reader Support**:
  - ARIA labels on all controls: `aria-label="Next slide"`, `aria-label="Download presentation"`
  - ARIA live regions for dynamic updates:
    - `aria-live="polite"` for page changes: "Now viewing slide 5 of 42"
    - `aria-live="assertive"` for errors: "Content failed to load"
  - Semantic HTML: Proper heading hierarchy (h1 for title, h2 for sections)
  - Alt text for all images and thumbnails
  - Content description read aloud: Title, speaker, event, abstract

- **Focus Management**:
  - On viewer open: Focus moves to content viewer container
  - On modal open (comments, download): Focus moves to modal first element
  - On modal close: Focus returns to trigger element
  - Focus trap in modals (Tab cycles within modal)
  - Visible focus indicators: 3px solid outline, high contrast

- **Color & Contrast**:
  - All text meets WCAG 2.1 AA standards (4.5:1 for normal, 3:1 for large text)
  - Icons paired with text labels (not relying on color alone)
  - Rating stars: Color + shape + number for accessibility
  - Error states: Red color + icon + text message

- **Video Accessibility**:
  - Captions/subtitles for all videos (if available)
  - Audio descriptions option (future enhancement)
  - Keyboard-accessible video controls
  - ARIA labels for play, pause, volume, scrubber

- **PDF Accessibility**:
  - Accessible PDFs preserved (tagged PDFs, alt text, reading order)
  - Text-to-speech support (browser native)
  - Searchable text layer
  - High-contrast mode option

- **Reduced Motion**:
  - Respects `prefers-reduced-motion` media query
  - Disables auto-advance, smooth scrolling, transitions
  - Instant state changes instead of animations

---

## State Management

### Local Component State

- `currentPage`: Current slide/page number (1-based index)
- `totalPages`: Total number of slides/pages
- `zoomLevel`: Current zoom percentage (25%, 50%, 100%, 200%, 400%)
- `isFullscreen`: Boolean for fullscreen state
- `isPlaying`: Boolean for video playback state (if video content)
- `playbackSpeed`: Video playback speed (0.5x, 1x, 1.25x, 1.5x, 2x)
- `viewerMode`: "fit-width" | "fit-page" | "custom"
- `commentsExpanded`: Boolean for comments section visibility
- `detailsExpanded`: Boolean for details sections (mobile)

### Global State (Zustand Store)

- `currentContent`: Content metadata object (full content details)
- `userBookmarks`: Array of user's bookmarked content IDs
- `userRatings`: Object mapping contentId → user's rating
- `viewingHistory`: Array of recently viewed content
- `downloadQueue`: Array of pending downloads

### Server State (React Query)

- **Query Keys**:
  - `['content', contentId]`: Content metadata (15 min cache)
  - `['content', contentId, 'materials']`: Additional materials (15 min cache)
  - `['content', contentId, 'related']`: Related content (30 min cache)
  - `['content', contentId, 'reviews']`: Comments/reviews (2 min cache)
  - `['user', userId, 'bookmarks']`: User bookmarks (5 min cache)

- **Mutations**:
  - `submitRating`: Invalidates `['content', contentId]` query (updates avg rating)
  - `postReview`: Invalidates `['content', contentId, 'reviews']` query (adds new review)
  - `addBookmark`: Invalidates `['user', userId, 'bookmarks']` query
  - `trackView`: No invalidation (analytics only, async)

### Real-Time Updates

- **View Tracking**: Update view stats every 30 seconds (debounced)
- **Playback Position**: Save video position every 10 seconds for resume capability
- **Auto-Save Bookmark**: If viewing >5 min, auto-save bookmark with current position
- **Rating Updates**: Real-time average rating updates when users submit ratings
- **Comment Count**: Live comment count updates when new reviews posted

### Offline State (PWA)

- **Cached Content**: Service worker caches viewed content for offline access
- **Download for Offline**: Explicit download stores content in IndexedDB
- **Offline Indicator**: Banner shows "Viewing offline" when no network
- **Sync Queue**: Ratings, comments queued when offline, synced when online

---

## Form Validation Rules

### Comment/Review Submission

- **Comment Text**:
  - Required: If rating not provided (either comment or rating required)
  - Min Length: 20 characters (ensures meaningful feedback)
  - Max Length: 2000 characters
  - Validation: No spam keywords, no excessive links, profanity filter

- **Rating**:
  - Optional: Can submit comment without rating, or rating without comment
  - Range: 1-5 stars (inclusive)
  - Validation: Must be integer

### Reply Submission

- **Reply Text**:
  - Required: Cannot submit empty reply
  - Min Length: 10 characters
  - Max Length: 1000 characters
  - Validation: Same spam/profanity checks as comments

---

## Edge Cases & Error Handling

### Empty States

- **No Comments**: Display "Be the first to review this content!" with [Add Review] CTA
- **No Related Content**: Display "No related content found" with link to browse all content
- **No Additional Materials**: Hide "Additional Materials" section entirely
- **No Ratings**: Display "Not yet rated" with invitation to rate

### Loading States

- **Content Loading**: Full-screen loading spinner with progress text "Loading content..."
- **Page Transition**: Smooth fade between slides with brief loading indicator
- **Comments Loading**: Skeleton screens for comment cards
- **Related Content Loading**: Skeleton cards in carousel
- **Download Generating**: "Generating download..." spinner on button

### Error States

- **Content Load Failed**:
  - Error screen: "Unable to load content"
  - Possible reasons: Network error, content deleted, CDN issue
  - Actions: [Retry], [Download Anyway], [Report Issue], [Close]

- **Unsupported File Type**:
  - Warning: "This file type cannot be previewed in browser"
  - Offer direct download instead
  - Show file metadata (name, size, type)

- **Large File Warning**:
  - For files >50 MB: "This is a large file (145 MB). Download may take several minutes."
  - Option to proceed or cancel
  - Progress indicator during download

- **Corrupt/Invalid PDF**:
  - Error: "This PDF file is corrupted and cannot be displayed"
  - Offer download to try opening in native PDF viewer
  - Link to report issue to organizers

### Permission Issues

- **Private Content (Unauthenticated)**:
  - Blurred preview with overlay: "Login to view full content"
  - [Login] / [Create Account] buttons
  - Abstract and metadata still visible

- **Download Restricted**:
  - Download button disabled with tooltip: "Downloads disabled by speaker"
  - Alternative: "Contact speaker for access"

### Playback Issues

- **Video Playback Error**:
  - Error: "Unable to play video"
  - Possible reasons: Format not supported, encoding issue, network error
  - Actions: [Retry], [Download Video], [Report Issue]

- **Audio/Video Out of Sync**:
  - User can report issue via [Report Issue] button
  - Issue flagged for organizer review

### Concurrent Actions

- **Bookmark While Offline**:
  - Bookmark saved to localStorage
  - Synced to server when online
  - Visual indicator: "Saved offline, will sync when online"

- **Rate/Comment While Offline**:
  - Action queued in IndexedDB
  - Auto-synced when connection restored
  - User notified of pending actions

### Browser Compatibility

- **PDF.js Not Supported**:
  - Fallback: Offer direct download
  - Message: "Your browser doesn't support PDF preview. Download to view."

- **Video Format Not Supported**:
  - Fallback to HLS stream or MP4 download
  - Message: "Switching to compatible format..."

- **Fullscreen API Not Supported**:
  - Hide fullscreen button
  - Maximize in viewport instead

---

## Change Log

| Date       | Version | Description                          | Author       |
|------------|---------|--------------------------------------|--------------|
| 2025-04-01 | 1.0     | Initial wireframe creation          | Sally (UX)   |

---

## Review Notes

### Stakeholder Feedback

*To be added after stakeholder review*

### Design Iterations

*To be documented as design evolves*

### Open Questions

1. **Annotations/Notes Feature**: Should users be able to add private notes or annotations directly on slides?
   - **Current**: Comment system only (public reviews)
   - **Enhancement**: Private notes linked to specific slides, stored in user account
   - **Use Case**: Study notes, implementation reminders, personal highlights
   - **Recommendation**: Add as future enhancement (post-MVP), requires additional API endpoints and UI

2. **Offline Download Limits**: Should there be a limit on how much content users can download for offline access?
   - **Current**: No explicit limit (device storage is limiting factor)
   - **Consideration**: Storage costs, user experience with large libraries
   - **Recommendation**: Implement soft quota (e.g., 5 GB) with option to manage offline content

3. **Content Versioning**: If a speaker updates their presentation, should old versions remain accessible?
   - **Current**: Single version only (latest overwrites previous)
   - **Use Case**: Track changes, compare versions, access historical versions
   - **Recommendation**: Keep latest 3 versions, archive older versions with metadata

4. **Speaker Notifications**: Should speakers be notified when their content receives reviews/ratings?
   - **Current**: No notifications specified
   - **Enhancement**: Email digest (weekly) with review summaries
   - **Recommendation**: Add in notification preferences (Story 1.20)

5. **Content Expiration**: Should old content (>10 years) be archived or removed from discovery?
   - **Current**: All 20+ years accessible
   - **Consideration**: Storage costs vs. historical value
   - **Recommendation**: Keep all content, add "Archive" filter for old content (pre-2015)
