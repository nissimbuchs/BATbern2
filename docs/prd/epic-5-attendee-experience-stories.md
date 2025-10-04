# Epic 5: Attendee Experience - Vertical Slice Stories

## Epic Overview

**Epic Goal**: Deliver comprehensive attendee experience with content discovery, personal engagement management, and mobile access to maximize learning and participation.

**Deliverable**: Attendees can search 20+ years of content, manage preferences, and access everything via mobile PWA.

**Architecture Context**:
- **Core Service**: Attendee Experience Service (Java 21 + Spring Boot 3.2)
- **Search**: PostgreSQL full-text search for content discovery
- **Frontend**: React PWA with offline capabilities
- **Infrastructure**: CloudFront CDN for global delivery

**Duration**: 8 weeks (Weeks 39-46)

---

## Story 5.1: Historical Content Search

**User Story:**
As an **attendee**, I want to search 20+ years of BATbern content with filtering capabilities, so that I can find relevant presentations and expertise.

**Architecture Integration:**
- **Search Engine**: PostgreSQL full-text search
- **Database**: PostgreSQL for content metadata and search
- **Frontend**: React search interface with facets
- **Cache**: Redis for search results

**Wireframe Context:**

### Wireframe References
**From docs/wireframes/sitemap.md:**
- **Main Screen:** `docs/wireframes/story-5.1-content-discovery.md` ✅
  - Content Discovery interface with search and filtering
  - **Note:** AI-powered recommendations removed (FR13 moved to backlog)
  - Basic non-AI recommendations only for MVP
  - Search, browse, save content, rate & review

### UI Components
**Key interface elements:**
- **Search Bar**: Prominent search input with autocomplete suggestions
  - Search across presentations, speakers, topics, companies
  - Real-time search suggestions as user types
  - Voice search option (mobile)
- **Filter Panel**: Multi-criteria filtering sidebar
  - Year/date range slider (2000-2025)
  - Topic category checkboxes
  - Speaker dropdown with autocomplete
  - Company filter
  - Content type (presentation, video, photo)
  - Rating filter (4+ stars, 3+ stars, etc.)
- **Content Grid**: Search results display
  - Content cards with thumbnail, title, speaker, date, rating
  - Hover preview with abstract excerpt
  - Save/bookmark icon
  - [View] button → Content Viewer
  - Sort controls (relevance, date, popularity, rating)
- **Basic Recommendations Panel**: Non-AI content suggestions
  - "Recently viewed"
  - "Popular this month"
  - "Related to your saved content"
  - Note: AI-powered recommendations removed per FR13
- **Content Actions**:
  - Save to library
  - Rate (1-5 stars)
  - Review/comment
  - Share
  - Download

### Wireframe Status
- ✅ **EXISTS**: Content Discovery wireframe fully documented (`story-5.1-content-discovery.md`)
  - Complete search and filtering interface
  - AI features removed per FR13 (moved to backlog)
  - Basic non-AI recommendations retained for MVP
- ✅ **EXISTS**: Content Viewer Page (`story-5.1-content-viewer.md`)
  - Core content consumption interface
  - PDF/video/slides viewer with navigation
  - Bookmarking and ratings system
- ✅ **EXISTS**: Filter Modal (`story-5.1-filter-modal.md`)
  - Mobile-responsive filtering across multiple contexts
  - Multi-criteria filtering with real-time results

### Navigation
**Key navigation paths from this screen:**
- → Content Viewer Page (click content card) ✅
- → Filter Modal (mobile filter interface) ✅
- → Speaker Profile (click speaker name)
- → Full Library Management (view saved content)
- ↔ Personal Attendee Dashboard (bidirectional)

**Acceptance Criteria:**
1. **Full-Text Search**: Search across titles, abstracts, speakers, topics
2. **Advanced Filtering**: By year, topic, company, speaker
3. **Content Preview**: View abstracts and speaker info
4. **Download Access**: Download available presentations
5. **Search Suggestions**: Auto-complete based on existing content
6. **Sort Options**: Relevance, date, popularity

**Definition of Done:**
- [ ] Search returns results in <500ms
- [ ] 20+ years of content indexed
- [ ] Faceted filtering works intuitively
- [ ] Download tracking implemented
- [ ] Mobile-responsive search interface
- [ ] Search accuracy validated with test queries

---

## Story 5.2: Personal Engagement Management

**User Story:**
As an **attendee**, I want to manage my preferences, bookmarks, and subscriptions, so that I can customize my BATbern experience.

**Architecture Integration:**
- **Service**: Attendee Experience Service
- **Database**: PostgreSQL user preferences
- **Email**: AWS SES for subscriptions
- **Frontend**: React profile management

**Wireframe Context:**

### Wireframe References
**From docs/wireframes/sitemap.md:**

1. **Personal Attendee Dashboard:** `docs/wireframes/story-5.2-personal-dashboard.md` ✅
   - Attendee's main landing page after login
   - Upcoming events, registrations, saved content
   - Learning progress tracking
   - Notification summary
   - Quick access to all features

2. **User Settings:** `docs/wireframes/story-5.2-user-settings.md` ✅ (Attendee version)
   - Account settings and profile management
   - Password change and security settings
   - Granular notification preferences (email, in-app, push)
   - Privacy controls and GDPR data export/delete
   - Content preferences and interests
   - Note: General user settings wireframe needed for all roles

### UI Components
**Key interface elements:**
- **Personal Dashboard**:
  - Upcoming Events cards (registered events with countdown)
  - My Registrations list (current and past)
  - Saved Content preview (recent bookmarks)
  - Learning Progress widget (paths, achievements)
  - Notification Center summary (unread count)
  - Quick Actions (find content, register for event, view profile)
- **User Settings Tabs**:
  - Account (profile photo, name, email, password change)
  - Notification Preferences (granular channel & type control)
  - Privacy (data export GDPR, data delete, visibility controls)
  - Content Preferences (interests, topic subscriptions)
  - App Settings (language, accessibility, PWA settings)
- **Notification Preferences Panel**:
  - Channel toggles (Email / In-App / Push)
  - Notification type checkboxes (events, speakers, newsletters, partners, system)
  - Frequency dropdown (Immediate / Daily Digest / Weekly Digest)
  - Quiet Hours configuration (start time, end time, timezone)
  - [Preview Email] button
  - Bulk actions ([Enable All] / [Disable All])
  - Notification History link
- **Bookmark Management**:
  - Saved content library with grid/list view
  - Collections/folders for organization
  - Quick filters (presentations, speakers, topics)
  - Bulk actions (move to collection, remove)
- **Profile Editor**:
  - Personal information form
  - Interest topics multi-select
  - Company affiliation (optional)
  - Biography/notes (optional)
  - [Save Changes] button

### Wireframe Status
- ✅ **EXISTS**: Both wireframes fully documented
  - Personal Attendee Dashboard (`story-5.2-personal-dashboard.md`)
  - User Settings (`story-5.2-user-settings.md` - attendee version with notification preferences)
- ✅ **EXISTS**: Event Details Page (Attendee View) (`story-5.2-event-details-attendee-view.md`)
  - Attendee-specific event detail view
  - Personal schedule management
  - Registration status tracking

### Navigation
**Key navigation paths from these screens:**
- **Personal Dashboard →**
  - → Event Details Page (Attendee View) (click event card) ✅
  - → Content Viewer (click saved content) ✅
  - → User Settings (click settings icon) ✅
  - → Notification Center (global navigation) ✅
- **User Settings →**
  - → Notification History (view past notifications)
  - → Privacy Controls (GDPR export/delete from Story 1.11)
  - → Email Preview Modal (preview notifications)
  - ⤴ Personal Dashboard

**Acceptance Criteria:**
1. **User Profile**: Create and manage attendee profile
2. **Newsletter Preferences**: Subscribe/unsubscribe options
3. **Content Bookmarking**: Save interesting presentations
4. **Download History**: Track downloaded content
5. **Interest Topics**: Select areas of interest

**Notification Preferences (Enhanced):**
6. **Channel Control**: Opt in/out for email, in-app, push notifications
7. **Notification Type Granularity**: Control preferences for event announcements, speaker updates, newsletter subscriptions, partner communications, system alerts
8. **Frequency Management**: Choose immediate, daily digest, or weekly digest
9. **Quiet Hours**: Configure quiet hours (no notifications sent during this time)
10. **Notification History**: View history of all received notifications
11. **Email Preview**: Preview how notifications will appear
12. **Bulk Actions**: Enable/disable all notifications with one click

**Definition of Done:**
- [ ] Profile creation simple and quick
- [ ] Preferences instantly updated
- [ ] Bookmarks synchronized across devices
- [ ] Newsletter preferences respected
- [ ] GDPR compliance implemented
- [ ] Data export available
- [ ] Granular notification preference controls functional
- [ ] All notification channels (email, in-app, push) respected
- [ ] Frequency settings (immediate, daily, weekly) working
- [ ] Quiet hours preventing notifications during configured times
- [ ] Notification history displaying last 90 days
- [ ] Email preview showing actual rendered template
- [ ] Bulk enable/disable affecting all preferences correctly
- [ ] GDPR data export including notification history

---

## Epic 5 Success Metrics

**Functional Success:**
- ✅ 20+ years of content searchable
- ✅ Personal preferences managed
- ✅ Mobile access fully functional
- ✅ Offline capabilities working

**Technical Performance:**
- **Search Speed**: <500ms response time
- **Mobile Performance**: Lighthouse >90
- **Offline Storage**: 100MB content cached
- **System Availability**: >99.5% uptime

**Business Value:**
- **Content Access**: 200% increase in historical content downloads
- **User Engagement**: 40% use mobile app
- **Satisfaction**: >4/5 user rating
- **Retention**: 50% return visitors

This epic transforms the attendee experience with modern content discovery and mobile capabilities.