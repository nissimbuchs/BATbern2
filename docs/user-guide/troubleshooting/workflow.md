# Workflow Troubleshooting

## Overview

This guide helps resolve issues with the 16-step event workflow, including state transitions, validation errors, and blocked progression. The workflow governs event progression from creation through completion.

See [16-Step Workflow Documentation](../workflow/) for complete workflow details.

## Quick Diagnosis

```
Can you view the workflow?
│
├─ NO → [Access/Permission Issues](#cant-access-workflow)
│
└─ YES → Can you click "Advance to Next Step"?
    │
    ├─ NO (button disabled) → [Validation Errors](#validation-errors-blocking-advancement)
    ├─ NO (button missing) → [Permission Issues](#insufficient-permissions)
    │
    └─ YES → Does step advance?
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
2. Verify you have one of:
   - ORGANIZER (assigned to event)
   - ADMIN (full access)
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

**Symptom**: Can view workflow but "Advance to Next Step" button missing or disabled with "Permission denied" tooltip

**Cause**: You don't have edit permission for this event

**Who Can Advance Workflow**:
- **ADMIN** role → Can advance any event
- **ORGANIZER** role → Can advance only events where they're assigned
- **SPEAKER** or **ATTENDEE** → Cannot advance workflow (read-only access)

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

### Validation Errors Blocking Advancement

**Symptom**: "Advance to Next Step" button disabled with red error messages above

**Cause**: Required criteria for current step not met

**Common Validations by Step**:

#### Step 2: Topic Selection
```
Validation: Must have selected event topic(s)
Error: "At least one topic must be selected"

Solution:
1. Go to Topic Backlog section
2. Select 1-4 topics for the event
3. Click "Save Topic Selection"
4. Error should clear, advance button enabled
```

#### Step 3: Speaker Brainstorming
```
Validation: Must have identified speakers (minimum threshold)
Error: "Minimum 8 speakers required (current: 5)"

Solution:
1. Go to Speaker Brainstorming section
2. Add more speakers:
   - Search existing speaker database
   - Or add new speaker profiles
3. Save each speaker addition
4. Once threshold reached, advance button enabled

Tip: Threshold is configurable per event (default: 8-10 speakers)
```

#### Step 6: Content Collection
```
Validation: All invited speakers must have submitted content
Error: "3 speakers have not submitted materials"

Solution:
1. View list of pending speakers
2. For each speaker:
   - Send reminder (click "Send Reminder" button)
   - Or manually upload on their behalf
   - Or mark as "Withdrawn" if not participating
3. When all speakers addressed, advance button enabled

Override Option (Admin only):
- Click "Override Validation" checkbox
- Provide reason (e.g., "Waiting for 1 speaker, proceeding anyway")
- Advance with incomplete content (not recommended)
```

#### Step 8: Quality Threshold
```
Validation: Minimum number of quality-approved speakers
Error: "Threshold not met: 7/10 speakers approved"

Solution:
1. Review pending speakers in Quality Review queue
2. For each speaker:
   - Approve (if content meets standards)
   - Request revisions (if minor issues)
   - Reject (if significant problems)
3. Once 10 speakers approved, advance button enabled

Note: Threshold is 10 speakers for full-day events, 6 for afternoon, 4 for evening
```

#### Step 10: Slot Assignment
```
Validation: All approved speakers assigned to time slots
Error: "4 speakers not assigned to slots"

Solution:
1. Go to Slot Assignment view
2. Drag unassigned speakers to available slots
3. Resolve any conflicts (double-booking, speaker unavailability)
4. Save slot assignments
5. Advance button enabled when all assigned
```

---

### Validation Error: "Required field missing"

**Symptom**: Generic validation error without specific details

**Common Missing Fields by Step**:

| Step | Common Missing Fields |
|------|-----------------------|
| 1 - Event Setup | Event type, date, venue, capacity |
| 2 - Topic Selection | At least one topic selected |
| 4 - Outreach Initiated | Invitation email template configured |
| 6 - Content Collection | Speaker submission deadline set |
| 11 - Topic Publishing | Event description (public-facing) |
| 12 - Speaker Publishing | All speaker bios complete |

**Solutions**:

**Step 1: Identify Missing Field**
```
1. Scroll through workflow step page
2. Look for red border or asterisk (*) on fields
3. Red error text usually below field
4. Hover over disabled "Advance" button for hint
```

**Step 2: Complete Required Fields**
```
1. Fill in highlighted fields
2. Click "Save" or "Update" button
3. Verify red errors disappear
4. Advance button should enable
```

**If Still Can't Identify Missing Field**:
```
1. Open browser console (F12 → Console)
2. Click "Advance" button
3. Console may show detailed validation errors
4. Screenshot and send to support if unclear
```

---

## Transition Failures

### Transition Failed Errors

**Symptom**: Click "Advance", loading spinner, then error "Failed to advance workflow" or similar

**Possible Causes**:
1. Concurrent edit conflict (someone else modified event)
2. Database transaction failure
3. Validation changed since page loaded
4. Network timeout during save

**Solutions**:

**Immediate Retry**:
```
1. Click "Advance" button again (may be temporary glitch)
2. If error persists, proceed to next step
```

**Refresh and Retry**:
```
1. Refresh page (F5 or Ctrl+R)
2. Verify all your changes are still saved
3. Review validation errors (may have changed)
4. Try advancing again
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

**Symptom**: Click "Advance", spinner shows indefinitely, no error or success

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
1. Refresh page (do NOT click "Advance" again while spinning)
2. Check if step advanced despite no confirmation
3. If still at same step, click "Advance" again
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
- Entity edits (companies, speakers): Manual save required (click "Save" button)
- Workflow advancement: Automatic save (no button needed)
- Drag-and-drop actions (slot assignment): Auto-save after 2 seconds

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

### Missing Speakers/Topics After Advancing

**Symptom**: Advanced to next step, but speakers or topics that were added are now missing

**Possible Causes**:
1. Transition validation removed incomplete records
2. Filter hiding records
3. Status change moved items to different view
4. Accidental deletion by another organizer

**Solutions**:

**Check Filters**:
```
1. Look at filter panel (usually top-right)
2. Clear all filters (click "Reset" or "Clear All")
3. Change status filter to "All Statuses"
4. Expand date range to "All Time"
```

**Check Status-Based Views**:
```
Speakers may move between views based on workflow step:
- Step 3: All speakers in "Brainstorming" list
- Step 4: Moves to "Invited" status
- Step 6: Moves to "Content Submitted" status
- Step 8: Moves to "Approved" or "Rejected" status

Solution: Click through different status tabs to find speakers.
```

**Check Archived/Deleted**:
```
1. Toggle "Show Archived" checkbox (if available)
2. Go to Settings → Audit Log to see recent deletions
3. If accidentally deleted, contact admin to restore
```

---

## Workflow State Issues

### Can't Go Back to Previous Step

**Symptom**: Want to revert to earlier step, but no "Go Back" or "Revert" option

**By Design**: Workflow is one-directional by default (forward only) to maintain data integrity

**Solutions**:

**Admin Override** (ADMIN role only):
```
1. Event → Workflow → Settings (gear icon)
2. Click "Revert to Previous Step" button
3. Select target step (dropdown)
4. Provide reason (required for audit trail)
5. Confirm reversion
6. Event reverts to selected step, but data from later steps is preserved (hidden)
```

**Correct Data Without Reverting**:
```
Most data can be edited at any step:
- Speakers: Edit profiles, change status at any time
- Topics: Can add/remove even after publishing (requires re-publish)
- Content: Upload new versions, speakers can revise

Only actual "step number" is locked forward.
```

**If You Must Revert** (Non-Admin):
```
1. Email admin with:
   - Event number
   - Current step and desired step
   - Reason for reversion
   - Confirmation you understand data implications
2. Admin will review and revert if justified
```

---

### Event Stuck in Status

**Symptom**: Event shows "IN_PROGRESS" or specific step status but workflow UI won't load or shows errors

**Possible Causes**:
1. Database state corruption
2. Event in transition (workflow updating)
3. Feature flag disabled for event
4. Event archived but status not updated

**Solutions**:

**Check Event Status**:
```
1. Go to Dashboard → Events → [Event]
2. Look at "Status" field (top-right)
3. Check "Current Step" field
4. If mismatch (e.g., Status says "Completed" but step says "6"), data inconsistency exists
```

**Verify Not Archived**:
```
1. Event → Settings → Status
2. Check "Archived" checkbox status
3. If archived, unarchive to make editable again
```

**Force Refresh Event State**:
```
ADMIN only:
1. Event → Settings → Advanced
2. Click "Recalculate Workflow State" button
3. System re-evaluates event against workflow rules
4. May fix state inconsistencies
```

**If Still Stuck**:
```
Contact support with:
- Event number
- Current status and step
- Screenshot of workflow UI
- Browser console errors (F12 → Console)
- Recent actions taken before getting stuck
```

---

## Error Messages Reference

| Error Code | Message | Cause | Solution |
|------------|---------|-------|----------|
| `WF_001` | "Validation failed: Required fields missing" | Incomplete step data | Complete highlighted fields, then advance |
| `WF_002` | "Transition not allowed from current state" | Invalid state transition | Refresh page, verify current step, contact support if repeats |
| `WF_003` | "Minimum threshold not met" | Not enough speakers/topics/etc. | Add more items to meet threshold, or request admin override |
| `WF_004` | "Permission denied: Insufficient role" | User lacks workflow edit permission | Contact admin to add you as organizer or promote role |
| `WF_005` | "Concurrent modification detected" | Another user edited event | Refresh page, re-apply changes, advance again |
| `WF_006` | "Deadline passed: Cannot advance" | Past configured deadline for step | Contact admin to extend deadline or override |
| `WF_007` | "Event finalized: Workflow locked" | Event marked as completed | Contact admin to unlock event if changes needed |

---

## Prevention Best Practices

### Avoiding Workflow Issues

1. **Save Frequently**
   - Don't accumulate many changes before saving
   - Look for auto-save indicators ("Saving..." / "Saved ✓")
   - Manual save after each entity edit

2. **Communicate with Team**
   - Use "Currently Editing" status to avoid conflicts
   - Assign specific steps to specific organizers
   - Don't have multiple people advancing workflow simultaneously

3. **Validate Before Advancing**
   - Review checklist for each step (provided in workflow UI)
   - Verify counts (speakers, topics, slots, etc.)
   - Test any email templates before sending to speakers

4. **Understand Deadlines**
   - Note deadlines for each step (visible in workflow header)
   - Set calendar reminders 2-3 days before deadline
   - Request deadline extension from admin if running behind

### Workflow Best Practices

1. **Plan Ahead**
   - Review entire workflow at event start (know what's coming)
   - Identify potential bottlenecks (speaker outreach, content collection)
   - Allocate sufficient time for each phase

2. **Track Progress**
   - Use workflow dashboard to monitor overall progress
   - Check completion % for each step
   - Address blockers early (don't wait until deadline)

3. **Document Decisions**
   - Use Notes field (available at each step)
   - Explain why you made specific choices (topic selection, speaker rejection)
   - Helps future organizers understand rationale

4. **Test Transitions**
   - When approaching new step, review requirements in advance
   - Prepare data/content before attempting advance
   - Don't advance "just to see what happens" (can't easily revert)

---

## Getting Additional Help

### Before Contacting Support

1. Review error messages carefully (specific details often provided)
2. Check [Workflow Documentation](../workflow/) for step-specific guidance
3. Try browser refresh and cache clear
4. Verify your role and event assignment

### Contact Support

**Email**: support@batbern.ch

**Include**:
- Event number and current workflow step
- Error message screenshot (exact text)
- Browser console log (F12 → Console → screenshot)
- What you were trying to do
- Steps already tried
- Urgency (if event deadline approaching)

**Response Times**:
- Critical (event deadline today): 1-2 hours
- High priority (deadline this week): 4-8 hours
- Standard: 1-2 business days

---

## Related Resources

- **[16-Step Workflow Overview](../workflow/)** - Complete workflow documentation
- **[Phase A: Setup](../workflow/phase-a-setup.md)** - Steps 1-3 details
- **[Phase B: Outreach](../workflow/phase-b-outreach.md)** - Steps 4-6 details
- **[Phase C: Quality](../workflow/phase-c-quality.md)** - Steps 7-8 details
- **[Phase D: Assignment](../workflow/phase-d-assignment.md)** - Steps 9-10 details
- **[Phase E: Publishing](../workflow/phase-e-publishing.md)** - Steps 11-12 details
- **[Phase F: Communication](../workflow/phase-f-communication.md)** - Steps 13-16 details
- **[Troubleshooting Overview](README.md)** - Other common issues

---

**Back to Troubleshooting**: Return to [Troubleshooting Overview](README.md) →
