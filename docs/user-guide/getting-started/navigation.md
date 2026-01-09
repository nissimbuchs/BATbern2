# UI Conventions

> Common patterns and design principles in BATbern

<span class="feature-status implemented">Implemented</span>

## Overview

BATbern follows **Swiss design principles** for a clean, functional, and intuitive user experience. This guide explains common UI patterns you'll encounter throughout the platform.

## Design Principles

### Swiss Design Aesthetics

BATbern's interface embodies Swiss design:

- **Minimalism**: Clean layouts with purposeful whitespace
- **Grid-based**: Structured layouts using 8px spacing units
- **High Contrast**: Excellent readability (WCAG 2.1 AA compliant)
- **Typography**: Helvetica Neue/Helvetica sans-serif family
- **Functionality**: Every element serves a clear purpose

### Color Semantics

Colors convey meaning consistently:

| Color | Meaning | Examples |
|-------|---------|----------|
| **Blue (#2C5F7C)** | Primary actions, Organizer role | Save buttons, active navigation |
| **Light Blue (#4A90B8)** | Speaker role, secondary info | Speaker badges, links |
| **Orange (#E67E22)** | Partner role, accents | Partner tier badges, warnings |
| **Green (#27AE60)** | Success, completion | Successful state transitions, success messages |
| **Yellow (#F39C12)** | Pending, in-progress | Pending status, draft states |
| **Red (#E74C3C)** | Errors, failed states | Validation errors, failed operations |

## Common UI Patterns

### Role-Based UI Adaptation

<span class="feature-status implemented">Implemented</span>

The interface adapts based on your role:

**As an Organizer, you see**:
- ✅ Full entity management (Companies, Users, Events, Partners, Speakers)
- ✅ Event workflow management interface
- ✅ Advanced analytics and reports
- ✅ Partner coordination tools

**As a Speaker, you would see**:
- 👤 Your speaker profile
- 📝 Content submission forms
- 📅 Your assigned sessions
- ❌ No access to workflow or entity management

**As an Attendee, you would see**:
- 🎫 Event registration
- 📆 Event schedules
- 📄 Session materials
- ❌ No access to planning tools

### Buttons

#### Primary Actions

<button style="background: #2C5F7C; color: white; padding: 8px 16px; border: none; border-radius: 4px; cursor: pointer;">Save Changes</button>

Used for primary actions:
- Save changes
- Submit forms
- Create new entities
- Confirm operations

#### Secondary Actions

<button style="background: transparent; color: #2C5F7C; padding: 8px 16px; border: 1px solid #2C5F7C; border-radius: 4px; cursor: pointer;">Cancel</button>

Used for secondary actions:
- Cancel operations
- Go back
- Alternative actions

#### Destructive Actions

<button style="background: #E74C3C; color: white; padding: 8px 16px; border: none; border-radius: 4px; cursor: pointer;">Delete</button>

Used for destructive operations:
- Delete entities
- Permanently remove data
- **Always requires confirmation**

### Form Fields

#### Required Fields

Fields with a red asterisk (*) are required:

```
Email Address *
[                    ]
```

Validation errors appear inline:

```
Email Address *
[invalid-email      ]
⚠️ Please enter a valid email address
```

#### Field Types

**Text Input**:
```
Company Name *
[Müller Architekten AG]
```

**Dropdown Select**:
```
Event Type *
[▼ Select type...    ]
   Full-Day Conference
   Afternoon Workshop
   Evening Lecture
```

**Date Picker**:
```
Event Date *
[📅 2025-03-15       ]
```

**Multi-Select**:
```
Topics
[☑️ Sustainable Building]
[☐ Digital Transformation]
[☑️ Urban Planning]
```

**File Upload**:
```
Company Logo
[Choose File] No file chosen
└─ PNG, JPG, SVG (max 2MB)
```

### Status Badges

<span class="feature-status implemented">Implemented</span>

Status badges provide at-a-glance information:

**Workflow States**:
- <span style="background: #ECF0F1; padding: 4px 8px; border-radius: 12px; font-size: 0.75rem; font-weight: 500;">CREATED</span> - New entity
- <span style="background: #FFF3E0; padding: 4px 8px; border-radius: 12px; font-size: 0.75rem; font-weight: 500;">IN PROGRESS</span> - Active work
- <span style="background: #E8F5E9; padding: 4px 8px; border-radius: 12px; font-size: 0.75rem; font-weight: 500;">COMPLETED</span> - Finished

**User Roles**:
- <span style="background: #2C5F7C; color: white; padding: 4px 8px; border-radius: 12px; font-size: 0.75rem; font-weight: 500;">ORGANIZER</span>
- <span style="background: #4A90B8; color: white; padding: 4px 8px; border-radius: 12px; font-size: 0.75rem; font-weight: 500;">SPEAKER</span>
- <span style="background: #3498DB; color: white; padding: 4px 8px; border-radius: 12px; font-size: 0.75rem; font-weight: 500;">ATTENDEE</span>
- <span style="background: #E74C3C; color: white; padding: 4px 8px; border-radius: 12px; font-size: 0.75rem; font-weight: 500;">ADMIN</span>

**Partner Tiers**:
- <span style="background: linear-gradient(135deg, #B8B8D0, #D0D0E0); padding: 4px 8px; border-radius: 12px; font-size: 0.75rem; font-weight: 500;">💎 DIAMOND</span>
- <span style="background: #E5E4E2; padding: 4px 8px; border-radius: 12px; font-size: 0.75rem; font-weight: 500;">PLATINUM</span>
- <span style="background: #FFD700; padding: 4px 8px; border-radius: 12px; font-size: 0.75rem; font-weight: 500;">GOLD</span>

### Notifications & Alerts

#### Toast Notifications

<span class="feature-status planned">Planned</span>

Brief messages appear at bottom-right:

<div style="background: #27AE60; color: white; padding: 12px; border-radius: 4px; margin: 8px 0;">
✅ Company "Müller Architekten AG" created successfully
</div>

<div style="background: #E74C3C; color: white; padding: 12px; border-radius: 4px; margin: 8px 0;">
❌ Failed to save changes. Please try again.
</div>

Toasts auto-dismiss after 5 seconds or can be manually closed.

#### Inline Alerts

Persistent alerts for important information:

<div class="alert info">
ℹ️ <strong>Info:</strong> This event uses staging Cognito for authentication.
</div>

<div class="alert warning">
⚠️ <strong>Warning:</strong> You have unsaved changes. Save or discard before leaving.
</div>

<div class="alert error">
❌ <strong>Error:</strong> Swiss UID validation failed. Please check the format.
</div>

<div class="alert success">
✅ <strong>Success:</strong> All speakers confirmed for this event.
</div>

### Tables & Data Grids

#### Table Actions

Every table row includes action buttons:

| Company Name | Swiss UID | Employees | Actions |
|--------------|-----------|-----------|---------|
| Müller Architekten AG | CHE-123.456.789 | 45 | 👁️ 📝 🗑️ |

- **👁️ View** - Read-only details
- **📝 Edit** - Modify entity
- **🗑️ Delete** - Remove (with confirmation)

#### Sorting

Click column headers to sort:

- **First click**: Ascending (▲)
- **Second click**: Descending (▼)
- **Third click**: Clear sorting

#### Pagination

Navigate large datasets:

```
◀ Previous  [1] 2 3 ... 10  Next ▶
Showing 1-25 of 237 results
```

Options: 10, 25, 50, 100 rows per page

#### Search & Filters

**Global Search**:
```
🔍 [Search companies...     ]
```

**Advanced Filters** <span class="feature-status implemented">Implemented</span>:
```
🎛️ Filters
   [canton:ZH]
   [employees:>100]
   [verified:true]
```

JSON-based filter syntax for complex queries.

### Modals & Dialogs

#### Confirmation Dialogs

Destructive actions require confirmation:

```
┌────────────────────────────────────┐
│  Delete Company?                   │
├────────────────────────────────────┤
│  Are you sure you want to delete   │
│  "Müller Architekten AG"?          │
│                                    │
│  This action cannot be undone.     │
│                                    │
│  [Cancel]         [Delete] ←red    │
└────────────────────────────────────┘
```

#### Form Modals

Quick forms appear in modals:

```
┌────────────────────────────────────┐
│  Add New Speaker              [×]  │
├────────────────────────────────────┤
│  First Name *                      │
│  [                    ]            │
│                                    │
│  Last Name *                       │
│  [                    ]            │
│                                    │
│  Email *                           │
│  [                    ]            │
│                                    │
│  [Cancel]            [Add Speaker] │
└────────────────────────────────────┘
```

### Loading States

#### Skeleton Loaders

While data loads, skeleton placeholders appear:

```
┌────────────────────────────────────┐
│  ████████████         ▒▒▒▒▒▒▒▒    │
│  ███████              ▒▒▒▒▒▒      │
│                                    │
│  ████████████         ▒▒▒▒▒▒▒▒    │
│  ███████              ▒▒▒▒▒▒      │
└────────────────────────────────────┘
```

#### Spinner

For quick operations:

<div style="text-align: center; padding: 20px;">
🔄 Loading...
</div>

#### Progress Bars

<span class="feature-status planned">Planned</span>

For long operations (file uploads, data imports):

```
Uploading company logo...
[████████████░░░░░░░░] 60%
```

### Breadcrumbs

Navigate hierarchical views:

```
Home > Events > BATbern 2025 > Workflow > Phase A: Setup
```

Click any segment to navigate.

### Tabs

Organize complex forms:

```
[General] [Timeline] [Speakers] [Partners]
─────────
   General Information
   Event Name: BATbern 2025
   Event Type: Full-Day Conference
   ...
```

## Accessibility Features

<span class="feature-status implemented">Implemented</span>

BATbern meets **WCAG 2.1 AA** standards:

- ✅ **Keyboard Navigation**: Tab through all interactive elements
- ✅ **Screen Reader Support**: ARIA labels on all controls
- ✅ **High Contrast**: Text contrast ratio ≥ 4.5:1
- ✅ **Focus Indicators**: Visible focus rings on keyboard navigation
- ✅ **Semantic HTML**: Proper heading hierarchy and landmark roles

## Mobile & Responsive

The interface adapts to different devices:

### Touch Gestures

On mobile/tablet:
- **Tap** - Activate buttons, links
- **Swipe Left/Right** - Navigate carousel items (e.g., event cards)
- **Long Press** - Show context menu <span class="feature-status planned">Planned</span>
- **Pull Down** - Refresh lists <span class="feature-status planned">Planned</span>

### Adaptive Layouts

- **Desktop**: Multi-column, expanded sidebar
- **Tablet**: Two-column, collapsible sidebar
- **Mobile**: Single-column, hamburger menu

## What's Next?

- [Entity Management →](../entity-management/README.md) - Start managing entities
- [Workflow System →](../workflow/README.md) - Master the 3 workflow systems
- [Keyboard Shortcuts →](../appendix/keyboard-shortcuts.md) - Speed up navigation

## Related Topics

- [Dashboard Navigation](dashboard.md) - Dashboard layout and features
- [Feature Status](../appendix/feature-status.md) - What's implemented vs planned
- [Accessibility Guide](../appendix/accessibility.md) <span class="feature-status planned">Planned</span>
