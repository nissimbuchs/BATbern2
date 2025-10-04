# BATbern Event Management Platform - Comprehensive Site Map

**Generated:** 2025-10-04
**Version:** 1.2
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
🌐 Current Event Landing Page ✅ [EXISTS]
│   Story: 2.4-current-event-landing
│   File: story-2.4-current-event-landing.md
│   Note: Is directly the public homepage
├── → Authentication/Login
├── → Event Catalog (Public Events)
└── → About/Information Pages
├── → Event Registration Flow
├── → Session Details Modal ✅ [EXISTS] (story-2.4-session-details-modal.md)
├── → Speaker Profile Detail View ✅ [EXISTS] (story-7.1-speaker-profile-detail-view.md)

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
Login Screen ✅ [EXISTS]
│   Story: 1.2 - API Gateway & Authentication Service
│   File: story-1.2-login-screen.md
├── → Forgot Password
├── → Create Account
└── → Role-Based Dashboard (post-login)

Forgot Password Flow ✅ [EXISTS]
│   Story: 1.2 - API Gateway & Authentication Service
│   File: story-1.2-forgot-password.md
└── ⤴ Login Screen

Account Creation ✅ [EXISTS]
│   Story: 1.2 - API Gateway & Authentication Service
│   File: story-1.2-account-creation.md
├── Role Selection
└── → Email Verification

Email Verification ✅ [EXISTS]
│   Story: 1.2 - API Gateway & Authentication Service
│   File: story-1.2-email-verification.md
└── → Role-Based Dashboard
```

---

## 3. Global/Shared Screens (All Authenticated Users)

### 3.1 Core Navigation
```
Main Navigation Bar/Menu ✅ [EXISTS]
│   Story: 1.17 - React Frontend Foundation
│   File: story-1.17-main-navigation.md
├── → Role-Specific Dashboard
├── → User Profile Screen
├── → Notification Center
├── → Help Center
└── → Logout

🌐 User Profile Screen ✅ [EXISTS]
│   Story: 1.20 - User Role Management
│   File: story-1.20-user-profile.md
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

---

## 4. Organizer Portal (🎯 Role)

### 4.1 Main Dashboard & Overview
```
🎯 Event Management Dashboard ✅ [EXISTS]
│   Story: 1.16-event-management-dashboard
│   File: story-1.16-event-management-dashboard.md
├── → Event Detail/Edit Screen
├── → Event Settings Screen
├── → Workflow Visualization
├── → Topic Backlog Management
├── → Historical Archive
├── → Speaker Matching Interface
├── → Partner Directory ✅
└── → System Settings

🎯 Workflow Visualization ✅ [EXISTS]
│   Story: 1.16-workflow-visualization
│   File: story-1.16-workflow-visualization.md
│   Note: Event Detail/Edit screen also sets the moderator of that Event (one of the organizers)
├── → Event Detail/Edit Screen
└── ⤴ Event Management Dashboard
```

### 4.2 Event Management
```
🎯 Event Detail/Edit Screen ✅ [EXISTS]
│   Story: 1.16-event-detail-edit
│   File: story-1.16-event-detail-edit.md
├── Event Information (editable)
├── Moderator of that Event (one of the organizers) (editable)
├── Status & Workflow Position
├── → Assigned Topics (link to Topic Backlog)
├── → Confirmed Speakers (link to Speaker Matching)
├── → Registration Statistics
├── → Event Settings Screen
├── → Publishing Controls (link to Progressive Publishing)
└── ⤴ Event Management Dashboard

🎯 Event Settings Screen ✅ [EXISTS]
│   Story: 1.16 - Event Management Service Core / 4.3 - Full Progressive Publishing
│   File: story-1.16-event-settings.md
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
├── → Content Detail/Edit Screen ✅
├── Filter & Search
└── ⤴ Event Management Dashboard
```

### 4.3 Content Management
```
🎯 Topic Backlog Management ✅ [EXISTS]
│   Story: 2.2-topic-backlog-management
│   File: story-2.2-topic-backlog-management.md
├── → Topic Details Screen ✅ [EXISTS]
├── → Event Detail (assign topic)
├── Topic Voting (from story-6.4)
└── ⤴ Event Management Dashboard

🎯 Topic Details Screen ✅ [EXISTS]
│   Story: 2.2-topic-detail-screen
│   File: story-2.2-topic-detail-screen.md
│   Referenced From: story-2.2, story-6.4
│   Note: Complete topic detail with usage history, partner feedback, AI insights
├── Topic Overview & Metadata
├── Usage Metrics & Staleness Analysis
├── Usage History (all events)
├── Performance Trends Charts
├── Partner Interest & Feedback
├── Similarity Analysis (ML-powered)
├── Speaker History
├── AI Insights & Recommendations
├── → Event Details (historical events)
├── → Speaker Profile Detail View
├── → Related Topic Details (similar topics)
└── ⤴ Topic Backlog Management

🎯 Basic Publishing Engine ✅ [EXISTS]
│   Story: 2.3-basic-publishing-engine
│   File: story-2.3-basic-publishing-engine.md
├── → Event Detail
├── → Content Detail/Edit Screen ✅
├── Publishing Templates
└── ⤴ Event Management Dashboard

🎯 Progressive Publishing ✅ [EXISTS]
│   Story: 4.3-progressive-publishing
│   File: story-1.16-event-settings.md (Publishing tab)
│   Note: Progressive publishing configuration available in Event Settings screen
├── → Event Settings (publishing config)
├── Phase Configuration
├── Content Scheduling
└── ⤴ Event Management Dashboard

🎯 Content Library/Repository Screen ✅ [EXISTS]
│   Story: 3.3 - Material Submission Wizard
│   File: story-3.3-content-library-repository.md
│   Referenced From: story-3.3, story-5.1
│   Note: Multi-role content repository with version control, analytics, bulk operations
├── Content List (all types: slides, videos, handouts, recordings)
├── → Content Detail/Edit Screen ✅
├── Filters & Search (role-based visibility)
├── Bulk Operations (download ZIP, tag, archive, delete)
├── Version History (compare, revert, change notes)
├── Usage Statistics (views, downloads, ratings, audience insights)
└── Storage Quota Management (45.3 GB / 200 GB)

🎯 Content Detail/Edit Screen ✅ [EXISTS]
│   Story: 1.18 - Basic Event Display & Archive
│   File: story-multi-role-content-detail-edit.md
│   Referenced From: story-1.18-historical-archive, story-5.1, story-3.3
│   Note: Multi-role content viewing + metadata management (Organizer/Speaker/Attendee)
├── Content Metadata (editable)
├── File Information
├── → Associated Event/Session
├── Download Options
├── Related Content
└── ⤴ Parent Screen

🎯 Moderator Review Queue ✅ [EXISTS]
│   Story: 4.1 - Content Quality Review (Workflow Step 7)
│   File: story-4.1-moderator-review-queue.md
│   Referenced From: story-1.16, story-3.3
│   Priority: MEDIUM - Quality control workflow
│   Note: Moderator quality review workflow before slot assignment
├── Pending Reviews List (with urgency indicators and status)
├── → Content Preview (abstract, materials, speaker info)
├── Review Form (quality checks, ratings, feedback)
├── Approve/Reject Actions (approve, request changes, reject)
├── Feedback Notes (public feedback to speaker + private notes)
└── Assignment Controls (claim reviews, prevent duplicate work)
```

### 4.4 Speaker Management
```
🎯 Speaker Matching Interface ✅ [EXISTS]
│   Story: 3.1-speaker-matching-interface
│   File: story-3.1-speaker-matching-interface.md
├── → Speaker Profile Detail View
├── → Invitation Management Screen ✅
├── → Company Management Screen
├── Speaker Search & Filters
├── Send Invitations
└── ⤴ Event Management Dashboard

🎯 Speaker Profile Detail View ✅ [EXISTS]
│   Story: 7.1-speaker-profile-detail-view
│   File: story-7.1-speaker-profile-detail-view.md
│   Referenced From: story-3.1, story-7.1, story-1.16
│   Note: Multi-role profile view (Organizer, Speaker self-view, Attendee, Public)
├── Full Speaker Profile (read-only for organizer)
├── Speaking History
├── Expertise Areas
├── Company Affiliation
├── Contact Information
├── Ratings & Reviews
├── Availability & Preferences (organizer-only)
├── Past Presentations
├── → Send Invitation (organizer-only)
└── ⤴ Speaker Matching Interface

🎯 Invitation Management Screen ✅ [EXISTS]
│   Story: 3.1 - Speaker Invitation System / 3.2 - Invitation Response Workflow
│   File: story-3.1-invitation-management.md
│   Referenced From: story-3.1-speaker-matching-interface, story-3.2
│   Priority: HIGH - Core speaker coordination workflow
│   Note: Invitation tracking, email analytics, bulk operations
├── All Invitations (by event with status filtering)
├── Status Tracking (sent, opened, accepted, declined, pending, need info)
├── → Invitation Response (view speaker responses)
├── Email Tracking (AWS SES delivery, opens, clicks)
├── Resend Invitation (with reminder functionality)
├── Bulk Actions (bulk resend, bulk cancel, export)
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

🎯 Company Management Screen ✅ [EXISTS]
│   Story: 1.14-company-management-screen
│   File: story-1.14-company-management-screen.md
│   Referenced From: story-3.1, story-6.1
│   Note: Core company CRUD + logo management + Swiss UID verification
├── Company Information Form
├── Logo Upload (S3 presigned URL + CDN)
├── Industry/Sector Selection
├── Partner Status Toggle (with partnership levels)
├── Swiss UID Verification
├── → Associated Speakers/Employees
├── Company Statistics & Analytics
└── Activity History Timeline
```

### 4.5 Logistics & Venue Management
```
🎯 Logistics Coordination ✅ [EXISTS]
│   Story: 4.4-logistics-coordination
│   File: story-4.4-logistics-coordination.md
└── ⤴ Event Management Dashboard
```

### 4.6 Administrative Screens
```
🎯 System Settings/Configuration Screen ✅ [EXISTS]
│   Story: 1.0 - System Administration
│   File: story-1.0-system-settings.md
│   Referenced From: story-1.16, story-2.3
│   Note: Admin-level system configuration done by organizers (not in MVP scope)
├── Platform Configuration
├── Workflow Configuration
├── Integration Settings
├── Feature Flags
├── System Status
└── Security & Access
```

---

## 5. Partner Portal (💼 Role)

**⚠️ MAJOR SCOPE CHANGE:** FR4 (Partner Analytics) and FR9 (Automated Reports) removed from MVP.

**MVP Scope (Epic 6):**
- ✅ Topic Voting (6.1) - Partners vote on topics
- ✅ Meeting Coordination (6.2) - Seasonal partner meetings
- 📦 Partner Analytics Dashboard (FR4)

### 5.1 Main Dashboard & Analytics
```
📦 Partner Analytics Dashboard
│   Story: 6.1-partner-analytics-dashboard (wireframe exists)
│   File: story-6.1-partner-analytics-dashboard.md
│   Status: FR4 (Partner Analytics)
├── → Employee Analytics
├── → Partner Settings Screen ✅
├── → Strategic Planning
├── → Partner Meetings
└── Notifications

📦 Employee Analytics
│   Story: 6.1-employee-analytics (wireframe exists)
│   File: story-6.1-employee-analytics.md
└── ⤴ Partner Analytics Dashboard
```

### 5.4 Strategic Planning & Goals (✅ MVP: Topic Voting Only)
```
💼 Topic Voting Screen ✅ [EXISTS] ✨ MVP INCLUDED
│   Story: 6.4-topic-voting (Actually Story 6.1 in Epic 6)
│   File: story-6.4-topic-voting.md
│   Note: Core MVP feature - partner topic voting
├── → All Topics Browser Screen ✅ [EXISTS]
├── → Topic Details Screen ✅ [EXISTS]
├── Vote Submission
├── Voting History
└── ⤴ Strategic Planning

💼 All Topics Browser Screen ✅ [EXISTS] ✨ MVP INCLUDED
│   Story: 6.1-all-topics-browser
│   File: story-6.1-all-topics-browser.md
│   Referenced From: story-6.4-topic-voting
│   Note: Comprehensive topic browsing for partner voting
├── Search & Filter Topics
├── View Modes (Grid/List)
├── Vote Distribution Visualization
├── Topic Comparison (up to 3 topics)
├── Add/Remove from Voting List
├── → Topic Details Screen ✅ [EXISTS]
└── ⤴ Topic Voting Screen
```

### 5.5 Partner Meetings & Collaboration ✨ MVP INCLUDED
```
💼 Partner Meetings ✅ [EXISTS] ✨ MVP INCLUDED
│   Story: 6.2-partner-meetings
│   File: story-6.2-partner-meetings.md
│   Note: Core MVP feature - meeting coordination
├── → Agenda Proposal Screen ✅ [EXISTS]
│   Story: 6.2-agenda-proposal
│   File: story-6.2-agenda-proposal.md
│   Note: Partners can propose agenda items for meetings
└── ⤴ Partner Analytics Dashboard
```

### 5.6 Partner Settings
```
💼 Partner Settings Screen ✅ [EXISTS]
│   Story: 6.3 - Partner Coordination
│   File: story-6.3-partner-settings.md
│   Referenced From: story-6.1, story-6.3
│   Priority: LOW - Partner self-service configuration
├── Company Settings (logo, industry, primary contact, tier info)
├── Notification Preferences (voting, meetings, events, reports, quiet hours)
├── Integration Settings (calendar sync, SSO, webhooks, API access)
├── Billing & Subscription (tier management, invoices, payment info)
├── Team & Access (member management, roles, permissions)
└── Privacy & Data (data sharing, GDPR export, account management)
```

### 5.7 Partner Directory
```
💼 Partner Directory/List Screen ✅ [EXISTS]
│   Story: 6.3 - Partner Coordination
│   File: story-6.3-partner-directory.md
│   Referenced From: story-1.16, story-6.4
│   Priority: MEDIUM - Partner relationship management
├── Partner List (all partners with tier badges)
├── → Partner Detail Screen
├── Partner Tier Badges (⭐ Premium, 🥇 Gold, 🥈 Silver, 🥉 Bronze)
├── Engagement Score (visual progress bars)
├── Contact Information (name, email, phone)
├── Quick Actions (View Details, Send Email, Schedule Meeting, Analytics)
└── Filter by Tier/Status (with search and sort capabilities)

💼 Partner Detail Screen ✅ [EXISTS]
│   Story: 6.3 - Partner Coordination
│   File: story-6.3-partner-detail.md
│   Referenced From: story-6.1, story-1.16
│   Priority: MEDIUM - Partner relationship management
├── Partner Information (header with logo, tier, industry)
├── Quick Stats (partnership duration, events, votes, meetings)
├── Tabs: Overview, Contacts, Meetings, Activity, Notes, Settings
│   ├── Overview: Partnership details, engagement metrics, recent activity, employees
│   ├── Contacts: Primary & secondary contacts, communication preferences
│   ├── Meetings: Upcoming meetings, meeting history, scheduling
│   ├── Activity: Activity timeline with filters (events, votes, emails, notes, tier changes)
│   ├── Notes: Organizer notes (private/shared)
│   └── Settings: Partnership management, access permissions, data compliance (organizer only)
├── → Analytics Summary (if FR4 restored from backlog)
├── Meeting History & Scheduling
└── Notes & Interactions Log
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
├── → Submit for Review (Moderator Queue) ✅
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
├── → Speaker Profile
├── → Public Profile
├── Profile Information
├── Expertise Tags
├── Bio & Background
├── Social Media Links
└── ⤴ Speaker Dashboard
```

---

## 7. Attendee Portal (👤 Role)

### 7.1 Main Dashboard & Discovery
```
👤 Personal Attendee Dashboard ✅ [EXISTS]
│   Story: 5.2-personal-dashboard
│   File: story-5.2-personal-dashboard.md
│   Also: wireframes-attendee.md
├── → Event Details Page (Attendee View)
├── → Content Viewer Page ✅ [EXISTS]
├── → Full Library Management Page
├── → User Settings Screen
├── Upcoming Events
├── My Registrations
├── Saved Content
├── Learning Progress
└── Notifications

👤 Event Details Page (Attendee View) ✅ [EXISTS]
│   Story: 5.2 - Personal Engagement Management
│   File: story-5.2-event-details-attendee-view.md
│   Referenced From: story-5.2, story-2.4-current-event-landing
│   Priority: HIGH - Core attendee functionality
│   Note: Attendee-specific event detail view with personal schedule management
├── Event Information (read-only)
├── → Session Schedule (with personal schedule toggle)
├── → Speaker Lineup
├── Registration Status (My Participation section)
├── Add to Calendar (iCal, Google, Outlook)
├── Share Event (Email, Social, Link)
├── Related Content (past events)
└── ⤴ Personal Dashboard

👤 Content Discovery ✅ [EXISTS] (AI features removed per FR13)
│   Story: 5.1-content-discovery
│   File: story-5.1-content-discovery.md
│   Note: AI-powered recommendations moved to backlog
├── → Content Viewer Page ✅ [EXISTS]
├── → Filter Modal ✅ [EXISTS]
├── → All Reviews Screen [MISSING] 🟢
├── → Top Rated Content Screen [MISSING] 🟢
├── Search & Browse
├── Basic Recommendations (non-AI)
├── Save Content
├── Rate & Review
└── ↔ Personal Dashboard

👤 Content Viewer Page ✅ [EXISTS]
│   Story: 5.1-content-viewer
│   File: story-5.1-content-viewer.md
│   Referenced From: story-5.1, story-5.2, story-5.3
│   Priority: HIGH - Core content consumption
│   Note: Universal content viewer for presentations/documents
├── Content Display (PDF/Video/Slides viewer with PDF.js, HTML5 video, slide viewer)
├── Navigation Controls (prev/next, zoom, fullscreen, rotate, print, comments)
├── Download Option (single file or ZIP bundle of all materials)
├── Bookmarking (save to library with collections, position tracking)
├── Notes/Annotations (future enhancement - private notes on slides)
├── Related Content Suggestions (tag-based, speaker-based, non-AI)
├── Rating & Reviews (5-star ratings, comments with replies, helpful votes)
├── Engagement Tracking (views, downloads, completion rate, slide heatmap)
└── ⤴ Content Discovery / Personal Dashboard / Event Details

👤 Full Library Management Page
│   Referenced From: story-5.2
├── All Saved Content
├── Collections/Folders
├── Sort & Filter Options
├── → Content Viewer
├── Bulk Actions
├── Storage Usage
└── Sharing Capabilities
```

### 7.4 Event Registration & Attendance
```
👤 Event Listing Page ✅ [EXISTS]
│   Story: 5.3-event-listing-page
│   File: story-5.3-event-listing-page.md
│   Referenced From: wireframes-attendee.md, main navigation
│   Priority: MEDIUM - Event discovery for attendees
├── → Event Details Page (story-5.2-event-details-attendee-view.md)
├── → Filter Modal ✅ [EXISTS] (story-5.1-filter-modal.md)
├── → Event Registration Flow (story-2.4-event-registration.md)
├── Browse Upcoming Events (featured + card grid)
├── Search Events (real-time search with debounce)
├── Registration Status Indicators (open/full/waitlist)
├── Sort & Filter Controls
└── Infinite Scroll/Pagination

👤 Session Details Modal
│   Referenced From: story-2.4
│   Priority: HIGH - Core event feature
├── Session Title & Description
├── → Speaker Information
├── Time & Location
├── Capacity/Availability
├── Add to Schedule
├── Share Session
└── Related Sessions

👤 Filter Modal (Multiple Contexts) ✅ [EXISTS]
│   Story: 5.1-filter-modal
│   File: story-5.1-filter-modal.md
│   Referenced From: story-5.1, story-2.4, event-listing, speaker-directory
│   Priority: MEDIUM - Mobile-responsive filtering across multiple contexts
│   Note: Multi-context reusable filter modal (content, events, speakers)
├── Filter Criteria Options (topics, time, content type, speaker, company, rating)
├── Multi-select Capabilities (checkboxes, radio buttons, date range)
├── Clear Filters (individual pill removal, clear all)
├── Apply Button (sticky footer with result count)
├── Save Filter Preset (user-saved presets with load/delete)
├── Active Filters Indicator (dismissible pills at top)
├── Search Within Filters (autocomplete for topics, speakers, companies)
├── Collapsible Sections (accordion with expand/collapse)
├── Real-Time Result Count (debounced API, facet counts update)
└── Responsive Design (full-screen mobile modal, drawer tablet, sidebar desktop)

👤 Registration Confirmation Page
│   Referenced From: story-2.4
│   Priority: HIGH - Registration flow completion
├── Confirmation Message
├── Registration Summary
├── → Ticket/QR Code Page
├── Calendar Download
├── Email Confirmation Indicator
├── Next Steps
└── Edit Registration Option

👤 Ticket/QR Code Page ✅ [EXISTS]
│   Story: 2.4-ticket-qr-code-page
│   File: story-2.4-ticket-qr-code-page.md
│   Referenced From: story-2.4-registration-confirmation
│   Priority: HIGH - Event access requirement (Mobile-first)
├── Event QR Code (secure, auto-refreshing, full-screen tap)
├── Ticket Details (attendee info, ticket ID, event info)
├── Check-in Instructions (arrival time, location, procedures)
├── Add to Wallet (Apple Wallet, Google Wallet integration)
├── Print Option (browser print, PDF download)
├── Transfer Ticket (email transfer, ownership change)
├── Share Ticket (email, copy link, security warnings)
├── Offline Access (PWA cached, service worker)
└── Ticket Management (update, cancel registration)

👤 Attendee List Modal ✅ [EXISTS]
│   Story: 2.4-attendee-list-modal
│   File: story-2.4-attendee-list-modal.md
│   Referenced From: story-2.4-current-event-landing
│   Priority: LOW - Networking feature for enhanced engagement
├── Registered Attendees (filtered by networking opt-in)
├── → User Profile Preview
├── Networking Options (Connect, View Profile)
├── Filter by Company/Role
├── Privacy Controls (opt-in visibility)
└── Connection Requests (send/cancel)
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
      → Send Invitations (Speaker Matching Interface)
        → Track Invitations (Invitation Management) ✅
          → View Responses (Invitation Response) ✅
    → Publish Event (Progressive Publishing)
      → Monitor Registration (Event Detail/Edit)
```

#### Partner Journey: Analytics & Strategic Planning
```
Partner Analytics Dashboard
  → Review Employee Analytics
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
    → Confirm Registration (Step 2/2)
      → Get Ticket (Ticket/QR Code Page)
  → Track Progress (Personal Dashboard)
```

### 9.2 Cross-Role Navigation Points

#### Shared Navigation Targets
- User Profile Screen (from any role)
- Notification Center (from any role)
- Content Viewer (multi-role access)
- Event Details (role-specific views)
- Speaker Profiles (multi-role access)

---
