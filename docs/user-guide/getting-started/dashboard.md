# Dashboard Navigation

> Navigate the BATbern organizer interface

<span class="feature-status implemented">Implemented</span>

## Overview

The BATbern organizer dashboard is your central hub for event planning and management. The interface adapts to your **Organizer** role, showing only relevant features and actions.

## Dashboard Layout

The dashboard follows a clean, Swiss design aesthetic with three main areas:

```
┌─────────────────────────────────────────────────────┐
│  Top Navigation Bar (Logo, Search, Profile)        │
├──────────┬──────────────────────────────────────────┤
│          │                                          │
│  Left    │  Main Content Area                       │
│  Sidebar │  (Event Cards, Tables, Forms)            │
│          │                                          │
│  Entity  │                                          │
│  Nav     │                                          │
│          │                                          │
└──────────┴──────────────────────────────────────────┘
```

### Top Navigation Bar

The top bar provides quick access to global functions:

- **BATbern Logo** (top-left) - Click to return to dashboard home
- **Global Search** (center) - Search across companies, users, events, speakers
- **Notifications** (top-right) - Bell icon shows recent alerts <span class="feature-status planned">Planned</span>
- **Profile Menu** (top-right) - Avatar dropdown with:
  - Profile settings
  - Language selection (English/German) <span class="feature-status planned">Planned</span>
  - Logout

### Left Sidebar Navigation

The sidebar organizes features by entity type:

**Entity Management**:
- 🏢 **Companies** - Manage architectural firms
- 👥 **Users** - Manage organizers, speakers, attendees
- 📅 **Events** - Plan and coordinate conferences
- 🤝 **Partners** - Coordinate partner relationships
- 🎤 **Speakers** - Track speaker profiles and status

**Advanced Features** <span class="feature-status planned">Planned</span>:
- 📊 **Analytics** - Event metrics and reports
- 📧 **Notifications** - Manage alerts and escalations
- ⚙️ **Settings** - Platform configuration

### Main Content Area

The main content area displays:

- **Dashboard Home**: Event cards and quick actions
- **Entity Lists**: Paginated tables with search/filter
- **Entity Details**: Forms and detailed views
- **Workflow Views**: Specialized interfaces for 16-step workflow

## Dashboard Home

After logging in, you'll see the **Dashboard Home** with:

### Current Event Card

<div class="workflow-phase phase-a">

**Current Active Event** (if one exists)

Displays:
- Event name (e.g., "BATbern 2025")
- Event type badge (Full-Day / Afternoon / Evening)
- Current workflow state (e.g., "TOPIC_SELECTED")
- Days until event
- Quick actions:
  - **Continue Workflow** → Jump to next workflow step
  - **View Details** → See full event information
  - **Edit** → Modify event properties
</div>

### Recent Activity

A timeline showing recent platform activity:
- New user registrations
- Speaker status changes
- Partner meeting requests
- Workflow state transitions

### Quick Actions

Shortcuts to common organizer tasks:
- ➕ **Create New Event** - Start planning a new conference
- 👤 **Add Speaker** - Add a new speaker profile
- 🏢 **Register Company** - Add a new architectural firm
- 🤝 **Invite Partner** - Onboard a new partner organization

## Entity List Views

Clicking an entity in the sidebar navigates to the list view:

### Table Features

<span class="feature-status implemented">Implemented</span>

All entity tables support:

- **Search**: Global search across all columns
- **Filtering**: Advanced filters with JSON syntax <span class="feature-status implemented">Implemented</span>
- **Sorting**: Click column headers to sort ascending/descending
- **Pagination**: Navigate through large datasets (10/25/50 per page)
- **Bulk Actions**: Select multiple rows for batch operations <span class="feature-status planned">Planned</span>

### Table Example: Events

| Event Name | Type | Date | State | Speakers | Actions |
|------------|------|------|-------|----------|---------|
| BATbern 2025 | Full-Day | 2025-03-15 | SPEAKERS_IDENTIFIED | 8 | View • Edit • Delete |
| BATbern 2024 | Afternoon | 2024-03-20 | ARCHIVED | 6 | View |

**Actions**:
- **View** (👁️) - Read-only details
- **Edit** (✏️) - Modify entity
- **Delete** (🗑️) - Remove entity (with confirmation)

## Entity Detail Views

Clicking "View" or "Edit" navigates to the detail view:

### Form Layout

Forms follow a consistent pattern:

<div class="step" data-step="1">

**Header Section**
- Entity type (e.g., "Event Details")
- Status badge (e.g., "ACTIVE")
- Last modified timestamp
</div>

<div class="step" data-step="2">

**Form Tabs** (for complex entities)
- **General** - Basic information
- **Timeline** - Dates and schedules (Events)
- **Relationships** - Associated entities (Events ↔ Speakers)
- **History** - Audit log <span class="feature-status planned">Planned</span>
</div>

<div class="step" data-step="3">

**Form Fields**
- Required fields marked with red asterisk (*)
- Validation errors shown inline
- Help text for complex fields
</div>

<div class="step" data-step="4">

**Action Buttons**
- **Save** - Commit changes
- **Save & Continue** - Save and stay on page
- **Cancel** - Discard changes and return to list
</div>

## Keyboard Shortcuts

<span class="feature-status planned">Planned</span>

Speed up your workflow with keyboard shortcuts:

| Shortcut | Action |
|----------|--------|
| `Ctrl/Cmd + K` | Global search |
| `Ctrl/Cmd + S` | Save current form |
| `Ctrl/Cmd + N` | Create new entity |
| `Esc` | Close modal/cancel |
| `?` | Show keyboard shortcuts help |

See [Keyboard Shortcuts Reference](../appendix/keyboard-shortcuts.md) for the complete list.

## Responsive Design

The dashboard adapts to different screen sizes:

### Desktop (1920x1080+)
- Full sidebar visible
- Multi-column layouts
- Advanced filters expanded

### Tablet (768x1024)
- Collapsible sidebar
- Two-column layouts
- Simplified filters

### Mobile (375x667)
- Hamburger menu navigation
- Single-column layouts
- Touch-optimized controls

## Workflow-Specific Views

The 16-step workflow includes specialized interfaces:

### Topic Heat Map

<span class="feature-status implemented">Implemented</span>

Visualize 20+ years of historical topic data:
- Color-coded frequency (dark blue = frequent, light = rare)
- Interactive hover for details
- Click to select topics for current event

See [Topic Heat Map Feature](../features/heat-maps.md) for details.

### Speaker Outreach Tracker

<span class="feature-status in-progress">In Progress</span>

Track speaker contacts and status:
- Kanban-style board (IDENTIFIED → CONTACTED → INTERESTED → CONFIRMED)
- Drag-and-drop status updates
- Contact history and notes

See [Phase B: Outreach](../workflow/phase-b-outreach.md) for details.

### Slot Assignment Interface

<span class="feature-status planned">Planned</span>

Drag-and-drop speakers to event slots:
- Visual timeline
- Speaker availability indicators
- Conflict detection

See [Phase D: Assignment](../workflow/phase-d-assignment.md) for details.

## Tips for Efficient Navigation

### Use Global Search

The global search (top-center) searches across:
- Company names and Swiss UIDs
- User names and email addresses
- Event names and topics
- Speaker names and expertise
- Partner names

**Pro Tip**: Use partial matches (e.g., "Zür" finds "Zürich")

### Bookmark Common Views

Bookmark frequently used pages in your browser:
- Dashboard home: `/dashboard`
- Event list: `/events`
- Speaker list: `/speakers`
- Current event workflow: `/events/{event-id}/workflow`

### Resource Expansion

<span class="feature-status implemented">Implemented</span>

API responses support resource expansion for efficiency:

```
GET /api/events/123?expand=speakers,partners
```

Returns event with embedded speaker and partner data in a single request.

See [API Documentation](../../api/) for details.

## What's Next?

- [UI Conventions →](navigation.md) - Learn common patterns and shortcuts
- [Entity Management →](../entity-management/README.md) - Start managing entities
- [16-Step Workflow →](../workflow/README.md) - Master the event workflow

## Related Topics

- [Login & Authentication](login.md) - Access your account
- [Companies Management](../entity-management/companies.md) - Manage architectural firms
- [Events Management](../entity-management/events.md) - Plan conferences
