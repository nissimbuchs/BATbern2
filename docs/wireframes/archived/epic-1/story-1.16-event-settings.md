# Event Settings Screen

## Header Information

### Story
Epic 1, Story 1.16 - Event Management Service Core / Epic 4, Story 4.3 - Full Progressive Publishing

### Screen
Event Settings Configuration

### User Role
Organizer

### Related FR
FR2 (Event workflow management), FR5 (Progressive publishing), FR7 (Email notifications), FR19 (Progressive publishing engine with validation), FR20 (Intelligent notification system)

## Visual Wireframe

```
┌────────────────────────────────────────────────────────────────────────┐
│ ← Back to Event Details    Event Settings              [Save Changes]  │
├────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  Event: BATbern #55 - Cloud Native Architecture                        │
│  Status: Planning  │  Date: 2025-06-15                                 │
│                                                                         │
│  ┌─── TABS ────────────────────────────────────────────────────────┐  │
│  │ ● Publishing  │  Registration  │  Notifications  │  Access      │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌─── PUBLISHING CONFIGURATION ─────────────────────────────────────┐  │
│  │                                                                    │  │
│  │  Publishing Phases                                                │  │
│  │  ┌────────────────────────────────────────────────────────────┐  │  │
│  │  │ Phase 1: Topic Announcement         Status: ● Completed     │  │  │
│  │  │ Timing: Immediate (auto-triggered)                          │  │  │
│  │  │ Content: Event title, date, topics                          │  │  │
│  │  │ Newsletter: ☑ Send topic announcement                       │  │  │
│  │  │ Template: [Topic Announcement Email    ▼] [Preview]        │  │  │
│  │  └────────────────────────────────────────────────────────────┘  │  │
│  │                                                                    │  │
│  │  ┌────────────────────────────────────────────────────────────┐  │  │
│  │  │ Phase 2: Speaker Reveal             Status: ○ Pending       │  │  │
│  │  │ Timing: ○ Auto (4 weeks before)  ● Manual trigger          │  │  │
│  │  │ Schedule: [2025-05-18 10:00]                                │  │  │
│  │  │ Content: Speakers, bios, session titles                     │  │  │
│  │  │ Newsletter: ☑ Send speaker announcement                     │  │  │
│  │  │ Template: [Speaker Announcement       ▼] [Preview]         │  │  │
│  │  │ Target: ☑ Speakers ☑ Attendees ☑ Partners ☐ Organizers    │  │  │
│  │  └────────────────────────────────────────────────────────────┘  │  │
│  │                                                                    │  │
│  │  ┌────────────────────────────────────────────────────────────┐  │  │
│  │  │ Phase 3: Final Agenda Lock          Status: ○ Scheduled     │  │  │
│  │  │ Timing: ● Auto (2 weeks before, enforced)                   │  │  │
│  │  │ Lock Date: 2025-06-01                                       │  │  │
│  │  │ Content: Complete agenda, logistics, registration          │  │  │
│  │  │ Newsletter: ☑ Send final agenda                            │  │  │
│  │  │ Template: [Final Agenda Email         ▼] [Preview]         │  │  │
│  │  │ ⚠ After lock: Emergency override requires approval         │  │  │
│  │  └────────────────────────────────────────────────────────────┘  │  │
│  │                                                                    │  │
│  │  ┌────────────────────────────────────────────────────────────┐  │  │
│  │  │ Phase 4: Post-Event Materials       Status: ○ Not Started   │  │  │
│  │  │ Timing: ● Manual (after event completion)                   │  │  │
│  │  │ Content: Presentations, photos, recordings                  │  │  │
│  │  │ Newsletter: ☑ Send post-event materials                     │  │  │
│  │  │ Template: [Post-Event Materials       ▼] [Preview]         │  │  │
│  │  └────────────────────────────────────────────────────────────┘  │  │
│  │                                                                    │  │
│  │  Content Validation Rules                                         │  │
│  │  ☑ Require all speaker photos before Phase 2                     │  │
│  │  ☑ Require session times assigned before Phase 3                 │  │
│  │  ☑ Require venue details confirmed before Phase 3                │  │
│  │  ☐ Require abstracts reviewed before Phase 2                     │  │
│  │                                                                    │  │
│  │  [Manage Email Templates]  [View Publishing History]             │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌─── REGISTRATION TAB (Hidden) ───────────────────────────────────┐  │
│  │ Capacity Settings                                                 │  │
│  │ Maximum Attendees: [200]                                          │  │
│  │ Registration Opens: [2025-04-01 09:00]                            │  │
│  │ Registration Closes: [2025-06-10 23:59]                           │  │
│  │                                                                    │  │
│  │ Access Control                                                     │  │
│  │ ○ Public (anyone can register)                                    │  │
│  │ ● Invite Only (requires invitation code)                          │  │
│  │ ○ Approval Required (organizer approval needed)                   │  │
│  │                                                                    │  │
│  │ Pricing                                                            │  │
│  │ ☐ Free Event                                                      │  │
│  │ ☑ Paid Event: CHF [150.00]                                        │  │
│  │ ☑ Early Bird: CHF [120.00] until [2025-04-30]                    │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌─── NOTIFICATIONS TAB (Hidden) ──────────────────────────────────┐  │
│  │ Notification Rules                                                │  │
│  │                                                                    │  │
│  │ Workflow Notifications                                            │  │
│  │ ☑ Notify organizers on workflow state change                      │  │
│  │ ☑ Notify speakers on invitation status change                     │  │
│  │ ☑ Alert moderator when content needs review                       │  │
│  │                                                                    │  │
│  │ Deadline Alerts                                                    │  │
│  │ ☑ 1 week before speaker material deadline                         │  │
│  │ ☑ 3 days before final agenda lock                                 │  │
│  │ ☑ 24 hours before event start                                     │  │
│  │                                                                    │  │
│  │ Escalation Rules                                                   │  │
│  │ ☑ Escalate to moderator if no response in 72 hours               │  │
│  │ ☑ Escalate to organizers if deadline at risk (2 week warning)    │  │
│  │                                                                    │  │
│  │ [Configure Custom Rules]                                           │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌─── ACCESS PERMISSIONS TAB (Hidden) ─────────────────────────────┐  │
│  │ Event Access Control                                              │  │
│  │                                                                    │  │
│  │ Moderator Assignment                                               │  │
│  │ Current Moderator: [Select Organizer ▼]                          │  │
│  │ Selected: Maria Schmidt (maria@example.com)                       │  │
│  │                                                                    │  │
│  │ Co-Organizer Access                                                │  │
│  │ ☑ All organizers can edit event                                   │  │
│  │ ☐ Restrict editing to event creator and moderator                │  │
│  │                                                                    │  │
│  │ Speaker Permissions                                                │  │
│  │ ☑ Speakers can edit own session details                           │  │
│  │ ☑ Speakers can upload materials directly                          │  │
│  │ ☐ Require moderator approval for all speaker changes             │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│                                    [Cancel]  [Save Changes]            │
└────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Tab Navigation**: Switch between Publishing, Registration, Notifications, and Access settings
- **Publishing Phase Cards**: Expandable cards for each publishing phase with timing and content configuration
- **Newsletter Template Selector**: Dropdown to select email template with preview button
- **Template Preview Button**: Opens modal to preview email template with sample data
- **Phase Timing Controls**: Radio buttons for auto/manual trigger with date/time picker
- **Validation Checkboxes**: Toggle content validation rules for publishing phases
- **Registration Access Control**: Radio buttons to select registration access level (Public/Invite Only/Approval Required)
- **Notification Rule Toggles**: Checkboxes to enable/disable specific notification rules
- **Moderator Assignment Dropdown**: Searchable dropdown to assign event moderator
- **Save Changes Button**: Validates and saves all event settings (primary action)
- **Manage Email Templates Button**: Opens template management interface (secondary navigation)

## Functional Requirements Met

- **FR2 (Event Workflow Management)**: Configure event settings that control workflow automation and progression
- **FR5 (Progressive Publishing)**: Configure 4-phase progressive publishing with automated timing and content rules
- **FR7 (Email Notifications)**: Configure newsletter templates and distribution for each publishing phase
- **FR19 (Progressive Publishing Engine)**: Set up content validation rules and quality review requirements before publishing
- **FR20 (Intelligent Notification System)**: Configure role-based notification rules, deadline alerts, and escalation workflows

## Technical Notes

- **State Management**: Use Zustand store for event settings state with optimistic updates
- **Form Validation**: React Hook Form with Zod schema validation for all settings
- **API Caching**: React Query with 5-minute stale time for event settings, invalidate on save
- **Tab State**: Persist active tab in URL query parameter for deep linking (e.g., `?tab=publishing`)
- **Real-time Updates**: WebSocket connection for collaborative editing notifications (show when another organizer is editing)
- **Template Preview**: Server-side rendering of email templates with event data substitution
- **Date/Time Picker**: Use Material-UI DateTimePicker with timezone support (Europe/Zurich default)
- **Permission Checks**: Verify user is organizer and has edit permissions before rendering edit controls
- **Unsaved Changes Warning**: Browser prompt when navigating away with unsaved changes

## API Requirements

### Initial Page Load APIs

**CONSOLIDATED API APPROACH (Story 1.17):**

1. **GET /api/v1/events/{eventId}?include=publishing,workflow,team,notifications**
   - Returns: Complete event data with settings-related sub-resources in a single call
   - Response includes:
     - Event core data: id, title, eventDate, status
     - publishing: Publishing configuration (strategy, phases, templates, history, validation rules)
     - workflow: Current workflow state (for publishing readiness context)
     - team: Team assignments (moderator, organizers)
     - notifications: Notification rules and automation settings
   - Used for: Populate all event settings form fields across all tabs
   - **Performance**: Reduced from 4 API calls to 1 (75% reduction in HTTP requests)

2. **GET /api/v1/notifications/templates**
   - Query params: type=newsletter, eventId={eventId}
   - Returns: List of available email templates with id, name, description, variables
   - Used for: Populating template dropdown selectors in publishing phase configuration

3. **GET /api/v1/users?role=ORGANIZER&eventId={eventId}**
   - Query params: role=ORGANIZER, eventId for event-specific organizers
   - Returns: List of organizers eligible to be moderator with id, name, email
   - Used for: Populating moderator assignment dropdown

---

**MIGRATION NOTE (Story 1.17):**
The original implementation required 4 separate API calls on page load:
- Event settings
- Publishing history
- (Implicit) Workflow state
- (Implicit) Team assignments

The new consolidated API includes all settings-related data via the `?include=publishing,workflow,team,notifications` parameter. This provides:
- Page load time: ~70% faster (from ~1.2s to <350ms)
- Single loading state for entire settings screen
- Atomic data consistency across all tabs
- Reduced complexity in form initialization
- Better caching efficiency

### User Action APIs

1. **PUT /api/v1/events/{eventId}/settings**
   - Triggered by: [Save Changes] button click
   - Payload: Complete event settings object (only changed fields)
   - Response: Updated event settings with validation results
   - Used for: Persisting all settings changes across all tabs

2. **PUT /api/v1/events/{eventId}/publishing-config**
   - Triggered by: Changes to publishing phase configuration
   - Payload: Publishing phase settings (timing, templates, validation rules)
   - Response: Updated publishing configuration with next scheduled phase
   - Used for: Updating progressive publishing settings independently

3. **PUT /api/v1/events/{eventId}/registration-settings**
   - Triggered by: Changes to registration settings
   - Payload: Registration configuration (capacity, timing, access control, pricing)
   - Response: Updated registration settings with validation results
   - Used for: Updating registration configuration independently

4. **POST /api/v1/events/{eventId}/publishing-phases/{phaseId}/preview**
   - Triggered by: [Preview] button click on email template
   - Payload: { templateId, eventId, recipientRole }
   - Response: Rendered HTML email preview with event data substituted
   - Used for: Opening template preview modal with actual event data

5. **GET /api/v1/notifications/templates/{templateId}/preview**
   - Triggered by: [Preview] button in template selector
   - Query params: templateId, eventId (for data substitution)
   - Returns: HTML preview of email template with event data
   - Used for: Displaying email template preview in modal

6. **POST /api/v1/events/{eventId}/moderator**
   - Triggered by: Moderator assignment change in Access tab
   - Payload: { moderatorId, previousModeratorId }
   - Response: Updated moderator assignment with notification sent status
   - Used for: Assigning event moderator and triggering notification

## Navigation Map

### Primary Navigation Actions

1. **← Back to Event Details** → Navigate to `Event Detail/Edit Screen`
   - Target: story-1.16-event-detail-edit.md
   - Context: Returns to parent event management screen
   - Behavior: Warns if unsaved changes exist

2. **[Manage Email Templates]** → Navigate to `Email Template Management Screen`
   - Target: External template management interface (organization-wide)
   - Context: Opens in modal or new tab for template creation/editing
   - Navigation: Modal overlay with full template CRUD

3. **[View Publishing History]** → Navigate to `Publishing History Screen`
   - Target: Modal or side panel showing publishing audit log
   - Context: Display timeline of all publishing actions for this event
   - Navigation: Modal overlay with filterable history

### Secondary Navigation (Data Interactions)

1. **Template [Preview] button** → Opens `Email Template Preview Modal`
   - Target: Modal displaying rendered email with event data
   - Context: Preview specific email template before assignment
   - Navigation: Modal overlay, close returns to settings

2. **Tab Navigation** → Switch active settings tab
   - Target: Publishing / Registration / Notifications / Access tabs (inline)
   - Context: URL parameter updates (e.g., ?tab=notifications)
   - Navigation: Inline content swap, no page reload

### Event-Driven Navigation

1. **Save Success** → Show success toast notification
   - Target: Remains on Event Settings screen
   - Context: Display "Settings saved successfully" confirmation
   - Navigation: No navigation, toast dismisses after 3 seconds

2. **Save with Validation Errors** → Highlight error fields
   - Target: Remains on Event Settings screen, focus error tab
   - Context: Display inline validation errors, switch to tab with errors
   - Navigation: No navigation, scroll to first error field

### Error States & Redirects

1. **Unauthorized Access** → Redirect to `Event Detail/Edit Screen` (read-only)
   - Target: story-1.16-event-detail-edit.md
   - Context: User lacks edit permissions for event settings
   - Navigation: Full page redirect with error toast "You don't have permission to edit event settings"

2. **Event Not Found** → Redirect to `Event Management Dashboard`
   - Target: story-1.16-event-management-dashboard.md
   - Context: Event ID invalid or deleted
   - Navigation: Full page redirect with error toast "Event not found"

3. **Publishing Phase Conflict** → Show conflict resolution modal
   - Target: Modal with conflict details
   - Context: Another organizer saved conflicting publishing settings
   - Navigation: Modal overlay with options to reload or override

## Responsive Design Considerations

### Mobile Layout Changes

- **Tab Navigation**: Converts to vertical accordion on mobile (<768px)
  - Each tab becomes collapsible section with expand/collapse icons
  - Active section expands, others collapse by default
- **Publishing Phase Cards**: Stack vertically with full width
  - Newsletter template selector becomes full-width dropdown
  - Preview button moves below template selector
- **Form Fields**: Full width on mobile, side-by-side on tablet+
  - Date/time pickers adapt to mobile-friendly modal pickers
- **Action Buttons**: Fixed bottom bar on mobile with [Cancel] and [Save Changes]

### Tablet Layout Changes

- **Tab Navigation**: Horizontal tabs maintained but with reduced padding
- **Form Layout**: 2-column grid for related fields (e.g., registration dates)
- **Publishing Phases**: Cards remain expanded with slightly reduced padding

### Mobile-Specific Interactions

- **Swipe Gestures**: Swipe left/right to navigate between tabs
- **Touch Targets**: Minimum 44x44px for all interactive elements
- **Sticky Headers**: Publishing phase titles stick to top when scrolling
- **Collapse/Expand**: All publishing phase cards collapsible on mobile for space

## Accessibility Notes

- **ARIA Labels**: All form inputs have descriptive aria-label attributes
- **Keyboard Navigation**: Full keyboard support with logical tab order
  - Tab: Navigate between form fields and buttons
  - Enter: Submit form or activate buttons
  - Escape: Close modals or cancel unsaved changes
- **Screen Reader Support**:
  - Announce tab changes "Now viewing Publishing settings"
  - Announce save success/error states
  - Describe publishing phase status changes
- **Focus Indicators**: Clear 2px solid blue outline on focused elements
- **Color Contrast**: All text meets WCAG 2.1 AA standards (4.5:1 minimum)
- **Error Announcement**: Screen readers announce validation errors immediately
- **Required Fields**: Marked with aria-required and visual asterisk (*)

## State Management

### Local Component State

- **activeTab**: Currently selected tab (Publishing/Registration/Notifications/Access)
- **formData**: Form field values across all tabs (managed by React Hook Form)
- **unsavedChanges**: Boolean tracking if form has modifications
- **templatePreview**: Currently previewing template data (templateId, HTML content)
- **validationErrors**: Map of field names to error messages

### Global State (Zustand Store)

- **eventSettings**: Complete event settings object shared across components
- **availableTemplates**: List of email templates (cached, 30min TTL)
- **currentUser**: Current organizer's profile and permissions
- **editingUsers**: List of other organizers currently editing this event (WebSocket)

### Server State (React Query)

- **eventSettingsQuery**: GET /api/v1/events/{id}/settings
  - Cache key: ['eventSettings', eventId]
  - Stale time: 5 minutes
  - Refetch on window focus
- **templatesQuery**: GET /api/v1/notifications/templates
  - Cache key: ['emailTemplates', 'newsletter']
  - Stale time: 30 minutes
  - Background refetch
- **organizersQuery**: GET /api/v1/users?role=ORGANIZER
  - Cache key: ['organizers', eventId]
  - Stale time: 10 minutes

### Real-Time Updates

- **WebSocket Connection**: Connect to /ws/events/{eventId}/settings
  - Listen for 'settings.updated' events from other organizers
  - Listen for 'settings.editing' presence updates
  - Emit 'settings.editing' on field focus
  - Show notification when conflict detected: "Another organizer updated these settings. Reload to see changes."

## Form Validation Rules

### Field-Level Validations

- **Maximum Attendees**: Required, integer, min: 1, max: 10000
- **Registration Opens**: Required, date-time, must be before Registration Closes
- **Registration Closes**: Required, date-time, must be before Event Date, must be after Registration Opens
- **Pricing Amount**: Number, min: 0, max: 9999.99, format: CHF currency
- **Early Bird Until**: Date, must be before Registration Closes if Early Bird enabled
- **Phase Schedule Date**: Date-time, must be before Event Date
- **Email Template**: Required for phases with Newsletter enabled
- **Moderator Selection**: Required, must be valid organizer user ID

### Form-Level Validations

- **Publishing Phase Sequence**: Phase 2 date must be after Phase 1, Phase 3 after Phase 2
- **Early Bird Logic**: If Early Bird enabled, must have valid price < Regular Price and valid end date
- **Newsletter Recipients**: At least one target audience must be selected when newsletter enabled
- **Content Validation Rules**: If "Require speaker photos" enabled, cannot enable Phase 2 until all speakers have photos (checked on save)
- **Moderator Availability**: Selected moderator must be active organizer for this event

## Edge Cases & Error Handling

### Edge Cases

- **Empty State - No Email Templates**: Show "No email templates available. [Create Template]" message in template dropdowns
- **Loading State**: Display skeleton screens during initial data fetch with shimmer effect
- **Error State - Load Failure**: Show error message with [Retry] button: "Failed to load event settings. Please try again."
- **Permission Denied**: If user loses organizer role during editing, show read-only view with message "You no longer have permission to edit these settings"
- **Event Locked**: If event workflow state prevents settings changes (e.g., event completed), show all fields disabled with info banner
- **Conflict Resolution**: If another organizer saves conflicting settings, show dialog:
  - "Another organizer updated these settings."
  - Options: [Reload] (discard local changes) or [Review Conflicts] (show diff view)

### Error Handling

- **Save Failure**: Display error toast with specific message (e.g., "Failed to save publishing settings. Please try again.")
- **Validation Errors**: Highlight invalid fields in red, show inline error messages, focus first error
- **Network Timeout**: Show "Connection timeout. Check your internet and try again." with [Retry] button
- **Optimistic Update Failure**: Revert UI to previous state, show error notification
- **Template Preview Failure**: Show error in modal: "Failed to load template preview" with [Close] button
- **WebSocket Disconnect**: Show warning banner: "Lost connection to server. Reconnecting..." (auto-retry every 5s)
- **Concurrent Edit Conflict**: Show modal with conflict resolution options (reload or override with warning)

## Change Log

| Date       | Version | Description                              | Author     |
|------------|---------|------------------------------------------|------------|
| 2025-10-04 | 1.0     | Initial wireframe creation for Event Settings | UX Expert  |

## Review Notes

### Stakeholder Feedback

- **Pending Review**: Awaiting organizer feedback on publishing phase configuration complexity
- **Consideration**: May need to simplify newsletter template selection for non-technical organizers
- **Future Enhancement**: Consider adding A/B testing controls for email subject lines

### Design Iterations

- **v1.0**: Initial design with 4 tabs and progressive publishing phase configuration
- **Planned**: Add visual timeline diagram for publishing phases in future iteration

### Open Questions

1. **Template Management**: Should template creation be inline or separate admin interface?
   - Decision: Separate interface linked via [Manage Email Templates] button (organization-wide templates)

2. **Moderator Assignment**: Should moderator be assigned per event or per publishing phase?
   - Decision: Per event (one moderator responsible for entire event quality review)

3. **Notification Preferences**: Should speakers/attendees be able to opt-out of specific event notifications?
   - Decision: Global notification preferences in user settings, event settings only control organizer-side rules

4. **Publishing Phase Automation**: Should phases auto-advance or require manual confirmation?
   - Decision: Hybrid approach - Phase 1 auto, Phase 2 configurable, Phase 3 auto with lock, Phase 4 manual
