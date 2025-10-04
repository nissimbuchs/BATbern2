# Content Library/Repository Screen - Wireframe

**Story**: Epic 3, Story 3.3 - Speaker Material Submission / Epic 5, Story 5.1 - Content Discovery
**Screen**: Content Library/Repository Screen
**User Role**: Organizer (full access), Speaker (own content), Attendee (read-only)
**Related FR**: FR3 (Speaker Self-Service), FR18 (Historical Archive)

---

## Visual Wireframe

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back to Dashboard            Content Library                    [+ Upload Content] │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── SEARCH & FILTERS ──────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  [🔍 Search titles, speakers, topics...]                      [Advanced Search]  │ │
│  │                                                                                   │ │
│  │  Content Type: [All ▾]  Event: [All ▾]  Speaker: [All ▾]  Status: [All ▾]       │ │
│  │  Year: [All ▾]  Tags: [________]  Sort: [Recent ▾]                [Clear Filters]│ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── BULK ACTIONS ──────────────────────────────────────────────────────────────────┐ │
│  │  ☐ Select All (5 selected)    [Download Selected] [Tag] [Archive] [Delete]      │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── LIBRARY STATISTICS ────────────────────────────────────────────────────────────┐ │
│  │  Total: 1,247 files | Presentations: 892 | Videos: 156 | Docs: 199 | Storage:   │ │
│  │  45.3 GB / 200 GB                                                                 │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── CONTENT LIST ──────────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  Showing 247 items                                         [Grid ▣] [List ☰]     │ │
│  │                                                                                   │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ [☐] 📊 Presentation                               ⭐ Featured  🔥 Popular   │  │ │
│  │  │                                                                              │  │ │
│  │  │ [Thumbnail]  Kubernetes Best Practices for Production                       │  │ │
│  │  │              Dr. Peter Müller • TechCorp AG                                  │  │ │
│  │  │                                                                              │  │ │
│  │  │ Event: Spring Conference 2024                                               │  │ │
│  │  │ Type: Presentation (PDF, 15.2 MB) • Version 2 of 2                          │  │ │
│  │  │ Tags: [Kubernetes] [DevOps] [Cloud Native] [Production]                     │  │ │
│  │  │                                                                              │  │ │
│  │  │ 📈 Stats: 1,247 views • 523 downloads • 4.8★ (45 reviews)                  │  │ │
│  │  │ 📅 Uploaded: Mar 15, 2024 • Modified: Mar 18, 2024 • Status: Published     │  │ │
│  │  │                                                                              │  │ │
│  │  │ [View] [Download] [Edit Metadata] [Version History] [Analytics] [⋮ More]   │  │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ [☐] 🎥 Video Recording                                      📹 Recording     │  │ │
│  │  │                                                                              │  │ │
│  │  │ [Thumbnail]  Container Security Deep Dive                                   │  │ │
│  │  │              Sarah König • SwissBank Ltd                                     │  │ │
│  │  │                                                                              │  │ │
│  │  │ Event: Autumn Conference 2023                                               │  │ │
│  │  │ Type: Video (MP4, 342 MB, 45:32) • Version 1 of 1                           │  │ │
│  │  │ Tags: [Security] [Docker] [Containers] [CI/CD]                              │  │ │
│  │  │                                                                              │  │ │
│  │  │ 📈 Stats: 2,103 views • 891 downloads • 4.9★ (67 reviews)                  │  │ │
│  │  │ 📅 Uploaded: Oct 20, 2023 • Modified: Oct 20, 2023 • Status: Published     │  │ │
│  │  │                                                                              │  │ │
│  │  │ [View] [Download] [Edit Metadata] [Version History] [Analytics] [⋮ More]   │  │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ [☐] 📄 Handout                                           ⏳ Under Review     │  │ │
│  │  │                                                                              │  │ │
│  │  │ [Thumbnail]  Platform Engineering Workshop Materials                        │  │ │
│  │  │              Michael Weber • Digital Solutions GmbH                          │  │ │
│  │  │                                                                              │  │ │
│  │  │ Event: Spring Conference 2025 (Upcoming)                                    │  │ │
│  │  │ Type: Document (PDF, 3.8 MB) • Version 1 of 1                               │  │ │
│  │  │ Tags: [Platform Engineering] [Workshop] [Hands-on]                          │  │ │
│  │  │                                                                              │  │ │
│  │  │ 📈 Stats: Not yet published                                                 │  │ │
│  │  │ 📅 Uploaded: Jan 15, 2025 • Modified: Jan 18, 2025 • Status: Under Review  │  │ │
│  │  │                                                                              │  │ │
│  │  │ [View] [Download] [Edit Metadata] [Approve] [Reject] [⋮ More]              │  │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  [Load More...] (244 more items)                                                 │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### List View (Compact Layout)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│  ┌─── CONTENT LIST (Compact) ────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  ☐ | Type | Title                         | Speaker        | Event    | Size  | … │ │
│  │  ──┼──────┼───────────────────────────────┼────────────────┼──────────┼───────┼──│ │
│  │  ☐ │ 📊   │ Kubernetes Best Practices...  │ Dr. P. Müller  │ Spr 2024 │ 15 MB │ … │ │
│  │    │      │ 1,247 views • 523 downloads   │ TechCorp AG    │          │       │  │ │
│  │  ──┼──────┼───────────────────────────────┼────────────────┼──────────┼───────┼──│ │
│  │  ☐ │ 🎥   │ Container Security Deep Dive  │ S. König       │ Aut 2023 │ 342MB │ … │ │
│  │    │      │ 2,103 views • 891 downloads   │ SwissBank Ltd  │          │       │  │ │
│  │  ──┼──────┼───────────────────────────────┼────────────────┼──────────┼───────┼──│ │
│  │  ☐ │ 📄   │ Platform Engineering Workshop │ M. Weber       │ Spr 2025 │ 3.8MB │ … │ │
│  │    │      │ Under Review                   │ Digital Sol.   │          │       │  │ │
│  │  ──┼──────┼───────────────────────────────┼────────────────┼──────────┼───────┼──│ │
│  │                                                                                   │ │
│  │  [Load More...] (244 more items)                                                 │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Version History Modal

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ [X] Version History - Kubernetes Best Practices for Production                       │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── VERSION TIMELINE ──────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  Version 2 (Current) • Mar 18, 2024 15:30                                        │ │
│  │  ┌───────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ Updated by: Dr. Peter Müller                                              │  │ │
│  │  │ File: kubernetes-best-practices-v2.pdf (15.2 MB)                          │  │ │
│  │  │ Changes: Added cost optimization section, updated security examples       │  │ │
│  │  │ Status: Published                                                          │  │ │
│  │  │                                                                            │  │ │
│  │  │ [View] [Download] [Compare with v1] [Revert to this version]             │  │ │
│  │  └───────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  Version 1 • Mar 15, 2024 10:45                                                  │ │
│  │  ┌───────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ Uploaded by: Dr. Peter Müller                                             │  │ │
│  │  │ File: kubernetes-best-practices-v1.pdf (14.8 MB)                          │  │ │
│  │  │ Changes: Initial upload                                                    │  │ │
│  │  │ Status: Archived (replaced by v2)                                         │  │ │
│  │  │                                                                            │  │ │
│  │  │ [View] [Download] [Compare with v2]                                       │  │ │
│  │  └───────────────────────────────────────────────────────────────────────────┘  │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│                                                                              [Close]  │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Analytics Modal

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ [X] Content Analytics - Kubernetes Best Practices for Production                     │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── USAGE OVERVIEW ────────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  Last 30 Days                                                                     │ │
│  │  ┌─────────────────┬─────────────────┬─────────────────┬─────────────────┐      │ │
│  │  │ 👁️ Views        │ ⬇ Downloads     │ ⭐ Rating       │ 💬 Reviews      │      │ │
│  │  │ 342             │ 156             │ 4.8/5.0         │ 12              │      │ │
│  │  │ +15% vs prev    │ +23% vs prev    │ (45 total)      │ +3 this month   │      │ │
│  │  └─────────────────┴─────────────────┴─────────────────┴─────────────────┘      │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── TRAFFIC CHART ─────────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  Views & Downloads Over Time                                                     │ │
│  │  ┌───────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │     Views ●───●───●───●───●───●                                           │  │ │
│  │  │ 400 │                                                                      │  │ │
│  │  │     │         Downloads ▼───▼───▼───▼───▼                                 │  │ │
│  │  │ 200 │                                                                      │  │ │
│  │  │     │                                                                      │  │ │
│  │  │   0 └────────────────────────────────────────────────────────────         │  │ │
│  │  │      Mar 18  Mar 25  Apr 1   Apr 8   Apr 15  Today                        │  │ │
│  │  └───────────────────────────────────────────────────────────────────────────┘  │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── AUDIENCE INSIGHTS ─────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  Top Viewer Companies:                  Viewer Roles:                            │ │
│  │  1. UBS AG (45 views)                   • DevOps Engineers: 42%                 │ │
│  │  2. Credit Suisse (32 views)            • Architects: 28%                       │ │
│  │  3. Swiss Re (28 views)                 • Developers: 18%                       │ │
│  │  4. PostFinance (21 views)              • Managers: 12%                         │ │
│  │  5. Raiffeisen (18 views)                                                        │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── REVIEWS & FEEDBACK ────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  Recent Reviews:                                                                 │ │
│  │  ⭐⭐⭐⭐⭐ "Excellent practical insights!" - Thomas K., SwissBank (Apr 12)       │ │
│  │  ⭐⭐⭐⭐⭐ "Best Kubernetes talk I've attended" - Anna M., TechCorp (Apr 8)      │ │
│  │  ⭐⭐⭐⭐☆ "Great content, could use more demos" - Peter S., UBS (Apr 5)         │ │
│  │                                                                                   │ │
│  │  [View All Reviews (45) →]                                                        │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│                                                      [Export Report (PDF)] [Close]   │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Interactive Elements

### Search & Filter Section
- **Search Bar**: Real-time search across titles, speakers, descriptions, tags
- **Advanced Search**: Opens modal with additional search criteria
- **Content Type Filter**: Presentation, Video, Document, Handout
- **Event Filter**: Filter by specific event or all events
- **Speaker Filter**: Autocomplete speaker search
- **Status Filter**: Published, Under Review, Draft, Archived
- **Year Filter**: Historical content by year (20+ years)
- **Tags Input**: Multi-tag filtering
- **Sort Dropdown**: Recent, Popular, Most Downloaded, Rating, Alphabetical
- **Clear Filters**: Reset all filters to default

### Bulk Actions Bar
- **Select All Checkbox**: Select/deselect all visible items
- **Selection Counter**: Shows number of selected items
- **Download Selected**: Batch download as ZIP
- **Tag Button**: Bulk tag management
- **Archive Button**: Move selected to archive
- **Delete Button**: Bulk delete with confirmation

### Library Statistics
- **Total Files**: Count of all content items
- **By Type**: Breakdown by content type
- **Storage Usage**: Used/total storage with visual progress bar

### Content Cards (Grid View)
- **Checkbox**: Bulk selection
- **Content Type Icon**: Visual indicator (📊 presentation, 🎥 video, 📄 document)
- **Status Badges**: Featured, Popular, Under Review, New
- **Thumbnail**: Preview image
- **Title & Speaker**: Content title with speaker name and company
- **Event Info**: Associated event name and date
- **File Details**: Type, size, version number
- **Tags**: Clickable tags for filtering
- **Statistics**: Views, downloads, rating with review count
- **Timestamps**: Upload and modification dates
- **Status**: Published, Under Review, Draft, Archived
- **Action Buttons**: View, Download, Edit Metadata, Version History, Analytics, More menu

### List View (Compact)
- **Table Layout**: Compact rows for efficient scanning
- **Sortable Columns**: Click column headers to sort
- **Inline Stats**: Key metrics in condensed format
- **Quick Actions**: Hover for action menu

### Version History
- **Timeline View**: Chronological list of all versions
- **Version Cards**: Details for each version
- **Compare Versions**: Side-by-side comparison
- **Revert Option**: Restore previous version
- **Change Notes**: Description of what changed

### Analytics Dashboard
- **Usage Overview**: Key metrics (views, downloads, rating)
- **Traffic Chart**: Visual representation over time
- **Audience Insights**: Viewer companies and roles
- **Reviews Section**: Recent reviews with ratings
- **Export**: Download analytics report as PDF

---

## Functional Requirements Met

- **FR3**: Speaker self-service material management with version control
- **FR18**: Historical archive access across 20+ years of content
- **Content Organization**: Search, filter, and organize all content types
- **Version Management**: Track and manage content versions
- **Usage Analytics**: Monitor content performance and engagement
- **Bulk Operations**: Efficient management of multiple items
- **Role-Based Access**: Organizers manage all, speakers manage own content

---

## User Interactions

### For Organizers (Full Access)
1. **Browse All Content**: View complete content library across all events
2. **Advanced Search**: Use powerful search with multiple criteria
3. **Filter Content**: Apply multiple filters to find specific content
4. **Sort Results**: Order by various criteria (recent, popular, rating)
5. **Bulk Select**: Select multiple items for batch operations
6. **Bulk Download**: Download selected content as ZIP archive
7. **Bulk Tag**: Add/remove tags from multiple items
8. **Bulk Archive**: Move multiple items to archive
9. **Bulk Delete**: Delete multiple items with confirmation
10. **View Content**: Preview content in viewer
11. **Download Content**: Download individual files
12. **Edit Metadata**: Update titles, descriptions, tags
13. **Manage Versions**: View version history, compare, revert
14. **View Analytics**: Detailed usage statistics and insights
15. **Approve Content**: Review and approve submitted materials
16. **Reject Content**: Reject with feedback for revision
17. **Feature Content**: Mark content as featured
18. **Archive Content**: Move to archive while preserving history
19. **Delete Content**: Permanently delete with confirmation
20. **Upload Content**: Add new content to library

### For Speakers (Own Content Only)
21. **View My Content**: Filter to show only own submissions
22. **Upload Materials**: Submit presentations and materials
23. **Edit Own Metadata**: Update titles, descriptions for own content
24. **Version Control**: Upload new versions of own presentations
25. **View Own Analytics**: See performance of own content
26. **Draft Management**: Save drafts before submission
27. **Submit for Review**: Submit materials to moderator queue

### For Attendees (Read-Only)
28. **Search Content**: Search across all published content
29. **Filter & Browse**: Use filters to discover content
30. **View Content**: Preview presentations and materials
31. **Download Content**: Download available materials
32. **Rate & Review**: Provide feedback on content
33. **Save Favorites**: Bookmark content for later

### Common Interactions
34. **Quick Filters**: Apply predefined filter combinations
35. **Tag Click**: Filter by clicked tag
36. **Speaker Click**: View all content from speaker
37. **Event Click**: View all content from event
38. **Toggle View**: Switch between grid and list layouts
39. **Load More**: Infinite scroll or pagination

---

## Technical Notes

### Component Structure
- **ContentLibraryScreen.tsx**: Main content library screen
- **ContentSearchBar.tsx**: Search with autocomplete
- **ContentFilters.tsx**: Advanced filtering panel
- **ContentList.tsx**: Content list container
- **ContentCard.tsx**: Individual content card (grid view)
- **ContentRow.tsx**: Individual content row (list view)
- **BulkActionsBar.tsx**: Bulk selection and actions
- **VersionHistoryModal.tsx**: Version tracking interface
- **ContentAnalyticsModal.tsx**: Analytics dashboard
- **AdvancedSearchModal.tsx**: Advanced search interface
- **ContentUploadModal.tsx**: Upload new content
- **MetadataEditor.tsx**: Edit content metadata

### State Management
- **Local State**:
  - View mode (grid/list)
  - Search query
  - Active filters
  - Selected items (for bulk operations)
  - Sort order
- **Zustand Store**:
  - User's filter preferences
  - View mode preference
  - Recent searches
- **React Query**: Server state for content
  - `contentList` query: Cached for 5 minutes
  - `contentDetails` query: Cached for 10 minutes
  - `contentVersions` query: Cached for 15 minutes
  - `contentAnalytics` query: Cached for 10 minutes

### API Integration
- **Content List**: `GET /api/v1/content/search`
- **Content Details**: `GET /api/v1/content/{contentId}`
- **Upload Content**: `POST /api/v1/content` (multipart/form-data)
- **Update Metadata**: `PUT /api/v1/content/{contentId}/metadata`
- **Delete Content**: `DELETE /api/v1/content/{contentId}`
- **Version History**: `GET /api/v1/content/{contentId}/versions`
- **Upload Version**: `POST /api/v1/content/{contentId}/versions`
- **Analytics**: `GET /api/v1/content/{contentId}/analytics`
- **Bulk Operations**: `POST /api/v1/content/bulk/{action}`
- **Download**: `GET /api/v1/content/{contentId}/download` (presigned URL)

### Performance Optimization
- **Virtual Scrolling**: For lists with >100 items
- **Lazy Loading**: Load thumbnails on demand
- **Debounced Search**: 300ms debounce on search input
- **Pagination**: Load 20 items at a time
- **Thumbnail Optimization**: Compressed thumbnails from CDN
- **Memoization**: Memoize filter and sort calculations

### Caching Strategy
- **Content List**: 5-minute cache with background refresh
- **Content Details**: 10-minute cache
- **Search Results**: 15-minute cache per query
- **Thumbnails**: CDN caching with long TTL
- **Analytics**: 10-minute cache
- **Cache Invalidation**: Invalidate on content update, upload, delete

### File Upload Workflow
1. **Request Upload URL**: `POST /api/v1/files/presigned-upload-url`
2. **Direct S3 Upload**: Client uploads directly to S3
3. **Confirm Upload**: `POST /api/v1/files/{fileId}/confirm`
4. **Create Content Record**: `POST /api/v1/content` with fileId
5. **Virus Scan**: Automatic S3 Object Lambda scan
6. **CDN Distribution**: File distributed to CloudFront
7. **Notification**: Speaker notified of successful upload

### Accessibility
- **Keyboard Navigation**: Full keyboard access
- **ARIA Labels**:
  - `aria-label="Search content"` on search input
  - `aria-label="Select {title} for bulk action"` on checkboxes
  - `aria-label="Download {title}"` on download buttons
  - `aria-label="View analytics for {title}"` on analytics buttons
- **Focus Management**: Proper focus handling for modals
- **Screen Reader Support**:
  - Content cards fully described
  - Stats announced: "1,247 views, 523 downloads, 4.8 stars"
  - Filter changes announced
- **Color Contrast**: WCAG 2.1 AA compliant
- **Alt Text**: Thumbnails include descriptive alt text

---

## API Requirements

### Initial Page Load APIs

1. **GET /api/v1/content/search**
   - Query params: `limit=20, offset=0, sortBy=recent, order=desc, status=published`
   - Returns: Paginated list of content
   - Response: `{ results: [{ id, title, description, contentType, fileId, eventTitle, eventDate, speakerName, companyName, thumbnailUrl, downloadUrl, tags, viewCount, downloadCount, createdAt, status }], facets, pagination }`
   - Used for: Initial content list display

2. **GET /api/v1/content/statistics**
   - Returns: Overall library statistics
   - Response: `{ totalFiles: 1247, presentations: 892, videos: 156, documents: 199, storageUsed: 48693493760, storageLimit: 214748364800 }`
   - Used for: Library statistics bar

3. **GET /api/v1/content/facets**
   - Returns: Available filter options
   - Response: `{ contentTypes: [], years: [], speakers: [], events: [], tags: [] }`
   - Used for: Populate filter dropdowns

### Action APIs

#### Search & Filter

4. **GET /api/v1/content/search**
   - Triggered by: Search input, filter changes, sort changes
   - Query params: `query={text}, contentType={type}, eventId={id}, speakerId={id}, year={year}, tags={tags}, status={status}, sortBy={field}, limit=20, offset={n}`
   - Returns: Filtered content list
   - Used for: Real-time search and filtering

5. **GET /api/v1/content/search/advanced**
   - Triggered by: [Advanced Search] button
   - Payload: Complex search criteria
   - Returns: Advanced search results
   - Opens: Advanced search modal

#### Content Management

6. **POST /api/v1/content**
   - Triggered by: [+ Upload Content] button
   - Content-Type: multipart/form-data
   - Payload: File + metadata (title, description, eventId, tags, etc.)
   - Returns: Created content with ID
   - Side effects: Uploads to S3, creates content record, triggers virus scan

7. **PUT /api/v1/content/{contentId}/metadata**
   - Triggered by: [Edit Metadata] button save
   - Payload: `{ title, description, tags, eventId, speakerId }`
   - Returns: Updated content object
   - Used for: Update content metadata

8. **DELETE /api/v1/content/{contentId}**
   - Triggered by: [Delete] button confirmation
   - Returns: `{ success: true }`
   - Side effects: Soft delete, moves to archive, updates storage quota

9. **GET /api/v1/content/{contentId}**
   - Triggered by: [View] button
   - Returns: Complete content details
   - Opens: Content Detail/Edit Screen

10. **GET /api/v1/content/{contentId}/download**
    - Triggered by: [Download] button
    - Returns: `{ downloadUrl: "presigned S3 URL" }`
    - Used for: Generate secure download link

#### Version Management

11. **GET /api/v1/content/{contentId}/versions**
    - Triggered by: [Version History] button
    - Returns: List of all versions
    - Response: `{ versions: [{ versionNumber, uploadedAt, uploadedBy, fileSize, fileName, status, changeNotes }] }`
    - Opens: Version History modal

12. **POST /api/v1/content/{contentId}/versions**
    - Triggered by: Upload new version
    - Payload: New file + change notes
    - Returns: New version object
    - Side effects: Creates new version, archives previous

13. **POST /api/v1/content/{contentId}/versions/{versionId}/revert**
    - Triggered by: [Revert to this version] button
    - Returns: Reverted content object
    - Side effects: Makes selected version current, creates new version entry

14. **GET /api/v1/content/{contentId}/versions/compare**
    - Triggered by: [Compare with v{n}] button
    - Query params: `version1={n}, version2={m}`
    - Returns: Comparison data
    - Opens: Version comparison view

#### Analytics

15. **GET /api/v1/content/{contentId}/analytics**
    - Triggered by: [Analytics] button
    - Query params: `period=30days`
    - Returns: Analytics data
    - Response: `{ views, downloads, rating, reviews, viewsOverTime, downloadsOverTime, topViewerCompanies, viewerRoles, recentReviews }`
    - Opens: Analytics modal

16. **GET /api/v1/content/{contentId}/analytics/export**
    - Triggered by: [Export Report] button
    - Query params: `format=pdf`
    - Returns: `{ downloadUrl }`
    - Used for: Download analytics report

#### Bulk Operations

17. **POST /api/v1/content/bulk/download**
    - Triggered by: [Download Selected] button
    - Payload: `{ contentIds: [] }`
    - Returns: `{ zipUrl: "presigned S3 URL for ZIP" }`
    - Used for: Bulk download as ZIP

18. **POST /api/v1/content/bulk/tag**
    - Triggered by: [Tag] button
    - Payload: `{ contentIds: [], addTags: [], removeTags: [] }`
    - Returns: `{ updatedCount: n }`
    - Used for: Bulk tag management

19. **POST /api/v1/content/bulk/archive**
    - Triggered by: [Archive] button
    - Payload: `{ contentIds: [] }`
    - Returns: `{ archivedCount: n }`
    - Used for: Bulk archive

20. **POST /api/v1/content/bulk/delete**
    - Triggered by: [Delete] button confirmation
    - Payload: `{ contentIds: [] }`
    - Returns: `{ deletedCount: n }`
    - Used for: Bulk delete

#### Review & Approval (Organizer Only)

21. **POST /api/v1/content/{contentId}/approve**
    - Triggered by: [Approve] button
    - Payload: `{ comments }`
    - Returns: Updated content with status=published
    - Side effects: Publishes content, notifies speaker

22. **POST /api/v1/content/{contentId}/reject**
    - Triggered by: [Reject] button
    - Payload: `{ reason, feedback }`
    - Returns: Updated content with status=rejected
    - Side effects: Rejects content, notifies speaker with feedback

---

## Navigation Map

### Primary Navigation Actions

1. **← Back to Dashboard button** → Navigate to appropriate dashboard
   - Type: Full page navigation
   - Target: Role-dependent (Organizer→Event Management Dashboard, Speaker→Speaker Dashboard, Attendee→Personal Dashboard)

2. **[+ Upload Content] button** → Opens upload modal
   - Type: Modal overlay
   - Opens: Content upload wizard
   - Role-based access: Organizer (all events), Speaker (own events only)

3. **Search input** → Filters content list
   - Type: In-place filtering
   - Debounced: 300ms delay
   - No navigation

4. **[Advanced Search] button** → Opens advanced search modal
   - Type: Modal overlay
   - Opens: Advanced search with additional criteria
   - No screen navigation

5. **Filter dropdowns** → Filters content list
   - Type: In-place filtering
   - Updates content list
   - No navigation

6. **[Clear Filters] button** → Resets all filters
   - Type: In-place action
   - Clears all active filters
   - No navigation

7. **Sort dropdown** → Changes sort order
   - Type: In-place sorting
   - Options: Recent, Popular, Most Downloaded, Rating, Alphabetical
   - No navigation

8. **[Grid/List] toggle** → Switches view mode
   - Type: In-place view change
   - Toggles between grid cards and list rows
   - No navigation

### Bulk Actions

9. **Select All checkbox** → Selects/deselects all visible items
   - Type: In-place selection
   - Updates: Selection counter, enables bulk actions
   - No navigation

10. **[Download Selected] button** → Downloads selected content
    - Type: File download
    - API call: `POST /api/v1/content/bulk/download`
    - Downloads: ZIP file with selected content
    - No navigation

11. **[Tag] button** → Opens bulk tag editor
    - Type: Modal overlay
    - Opens: Tag management modal for selected items
    - No screen navigation

12. **[Archive] button** → Archives selected content
    - Type: Confirmation dialog → API call
    - Confirmation: "Archive {n} items?"
    - No screen navigation

13. **[Delete] button** → Deletes selected content
    - Type: Confirmation dialog → API call
    - Confirmation: "Permanently delete {n} items?"
    - No screen navigation

### Content Card Actions

14. **Content card checkbox** → Selects item for bulk operations
    - Type: In-place selection
    - Updates: Selection counter, bulk actions bar
    - No navigation

15. **Thumbnail click** → Opens content viewer
    - Type: Modal or full page
    - Opens: Content preview/viewer
    - No screen navigation (modal)

16. **Title click** → Navigate to Content Detail/Edit Screen
    - Type: Full page navigation
    - Target: [story-multi-role-content-detail-edit.md](story-multi-role-content-detail-edit.md)
    - Context: Content ID passed

17. **Speaker name click** → Filters by speaker
    - Type: In-place filtering
    - Applies speaker filter
    - No navigation

18. **Event name click** → Navigate to Event Details
    - Type: Full page navigation
    - Target: Event Detail page (role-dependent view)

19. **Tag click** → Filters by tag
    - Type: In-place filtering
    - Applies tag filter
    - No navigation

20. **[View] button** → Navigate to Content Detail/Edit Screen
    - Type: Full page navigation
    - Same as title click

21. **[Download] button** → Downloads content file
    - Type: File download
    - API call: `GET /api/v1/content/{contentId}/download`
    - Downloads: File via presigned URL
    - No navigation

22. **[Edit Metadata] button** → Opens metadata editor
    - Type: Modal overlay
    - Opens: Metadata edit form
    - Role-based access: Organizer (all), Speaker (own only)
    - No screen navigation

23. **[Version History] button** → Opens version history modal
    - Type: Modal overlay
    - API call: `GET /api/v1/content/{contentId}/versions`
    - Opens: Version History modal
    - No screen navigation

24. **[Analytics] button** → Opens analytics modal
    - Type: Modal overlay
    - API call: `GET /api/v1/content/{contentId}/analytics`
    - Opens: Analytics modal
    - No screen navigation

25. **[⋮ More] menu** → Opens action menu
    - Type: Dropdown menu
    - Actions: Feature, Archive, Duplicate, Share, Report
    - No screen navigation

### Version History Modal Actions

26. **[View] button (version)** → Opens version viewer
    - Type: Modal or new tab
    - Opens: Content viewer for specific version
    - No screen navigation

27. **[Download] button (version)** → Downloads version
    - Type: File download
    - Downloads: Specific version file
    - No navigation

28. **[Compare with v{n}] button** → Opens comparison view
    - Type: Modal overlay
    - Opens: Side-by-side version comparison
    - No screen navigation

29. **[Revert to this version] button** → Reverts to version
    - Type: Confirmation dialog → API call
    - Confirmation: "Revert to version {n}?"
    - Updates: Current version, closes modal
    - No screen navigation

30. **[Close] button (modal)** → Closes modal
    - Type: Modal dismiss
    - Returns to library screen
    - No screen navigation

### Analytics Modal Actions

31. **[View All Reviews] link** → Opens reviews page
    - Type: Modal expansion or new page
    - Shows: Complete reviews list
    - No screen navigation (modal expansion)

32. **[Export Report (PDF)] button** → Downloads analytics report
    - Type: File download
    - API call: `GET /api/v1/content/{contentId}/analytics/export`
    - Downloads: PDF report
    - No navigation

33. **[Close] button (analytics modal)** → Closes modal
    - Type: Modal dismiss
    - Returns to library screen
    - No screen navigation

### Pagination Actions

34. **[Load More] button** → Loads additional content
    - Type: In-place content expansion
    - API call: Increments offset
    - Appends: Additional content cards
    - No navigation

35. **Scroll to bottom** → Infinite scroll trigger
    - Type: Auto-load
    - Triggers: Load more content
    - No manual navigation

### Error States & Redirects

36. **No content found** → Shows empty state
    - Type: In-page message
    - Message: "No content matches your search"
    - Action: [Clear Filters] or [Upload Content] button
    - No navigation

37. **Upload error** → Shows error notification
    - Type: Toast notification
    - Message: "Upload failed. Please try again."
    - Action: [Retry] button
    - No navigation

38. **API error loading content** → Shows error state
    - Type: In-page error
    - Message: "Unable to load content"
    - Action: [Retry] button
    - No navigation

39. **Network error** → Shows offline state
    - Type: Banner notification
    - Message: "You are offline. Showing cached content."
    - Shows: Last cached data
    - No navigation

---

## Responsive Design Considerations

### Mobile Layout Changes

- **Search Bar**: Full-width with filters in bottom sheet
- **Bulk Actions**: Sticky bottom bar when items selected
- **Content Cards**: Single column, stacked vertically
- **List View**: Optimized compact rows for mobile
- **Filters**: Bottom sheet instead of inline dropdowns
- **Statistics**: Simplified, scrollable horizontal cards
- **Modals**: Full-screen on mobile

### Tablet Layout Changes

- **Content Cards**: Two-column grid
- **Filters**: Collapsible sidebar
- **Version History**: Side panel instead of modal

### Mobile-Specific Interactions

- **Swipe to Select**: Swipe right to select for bulk actions
- **Pull to Refresh**: Refresh content list
- **Long Press**: Long press for quick actions menu
- **Bottom Sheet Filters**: Open filters in bottom sheet
- **Sticky Search**: Search bar sticky at top when scrolling

---

## Accessibility Notes

- **Keyboard Navigation**: All interactive elements accessible via Tab key
- **ARIA Labels**: Descriptive labels for all actions
- **Focus Management**: Proper focus handling for modals and dropdowns
- **Screen Reader Support**: Complete announcements for actions and changes
- **Color Contrast**: WCAG 2.1 AA compliant (4.5:1 minimum)
- **Alt Text**: Thumbnails and icons include descriptive alt text
- **Skip Links**: "Skip to content list" for keyboard users
- **ARIA Live Regions**: Search results and filter changes announced

---

## State Management

### Local Component State

- `viewMode`: 'grid' | 'list'
- `searchQuery`: string
- `filters`: object
- `sortBy`: string
- `selectedItems`: ContentId[]
- `isVersionHistoryOpen`: boolean
- `isAnalyticsOpen`: boolean

### Global State (Zustand Store)

- `content.viewPreferences`: object
- `content.filterPreferences`: object
- `content.recentSearches`: string[]
- `auth.currentUser`: User
- `auth.currentRole`: UserRole

### Server State (React Query)

- `contentList`: Content list (cached for 5 minutes)
- `contentDetails`: Individual content (cached for 10 minutes)
- `contentVersions`: Version history (cached for 15 minutes)
- `contentAnalytics`: Analytics data (cached for 10 minutes)
- `contentStatistics`: Library stats (cached for 5 minutes)

### Real-Time Updates

- **Upload Progress**: Live progress updates during upload
- **Review Status**: Real-time updates when content approved/rejected
- **View Count**: Periodic refresh of view counts
- **New Content**: Notification when new content added

---

## Edge Cases & Error Handling

- **Empty Library**: Show "No content in library yet" with [Upload Content] CTA
- **No Search Results**: Show "No content matches your search" with suggestions
- **Upload Too Large**: Block upload, show file size limit message
- **Upload Failed**: Show error with [Retry] button
- **Delete Confirmation**: Require confirmation for permanent deletion
- **Version Limit**: Warn when approaching version limit (if any)
- **Storage Quota**: Show warning when 80% storage used
- **Quota Exceeded**: Block upload, suggest cleanup
- **Loading State**: Display skeleton cards while loading
- **API Error**: Show error banner with [Retry] button
- **Network Timeout**: Show cached data with "Viewing offline version" banner
- **Permission Denied**: Hide edit/delete buttons for unauthorized users
- **Concurrent Edit**: Warn "Content was modified by another user"

---

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-10-04 | 1.0 | Initial wireframe creation for Content Library/Repository Screen | Sally (UX Expert) |

---

## Review Notes

### Open Questions

1. **Version Limit**: Should there be a maximum number of versions per content item?
2. **Auto-Archiving**: Should old content be automatically archived after X years?
3. **Content Moderation**: Should there be a moderation queue for all uploads or only first-time speakers?
4. **Sharing**: Should content be shareable outside the platform (public links)?
5. **Collaboration**: Should multiple speakers be able to co-author content?
6. **License Management**: Should content have license/usage rights metadata?

### Design Iterations

- **v1.0**: Initial comprehensive design with grid/list views, version control, analytics
- Consider adding: Content collections/playlists for curated content groupings
- Consider adding: Collaborative annotations and comments on content
- Consider adding: Content recommendation engine based on viewing history

### Stakeholder Feedback

- Pending review from organizers for bulk operations workflow
- Need to validate version control UX with speakers
- Confirm analytics metrics with business requirements
- Validate storage quota limits with infrastructure team
