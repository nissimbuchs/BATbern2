# Epic 2: Entity CRUD & Domain Services

## Epic Overview

**Epic Goal**: Deliver end-to-end CRUD functionality for all main platform entities (Company, Event, Speaker, User) with REST APIs and React frontend, enabling complete entity management across all user roles.

**Deliverable**: Complete CRUD operations for Company, Event, Speaker, and User entities with role-based access, React frontend forms, and PostgreSQL persistence.

**Architecture Context**:
- **Backend Services**: Company Management, Event Management, Speaker Coordination (Java 21 + Spring Boot 3.2)
- **Database**: PostgreSQL with proper domain models and indexes
- **Frontend**: React components with role-adaptive CRUD forms
- **Cache**: Redis for entity search and performance
- **Storage**: AWS S3 for logos, photos, and documents

**Duration**: 9 weeks (Weeks 10-18) - includes API consolidation for production-ready RESTful APIs

**Prerequisites**: Epic 1 complete (infrastructure, authentication, monitoring operational)

---

## Story Overview

This epic consolidates stories originally in Epic 1 (1.14-1.20, 1.17) that provide core entity CRUD functionality, **plus API consolidation stories (1.15a.x)** to ensure RESTful APIs are built correctly from the start:

- **Story 2.1 (formerly 1.14)**: Company Management Service Foundation **+ API Consolidation (1.15a.6, 1.15a.2)**
- **Story 2.2 (formerly 1.16)**: Event Management Service Core **+ API Consolidation (1.15a.1)**
- **Story 2.3 (formerly 1.19)**: Speaker Coordination Service Foundation **+ API Consolidation (1.15a.3)**
- **Story 2.4 (formerly 1.20)**: User Role Management **+ API Consolidation (1.15a.7, 1.15a.8)**
- **Story 2.5 (formerly 1.17 partial)**: React Frontend CRUD Foundation (consuming consolidated APIs)

**API Consolidation Integration:** Each microservice story includes its domain-specific API consolidation (1.15a.x), using the foundation from Story 1.15a (already complete in Epic 1). This ensures RESTful patterns are implemented from the start, avoiding technical debt and future refactoring.

**Note:** Full story details for Stories 2.1-2.4 are preserved in the original Epic 1 file (`epic-1-foundation-stories.md`) and individual story files in `docs/stories/`. This epic provides a consolidated view focused on entity CRUD delivery with production-ready APIs.

---

## Story 2.1: Company Management Service Foundation + API Consolidation
**(Formerly Story 1.14, includes 1.15a.6 Companies API + 1.15a.2 Partners API)**

**User Story:**
As a **partner or attendee**, I want my company affiliation to be properly managed and verified through consolidated RESTful APIs, so that I can access company-specific features and analytics efficiently.

**Architecture Integration:**
- **Service**: `company-management-service/` (Java 21 + Spring Boot 3.2)
- **Database**: PostgreSQL with company profiles, employee relationships
- **Storage**: AWS S3 for company logos
- **Cache**: Redis for company search
- **API Foundation**: Uses Story 1.15a utilities (FilterParser, SortParser, PaginationUtils)

**Key Functionality:**
1. Company CRUD operations with Swiss UID validation
2. Employee-company relationship management
3. Partner status toggle with enhanced privileges
4. Logo upload to S3 with CDN integration
5. Company search with Redis-backed autocomplete
6. **Consolidated API**: `GET /api/v1/companies?filter={}&include=employees,partnerships&fields=id,name,uid`
7. **Resource Expansion**: Include employees, partner details, event participation in single API call
8. **Performance**: <200ms response time with caching, reduce multiple API calls to 1-2 calls

---

### Wireframe Reference
**From docs/wireframes/sitemap.md:**
- **Main Screen**: `docs/wireframes/story-1.14-company-management-screen.md` ✅
  - Company list with search and filtering
  - Company detail view with edit capabilities
  - Swiss UID validation display
  - Employee affiliation management
  - Partner status toggle interface
  - Logo upload component

---

**Acceptance Criteria Summary:**
- [ ] Company domain model with DDD patterns
- [ ] **Consolidated REST API** implementing Stories 1.15a.6 + 1.15a.2 patterns
- [ ] OpenAPI documentation for all endpoints (basic + consolidated)
- [ ] Swiss UID validation integrated
- [ ] Company search with Redis caching
- [ ] S3 logo storage with CDN
- [ ] Domain events published to EventBridge
- [ ] **API Consolidation**: Support `?include=employees,partnerships,events` for resource expansion
- [ ] **Performance**: Company detail with all includes <200ms P95
- [ ] Integration tests verify all workflows including consolidated APIs

**Estimated Duration:** 2.5 weeks (includes API consolidation implementation)

**References:**
- Core functionality: `docs/prd/epic-1-foundation-stories.md` Story 1.14
- API consolidation: `docs/stories/1.15a.6.companies-api-consolidation.md`, `docs/stories/1.15a.2.partners-api-consolidation.md`

---

## Story 2.2: Event Management Service Core + API Consolidation
**(Formerly Story 1.16, includes 1.15a.1 Events API Consolidation)**

**User Story:**
As an **organizer**, I want to access and manage events through consolidated RESTful APIs that load all event data efficiently, so that I can plan conferences without making dozens of API calls.

**Architecture Integration:**
- **Service**: `event-management-service/` (Java 21 + Spring Boot 3.2)
- **Database**: PostgreSQL with event aggregates
- **Cache**: Redis for event data caching (15min TTL for expanded resources)
- **Events**: Domain events published to EventBridge
- **API Foundation**: Uses Story 1.15a utilities for filtering, sorting, pagination, field selection

**Key Functionality:**
1. Event CRUD operations with consolidated API patterns
2. Event types support (full-day, afternoon, evening)
3. Basic event status tracking
4. Event timeline with deadlines
5. **Resource Expansion**: `?include=venue,speakers,sessions,topics` reduces 30 API calls to 1
6. **Advanced Filtering**: JSON filter syntax for complex event searches
7. **Performance**: Event detail with all includes <500ms P95
8. **EXCLUDES**: 16-step workflow automation (deferred to Epic 5)

---

### Wireframe References
**From docs/wireframes/sitemap.md:**

1. **Event Management Dashboard**: `docs/wireframes/story-1.16-event-management-dashboard.md` ✅
   - Event list with status indicators
   - Quick filters and search
   - Event cards with key metrics
   - Create new event button

2. **Event Detail/Edit**: `docs/wireframes/story-1.16-event-detail-edit.md` ✅
   - Comprehensive event editing interface
   - Event type selection
   - Timeline and deadlines management
   - Venue, speakers, sessions configuration

3. **Event Settings**: `docs/wireframes/story-1.16-event-settings.md` ✅
   - Event configuration panel
   - Status management
   - Advanced settings

4. **Workflow Visualization**: `docs/wireframes/story-1.16-workflow-visualization.md` ✅
   - Event lifecycle visualization
   - Status transitions
   - Milestone tracking

---

**Acceptance Criteria Summary:**
- [ ] Event aggregate with DDD patterns
- [ ] **Consolidated REST API** implementing Story 1.15a.1 patterns (25 endpoints vs. 130 fragmented)
- [ ] OpenAPI documentation for all consolidated endpoints
- [ ] Event types configuration
- [ ] PostgreSQL schema with indexes
- [ ] Redis caching for performance (15min TTL)
- [ ] **API Consolidation**: Support `?include=venue,speakers,sessions,topics,registrations` for resource expansion
- [ ] **Advanced Search**: Filter by status, date, topic with JSON filter syntax
- [ ] **Performance**: List <100ms, detail <150ms, detail+includes <500ms (all P95)
- [ ] Domain events publishing
- [ ] Integration tests for all operations including consolidated APIs

**Estimated Duration:** 3 weeks (includes API consolidation implementation)

**References:**
- Core functionality: `docs/prd/epic-1-foundation-stories.md` Story 1.16
- API consolidation: `docs/stories/1.15a.1.events-api-consolidation.md` (81% endpoint reduction, 90% fewer HTTP requests)

**Important Scope Note:** This story focuses on CRUD with consolidated APIs. The 16-step workflow engine, topic management, and publishing automation are deferred to Epic 5 (Enhanced Organizer Workflows).

---

## Story 2.3: Speaker Coordination Service Foundation + API Consolidation
**(Formerly Story 1.19, includes 1.15a.3 Speakers API Consolidation)**

**User Story:**
As an **organizer**, I want the foundational Speaker Coordination Service with consolidated RESTful APIs, so that I can manage speaker profiles efficiently without multiple API calls.

**Architecture Integration:**
- **Service**: `speaker-coordination-service/` (Java 21 + Spring Boot 3.2)
- **Database**: PostgreSQL with speaker domain schema
- **Storage**: S3 for speaker photos and CVs
- **Cache**: Redis for speaker session data (10min TTL for expanded resources)
- **API Foundation**: Uses Story 1.15a utilities for filtering, sorting, pagination

**Key Functionality:**
1. Speaker CRUD operations with consolidated API patterns
2. Speaker profile management
3. Basic speaker-event associations
4. Speaker photo and CV storage
5. **Resource Expansion**: `?include=events,sessions,companies` for speaker history in single call
6. **Advanced Search**: Filter speakers by expertise, company, past participation
7. **Performance**: Speaker detail with history <300ms P95
8. **EXCLUDES**: Invitation workflows, material submission portal (deferred to Epic 6)

**Acceptance Criteria Summary:**
- [ ] Speaker aggregate with profile management
- [ ] **Consolidated REST API** implementing Story 1.15a.3 patterns
- [ ] OpenAPI documentation for all consolidated endpoints
- [ ] Database schema with Flyway migrations
- [ ] S3 integration for photos/CVs
- [ ] **API Consolidation**: Support `?include=events,sessions,companies,topics` for resource expansion
- [ ] **Advanced Search**: Filter by expertise, company, availability with JSON filter syntax
- [ ] **Performance**: List <100ms, detail <150ms, detail+includes <300ms (all P95)
- [ ] Domain events publishing
- [ ] Integration tests covering main workflows including consolidated APIs

**Estimated Duration:** 2.5 weeks (includes API consolidation implementation)

**References:**
- Core functionality: `docs/prd/epic-1-foundation-stories.md` Story 1.19
- API consolidation: `docs/stories/1.15a.3.speakers-api-consolidation.md`

**Important Scope Note:** This story provides speaker entity CRUD with consolidated APIs. Invitation emails, material submission workflows, and speaker portal are deferred to Epic 6 (Speaker Portal & Support).

---

## Story 2.4: User Role Management + API Consolidation
**(Formerly Story 1.20, includes 1.15a.7 Users API + 1.15a.8 Organizers API)**

**User Story:**
As an **organizer**, I want to manage user roles through consolidated RESTful APIs with efficient filtering and role-specific views, so that I can build teams without administrator intervention or multiple API calls.

**Architecture Integration:**
- **Service**: User Management Service or API Gateway authentication layer extension
- **Database**: PostgreSQL with role management tables
- **Integration**: AWS Cognito for role attribute updates
- **Cache**: Redis for user search and role lookups
- **API Foundation**: Uses Story 1.15a utilities for filtering, sorting, pagination
- **Frontend**: React role management interface

**Key Functionality:**
1. User CRUD operations with role-based access
2. Role promotion workflows (Attendee → Speaker, Attendee → Organizer)
3. Role demotion workflows with approval
4. Business rules enforcement (minimum 2 organizers)
5. Complete audit trail
6. **Consolidated API**: `GET /api/v1/users?filter={"role":"organizer"}&include=events,companies`
7. **Role-Specific Endpoints**: `GET /api/v1/organizers`, `GET /api/v1/attendees`, etc.
8. **Performance**: User list with filters <150ms P95

**Acceptance Criteria Summary:**
- [ ] **Consolidated REST API** implementing Stories 1.15a.7 + 1.15a.8 patterns
- [ ] OpenAPI documentation for all user and role endpoints
- [ ] Role promotion API endpoints
- [ ] Role demotion workflow with approval
- [ ] Minimum 2 organizers rule enforced
- [ ] Cognito custom attributes sync
- [ ] Complete audit trail
- [ ] **API Consolidation**: Support `?include=events,registrations,companies` for user context
- [ ] **Advanced Filtering**: Filter users by role, company, participation history with JSON syntax
- [ ] **Performance**: User list <100ms, user detail+includes <150ms (P95)
- [ ] Frontend role management interface consuming consolidated APIs
- [ ] Integration tests for all scenarios including consolidated APIs

**Estimated Duration:** 2 weeks (includes API consolidation implementation)

**References:**
- Core functionality: `docs/prd/epic-1-foundation-stories.md` Story 1.20
- API consolidation: `docs/stories/1.15a.7.users-api-consolidation.md`, `docs/stories/1.15a.8.organizers-api-consolidation.md`

---

## Story 2.5: React Frontend CRUD Foundation
**(Formerly Story 1.17 partial)**

**User Story:**
As a **user of any role**, I want to access CRUD forms for all main entities through a role-adaptive React interface, so that I can efficiently manage companies, events, speakers, and users.

**Architecture Integration:**
- **Frontend**: React 18.2+ with TypeScript
- **State Management**: Zustand + React Query
- **UI Framework**: Material-UI (MUI) 5.14+
- **Build**: Vite 5.0+

**Key Functionality:**
1. Role-adaptive navigation (Organizer/Speaker/Partner/Attendee views)
2. Company CRUD forms with Swiss UID validation
3. Event CRUD forms with type selection
4. Speaker CRUD forms with photo/CV upload
5. User role management interface
6. Form validation and error handling
7. **EXCLUDES**: Advanced features (workflow automation, speaker portal, PWA)

---

### Wireframe References
**From docs/wireframes/sitemap.md:**

This story implements the frontend consuming all entity CRUD APIs. Wireframes are organized by entity:

1. **Company Management**: `docs/wireframes/story-1.14-company-management-screen.md` ✅
   - Company list, search, and filters
   - Company create/edit forms
   - Swiss UID validation UI
   - Employee affiliation interface

2. **Event Management**: `docs/wireframes/story-1.16-*.md` ✅
   - Event Management Dashboard
   - Event Detail/Edit screen
   - Event Settings panel
   - Workflow Visualization (read-only in this story)

3. **Main Navigation**: `docs/wireframes/story-1.17-main-navigation.md` ✅
   - Role-adaptive navigation bar
   - User menu and profile access
   - Breadcrumb navigation
   - Mobile responsive menu

**Note**: Speaker and User wireframes exist in Epic 1 but are simpler CRUD forms without dedicated wireframe files for this story.

---

**Acceptance Criteria Summary:**
- [ ] Role-adaptive navigation operational
- [ ] CRUD forms for Company, Event, Speaker, User
- [ ] Authentication integration with auto token refresh
- [ ] API client layer for all microservices
- [ ] Responsive design (mobile/tablet/desktop)
- [ ] Component test coverage >80%
- [ ] Performance meets Core Web Vitals

**Estimated Duration:** 2.5 weeks

**Reference:** See `docs/prd/epic-1-foundation-stories.md` Story 1.17 for full details

**Important Scope Note:** This story focuses on basic CRUD forms only. Advanced UI features (speaker dashboard, communication hub, mobile PWA) are deferred to Epic 6 and Epic 7.

---

## Epic 2 Success Metrics

**Functional Success (End of Week 18):**
- ✅ All entities have complete CRUD operations (Company, Event, Speaker, User)
- ✅ **Consolidated RESTful APIs** operational for all entities (Stories 1.15a.1-1.15a.8)
- ✅ 100% of CRUD and consolidated APIs documented in OpenAPI specifications
- ✅ Role-based access control operational for all entities
- ✅ React frontend with role-adaptive navigation deployed
- ✅ Company Swiss UID validation integrated and working
- ✅ Event types (full-day, afternoon, evening) supported
- ✅ **Resource expansion** working (reduces multi-API calls to 1-2 calls per page load)
- ✅ **Advanced filtering** with JSON filter syntax operational

**Technical Performance:**
- **API Response Times**:
  - Basic CRUD: <200ms P95 for all operations
  - Event detail with includes: <500ms P95
  - Company/Speaker detail with includes: <200-300ms P95
  - User list with filters: <150ms P95
- **API Efficiency**: 80-90% reduction in HTTP requests per page load (via `?include=` expansion)
- **Search Performance**: Company/speaker/event search <500ms with advanced filters
- **Frontend Load Time**: Initial load <2.5s, subsequent <1s (improved via API consolidation)
- **Database Queries**: Optimized with proper indexes, <100ms P95
- **Cache Hit Rate**: >70% for frequently accessed entities, 15min TTL for expanded resources
- **Test Coverage**: >85% backend, >80% frontend components

**Business Value:**
- **Entity Management**: Organizers can manage all entities end-to-end
- **Data Integrity**: Swiss UID validation ensures company data quality
- **User Experience**: Role-adaptive UI provides streamlined workflows
- **Foundation Ready**: Platform prepared for data migration (Epic 3) and public website (Epic 4)
- **Demonstrable Progress**: Stakeholder demos showing functional CRUD operations

**Integration Verification:**
- [ ] All microservices communicate via API Gateway
- [ ] Domain events properly published to EventBridge
- [ ] Authentication working across all services
- [ ] Correlation IDs propagate through all requests
- [ ] Error handling consistent across all APIs
- [ ] Monitoring dashboards show all service health

---

## Dependencies & Prerequisites

**Required Before Starting Epic 2:**
- ✅ Epic 1 Stories 1.1-1.7 complete (infrastructure foundation)
- ✅ Epic 1 Stories 1.9, 1.11 complete (error handling, security)
- ✅ API Gateway operational with authentication
- ✅ PostgreSQL databases provisioned for all services
- ✅ Redis clusters operational
- ✅ S3 buckets configured with CDN

**Enables Following Epics:**
- **Epic 3**: Historical Data Migration (requires entity APIs to import data)
- **Epic 4**: Public Website (requires Event Management APIs for public display)
- **Epic 5**: Enhanced Organizer Workflows (builds on Event Management Service)
- **Epic 6**: Speaker Portal (builds on Speaker Coordination Service)

---

## Implementation Sequence

**Week 10-11: Backend Services (Parallel)**
- Story 2.1: Company Management Service + API Consolidation (2.5 weeks)
- Story 2.2: Event Management Service + API Consolidation (3 weeks) - start parallel
- Story 2.3: Speaker Coordination Service + API Consolidation (2.5 weeks) - start parallel

**Week 12-13: User Management & Integration**
- Story 2.4: User Role Management + API Consolidation (2 weeks)
- Integration testing across all backend services (0.5 weeks)

**Week 14-18: Frontend Development**
- Story 2.5: React Frontend CRUD Foundation consuming consolidated APIs (3 weeks)
- End-to-end testing and bug fixes (2 weeks)

**Rationale:**
- Backend services can be developed in parallel by different developers
- API consolidation adds ~0.5 weeks per service (worth it to avoid future refactoring)
- Frontend development benefits from consolidated APIs (fewer integration points)
- Extra time for frontend reflects complexity of consuming resource expansion APIs

---

## Risk Management

**Technical Risks:**
- **Risk**: Swiss UID API integration may have rate limits or downtime
  - **Mitigation**: Implement caching and fallback validation logic
- **Risk**: Performance issues with complex entity relationships
  - **Mitigation**: Database query optimization, proper indexing, Redis caching
- **Risk**: Frontend state management complexity with multiple entities
  - **Mitigation**: Use React Query for server state, Zustand for client state with clear boundaries

**Schedule Risks:**
- **Risk**: Backend services taking longer than estimated 2 weeks each
  - **Mitigation**: Simplify to core CRUD only, defer advanced features to later epics
  - **Mitigation**: Allocate buffer time in Week 17 for bug fixes
- **Risk**: Integration issues between services
  - **Mitigation**: Comprehensive integration tests, API contract testing

---

## Transition to Epic 3

**Epic 2 Exit Criteria:**
- [ ] All 5 stories (2.1-2.5) marked as complete
- [ ] All acceptance criteria verified
- [ ] Integration tests passing across all services
- [ ] Frontend deployed and accessible
- [ ] Performance benchmarks met
- [ ] Security scan showing zero critical vulnerabilities
- [ ] Stakeholder demo completed successfully

**Handoff to Epic 3 (Historical Data Migration):**
- Entity CRUD APIs operational and ready to receive migrated data
- Database schemas finalized and stable
- S3 storage configured for historical content
- Search indexing infrastructure ready

---

## Notes & References

**Original Story Locations:**
- Story 2.1-2.4 full details: `docs/prd/epic-1-foundation-stories.md` (archived sections)
- Individual story files: `docs/stories/1.14-*.md`, `docs/stories/1.16-*.md`, etc.
- Wireframes: `docs/wireframes/story-1.14-*.md`, `docs/wireframes/story-1.16-*.md`, etc.

**Epic Reorganization Context:**
- Date: 2025-10-12
- Reason: CRUD-first strategy to accelerate functional delivery
- Impact: Public website delivery accelerated from Week 38+ to Week 25
- Preserves: All functional requirements from original PRD

---

*This epic was created as part of the epic reorganization on 2025-10-12 to prioritize entity CRUD functionality before workflow automation.*
