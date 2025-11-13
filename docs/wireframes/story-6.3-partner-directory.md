# Story 6.3: Partner Directory - Wireframe

**Story**: Story 2.8 (Epic 2) - Partner Management Frontend
**Screen**: Partner Directory/List Screen
**User Role**: Organizer
**Related FR**: FR4 (Partner Analytics - Backlog), FR8 (Partner Strategic Input)

**Implementation Scope:**
- ✅ **Story 2.7** (Epic 2): Partner Coordination Service Foundation (backend APIs)
  - Partner CRUD with meaningful IDs (`companyName`)
  - Contact Management (stores `username`)
  - Topic Voting & Suggestions
- 📝 **Story 2.8** (Epic 2): Partner Management Frontend (THIS WIREFRAME)
  - Organizer UI for partner directory/detail
  - Basic partner CRUD interface
  - Contact management UI
  - Read-only topic voting display
- 📦 **Epic 8** (Deferred to Phase 2): Advanced Partner Portal Features
  - Engagement score calculation & analytics dashboard (Story 6.1)
  - Interactive topic voting interface (Story 6.4)
  - Full meeting coordination UI (Story 6.2)

**IMPORTANT (ADR-003 Compliance):**
- All API endpoints use `{companyName}` (meaningful ID), NOT `{partnerId}` (UUID)
- Contact endpoints use `{username}` (meaningful ID), NOT `{contactId}` or `{userId}` (UUID)
- Example: `GET /api/v1/partners/GoogleZH` (NOT `/api/v1/partners/{uuid}`)

---

## Visual Wireframe

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│ ← Back to Dashboard    Partner Directory                   [+ Add Partner]      │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                   │
│  ┌──── FILTERS & SEARCH ────────────────────────────────────────────────────┐   │
│  │                                                                           │   │
│  │  🔍 [Search partners by name...]           [All Tiers ⏷] [All Status ⏷] │   │
│  │                                                                           │   │
│  │  Quick Filters:  [🏆 Strategic] [💎 Platinum] [🥇 Gold] [🥈 Silver] [🥉 Bronze]  │   │
│  │                                                                           │   │
│  └───────────────────────────────────────────────────────────────────────────┘   │
│                                                                                   │
│  ┌──── PARTNER OVERVIEW ─────────────────────────────────────────────────────┐   │
│  │                                                                            │   │
│  │  📊 Total Partners: 24        Active: 22        Engaged: 18 (82%)        │   │
│  │                                                                            │   │
│  │  Tier Distribution:  🏆 Strategic (1)  💎 Platinum (2)  🥇 Gold (6)  🥈 Silver (9)  🥉 Bronze (6)  │   │
│  │                                                                            │   │
│  └────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                   │
│  ┌──── PARTNER LIST ──────────────────────────────────────────────────────────┐  │
│  │                                                                             │  │
│  │  Sort by: [Engagement Score ⏷]  View: [Grid ▦]  [List ☰]                  │  │
│  │                                                                             │  │
│  │  ┌─────────────────────────────────────────────────────────────────────┐  │  │
│  │  │                                                                      │  │  │
│  │  │  ┌──────┐  🏆 STRATEGIC                                              │  │  │
│  │  │  │[Logo]│  TechCorp AG (GoogleZH)          Engagement: ████████ 92%  │  │  │
│  │  │  └──────┘                                                            │  │  │
│  │  │           🏢 Software & IT Services                                  │  │  │
│  │  │           👤 Contact: Maria Schmidt (m.schmidt@techcorp.ch)         │  │  │
│  │  │           📊 Last Event: Spring 2025 (15 attendees)                 │  │  │
│  │  │           🗳️ Topic Votes: 5 active  |  📅 Next Meeting: May 20      │  │  │
│  │  │                                                                      │  │  │
│  │  │           [View Details] [Send Email] [Schedule Meeting] [Analytics]│  │  │
│  │  └──────────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                             │  │
│  │  ┌─────────────────────────────────────────────────────────────────────┐  │  │
│  │  │  ┌──────┐  🥇 GOLD                                                   │  │  │
│  │  │  │[Logo]│  Swiss Financial Group          Engagement: ██████░░ 78%  │  │  │
│  │  │  └──────┘                                                            │  │  │
│  │  │           🏢 Banking & Finance                                       │  │  │
│  │  │           👤 Contact: Peter Weber (p.weber@swissfinance.ch)         │  │  │
│  │  │           📊 Last Event: Spring 2025 (8 attendees)                  │  │  │
│  │  │           🗳️ Topic Votes: 3 active  |  📅 Next Meeting: June 15     │  │  │
│  │  │                                                                      │  │  │
│  │  │           [View Details] [Send Email] [Schedule Meeting] [Analytics]│  │  │
│  │  └──────────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                             │  │
│  │  ┌─────────────────────────────────────────────────────────────────────┐  │  │
│  │  │  ┌──────┐  🥈 SILVER                                                 │  │  │
│  │  │  │[Logo]│  Innovation Labs               Engagement: ████░░░░ 56%   │  │  │
│  │  │  └──────┘                                                            │  │  │
│  │  │           🏢 Technology & Research                                   │  │  │
│  │  │           👤 Contact: Anna Müller (a.mueller@innovlabs.ch)          │  │  │
│  │  │           📊 Last Event: Fall 2024 (4 attendees)                    │  │  │
│  │  │           🗳️ Topic Votes: 1 active  |  📅 Next Meeting: Pending     │  │  │
│  │  │                                                                      │  │  │
│  │  │           [View Details] [Send Email] [Schedule Meeting] [Analytics]│  │  │
│  │  └──────────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                             │  │
│  │  ┌─────────────────────────────────────────────────────────────────────┐  │  │
│  │  │  ┌──────┐  🥉 BRONZE                                                 │  │  │
│  │  │  │[Logo]│  StartupHub Bern               Engagement: ██░░░░░░ 34%   │  │  │
│  │  │  └──────┘                                                            │  │  │
│  │  │           🏢 Startup Incubator                   ⚠️ Low Engagement   │  │  │
│  │  │           👤 Contact: Thomas Klein (t.klein@startuphub.ch)          │  │  │
│  │  │           📊 Last Event: Spring 2024 (2 attendees)                  │  │  │
│  │  │           🗳️ Topic Votes: 0 active  |  📅 Next Meeting: Not scheduled│  │  │
│  │  │                                                                      │  │  │
│  │  │           [View Details] [Send Email] [Schedule Meeting] [⚠️ Follow Up] │  │
│  │  └──────────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                             │  │
│  │                           [Load More Partners (20 more) ↓]                 │  │
│  │                                                                             │  │
│  └─────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                   │
│  ┌──── BULK ACTIONS ──────────────────────────────────────────────────────────┐  │
│  │                                                                             │  │
│  │  Selected: 0 partners                                                       │  │
│  │                                                                             │  │
│  │  [ Select All ] [ Send Bulk Email ] [ Export Contact List ] [ Generate Report ]│
│  │                                                                             │  │
│  └─────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Interactive Elements

- **[← Back to Dashboard] button**: Return to Event Management Dashboard (story-1.16-event-management-dashboard.md)
- **[+ Add Partner] button**: Opens partner creation workflow (promotes company to partner status)
- **Search input field**: Real-time partner name search with autocomplete
- **[All Tiers ⏷] dropdown**: Filter by partnership tier (Strategic, Platinum, Gold, Silver, Bronze, All)
- **[All Status ⏷] dropdown**: Filter by engagement status (Active, Inactive, Low Engagement, All)
- **Quick filter badges**: One-click tier filtering (🏆 Strategic, 💎 Platinum, 🥇 Gold, 🥈 Silver, 🥉 Bronze)
- **[Engagement Score ⏷] dropdown**: Sort options (Engagement, Name, Industry, Last Event, Tier)
- **View toggles [Grid ▦] [List ☰]**: Switch between grid and list view layouts
- **Partner cards**: Click anywhere to view full partner details
- **[View Details] button**: Navigate to Partner Detail Screen
- **[Send Email] button**: Opens email composition with partner contact pre-filled
- **[Schedule Meeting] button**: Opens meeting scheduler with partner availability
- **[Analytics] button**: Navigate to Partner Analytics Dashboard (if FR4 restored post-MVP)
- **[⚠️ Follow Up] button**: Creates follow-up task for low-engagement partners
- **Checkbox selection**: Multi-select partners for bulk actions
- **[ Select All ] button**: Select all visible partners
- **[ Send Bulk Email ] button**: Send email to selected partners
- **[ Export Contact List ] button**: Download selected partners as CSV/Excel
- **[ Generate Report ] button**: Create engagement report for selected partners

---

## Functional Requirements Met

- **FR8**: Partner strategic input coordination through topic voting visibility
- **FR4 (Backlog)**: Partner analytics dashboard access (analytics button shown but links to backlog)
- **Partner Management**: Centralized partner directory with tier management
- **Communication**: Direct email and meeting scheduling capabilities
- **Engagement Tracking**: Visual engagement scores and activity indicators

---

## Technical Notes

- **Real-time Search**: Debounced search with 300ms delay using Lodash debounce
- **Lazy Loading**: Virtual scrolling for 100+ partners using react-window library
- **Tier Badges**: Material-UI Chip components with tier-specific colors (🏆 Strategic, 💎 Platinum, 🥇 Gold, 🥈 Silver, 🥉 Bronze)
- **Engagement Calculation**: ⚠️ **Deferred to Epic 8 (Story 6.1)** - Currently showing placeholder/mock data. Full calculation: topic votes (30%), event attendance (40%), meeting participation (20%), content interaction (10%)
- **Responsive Grid**: Material-UI Grid with breakpoints (xs=1, sm=2, md=2, lg=3 columns)
- **Export Functionality**: CSV generation using Papa Parse library
- **Email Integration**: Opens system mailto: link or integrates with email service
- **Meeting Scheduling**: Integration with calendar API for availability checking
- **Data Refresh**: Auto-refresh every 5 minutes for engagement scores

---

## API Requirements

### Initial Page Load APIs

**Note**: This wireframe uses the Partners API from Story 2.7 (Partner Coordination Service Foundation).

**IMPORTANT (ADR-003)**: All endpoints use `companyName` (meaningful ID), NOT UUID identifiers.

1. **GET /api/v1/partners?filter={"status":"active"}&sort=-partnershipLevel&page=1&limit=50**
   - **ADR-003**: No `partnerId` in response - partners identified by `companyName`
   - Query params: filter (JSON filter), sort (partnershipLevel, companyName), page, limit
   - Returns: Array of partner entities (companyName, partnershipLevel, startDate, endDate, isActive)
   - Used for: Display partner list
   - **Note**: Engagement scores deferred to Epic 8 (Story 6.1) - show placeholder data in Story 2.8

2. **GET /api/v1/partners (count aggregation via application logic)**
   - **Story 2.8 Implementation**: Frontend calculates summary from partner list response
   - Returns: Standard partner list, frontend counts active, calculates tier distribution
   - Used for: Display partner overview section and tier distribution
   - **Note**: Backend aggregation endpoint could be added later for performance optimization

3. **GET /api/v1/partners/{companyName}?include=contacts** ✅ **ADR-003 Compliant**
   - **ADR-003**: Uses `companyName` path parameter (e.g., `/api/v1/partners/GoogleZH`)
   - Query params: include=contacts (embeds contact information via HTTP enrichment)
   - Returns: Partner entity with embedded contacts enriched from User Service
   - Used for: Display contact information on partner cards
   - **Story 2.7**: Backend calls `GET /api/v1/users/{username}` to enrich contact data

### User Action APIs

4. **GET /api/v1/partners?filter={"companyName":{"$contains":"Tech"}}&limit=20** ✅ **ADR-003**
   - **ADR-003**: Search by `companyName` (meaningful ID), NOT `partnerId`
   - Triggered by: User types in search box
   - Query params: filter (JSON with text search operators), limit
   - Returns: Filtered partner list matching search term
   - Used for: Real-time search results, autocomplete suggestions

5. **GET /api/v1/partners?filter={"partnershipLevel":"GOLD","isActive":true}&page=1** ✅ **ADR-003**
   - Triggered by: User selects tier/status filters
   - Query params: filter (JSON with multiple criteria)
   - Returns: Filtered partner list
   - Used for: Filter partners by tier (partnershipLevel: BRONZE, SILVER, GOLD, PLATINUM, STRATEGIC) and active status
   - **Note**: `engagementLevel` filter deferred to Epic 8 (requires engagement calculation)

6. **POST /api/v1/partners** ✅ **ADR-003 Compliant**
   - **ADR-003**: Request uses `companyName` (meaningful ID), NOT `companyId` (UUID)
   - Triggered by: User clicks [+ Add Partner] and selects company
   - Payload: `{ companyName: "GoogleZH", partnershipLevel: "BRONZE", startDate: "2025-01-01", endDate: "2026-12-31" }`
   - Returns: Created partner entity with isActive=true
   - Used for: Create new partnership
   - **Story 2.7**: Backend validates company exists via `GET /api/v1/companies/{companyName}`

7. **PATCH /api/v1/partners/{companyName}** ✅ **ADR-003 Compliant**
   - **ADR-003**: Path uses `companyName` (e.g., `/api/v1/partners/GoogleZH`)
   - Triggered by: Organizer changes partner tier
   - Payload: `{ partnershipLevel: "GOLD", startDate: "2025-05-01", endDate: "2026-12-31" }`
   - Returns: Updated partner with new partnershipLevel
   - Used for: Change partner tier level
   - **Note**: Tier change reason tracking could be added in Epic 8 for audit trail

8. **POST /api/v1/partners/export** ⚠️ **Deferred to Story 2.8+**
   - Triggered by: User clicks [Send Bulk Email] or [Export Contact List]
   - Payload: `{ action: "email|export", companyNames: ["GoogleZH", "MicrosoftBE"], format: "csv|xlsx", fields: [] }`
   - Returns: Email task ID or export download URL
   - Used for: Bulk email or export partner data
   - **Story 2.8 Note**: Bulk email integration deferred to Epic 8, export to CSV as simple frontend implementation

9. **POST /api/v1/partners/{companyName}/meetings** ✅ **ADR-003 + Deferred**
   - **ADR-003**: Path uses `companyName` (e.g., `/api/v1/partners/GoogleZH/meetings`)
   - Triggered by: User clicks [Schedule Meeting] on partner card
   - Payload: `{ type: "strategic_planning", proposedDates: [], agenda }`
   - Returns: Created meeting ID
   - Used for: Schedule meeting with specific partner
   - **Story 2.7**: Basic meeting entity exists, full scheduling UI deferred to Epic 8 (Story 6.2)

10. **GET /api/v1/partners/{companyName}/analytics** ⚠️ **Deferred to Epic 8 (Story 6.1)**
    - **ADR-003**: Path uses `companyName`
    - Triggered by: User clicks [Analytics] button
    - Status: **NOT IMPLEMENTED in Story 2.7/2.8** - Analytics dashboard deferred to Epic 8
    - Story 2.8: Show "Coming soon" placeholder or disable button
    - Epic 8 (Story 6.1): Full analytics implementation with engagement calculations

---

## Navigation Map

### Primary Navigation Actions

1. **← Back button** → Navigate to `Event Management Dashboard` (story-1.16-event-management-dashboard.md)
   - Target: Organizer main dashboard
   - Context: Return to event management

2. **[+ Add Partner] button** → Opens `Add Partner Workflow`
   - Target: Modal or dedicated page for partner creation
   - Context: Company selection → tier assignment → contact setup

3. **[View Details] button** → Navigate to `Partner Detail Screen` (MISSING - see sitemap)
   - Target: Full partner profile page
   - Context: companyName, current engagement data

4. **[Analytics] button** → Navigate to `Partner Analytics Dashboard` (Backlog - FR4)
   - Target: Partner-specific analytics (if FR4 restored post-MVP)
   - Context: companyName, dateRange for metrics

### Secondary Navigation (Data Interactions)

5. **Partner card click** → Navigate to `Partner Detail Screen` (MISSING - see sitemap)
   - Target: Full partner information page
   - Context: companyName, full contact and engagement history

6. **[Schedule Meeting] button** → Opens `Meeting Scheduler`
   - Target: Meeting coordination interface (story-6.2-partner-meetings.md)
   - Context: companyName, meeting type, availability

7. **Industry link** → Filter partners by industry
   - Target: Same page, filtered by industry
   - Context: industryFilter applied to partner list

8. **Engagement score** → Opens `Engagement Details Modal`
   - Target: Modal overlay showing engagement breakdown
   - Context: companyName, engagement metrics detail

### Event-Driven Navigation

9. **[Send Email] button** → Opens email client or email modal
   - Action: Compose email with partner contact pre-filled
   - Target: System email client (mailto:) or in-app email composer
   - Context: partnerContactEmail, emailTemplate

10. **[Send Bulk Email] button** → Opens `Bulk Email Composer`
    - Target: Modal with email template selection
    - Context: selectedCompanyNames[], email recipients list

11. **[Export Contact List] button** → Downloads file
    - Action: Generate and download CSV/Excel file
    - Target: File download
    - Context: selectedCompanyNames[], export format

12. **[Generate Report] button** → Opens `Report Generator`
    - Target: Modal for report configuration
    - Context: selectedCompanyNames[], reportType selection

### Error States & Redirects

13. **No partners found** → Show empty state
    - Condition: No partners in system or filters too restrictive
    - Action: Display empty state with [Add Partner] CTA
    - Context: Clear filters option, create first partner prompt

14. **Search no results** → Show no results message
    - Condition: Search query returns no matches
    - Action: Display "No partners found matching '{query}'" with suggestions
    - Context: Clear search, adjust filters, browse all partners

15. **API failure** → Show error banner
    - Condition: API request fails or times out
    - Action: Display error with [Retry] button
    - Context: Error message, last successful data cached

16. **Unauthorized access** → Redirect to login
    - Condition: User not authenticated or insufficient permissions
    - Target: Login screen with return URL
    - Context: Redirect back to partner directory after authentication

---

## Responsive Design Considerations

### Mobile Layout Changes

**Stacked Layout (320px - 768px):**
- Search and filters: Stacked vertically with collapsible filter panel
- Partner Overview: Single-row stats with horizontal scroll
- Quick filters: Horizontal scrollable chips
- Partner cards: Full-width stacked layout (one per row)
  - Logo and tier badge at top
  - Contact info and engagement score stacked
  - Action buttons: Dropdown menu (⋮) for space efficiency
- Bulk actions: Bottom sheet modal
- Sort/view controls: Combined in single dropdown

**Tablet Layout (768px - 1024px):**
- Two-column partner grid
- Side-by-side search and filters
- Partner overview: Full row with all stats visible
- Action buttons: Visible with compact labels

### Mobile-Specific Interactions

- **Swipe gestures**: Swipe partner card left to reveal quick actions (Email, Meeting)
- **Pull-to-refresh**: Refresh partner list and engagement scores
- **Bottom sheet**: Filters, bulk actions, partner details in bottom sheets
- **Touch targets**: 44px minimum for all interactive elements
- **Sticky header**: Search bar sticky on scroll
- **Infinite scroll**: Load more partners on scroll (replaces pagination on mobile)
- **Floating action button (FAB)**: [+ Add Partner] as FAB in bottom-right
- **Tap to expand**: Tap partner card to expand details inline before navigating

---

## Accessibility Notes

- **Keyboard Navigation**: Full tab navigation with logical focus order
- **ARIA Labels**:
  - `aria-label="Filter partners by tier"` on tier dropdown
  - `aria-label="Search partners by name"` on search input
  - `aria-label="Partner engagement score: 92%"` on engagement bars
  - `role="listitem"` on partner cards with `aria-labelledby` pointing to partner name
- **Screen Reader Announcements**:
  - Live region (`aria-live="polite"`) for search results count
  - Status updates when filters applied
  - Engagement warnings announced for screen readers
- **Color Contrast**: WCAG 2.1 AA compliance
  - Tier badges: Sufficient contrast ratios (Premium: gold on dark, Gold: yellow on dark, etc.)
  - Engagement bars: Color + percentage text for accessibility
- **Focus Indicators**: 2px solid outline on all focused elements
- **Alt Text**: All partner logos have descriptive alt text (`alt="TechCorp AG logo"`)
- **Semantic HTML**:
  - `<main>` for partner list section
  - `<nav>` for filters and sorting controls
  - `<article>` for each partner card
- **Skip Links**: "Skip to partner list" link at top

---

## State Management

### Local Component State

- `searchQuery`: Current search input value
- `selectedFilters`: Object containing tier and status filters { tier: 'all', status: 'all' }
- `viewMode`: Current view mode ('grid' or 'list')
- `sortBy`: Current sort field ('engagement', 'name', 'industry', 'lastEvent', 'tier')
- `sortOrder`: Sort direction ('asc' or 'desc')
- `selectedPartners`: Array of selected partner companyNames for bulk actions
- `page`: Current pagination page (offset for API)

### Global State (Zustand Store)

- `partnerList`: Array of partner objects from API
- `partnerStatistics`: Aggregate partner statistics
- `engagementMetrics`: Map of companyName to engagement data
- `filterOptions`: Available filter options (tiers, industries, statuses)

### Server State (React Query)

- `usePartners(filters, sortBy, page)`: Partner list with 2-minute cache, refetch on window focus
- `usePartnerStatistics()`: Statistics with 5-minute cache
- `usePartnerEngagement(companyName)`: Individual engagement metrics with 1-minute cache
- `usePartnerContacts(companyName)`: Contact information with 10-minute cache

### Real-Time Updates

- **WebSocket Connection**: `/ws/partners/engagement` (if implemented)
  - Real-time engagement score updates when partners vote or attend events
  - Updates partner card engagement bars without refresh
  - Fallback to polling every 2 minutes if WebSocket unavailable

---

## Form Validation Rules

_No forms on this screen - filtering and search are non-validated inputs_

---

## Edge Cases & Error Handling

- **Empty State (No Partners)**:
  - Show "No partners yet" message
  - Display [+ Add Partner] CTA prominently
  - Provide guidance on partner onboarding process

- **Loading State**:
  - Display skeleton cards for partner list (4-6 skeleton items)
  - Show loading spinner in partner overview section
  - Disable filters and search while loading

- **Error State (API Failure)**:
  - Show error banner: "Unable to load partners. Please try again."
  - Provide [Retry] button to refetch data
  - Cache last successful data if available (show with "showing cached data" indicator)

- **Search No Results**:
  - Display "No partners found matching '{query}'"
  - Suggest: "Try adjusting your search or clearing filters"
  - Show [Clear Search] and [Clear Filters] buttons

- **Filter No Results**:
  - Display "No partners match the selected filters"
  - Show active filters with [x] to remove individual filters
  - Provide [Clear All Filters] button

- **Low Engagement Warning**:
  - Highlight partners with <40% engagement in orange/red
  - Show ⚠️ warning icon on card
  - Provide [⚠️ Follow Up] action button
  - Auto-suggest follow-up email templates

- **Meeting Scheduling Conflict**:
  - Show availability conflict message
  - Suggest alternative dates based on partner availability
  - Allow manual date/time entry

- **Bulk Action Limits**:
  - Limit bulk email to max 50 partners at once
  - Show warning if limit exceeded: "Please select 50 or fewer partners"
  - Provide option to split into multiple batches

- **Export Failure**:
  - Show error: "Unable to generate export. Please try again."
  - Offer alternative: "Copy email addresses to clipboard"
  - Log error for debugging

- **Tier Update Unauthorized**:
  - Show permission error: "Only senior organizers can change partner tiers"
  - Provide option to request tier change approval

- **Stale Data**:
  - Show "Last updated: 5 minutes ago" timestamp
  - Provide [Refresh] button for manual update
  - Auto-refresh engagement scores every 5 minutes

---

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-10-04 | 1.0 | Initial wireframe creation for Partner Directory Screen | ux-expert |

---

## Review Notes

### Stakeholder Feedback

_To be added during review_

### Design Iterations

_To be documented as design evolves_

### Open Questions

1. **Partner Tier Levels**: Confirm tier naming convention (Premium/Gold/Silver/Bronze vs. Platinum/Gold/Silver)?
   - Recommendation: Use ⭐ Premium, 🥇 Gold, 🥈 Silver, 🥉 Bronze for visual clarity
   - Decision needed from business team

2. **Engagement Score Calculation**: Confirm weighting algorithm for engagement score?
   - Proposed: Topic votes (30%), Event attendance (40%), Meeting participation (20%), Content interaction (10%)
   - Algorithm specification needed from analytics team

3. **Bulk Email Limits**: What's the maximum number of partners for bulk email?
   - Technical constraint: AWS SES sending limits
   - Business rule: Recommended max 50 per batch to avoid spam filters
   - Decision needed from compliance team

4. **Partner Detail Screen**: Should we create a dedicated partner detail screen or use a modal?
   - Option A: Full-page partner profile (better for complex information)
   - Option B: Slide-over panel or modal (faster interaction)
   - UX testing needed to determine preference

5. **Analytics Integration**: When FR4 (Partner Analytics) is restored, how deeply should directory integrate?
   - Should engagement scores link directly to analytics dashboard?
   - Should cards show preview charts or just link to full analytics?
   - Product roadmap dependency

6. **Meeting Scheduling**: Integrate with external calendar systems (Outlook, Google) or internal only?
   - External integration complexity vs. user convenience
   - Technical feasibility assessment needed
