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

**Duration**: 8 weeks (Weeks 10-17)

**Prerequisites**: Epic 1 complete (infrastructure, authentication, monitoring operational)

---

## Story Overview

This epic consolidates stories originally in Epic 1 (1.14-1.20, 1.17) that provide core entity CRUD functionality:

- **Story 2.1 (formerly 1.14)**: Company Management Service Foundation
- **Story 2.2 (formerly 1.16)**: Event Management Service Core Implementation
- **Story 2.3 (formerly 1.19)**: Speaker Coordination Service Foundation
- **Story 2.4 (formerly 1.20)**: User Role Management & Promotion
- **Story 2.5 (formerly 1.17 partial)**: React Frontend CRUD Foundation

**Note:** Full story details for Stories 2.1-2.4 are preserved in the original Epic 1 file (`epic-1-foundation-stories.md`) and individual story files in `docs/stories/`. This epic provides a consolidated view focused on entity CRUD delivery.

---

## Story 2.1: Company Management Service Foundation
**(Formerly Story 1.14)**

**User Story:**
As a **partner or attendee**, I want my company affiliation to be properly managed and verified, so that I can access company-specific features and analytics while maintaining data integrity.

**Architecture Integration:**
- **Service**: `company-management-service/` (Java 21 + Spring Boot 3.2)
- **Database**: PostgreSQL with company profiles, employee relationships
- **Storage**: AWS S3 for company logos
- **Cache**: Redis for company search

**Key Functionality:**
1. Company CRUD operations with Swiss UID validation
2. Employee-company relationship management
3. Partner status toggle with enhanced privileges
4. Logo upload to S3 with CDN integration
5. Company search with Redis-backed autocomplete

**Acceptance Criteria Summary:**
- [ ] Company domain model with DDD patterns
- [ ] REST API with OpenAPI documentation
- [ ] Swiss UID validation integrated
- [ ] Company search with Redis caching
- [ ] S3 logo storage with CDN
- [ ] Domain events published to EventBridge
- [ ] Integration tests verify all workflows

**Estimated Duration:** 2 weeks

**Reference:** See `docs/prd/epic-1-foundation-stories.md` Story 1.14 for full details

---

## Story 2.2: Event Management Service Core Implementation
**(Formerly Story 1.16)**

**User Story:**
As an **organizer**, I want to access and manage events through a robust service that handles event CRUD operations, so that I can efficiently plan and coordinate BATbern conferences.

**Architecture Integration:**
- **Service**: `event-management-service/` (Java 21 + Spring Boot 3.2)
- **Database**: PostgreSQL with event aggregates
- **Cache**: Redis for event data caching
- **Events**: Domain events published to EventBridge

**Key Functionality:**
1. Event CRUD operations
2. Event types support (full-day, afternoon, evening)
3. Basic event status tracking
4. Event timeline with deadlines
5. **EXCLUDES**: 16-step workflow automation (deferred to Epic 5)

**Acceptance Criteria Summary:**
- [ ] Event aggregate with DDD patterns
- [ ] REST API with CQRS pattern
- [ ] Event types configuration
- [ ] PostgreSQL schema with indexes
- [ ] Redis caching for performance
- [ ] Domain events publishing
- [ ] Integration tests for all operations

**Estimated Duration:** 2 weeks

**Reference:** See `docs/prd/epic-1-foundation-stories.md` Story 1.16 for full details

**Important Scope Note:** This story focuses on basic CRUD and event types only. The 16-step workflow engine, topic management, and publishing automation are deferred to Epic 5 (Enhanced Organizer Workflows).

---

## Story 2.3: Speaker Coordination Service Foundation
**(Formerly Story 1.19)**

**User Story:**
As an **organizer**, I want the foundational Speaker Coordination Service deployed with core domain models and APIs, so that we can manage speaker profiles and basic coordination.

**Architecture Integration:**
- **Service**: `speaker-coordination-service/` (Java 21 + Spring Boot 3.2)
- **Database**: PostgreSQL with speaker domain schema
- **Storage**: S3 for speaker photos and CVs
- **Cache**: Redis for speaker session data

**Key Functionality:**
1. Speaker CRUD operations
2. Speaker profile management
3. Basic speaker-event associations
4. Speaker photo and CV storage
5. **EXCLUDES**: Invitation workflows, material submission portal (deferred to Epic 6)

**Acceptance Criteria Summary:**
- [ ] Speaker aggregate with profile management
- [ ] REST API with OpenAPI documentation
- [ ] Database schema with Flyway migrations
- [ ] S3 integration for photos/CVs
- [ ] Domain events publishing
- [ ] Integration tests covering main workflows

**Estimated Duration:** 2 weeks

**Reference:** See `docs/prd/epic-1-foundation-stories.md` Story 1.19 for full details

**Important Scope Note:** This story provides speaker entity CRUD only. Invitation emails, material submission workflows, and speaker portal are deferred to Epic 6 (Speaker Portal & Support).

---

## Story 2.4: User Role Management & Promotion
**(Formerly Story 1.20)**

**User Story:**
As an **organizer**, I want to manage user roles with promotion and demotion capabilities, so that I can build and maintain my event team without requiring administrator intervention.

**Architecture Integration:**
- **Service**: User Management Service or API Gateway authentication layer extension
- **Database**: PostgreSQL with role management tables
- **Integration**: AWS Cognito for role attribute updates
- **Frontend**: React role management interface

**Key Functionality:**
1. User CRUD operations with role-based access
2. Role promotion workflows (Attendee → Speaker, Attendee → Organizer)
3. Role demotion workflows with approval
4. Business rules enforcement (minimum 2 organizers)
5. Complete audit trail

**Acceptance Criteria Summary:**
- [ ] Role promotion API endpoints
- [ ] Role demotion workflow with approval
- [ ] Minimum 2 organizers rule enforced
- [ ] Cognito custom attributes sync
- [ ] Complete audit trail
- [ ] Frontend role management interface
- [ ] Integration tests for all scenarios

**Estimated Duration:** 1.5 weeks

**Reference:** See `docs/prd/epic-1-foundation-stories.md` Story 1.20 for full details

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

**Functional Success (End of Week 17):**
- ✅ All entities have complete CRUD operations (Company, Event, Speaker, User)
- ✅ 100% of CRUD APIs documented in OpenAPI specifications
- ✅ Role-based access control operational for all entities
- ✅ React frontend with role-adaptive navigation deployed
- ✅ Company Swiss UID validation integrated and working
- ✅ Event types (full-day, afternoon, evening) supported

**Technical Performance:**
- **API Response Times**: <200ms P95 for all CRUD operations
- **Search Performance**: Company/speaker search <500ms
- **Frontend Load Time**: Initial load <2.5s, subsequent <1s
- **Database Queries**: Optimized with proper indexes, <100ms P95
- **Cache Hit Rate**: >70% for frequently accessed entities
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
- Story 2.1: Company Management Service (2 weeks)
- Story 2.2: Event Management Service (2 weeks) - start parallel
- Story 2.3: Speaker Coordination Service (2 weeks) - start parallel

**Week 12-13: User Management & Integration**
- Story 2.4: User Role Management (1.5 weeks)
- Integration testing across all backend services (0.5 weeks)

**Week 14-17: Frontend Development**
- Story 2.5: React Frontend CRUD Foundation (2.5 weeks)
- End-to-end testing and bug fixes (1.5 weeks)

**Rationale:** Backend services can be developed in parallel by different developers. Frontend development begins after backend APIs are stable, ensuring smooth integration.

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
