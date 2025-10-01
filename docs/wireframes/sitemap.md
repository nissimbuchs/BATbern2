# BATbern Event Management Platform - Comprehensive Site Map

**Generated:** 2025-10-01
**Version:** 1.0
**Purpose:** Complete hierarchical overview of all platform screens and navigation relationships

---

## Legend

### Status Indicators
- ✅ **[EXISTS]** - Wireframe documented
- ❌ **[MISSING]** - Referenced but not documented
- 🔄 **[PARTIAL]** - Mentioned in role wireframes but needs dedicated file

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
├── → Authentication/Login
├── → Event Catalog (Public Events)
└── → About/Information Pages

🌐 Current Event Landing Page ✅ [EXISTS]
│   Story: 2.4-current-event-landing
│   File: story-2.4-current-event-landing.md
├── → Event Registration Flow
├── → Session Details Modal [MISSING] 🔴
├── → Speaker Profile Detail View [MISSING] 🔴
├── → Venue Details Screen [MISSING] 🔴
└── → Filter Modal [MISSING] 🟡

🌐 Event Registration Flow (Multi-Step) [PARTIAL]
│   Story: 2.4-event-registration
│   File: story-2.4-event-registration.md
├── Step 1/3: Personal Information ✅
├── Step 2/3: Session Selection [MISSING] 🔴
├── Step 3/3: Review & Confirm [MISSING] 🔴
├── → Registration Confirmation Page [MISSING] 🔴
└── → Ticket/QR Code Page [MISSING] 🔴
```

---

## 2. Authentication Layer

### 2.1 Authentication Screens
```
Login Screen [MISSING] 🔴
├── → Forgot Password
├── → Create Account
└── → Role-Based Dashboard (post-login)

Forgot Password Flow [MISSING] 🟡
└── ⤴ Login Screen

Account Creation [MISSING] 🟡
├── Role Selection
└── → Email Verification

Email Verification [MISSING] 🟡
└── → Role-Based Dashboard
```

---

## 3. Global/Shared Screens (All Authenticated Users)

### 3.1 Core Navigation
```
Main Navigation Bar/Menu [MISSING] 🔴
├── → Role-Specific Dashboard
├── → User Profile Screen
├── → Notification Center
├── → Help Center
└── → Logout

🌐 User Profile Screen [MISSING] 🟡
│   Roles: All
│   Referenced From: story-5.2, story-7.3, story-7.4
├── Personal Information (view/edit)
├── Contact Details
├── Role-Specific Information
├── Activity History
├── → User Settings Screen
└── ⤴ Previous Screen

🌐 User Settings Screen [MISSING] 🟡
│   Roles: All
│   Referenced From: story-5.2, story-1.20
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
│   Roles: All
│   Referenced From: story-5.2, multiple screens
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
🎯 Event Detail/Edit Screen [MISSING] 🔴
│   Referenced From: story-1.16, story-2.2
│   Priority: HIGH - Core functionality
├── Event Information (editable)
├── Status & Workflow Position
├── → Assigned Topics (link to Topic Backlog)
├── → Confirmed Speakers (link to Speaker Matching)
├── → Registration Statistics
├── → Event Settings Screen
├── → Publishing Controls (link to Progressive Publishing)
└── ⤴ Event Management Dashboard

🎯 Event Settings Screen [MISSING] 🟡
│   Referenced From: story-1.16, story-4.3
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

🎯 Progressive Publishing ✅ [EXISTS]
│   Story: 4.3-progressive-publishing
│   File: story-4.3-progressive-publishing.md
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
│   Referenced From: story-1.18, story-5.1, story-3.3
├── Content Metadata (editable)
├── File Information
├── → Associated Event/Session
├── Download Options
├── Related Content
└── ⤴ Parent Screen

🎯 Moderator Review Queue [MISSING] 🟡
│   Referenced From: story-1.16, story-3.3
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
│   Referenced From: story-3.1, story-3.2
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
│   Referenced From: story-3.1, story-6.1
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
│   Referenced From: story-4.4, story-2.4
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
│   Referenced From: story-4.4
│   Priority: HIGH - Required for multi-year planning
├── Booking Form
├── Availability Calendar
├── Pricing Information
├── Special Requirements
├── Confirmation Workflow
├── Contract Upload
└── ⤴ Venue Details Screen

🎯 Catering Management Screen [MISSING] 🟡
│   Referenced From: story-4.4
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
│   Referenced From: story-1.16, story-2.3
├── Platform Configuration
├── Email Template Management
├── Workflow Configuration
├── Integration Settings
├── Feature Flags
└── System Status
```

---

## 5. Partner Portal (💼 Role)

### 5.1 Main Dashboard & Analytics
```
💼 Partner Analytics Dashboard ✅ [EXISTS]
│   Story: 6.1-partner-analytics-dashboard
│   File: story-6.1-partner-analytics-dashboard.md
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

💼 Employee Analytics ✅ [EXISTS]
│   Story: 6.1-employee-analytics
│   File: story-6.1-employee-analytics.md
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

### 5.2 Brand & Exposure Management
```
💼 Brand Exposure ✅ [EXISTS]
│   Story: 6.2-brand-exposure
│   File: story-6.2-brand-exposure.md
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

### 5.3 Budget & Financial Management
```
💼 Budget Management ✅ [EXISTS]
│   Story: 6.3-budget-management
│   File: story-6.3-budget-management.md
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

💼 Custom Report Builder ✅ [EXISTS]
│   Story: 6.3-custom-report-builder
│   File: story-6.3-custom-report-builder.md
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

### 5.4 Strategic Planning & Goals
```
💼 Strategic Planning ✅ [EXISTS]
│   Story: 6.4-strategic-planning
│   File: story-6.4-strategic-planning.md
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

💼 Goals Management Screen [MISSING] 🔴
│   Referenced From: story-6.4
│   Priority: HIGH - Core partner functionality
├── Goals List (active/completed)
├── Progress Tracking
├── → Goal Details Screen
├── → New Goal Creation Screen
├── Goal Metrics Assignment
├── Timeline Visualization
└── ⤴ Strategic Planning

💼 Topic Voting Screen ✅ [EXISTS]
│   Story: 6.4-topic-voting
│   File: story-6.4-topic-voting.md
├── → All Topics Browser Screen [MISSING] 🟡
├── → Topic Details Screen [MISSING] 🟡
├── Vote Submission
├── Voting History
└── ⤴ Strategic Planning

💼 Certification Paths Browser Screen [MISSING] 🟡
│   Referenced From: story-6.4
├── Available Certification Programs
├── Prerequisites & Requirements
├── Completion Rates
├── → Employee Certification List [MISSING] 🟡
├── Recommendation Engine
└── ⤴ Strategic Planning
```

### 5.5 Partner Meetings & Collaboration
```
💼 Partner Meetings ✅ [EXISTS]
│   Story: 6.5-partner-meetings
│   File: story-6.5-partner-meetings.md
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
🎤 Speaker Dashboard ✅ [EXISTS]
│   Story: 3.3-speaker-dashboard
│   File: story-3.3-speaker-dashboard.md
│   Also: wireframes-speaker.md
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

🎤 Speaker Community ✅ [EXISTS]
│   Story: 7.1-speaker-community
│   File: story-7.1-speaker-community.md
├── → Full Speaker Network Screen [MISSING] 🔴
├── → Discussion Thread Screen [MISSING] 🔴
├── → New Discussion Screen [MISSING] 🟡
├── → Mentor Profile Screen [MISSING] 🟡
├── → Mentor Application Screen [MISSING] 🟡
├── → Resource Viewer Screen [MISSING] 🟡
├── → Course Enrollment Screen [MISSING] 🟡
├── → Resource Details Screen [MISSING] 🟡
├── Featured Speakers
├── Connection Requests
└── ⤴ Speaker Dashboard

🎤 Full Speaker Network Screen [MISSING] 🔴
│   Referenced From: story-7.1
│   Priority: HIGH - Core community feature
├── Searchable Speaker Directory
├── Filter by Expertise/Location/Availability
├── Speaker Cards
├── → Speaker Profile Detail View
├── Connection Request
└── Network Visualization

🎤 Discussion Thread Screen [MISSING] 🔴
│   Referenced From: story-7.1, story-7.4
│   Priority: HIGH - Core community feature
├── Thread Title & Metadata
├── Chronological Posts
├── Reply Functionality
├── Reactions & Voting
├── Moderation Controls
├── Subscription Options
└── ⤴ Parent Screen

🎤 Mentor Profile Screen [MISSING] 🟡
│   Referenced From: story-7.1
├── Mentor Bio & Expertise
├── Mentoring Areas
├── Availability
├── Past Mentees (if shareable)
├── Request Mentorship Button
└── Reviews/Ratings

🎤 Resource Viewer Screen [MISSING] 🟡
│   Referenced From: story-7.1
├── Resource Content Display
├── Metadata
├── Download Options
├── Related Resources
├── Comments/Ratings
└── Share Functionality
```

### 6.4 Communication
```
🎤 Communication Hub ✅ [EXISTS]
│   Story: 7.3-communication-hub
│   File: story-7.3-communication-hub.md
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

### 6.5 Community Features
```
🎤 Community Features ✅ [EXISTS]
│   Story: 7.4-community-features
│   File: story-7.4-community-features.md
├── → Discussion Topics Browser [MISSING] 🟡
├── → My Discussions Screen [MISSING] 🟡
├── → Trending Discussions [MISSING] 🟡
├── → Study Group Details Screen [MISSING] 🟢
├── → User Profile Screen
├── Forums & Discussions
├── Study Groups
└── ⤴ Speaker Dashboard

🎤 Discussion Topics Browser [MISSING] 🟡
│   Referenced From: story-7.4
├── Topic Categories
├── Active Discussions Count
├── Popular Topics
├── Search Functionality
├── → Create New Topic
└── Subscribe to Topics

🎤 My Discussions Screen [MISSING] 🟡
│   Referenced From: story-7.4
├── Discussions Started
├── Discussions Participated In
├── Subscribed Discussions
├── Saved Discussions
├── Filter/Sort Options
└── Activity Notifications
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
│   Referenced From: story-5.2, story-2.4
│   Priority: HIGH - Core attendee functionality
├── Event Information (read-only)
├── → Session Schedule
├── → Speaker Lineup
├── Registration Status
├── Add to Calendar
├── Share Event
├── Related Content
└── ⤴ Personal Dashboard

👤 Content Discovery ✅ [EXISTS]
│   Story: 5.1-content-discovery
│   File: story-5.1-content-discovery.md
├── → Content Viewer Page [MISSING] 🔴
├── → Filter Modal [MISSING] 🟡
├── → All Reviews Screen [MISSING] 🟢
├── → Top Rated Content Screen [MISSING] 🟢
├── Search & Browse
├── Recommendations
├── Save Content
├── Rate & Review
└── ↔ Personal Dashboard

👤 Content Viewer Page [MISSING] 🔴
│   Referenced From: story-5.1, story-5.2, story-5.3
│   Priority: HIGH - Core content consumption
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
👤 Mobile PWA Experience ✅ [EXISTS]
│   Story: 5.3-mobile-pwa
│   File: story-5.3-mobile-pwa.md
├── → Offline Content & Download Manager
├── → Storage Management Screen [MISSING] 🟡
├── → User Settings (PWA-specific) [MISSING] 🔴
├── Mobile-Optimized Navigation
├── Push Notifications
├── Offline Capabilities
└── Install Prompt

👤 Offline Content & Download Manager ✅ [EXISTS]
│   Story: 5.3-offline-content
│   File: story-5.3-offline-content.md
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

### 7.5 Community & Social
```
👤 Community Forum/Discussion Page [MISSING] 🟡
│   Referenced From: wireframes-attendee.md
├── → Discussion Thread Screen
├── → Discussion Topics Browser
├── Browse Discussions
├── Create New Discussion
└── My Discussions

👤 All Reviews Screen [MISSING] 🟢
│   Referenced From: story-5.1
├── Review List
├── Filter by Rating/Date
├── Sort Options
├── Review Details
├── Helpful Voting
└── Report Inappropriate

👤 Study Group Details Screen [MISSING] 🟢
│   Referenced From: story-7.4
├── Group Information
├── Member List
├── Shared Resources
├── → Discussion Board
├── Events/Meetings
└── Join/Leave Group
```

### 7.6 Attendee Settings & Help
```
👤 User Settings Screen (Attendee-specific) [MISSING] 🔴
│   Referenced From: story-5.2, story-5.3
│   Priority: HIGH - User control
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

**Total Screens Identified:** 157+

#### By Status
- ✅ **Documented (EXISTS):** 32 story wireframes + role wireframes = ~35-40 screens
- ❌ **Missing:** 96 screens
- 🔄 **Partial:** ~20 screens (mentioned in role docs but need dedicated files)

#### By Priority (Missing Screens Only)
- 🔴 **HIGH Priority:** 28 screens
- 🟡 **MEDIUM Priority:** 52 screens
- 🟢 **LOW Priority:** 16 screens

### 10.2 Coverage by Role

#### Organizer Portal
- **Existing:** 10 story wireframes
- **Missing:** 18 screens
- **Coverage:** ~35% complete

#### Partner Portal
- **Existing:** 8 story wireframes
- **Missing:** 29 screens
- **Coverage:** ~22% complete

#### Speaker Portal
- **Existing:** 7 story wireframes
- **Missing:** 17 screens
- **Coverage:** ~29% complete

#### Attendee Portal
- **Existing:** 5 story wireframes
- **Missing:** 27 screens
- **Coverage:** ~16% complete

#### Global/Shared
- **Existing:** 1 wireframe (Notification Center)
- **Missing:** 5 screens
- **Coverage:** ~17% complete

### 10.3 Feature Area Coverage

#### Event Management
- **Coverage:** 60% (core workflows documented)
- **Gaps:** Detail/edit screens, settings

#### Analytics & Reporting
- **Coverage:** 40% (dashboards exist, drill-downs missing)
- **Gaps:** Detail screens, metric viewers

#### Content Management
- **Coverage:** 45% (upload/submission exists)
- **Gaps:** Viewers, library management, moderation

#### Community & Social
- **Coverage:** 30% (basic features documented)
- **Gaps:** Discussion threads, networking, profiles

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

## Notes

1. **Modal vs. Page Decisions Needed:** Many detail/edit screens could be modals instead of full pages. Recommend UX review for each.

2. **URL Routing Strategy:** Site map provides foundation for URL structure planning. Consider role-based prefixes (e.g., /organizer/*, /partner/*, /speaker/*, /attendee/*).

3. **Responsive Design:** Many screens will need mobile variants, especially for Attendee role. Mobile PWA (story-5.3) provides patterns.

4. **Accessibility:** All screens should follow WCAG 2.1 AA standards. Document accessibility requirements per screen type.

5. **State Management:** Complex navigation flows suggest need for robust state management (Redux/Zustand). Track user context across role switches.

6. **Performance:** Consider lazy loading for role-specific portals. Pre-load critical paths only.

7. **Security:** Role-based access control (RBAC) must be enforced at navigation level. Document permissions per screen.

8. **Deep Linking:** All screens should support deep linking for bookmarking and sharing (where privacy allows).

---

**End of Site Map**

*This document represents the complete navigational architecture of the BATbern Event Management Platform as of 2025-10-01. It should be used in conjunction with individual wireframe files and the navigation-gaps-analysis.md document for comprehensive understanding of the platform structure.*
