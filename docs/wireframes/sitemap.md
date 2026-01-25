# BATbern Event Management Platform - Comprehensive Site Map

**Generated:** 2026-01-25
**Version:** 2.0 (MVP Completion Update)
**Purpose:** Complete hierarchical overview of all platform screens and navigation relationships
**Update:** Reflects actual MVP implementation (Epics 1-5 100% COMPLETE) and Epic 6-8 deferrals to Phase 2+

---

## Legend

### Status Indicators
- ✅ **[IMPLEMENTED IN MVP]** - Actually built and deployed in Epics 1-5
- ❌ **[NOT IMPLEMENTED IN MVP]** - Planned but not built
- 📦 **[EPIC 6 - DEFERRED TO PHASE 2+]** - Speaker Self-Service Portal (optional enhancement)
- 📦 **[EPIC 7 - DEFERRED TO PHASE 2+]** - Attendee Experience Enhancements (personal dashboards, PWA)
- 📦 **[EPIC 8 - DEFERRED TO PHASE 2+]** - Advanced Partner Analytics & Voting (optional enhancement)
- 🔄 **[PARTIAL]** - Placeholder exists but full implementation deferred

### Role Indicators
- 🎯 **Organizer** - Event organizers (equivalent to admin privileges for event management)
- 💼 **Partner** - Corporate partners and sponsors
- 🎤 **Speaker** - Speakers and presenters
- 👤 **Attendee** - Event attendees and participants
- 🌐 **Public** - Unauthenticated public access

**Note:** The platform uses four user roles: ORGANIZER, SPEAKER, PARTNER, ATTENDEE. There is no separate "ADMIN" role - organizers have administrative privileges for event management.

### Navigation Indicators
- → Single direction navigation
- ↔ Bidirectional navigation
- ⤴ Returns to parent
- 🔗 Cross-reference to another section

---

## Platform Architecture Overview

```
BATbern Platform
├── Public Layer (Unauthenticated) ✅ IMPLEMENTED
├── Authentication Layer ✅ IMPLEMENTED
├── Global/Shared Screens (All Roles) ✅ IMPLEMENTED
├── Organizer Portal ✅ IMPLEMENTED
├── Partner Portal 🔄 BASIC (Epic 8 deferred)
├── Speaker Portal 📦 DEFERRED (Epic 6)
└── Attendee Portal 🔄 PUBLIC ONLY (Epic 7 deferred)
```

---

## 1. Public Layer (Unauthenticated) ✅ IMPLEMENTED IN MVP

### 1.1 Public Access Screens
```
🌐 HomePage (Current/Archived Event Display) ✅ [IMPLEMENTED IN MVP]
│   Route: / or /events/:eventCode or /archive/:eventCode
│   Story: 4.1.2, 4.1.3, 4.1.5, 4.1.6
│   File: web-frontend/src/pages/public/HomePage.tsx
│   Features:
│   ├── Current event display with timeline
│   ├── Archived event display (timeline-only view)
│   ├── Session Details Modal
│   ├── Speaker Profile previews
│   └── Public event registration CTA
├── → Event Registration Flow
├── → Session Details (modal)
├── → About Page
└── → Archive Page

🌐 Event Registration Flow (2-Step Wizard) ✅ [IMPLEMENTED IN MVP]
│   Route: /register/:eventCode
│   Story: 4.1.5 (Public Registration Form)
│   File: web-frontend/src/pages/public/RegistrationPage.tsx
│   Note: Accordion-style wizard, not 3 steps
├── Step 1/2: Personal Details (name, email, company, role) ✅
├── Step 2/2: Confirm Registration (review, preferences, terms) ✅
├── → Registration Success Page ✅
└── → Email Confirmation/Cancellation Links ✅

🌐 Registration Success Page ✅ [IMPLEMENTED IN MVP]
│   Route: /registration-success
│   Story: 4.1.5
│   File: web-frontend/src/pages/public/RegistrationSuccessPage.tsx
└── Shows confirmation message and next steps

🌐 Confirm Registration Page ✅ [IMPLEMENTED IN MVP]
│   Route: /events/:eventCode/confirm-registration
│   Story: 4.1.6 (Email Confirmation Links)
│   File: web-frontend/src/pages/public/ConfirmRegistrationPage.tsx
└── Email-linked registration confirmation

🌐 Cancel Registration Page ✅ [IMPLEMENTED IN MVP]
│   Route: /events/:eventCode/cancel-registration
│   Story: 4.1.6 (Email Confirmation Links)
│   File: web-frontend/src/pages/public/CancelRegistrationPage.tsx
└── Email-linked registration cancellation

🌐 Registration Confirmation Page (Deprecated) ✅ [IMPLEMENTED IN MVP]
│   Route: /registration-confirmation/:confirmationCode
│   File: web-frontend/src/pages/public/RegistrationConfirmationPage.tsx
│   Note: Legacy route, replaced by email confirmation flow

🌐 Archive Page ✅ [IMPLEMENTED IN MVP]
│   Route: /archive
│   Story: 4.2 (Archive Browsing)
│   File: web-frontend/src/pages/public/ArchivePage.tsx
│   Features:
│   ├── Browse historical events (20+ years)
│   ├── Filter by date range and topics
│   ├── Search functionality
│   └── → Archived Event Detail (reuses HomePage)

🌐 About Page ✅ [IMPLEMENTED IN MVP]
│   Route: /about
│   File: web-frontend/src/pages/AboutPage.tsx

🌐 Search Page 🔄 [PARTIAL - PLACEHOLDER]
│   Route: /search
│   Note: Placeholder only, full implementation in Epic 7

🌐 Privacy Policy Page 🔄 [PARTIAL - PLACEHOLDER]
│   Route: /privacy
│   Note: Placeholder only

🌐 Terms of Service Page 🔄 [PARTIAL - PLACEHOLDER]
│   Route: /terms
│   Note: Placeholder only
```

### 1.2 Public Features NOT Implemented in MVP
```
❌ Session Details Modal (Detailed View)
│   Note: Basic session display exists in HomePage timeline, but dedicated modal not implemented
│   Priority: MEDIUM - Would enhance public event browsing

❌ Ticket/QR Code Page
│   Note: Registration confirmation emails sent, but no QR code generation
│   Story: Wireframed in story-2.4-ticket-qr-code-page.md
│   Priority: LOW for MVP - manual check-in acceptable

❌ Attendee List Modal
│   Note: Networking feature not implemented
│   Story: Wireframed in story-2.4-attendee-list-modal.md
│   Priority: LOW - Nice to have for networking
```

---

## 2. Authentication Layer ✅ IMPLEMENTED IN MVP

### 2.1 Authentication Screens
```
Login Screen ✅ [IMPLEMENTED IN MVP]
│   Route: /login or /auth/login
│   Story: 1.2 - API Gateway & Authentication Service
│   Component: LoginForm
│   File: web-frontend/src/components/auth/LoginForm.tsx
├── → Forgot Password
├── → Account Creation
└── → Role-Based Dashboard (post-login)

Forgot Password Flow ✅ [IMPLEMENTED IN MVP]
│   Route: /auth/forgot-password
│   Story: 1.2 - API Gateway & Authentication Service
│   Component: ForgotPasswordForm
│   File: web-frontend/src/components/auth/ForgotPasswordForm.tsx
└── ⤴ Login Screen

Reset Password Flow ✅ [IMPLEMENTED IN MVP]
│   Route: /auth/reset-password
│   Story: 1.2 - API Gateway & Authentication Service
│   Component: ResetPasswordForm
│   File: web-frontend/src/components/auth/ResetPasswordForm.tsx
└── → Login Screen

Account Creation ✅ [IMPLEMENTED IN MVP]
│   Route: /auth/register
│   Story: 1.2 - API Gateway & Authentication Service
│   Component: RegistrationWizard
│   File: web-frontend/src/components/auth/RegistrationWizard.tsx
├── Role Selection
└── → Email Verification

Email Verification ✅ [IMPLEMENTED IN MVP]
│   Route: /auth/verify-email
│   Story: 1.2 - API Gateway & Authentication Service
│   Component: EmailVerification
│   File: web-frontend/src/components/auth/EmailVerification.tsx
└── → Role-Based Dashboard
```

---

## 3. Global/Shared Screens (All Authenticated Users) ✅ IMPLEMENTED IN MVP

### 3.1 Core Navigation
```
Main Navigation Bar/Menu ✅ [IMPLEMENTED IN MVP]
│   Story: 1.17 - React Frontend Foundation
│   Component: Implemented in BaseLayout
│   File: web-frontend/src/components/shared/Layout/BaseLayout.tsx
├── → Role-Specific Dashboard
├── → User Account Page
├── → Notification Center (referenced in code)
└── → Logout

🌐 User Account Page ✅ [IMPLEMENTED IN MVP]
│   Route: /account
│   Story: 2.6 (User Account Management)
│   File: web-frontend/src/pages/UserAccountPage/UserAccountPage.tsx
│   Roles: All authenticated users
│   Features:
│   ├── Profile Tab (view/edit personal information)
│   ├── Settings Tab (preferences, notifications, language)
│   ├── Company affiliation display
│   └── Role-specific information
└── ⤴ Previous Screen

Notification Center ✅ [IMPLEMENTED IN MVP - REFERENCED]
│   Story: 1.20-notification-center
│   Note: Referenced in code, basic implementation exists
│   File: Notification system integrated into layouts
│   Features:
│   ├── Role-specific notifications
│   ├── Mark read/unread
│   └── In-app notification display
```

### 3.2 Global Features NOT Implemented in MVP
```
❌ Help Center
│   Note: No dedicated help/documentation screen
│   Priority: MEDIUM - Would improve user onboarding

❌ Support Ticket System
│   Note: No built-in support ticket functionality
│   Priority: LOW - Can use email for MVP

❌ Advanced Notification Settings
│   Note: Basic notification preferences exist in User Account Page
│   Story: Full notification center wireframed in story-1.20-notification-center.md
│   Priority: LOW - Basic preferences sufficient for MVP
```

---

## 4. Organizer Portal (🎯 Role) ✅ IMPLEMENTED IN MVP

### 4.1 Main Dashboard & Overview
```
🎯 Dashboard (Role-Based Redirect) ✅ [IMPLEMENTED IN MVP]
│   Route: /dashboard
│   File: web-frontend/src/pages/Dashboard.tsx
│   Note: Redirects to role-appropriate dashboard
└── → Event Management Dashboard (for organizers)

🎯 Event Management Dashboard ✅ [IMPLEMENTED IN MVP]
│   Route: /organizer/events
│   Story: 1.16, 2.5.3 (Event Management Dashboard)
│   File: web-frontend/src/pages/EventManagementDashboard.tsx
│   Features:
│   ├── Event list with status indicators
│   ├── Quick actions (view, edit, publish)
│   ├── Event type filtering
│   └── Create new event button
├── → Event Page (unified detail/edit)
├── → Event Type Configuration
├── → Topic Management
├── → Task Board
├── → Slot Assignment
├── → Speaker Management (placeholder)
├── → Partner Directory
├── → Company Management
└── → User Management
```

### 4.2 Event Management ✅ IMPLEMENTED IN MVP
```
🎯 Event Page (Unified Detail/Edit) ✅ [IMPLEMENTED IN MVP]
│   Route: /organizer/events/:eventCode
│   Story: 5.6 (Unified Event Page with Tabs)
│   File: web-frontend/src/pages/organizer/EventPage.tsx
│   Features: Tab-based interface with:
│   ├── Details Tab: Event information (view/edit), status, workflow
│   ├── Speakers Tab: Speaker assignments, invitations, status tracking
│   ├── Tasks Tab: Event-specific task management
│   ├── Moderator assignment
│   ├── Publishing controls
│   └── Event state management (9-state workflow)
├── → Slot Assignment Page (dedicated drag-drop UI)
├── → Task Board (filtered view)
└── ⤴ Event Management Dashboard

🎯 Slot Assignment Page ✅ [IMPLEMENTED IN MVP]
│   Route: /organizer/events/:eventCode/slot-assignment
│   Story: 5.7 (BAT-11 - Slot Assignment Interface)
│   File: web-frontend/src/pages/organizer/SlotAssignmentPage.tsx
│   Features:
│   ├── Drag-and-drop speaker assignment to time slots
│   ├── Visual timeline with slot availability
│   ├── Speaker pool management
│   └── Conflict detection
└── ⤴ Event Page

🎯 Event Type Configuration ✅ [IMPLEMENTED IN MVP]
│   Route: /organizer/event-types
│   Story: 5.1 (Event Type Configuration)
│   File: web-frontend/src/pages/organizer/EventTypeConfigurationAdmin.tsx
│   Note: "Admin" suffix in filename is legacy - organizers have full access
│   Features:
│   ├── Event type CRUD
│   ├── Template management
│   └── Workflow configuration
└── ⤴ Event Management Dashboard

🎯 Event Create Page 🔄 [PARTIAL - PLACEHOLDER]
│   Route: /organizer/events/create
│   File: web-frontend/src/pages/EventCreate.tsx
│   Note: Placeholder exists, creation may use Event Page instead

🎯 Event Timeline Page 🔄 [PARTIAL - PLACEHOLDER]
│   Route: /organizer/events/timeline
│   File: web-frontend/src/pages/EventTimeline.tsx
│   Note: Placeholder exists, timeline integrated into Event Page
```

### 4.3 Content & Topic Management ✅ IMPLEMENTED IN MVP
```
🎯 Topic Management Page ✅ [IMPLEMENTED IN MVP]
│   Route: /organizer/topics
│   Story: 5.2 (Topic Management)
│   File: web-frontend/src/pages/organizer/TopicManagementPage.tsx
│   Features:
│   ├── Topic CRUD operations
│   ├── Topic backlog management
│   ├── Topic assignment to events
│   ├── Topic staleness tracking
│   └── Usage history
└── ⤴ Event Management Dashboard
```

### 4.4 Task Management ✅ IMPLEMENTED IN MVP
```
🎯 Task Board Page ✅ [IMPLEMENTED IN MVP]
│   Route: /organizer/tasks
│   Story: 5.5 (Task Management System)
│   File: web-frontend/src/pages/organizer/TaskBoardPage.tsx
│   Features:
│   ├── Kanban board with 4 columns (To Do, In Progress, Review, Done)
│   ├── Drag-and-drop task movement
│   ├── Task filtering by event, assignee, priority
│   ├── Task creation and editing
│   └── Event-specific task views (also in Event Page)
└── ⤴ Event Management Dashboard
```

### 4.5 Speaker Management 🔄 PARTIAL (Epic 6 Deferred)
```
🎯 Speaker Management Screen 🔄 [PARTIAL - PLACEHOLDER]
│   Route: /organizer/speakers
│   File: web-frontend/src/pages/Speakers.tsx
│   Note: Placeholder only, full speaker portal deferred to Epic 6
│   MVP Approach: Organizers manage speakers manually via:
│   ├── Event Page → Speakers Tab (speaker assignments)
│   ├── Slot Assignment Page (speaker-to-slot mapping)
│   └── Manual coordination via email/notes

📦 DEFERRED TO EPIC 6 (Speaker Self-Service Portal):
├── Speaker Profile Detail View (wireframed in story-7.1-speaker-profile-detail-view.md)
├── Speaker Matching Interface (wireframed in story-3.1-speaker-matching-interface.md)
├── Invitation Management Screen (wireframed in story-3.1-invitation-management.md)
├── Material Submission Wizard (wireframed in story-3.3-material-submission-wizard.md)
└── Speaker Dashboard (referenced in wireframes-speaker.md)
```

### 4.6 Partner Management ✅ IMPLEMENTED IN MVP (Basic)
```
🎯 Partner Directory/List Screen ✅ [IMPLEMENTED IN MVP]
│   Route: /organizer/partners
│   Story: 6.3 (Partner Coordination)
│   File: web-frontend/src/pages/OrganizerPartners.tsx
│   Features:
│   ├── Partner list with tier badges
│   ├── Contact information display
│   ├── Quick actions (View Details, Email)
│   └── Filter by tier/status
└── → Partner Detail Screen

🎯 Partner Detail Screen ✅ [IMPLEMENTED IN MVP]
│   Route: /organizer/partners/:companyName
│   Story: 6.3 (Partner Coordination)
│   File: web-frontend/src/pages/OrganizerPartnerDetail.tsx
│   Features:
│   ├── Partner information (logo, tier, industry)
│   ├── Contact management
│   ├── Meeting history (basic - Story 5.15)
│   ├── Activity timeline
│   └── Notes (organizer private)
└── ⤴ Partner Directory
```

### 4.7 Company & User Management ✅ IMPLEMENTED IN MVP
```
🎯 Company Management Screen ✅ [IMPLEMENTED IN MVP]
│   Route: /organizer/companies/*
│   Story: 1.14 (Company Management)
│   File: web-frontend/src/components/shared/Company/CompanyManagementScreen.tsx
│   Features:
│   ├── Company CRUD operations
│   ├── Logo upload (S3 presigned URLs)
│   ├── Swiss UID verification
│   ├── Industry/sector selection
│   ├── Partner status toggle
│   └── Nested routing (list + detail views)
└── ⤴ Event Management Dashboard

🎯 User Management ✅ [IMPLEMENTED IN MVP]
│   Route: /organizer/users/*
│   Story: 2.5.2 (User Management)
│   File: web-frontend/src/components/organizer/UserManagement/UserManagement.tsx
│   Features:
│   ├── User list with search/filter
│   ├── User detail view with userId parameter
│   ├── Role management
│   ├── Company association
│   └── Nested routing (list + detail views)
└── ⤴ Event Management Dashboard
```

### 4.8 Organizer Features NOT Implemented in MVP
```
❌ Workflow Visualization Screen
│   Note: Event workflow status shown in Event Page, but no dedicated visualization
│   Story: Wireframed in story-1.16-workflow-visualization.md
│   Priority: LOW - Status indicators sufficient for MVP

❌ Progressive Publishing Configuration Screen
│   Note: Auto-publishing implemented (Story 5.8 - BAT-16), but no UI configuration
│   Story: Publishing config wireframed in Event Settings (story-1.16-event-settings.md)
│   Priority: LOW - Automated publishing meets MVP needs
│   Implementation: Auto-publish speakers at EVENT_LIVE-30d, agenda at EVENT_LIVE-14d

❌ Content Library/Repository Screen
│   Note: Content management deferred to Epic 7
│   Story: Wireframed in story-3.3-content-library-repository.md
│   Priority: MEDIUM - Would improve material organization

❌ Moderator Review Queue
│   Note: Quality review workflow not implemented
│   Story: Wireframed in story-4.1-moderator-review-queue.md
│   Priority: MEDIUM - Manual review acceptable for MVP

❌ System Settings/Configuration Screen
│   Note: No admin-level system configuration UI
│   Story: Wireframed in story-1.0-system-settings.md
│   Priority: LOW - Configuration via backend/environment variables

❌ Logistics Coordination Screen
│   Note: Minimal venue/logistics features
│   Story: Wireframed in story-4.4-logistics-coordination.md
│   Priority: LOW - Manual logistics acceptable for MVP
```

---

## 5. Partner Portal (💼 Role) 🔄 BASIC (Epic 8 Deferred)

**MVP Status:** Basic partner coordination via Story 5.15 (Epic 5). Advanced analytics and voting deferred to Epic 8.

### 5.1 Partner Screens Implemented in MVP
```
💼 Partner Management (Read-Only View) ✅ [IMPLEMENTED IN MVP]
│   Route: /partners
│   File: web-frontend/src/pages/Partners.tsx
│   Note: Placeholder for partner portal, actual management via organizer portal
│   MVP Features:
│   ├── View company profile
│   ├── View meeting history (basic from Story 5.15)
│   └── Contact organizers

💼 Basic Partner Meeting Coordination ✅ [IMPLEMENTED IN MVP]
│   Story: 5.15 (Partner Meeting Coordination - Epic 5)
│   Features:
│   ├── Schedule Spring/Autumn meetings (date/time field)
│   ├── Meeting agenda template (3 sections)
│   ├── Capture topics from meeting notes
│   ├── Free-text meeting notes
│   └── View meeting history
│   Note: Managed by organizers via Partner Detail Screen
```

### 5.2 Epic 8 Features DEFERRED TO PHASE 2+
```
📦 DEFERRED TO EPIC 8 (Advanced Partner Analytics & Voting):

Partner Analytics Dashboard 📦 [EPIC 8 - DEFERRED]
│   Story: 8.1 (Partner Analytics Dashboard)
│   Wireframe: story-6.1-partner-analytics-dashboard.md ✅
│   Features:
│   ├── Employee attendance metrics
│   ├── Engagement analytics
│   ├── ROI tracking
│   ├── Trend analysis
│   └── Department breakdown

Employee Analytics 📦 [EPIC 8 - DEFERRED]
│   Story: 8.1
│   Wireframe: story-6.1-employee-analytics.md ✅

Topic Voting Screen 📦 [EPIC 8 - DEFERRED]
│   Story: 8.2 (Topic Voting)
│   Wireframe: story-6.4-topic-voting.md ✅
│   Features:
│   ├── Weighted voting by partner tier
│   ├── Vote distribution visualization
│   ├── Topic comparison
│   └── Voting history

All Topics Browser Screen 📦 [EPIC 8 - DEFERRED]
│   Story: 8.2
│   Wireframe: story-6.1-all-topics-browser.md ✅

Partner Settings Screen 📦 [EPIC 8 - DEFERRED]
│   Wireframe: story-6.3-partner-settings.md ✅
│   Features:
│   ├── Notification preferences
│   ├── Integration settings
│   ├── Billing & subscription
│   └── Team & access management

Advanced Meeting Features 📦 [EPIC 8 - DEFERRED]
│   Story: 8.3 (Meeting Automation)
│   Wireframe: story-6.2-partner-meetings.md ✅
│   Features:
│   ├── Calendar integration
│   ├── Automated agenda generation
│   ├── Structured action items
│   └── Meeting analytics
```

---

## 6. Speaker Portal (🎤 Role) 📦 DEFERRED TO EPIC 6 (Phase 2+)

**MVP Status:** All speaker portal features deferred to Epic 6. MVP uses organizer-driven workflow.

### 6.1 Speaker Self-Service Features DEFERRED
```
📦 DEFERRED TO EPIC 6 (Speaker Self-Service Portal):

Speaker Dashboard 📦 [EPIC 6 - DEFERRED]
│   Story: 6.1
│   Note: Referenced in wireframes-speaker.md, dedicated file not created
│   Features:
│   ├── My events and invitations
│   ├── Material submission status
│   ├── Upcoming presentations
│   └── Profile management access

Material Submission Wizard 📦 [EPIC 6 - DEFERRED]
│   Story: 6.2
│   Wireframe: story-3.3-material-submission-wizard.md ✅
│   Features:
│   ├── Step-by-step submission
│   ├── Abstract entry
│   ├── Presentation upload
│   └── Metadata entry

Presentation Upload 📦 [EPIC 6 - DEFERRED]
│   Story: 6.2
│   Wireframe: story-3.3-presentation-upload.md ✅
│   Features:
│   ├── File upload interface (S3 presigned URLs)
│   ├── Version management
│   └── Upload progress tracking

Speaker Profile Management 📦 [EPIC 6 - DEFERRED]
│   Story: 6.3
│   Wireframe: story-7.1-speaker-profile-management.md ✅
│   Features:
│   ├── Bio and expertise editing
│   ├── Photo upload
│   ├── Social media links
│   └── Public profile preview

Event Timeline (Speaker View) 📦 [EPIC 6 - DEFERRED]
│   Story: 6.4
│   Wireframe: story-3.5-event-timeline.md ✅
│   Features:
│   ├── Key dates and milestones
│   ├── Task list (speaker tasks)
│   ├── Submission deadlines
│   └── Event day details

Invitation Response 📦 [EPIC 6 - DEFERRED]
│   Story: 6.5
│   Wireframe: story-3.2-invitation-response.md ✅
│   Features:
│   ├── Accept/decline invitation
│   ├── Alternative dates suggestion
│   ├── Availability confirmation
│   └── Response tracking

Speaker Community 📦 [EPIC 6 - DEFERRED]
│   Note: Referenced in wireframes, community features not scoped for MVP
│   Features:
│   ├── Discussion forums
│   ├── Speaker networking
│   ├── Resource sharing
│   └── Mentorship connections
```

### 6.2 MVP Workaround for Speaker Management
```
✅ MVP APPROACH (Epic 5 - Organizer-Driven):
├── Organizers manually contact speakers (email/phone)
├── Organizers record speaker status in Event Page → Speakers Tab
├── Organizers upload materials on behalf of speakers (S3)
├── Organizers manage speaker profile data in system
└── No speaker self-service portal required for MVP launch
```

---

## 7. Attendee Portal (👤 Role) 🔄 PUBLIC ONLY (Epic 7 Deferred)

**MVP Status:** Public event browsing and registration implemented. Personal dashboards and PWA deferred to Epic 7.

### 7.1 Attendee Features Implemented in MVP
```
👤 Public Event Access ✅ [IMPLEMENTED IN MVP]
│   Routes: /, /events/:eventCode, /archive, /archive/:eventCode
│   Stories: 4.1.2, 4.1.3, 4.1.5, 4.1.6, 4.2
│   Features:
│   ├── Browse current and archived events
│   ├── View event details (timeline, sessions, speakers)
│   ├── Register for events (2-step wizard)
│   ├── Email confirmation/cancellation
│   └── Search historical archive (20+ years)

👤 Public Registration ✅ [IMPLEMENTED IN MVP]
│   Route: /register/:eventCode
│   Story: 4.1.5 (Public Registration Form)
│   Features:
│   ├── 3-step registration wizard
│   ├── Personal information capture
│   ├── Session selection
│   ├── Review and submit
│   └── Email confirmation
```

### 7.2 Epic 7 Features DEFERRED TO PHASE 2+
```
📦 DEFERRED TO EPIC 7 (Attendee Experience Enhancements):

Personal Attendee Dashboard 📦 [EPIC 7 - DEFERRED]
│   Story: 7.1 (Personal Dashboard)
│   Wireframe: story-5.2-personal-dashboard.md ✅
│   Features:
│   ├── Upcoming events
│   ├── My registrations
│   ├── Saved content
│   ├── Learning progress
│   └── Notifications

Content Discovery 📦 [EPIC 7 - DEFERRED]
│   Story: 7.2 (Content Discovery)
│   Wireframe: story-5.1-content-discovery.md ✅
│   Features:
│   ├── Advanced search with filters
│   ├── Content recommendations (non-AI)
│   ├── Save to library
│   ├── Rate and review
│   └── Download capabilities

Content Viewer Page 📦 [EPIC 7 - DEFERRED]
│   Story: 7.2
│   Wireframe: story-5.1-content-viewer.md ✅
│   Features:
│   ├── PDF/video/slides viewer
│   ├── Navigation controls
│   ├── Download options
│   ├── Bookmarking
│   ├── Notes/annotations
│   └── Engagement tracking

Full Library Management Page 📦 [EPIC 7 - DEFERRED]
│   Story: 7.3
│   Features:
│   ├── All saved content
│   ├── Collections/folders
│   ├── Sort and filter
│   ├── Bulk actions
│   └── Storage usage tracking

Event Listing Page 📦 [EPIC 7 - DEFERRED]
│   Story: 7.4
│   Wireframe: story-5.3-event-listing-page.md ✅
│   Features:
│   ├── Browse upcoming events
│   ├── Search events
│   ├── Registration status indicators
│   └── Filter controls

Event Details Page (Attendee View) 📦 [EPIC 7 - DEFERRED]
│   Story: 7.4
│   Wireframe: story-5.2-event-details-attendee-view.md ✅
│   Features:
│   ├── Personal schedule management
│   ├── Add to calendar
│   ├── Share event
│   └── Registration status

Filter Modal 📦 [EPIC 7 - DEFERRED]
│   Wireframe: story-5.1-filter-modal.md ✅
│   Features:
│   ├── Multi-criteria filtering
│   ├── Mobile-responsive design
│   ├── Real-time result count
│   └── Save filter presets

Mobile PWA 📦 [EPIC 7 - DEFERRED]
│   Story: 7.5 (Mobile PWA)
│   Features:
│   ├── Offline content access
│   ├── Service worker caching
│   ├── Home screen installation
│   └── Push notifications

User Settings Screen (Attendee-specific) 📦 [EPIC 7 - DEFERRED]
│   Wireframe: story-5.2-user-settings.md ✅
│   Features:
│   ├── Content preferences
│   ├── Notification preferences
│   ├── Privacy controls
│   └── App settings (PWA)
```

---

## 8. Cross-Role Shared Components ✅ IMPLEMENTED IN MVP

### 8.1 Multi-Role Components
```
Company Management Screen ✅ [IMPLEMENTED IN MVP]
│   Routes:
│   ├── /organizer/companies/* (organizer access)
│   └── /speaker/company/* (speaker own company)
│   File: web-frontend/src/components/shared/Company/CompanyManagementScreen.tsx
│   Features:
│   ├── Company CRUD (role-based permissions)
│   ├── Logo upload (S3 presigned URLs + CDN)
│   ├── Swiss UID verification
│   └── Nested routing (list + detail)

User Account Page ✅ [IMPLEMENTED IN MVP]
│   Route: /account
│   File: web-frontend/src/pages/UserAccountPage/UserAccountPage.tsx
│   Features:
│   ├── Profile Tab (all roles)
│   ├── Settings Tab (all roles)
│   ├── Role-specific information display
│   └── Language preferences
```

---

## 9. Navigation Patterns & Flows

### 9.1 Primary User Journeys (MVP)

#### Organizer Journey: Event Creation to Publishing ✅
```
Event Management Dashboard ✅
  → Create Event (Event Page - Details Tab) ✅
    → Assign Topics (Topic Management) ✅
    → Assign Speakers (Event Page - Speakers Tab) ✅
      → Slot Assignment (Drag-Drop Interface) ✅
    → Manage Tasks (Task Board or Event Page - Tasks Tab) ✅
    → Auto-Publish (EVENT_LIVE-30d speakers, EVENT_LIVE-14d agenda) ✅
      → Monitor Public Display (HomePage) ✅
```

#### Public Attendee Journey: Discovery to Registration ✅
```
HomePage (/) ✅
  → View Event Timeline ✅
  → Browse Sessions ✅
  → Register for Event (/register/:eventCode) ✅
    → Step 1: Personal Info ✅
    → Step 2: Session Selection ✅
    → Step 3: Review & Confirm ✅
      → Registration Success Page ✅
        → Email Confirmation Link ✅
```

#### Historical Archive Journey ✅
```
Archive Page (/archive) ✅
  → Filter by Date/Topics ✅
  → Search Events ✅
  → View Archived Event (/archive/:eventCode) ✅
    → Timeline Display (read-only) ✅
```

### 9.2 User Journeys DEFERRED TO PHASE 2+

#### Partner Journey: Analytics & Strategic Planning 📦 [EPIC 8 - DEFERRED]
```
Partner Analytics Dashboard 📦
  → Review Employee Analytics 📦
  → Vote on Topics 📦
  → Schedule Meetings 📦
  → View ROI Reports 📦
```

#### Speaker Journey: Invitation to Presentation 📦 [EPIC 6 - DEFERRED]
```
Invitation Response 📦
  → Accept Invitation 📦
    → Update Profile 📦
    → Submit Materials (Wizard) 📦
      → Upload Presentation 📦
      → Review Timeline 📦
```

#### Attendee Journey: Content Discovery 📦 [EPIC 7 - DEFERRED]
```
Personal Dashboard 📦
  → Discover Content 📦
    → View Content (Viewer) 📦
    → Save to Library 📦
    → Rate & Review 📦
  → Track Learning Progress 📦
```

---

## 10. Screen Implementation Summary

### 10.1 MVP Implementation Status (Epics 1-5)

**Public Layer:** 100% COMPLETE
- ✅ 11 screens/pages implemented
- ✅ Registration flow complete (2-step wizard)
- ✅ Archive browsing operational (20+ years of content)
- ✅ Email confirmation/cancellation links
- ❌ 3 screens deferred (QR codes, attendee networking, detailed session modal)

**Authentication Layer:** 100% COMPLETE
- ✅ 5 authentication flows implemented
- ✅ AWS Cognito integration
- ✅ Password reset and email verification

**Organizer Portal:** 90% COMPLETE (Core Workflows)
- ✅ 13 screens/pages implemented
- ✅ Event management (unified Event Page with tabs)
- ✅ Slot assignment (drag-drop UI)
- ✅ Topic management
- ✅ Task board (Kanban)
- ✅ Company and user management
- ✅ Basic partner coordination
- ✅ Auto-publishing (speakers @ 30d, agenda @ 14d before event)
- ❌ 7 screens deferred (workflow viz, content library, moderator queue, advanced logistics)

**Global/Shared:** 100% COMPLETE (Core Features)
- ✅ User Account Page (Profile + Settings tabs)
- ✅ Navigation bar
- ✅ Notification system (basic)
- ❌ 2 screens deferred (help center, support tickets)

### 10.2 Phase 2+ Deferral Status

**Epic 6 (Speaker Self-Service Portal):** 0% IMPLEMENTED
- 📦 6 major screens deferred
- 📦 Material submission wizard
- 📦 Speaker dashboard
- 📦 Profile management
- 📦 Invitation response flow
- Note: Organizer-driven workflow fully operational without Epic 6

**Epic 7 (Attendee Experience):** 20% IMPLEMENTED (Public Only)
- ✅ Public browsing and registration (Story 4.1.5, 4.1.6)
- ✅ Archive browsing (Story 4.2)
- 📦 8 major screens deferred
- 📦 Personal dashboard
- 📦 Content discovery and viewer
- 📦 Library management
- 📦 Mobile PWA with offline access

**Epic 8 (Partner Analytics):** 10% IMPLEMENTED (Basic Coordination)
- ✅ Basic partner meetings (Story 5.15)
- ✅ Partner directory (organizer view)
- 📦 6 major screens deferred
- 📦 Analytics dashboard
- 📦 Topic voting with weighting
- 📦 Advanced meeting automation
- 📦 ROI reporting

### 10.3 Overall Platform Completion

**MVP (Epics 1-5):** ✅ 100% COMPLETE
- Total screens planned: ~45
- Screens implemented: ~35
- Screens deferred: ~10 (low priority/enhancement features)

**Phase 2+ (Epics 6-8):** 📦 DEFERRED
- Total screens planned: ~30
- Screens implemented: 0 (Epic 6), ~2 (Epic 7), ~1 (Epic 8)
- Screens deferred: ~27

**Platform Total:**
- Screens documented with wireframes: ~75
- Screens implemented in MVP: ~38 (51%)
- Screens deferred to Phase 2+: ~37 (49%)
- **Functional completeness for launch:** ✅ 100% (all core workflows operational)

---

## 11. Key Architectural Decisions Reflected in MVP

### 11.1 What Was Built
1. **Event-Centric Workflow** ✅
   - 9-state event workflow (DRAFT → COMPLETED)
   - Unified Event Page with tabs (details, speakers, tasks)
   - Auto-publishing at defined milestones (BAT-16)
   - Slot assignment with drag-drop UI

2. **Organizer-Driven Coordination** ✅
   - Organizers manage all speakers manually (no speaker portal needed)
   - Organizers upload materials on behalf of speakers
   - Organizers handle partner coordination
   - Manual workflow sufficient for MVP launch

3. **Public-First Content** ✅
   - Public can browse 20+ years of archived events
   - Public registration with email confirmation
   - Public event display with timeline view
   - No authentication required for browsing

4. **Role-Based Access** ✅
   - AWS Cognito authentication
   - Protected routes with role-based redirects
   - Shared components with permission-based features
   - User Account Page for all roles

5. **Modern Frontend Architecture** ✅
   - React 19 with TypeScript
   - Route-level code splitting (React.lazy)
   - React Query for data fetching
   - Material-UI component library

### 11.2 What Was Deferred (Scope Reductions)
1. **Speaker Self-Service** 📦 Epic 6
   - Reason: Organizer-driven workflow operational without it
   - Value: 40% reduction in organizer workload (enhancement, not required)
   - Decision: Gather feedback before investing in Epic 6

2. **Advanced Partner Analytics** 📦 Epic 8
   - Reason: Basic meetings and coordination sufficient for MVP
   - Value: ROI visibility for partners (nice to have)
   - Decision: Assess partner demand before building dashboards

3. **Attendee Personal Features** 📦 Epic 7
   - Reason: Public browsing meets discovery needs
   - Value: Personal bookmarks and offline access (enhancement)
   - Decision: MVP focuses on organizer efficiency, not attendee personalization

4. **Content Management System** ❌ Not in MVP
   - Reason: Manual content upload acceptable for MVP
   - Priority: LOW - Can manage via S3 console/backend
   - Decision: Build if usage patterns warrant dedicated UI

5. **Workflow Visualization** ❌ Not in MVP
   - Reason: Event status indicators sufficient
   - Priority: LOW - Advanced monitoring not needed for small team
   - Decision: Status badges in Event Page meet needs

---

## 12. Future Enhancements (Post-MVP)

### 12.1 High Priority (If Needed)
```
Session Details Modal (Public)
│   Priority: MEDIUM
│   Benefit: Enhanced public event browsing
│   Effort: 1 week

Content Library/Repository
│   Priority: MEDIUM
│   Benefit: Better material organization for organizers
│   Effort: 2 weeks

Moderator Review Queue
│   Priority: MEDIUM
│   Benefit: Quality control for speaker materials
│   Effort: 2 weeks

Help Center & Documentation
│   Priority: MEDIUM
│   Benefit: Improved user onboarding
│   Effort: 1 week
```

### 12.2 Epic 6-8 Implementation (Optional)
```
Epic 6: Speaker Self-Service Portal
│   Duration: 10.5 weeks
│   ROI: 40% reduction in organizer workload
│   Decision: Implement if organizer feedback indicates high manual burden

Epic 7: Attendee Experience Enhancements
│   Duration: 8 weeks
│   ROI: Increased content engagement and mobile usage
│   Decision: Implement if attendee demand for personal features is high

Epic 8: Partner Analytics & Voting
│   Duration: 6 weeks
│   ROI: Partner satisfaction and retention
│   Decision: Implement if partners request ROI visibility
```

---

## Notes

### Sitemap Maintenance
- **Update Frequency:** After each major release or sprint
- **Source of Truth:** This file reflects actual implementation, not planned features
- **Wireframe References:** Wireframes exist for many deferred features (see `docs/wireframes/`)
- **Epic Files:** See `docs/prd/epic-*.md` for detailed story breakdowns

### Related Documentation
- **Architecture:** `docs/architecture/`
- **API Specs:** `docs/api/*.openapi.yml`
- **Stories:** `docs/stories/`
- **Wireframes:** `docs/wireframes/`
- **PRD:** `docs/prd/`

### Legend for Developers
- ✅ **[IMPLEMENTED IN MVP]** - Safe to reference in code, fully operational
- 🔄 **[PARTIAL]** - Placeholder exists, may need implementation
- ❌ **[NOT IMPLEMENTED IN MVP]** - Do not assume exists, check wireframes if needed
- 📦 **[EPIC X - DEFERRED]** - Planned for Phase 2+, wireframes may exist

---

**End of Site Map**

*This document provides a complete overview of the BATbern platform as implemented in MVP (Epics 1-5) and planned for Phase 2+ (Epics 6-8). Use in conjunction with sitemap-mermaid.md for visual representations.*
