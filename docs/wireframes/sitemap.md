# BATbern Event Management Platform - Comprehensive Site Map

**Generated:** 2025-10-03
**Version:** 1.1
**Purpose:** Complete hierarchical overview of all platform screens and navigation relationships
**Update:** Aligned with PRD v4 - FR13 and FR16 removed from MVP scope

---

## Legend

### Status Indicators
- ✅ **[EXISTS]** - Wireframe documented
- ❌ **[MISSING]** - Referenced but not documented
- 🔄 **[PARTIAL]** - Mentioned in role wireframes but needs dedicated file
- 📦 **[BACKLOG]** - Removed from MVP scope (FR13/FR16), may return post-MVP

### Role Indicators
- 🎯 **Organizer** - Event organizers and administrators
- 💼 **Partner** - Corporate partners and sponsors
- 🎤 **Speaker** - Speakers and presenters
- 👤 **Attendee** - Event attendees and participants
- 🌐 **Public** - Unauthenticated public access
- ⚙️ **Admin** - System administrators

### Priority Levels (for missing screens)
- 🔴 **HIGH** - Critical for MVP/core functionality
- 🟡 **MEDIUM** - Important for completeness
- 🟢 **LOW** - Nice to have/future enhancement

### Navigation Indicators
- → Single direction navigation
- ↔ Bidirectional navigation
- ⤴ Returns to parent
- 🔗 Cross-reference to another section

---

## Platform Architecture Overview

```
BATbern Platform
├── Public Layer (Unauthenticated)
├── Authentication Layer
├── Global/Shared Screens (All Roles)
├── Organizer Portal
├── Partner Portal
├── Speaker Portal
└── Attendee Portal
```

---

## 1. Public Layer (Unauthenticated)

### 1.1 Public Access Screens
```
🌐 Public Homepage [MISSING] 🟡
│   Story: ⚠️ MISSING STORY - Public Website & Marketing
│   Note: Public homepage with event promotion (not in current epic scope)
├── → Authentication/Login
├── → Event Catalog (Public Events)
└── → About/Information Pages

🌐 Current Event Landing Page ✅ [EXISTS]
│   Story: 2.4-current-event-landing
│   File: story-2.4-current-event-landing.md
├── → Event Registration Flow
├── → Session Details Modal ✅ [EXISTS] (story-2.4-session-details-modal.md)
├── → Speaker Profile Detail View ✅ [EXISTS] (story-7.1-speaker-profile-detail-view.md)
├── → Venue Details Screen [MISSING] 🔴
└── → Filter Modal [MISSING] 🟡

🌐 Event Registration Flow (Multi-Step) ✅ [EXISTS]
│   Story: 2.4-event-registration
│   File: story-2.4-event-registration.md
├── Step 1/3: Personal Information ✅
├── Step 2/3: Session Selection ✅
├── Step 3/3: Review & Confirm ✅
├── → Registration Confirmation Page ✅ [EXISTS] (story-2.4-registration-confirmation.md)
└── → Ticket/QR Code Page (see confirmation page)
```

---

## 2. Authentication Layer

### 2.1 Authentication Screens
```
Login Screen [MISSING] 🔴
│   Story: 1.2 - API Gateway & Authentication Service
│   Note: Authentication wireframe not yet created
├── → Forgot Password
├── → Create Account
└── → Role-Based Dashboard (post-login)

Forgot Password Flow [MISSING] 🟡
│   Story: 1.2 - API Gateway & Authentication Service
└── ⤴ Login Screen

Account Creation [MISSING] 🟡
│   Story: 1.2 - API Gateway & Authentication Service
├── Role Selection
└── → Email Verification

Email Verification [MISSING] 🟡
│   Story: 1.2 - API Gateway & Authentication Service
└── → Role-Based Dashboard
```

---

## 3. Global/Shared Screens (All Authenticated Users)

### 3.1 Core Navigation
```
Main Navigation Bar/Menu [MISSING] 🔴
│   Story: 1.17 - React Frontend Foundation
│   Note: Global navigation component
├── → Role-Specific Dashboard
├── → User Profile Screen
├── → Notification Center
├── → Help Center
└── → Logout

🌐 User Profile Screen [MISSING] 🟡
│   Story: 1.20 - User Role Management
│   Roles: All
│   Referenced From: story-5.2, story-7.3
├── Personal Information (view/edit)
├── Contact Details
├── Role-Specific Information
├── Activity History
├── → User Settings Screen
└── ⤴ Previous Screen

🌐 User Settings Screen ✅ [EXISTS] (Attendee version)
│   Story: 1.20 - User Role Management / 5.2 - Personal Dashboard
│   File: story-5.2-user-settings.md
│   Note: General user settings wireframe needed for all roles
├── Account Settings
├── Password Change
├── Email Preferences
├── → Notification Settings (from story-1.20)
├── Privacy Controls
├── Data Export/Delete
└── ⤴ User Profile Screen

🎯👤🎤💼 Notification Center ✅ [EXISTS]
│   Story: 1.20-notification-center
│   File: story-1.20-notification-center.md
│   Roles: All (role-specific content)
├── Notification List (filtered by role)
├── Mark Read/Unread
├── → Notification Settings (in User Settings)
├── → Related Content (context-specific)
└── Archive/Delete
```

### 3.2 Help & Support
```
🌐 Help Center Screen [MISSING] 🟡
│   Story: ⚠️ MISSING STORY - Help & Support System
│   Roles: All
│   Referenced From: story-5.2, multiple screens
│   Note: Help center not in current MVP scope
├── FAQ Sections
├── How-to Guides
├── Video Tutorials
├── Search Functionality
├── → Support Ticket Screen
├── → Community Forum
└── System Status

🌐 Support Ticket Screen [MISSING] 🟢
│   Roles: All
│   Referenced From: story-1.16
├── Create Ticket Form
├── Ticket History
├── Status Tracking
└── ⤴ Help Center
```

---

## 4. Organizer Portal (🎯 Role)

### 4.1 Main Dashboard & Overview
```
🎯 Event Management Dashboard ✅ [EXISTS]
│   Story: 1.16-event-management-dashboard
│   File: story-1.16-event-management-dashboard.md
├── → Event Detail/Edit Screen [MISSING] 🔴
├── → Event Settings Screen [MISSING] 🟡
├── → Workflow Visualization
├── → Topic Backlog Management
├── → Historical Archive
├── → Speaker Matching Interface
├── → Partner Directory [MISSING] 🟡
└── → System Settings [MISSING] 🟢

🎯 Workflow Visualization ✅ [EXISTS]
│   Story: 1.16-workflow-visualization
│   File: story-1.16-workflow-visualization.md
├── → Event Detail/Edit Screen [MISSING] 🔴
├── → Moderator Review Queue [MISSING] 🟡
└── ⤴ Event Management Dashboard
```

### 4.2 Event Management
```
🎯 Event Detail/Edit Screen ✅ [EXISTS]
│   Story: 1.16-event-detail-edit
│   File: story-1.16-event-detail-edit.md
├── Event Information (editable)
├── Status & Workflow Position
├── → Assigned Topics (link to Topic Backlog)
├── → Confirmed Speakers (link to Speaker Matching)
├── → Registration Statistics
├── → Event Settings Screen
├── → Publishing Controls (link to Progressive Publishing)
└── ⤴ Event Management Dashboard

🎯 Event Settings Screen [MISSING] 🟡
│   Story: 1.16 - Event Management Service Core / 4.3 - Full Progressive Publishing
│   Referenced From: story-1.16, story-4.3
│   Note: Event configuration, publishing, notifications
├── Registration Settings
├── Publishing Configuration
├── Email Templates
├── Notification Rules
├── Access Permissions
├── Integration Settings
└── ⤴ Event Detail/Edit Screen

🎯 Historical Archive ✅ [EXISTS]
│   Story: 1.18-historical-archive
│   File: story-1.18-historical-archive.md
├── → Event Detail (read-only)
├── → Content Detail/Edit Screen [MISSING] 🟡
├── Filter & Search
└── ⤴ Event Management Dashboard
```

### 4.3 Content Management
```
🎯 Topic Backlog Management ✅ [EXISTS]
│   Story: 2.2-topic-backlog-management
│   File: story-2.2-topic-backlog-management.md
├── → Topic Details Screen [MISSING] 🟡
├── → Event Detail (assign topic)
├── Topic Voting (from story-6.4)
└── ⤴ Event Management Dashboard

🎯 Basic Publishing Engine ✅ [EXISTS]
│   Story: 2.3-basic-publishing-engine
│   File: story-2.3-basic-publishing-engine.md
├── → Event Detail
├── → Content Detail/Edit Screen [MISSING] 🟡
├── Publishing Templates
└── ⤴ Event Management Dashboard

🎯 Progressive Publishing 🔄 [PARTIAL]
│   Story: 4.3-progressive-publishing
│   Note: Referenced in wireframes-organizer.md, dedicated file not yet created
├── → Event Settings (publishing config)
├── Phase Configuration
├── Content Scheduling
└── ⤴ Event Management Dashboard

🎯 Content Library/Repository Screen [MISSING] 🟡
│   Referenced From: story-3.3, story-5.1
├── Content List (all types)
├── → Content Detail/Edit Screen
├── Filters & Search
├── Bulk Operations
├── Version History
└── Usage Statistics

🎯 Content Detail/Edit Screen [MISSING] 🟡
│   Story: 1.18 - Basic Event Display & Archive
│   Referenced From: story-1.18-historical-archive, story-5.1, story-3.3
│   Note: Content viewing + metadata management
├── Content Metadata (editable)
├── File Information
├── → Associated Event/Session
├── Download Options
├── Related Content
└── ⤴ Parent Screen

🎯 Moderator Review Queue [MISSING] 🟡
│   Story: 4.1 - Content Quality Review
│   Referenced From: story-1.16, story-3.3
│   Note: Moderator quality review workflow (Step 7)
├── Pending Reviews List
├── → Content Preview
├── Review Form
├── Approve/Reject Actions
├── Feedback Notes
└── Assignment Controls
```

### 4.4 Speaker Management
```
🎯 Speaker Matching Interface ✅ [EXISTS]
│   Story: 3.1-speaker-matching-interface
│   File: story-3.1-speaker-matching-interface.md
├── → Speaker Profile Detail View [MISSING] 🔴
├── → Invitation Management Screen [MISSING] 🟡
├── → Company Management Screen [MISSING] 🟡
├── Speaker Search & Filters
├── Send Invitations
└── ⤴ Event Management Dashboard

🎯 Speaker Profile Detail View [MISSING] 🔴
│   Referenced From: story-3.1, story-7.1, story-1.16
│   Priority: HIGH - Core functionality
├── Full Speaker Profile (read-only for organizer)
├── Speaking History
├── Expertise Areas
├── Company Affiliation
├── Contact Information
├── Ratings & Reviews
├── Availability Calendar
├── Past Presentations
├── → Send Invitation
└── ⤴ Speaker Matching Interface

🎯 Invitation Management Screen [MISSING] 🟡
│   Story: 3.1 - Speaker Invitation System / 3.2 - Invitation Response Workflow
│   Referenced From: story-3.1-speaker-matching-interface, story-3.2
│   Note: Invitation tracking and bulk operations
├── All Invitations (by event)
├── Status Tracking
├── → Invitation Response (view)
├── Resend Invitation
├── Bulk Actions
└── ⤴ Speaker Matching Interface

🎯🎤 Invitation Response ✅ [EXISTS]
│   Story: 3.2-invitation-response
│   File: story-3.2-invitation-response.md
│   Roles: Organizer (view), Speaker (respond)
├── Invitation Details
├── Accept/Decline (Speaker)
├── Alternative Dates (Speaker)
├── Status View (Organizer)
└── ⤴ Parent Screen (role-dependent)

🎯 Company Management Screen [MISSING] 🟡
│   Story: 1.14 - Company Management Service
│   Referenced From: story-3.1, story-6.1
│   Note: Core company CRUD + logo management
├── Company Information Form
├── Logo Upload
├── Industry/Sector
├── Partner Status Toggle
├── → Associated Speakers/Employees
└── Statistics
```

### 4.5 Logistics & Venue Management
```
🎯 Logistics Coordination ✅ [EXISTS]
│   Story: 4.4-logistics-coordination
│   File: story-4.4-logistics-coordination.md
├── → Venue Details Screen [MISSING] 🔴
├── → Venue Booking Screen [MISSING] 🔴
├── → Catering Management Screen [MISSING] 🟡
├── Equipment Management
└── ⤴ Event Management Dashboard

🎯 Venue Details Screen [MISSING] 🔴
│   Story: 4.4 - Event Logistics Coordination
│   Referenced From: story-4.4-logistics-coordination, story-2.4
│   Priority: HIGH - Required for logistics
├── Venue Information
├── Capacity Details
├── Amenities & Facilities
├── Map/Directions
├── Contact Information
├── Booking History
├── Availability Calendar
├── Photos
├── → Venue Booking Screen
└── ⤴ Logistics Coordination

🎯 Venue Booking Screen [MISSING] 🔴
│   Story: 4.4 - Event Logistics Coordination
│   Referenced From: story-4.4-logistics-coordination
│   Priority: HIGH - Required for multi-year planning (FR21)
├── Booking Form
├── Availability Calendar
├── Pricing Information
├── Special Requirements
├── Confirmation Workflow
├── Contract Upload
└── ⤴ Venue Details Screen

🎯 Catering Management Screen [MISSING] 🟡
│   Story: 4.4 - Event Logistics Coordination
│   Referenced From: story-4.4-logistics-coordination
├── Catering Options
├── Menu Selection
├── Attendee Count
├── Dietary Restrictions
├── Pricing
├── Order Confirmation
└── Vendor Contact
```

### 4.6 Administrative Screens
```
🎯 System Settings/Configuration Screen [MISSING] 🟢
│   Story: ⚠️ MISSING STORY - System Administration
│   Referenced From: story-1.16, story-2.3
│   Note: Admin-level system configuration (not in MVP scope)
├── Platform Configuration
├── Email Template Management
├── Workflow Configuration
├── Integration Settings
├── Feature Flags
└── System Status
```

---

## 5. Partner Portal (💼 Role)

**⚠️ MAJOR SCOPE CHANGE:** FR4 (Partner Analytics) and FR9 (Automated Reports) removed from MVP.

**MVP Scope (Epic 6):**
- ✅ Topic Voting (6.1) - Partners vote on topics
- ✅ Meeting Coordination (6.2) - Seasonal partner meetings

**Moved to Backlog (Post-MVP):**
- 📦 Partner Analytics Dashboard (FR4)
- 📦 Employee Analytics (FR4)
- 📦 Brand Exposure Metrics (FR4)
- 📦 Budget Management (FR4)
- 📦 Custom Report Builder (FR9)
- 📦 Advanced Strategic Planning (FR4)

Wireframes exist for analytics features but implementation deferred based on partner feedback post-MVP.

### 5.1 Main Dashboard & Analytics (📦 BACKLOG - FR4 Removed)
```
📦 Partner Analytics Dashboard [BACKLOG] (FR4 removed from MVP)
│   Story: 6.1-partner-analytics-dashboard (wireframe exists)
│   File: story-6.1-partner-analytics-dashboard.md
│   Status: FR4 (Partner Analytics) removed from MVP scope
│   Note: May return post-MVP based on partner feedback and operational data
├── → Switch Partner Account Screen [MISSING] 🟡
├── → Metric Detail Screens [MISSING] 🔴
├── → Employee Analytics
├── → Partner Settings Screen [MISSING] 🟢
├── → Report Builder Screen
├── → Budget Management
├── → Brand Exposure
├── → Strategic Planning
├── → Partner Meetings
└── Notifications

💼 Switch Partner Account Screen [MISSING] 🟡
│   Referenced From: story-6.1
├── Partner Account List
├── Current Account Indicator
├── Quick Switch
└── Account Role/Permissions Display

💼 Metric Detail Screens (Various Types) [MISSING] 🔴
│   Referenced From: story-6.1, story-6.1-employee-analytics
│   Priority: HIGH - Core analytics functionality
├── Detailed Metric Visualization
├── Historical Trend Data
├── Drill-down Capabilities
├── Export Options
├── Comparison Tools
└── ⤴ Parent Dashboard/Report

📦 Employee Analytics [BACKLOG] (FR4 removed from MVP)
│   Story: 6.1-employee-analytics (wireframe exists)
│   File: story-6.1-employee-analytics.md
│   Status: FR4 removed - moved to backlog
├── → Metric Detail Screen (Department) [MISSING] 🔴
├── → Metric Detail Screen (Level) [MISSING] 🔴
├── → Metric Detail Screen (Team) [MISSING] 🔴
├── → Employee Detail Screen [MISSING] 🟡
├── → Learning Path Details [MISSING] 🟡
└── ⤴ Partner Analytics Dashboard

💼 Employee Detail Screen [MISSING] 🟡
│   Referenced From: story-6.1-employee-analytics
├── Employee Profile
├── Attendance History
├── Content Engagement
├── Skills Development
├── Certifications
└── ⤴ Employee Analytics
```

### 5.2 Brand & Exposure Management (📦 BACKLOG - FR4 Removed)
```
📦 Brand Exposure [BACKLOG] (FR4 removed from MVP)
│   Story: 6.2-brand-exposure (referenced in wireframes-partner.md)
│   Status: FR4 removed - brand analytics moved to backlog
├── → Exposure Type Details Screen [MISSING] 🟡
├── → Website Analytics Detail [MISSING] 🟡
├── → Social Media Report [MISSING] 🟡
├── → Competitor Benchmarking Screen [MISSING] 🟡
├── → Content Performance Details [MISSING] 🟡
├── → Speaker Brand Impact Screen [MISSING] 🟡
├── → Traffic Source Analysis [MISSING] 🟡
├── → ROI Trend Analysis [MISSING] 🟡
├── → Impression Details [MISSING] 🟡
├── → Mentions List Screen [MISSING] 🟡
├── → Package Upgrade Request Screen [MISSING] 🟡
└── ⤴ Partner Analytics Dashboard

💼 Package Upgrade Request Screen [MISSING] 🟡
│   Referenced From: story-6.2
├── Current Package Features
├── Available Upgrade Tiers
├── Feature Comparison Table
├── Pricing Information
└── Request/Purchase Workflow
```

### 5.3 Budget & Financial Management (📦 BACKLOG - FR4/FR9 Removed)
```
📦 Budget Management [BACKLOG] (FR4 removed from MVP)
│   Story: 6.3-budget-management (referenced in wireframes-partner.md)
│   Status: FR4 removed - budget analytics moved to backlog
├── → Budget Forecasting Tool Screen [MISSING] 🟡
├── → Year-over-Year Comparison Screen [MISSING] 🟡
├── → ROI Trend Analysis [MISSING] 🟡
├── Budget Allocation View
├── Expense Tracking
└── ⤴ Partner Analytics Dashboard

💼 Budget Forecasting Tool Screen [MISSING] 🟡
│   Referenced From: story-6.3
├── Budget Projection Models
├── Historical Spending Analysis
├── ROI Predictions
├── Scenario Planning Tools
└── Export Capabilities

📦 Custom Report Builder [BACKLOG] (FR9 removed from MVP)
│   Story: 6.3-custom-report-builder (referenced in wireframes-partner.md)
│   Status: FR9 (Automated Reports) removed - moved to backlog
├── → Report Preview Screen [MISSING] 🟡
├── → Report Scheduling Screen [MISSING] 🟡
├── Report Design Interface
├── Data Source Selection
├── Visualization Options
└── ⤴ Partner Analytics Dashboard

💼 Report Preview Screen [MISSING] 🟡
│   Referenced From: story-6.3
└── ⤴ Custom Report Builder

💼 Report Scheduling Screen [MISSING] 🟡
│   Referenced From: story-6.3
└── ⤴ Custom Report Builder
```

### 5.4 Strategic Planning & Goals (✅ MVP: Topic Voting Only)
```
📦 Strategic Planning [MOSTLY BACKLOG] (FR4 removed - except voting)
│   Story: 6.4-strategic-planning (referenced in wireframes-partner.md)
│   Status: Only Topic Voting in MVP; advanced features moved to backlog (FR4)
├── → Goals Management Screen [MISSING] 🔴
├── → Goal Details Screen [MISSING] 🟡
├── → New Goal Creation Screen [MISSING] 🟡
├── → Topic Voting Screen
├── → Skill Development Path Screen [MISSING] 🟡
├── → Custom Workshop Request Screen [MISSING] 🟡
├── → Certification Paths Browser Screen [MISSING] 🟡
├── → Industry Trend Report Screen [MISSING] 🟡
├── → Detailed Benchmark Comparison Screen [MISSING] 🟡
├── → Best Practices Guide Screen [MISSING] 🟡
├── → Custom Proposal Request Screen [MISSING] 🟡
└── ⤴ Partner Analytics Dashboard

📦 Goals Management Screen [BACKLOG] (FR4 removed)
│   Referenced From: story-6.4-strategic-planning (removed from MVP)
│   Status: Advanced goal tracking moved to backlog
└── Features: Goals List, Progress Tracking, Metrics Assignment (all backlog)

💼 Topic Voting Screen ✅ [EXISTS] ✨ MVP INCLUDED
│   Story: 6.4-topic-voting (Actually Story 6.1 in Epic 6)
│   File: story-6.4-topic-voting.md
│   Note: Core MVP feature - partner topic voting
├── → All Topics Browser Screen [MISSING] 🟡
├── → Topic Details Screen [MISSING] 🟡
├── Vote Submission
├── Voting History
└── ⤴ Strategic Planning

📦 Certification Paths Browser Screen [BACKLOG] (FR4 removed)
│   Referenced From: story-6.4-strategic-planning (removed)
│   Status: Employee development tracking moved to backlog
└── Features: Certification programs, prerequisites, completion tracking (all backlog)
```

### 5.5 Partner Meetings & Collaboration ✨ MVP INCLUDED
```
💼 Partner Meetings ✅ [EXISTS] ✨ MVP INCLUDED
│   Story: 6.5-partner-meetings (Actually Story 6.2 in Epic 6)
│   File: story-6.5-partner-meetings.md
│   Note: Core MVP feature - meeting coordination
├── → Meeting Calendar View Screen [MISSING] 🔴
├── → Meeting Details Screen [MISSING] 🟡
├── → Meeting Materials Library [MISSING] 🟡
├── → Agenda Proposal Screen [MISSING] 🟡
├── → Past Meeting Details [MISSING] 🟡
├── → Action Items Dashboard [MISSING] 🟡
└── ⤴ Partner Analytics Dashboard

💼 Meeting Calendar View Screen [MISSING] 🔴
│   Referenced From: story-6.5
│   Priority: HIGH - Core meeting functionality
├── Calendar Interface (month/week/day)
├── Meeting Markers
├── → Meeting Details Screen
├── → Schedule New Meeting
└── Sync with External Calendars

💼 Action Items Dashboard Screen [MISSING] 🟡
│   Referenced From: story-6.5
├── Action Items List
├── Assignment & Ownership
├── Due Dates & Priorities
├── Status Tracking
└── Completion Workflow
```

### 5.6 Partner Settings
```
💼 Partner Settings Screen [MISSING] 🟢
│   Referenced From: story-6.1, story-6.3
├── Company Settings
├── User Management
├── Notification Preferences
├── Integration Settings
├── Billing Information
└── Access Permissions
```

### 5.7 Partner Directory
```
💼 Partner Directory/List Screen [MISSING] 🟡
│   Referenced From: story-1.16, story-6.4
├── Partner List (all)
├── → Partner Detail Screen
├── Partner Tier Badges
├── Engagement Score
├── Contact Information
├── Quick Actions
└── Filter by Tier/Status

💼 Partner Detail Screen [MISSING] 🟡
│   Referenced From: story-6.1, story-1.16
├── Partner Information
├── Sponsorship Details
├── Key Contacts
├── → Analytics Summary
├── Meeting History
└── Notes & Interactions
```

---

## 6. Speaker Portal (🎤 Role)

### 6.1 Main Dashboard
```
🎤 Speaker Dashboard 🔄 [PARTIAL]
│   Story: 3.3-speaker-dashboard
│   Note: Referenced in wireframes-speaker.md, dedicated file not yet created
├── → Material Submission Wizard
├── → Presentation Upload
├── → Event Timeline
├── → Speaker Profile Management
├── → Speaker Community
├── → Communication Hub
├── → Content Library (my presentations)
└── → Invitation Response

🎤 Event Timeline ✅ [EXISTS]
│   Story: 3.5-event-timeline
│   File: story-3.5-event-timeline.md
├── → Event Details (speaker view)
├── → Session Details
├── Key Dates & Milestones
├── Task List
└── ⤴ Speaker Dashboard
```

### 6.2 Content Submission & Management
```
🎤 Material Submission Wizard ✅ [EXISTS]
│   Story: 3.3-material-submission-wizard
│   File: story-3.3-material-submission-wizard.md
├── → Presentation Upload
├── Step-by-step Submission
├── Metadata Entry
├── → Submit for Review (Moderator Queue)
└── ⤴ Speaker Dashboard

🎤 Presentation Upload ✅ [EXISTS]
│   Story: 3.3-presentation-upload
│   File: story-3.3-presentation-upload.md
├── File Upload Interface
├── → Content Detail/Edit (metadata)
├── Version Management
└── ⤴ Material Submission Wizard / Speaker Dashboard
```

### 6.3 Profile & Community
```
🎤 Speaker Profile Management ✅ [EXISTS]
│   Story: 7.1-speaker-profile-management
│   File: story-7.1-speaker-profile-management.md
├── → Speaker Profile Edit Screen [MISSING] 🟡
├── → Public Profile Preview Screen [MISSING] 🟢
├── Profile Information
├── Expertise Tags
├── Bio & Background
├── Social Media Links
└── ⤴ Speaker Dashboard

🎤 Speaker Profile Edit Screen [MISSING] 🟡
│   Referenced From: story-3.3, story-7.1
├── Personal Information Form
├── Photo Upload
├── Expertise Tags
├── Bio Editor
├── Social Media Links
├── Presentation Preferences
├── Privacy Settings
└── ⤴ Speaker Profile Management

🎤 Public Profile Preview Screen [MISSING] 🟢
│   Referenced From: story-7.1
├── Read-only Public View
├── As-Seen-By-Others Perspective
├── Privacy Indicators
├── → Edit (back to management)
└── Share Profile Options

🎤 Speaker Community ✅ [EXISTS] (Basic networking only - FR16 removed)
│   Story: 7.1-speaker-community
│   File: story-7.1-speaker-community.md
│   Note: Advanced features (mentoring, forums, resources) moved to backlog
├── Featured Speakers
├── Basic Speaker Directory
├── Connection Requests
└── ⤴ Speaker Dashboard

🎤 Full Speaker Network Screen [MISSING] 🟡 (Simplified)
│   Referenced From: story-7.1
│   Note: Basic directory only, no advanced networking
├── Searchable Speaker Directory
├── Filter by Expertise
├── Speaker Cards
├── → Speaker Profile Detail View
└── ⤴ Speaker Community

📦 Discussion Thread Screen [BACKLOG] 🟢 (FR16)
│   Referenced From: story-7.4 (removed)
│   Status: Moved to backlog - advanced community features
└── May return post-MVP

📦 Mentor Profile Screen [BACKLOG] 🟢 (FR16)
│   Referenced From: story-7.1 (removed)
│   Status: Moved to backlog - mentoring features
└── May return post-MVP

📦 Resource Viewer Screen [BACKLOG] 🟢 (FR16)
│   Referenced From: story-7.1 (removed)
│   Status: Moved to backlog - resource library
└── May return post-MVP
```

### 6.4 Communication
```
🎤 Communication Hub 🔄 [PARTIAL]
│   Story: 7.3-communication-hub
│   Note: Referenced in wireframes-speaker.md, dedicated file not yet created
├── → Announcement Details Screen [MISSING] 🟡
├── Messages Inbox
├── Event Updates
├── System Notifications
└── ⤴ Speaker Dashboard

🎤 Announcement Details Screen [MISSING] 🟡
│   Referenced From: story-7.3
├── Full Announcement Text
├── Author & Timestamp
├── Attachments/Links
├── Target Audience Info
├── Reply/Comment
└── ⤴ Communication Hub
```

### 6.5 Community Features (FR16 - REMOVED FROM MVP)
```
📦 Community Features [BACKLOG] (FR16)
│   Story: 7.4-community-features (REMOVED)
│   Status: Entire section moved to backlog
│   Note: Advanced community features deferred post-MVP
│
└── Features Removed:
    ├── Discussion Forums & Topics
    ├── Study Groups
    ├── Advanced Networking
    ├── Mentoring Programs
    └── Resource Libraries

Note: Story 7.4 replaced with simpler "Community Feedback System" in Epic 7
      focusing on post-event surveys and feedback collection only.
```

### 6.6 Speaker Analytics
```
🎤 Speaker Analytics (Detailed) [MISSING] 🟡
│   Referenced From: story-7.1
├── Session Ratings
├── Attendee Feedback
├── Content Downloads
├── Engagement Metrics
└── Trend Analysis
```

---

## 7. Attendee Portal (👤 Role)

### 7.1 Main Dashboard & Discovery
```
👤 Personal Attendee Dashboard ✅ [EXISTS]
│   Story: 5.2-personal-dashboard
│   File: story-5.2-personal-dashboard.md
│   Also: wireframes-attendee.md
├── → Event Details Page (Attendee View) [MISSING] 🔴
├── → Content Viewer Page [MISSING] 🔴
├── → Full Library Management Page [MISSING] 🟡
├── → Learning Path Details Modal [MISSING] 🟡
├── → Learning Path Selection Modal [MISSING] 🟡
├── → Achievements Gallery Modal [MISSING] 🟢
├── → Community Leaderboard Page [MISSING] 🟢
├── → User Settings Screen [MISSING] 🔴
├── → Help Center Screen [MISSING] 🟡
├── Upcoming Events
├── My Registrations
├── Saved Content
├── Learning Progress
└── Notifications

👤 Event Details Page (Attendee View) [MISSING] 🔴
│   Story: 2.4 - Current Event Landing Page / 1.18 - Basic Event Display
│   Referenced From: story-5.2, story-2.4-current-event-landing
│   Priority: HIGH - Core attendee functionality
│   Note: Attendee-specific event detail view
├── Event Information (read-only)
├── → Session Schedule
├── → Speaker Lineup
├── Registration Status
├── Add to Calendar
├── Share Event
├── Related Content
└── ⤴ Personal Dashboard

👤 Content Discovery ✅ [EXISTS] (AI features removed per FR13)
│   Story: 5.1-content-discovery
│   File: story-5.1-content-discovery.md
│   Note: AI-powered recommendations moved to backlog
├── → Content Viewer Page [MISSING] 🔴
├── → Filter Modal [MISSING] 🟡
├── → All Reviews Screen [MISSING] 🟢
├── → Top Rated Content Screen [MISSING] 🟢
├── Search & Browse
├── Basic Recommendations (non-AI)
├── Save Content
├── Rate & Review
└── ↔ Personal Dashboard

👤 Content Viewer Page [MISSING] 🔴
│   Story: 5.1 - Content Discovery / 1.18 - Basic Event Display & Archive
│   Referenced From: story-5.1, story-5.2, story-5.3
│   Priority: HIGH - Core content consumption
│   Note: Universal content viewer for presentations/documents
├── Content Display (PDF/Video/Slides)
├── Navigation Controls
├── Download Option
├── Bookmarking
├── Notes/Annotations
├── Related Content Suggestions
└── ⤴ Parent Screen

👤 Full Library Management Page [MISSING] 🟡
│   Referenced From: story-5.2
├── All Saved Content
├── Collections/Folders
├── Sort & Filter Options
├── → Content Viewer
├── Bulk Actions
├── Storage Usage
└── Sharing Capabilities
```

### 7.2 Mobile & Offline Experience
```
👤 Mobile PWA Experience 🔄 [PARTIAL]
│   Story: 5.3-mobile-pwa
│   Note: Referenced in wireframes-attendee.md, dedicated file not yet created
├── → Offline Content & Download Manager
├── → Storage Management Screen [MISSING] 🟡
├── → User Settings (PWA-specific) [MISSING] 🔴
├── Mobile-Optimized Navigation
├── Push Notifications
├── Offline Capabilities
└── Install Prompt

👤 Offline Content & Download Manager 🔄 [PARTIAL]
│   Story: 5.3-offline-content
│   Note: Referenced in wireframes-attendee.md, dedicated file not yet created
├── → Storage Management Screen [MISSING] 🟡
├── → Offline Settings Configuration Screen [MISSING] 🟡
├── → Bundle Content List Screen [MISSING] 🟡
├── → Smart Sync Rules Configuration Screen [MISSING] 🟢
├── → Sync History Screen [MISSING] 🟡
├── → Offline Content Viewer [MISSING] 🔴
├── Downloaded Content List
├── Sync Status
└── ⤴ Personal Dashboard / Mobile PWA

👤 Storage Management Screen [MISSING] 🟡
│   Referenced From: story-5.3 (multiple)
├── Storage Usage Breakdown
├── Downloaded Content List
├── Clear Cache Options
├── Download Quality Settings
├── Auto-cleanup Rules
└── Available Space Indicator

👤 Offline Settings Configuration Screen [MISSING] 🟡
│   Referenced From: story-5.3
├── Auto-download Preferences
├── Sync Schedule
├── Network Usage Limits
├── Content Priority Rules
├── Storage Limits
└── Notification Settings

👤 Smart Sync Rules Configuration Screen [MISSING] 🟢
│   Referenced From: story-5.3
├── Rule Creation Interface
├── Condition Builder
├── Priority Settings
├── Test/Preview Rules
├── Active Rules List
└── Rule Templates
```

### 7.3 Learning & Progress
```
👤 Learning Path Details Modal/Page [MISSING] 🟡
│   Referenced From: story-5.2
├── Path Overview & Objectives
├── Required Content Items
├── Progress Tracking
├── Estimated Completion Time
├── Enroll/Start Button
└── Related Paths

👤 Achievements Gallery Modal [MISSING] 🟢
│   Referenced From: story-5.2
├── All Achievements (earned/locked)
├── Achievement Details
├── Earn Criteria
├── Rarity/Statistics
├── Share Options
└── Related Achievements

👤 Community Leaderboard Page [MISSING] 🟢
│   Referenced From: story-5.2
├── Ranked User List
├── Scoring Methodology
├── Filter by Timeframe/Category
├── User Position Highlight
├── → User Profile Links
└── Achievement Badges
```

### 7.4 Event Registration & Attendance
```
👤 Event Listing Page [MISSING] 🟡
│   Referenced From: wireframes-attendee.md
├── → Event Details Page
├── → Filter Modal
├── Browse Upcoming Events
├── Search Events
└── Registration Status Indicators

👤 Full Speaker List [MISSING] 🟡
│   Referenced From: wireframes-attendee.md
├── → Speaker Profile Detail View
├── Speaker Directory
├── Filter by Expertise
└── Search Speakers

👤 Complete Event Schedule [MISSING] 🟡
│   Referenced From: wireframes-attendee.md
├── → Session Details Modal
├── Schedule Grid/Calendar View
├── Filter by Track/Time
├── Add to Personal Schedule
└── Export Calendar

👤 Session Details Modal [MISSING] 🔴
│   Referenced From: story-2.4
│   Priority: HIGH - Core event feature
├── Session Title & Description
├── → Speaker Information
├── Time & Location
├── Capacity/Availability
├── Add to Schedule
├── Share Session
└── Related Sessions

👤 Filter Modal (Multiple Contexts) [MISSING] 🟡
│   Referenced From: story-5.1, story-2.4
├── Filter Criteria Options
├── Multi-select Capabilities
├── Clear Filters
├── Apply Button
├── Save Filter Preset
└── Active Filters Indicator

👤 Registration Confirmation Page [MISSING] 🔴
│   Referenced From: story-2.4
│   Priority: HIGH - Registration flow completion
├── Confirmation Message
├── Registration Summary
├── → Ticket/QR Code Page
├── Calendar Download
├── Email Confirmation Indicator
├── Next Steps
└── Edit Registration Option

👤 Ticket/QR Code Page [MISSING] 🔴
│   Referenced From: story-2.4
│   Priority: HIGH - Event access requirement
├── Event QR Code
├── Ticket Details
├── Check-in Instructions
├── Add to Wallet
├── Print Option
└── Transfer Ticket

👤 Attendee List Modal [MISSING] 🟢
│   Referenced From: story-2.4
├── Registered Attendees
├── → User Profile Preview
├── Networking Options
├── Filter by Company/Role
├── Privacy Controls
└── Connection Requests
```

### 7.5 Community & Social (FR16 - MOSTLY REMOVED)
```
📦 Community Forum/Discussion Page [BACKLOG] (FR16)
│   Referenced From: wireframes-attendee.md (removed)
│   Status: Advanced forums moved to backlog
└── May return post-MVP

👤 All Reviews Screen [MISSING] 🟢 (Basic version retained)
│   Referenced From: story-5.1
│   Note: Simple reviews only, no advanced social features
├── Review List
├── Filter by Rating/Date
├── Sort Options
├── Review Details
└── Report Inappropriate

📦 Study Group Details Screen [BACKLOG] (FR16)
│   Referenced From: story-7.4 (removed)
│   Status: Study groups moved to backlog
└── May return post-MVP
```

### 7.6 Attendee Settings & Help
```
👤 User Settings Screen (Attendee-specific) ✅ [EXISTS]
│   Story: 5.2-user-settings
│   File: story-5.2-user-settings.md
├── Profile Settings
├── Notification Preferences
├── Privacy Controls
├── Content Preferences
├── App Settings (for PWA)
└── Language/Accessibility

👤 Help Center Screen [MISSING] 🟡
│   Referenced From: story-5.2, multiple screens
├── FAQ Sections
├── How-to Guides
├── Video Tutorials
├── Search Functionality
├── → Support Ticket
├── → Community Forum
└── System Status
```

---

## 8. Cross-Role & Shared Features

### 8.1 Content Management (Multi-Role)
```
🎯🎤👤 Content Detail/Edit Screen [MISSING] 🟡
│   Roles: Organizer (full edit), Speaker (own content), Attendee (view)
│   Referenced From: story-1.18, story-5.1, story-3.3
├── Content Metadata
├── File Information
├── Associated Event/Session
├── Download Options
├── Edit Capabilities (role-dependent)
├── Related Content
└── ⤴ Parent Screen (role-dependent)
```

### 8.2 Event Information (Multi-Role)
```
🎯💼🎤👤 Event Details (Role-Specific Views) [MISSING] 🔴
│   Priority: HIGH - Core functionality for all roles
│   Variants:
│   ├── Organizer: Full edit access → Event Detail/Edit Screen
│   ├── Partner: Analytics view → linked from Partner Analytics
│   ├── Speaker: Task/timeline view → linked from Event Timeline
│   └── Attendee: Public info view → Event Details Page (Attendee View)
```

### 8.3 Profile & Directory (Multi-Role)
```
🎯🎤👤 Speaker Profile Detail View [MISSING] 🔴
│   Roles: Organizer (search), Speaker (own), Attendee (view)
│   Different access levels by role

🌐 User Profile Screen [MISSING] 🟡
│   Roles: All
│   Role-specific information displayed
```

---

## 9. Navigation Patterns & Flows

### 9.1 Primary User Journeys

#### Organizer Journey: Event Creation to Publishing
```
Event Management Dashboard
  → Create Event (Event Detail/Edit)
    → Assign Topics (Topic Backlog Management)
    → Match Speakers (Speaker Matching Interface)
      → Send Invitations (Invitation Management)
        → Track Responses (Invitation Response)
    → Configure Venue (Logistics Coordination)
      → Book Venue (Venue Booking Screen)
    → Publish Event (Progressive Publishing)
      → Monitor Registration (Event Detail/Edit)
```

#### Partner Journey: Analytics & Strategic Planning
```
Partner Analytics Dashboard
  → Review Employee Analytics
    → Identify Training Needs
      → Set Goals (Goals Management)
        → Vote on Topics (Topic Voting)
  → Track ROI (Budget Management)
    → Generate Reports (Custom Report Builder)
  → Schedule Meetings (Partner Meetings)
```

#### Speaker Journey: Invitation to Presentation
```
Invitation Response
  → Accept Invitation
    → Update Profile (Speaker Profile Management)
    → Submit Materials (Material Submission Wizard)
      → Upload Presentation (Presentation Upload)
      → Review Timeline (Event Timeline)
    → Engage Community (Speaker Community)
```

#### Attendee Journey: Discovery to Attendance
```
Current Event Landing Page
  → Register for Event (Event Registration Flow)
    → Select Sessions (Step 2/3)
    → Confirm Registration (Step 3/3)
      → Get Ticket (Ticket/QR Code Page)
  → Discover Content (Content Discovery)
    → View Content (Content Viewer)
    → Save to Library (Full Library Management)
  → Track Progress (Personal Dashboard)
```

### 9.2 Cross-Role Navigation Points

#### Shared Navigation Targets
- User Profile Screen (from any role)
- Notification Center (from any role)
- Help Center (from any role)
- Content Viewer (multi-role access)
- Event Details (role-specific views)
- Speaker Profiles (multi-role access)

---

## 10. Screen Status Summary

### 10.1 Implementation Status Overview

**Total Screens Identified (MVP Scope):** ~110 screens
**Removed from MVP:**
- FR13 (AI Content Discovery): ~5 screens
- FR16 (Advanced Community): ~12 screens
- FR4 (Partner Analytics): ~25 screens
- FR9 (Automated Reports): ~5 screens
**Total Backlog:** ~47 screens moved to backlog

#### By Status
- ✅ **Documented (EXISTS):** 26 story wireframes + 4 role overview files = 30 files total
  - Individual story wireframes: 26 dedicated screen files
  - Role overview documents: 4 files (organizer, speaker, partner, attendee)
- 🔄 **Partial:** ~4 screens (referenced in role docs but need dedicated files)
  - Examples: Progressive Publishing, Speaker Dashboard, Mobile PWA, Offline Content
- ❌ **Missing:** ~50 screens (MVP scope, not yet documented)
- 📦 **Backlog:** ~47 screens (FR4/FR9/FR13/FR16 - analytics, reports, AI, community)

#### By Priority (Missing Screens Only)
- 🔴 **HIGH Priority:** 28 screens
- 🟡 **MEDIUM Priority:** 52 screens
- 🟢 **LOW Priority:** 16 screens

### 10.2 Coverage by Role

#### Organizer Portal
- **Existing Wireframes:** 7 dedicated files + 1 role overview
  - Event Management Dashboard, Workflow Visualization, Event Detail/Edit
  - Topic Backlog, Basic Publishing, Logistics Coordination
  - Notification Center
- **Partial:** 1 screen (Progressive Publishing - in role doc)
- **Missing:** ~16 screens
- **Coverage:** ~30% complete

#### Partner Portal
- **MVP Scope:** 2 features only (FR4/FR9 removed)
  - ✅ Topic Voting (wireframe exists)
  - ✅ Partner Meetings (wireframe exists)
- **Backlog (FR4/FR9):** ~25 screens moved to backlog
  - Partner Analytics Dashboard, Employee Analytics
  - Brand Exposure, Budget Management
  - Custom Report Builder, Strategic Planning
- **Missing (MVP):** ~5 screens for voting & meetings
- **Coverage:** 40% of MVP scope (2 of 2 core features have wireframes)

#### Speaker Portal
- **Existing Wireframes:** 6 dedicated files + 1 role overview
  - Speaker Matching Interface, Invitation Response
  - Material Submission Wizard, Presentation Upload
  - Event Timeline, Speaker Profile Management, Speaker Profile Detail View
- **Partial:** 2 screens (Speaker Dashboard, Communication Hub - in role doc)
- **Missing:** ~12 screens
- **Coverage:** ~33% complete

#### Attendee Portal
- **Existing Wireframes:** 7 dedicated files + 1 role overview
  - Current Event Landing, Event Registration, Registration Confirmation
  - Session Details Modal, Content Discovery, Personal Dashboard
  - Historical Archive, User Settings
- **Partial:** 2 screens (Mobile PWA, Offline Content - in role doc)
- **Missing:** ~20 screens
- **Coverage:** ~30% complete

#### Global/Shared
- **Existing:** 1 wireframe (Notification Center)
- **Missing:** 5 screens
- **Coverage:** ~17% complete

### 10.3 Feature Area Coverage

#### Event Management
- **Coverage:** 60% (core workflows documented)
- **Gaps:** Detail/edit screens, settings

#### Analytics & Reporting
- **Status:** FR4 and FR9 removed from MVP
- **Coverage:** 0% for MVP (all analytics features moved to backlog)
- **Backlog:** Partner analytics dashboards, employee metrics, brand exposure, budget management, custom reports

#### Content Management
- **Coverage:** 45% (upload/submission exists)
- **Gaps:** Viewers, library management, moderation
- **Note:** AI-powered discovery removed (FR13)

#### Community & Social
- **Coverage:** 15% (basic features only)
- **Status:** Advanced features removed (FR16)
- **Backlog:** Discussion forums, mentoring, study groups, resource libraries

#### Logistics & Operations
- **Coverage:** 35% (coordination documented)
- **Gaps:** Venue details, booking, catering

---

## 11. Recommended Implementation Order

### Phase 1: Critical Missing Screens (MVP)
1. Event Detail/Edit Screen 🔴
2. Speaker Profile Detail View 🔴
3. Venue Details Screen 🔴
4. Venue Booking Screen 🔴
5. Content Viewer Page 🔴
6. Event Details Page (Attendee View) 🔴
7. Session Details Modal 🔴
8. User Settings Screen 🔴
9. Metric Detail Screens 🔴
10. Goals Management Screen 🔴

### Phase 2: Important Supporting Screens
11. Content Detail/Edit Screen 🟡
12. Moderator Review Queue 🟡
13. Full Speaker Network Screen 🔴
14. Discussion Thread Screen 🔴
15. Meeting Calendar View Screen 🔴
16. Registration Flow Steps 2/3 & 3/3 🔴
17. Registration Confirmation Page 🔴
18. Ticket/QR Code Page 🔴

### Phase 3: Feature Completion
19-40. Remaining MEDIUM priority screens

### Phase 4: Enhancement & Polish
41-96. LOW priority screens and nice-to-haves

---

## 12. Maintenance & Updates

### Document Maintenance
- **Review Frequency:** Bi-weekly during active development
- **Update Triggers:**
  - New wireframe created
  - Navigation flow changes
  - Feature scope adjustments
- **Version Control:** Track in git with PRD/architecture updates

### Validation Checklist
- [ ] All new wireframes added to site map
- [ ] Navigation relationships verified
- [ ] Role access documented
- [ ] Priority levels assigned
- [ ] Cross-references updated
- [ ] User journey flows validated

---

## 12. Missing Stories Analysis

### Screens Without Assigned Stories

Several screens identified in the sitemap do not have corresponding stories in the current epic structure. These represent potential gaps in the PRD that should be addressed:

#### ⚠️ Public & Marketing Screens (Not in MVP Scope)
- **Public Homepage** - Public website with event promotion
  - Status: Not planned for MVP
  - Rationale: Focus on authenticated user experiences first
  - Future: Post-MVP public marketing site

#### ⚠️ Help & Support (Not in MVP Scope)
- **Help Center Screen** - Comprehensive help system
- **Support Ticket Screen** - Customer support ticketing
  - Status: Not in current MVP scope
  - Rationale: Start with basic documentation; add support system based on user feedback
  - Future: Consider post-MVP or integrate with external support tool

#### ⚠️ System Administration (Not in MVP Scope)
- **System Settings/Configuration Screen** - Admin-level platform configuration
  - Status: Not in MVP scope
  - Rationale: Configuration will be code-based or environment variables for MVP
  - Future: Add admin UI post-MVP as platform matures

#### ✅ Screens with Story Assignments

Most major screens have been mapped to existing stories:

**Epic 1 - Foundation:**
- Story 1.2: Authentication screens (Login, Password Reset, Account Creation)
- Story 1.14: Company Management screens
- Story 1.17: Main Navigation, Frontend Foundation
- Story 1.18: Content Detail/Edit, Event Display & Archive
- Story 1.20: User Profile, User Settings, Role Management

**Epic 2 - Event Creation:**
- Story 2.2: Topic Backlog Management, Topic Details
- Story 2.4: Current Event Landing, Event Registration, Session Details

**Epic 3 - Speaker Management:**
- Story 3.1: Speaker Matching, Invitation Management
- Story 3.2: Invitation Response
- Story 3.3: Material Submission, Presentation Upload

**Epic 4 - Event Finalization:**
- Story 4.1: Moderator Review Queue, Content Quality Review
- Story 4.3: Event Settings (publishing configuration)
- Story 4.4: Venue Details, Venue Booking, Catering Management

**Epic 5 - Attendee Experience:**
- Story 5.1: Content Discovery, Content Viewer
- Story 5.2: Personal Dashboard, User Settings

**Epic 6 - Partner Coordination:**
- Story 6.1: Topic Voting
- Story 6.2: Partner Meetings

### Recommendation

**For MVP:**
- Proceed with assigned stories
- Defer public homepage, help center, and system admin screens to post-MVP
- Focus on core authenticated user experiences

**Post-MVP Prioritization:**
- Phase 1: Help Center (user demand)
- Phase 2: Public Marketing Site (growth)
- Phase 3: System Administration (operational efficiency)

---

## Notes

1. **MVP Scope Changes (2025-10-03):**
   - **Removed from MVP:**
     - **FR4**: Partner Analytics (~25 screens) - Detailed analytics dashboards, employee metrics, brand exposure, budget management, strategic planning
     - **FR9**: Automated Reports (~5 screens) - Custom report builder, scheduled reports, advanced analytics
     - **FR13**: AI-powered Content Discovery (~5 screens) - AI recommendations, smart content suggestions
     - **FR16**: Advanced Community Features (~12 screens) - Forums, mentoring, study groups, resource libraries
   - **Total Removed:** ~47 screens moved to backlog
   - **Partner MVP Scope:**
     - ✅ Topic Voting (6.1) - Core MVP feature
     - ✅ Meeting Coordination (6.2) - Core MVP feature
     - 📦 All analytics features → Backlog (FR4/FR9)
   - **Rationale:** Focus on core event management, speaker coordination, and essential partner collaboration (voting & meetings) for MVP. Advanced analytics, reports, AI, and community features to be added post-MVP based on operational data and partner feedback.

2. **Modal vs. Page Decisions Needed:** Many detail/edit screens could be modals instead of full pages. Recommend UX review for each.

3. **URL Routing Strategy:** Site map provides foundation for URL structure planning. Consider role-based prefixes (e.g., /organizer/*, /partner/*, /speaker/*, /attendee/*).

4. **Responsive Design:** Many screens will need mobile variants, especially for Attendee role. Mobile PWA (story-5.3) provides patterns.

5. **Accessibility:** All screens should follow WCAG 2.1 AA standards. Document accessibility requirements per screen type.

6. **State Management:** Complex navigation flows suggest need for robust state management (Redux/Zustand). Track user context across role switches.

7. **Performance:** Consider lazy loading for role-specific portals. Pre-load critical paths only.

8. **Security:** Role-based access control (RBAC) must be enforced at navigation level. Document permissions per screen.

9. **Deep Linking:** All screens should support deep linking for bookmarking and sharing (where privacy allows).

10. **Backlog Features:** Community forums, mentoring programs, study groups, AI-powered recommendations, and advanced networking features deferred to post-MVP. Focus remains on core event management, speaker coordination, partner analytics, and basic attendee experience.

---

**End of Site Map**

*This document represents the complete navigational architecture of the BATbern Event Management Platform as of 2025-10-03 (v1.1).*

**Updates in v1.1:**
- Aligned with PRD v4 scope changes:
  - FR4 (Partner Analytics) removed - ~25 screens to backlog
  - FR9 (Automated Reports) removed - ~5 screens to backlog
  - FR13 (AI Discovery) removed - ~5 screens to backlog
  - FR16 (Community Features) removed - ~12 screens to backlog
- Updated status markers to reflect actual wireframe implementation status
- 26 dedicated story wireframes documented (✅ EXISTS)
- 4 screens referenced in role docs but not yet in dedicated files (🔄 PARTIAL)
- 50+ screens identified but not yet documented for MVP (❌ MISSING)
- 47 advanced features moved to backlog (📦 BACKLOG)
- **Story Mapping:** All missing screens mapped to existing stories or identified as missing stories
  - Most screens mapped to Stories 1.2, 1.14, 1.17, 1.18, 1.20, 2.2, 2.4, 3.1, 3.2, 3.3, 4.1, 4.3, 4.4, 5.1, 5.2, 6.1, 6.2
  - 3 screen categories identified as missing stories (Public Homepage, Help Center, System Admin) - deferred to post-MVP

*Use this sitemap in conjunction with individual wireframe files in docs/wireframes/ and the navigation-gaps-analysis.md document for comprehensive understanding of the platform structure.*
