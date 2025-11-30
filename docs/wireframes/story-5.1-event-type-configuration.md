# Story 5.1: Event Type Configuration - Wireframe

**Story**: Epic 5, Story 5.1 - Event Type Definition
**Screens**: Event Type Selector (Organizer), Event Type Configuration (Admin)
**User Roles**: Organizer, Admin
**Related FR**: FR2 (16-Step Workflow - Step 1 partial)

---

## Implementation Status & Scope

**CURRENT STATE** (Story 2.5.3):
- ✅ Event Type selector exists in EventForm.tsx (lines 570-603)
- ✅ Basic dropdown with 3 options: Full Day, Afternoon, Evening
- ✅ Values stored in event metadata as JSON
- ❌ NO backend event_types table yet
- ❌ NO slot configuration details shown
- ❌ NO preview component
- ❌ NO admin configuration screen

**STORY 5.1 WILL ADD**:
1. **Backend**: event_types table, REST API endpoints, EventType entity
2. **Frontend Enhancement**: Fetch slot details from API, show in dropdown
3. **New Component**: SlotTemplatePreview below selector
4. **Admin Screen**: Configure event type templates (ORGANIZER only)
5. **Caching**: Caffeine cache for event type configurations

**Integration Point**:
Story 5.1 enhances the existing EventForm component (lines 570-603) by:
- Replacing hardcoded MenuItem values with API-fetched data
- Adding slot configuration details to each MenuItem
- Adding SlotTemplatePreview component below the selector

---

## Screen 1: Event Type Selector in Event Form Modal (CURRENT IMPLEMENTATION)

**Context**: Organizer clicks "Create Event" or "Edit Event" which opens a modal dialog with the EventForm component. The event type selector is integrated into this form.

**Implementation**: `web-frontend/src/components/organizer/EventManagement/EventForm.tsx` (lines 448-721)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  Create Event                                                              ✕    │
├─────────────────────────────────────────────────────────────────────────────────┤
│  📝 Auto-save enabled  [●] Saved at 14:32:15                                   │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ┌─ BASIC INFORMATION ────────────────────────────────────────────────────────┐ │
│  │                                                                             │ │
│  │  Event #: [54      ]  Title: [Spring Conference 2025              ] *      │ │
│  │                                                                             │ │
│  │  Description: *                                                            │ │
│  │  ┌────────────────────────────────────────────────────────────────────┐   │ │
│  │  │ A comprehensive Spring conference covering advanced                │   │ │
│  │  │ microservices architecture patterns, cloud-native                  │   │ │
│  │  │ development, and lessons learned from production deployments.      │   │ │
│  │  └────────────────────────────────────────────────────────────────────┘   │ │
│  │                                                                             │ │
│  └─────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                  │
│  ┌─ DATES & TIMING ───────────────────────────────────────────────────────────┐ │
│  │                                                                             │ │
│  │  Event Date: * [2025-03-15  📅]    Reg. Deadline: [2025-03-10  📅]        │ │
│  │                                                                             │ │
│  └─────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                  │
│  ┌─ EVENT TYPE & CAPACITY ────────────────────────────────────────────────────┐ │
│  │                                                                             │ │
│  │  Event Type: [Full Day Event                                          ▼] * │ │
│  │              Capacity: [200        ]                                       │ │
│  │                                                                             │ │
│  └─────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                  │
│  ┌─ STATUS ───────────────────────────────────────────────────────────────────┐ │
│  │                                                                             │ │
│  │  Status: [Planning                                                     ▼]  │ │
│  │                                                                             │ │
│  └─────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                  │
│  ┌─ VENUE ────────────────────────────────────────────────────────────────────┐ │
│  │                                                                             │ │
│  │  Venue Name: * [Kornhausforum Bern                                    ]    │ │
│  │                                                                             │ │
│  │  Address: *    [Kornhausplatz 18, 3011 Bern                           ]    │ │
│  │                                                                             │ │
│  └─────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                  │
│  ┌─ THEME (OPTIONAL) ─────────────────────────────────────────────────────────┐ │
│  │                                                                             │ │
│  │  Theme:        [Cloud-Native Architecture                             ]    │ │
│  │                                                                             │ │
│  │  ──────────────────────────────────────────────────────────────────────────│ │
│  │                                                                             │ │
│  │  Theme Image                                                               │ │
│  │  Upload a banner image for this event (PNG, JPG, SVG - max 5MB)           │ │
│  │                                                                             │ │
│  │  [Choose File]  No file chosen                                             │ │
│  │                                                                             │ │
│  └─────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                  │
│  ⚠️ Validation errors shown inline below each field                             │
│                                                                                  │
│  [Cancel]                           [Save as Draft]        [Create Event]       │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

**Key Implementation Details** (from EventForm.tsx):
- **Lines 570-603**: Event Type selector implementation
- **Lines 484-514**: Event Number and Title side-by-side
- **Lines 534-567**: Event Date and Registration Deadline side-by-side
- **Lines 570-603**: Event Type and Venue Capacity side-by-side
- **Lines 605-632**: Status dropdown
- **Lines 634-660**: Venue Name and Address
- **Lines 662-668**: Theme text field
- **Lines 670-690**: Theme Image Upload (FileUpload component)

**Actual Material-UI Components Used**:
- `Dialog` with `maxWidth="md"` and `fullWidth`
- `DialogTitle`, `DialogContent`, `DialogActions`
- `TextField` with `type="date"` for date pickers
- `Select` + `MenuItem` for dropdowns (event type, status)
- `FormControl`, `InputLabel`, `FormHelperText` for form fields
- `Alert` for error messages
- `Chip` for auto-save status
- `Box` with `display: 'flex', gap: 2` for side-by-side layouts

### Component: EventTypeSelector

**Material-UI Components**: `Select`, `MenuItem`, `FormControl`, `InputLabel`

**Props**:
```typescript
interface EventTypeSelectorProps {
  value: EventType;
  onChange: (type: EventType) => void;
  disabled?: boolean;
}
```

**Behavior**:
- Displays 3 options: Full Day Event, Afternoon Event, Evening Event
- Each option shows event type name in dropdown
- When user hovers over option, shows brief description
- On selection, triggers SlotTemplatePreview update below

---

## Screen 2: Event Type Selector - Dropdown Expanded (CURRENT IMPLEMENTATION)

**Implementation**: Lines 577-581 in EventForm.tsx show simple MenuItem text values

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  Event Type: [Full Day Event                                          ▼]       │
│              ┌──────────────────────────────────────────────────────────────┐   │
│              │ ✓ Full Day Event                                            │   │
│              ├──────────────────────────────────────────────────────────────┤   │
│              │   Afternoon Event                                            │   │
│              ├──────────────────────────────────────────────────────────────┤   │
│              │   Evening Event                                              │   │
│              └──────────────────────────────────────────────────────────────┘   │
```

**Current Implementation** (lines 577-581):
```tsx
<Select {...field} label={t('form.eventType')}>
  <MenuItem value="full_day">{t('form.eventTypes.fullDay')}</MenuItem>
  <MenuItem value="afternoon">{t('form.eventTypes.afternoon')}</MenuItem>
  <MenuItem value="evening">{t('form.eventTypes.evening')}</MenuItem>
</Select>
```

**Translation Keys** (from i18n):
- `form.eventTypes.fullDay` → "Full Day Event"
- `form.eventTypes.afternoon` → "Afternoon Event"
- `form.eventTypes.evening` → "Evening Event"

**Material-UI Components**: Standard `MenuItem` with text-only content

**Story 5.1 Enhancement Opportunity**:
Add detailed slot information to MenuItem (secondary text) showing:
- Slot count range (e.g., "6-8 slots")
- Duration (e.g., "45 min each")
- Typical timing (e.g., "09:00-17:00")

**Enhanced MenuItem Example** (Story 5.1 implementation):
```tsx
<MenuItem value="full_day">
  <Box>
    <Typography variant="body1">Full Day Event</Typography>
    <Typography variant="caption" color="textSecondary">
      6-8 slots • 45 min each • 09:00-17:00
    </Typography>
  </Box>
</MenuItem>
```

---

## Screen 3: Slot Template Preview Component

**Context**: Shown below event type selector, updates dynamically when event type changes

```
┌─ SLOT CONFIGURATION PREVIEW ─────────────────────────────────────────────────┐
│                                                                               │
│  📊 Afternoon Event                                                          │
│                                                                               │
│  ┌──────────────┬──────────────────┬──────────────┬──────────────────────┐  │
│  │ 🎯 Slots     │ ⏱️ Duration       │ 👥 Capacity  │ 🕐 Timing            │  │
│  ├──────────────┼──────────────────┼──────────────┼──────────────────────┤  │
│  │ 6-8 sessions │ 30 min each      │ 150 people   │ 13:00 - 18:00        │  │
│  └──────────────┴──────────────────┴──────────────┴──────────────────────┘  │
│                                                                               │
│  📅 Example Timeline:                                                        │
│   13:00 - 13:30   Session 1 (Theoretical)                                   │
│   13:30 - 14:00   Session 2 (Theoretical)                                   │
│   14:00 - 14:30   Session 3 (Theoretical)                                   │
│   14:30 - 15:00   ☕ Break                                                    │
│   15:00 - 15:30   Session 4 (Practical)                                     │
│   15:30 - 16:00   Session 5 (Practical)                                     │
│   16:00 - 16:30   Session 6 (Practical)                                     │
│   16:30 - 17:00   Session 7 (Practical) [Optional]                          │
│   17:00 - 17:30   Session 8 (Practical) [Optional]                          │
│   17:30 - 18:00   🤝 Networking                                              │
│                                                                               │
│  ℹ️  Shorter sessions allow more topic diversity                            │
│                                                                               │
└───────────────────────────────────────────────────────────────────────────────┘
```

### Component: SlotTemplatePreview

**Material-UI Components**: `Card`, `CardContent`, `Typography`, `Chip`, `Stack`, `Table`

**Props**:
```typescript
interface SlotTemplatePreviewProps {
  eventType: EventType;
  slotConfiguration: EventSlotConfiguration;
}
```

**Visual Elements**:
- Header with event type icon and name
- 4-column info grid: Slots, Duration, Capacity, Timing
- Example timeline with session breakdown
- Footer info message explaining key characteristics

---

## Screen 4: Event Type Configuration Admin Screen

**Context**: Admin-only screen for configuring event type templates (accessed via System Settings)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│ ← System Settings    Event Type Configuration                    [ADMIN ONLY]   │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  📋 Event Type Templates                                                        │
│                                                                                  │
│  ┌─ FULL DAY EVENT ────────────────────────────────────────────────────────┐   │
│  │                                                                          │   │
│  │  Status: ● Active                                           [Edit] [⚙️] │   │
│  │                                                                          │   │
│  │  ┌──────────────┬─────────────┬──────────────┬─────────────────────┐   │   │
│  │  │ Min Slots    │ Max Slots   │ Duration     │ Default Capacity    │   │   │
│  │  ├──────────────┼─────────────┼──────────────┼─────────────────────┤   │   │
│  │  │ 6            │ 8           │ 45 min       │ 200                 │   │   │
│  │  └──────────────┴─────────────┴──────────────┴─────────────────────┘   │   │
│  │                                                                          │   │
│  │  🕐 Typical Timing: 09:00 - 17:00                                       │   │
│  │  ☕ Break Slots: 2    🍽️ Lunch Slots: 1    🔬 Theoretical AM: ✓ Yes   │   │
│  │                                                                          │   │
│  │  Last Modified: 2025-01-15 by admin@batbern.ch                         │   │
│  │                                                                          │   │
│  └──────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│  ┌─ AFTERNOON EVENT ───────────────────────────────────────────────────────┐   │
│  │                                                                          │   │
│  │  Status: ● Active                                           [Edit] [⚙️] │   │
│  │                                                                          │   │
│  │  ┌──────────────┬─────────────┬──────────────┬─────────────────────┐   │   │
│  │  │ Min Slots    │ Max Slots   │ Duration     │ Default Capacity    │   │   │
│  │  ├──────────────┼─────────────┼──────────────┼─────────────────────┤   │   │
│  │  │ 6            │ 8           │ 30 min       │ 150                 │   │   │
│  │  └──────────────┴─────────────┴──────────────┴─────────────────────┘   │   │
│  │                                                                          │   │
│  │  🕐 Typical Timing: 13:00 - 18:00                                       │   │
│  │  ☕ Break Slots: 1    🍽️ Lunch Slots: 0    🔬 Theoretical AM: ✗ No    │   │
│  │                                                                          │   │
│  │  Last Modified: 2025-01-15 by admin@batbern.ch                         │   │
│  │                                                                          │   │
│  └──────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│  ┌─ EVENING EVENT ─────────────────────────────────────────────────────────┐   │
│  │                                                                          │   │
│  │  Status: ● Active                                           [Edit] [⚙️] │   │
│  │                                                                          │   │
│  │  ┌──────────────┬─────────────┬──────────────┬─────────────────────┐   │   │
│  │  │ Min Slots    │ Max Slots   │ Duration     │ Default Capacity    │   │   │
│  │  ├──────────────┼─────────────┼──────────────┼─────────────────────┤   │   │
│  │  │ 3            │ 4           │ 45 min       │ 100                 │   │   │
│  │  └──────────────┴─────────────┴──────────────┴─────────────────────┘   │   │
│  │                                                                          │   │
│  │  🕐 Typical Timing: 18:00 - 21:00                                       │   │
│  │  ☕ Break Slots: 1    🍽️ Lunch Slots: 0    🔬 Theoretical AM: ✗ No    │   │
│  │                                                                          │   │
│  │  Last Modified: 2025-01-10 by admin@batbern.ch                         │   │
│  │                                                                          │   │
│  └──────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│  ⚠️ Changes to event type templates will affect future events only.             │
│     Existing events retain their original configuration.                        │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

**Access Control**: ORGANIZER role only (admin organizers)

**Material-UI Components**: `Card`, `CardHeader`, `CardContent`, `IconButton`, `Chip`, `Typography`

**Features**:
- Read-only view showing all 3 event types
- Each type displayed in expandable card
- [Edit] button opens modal for configuration
- Warning message about changes affecting future events only

---

## Screen 5: Edit Event Type Configuration Modal

**Context**: Admin clicks [Edit] button on an event type card

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  Edit Event Type Configuration                                            ✕    │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  Event Type: Full Day Event                                                     │
│                                                                                  │
│  ┌─ SLOT CONFIGURATION ───────────────────────────────────────────────────┐    │
│  │                                                                         │    │
│  │  Minimum Slots: [6                    ]  ⓘ Minimum sessions required  │    │
│  │                                                                         │    │
│  │  Maximum Slots: [8                    ]  ⓘ Maximum sessions allowed   │    │
│  │                                                                         │    │
│  │  Slot Duration: [45                   ] minutes                        │    │
│  │                                                                         │    │
│  │  ⚠️ Validation: Min slots (6) must be ≤ Max slots (8)                 │    │
│  │                                                                         │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                  │
│  ┌─ TIMING TEMPLATE ──────────────────────────────────────────────────────┐    │
│  │                                                                         │    │
│  │  Typical Start Time: [09:00   🕐]     Typical End Time: [17:00   🕐]  │    │
│  │                                                                         │    │
│  │  Break Slots: [2                    ]  ⓘ Coffee/networking breaks     │    │
│  │                                                                         │    │
│  │  Lunch Slots: [1                    ]  ⓘ Lunch break periods          │    │
│  │                                                                         │    │
│  │  ☐ Theoretical Presentations in Morning                                │    │
│  │     When enabled, theoretical sessions scheduled before lunch          │    │
│  │                                                                         │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                  │
│  ┌─ CAPACITY PLANNING ────────────────────────────────────────────────────┐    │
│  │                                                                         │    │
│  │  Default Capacity: [200                 ]  ⓘ Default attendee limit   │    │
│  │                                                                         │    │
│  │  Note: Organizers can override capacity when creating events           │    │
│  │                                                                         │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                  │
│  ┌─ PREVIEW ──────────────────────────────────────────────────────────────┐    │
│  │                                                                         │    │
│  │  📅 Example Timeline with Current Settings:                            │    │
│  │   09:00 - 09:45   Session 1 (45 min)                                  │    │
│  │   09:45 - 10:30   Session 2 (45 min)                                  │    │
│  │   10:30 - 11:00   ☕ Break                                              │    │
│  │   11:00 - 11:45   Session 3 (45 min)                                  │    │
│  │   11:45 - 12:30   Session 4 (45 min)                                  │    │
│  │   12:30 - 13:30   🍽️ Lunch                                             │    │
│  │   13:30 - 14:15   Session 5 (45 min)                                  │    │
│  │   14:15 - 15:00   Session 6 (45 min)                                  │    │
│  │   15:00 - 15:30   ☕ Break                                              │    │
│  │   15:30 - 16:15   Session 7 (45 min) [Optional]                       │    │
│  │   16:15 - 17:00   Session 8 (45 min) [Optional]                       │    │
│  │                                                                         │    │
│  │  Total: 6 required + 2 optional = 6-8 slots ✓                         │    │
│  │                                                                         │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                  │
│  ⚠️ Warning: Changes will apply to future events only.                          │
│              54 existing events use the current configuration.                  │
│                                                                                  │
│  [Cancel]                                                 [Save Changes]        │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Component: EventTypeConfigurationForm

**Material-UI Components**: `Dialog`, `DialogTitle`, `DialogContent`, `DialogActions`, `TextField`, `Switch`, `Button`, `Alert`

**Props**:
```typescript
interface EventTypeConfigurationFormProps {
  eventType: EventType;
  initialConfig?: EventSlotConfiguration;
  open: boolean;
  onClose: () => void;
  onSave: (config: EventSlotConfiguration) => Promise<void>;
}
```

**Validation Rules**:
- Min Slots > 0
- Max Slots ≥ Min Slots
- Slot Duration ≥ 15 minutes
- Default Capacity > 0
- Start Time < End Time

**Behavior**:
- Real-time preview updates as user changes values
- Validation errors shown inline with red text
- [Save Changes] disabled until all validation passes
- Warning shows count of existing events using current config

---

## Responsive Behavior

### Mobile (< 768px)
```
┌─────────────────────────────┐
│  Event Type                 │
│  [Full Day Event        ▼]  │
│                             │
│  ┌─ PREVIEW ───────────────┐│
│  │ 📊 Full Day Event       ││
│  │                         ││
│  │ 🎯 6-8 slots            ││
│  │ ⏱️ 45 min each          ││
│  │ 👥 200 capacity         ││
│  │ 🕐 09:00-17:00          ││
│  │                         ││
│  │ [View Timeline]         ││
│  └─────────────────────────┘│
└─────────────────────────────┘
```

- Preview cards stack vertically
- Timeline collapsed behind [View Timeline] button
- Configuration form becomes full-screen modal

### Tablet (768px - 1024px)
- Two-column layout: Selector left, Preview right
- Timeline visible but scrollable
- Configuration form as centered modal

### Desktop (> 1024px)
- Side-by-side layout as shown in wireframes
- Full timeline visible without scrolling
- Configuration form as wide modal

---

## Accessibility (WCAG 2.1 AA)

**Keyboard Navigation**:
- Tab through: Event Type dropdown → each form field → buttons
- Enter/Space: Opens dropdown, activates buttons
- Arrow keys: Navigate dropdown options
- Escape: Closes dropdown or modal

**Screen Reader**:
- `aria-label` on event type selector: "Select event type format"
- `aria-describedby` linking to preview section
- Live region announces when preview updates
- Form fields have proper labels and error messages

**Visual**:
- Color contrast ratio ≥ 4.5:1 for all text
- Focus indicators visible on all interactive elements
- Icons supplemented with text labels
- Error messages not conveyed by color alone

---

## State Management

**React Query Hooks**:
```typescript
// Fetch all event types (cached 1 hour)
const { data: eventTypes } = useEventTypes();

// Fetch specific event type
const { data: eventType } = useEventType(EventType.FULL_DAY);

// Update event type configuration (ORGANIZER only)
const updateMutation = useUpdateEventType();
```

**Local State** (Zustand):
```typescript
interface EventCreationStore {
  selectedEventType: EventType;
  setEventType: (type: EventType) => void;
  slotConfiguration: EventSlotConfiguration | null;
}
```

---

## API Integration

**GET /api/events/types** - List all event types
- Used by: EventTypeSelector on load
- Caching: 1 hour (types rarely change)
- Response: Array of EventSlotConfiguration

**GET /api/events/types/{type}** - Get specific type
- Used by: SlotTemplatePreview for detailed info
- Caching: 1 hour
- Response: EventSlotConfiguration

**PUT /api/events/types/{type}** - Update configuration
- Used by: EventTypeConfigurationForm on save
- Auth: ORGANIZER role required
- Cache invalidation: Clears eventTypes cache
- Response: Updated EventSlotConfiguration

---

## Component Hierarchy

```
EventCreationForm
├── EventTypeSelector
│   ├── FormControl (Material-UI)
│   ├── Select (Material-UI)
│   └── MenuItem (Material-UI) x3
│
└── SlotTemplatePreview
    ├── Card (Material-UI)
    ├── CardContent
    │   ├── Typography (header)
    │   ├── Stack (info chips)
    │   │   └── Chip x4
    │   ├── Divider
    │   ├── Typography (timeline header)
    │   ├── List (timeline items)
    │   │   └── ListItem x10
    │   └── Alert (info message)
    └── CardActions (optional)

EventTypeConfigurationAdmin
├── Card x3 (one per event type)
│   ├── CardHeader
│   ├── CardContent
│   └── CardActions
│       └── IconButton [Edit]
│
└── EventTypeConfigurationModal
    ├── Dialog (Material-UI)
    ├── DialogTitle
    ├── DialogContent
    │   ├── TextField x6 (form fields)
    │   ├── Switch x1 (theoretical AM)
    │   └── SlotTemplatePreview (live preview)
    └── DialogActions
        ├── Button [Cancel]
        └── Button [Save Changes]
```

---

## Testing Scenarios

**EventTypeSelector Tests**:
- ✓ Displays 3 event type options
- ✓ Shows slot details in each option
- ✓ Calls onChange when type selected
- ✓ Disables when disabled prop is true
- ✓ Pre-selects current value

**SlotTemplatePreview Tests**:
- ✓ Displays correct info for each event type
- ✓ Shows timeline with correct slot count
- ✓ Updates when eventType prop changes
- ✓ Calculates correct timing based on duration
- ✓ Shows optional slots in gray

**EventTypeConfigurationForm Tests**:
- ✓ Validates min slots ≤ max slots
- ✓ Prevents saving with invalid data
- ✓ Updates preview in real-time
- ✓ Shows warning about existing events
- ✓ Calls onSave with correct data

**E2E Test**:
- ✓ Organizer creates event and selects type
- ✓ Preview updates to match selection
- ✓ Event saves with correct slot configuration
- ✓ Admin edits event type configuration
- ✓ Changes apply to new events only

---

## Implementation Notes

1. **Event Type Selector** goes in Event Creation/Edit form (Story 1.16)
2. **Slot Template Preview** appears immediately below selector
3. **Admin Configuration** accessible via System Settings → Event Types
4. All components use Material-UI for consistency
5. Real-time preview uses React state (no API calls during editing)
6. Configuration changes invalidate React Query cache
7. Cache strategy: Aggressive (1 hour) because event types rarely change
