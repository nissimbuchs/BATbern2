# Epic 2: Basic Event Creation & Publishing - Vertical Slice Stories

## Epic Overview

**Epic Goal**: Deliver minimal viable event management functionality that allows organizers to create events with topics and immediately publish them to a public landing page, providing immediate value to the platform.

**Deliverable**: Organizers can create events, select topics, and have them immediately visible on a public landing page - achieving end-to-end event visibility in <30 minutes.

**Architecture Context**:
- **Core Service**: Event Management Service (Java 21 + Spring Boot 3.2)
- **Frontend**: React components for event creation and public landing page
- **Publishing**: Basic publishing engine with immediate topic publication
- **Infrastructure**: AWS CloudFront CDN for landing page delivery

**Duration**: 8 weeks (Weeks 13-20)

---

## Story 2.1: Event Type Definition (Workflow Step 1)

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

## Story 2.2: Topic Selection System (Workflow Step 2)

**User Story:**
As an **organizer**, I want to select topics from our backlog with intelligent suggestions, so that I can choose compelling topics while avoiding recent duplicates.

**Architecture Integration:**
- **Service**: Event Management Service with topic management
- **Database**: PostgreSQL topics table with usage history
- **AI/ML**: Basic similarity detection using PostgreSQL full-text search
- **Frontend**: React topic selector with search and suggestions

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

**Technical Implementation:**
9. **Topic Entity**: Create Topic aggregate with usage tracking
10. **REST API**: GET /api/topics with search and filtering
11. **React Component**: TopicSelector with autocomplete
12. **Domain Event**: Publish TopicSelectedEvent when topic chosen

**Definition of Done:**
- [ ] Topic backlog displays all historical topics
- [ ] Usage history prevents duplicate selection
- [ ] Search returns relevant results in <500ms
- [ ] New topics can be created inline
- [ ] Frontend provides intelligent suggestions
- [ ] Topic selection triggers domain event

---

## Story 2.3: Basic Publishing Engine (Workflow Step 11 - Partial)

**User Story:**
As an **organizer**, I want created events with topics to be immediately visible on the public website, so that potential attendees can see upcoming events as soon as they're planned.

**Architecture Integration:**
- **Publishing Service**: Part of Event Management Service
- **Cache**: Redis for published event data
- **CDN**: CloudFront for public content delivery
- **Frontend**: React components for public display

**Acceptance Criteria:**

**Publishing Workflow:**
1. **Immediate Publication**: Topics publish immediately upon selection
2. **Event Visibility**: Published events appear on landing page within 1 minute
3. **Draft vs Published**: Clear distinction between draft and published states
4. **Publishing Validation**: Only events with required fields can be published

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

---

## Story 2.4: Current Event Landing Page

**User Story:**
As an **attendee**, I want to see the current/upcoming BATbern event prominently displayed with all key information, so that I can quickly understand event details and decide to attend.

**Architecture Integration:**
- **Frontend**: React landing page with server-side rendering
- **Backend**: Attendee Experience Service (basic implementation)
- **CDN**: CloudFront for global content delivery
- **Cache**: Redis for event data caching

**Acceptance Criteria:**

**Landing Page Components:**
1. **Hero Section**: Prominent display of next event with title and date
2. **Event Details**: Clear display of:
   - Date and time
   - Location with map link
   - "Free attendance" badge
   - Topic description
3. **Call-to-Action**: Registration button (placeholder for now)
4. **Countdown Timer**: Days until event (if within 30 days)

**Technical Requirements:**
5. **Responsive Design**: Mobile-first responsive layout
6. **Performance**: Page loads in <1.5 seconds globally
7. **SEO Optimization**: Proper meta tags and structured data
8. **Social Sharing**: Open Graph tags for social media

**Infrastructure:**
9. **CloudFront Setup**: CDN distribution for EU regions
10. **S3 Static Assets**: Images and CSS served from S3
11. **Redis Caching**: Event data cached with 1-minute TTL
12. **Health Monitoring**: CloudWatch alarms for availability

**Definition of Done:**
- [ ] Landing page displays current event prominently
- [ ] All event logistics clearly visible
- [ ] Mobile responsive design implemented
- [ ] Page loads <1.5 seconds from CloudFront
- [ ] SEO meta tags properly configured
- [ ] Social sharing produces rich previews
- [ ] >99% uptime monitoring in place

---

## Epic 2 Success Metrics

**Functional Success:**
- ✅ Event creation to publication in <30 minutes
- ✅ Landing page live with current event information
- ✅ 100% of event types supported (full-day, afternoon, evening)
- ✅ Topic selection prevents duplicates effectively

**Technical Performance:**
- **Response Times**: API responses <200ms P95
- **Publishing Speed**: <1 minute from creation to visibility
- **Landing Page**: <1.5 second load time globally
- **Availability**: >99.5% uptime for public page

**Business Value:**
- **Immediate Visibility**: Events visible to public immediately
- **Reduced Manual Work**: No manual website updates needed
- **Professional Presence**: Modern, responsive event landing page
- **Foundation Ready**: Infrastructure ready for speaker management (Epic 3)

This epic delivers the first truly functional vertical slice where organizers can create and publish events that attendees can immediately see, establishing the core event management → public visibility flow.