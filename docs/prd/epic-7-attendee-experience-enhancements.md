# Epic 7: Attendee Experience Enhancements - DEFERRED

**Status:** 📦 **DEFERRED TO PHASE 2** (Week 26+)

**Reorganization Note:** This epic was formerly "Epic 5: Attendee Experience". Basic content search (Story 5.1) moved to Epic 4 (Public Website). This epic now focuses on personal engagement enhancements and mobile PWA.

**Phase 1 Priority:** Epic 4 provides public content search and discovery. This epic adds personal dashboards, bookmarks, and offline PWA capabilities.

---

## Epic Overview

**Epic Goal**: Enhance attendee experience with personal engagement dashboards, content bookmarking, granular notification preferences, and mobile PWA with offline capabilities.

**Deliverable**: Attendees can search 20+ years of content, manage preferences, and access everything via mobile PWA.

**Architecture Context**:
- **Core Service**: Attendee Experience Service (Java 21 + Spring Boot 3.2)
- **Search**: PostgreSQL full-text search for content discovery
- **Frontend**: React PWA with offline capabilities
- **Infrastructure**: CloudFront CDN for global delivery
- **Caching**: Caffeine in-memory cache for search optimization

**Duration**: 8 weeks (Weeks 39-46)

---

## Story 5.1: Historical Content Search

**User Story:**
As an **attendee**, I want to search 20+ years of BATbern content with filtering capabilities, so that I can find relevant presentations and expertise.

**Architecture Integration:**
- **Search Engine**: PostgreSQL full-text search
- **Database**: PostgreSQL for content metadata and search
- **Frontend**: React search interface with facets
- **Cache**: Caffeine in-memory cache for search results

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

## Story 5.2: Personal Engagement Management (Advanced Settings Only)

> **⚠️ SCOPE REDUCED - Basic Settings Moved to Epic 2 (Story 2.6)**
> Basic account management features (profile info, email, password, basic notifications, basic privacy) have been moved to **Story 2.6: User Account Management Frontend** in Epic 2 as foundational features.
>
> **Story 5.2 now focuses exclusively on ADVANCED attendee-specific features:**
> - Content Preferences (interests, topics, experience level, content formats)
> - Language & Accessibility (UI language, date/time formats, accessibility options)
> - Data & Export (GDPR data export, account deactivation, account deletion)
> - Personal Attendee Dashboard (bookmarks, learning progress, content recommendations)

**User Story:**
As an **attendee**, I want advanced personalization options and content management tools, so that I can optimize my BATbern learning experience with tailored content recommendations and accessibility preferences.

**Architecture Integration:**
- **Service**: Attendee Experience Service
- **Database**: PostgreSQL user preferences, content bookmarks, learning progress
- **Email**: AWS SES for data export notifications
- **Frontend**: React advanced settings and personal dashboard

**Wireframe Context:**

### Wireframe References
**From docs/wireframes/sitemap.md:**

1. **Personal Attendee Dashboard:** `docs/wireframes/story-5.2-personal-dashboard.md` ✅
   - Attendee's main landing page after login
   - Upcoming events, registrations, saved content
   - Learning progress tracking
   - Notification summary
   - Quick access to all features

2. **Advanced User Settings:** `docs/wireframes/story-5.2-user-settings.md` ✅ (Advanced features only)
   - **Content Preferences**: Interests/topics, content language, experience level, format preferences
   - **Language & Accessibility**: UI language, date/time formats, accessibility options
   - **Data & Export**: GDPR data export, account deactivation/deletion
   - **Note**: Basic settings (Account, Notifications, Privacy) moved to Story 2.6 in Epic 2

### UI Components
**Key interface elements:**

> **Note**: Basic account settings (profile photo, name, email, password, basic notifications, basic privacy) are in Story 2.6 (Epic 2).
> This story focuses on advanced features only.

- **Personal Dashboard**:
  - Upcoming Events cards (registered events with countdown)
  - My Registrations list (current and past)
  - Saved Content preview (recent bookmarks)
  - Learning Progress widget (paths, achievements)
  - Notification Center summary (unread count)
  - Quick Actions (find content, register for event, view profile)

- **Advanced Settings Tabs** (extends Story 2.6 basic settings):
  - **Content Preferences**: Interests, topic subscriptions, content language, experience level, format preferences
  - **Language & Accessibility**: UI language, date/time formats, high contrast mode, larger text, screen reader optimizations, keyboard shortcuts
  - **Data & Export**: GDPR data export request, account deactivation (60-day retention), permanent account deletion

- **Content Preferences Panel**:
  - Interest topic multi-select (unlimited custom tags)
  - Content language preferences (German, English, both)
  - Experience level checkboxes (Beginner, Intermediate, Advanced)
  - Content format preferences (Presentations, Workshops, Panels, Lightning talks)
  - Default view mode (Grid vs List)

- **Language & Accessibility Panel**:
  - UI language selector (German, English)
  - Date/time format preferences (DD.MM.YYYY, MM/DD/YYYY, YYYY-MM-DD)
  - Time format (24-hour, 12-hour)
  - Accessibility options: High contrast, larger text, reduce animations, focus indicators, keyboard shortcuts, screen reader optimizations
  - [View Keyboard Shortcuts] link

- **Data & Export Panel**:
  - GDPR data export (JSON/CSV format)
  - [Request Data Export] button (async, email notification when ready)
  - Account deactivation (60-day retention, unregisters from all events)
  - Permanent account deletion (immediate, irreversible)
  - Account status display (Active, member since, events attended)

- **Bookmark Management**:
  - Saved content library with grid/list view
  - Collections/folders for organization
  - Quick filters (presentations, speakers, topics)
  - Bulk actions (move to collection, remove)

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

> **Note**: Basic profile, notifications, and privacy features (AC 1, 2, 6-12) are in Story 2.6 (Epic 2).
> This story focuses on advanced personalization and GDPR features.

**Personal Dashboard:**
1. **Bookmark Management**: Save and organize content in collections/folders
2. **Download History**: Track downloaded presentations and materials
3. **Learning Progress**: Display completed content, paths, achievements

**Advanced Content Preferences:**
4. **Interest Topics**: Select unlimited custom interest tags
5. **Content Language**: Prefer German, English, or both for presentations
6. **Experience Level**: Filter by Beginner, Intermediate, Advanced
7. **Content Formats**: Prefer Presentations, Workshops, Panels, Lightning talks
8. **View Mode**: Default to Grid or List view

**Language & Accessibility:**
9. **UI Language**: Select German or English interface language
10. **Date/Time Formats**: Choose date format (DD.MM.YYYY, MM/DD/YYYY, YYYY-MM-DD) and time format (24-hour, 12-hour)
11. **Accessibility Options**: Enable high contrast, larger text, reduce animations, focus indicators, keyboard shortcuts, screen reader optimizations
12. **Keyboard Shortcuts**: View and customize keyboard shortcuts

**Data & Export (GDPR):**
13. **Data Export**: Request complete data export (JSON/CSV) with email notification when ready (24h SLA)
14. **Account Deactivation**: Temporarily disable account (60-day retention, auto-delete after, unregisters from all events)
15. **Account Deletion**: Permanently delete all data immediately (irreversible, cascade delete across all services)
16. **Export Content**: Include profile, event registrations, download history, bookmarks, preferences, settings, notification history

**Definition of Done:**
- [ ] Bookmarks synchronized across devices
- [ ] Collections/folders for bookmark organization functional
- [ ] Learning progress tracking displays completed content
- [ ] Content preferences (interests, language, level, formats) working
- [ ] UI language selection changes entire interface (i18n)
- [ ] Date/time format preferences applied throughout app
- [ ] All accessibility options functional (high contrast, larger text, etc.)
- [ ] Keyboard shortcuts customizable and working
- [ ] GDPR data export request triggers async job, email notification sent when ready
- [ ] Data export includes all user data (profile, events, downloads, bookmarks, preferences, notifications)
- [ ] Account deactivation unregisters from all events, hides profile, retains data 60 days
- [ ] Account deletion cascades across all domain services (User, Event, Speaker, Attendee)
- [ ] Deletion audit trail logged for compliance
- [ ] All advanced features accessible from Story 2.6 basic settings (seamless UX)

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