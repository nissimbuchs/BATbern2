# Epic 2: Entity CRUD & Domain Services

## Status
In progress

## Epic Overview

**Epic Goal**: Deliver end-to-end CRUD functionality for all main platform entities (Company, Event, Speaker, User, Partner) with REST APIs and React frontend, enabling complete entity management across all user roles.

**Deliverable**: Complete CRUD operations for Company, Event, Speaker, User, and Partner entities with role-based access, React frontend forms, and PostgreSQL persistence.

**Architecture Context**:
- **Backend Services**: Company Management, Event Management, Speaker Coordination, Partner Coordination (Java 21 + Spring Boot 3.2)
- **Database**: PostgreSQL with proper domain models and indexes
- **Frontend**: React components with role-adaptive CRUD forms
- **Cache**: Caffeine for entity search and performance
- **Storage**: AWS S3 for logos, photos, and documents
- **Service Integration**: HTTP-based communication following ADR-003 (meaningful IDs, JWT propagation)

**Duration**: 9 weeks (Weeks 10-18) - includes API consolidation for production-ready RESTful APIs

**Prerequisites**: Epic 1 complete (infrastructure, authentication, monitoring operational)

---

## 📋 Epic 2 Status Summary

**✅ Completed Stories:**
- ✅ **Story 2.1**: Company Management Service Foundation + API Consolidation (1.15a.6)
- ✅ **Story 2.1b**: User Management Service Foundation + API Consolidation (1.15a.7, 1.15a.8)
- ✅ **Story 2.2**: Event Management Service Core + Architecture Compliance (refactored from 1.15a.1) - **Done**
- ✅ **Story 2.4**: User Role Management + API Consolidation (implemented via Stories 2.1b + 2.5.2)

- ✅ **Story 2.5**: React Frontend CRUD Foundation
  - ✅ **Story 1.17**: React Frontend Foundation
  - ✅ **Story 2.5.1**: Company Management Frontend
  - ✅ **Story 2.5.2**: User Management Frontend (76/76 tests passing, all acceptance criteria met)
  - ✅ **Story 2.5.3**: Event Management Frontend

- ✅ **Story 2.6**: User Account Management Frontend (Basic Profile + Settings) - **Accepted**
- ✅ **Story 2.7**: Partner Coordination Service Foundation + API Consolidation (1.15a.2) - **Done**

**📝 Not Started:**
- **Story 2.3**: Speaker Coordination Service Foundation + API Consolidation (1.15a.3)
- **Story 2.8**: Partner Management Frontend (NEW - completes CRUD pattern for all core entities)

**Progress:** 7/9 stories complete (77.8%)

---

## Story Overview

This epic consolidates stories originally in Epic 1 (1.14-1.20, 1.17) that provide core entity CRUD functionality, **plus API consolidation stories (1.15a.x)** to ensure RESTful APIs are built correctly from the start:

- ✅ **Story 2.1 (formerly 1.14)**: Company Management Service Foundation **+ API Consolidation (1.15a.6, 1.15a.2)** - **Done**
- ✅ **Story 2.1b (formerly 1.14-2)**: User Management Service Foundation **+ API Consolidation (1.15a.7, 1.15a.8)** - **Done**
- ✅ **Story 2.2 (formerly 1.16)**: Event Management Service Core **+ Architecture Compliance (refactored from 1.15a.1)** - **Done**
- **Story 2.3 (formerly 1.19)**: Speaker Coordination Service Foundation **+ API Consolidation (1.15a.3)** - **Not Started**
- ✅ **Story 2.4 (formerly 1.20)**: User Role Management **+ API Consolidation** - **Done** (implemented via Stories 2.1b + 2.5.2)
- ✅ **Story 2.5 (formerly 1.17 partial)**: React Frontend CRUD Foundation (consuming consolidated APIs) - **Done**
- ✅ **Story 2.6**: User Account Management Frontend (Basic Profile + Settings) - **Accepted** (consolidates story-1.20 + story-5.2 basic features)
- ✅ **Story 2.7**: Partner Coordination Service Foundation **+ API Consolidation (1.15a.2)** - **Done**
- **Story 2.8**: Partner Management Frontend - **Not Started** (NEW - uses wireframes story-6.3-partner-directory.md, story-6.3-partner-detail.md)

**API Consolidation Integration:** Each microservice story includes its domain-specific API consolidation (1.15a.x), using the foundation from Story 1.15a (already complete in Epic 1). This ensures RESTful patterns are implemented from the start, avoiding technical debt and future refactoring.

**Note:** Full story details for Stories 2.1-2.4 are preserved in the original Epic 1 file (`epic-1-foundation-stories.md`) and individual story files in `docs/stories/`. This epic provides a consolidated view focused on entity CRUD delivery with production-ready APIs.

---

## Story 2.1: Company Management Service Foundation + API Consolidation ✅
**(Formerly Story 1.14, includes 1.15a.6 Companies API)**

**Status:** Done

**Note:** User Management extracted to Story 2.1b for separation of concerns. Partner-specific logic removed and deferred to Epic 8 (Partner Coordination).

**User Story:**
As a **user of any role**, I want my company affiliation to be properly managed and verified through consolidated RESTful APIs, so that domain services can access company data efficiently.

**Architecture Integration:**
- **Service**: `company-user-management-service/` (Java 21 + Spring Boot 3.2)
- **Database**: PostgreSQL with company profiles, employee relationships
- **Storage**: AWS S3 for company logos
- **Cache**: Caffeine for company search
- **API Foundation**: Uses Story 1.15a utilities (FilterParser, SortParser, PaginationUtils)

**Key Functionality:**
1. Company CRUD operations with Swiss UID validation
2. Employee-company relationship management
3. Logo upload to S3 with CDN integration
4. Company search with Caffeine-backed autocomplete
5. **Consolidated API**: `GET /api/v1/companies?filter={}&include=employees&fields=id,name,uid`
6. **Resource Expansion**: Include employees, statistics, logo in single API call
7. **Performance**: <200ms response time with caching, reduce multiple API calls to 1-2 calls

**Note**: Partner status management moved to Epic 8 (Partner Coordination Service) as it's a domain-specific concern, not foundational company data.

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
- [ ] **Consolidated REST API** implementing Story 1.15a.6 patterns
- [ ] OpenAPI documentation for all endpoints (basic + consolidated)
- [ ] Swiss UID validation integrated
- [ ] Company search with Caffeine caching
- [ ] S3 logo storage with CDN
- [ ] Domain events published to EventBridge
- [ ] **API Consolidation**: Support `?include=employees,statistics,logo` for resource expansion
- [ ] **Performance**: Company detail with all includes <200ms P95
- [ ] Integration tests verify all workflows including consolidated APIs

**Estimated Duration:** 2 weeks (reduced from 2.5 weeks due to partner logic removal)

**References:**
- Core functionality: `docs/prd/epic-1-foundation-stories.md` Story 1.14
- Story file: `docs/stories/1.14.company-management-service-foundation.md`
- API consolidation: `docs/stories/1.15a.6.companies-api-consolidation.md`
- Partner APIs moved to: `docs/stories/1.15a.2.partners-api-consolidation.md` (Epic 8)

---

## Story 2.1b: User Management Service Foundation + API Consolidation ✅
**(Story 1.14-2, includes 1.15a.7 Users API + 1.15a.8 Organizers API)**

**Status:** Done

**User Story:**
As a **user of any role**, I want my user profile, preferences, and settings managed through consolidated RESTful APIs, so that I can efficiently manage my account and domain services can integrate with user data.

**Architecture Integration:**
- **Service**: `user-management-service/` (Java 21 + Spring Boot 3.2)
- **Database**: PostgreSQL with user profiles, preferences, roles, activity history
- **Storage**: AWS S3 for profile pictures with CloudFront
- **Cache**: Caffeine for user search and session caching (10min TTL)
- **Integration**: AWS Cognito for authentication sync
- **API Foundation**: Uses Story 1.15a utilities (FilterParser, SortParser, PaginationUtils, FieldSelector, IncludeParser)

**Key Functionality:**
1. User CRUD operations with consolidated API patterns
2. User preferences and settings management
3. Profile picture upload to S3 with CloudFront
4. Role management with business rules (minimum 2 organizers)
5. Activity history tracking
6. GDPR-compliant data export and cascade deletion
7. **Get-or-Create User Pattern**: Critical endpoint for domain services (Speaker, Partner, Attendee)
8. **Resource Expansion**: `?include=company,roles,preferences,settings` reduces API calls
9. **Advanced Filtering**: `?filter={"role":"SPEAKER","company":"comp-123"}`
10. **Performance**: User detail with includes <150ms P95

**Acceptance Criteria Summary:**
- [ ] User aggregate with DDD patterns
- [ ] **Consolidated REST API** implementing Stories 1.15a.7 + 1.15a.8 patterns (7 endpoints)
- [ ] OpenAPI documentation for all user endpoints
- [ ] AWS Cognito integration for authentication sync
- [ ] Profile picture S3 storage with presigned URLs
- [ ] User preferences and settings management
- [ ] Role management with business rules enforcement (minimum 2 organizers)
- [ ] Activity history with pagination
- [ ] **GDPR Compliance**: Data export, cascade deletion, audit logging
- [ ] **Get-or-Create User**: Idempotent endpoint for domain services (critical for Speaker/Partner/Attendee services)
- [ ] **API Consolidation**: Support `?include=company,roles,preferences,settings,activity` for resource expansion
- [ ] **Advanced Search**: Filter by role, company, activity with JSON syntax
- [ ] **Performance**: List <100ms, detail+includes <150ms (P95)
- [ ] Integration tests covering all workflows including consolidated APIs

**Estimated Duration:** 2 weeks

**References:**
- Story file: `docs/stories/1.14-2.user-management-service-foundation.md`
- API consolidation: `docs/stories/1.15a.7.users-api-consolidation.md`, `docs/stories/1.15a.8.organizers-api-consolidation.md`
- GDPR requirements: Story 1.11 (Security & Compliance Essentials)

---

## Story 2.2: Event Management Service Core + Architecture Compliance
**(Formerly Story 1.16, refactored from 1.15a.1 Events API Consolidation)**

**Status:** ✅ Done

**User Story:**
As an **organizer**, I want to access and manage events through consolidated RESTful APIs that load all event data efficiently, so that I can plan conferences without making dozens of API calls.

**Note:** This was a refactoring story. The core functionality was implemented in Story 1.15a.1, and Story 2.2 brought it to full architecture compliance with OpenAPI Generator, shared-kernel integration, and meaningful IDs (eventCode pattern).

**Architecture Integration:**
- **Service**: `event-management-service/` (Java 21 + Spring Boot 3.2)
- **Database**: PostgreSQL with event aggregates
- **Cache**: Caffeine for event data caching (15min TTL for expanded resources)
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

### Architecture Compliance Requirements

**OpenAPI Code Generation (Mandatory):**
- [ ] OpenAPI spec imports shared-kernel types (NO local ErrorResponse/PaginationMetadata definitions)
- [ ] Controllers implement generated API interfaces from openApiGenerate task
- [ ] Use generated DTOs for all request/response models
- [ ] Build fails if implementation doesn't match OpenAPI spec

**Shared-Kernel Integration (Mandatory):**
- [ ] Exceptions extend shared-kernel hierarchy (EventNotFoundException extends NotFoundException)
- [ ] GlobalExceptionHandler uses ch.batbern.shared.dto.ErrorResponse
- [ ] Domain events extend DomainEvent\<UUID\> with full metadata (eventId, correlationId, causationId, userId)
- [ ] Inject DomainEventPublisher from shared-kernel (NO custom publisher classes)
- [ ] Use FilterParser, SortParser, IncludeParser, FieldSelector, PaginationUtils from shared-kernel

**Database-Centric Architecture (per ADR-001):**
- [ ] PostgreSQL is single source of truth for all event data
- [ ] NO AWS SDK calls for event CRUD operations
- [ ] Service operates without AWS IAM credentials (beyond JWT validation)

**DDD Layered Architecture:**
- [ ] Service layer implements business logic (NO repository injection in controllers)
- [ ] Repository pattern for data access
- [ ] Domain entities separate from DTOs
- [ ] Controllers delegate to service layer only

**Implementation References:**
- See `/services/company-user-management-service/OPENAPI-CODEGEN.md` for OpenAPI Generator patterns
- See `/services/company-user-management-service/ARCHITECTURE-COMPLIANCE-FIXES.md` for compliance examples
- See `/docs/architecture/ADR-001-invitation-based-user-registration.md` for database-centric principles

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
- [ ] Caffeine caching for performance (15min TTL)
- [ ] **API Consolidation**: Support `?include=venue,speakers,sessions,topics,registrations` for resource expansion
- [ ] **Advanced Search**: Filter by status, date, topic with JSON filter syntax
- [ ] **Performance**: List <100ms, detail <150ms, detail+includes <500ms (all P95)
- [ ] Domain events publishing
- [ ] Integration tests for all operations including consolidated APIs
- [ ] **Architecture Compliance**: All items in "Architecture Compliance Requirements" section verified
- [ ] **OpenAPI Spec**: ErrorResponse and PaginationMetadata imported from shared-kernel (NOT defined locally)
- [ ] **Service Layer**: EventService implements all business logic (controllers delegate only)
- [ ] **Event Publishing**: DomainEventPublisher from shared-kernel used for all domain events
- [ ] **Testing**: Integration tests verify shared-kernel ErrorResponse structure and event metadata

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
- **Cache**: Caffeine for speaker session data (10min TTL for expanded resources)
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

## Story 2.4: User Role Management + API Consolidation ✅
**(Formerly Story 1.20, includes 1.15a.7 Users API + 1.15a.8 Organizers API)**

**Status:** Done (implemented via Stories 2.1b + 2.5.2)

**Implementation Notes:**
- ✅ Core role management fully implemented in Story 2.1b (User Management Service Foundation)
- ✅ Frontend role management interface complete in Story 2.5.2 (User Management Frontend)
- ✅ All essential AC requirements met: role CRUD, minimum 2 organizers rule, Cognito sync, consolidated APIs
- ⚠️ Deferred features (non-blocking): Formal promotion/demotion approval workflows, UserRoleChangedEvent, dedicated role-specific endpoints (achievable via filter parameters)
- 📊 Test coverage: Backend 357/357 passing, Frontend 76/76 passing

**User Story:**
As an **organizer**, I want to manage user roles through consolidated RESTful APIs with efficient filtering and role-specific views, so that I can build teams without administrator intervention or multiple API calls.

**Architecture Integration:**
- **Service**: User Management Service or API Gateway authentication layer extension
- **Database**: PostgreSQL with role management tables
- **Integration**: AWS Cognito for role attribute updates
- **Cache**: Caffeine for user search and role lookups
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
- [x] **Consolidated REST API** implementing Stories 1.15a.7 + 1.15a.8 patterns (✅ Story 2.1b)
- [x] OpenAPI documentation for all user and role endpoints (✅ Story 2.1b)
- [x] Role promotion API endpoints (✅ `PUT /api/v1/users/{id}/roles`)
- [ ] Role demotion workflow with approval (⚠️ Deferred: basic role removal exists, formal approval workflow for future)
- [x] Minimum 2 organizers rule enforced (✅ Backend RoleService + Frontend validation)
- [x] Cognito custom attributes sync (✅ CognitoIntegrationService)
- [ ] Complete audit trail (⚠️ Partial: domain events exist, UserRoleChangedEvent deferred)
- [x] **API Consolidation**: Support `?include=events,registrations,companies` for user context (✅ Resource expansion working)
- [x] **Advanced Filtering**: Filter users by role, company, participation history with JSON syntax (✅ FilterParser integration)
- [x] **Performance**: User list <100ms, user detail+includes <150ms (P95) (✅ Verified with @Timed metrics)
- [x] Frontend role management interface consuming consolidated APIs (✅ Story 2.5.2: RoleManagerModal, UserFilters)
- [x] Integration tests for all scenarios including consolidated APIs (✅ 357 backend + 76 frontend tests passing)

**Actual Duration:** Implemented across Stories 2.1b (2 weeks backend) + 2.5.2 (1 week frontend) = 3 weeks total

**References:**
- Core functionality: `docs/prd/epic-1-foundation-stories.md` Story 1.20
- API consolidation: `docs/stories/1.15a.7.users-api-consolidation.md`, `docs/stories/1.15a.8.organizers-api-consolidation.md`

---

## Story 2.5: React Frontend CRUD Foundation ✅
**(Formerly Story 1.17 partial)**

**Status:** Done (Foundation Complete)

**Implementation Status:**
- ✅ **Story 1.17**: React Frontend Foundation - Done
- ✅ **Story 2.5.1**: Company Management Frontend - Accepted
- 🔍 **Story 2.5.2**: User Management Frontend - Ready for Review
- ✅ **Story 2.5.3**: Event Management Frontend - Accepted

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
   - Full i18n support (German/English)

2. **User Management**: `docs/wireframes/story-2.4b-user-management-screen.md` ✅
   - User list table with search and autocomplete
   - Advanced filtering by role, company, and status
   - User detail modal with full profile information
   - User create modal with role assignment
   - Role management with business rules enforcement
   - GDPR-compliant user deletion workflow
   - Full i18n support (German/English)

3. **Event Management**: `docs/wireframes/story-1.16-*.md` ✅
   - Event Management Dashboard
   - Event Detail/Edit screen
   - Event Settings panel
   - Workflow Visualization (read-only in this story)
   - Full i18n support (German/English)

**Note**: Speaker wireframes exist in Epic 1 but are simpler CRUD forms without dedicated wireframe files for this story.

---

**Acceptance Criteria Summary:**
- [ ] Role-adaptive navigation operational
- [ ] CRUD forms for Company, Event, Speaker, User
- [ ] **User Management Screen (Organizer-only):**
  - [ ] User list table with columns: Name, Email, Company, Roles, Status, Actions
  - [ ] Search bar with autocomplete (using `GET /api/v1/users/search`)
  - [ ] Filters: Role (ORGANIZER/SPEAKER/PARTNER/ATTENDEE), Company, Status
  - [ ] Click row → User Detail Modal with full user information
  - [ ] "Add User" button → User Create Modal
  - [ ] Actions per row: View, Edit Roles, Delete (with confirmation)
  - [ ] Advanced filtering using JSON filter syntax from API
  - [ ] Resource expansion `?include=company,roles` for efficient data loading
  - [ ] Pagination with 20 users per page (configurable)
  - [ ] Performance: List load <150ms P95, search <100ms P95
- [ ] **Internationalization (i18n):**
  - [ ] All UI components fully translated (German primary, English secondary)
  - [ ] Translation namespace: `userManagement` (i18next + react-i18next)
  - [ ] All text (buttons, labels, messages, errors, tooltips) uses translation keys
  - [ ] Language switcher in global navigation
  - [ ] Translation validation in CI/CD (keys must exist in both de and en)
  - [ ] Component tests verify translations in both languages
- [ ] Authentication integration with auto token refresh
- [ ] API client layer for all microservices
- [ ] Responsive design (mobile/tablet/desktop)
- [ ] Component test coverage >80%
- [ ] Performance meets Core Web Vitals

**Estimated Duration:** 3 weeks (expanded from 2.5 weeks to include User Management screen)

**Reference:** See `docs/prd/epic-1-foundation-stories.md` Story 1.17 for full details

**Important Scope Note:** This story focuses on basic CRUD forms only. Advanced UI features (speaker dashboard, communication hub, mobile PWA) are deferred to Epic 6 and Epic 7.

---

## Story 2.6: User Account Management Frontend (Basic Profile + Settings)

**Status:** ✅ Accepted

**User Story:**
As a **user of any role**, I want to view and manage my account through a unified interface with basic profile and settings, so that I can maintain my personal information and configure essential preferences.

**Architecture Integration:**
- **Frontend**: React 19+ with TypeScript
- **State Management**: Zustand + React Query
- **UI Framework**: Material-UI (MUI) 5.14+
- **API Integration**: Consolidated User APIs from Story 2.1b

**Key Functionality:**
1. **Profile Tab**: View and edit personal profile information
   - Profile header with photo, name, roles, company affiliation
   - Personal information (bio, phone, job title, social links)
   - Role-specific tabbed interface (Organizer/Speaker/Partner/Attendee)
   - Recent activity history (last 5 items)
   - Profile photo upload (ADR-002 Generic File Upload Service)
2. **Settings Tab - Account**: Email display, password change, profile picture management
3. **Settings Tab - Notifications**: Basic email notification toggles, newsletter subscription
4. **Settings Tab - Privacy**: Profile visibility, activity visibility, company display preferences
5. **EXCLUDES**: Advanced content preferences, language/accessibility, GDPR data export (deferred to Epic 7 Story 5.2)

---

### Wireframe Reference
**From docs/wireframes/:**
- **Main Screen**: `docs/wireframes/story-2.6-user-account-management.md` ✅
  - Profile tab with role-specific information
  - Settings tab with Account, Notifications, Privacy sub-tabs
  - Profile photo upload component
  - Activity history timeline
  - Consolidates basic features from story-1.20 and story-5.2

---

**Acceptance Criteria Summary:**

**Profile Tab:**
- [ ] Profile header displays photo, name, company, email (Cognito-verified), roles, member since
- [ ] Profile photo upload/remove using ADR-002 3-phase pattern (presigned URL → S3 → confirm → associate)
- [ ] Inline profile editing for firstName, lastName, bio (max 2000 chars)
- [ ] Bio character counter displays (0/2000)
- [ ] Role-specific tabs display only for assigned roles
- [ ] Activity history displays last 5 items with [View All →] link
- [ ] [View Company Profile] link navigates to Story 2.5.1 company detail

**Settings → Account:**
- [ ] Email displays with "Verified (managed by Cognito)" status (read-only)
- [ ] [Change Password] redirects to Cognito password change flow
- [ ] Theme selector (Light/Dark/Auto) persists to UserPreferences
- [ ] Timezone selector (autocomplete, IANA database) persists to UserSettings

**Settings → Notifications:**
- [ ] Notification channel toggles (email, in-app, push) persist to UserPreferences
- [ ] Notification frequency selector (immediate/daily digest/weekly digest) persists to UserPreferences
- [ ] Info message displays: "Advanced settings (quiet hours) in Epic 7"

**Settings → Privacy:**
- [ ] Profile visibility selector (Public/Members only/Private) persists to UserSettings
- [ ] Profile information toggles (show email, show company, show activity) persist to UserSettings
- [ ] Allow messaging toggle persists to UserSettings
- [ ] [View Privacy Policy] link functional

**General:**
- [ ] Tab navigation smooth (Profile ↔ Settings)
- [ ] Settings sub-tab navigation works (Account, Notifications, Privacy)
- [ ] Advanced features sidebar shows "Coming in Epic 7" for deferred items
- [ ] All changes persist via consolidated User APIs:
  - `PATCH /api/v1/users/me` (profile fields)
  - `PUT /api/v1/users/me/preferences` (notifications, theme, timezone)
  - `PUT /api/v1/users/me/settings` (privacy, visibility)
- [ ] Form validation: client-side (bio 2000 max) + server-side (all fields)
- [ ] Success/error toast notifications display appropriately
- [ ] Loading states with skeleton loaders
- [ ] Responsive design (mobile <768px, tablet 768-1024px, desktop >1024px)
- [ ] Full i18n support (German primary, English secondary) namespace: `userAccount`
- [ ] Accessibility: ARIA labels, keyboard navigation (Tab/Enter/Esc), screen reader support
- [ ] Performance: Initial load <150ms, save operations <200ms (P95)
- [ ] Component test coverage >80%

**Estimated Duration:** 2 weeks

**References:**
- Wireframe: `docs/wireframes/story-2.6-user-account-management.md`
- Consolidated from: `docs/wireframes/story-1.20-user-profile.md` (profile features) + `docs/wireframes/story-5.2-user-settings.md` (basic settings only)
- Backend APIs: Story 2.1b (User Management Service Foundation + API Consolidation)
- File Upload: ADR-002 (Generic File Upload Service)

**Important Scope Note:** This story implements **foundational** user account management only. Advanced features (content preferences, language/accessibility options, GDPR data export, account deactivation/deletion) are deferred to Epic 7 (Story 5.2 - Personal Engagement Management) to focus Epic 2 on core entity CRUD functionality.

---

## Story 2.7: Partner Coordination Service Foundation + API Consolidation
**(Includes 1.15a.2 Partners API Consolidation)**

**Status:** ✅ Done

**User Story:**
As an **organizer**, I want the foundational Partner Coordination Service with consolidated RESTful APIs, so that I can manage partner relationships, partner contacts, topic votes, and meeting coordination efficiently without multiple API calls.

**Architecture Integration:**
- **Service**: `partner-coordination-service/` (Java 21 + Spring Boot 3.2)
- **Database**: PostgreSQL with partner domain schema
- **Cache**: Caffeine for partner data and HTTP responses (15min TTL)
- **API Foundation**: Uses Story 1.15a utilities for filtering, sorting, pagination
- **HTTP Clients**: CompanyServiceClient, UserServiceClient for meaningful ID enrichment
- **Testing**: Extends `ch.batbern.shared.test.AbstractIntegrationTest` from testFixtures

**Key Functionality:**
1. Partner CRUD operations with consolidated API patterns
2. Partner contact management (storing username, not userId UUID)
3. Topic voting system with weighted votes
4. Topic suggestion workflow
5. Partner meeting coordination
6. **ADR-003 Compliance**: Stores meaningful IDs (companyName, username) instead of UUIDs
7. **HTTP Integration**: Calls Company/User services for data enrichment with JWT propagation
8. **Resource Expansion**: `?include=company,contacts,votes` for partner detail in single call
9. **Advanced Search**: Filter partners by tier, status, company with JSON filter syntax
10. **Performance**: Partner detail with includes <300ms P95

**Architecture Compliance (ADR-003 Microservices):**
- **NO cross-service database joins** (NO JPQL joins to companies/users tables)
- **Stores meaningful IDs**: `companyName` (String), `username` (String) - NOT UUIDs
- **HTTP enrichment**: UserServiceClient, CompanyServiceClient with JWT token propagation
- **Error handling**: HttpClientErrorException.NotFound, HttpServerErrorException, ResourceAccessException
- **Testing pattern**: Spring @MockBean with Mockito (NOT WireMock)
- **TestFixtures**: Extends `ch.batbern.shared.test.AbstractIntegrationTest` (NO custom AbstractIntegrationTest)

**Acceptance Criteria Summary:**
- [ ] Partner aggregate with partnership tier management
- [ ] PartnerContact entity storing username (meaningful ID, NOT userId UUID)
- [ ] **Consolidated REST API** implementing Story 1.15a.2 patterns
- [ ] OpenAPI documentation for all consolidated endpoints
- [ ] Database schema with Flyway migrations (NO foreign keys to companies/users)
- [ ] HTTP Clients: CompanyServiceClient, UserServiceClient with JWT propagation
- [ ] CacheConfig class with named caches (partners, companies, users) - 15min TTL
- [ ] **API Consolidation**: Support `?include=company,contacts,votes,meetings` for resource expansion
- [ ] **Advanced Search**: Filter by tier, active status, companyName with JSON filter syntax
- [ ] **Performance**: List <100ms, detail <150ms, detail+includes <300ms (all P95)
- [ ] Domain events publishing to EventBridge
- [ ] Integration tests using testFixtures AbstractIntegrationTest
- [ ] Unit tests with @MockBean for HTTP clients (NOT WireMock)

**Testing Requirements:**
- [ ] Extends `ch.batbern.shared.test.AbstractIntegrationTest` from testFixtures
- [ ] TestConfiguration classes: TestCompanyServiceClientConfig, TestUserServiceClientConfig
- [ ] HTTP client mocking via Spring @MockBean with Mockito
- [ ] Build.gradle includes `testImplementation testFixtures(project(':shared-kernel'))`
- [ ] NO custom AbstractIntegrationTest.java file
- [ ] NO WireMock dependencies
- [ ] NO Resilience4j dependencies

**Estimated Duration:** 2.5 weeks (includes API consolidation implementation + HTTP client integration)

**References:**
- Story file: `docs/stories/2.7.partner-coordination-service-foundation.md`
- API consolidation: `docs/stories/1.15a.2.partners-api-consolidation.md`
- ADR-003: `docs/architecture/ADR-003-meaningful-identifiers-public-apis.md`
- Event-management-service: Reference implementation for HTTP clients with JWT propagation

**Important Scope Note:** This story provides partner entity CRUD with consolidated APIs and HTTP-based service integration (ADR-003 compliant). Partner portal, invitation workflows, and advanced partner features are deferred to Epic 8 (Partner Coordination & Engagement).

---

## Epic 2 Success Metrics

**Functional Success (End of Week 18):**
- ✅ All entities have complete CRUD operations (Company, Event, Speaker, User, Partner)
- ✅ **Consolidated RESTful APIs** operational for all entities (Stories 1.15a.1-1.15a.8 + 1.15a.2)
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
  - Company/Speaker/Partner detail with includes: <200-300ms P95
  - User list with filters: <150ms P95
  - Partner detail with HTTP enrichment: <300ms P95
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

## Story 2.8: Partner Management Frontend (Split into 2.8.1 - 2.8.4)

**Status:** Split into focused stories (2.8.1, 2.8.2, 2.8.3, 2.8.4)

**Original User Story:**
As an **organizer**, I want a React frontend interface for partner management with directory listing, detail views, and CRUD operations, so that I can efficiently manage partner relationships through an intuitive user interface accessible from the main navigation.

**Story Split Rationale:**
Story 2.8 was split into four focused stories for better manageability, clearer acceptance criteria, and independent deployment:

### Story 2.8.1: Partner Directory
**Status:** Draft

**Focus:** Partner list screen with search, filtering, and navigation

**Key Features:**
- Partner directory with search by name (debounced 300ms)
- Filtering by tier (Strategic, Platinum, Gold, Silver, Bronze) and status (Active/Inactive)
- Quick filter chips for tier selection
- Grid/List view toggle (persisted in localStorage)
- Partner overview statistics (total, active, tier distribution)
- Partner cards with logo, tier badge, company info, engagement placeholder
- Pagination (20 partners per page)
- Top navigation integration ("Partners" menu item)
- Navigate to detail view on card click

**Wireframe:** `docs/wireframes/story-6.3-partner-directory.md`

**Story File:** `docs/stories/2.8.1.partner-directory.md`

### Story 2.8.2: Partner Detail View
**Status:** Draft

**Focus:** Partner detail screen with tabbed interface and comprehensive information display

**Key Features:**
- Partner detail header (logo, tier badge, company info, engagement placeholder)
- Quick stats cards (partner since, events attended, active votes, meetings)
- Tab navigation (Overview, Contacts, Meetings, Activity, Notes, Settings)
- Overview tab (partnership details, engagement placeholder, recent activity, topic votes)
- Meetings tab (read-only meetings list with RSVP status)
- Activity tab (timeline of partner activities with filtering)
- Notes tab (organizer notes CRUD)
- Settings tab (partnership status, auto-renewal - organizer only)
- Back button to directory
- Edit button to open modal (Story 2.8.3)

**Wireframe:** `docs/wireframes/story-6.3-partner-detail.md`

**Story File:** `docs/stories/2.8.2.partner-detail-view.md`

**Dependencies:** Story 2.8.1 (navigation from directory)

### Story 2.8.3: Partner Create/Edit Modal
**Status:** Draft

**Focus:** Modal form for creating new partnerships and editing existing partnership details

**Key Features:**
- Create partner modal (triggered from directory [+ Add Partner] button)
- Edit partner modal (triggered from detail view [Edit Partner] button)
- Company autocomplete search (min 2 chars, debounced 300ms)
- Partnership tier dropdown (with emoji icons 🏆💎🥇🥈🥉)
- Partnership start/end date pickers (MUI X DatePicker)
- Tier benefits preview (updates dynamically based on tier)
- Form validation (Zod schema with i18n errors)
- Dirty detection with unsaved changes warning
- Create mutation (POST /partners) with navigation to detail view
- Update mutation (PATCH /partners/{companyName}) with optimistic update

**Wireframe:** `docs/wireframes/story-6.3-partner-directory.md` (Create Modal), `story-6.3-partner-detail.md` (Edit Modal)

**Story File:** `docs/stories/2.8.3.partner-create-edit-modal.md`

**Dependencies:** Story 2.8.1 (create trigger), Story 2.8.2 (edit trigger)

### Story 2.8.4: Partner Contact Management
**Status:** Draft

**Focus:** Contact management within Contacts tab - add/remove contacts with role assignment

**Key Features:**
- Contacts tab integration (from Story 2.8.2)
- Contact list with enriched user data (name, email, role, profile picture)
- Contact cards with role badges (👤 PRIMARY, 💳 BILLING, 🔧 TECHNICAL, 📧 MARKETING)
- Primary contact indicator (⭐ star badge)
- Add contact modal (user autocomplete, role selection, primary toggle)
- Remove contact with confirmation dialog
- Primary contact validation (at least one PRIMARY required)
- User autocomplete search (by username or email)
- Optimistic updates for add/remove operations
- Error handling (409 duplicate, 400 primary validation, 404 not found)

**Wireframe:** `docs/wireframes/story-6.3-partner-detail.md` (Contacts Tab)

**Story File:** `docs/stories/2.8.4.partner-contact-management.md`

**Dependencies:** Story 2.8.2 (Contacts tab container)

---

**Architecture Integration (All Stories):**
- **Frontend**: React 19.x application (TypeScript, Material-UI v5, Zustand, React Query, React Hook Form, Zod)
- **Backend**: Partner Coordination Service REST APIs (from Story 2.7)
- **Integration**: Company Management Service (for company data), User Management Service (for contact data)
- **CDN**: CloudFront for company logos and user profile pictures
- **Cache**: React Query (2min for lists, 5min for detail, 10min for contacts)
- **HTTP Enrichment (ADR-004)**: Backend enriches partner data with company/user info via HTTP calls

**Shared Acceptance Criteria (All Stories):**
- Responsive design (mobile/tablet/desktop)
- Accessibility (WCAG 2.1 AA)
- Performance (<2s load, <200ms interactions)
- Internationalization (German/English)
- Integration tests with Story 2.7 APIs

**Deferred to Epic 8 (Partner Portal - Phase 2):**
- Analytics dashboard (Story 6.1) - Advanced engagement analytics
- Interactive topic voting interface (Story 6.4) - Weighted voting with priorities
- Meeting coordination UI (Story 6.2) - Full meeting scheduling and RSVP management
- Email integration, bulk actions, advanced search, export functionality

**Story 2.8.x completes the Epic 2 CRUD pattern:**
| Entity | Backend Service | Frontend CRUD |
|--------|----------------|---------------|
| **Company** | Story 2.1 ✅ | Story 2.5.1 ✅ |
| **User** | Story 2.1b ✅ | Story 2.5.2 ✅ |
| **Event** | Story 2.2 ✅ | Story 2.5.3 ✅ |
| **Partner** | Story 2.7 ✅ | Stories 2.8.1-2.8.4 📝 NEW |

---

## Dependencies & Prerequisites

**Required Before Starting Epic 2:**
- ✅ Epic 1 Stories 1.1-1.7 complete (infrastructure foundation)
- ✅ Epic 1 Stories 1.9, 1.11 complete (error handling, security)
- ✅ API Gateway operational with authentication
- ✅ PostgreSQL databases provisioned for all services
- ✅ Caffeine clusters operational
- ✅ S3 buckets configured with CDN

**Enables Following Epics:**
- **Epic 3**: Historical Data Migration (requires entity APIs to import data)
- **Epic 4**: Public Website (requires Event Management APIs for public display)
- **Epic 5**: Enhanced Organizer Workflows (builds on Event Management Service)
- **Epic 6**: Speaker Portal (builds on Speaker Coordination Service)
- **Epic 8**: Partner Coordination (builds on Story 2.7 backend + Story 2.8 frontend for advanced partner portal features)

---

## Implementation Sequence

**Week 10-12: Backend Services (Parallel with User Management)**
- Story 2.1: Company Management Service + API Consolidation (2.5 weeks) ✅ Done
- Story 2.1b: User Management Service + API Consolidation (2 weeks) ✅ Done
- Story 2.2: Event Management Service + Architecture Compliance (refactoring) ✅ Done
- Story 2.3: Speaker Coordination Service + API Consolidation (2.5 weeks) - Not Started
- Story 2.7: Partner Coordination Service + API Consolidation (2.5 weeks) ✅ Done

**Week 13: User Role Management & Integration**
- Story 2.4: User Role Management + API Consolidation (2 weeks) ✅ Done
- Integration testing across all backend services (0.5 weeks)

**Week 14-18: Frontend Development**
- Story 2.5: React Frontend CRUD Foundation consuming consolidated APIs (3 weeks) ✅ Done
  - Story 2.5.1: Company Management Frontend ✅ Done
  - Story 2.5.2: User Management Frontend ✅ Done
  - Story 2.5.3: Event Management Frontend ✅ Done
- Story 2.6: User Account Management Frontend (2 weeks) ✅ Accepted
- Story 2.8: Partner Management Frontend (1.5 weeks) - Not Started
- End-to-end testing and bug fixes (2 weeks)

**Rationale:**
- Backend services can be developed in parallel by different developers
- Story 2.1b (User Management) can be developed parallel to Story 2.1 (Company Management)
- Story 2.7 (Partner Coordination) depends on Story 2.1 + 2.1b (requires Company/User services for HTTP integration) - ✅ Complete
- Story 2.4 (User Role Management) depends on Story 2.1b completion - ✅ Complete
- API consolidation adds ~0.5 weeks per service (worth it to avoid future refactoring)
- Frontend development benefits from consolidated APIs (fewer integration points)
- Extra time for frontend reflects complexity of consuming resource expansion APIs

**Current Status (Updated):**
- 7 out of 9 stories complete (77.8% progress)
- Remaining stories: 2.3 (Speaker Coordination Service Foundation), 2.8 (Partner Management Frontend - NEW)
- Epic 2 backend services nearly complete (4/5), frontend CRUD pattern needs Partner UI to be complete

---

## Risk Management

**Technical Risks:**
- **Risk**: Swiss UID API integration may have rate limits or downtime
  - **Mitigation**: Implement caching and fallback validation logic
- **Risk**: Performance issues with complex entity relationships
  - **Mitigation**: Database query optimization, proper indexing, Caffeine caching
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
- [x] Story 2.1 (Company Management Service) - Complete ✅
- [x] Story 2.1b (User Management Service) - Complete ✅
- [x] Story 2.2 (Event Management Service Core + Architecture Compliance) - Complete ✅
- [ ] Story 2.3 (Speaker Coordination Service) - Not Started
- [x] Story 2.4 (User Role Management) - Complete ✅
- [x] Story 2.5 (React Frontend CRUD Foundation) - Complete ✅
  - [x] Story 2.5.1 (Company Management Frontend) ✅
  - [x] Story 2.5.2 (User Management Frontend) ✅
  - [x] Story 2.5.3 (Event Management Frontend) ✅
- [x] Story 2.6 (User Account Management Frontend) - Accepted ✅
- [x] Story 2.7 (Partner Coordination Service) - Complete ✅
- [ ] Story 2.8 (Partner Management Frontend) - Not Started (NEW)
- [x] All completed acceptance criteria verified ✅
- [x] Integration tests passing for completed services ✅
- [x] Frontend deployed and accessible ✅
- [x] Performance benchmarks met for completed features ✅
- [x] Security scan showing zero critical vulnerabilities ✅
- [x] All completed services follow ADR-003 microservices pattern ✅

**Progress: 7/9 stories complete (77.8%)**
**Remaining:**
- Speaker Coordination Service Foundation (2.3)
- Partner Management Frontend (2.8) - NEW story to complete CRUD pattern for all entities

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
