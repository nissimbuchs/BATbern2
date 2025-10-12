# Epic 5: Enhanced Organizer Workflows - DEFERRED

**Status:** 📦 **DEFERRED TO PHASE 2** (Week 26+)

**Reorganization Note:** This epic was formerly "Epic 2: Basic Event Creation & Publishing" and now includes stories from original Epic 4 (Event Finalization). It has been reorganized and deferred to Phase 2 to prioritize CRUD foundation and public website launch in Phase 1.

**Phase 1 Priority:** Epics 1-4 (Foundation, CRUD, Migration, Public Website) deliver functional platform by Week 25.

---

## Epic Overview

**Epic Goal**: Implement the complete 16-step event planning workflow with intelligent automation, topic management, progressive publishing, and quality control, transforming manual event coordination into streamlined automated workflows.

**Deliverable**: Organizers can create events with intelligent topic selection, automated publishing workflows, and instant visibility - achieving end-to-end event creation in <30 minutes.

**Architecture Context**:
- **Core Service**: Event Management Service (Java 21 + Spring Boot 3.2)
- **Frontend**: React components for event creation and management
- **Publishing**: Automated publishing engine with immediate publication
- **Infrastructure**: AWS CloudFront CDN integration

**Duration**: 8 weeks (Weeks 13-20)

---

## Story 5.1: Event Type Definition (Workflow Step 1)

**User Story:**
As an **organizer**, I want to define event types with slot requirements, so that I can create events tailored to our different format requirements (full-day, afternoon, evening).

**Architecture Integration:**
- **Service**: Event Management Service
- **Database**: PostgreSQL event_types and event_slots tables
- **Frontend**: React event type configuration component
- **Cache**: Redis for event type templates

**Acceptance Criteria:**

**Event Type Configuration:**
1. **Event Type Definition**: Configure three event types:
   - Full-day: 6-8 slots (9:00-17:00)
   - Afternoon: 6-8 slots (13:00-18:00)
   - Evening: 3-4 slots (18:00-21:00)
2. **Slot Requirements**: Define minimum/maximum slots per event type
3. **Timing Templates**: Create reusable timing templates for each format
4. **Capacity Planning**: Set default attendee capacity based on event type

**Technical Implementation:**
5. **Event Type Entity**: Create EventType aggregate with validation rules
6. **REST API**: POST/GET/PUT /api/events/types endpoints
7. **React Component**: EventTypeSelector with template preview
8. **Validation**: Ensure slot counts match event type requirements

**Definition of Done:**
- [ ] Event type configuration supports all three formats
- [ ] Timing templates automatically populate slot times
- [ ] API endpoints fully documented in OpenAPI spec
- [ ] Frontend component validates slot requirements
- [ ] Unit tests cover all event type scenarios
- [ ] Integration test verifies event type creation

---

## Story 5.2: Topic Selection System (Workflow Step 2)

**User Story:**
As an **organizer**, I want to select topics from our backlog with intelligent suggestions, so that I can choose compelling topics while avoiding recent duplicates.

**Architecture Integration:**
- **Service**: Event Management Service with topic management
- **Database**: PostgreSQL topics table with usage history
- **AI/ML**: Basic similarity detection using PostgreSQL full-text search
- **Frontend**: React topic selector with search and suggestions

**Wireframe Context:**

### Wireframe References
**From docs/wireframes/sitemap.md:**
- **Main Screen:** `docs/wireframes/story-2.2-topic-backlog-management.md` ✅
  - Topic Backlog Manager with heat map visualization
  - Visual representation of topic usage over 24 months
  - Interactive topic cards with selection interface
  - Filter panel with category, status, partner interest filters
  - AI suggestions panel showing trending topics

### UI Components
**Key interface elements:**
- **Heat Map Visualization**: Interactive grid showing topic usage patterns from 2020-2025
  - Darker cells = more recent/frequent usage
  - Color-coded by staleness (red: <6 months, yellow: 6-12 months, green: >12 months)
  - Click to select topic
  - Hover for usage details tooltip
- **Topic Details Panel**: Displays selected topic information:
  - Performance metrics (attendance, ratings, downloads, engagement)
  - Partner interest (votes, priority, sponsor names, comments)
  - Similarity analysis (15-22% overlap indicators with related topics)
  - Last used date and suggested wait period
  - Related topics list
- **AI Suggestions Panel**: Machine learning-powered topic recommendations
  - Trending industry topics
  - Partner-requested topics
  - Topics aligned with current trends
  - "Platform Engineering", "FinOps & Cloud Cost", "Zero Trust Security"
- **Filter Panel**: Multi-criteria filtering
  - Category dropdown (All Categories, Infrastructure, Security, AI/ML, etc.)
  - Status dropdown (Available, Used, Stale)
  - Last Used dropdown (Any Time, >6 months, >12 months)
  - Partner Interest dropdown (High, Medium, Low)
- **View Options**: Sort and group controls
  - Sort by: Partner Priority, Last Used, Rating, Category
  - Group by: Category, Status, Year
  - View toggle: Heat Map, List, Board
- **Action Buttons**:
  - [+ Add] - Create new topic
  - [Select for Event] - Assign topic to current event
  - [View History] - See complete usage history
  - [Similar Topics] - Show similarity analysis
  - [Partner Feedback] - View partner voting and comments

### Wireframe Status
- ✅ **EXISTS**: Topic Backlog Management wireframe fully documented
  - Complete heat map visualization with 20+ years of usage data
  - Interactive topic selection interface
  - Partner voting integration (from Story 6.4)
  - AI-powered suggestions panel
  - Similarity detection and staleness indicators

### Navigation
**Key navigation paths from this screen:**
- → Topic Details Screen (click topic for full details)
- → Event Detail (assign topic via [Select for Event])
- → Topic Voting Interface (partner feedback - story-6.4-topic-voting.md)
- → Partner Profile (click sponsor name)
- → AI Topic Suggestions Screen (click [See All →])
- ⤴ Event Management Dashboard

**Acceptance Criteria:**

**Topic Management:**
1. **Topic Backlog**: Display searchable list of all available topics
2. **Usage History**: Show when each topic was last used
3. **Duplicate Detection**: Warn if topic used in last 6 months
4. **Topic Creation**: Allow new topic creation with description

**Intelligent Suggestions:**
5. **Recent Usage Check**: Flag topics used in last 3 events
6. **Search Functionality**: Full-text search across topic titles and descriptions
7. **Topic Categories**: Group topics by category for easier selection
8. **Quick Selection**: Popular topics displayed prominently

**Visual Intelligence:**
9. **Heat Map Visualization**: Display topic usage heat map showing frequency over last 24 months
10. **Color-Coded Freshness**: Topics color-coded by staleness (red: <6 months, yellow: 6-12 months, green: >12 months)
11. **Usage Timeline**: Interactive timeline showing when each topic was previously used
12. **Similarity Indicators**: Visual indicators showing similar/related topics in backlog

**ML-Powered Duplicate Avoidance:**
13. **Similarity Scoring**: Calculate similarity scores between topics using TF-IDF and cosine similarity
14. **Duplicate Warnings**: Automatic warnings when similarity score >70% with recent topics
15. **Similar Topic Clusters**: Group similar topics visually to aid decision-making
16. **Semantic Analysis**: Use PostgreSQL full-text search with similarity ranking

**Staleness Detection:**
17. **Wait Period Calculation**: Algorithm calculates recommended wait time based on topic frequency
18. **Staleness Score**: Display 0-100 staleness score (100 = safe to reuse, 0 = too recent)
19. **Reuse Recommendations**: Explicit "Ready to reuse" or "Wait X months" recommendations
20. **Override Capability**: Allow organizers to override warnings with justification

**Technical Implementation:**
9. **Topic Entity**: Create Topic aggregate with usage tracking
10. **REST API**: GET /api/topics with search and filtering
11. **React Component**: TopicSelector with autocomplete
12. **Domain Event**: Publish TopicSelectedEvent when topic chosen

**Additional Technical Requirements:**
13. **Topic Entity Enhancement**: Add fields to Topic aggregate:
    - lastUsedDate: Date
    - usageCount: Integer
    - usageHistory: Date[]
    - similarityScores: Map<TopicId, Float>
    - stalenessScore: Float (0-100)
14. **Similarity Service**: PostgreSQL-based similarity calculation using ts_rank
15. **Heat Map Component**: React component using Recharts library
16. **Domain Event**: Publish TopicSimilarityDetectedEvent when duplicates found

**Definition of Done:**
- [ ] Topic backlog displays all historical topics
- [ ] Usage history prevents duplicate selection
- [ ] Search returns relevant results in <500ms
- [ ] New topics can be created inline
- [ ] Frontend provides intelligent suggestions
- [ ] Topic selection triggers domain event
- [ ] Heat map visualization displays 24-month topic usage history
- [ ] Topics color-coded by staleness (red/yellow/green indicators)
- [ ] Similarity scoring detects topics with >70% similarity
- [ ] Automatic warnings displayed for similar/recent topics
- [ ] Staleness score calculation accurate based on usage patterns
- [ ] Recommended wait periods shown for recently used topics
- [ ] Organizers can override warnings with recorded justification
- [ ] Recharts heat map renders in <500ms
- [ ] Topic similarity calculations complete in <200ms

---

## Story 5.3: Basic Publishing Engine (Workflow Step 11 - Partial)

**User Story:**
As an **organizer**, I want created events with topics to be immediately visible on the public website, so that potential attendees can see upcoming events as soon as they're planned.

**Architecture Integration:**
- **Publishing Service**: Part of Event Management Service
- **Cache**: Redis for published event data
- **CDN**: CloudFront for public content delivery
- **Frontend**: React components for public display

**Wireframe Context:**

### Wireframe References
**From docs/wireframes/sitemap.md:**
- **Main Screen:** `docs/wireframes/story-2.3-basic-publishing-engine.md` ✅
  - Publishing controls interface within Event Detail/Edit screen
  - Content scheduling and preview interface
  - Publishing status dashboard
  - Draft/Published state toggle

### UI Components
**Key interface elements:**
- **Publishing Control Panel**: Status and action controls
  - Current status badge (Draft/Published/Archived)
  - [Publish Event] button (primary action)
  - [Preview] button (see public view)
  - [Unpublish] button (rollback capability)
  - Publishing validation checklist (required fields completed)
- **Publishing Validation Checklist**: Pre-publish requirements
  - ✓ Event title and description
  - ✓ Date and time set
  - ✓ Topics selected
  - ✓ Venue configured
  - ⚠ Missing items highlighted in red
  - Disabled publish button until all requirements met
- **Preview Panel**: Shows public-facing event view
  - Event landing page preview
  - Session list preview
  - Speaker lineup preview
  - [Edit] link to return to event editor
- **Publishing Status Timeline**: Publication history
  - Published date/time
  - Published by (organizer name)
  - Unpublished events (if any)
  - Cache invalidation status
- **CDN Cache Status**: Real-time cache state
  - Cache status indicator (cached/invalidating/fresh)
  - Last invalidation timestamp
  - [Force Cache Clear] button (manual invalidation)
- **Notification Settings**: Publishing notification config
  - Recipients list (organizers, partners)
  - Email template selection
  - Send notification checkbox

### Wireframe Status
- ✅ **EXISTS**: Basic Publishing Engine wireframe fully documented
  - Complete publishing control interface
  - Validation checklist component
  - Preview functionality
  - Status tracking and history

### Navigation
**Key navigation paths from this screen:**
- → Event Preview (public view)
- → Event Detail/Edit (edit before publishing)
- → Publishing Templates (email template editor)
- → Notification Center (view sent notifications)
- ⤴ Event Management Dashboard

**Acceptance Criteria:**

**Publishing Workflow:**
1. **Immediate Publication**: Topics publish immediately upon selection
2. **Event Visibility**: Published events appear on landing page within 1 minute
3. **Draft vs Published**: Clear distinction between draft and published states
4. **Publishing Validation**: Only events with required fields can be published

**Notification Integration:**
5. **Publish Notification**: Trigger notification to subscribed organizers/partners on event publish
6. **Email Template**: Use "event_published" template for notification email
7. **Notification Logging**: Log all sent notifications with delivery tracking

**Content Management:**
5. **Event Preview**: Preview how event will appear before publishing
6. **Publishing Status**: Track publication status (draft/published/archived)
7. **Rollback Capability**: Ability to unpublish if needed
8. **Cache Management**: Clear CDN cache on publication

**Technical Implementation:**
9. **Publishing State Machine**: Implement state transitions for publishing
10. **REST API**: POST /api/events/{id}/publish endpoint
11. **Cache Strategy**: Redis cache with 1-minute TTL for event data
12. **CDN Integration**: CloudFront invalidation on publish

**Definition of Done:**
- [ ] Events publish immediately when ready
- [ ] Published events visible on landing page <1 minute
- [ ] Publishing state properly tracked in database
- [ ] CDN cache properly invalidated on changes
- [ ] Preview functionality works correctly
- [ ] Rollback capability tested and functional
- [ ] Event publication triggers notification to stakeholders
- [ ] Notification delivery tracked and logged

---

## Epic 5 Success Metrics

**Functional Success:**
- ✅ Event creation to publication in <30 minutes
- ✅ 100% of event types supported (full-day, afternoon, evening)
- ✅ Topic selection prevents duplicates effectively
- ✅ Publishing workflow operational

**Technical Performance:**
- **Response Times**: API responses <200ms P95
- **Publishing Speed**: <1 minute from creation to visibility
- **Topic Search**: Results in <500ms
- **System Availability**: >99.5% uptime

**Business Value:**
- **Workflow Automation**: 80% reduction in manual event setup time
- **Topic Intelligence**: Heat map and similarity detection prevent duplicate topics
- **Publishing Efficiency**: Automated CDN cache invalidation and notification triggers
- **Quality Control**: Publishing validation ensures complete event data

This epic delivers enhanced organizer workflows with intelligent topic management, automated publishing, and streamlined event creation processes.