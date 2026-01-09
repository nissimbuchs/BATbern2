# Workflow Troubleshooting

## Overview

This guide helps resolve issues with the BATbern workflow system, including state transitions, validation errors, and blocked progression. BATbern uses **three independent workflow systems**:

1. **Event Workflow** - 9-state lifecycle (CREATED → TOPIC_SELECTION → SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT → AGENDA_PUBLISHED → AGENDA_FINALIZED → EVENT_LIVE → EVENT_COMPLETED → ARCHIVED)
2. **Speaker Workflow** - Per-speaker states (identified → contacted → ready → accepted → content_submitted → quality_reviewed → confirmed)
3. **Task System** - Configurable tasks triggered by event state transitions

See [Workflow System Documentation](../workflow/) for complete workflow details.

## Quick Diagnosis

```
Can you view the workflow?
│
├─ NO → [Access/Permission Issues](#cant-access-workflow)
│
└─ YES → Can you transition event state?
    │
    ├─ NO (button disabled) → [Validation Errors](#validation-errors-blocking-state-transitions)
    ├─ NO (button missing) → [Permission Issues](#insufficient-permissions)
    │
    └─ YES → Does state transition succeed?
        │
        ├─ NO (error message) → [Transition Failed](#transition-failed-errors)
        ├─ NO (no response) → [System Issue](#system-not-responding)
        │
        └─ YES → Data showing correctly?
            │
            ├─ NO → [Data Sync Issues](#data-not-updating)
            └─ YES → All good!
```

---

## Access and Permission Issues

### Can't Access Workflow

**Symptom**: "Access Denied" or "Workflow not found" error when trying to view event workflow

**Possible Causes**:
1. Not assigned as organizer for this event
2. Insufficient role permissions
3. Event archived or deleted
4. Wrong event ID in URL

**Solutions**:

**Verify Event Assignment**:
```
1. Go to Dashboard → Events
2. Check if event appears in "My Events" list
3. If not listed:
   - Contact admin to add you as organizer
   - Or ask current organizer to delegate
```

**Check Role Permissions**:
```
1. Go to Settings → Profile → View Role
2. Verify you have ORGANIZER role assigned to this event
3. If wrong role, contact admin to update
```

**Verify Event Exists**:
```
1. Go to Dashboard → Events → All Events
2. Search for event by number or name
3. If not found:
   - Event may be archived (click "Show Archived")
   - Event may be deleted (contact admin to restore)
   - You may have wrong event number
```

---

### Insufficient Permissions

**Symptom**: Can view workflow but state transition buttons missing or disabled with "Permission denied" tooltip

**Cause**: You don't have edit permission for this event

**Who Can Manage Workflow**:
- **ORGANIZER** role → Can manage only events where they're assigned
- **SPEAKER** or **ATTENDEE** → Cannot manage workflow (read-only access)

**Solutions**:

**If You're an Organizer (But Not Assigned)**:
```
1. Contact event's primary organizer
2. Request they add you as co-organizer:
   - Event → Settings → Organizers → Add Organizer
3. After being added, refresh page
4. "Advance" button should now appear
```

**If You Need to Be Promoted**:
```
1. Email admin with justification
2. Include:
   - Your current role
   - Events you need access to
   - Why you need workflow edit permission
3. Admin will review and update role if appropriate
```

---

## Validation Errors

### Validation Errors Blocking State Transitions

**Symptom**: State transition button disabled with red error messages above

**Cause**: Required criteria for current state not met

**Common Validations by Event State**:

#### CREATED → TOPIC_SELECTION
```
Validation: Must have selected event topic(s)
Error: "At least one topic must be selected"

Solution:
1. Go to Phase A → Topic Selection
2. Click "View Topic Heat Map" to review historical coverage
3. Select 1-4 topics for the event
4. Click "Save Topic Selection"
5. Event automatically transitions to TOPIC_SELECTION state
```

#### TOPIC_SELECTION → SPEAKER_IDENTIFICATION
```
Validation: Must have identified speaker candidates (minimum threshold)
Error: "Minimum 8 speaker candidates required (current: 5)"

Solution:
1. Go to Phase A → Speaker Brainstorming
2. Add more speaker candidates:
   - Search existing speaker database
   - Or add new speaker profiles
3. Each speaker added starts in "identified" state
4. Once threshold reached, event can transition to SPEAKER_IDENTIFICATION

Tip: Threshold is configurable per event type (default: 10 for full-day, 6 for afternoon, 4 for evening)
```

#### SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT
```
Validation: Minimum confirmed speakers with quality-reviewed content
Error: "Insufficient confirmed speakers: 7/10 required"

Solution:
1. Review speaker workflow progress in Phase B Kanban board
2. For each speaker:
   - Contact speakers: identified → contacted → accepted
   - Collect content: accepted → content_submitted
   - Review quality (Phase C): content_submitted → quality_reviewed
3. Speakers auto-confirm when quality_reviewed AND session.startTime exists
4. Once minimum confirmed speakers reached, can transition to SLOT_ASSIGNMENT

Note: Minimum is 10 confirmed speakers for full-day events, 6 for afternoon, 4 for evening
```

#### SLOT_ASSIGNMENT → AGENDA_PUBLISHED
```
Validation: All confirmed speakers assigned to time slots
Error: "4 confirmed speakers not assigned to slots"

Solution:
1. Go to Phase D → Slot Assignment
2. Drag confirmed speakers to available time slots
3. Resolve any conflicts (double-booking, speaker unavailability)
4. System auto-saves slot assignments
5. Click "Publish Agenda" when all confirmed speakers assigned
6. Event transitions to AGENDA_PUBLISHED state
```

#### Speaker-Specific Validations
```
Validation: Speaker cannot reach "confirmed" state
Error: Speaker stuck at "quality_reviewed" or has slot but not confirmed

Cause: Confirmation requires BOTH conditions:
- Speaker status = quality_reviewed
- Session has startTime (slot assigned)

Solution:
1. If quality_reviewed but no slot:
   - Go to Phase D → Slot Assignment
   - Assign speaker to time slot
   - Speaker auto-confirms when slot saved

2. If has slot but not quality_reviewed:
   - Go to Phase C → Quality Review
   - Review and approve speaker content
   - Speaker auto-confirms when approved

Note: Order doesn't matter - whichever completes second triggers auto-confirmation
```

---

### Validation Error: "Required field missing"

**Symptom**: Generic validation error without specific details

**Common Missing Fields by Event State**:

| Event State | Common Missing Fields |
|-------------|-----------------------|
| CREATED | Event type, date, venue, capacity |
| TOPIC_SELECTION | At least one topic selected |
| SPEAKER_IDENTIFICATION | Minimum speaker candidates identified |
| SLOT_ASSIGNMENT | All confirmed speakers assigned to slots |
| AGENDA_PUBLISHED | Event description (public-facing), all speaker bios complete |

**Solutions**:

**Identify Missing Field**:
```
1. Review validation error message carefully
2. Look for red border or asterisk (*) on fields
3. Red error text usually indicates specific issue
4. Hover over disabled transition button for hint
```

**Complete Required Fields**:
```
1. Fill in highlighted fields or complete required actions
2. Click "Save" or "Update" button if editing entity
3. Verify red errors disappear
4. State transition button should enable
```

**If Still Can't Identify Missing Field**:
```
1. Open browser console (F12 → Console)
2. Click state transition button
3. Console may show detailed validation errors
4. Screenshot and send to support if unclear
```

---

## Transition Failures

### Transition Failed Errors

**Symptom**: Click state transition button, loading spinner, then error "Failed to transition event state" or similar

**Possible Causes**:
1. Concurrent edit conflict (someone else modified event)
2. Database transaction failure
3. Validation changed since page loaded
4. Network timeout during save

**Solutions**:

**Immediate Retry**:
```
1. Click state transition button again (may be temporary glitch)
2. If error persists, proceed to next step
```

**Refresh and Retry**:
```
1. Refresh page (F5 or Ctrl+R)
2. Verify all your changes are still saved
3. Review validation errors (may have changed)
4. Try transition again
```

**Check for Concurrent Edits**:
```
If error mentions "conflict" or "modified":
1. Someone else edited event while you were working
2. Refresh page to get latest state
3. Re-apply your changes if needed
4. Advance again

Tip: Platform shows "Currently editing: [Name]" badge at top if others are working on same event
```

**Network Issue**:
```
If error mentions "timeout" or "network":
1. Check internet connection
2. Verify status page: https://status.batbern.ch
3. Wait 1 minute and retry
4. If persists, contact support
```

---

### System Not Responding

**Symptom**: Click state transition button, spinner shows indefinitely, no error or success

**Possible Causes**:
1. Network timeout (slow connection)
2. Backend processing taking longer than expected
3. Browser frozen
4. JavaScript error blocking UI update

**Solutions**:

**Wait and Monitor**:
```
1. Wait up to 2 minutes (complex transitions may take time)
2. Watch browser status bar (bottom-left) for network activity
3. Check browser console (F12 → Console) for errors
```

**Cancel and Retry**:
```
1. Refresh page (do NOT click transition button again while spinning)
2. Check if state transitioned despite no confirmation
3. If still at same state, click transition button again
```

**Browser Issue**:
```
1. Check if browser frozen (can you switch tabs?)
2. If frozen:
   - Force close browser (Task Manager on Windows, Force Quit on Mac)
   - Reopen and log back in
   - Platform auto-saves, so progress should be preserved
3. If not frozen but stuck:
   - Open browser console (F12)
   - Look for JavaScript errors
   - Screenshot and send to support
```

---

## Data and Sync Issues

### Data Not Updating

**Symptom**: Made changes (added speakers, updated status) but changes don't appear or revert

**Possible Causes**:
1. Didn't click "Save" button after edits
2. Browser cache showing stale data
3. Optimistic update failed (UI updated but save failed)
4. Concurrent edit conflict

**Solutions**:

**Verify Save Behavior**:
```
Platform save patterns:
- Entity edits (companies, speakers, events): Manual save required (click "Save" button)
- Event state transitions: Automatic save (no separate save button)
- Speaker state updates: Immediate save when changed (Kanban drag-and-drop)
- Slot assignments: Auto-save after 2 seconds

If you edited entity, ensure you clicked "Save" before navigating away.
```

**Force Refresh**:
```
1. Hard refresh: Ctrl+Shift+R (Cmd+Shift+R on Mac)
2. Clears cache and reloads from server
3. Verify if your changes are present
4. If not present, changes were never saved (see next step)
```

**Check Auto-Save Status**:
```
Look for indicators:
- "Saving..." → Changes being saved
- "Saved ✓" → Changes persisted successfully
- "Failed to save" → Error, see browser console for details

If "Failed to save":
1. Check browser console (F12 → Console)
2. Common errors:
   - "Session expired" → Log in again
   - "Permission denied" → Verify role
   - "Network error" → Check connection
3. Re-apply changes and save again
```

---

### Missing Speakers/Topics After State Transition

**Symptom**: Transitioned event state, but speakers or topics that were added are now missing

**Possible Causes**:
1. Transition validation removed incomplete records
2. Filter hiding records
3. Speaker state change moved items to different Kanban column
4. Accidental deletion by another organizer

**Solutions**:

**Check Filters**:
```
1. Look at filter panel (usually top-right)
2. Clear all filters (click "Reset" or "Clear All")
3. Change status filter to "All Statuses"
4. Expand date range to "All Time"
```

**Check Speaker State Columns (Kanban Board)**:
```
Speakers move between Kanban columns based on their individual workflow state:
- Phase A (Setup): Speakers in "Identified" column
- Phase B (Outreach): Moves to "Contacted", "Accepted", "Content Submitted" columns
- Phase C (Quality): Moves to "Quality Reviewed" column
- Phase D (Assignment): Confirmed speakers visible in slot assignment view

Solution: Click through different phase views (A-D) to find speakers in their current state.
```

**Check Archived/Deleted**:
```
1. Toggle "Show Archived" checkbox (if available)
2. Go to Settings → Audit Log to see recent deletions
3. If accidentally deleted, contact admin to restore
```

---

## Workflow State Issues

### Can't Revert to Previous Event State

**Symptom**: Want to revert to earlier event state, but no "Revert State" option

**By Design**: Event workflow is one-directional by default (forward only) to maintain data integrity

**Solutions**:

**Manual State Override** (Organizer with override permission):
```
1. Event → Edit Event modal
2. Check "Override workflow validation" checkbox (if available)
3. Select desired state from "Event State" dropdown
4. Provide reason (required for audit trail)
5. Click "Save Changes"
6. Event reverts to selected state, but data from later states is preserved

Note: This is typically only needed for cancelled/rescheduled events
```

**Correct Data Without Reverting Event State**:
```
Most data can be edited without reverting event state:
- Speakers: Can update individual speaker states at any time (Kanban drag-and-drop)
- Topics: Can add/remove even after AGENDA_PUBLISHED (requires re-publish)
- Content: Upload new versions, speakers can revise submissions
- Slots: Can reassign speakers even after publishing

Only the high-level event state is locked forward. Speaker states are independent.
```

**Special Case: Speaker Workflow Reversal**:
```
Individual speakers CAN move backwards through states:
- confirmed → quality_reviewed (remove slot assignment)
- quality_reviewed → content_submitted (request revisions)
- accepted → contacted (speaker withdrew, re-contacting)

This is done via:
1. Phase B Kanban board: Drag speaker to earlier column
2. Phase C Quality drawer: Click "Request Revisions" instead of "Approve"
3. Phase D Slot assignment: Remove speaker from slot (auto-updates state)
```

---

### Event Stuck in State

**Symptom**: Event shows specific state but workflow UI won't load or shows errors, or can't transition to next state

**Possible Causes**:
1. Database state corruption
2. Event in transition (state updating)
3. Validation criteria not met but error not displayed
4. Event archived but state not updated

**Solutions**:

**Check Event State**:
```
1. Go to Dashboard → Events → [Event]
2. Look at "Event State" badge (top-right)
3. Verify state matches expected workflow position
4. Check for validation errors preventing next transition
```

**Verify Not Archived**:
```
1. Event → Settings → Status
2. Check "Archived" checkbox status
3. If archived, unarchive to make editable again
```

**Force Refresh Event State**:
```
Use Edit modal override:
1. Event → Edit Event
2. Check "Override workflow validation" checkbox
3. System allows manual state selection
4. Select correct state if corrupted
5. Provide reason in notes
6. Save changes

Warning: Only use if state is genuinely incorrect. Most "stuck" issues are validation failures.
```

**Check Speaker State Blocking Event Transition**:
```
Event may be stuck if speaker states prevent progression:
1. SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT requires minimum confirmed speakers
2. Go to Phase B/C to review speaker workflow progress
3. Check how many speakers are in each state:
   - identified, contacted, accepted, content_submitted, quality_reviewed, confirmed
4. Ensure minimum threshold of confirmed speakers reached
5. Event can only progress when speaker requirements met
```

**If Still Stuck**:
```
Contact support with:
- Event number
- Current event state (badge text)
- Screenshot of validation errors
- Browser console errors (F12 → Console)
- Recent actions taken before getting stuck
- Speaker state counts (how many in each state)
```

---

## Error Messages Reference

| Error Code | Message | Cause | Solution |
|------------|---------|-------|----------|
| `WF_001` | "Validation failed: Required criteria not met" | Missing topics, insufficient speakers, or other state requirements | Complete required actions for current state, then transition |
| `WF_002` | "Transition not allowed from current state" | Invalid state transition (e.g., CREATED → SLOT_ASSIGNMENT) | States must progress sequentially. Verify current state, contact support if state corrupted |
| `WF_003` | "Minimum confirmed speakers not met" | Not enough speakers in confirmed state | Review Phase B/C to move more speakers through workflow (identified → confirmed) |
| `WF_004` | "Permission denied: Insufficient role" | User lacks event management permission | Contact admin to add you as organizer for this event |
| `WF_005` | "Concurrent modification detected" | Another user edited event | Refresh page, re-apply changes, attempt transition again |
| `WF_006` | "Cannot transition: Missing slot assignments" | Confirmed speakers not assigned to time slots | Go to Phase D → Slot Assignment, assign all confirmed speakers |
| `WF_007` | "Event archived: State transitions locked" | Event in ARCHIVED state | Unarchive event if changes needed, or create new event |
| `WF_008` | "Speaker cannot reach confirmed state" | Speaker quality not reviewed OR no slot assigned | Ensure BOTH quality_reviewed AND session.startTime exist (order doesn't matter) |

---

## Prevention Best Practices

### Avoiding Workflow Issues

1. **Save Frequently**
   - Don't accumulate many changes before saving
   - Look for auto-save indicators ("Saving..." / "Saved ✓")
   - Manual save after each entity edit (events, speakers, companies)
   - State transitions and speaker state updates auto-save

2. **Communicate with Team**
   - Use "Currently Editing" status to avoid conflicts
   - Assign specific workflow phases to specific organizers
   - Coordinate event state transitions (only one person should trigger)
   - Speaker state updates can happen in parallel

3. **Validate Before Transitioning**
   - Review validation criteria for current state (shown in error messages)
   - Verify counts (speakers in each state, topics selected, slots filled)
   - Ensure speaker workflow progress aligns with event state requirements
   - Test any email templates before sending to speakers

4. **Understand State Dependencies**
   - Event states progress sequentially: CREATED → TOPIC_SELECTION → ... → ARCHIVED
   - Speaker states progress independently and in parallel
   - Event can only advance when sufficient speakers reach required states
   - Quality review and slot assignment can happen in any order

### Workflow Best Practices

1. **Understand the 3 Workflow Systems**
   - Event Workflow: High-level event lifecycle (9 states, sequential)
   - Speaker Workflow: Per-speaker progression (parallel, independent)
   - Task System: Configurable tasks triggered by event state changes
   - Each system operates independently but they coordinate

2. **Plan Phase Transitions**
   - Review state transition requirements before attempting
   - Identify potential bottlenecks (minimum confirmed speakers, slot availability)
   - Allocate sufficient time for speaker workflow progression
   - Remember: Event state blocked until speakers reach required states

3. **Track Speaker Progress**
   - Use Phase B Kanban board to monitor speaker workflow states
   - Check speaker distribution across states (identified, contacted, accepted, etc.)
   - Address blockers early (stuck in contacted, waiting for content)
   - Parallel quality review and slot assignment for efficiency

4. **Document Decisions**
   - Use Notes field in event edit modal
   - Explain specific choices (topic selection rationale, speaker rejection reasons)
   - Record why you used workflow override (if needed)
   - Helps future organizers understand historical decisions

---

## Getting Additional Help

### Before Contacting Support

1. Review error messages carefully (specific details often provided)
2. Check [Workflow Documentation](../workflow/) for state-specific guidance
3. Try browser refresh and cache clear
4. Verify your role and event assignment
5. Check speaker state distribution (may be blocking event transition)

### Contact Support

**Email**: support@batbern.ch

**Include**:
- Event number and current event state
- Speaker state distribution (how many in each state)
- Error message screenshot (exact text)
- Browser console log (F12 → Console → screenshot)
- What you were trying to do (state transition, speaker update, etc.)
- Steps already tried
- Urgency (if event deadline approaching)

**Response Times**:
- Critical (event deadline today): 1-2 hours
- High priority (deadline this week): 4-8 hours
- Standard: 1-2 business days

---

## Related Resources

- **[Workflow System Overview](../workflow/)** - Complete 3-workflow-system documentation
- **[Phase A: Setup](../workflow/phase-a-setup.md)** - Event states: CREATED → TOPIC_SELECTION → SPEAKER_IDENTIFICATION
- **[Phase B: Outreach](../workflow/phase-b-outreach.md)** - Speaker states: identified → contacted → accepted → content_submitted
- **[Phase C: Quality](../workflow/phase-c-quality.md)** - Speaker state: content_submitted → quality_reviewed
- **[Phase D: Assignment](../workflow/phase-d-assignment.md)** - Event states: SLOT_ASSIGNMENT → AGENDA_PUBLISHED, Speaker auto-confirmation
- **[Phase E: Archival](../workflow/phase-e-publishing.md)** - Event state: Any state → ARCHIVED
- **[Event Management](../entity-management/events.md)** - Event workflow states reference
- **[Troubleshooting Overview](README.md)** - Other common issues

---

**Back to Troubleshooting**: Return to [Troubleshooting Overview](README.md) →
