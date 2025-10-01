# Navigation Gaps Analysis - BATbern Event Management Platform

**Generated:** 2025-09-30
**Updated:** 2025-10-01
**Source:** Comprehensive wireframe cross-reference analysis

## Executive Summary

This document identifies missing wireframe screens that are referenced in navigation flows but don't have corresponding wireframe files.

**Statistics:**
- **Total Navigation References:** 157+
- **Missing Wireframes:** 96 screens (18 original + 78 newly identified)
- **Severity:** HIGH - Significant gaps across all role-specific interfaces
- **Analysis Coverage:** 32 story wireframe files comprehensively analyzed

---

## Update Summary (2025-10-01)

The comprehensive analysis of all 32 story wireframe files revealed **78 additional missing screens** beyond the original 18 identified on 2025-09-30. New gaps span:
- **Partner Analytics & Planning:** 29 screens
- **Speaker Community & Networking:** 10 screens
- **Attendee Experience & Engagement:** 27 screens
- **Content & Community Features:** 8 screens
- **Navigation Modals & Supporting Screens:** 4 screens

---

## Missing Wireframes by Category

### 1. Event Management Screens

#### Event Detail/Edit Screen
**Status:** MISSING
**Referenced From:**
- story-1.16-event-management-dashboard.md (Click on event card)
- story-1.16-workflow-visualization.md (Edit event button)
- story-2.2-topic-backlog-management.md (Assign topic to event)

**Required Content:**
- Event basic information (title, date, venue)
- Event description and theme
- Status and workflow position
- Assigned topics
- Confirmed speakers
- Registration statistics
- Edit capabilities
- Publish controls

**Priority:** HIGH

---

#### Event Settings Screen
**Status:** MISSING
**Referenced From:**
- story-1.16-event-management-dashboard.md (Settings icon on event card)
- story-4.3-progressive-publishing.md (Configure publishing phases)

**Required Content:**
- Registration settings (capacity, cutoff date)
- Publishing configuration
- Email templates
- Notification rules
- Access permissions
- Integration settings

**Priority:** MEDIUM

---

### 2. Speaker Management Screens

#### Speaker Profile Detail View
**Status:** MISSING
**Referenced From:**
- story-3.1-speaker-matching-interface.md (Click on speaker card)
- story-7.1-speaker-community.md (View speaker in directory)
- story-1.16-event-management-dashboard.md (View speaker details)

**Required Content:**
- Full speaker profile
- Speaking history
- Expertise areas
- Company affiliation
- Contact information
- Ratings and reviews
- Availability calendar
- Past presentations

**Priority:** HIGH

---

#### Speaker Profile Edit Screen
**Status:** MISSING (but referenced in wireframes-speaker.md)
**Referenced From:**
- story-3.3-speaker-dashboard.md (Edit profile link)
- story-7.1-speaker-profile-management.md (Listed but detailed wireframe missing)

**Required Content:**
- Personal information form
- Photo upload
- Expertise tags
- Bio editor
- Social media links
- Presentation preferences
- Privacy settings

**Priority:** MEDIUM

---

#### Invitation Management Screen
**Status:** MISSING
**Referenced From:**
- story-3.1-speaker-matching-interface.md (View all invitations)
- story-3.2-invitation-response.md (Organizer view of invitation status)

**Required Content:**
- List of all invitations (by event)
- Invitation status (pending, accepted, declined)
- Response timestamps
- Filter by status/date
- Bulk actions
- Resend invitation

**Priority:** MEDIUM

---

### 3. Content Management Screens

#### Content Library/Repository Screen
**Status:** MISSING
**Referenced From:**
- story-3.3-speaker-dashboard.md (View my presentations)
- story-5.1-content-discovery.md (Admin content management)
- story-5.2-personal-dashboard.md (My saved content)

**Required Content:**
- List of all content items
- Filters (type, date, status)
- Preview capabilities
- Bulk operations
- Version history
- Usage statistics

**Priority:** MEDIUM

---

#### Content Detail/Edit Screen
**Status:** MISSING
**Referenced From:**
- story-1.18-historical-archive.md (Click on content item)
- story-5.1-content-discovery.md (View content details)
- story-3.3-presentation-upload.md (Edit metadata)

**Required Content:**
- Content metadata (title, description, tags)
- File information (size, format, version)
- Associated event/session
- Download options
- Edit capabilities
- Related content

**Priority:** MEDIUM

---

### 4. Venue & Logistics Screens

#### Venue Details Screen
**Status:** MISSING
**Referenced From:**
- story-4.4-logistics-coordination.md (View venue details)
- story-2.4-current-event-landing.md (Venue information link)

**Required Content:**
- Venue name and address
- Capacity information
- Amenities and facilities
- Map/directions
- Contact information
- Booking history
- Availability calendar
- Photos

**Priority:** HIGH

---

#### Venue Booking Screen
**Status:** MISSING
**Referenced From:**
- story-4.4-logistics-coordination.md (Book venue button)

**Required Content:**
- Booking form (date, time, setup)
- Availability calendar
- Pricing information
- Special requirements
- Confirmation workflow
- Contract upload

**Priority:** HIGH

---

#### Catering Management Screen
**Status:** MISSING
**Referenced From:**
- story-4.4-logistics-coordination.md (Catering tab)

**Required Content:**
- Catering options
- Menu selection
- Attendee count
- Dietary restrictions
- Pricing
- Order confirmation
- Vendor contact

**Priority:** MEDIUM

---

### 5. Partner Portal Screens

#### Partner Directory/List Screen
**Status:** MISSING
**Referenced From:**
- story-1.16-event-management-dashboard.md (View all partners)
- story-6.4-strategic-planning.md (Partner comparison)

**Required Content:**
- List of all partners
- Partner tier badges
- Engagement score
- Contact information
- Quick actions
- Filter by tier/status

**Priority:** MEDIUM

---

#### Partner Detail Screen
**Status:** MISSING
**Referenced From:**
- story-6.1-partner-analytics-dashboard.md (Organizer view of partner)
- story-1.16-event-management-dashboard.md (Click on partner name)

**Required Content:**
- Partner information
- Sponsorship details
- Key contacts
- Analytics summary
- Meeting history
- Notes and interactions

**Priority:** MEDIUM

---

#### Partner Settings Screen
**Status:** MISSING
**Referenced From:**
- story-6.1-partner-analytics-dashboard.md (Settings icon)
- story-6.3-budget-management.md (Configure budget)

**Required Content:**
- Company settings
- User management
- Notification preferences
- Integration settings
- Billing information
- Access permissions

**Priority:** LOW

---

### 6. User Management Screens

#### User Profile Screen
**Status:** MISSING
**Referenced From:**
- story-5.2-personal-dashboard.md (Profile link)
- story-7.3-communication-hub.md (View user profile)

**Required Content:**
- Personal information
- Contact details
- Role(s)
- Preferences
- Activity history
- Avatar/photo

**Priority:** MEDIUM

---

#### User Settings Screen
**Status:** MISSING
**Referenced From:**
- story-5.2-personal-dashboard.md (Settings icon)
- story-1.20-notification-center.md (Notification preferences link)

**Required Content:**
- Account settings
- Password change
- Email preferences
- Notification settings
- Privacy controls
- Data export/delete

**Priority:** MEDIUM

---

### 7. Administrative Screens

#### Company Management Screen
**Status:** MISSING
**Referenced From:**
- story-3.1-speaker-matching-interface.md (Add new company)
- story-6.1-partner-analytics-dashboard.md (Manage company)

**Required Content:**
- Company information form
- Logo upload
- Industry/sector
- Partner status toggle
- Associated speakers/employees
- Statistics

**Priority:** MEDIUM

---

#### Moderator Review Queue
**Status:** MISSING
**Referenced From:**
- story-1.16-workflow-visualization.md (Content review step)
- story-3.3-material-submission-wizard.md (Submit for review)

**Required Content:**
- Queue of pending reviews
- Content preview
- Review form
- Approve/reject actions
- Feedback notes
- Assignment to moderators

**Priority:** MEDIUM

---

#### System Settings/Configuration Screen
**Status:** MISSING
**Referenced From:**
- story-1.16-event-management-dashboard.md (Admin menu)
- story-2.3-basic-publishing-engine.md (Configure publishing)

**Required Content:**
- Platform configuration
- Email template management
- Workflow configuration
- Integration settings
- Feature flags
- System status

**Priority:** LOW

---

### 8. Support & Documentation Screens

#### Help/Documentation Screen
**Status:** MISSING
**Referenced From:**
- story-1.16-event-management-dashboard.md (Help icon)
- story-3.3-speaker-dashboard.md (Help link)
- story-6.1-partner-analytics-dashboard.md (Help icon)

**Required Content:**
- Searchable documentation
- Role-based help topics
- Video tutorials
- FAQs
- Contact support
- System status

**Priority:** LOW

---

#### Support Ticket Screen
**Status:** MISSING
**Referenced From:**
- story-1.16-event-management-dashboard.md (Contact support)

**Required Content:**
- Support ticket form
- Issue categorization
- File attachments
- Ticket history
- Status tracking

**Priority:** LOW

---

## Navigation Flow Issues

### Circular References

#### Issue: Event Timeline ↔ Speaker Dashboard
**Problem:** Both screens reference each other but navigation context unclear

**story-3.5-event-timeline.md:**
- References speaker dashboard for speaker-specific timeline

**story-3.3-speaker-dashboard.md:**
- References event timeline for upcoming events

**Resolution Needed:** Clarify which view is primary and navigation parameters

---

#### Issue: Content Discovery ↔ Personal Dashboard
**Problem:** Both screens have "saved content" features with unclear relationship

**story-5.1-content-discovery.md:**
- Save content action

**story-5.2-personal-dashboard.md:**
- View saved content

**Resolution Needed:** Define whether they're the same list or different collections

---

### Ambiguous Navigation Targets

#### "View Event" Links
**Referenced in:** 15+ wireframes
**Target unclear:** Could be:
- Event detail (read-only)
- Event edit screen
- Event public page
- Event workflow view

**Resolution Needed:** Define different navigation targets for different contexts

---

#### "Manage Settings" Links
**Referenced in:** 10+ wireframes
**Target unclear:** Could be:
- User settings
- Event settings
- System settings
- Feature-specific settings

**Resolution Needed:** Use specific naming (e.g., "Event Settings" vs "My Settings")

---

## Recommendations

### Priority 1 (Critical for MVP)
1. **Create Event Detail/Edit Screen wireframe** - Core functionality
2. **Create Speaker Profile Detail View wireframe** - Core functionality
3. **Create Venue Details Screen wireframe** - Required for logistics
4. **Create Venue Booking Screen wireframe** - Required for multi-year planning

### Priority 2 (Important for Completeness)
5. **Create Content Detail/Edit Screen wireframe** - Content management
6. **Create Moderator Review Queue wireframe** - Quality control
7. **Create User Profile Screen wireframe** - User management
8. **Create Partner Directory Screen wireframe** - Partner management

### Priority 3 (Nice to Have)
9. **Create Settings screens** - Various configuration screens
10. **Create Help/Documentation screen** - User support
11. **Clarify navigation ambiguities** - Update existing wireframes with specific targets

### Process Improvements

1. **Establish naming conventions** for navigation targets
2. **Create wireframe dependency map** to visualize all navigation flows
3. **Add "Referenced By" sections** to each wireframe file
4. **Use consistent navigation patterns** across all wireframes
5. **Create modal/overlay specifications** for popup interactions

---

## Navigation Consistency Checklist

When creating new wireframes, ensure:
- [ ] All navigation targets are explicitly named
- [ ] Target wireframe files exist or are noted as "TODO"
- [ ] Navigation parameters are specified (IDs, filters, etc.)
- [ ] Back navigation is defined
- [ ] Breadcrumbs are shown where applicable
- [ ] Deep linking is considered for bookmarkable screens
- [ ] Mobile navigation differences are noted

---

## 9. Partner Analytics & Planning Screens (NEW - 2025-10-01)

#### Switch Partner Account Screen
**Status:** MISSING
**Referenced From:**
- story-6.1-partner-analytics-dashboard.md (Switch account dropdown)

**Required Content:**
- List of partner accounts user has access to
- Current active account indicator
- Quick switch functionality
- Account role/permissions display

**Priority:** MEDIUM

---

#### Metric Detail Screens (Multiple Types)
**Status:** MISSING
**Referenced From:**
- story-6.1-partner-analytics-dashboard.md (Click on any metric)
- story-6.1-employee-analytics.md (Department/Level/Team analytics)

**Required Content:**
- Detailed metric visualization
- Historical trend data
- Drill-down capabilities
- Export options
- Comparison tools

**Priority:** HIGH

---

#### Package Upgrade Request Screen
**Status:** MISSING
**Referenced From:**
- story-6.2-brand-exposure.md (Upgrade package button)

**Required Content:**
- Current package features
- Available upgrade tiers
- Feature comparison table
- Pricing information
- Request/purchase workflow

**Priority:** MEDIUM

---

#### Budget Forecasting Tool Screen
**Status:** MISSING
**Referenced From:**
- story-6.3-budget-management.md (Forecast button)

**Required Content:**
- Budget projection models
- Historical spending analysis
- ROI predictions
- Scenario planning tools
- Export capabilities

**Priority:** MEDIUM

---

#### Goals Management Screen
**Status:** MISSING
**Referenced From:**
- story-6.4-strategic-planning.md (Manage goals)

**Required Content:**
- Goals list (active/completed)
- Progress tracking
- Create/edit/delete goals
- Goal metrics assignment
- Timeline visualization

**Priority:** HIGH

---

#### Certification Paths Browser Screen
**Status:** MISSING
**Referenced From:**
- story-6.4-strategic-planning.md (View certification paths)

**Required Content:**
- Available certification programs
- Prerequisites and requirements
- Completion rates
- Employee enrollment status
- Recommendation engine

**Priority:** MEDIUM

---

#### Meeting Calendar View Screen
**Status:** MISSING
**Referenced From:**
- story-6.5-partner-meetings.md (Calendar tab)

**Required Content:**
- Calendar interface (month/week/day views)
- Meeting markers
- Quick meeting details
- Schedule new meeting
- Sync with external calendars

**Priority:** HIGH

---

#### Action Items Dashboard Screen
**Status:** MISSING
**Referenced From:**
- story-6.5-partner-meetings.md (Action items tab)

**Required Content:**
- Action items list
- Assignment and ownership
- Due dates and priorities
- Status tracking
- Completion workflow

**Priority:** MEDIUM

---

## 10. Speaker Community & Networking Screens (NEW - 2025-10-01)

#### Full Speaker Network Screen
**Status:** MISSING
**Referenced From:**
- story-7.1-speaker-community.md (View all speakers)

**Required Content:**
- Searchable speaker directory
- Filter by expertise/location/availability
- Speaker cards with key info
- Connection request functionality
- Network visualization

**Priority:** HIGH

---

#### Discussion Thread Screen
**Status:** MISSING
**Referenced From:**
- story-7.1-speaker-community.md (Click on discussion)
- story-7.4-community-features.md (Discussion navigation)

**Required Content:**
- Thread title and metadata
- Chronological posts
- Reply functionality
- Reactions and voting
- Moderation controls
- Subscription options

**Priority:** HIGH

---

#### Mentor Profile Screen
**Status:** MISSING
**Referenced From:**
- story-7.1-speaker-community.md (View mentor)

**Required Content:**
- Mentor bio and expertise
- Mentoring areas
- Availability
- Past mentees (if shareable)
- Request mentorship button
- Reviews/ratings

**Priority:** MEDIUM

---

#### Resource Viewer Screen
**Status:** MISSING
**Referenced From:**
- story-7.1-speaker-community.md (View resource)

**Required Content:**
- Resource content display
- Metadata (author, date, category)
- Download options
- Related resources
- Comments/ratings
- Share functionality

**Priority:** MEDIUM

---

#### Public Profile Preview Screen
**Status:** MISSING
**Referenced From:**
- story-7.1-speaker-profile-management.md (Preview public profile)

**Required Content:**
- Read-only view of public profile
- As-seen-by-others perspective
- Privacy indicators
- Edit link back to management
- Share profile options

**Priority:** LOW

---

#### Announcement Details Screen
**Status:** MISSING
**Referenced From:**
- story-7.3-communication-hub.md (Click on announcement)

**Required Content:**
- Full announcement text
- Author and timestamp
- Attachments/links
- Target audience info
- Read receipt tracking (if organizer)
- Reply/comment functionality

**Priority:** MEDIUM

---

## 11. Attendee Experience & Engagement Screens (NEW - 2025-10-01)

#### User Settings Screen (Attendee-specific)
**Status:** MISSING (mentioned in Category 6, but attendee-specific version needed)
**Referenced From:**
- story-5.2-personal-dashboard.md (Settings icon)
- story-5.3-mobile-pwa.md (Settings menu)

**Required Content:**
- Profile settings
- Notification preferences
- Privacy controls
- Content preferences
- App settings (for PWA)
- Language/accessibility

**Priority:** HIGH

---

#### Help Center Screen
**Status:** MISSING (different from Support Ticket in Category 8)
**Referenced From:**
- story-5.2-personal-dashboard.md (Help link)
- Multiple attendee screens (Help icon)

**Required Content:**
- FAQ sections
- How-to guides
- Video tutorials
- Search functionality
- Contact support link
- Community forum link

**Priority:** MEDIUM

---

#### Event Details Page (Attendee View)
**Status:** MISSING (different from organizer Event Detail)
**Referenced From:**
- story-5.2-personal-dashboard.md (Click on event)
- story-2.4-current-event-landing.md (Attendee view)

**Required Content:**
- Event information (read-only)
- Session schedule
- Speaker lineup
- Registration status
- Add to calendar
- Share event
- Related content

**Priority:** HIGH

---

#### Content Viewer Page
**Status:** MISSING
**Referenced From:**
- story-5.1-content-discovery.md (View content)
- story-5.2-personal-dashboard.md (Open content)
- story-5.3-offline-content.md (Offline viewer)

**Required Content:**
- Content display (PDF/video/slides)
- Navigation controls
- Download option
- Bookmarking
- Notes/annotations
- Related content suggestions

**Priority:** HIGH

---

#### Full Library Management Page
**Status:** MISSING
**Referenced From:**
- story-5.2-personal-dashboard.md (Manage library)

**Required Content:**
- All saved content
- Collections/folders
- Sort and filter options
- Bulk actions
- Storage usage
- Sharing capabilities

**Priority:** MEDIUM

---

#### Learning Path Details Modal/Page
**Status:** MISSING
**Referenced From:**
- story-5.2-personal-dashboard.md (View learning path)

**Required Content:**
- Path overview and objectives
- Required content items
- Progress tracking
- Estimated completion time
- Enroll/start button
- Related paths

**Priority:** MEDIUM

---

#### Achievements Gallery Modal
**Status:** MISSING
**Referenced From:**
- story-5.2-personal-dashboard.md (View all achievements)

**Required Content:**
- All achievements (earned/locked)
- Achievement details
- Earn criteria
- Rarity/statistics
- Share options
- Related achievements

**Priority:** LOW

---

#### Community Leaderboard Page
**Status:** MISSING
**Referenced From:**
- story-5.2-personal-dashboard.md (View full leaderboard)

**Required Content:**
- Ranked user list
- Scoring methodology
- Filter by timeframe/category
- User position highlight
- Profile links
- Achievement badges

**Priority:** LOW

---

#### Storage Management Screen
**Status:** MISSING
**Referenced From:**
- story-5.3-offline-content.md (Manage storage)
- story-5.3-mobile-pwa.md (Storage settings)

**Required Content:**
- Storage usage breakdown
- Downloaded content list
- Clear cache options
- Download quality settings
- Auto-cleanup rules
- Available space indicator

**Priority:** MEDIUM

---

#### Offline Settings Configuration Screen
**Status:** MISSING
**Referenced From:**
- story-5.3-offline-content.md (Configure sync)

**Required Content:**
- Auto-download preferences
- Sync schedule
- Network usage limits
- Content priority rules
- Storage limits
- Notification settings

**Priority:** MEDIUM

---

#### Smart Sync Rules Configuration Screen
**Status:** MISSING
**Referenced From:**
- story-5.3-offline-content.md (Manage smart sync)

**Required Content:**
- Rule creation interface
- Condition builder
- Priority settings
- Test/preview rules
- Active rules list
- Rule templates

**Priority:** LOW

---

#### Registration Flow Screens (Steps 2/3 and 3/3)
**Status:** MISSING
**Referenced From:**
- story-2.4-event-registration.md (Multi-step registration)

**Required Content:**
- **Step 2/3:** Session selection interface, time conflict warnings, capacity indicators
- **Step 3/3:** Review all selections, personal info confirmation, T&C acceptance, submit button

**Priority:** HIGH

---

#### Registration Confirmation Page
**Status:** MISSING
**Referenced From:**
- story-2.4-event-registration.md (Post-registration)

**Required Content:**
- Confirmation message
- Registration summary
- Calendar download
- Email confirmation indicator
- Next steps
- Edit registration option

**Priority:** HIGH

---

#### Ticket/QR Code Page
**Status:** MISSING
**Referenced From:**
- story-2.4-event-registration.md (View ticket)

**Required Content:**
- Event QR code
- Ticket details
- Check-in instructions
- Add to wallet
- Print option
- Transfer ticket

**Priority:** HIGH

---

## 12. Content & Community Features (NEW - 2025-10-01)

#### All Reviews Screen
**Status:** MISSING
**Referenced From:**
- story-5.1-content-discovery.md (View all reviews)

**Required Content:**
- Review list
- Filter by rating/date
- Sort options
- Review details
- Helpful voting
- Report inappropriate

**Priority:** LOW

---

#### Discussion Topics Browser
**Status:** MISSING
**Referenced From:**
- story-7.4-community-features.md (Browse topics)

**Required Content:**
- Topic categories
- Active discussions count
- Popular topics
- Search functionality
- Create new topic
- Subscribe to topics

**Priority:** MEDIUM

---

#### My Discussions Screen
**Status:** MISSING
**Referenced From:**
- story-7.4-community-features.md (My discussions)

**Required Content:**
- Discussions started
- Discussions participated in
- Subscribed discussions
- Saved discussions
- Filter/sort options
- Activity notifications

**Priority:** MEDIUM

---

#### Study Group Details Screen
**Status:** MISSING
**Referenced From:**
- story-7.4-community-features.md (View study group)

**Required Content:**
- Group information
- Member list
- Shared resources
- Discussion board
- Events/meetings
- Join/leave group

**Priority:** LOW

---

## 13. Navigation Modals & Supporting Screens (NEW - 2025-10-01)

#### Filter Modal (Multiple Contexts)
**Status:** MISSING
**Referenced From:**
- story-5.1-content-discovery.md (Filter button)
- story-2.4-current-event-landing.md (Filter sessions)

**Required Content:**
- Filter criteria options
- Multi-select capabilities
- Clear filters
- Apply button
- Save filter preset
- Active filters indicator

**Priority:** MEDIUM

---

#### Session Details Modal
**Status:** MISSING
**Referenced From:**
- story-2.4-current-event-landing.md (Click on session)

**Required Content:**
- Session title and description
- Speaker information
- Time and location
- Capacity/availability
- Add to schedule
- Share session
- Related sessions

**Priority:** HIGH

---

#### Attendee List Modal
**Status:** MISSING
**Referenced From:**
- story-2.4-event-registration.md (View attendees - if public)

**Required Content:**
- Registered attendees
- Profile preview
- Networking options
- Filter by company/role
- Privacy controls
- Connection requests

**Priority:** LOW

---

## Notes

- Many "edit" screens can potentially be modals/overlays rather than full pages
- Consider using consistent patterns (list → detail → edit) across all domains
- Some missing screens may be intentionally deferred to later phases
- This analysis assumes all screens will be implemented; prioritization may reduce scope
- Navigation flows should be validated with actual user journey testing
- **NEW (2025-10-01):** The comprehensive analysis revealed extensive gaps in Partner, Speaker, and Attendee role-specific screens
- **NEW (2025-10-01):** Many missing screens are detail/modal views that support primary workflows
- **NEW (2025-10-01):** Community and engagement features have minimal wireframe coverage

---

## Next Steps

1. **Prioritize missing wireframes** by epic/story implementation order
2. **Create critical missing wireframes** for upcoming development sprints
3. **Decide modal vs. page** for detail/settings screens
4. **Update existing wireframes** with clarified navigation targets
5. **Create comprehensive site map** showing all screens and relationships
6. **Define URL routing structure** matching navigation flows
7. **Document modal vs. page decisions** for edit/settings screens
8. **Consider wireframe templates** for repeated patterns (detail views, lists, modals)
